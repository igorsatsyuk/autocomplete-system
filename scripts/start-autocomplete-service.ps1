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
$serviceDir = Join-Path $repoRoot "autocomplete-service"
$servicePom = Join-Path $serviceDir "pom.xml"

if (-not (Test-Path $envFile)) {
    throw "Не найден .env в корне репозитория."
}

if (-not (Test-Path $servicePom)) {
    throw "Не найден autocomplete-service/pom.xml."
}

$profile = Get-EnvValue -Path $envFile -Key "SPRING_PROFILES_ACTIVE" -Default "strict"
$redisPort = Get-EnvValue -Path $envFile -Key "REDIS_PORT" -Default "6379"
$serverPort = Get-EnvValue -Path $envFile -Key "AUTOCOMPLETE_SERVICE_PORT" -Default "8081"

$env:SPRING_PROFILES_ACTIVE = $profile
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = $redisPort
$env:SERVER_PORT = $serverPort

Write-Host "[INFO] Запускаю autocomplete-service с локальными override переменными..." -ForegroundColor Cyan
Push-Location $serviceDir
try {
    mvn -B -ntp spring-boot:run
}
finally {
    Pop-Location
}

