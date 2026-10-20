package com.gig0d.banking.transfer;

import com.gig0d.banking.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Repository
public class IdempotencyRepository {
    public record Record(String hash, UUID transfer) {}
    private final JdbcTemplate jdbc;
    public IdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public Record lock(UUID customer, String key, String hash) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,96}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key requires 8-96 letters, digits, dots, underscores, colons or hyphens.");
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        // A conflicting INSERT waits for the other transaction. SELECT then sees its committed response.
        jdbc.update("INSERT INTO idempotency_records(customer_id,request_key,payload_hash) VALUES (?,?,?) ON CONFLICT DO NOTHING", customer, key, hash);
        return jdbc.queryForObject("SELECT payload_hash,transfer_id FROM idempotency_records WHERE customer_id=? AND request_key=? FOR UPDATE",
                (rs, row) -> new Record(rs.getString(1), rs.getObject(2, UUID.class)), customer, key);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID customer, String key, UUID transfer) {
        jdbc.update("UPDATE idempotency_records SET transfer_id=? WHERE customer_id=? AND request_key=?", transfer, customer, key);
    }
}
