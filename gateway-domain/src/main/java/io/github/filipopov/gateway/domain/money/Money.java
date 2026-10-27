package io.github.filipopov.gateway.domain.money;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a specific ISO 4217 currency.
 *
 * <p>Invariants:
 * <ul>
 *   <li>The amount is a {@link BigDecimal}; doubles are never used.</li>
 *   <li>The amount is normalised to the currency's minor-unit scale (2 for CHF/EUR, 0 for JPY, 3 for BHD), so two
 *       equal amounts are also {@code equals()}.</li>
 *   <li>An amount with more decimals than the currency allows is rejected rather than silently rounded.</li>
 * </ul>
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new DomainValidationException("Currency " + currency.getCurrencyCode() + " has no minor unit and is not supported");
        }
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new DomainValidationException(
                    "Amount %s has more than %d decimal places allowed for %s".formatted(amount.toPlainString(), digits, currency.getCurrencyCode()));
        }
        amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currency(currencyCode));
    }

    public static Money of(String amount, String currencyCode) {
        Objects.requireNonNull(amount, "amount");
        try {
            return of(new BigDecimal(amount), currencyCode);
        } catch (NumberFormatException e) {
            throw new DomainValidationException("'" + amount + "' is not a decimal amount", e);
        }
    }

    /** Creates money from an integer amount of minor units, e.g. {@code 1250 CHF -> 12.50 CHF}. */
    public static Money ofMinor(long minorUnits, String currencyCode) {
        Currency currency = currency(currencyCode);
        return new Money(BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits()), currency);
    }

    /** Converts to an exact integer amount of minor units, e.g. {@code 12.50 CHF -> 1250}. */
    public long toMinorUnits() {
        return amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainValidationException("Currency mismatch: %s vs %s".formatted(currencyCode(), other.currencyCode()));
        }
    }

    private static Currency currency(String code) {
        Objects.requireNonNull(code, "currencyCode");
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("'" + code + "' is not an ISO 4217 currency code", e);
        }
    }
}
