package io.github.filipopov.gateway.domain.account;

import java.util.Currency;
import java.util.Objects;

/**
 * A customer account as exposed by the gateway.
 *
 * @param iban may be {@code null} for providers or account types without an IBAN
 */
public record Account(AccountId id, String iban, String name, Currency currency, AccountType type) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
    }
}
