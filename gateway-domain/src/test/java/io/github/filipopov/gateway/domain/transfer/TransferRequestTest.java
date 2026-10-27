package io.github.filipopov.gateway.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.money.Money;
import org.junit.jupiter.api.Test;

class TransferRequestTest {

    private static final AccountId FROM = AccountId.of("A1");
    private static final AccountId TO = AccountId.of("A2");

    @Test
    void acceptsValidTransfer() {
        var request = new TransferRequest(FROM, TO, Money.of("25.00", "CHF"), "Rent share");

        assertThat(request.amount()).isEqualTo(Money.of("25", "CHF"));
    }

    @Test
    void rejectsTransferToSameAccount() {
        assertThatThrownBy(() -> new TransferRequest(FROM, FROM, Money.of("1.00", "CHF"), null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> new TransferRequest(FROM, TO, Money.of("0.00", "CHF"), null))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new TransferRequest(FROM, TO, Money.of("-5.00", "CHF"), null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsOverlongReference() {
        assertThatThrownBy(() -> new TransferRequest(FROM, TO, Money.of("1.00", "CHF"), "r".repeat(141)))
                .isInstanceOf(DomainValidationException.class);
    }
}
