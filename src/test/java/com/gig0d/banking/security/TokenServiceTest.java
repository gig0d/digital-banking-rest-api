package com.gig0d.banking.security;

import com.gig0d.banking.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    private final SecurityConfiguration config = new SecurityConfiguration();
    private final JwtSettings settings = settings();
    private final SecretKey key = config.jwtKey(settings);
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, settings);

    private static JwtSettings settings() {
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        return new JwtSettings("urn:test:issuer", "test-api", Base64.getEncoder().encodeToString(secret), Duration.ofMinutes(15));
    }
    private JwtClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of(settings.audience()))
                .subject(UUID.randomUUID().toString()).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(900));
    }
    private String sign(JwtClaimsSet claims, MacAlgorithm algorithm) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }
    @Test
    void issuedTokenContainsOnlyIdentityAndAuthorizationClaims() {
        User user = new User("alice@example.test", "hash", User.Role.CUSTOMER);
        Jwt jwt = decoder.decode(new TokenService(encoder, settings).issue(user).accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "email", "balance");
    }
    @Test
    void rejectsWrongSignature() {
        JwtEncoder other = config.jwtEncoder(config.jwtKey(settings()));
        String token = other.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims().build())).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test
    void rejectsWrongIssuerAudienceAndMissingExpiration() {
        for (JwtClaimsSet invalid : List.of(claims().issuer("attacker").build(), claims().audience(List.of("other")).build(),
                JwtClaimsSet.builder().issuer(settings.issuer()).subject(UUID.randomUUID().toString()).build())) {
            assertThatThrownBy(() -> decoder.decode(sign(invalid, MacAlgorithm.HS256))).isInstanceOf(JwtException.class);
        }
    }
    @Test
    void rejectsExpiredAndNotYetValidTokens() {
        Instant now = Instant.now();
        for (JwtClaimsSet invalid : List.of(claims().issuedAt(now.minusSeconds(1800)).notBefore(now.minusSeconds(1800))
                .expiresAt(now.minusSeconds(900)).build(), claims().issuedAt(now.plusSeconds(120))
                .notBefore(now.plusSeconds(120)).expiresAt(now.plusSeconds(900)).build())) {
            assertThatThrownBy(() -> decoder.decode(sign(invalid, MacAlgorithm.HS256))).isInstanceOf(JwtException.class);
        }
    }
    @Test
    void rejectsMalformedSubjectAndAlgorithmConfusion() {
        assertThatThrownBy(() -> decoder.decode(sign(claims().subject("not-a-uuid").build(), MacAlgorithm.HS256))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.")).isInstanceOf(JwtException.class);
    }
    @Test
    void failsClosedForShortSigningKeys() {
        assertThatThrownBy(() -> config.jwtKey(new JwtSettings("issuer", "audience", "c2hvcnQ=", Duration.ofMinutes(15))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
