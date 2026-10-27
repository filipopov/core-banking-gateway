package io.github.filipopov.gateway.adapters.config;

import io.github.filipopov.gateway.adapters.resilience.ResilienceSettings;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway configuration, bound from {@code gateway.*}.
 *
 * <pre>
 * gateway:
 *   tenants:
 *     alpine-bank:
 *       provider: ledgerline
 *       base-url: http://localhost:8081
 *       features: [transfers, transaction-search]
 * </pre>
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Map<String, Tenant> tenants, ResilienceSettings resilience) {

    public GatewayProperties {
        tenants = tenants == null ? Map.of() : Map.copyOf(tenants);
        resilience = resilience == null ? ResilienceSettings.defaults() : resilience;
    }

    /**
     * @param provider       name of the provider adapter, e.g. {@code ledgerline} or {@code coreflow}
     * @param baseUrl        base URL of the tenant's core banking system
     * @param features       feature keys switched on for this tenant; everything else is off
     * @param connectTimeout TCP connect timeout towards the provider (default 2s)
     * @param readTimeout    response timeout towards the provider (default 5s)
     */
    public record Tenant(String provider, URI baseUrl, Set<String> features, Duration connectTimeout, Duration readTimeout) {

        public Tenant {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("gateway.tenants.*.provider is required");
            }
            if (baseUrl == null) {
                throw new IllegalArgumentException("gateway.tenants.*.base-url is required");
            }
            features = features == null ? Set.of() : Set.copyOf(features);
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        }
    }
}
