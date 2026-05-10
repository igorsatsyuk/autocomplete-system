import html
import json
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


UNIT_ROOT = Path("test-reports/unit")
INTEGRATION_ROOT = Path("test-reports/integration")
SONAR_JOB_PREFIX = "SonarQube - "

FAILURE_STATES = {"failure", "cancelled", "timed_out", "action_required"}
SUCCESS_STATES = {"success", "skipped", "neutral"}
JOB_RESULT_FIELDS = (
    ("backend-unit", "BACKEND_UNIT_RESULT"),
    ("backend-integration", "BACKEND_INT_RESULT"),
    ("frontend", "FRONTEND_RESULT"),
    ("sonarqube", "SONAR_BACKEND_RESULT"),
    ("sonarqube-frontend", "SONAR_FRONTEND_RESULT"),
    ("docker", "DOCKER_RESULT"),
)


def collect(root: Path, service: str, warnings: list[str]) -> dict[str, int]:
    totals = {"total": 0, "failed": 0, "skipped": 0}
    if not root.exists():
        return totals

    for file_path in root.rglob("TEST-*.xml"):
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
    for root, prefix in (
        (UNIT_ROOT, "unit-test-reports-"),
        (INTEGRATION_ROOT, "integration-test-reports-"),
    ):
        if not root.exists():
            continue
        for artifact_dir in root.iterdir():
            if artifact_dir.is_dir() and artifact_dir.name.startswith(prefix):
                services.add(artifact_dir.name[len(prefix):])
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
    statuses = dict.fromkeys([*services, "frontend"], "unknown")
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


def needs_overall_status() -> str:
    tracked_results = [
        os.environ.get("BACKEND_UNIT_RESULT", "unknown"),
        os.environ.get("BACKEND_INT_RESULT", "unknown"),
        os.environ.get("FRONTEND_RESULT", "unknown"),
        os.environ.get("SONAR_BACKEND_RESULT", "unknown"),
        os.environ.get("SONAR_FRONTEND_RESULT", "unknown"),
        os.environ.get("DOCKER_RESULT", "unknown"),
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
    if name in ("Build common", "Frontend (Angular)", "Docker Build"):
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

    lines.append("")
    lines.append("<b>SonarQube checks</b>")
    for service in services + ["frontend"]:
        counts = sonar_counts(sonar_statuses.get(service, "unknown"))
        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        lines.append(f"- Total: {counts['total']}")
        lines.append(f"- ✅ Success: {counts['success']}")
        lines.append(emphasize(f"- ❌ Failure: {counts['failure']}", counts["failure"] > 0))
        lines.append(f"- ⏭️ Skipped: {counts['skipped']}")
        lines.append(f"- ❓ Unknown: {counts['unknown']}")

    for service in services:
        unit = collect(UNIT_ROOT, service, warnings)
        unit_passed = max(unit["total"] - unit["failed"] - unit["skipped"], 0)
        integration = collect(INTEGRATION_ROOT, service, warnings)
        integration_passed = max(integration["total"] - integration["failed"] - integration["skipped"], 0)

        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        lines.append("")
        lines.append("Unit Tests")
        lines.append(f"- Total: {unit['total']}")
        lines.append(f"- ✅ Passed: {unit_passed}")
        lines.append(emphasize(f"- ❌ Failed: {unit['failed']}", unit["failed"] > 0))
        lines.append(f"- ⏭️ Skipped: {unit['skipped']}")
        lines.append("")
        lines.append("Integration Tests")
        lines.append(f"- Total: {integration['total']}")
        lines.append(f"- ✅ Passed: {integration_passed}")
        lines.append(emphasize(f"- ❌ Failed: {integration['failed']}", integration["failed"] > 0))
        lines.append(f"- ⏭️ Skipped: {integration['skipped']}")

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

