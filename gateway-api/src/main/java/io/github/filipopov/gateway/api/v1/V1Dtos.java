package io.github.filipopov.gateway.api.v1;

import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * v1 wire format. Money is flattened into a JSON number plus a sibling currency field. Kept stable for existing
 * clients; see {@code docs/api-versioning.md} for what changed in v2.
 */
final class V1Dtos {

    private V1Dtos() {
    }

    record AccountV1(String id, String iban, String name, String currency, String type) {

        static AccountV1 from(Account account) {
            return new AccountV1(account.id().value(), account.iban(), account.name(),
                    account.currency().getCurrencyCode(), account.type().name());
        }
    }

    record BalanceV1(String accountId,
                     @Schema(type = "number", example = "12540.75") BigDecimal bookedAmount,
                     @Schema(type = "number", example = "12040.75") BigDecimal availableAmount,
                     String currency) {

        static BalanceV1 from(Balance balance) {
            return new BalanceV1(balance.accountId().value(), balance.booked().amount(), balance.available().amount(),
                    balance.booked().currencyCode());
        }
    }

    record TransactionV1(String id, LocalDate bookingDate,
                         @Schema(type = "number", example = "-4.50") BigDecimal amount,
                         String currency, String description, String counterpartyName) {

        static TransactionV1 from(Transaction transaction) {
            return new TransactionV1(transaction.id(), transaction.bookingDate(), transaction.amount().amount(),
                    transaction.amount().currencyCode(), transaction.description(), transaction.counterpartyName());
        }
    }

    record TransferRequestV1(@NotBlank String fromAccountId,
                             @NotBlank String toAccountId,
                             @NotNull @Schema(type = "number", example = "25.00") BigDecimal amount,
                             @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
                             @Size(max = 140) String reference) {
    }

    record TransferResponseV1(String transferId, String status) {

        static TransferResponseV1 from(TransferResult result) {
            return new TransferResponseV1(result.transferId(), result.status().name());
        }
    }
}
