$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Create .env using New-LocalEnv.ps1 first.' }
$allowed = @('DB_PASSWORD','JWT_SECRET','SPRING_PROFILES_ACTIVE','DEMO_ENABLED','DEMO_ADMIN_EMAIL','DEMO_ADMIN_PASSWORD','API_DOCS_ENABLED')
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^([A-Z_]+)=(.*)$' -and $allowed -contains $Matches[1]) {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
Write-Output 'Local demo variables loaded into this PowerShell process.'
