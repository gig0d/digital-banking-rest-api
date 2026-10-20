package com.gig0d.banking.customer;

import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Service
public class CustomerService {
    public record ProfileResponse(UUID id, String fullName, String phone) {}
    public record UpdateProfile(@NotBlank @Size(max = 120) String fullName,
            @Size(max = 30) @Pattern(regexp = "[+0-9 ()-]*") String phone) {}
    private final CustomerRepository customers;
    private final AuditService audit;
    public CustomerService(CustomerRepository customers, AuditService audit) { this.customers = customers; this.audit = audit; }
    @Transactional(readOnly = true)
    public ProfileResponse get(UUID user) { return map(find(user)); }
    @Transactional
    public ProfileResponse update(UUID user, UpdateProfile update) {
        CustomerProfile profile = find(user);
        profile.update(update.fullName(), update.phone());
        audit.record(user, "PROFILE_UPDATED", profile.getId(), "SUCCESS");
        return map(profile);
    }
    private CustomerProfile find(UUID user) { return customers.findByUserId(user).orElseThrow(ApiException::notFound); }
    private ProfileResponse map(CustomerProfile profile) { return new ProfileResponse(profile.getId(), profile.getFullName(), profile.getPhone()); }
}
