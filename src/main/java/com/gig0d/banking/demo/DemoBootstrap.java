package com.gig0d.banking.demo;

import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.identity.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo")
@ConditionalOnProperty(name = "banking.demo.enabled", havingValue = "true")
public class DemoBootstrap {
    private final DemoSettings settings;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    public DemoBootstrap(DemoSettings settings, UserRepository users, PasswordEncoder passwords, AuditService audit) {
        this.settings = settings; this.users = users; this.passwords = passwords; this.audit = audit;
    }
    @Transactional
    public void bootstrap() {
        String email = settings.adminEmail();
        if (email == null || email.isBlank()) return;
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("DEMO_ADMIN_EMAIL must be a valid email address.");
        User existing = users.findByEmail(User.normalize(email)).orElse(null);
        if (existing != null) {
            if (existing.getRole() != User.Role.ADMIN) throw new IllegalStateException("Demo administrator email belongs to a customer. Choose another email.");
            return; // Never reset passwords or promote existing customers at startup.
        }
        String password = settings.adminPassword();
        if (password == null || password.length() < 12 || password.length() > 72 || !password.matches("[\\x20-\\x7E]+"))
            throw new IllegalArgumentException("A new demo administrator requires a 12-72 character ASCII password.");
        User admin = users.saveAndFlush(new User(email, passwords.encode(password), User.Role.ADMIN));
        audit.record(admin.getId(), "DEMO_ADMIN_BOOTSTRAPPED", admin.getId(), "SUCCESS");
    }
}
