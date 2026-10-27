package io.github.filipopov.gateway.application.tenant;

/** No tenant with this id is configured. */
public class UnknownTenantException extends RuntimeException {

    private final TenantId tenantId;

    public UnknownTenantException(TenantId tenantId) {
        super("Tenant '%s' is not configured".formatted(tenantId.value()));
        this.tenantId = tenantId;
    }

    public TenantId tenantId() {
        return tenantId;
    }
}
