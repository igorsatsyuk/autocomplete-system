import html
import json
import os
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


UNIT_ROOT = Path("test-reports/unit")
INTEGRATION_ROOT = Path("test-reports/integration")


def collect(root: Path, service: str) -> dict[str, int]:
    totals = {"total": 0, "failed": 0, "skipped": 0}
    if not root.exists():
        return totals

    for file_path in root.rglob("TEST-*.xml"):
        if service not in str(file_path):
            continue
        try:
            xml_root = ET.parse(file_path).getroot()
        except ET.ParseError:
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


def fetch_run_jobs() -> list[dict]:
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
    except Exception:
        return []

    return payload.get("jobs", [])


def discover_services(run_jobs: list[dict]) -> list[str]:
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

    for job in run_jobs:
        name = job.get("name", "")
        for prefix in ("Unit - ", "Integration - ", "SonarQube - "):
            if name.startswith(prefix):
                service = name[len(prefix):]
                if service != "frontend":
                    services.add(service)

    return sorted(services)


def get_sonar_statuses(run_jobs: list[dict], services: list[str]) -> dict[str, str]:
    statuses = {service: "unknown" for service in services}
    statuses["frontend"] = "unknown"

    for job in run_jobs:
        name = job.get("name", "")
        conclusion = job.get("conclusion") or job.get("status") or "unknown"

        analysis_step_conclusion = ""
        for step in job.get("steps", []):
            step_name = step.get("name", "")
            if step_name in ("SonarQube analysis", "SonarQube analysis (frontend)"):
                analysis_step_conclusion = (step.get("conclusion") or step.get("status") or "").lower()
                break

        if analysis_step_conclusion == "skipped":
            normalized = "skipped"
        elif analysis_step_conclusion == "success":
            normalized = "success"
        elif analysis_step_conclusion in ("failure", "cancelled", "timed_out", "action_required"):
            normalized = "failure"
        else:
            normalized = conclusion

        if name == "SonarQube - frontend":
            statuses["frontend"] = normalized
        elif name.startswith("SonarQube - "):
            service = name.replace("SonarQube - ", "", 1)
            if service in statuses:
                statuses[service] = normalized

    return statuses


def sonar_counts(status: str) -> dict[str, int]:
    s = (status or "unknown").lower()
    counts = {"total": 1, "success": 0, "failure": 0, "skipped": 0}
    if s == "success":
        counts["success"] = 1
    elif s in ("failure", "cancelled", "timed_out", "action_required"):
        counts["failure"] = 1
    else:
        counts["skipped"] = 1
    return counts


def esc(value: str) -> str:
    return html.escape(str(value), quote=True)


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

    if any(r in ("failure", "cancelled", "timed_out", "action_required") for r in lowered):
        return "failure"
    if all(r in ("success", "skipped", "neutral") for r in lowered):
        return "success"
    return "unknown"


def workflow_overall_status(run_jobs: list[dict]) -> str:
    if not run_jobs:
        return needs_overall_status()

    relevant_jobs = []
    for job in run_jobs:
        name = job.get("name", "")
        if name == "Notify Telegram":
            continue
        if name in ("Build common", "Frontend (Angular)", "Docker Build"):
            relevant_jobs.append(job)
            continue
        if name.startswith(("Unit - ", "Integration - ", "SonarQube - ")):
            relevant_jobs.append(job)

    if not relevant_jobs:
        return needs_overall_status()

    for job in relevant_jobs:
        conclusion = (job.get("conclusion") or job.get("status") or "unknown").lower()
        if conclusion in ("failure", "cancelled", "timed_out", "action_required"):
            return "failure"

    for job in relevant_jobs:
        conclusion = (job.get("conclusion") or job.get("status") or "unknown").lower()
        if conclusion not in ("success", "skipped", "neutral"):
            return needs_overall_status()

    return "success"


def main() -> None:
    run_jobs = fetch_run_jobs()
    services = discover_services(run_jobs)

    overall_status = workflow_overall_status(run_jobs)

    lines: list[str] = []
    lines.append("autocomplete-system CI finished")
    lines.append("")
    lines.append(f"<b>Status:</b> {esc(overall_status)}")
    lines.append(f"<b>Branch:</b> {esc(os.environ.get('GITHUB_REF_NAME', ''))}")
    lines.append(f"<b>Commit:</b> {esc(os.environ.get('GITHUB_SHA', ''))}")
    lines.append(f"<b>Actor:</b> {esc(os.environ.get('GITHUB_ACTOR', ''))}")
    lines.append(f"<b>Workflow:</b> {esc(os.environ.get('GITHUB_WORKFLOW', ''))}")
    lines.append("")
    lines.append("<b>Job results</b>")
    lines.append(f"- backend-unit: {esc(os.environ.get('BACKEND_UNIT_RESULT', 'unknown'))}")
    lines.append(f"- backend-integration: {esc(os.environ.get('BACKEND_INT_RESULT', 'unknown'))}")
    lines.append(f"- frontend: {esc(os.environ.get('FRONTEND_RESULT', 'unknown'))}")
    lines.append(f"- sonarqube: {esc(os.environ.get('SONAR_BACKEND_RESULT', 'unknown'))}")
    lines.append(f"- sonarqube-frontend: {esc(os.environ.get('SONAR_FRONTEND_RESULT', 'unknown'))}")
    lines.append(f"- docker: {esc(os.environ.get('DOCKER_RESULT', 'unknown'))}")

    sonar_statuses = get_sonar_statuses(run_jobs, services)

    lines.append("")
    lines.append("<b>SonarQube checks</b>")
    for service in services + ["frontend"]:
        counts = sonar_counts(sonar_statuses.get(service, "unknown"))
        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        lines.append(f"- Total: {counts['total']}")
        lines.append(f"- ✅ Success: {counts['success']}")
        lines.append(f"- ❌ Failure: {counts['failure']}")
        lines.append(f"- ⏭️ Skipped: {counts['skipped']}")

    for service in services:
        unit = collect(UNIT_ROOT, service)
        unit_passed = max(unit["total"] - unit["failed"] - unit["skipped"], 0)
        integration = collect(INTEGRATION_ROOT, service)
        integration_passed = max(integration["total"] - integration["failed"] - integration["skipped"], 0)

        lines.append("")
        lines.append(f"<b>{esc(service)}</b>")
        lines.append("")
        lines.append("Unit Tests")
        lines.append(f"- Total: {unit['total']}")
        lines.append(f"- Passed: {unit_passed}")
        lines.append(f"- Failed: {unit['failed']}")
        lines.append(f"- Skipped: {unit['skipped']}")
        lines.append("")
        lines.append("Integration Tests")
        lines.append(f"- Total: {integration['total']}")
        lines.append(f"- Passed: {integration_passed}")
        lines.append(f"- Failed: {integration['failed']}")
        lines.append(f"- Skipped: {integration['skipped']}")

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


if __name__ == "__main__":
    main()

