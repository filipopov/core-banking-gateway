package io.github.filipopov.gateway.application.error;

/**
 * The provider could not be reached or answered with a server error: connection refused, timeout, 5xx or an open
 * circuit breaker. Read operations may be retried.
 */
public class ProviderUnavailableException extends RuntimeException {

    private final String provider;

    public ProviderUnavailableException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public ProviderUnavailableException(String provider, String message) {
        this(provider, message, null);
    }

    public String provider() {
        return provider;
    }
}
