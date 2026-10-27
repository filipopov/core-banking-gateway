package io.github.filipopov.gateway.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.tenant.TenantId;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigFeatureFlagsTest {

    private static GatewayProperties.Tenant tenant(String... features) {
        return new GatewayProperties.Tenant("ledgerline", URI.create("http://localhost"), Set.of(features), null, null);
    }

    @Test
    void enablesOnlyConfiguredFeaturesPerTenant() {
        var flags = new ConfigFeatureFlags(new GatewayProperties(Map.of(
                "alpine-bank", tenant("transfers", "transaction-search"),
                "meadow-bank", tenant()), null));

        assertThat(flags.isEnabled(TenantId.of("alpine-bank"), Feature.TRANSFERS)).isTrue();
        assertThat(flags.isEnabled(TenantId.of("alpine-bank"), Feature.TRANSACTION_SEARCH)).isTrue();
        assertThat(flags.isEnabled(TenantId.of("meadow-bank"), Feature.TRANSFERS)).isFalse();
    }

    @Test
    void unknownTenantHasNoFeatures() {
        var flags = new ConfigFeatureFlags(new GatewayProperties(Map.of("alpine-bank", tenant("transfers")), null));

        assertThat(flags.isEnabled(TenantId.of("ghost-bank"), Feature.TRANSFERS)).isFalse();
    }

    @Test
    void typoInFeatureKeyFailsFast() {
        var properties = new GatewayProperties(Map.of("alpine-bank", tenant("transferz")), null);

        assertThatThrownBy(() -> new ConfigFeatureFlags(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transferz");
    }

    @Test
    void tenantDefaultsTimeouts() {
        var tenant = tenant();

        assertThat(tenant.connectTimeout()).hasSeconds(2);
        assertThat(tenant.readTimeout()).hasSeconds(5);
    }

    @Test
    void tenantRequiresProviderAndBaseUrl() {
        assertThatThrownBy(() -> new GatewayProperties.Tenant(" ", URI.create("http://x"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayProperties.Tenant("coreflow", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
