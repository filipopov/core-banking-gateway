package io.github.filipopov.gateway.domain.error;

/** The provider understood the transfer but refused it for a business reason the gateway has no dedicated type for. */
public class TransferRejectedException extends RuntimeException {

    public TransferRejectedException(String reason) {
        super(reason);
    }
}
