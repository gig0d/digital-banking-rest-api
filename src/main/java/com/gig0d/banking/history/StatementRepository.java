package com.gig0d.banking.history;

import com.gig0d.banking.shared.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Repository
public class StatementRepository {
    public enum MovementType { TRANSFER, DEMO_DEPOSIT }
    public enum Direction { ASC, DESC }
    public record Movement(UUID id, UUID referenceId, MovementType type, String side, BigDecimal amount, String currency, Instant occurredAt) {}
    private final JdbcTemplate jdbc;
    public StatementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public PageResponse<Movement> history(UUID account, Instant from, Instant to, MovementType type, Direction direction, int page, int size) {
        String filter = " FROM ledger_entries e JOIN journals j ON e.journal_id=j.id WHERE e.account_id=?";
        List<Object> args = new ArrayList<>(); args.add(account);
        if (from != null) { filter += " AND e.created_at>=?"; args.add(java.sql.Timestamp.from(from)); }
        if (to != null) { filter += " AND e.created_at<?"; args.add(java.sql.Timestamp.from(to)); }
        if (type != null) { filter += " AND j.type=?"; args.add(type.name()); }
        Long count = jdbc.queryForObject("SELECT count(*)" + filter, Long.class, args.toArray());
        String order = direction.name(); // Closed enum, never raw request text.
        String query = "SELECT e.id,e.journal_id,j.type,e.side,e.amount,e.currency,e.created_at" + filter
                + " ORDER BY e.created_at " + order + ",e.id " + order + " LIMIT ? OFFSET ?";
        args.add(size); args.add((long) page * size);
        List<Movement> rows = jdbc.query(query, (rs, row) -> new Movement(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                MovementType.valueOf(rs.getString(3)), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getTimestamp(7).toInstant()), args.toArray());
        long total = count == null ? 0 : count;
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
    public BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT coalesce(sum(CASE WHEN side='CREDIT' THEN amount ELSE -amount END),0) FROM ledger_entries WHERE account_id=?", BigDecimal.class, account);
    }
}
