package io.github.filipopov.gateway.api;

import static io.github.filipopov.gateway.api.ApiFixtures.ACCOUNT;
import static io.github.filipopov.gateway.api.ApiFixtures.TENANT;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.filipopov.gateway.api.common.TenantHeader;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.port.in.SubmitTransferUseCase;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.PageRequest;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.github.filipopov.gateway.domain.transfer.TransferStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The same use-case result rendered by v1 and v2: documents the breaking changes between the two versions. */
@WebMvcTest
class ApiVersionsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QueryAccountsUseCase accounts;

    @MockitoBean
    SubmitTransferUseCase transfers;

    @Nested
    class Balances {

        @Test
        void v1FlattensMoneyIntoNumbersWithSiblingCurrency() throws Exception {
            when(accounts.getBalance(TenantId.of(TENANT), ACCOUNT)).thenReturn(ApiFixtures.balance());

            mvc.perform(get("/api/v1/accounts/LL-1001/balances").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"accountId": "LL-1001", "bookedAmount": 12540.75, "availableAmount": 12040.75, "currency": "CHF"}
                            """, true));
        }

        @Test
        void v2NestsMoneyObjectsWithDecimalStrings() throws Exception {
            when(accounts.getBalance(TenantId.of(TENANT), ACCOUNT)).thenReturn(ApiFixtures.balance());

            mvc.perform(get("/api/v2/accounts/LL-1001/balances").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"accountId": "LL-1001",
                             "booked": {"amount": "12540.75", "currency": "CHF"},
                             "available": {"amount": "12040.75", "currency": "CHF"}}
                            """, true));
        }
    }

    @Nested
    class Transactions {

        @Test
        void v1ReturnsPlainArrayLimitedToFirstPage() throws Exception {
            when(accounts.listTransactions(TenantId.of(TENANT), ACCOUNT, TransactionQuery.page(PageRequest.first(2))))
                    .thenReturn(ApiFixtures.transactions("more"));

            mvc.perform(get("/api/v1/accounts/LL-1001/transactions?limit=2").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].amount").value(-4.50))
                    .andExpect(jsonPath("$[0].currency").value("CHF"))
                    .andExpect(jsonPath("$[1].counterpartyName").value(nullValue()));
        }

        @Test
        void v2ReturnsCursorPagedEnvelope() throws Exception {
            var query = new TransactionQuery(new PageRequest("abc", 2), "coffee");
            when(accounts.listTransactions(TenantId.of(TENANT), ACCOUNT, query)).thenReturn(ApiFixtures.transactions("next-cursor"));

            mvc.perform(get("/api/v2/accounts/LL-1001/transactions?cursor=abc&size=2&q=coffee").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.items[0].amount.amount").value("-4.50"))
                    .andExpect(jsonPath("$.items[0].amount.currency").value("CHF"))
                    .andExpect(jsonPath("$.nextCursor").value("next-cursor"));
        }

        @Test
        void pageSizeIsBounded() throws Exception {
            mvc.perform(get("/api/v2/accounts/LL-1001/transactions?size=500").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("invalid-request"));
        }
    }

    @Nested
    class Transfers {

        private final TransferRequest expected =
                new TransferRequest(ACCOUNT, AccountId.of("LL-1002"), Money.of("25.00", "CHF"), "Savings");

        @Test
        void v1AcceptsNumericAmount() throws Exception {
            when(transfers.submit(eq(TenantId.of(TENANT)), any())).thenReturn(new TransferResult("TR-1", TransferStatus.BOOKED));

            mvc.perform(post("/api/v1/transfers").header(TenantHeader.NAME, TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromAccountId": "LL-1001", "toAccountId": "LL-1002", "amount": 25.00, "currency": "CHF", "reference": "Savings"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"transferId\": \"TR-1\", \"status\": \"BOOKED\"}", true));
            verify(transfers).submit(TenantId.of(TENANT), expected);
        }

        @Test
        void v2AcceptsMoneyObject() throws Exception {
            when(transfers.submit(eq(TenantId.of(TENANT)), any())).thenReturn(new TransferResult("TR-2", TransferStatus.PENDING));

            mvc.perform(post("/api/v2/transfers").header(TenantHeader.NAME, TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromAccountId": "LL-1001", "toAccountId": "LL-1002",
                                     "amount": {"amount": "25.00", "currency": "CHF"}, "reference": "Savings"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"));
            verify(transfers).submit(TenantId.of(TENANT), expected);
        }

        @Test
        void v2RejectsV1StyleNumericAmountWithFieldErrors() throws Exception {
            mvc.perform(post("/api/v2/transfers").header(TenantHeader.NAME, TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fromAccountId": "LL-1001", "toAccountId": "", "amount": {"amount": "1.5.0", "currency": "chf"}}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-request"))
                    .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder(
                            "toAccountId", "amount.amount", "amount.currency")));
        }
    }
}
