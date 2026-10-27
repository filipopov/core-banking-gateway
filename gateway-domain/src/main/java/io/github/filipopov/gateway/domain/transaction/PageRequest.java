package io.github.filipopov.gateway.domain.transaction;

import io.github.filipopov.gateway.domain.error.DomainValidationException;

/**
 * Cursor-based page request. The cursor is opaque to callers: each provider adapter decides how to encode its native
 * paging (offset, page token, ...) into it.
 *
 * @param cursor {@code null} for the first page
 */
public record PageRequest(String cursor, int size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainValidationException("Page size must be between 1 and " + MAX_SIZE);
        }
        if (cursor != null && cursor.isBlank()) {
            cursor = null;
        }
    }

    public static PageRequest first(int size) {
        return new PageRequest(null, size);
    }

    public boolean isFirstPage() {
        return cursor == null;
    }
}
