package io.github.filipopov.gateway.adapters.provider;

import org.springframework.http.HttpStatusCode;

/**
 * A provider error response reduced to what the gateway needs for translation.
 *
 * @param code       the provider's machine-readable error code, {@code null} if the body could not be parsed
 * @param message    the provider's human-readable message, for logs only (never exposed to API clients)
 * @param accountRef the account the provider says the error refers to, if any
 */
public record UpstreamError(HttpStatusCode status, String code, String message, String accountRef) {

    public static UpstreamError unparseable(HttpStatusCode status) {
        return new UpstreamError(status, null, null, null);
    }
}
