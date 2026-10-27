package io.github.filipopov.gateway.domain.transfer;

import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.money.Money;
import java.util.Objects;

/**
 * An internal transfer between two accounts of the same bank.
 *
 * @param reference free text shown on both statements, may be {@code null}
 */
public record TransferRequest(AccountId debtorAccount, AccountId creditorAccount, Money amount, String reference) {

    public static final int MAX_REFERENCE_LENGTH = 140;

    public TransferRequest {
        Objects.requireNonNull(debtorAccount, "debtorAccount");
        Objects.requireNonNull(creditorAccount, "creditorAccount");
        Objects.requireNonNull(amount, "amount");
        if (debtorAccount.equals(creditorAccount)) {
            throw new DomainValidationException("Debtor and creditor account must differ");
        }
        if (!amount.isPositive()) {
            throw new DomainValidationException("Transfer amount must be positive");
        }
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
            throw new DomainValidationException("Reference must be at most " + MAX_REFERENCE_LENGTH + " characters");
        }
    }
}
