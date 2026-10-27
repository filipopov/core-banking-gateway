package io.github.filipopov.gateway.application.port.out;

/**
 * The provider answered, but not in a way the adapter understands: an unexpected status, an unknown error code or a
 * malformed body. Retrying will not help; this indicates a contract drift between gateway and provider.
 */
public class ProviderContractException extends RuntimeException {

    private final String provider;

    public ProviderContractException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public ProviderContractException(String provider, String message) {
        this(provider, message, null);
    }

    public String provider() {
        return provider;
    }
}
