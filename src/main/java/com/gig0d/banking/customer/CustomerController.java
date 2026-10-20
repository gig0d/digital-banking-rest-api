package com.gig0d.banking.customer;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.gig0d.banking.customer.CustomerService.*;

@RestController
@RequestMapping("/api/v1/customers/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }
    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) { return service.get(UUID.fromString(jwt.getSubject())); }
    @PatchMapping
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfile request) {
        return service.update(UUID.fromString(jwt.getSubject()), request);
    }
}
