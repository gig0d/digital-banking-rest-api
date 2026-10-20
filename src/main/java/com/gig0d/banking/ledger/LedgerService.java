package com.gig0d.banking.ledger;

import com.gig0d.banking.account.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class LedgerService {
    private final JdbcTemplate jdbc;
    public LedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID post(String type, Account debit, Account credit, BigDecimal amount) {
        if (debit.getId().equals(credit.getId()) || !debit.getCurrency().equals(credit.getCurrency()))
            throw new IllegalArgumentException("A journal requires distinct accounts of the same currency.");
        UUID journal = UUID.randomUUID();
        jdbc.update("INSERT INTO journals(id,type,currency) VALUES (?,?,?)", journal, type, debit.getCurrency());
        entry(journal, debit, "DEBIT", amount);
        entry(journal, credit, "CREDIT", amount);
        debit.debit(amount);
        credit.credit(amount);
        return journal;
    }
    private void entry(UUID journal, Account account, String side, BigDecimal amount) {
        jdbc.update("INSERT INTO ledger_entries(id,journal_id,account_id,currency,side,amount) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), journal, account.getId(), account.getCurrency(), side, amount);
    }
}
