package io.github.filipopov.gateway.domain.account;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.money.Money;
import java.util.Objects;

/**
 * Balances of one account.
 *
 * @param booked    settled balance
 * @param available what the customer can spend right now (booked minus reservations plus credit lines)
 */
public record Balance(AccountId accountId, Money booked, Money available) {

    public Balance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(booked, "booked");
        Objects.requireNonNull(available, "available");
        if (!booked.currency().equals(available.currency())) {
            throw new DomainValidationException("Booked and available balance must share one currency");
        }
    }
}
