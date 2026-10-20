# Roadmap

## Implemented

Customer registration/login and profile updates; secure short-lived bearer tokens; checking/savings accounts; lifecycle controls; opt-in administrator bootstrap and one-time demo deposits; transactional transfers; immutable balanced journals; ordered account locking; durable customer-scoped idempotency; persisted rejected transfers; authorized statements; bounded pagination and filters; administrator summary/audit; PostgreSQL migrations and constraints; automated business/security/concurrency tests; OpenAPI; Docker/Compose configuration; CI and optional vulnerability analysis workflows.

Local tests and remote CI are separate validation results. See [testing evidence](testing.md#validation-evidence) for the exact executed environment and remaining delivery checks.

## Planned improvements

- More targeted database privileges and separate migration/runtime roles.
- Asymmetric signing keys with rotation and a deliberate revocation/session model.
- Email verification, password change/recovery, and stronger administrator authentication.
- Keyset pagination and explicit statement export snapshots.
- Measured load testing, reconciliation cost analysis, and a retention policy for idempotency/audit data.
- Database backup/restore exercises and additional failure-injection tests.
- Automated dependency updates reviewed against behavioral and security tests.

These are proposed work, not existing capabilities. No delivery dates or performance claims are implied.

## Intentionally excluded from the MVP

Real funds, card data, payment networks, external transfers, loans, interest accrual, multiple currencies, foreign exchange, regulatory certification, KYC/AML integrations, fraud engines, public deployment, multi-region availability, microservices, Kafka, Kubernetes, Redis, external identity providers, and a frontend. Each would need a separately justified scope and, where relevant, owner approval.
