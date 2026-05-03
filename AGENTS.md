# AGENTS.md

## System map
- This repo is an event-driven autocomplete pipeline: `search-service` records searches, `cdc-service` projects DB changes into Redis, `autocomplete-service` serves suggestions, and `frontend` is the UI + API proxy.
- Use shared runtime identifiers from `common/src/main/java/lt/satsyuk/common/` via `KafkaTopics` and `RedisKeys`.
- Follow the main flow: `frontend` -> `SearchController` -> `SearchEventProducer` -> `search-events` -> `SearchStatsTopology` -> Postgres `search_stats` -> `db-changes.public.search_stats` -> `DebeziumConsumer` -> `RedisSearchUpdater` -> Redis keys like `autocomplete:ja` -> `AutocompleteQueryService`.
- Treat `SearchStatsTopology` as the write-side core: lowercase queries, aggregate in state store `search-counts`, persist via `SearchStatRepository`, emit `search-stats`.
- Treat `DebeziumConsumer` as envelope-based CDC parsing: read `payload.after`, then let `RedisSearchUpdater` fan the query into prefix keys.
- Keep `AutocompleteQueryService` simple: blank prefixes and non-positive limits return empty results.

## Working rules
- Trace changes across service boundaries before editing: UI -> command side -> Postgres/Debezium/Kafka -> Redis -> query side.
- Prefer `docker-compose.yml` for end-to-end validation.
- Treat backend modules as independent Maven projects, not one root build; install `common` first when running outside Docker.
- Build Java service Dockerfiles from repo root; `frontend/Dockerfile` builds from `frontend/`.
- Keep `frontend/proxy.conf.json` and `frontend/nginx.conf` aligned when API routes change.
- Use integration tests as specs; they may skip without Docker via `@Testcontainers(disabledWithoutDocker = true)`.

## Do
- Keep controllers thin; put behavior in producer/service/topology classes (`SearchController`, `AutocompleteController`).
- Preserve lowercase normalization anywhere data is persisted or indexed (`SearchStatsTopology`, `RedisSearchUpdater`, `AutocompleteQueryService`).
- Add new Kafka or Redis identifiers in `common` first, then reference them via configuration defaults.
- Preserve Debezium message handling through `payload.after`; do not assume a flat CDC JSON payload.
- When changing schema, update both `search-service/src/main/resources/db/migration/` and `infra/postgres/` so Compose bootstrap matches Flyway.
- Use nearby tests as behavior specs: `SearchServiceKafkaIT`, `CdcServiceRedisIT`, `AutocompleteServiceRedisIT`, plus unit tests around controllers, producer, topology, and query service.

## Don't
- Don't bypass the CDC path by writing autocomplete data directly from `search-service` to Redis.
- Don't hardcode `search-events`, `db-changes.public.search_stats`, or `autocomplete:` outside `common` defaults.
- Don't add business logic to the Angular service beyond debouncing, HTTP calls, and UI-facing composition.
- Don't skip operational verification when touching the pipeline: check Compose logs, Redis keys like `autocomplete:ja`, and rows in `search_stats`.

## Change checklist
- Identify the affected stage: UI, command side, Postgres/Flyway, Debezium/Kafka, Redis indexing, or query side.
- Reuse identifiers from `common` before adding any topic names, regexes, or Redis prefixes.
- Preserve lowercase normalization and Debezium parsing via `payload.after`.
- Sync paired files when needed: `db/migration` + `infra/postgres`, `frontend/proxy.conf.json` + `frontend/nginx.conf`.
- Check the nearest existing test first, then run the most relevant service test(s) after editing.
- Decide what observable state you will verify: Compose logs, Redis keys, Kafka topic flow, or rows in `search_stats`.

## Quick references
- Start with: `README.md`, `docker-compose.yml`, `infra/debezium/connector-config.json`, `infra/kafka/kafka-init.sh`.
- Key files: `search-service/.../SearchController.java`, `search-service/.../SearchEventProducer.java`, `search-service/.../SearchStatsTopology.java`, `cdc-service/.../DebeziumConsumer.java`, `cdc-service/.../RedisSearchUpdater.java`, `autocomplete-service/.../AutocompleteQueryService.java`, `frontend/src/app/services/autocomplete.service.ts`.

