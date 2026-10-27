package io.github.filipopov.gateway.domain.account;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.util.regex.Pattern;

/**
 * Provider-scoped account identifier. The gateway treats it as opaque but restricts the character set so it can never
 * be used to manipulate upstream URLs.
 */
public record AccountId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public AccountId {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new DomainValidationException("Account id must match " + ALLOWED.pattern());
        }
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
