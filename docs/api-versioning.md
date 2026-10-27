# API versioning

The gateway uses two complementary mechanisms. They answer different questions.

| | URL versions (`/api/v1`, `/api/v2`) | Release routing (`X-Api-Version` header, nginx) |
|---|---|---|
| Question | *Which contract does the client speak?* | *Which build of the gateway serves the client?* |
| Lives in | One deployment, several controller packages | Several deployments side by side |
| Changes when | A breaking change to the wire format | Any release, including bug fixes and refactorings |
| Implemented in | `gateway-api` (`v1`, `v2` packages) | `deploy/nginx/gateway.conf` (example, not run here) |

## v1 to v2: breaking changes

Both versions call the same use cases; only the DTOs and controllers differ. ArchUnit forbids `api.v1` and `api.v2`
from depending on each other, so a v2 change cannot leak into v1.

### 1. Money became an object with a decimal string

```jsonc
// v1: flattened, amount is a JSON number
{ "accountId": "LL-1001", "bookedAmount": 12540.75, "availableAmount": 12040.75, "currency": "CHF" }

// v2: one money object per amount, amount is a string
{ "accountId": "LL-1001",
  "booked":    { "amount": "12540.75", "currency": "CHF" },
  "available": { "amount": "12040.75", "currency": "CHF" } }
```

Why: a JSON number is parsed into a binary floating point value by most client platforms (JavaScript, many JSON
libraries), so `0.1 + 0.2` problems reach banking UIs. A string forces clients to use a decimal type, and keeping
amount and currency in one object makes it impossible to show an amount with the wrong currency.

The same applies to request bodies: `POST /api/v2/transfers` expects `"amount": {"amount": "25.00", "currency": "CHF"}`,
while v1 expects `"amount": 25.00, "currency": "CHF"`.

### 2. Transactions are cursor-paged

```jsonc
// v1: GET /api/v1/accounts/{id}/transactions?limit=25  ->  plain array, first page only
[ { "id": "LT-5005", "amount": -4.50, "currency": "CHF", ... } ]

// v2: GET /api/v2/accounts/{id}/transactions?size=25&cursor=...  ->  envelope
{ "items": [ { "id": "LT-5005", "amount": { "amount": "-4.50", "currency": "CHF" }, ... } ],
  "nextCursor": "bGw6Mw" }
```

Why: the providers page differently (Ledgerline: offset/limit, Coreflow: page tokens). An opaque cursor hides that.
Each adapter encodes its native paging state into the cursor with a provider prefix, so a cursor from one provider is
rejected by another. v2 also adds `q` for free-text search, gated by the `transaction-search` feature.

Cursors are base64url-encoded, not encrypted or signed: they are opaque by contract, not tamper-proof. A tampered
cursor can at worst request another page of the same account, which the provider authorises anyway.

## Release routing with nginx

See the README section *Running several gateway versions side by side* and
[`deploy/nginx/gateway.conf`](../deploy/nginx/gateway.conf).
