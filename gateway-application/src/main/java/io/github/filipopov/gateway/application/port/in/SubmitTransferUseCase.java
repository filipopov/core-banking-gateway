package io.github.filipopov.gateway.application.port.in;

import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;

/** Executes an internal transfer. Requires the {@code transfers} feature for the tenant. */
public interface SubmitTransferUseCase {

    TransferResult submit(TenantId tenant, TransferRequest request);
}
