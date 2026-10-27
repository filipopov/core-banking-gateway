package io.github.filipopov.gateway.application.service;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.CoreBankingProviders;
import io.github.filipopov.gateway.application.port.out.FeatureFlags;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import java.util.List;
import java.util.Objects;

public class AccountQueryService implements QueryAccountsUseCase {

    private final CoreBankingProviders providers;
    private final FeatureFlags featureFlags;

    public AccountQueryService(CoreBankingProviders providers, FeatureFlags featureFlags) {
        this.providers = Objects.requireNonNull(providers);
        this.featureFlags = Objects.requireNonNull(featureFlags);
    }

    @Override
    public List<Account> listAccounts(TenantId tenant) {
        return providers.forTenant(tenant).listAccounts();
    }

    @Override
    public Account getAccount(TenantId tenant, AccountId accountId) {
        return providers.forTenant(tenant).getAccount(accountId);
    }

    @Override
    public Balance getBalance(TenantId tenant, AccountId accountId) {
        return providers.forTenant(tenant).getBalance(accountId);
    }

    @Override
    public Page<Transaction> listTransactions(TenantId tenant, AccountId accountId, TransactionQuery query) {
        // Resolve first so an unknown tenant is reported as such, not as a disabled feature.
        CoreBankingProvider provider = providers.forTenant(tenant);
        if (query.isSearch() && !featureFlags.isEnabled(tenant, Feature.TRANSACTION_SEARCH)) {
            throw new FeatureDisabledException(tenant, Feature.TRANSACTION_SEARCH);
        }
        return provider.listTransactions(accountId, query);
    }
}
