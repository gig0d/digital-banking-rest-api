package com.gig0d.banking.admin;

import com.gig0d.banking.account.*;
import com.gig0d.banking.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    public record StatusRequest(@NotNull Account.Status status) {}
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }
    @GetMapping("/summary")
    public AdminService.Summary summary() { return service.summary(); }
    @PatchMapping("/accounts/{id}/status")
    public AccountService.AccountResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(UUID.fromString(jwt.getSubject()), id, request.status());
    }
    @GetMapping("/audit-events")
    public PageResponse<AdminService.AuditEvent> audit(@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.auditEvents(page, size); }
}
