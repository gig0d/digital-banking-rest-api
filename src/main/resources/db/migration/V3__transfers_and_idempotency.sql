CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_users(id),
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 1000000.00),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUCCEEDED', 'REJECTED')),
    rejection_code VARCHAR(64),
    journal_id UUID UNIQUE REFERENCES journals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_account_id, currency) REFERENCES accounts(id, currency),
    FOREIGN KEY (destination_account_id, currency) REFERENCES accounts(id, currency),
    CHECK (source_account_id <> destination_account_id),
    CHECK ((status = 'SUCCEEDED' AND journal_id IS NOT NULL AND rejection_code IS NULL)
        OR (status = 'REJECTED' AND journal_id IS NULL AND rejection_code IS NOT NULL))
);
CREATE INDEX ix_transfers_customer ON transfers(customer_id, created_at DESC, id DESC);
CREATE TABLE idempotency_records (
    customer_id UUID NOT NULL REFERENCES app_users(id),
    request_key VARCHAR(96) NOT NULL CHECK (length(request_key) BETWEEN 8 AND 96),
    payload_hash VARCHAR(64) NOT NULL,
    transfer_id UUID UNIQUE REFERENCES transfers(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, request_key)
);
CREATE TRIGGER immutable_transfers BEFORE UPDATE OR DELETE ON transfers FOR EACH ROW EXECUTE FUNCTION prevent_mutation();

CREATE FUNCTION verify_completed_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM idempotency_records WHERE customer_id=NEW.customer_id
        AND request_key=NEW.request_key AND transfer_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Idempotency records must contain a completed response' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER completed_idempotency AFTER INSERT OR UPDATE ON idempotency_records
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_completed_idempotency();

CREATE FUNCTION verify_transfer_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status='SUCCEEDED' AND NOT EXISTS (
        SELECT 1 FROM journals j JOIN ledger_entries d ON d.journal_id=j.id AND d.side='DEBIT'
        JOIN ledger_entries c ON c.journal_id=j.id AND c.side='CREDIT'
        WHERE j.id=NEW.journal_id AND j.type='TRANSFER' AND j.currency=NEW.currency
            AND d.account_id=NEW.source_account_id AND c.account_id=NEW.destination_account_id
            AND d.amount=NEW.amount AND c.amount=NEW.amount
    ) THEN
        RAISE EXCEPTION 'Transfer must match its journal' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM accounts WHERE id=NEW.source_account_id AND owner_id=NEW.customer_id) THEN
        RAISE EXCEPTION 'Transfer source must belong to customer' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER transfer_journal AFTER INSERT ON transfers DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_transfer_journal();
