package com.gig0d.banking.history;

import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
import static com.gig0d.banking.history.StatementRepository.*;

@RestController
@RequestMapping("/api/v1/accounts/{id}/statement")
@PreAuthorize("hasRole('CUSTOMER')")
public class StatementController {
    private final StatementService service;
    public StatementController(StatementService service) { this.service = service; }
    @GetMapping
    public StatementService.Statement get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) MovementType type, @RequestParam(defaultValue = "DESC") Direction direction,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.get(UUID.fromString(jwt.getSubject()), id, from, to, type, direction, page, size);
    }
}
