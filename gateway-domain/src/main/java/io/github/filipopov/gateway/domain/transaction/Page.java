package io.github.filipopov.gateway.domain.transaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of results.
 *
 * @param nextCursor opaque cursor for the next page, {@code null} on the last page
 */
public record Page<T>(List<T> items, String nextCursor) {

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static <T> Page<T> last(List<T> items) {
        return new Page<>(items, null);
    }

    public Optional<String> next() {
        return Optional.ofNullable(nextCursor);
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
