param([string]$AdminEmail = 'admin@example.test')
$ErrorActionPreference = 'Stop'
$destination = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (Test-Path -LiteralPath $destination) { throw '.env already exists. Review it locally instead of overwriting it.' }
function New-RandomValue {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}
if ($AdminEmail -notmatch '^[^\s@=]+@[^\s@=]+\.[^\s@=]+$') { throw 'Supply a valid synthetic administrator email.' }
$content = @(
    '# Local synthetic demo only. Never commit this file.'
    "DB_PASSWORD=$(New-RandomValue)"
    "JWT_SECRET=$(New-RandomValue)"
    'SPRING_PROFILES_ACTIVE=demo'
    'DEMO_ENABLED=true'
    "DEMO_ADMIN_EMAIL=$AdminEmail"
    "DEMO_ADMIN_PASSWORD=$(New-RandomValue)"
    'API_DOCS_ENABLED=true'
) -join "`n"
[System.IO.File]::WriteAllText($destination, $content + "`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output '.env created with random local credentials and the demo profile enabled. Keep it private.'
