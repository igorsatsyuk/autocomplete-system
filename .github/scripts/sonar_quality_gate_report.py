#!/usr/bin/env python3
"""Wait for Sonar analysis completion and publish a detailed Quality Gate summary."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def parse_kv_file(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def api_get_json(url: str, token: str) -> dict:
    request = urllib.request.Request(url)
    request.add_header("Authorization", f"Basic {build_basic_auth(token)}")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Sonar API request failed: {url} returned HTTP {error.code}. Response: {response_body}"
        ) from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Sonar API request failed: {url}. Reason: {error.reason}") from error


def build_basic_auth(token: str) -> str:
    import base64

    raw = f"{token}:".encode("utf-8")
    return base64.b64encode(raw).decode("ascii")


def wait_for_ce_task(ce_task_url: str, token: str, timeout_seconds: int, poll_seconds: int) -> dict:
    started = time.monotonic()
    while True:
        payload = api_get_json(ce_task_url, token)
        task = payload.get("task", {})
        status = task.get("status")

        if status in {"SUCCESS", "FAILED", "CANCELED"}:
            return task

        if (time.monotonic() - started) > timeout_seconds:
            raise TimeoutError(f"Timed out waiting for Sonar CE task at {ce_task_url}")

        time.sleep(poll_seconds)


def build_measures_url(host_url: str, project_key: str, pull_request: str | None, branch: str | None) -> str:
    metric_keys = ",".join(
        [
            "coverage",
            "new_coverage",
            "bugs",
            "new_bugs",
            "vulnerabilities",
            "new_vulnerabilities",
            "code_smells",
            "new_code_smells",
            "duplicated_lines_density",
            "new_duplicated_lines_density",
        ]
    )
    query = {
        "component": project_key,
        "metricKeys": metric_keys,
    }
    if pull_request:
        query["pullRequest"] = pull_request
    elif branch:
        query["branch"] = branch
    return f"{host_url}/api/measures/component?{urllib.parse.urlencode(query)}"


def detect_analysis_scope() -> tuple[str | None, str | None]:
    event_path = os.getenv("GITHUB_EVENT_PATH", "")
    if event_path:
        try:
            event_payload = json.loads(Path(event_path).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            event_payload = {}
        pull_request_number = event_payload.get("pull_request", {}).get("number")
        if pull_request_number is not None:
            return str(pull_request_number), None

    branch = (os.getenv("GITHUB_HEAD_REF") or os.getenv("GITHUB_REF_NAME") or "").strip()
    return None, branch or None


def to_measure_map(payload: dict) -> dict[str, str]:
    component = payload.get("component", {})
    result: dict[str, str] = {}
    for measure in component.get("measures", []):
        result[measure.get("metric")] = measure.get("value", "-")
    return result


def fetch_measures(host_url: str, project_key: str, token: str) -> dict[str, str]:
    pull_request, branch = detect_analysis_scope()
    scoped_url = build_measures_url(host_url, project_key, pull_request, branch)

    # Some Sonar editions reject branch/pullRequest params; retry without scope.
    try:
        return to_measure_map(api_get_json(scoped_url, token))
    except RuntimeError as scoped_error:
        base_url = build_measures_url(host_url, project_key, None, None)
        if base_url != scoped_url:
            try:
                print(
                    "Scoped measures query failed; retrying without branch/pullRequest context",
                    file=sys.stderr,
                )
                return to_measure_map(api_get_json(base_url, token))
            except RuntimeError as base_error:
                print(f"Unable to load Sonar measures: {base_error}", file=sys.stderr)
                return {}

        print(f"Unable to load Sonar measures: {scoped_error}", file=sys.stderr)
        return {}


def append_summary(text: str) -> None:
    summary_file = os.getenv("GITHUB_STEP_SUMMARY")
    if not summary_file:
        return
    with open(summary_file, "a", encoding="utf-8") as handle:
        handle.write(text)


def is_missing_new_code_metrics_only(gate_status: str, conditions: list[dict], measures: dict[str, str]) -> bool:
    if gate_status != "ERROR":
        return False

    # When Sonar cannot compute new-code metrics for a context (e.g., no new code),
    # these measures are often "-" and the gate can surface as ERROR.
    if measures.get("new_coverage", "-") not in {"-", "NO_VALUE", ""}:
        return False

    if not conditions:
        return False

    has_non_ok_condition = False
    for condition in conditions:
        status = (condition.get("status") or "").upper()
        if status in {"", "OK"}:
            continue

        has_non_ok_condition = True
        metric_key = condition.get("metricKey", "")
        actual_value = condition.get("actualValue")
        normalized_actual = "-" if actual_value in (None, "") else str(actual_value)

        if not metric_key.startswith("new_"):
            return False
        if normalized_actual not in {"-", "NO_VALUE", "None"}:
            return False

    return has_non_ok_condition


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-key", required=True)
    parser.add_argument("--component-name", required=True)
    parser.add_argument("--report-task-file", required=True)
    parser.add_argument("--timeout-seconds", type=int, default=300)
    parser.add_argument("--poll-seconds", type=int, default=5)
    parser.add_argument(
        "--allow-missing-new-code-metrics",
        action="store_true",
        help="Do not fail when Sonar returns ERROR only because new_* metrics are unavailable",
    )
    args = parser.parse_args()

    token = os.getenv("SONAR_TOKEN", "")
    if not token:
        print("SONAR_TOKEN is missing", file=sys.stderr)
        return 2

    configured_host_url = (os.getenv("SONAR_HOST_URL") or "").strip()
    report_task_path = Path(args.report_task_file)
    if not report_task_path.exists():
        print(f"report-task.txt not found: {report_task_path}", file=sys.stderr)
        return 2

    report = parse_kv_file(report_task_path)
    report_server_url = (report.get("serverUrl") or "").strip()
    host_url = (configured_host_url or report_server_url or "https://sonarcloud.io").rstrip("/")
    ce_task_url = report.get("ceTaskUrl")
    if not ce_task_url:
        print("ceTaskUrl is missing in report-task.txt", file=sys.stderr)
        return 2

    try:
        print(f"Waiting for Sonar CE task: {ce_task_url}")
        task = wait_for_ce_task(ce_task_url, token, args.timeout_seconds, args.poll_seconds)
        task_status = task.get("status", "UNKNOWN")
        analysis_id = task.get("analysisId")

        if task_status != "SUCCESS" or not analysis_id:
            print(f"Sonar CE task status is {task_status}; analysisId={analysis_id}", file=sys.stderr)
            return 1

        qg_url = f"{host_url}/api/qualitygates/project_status?analysisId={urllib.parse.quote(analysis_id)}"
        qg_payload = api_get_json(qg_url, token)
        project_status = qg_payload.get("projectStatus", {})
        gate_status = project_status.get("status", "NONE")
        conditions = project_status.get("conditions", [])

        measures = fetch_measures(host_url, args.project_key, token)
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(f"Quality Gate ({args.component_name}): {gate_status}")
    print("Measures:")
    for metric_key in [
        "coverage",
        "new_coverage",
        "bugs",
        "new_bugs",
        "vulnerabilities",
        "new_vulnerabilities",
        "code_smells",
        "new_code_smells",
        "duplicated_lines_density",
        "new_duplicated_lines_density",
    ]:
        print(f"  {metric_key}: {measures.get(metric_key, '-')}")

    summary_lines = [
        f"### SonarQube - {args.component_name}",
        "",
        f"- Quality Gate: **{gate_status}**",
        f"- Project key: `{args.project_key}`",
        "",
        "| Metric | Value |",
        "|---|---:|",
    ]
    for metric_key in [
        "coverage",
        "new_coverage",
        "bugs",
        "new_bugs",
        "vulnerabilities",
        "new_vulnerabilities",
        "code_smells",
        "new_code_smells",
        "duplicated_lines_density",
        "new_duplicated_lines_density",
    ]:
        summary_lines.append(f"| `{metric_key}` | {measures.get(metric_key, '-')} |")

    summary_lines.extend(["", "| Condition metric | Status | Actual | Threshold |", "|---|---|---:|---:|"])
    if conditions:
        for condition in conditions:
            summary_lines.append(
                "| `{}` | {} | {} | {} |".format(
                    condition.get("metricKey", "-"),
                    condition.get("status", "-"),
                    condition.get("actualValue", "-"),
                    condition.get("errorThreshold", "-"),
                )
            )
    else:
        summary_lines.append("| `-` | - | - | - |")

    summary_lines.append("\n")
    append_summary("\n".join(summary_lines))

    if gate_status != "OK":
        if args.allow_missing_new_code_metrics and is_missing_new_code_metrics_only(gate_status, conditions, measures):
            print(
                "Quality Gate returned ERROR due to unavailable new-code metrics; treated as neutral for this run"
            )
            return 0
        print("Quality Gate failed", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

