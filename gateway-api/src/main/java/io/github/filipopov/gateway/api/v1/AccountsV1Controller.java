package io.github.filipopov.gateway.api.v1;

import io.github.filipopov.gateway.api.v1.V1Dtos.AccountV1;
import io.github.filipopov.gateway.api.v1.V1Dtos.BalanceV1;
import io.github.filipopov.gateway.api.v1.V1Dtos.TransactionV1;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.transaction.PageRequest;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping(path = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Accounts (v1)")
class AccountsV1Controller {

    private final QueryAccountsUseCase accounts;

    AccountsV1Controller(QueryAccountsUseCase accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "List the tenant's accounts")
    List<AccountV1> list(TenantId tenant) {
        return accounts.listAccounts(tenant).stream().map(AccountV1::from).toList();
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get one account")
    AccountV1 get(TenantId tenant, @PathVariable String accountId) {
        return AccountV1.from(accounts.getAccount(tenant, AccountId.of(accountId)));
    }

    @GetMapping("/{accountId}/balances")
    @Operation(summary = "Get booked and available balance")
    BalanceV1 balances(TenantId tenant, @PathVariable String accountId) {
        return BalanceV1.from(accounts.getBalance(tenant, AccountId.of(accountId)));
    }

    @GetMapping("/{accountId}/transactions")
    @Operation(summary = "Most recent transactions",
            description = "Returns at most `limit` transactions as a plain array. v1 has no paging; use v2 for cursor paging and search.")
    List<TransactionV1> transactions(TenantId tenant, @PathVariable String accountId,
                                     @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        var query = TransactionQuery.page(PageRequest.first(limit));
        return accounts.listTransactions(tenant, AccountId.of(accountId), query).items().stream()
                .map(TransactionV1::from)
                .toList();
    }
}
