package io.github.filipopov.gateway.adapters.support;

import io.github.filipopov.gateway.adapters.provider.ProviderSettings;
import io.github.filipopov.gateway.adapters.provider.RestClients;
import io.github.filipopov.gateway.application.tenant.TenantId;
import java.net.URI;
import java.time.Duration;
import org.springframework.web.client.RestClient;

public final class TestClients {

    public static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    private TestClients() {
    }

    public static RestClient forBaseUrl(String baseUrl) {
        var settings = new ProviderSettings(TenantId.of("test-bank"), URI.create(baseUrl), Duration.ofSeconds(1), READ_TIMEOUT);
        return RestClients.create(RestClient.builder(), settings);
    }
}
