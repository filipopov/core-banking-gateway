package io.github.filipopov.gateway.domain.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.money.Money;
import org.junit.jupiter.api.Test;

class BalanceTest {

    @Test
    void bookedAndAvailableMustShareCurrency() {
        assertThatThrownBy(() -> new Balance(AccountId.of("A1"), Money.of("1.00", "CHF"), Money.of("1.00", "EUR")))
                .isInstanceOf(DomainValidationException.class);
    }
}
