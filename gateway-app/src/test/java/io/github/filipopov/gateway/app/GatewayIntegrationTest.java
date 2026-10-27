package io.github.filipopov.gateway.app;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Full stack: HTTP API &rarr; use cases &rarr; real adapters &rarr; WireMock providers loaded from {@code mocks/}.
 * Tenants come from {@code application.yml}; only the base URLs are pointed at the random WireMock ports.
 *
 * <p>Deliberately not split into {@code @Nested} classes: nested classes would restart the static WireMock servers on
 * new ports while the cached Spring context still points at the old ones.
 */
@SpringBootTest(properties = {
        "gateway.resilience.retry-wait=10ms",
        "gateway.resilience.minimum-number-of-calls=100"
})
@AutoConfigureMockMvc
class GatewayIntegrationTest {

    @RegisterExtension
    static WireMockExtension ledgerline = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory(MockFiles.dir("ledgerline")))
            .build();

    @RegisterExtension
    static WireMockExtension coreflow = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderDirectory(MockFiles.dir("coreflow")))
            .build();

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.tenants.alpine-bank.base-url", ledgerline::baseUrl);
        registry.add("gateway.tenants.meadow-bank.base-url", ledgerline::baseUrl);
        registry.add("gateway.tenants.harbor-bank.base-url", coreflow::baseUrl);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private ResultActions get(String tenant, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).header("X-Tenant-Id", tenant));
    }

    private ResultActions transfer(String tenant, String body) throws Exception {
        return mvc.perform(post("/api/v2/transfers").header("X-Tenant-Id", tenant)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- one api many providers ----

    @ParameterizedTest(name = "{0} via {1}")
    @CsvSource({
        "alpine-bank, ledgerline, LL-1001, 12540.75, CHF",
        "harbor-bank, coreflow,   cf-acc-2001, 3120.40, EUR",
    })
    void sameV2ShapeRegardlessOfProvider(String tenant, String provider, String account, String booked, String currency) throws Exception {
        get(tenant, "/api/v2/accounts/" + account + "/balances")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account))
                .andExpect(jsonPath("$.booked.amount").value(booked))
                .andExpect(jsonPath("$.booked.currency").value(currency));
        get(tenant, "/api/v2/accounts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("CURRENT"))
                .andExpect(jsonPath("$[1].type").value("SAVINGS"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "alpine-bank, LL-1001, 5",
        "harbor-bank, cf-acc-2001, 4",
    })
    void cursorPagingWalksOffsetAndTokenBasedProvidersAlike(String tenant, String account, int total) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String path = "/api/v2/accounts/" + account + "/transactions?size=3" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = objectMapper.readTree(get(tenant, path).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            page.get("items").forEach(item -> ids.add(item.get("id").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(ids).hasSize(total).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(2);
    }

    @Test
    void v1KeepsLegacyShapeOnTheSameData() throws Exception {
        get("harbor-bank", "/api/v1/accounts/cf-acc-2001/transactions?limit=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount").value(-3.80))
                .andExpect(jsonPath("$[0].currency").value("EUR"));
    }

    @Test
    void unknownAccountIsTheSameProblemForEveryProvider() throws Exception {
        for (String[] tenantAndAccount : new String[][] {{"alpine-bank", "LL-0000"}, {"harbor-bank", "cf-acc-0000"}}) {
            get(tenantAndAccount[0], "/api/v2/accounts/" + tenantAndAccount[1])
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("account-not-found"))
                    .andExpect(jsonPath("$.accountId").value(tenantAndAccount[1]));
        }
    }

    // ---- feature flags ----

    @Test
    void searchEnabledForAlpine() throws Exception {
        get("alpine-bank", "/api/v2/accounts/LL-1001/transactions?q=coffee")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void searchDisabledForHarborWithoutCallingTheProvider() throws Exception {
        get("harbor-bank", "/api/v2/accounts/cf-acc-2001/transactions?q=bakery")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("feature-disabled"))
                .andExpect(jsonPath("$.feature").value("transaction-search"));
        coreflow.verify(0, getRequestedFor(urlPathEqualTo("/api/accounts/cf-acc-2001/transactions")));
    }

    @Test
    void transfersEnabledForAlpineAndHarbor() throws Exception {
        transfer("alpine-bank", """
                {"fromAccountId": "LL-1001", "toAccountId": "LL-1002", "amount": {"amount": "25.00", "currency": "CHF"}}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId").value("LTR-80001"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
        transfer("harbor-bank", """
                {"fromAccountId": "cf-acc-2001", "toAccountId": "cf-acc-2002", "amount": {"amount": "25.00", "currency": "EUR"}}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId").value("pay-7c21"));
    }

    @Test
    void transfersDisabledForMeadowEvenThoughItSharesAlpinesProvider() throws Exception {
        transfer("meadow-bank", """
                {"fromAccountId": "LL-1001", "toAccountId": "LL-1002", "amount": {"amount": "25.00", "currency": "CHF"}}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("feature-disabled"))
                .andExpect(jsonPath("$.tenant").value("meadow-bank"));
        ledgerline.verify(0, postRequestedFor(urlPathEqualTo("/v3/transfers")));
    }

    @Test
    void readsStayAvailableWhenFeaturesAreOff() throws Exception {
        get("meadow-bank", "/api/v2/accounts/LL-1001/balances").andExpect(status().isOk());
    }

    // ---- errors ----

    @Test
    void insufficientFundsFromCoreflowFaultCode() throws Exception {
        transfer("harbor-bank", """
                {"fromAccountId": "cf-acc-2001", "toAccountId": "cf-acc-2002", "amount": {"amount": "25000.00", "currency": "EUR"}}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("insufficient-funds"))
                .andExpect(jsonPath("$.accountId").value("cf-acc-2001"));
    }

    @Test
    void providerOutageIsRetriedThenReportedAs503() throws Exception {
        coreflow.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/api/accounts").atPriority(1).willReturn(aResponse().withStatus(503)));

        get("harbor-bank", "/api/v2/accounts")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider-unavailable"))
                .andExpect(jsonPath("$.detail").value(not(containsString("coreflow"))));
        coreflow.verify(3, getRequestedFor(urlPathEqualTo("/api/accounts")));
    }

    @Test
    void unknownTenant() throws Exception {
        get("ghost-bank", "/api/v2/accounts")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unknown-tenant"));
    }

    @Test
    void missingTenant() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v2/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing-tenant"));
    }

    // ---- documentation ----

    @Test
    void separateOpenApiDocumentPerVersion() throws Exception {
        String v1 = mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs/v1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String v2 = mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs/v2")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(v1).contains("/api/v1/accounts").doesNotContain("/api/v2/");
        assertThat(v2).contains("/api/v2/accounts", "nextCursor").doesNotContain("/api/v1/");
        assertThat(v2).contains("X-Tenant-Id");
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
