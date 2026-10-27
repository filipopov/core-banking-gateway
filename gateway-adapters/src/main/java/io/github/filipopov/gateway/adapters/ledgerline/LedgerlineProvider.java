package io.github.filipopov.gateway.adapters.ledgerline;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.AccountDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.AccountListDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.BalanceDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.ErrorEnvelopeDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.TransactionDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.TransactionListDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.TransferRequestDto;
import io.github.filipopov.gateway.adapters.ledgerline.LedgerlineApi.TransferResponseDto;
import io.github.filipopov.gateway.adapters.provider.Cursors;
import io.github.filipopov.gateway.adapters.provider.UpstreamCalls;
import io.github.filipopov.gateway.adapters.provider.UpstreamError;
import io.github.filipopov.gateway.adapters.provider.UpstreamErrorException;
import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.AccountType;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.error.InsufficientFundsException;
import io.github.filipopov.gateway.domain.error.TransferRejectedException;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.github.filipopov.gateway.domain.transfer.TransferStatus;
import java.io.IOException;
import java.util.Currency;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/** Adapter for the Ledgerline core banking API. */
public final class LedgerlineProvider implements CoreBankingProvider {

    public static final String NAME = "ledgerline";

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestClient http;
    private final UpstreamCalls calls = new UpstreamCalls(NAME);
    private final Cursors cursors = new Cursors("ll");

    public LedgerlineProvider(RestClient restClient) {
        this.http = restClient.mutate()
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new UpstreamErrorException(parseError(response));
                })
                .build();
    }

    @Override
    public List<Account> listAccounts() {
        return calls.execute("listAccounts",
                () -> http.get().uri("/v3/accounts").retrieve().body(AccountListDto.class)
                        .accounts().stream().map(LedgerlineProvider::toAccount).toList(),
                error -> translate(error, null));
    }

    @Override
    public Account getAccount(AccountId accountId) {
        return calls.execute("getAccount",
                () -> toAccount(http.get().uri("/v3/accounts/{id}", accountId.value()).retrieve().body(AccountDto.class)),
                error -> translate(error, accountId));
    }

    @Override
    public Balance getBalance(AccountId accountId) {
        return calls.execute("getBalance", () -> {
            BalanceDto dto = http.get().uri("/v3/accounts/{id}/balance", accountId.value()).retrieve().body(BalanceDto.class);
            return new Balance(accountId,
                    Money.ofMinor(dto.bookedMinor(), dto.currencyCode()),
                    Money.ofMinor(dto.availableMinor(), dto.currencyCode()));
        }, error -> translate(error, accountId));
    }

    @Override
    public Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query) {
        int offset = query.page().isFirstPage() ? 0 : decodeOffset(query.page().cursor());
        int limit = query.page().size();
        return calls.execute("listTransactions", () -> {
            TransactionListDto dto = http.get()
                    .uri(uri -> {
                        uri.path("/v3/accounts/{id}/transactions")
                                .queryParam("offset", offset)
                                .queryParam("limit", limit);
                        query.search().ifPresent(text -> uri.queryParam("search", text));
                        return uri.build(accountId.value());
                    })
                    .retrieve()
                    .body(TransactionListDto.class);
            List<Transaction> items = dto.transactions().stream().map(LedgerlineProvider::toTransaction).toList();
            int nextOffset = offset + items.size();
            String next = !items.isEmpty() && nextOffset < dto.totalCount() ? cursors.encode(Integer.toString(nextOffset)) : null;
            return new Page<>(items, next);
        }, error -> translate(error, accountId));
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        var body = new TransferRequestDto(request.debtorAccount().value(), request.creditorAccount().value(),
                request.amount().toMinorUnits(), request.amount().currencyCode(), request.reference());
        return calls.execute("transfer", () -> {
            TransferResponseDto dto = http.post().uri("/v3/transfers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TransferResponseDto.class);
            return new TransferResult(dto.transferId(), toTransferStatus(dto.status()));
        }, error -> translate(error, request.debtorAccount()));
    }

    private int decodeOffset(String cursor) {
        try {
            int offset = Integer.parseInt(cursors.decode(cursor));
            if (offset < 0) {
                throw new DomainValidationException("Invalid page cursor");
            }
            return offset;
        } catch (NumberFormatException e) {
            throw new DomainValidationException("Invalid page cursor", e);
        }
    }

    private RuntimeException translate(UpstreamError error, AccountId subject) {
        AccountId account = referencedAccount(error, subject);
        return switch (error.code() == null ? "" : error.code()) {
            case "ACCOUNT_NOT_FOUND" -> account != null ? new AccountNotFoundException(account) : calls.unexpected(error);
            case "INSUFFICIENT_FUNDS" -> account != null ? new InsufficientFundsException(account) : calls.unexpected(error);
            case "TRANSFER_DECLINED" -> new TransferRejectedException("The transfer was declined by the bank");
            default -> calls.unexpected(error);
        };
    }

    private static AccountId referencedAccount(UpstreamError error, AccountId fallback) {
        if (error.accountRef() == null) {
            return fallback;
        }
        try {
            return AccountId.of(error.accountRef());
        } catch (DomainValidationException e) {
            return fallback;
        }
    }

    private static UpstreamError parseError(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        try {
            ErrorEnvelopeDto envelope = ERROR_MAPPER.readValue(response.getBody(), ErrorEnvelopeDto.class);
            if (envelope == null || envelope.error() == null) {
                return UpstreamError.unparseable(status);
            }
            return new UpstreamError(status, envelope.error().code(), envelope.error().message(), envelope.error().accountId());
        } catch (IOException e) {
            // Non-JSON error bodies (proxies, load balancers) are expected; the status alone decides the outcome.
            return UpstreamError.unparseable(status);
        }
    }

    private static Account toAccount(AccountDto dto) {
        return new Account(AccountId.of(dto.accountId()), dto.iban(), dto.displayName(),
                currency(dto.currencyCode()), toAccountType(dto.accountType()));
    }

    private static AccountType toAccountType(String value) {
        if (value == null) {
            return AccountType.OTHER;
        }
        return switch (value) {
            case "CURRENT" -> AccountType.CURRENT;
            case "SAVINGS" -> AccountType.SAVINGS;
            default -> AccountType.OTHER;
        };
    }

    private static Transaction toTransaction(TransactionDto dto) {
        return new Transaction(dto.transactionId(), dto.bookingDate(),
                Money.ofMinor(dto.amountMinor(), dto.currencyCode()), dto.remittanceInfo(), dto.counterpartyName());
    }

    private static TransferStatus toTransferStatus(String status) {
        return switch (status) {
            case "BOOKED" -> TransferStatus.BOOKED;
            case "QUEUED" -> TransferStatus.PENDING;
            default -> throw new DomainValidationException("Unknown Ledgerline transfer status " + status);
        };
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainValidationException("Invalid currency " + code, e);
        }
    }
}
