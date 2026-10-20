CREATE FUNCTION guard_account_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Accounts are retained for ledger history' USING ERRCODE='23514';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.owner_id IS DISTINCT FROM OLD.owner_id
        OR NEW.currency IS DISTINCT FROM OLD.currency OR NEW.type IS DISTINCT FROM OLD.type
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR (OLD.status='CLOSED' AND NEW.status<>'CLOSED') OR (OLD.demo_funded AND NOT NEW.demo_funded) THEN
        RAISE EXCEPTION 'Account identity and terminal state are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER account_identity BEFORE UPDATE OR DELETE ON accounts FOR EACH ROW EXECUTE FUNCTION guard_account_identity();

CREATE FUNCTION guard_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Idempotency records must be retained' USING ERRCODE='23514';
    END IF;
    IF NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.request_key IS DISTINCT FROM OLD.request_key
        OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR OLD.transfer_id IS NOT NULL THEN
        RAISE EXCEPTION 'Completed idempotency records are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_completed_idempotency BEFORE UPDATE OR DELETE ON idempotency_records
    FOR EACH ROW EXECUTE FUNCTION guard_idempotency();

CREATE FUNCTION verify_journal_reference() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.type='TRANSFER' AND NOT EXISTS (SELECT 1 FROM transfers WHERE journal_id=NEW.id AND status='SUCCEEDED') THEN
        RAISE EXCEPTION 'Transfer journal requires a successful transfer' USING ERRCODE='23514';
    END IF;
    IF NEW.type='DEMO_DEPOSIT' AND NOT EXISTS (
        SELECT 1 FROM demo_deposits f
        JOIN app_users u ON u.id=f.admin_id AND u.role='ADMIN'
        JOIN accounts a ON a.id=f.account_id AND a.type<>'SYSTEM' AND a.demo_funded
        JOIN ledger_entries c ON c.journal_id=f.journal_id AND c.side='CREDIT' AND c.account_id=f.account_id AND c.amount=f.amount
        JOIN ledger_entries d ON d.journal_id=f.journal_id AND d.side='DEBIT' AND d.amount=f.amount
        JOIN accounts s ON s.id=d.account_id AND s.type='SYSTEM'
        WHERE f.journal_id=NEW.id
    ) THEN
        RAISE EXCEPTION 'Demo deposit must match its authorized funding record' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER journal_reference AFTER INSERT ON journals DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_journal_reference();

CREATE FUNCTION verify_idempotency_owner() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM transfers WHERE id=NEW.transfer_id AND customer_id=NEW.customer_id) THEN
        RAISE EXCEPTION 'Idempotency response must belong to customer' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER idempotency_owner AFTER UPDATE ON idempotency_records DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_idempotency_owner();
