package io.github.filipopov.gateway.application.service;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.CoreBankingProviders;
import io.github.filipopov.gateway.application.port.out.FeatureFlags;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.AccountType;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.github.filipopov.gateway.domain.transfer.TransferStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hand-written test doubles; the application layer is small enough not to need a mocking library. */
final class Fakes {

    static final TenantId TENANT = TenantId.of("test-bank");
    static final AccountId ACCOUNT = AccountId.of("A1");

    private Fakes() {
    }

    static final class RecordingProvider implements CoreBankingProvider {

        final List<Object> calls = new ArrayList<>();

        @Override
        public List<Account> listAccounts() {
            calls.add("listAccounts");
            return List.of(new Account(ACCOUNT, null, "Main", Currency.getInstance("CHF"), AccountType.CURRENT));
        }

        @Override
        public Account getAccount(AccountId accountId) {
            calls.add(accountId);
            return listAccounts().getFirst();
        }

        @Override
        public Balance getBalance(AccountId accountId) {
            calls.add(accountId);
            return new Balance(accountId, Money.of("1.00", "CHF"), Money.of("1.00", "CHF"));
        }

        @Override
        public Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query) {
            calls.add(query);
            return Page.last(List.of(new Transaction("T1", LocalDate.of(2026, 1, 2), Money.of("-4.50", "CHF"), "Coffee", null)));
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            calls.add(request);
            return new TransferResult("TR-1", TransferStatus.BOOKED);
        }
    }

    static CoreBankingProviders providers(Map<TenantId, CoreBankingProvider> byTenant) {
        return tenant -> {
            CoreBankingProvider provider = byTenant.get(tenant);
            if (provider == null) {
                throw new UnknownTenantException(tenant);
            }
            return provider;
        };
    }

    static FeatureFlags flags(Feature... enabled) {
        Set<Feature> features = enabled.length == 0 ? EnumSet.noneOf(Feature.class) : EnumSet.of(enabled[0], enabled);
        return (tenant, feature) -> TENANT.equals(tenant) && features.contains(feature);
    }
}
