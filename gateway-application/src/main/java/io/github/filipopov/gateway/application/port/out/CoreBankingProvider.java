package io.github.filipopov.gateway.application.port.out;

import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import java.util.List;

/**
 * The port every core banking provider adapter implements. One instance serves one tenant.
 *
 * <p>Implementations translate upstream failures into domain exceptions
 * ({@link io.github.filipopov.gateway.domain.error.AccountNotFoundException} etc.),
 * {@link ProviderUnavailableException} for transport problems and 5xx responses, and
 * {@link ProviderContractException} for responses the adapter cannot interpret.
 */
public interface CoreBankingProvider {

    List<Account> listAccounts();

    Account getAccount(AccountId accountId);

    Balance getBalance(AccountId accountId);

    Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query);

    TransferResult transfer(TransferRequest request);
}
