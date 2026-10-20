package com.gig0d.banking.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class User {
    public enum Role { CUSTOMER, ADMIN }
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Role role;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected User() {}
    public User(String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.email = normalize(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }
    public static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
