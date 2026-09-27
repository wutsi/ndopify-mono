# ndopify-mono

Maven monorepo for Ndopify — a multi-tenant backend for managing mobile-money agents, their KYC verification, and
mobile-money payment integrations across Africa.

## Project Status

Early-stage. Only two modules exist today — there is no portal, SDK, or tracking-server module (yet).

## Modules

| Module                                   | Description                                                    |
|------------------------------------------|----------------------------------------------------------------|
| [ndopify-dto](modules/ndopify-dto)       | Shared request/response DTOs and contracts — no business logic |
| [ndopify-server](modules/ndopify-server) | REST API backend (Spring Boot)                                 |

### Module Dependency

```
ndopify-dto  →  ndopify-server
```

## Technologies

- **Language**: Kotlin, JDK 17
- **Framework**: Spring Boot (Web, Data JPA, Security, Cache, Validation, Actuator)
- **Database**: MySQL, with Flyway for versioned schema migrations
- **API docs**: SpringDoc OpenAPI / Swagger UI
- **Build**: Maven
- **Code quality**: ktlint (formatting), JaCoCo (coverage gate)

## Architecture Overview

`ndopify-server` is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same
layering (`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

- **refdata** — shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`.
- **security** — `User` accounts, JWT issuance, and pluggable authentication (`PasswordAuthenticator`,
  `GoogleOneTapAuthenticator`).
- **agent** — mobile-money agents and their KYC lifecycle: mobile number changes and identity document changes,
  each verified through a `MoMoGateway` (currently MTN Mobile Money) and reconciled by a scheduled job.
- **error** — shared exception types and a global error handler.

A non-domain **`platform.momo`** package holds the mobile-money gateway integration (`MoMoGateway`,
`MoMoGatewayFactory`, MTN-specific implementation), used by the `agent` domain to verify KYC data.

Tenancy is request/token-scoped via the `X-Tenant-ID` header and a `tenantId` JWT claim; only the `agent` domain
currently persists a `tenantId` column and filters by it explicitly in its service layer — reference data (`refdata`,
`security`) is still global.

See [CLAUDE.md](CLAUDE.md) for detailed build/test commands and architecture notes for AI-assisted development.

## Getting Started

Requirements: JDK 17, Maven 3.9+, MySQL 8+ (local `root` user with no password).

```bash
# Build the whole monorepo
mvn install -Dheadless=true

# Run the server (starts on port 8080, Swagger UI at /api.html)
cd modules/ndopify-server
mvn spring-boot:run
```

First-time local setup requires importing reference data:

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
