package io.github.filipopov.gateway.adapters.coreflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.AccountDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.AccountRefDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.AmountDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.BalanceEntryDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.BalancesDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.Envelope;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.FaultDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.FaultsDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.PaymentDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.PaymentRequestDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.TransactionDto;
import io.github.filipopov.gateway.adapters.coreflow.CoreflowApi.TransactionPageDto;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/** Adapter for the Coreflow core banking API. */
public final class CoreflowProvider implements CoreBankingProvider {

    public static final String NAME = "coreflow";

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final ParameterizedTypeReference<Envelope<List<AccountDto>>> ACCOUNT_LIST = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Envelope<AccountDto>> ACCOUNT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Envelope<BalancesDto>> BALANCES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Envelope<PaymentDto>> PAYMENT = new ParameterizedTypeReference<>() { };

    private final RestClient http;
    private final UpstreamCalls calls = new UpstreamCalls(NAME);
    private final Cursors cursors = new Cursors("cf");

    public CoreflowProvider(RestClient restClient) {
        this.http = restClient.mutate()
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new UpstreamErrorException(parseError(response));
                })
                .build();
    }

    @Override
    public List<Account> listAccounts() {
        return calls.execute("listAccounts",
                () -> http.get().uri("/api/accounts").retrieve().body(ACCOUNT_LIST)
                        .data().stream().map(CoreflowProvider::toAccount).toList(),
                error -> translate(error, null));
    }

    @Override
    public Account getAccount(AccountId accountId) {
        return calls.execute("getAccount",
                () -> toAccount(http.get().uri("/api/accounts/{id}", accountId.value()).retrieve().body(ACCOUNT).data()),
                error -> translate(error, accountId));
    }

    @Override
    public Balance getBalance(AccountId accountId) {
        return calls.execute("getBalance", () -> {
            List<BalanceEntryDto> balances = http.get().uri("/api/accounts/{id}/balances", accountId.value())
                    .retrieve().body(BALANCES).data().balances();
            return new Balance(accountId, balanceOfType(balances, "closingBooked"), balanceOfType(balances, "interimAvailable"));
        }, error -> translate(error, accountId));
    }

    @Override
    public Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query) {
        String pageToken = query.page().isFirstPage() ? null : cursors.decode(query.page().cursor());
        return calls.execute("listTransactions", () -> {
            TransactionPageDto dto = http.get()
                    .uri(uri -> {
                        uri.path("/api/accounts/{id}/transactions").queryParam("pageSize", query.page().size());
                        if (pageToken != null) {
                            uri.queryParam("pageToken", pageToken);
                        }
                        query.search().ifPresent(text -> uri.queryParam("text", text));
                        return uri.build(accountId.value());
                    })
                    .retrieve()
                    .body(TransactionPageDto.class);
            List<Transaction> items = dto.data().stream().map(CoreflowProvider::toTransaction).toList();
            String nextToken = dto.paging() == null ? null : dto.paging().nextPageToken();
            return new Page<>(items, nextToken == null || nextToken.isBlank() ? null : cursors.encode(nextToken));
        }, error -> translate(error, accountId));
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        var body = new PaymentRequestDto(
                new AccountRefDto(request.debtorAccount().value()),
                new AccountRefDto(request.creditorAccount().value()),
                new AmountDto(request.amount().amount(), request.amount().currencyCode()),
                request.reference());
        return calls.execute("transfer", () -> {
            PaymentDto payment = http.post().uri("/api/payments/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PAYMENT)
                    .data();
            return new TransferResult(payment.paymentId(), toTransferStatus(payment.state()));
        }, error -> translate(error, request.debtorAccount()));
    }

    private RuntimeException translate(UpstreamError error, AccountId subject) {
        AccountId account = referencedAccount(error, subject);
        return switch (error.code() == null ? "" : error.code()) {
            case "CF-1404" -> account != null ? new AccountNotFoundException(account) : calls.unexpected(error);
            case "CF-2201" -> account != null ? new InsufficientFundsException(account) : calls.unexpected(error);
            case "CF-2299" -> new TransferRejectedException("The transfer was rejected by the bank");
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
            FaultsDto faults = ERROR_MAPPER.readValue(response.getBody(), FaultsDto.class);
            if (faults == null || faults.faults() == null || faults.faults().isEmpty()) {
                return UpstreamError.unparseable(status);
            }
            // Coreflow may report several faults; the first one is the primary cause.
            FaultDto first = faults.faults().getFirst();
            return new UpstreamError(status, first.faultCode(), first.detail(), first.ref());
        } catch (IOException e) {
            // Non-JSON error bodies (proxies, load balancers) are expected; the status alone decides the outcome.
            return UpstreamError.unparseable(status);
        }
    }

    private static Money balanceOfType(List<BalanceEntryDto> balances, String type) {
        return balances.stream()
                .filter(balance -> type.equals(balance.type()))
                .findFirst()
                .map(balance -> toMoney(balance.amount()))
                .orElseThrow(() -> new DomainValidationException("Coreflow balance '" + type + "' missing"));
    }

    private static Account toAccount(AccountDto dto) {
        String iban = dto.identifiers() == null ? null : dto.identifiers().iban();
        return new Account(AccountId.of(dto.id()), iban, dto.name(), currency(dto.currency()), toAccountType(dto.type()));
    }

    private static AccountType toAccountType(String value) {
        if (value == null) {
            return AccountType.OTHER;
        }
        return switch (value) {
            case "checking" -> AccountType.CURRENT;
            case "savings" -> AccountType.SAVINGS;
            default -> AccountType.OTHER;
        };
    }

    private static Transaction toTransaction(TransactionDto dto) {
        String counterparty = dto.counterparty() == null ? null : dto.counterparty().name();
        return new Transaction(dto.id(), dto.bookedAt().toLocalDate(), toMoney(dto.amount()), dto.description(), counterparty);
    }

    private static Money toMoney(AmountDto dto) {
        return Money.of(dto.value(), dto.currency());
    }

    private static TransferStatus toTransferStatus(String state) {
        return switch (state) {
            case "EXECUTED" -> TransferStatus.BOOKED;
            case "ACCEPTED" -> TransferStatus.PENDING;
            default -> throw new DomainValidationException("Unknown Coreflow payment state " + state);
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
