package com.gig0d.banking.demo;

import com.gig0d.banking.account.*;
import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.ledger.LedgerService;
import com.gig0d.banking.shared.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@Profile("demo")
@ConditionalOnProperty(name = "banking.demo.enabled", havingValue = "true")
public class DemoFundingService {
    public static final UUID CLEARING_ACCOUNT = new UUID(0, 1);
    public record FundingResponse(UUID referenceId, UUID accountId, BigDecimal amount, String currency) {}
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    public DemoFundingService(AccountRepository accounts, LedgerService ledger, AuditService audit, JdbcTemplate jdbc) {
        this.accounts = accounts; this.ledger = ledger; this.audit = audit; this.jdbc = jdbc;
    }
    @Transactional(timeout = 15)
    public FundingResponse fund(UUID admin, UUID id, BigDecimal input) {
        BigDecimal amount = Money.amount(input);
        if (amount.compareTo(new BigDecimal("1000.00")) > 0)
            throw ApiException.conflict("DEMO_FUNDING_LIMIT", "Demo funding is limited to 1000.00 USD once per account.");
        // All money writers use the same UUID ordering, including the clearing account.
        Account first = accounts.lockById(CLEARING_ACCOUNT.compareTo(id) < 0 ? CLEARING_ACCOUNT : id).orElseThrow(ApiException::notFound);
        Account second = accounts.lockById(CLEARING_ACCOUNT.compareTo(id) < 0 ? id : CLEARING_ACCOUNT).orElseThrow(ApiException::notFound);
        Account account = first.getId().equals(id) ? first : second;
        Account clearing = first.getId().equals(CLEARING_ACCOUNT) ? first : second;
        if (account.getType() == Account.Type.SYSTEM) throw ApiException.notFound();
        account.requireActive();
        if (account.isDemoFunded()) throw ApiException.conflict("ALREADY_FUNDED", "This account has already received its demo deposit.");
        UUID reference = ledger.post("DEMO_DEPOSIT", clearing, account, amount);
        jdbc.update("INSERT INTO demo_deposits(account_id,journal_id,admin_id,amount) VALUES (?,?,?,?)", id, reference, admin, amount);
        account.markDemoFunded();
        audit.record(admin, "DEMO_DEPOSIT", account.getId(), "SUCCESS");
        return new FundingResponse(reference, id, amount, "USD");
    }
}
