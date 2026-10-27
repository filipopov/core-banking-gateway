package io.github.filipopov.gateway.application.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void acceptsLowercaseKebabCase() {
        assertThat(TenantId.of("alpine-bank").value()).isEqualTo("alpine-bank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Alpine", "-bank", "bank_1", "a b"})
    void rejectsMalformedIds(String value) {
        assertThatThrownBy(() -> TenantId.of(value)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void featureKeysRoundTrip() {
        for (Feature feature : Feature.values()) {
            assertThat(Feature.fromKey(feature.key())).contains(feature);
        }
        assertThat(Feature.fromKey("teleport")).isEmpty();
    }
}
