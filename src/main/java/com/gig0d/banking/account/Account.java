package com.gig0d.banking.account;

import com.gig0d.banking.shared.ApiException;
import com.gig0d.banking.shared.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    public enum Type { CHECKING, SAVINGS, SYSTEM }
    public enum Status { ACTIVE, FROZEN, CLOSED }
    @Id private UUID id;
    @Column(name = "owner_id") private UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Type type;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Column(name = "demo_funded", nullable = false) private boolean demoFunded;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Account() {}
    public Account(UUID ownerId, Type type) {
        if (type == null || type == Type.SYSTEM || ownerId == null) throw new IllegalArgumentException("Customer account type and owner are required.");
        this.id = UUID.randomUUID(); this.ownerId = ownerId; this.type = type;
        this.currency = "USD"; this.status = Status.ACTIVE;
        this.balance = new BigDecimal("0.00"); this.createdAt = Instant.now();
    }
    public void requireActive() {
        if (status != Status.ACTIVE) throw ApiException.conflict("ACCOUNT_NOT_ACTIVE", "Both accounts must be active.");
    }
    public void debit(BigDecimal amount) {
        amount = Money.amount(amount);
        if (type != Type.SYSTEM && balance.compareTo(amount) < 0)
            throw ApiException.conflict("INSUFFICIENT_FUNDS", "The source account has insufficient funds.");
        balance = balance.subtract(amount);
    }
    public void credit(BigDecimal amount) { balance = balance.add(Money.amount(amount)); }
    public void markDemoFunded() { demoFunded = true; }
    public void changeStatus(Status next) {
        if (type == Type.SYSTEM || status == Status.CLOSED || next == null)
            throw ApiException.conflict("INVALID_ACCOUNT_STATE", "This account state transition is not permitted.");
        if (next == Status.CLOSED && (balance.signum() != 0 || status != Status.ACTIVE))
            throw ApiException.conflict("ACCOUNT_CANNOT_CLOSE", "Only an active account with zero balance can close.");
        status = next;
    }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isDemoFunded() { return demoFunded; }
}
