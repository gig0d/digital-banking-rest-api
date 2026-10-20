package com.gig0d.banking.transfer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {
    public enum Status { SUCCEEDED, REJECTED }
    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "source_account_id", nullable = false) private UUID sourceAccountId;
    @Column(name = "destination_account_id", nullable = false) private UUID destinationAccountId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "rejection_code", length = 64) private String rejectionCode;
    @Column(name = "journal_id") private UUID journalId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Transfer() {}
    public Transfer(UUID customer, UUID source, UUID destination, BigDecimal amount, String rejection, UUID journal) {
        this.id = UUID.randomUUID(); this.customerId = customer; this.sourceAccountId = source;
        this.destinationAccountId = destination; this.currency = "USD"; this.amount = amount;
        this.status = rejection == null ? Status.SUCCEEDED : Status.REJECTED;
        this.rejectionCode = rejection; this.journalId = journal;
        this.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public String getRejectionCode() { return rejectionCode; }
    public UUID getJournalId() { return journalId; }
    public Instant getCreatedAt() { return createdAt; }
}
