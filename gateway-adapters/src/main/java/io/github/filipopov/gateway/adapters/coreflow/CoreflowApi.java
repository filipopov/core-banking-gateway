package io.github.filipopov.gateway.adapters.coreflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Wire model of the (fictitious) Coreflow core banking API: camelCase JSON wrapped in a {@code data} envelope, nested
 * objects, decimal amounts as strings, page-token paging and errors shaped as
 * {@code {"faults": [{"faultCode", "detail", "ref"}]}}.
 */
final class CoreflowApi {

    private CoreflowApi() {
    }

    public record Envelope<T>(T data) {
    }

    /** Coreflow transports decimals as JSON strings to avoid floating point issues in its clients. */
    public record AmountDto(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value, String currency) {
    }

    public record IdentifiersDto(String iban) {
    }

    public record AccountDto(String id, String name, String type, String currency, IdentifiersDto identifiers) {
    }

    public record BalanceEntryDto(String type, AmountDto amount) {
    }

    public record BalancesDto(List<BalanceEntryDto> balances) {
    }

    public record CounterpartyDto(String name) {
    }

    public record TransactionDto(String id, OffsetDateTime bookedAt, AmountDto amount, String description,
                                 CounterpartyDto counterparty) {
    }

    public record PagingDto(String nextPageToken) {
    }

    public record TransactionPageDto(List<TransactionDto> data, PagingDto paging) {
    }

    public record AccountRefDto(String accountId) {
    }

    public record PaymentRequestDto(AccountRefDto debtor, AccountRefDto creditor, AmountDto instructedAmount,
                                    String remittance) {
    }

    public record PaymentDto(String paymentId, String state) {
    }

    public record FaultDto(String faultCode, String detail, String ref) {
    }

    public record FaultsDto(List<FaultDto> faults) {
    }
}
