# Error catalogue

Every error response is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem document with
`Content-Type: application/problem+json`. The `type` URI points to a section of this page; the `code` member carries
the same slug for clients that prefer to switch on a short string.

```json
{
  "type": "https://github.com/filipopov/core-banking-gateway/blob/main/docs/problems.md#account-not-found",
  "title": "Account not found",
  "status": 404,
  "detail": "Account 'LL-4040' was not found",
  "instance": "/api/v2/accounts/LL-4040",
  "code": "account-not-found",
  "accountId": "LL-4040"
}
```

Upstream error messages are logged by the gateway but never forwarded to clients. Whatever format a provider uses
(Ledgerline's `{"error": {...}}`, Coreflow's `{"faults": [...]}`), clients only ever see the types below.

| Code | Status | Extra members | Meaning |
|------|--------|---------------|---------|
| [`missing-tenant`](#missing-tenant) | 400 | | `X-Tenant-Id` header absent or malformed |
| [`unknown-tenant`](#unknown-tenant) | 400 | `tenant` | No tenant with this id is configured |
| [`invalid-request`](#invalid-request) | 400 | `errors` (body validation only) | Malformed JSON, failed validation, bad cursor, domain rule violated |
| [`feature-disabled`](#feature-disabled) | 403 | `tenant`, `feature` | Feature switched off for this tenant |
| [`account-not-found`](#account-not-found) | 404 | `accountId` | Provider does not know the account |
| [`insufficient-funds`](#insufficient-funds) | 422 | `accountId` | Debtor account cannot cover the transfer |
| [`transfer-rejected`](#transfer-rejected) | 422 | | Provider declined the transfer for another business reason |
| [`provider-error`](#provider-error) | 502 | | Provider answered with something the gateway cannot interpret |
| [`provider-unavailable`](#provider-unavailable) | 503 | | Provider unreachable, timed out, 5xx, or circuit breaker open |
| [`internal-error`](#internal-error) | 500 | | Bug in the gateway |
| [`unsupported-api-version`](#unsupported-api-version) | 400 | | Emitted by the nginx edge, not the app |

## missing-tenant

Send `X-Tenant-Id: <tenant>` with every `/api/**` request. Tenant ids are lowercase kebab-case, e.g. `alpine-bank`.

## unknown-tenant

The tenant id is well-formed but not present under `gateway.tenants` in the configuration.

## invalid-request

The request cannot be processed as sent. For bean-validation failures on a JSON body, `errors` lists
`{field, message}` pairs. Domain rules (same debtor and creditor, more decimals than the currency allows, page size
out of range, cursor issued by another provider, ...) are reported with a `detail` message.

## feature-disabled

The capability exists but is switched off for this tenant (`gateway.tenants.<id>.features`). `feature` names it,
e.g. `transfers` or `transaction-search`. Retrying will not help; this is configuration, not an outage.

## account-not-found

The provider reports the account as unknown. For transfers, `accountId` is the account the provider named, which may
be the creditor.

## insufficient-funds

The available balance of `accountId` does not cover the transfer amount.

## transfer-rejected

The provider declined the transfer for a business reason the gateway has no dedicated type for.

## provider-error

The provider answered, but with an unknown error code, an unexpected status or a payload that violates the domain
model (e.g. an invalid currency). This points at contract drift between gateway and provider; it is logged as an
error and is not retried.

## provider-unavailable

The provider could not be reached, did not answer within the read timeout, answered 5xx, or its circuit breaker is
open. Read operations were already retried by the gateway; clients may retry later.

## internal-error

An unexpected exception inside the gateway. The `detail` is generic on purpose; the stack trace is in the logs.

## unsupported-api-version

Returned by the example nginx configuration in [`deploy/nginx/gateway.conf`](../deploy/nginx/gateway.conf) when the
`X-Api-Version` header names a gateway release that is not deployed.
