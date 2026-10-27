package io.github.filipopov.gateway.application.feature;

import io.github.filipopov.gateway.application.tenant.TenantId;

/** The requested capability is switched off for the calling tenant. */
public class FeatureDisabledException extends RuntimeException {

    private final TenantId tenantId;
    private final Feature feature;

    public FeatureDisabledException(TenantId tenantId, Feature feature) {
        super("Feature '%s' is not enabled for tenant '%s'".formatted(feature.key(), tenantId.value()));
        this.tenantId = tenantId;
        this.feature = feature;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Feature feature() {
        return feature;
    }
}
