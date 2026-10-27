package io.github.filipopov.gateway.api.v2;

import io.github.filipopov.gateway.api.v2.V2Dtos.AccountV2;
import io.github.filipopov.gateway.api.v2.V2Dtos.BalanceV2;
import io.github.filipopov.gateway.api.v2.V2Dtos.TransactionPageV2;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.transaction.PageRequest;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts (v2)")
class AccountsV2Controller {

    private final QueryAccountsUseCase accounts;

    AccountsV2Controller(QueryAccountsUseCase accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "List the tenant's accounts")
    List<AccountV2> list(TenantId tenant) {
        return accounts.listAccounts(tenant).stream().map(AccountV2::from).toList();
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get one account")
    AccountV2 get(TenantId tenant, @PathVariable String accountId) {
        return AccountV2.from(accounts.getAccount(tenant, AccountId.of(accountId)));
    }

    @GetMapping("/{accountId}/balances")
    @Operation(summary = "Get booked and available balance")
    BalanceV2 balances(TenantId tenant, @PathVariable String accountId) {
        return BalanceV2.from(accounts.getBalance(tenant, AccountId.of(accountId)));
    }

    @GetMapping("/{accountId}/transactions")
    @Operation(summary = "List transactions, cursor-paged",
            description = "Searching with `q` requires the `transaction-search` feature for the tenant.")
    TransactionPageV2 transactions(
            TenantId tenant,
            @PathVariable String accountId,
            @Parameter(description = "Opaque cursor from a previous page's `nextCursor`") @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @Parameter(description = "Free-text search on description and counterparty") @RequestParam(required = false) String q) {
        var query = new TransactionQuery(new PageRequest(cursor, size), q);
        return TransactionPageV2.from(accounts.listTransactions(tenant, AccountId.of(accountId), query));
    }
}
