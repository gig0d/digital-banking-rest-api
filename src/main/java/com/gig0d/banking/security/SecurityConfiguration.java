package com.gig0d.banking.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtSettings.class)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtKey(JwtSettings settings) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(settings.secret()); }
        catch (Exception ex) { throw new IllegalArgumentException("JWT_SECRET must contain Base64-encoded random bytes."); }
        if (decoded.length < 32) throw new IllegalArgumentException("JWT_SECRET requires at least 32 decoded bytes.");
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, JwtSettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean validSubject;
            try { validSubject = UUID.fromString(jwt.getSubject()).toString().equals(jwt.getSubject()); }
            catch (Exception ex) { validSubject = false; }
            boolean valid = validSubject && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getNotBefore() != null && jwt.getAudience().contains(settings.audience())
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(settings.ttl()) <= 0
                    && !jwt.getIssuedAt().isAfter(java.time.Instant.now().plusSeconds(30));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required token claims are invalid.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(settings.issuer()), claims));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            SecurityProblems.write(response, 401, "Unauthorized", "UNAUTHENTICATED", "A valid bearer token is required.");
                        })
                        .accessDeniedHandler((request, response, ex) -> SecurityProblems.write(response, 403,
                                "Forbidden", "FORBIDDEN", "This operation is not permitted.")))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                            SecurityProblems.write(response, 401, "Unauthorized", "INVALID_TOKEN", "The bearer token is invalid or expired.");
                        }))
                .addFilterBefore(new AuthRateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${banking.cors-origins:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        if (!origins.isBlank()) {
            List<String> allowed = Arrays.stream(origins.split(",")).map(String::strip).toList();
            if (allowed.stream().anyMatch(value -> value.contains("*") || !(value.startsWith("http://") || value.startsWith("https://"))))
                throw new IllegalArgumentException("CORS_ORIGINS must contain explicit HTTP(S) origins without wildcards.");
            config.setAllowedOrigins(allowed);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
