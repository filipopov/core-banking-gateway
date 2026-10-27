package io.github.filipopov.gateway.application.service;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.port.in.SubmitTransferUseCase;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.CoreBankingProviders;
import io.github.filipopov.gateway.application.port.out.FeatureFlags;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import java.util.Objects;

public class TransferService implements SubmitTransferUseCase {

    private final CoreBankingProviders providers;
    private final FeatureFlags featureFlags;

    public TransferService(CoreBankingProviders providers, FeatureFlags featureFlags) {
        this.providers = Objects.requireNonNull(providers);
        this.featureFlags = Objects.requireNonNull(featureFlags);
    }

    @Override
    public TransferResult submit(TenantId tenant, TransferRequest request) {
        CoreBankingProvider provider = providers.forTenant(tenant);
        if (!featureFlags.isEnabled(tenant, Feature.TRANSFERS)) {
            throw new FeatureDisabledException(tenant, Feature.TRANSFERS);
        }
        return provider.transfer(request);
    }
}
