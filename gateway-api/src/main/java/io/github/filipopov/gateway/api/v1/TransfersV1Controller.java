package io.github.filipopov.gateway.api.v1;

import io.github.filipopov.gateway.api.v1.V1Dtos.TransferRequestV1;
import io.github.filipopov.gateway.api.v1.V1Dtos.TransferResponseV1;
import io.github.filipopov.gateway.application.port.in.SubmitTransferUseCase;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transfers (v1)")
class TransfersV1Controller {

    private final SubmitTransferUseCase transfers;

    TransfersV1Controller(SubmitTransferUseCase transfers) {
        this.transfers = transfers;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer money between two accounts of the same bank",
            description = "Requires the `transfers` feature for the tenant.")
    TransferResponseV1 submit(TenantId tenant, @Valid @RequestBody TransferRequestV1 body) {
        var request = new TransferRequest(AccountId.of(body.fromAccountId()), AccountId.of(body.toAccountId()),
                Money.of(body.amount(), body.currency()), body.reference());
        return TransferResponseV1.from(transfers.submit(tenant, request));
    }
}
