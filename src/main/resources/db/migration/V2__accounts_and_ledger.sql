CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES app_users(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'SYSTEM')),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    balance NUMERIC(19,2) NOT NULL DEFAULT 0,
    demo_funded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, currency),
    CHECK ((type = 'SYSTEM' AND owner_id IS NULL AND status = 'ACTIVE') OR (type <> 'SYSTEM' AND owner_id IS NOT NULL)),
    CHECK (type = 'SYSTEM' OR balance >= 0),
    CHECK (status <> 'CLOSED' OR balance = 0)
);
CREATE UNIQUE INDEX uq_single_system_account ON accounts(type) WHERE type = 'SYSTEM';
CREATE INDEX ix_accounts_owner ON accounts(owner_id, created_at DESC, id DESC);
INSERT INTO accounts(id, type, status) VALUES ('00000000-0000-0000-0000-000000000001', 'SYSTEM', 'ACTIVE');

CREATE TABLE journals (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('TRANSFER', 'DEMO_DEPOSIT')),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, currency)
);
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    journal_id UUID NOT NULL,
    account_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (journal_id, currency) REFERENCES journals(id, currency),
    FOREIGN KEY (account_id, currency) REFERENCES accounts(id, currency),
    UNIQUE (journal_id, side),
    UNIQUE (journal_id, account_id)
);
CREATE INDEX ix_ledger_account_time ON ledger_entries(account_id, created_at DESC, id DESC);

CREATE TABLE demo_deposits (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    journal_id UUID NOT NULL UNIQUE REFERENCES journals(id),
    admin_id UUID NOT NULL REFERENCES app_users(id),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount <= 1000.00),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE FUNCTION prevent_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Immutable records cannot be updated or deleted' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_journals BEFORE UPDATE OR DELETE ON journals FOR EACH ROW EXECUTE FUNCTION prevent_mutation();
CREATE TRIGGER immutable_entries BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION prevent_mutation();
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION prevent_mutation();
CREATE TRIGGER immutable_demo_deposits BEFORE UPDATE OR DELETE ON demo_deposits FOR EACH ROW EXECUTE FUNCTION prevent_mutation();

CREATE FUNCTION verify_journal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    journal UUID;
    entry_count BIGINT;
    net NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'journals' THEN journal := NEW.id; ELSE journal := NEW.journal_id; END IF;
    SELECT count(*), coalesce(sum(CASE WHEN side = 'CREDIT' THEN amount ELSE -amount END), 0)
      INTO entry_count, net FROM ledger_entries WHERE journal_id = journal;
    IF entry_count <> 2 OR net <> 0 THEN
        RAISE EXCEPTION 'Journal requires exactly two balanced entries' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER balanced_journal AFTER INSERT ON journals DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_journal();
CREATE CONSTRAINT TRIGGER balanced_entries AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_journal();

CREATE FUNCTION verify_account_balance() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    account UUID;
    cached NUMERIC;
    ledger NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'accounts' THEN account := NEW.id; ELSE account := NEW.account_id; END IF;
    SELECT balance INTO cached FROM accounts WHERE id = account;
    SELECT coalesce(sum(CASE WHEN side = 'CREDIT' THEN amount ELSE -amount END), 0)
      INTO ledger FROM ledger_entries WHERE account_id = account;
    IF cached IS DISTINCT FROM ledger THEN
        RAISE EXCEPTION 'Account balance must reconcile with ledger' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER reconciled_account AFTER INSERT OR UPDATE ON accounts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_account_balance();
CREATE CONSTRAINT TRIGGER reconciled_entries AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_account_balance();
