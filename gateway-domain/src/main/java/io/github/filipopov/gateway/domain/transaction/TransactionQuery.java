package io.github.filipopov.gateway.domain.transaction;

import io.github.filipopov.gateway.domain.error.DomainValidationException;
import java.util.Objects;
import java.util.Optional;

/**
 * Criteria for listing transactions of an account.
 *
 * @param searchText optional free-text filter on description/counterparty; blank is treated as absent
 */
public record TransactionQuery(PageRequest page, String searchText) {

    private static final int MAX_SEARCH_LENGTH = 100;

    public TransactionQuery {
        Objects.requireNonNull(page, "page");
        if (searchText != null) {
            searchText = searchText.strip();
            if (searchText.isEmpty()) {
                searchText = null;
            } else if (searchText.length() > MAX_SEARCH_LENGTH) {
                throw new DomainValidationException("Search text must be at most " + MAX_SEARCH_LENGTH + " characters");
            }
        }
    }

    public static TransactionQuery page(PageRequest page) {
        return new TransactionQuery(page, null);
    }

    public Optional<String> search() {
        return Optional.ofNullable(searchText);
    }

    public boolean isSearch() {
        return searchText != null;
    }
}
