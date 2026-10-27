package io.github.filipopov.gateway.application.port.out;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.tenant.TenantId;

/** Answers whether a feature is switched on for a tenant. Unknown tenants have no features. */
public interface FeatureFlags {

    boolean isEnabled(TenantId tenant, Feature feature);
}
