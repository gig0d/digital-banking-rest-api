# Digital Banking REST API

A Java 21 banking backend by **Giovanny Alvarado Barrantes** ([gig0d](https://github.com/gig0d)), built to demonstrate transactional consistency, API authorization, relational modeling, and repeatable delivery.

This is a simulated educational portfolio project. It processes synthetic USD balances, has no connection to payment networks, and is not a production-certified banking application.

## What works

- Customer registration, BCrypt password hashing, login, and short-lived JWTs with signature, algorithm, issuer, audience, and time validation.
- Private customer profiles; checking and savings accounts with active, frozen, and closed lifecycle states.
- Explicitly enabled, administrator-only demo deposits: at most USD 1,000 once per account, offset against a system clearing account.
- Atomic internal transfers with ordered PostgreSQL row locks, persisted rejected outcomes, and customer-scoped idempotency keys.
- An immutable double-entry ledger with deferred database checks for balanced journals and account reconciliation.
- Authorized account statements with date/type filters, stable sorting, and bounded pagination.
- Administrator freeze/unfreeze operations, operational summaries, and separate audit records.
- Problem Details errors, OpenAPI/Swagger UI, safe health probes, Docker configuration, and CI workflows.

## Engineering choices

| Area | Choice and reason |
| --- | --- |
| Runtime | Java 21 LTS, Spring Boot 4.0.8, Spring MVC |
| Persistence | PostgreSQL 17, Spring Data JPA for entities, parameterized JDBC for ledger/idempotency queries, Flyway |
| Security | Spring Security resource server and Nimbus JWT components; HS256 restricted to one issuer/service |
| Money | `BigDecimal`, explicit USD, two decimal places, no implicit rounding |
| Concurrency | Pessimistic row locks in a consistent UUID order; a bounded wait followed by a retryable error |
| Tests | JUnit Jupiter 6, Mockito, Spring Boot Test, Spring Security Test, PostgreSQL Testcontainers |
| API description | springdoc-openapi 3.0.3 with bearer authentication |

JUnit 6 is an **owner-approved change** from the original JUnit 5 requirement: Spring Framework 7's test extension requires its APIs. See [ADR 003](docs/decisions/003-supported-stack.md) for compatibility sources and the observed failure.

## Quick start with Docker Compose

Install Git and Docker Desktop with Compose and Linux containers. You do not need a host JDK for this path. First-time builds require internet access to download public dependencies and container images.

```powershell
git clone https://github.com/gig0d/digital-banking-rest-api.git
cd digital-banking-rest-api
# Generates .env without printing credentials; enables synthetic demo funding.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\New-LocalEnv.ps1
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Demo-Flow.ps1
```

Unix shell equivalents:

```sh
git clone https://github.com/gig0d/digital-banking-rest-api.git
cd digital-banking-rest-api
sh scripts/new-local-env.sh
docker compose up --build -d
curl --fail http://localhost:8080/actuator/health/readiness
# Follow docs/api-examples.md for curl examples, or run Demo-Flow.ps1 with pwsh.
```

Wait for readiness to report `UP` before running requests. Inspect startup failures with `docker compose logs app`. The API and database ports bind to loopback only. Open [Swagger UI](http://localhost:8080/swagger-ui/index.html) or [OpenAPI JSON](http://localhost:8080/v3/api-docs) after enabling documentation in your local environment.

The demo script registers a new synthetic customer, logs in both roles, creates checking and savings accounts, deposits USD 100, transfers USD 25, repeats the same request, and verifies balances of USD 75 and USD 25. It prints reference IDs, never passwords or tokens.

Alternatively, copy `.env.example` to `.env` and fill every required value yourself. `JWT_SECRET` must be Base64-encoded random bytes, at least 32 decoded bytes; a phrase is unsuitable. The scripts generate random values locally. `.env` is ignored by Git. Review its permissions before sharing your computer.

## Run with Java 21 and the Maven Wrapper

Set `JAVA_HOME` to a Java 21 JDK. Maven is downloaded by the wrapper; a global Maven installation is unnecessary. Start the database using the same `.env`:

```powershell
docker compose up -d db
# For the reviewed local scripts, applies only to this PowerShell session.
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\Import-LocalEnv.ps1
.\mvnw.cmd spring-boot:run
```

```sh
docker compose up -d db
# Only source the .env you generated and control locally.
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

For a separately managed database, set `DB_URL`, `DB_USER`, and `DB_PASSWORD`. Flyway creates and validates the schema; Hibernate uses `ddl-auto=validate`.

Demo bootstrap requires **both** the `demo` profile and `DEMO_ENABLED=true`. Supply a unique synthetic `DEMO_ADMIN_EMAIL` and a random 12–72 ASCII character `DEMO_ADMIN_PASSWORD` for its first startup. Existing customer accounts are never promoted. Once the administrator exists, remove the bootstrap password from `.env` when it is no longer needed; keep a private local password record for login. No administrator credentials are built into the application.

To run without demo capabilities, disable `DEMO_ENABLED` and remove the `demo` profile. API documentation is disabled by default outside the example Compose configuration.

## API tour

All business resources are under `/api/v1`. [Reproducible API examples](docs/api-examples.md) include both PowerShell and curl.

| Method | Resource | Access |
| --- | --- | --- |
| POST | `/auth/register`, `/auth/login` | Public, rate limited |
| GET / PATCH | `/customers/me` | Current customer |
| POST / GET | `/accounts` | Current customer's accounts |
| GET | `/accounts/{id}` | Account owner |
| POST | `/accounts/{id}/closure` | Owner; active, zero balance |
| POST | `/transfers` | Owned source; `Idempotency-Key` required |
| GET | `/transfers/{id}` | Initiating customer |
| GET | `/accounts/{id}/statement` | Account owner |
| POST | `/admin/demo-deposits` | Administrator; explicitly enabled demo only |
| PATCH | `/admin/accounts/{id}/status` | Administrator; ACTIVE or FROZEN |
| GET | `/admin/summary`, `/admin/audit-events` | Administrator |

Incoming transfers appear in a recipient's account statement. A recipient cannot retrieve the sender's private transfer request. Reusing a transfer key returns the original outcome, including an original rejection; a changed payload returns `409 IDEMPOTENCY_CONFLICT`. A new business attempt requires a new key.

## Tests and delivery

```powershell
.\mvnw.cmd test       # Unit tests; Java 21, no database needed
.\mvnw.cmd verify     # Unit + PostgreSQL integration tests; Docker required
```

```sh
./mvnw test
./mvnw verify
```

The integration suite starts PostgreSQL with Testcontainers by default and fails when Docker is unavailable. It never silently skips database tests. An explicitly supplied, isolated local PostgreSQL instance is also supported; see [testing](docs/testing.md).

The [CI workflow](.github/workflows/ci.yml) builds, tests with Testcontainers, builds the Docker image, starts Compose, and verifies the synthetic API flow. The optional manual [vulnerability analysis workflow](.github/workflows/dependency-scan.yml) runs OWASP Dependency-Check. Consult actual [GitHub Actions runs](https://github.com/gig0d/digital-banking-rest-api/actions) for remote results; a workflow file alone is not evidence of a green run.

See [validation evidence and limitations](docs/testing.md#validation-evidence). Docker execution and CI status are reported separately from local PostgreSQL results.

## Read the design

- [Architecture and transfer sequence](docs/architecture.md)
- [Data model and ledger invariants](docs/data-model.md)
- [Security assumptions, controls, and limitations](docs/security.md)
- [Tests and demonstrated scenarios](docs/testing.md)
- [Architecture decision records](docs/decisions/)
- [Implemented, planned, and excluded scope](docs/roadmap.md)

Suggested review path: `TransferService` → `IdempotencyRepository` → `LedgerService` → Flyway migrations → `TransferIT`. These show where concurrency is serialized, where atomicity comes from, and which invariants the database enforces.

Stop the local stack with `docker compose down`; the named volume preserves synthetic data. Remove a volume only when you intend to discard that local data. No public hosting or cloud resources are provisioned by this project.
