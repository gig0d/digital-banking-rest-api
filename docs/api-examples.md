# Reproducible API examples

Start the application with the README's local demo configuration. Use only synthetic data. `scripts/Demo-Flow.ps1` executes and verifies the core happy path without printing credentials or access tokens. The examples below show individual requests for inspection.

## PowerShell

Use PowerShell 5.1 or newer. Import only the locally generated `.env` using the supplied allowlisted loader. Set your own customer password if you reuse this outside an isolated demo.

```powershell
# If local scripts are disabled, allow the reviewed scripts in this process only.
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\Import-LocalEnv.ps1
$base = 'http://localhost:8080/api/v1'
$email = "customer-$([Guid]::NewGuid().ToString('N'))@example.test"
$password = [Guid]::NewGuid().ToString('N') + '-Demo!'
$registration = @{ email=$email; password=$password; fullName='Synthetic Customer' } | ConvertTo-Json
$customer = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType 'application/json' -Body $registration
# 201: userId, customerId, normalized email, fullName. No password/hash.

$credentials = @{ email=$email; password=$password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $credentials
$headers = @{ Authorization="Bearer $($login.accessToken)" }
# 200: accessToken, tokenType=Bearer, expiresIn=900. Keep the token private.

Invoke-RestMethod -Uri "$base/customers/me" -Headers $headers
$profile = @{ fullName='Updated Synthetic Customer'; phone='+506 1234 5678' } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "$base/customers/me" -Headers $headers -ContentType 'application/json' -Body $profile

$checking = Invoke-RestMethod -Method Post -Uri "$base/accounts" -Headers $headers -ContentType 'application/json' -Body '{"type":"CHECKING"}'
$savings = Invoke-RestMethod -Method Post -Uri "$base/accounts" -Headers $headers -ContentType 'application/json' -Body '{"type":"SAVINGS"}'
# 201. Both start at 0.00 USD. No client-supplied balance is accepted.
Invoke-RestMethod -Uri "$base/accounts?page=0&size=20" -Headers $headers
Invoke-RestMethod -Uri "$base/accounts/$($checking.id)" -Headers $headers

$adminCredentials = @{ email=$env:DEMO_ADMIN_EMAIL; password=$env:DEMO_ADMIN_PASSWORD } | ConvertTo-Json
$adminLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $adminCredentials
$adminHeaders = @{ Authorization="Bearer $($adminLogin.accessToken)" }
$deposit = @{ accountId=$checking.id; amount=100 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/admin/demo-deposits" -Headers $adminHeaders -ContentType 'application/json' -Body $deposit
# 201. Only once per account, maximum 1000.00 USD. Requires demo opt-in and ADMIN.

$transferHeaders = @{ Authorization=$headers.Authorization; 'Idempotency-Key'=[Guid]::NewGuid().ToString() }
$payload = @{ sourceAccountId=$checking.id; destinationAccountId=$savings.id; amount=25 } | ConvertTo-Json
$transfer = Invoke-RestMethod -Method Post -Uri "$base/transfers" -Headers $transferHeaders -ContentType 'application/json' -Body $payload
# 200 SUCCEEDED. The source must belong to the authenticated customer.
$replay = Invoke-RestMethod -Method Post -Uri "$base/transfers" -Headers $transferHeaders -ContentType 'application/json' -Body $payload
# Same transfer ID, reference, amount, timestamp and outcome. No second movement.
Invoke-RestMethod -Uri "$base/transfers/$($transfer.id)" -Headers $headers

Invoke-RestMethod -Uri "$base/accounts/$($checking.id)/statement?page=0&size=20&direction=DESC" -Headers $headers
Invoke-RestMethod -Uri "$base/accounts/$($checking.id)/statement?type=TRANSFER&from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z" -Headers $headers
# Cached balance=75.00 and ledgerBalance=75.00. Savings balance=25.00.

Invoke-RestMethod -Method Patch -Uri "$base/admin/accounts/$($checking.id)/status" -Headers $adminHeaders -ContentType 'application/json' -Body '{"status":"FROZEN"}'
Invoke-RestMethod -Method Patch -Uri "$base/admin/accounts/$($checking.id)/status" -Headers $adminHeaders -ContentType 'application/json' -Body '{"status":"ACTIVE"}'
Invoke-RestMethod -Uri "$base/admin/summary" -Headers $adminHeaders
Invoke-RestMethod -Uri "$base/admin/audit-events?page=0&size=20" -Headers $adminHeaders

# Closure requires a separate active account with zero balance.
$empty = Invoke-RestMethod -Method Post -Uri "$base/accounts" -Headers $headers -ContentType 'application/json' -Body '{"type":"SAVINGS"}'
Invoke-RestMethod -Method Post -Uri "$base/accounts/$($empty.id)/closure" -Headers $headers
```

`Invoke-RestMethod` throws on non-2xx responses. Inspect the response body locally when exercising rejection cases; do not paste authorization headers or secrets into logs. Existing local administrator login credentials must match the original bootstrap password; changing the environment variable does not reset an existing hash.

## Unix curl

These examples use `curl`, `jq`, and the generated local `.env`. Avoid shell tracing (`set -x`). The environment file contains local random secrets and must be one you control. `curl --fail-with-body` requires curl 7.76 or later; omit that flag on older installations when inspecting error bodies.

```sh
set -a; . ./.env; set +a
BASE=http://localhost:8080/api/v1
EMAIL="customer-$(date +%s)@example.test"
PASSWORD="$(openssl rand -hex 24)"

curl --fail-with-body -sS "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p,fullName:"Synthetic Customer"}')"
TOKEN=$(curl --fail-with-body -sS "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')" | jq -er .accessToken)
ADMIN_TOKEN=$(curl --fail-with-body -sS "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$DEMO_ADMIN_EMAIL" --arg p "$DEMO_ADMIN_PASSWORD" '{email:$e,password:$p}')" | jq -er .accessToken)

CHECKING=$(curl --fail-with-body -sS "$BASE/accounts" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"type":"CHECKING"}' | jq -er .id)
SAVINGS=$(curl --fail-with-body -sS "$BASE/accounts" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"type":"SAVINGS"}' | jq -er .id)
curl --fail-with-body -sS "$BASE/admin/demo-deposits" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg a "$CHECKING" '{accountId:$a,amount:100}')"

KEY="demo-$(openssl rand -hex 16)"
PAYLOAD=$(jq -n --arg s "$CHECKING" --arg d "$SAVINGS" '{sourceAccountId:$s,destinationAccountId:$d,amount:25}')
curl --fail-with-body -sS "$BASE/transfers" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$PAYLOAD"
# Repeat unchanged to retrieve the same committed outcome.
curl --fail-with-body -sS "$BASE/transfers" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' -d "$PAYLOAD"
curl --fail-with-body -sS "$BASE/accounts/$CHECKING/statement?type=TRANSFER&page=0&size=20&direction=DESC" -H "Authorization: Bearer $TOKEN"
curl --fail-with-body -sS -X PATCH "$BASE/admin/accounts/$CHECKING/status" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"status":"FROZEN"}'
curl --fail-with-body -sS -X PATCH "$BASE/admin/accounts/$CHECKING/status" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' -d '{"status":"ACTIVE"}'
curl --fail-with-body -sS "$BASE/admin/audit-events?page=0&size=20" -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Request and response rules

- Account types: CHECKING or SAVINGS. Currency: USD, server assigned.
- Movement amounts: positive, at most two decimal places; transfers at most 1,000,000.00; demo deposits at most 1,000.00. Values are validated, never rounded into acceptance.
- Transfer keys: 8–96 characters, letters/digits or `._:-`; scoped to the initiating authenticated customer. Retained indefinitely in the MVP.
- Pagination: zero-based `page` from 0 to 10,000, `size` from 1 to 100, default 20.
- Statements: optional ISO UTC `from` inclusive / `to` exclusive; `type=TRANSFER|DEMO_DEPOSIT`; `direction=ASC|DESC`, default DESC. Sort ties use entry UUID.
- Successful transfer submission and replay return 200. Persisted business rejection returns 409 and a `transferId`; retrieve that ID as the initiating customer to inspect its terminal REJECTED record.
- Balances and statements omit customer credentials and counterpart private profile information. Rejected requests create no statement entry.

Example insufficient-funds response (`application/problem+json`):

```json
{
  "type": "urn:banking:problem:insufficient-funds",
  "title": "Conflict",
  "status": 409,
  "detail": "The transfer was rejected by account status or funds rules. Retrieve the transfer for its persisted outcome.",
  "code": "INSUFFICIENT_FUNDS",
  "transferId": "a synthetic transfer UUID"
}
```

| Scenario | Expected result |
| --- | --- |
| No bearer token / invalid bearer | 401, UNAUTHENTICATED / INVALID_TOKEN |
| Customer calls administrator route | 403 FORBIDDEN |
| Another customer's account, statement, or private transfer | 404 RESOURCE_NOT_FOUND |
| Zero/negative/overprecision amount or malformed JSON | 400 INVALID_REQUEST |
| Missing key / invalid key syntax | 400 INVALID_REQUEST / INVALID_IDEMPOTENCY_KEY |
| Same key with changed payload | 409 IDEMPOTENCY_CONFLICT |
| Insufficient funds / frozen or closed endpoint | 409 INSUFFICIENT_FUNDS / ACCOUNT_NOT_ACTIVE; original rejection persists |
| Repeat one-time funding | 409 ALREADY_FUNDED |
| Too many authentication requests | 429 RATE_LIMITED, Retry-After: 60 |
| Transfer lock contention | 503 RETRY_REQUEST, Retry-After: 1; retry unchanged with the same key |

Swagger UI's Authorize control accepts the raw access token. Authentication operations are public in the OpenAPI document. Keep documentation disabled on any environment where it is not intentionally exposed.
