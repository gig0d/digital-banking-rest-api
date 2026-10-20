package com.gig0d.banking.integration;

import com.gig0d.banking.account.*;
import com.gig0d.banking.admin.AdminService;
import com.gig0d.banking.audit.AuditService;
import com.gig0d.banking.demo.DemoFundingService;
import com.gig0d.banking.identity.*;
import com.gig0d.banking.transfer.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static com.gig0d.banking.transfer.TransferDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("demo")
class TransferIT extends PostgresSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccountService accounts;
    @Autowired TransferService transfers;
    @Autowired DemoFundingService funding;
    @Autowired AdminService administration;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accountRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean AuditService audit;

    private User user(User.Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "not-used-by-jwt-fixtures", role));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(User user) {
        return jwt().jwt(claims -> claims.subject(user.getId().toString())).authorities(createAuthorityList("ROLE_" + user.getRole().name()));
    }
    private UUID account(User owner) { return accounts.create(owner.getId(), Account.Type.CHECKING).id(); }
    private UUID funded(User owner, User admin) {
        UUID id = account(owner); funding.fund(admin.getId(), id, new BigDecimal("100.00")); return id;
    }
    private TransferRequest request(UUID from, UUID to, String amount) { return new TransferRequest(from, to, new BigDecimal(amount)); }
    private String key() { return UUID.randomUUID().toString(); }
    private BigDecimal balance(User user, UUID id) { return accounts.get(user.getId(), id).balance(); }
    private long count(String table, String column, UUID id) {
        // Identifiers are test constants; all values are bound parameters.
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }
    private <T> List<T> race(int participants, IntFunction<T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(participants), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(participants)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < participants; i++) {
                int index = i;
                futures.add(executor.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return action.apply(index); }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
    @Test
    void transferAtomicallyPostsTwoEntriesAndReconcilesBothAccounts() {
        User alice = user(User.Role.CUSTOMER), bob = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(bob);
        TransferResponse result = transfers.transfer(alice.getId(), key(), request(source, destination, "25.00"));
        assertThat(result.status()).isEqualTo(Transfer.Status.SUCCEEDED);
        assertThat(balance(alice, source)).isEqualByComparingTo("75.00");
        assertThat(balance(bob, destination)).isEqualByComparingTo("25.00");
        assertThat(count("ledger_entries", "journal_id", result.referenceId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(CASE WHEN side='CREDIT' THEN amount ELSE -amount END) FROM ledger_entries WHERE journal_id=?", BigDecimal.class, result.referenceId())).isEqualByComparingTo("0.00");
        assertThat(administration.summary().unreconciledAccounts()).isZero();
    }
    @Test
    void insufficientFundsIsPersistedAndDuplicateReplaysTheOriginalRejection() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = account(alice), destination = account(alice); String key = key();
        TransferRequest request = request(source, destination, "1.00");
        TransferResponse rejected = transfers.transfer(alice.getId(), key, request);
        assertThat(rejected.status()).isEqualTo(Transfer.Status.REJECTED);
        assertThat(rejected.rejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(rejected.referenceId()).isNull();
        funding.fund(admin.getId(), source, new BigDecimal("100.00"));
        assertThat(transfers.transfer(alice.getId(), key, request)).isEqualTo(rejected);
        assertThat(balance(alice, source)).isEqualByComparingTo("100.00");
        assertThat(count("ledger_entries", "account_id", destination)).isZero();
    }
    @Test
    void frozenSourcesFrozenDestinationsAndClosedAccountsAreRejected() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice);
        administration.changeStatus(admin.getId(), source, Account.Status.FROZEN);
        assertThat(transfers.transfer(alice.getId(), key(), request(source, destination, "1.00")).rejectionCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        administration.changeStatus(admin.getId(), source, Account.Status.ACTIVE);
        administration.changeStatus(admin.getId(), destination, Account.Status.FROZEN);
        assertThat(transfers.transfer(alice.getId(), key(), request(source, destination, "1.00")).rejectionCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        administration.changeStatus(admin.getId(), destination, Account.Status.ACTIVE);
        accounts.close(alice.getId(), destination);
        assertThat(transfers.transfer(alice.getId(), key(), request(source, destination, "1.00")).rejectionCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(balance(alice, source)).isEqualByComparingTo("100.00");
    }
    @Test
    void idempotencyNormalizesMoneyAndRejectsPayloadChanges() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice); String key = key();
        TransferResponse initial = transfers.transfer(alice.getId(), key, request(source, destination, "1"));
        assertThat(transfers.transfer(alice.getId(), key, request(source, destination, "1.00"))).isEqualTo(initial);
        assertThatThrownBy(() -> transfers.transfer(alice.getId(), key, request(source, destination, "2.00"))).hasMessageContaining("different payload");
        assertThat(balance(alice, source)).isEqualByComparingTo("99.00");
    }
    @Test
    void concurrentDuplicateRequestsPostExactlyOneMovement() throws Exception {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice); String key = key();
        List<TransferResponse> results = race(6, i -> transfers.transfer(alice.getId(), key, request(source, destination, "10.00")));
        assertThat(results.stream().map(TransferResponse::id).distinct().count()).isEqualTo(1);
        assertThat(results).allMatch(result -> result.status() == Transfer.Status.SUCCEEDED);
        assertThat(count("transfers", "source_account_id", source)).isEqualTo(1);
        assertThat(balance(alice, source)).isEqualByComparingTo("90.00");
        assertThat(balance(alice, destination)).isEqualByComparingTo("10.00");
    }
    @Test
    void concurrentConflictingPayloadsHaveOneWinnerAndKeysAreScopedToCustomers() throws Exception {
        User alice = user(User.Role.CUSTOMER), bob = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID a = funded(alice, admin), b = funded(bob, admin); String key = key();
        List<String> outcomes = race(2, i -> {
            try { transfers.transfer(alice.getId(), key, request(a,b,i == 0 ? "10.00" : "11.00")); return "SUCCEEDED"; }
            catch (com.gig0d.banking.shared.ApiException ex) { return ex.code(); }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCEEDED", "IDEMPOTENCY_CONFLICT");
        assertThat(transfers.transfer(bob.getId(), key, request(b,a,"1.00")).status()).isEqualTo(Transfer.Status.SUCCEEDED);
        assertThat(count("idempotency_records", "customer_id", alice.getId())).isEqualTo(1);
        assertThat(count("idempotency_records", "customer_id", bob.getId())).isEqualTo(1);
    }
    @Test
    void lockTimeoutReturnsRetryableErrorAndDoesNotConsumeTheKey() throws Exception {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice); String key = key();
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                accountRepository.lockById(source).orElseThrow(); locked.countDown();
                try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            String body = "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination + "\",\"amount\":1.00}";
            try {
                mvc.perform(post("/api/v1/transfers").with(as(alice)).header("Idempotency-Key", key).contentType("application/json").content(body))
                        .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
                        .andExpect(jsonPath("$.code").value("RETRY_REQUEST"));
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
        }
        assertThat(count("idempotency_records", "customer_id", alice.getId())).isZero();
        assertThat(balance(alice, source)).isEqualByComparingTo("100.00");
        assertThat(transfers.transfer(alice.getId(), key, request(source, destination, "1.00")).status()).isEqualTo(Transfer.Status.SUCCEEDED);
    }
    @Test
    void concurrentSpendingCannotOverdrawAnAccount() throws Exception {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice);
        List<TransferResponse> results = race(2, i -> transfers.transfer(alice.getId(), key(), request(source, destination, "75.00")));
        assertThat(results).extracting(TransferResponse::status).containsExactlyInAnyOrder(Transfer.Status.SUCCEEDED, Transfer.Status.REJECTED);
        assertThat(balance(alice, source)).isEqualByComparingTo("25.00");
        assertThat(balance(alice, destination)).isEqualByComparingTo("75.00");
        assertThat(administration.summary().unreconciledAccounts()).isZero();
    }
    @Test
    void opposingTransfersUseConsistentLockOrdering() throws Exception {
        User alice = user(User.Role.CUSTOMER), bob = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID a = funded(alice, admin), b = funded(bob, admin);
        List<TransferResponse> results = race(2, i -> i == 0 ? transfers.transfer(alice.getId(), key(), request(a,b,"10.00"))
                : transfers.transfer(bob.getId(), key(), request(b,a,"10.00")));
        assertThat(results).allMatch(result -> result.status() == Transfer.Status.SUCCEEDED);
        assertThat(balance(alice, a)).isEqualByComparingTo("100.00");
        assertThat(balance(bob, b)).isEqualByComparingTo("100.00");
    }
    @Test
    void failureAfterFinancialWritesRollsBackBalancesLedgerTransferAndIdempotency() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice); String key = key();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).record(eq(alice.getId()), eq("TRANSFER"), any(), any());
        try {
            assertThatThrownBy(() -> transfers.transfer(alice.getId(), key, request(source, destination, "10.00")))
                    .isInstanceOf(IllegalStateException.class);
        } finally { reset(audit); }
        assertThat(balance(alice, source)).isEqualByComparingTo("100.00");
        assertThat(balance(alice, destination)).isEqualByComparingTo("0.00");
        assertThat(count("transfers", "source_account_id", source)).isZero();
        assertThat(count("ledger_entries", "account_id", destination)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE customer_id=? AND request_key=?", Long.class, alice.getId(), key)).isZero();
        assertThat(transfers.transfer(alice.getId(), key, request(source, destination, "10.00")).status()).isEqualTo(Transfer.Status.SUCCEEDED);
    }
    @Test
    void customersCannotDebitOrInspectOtherCustomersResources() throws Exception {
        User alice = user(User.Role.CUSTOMER), bob = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID a = funded(alice, admin), b = account(bob);
        String body = "{\"sourceAccountId\":\"" + a + "\",\"destinationAccountId\":\"" + b + "\",\"amount\":1.00}";
        mvc.perform(post("/api/v1/transfers").with(as(bob)).header("Idempotency-Key", key()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        TransferResponse transfer = transfers.transfer(alice.getId(), key(), request(a, b, "1.00"));
        mvc.perform(get("/api/v1/transfers/" + transfer.id()).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + a + "/statement").with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/audit-events").with(as(alice))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/summary").with(as(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.unreconciledAccounts").value(0));
    }
    @Test
    void invalidTransferRequestsReturnProblemDetailsWithoutFinancialWrites() throws Exception {
        User alice = user(User.Role.CUSTOMER); UUID source = account(alice), destination = account(alice);
        for (String amount : new String[]{"-1.00", "0", "0.001", "1000000.01"}) {
            String body = "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination + "\",\"amount\":" + amount + "}";
            mvc.perform(post("/api/v1/transfers").with(as(alice)).header("Idempotency-Key", key()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        String body = "{\"sourceAccountId\":\"" + source + "\",\"destinationAccountId\":\"" + destination + "\",\"amount\":1}";
        mvc.perform(post("/api/v1/transfers").with(as(alice)).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transfers").with(as(alice)).header("Idempotency-Key", "short").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        assertThat(count("transfers", "source_account_id", source)).isZero();
    }
    @Test
    void statementsFilterPaginateSortAndShowAuthoritativeBalance() throws Exception {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice);
        transfers.transfer(alice.getId(), key(), request(source, destination, "5.00"));
        String path = "/api/v1/accounts/" + source + "/statement";
        mvc.perform(get(path + "?size=1&direction=ASC").with(as(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.balance").value(95)).andExpect(jsonPath("$.ledgerBalance").value(95))
                .andExpect(jsonPath("$.movements.totalElements").value(2)).andExpect(jsonPath("$.movements.content[0].type").value("DEMO_DEPOSIT"));
        mvc.perform(get(path + "?type=TRANSFER").with(as(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.movements.totalElements").value(1)).andExpect(jsonPath("$.movements.content[0].side").value("DEBIT"));
        mvc.perform(get(path + "?to=2000-01-01T00:00:00Z").with(as(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.movements.totalElements").value(0));
        mvc.perform(get(path + "?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z").with(as(alice))).andExpect(status().isBadRequest());
        mvc.perform(get(path + "?size=101").with(as(alice))).andExpect(status().isBadRequest());
    }
    @Test
    void financialAndAuditRecordsAreImmutable() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID source = funded(alice, admin), destination = account(alice);
        TransferResponse transfer = transfers.transfer(alice.getId(), key(), request(source, destination, "1.00"));
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount=2 WHERE journal_id=?", transfer.referenceId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM journals WHERE id=?", transfer.referenceId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transfers WHERE id=?", transfer.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE resource_id=?", transfer.id())).isInstanceOf(RuntimeException.class);
        assertThat(count("ledger_entries", "journal_id", transfer.referenceId())).isEqualTo(2);
    }
}
