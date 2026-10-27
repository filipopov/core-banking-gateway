package io.github.filipopov.gateway.domain.error;

import io.github.filipopov.gateway.domain.account.AccountId;

/** The debtor account cannot cover the requested transfer amount. */
public class InsufficientFundsException extends RuntimeException {

    private final AccountId accountId;

    public InsufficientFundsException(AccountId accountId) {
        super("Account '%s' has insufficient funds for this transfer".formatted(accountId.value()));
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
