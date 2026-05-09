Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$commonDir = Join-Path $repoRoot "common"
$commonPom = Join-Path $commonDir "pom.xml"

if (-not (Test-Path $commonPom)) {
    throw "Не найден common/pom.xml. Проверьте структуру репозитория."
}

Write-Host "[INFO] Устанавливаю модуль common в локальный Maven-репозиторий..." -ForegroundColor Cyan
Push-Location $commonDir
try {
    mvn -B -DskipTests install
    Write-Host "[OK] common установлен." -ForegroundColor Green
}
finally {
    Pop-Location
}

