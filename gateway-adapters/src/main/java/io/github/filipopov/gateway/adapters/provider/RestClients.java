package io.github.filipopov.gateway.adapters.provider;

import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds per-tenant RestClients with explicit connect and read timeouts. */
public final class RestClients {

    private RestClients() {
    }

    public static RestClient create(RestClient.Builder builder, ProviderSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return builder.clone()
                .baseUrl(settings.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
