package com.gig0d.banking.identity;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.gig0d.banking.identity.AuthDtos.*;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public RegisteredCustomer register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }
}
