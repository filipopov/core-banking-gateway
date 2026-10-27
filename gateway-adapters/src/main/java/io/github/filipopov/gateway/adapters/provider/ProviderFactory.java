package io.github.filipopov.gateway.adapters.provider;

import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import org.springframework.web.client.RestClient;

/**
 * Service provider interface for core banking adapters. Adding a provider means implementing
 * {@link CoreBankingProvider} plus one of these and registering it as a bean; tenants then select it by
 * {@link #providerName()} in configuration.
 */
public interface ProviderFactory {

    /** The value tenants use in {@code gateway.tenants.<id>.provider}. */
    String providerName();

    /**
     * Creates an adapter for one tenant.
     *
     * @param restClient a client already configured with the tenant's base URL and timeouts
     */
    CoreBankingProvider create(ProviderSettings settings, RestClient restClient);
}
