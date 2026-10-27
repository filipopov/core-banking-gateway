package io.github.filipopov.gateway.adapters.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorsTest {

    private final Cursors cursors = new Cursors("ll");

    @Test
    void roundTripsNativeStateAsUrlSafeToken() {
        String cursor = cursors.encode("40");

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
        assertThat(cursors.decode(cursor)).isEqualTo("40");
    }

    @Test
    void rejectsGarbageEmptyStateAndOtherPrefixes() {
        String empty = Base64.getUrlEncoder().encodeToString("ll:".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> cursors.decode("%%%")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> cursors.decode(empty)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> cursors.decode(new Cursors("cf").encode("40"))).isInstanceOf(DomainValidationException.class);
    }
}
