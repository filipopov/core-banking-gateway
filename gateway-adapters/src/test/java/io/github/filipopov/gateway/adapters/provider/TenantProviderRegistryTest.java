package io.github.filipopov.gateway.adapters.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.adapters.config.GatewayProperties;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowProviderFactory;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineProviderFactory;
import io.github.filipopov.gateway.adapters.resilience.ResilientCoreBankingProvider;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TenantProviderRegistryTest {

    private static final List<ProviderFactory> FACTORIES = List.of(new LedgerlineProviderFactory(), new CoreflowProviderFactory());

    private static GatewayProperties.Tenant tenant(String provider) {
        return new GatewayProperties.Tenant(provider, URI.create("http://localhost:9"), null, null, null);
    }

    @Test
    void createsOneResilientAdapterPerTenant() {
        var registry = new TenantProviderRegistry(new GatewayProperties(Map.of(
                "alpine-bank", tenant("ledgerline"),
                "harbor-bank", tenant("coreflow")), null), FACTORIES, RestClient.builder());

        assertThat(registry.forTenant(TenantId.of("alpine-bank"))).isInstanceOf(ResilientCoreBankingProvider.class);
        assertThat(registry.forTenant(TenantId.of("harbor-bank")))
                .isNotSameAs(registry.forTenant(TenantId.of("alpine-bank")));
    }

    @Test
    void unknownTenantIsRejected() {
        var registry = new TenantProviderRegistry(new GatewayProperties(Map.of(), null), FACTORIES, RestClient.builder());

        assertThatThrownBy(() -> registry.forTenant(TenantId.of("ghost-bank"))).isInstanceOf(UnknownTenantException.class);
    }

    @Test
    void unknownProviderFailsStartup() {
        var properties = new GatewayProperties(Map.of("alpine-bank", tenant("mainframe-2000")), null);

        assertThatThrownBy(() -> new TenantProviderRegistry(properties, FACTORIES, RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mainframe-2000")
                .hasMessageContaining("ledgerline");
    }
}
