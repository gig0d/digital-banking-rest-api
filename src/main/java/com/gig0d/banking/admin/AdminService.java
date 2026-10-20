package com.gig0d.banking.admin;

import com.gig0d.banking.account.*;
import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdminService {
    public record Summary(long customers, long activeAccounts, long frozenAccounts, long closedAccounts,
            long successfulTransfers, long rejectedTransfers, long unreconciledAccounts) {}
    public record AuditEvent(UUID id, UUID actorId, String action, UUID resourceId, String outcome, Instant occurredAt) {}
    private final AccountRepository accounts;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    public AdminService(AccountRepository accounts, AuditService audit, JdbcTemplate jdbc) { this.accounts = accounts; this.audit = audit; this.jdbc = jdbc; }
    @Transactional
    public AccountService.AccountResponse changeStatus(UUID admin, UUID id, Account.Status status) {
        if (status != Account.Status.ACTIVE && status != Account.Status.FROZEN)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_STATE", "Administrators can freeze or activate accounts.");
        Account account = accounts.lockById(id).orElseThrow(ApiException::notFound);
        if (account.getType() == Account.Type.SYSTEM) throw ApiException.notFound();
        account.changeStatus(status);
        audit.record(admin, "ACCOUNT_STATUS_CHANGED", id, status.name());
        return AccountService.map(account);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary() {
        return new Summary(count("SELECT count(*) FROM app_users WHERE role='CUSTOMER'"),
                count("SELECT count(*) FROM accounts WHERE type<>'SYSTEM' AND status='ACTIVE'"),
                count("SELECT count(*) FROM accounts WHERE type<>'SYSTEM' AND status='FROZEN'"),
                count("SELECT count(*) FROM accounts WHERE type<>'SYSTEM' AND status='CLOSED'"),
                count("SELECT count(*) FROM transfers WHERE status='SUCCEEDED'"),
                count("SELECT count(*) FROM transfers WHERE status='REJECTED'"),
                count("SELECT count(*) FROM accounts a WHERE balance<>(SELECT coalesce(sum(CASE WHEN side='CREDIT' THEN amount ELSE -amount END),0) FROM ledger_entries e WHERE e.account_id=a.id)"));
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AuditEvent> auditEvents(int page, int size) {
        long total = count("SELECT count(*) FROM audit_events");
        var events = jdbc.query("SELECT id,actor_id,action,resource_id,outcome,occurred_at FROM audit_events ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new AuditEvent(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant()), size, (long) page * size);
        return new PageResponse<>(events, page, size, total, (int) ((total + size - 1) / size));
    }
}
