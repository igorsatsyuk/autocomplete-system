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
$kafkaPort = Get-EnvValue -Path $envFile -Key "KAFKA_PORT" -Default "9092"
$redisPort = Get-EnvValue -Path $envFile -Key "REDIS_PORT" -Default "6379"
$serverPort = Get-EnvValue -Path $envFile -Key "CDC_SERVICE_PORT" -Default "8084"

$env:SPRING_PROFILES_ACTIVE = $profile
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "localhost:$kafkaPort"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = $redisPort
$env:SERVER_PORT = $serverPort

Write-Host "[INFO] Запускаю cdc-service с локальными override переменными..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    mvn -B -ntp -pl cdc-service -am spring-boot:run
}
finally {
    Pop-Location
}
