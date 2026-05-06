# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| `main` (latest) | Yes |
| Older branches/tags | Best effort |

## Reporting a Vulnerability

Please report vulnerabilities responsibly.

### How to report

1. Do **not** open a public issue for a suspected vulnerability.
2. Report privately to: **[security contact email]**.
3. Include:
   - affected module(s): `search-service`, `cdc-service`, `autocomplete-service`, `frontend`, `common`, or infra
   - vulnerability description
   - reproducible steps / PoC
   - impact assessment
   - proposed remediation (optional)

### Response timeline (target)

- Acknowledgment: within 48 hours
- Initial triage: within 5 business days
- Fix timeline: severity-based
  - Critical: as soon as possible
  - High: within 7 days
  - Medium: within 30 days

## Security Baseline

### Secrets and credentials

- Secrets must be provided through environment variables (`.env`).
- Never commit `.env` or real credentials.
- Use `.env.example` as the only committed template.
- Rotate `POSTGRES_PASSWORD` when sharing environment access.
- Redact connection details and credentials from logs before sharing.

### Runtime hardening

- Compose publishes service ports on `127.0.0.1` by default.
- Services run in strict profile by default: `SPRING_PROFILES_ACTIVE=strict`.
- Strict mode fails startup when required connectivity vars are missing.
- `search-service` management exposure is limited to `health,info` by default.

### Data flow and trust boundaries

- Do not bypass CDC path by writing directly from `search-service` to Redis.
- Debezium messages must be parsed from envelope (`payload.after`).
- Topic names and Redis prefixes must come from `common` constants (`KafkaTopics`, `RedisKeys`).
- Keep normalization (`trim + lowercase`) consistent across write/index/query layers.

### Infrastructure and CI

- Validate Debezium connector health before testing production-like flows.
- CI includes backend/frontend tests, Sonar analysis (optional when token configured), Docker builds, and optional Telegram notifications.
- Keep GitHub Actions pinned/action-reviewed and dependencies updated.

## Required Environment Variables

At minimum for secure local startup in strict mode:

| Variable | Description |
|---|---|
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `POSTGRES_DB` | PostgreSQL database |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |

Additional Debezium/runtime variables are defined in `.env.example`.

## Dependency and Image Hygiene

- Keep Maven/npm dependencies updated to patched versions.
- Keep Docker base images updated (`postgres`, `redis/redis-stack`, `confluentinc/cp-kafka`, `debezium/connect`, `curlimages/curl`).
- Review CVEs before releases and after dependency bumps.

## Operational Security Checks

Useful checks during incident triage:

```powershell
docker compose ps
curl "http://localhost:8083/connectors/postgres-connector/status"
docker compose logs --tail=200 debezium
docker compose logs --tail=200 cdc-service
docker compose exec redis redis-cli KEYS "autocomplete:*"
```

## Disclosure and Coordination

- Please allow maintainers time to investigate and patch before public disclosure.
- Credit can be provided in release notes upon request.

