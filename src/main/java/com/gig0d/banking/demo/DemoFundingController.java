package com.gig0d.banking.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@Profile("demo")
@ConditionalOnProperty(name = "banking.demo.enabled", havingValue = "true")
@RequestMapping("/api/v1/admin/demo-deposits")
@PreAuthorize("hasRole('ADMIN')")
public class DemoFundingController {
    public record FundingRequest(@NotNull UUID accountId, @NotNull @DecimalMin("0.01") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2) BigDecimal amount) {}
    private final DemoFundingService funding;
    public DemoFundingController(DemoFundingService funding) { this.funding = funding; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DemoFundingService.FundingResponse fund(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FundingRequest request) {
        return funding.fund(UUID.fromString(jwt.getSubject()), request.accountId(), request.amount());
    }
}
