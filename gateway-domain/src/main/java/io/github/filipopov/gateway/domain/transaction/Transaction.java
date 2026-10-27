package io.github.filipopov.gateway.domain.transaction;

import io.github.filipopov.gateway.domain.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A booked account transaction. Debits carry a negative amount, credits a positive one.
 *
 * @param counterpartyName may be {@code null} when the provider does not supply it
 */
public record Transaction(String id, LocalDate bookingDate, Money amount, String description, String counterpartyName) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bookingDate, "bookingDate");
        Objects.requireNonNull(amount, "amount");
        description = description == null ? "" : description;
    }

    public Optional<String> counterparty() {
        return Optional.ofNullable(counterpartyName);
    }
}
