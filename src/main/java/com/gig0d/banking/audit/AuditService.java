package com.gig0d.banking.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void record(UUID actor, String action, UUID resource, String outcome) {
        jdbc.update("INSERT INTO audit_events(id,actor_id,action,resource_id,outcome) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), actor, action, resource, outcome);
    }
}
