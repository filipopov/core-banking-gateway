package io.github.filipopov.gateway.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Test
    void normalisesToCurrencyScaleSoEqualAmountsAreEqual() {
        assertThat(Money.of("12.5", "CHF")).isEqualTo(Money.of(new BigDecimal("12.500"), "CHF"));
        assertThat(Money.of("12.5", "CHF").amount()).isEqualByComparingTo("12.50");
        assertThat(Money.of("12.5", "CHF").amount().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "1250, CHF, 12.50",
        "-99, EUR, -0.99",
        "1500, JPY, 1500",
        "12345, BHD, 12.345",
    })
    void convertsFromMinorUnitsUsingCurrencyFractionDigits(long minor, String currency, String expected) {
        Money money = Money.ofMinor(minor, currency);

        assertThat(money.amount()).isEqualByComparingTo(expected);
        assertThat(money.toMinorUnits()).isEqualTo(minor);
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfRounding() {
        assertThatThrownBy(() -> Money.of("10.005", "EUR"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("more than 2 decimal places");
        assertThatThrownBy(() -> Money.of("1.5", "JPY")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsUnknownCurrencyAndNonNumericAmounts() {
        assertThatThrownBy(() -> Money.of("1.00", "XYZ")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> Money.of("one", "EUR")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsCurrenciesWithoutMinorUnit() {
        assertThatThrownBy(() -> Money.of("1", "XAU")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void arithmeticRequiresSameCurrency() {
        Money tenFrancs = Money.of("10.00", "CHF");

        assertThat(tenFrancs.add(Money.of("0.05", "CHF"))).isEqualTo(Money.of("10.05", "CHF"));
        assertThat(tenFrancs.negate().isNegative()).isTrue();
        assertThat(tenFrancs.compareTo(Money.of("9.99", "CHF"))).isPositive();
        assertThatThrownBy(() -> tenFrancs.add(Money.of("1.00", "EUR")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void toStringUsesPlainNotation() {
        assertThat(Money.of(new BigDecimal("1E+3"), "EUR")).hasToString("1000.00 EUR");
    }
}
