package com.gig0d.banking.transfer;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransferDtos {
    private TransferDtos() {}
    public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal amount) {}
    public record TransferResponse(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
            String currency, Transfer.Status status, String rejectionCode, UUID referenceId, Instant createdAt) {
        public static TransferResponse from(Transfer t) {
            return new TransferResponse(t.getId(), t.getSourceAccountId(), t.getDestinationAccountId(), t.getAmount(),
                    t.getCurrency(), t.getStatus(), t.getRejectionCode(), t.getJournalId(), t.getCreatedAt());
        }
    }
}
