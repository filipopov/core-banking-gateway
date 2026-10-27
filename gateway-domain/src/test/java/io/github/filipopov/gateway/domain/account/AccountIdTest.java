package io.github.filipopov.gateway.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountIdTest {

    @Test
    void acceptsProviderStyleIdentifiers() {
        assertThat(AccountId.of("LL-1001").value()).isEqualTo("LL-1001");
        assertThat(AccountId.of("cf_acc.2001").value()).isEqualTo("cf_acc.2001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../admin", "a/b", "id?x=1", "with space", "%2e%2e"})
    void rejectsValuesThatCouldManipulateUpstreamUrls(String value) {
        assertThatThrownBy(() -> AccountId.of(value)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsOverlongIdentifiers() {
        assertThatThrownBy(() -> AccountId.of("a".repeat(65))).isInstanceOf(DomainValidationException.class);
    }
}
