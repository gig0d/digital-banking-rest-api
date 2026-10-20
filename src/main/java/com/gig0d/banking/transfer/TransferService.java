package com.gig0d.banking.transfer;

import com.gig0d.banking.account.*;
import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.ledger.LedgerService;
import com.gig0d.banking.shared.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import static com.gig0d.banking.transfer.TransferDtos.*;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final IdempotencyRepository idempotency;
    private final LedgerService ledger;
    private final AuditService audit;
    public TransferService(AccountRepository accounts, TransferRepository transfers, IdempotencyRepository idempotency,
                           LedgerService ledger, AuditService audit) {
        this.accounts = accounts; this.transfers = transfers; this.idempotency = idempotency; this.ledger = ledger; this.audit = audit;
    }

    @Transactional(timeout = 15)
    public TransferResponse transfer(UUID customer, String key, TransferRequest request) {
        BigDecimal amount = Money.amount(request.amount());
        if (request.sourceAccountId() == null || request.destinationAccountId() == null
                || request.sourceAccountId().equals(request.destinationAccountId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISTINCT_ACCOUNTS_REQUIRED", "Choose two distinct account IDs.");
        IdempotencyRepository.Record record = idempotency.lock(customer, key, fingerprint(request, amount));
        if (!record.hash().equals(fingerprint(request, amount)))
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "This idempotency key was used with a different payload.");
        if (record.transfer() != null) return TransferResponse.from(transfers.findByIdAndCustomerId(record.transfer(), customer).orElseThrow(ApiException::notFound));

        UUID sourceId = request.sourceAccountId(), destinationId = request.destinationAccountId();
        Account first = accounts.lockById(sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId).orElseThrow(ApiException::notFound);
        Account second = accounts.lockById(sourceId.compareTo(destinationId) < 0 ? destinationId : sourceId).orElseThrow(ApiException::notFound);
        Account source = first.getId().equals(sourceId) ? first : second;
        Account destination = first.getId().equals(destinationId) ? first : second;
        if (!customer.equals(source.getOwnerId()) || destination.getType() == Account.Type.SYSTEM) throw ApiException.notFound();

        String rejection = rejection(source, destination, amount);
        UUID journal = rejection == null ? ledger.post("TRANSFER", source, destination, amount) : null;
        Transfer transfer = transfers.saveAndFlush(new Transfer(customer, sourceId, destinationId, amount, rejection, journal));
        idempotency.complete(customer, key, transfer.getId());
        audit.record(customer, "TRANSFER", transfer.getId(), transfer.getStatus().name());
        return TransferResponse.from(transfer);
    }
    private String rejection(Account source, Account destination, BigDecimal amount) {
        if (!source.getCurrency().equals(destination.getCurrency())) return "CURRENCY_MISMATCH";
        if (source.getStatus() != Account.Status.ACTIVE || destination.getStatus() != Account.Status.ACTIVE) return "ACCOUNT_NOT_ACTIVE";
        if (source.getBalance().compareTo(amount) < 0) return "INSUFFICIENT_FUNDS";
        return null;
    }
    static String fingerprint(TransferRequest request, BigDecimal amount) {
        String canonical = request.sourceAccountId() + "|" + request.destinationAccountId() + "|USD|" + amount.toPlainString();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is required by the JDK.", ex); }
    }
    @Transactional(readOnly = true)
    public TransferResponse get(UUID customer, UUID id) {
        return TransferResponse.from(transfers.findByIdAndCustomerId(id, customer).orElseThrow(ApiException::notFound));
    }
}
