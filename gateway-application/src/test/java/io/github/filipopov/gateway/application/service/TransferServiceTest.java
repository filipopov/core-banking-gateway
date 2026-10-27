package io.github.filipopov.gateway.application.service;

import static io.github.filipopov.gateway.application.service.Fakes.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private final Fakes.RecordingProvider provider = new Fakes.RecordingProvider();
    private final TransferRequest request =
            new TransferRequest(AccountId.of("A1"), AccountId.of("A2"), Money.of("10.00", "CHF"), "Lunch");

    @Test
    void submitsTransferWhenFeatureIsEnabled() {
        var service = new TransferService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags(Feature.TRANSFERS));

        var result = service.submit(TENANT, request);

        assertThat(result.status()).isEqualTo(TransferStatus.BOOKED);
        assertThat(provider.calls).containsExactly(request);
    }

    @Test
    void rejectsTransferWhenFeatureIsDisabled() {
        var service = new TransferService(Fakes.providers(Map.of(TENANT, provider)), Fakes.flags(Feature.TRANSACTION_SEARCH));

        assertThatThrownBy(() -> service.submit(TENANT, request)).isInstanceOf(FeatureDisabledException.class);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void rejectsUnknownTenant() {
        var service = new TransferService(Fakes.providers(Map.of()), Fakes.flags(Feature.TRANSFERS));

        assertThatThrownBy(() -> service.submit(TenantId.of("ghost-bank"), request)).isInstanceOf(UnknownTenantException.class);
    }
}
