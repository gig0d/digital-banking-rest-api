param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^http://(localhost|127\.0\.0\.1)(:[0-9]+)?$') { throw 'This synthetic demo script only accepts a loopback HTTP endpoint.' }
$settings = @{}
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^(DEMO_ADMIN_EMAIL|DEMO_ADMIN_PASSWORD)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
}
if ($env:DEMO_ADMIN_EMAIL) { $settings['DEMO_ADMIN_EMAIL'] = $env:DEMO_ADMIN_EMAIL }
if ($env:DEMO_ADMIN_PASSWORD) { $settings['DEMO_ADMIN_PASSWORD'] = $env:DEMO_ADMIN_PASSWORD }
if (-not $settings['DEMO_ADMIN_EMAIL'] -or -not $settings['DEMO_ADMIN_PASSWORD']) { throw 'Demo administrator credentials must be set locally in .env or environment variables.' }
function Invoke-Banking([string]$Method, [string]$Path, $Body, [hashtable]$Headers = @{}) {
    $parameters = @{ Method=$Method; Uri="$BaseUrl/api/v1$Path"; Headers=$Headers; ContentType='application/json' }
    if ($null -ne $Body) { $parameters.Body = ($Body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @parameters
}
$email = "demo-$([Guid]::NewGuid().ToString('N'))@example.test"
$password = [Guid]::NewGuid().ToString('N') + '-Demo!'
$null = Invoke-Banking POST '/auth/register' @{ email=$email; password=$password; fullName='Synthetic Portfolio Customer' }
$customer = Invoke-Banking POST '/auth/login' @{ email=$email; password=$password }
$admin = Invoke-Banking POST '/auth/login' @{ email=$settings['DEMO_ADMIN_EMAIL']; password=$settings['DEMO_ADMIN_PASSWORD'] }
$customerHeaders = @{ Authorization="Bearer $($customer.accessToken)" }
$adminHeaders = @{ Authorization="Bearer $($admin.accessToken)" }
$checking = Invoke-Banking POST '/accounts' @{ type='CHECKING' } $customerHeaders
$savings = Invoke-Banking POST '/accounts' @{ type='SAVINGS' } $customerHeaders
$null = Invoke-Banking POST '/admin/demo-deposits' @{ accountId=$checking.id; amount=100 } $adminHeaders
$transferHeaders = @{ Authorization=$customerHeaders.Authorization; 'Idempotency-Key'=[Guid]::NewGuid().ToString() }
$payload = @{ sourceAccountId=$checking.id; destinationAccountId=$savings.id; amount=25 }
$transfer = Invoke-Banking POST '/transfers' $payload $transferHeaders
$replay = Invoke-Banking POST '/transfers' $payload $transferHeaders
if ($transfer.id -ne $replay.id) { throw 'Idempotency replay returned a different transfer.' }
$statement = Invoke-Banking GET "/accounts/$($checking.id)/statement" $null $customerHeaders
$destination = Invoke-Banking GET "/accounts/$($savings.id)" $null $customerHeaders
if ($statement.account.balance -ne 75 -or $statement.ledgerBalance -ne 75 -or $destination.balance -ne 25) { throw 'Balances did not reconcile.' }
$summary = Invoke-Banking GET '/admin/summary' $null $adminHeaders
if ($summary.unreconciledAccounts -ne 0) { throw 'Reconciliation failed.' }
Write-Output "Demo passed: checking USD 75.00, savings USD 25.00; duplicate transfer replayed once."
Write-Output "Transfer reference: $($transfer.referenceId)"
