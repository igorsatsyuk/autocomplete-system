import html
import json
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


BACKEND_TEST_ROOT = Path("test-reports/backend")
SONAR_JOB_PREFIX = "SonarQube - "

FAILURE_STATES = {"failure", "cancelled", "timed_out", "action_required"}
SUCCESS_STATES = {"success", "skipped", "neutral"}
JOB_RESULT_FIELDS = (
    ("backend", "BACKEND_RESULT"),
    ("frontend", "FRONTEND_RESULT"),
    ("sonarqube", "SONAR_BACKEND_RESULT"),
    ("sonarqube-frontend", "SONAR_FRONTEND_RESULT"),
    ("docker", "DOCKER_RESULT"),
    ("frontend-e2e-smoke", "FRONTEND_E2E_SMOKE_RESULT"),
)
TOTAL_LINE = "- Total: {value}"
SKIPPED_LINE = "- ⏭️ Skipped: {value}"


def collect(root: Path, service: str, report_dir: str, warnings: list[str]) -> dict[str, int]:
    totals = {"total": 0, "failed": 0, "skipped": 0}
    if not root.exists():
        return totals

    for file_path in root.rglob(f"*/target/{report_dir}/TEST-*.xml"):
        if service not in str(file_path):
            continue
        try:
            xml_root = ET.parse(file_path).getroot()
        except ET.ParseError as exc:
            warnings.append(f"Failed to parse JUnit XML for {service}: {file_path} ({exc})")
            continue

        suites = [xml_root] if xml_root.tag == "testsuite" else list(xml_root.findall("testsuite"))
        for suite in suites:
            tests = int(suite.attrib.get("tests", 0))
            failures = int(suite.attrib.get("failures", 0)) + int(suite.attrib.get("errors", 0))
            skipped = int(suite.attrib.get("skipped", 0))
            totals["total"] += tests
            totals["failed"] += failures
            totals["skipped"] += skipped

    return totals


def fetch_run_jobs(warnings: list[str]) -> list[dict]:
    token = os.environ.get("GH_TOKEN", "")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    api_url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/jobs?per_page=100"

    if not token or not repo or not run_id:
        return []

    req = urllib.request.Request(
        api_url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )

    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except Exception as exc:
        warnings.append(f"Failed to fetch GitHub Actions jobs: {exc}")
        return []

    return payload.get("jobs", [])


def _discover_services_from_artifacts() -> set[str]:
    services: set[str] = set()
    if BACKEND_TEST_ROOT.exists():
        for file_path in BACKEND_TEST_ROOT.rglob("TEST-*.xml"):
            parts = file_path.parts
            if "target" not in parts:
                continue
            target_index = parts.index("target")
            if target_index > 0:
                services.add(parts[target_index - 1])
    return services


def _discover_service_from_job_name(job_name: str) -> str:
    for prefix in ("Unit - ", "Integration - ", SONAR_JOB_PREFIX):
        if job_name.startswith(prefix):
            return job_name[len(prefix):]
    return ""


def discover_services(run_jobs: list[dict]) -> list[str]:
    services = _discover_services_from_artifacts()
    for job in run_jobs:
        service = _discover_service_from_job_name(job.get("name", ""))
        if service and service != "frontend":
            services.add(service)
    return sorted(services)


def _normalize_conclusion(value: str) -> str:
    return (value or "unknown").lower()


def _analysis_step_conclusion(job: dict) -> str:
    for step in job.get("steps", []):
        step_name = step.get("name", "")
        if step_name in ("SonarQube analysis", "SonarQube analysis (frontend)"):
            return _normalize_conclusion(step.get("conclusion") or step.get("status") or "")
    return ""


def _normalize_sonar_status(job: dict) -> str:
    analysis_conclusion = _analysis_step_conclusion(job)
    if analysis_conclusion == "skipped":
        return "skipped"
    if analysis_conclusion == "success":
        return "success"
    if analysis_conclusion in FAILURE_STATES:
        return "failure"
    return _normalize_conclusion(job.get("conclusion") or job.get("status") or "unknown")


def _sonar_service_name(job_name: str) -> str:
    if job_name == f"{SONAR_JOB_PREFIX}frontend":
        return "frontend"
    if job_name.startswith(SONAR_JOB_PREFIX):
        return job_name.replace(SONAR_JOB_PREFIX, "", 1)
    return ""


def get_sonar_statuses(run_jobs: list[dict], services: list[str]) -> dict[str, str]:
    statuses = dict.fromkeys(["backend", "frontend"], "unknown")
    for job in run_jobs:
        service = _sonar_service_name(job.get("name", ""))
        if service and service in statuses:
            statuses[service] = _normalize_sonar_status(job)
    return statuses


def sonar_counts(status: str) -> dict[str, int]:
    s = (status or "unknown").lower()
    counts = {"total": 1, "success": 0, "failure": 0, "skipped": 0, "unknown": 0}
    if s == "success":
        counts["success"] = 1
    elif s in FAILURE_STATES:
        counts["failure"] = 1
    elif s in SUCCESS_STATES:
        counts["skipped"] = 1
    else:
        counts["unknown"] = 1
    return counts


def esc(value: str) -> str:
    return html.escape(str(value), quote=True)


def emphasize(text: str, enabled: bool) -> str:
    if not enabled:
        return text
    return f"<b>⚠️ {text}</b>"


def status_presentation(status: str) -> tuple[str, bool]:
    normalized = _normalize_conclusion(status)
    if normalized == "success":
        return "✅", False
    if normalized in FAILURE_STATES:
        return "❌", True
    if normalized in ("skipped", "neutral"):
        return "⏭️", False
    return "❓", False


def job_result_line(name: str, status: str) -> str:
    normalized = _normalize_conclusion(status)
    icon, is_failure = status_presentation(normalized)
    line = f"- {icon} {name}: {esc(normalized)}"
    return emphasize(line, is_failure)


def overall_status_line(status: str) -> str:
    normalized = _normalize_conclusion(status)
    icon, is_failure = status_presentation(normalized)
    line = f"{icon} <b>Status:</b> {esc(normalized)}"
    if is_failure:
        return f"⚠️ {line}"
    return line


def positive_count_lines(entries: list[tuple[str, int, bool]]) -> list[str]:
    lines: list[str] = []
    for template, value, highlight in entries:
        if value <= 0:
            continue
        line = template.format(value=value)
        lines.append(emphasize(line, highlight))
    return lines


def metric_section(title: str, entries: list[tuple[str, int, bool]]) -> list[str]:
    count_lines = positive_count_lines(entries)
    if not count_lines:
        return []
    return [title, *count_lines]


def needs_overall_status() -> str:
    tracked_results = [
        os.environ.get("BACKEND_RESULT", "unknown"),
        os.environ.get("FRONTEND_RESULT", "unknown"),
        os.environ.get("SONAR_BACKEND_RESULT", "unknown"),
        os.environ.get("SONAR_FRONTEND_RESULT", "unknown"),
        os.environ.get("DOCKER_RESULT", "unknown"),
        os.environ.get("FRONTEND_E2E_SMOKE_RESULT", "unknown"),
    ]
    lowered = [r.lower() for r in tracked_results]

    if any(r in FAILURE_STATES for r in lowered):
        return "failure"
    if all(r in SUCCESS_STATES for r in lowered):
        return "success"
    return "unknown"


def _is_relevant_job(name: str) -> bool:
    if name == "Notify Telegram":
        return False
    if name in ("Backend (Maven)", "Frontend (Angular)", "Docker Build", "Frontend E2E Smoke"):
        return True
    return name.startswith(("Unit - ", "Integration - ", SONAR_JOB_PREFIX))


def workflow_overall_status(run_jobs: list[dict]) -> str:
    if not run_jobs:
        return needs_overall_status()

    relevant_conclusions = [
        _normalize_conclusion(job.get("conclusion") or job.get("status") or "unknown")
        for job in run_jobs
        if _is_relevant_job(job.get("name", ""))
    ]
    if not relevant_conclusions:
        return needs_overall_status()
    if any(conclusion in FAILURE_STATES for conclusion in relevant_conclusions):
        return "failure"
    if all(conclusion in SUCCESS_STATES for conclusion in relevant_conclusions):
        return "success"
    return needs_overall_status()


def append_sonar_checks(lines: list[str], services: list[str], sonar_statuses: dict[str, str]) -> None:
    lines.append("")
    lines.append("<b>SonarQube checks</b>")
    for service in ["backend", "frontend"]:
        counts = sonar_counts(sonar_statuses.get(service, "unknown"))
        sonar_lines = positive_count_lines(
            [
                ("- ✅ Success: {value}", counts["success"], False),
                ("- ❌ Failure: {value}", counts["failure"], True),
                (SKIPPED_LINE, counts["skipped"], False),
                ("- ❓ Unknown: {value}", counts["unknown"], False),
            ]
        )
        if not sonar_lines:
            continue
        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        lines.extend(sonar_lines)


def append_test_sections(lines: list[str], services: list[str], warnings: list[str]) -> None:
    for service in services:
        unit = collect(BACKEND_TEST_ROOT, service, "surefire-reports", warnings)
        unit_passed = max(unit["total"] - unit["failed"] - unit["skipped"], 0)
        integration = collect(BACKEND_TEST_ROOT, service, "failsafe-reports", warnings)
        integration_passed = max(integration["total"] - integration["failed"] - integration["skipped"], 0)

        unit_section = metric_section(
            "Unit Tests",
            [
                (TOTAL_LINE, unit["total"], False),
                ("- ✅ Passed: {value}", unit_passed, False),
                ("- ❌ Failed: {value}", unit["failed"], True),
                (SKIPPED_LINE, unit["skipped"], False),
            ],
        )
        integration_section = metric_section(
            "Integration Tests",
            [
                (TOTAL_LINE, integration["total"], False),
                ("- ✅ Passed: {value}", integration_passed, False),
                ("- ❌ Failed: {value}", integration["failed"], True),
                (SKIPPED_LINE, integration["skipped"], False),
            ],
        )

        if not unit_section and not integration_section:
            continue

        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        if unit_section:
            lines.append("")
            lines.extend(unit_section)
        if integration_section:
            lines.append("")
            lines.extend(integration_section)


def main() -> None:
    warnings: list[str] = []

    run_jobs = fetch_run_jobs(warnings)
    services = discover_services(run_jobs)

    overall_status = workflow_overall_status(run_jobs)

    lines: list[str] = []
    lines.append("autocomplete-system CI finished")
    lines.append("")
    lines.append(overall_status_line(overall_status))
    lines.append(f"<b>Branch:</b> {esc(os.environ.get('GITHUB_REF_NAME', ''))}")
    lines.append(f"<b>Commit:</b> {esc(os.environ.get('GITHUB_SHA', ''))}")
    lines.append(f"<b>Actor:</b> {esc(os.environ.get('GITHUB_ACTOR', ''))}")
    lines.append(f"<b>Workflow:</b> {esc(os.environ.get('GITHUB_WORKFLOW', ''))}")
    lines.append("")
    lines.append("<b>Job results</b>")
    for job_name, env_name in JOB_RESULT_FIELDS:
        lines.append(job_result_line(job_name, os.environ.get(env_name, "unknown")))

    sonar_statuses = get_sonar_statuses(run_jobs, services)
    append_sonar_checks(lines, services, sonar_statuses)
    append_test_sections(lines, services, warnings)

    if warnings:
        lines.append("")
        lines.append("<b>Diagnostics</b>")
        for warning in warnings:
            lines.append(f"- {esc(warning)}")

    lines.append("")
    lines.append(
        f"Link: {esc(os.environ.get('GITHUB_SERVER_URL', ''))}/{esc(os.environ.get('GITHUB_REPOSITORY', ''))}/actions/runs/{esc(os.environ.get('GITHUB_RUN_ID', ''))}"
    )

    message = "\n".join(lines)
    output_path = os.environ["GITHUB_OUTPUT"]
    with open(output_path, "a", encoding="utf-8") as output_file:
        output_file.write("message<<EOF\n")
        output_file.write(message)
        output_file.write("\nEOF\n")

    for warning in warnings:
        print(warning, file=sys.stderr)


if __name__ == "__main__":
    main()

