package io.github.filipopov.gateway.api.v2;

import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * v2 wire format. Breaking changes compared to v1: money is an object with the amount as a decimal string, and
 * transaction lists are cursor-paged envelopes.
 */
final class V2Dtos {

    private V2Dtos() {
    }

    @Schema(description = "Money. The amount is a decimal string so no client ever parses it as a floating point number.")
    record MoneyV2(
            @NotNull @Pattern(regexp = "-?\\d{1,15}(\\.\\d{1,3})?") @Schema(example = "12.50") String amount,
            @NotNull @Pattern(regexp = "[A-Z]{3}") @Schema(example = "CHF") String currency) {

        static MoneyV2 from(Money money) {
            return new MoneyV2(money.amount().toPlainString(), money.currencyCode());
        }

        Money toMoney() {
            return Money.of(amount, currency);
        }
    }

    record AccountV2(String id, String iban, String name, String currency, String type) {

        static AccountV2 from(Account account) {
            return new AccountV2(account.id().value(), account.iban(), account.name(),
                    account.currency().getCurrencyCode(), account.type().name());
        }
    }

    record BalanceV2(String accountId, MoneyV2 booked, MoneyV2 available) {

        static BalanceV2 from(Balance balance) {
            return new BalanceV2(balance.accountId().value(), MoneyV2.from(balance.booked()), MoneyV2.from(balance.available()));
        }
    }

    record TransactionV2(String id, LocalDate bookingDate, MoneyV2 amount, String description, String counterpartyName) {

        static TransactionV2 from(Transaction transaction) {
            return new TransactionV2(transaction.id(), transaction.bookingDate(), MoneyV2.from(transaction.amount()),
                    transaction.description(), transaction.counterpartyName());
        }
    }

    record TransactionPageV2(List<TransactionV2> items,
                             @Schema(description = "Pass as `cursor` to fetch the next page; null on the last page")
                             String nextCursor) {

        static TransactionPageV2 from(Page<Transaction> page) {
            return new TransactionPageV2(page.items().stream().map(TransactionV2::from).toList(), page.nextCursor());
        }
    }

    record TransferRequestV2(@NotBlank String fromAccountId,
                             @NotBlank String toAccountId,
                             @NotNull @Valid MoneyV2 amount,
                             @Size(max = 140) String reference) {
    }

    record TransferResponseV2(String transferId, String status) {

        static TransferResponseV2 from(TransferResult result) {
            return new TransferResponseV2(result.transferId(), result.status().name());
        }
    }
}
