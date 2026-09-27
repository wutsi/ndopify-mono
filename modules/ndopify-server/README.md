# ndopify-server

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-pr.yml)

[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-server-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-server-jacoco.svg)

REST API backend (Spring Boot) for Ndopify — mobile-money agent management, KYC verification, and mobile-money
gateway integration. Depends on [ndopify-dto](../ndopify-dto) for shared contracts.

## Domains

Code is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same layering
(`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

| Domain     | Description                                                                                                                                              |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `refdata`  | Shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`                                                              |
| `security` | `User` accounts, `UserApplication` roles, JWT issuance, pluggable authentication (password/Google One Tap)                                               |
| `agent`    | Mobile-money agents and their KYC lifecycle — mobile number and identity change requests, verified via a `MoMoGateway` and reconciled by a scheduled job |
| `error`    | Shared exception types and the global `ControllerErrorHandler`                                                                                           |

A non-domain **`platform.momo`** package holds the mobile-money gateway integration (`MoMoGateway`,
`MoMoGatewayFactory`, MTN-specific implementation), used by the `agent` domain to verify KYC data.

Tenancy is request/token-scoped via the `X-Tenant-ID` header and a `tenantId` JWT claim; only the `agent` domain
currently persists a `tenantId` column and filters by it explicitly in its service layer.

## Build, Test & Run

Requires JDK 17, Maven 3.9+, and a local MySQL 8+ instance (`root` user, no password) — endpoint tests run against a
real, Flyway-migrated database.

```bash
cd modules/ndopify-server

mvn package                     # build
mvn test                        # run all tests
mvn test -Dtest=ClassName       # run a single test class
mvn verify                      # run tests + JaCoCo coverage gate

mvn spring-boot:run              # start on port 8080, Swagger UI at /api.html
```

First-time local setup requires importing reference data:

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

See the repo root [CLAUDE.md](../../CLAUDE.md) for detailed architecture notes and conventions.
