package com.gig0d.banking.account;

import com.gig0d.banking.shared.ApiException;
import com.gig0d.banking.shared.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AccountTest {
    private Account account() { return new Account(UUID.randomUUID(), Account.Type.CHECKING); }
    @Test
    void customersStartWithZeroAndCannotCreateSystemAccounts() {
        assertThat(account().getBalance()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> new Account(UUID.randomUUID(), Account.Type.SYSTEM)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void insufficientFundsNeverChangesBalance() {
        Account account = account(); account.credit(new BigDecimal("10.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01"))).isInstanceOf(ApiException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }
    @Test
    void frozenAccountsRejectMovementsAndClosedAccountsCannotReopen() {
        Account account = account(); account.changeStatus(Account.Status.FROZEN);
        assertThatThrownBy(account::requireActive).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> account.changeStatus(Account.Status.CLOSED)).isInstanceOf(ApiException.class);
        account.changeStatus(Account.Status.ACTIVE); account.changeStatus(Account.Status.CLOSED);
        assertThatThrownBy(() -> account.changeStatus(Account.Status.ACTIVE)).isInstanceOf(ApiException.class);
    }
    @Test
    void cannotCloseAnAccountWithFunds() {
        Account account = account(); account.credit(new BigDecimal("0.01"));
        assertThatThrownBy(() -> account.changeStatus(Account.Status.CLOSED)).isInstanceOf(ApiException.class);
        assertThat(account.getStatus()).isEqualTo(Account.Status.ACTIVE);
    }
    @Test
    void rejectsNegativeZeroExcessPrecisionAndOversizedAmountsWithoutRounding() {
        for (String value : new String[]{"-1.00", "0", "0.001", "1.000", "1000000.01"})
            assertThatThrownBy(() -> Money.amount(new BigDecimal(value))).isInstanceOf(ApiException.class);
        assertThat(Money.amount(new BigDecimal("1"))).isEqualTo(new BigDecimal("1.00"));
    }
}
