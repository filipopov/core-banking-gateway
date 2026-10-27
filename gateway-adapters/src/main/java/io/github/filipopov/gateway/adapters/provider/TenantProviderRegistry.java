package io.github.filipopov.gateway.adapters.provider;

import io.github.filipopov.gateway.adapters.config.GatewayProperties;
import io.github.filipopov.gateway.adapters.resilience.ResilientCoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.CoreBankingProviders;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds one provider adapter per configured tenant at startup, wrapped in its own circuit breaker and retry, and
 * resolves them by tenant id at request time. A tenant referencing an unknown provider fails startup.
 */
public final class TenantProviderRegistry implements CoreBankingProviders {

    private static final Logger log = LoggerFactory.getLogger(TenantProviderRegistry.class);

    private final Map<TenantId, CoreBankingProvider> providers;

    public TenantProviderRegistry(GatewayProperties properties, List<ProviderFactory> factories, RestClient.Builder restClientBuilder) {
        Map<String, ProviderFactory> factoriesByName = factories.stream()
                .collect(Collectors.toMap(ProviderFactory::providerName, Function.identity()));
        Map<TenantId, CoreBankingProvider> byTenant = new HashMap<>();
        properties.tenants().forEach((key, tenant) -> {
            TenantId tenantId = TenantId.of(key);
            ProviderFactory factory = factoriesByName.get(tenant.provider());
            if (factory == null) {
                throw new IllegalStateException("Tenant '%s' references unknown provider '%s'; known providers: %s"
                        .formatted(key, tenant.provider(), factoriesByName.keySet()));
            }
            var settings = new ProviderSettings(tenantId, tenant.baseUrl(), tenant.connectTimeout(), tenant.readTimeout());
            CoreBankingProvider adapter = factory.create(settings, RestClients.create(restClientBuilder, settings));
            byTenant.put(tenantId, ResilientCoreBankingProvider.decorate(key, adapter, properties.resilience()));
            log.info("Tenant '{}' -> provider '{}' at {} (features: {})", key, tenant.provider(), tenant.baseUrl(), tenant.features());
        });
        this.providers = Map.copyOf(byTenant);
    }

    @Override
    public CoreBankingProvider forTenant(TenantId tenant) {
        CoreBankingProvider provider = providers.get(tenant);
        if (provider == null) {
            throw new UnknownTenantException(tenant);
        }
        return provider;
    }
}
