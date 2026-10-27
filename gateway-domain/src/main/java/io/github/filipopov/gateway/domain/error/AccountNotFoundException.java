package io.github.filipopov.gateway.domain.error;

import io.github.filipopov.gateway.domain.account.AccountId;

/** The account does not exist at the provider, or is not visible to the caller. */
public class AccountNotFoundException extends RuntimeException {

    private final AccountId accountId;

    public AccountNotFoundException(AccountId accountId) {
        super("Account '%s' was not found".formatted(accountId.value()));
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
