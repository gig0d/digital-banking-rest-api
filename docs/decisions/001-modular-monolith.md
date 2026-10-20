# ADR 001: Modular monolith with one transactional database

Status: Accepted. Date: 2026-10-19.

## Context

The portfolio needs understandable business boundaries and atomic financial operations without operating a distributed system.

## Decision

Use package modules inside one Java application and one PostgreSQL database. Keep controllers, services, repositories, and DTOs explicit. Use JPA for normal entity persistence and small parameterized JDBC repositories where PostgreSQL locking/idempotency SQL is clearer.

## Consequences

Spring transactions cover balances, ledger, transfers, keys, and audit without distributed coordination. Deployment and local development remain small. Boundaries are enforced through code review and package responsibilities rather than remote APIs or a module framework. High-scale partitioning is excluded until a measured need exists.
