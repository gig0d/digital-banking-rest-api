package com.gig0d.banking.identity;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 12, max = 72) @Pattern(regexp = "[\\x20-\\x7E]+") String password,
            @NotBlank @Size(max = 120) String fullName) {}
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 1, max = 72) @Pattern(regexp = "[\\x20-\\x7E]+") String password) {}
    public record RegisteredCustomer(UUID userId, UUID customerId, String email, String fullName) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
