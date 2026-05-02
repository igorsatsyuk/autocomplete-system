# Autocomplete System

## Prerequisites
- Docker Desktop with Compose

## Start
```powershell
docker compose up -d --build
```

## Iteration 1: local docker smoke

### Quick checks
```powershell
docker compose ps
curl "http://localhost:4200/api/search?q=java"
curl "http://localhost:4200/api/complete?q=ja&limit=10"
```

## Iteration 2: end-to-end CDC flow

### 1. Trigger a search event
```powershell
curl "http://localhost:8082/api/search?q=java"
```

### 2. Check autocomplete result
```powershell
curl "http://localhost:8081/api/complete?q=ja&limit=10"
```

Expected result after the pipeline settles:

```json
[{"query":"java","score":0.0}]
```

### 3. Verify Postgres aggregate
```powershell
docker exec autocomplete-system-postgres-1 psql -U autocomplete -d autocomplete -c "SELECT query, frequency FROM search_stats WHERE query='java';"
```

### 4. Verify Debezium and Kafka
```powershell
curl "http://localhost:8083/connectors"
curl "http://localhost:8083/connectors/postgres-connector/status"
docker exec autocomplete-system-kafka-1 kafka-topics --bootstrap-server kafka:9092 --list
docker exec autocomplete-system-kafka-1 kafka-consumer-groups --bootstrap-server kafka:9092 --list
```

### 5. Verify Redis autocomplete index
```powershell
docker exec autocomplete-system-redis-1 redis-cli ZREVRANGE autocomplete:ja 0 9 WITHSCORES
```

## Iteration 3: guardrails and maintainability

### What was added
- unit/contract tests for `search-service`, `cdc-service`, `autocomplete-service`
- configurable Kafka topics and Streams identifiers in `search-service`
- configurable Redis prefix in `cdc-service` and `autocomplete-service`

### Run backend tests
```powershell
cd search-service
mvn -B test

cd ..\cdc-service
mvn -B test

cd ..\autocomplete-service
mvn -B test
```

### What should pass
- `SearchControllerTest`
- `SearchEventProducerTest`
- `SearchStatsTopologyTest`
- `DebeziumConsumerTest`
- `AutocompleteQueryServiceTest`

## Iteration 4: shared contracts, integration tests, and stability

### What was added

#### 1. Shared constants module (`common`)
- `KafkaTopics.java`: centralized Kafka topic constants
  - `SEARCH_EVENTS` = "search-events"
  - `SEARCH_STATS` = "search-stats"
  - `DB_CHANGES_SEARCH_STATS` = "db-changes.public.search_stats"
  - `DB_CHANGES_SEARCH_STATS_PATTERN` = "db-changes\\.public\\.search_stats"
- `RedisKeys.java`: centralized Redis key prefix
  - `AUTOCOMPLETE_PREFIX` = "autocomplete:"

All Java services now **depend on** `common:1.0.0` and use these constants at runtime.

#### 2. Testcontainers integration tests
New IT tests added to each backend service:
- `search-service`: `SearchServiceKafkaIntegrationTest`
  - Validates Kafka producer publishes events correctly
- `cdc-service`: `CdcServiceRedisIntegrationTest`
  - Validates Debezium message parsing and Redis updates
- `autocomplete-service`: `AutocompleteServiceRedisIntegrationTest`
  - Validates Redis-backed API end-to-end

**Important**: ITs are marked with `@Testcontainers(disabledWithoutDocker = true)`:
- If Docker is available: full container-backed IT tests run
- If Docker is unavailable (e.g., local development): ITs are **safely skipped**, normal unit tests still run
- CI/CD with Docker will execute all ITs

#### 3. Mockito JDK 25 self-attach warning removal
- Added `byte-buddy-agent` test dependency to all backend service poms
- Configured surefire plugin with javaagent in `<argLine>`:
  ```xml
  <argLine>-javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/${bytebuddy.version}/byte-buddy-agent-${bytebuddy.version}.jar</argLine>
  ```
- Eliminates Mockito instrumentation warnings on JDK 25+

#### 4. Docker build context updates
- Java service Dockerfiles now build from **repo root** context:
  - `compose.yaml`: services use `build.context: .` and `build.dockerfile: service/Dockerfile`
  - Each service Dockerfile:
    1. Copies `common` source
    2. Installs `common` module first
    3. Then builds service artifact
- Enables shared module reuse during Docker image assembly

### Run backend tests with integration test validation
```powershell
cd common
mvn -B -DskipTests install

cd ..\search-service
mvn -B test

cd ..\cdc-service
mvn -B test

cd ..\autocomplete-service
mvn -B test
```

**Expected output**:
- Unit tests and mocks pass
- ITs run under Docker, or are skipped without Docker (no test failures)
- No Mockito self-attach warnings

### Validate Docker builds
```powershell
docker compose config | grep -A 5 'build:'
docker compose build search-service cdc-service autocomplete-service
```

### Optional: Full smoke test
```powershell
docker compose up -d --build
docker compose ps

# Check services health
curl "http://localhost:8082/api/search?q=java"
curl "http://localhost:8081/api/complete?q=ja&limit=10"

docker compose down
```

## Useful logs
```powershell
docker compose logs --tail=100 search-service
docker compose logs --tail=100 cdc-service
docker compose logs --tail=100 autocomplete-service
docker compose logs --tail=100 debezium
docker compose logs --tail=100 frontend
```

## Stop
```powershell
docker compose down
```

