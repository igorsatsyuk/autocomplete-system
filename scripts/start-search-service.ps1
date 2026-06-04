Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-EnvValue {
    param(
        [string]$Path,
        [string]$Key,
        [string]$Default = ""
    )

    $match = Select-String -Path $Path -Pattern "^$Key=(.*)$" | Select-Object -First 1
    if ($null -eq $match) {
        return $Default
    }

    return $match.Matches[0].Groups[1].Value.Trim()
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot ".env"
$backendDir = Join-Path $repoRoot "backend"
$backendPom = Join-Path $backendDir "pom.xml"

if (-not (Test-Path $envFile)) {
    throw "Не найден .env в корне репозитория."
}

if (-not (Test-Path $backendPom)) {
    throw "Не найден backend/pom.xml."
}

$profile = Get-EnvValue -Path $envFile -Key "SPRING_PROFILES_ACTIVE" -Default "strict"
$postgresDb = Get-EnvValue -Path $envFile -Key "POSTGRES_DB" -Default "autocomplete"
$postgresUser = Get-EnvValue -Path $envFile -Key "POSTGRES_USER" -Default "autocomplete"
$postgresPassword = Get-EnvValue -Path $envFile -Key "POSTGRES_PASSWORD"
$kafkaPort = Get-EnvValue -Path $envFile -Key "KAFKA_PORT" -Default "9092"
$postgresPort = Get-EnvValue -Path $envFile -Key "POSTGRES_PORT" -Default "5432"
$serverPort = Get-EnvValue -Path $envFile -Key "SEARCH_SERVICE_PORT" -Default "8082"
$managementExposure = Get-EnvValue -Path $envFile -Key "SEARCH_MANAGEMENT_ENDPOINTS_EXPOSURE" -Default "health,info"

if ([string]::IsNullOrWhiteSpace($postgresPassword)) {
    throw "POSTGRES_PASSWORD не задан в .env."
}

$env:SPRING_PROFILES_ACTIVE = $profile
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "localhost:$kafkaPort"
$env:SPRING_KAFKA_STREAMS_BOOTSTRAP_SERVERS = "localhost:$kafkaPort"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$postgresPort/$postgresDb"
$env:SPRING_DATASOURCE_USERNAME = $postgresUser
$env:SPRING_DATASOURCE_PASSWORD = $postgresPassword
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = $managementExposure
$env:SERVER_PORT = $serverPort

Write-Host "[INFO] Запускаю search-service с локальными override переменными..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    mvn -B -ntp -pl search-service -am spring-boot:run
}
finally {
    Pop-Location
}
