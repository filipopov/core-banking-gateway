package io.github.filipopov.gateway.application.port.in;

import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import java.util.List;

/** Read-only account use cases, independent of API version and provider. */
public interface QueryAccountsUseCase {

    List<Account> listAccounts(TenantId tenant);

    Account getAccount(TenantId tenant, AccountId accountId);

    Balance getBalance(TenantId tenant, AccountId accountId);

    /**
     * Lists transactions. A query with search text requires the {@code transaction-search} feature for the tenant.
     */
    Page<Transaction> listTransactions(TenantId tenant, AccountId accountId, TransactionQuery query);
}
