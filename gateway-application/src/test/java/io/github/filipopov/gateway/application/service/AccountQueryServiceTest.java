package io.github.filipopov.gateway.application.service;

import static io.github.filipopov.gateway.application.service.Fakes.ACCOUNT;
import static io.github.filipopov.gateway.application.service.Fakes.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import io.github.filipopov.gateway.domain.transaction.PageRequest;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountQueryServiceTest {

    private final Fakes.RecordingProvider provider = new Fakes.RecordingProvider();

    @Test
    void delegatesReadsToTheTenantsProvider() {
        var service = new AccountQueryService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags());

        assertThat(service.listAccounts(TENANT)).hasSize(1);
        assertThat(service.getBalance(TENANT, ACCOUNT).accountId()).isEqualTo(ACCOUNT);
        assertThat(provider.calls).containsExactly("listAccounts", ACCOUNT);
    }

    @Test
    void plainTransactionListingNeedsNoFeature() {
        var service = new AccountQueryService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags());

        var page = service.listTransactions(TENANT, ACCOUNT, TransactionQuery.page(PageRequest.first(10)));

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void searchIsRejectedWhenFeatureIsDisabled() {
        var service = new AccountQueryService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags(Feature.TRANSFERS));
        var search = new TransactionQuery(PageRequest.first(10), "coffee");

        assertThatThrownBy(() -> service.listTransactions(TENANT, ACCOUNT, search))
                .isInstanceOfSatisfying(FeatureDisabledException.class,
                        e -> assertThat(e.feature()).isEqualTo(Feature.TRANSACTION_SEARCH));
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void searchIsDelegatedWhenFeatureIsEnabled() {
        var service = new AccountQueryService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags(Feature.TRANSACTION_SEARCH));
        var search = new TransactionQuery(PageRequest.first(10), "coffee");

        service.listTransactions(TENANT, ACCOUNT, search);

        assertThat(provider.calls).containsExactly(search);
    }

    @Test
    void unknownTenantWinsOverDisabledFeature() {
        var service = new AccountQueryService(Fakes.providers(Map.of()), Fakes.flags());
        var search = new TransactionQuery(PageRequest.first(10), "coffee");

        assertThatThrownBy(() -> service.listTransactions(TenantId.of("nobody"), ACCOUNT, search))
                .isInstanceOf(UnknownTenantException.class);
    }
}
