package io.github.filipopov.gateway.api.common;

/** The tenant header is absent or malformed. */
public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}
