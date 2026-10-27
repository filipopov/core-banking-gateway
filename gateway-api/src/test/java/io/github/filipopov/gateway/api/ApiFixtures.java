package io.github.filipopov.gateway.api;

import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.AccountType;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

final class ApiFixtures {

    static final String TENANT = "alpine-bank";
    static final AccountId ACCOUNT = AccountId.of("LL-1001");

    private ApiFixtures() {
    }

    static Account account() {
        return new Account(ACCOUNT, "CH9300762011623852957", "Private account", Currency.getInstance("CHF"), AccountType.CURRENT);
    }

    static Balance balance() {
        return new Balance(ACCOUNT, Money.of("12540.75", "CHF"), Money.of("12040.75", "CHF"));
    }

    static Page<Transaction> transactions(String nextCursor) {
        return new Page<>(List.of(
                new Transaction("LT-5005", LocalDate.of(2026, 10, 20), Money.of("-4.50", "CHF"), "Coffee", "Coffee Bar"),
                new Transaction("LT-5004", LocalDate.of(2026, 10, 18), Money.of("6500.00", "CHF"), "Salary", null)),
                nextCursor);
    }
}
