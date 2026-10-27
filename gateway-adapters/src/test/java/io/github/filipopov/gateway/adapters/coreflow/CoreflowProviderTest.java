package io.github.filipopov.gateway.adapters.coreflow;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.filipopov.gateway.adapters.provider.Cursors;
import io.github.filipopov.gateway.adapters.support.MockFiles;
import io.github.filipopov.gateway.adapters.support.TestClients;
import io.github.filipopov.gateway.application.error.ProviderContractException;
import io.github.filipopov.gateway.application.error.ProviderUnavailableException;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.AccountType;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.error.InsufficientFundsException;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.PageRequest;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Verifies the Coreflow adapter against the shared stubs in {@code mocks/coreflow}. */
class CoreflowProviderTest {

    @RegisterExtension
    static WireMockExtension coreflow = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory(MockFiles.dir("coreflow")))
            .build();

    private static final AccountId EVERYDAY = AccountId.of("cf-acc-2001");

    private CoreflowProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CoreflowProvider(TestClients.forBaseUrl(coreflow.baseUrl()));
    }

    @Nested
    class Mapping {

        @Test
        void unwrapsEnvelopeAndNestedIdentifiers() {
            var accounts = provider.listAccounts();

            assertThat(accounts).hasSize(2);
            assertThat(accounts.getFirst().id()).isEqualTo(EVERYDAY);
            assertThat(accounts.getFirst().iban()).isEqualTo("DE89370400440532013000");
            assertThat(accounts.getFirst().type()).isEqualTo(AccountType.CURRENT);
            assertThat(accounts.get(1).type()).isEqualTo(AccountType.SAVINGS);
            assertThat(provider.getAccount(EVERYDAY).name()).isEqualTo("Everyday");
        }

        @Test
        void picksBookedAndAvailableFromTypedBalanceList() {
            var balance = provider.getBalance(EVERYDAY);

            assertThat(balance.booked()).isEqualTo(Money.of("3120.40", "EUR"));
            assertThat(balance.available()).isEqualTo(Money.of("2980.40", "EUR"));
        }

        @Test
        void missingBalanceTypeIsAContractViolation() {
            coreflow.stubFor(get("/api/accounts/cf-acc-2001/balances").atPriority(1).willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"data\": {\"balances\": [{\"type\": \"closingBooked\", \"amount\": {\"value\": \"1.00\", \"currency\": \"EUR\"}}]}}")));

            assertThatThrownBy(() -> provider.getBalance(EVERYDAY)).isInstanceOf(ProviderContractException.class);
        }

        @Test
        void usesLocalDateOfTheBookingTimestamp() {
            var page = provider.listTransactions(EVERYDAY, TransactionQuery.page(PageRequest.first(2)));

            Transaction bakery = page.items().getFirst();
            assertThat(bakery.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 21));
            assertThat(bakery.amount()).isEqualTo(Money.of("-3.80", "EUR"));
            assertThat(bakery.counterparty()).contains("Corner Bakery");
        }
    }

    @Nested
    class Paging {

        @Test
        void wrapsPageTokensIntoOpaqueCursors() {
            var first = provider.listTransactions(EVERYDAY, TransactionQuery.page(PageRequest.first(2)));

            assertThat(first.nextCursor()).isNotNull().isNotEqualTo("pt-2");
            coreflow.verify(getRequestedFor(urlPathEqualTo("/api/accounts/cf-acc-2001/transactions"))
                    .withQueryParam("pageSize", equalTo("2"))
                    .withQueryParam("pageToken", absent()));

            var second = provider.listTransactions(EVERYDAY, TransactionQuery.page(new PageRequest(first.nextCursor(), 2)));

            assertThat(second.items()).extracting(Transaction::id).containsExactly("tx-9f38", "tx-9f37");
            assertThat(second.hasMore()).isFalse();
            coreflow.verify(getRequestedFor(urlPathEqualTo("/api/accounts/cf-acc-2001/transactions"))
                    .withQueryParam("pageToken", equalTo("pt-2")));
        }

        @Test
        void passesSearchTextAsTextParameter() {
            var page = provider.listTransactions(EVERYDAY, new TransactionQuery(PageRequest.first(10), "bakery"));

            assertThat(page.items()).hasSize(2);
            assertThat(page.hasMore()).isFalse();
            coreflow.verify(getRequestedFor(urlPathEqualTo("/api/accounts/cf-acc-2001/transactions"))
                    .withQueryParam("text", equalTo("bakery")));
        }

        @Test
        void rejectsCursorIssuedForAnotherProvider() {
            String ledgerlineCursor = new Cursors("ll").encode("3");

            assertThatThrownBy(() -> provider.listTransactions(EVERYDAY, TransactionQuery.page(new PageRequest(ledgerlineCursor, 2))))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class Transfers {

        @Test
        void sendsNestedDecimalPayloadAndMapsState() {
            var result = provider.transfer(new TransferRequest(EVERYDAY, AccountId.of("cf-acc-2002"), Money.of("50", "EUR"), "Holiday fund"));

            assertThat(result.transferId()).isEqualTo("pay-7c21");
            assertThat(result.status()).isEqualTo(TransferStatus.BOOKED);
            coreflow.verify(postRequestedFor(urlPathEqualTo("/api/payments/internal")).withRequestBody(equalToJson("""
                    {"debtor": {"accountId": "cf-acc-2001"}, "creditor": {"accountId": "cf-acc-2002"},
                     "instructedAmount": {"value": "50.00", "currency": "EUR"}, "remittance": "Holiday fund"}""")));
        }

        @Test
        void translatesFaultCodeForInsufficientFunds() {
            var request = new TransferRequest(EVERYDAY, AccountId.of("cf-acc-2002"), Money.of("25000.00", "EUR"), null);

            assertThatThrownBy(() -> provider.transfer(request))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, e -> assertThat(e.accountId()).isEqualTo(EVERYDAY));
        }

        @Test
        void translatesFaultCodeForUnknownCreditor() {
            var request = new TransferRequest(EVERYDAY, AccountId.of("cf-acc-9999"), Money.of("1.00", "EUR"), null);

            assertThatThrownBy(() -> provider.transfer(request))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.accountId()).isEqualTo(AccountId.of("cf-acc-9999")));
        }
    }

    @Nested
    class Errors {

        @Test
        void translatesUnknownAccount() {
            assertThatThrownBy(() -> provider.getBalance(AccountId.of("cf-acc-0000")))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.accountId()).isEqualTo(AccountId.of("cf-acc-0000")));
        }

        @Test
        void serverErrorMeansProviderUnavailable() {
            coreflow.stubFor(get("/api/accounts").atPriority(1).willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderUnavailableException.class);
        }

        @Test
        void droppedConnectionMeansProviderUnavailable() {
            coreflow.stubFor(get("/api/accounts").atPriority(1).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderUnavailableException.class);
        }

        @Test
        void slowResponseIsCutOffByReadTimeout() {
            coreflow.stubFor(get("/api/accounts").atPriority(1).willReturn(aResponse()
                    .withFixedDelay((int) TestClients.READ_TIMEOUT.toMillis() * 3)
                    .withHeader("Content-Type", "application/json").withBody("{\"data\": []}")));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderUnavailableException.class);
        }

        @Test
        void unknownFaultCodeIsAContractViolation() {
            coreflow.stubFor(get("/api/accounts").atPriority(1).willReturn(aResponse().withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"faults\": [{\"faultCode\": \"CF-1001\", \"detail\": \"pageSize invalid\"}]}")));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderContractException.class);
        }

        @Test
        void unreachableHostMeansProviderUnavailable() {
            var offline = new CoreflowProvider(TestClients.forBaseUrl("http://localhost:1"));

            assertThatThrownBy(offline::listAccounts).isInstanceOf(ProviderUnavailableException.class);
        }
    }
}
