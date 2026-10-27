# Adding a provider

This walks through adding a third core banking system, called **Vaultic** here, next to Ledgerline and Coreflow.
Nothing outside `gateway-adapters`, `gateway-app` (one bean) and `mocks/` changes. The domain, the use cases and both
API versions stay untouched.

## 1. Describe the upstream contract

Create `gateway-adapters/src/main/java/io/github/filipopov/gateway/adapters/vaultic/VaulticApi.java` with records for
the upstream JSON: accounts, balances, transactions, transfer request/response and the error body. Keep them
package-private so nothing outside the adapter can see the provider's wire format.

## 2. Implement the port

Create `VaulticProvider implements CoreBankingProvider`. Follow the existing adapters:

- Install a status handler that parses Vaultic's error body into an `UpstreamError` and throws
  `UpstreamErrorException`:

  ```java
  this.http = restClient.mutate()
          .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
              throw new UpstreamErrorException(parseError(response));
          })
          .build();
  ```

- Run every call through `UpstreamCalls.execute(operation, call, translator)`. It already maps timeouts, I/O errors
  and 5xx to `ProviderUnavailableException`, and unreadable or invariant-violating payloads to
  `ProviderContractException`.
- Write the `translator`: map Vaultic's error codes to `AccountNotFoundException`, `InsufficientFundsException` and
  `TransferRejectedException`; return `calls.unexpected(error)` for anything else.
- Map amounts into `Money` exactly. Use `Money.ofMinor(...)` for minor units or `Money.of(BigDecimal, ...)` for
  decimals. Never go through `double`.
- Encode Vaultic's paging state with `new Cursors("vt")` so its cursors cannot be confused with other providers'.

## 3. Add a factory

```java
public final class VaulticProviderFactory implements ProviderFactory {
    @Override public String providerName() { return "vaultic"; }
    @Override public CoreBankingProvider create(ProviderSettings settings, RestClient restClient) {
        return new VaulticProvider(restClient);
    }
}
```

The `RestClient` you get already has the tenant's base URL and timeouts. `TenantProviderRegistry` wraps your adapter
in a per-tenant circuit breaker and retry, so the adapter itself stays free of resilience code.

## 4. Register the factory

In `gateway-app/.../GatewayConfiguration.java`:

```java
@Bean
ProviderFactory vaulticProviderFactory() {
    return new VaulticProviderFactory();
}
```

## 5. Add mock stubs

Create `mocks/vaultic/mappings/*.json` with WireMock stubs for every endpoint the adapter calls, including at least
one error case per error code you translate. The same files serve local runs, docker compose and tests.

## 6. Test the adapter against the stubs

Copy `LedgerlineProviderTest` as a starting point. Load the stubs with
`wireMockConfig().dynamicPort().usingFilesUnderDirectory(MockFiles.dir("vaultic"))` and cover mapping, paging,
error translation and a read timeout. Add the new provider to the parameterised cases in `GatewayIntegrationTest` to
prove the API shape is identical to the other providers.

## 7. Point a tenant at it

```yaml
gateway:
  tenants:
    river-bank:
      provider: vaultic
      base-url: https://vaultic.example.internal
      features: [transfers]
```

A typo in `provider` or in a feature key fails application startup with a message listing the valid values.

`ArchitectureTest` will fail the build if the new adapter reaches into another provider's package or into the API
layer.
