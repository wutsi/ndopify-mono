# ndopify-server

REST API backend for Ndopify — a multi-tenant service for managing mobile-money agents, their KYC verification,
party/identification records, payment methods, and mobile-money payment integrations across Africa.

Built with Kotlin + Spring Boot. Depends on [ndopify-dto](../ndopify-dto) for shared request/response contracts.

## Domains

Code is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same layering
(`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

| Domain       | Responsibility                                                                                    |
|--------------|-----------------------------------------------------------------------------------------------------|
| **refdata**  | Shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`. Global, not tenant-scoped. |
| **security** | `User` accounts, `UserApplication` membership/roles, JWT issuance (`AccessTokenService`), pluggable authentication (`PasswordAuthenticator`, `GoogleOneTapAuthenticator`). |
| **agent**    | Mobile-money agents and their KYC lifecycle — mobile number changes and identity document changes, verified via a `MoMoGateway` and reconciled by scheduled jobs (`MobileChangeJobs`). Tenant-scoped by explicit `tenantId` column + service-level predicate (no framework-level enforcement). |
| **party**    | Parties, identifications (with uploaded images), KYC cases/verifications, and payment methods. KYC cases are verified via pluggable `KycVerifier` strategies (`KycVerifierIdentification`, `KycVerifierMoMo`, `KycVerifierManual`). |
| **error**    | Shared exception types (`WutsiException` subclasses) and a global `ControllerErrorHandler`. |

A non-domain **`platform`** package holds infrastructure shared across domains:

- `platform.momo` — mobile-money gateway integration (`MoMoGateway`, `MoMoGatewayFactory`); only the MTN
  implementation (`momo.mtn`) is wired up today.
- `platform.storage` — file storage abstraction (`StorageService`, `StorageServiceProvider`) with `local` (disk +
  servlet) and `s3` implementations, selected via `ndopify.storage.default-type`.
- `platform.logger` — structured key-value request logging (`KVLogger`, `KVLoggerFilter`).
- `platform.config` — Spring `@Configuration` classes wiring the above.

See the root [CLAUDE.md](../../CLAUDE.md) for architecture notes aimed at AI-assisted development, and
[Multi-Tenancy](../../CLAUDE.md#multi-tenancy--current-state) for the tenancy model in detail.

## Environment Setup

Required for local development:

- JDK 17, Maven 3.9+
- MySQL 8+, with the `root` user having **no password** (matches the default `application.yml` datasource)

No RabbitMQ or messaging broker is wired up today. MTN Mobile Money credentials
(`ndopify.mobile-money.mtn.*`) default to sandbox values and don't need to be set for local dev unless exercising
real MTN calls. Local file storage defaults to `~/ndopify` (`ndopify.storage.local.directory`); S3 is only used when
`ndopify.storage.default-type` is set to `s3`.

## Running the Server

```bash
mvn spring-boot:run          # starts on port 8080, Swagger UI at /api.html
```

First-time local setup requires importing reference data (see `LocationEndpoints` / `ApplicationEndpoints`):

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

## Build & Test

```bash
# Build this module
mvn package

# Run all tests (requires a local MySQL instance — endpoint tests use TestRestTemplate against a
# real, Flyway-migrated database)
mvn test

# Run a single test class / method
mvn test -Dtest=ClassName
mvn test -Dtest=ClassName#methodName
```

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
mvn verify                            # JaCoCo coverage gate (build-breaking)
```

This module overrides the parent POM's coverage thresholds — see `jacoco.threshold.line` / `jacoco.threshold.class`
in this module's `pom.xml` (and the parent `pom.xml` for the defaults it overrides) for the current values; they
change over time so are not duplicated here.

## Testing Patterns

- **Assertions**: use `kotlin.test` (`assertEquals`, `assertTrue`, `assertFailsWith`, etc.), never
  `org.junit.jupiter.api.Assertions.*`.
- **Base classes** (`src/test/kotlin/com/wutsi/ndopify/`): `BaseEndpointIntegrationTest` (`TestRestTemplate` +
  `X-Device-ID` header) and `TenantAwareEndpointIntegrationTest` (adds `X-Tenant-ID: 1`).
- **Mocking beans** in `@SpringBootTest` integration tests: use `@MockitoBean`
  (`org.springframework.test.context.bean.override.mockito.MockitoBean`), not the removed `@MockBean`.
- **SQL fixtures** live under `src/test/resources/db/test/<domain>/`, named after the test class
  (`FooEndpointTest.kt` → `FooEndpoint.sql`).

## Database Migrations

Flyway-managed, under `src/main/resources/db/migration/`, versioned as `V{version}__{description}.sql`. Never
modify an existing migration — always add a new one.
