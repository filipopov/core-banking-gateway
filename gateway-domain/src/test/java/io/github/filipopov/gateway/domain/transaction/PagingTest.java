package io.github.filipopov.gateway.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PagingTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, PageRequest.MAX_SIZE + 1})
    void rejectsPageSizeOutOfBounds(int size) {
        assertThatThrownBy(() -> PageRequest.first(size)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void blankCursorMeansFirstPage() {
        assertThat(new PageRequest("  ", 10).isFirstPage()).isTrue();
        assertThat(new PageRequest("abc", 10).isFirstPage()).isFalse();
    }

    @Test
    void pageExposesNextCursorAndIsImmutable() {
        var items = new java.util.ArrayList<>(List.of("a", "b"));
        Page<String> page = new Page<>(items, "next");
        items.add("c");

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.next()).contains("next");
        assertThat(Page.last(List.of()).hasMore()).isFalse();
    }

    @Test
    void searchTextIsTrimmedAndBlankMeansNoSearch() {
        assertThat(new TransactionQuery(PageRequest.first(5), "  coffee ").search()).contains("coffee");
        assertThat(new TransactionQuery(PageRequest.first(5), "   ").isSearch()).isFalse();
        assertThatThrownBy(() -> new TransactionQuery(PageRequest.first(5), "x".repeat(101)))
                .isInstanceOf(DomainValidationException.class);
    }
}
