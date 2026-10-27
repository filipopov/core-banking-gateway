package io.github.filipopov.gateway.adapters.ledgerline;

import io.github.filipopov.gateway.adapters.provider.ProviderFactory;
import io.github.filipopov.gateway.adapters.provider.ProviderSettings;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import org.springframework.web.client.RestClient;

public final class LedgerlineProviderFactory implements ProviderFactory {

    @Override
    public String providerName() {
        return LedgerlineProvider.NAME;
    }

    @Override
    public CoreBankingProvider create(ProviderSettings settings, RestClient restClient) {
        return new LedgerlineProvider(restClient);
    }
}
