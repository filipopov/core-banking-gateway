package io.github.filipopov.gateway.api.common;

import io.github.filipopov.gateway.application.error.ProviderContractException;
import io.github.filipopov.gateway.application.error.ProviderUnavailableException;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.error.InsufficientFundsException;
import io.github.filipopov.gateway.domain.error.TransferRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 9457 problem response. Domain and application exceptions get a type from
 * {@link ProblemType}; framework exceptions (bad JSON, failed bean validation, wrong method, ...) are handled by
 * {@link ResponseEntityExceptionHandler} and enriched so that all 400s share the {@code invalid-request} type.
 *
 * <p>Upstream error messages are logged but never forwarded to clients.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MissingTenantException.class)
    ResponseEntity<ProblemDetail> missingTenant(MissingTenantException e, HttpServletRequest request) {
        return problem(ProblemType.MISSING_TENANT, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnknownTenantException.class)
    ResponseEntity<ProblemDetail> unknownTenant(UnknownTenantException e, HttpServletRequest request) {
        return problem(ProblemType.UNKNOWN_TENANT, e.getMessage(), request, Map.of("tenant", e.tenantId().value()));
    }

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ProblemDetail> invalid(DomainValidationException e, HttpServletRequest request) {
        return problem(ProblemType.INVALID_REQUEST, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(FeatureDisabledException.class)
    ResponseEntity<ProblemDetail> featureDisabled(FeatureDisabledException e, HttpServletRequest request) {
        return problem(ProblemType.FEATURE_DISABLED, e.getMessage(), request,
                Map.of("tenant", e.tenantId().value(), "feature", e.feature().key()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> accountNotFound(AccountNotFoundException e, HttpServletRequest request) {
        return problem(ProblemType.ACCOUNT_NOT_FOUND, e.getMessage(), request, Map.of("accountId", e.accountId().value()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ProblemDetail> insufficientFunds(InsufficientFundsException e, HttpServletRequest request) {
        return problem(ProblemType.INSUFFICIENT_FUNDS, e.getMessage(), request, Map.of("accountId", e.accountId().value()));
    }

    @ExceptionHandler(TransferRejectedException.class)
    ResponseEntity<ProblemDetail> transferRejected(TransferRejectedException e, HttpServletRequest request) {
        return problem(ProblemType.TRANSFER_REJECTED, e.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    ResponseEntity<ProblemDetail> providerUnavailable(ProviderUnavailableException e, HttpServletRequest request) {
        log.warn("Provider unavailable: {}", e.getMessage());
        return problem(ProblemType.PROVIDER_UNAVAILABLE, "The core banking system is temporarily unavailable. Please retry later.",
                request, Map.of());
    }

    @ExceptionHandler(ProviderContractException.class)
    ResponseEntity<ProblemDetail> providerError(ProviderContractException e, HttpServletRequest request) {
        log.error("Provider contract violation: {}", e.getMessage(), e);
        return problem(ProblemType.PROVIDER_ERROR, "The core banking system returned a response the gateway could not process.",
                request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(ProblemType.INTERNAL_ERROR, "An unexpected error occurred.", request, Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        body.setDetail("Request body is invalid");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (statusCode.value() == ProblemType.INVALID_REQUEST.status().value()) {
                problem.setType(ProblemType.INVALID_REQUEST.uri());
                problem.setTitle(ProblemType.INVALID_REQUEST.title());
                problem.setProperty("code", ProblemType.INVALID_REQUEST.code());
            }
            if (request instanceof ServletWebRequest servletRequest) {
                problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            }
        }
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemType type, String detail, HttpServletRequest request,
                                                         Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problem.setType(type.uri());
        problem.setTitle(type.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", type.code());
        properties.forEach(problem::setProperty);
        return ResponseEntity.status(type.status()).body(problem);
    }
}
