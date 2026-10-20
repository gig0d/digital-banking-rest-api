package com.gig0d.banking.identity;

import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.customer.CustomerProfile;
import com.gig0d.banking.customer.CustomerRepository;
import com.gig0d.banking.security.TokenService;
import com.gig0d.banking.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.gig0d.banking.identity.AuthDtos.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(UserRepository users, CustomerRepository customers, PasswordEncoder passwords,
                       TokenService tokens, AuditService audit) {
        this.users = users; this.customers = customers; this.passwords = passwords;
        this.tokens = tokens; this.audit = audit;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public RegisteredCustomer register(RegisterRequest request) {
        if (users.findByEmail(User.normalize(request.email())).isPresent()) {
            throw ApiException.conflict("EMAIL_UNAVAILABLE", "This email address is unavailable.");
        }
        User user = users.saveAndFlush(new User(request.email(), passwords.encode(request.password()), User.Role.CUSTOMER));
        CustomerProfile profile = customers.save(new CustomerProfile(user.getId(), request.fullName()));
        audit.record(user.getId(), "CUSTOMER_REGISTERED", profile.getId(), "SUCCESS");
        return new RegisteredCustomer(user.getId(), profile.getId(), user.getEmail(), profile.getFullName());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(User.normalize(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            audit.record(null, "LOGIN", null, "REJECTED");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
        }
        audit.record(user.getId(), "LOGIN", user.getId(), "SUCCESS");
        return tokens.issue(user);
    }
}
