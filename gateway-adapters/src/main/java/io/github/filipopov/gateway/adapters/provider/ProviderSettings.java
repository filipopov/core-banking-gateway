package io.github.filipopov.gateway.adapters.provider;

import io.github.filipopov.gateway.application.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Everything a provider factory needs to build an adapter instance for one tenant. */
public record ProviderSettings(TenantId tenant, URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ProviderSettings {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
    }
}
