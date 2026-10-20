package com.gig0d.banking.customer;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(name = "full_name", nullable = false, length = 120) private String fullName;
    @Column(length = 30) private String phone;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected CustomerProfile() {}
    public CustomerProfile(UUID userId, String fullName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.fullName = fullName.strip();
        this.createdAt = Instant.now();
    }
    public void update(String fullName, String phone) {
        this.fullName = fullName.strip();
        this.phone = phone == null ? null : phone.strip();
    }
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
}
