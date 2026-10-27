package io.github.filipopov.gateway.adapters.provider;

import io.github.filipopov.gateway.application.port.out.ProviderContractException;
import io.github.filipopov.gateway.application.port.out.ProviderUnavailableException;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Executes one upstream call and maps every failure mode into the gateway's error model:
 *
 * <ul>
 *   <li>I/O problems and timeouts, and 5xx responses &rarr; {@link ProviderUnavailableException} (retryable)</li>
 *   <li>provider error responses &rarr; whatever the provider-specific translator returns</li>
 *   <li>unreadable bodies, or payloads that violate domain invariants &rarr; {@link ProviderContractException}</li>
 * </ul>
 */
public final class UpstreamCalls {

    private static final Logger log = LoggerFactory.getLogger(UpstreamCalls.class);

    private final String provider;

    public UpstreamCalls(String provider) {
        this.provider = provider;
    }

    public <T> T execute(String operation, Supplier<T> call, Function<UpstreamError, RuntimeException> translator) {
        try {
            return call.get();
        } catch (UpstreamErrorException e) {
            UpstreamError error = e.error();
            log.warn("{} {} failed: status={} code={} message={}", provider, operation, error.status().value(), error.code(), error.message());
            if (error.status().is5xxServerError()) {
                throw new ProviderUnavailableException(provider, provider + " responded " + error.status().value(), e);
            }
            throw translator.apply(error);
        } catch (ResourceAccessException e) {
            log.warn("{} {} failed: {}", provider, operation, e.getMessage());
            throw new ProviderUnavailableException(provider, provider + " is not reachable: " + e.getMessage(), e);
        } catch (RestClientException | DomainValidationException | NullPointerException e) {
            // The provider answered with something we cannot map: treat as contract drift, not as a client error.
            log.warn("{} {} returned an unexpected payload: {}", provider, operation, e.toString());
            throw new ProviderContractException(provider, provider + " returned an unexpected response", e);
        }
    }

    public ProviderContractException unexpected(UpstreamError error) {
        return new ProviderContractException(provider,
                "%s returned unexpected error %d/%s".formatted(provider, error.status().value(), error.code()));
    }
}
