package io.github.filipopov.gateway.api;

import static io.github.filipopov.gateway.api.ApiFixtures.ACCOUNT;
import static io.github.filipopov.gateway.api.ApiFixtures.TENANT;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.filipopov.gateway.api.common.TenantHeader;
import io.github.filipopov.gateway.application.error.ProviderContractException;
import io.github.filipopov.gateway.application.error.ProviderUnavailableException;
import io.github.filipopov.gateway.application.feature.Feature;
import io.github.filipopov.gateway.application.feature.FeatureDisabledException;
import io.github.filipopov.gateway.application.port.in.QueryAccountsUseCase;
import io.github.filipopov.gateway.application.port.in.SubmitTransferUseCase;
import io.github.filipopov.gateway.application.tenant.TenantId;
import io.github.filipopov.gateway.application.tenant.UnknownTenantException;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.error.InsufficientFundsException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Every failure is rendered as an RFC 9457 problem document with a stable {@code type} and {@code code}. */
@WebMvcTest
class ProblemResponsesTest {

    private static final String TYPE_BASE = "https://github.com/filipopov/core-banking-gateway/blob/main/docs/problems.md#";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QueryAccountsUseCase accounts;

    @MockitoBean
    SubmitTransferUseCase transfers;

    private ResultActions getAccount(String tenant) throws Exception {
        var request = get("/api/v2/accounts/LL-1001");
        if (tenant != null) {
            request.header(TenantHeader.NAME, tenant);
        }
        return mvc.perform(request);
    }

    private static void assertProblem(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TYPE_BASE + code))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/api/v2/accounts/LL-1001"));
    }

    @Nested
    class TenantResolution {

        @Test
        void missingTenantHeader() throws Exception {
            var result = getAccount(null);

            assertProblem(result, 400, "missing-tenant");
            result.andExpect(jsonPath("$.detail").value(containsString(TenantHeader.NAME)));
            verifyNoInteractions(accounts);
        }

        @Test
        void malformedTenantHeader() throws Exception {
            assertProblem(getAccount("Alpine Bank!"), 400, "missing-tenant");
            verifyNoInteractions(accounts);
        }

        @Test
        void unknownTenant() throws Exception {
            when(accounts.getAccount(TenantId.of("ghost-bank"), ACCOUNT)).thenThrow(new UnknownTenantException(TenantId.of("ghost-bank")));

            var result = getAccount("ghost-bank");

            assertProblem(result, 400, "unknown-tenant");
            result.andExpect(jsonPath("$.tenant").value("ghost-bank"));
        }
    }

    @Nested
    class DomainAndProviderErrors {

        @Test
        void accountNotFound() throws Exception {
            when(accounts.getAccount(TenantId.of(TENANT), ACCOUNT)).thenThrow(new AccountNotFoundException(ACCOUNT));

            var result = getAccount(TENANT);

            assertProblem(result, 404, "account-not-found");
            result.andExpect(jsonPath("$.accountId").value("LL-1001"));
        }

        @Test
        void providerUnavailableDoesNotLeakInternals() throws Exception {
            when(accounts.getAccount(TenantId.of(TENANT), ACCOUNT))
                    .thenThrow(new ProviderUnavailableException("ledgerline", "ledgerline is not reachable: Connection refused 10.0.0.7"));

            var result = getAccount(TENANT);

            assertProblem(result, 503, "provider-unavailable");
            result.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("10.0.0.7"))));
        }

        @Test
        void providerContractViolationIsBadGateway() throws Exception {
            when(accounts.getAccount(TenantId.of(TENANT), ACCOUNT)).thenThrow(new ProviderContractException("coreflow", "bad payload"));

            assertProblem(getAccount(TENANT), 502, "provider-error");
        }

        @Test
        void unexpectedExceptionIsGenericInternalError() throws Exception {
            when(accounts.getAccount(TenantId.of(TENANT), ACCOUNT)).thenThrow(new IllegalStateException("secret detail"));

            var result = getAccount(TENANT);

            assertProblem(result, 500, "internal-error");
            result.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("secret"))));
        }

        @Test
        void invalidAccountIdIsInvalidRequest() throws Exception {
            mvc.perform(get("/api/v2/accounts/bad%20id").header(TenantHeader.NAME, TENANT))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-request"));
        }
    }

    @Nested
    class Transfers {

        private static final String BODY = """
                {"fromAccountId": "LL-1001", "toAccountId": "LL-1002", "amount": {"amount": "10.00", "currency": "CHF"}}
                """;

        @Test
        void featureDisabled() throws Exception {
            when(transfers.submit(any(), any())).thenThrow(new FeatureDisabledException(TenantId.of(TENANT), Feature.TRANSFERS));

            mvc.perform(post("/api/v2/transfers").header(TenantHeader.NAME, TENANT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("feature-disabled"))
                    .andExpect(jsonPath("$.feature").value("transfers"))
                    .andExpect(jsonPath("$.tenant").value(TENANT));
        }

        @Test
        void insufficientFunds() throws Exception {
            when(transfers.submit(any(), any())).thenThrow(new InsufficientFundsException(ACCOUNT));

            mvc.perform(post("/api/v2/transfers").header(TenantHeader.NAME, TENANT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("insufficient-funds"));
        }

        @Test
        void sameAccountTransferIsRejectedByDomainBeforeAnyCall() throws Exception {
            mvc.perform(post("/api/v2/transfers").header(TenantHeader.NAME, TENANT).contentType(MediaType.APPLICATION_JSON).content("""
                            {"fromAccountId": "LL-1001", "toAccountId": "LL-1001", "amount": {"amount": "10.00", "currency": "CHF"}}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-request"))
                    .andExpect(jsonPath("$.detail").value(containsString("must differ")));
            verifyNoInteractions(transfers);
        }

        @Test
        void malformedJsonIsInvalidRequest() throws Exception {
            mvc.perform(post("/api/v1/transfers").header(TenantHeader.NAME, TENANT).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("invalid-request"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/transfers"));
        }
    }
}
