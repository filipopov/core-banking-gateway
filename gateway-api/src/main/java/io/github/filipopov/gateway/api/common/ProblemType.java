package io.github.filipopov.gateway.api.common;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The gateway's error catalogue. Every error response is an RFC 9457 {@code application/problem+json} document whose
 * {@code type} points to the matching section of {@code docs/problems.md}, and whose {@code code} extension member
 * carries the same stable slug for clients that prefer switching on a short string.
 */
public enum ProblemType {
    MISSING_TENANT("missing-tenant", "Tenant header missing or malformed", HttpStatus.BAD_REQUEST),
    UNKNOWN_TENANT("unknown-tenant", "Unknown tenant", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("invalid-request", "Invalid request", HttpStatus.BAD_REQUEST),
    FEATURE_DISABLED("feature-disabled", "Feature not enabled", HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_FOUND("account-not-found", "Account not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_FUNDS("insufficient-funds", "Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY),
    TRANSFER_REJECTED("transfer-rejected", "Transfer rejected", HttpStatus.UNPROCESSABLE_ENTITY),
    PROVIDER_ERROR("provider-error", "Unexpected response from core banking system", HttpStatus.BAD_GATEWAY),
    PROVIDER_UNAVAILABLE("provider-unavailable", "Core banking system unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR("internal-error", "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);

    static final String BASE_URI = "https://github.com/filipopov/core-banking-gateway/blob/main/docs/problems.md#";

    private final String code;
    private final String title;
    private final HttpStatus status;

    ProblemType(String code, String title, HttpStatus status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }

    public URI uri() {
        return URI.create(BASE_URI + code);
    }
}
