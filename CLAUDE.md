# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

`ndopify-mono` is an early-stage Maven monorepo. The group/package is `com.wutsi.ndopify`. Only two modules
currently exist:

- `modules/ndopify-dto` — shared DTOs/contracts (no business logic)
- `modules/ndopify-server` — the REST API backend (Spring Boot)

There is no portal, SDK, platform, or tracking-server module yet. Do not assume they exist or scaffold code that
depends on them.

## Build & Test Commands

### Full Build

```bash
# Build entire monorepo
mvn install -Dheadless=true

# Build a single module
cd modules/<module-name>
mvn package
```

### Testing

```bash
# Run all tests for a module
cd modules/ndopify-server
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

Endpoint tests use `TestRestTemplate` against a real MySQL database (Flyway-migrated), so a local MySQL instance
must be running (see Environment Setup below).

### Code Quality

```bash
# ktlint runs automatically during the validate phase
mvn validate

# Auto-format code with ktlint
mvn antrun:run@ktlint-format

# Check test coverage (JaCoCo), enforced as a build-breaking gate
mvn verify
```

Coverage thresholds are set per-module and can override the parent POM's defaults — see `jacoco.threshold.line` /
`jacoco.threshold.class` in the root `pom.xml` and any per-module override (e.g. `modules/ndopify-server/pom.xml`)
for the current values; they change over time so are not duplicated here.

### Running the Server

```bash
cd modules/ndopify-server
mvn spring-boot:run          # starts on port 8080, Swagger UI at /api.html
```

First-time local setup requires importing reference data (see `LocationEndpoints` / `ApplicationEndpoints`):

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

## Environment Setup

Required for local development (there is no `CONTRIBUTING.md` in this repo — this is derived from `application.yml`
and the module POMs):

- JDK 17, Maven 3.9+
- MySQL 8+, with the `root` user having **no password** (matches the default `application.yml` datasource)

No RabbitMQ, external AI/LLM API keys, or messaging broker are wired up in `ndopify-server` today — don't assume
those integrations exist. MTN Mobile Money credentials (`ndopify.mobile-money.mtn.*` in `application.yml`) default to
sandbox values and don't need to be set for local dev unless you're exercising real MTN calls.

## Architecture Overview

### Module Dependency

```
ndopify-dto  →  ndopify-server
```

DTOs live in `ndopify-dto` and are shared between server request/response contracts. `ndopify-server` must not be
depended on by `ndopify-dto`.

### Domains in `ndopify-server`

Code is organized by domain under `com.wutsi.ndopify.<domain>.server`, each following the same layering:

```
<domain>/server/
    endpoint/   - @RestController classes (*Endpoints.kt)
    service/    - business logic (*Service.kt)
    dao/        - Spring Data JPA repositories (*Repository.kt)
    domain/     - JPA entities (*Entity.kt)
    mapper/     - Entity ↔ DTO mapping (*Mapper.kt)
    io/         - import/export (e.g. GeonamesImporter, NeighbourhoodImporter)
    cron/       - @Scheduled jobs (e.g. MobileChangeJobs)
```

Current domains:

- **refdata** — `Tenant`, `Location` (countries/regions/cities/neighbourhoods, imported from GeoNames), `Application`,
  `Role`. This is shared reference data, not tenant-scoped.
- **security** — `User`, `UserApplication` (a user's membership + roles in a given `Application`), `AuthFactor`
  (password/Google One Tap credentials), JWT issuance (`AccessTokenService`), and the `Authenticator` strategy
  interface with `PasswordAuthenticator` / `GoogleOneTapAuthenticator` implementations, selected via
  `AuthenticatorFactory` based on `AuthType`.
- **agent** — mobile-money agents (`AgentEntity`: identity, mobile-money number/gateway, KYC status) and their KYC
  workflows: `MobileChangeEntity` (mobile number change requests) and `IdentityChangeEntity` (identity document change
  requests), each going through a `KycStatus` (PENDING/verified/failed) lifecycle. `MobileChangeJobs`
  (`agent/server/cron`) is a `@Scheduled` job, cron expression from `ndopify.agent.cron.mobile-change-verify.expression`,
  that polls pending `MobileChangeEntity` rows and calls `MobileChangeService.verify()` on each. This is the one
  domain that is tenant-scoped today (see Multi-Tenancy below).
- **error** — shared exception types (`WutsiException` subclasses: `BadRequestException`, `NotFoundException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `InternalErrorException`) and a global
  `ControllerErrorHandler`.

There's also a non-domain **`platform`** package (`platform/momo`) — infrastructure for talking to mobile-money
providers, used by the `agent` domain to KYC-verify a mobile-money number/holder-name match:

- `MoMoGateway` — interface with a single `kycMatch(request): MoMoKycMatchResponse` operation.
- `MoMoGatewayFactory` — picks an implementation from `MoMoGatewayType` (`refdata.dto`); only `MTN` is wired up today,
  other gateway types resolve to `null`.
- `mtn/` — the MTN implementation (`MoMoGatewayMtn`), with `MtnUserProvider` split into `MtnUserProviderSandbox` /
  `MtnUserProviderProduction` (env-driven via `MtnMoMoConfiguration` / `MtnEnvironment`).

### Multi-Tenancy — current state

Tenancy is enforced via **Hibernate's built-in multi-tenancy mechanism**, not hand-rolled predicates:

- `TenantContext` (`platform/tenant/TenantContext.kt`) is a `ThreadLocal<Long?>` holding the current request's
  tenant.
- `TenantIdentifierResolver` (`platform/tenant/jpa/TenantIdentifierResolver.kt`) implements Hibernate's
  `CurrentTenantIdentifierResolver<Long>`, resolving from `TenantContext` and failing closed to `NO_TENANT = -1L`
  when unset.
- `TenantContextFilter` (`platform/tenant/servlet/TenantContextFilter.kt`), registered at
  `Ordered.HIGHEST_PRECEDENCE` via `platform/config/TenantConfiguration.kt`, populates `TenantContext` per request —
  preferring the tenant claim on the decoded JWT (`JWTPrincipal.CLAIM_TENANT_ID` / `AccessTokenService`) and falling
  back to the `X-Tenant-ID` header (`HttpHeader.TENANT_ID` in `ndopify-dto`) if there's no principal.
- Any JPA entity annotated `org.hibernate.annotations.@TenantId` is automatically filtered by Hibernate for every
  query against it — no `CriteriaBuilder` predicate, repository override, or service-level check needed. Today
  that's `PartyEntity`, `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, `KycCaseEntity`,
  and `AgentEntity`. Leave the field `null` on construction (see the comment on any of them) so Hibernate's
  `TenantIdGeneration` populates it from `TenantContext` on insert — setting a non-null value yourself makes
  Hibernate treat it as caller-assigned and throw if it doesn't match the resolved tenant.
- `KycVerificationEntity` is deliberately **not** `@TenantId`-annotated: it's never loaded by its own id anywhere in
  the codebase, only reached via the already-tenant-filtered `KycCaseEntity.verifications` association.
- If you add tenant-scoped data to a new or existing domain, add `@TenantId` to the entity — don't write a manual
  predicate; that duplicates enforcement and can silently drift from what Hibernate actually filters.
- refdata entities are still global: `TenantEntity`, `LocationEntity`, `ApplicationEntity`, `RoleEntity` have no
  `tenantId` and are not filtered. Same for `UserEntity`, `UserApplicationEntity`, `AuthFactorEntity` in the
  `security` domain.
- **Known, not-yet-closed gap**: tenant *resolution* itself isn't trustworthy yet. JWTs are issued via
  `Algorithm.none()` (unsigned, in `AccessTokenService`), and `SecurityConfiguration` currently `permitAll()`s every
  request — there is no Spring Security authentication enforced at the filter-chain level. A caller can set
  `X-Tenant-ID` to whatever it wants. The `@TenantId` mechanism above correctly isolates data *once a tenant is
  resolved*, but closing the resolution/authentication gap is separate, future work.

### Testing Patterns

**Assertions:** use `kotlin.test` (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`, etc.), never
`org.junit.jupiter.api.Assertions.*` or `org.junit.jupiter.api.assertThrows`. `@Test` from either JUnit or
`kotlin.test` is fine — this convention is only about the assertion calls. Not currently enforced by a build gate
(ktlint doesn't check semantics); enforce via code review.

Base classes (`src/test/kotlin/com/wutsi/ndopify/`):

- `BaseEndpointIntegrationTest` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, injects the
  `X-Device-ID` header via a `ClientHttpRequestInterceptor`.
- `TenantAwareEndpointIntegrationTest` — extends `BaseEndpointIntegrationTest`, additionally injects
  `X-Tenant-ID: 1` (override via `ignoreTenantIdHeader = true` in a test to omit it).

There is currently no `AuthorizationAwareEndpointTest`; tests needing an authenticated user obtain a JWT explicitly
via the auth endpoints (see `AuthenticatePasswordEndpointTest`, `AuthenticateGoogleOneTapEndpointTest`).

SQL test fixtures live under `modules/ndopify-server/src/test/resources/db/test/<domain>/`, named after the test
class per convention (`FooEndpointTest.kt` → `FooEndpoint.sql`). `ResetDBTest`/Flyway config in
`src/test/kotlin/com/wutsi/ndopify/FlywayConfiguration.kt` manages schema reset between runs.

### Database Migrations

Flyway, split across two classpath locations composed in `application.yml`
(`db.migration.common` + `db.migration.local`):

- `modules/ndopify-server/src/main/resources/db/migration/` — versioned as `V{version}__{description}.sql`
- Never modify an existing migration; always add a new one.

### Custom JPA Utilities (`util/jpa`)

- `CustomPhysicalNamingStrategy` — table/column naming strategy (see Hibernate config in `application.yml`)
- `StringListConverter` / `AuthTypeListConverter` / `LongListConverter` — `@Convert` converters for storing
  `List<String>` / `List<AuthType>` / `List<Long>` in a single column (used e.g. by `TenantEntity.locales`,
  `AgentEntity.neighborhoodIds`)

## Key Configuration Files

- `pom.xml` (root) — parent POM: shared dependencies, JaCoCo/ktlint plugin config, coverage thresholds
- `modules/*/pom.xml` — module-specific dependencies and overrides (e.g. `ndopify-server`'s coverage thresholds)
- `modules/ndopify-server/src/main/resources/application.yml` — default config (MySQL, Flyway locations, Jackson,
  SpringDoc/Swagger groups)
- `application-test.yml` / `application-prod.yml` — profile overrides

## README.md Files

Every module (`modules/ndopify-dto/README.md`, `modules/ndopify-server/README.md`) has CI-managed badges as the
first lines, right after the `# <module-name>` heading:

- `main` and `pull_request` GitHub Actions workflow-status badges (both modules).
- A JaCoCo coverage badge (`ndopify-server` only), pointing at the auto-generated SVG in
  `.github/badges/ndopify-server-jacoco.svg`.

These badges must be preserved whenever a README is edited. Edit module READMEs with a targeted edit (not a
full-file rewrite) so the badge block isn't clobbered — and if a full rewrite is unavoidable, read the existing
file first and carry the badge lines over verbatim.
