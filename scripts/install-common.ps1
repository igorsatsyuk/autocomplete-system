Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$backendPom = Join-Path $backendDir "pom.xml"

if (-not (Test-Path $backendPom)) {
    throw "Не найден backend/pom.xml. Проверьте структуру репозитория."
}

Write-Host "[INFO] Устанавливаю модуль common в локальный Maven-репозиторий..." -ForegroundColor Cyan
Push-Location $backendDir
try {
    mvn -B -DskipTests -pl common install
    Write-Host "[OK] common установлен." -ForegroundColor Green
}
finally {
    Pop-Location
}

