package com.gig0d.banking.account;

import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.shared.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {
    public record AccountResponse(UUID id, Account.Type type, String currency, Account.Status status, BigDecimal balance, Instant createdAt) {}
    private final AccountRepository accounts;
    private final AuditService audit;
    public AccountService(AccountRepository accounts, AuditService audit) { this.accounts = accounts; this.audit = audit; }
    public static AccountResponse map(Account a) {
        return new AccountResponse(a.getId(), a.getType(), a.getCurrency(), a.getStatus(), a.getBalance(), a.getCreatedAt());
    }
    @Transactional
    public AccountResponse create(UUID owner, Account.Type type) {
        if (type == Account.Type.SYSTEM) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_TYPE", "Use CHECKING or SAVINGS.");
        Account account = accounts.save(new Account(owner, type));
        audit.record(owner, "ACCOUNT_CREATED", account.getId(), "SUCCESS");
        return map(account);
    }
    @Transactional(readOnly = true)
    public AccountResponse get(UUID owner, UUID id) { return map(owned(owner, id)); }
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(UUID owner, int page, int size) {
        return PageResponse.from(accounts.findByOwnerId(owner, PageRequest.of(page, size, Sort.by("createdAt", "id").descending())).map(AccountService::map));
    }
    public Account owned(UUID owner, UUID id) { return accounts.findByIdAndOwnerId(id, owner).orElseThrow(ApiException::notFound); }
    @Transactional
    public AccountResponse close(UUID owner, UUID id) {
        Account account = accounts.lockById(id).orElseThrow(ApiException::notFound);
        if (!owner.equals(account.getOwnerId())) throw ApiException.notFound();
        account.changeStatus(Account.Status.CLOSED);
        audit.record(owner, "ACCOUNT_CLOSED", id, "SUCCESS");
        return map(account);
    }
}
