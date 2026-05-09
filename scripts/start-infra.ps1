Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $composeFile)) {
    throw "Не найден docker-compose.yml. Запустите скрипт из репозитория autocomplete-system."
}

if (-not (Test-Path $envFile)) {
    throw "Не найден .env. Создайте его из .env.example перед запуском инфраструктуры."
}

Write-Host "[INFO] Поднимаю инфраструктуру Docker Compose..." -ForegroundColor Cyan
Push-Location $repoRoot
try {
    # debezium-init намеренно не запускаем здесь: он depends_on search-service.
    docker compose up -d postgres redis zookeeper kafka kafka-init debezium kafka-ui
    docker compose ps
    Write-Host "[OK] Инфраструктура запущена." -ForegroundColor Green
}
finally {
    Pop-Location
}

