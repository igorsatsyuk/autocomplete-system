param(
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"
$packageJson = Join-Path $frontendDir "package.json"

if (-not (Test-Path $packageJson)) {
    throw "Не найден frontend/package.json."
}

Push-Location $frontendDir
try {
    if (-not $SkipInstall) {
        Write-Host "[INFO] Устанавливаю frontend-зависимости (npm ci)..." -ForegroundColor Cyan
        npm ci
    }

    Write-Host "[INFO] Запускаю frontend (npm start)..." -ForegroundColor Cyan
    npm start
}
finally {
    Pop-Location
}

