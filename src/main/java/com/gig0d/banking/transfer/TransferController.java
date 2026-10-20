package com.gig0d.banking.transfer;

import com.gig0d.banking.shared.ApiErrors;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.gig0d.banking.transfer.TransferDtos.*;

@RestController
@RequestMapping("/api/v1/transfers")
@PreAuthorize("hasRole('CUSTOMER')")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<?> transfer(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = service.transfer(UUID.fromString(jwt.getSubject()), key, request);
        if (response.status() == Transfer.Status.REJECTED) {
            ProblemDetail problem = ApiErrors.problem(HttpStatus.CONFLICT, response.rejectionCode(),
                    "The transfer was rejected by account status or funds rules. Retrieve the transfer for its persisted outcome.");
            problem.setProperty("transferId", response.id());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
        return ResponseEntity.ok(response);
    }
    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(UUID.fromString(jwt.getSubject()), id);
    }
}
