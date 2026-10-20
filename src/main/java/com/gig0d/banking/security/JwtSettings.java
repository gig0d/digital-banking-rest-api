package com.gig0d.banking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("banking.jwt")
public record JwtSettings(String issuer, String audience, String secret, Duration ttl) {
    public JwtSettings {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                || ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("JWT issuer, audience and a TTL of at most 30 minutes are required.");
        }
    }
}
