package com.gig0d.banking.history;

import com.gig0d.banking.account.AccountService;
import com.gig0d.banking.shared.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static com.gig0d.banking.history.StatementRepository.*;

@Service
public class StatementService {
    public record Statement(AccountService.AccountResponse account, BigDecimal ledgerBalance, PageResponse<Movement> movements) {}
    private final AccountService accounts;
    private final StatementRepository statements;
    public StatementService(AccountService accounts, StatementRepository statements) { this.accounts = accounts; this.statements = statements; }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Statement get(UUID customer, UUID account, Instant from, Instant to, MovementType type, Direction direction, int page, int size) {
        if (from != null && to != null && !from.isBefore(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from must precede to; to is exclusive.");
        AccountService.AccountResponse summary = accounts.get(customer, account);
        return new Statement(summary, statements.balance(account), statements.history(account, from, to, type, direction, page, size));
    }
}
