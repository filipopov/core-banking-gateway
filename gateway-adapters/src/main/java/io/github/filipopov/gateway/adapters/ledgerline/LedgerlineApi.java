package io.github.filipopov.gateway.adapters.ledgerline;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire model of the (fictitious) Ledgerline core banking API, version 3: snake_case JSON, amounts as integer minor
 * units, offset/limit paging and errors shaped as {@code {"error": {"code", "message", "account_id"}}}.
 */
final class LedgerlineApi {

    private LedgerlineApi() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AccountDto(String accountId, String iban, String displayName, String currencyCode, String accountType) {
    }

    public record AccountListDto(List<AccountDto> accounts) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BalanceDto(String accountId, String currencyCode, Long bookedMinor, Long availableMinor) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransactionDto(String transactionId, LocalDate bookingDate, Long amountMinor, String currencyCode,
                                 String remittanceInfo, String counterpartyName) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransactionListDto(List<TransactionDto> transactions, int offset, int limit, int totalCount) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransferRequestDto(String fromAccountId, String toAccountId, long amountMinor, String currencyCode,
                                     String reference) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TransferResponseDto(String transferId, String status) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ErrorDto(String code, String message, String accountId) {
    }

    public record ErrorEnvelopeDto(ErrorDto error) {
    }
}
