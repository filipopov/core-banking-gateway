package io.github.filipopov.gateway.adapters.config;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.port.out.FeatureFlags;
import io.github.filipopov.gateway.application.tenant.TenantId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feature flags read from {@code gateway.tenants.<id>.features}. Unknown feature keys fail application startup, so a
 * typo in configuration cannot silently disable a feature.
 */
public final class ConfigFeatureFlags implements FeatureFlags {

    private final Map<TenantId, Set<Feature>> enabled;

    public ConfigFeatureFlags(GatewayProperties properties) {
        Map<TenantId, Set<Feature>> byTenant = new HashMap<>();
        properties.tenants().forEach((tenantKey, tenant) -> {
            Set<Feature> features = EnumSet.noneOf(Feature.class);
            for (String key : tenant.features()) {
                features.add(Feature.fromKey(key).orElseThrow(() -> new IllegalStateException(
                        "Unknown feature '%s' configured for tenant '%s'".formatted(key, tenantKey))));
            }
            byTenant.put(TenantId.of(tenantKey), features);
        });
        this.enabled = Map.copyOf(byTenant);
    }

    @Override
    public boolean isEnabled(TenantId tenant, Feature feature) {
        return enabled.getOrDefault(tenant, Set.of()).contains(feature);
    }
}
