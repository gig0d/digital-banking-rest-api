CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT email_normalized CHECK (email = lower(email) AND email = btrim(email))
);
CREATE TABLE customer_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_users(id),
    full_name VARCHAR(120) NOT NULL CHECK (length(btrim(full_name)) BETWEEN 1 AND 120),
    phone VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_users(id),
    action VARCHAR(64) NOT NULL,
    resource_id UUID,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_occurred ON audit_events(occurred_at DESC, id DESC);
