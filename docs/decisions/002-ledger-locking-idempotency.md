# ADR 002: Immutable ledger, ordered row locks, and durable idempotency

Status: Accepted. Date: 2026-10-19.

## Context

Two requests must not spend the same balance, a retried request must not execute twice, and a failed operation must not leave one-sided movements.

## Decision

Represent money as USD `BigDecimal` amounts with two decimals. Use an immutable two-entry journal for every supported movement, with cached balances verified at commit by PostgreSQL deferred triggers. Acquire account write locks in Java UUID order. Lock customer-scoped idempotency records using a unique key, `INSERT ON CONFLICT`, and a subsequent `FOR UPDATE` select. Retain completed outcomes and fingerprints indefinitely in the MVP.

Persist terminal SUCCEEDED or REJECTED transfer records. The processing state exists only inside the uncommitted transaction; no committed pending records are exposed. Return the original terminal result on replay. Reject changed payloads with 409. Time out lock contention and let the caller retry the same key; do not automatically repeat unknown financial outcomes.

## Consequences

Retries and concurrent duplicates have durable semantics without an external cache. Rejected attempts are reviewable without ledger movements. A very busy account serializes writers; clearing-account funding also serializes. Reconciliation scans grow with account history, and indefinitely retained keys consume storage. These are acceptable portfolio tradeoffs, not a high-volume banking claim.

Sources: [PostgreSQL row locking](https://www.postgresql.org/docs/17/explicit-locking.html), [deferred constraint triggers](https://www.postgresql.org/docs/17/sql-createtrigger.html).
