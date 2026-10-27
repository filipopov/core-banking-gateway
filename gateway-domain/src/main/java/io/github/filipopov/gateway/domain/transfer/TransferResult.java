package io.github.filipopov.gateway.domain.transfer;

import java.util.Objects;

public record TransferResult(String transferId, TransferStatus status) {

    public TransferResult {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(status, "status");
    }
}
