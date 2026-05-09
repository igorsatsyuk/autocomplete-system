# Start Infrastructure
.\scripts\start-infra.ps1

# Register Debezium Connector (one-off, without pulling search-service)
docker compose run --rm --no-deps debezium-init

# Install Common
.\scripts\install-common.ps1

# Start Services
.\scripts\start-search-service.ps1
.\scripts\start-cdc-service.ps1
.\scripts\start-autocomplete-service.ps1
.\scripts\start-frontend.ps1