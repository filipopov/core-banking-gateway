package io.github.filipopov.gateway.adapters.ledgerline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.filipopov.gateway.adapters.provider.Cursors;
import io.github.filipopov.gateway.adapters.support.MockFiles;
import io.github.filipopov.gateway.adapters.support.TestClients;
import io.github.filipopov.gateway.application.port.out.ProviderContractException;
import io.github.filipopov.gateway.application.port.out.ProviderUnavailableException;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.AccountType;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.error.DomainValidationException;
import io.github.filipopov.gateway.domain.error.InsufficientFundsException;
import io.github.filipopov.gateway.domain.error.TransferRejectedException;
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

/** Verifies the Ledgerline adapter against the shared stubs in {@code mocks/ledgerline}. */
class LedgerlineProviderTest {

    @RegisterExtension
    static WireMockExtension ledgerline = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory(MockFiles.dir("ledgerline")))
            .build();

    private static final AccountId MAIN = AccountId.of("LL-1001");

    private LedgerlineProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LedgerlineProvider(TestClients.forBaseUrl(ledgerline.baseUrl()));
    }

    @Nested
    class Mapping {

        @Test
        void mapsSnakeCaseAccounts() {
            var accounts = provider.listAccounts();

            assertThat(accounts).hasSize(2);
            var first = accounts.getFirst();
            assertThat(first.id()).isEqualTo(MAIN);
            assertThat(first.name()).isEqualTo("Private account");
            assertThat(first.iban()).isEqualTo("CH9300762011623852957");
            assertThat(first.currency().getCurrencyCode()).isEqualTo("CHF");
            assertThat(first.type()).isEqualTo(AccountType.CURRENT);
            assertThat(accounts.get(1).type()).isEqualTo(AccountType.SAVINGS);
        }

        @Test
        void convertsMinorUnitsToMoney() {
            var balance = provider.getBalance(MAIN);

            assertThat(balance.booked()).isEqualTo(Money.of("12540.75", "CHF"));
            assertThat(balance.available()).isEqualTo(Money.of("12040.75", "CHF"));
        }

        @Test
        void mapsTransactionsIncludingMissingCounterparty() {
            var page = provider.listTransactions(MAIN, TransactionQuery.page(PageRequest.first(3)));

            Transaction salary = page.items().get(1);
            assertThat(salary.amount()).isEqualTo(Money.of("6500.00", "CHF"));
            assertThat(salary.bookingDate()).isEqualTo(LocalDate.of(2026, 10, 18));
            assertThat(salary.counterparty()).contains("Example Employer Ltd");
            assertThat(page.items().get(2).counterparty()).isEmpty();
        }
    }

    @Nested
    class Paging {

        @Test
        void translatesOffsetPagingIntoOpaqueCursors() {
            var first = provider.listTransactions(MAIN, TransactionQuery.page(PageRequest.first(3)));

            assertThat(first.items()).extracting(Transaction::id).containsExactly("LT-5005", "LT-5004", "LT-5003");
            assertThat(first.nextCursor()).isNotNull().doesNotContain("offset");

            var second = provider.listTransactions(MAIN, TransactionQuery.page(new PageRequest(first.nextCursor(), 3)));

            assertThat(second.items()).extracting(Transaction::id).containsExactly("LT-5002", "LT-5001");
            assertThat(second.hasMore()).isFalse();
            ledgerline.verify(getRequestedFor(urlPathEqualTo("/v3/accounts/LL-1001/transactions"))
                    .withQueryParam("offset", equalTo("3"))
                    .withQueryParam("limit", equalTo("3")));
        }

        @Test
        void passesSearchTextAsSearchParameter() {
            var page = provider.listTransactions(MAIN, new TransactionQuery(PageRequest.first(25), "coffee"));

            assertThat(page.items()).extracting(Transaction::id).containsExactly("LT-5005", "LT-5001");
            ledgerline.verify(getRequestedFor(urlPathEqualTo("/v3/accounts/LL-1001/transactions"))
                    .withQueryParam("search", equalTo("coffee")));
        }

        @Test
        void rejectsMalformedOrForeignCursorsWithoutCallingUpstream() {
            String foreignCursor = new Cursors("cf").encode("pt-2");

            assertThatThrownBy(() -> provider.listTransactions(MAIN, TransactionQuery.page(new PageRequest("not-base64!", 3))))
                    .isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> provider.listTransactions(MAIN, TransactionQuery.page(new PageRequest(foreignCursor, 3))))
                    .isInstanceOf(DomainValidationException.class);
            ledgerline.verify(0, getRequestedFor(urlPathEqualTo("/v3/accounts/LL-1001/transactions")));
        }
    }

    @Nested
    class Transfers {

        @Test
        void sendsAmountInMinorUnitsAndMapsStatus() {
            var result = provider.transfer(new TransferRequest(MAIN, AccountId.of("LL-1002"), Money.of("25.00", "CHF"), "Savings"));

            assertThat(result.transferId()).isEqualTo("LTR-80001");
            assertThat(result.status()).isEqualTo(TransferStatus.BOOKED);
            ledgerline.verify(postRequestedFor(urlPathEqualTo("/v3/transfers")).withRequestBody(equalToJson("""
                    {"from_account_id": "LL-1001", "to_account_id": "LL-1002", "amount_minor": 2500,
                     "currency_code": "CHF", "reference": "Savings"}""")));
        }

        @Test
        void translatesInsufficientFunds() {
            var request = new TransferRequest(MAIN, AccountId.of("LL-1002"), Money.of("10000.00", "CHF"), null);

            assertThatThrownBy(() -> provider.transfer(request))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, e -> assertThat(e.accountId()).isEqualTo(MAIN));
        }

        @Test
        void translatesUnknownCreditorUsingTheAccountReferencedByTheProvider() {
            var request = new TransferRequest(MAIN, AccountId.of("LL-9999"), Money.of("1.00", "CHF"), null);

            assertThatThrownBy(() -> provider.transfer(request))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.accountId()).isEqualTo(AccountId.of("LL-9999")));
        }

        @Test
        void translatesDeclinedTransfer() {
            ledgerline.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/v3/transfers").atPriority(1)
                    .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
                            .withBody("{\"error\": {\"code\": \"TRANSFER_DECLINED\", \"message\": \"Blocked\"}}")));

            assertThatThrownBy(() -> provider.transfer(new TransferRequest(MAIN, AccountId.of("LL-1002"), Money.of("1.00", "CHF"), null)))
                    .isInstanceOf(TransferRejectedException.class);
        }
    }

    @Nested
    class Errors {

        @Test
        void translatesUnknownAccount() {
            assertThatThrownBy(() -> provider.getAccount(AccountId.of("LL-4040")))
                    .isInstanceOfSatisfying(AccountNotFoundException.class,
                            e -> assertThat(e.accountId()).isEqualTo(AccountId.of("LL-4040")));
        }

        @Test
        void serverErrorMeansProviderUnavailable() {
            ledgerline.stubFor(get("/v3/accounts").atPriority(1).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderUnavailableException.class);
        }

        @Test
        void slowResponseIsCutOffByReadTimeout() {
            ledgerline.stubFor(get("/v3/accounts").atPriority(1).willReturn(aResponse()
                    .withFixedDelay((int) TestClients.READ_TIMEOUT.toMillis() * 3)
                    .withHeader("Content-Type", "application/json").withBody("{\"accounts\": []}")));

            assertThatThrownBy(provider::listAccounts)
                    .isInstanceOf(ProviderUnavailableException.class)
                    .hasMessageContaining("not reachable");
        }

        @Test
        void unknownErrorCodeIsAContractViolation() {
            ledgerline.stubFor(get("/v3/accounts").atPriority(1).willReturn(aResponse().withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": {\"code\": \"SOMETHING_NEW\", \"message\": \"?\"}}")));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderContractException.class);
        }

        @Test
        void nonJsonErrorBodyIsAContractViolation() {
            ledgerline.stubFor(get("/v3/accounts").atPriority(1).willReturn(aResponse().withStatus(403)
                    .withHeader("Content-Type", "text/html").withBody("<html>Forbidden</html>")));

            assertThatThrownBy(provider::listAccounts).isInstanceOf(ProviderContractException.class);
        }

        @Test
        void payloadViolatingDomainInvariantsIsAContractViolationNotAClientError() {
            ledgerline.stubFor(get("/v3/accounts/LL-1001/balance").atPriority(1).willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"account_id\": \"LL-1001\", \"currency_code\": \"???\", \"booked_minor\": 1, \"available_minor\": 1}")));

            assertThatThrownBy(() -> provider.getBalance(MAIN)).isInstanceOf(ProviderContractException.class);
        }
    }
}
