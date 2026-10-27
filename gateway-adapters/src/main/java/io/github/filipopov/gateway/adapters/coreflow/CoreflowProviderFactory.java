package io.github.filipopov.gateway.adapters.coreflow;

import io.github.filipopov.gateway.adapters.provider.ProviderFactory;
import io.github.filipopov.gateway.adapters.provider.ProviderSettings;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import org.springframework.web.client.RestClient;

public final class CoreflowProviderFactory implements ProviderFactory {

    @Override
    public String providerName() {
        return CoreflowProvider.NAME;
    }

    @Override
    public CoreBankingProvider create(ProviderSettings settings, RestClient restClient) {
        return new CoreflowProvider(restClient);
    }
}
