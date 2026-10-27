package io.github.filipopov.gateway.domain.transfer;

public enum TransferStatus {
    /** Executed and booked on both accounts. */
    BOOKED,
    /** Accepted by the provider, booking will follow asynchronously. */
    PENDING
}
