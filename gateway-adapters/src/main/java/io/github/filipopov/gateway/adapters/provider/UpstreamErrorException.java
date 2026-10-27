package io.github.filipopov.gateway.adapters.provider;

/**
 * Internal carrier for a parsed provider error. Thrown from the RestClient status handler and translated into a
 * domain or port exception by {@link UpstreamCalls}; it never leaves the adapter.
 */
public class UpstreamErrorException extends RuntimeException {

    private final transient UpstreamError error;

    public UpstreamErrorException(UpstreamError error) {
        super("Upstream responded " + error.status().value() + " with code " + error.code());
        this.error = error;
    }

    public UpstreamError error() {
        return error;
    }
}
