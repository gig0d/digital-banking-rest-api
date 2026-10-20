# Security

## Threat assumptions

This API uses synthetic data on a trusted developer machine. Customers may submit malicious payloads, guess resource UUIDs, replay requests, race transfers, spoof forwarding headers, or present invalid tokens. An administrator is trusted to freeze/unfreeze accounts and issue bounded local demo deposits. Database owners, the host OS, and holders of the JWT signing key are trusted infrastructure.

The application is not certified for PCI DSS, SOC 2, banking regulation, or handling real deposits. Local HTTP and the bootstrap workflow are demo conveniences. Do not use real customer data, reused passwords, or production credentials.

## Implemented controls

| Threat | Control |
| --- | --- |
| Broken object-level authorization | Account queries include the authenticated owner; transfers validate source ownership; statements require ownership; private transfer responses are scoped to the initiator. Unknown and unowned resources both return 404. Integration tests exercise other-customer IDs. |
| Role escalation | Registration always assigns CUSTOMER. Unknown request fields are rejected. ADMIN tokens are issued only for existing administrator records. Administrator routes and controllers require ADMIN; administrators do not gain blanket access to customer endpoints. |
| Credential disclosure | BCrypt cost 12, bounded password input, no plaintext storage. Password and token DTO string representations are redacted. Authentication failures return a generic message. Missing-user login still performs an adaptive hash comparison. |
| JWT forgery/confusion | Spring Security/Nimbus verifies HS256 signatures and restricts accepted algorithms. Tokens require the configured issuer, audience, UUID subject, issued-at, not-before, and expiration. Lifetime is 15 minutes, with 30 seconds of clock skew. Startup rejects short or invalid Base64 keys. |
| Duplicate or concurrent spending | Customer-scoped key uniqueness, payload hashing, transaction-scoped key locking, ordered account locks, no overdraft constraints, atomic posting, and deferred ledger reconciliation. |
| Mass assignment | Request DTOs enumerate allowed fields. Customers cannot supply balances, owners, roles, or arbitrary currency. Profile updates allow only name and phone. |
| SQL injection | ORM parameter binding and parameterized JDBC. Sort direction and movement type come from closed enums. SQL identifiers in production queries are fixed. |
| Authentication abuse | A bounded, per-process, remote-address limiter permits 20 authentication POST requests per minute per IP. At most 10,000 active IP windows are retained. X-Forwarded-For is ignored. Saturated capacity rejects new windows rather than growing memory without limit. |
| Browser exposure | Stateless bearer headers, no auth cookies, explicit CORS allowlist with no wildcard/credentials. CSRF is disabled because authentication is not ambient cookie authentication. Spring Security adds cache, content-type, frame, and transport headers; a restrictive CSP permits local Swagger assets. |
| Information leakage | Problem Details omit exception messages, stack traces, rejected input, SQL, and token contents. Health probes expose only status. Audit events are separate database records with constrained metadata. |

Passwords are limited to 12–72 printable ASCII characters for registration, avoiding BCrypt's byte-length truncation ambiguity. This is a deliberate MVP input policy, not a password-strength certification. Customers cannot change email or password through this MVP.

## Local administrator and funding

The `demo` profile and `DEMO_ENABLED=true` are both required to create the demo funding service and controller. Bootstrap creates an administrator only when a new configured email and a valid supplied password are present. An existing customer with that email causes startup to fail; no promotion or password reset occurs. Existing administrators retain their stored hash. Omit the bootstrap email if an administrator already exists and no bootstrap is needed.

Funding is administrator-only, once per account, at most USD 1,000, active accounts only. It creates a balanced journal against the hidden clearing account and an immutable funding/audit record. Repeating a funding request cannot fund again; it returns `409 ALREADY_FUNDED`. Funding has no general-purpose customer balance endpoint. A lost funding response should be checked using the statement before another attempt.

The clearing account's negative balance represents synthetic issuance. It is not a solvency claim. Administrators can create synthetic deposits only through this explicitly enabled demo mechanism; disable it for any non-demo environment.

## Secrets, logging, and deployment boundaries

Keep `.env` private and out of Git. Generate fresh random signing material; never use the example customer passwords as real credentials. The default application has no signing secret or database password fallback. Do not put secrets in URLs, screenshots, exception output, shell transcripts, CI artifacts, or issue reports. SQL bind logging and request-body logging must remain off for real data. `DEBUG`/`TRACE` environment settings are inappropriate for sensitive environments.

HS256 shares issuance and verification trust inside this one application. Anyone with the key can mint administrator tokens. A real deployment needs managed asymmetric keys, rotation, a revocation/session strategy, MFA, secure credential recovery, verified email, and a considered identity lifecycle. Stateless tokens remain usable until expiration; there is no logout/revocation endpoint in the MVP.

Rate limiting is local to each process, can affect users behind the same NAT, and does not survive a restart. A reverse proxy requires deliberate trusted-address handling and edge rate limits. No proxy forwarding headers are trusted by this application. Do not enable permissive forwarded-header processing casually.

Compose exposes loopback ports and uses a development database owner for Flyway and runtime access. The application container runs as an unprivileged user with a read-only filesystem, temporary `/tmp`, dropped capabilities, and no new privileges. Container image builds and full Compose execution require Docker validation; see the test evidence for what was actually run.

## Additional work for a real financial deployment

Independent threat modeling and security assessment; restricted database roles and migration ownership; TLS and network segmentation; managed secrets and key rotation; tamper-evident off-host audit retention; robust fraud/AML/KYC and sanctions controls; access review and dual control; backups and tested disaster recovery; availability and load testing; reconciliation with external settlement systems; privacy/data-retention policies; monitored incident response; and appropriate legal/regulatory review are all outside this portfolio's claims.

The manual dependency analysis workflow can identify known vulnerable dependencies when vulnerability feeds are reachable. Its presence does not prove the project is free of vulnerabilities. Review findings and false positives; do not suppress them simply to produce a green badge.
