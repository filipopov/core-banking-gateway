package io.github.filipopov.gateway.application.tenant;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.util.regex.Pattern;

/** Identifies a tenant (a bank) served by this gateway, e.g. {@code alpine-bank}. */
public record TenantId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");

    public TenantId {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new DomainValidationException("Tenant id must match " + ALLOWED.pattern());
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
