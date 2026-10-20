package com.gig0d.banking.integration;

import com.gig0d.banking.account.*;
import com.gig0d.banking.demo.DemoFundingService;
import com.gig0d.banking.identity.*;
import com.gig0d.banking.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("demo")
class BankingIT extends PostgresSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AccountService accounts;
    @Autowired DemoFundingService funding;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;

    private User user(User.Role role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "not-used-by-jwt-fixtures", role));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(User user) {
        return jwt().jwt(claims -> claims.subject(user.getId().toString())).authorities(createAuthorityList("ROLE_" + user.getRole().name()));
    }
    private AccountService.AccountResponse account(User owner) { return accounts.create(owner.getId(), Account.Type.CHECKING); }
    private void fund(User admin, UUID account, String amount) { funding.fund(admin.getId(), account, new BigDecimal(amount)); }

    @Test
    void migrationsAndJpaValidationStartAndExposeOnlySafeHealth() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isGreaterThanOrEqualTo(2);
    }
    @Test
    void openApiDeclaresBearerAuthenticationAndDoesNotRequireAuthForRegistration() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
    @Test
    void profileAndAuthenticationValidationRejectsUnsafeRequestsWithoutEchoingSecrets() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"Synthetic-Password-42!\",\"fullName\":\"Customer\"}";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Wrong-Password-42!\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Wrong-Password"))));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"email\":\"invalid\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        User customer = user(User.Role.CUSTOMER);
        mvc.perform(delete("/api/v1/accounts").with(as(customer))).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/accounts").with(as(customer)).contentType("text/plain").content("CHECKING"))
                .andExpect(status().isUnsupportedMediaType());
    }
    @Test
    void adminCanFreezeUnfreezeReviewAuditButCannotCloseOrReadAsCustomer() throws Exception {
        User customer = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN); UUID id = account(customer).id();
        mvc.perform(patch("/api/v1/admin/accounts/" + id + "/status").with(as(admin)).contentType("application/json").content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FROZEN"));
        assertThatThrownBy(() -> fund(admin, id, "10.00")).hasMessageContaining("active");
        mvc.perform(patch("/api/v1/admin/accounts/" + id + "/status").with(as(admin)).contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/accounts/" + id + "/status").with(as(admin)).contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/" + id).with(as(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-events?size=1").with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].password").doesNotExist());
    }
    @Test
    void registrationLoginAndProfileUpdateUseRealPasswordAndBearerValidation() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
                {"email":"%s","password":"Synthetic-Password-42!","fullName":"Synthetic Customer"}
                """.formatted(email))).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        User user = users.findByEmail(email).orElseThrow();
        assertThat(passwords.matches("Synthetic-Password-42!", user.getPasswordHash())).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                {"email":"%s","password":"Synthetic-Password-42!"}
                """.formatted(email))).andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"));
        String token = tokens.issue(user).accessToken();
        mvc.perform(patch("/api/v1/customers/me").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"fullName\":\"Updated Customer\",\"phone\":\"+506 1234 5678\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Customer"));
        mvc.perform(get("/api/v1/customers/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
    @Test
    void accountOwnershipAndPrivilegedEndpointsRejectOtherCustomers() throws Exception {
        User alice = user(User.Role.CUSTOMER), bob = user(User.Role.CUSTOMER);
        UUID id = account(alice).id();
        mvc.perform(get("/api/v1/accounts/" + id).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/accounts/" + id + "/closure").with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/demo-deposits").with(as(alice)).contentType("application/json")
                .content("{\"accountId\":\"" + id + "\",\"amount\":100}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/" + id).with(as(alice))).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
    }
    @Test
    void demoDepositIsBoundedOneTimeBalancedAndReconciled() {
        User alice = user(User.Role.CUSTOMER), admin = user(User.Role.ADMIN);
        UUID id = account(alice).id();
        assertThatThrownBy(() -> fund(admin, id, "1000.01")).hasMessageContaining("limited");
        fund(admin, id, "100.00");
        assertThatThrownBy(() -> fund(admin, id, "100.00")).hasMessageContaining("already");
        assertThat(accounts.get(alice.getId(), id).balance()).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT sum(CASE WHEN side='CREDIT' THEN amount ELSE -amount END) FROM ledger_entries WHERE account_id=?", BigDecimal.class, id))
                .isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journals j WHERE (SELECT sum(CASE WHEN side='CREDIT' THEN amount ELSE -amount END) FROM ledger_entries e WHERE e.journal_id=j.id) <> 0", Integer.class)).isZero();
    }
    @Test
    void databaseRejectsUnbalancedJournalAndUnsafeBalanceWrites() {
        User alice = user(User.Role.CUSTOMER); UUID id = account(alice).id();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update("UPDATE accounts SET balance=1 WHERE id=?", id)))
                .isInstanceOf(RuntimeException.class);
        assertThat(accounts.get(alice.getId(), id).balance()).isEqualByComparingTo("0.00");
        UUID journal = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update("INSERT INTO journals(id,type,currency) VALUES (?,'TRANSFER','USD')", journal)))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journals WHERE id=?", Integer.class, journal)).isZero();
    }
    @Test
    void validatesAccountCreationAndBoundsPagination() throws Exception {
        User alice = user(User.Role.CUSTOMER);
        for (String invalid : new String[]{"{\"type\":\"SYSTEM\"}", "{\"type\":\"CHECKING\",\"balance\":999}", "{}"})
            mvc.perform(post("/api/v1/accounts").with(as(alice)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/accounts?size=101").with(as(alice))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts?page=-1").with(as(alice))).andExpect(status().isBadRequest());
        account(alice); account(alice);
        mvc.perform(get("/api/v1/accounts?size=1").with(as(alice))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2));
    }
}
