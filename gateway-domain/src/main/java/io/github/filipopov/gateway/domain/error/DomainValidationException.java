package io.github.filipopov.gateway.domain.error;

/**
 * Raised when a value object or command violates a domain invariant (for example a negative transfer amount or an
 * amount with more decimals than its currency allows).
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }

    public DomainValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
