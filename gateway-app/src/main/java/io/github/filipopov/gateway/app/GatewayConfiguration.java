package io.github.filipopov.gateway.app;

import io.github.filipopov.gateway.adapters.config.ConfigFeatureFlags;
import io.github.filipopov.gateway.adapters.config.GatewayProperties;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowProviderFactory;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineProviderFactory;
import io.github.filipopov.gateway.adapters.provider.ProviderFactory;
import io.github.filipopov.gateway.adapters.provider.TenantProviderRegistry;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.port.in.SubmitTransferUseCase;
import io.github.filipopov.gateway.application.port.out.CoreBankingProviders;
import io.github.filipopov.gateway.application.port.out.FeatureFlags;
import io.github.filipopov.gateway.application.service.AccountQueryService;
import io.github.filipopov.gateway.application.service.TransferService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires use cases to adapters. The domain and application modules stay free of Spring because all their objects
 * are created here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class GatewayConfiguration {

    @Bean
    ProviderFactory ledgerlineProviderFactory() {
        return new LedgerlineProviderFactory();
    }

    @Bean
    ProviderFactory coreflowProviderFactory() {
        return new CoreflowProviderFactory();
    }

    @Bean
    CoreBankingProviders coreBankingProviders(GatewayProperties properties, List<ProviderFactory> factories,
                                              RestClient.Builder restClientBuilder) {
        return new TenantProviderRegistry(properties, factories, restClientBuilder);
    }

    @Bean
    FeatureFlags featureFlags(GatewayProperties properties) {
        return new ConfigFeatureFlags(properties);
    }

    @Bean
    QueryAccountsUseCase queryAccountsUseCase(CoreBankingProviders providers, FeatureFlags featureFlags) {
        return new AccountQueryService(providers, featureFlags);
    }

    @Bean
    SubmitTransferUseCase submitTransferUseCase(CoreBankingProviders providers, FeatureFlags featureFlags) {
        return new TransferService(providers, featureFlags);
    }
}
