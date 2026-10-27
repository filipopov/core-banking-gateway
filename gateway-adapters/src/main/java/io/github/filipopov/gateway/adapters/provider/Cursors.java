package io.github.filipopov.gateway.adapters.provider;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes a provider's native paging state into the opaque cursor handed to API clients. The provider prefix makes
 * sure a cursor issued for one provider is rejected by another.
 */
public final class Cursors {

    private final String prefix;

    public Cursors(String providerPrefix) {
        this.prefix = providerPrefix + ":";
    }

    public String encode(String nativeState) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((prefix + nativeState).getBytes(StandardCharsets.UTF_8));
    }

    public String decode(String cursor) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Invalid page cursor", e);
        }
        if (!decoded.startsWith(prefix) || decoded.length() == prefix.length()) {
            throw new DomainValidationException("Invalid page cursor");
        }
        return decoded.substring(prefix.length());
    }
}
