package com.gig0d.banking.security;

import com.gig0d.banking.identity.User;
import com.gig0d.banking.identity.AuthDtos.TokenResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtSettings settings;
    public TokenService(JwtEncoder encoder, JwtSettings settings) { this.encoder = encoder; this.settings = settings; }
    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of(settings.audience()))
                .subject(user.getId().toString()).issuedAt(now).notBefore(now).expiresAt(now.plus(settings.ttl()))
                .id(UUID.randomUUID().toString()).claim("roles", List.of(user.getRole().name())).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", settings.ttl().toSeconds());
    }
}
