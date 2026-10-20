package com.gig0d.banking.account;

import com.gig0d.banking.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.gig0d.banking.account.AccountService.AccountResponse;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountController {
    public record CreateAccount(@NotNull Account.Type type) {}
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccount request) {
        return service.create(UUID.fromString(jwt.getSubject()), request.type());
    }
    @GetMapping
    public PageResponse<AccountResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(UUID.fromString(jwt.getSubject()), page, size);
    }
    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(UUID.fromString(jwt.getSubject()), id);
    }
    @PostMapping("/{id}/closure")
    public AccountResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.close(UUID.fromString(jwt.getSubject()), id);
    }
}
