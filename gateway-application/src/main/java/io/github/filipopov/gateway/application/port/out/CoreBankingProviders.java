package io.github.filipopov.gateway.application.port.out;

import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;

/** Resolves the provider adapter configured for a tenant. */
public interface CoreBankingProviders {

    /**
     * @throws UnknownTenantException if the tenant is not configured
     */
    CoreBankingProvider forTenant(TenantId tenant);
}
