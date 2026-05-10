# Contributing to autocomplete-system

Thanks for contributing. This guide explains how to work in this repository safely and consistently.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Architecture Rules](#architecture-rules)
- [Coding Standards](#coding-standards)
- [Branch Naming](#branch-naming)
- [Commit Messages](#commit-messages)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Code Review Checklist](#code-review-checklist)

---

## Development Environment Setup

### Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 25 |
| Maven | 3.9+ |
| Docker Desktop | Latest stable |
| Node.js | 20 |
| npm | 10+ |
| Git | 2.40+ |

### First-time setup

1. Clone the repository:
   ```powershell
   git clone https://github.com/<owner>/autocomplete-system.git
   Set-Location .\autocomplete-system
   ```

2. Copy the env template and set required values:
   ```powershell
   Copy-Item .env.example .env
   ```

3. Start the stack:
   ```powershell
   docker compose up -d --build
   ```

4. Verify services:
   ```powershell
   docker compose ps
   ```

5. (Optional for local non-Docker app runs) install shared module first:
   ```powershell
   Set-Location .\common
   mvn -B -DskipTests install
   ```

---

## Project Structure

- `common/` - shared runtime contracts (`KafkaTopics`, `RedisKeys`, DTO/util classes)
- `search-service/` - write side: search intake + Kafka Streams aggregation + PostgreSQL writes
- `cdc-service/` - Debezium CDC consumer + Redis prefix index updater
- `autocomplete-service/` - query side: suggestions from Redis
- `frontend/` - Angular UI + API proxy/nginx
- `infra/` - bootstrap assets for Kafka, Debezium, PostgreSQL, Redis
- `docker-compose.yml` - full local environment

---

## Architecture Rules

When changing behavior, trace the full flow:

`frontend -> /api/search -> search-service -> search-events -> SearchStatsTopology -> search_stats -> Debezium -> cdc-service -> Redis -> autocomplete-service -> /api/complete`

Required rules:

- Keep controllers thin; place logic in services/producers/topologies.
- Do not bypass CDC by writing autocomplete data from `search-service` directly to Redis.
- Keep query normalization (`trim + lowercase`) consistent in write/index/query path.
- Parse Debezium envelope via `payload.after`; do not assume flat CDC JSON.
- Use identifiers from `common` (`KafkaTopics`, `RedisKeys`) instead of hardcoding names.
- Keep migration files mirrored between:
  - `search-service/src/main/resources/db/migration/`
  - `infra/postgres/`
- Keep `frontend/proxy.conf.json` and `frontend/nginx.conf` aligned for API route changes.

---

## Coding Standards

### General

- Prefer readable, small methods and explicit naming.
- Keep module boundaries clean (no cross-service shortcuts).
- Preserve strict profile compatibility (`SPRING_PROFILES_ACTIVE=strict`).

### Backend (Java)

- Java 25 features are allowed when they improve clarity.
- Reuse constants from `common` for topics/prefixes.
- Follow existing package layout `lt.satsyuk.<module>`.
- Add or update tests for behavioral changes.

### Frontend (Angular)

- Keep business logic out of UI service layers unless UI-specific.
- `autocomplete.service.ts` should stay focused on debouncing + HTTP + UI composition.
- Keep API paths consistent with proxy/nginx configs.

### Security

- Never commit real secrets or credentials.
- Keep secrets in `.env` only.
- Redact sensitive values when sharing logs.

---

## Branch Naming

Use one of the prefixes:

| Prefix | Purpose | Example |
|---|---|---|
| `feat/` | New feature | `feat/add-prefix-boosting` |
| `fix/` | Bug fix | `fix/fix-cdc-envelope-parse` |
| `hotfix/` | Urgent production fix | `hotfix/fix-redis-clear-timeout` |
| `chore/` | Maintenance | `chore/update-workflow-actions` |
| `docs/` | Documentation changes | `docs/update-readme-ci-section` |
| `test/` | Test-only changes | `test/add-cdc-truncate-it` |

---

## Commit Messages

Use Conventional Commits:

```text
<type>(<scope>): <short description>
```

Common types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`

Examples:

```text
feat(search-service): add state store override for streams topology
fix(cdc-service): block updates during truncate clear window
docs: align readme with current ci workflow
ci: add telegram notification summary step
```

---

## Testing

Backend modules are independent Maven projects.

### Backend unit tests

```powershell
Set-Location .\search-service
mvn -B -ntp test

Set-Location ..\cdc-service
mvn -B -ntp test

Set-Location ..\autocomplete-service
mvn -B -ntp test
```

### Backend integration tests

```powershell
Set-Location .\search-service
mvn -B -ntp test-compile failsafe:integration-test failsafe:verify

Set-Location ..\cdc-service
mvn -B -ntp test-compile failsafe:integration-test failsafe:verify

Set-Location ..\autocomplete-service
mvn -B -ntp test-compile failsafe:integration-test failsafe:verify
```

### Frontend checks

```powershell
Set-Location .\frontend
npm ci
npm run lint --if-present
npm run test:ci
npm run build --if-present
```

### Frontend E2E smoke

Verifies the full pipeline end-to-end: seeds search events, waits for Redis prefix index to populate, opens the UI in a headless browser, and asserts suggestions are sorted by score with correct click-through behavior.

Requires Docker Desktop running and `.env` populated (`Copy-Item .env.example .env`).

```powershell
# Stack must already be up
Set-Location .\frontend
npm run test:e2e-smoke
```

To start the stack, run, and tear down:

```powershell
docker compose up -d --build
Set-Location .\frontend
npm run test:e2e-smoke
Set-Location ..
docker compose down -v
```

### End-to-end sanity checks

```powershell
docker compose logs --tail=100 search-service
docker compose logs --tail=100 cdc-service
docker compose exec redis redis-cli ZREVRANGE autocomplete:ja 0 9 WITHSCORES
```

For a fully automated end-to-end check, use the smoke test:

```powershell
Set-Location .\frontend
npm run test:e2e-smoke
```

---

## Pull Request Process

1. Create a branch from `main`.
2. Implement changes with tests.
3. Run relevant backend/frontend checks locally.
4. Run E2E smoke test if pipeline behavior was affected: `npm run test:e2e-smoke` from `frontend/`.
5. Verify docs/configs are in sync when needed:
   - migrations + `infra/postgres`
   - `proxy.conf.json` + `nginx.conf`
6. Open a PR to `main` with:
   - change summary
   - affected pipeline stage(s)
   - test evidence
7. Address review comments.
8. Merge after CI passes (including `frontend-e2e-smoke` job).

---

## Code Review Checklist

- [ ] Change follows pipeline architecture and service boundaries
- [ ] No hardcoded Kafka topic or Redis key names outside `common`
- [ ] Lowercase normalization preserved on write/index/query path
- [ ] Debezium parsing uses envelope (`payload.after`)
- [ ] Schema changes mirrored in both migration locations
- [ ] Relevant unit/integration/frontend tests are updated
- [ ] E2E smoke passes locally (`npm run test:e2e-smoke`) if pipeline behavior changed
- [ ] `frontend/proxy.conf.json` and `frontend/nginx.conf` are aligned (if routes changed)
- [ ] No secrets committed; logs/examples are redacted
- [ ] Commit messages follow Conventional Commits

