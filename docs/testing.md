# Testing

## Commands

Use a Java 21 JDK with `JAVA_HOME` set. The Maven Wrapper downloads Maven 3.9.11 on first use. Unit tests have names ending in `Test`; PostgreSQL integration tests end in `IT` and run through Failsafe during `verify`.

| Action | PowerShell | Unix |
| --- | --- | --- |
| Unit tests | `.\mvnw.cmd test` | `./mvnw test` |
| Full suite and executable jar | `.\mvnw.cmd verify` | `./mvnw verify` |
| Optional dependency analysis | `.\mvnw.cmd -Pvulnerability-scan -DskipTests verify` | `./mvnw -Pvulnerability-scan -DskipTests verify` |

The full suite requires Docker by default. Testcontainers starts PostgreSQL 17.7 and cleans up its container after execution. Missing Docker is an error, not a reason to silently report success. No live banking service, paid account, cloud service, or externally provisioned identity provider is required.

## Explicit local PostgreSQL alternative

Use a dedicated, disposable database and a role permitted to create schemas. Each JVM creates a uniquely named `banking_it_<random>` schema, leaving unrelated schemas untouched. Local schemas are retained for inspection; discard the dedicated database when you finish. Never point this at a production or shared business database.

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/banking_test'
$env:TEST_DB_USER = 'banking_test'
$env:TEST_DB_PASSWORD = '<your local test password>'
.\mvnw.cmd verify
# Remove these variables to use Testcontainers again.
Remove-Item Env:TEST_DB_URL, Env:TEST_DB_USER, Env:TEST_DB_PASSWORD
```

```sh
TEST_DB_URL=jdbc:postgresql://localhost:5432/banking_test \
TEST_DB_USER=banking_test TEST_DB_PASSWORD='<your local test password>' ./mvnw verify
```

The same integration test classes run in either mode. They generate signing keys and synthetic user identifiers at runtime. Spring Security's JWT test helper isolates object/role authorization scenarios; separate tests exercise real signed bearer tokens, real password hashes, and actual login requests.

## Demonstrated scenarios

| Layer | Behavior tested |
| --- | --- |
| Unit | Zero starting balance; customer account types; insufficient funds leave balance unchanged; closure requires active/zero balance; closed accounts cannot reopen; exact monetary precision and positive amounts |
| JWT and rate limiter | Valid claims; wrong signature/issuer/audience; missing expiry; expired and future tokens; malformed subjects; unsigned algorithm confusion; short keys; spoofed forwarding headers cannot bypass an IP limit |
| Startup and migrations | Fresh Flyway migration chain, subsequent checksum validation, Hibernate schema validation, application context startup, safe readiness, bearer declaration and Swagger resource availability |
| Identity and authorization | Registration hashes passwords; actual login; real bearer profile update; malformed bearer rejection; unauthenticated access; other-customer account/transfer/statement/closure rejection; customer cannot fund or read administrative audit |
| Money | Bounded one-time deposits; journal balancing; cached balance reconciliation; direct unsafe SQL balance updates and empty journals fail at commit |
| Transfers | Success and persisted insufficient-funds rejection; frozen/closed endpoints; normalized duplicate replay; changed-payload conflict; concurrent exact duplicates; concurrent spending and opposing transfers |
| Rollback | A Mockito-induced audit failure occurs after financial writes; balances, entries, transfer, and key all roll back; the same key can then succeed |
| History and API | Page bounds and sizes; stable direction; type/date filtering; ledger/current balance agreement; malformed and unknown request fields; missing/invalid idempotency headers; financial/audit immutability |

Concurrent tests release worker threads with latches and wait on bounded futures. They assert final business outcomes and balances, not unreliable timing thresholds. These are correctness tests on a small workload, not performance benchmarks or proof against all possible failures.

## Validation evidence

On **2026-10-19**, the local build ran on Windows with Temurin Java 21.0.12.1 and PostgreSQL 17.7. The suite passed **12 unit tests and 25 integration tests**, with zero failures, errors, or skipped tests. PostgreSQL ran as a temporary loopback-only process because Docker Desktop was not installed. The Maven Wrapper also completed a build against that instance. Reports are produced in `target/surefire-reports` and `target/failsafe-reports`; generated build artifacts are not committed.

Testing found and fixed a replay precision defect: Java nanoseconds differed from PostgreSQL microseconds in otherwise identical responses. The replay tests compare complete DTOs and now pass for both successful and rejected outcomes.

Default-profile tests also caught and corrected CORS bean discovery: the explicitly configured source now has Spring Security's expected bean name. Untrusted preflight requests are rejected. A database lock-timeout test verifies `503 RETRY_REQUEST`, no consumed key or partial balance change, and successful retry after the competing transaction releases its lock. Mockito is attached explicitly as a Java agent for Java 21 test execution. MockMvc request/response dumps are disabled to keep authentication material out of failure diagnostics.

The packaged executable jar was also started separately on loopback port 18080 against PostgreSQL. Readiness returned `UP`. The committed PowerShell demo script successfully registered/logged in a synthetic customer, authenticated the bootstrapped administrator, created both account types, deposited USD 100, transferred USD 25, replayed the same transfer, and verified USD 75 / USD 25 balances with zero unreconciled accounts. Local environment scripts generated private random credentials without printing them.

Docker image construction, full Compose execution, and the Testcontainers transport itself were not executed locally. The CI workflow exercises Testcontainers, builds the image, starts Compose, waits for readiness, and runs the synthetic API demo on GitHub-hosted Linux runners. Inspect actual Actions runs before claiming those checks passed. The optional OWASP vulnerability-feed scan is separate and has no claimed clean result unless an actual report is available. No coverage percentage, throughput number, or production certification is asserted.

## Troubleshooting

- Docker connection errors: start Docker Desktop with Linux containers, confirm `docker info`, and retry. Do not replace PostgreSQL with H2 or skip financial tests to make CI pass.
- JWT startup failure: generate at least 32 random bytes and Base64 encode them. Do not use a literal password as a signing key.
- Existing migration checksum mismatch: preserve the applied migration and add a new versioned migration. A disposable development database may be recreated intentionally.
- Wrapper download failure: check your proxy/network configuration and Java installation. Do not commit credentials into Maven settings or wrapper properties.
- `503 RETRY_REQUEST`: retry with the same transfer key and unchanged payload. A new key can execute a second movement.
