# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

`ndopify-mono` is an early-stage monorepo. The group/package is `com.wutsi.ndopify`. Four modules currently exist:

- `modules/ndopify-dto` — shared DTOs/contracts (no business logic)
- `modules/ndopify-server` — the REST API backend (Spring Boot)
- `modules/ndopify-partner-central` — agent-facing web app (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.npc`. No database; talks to `ndopify-server` over REST.
- `modules/ndopify-website` — static marketing site (plain HTML/CSS/JS, French). **Not** a Maven module (not in the
  root `pom.xml` reactor); deployed to S3 by its own workflow.

There is no SDK or tracking-server module yet. Do not assume they exist or scaffold code that depends on them.

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

`ndopify-server` endpoint tests use `TestRestTemplate` against a real MySQL database (Flyway-migrated), so a local
MySQL instance must be running (see Environment Setup below). `ndopify-partner-central` tests are plain unit tests /
standalone `MockMvc` and need neither MySQL nor a running server.

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

cd modules/ndopify-partner-central
mvn spring-boot:run          # starts on port 8081, calls ndopify-server at ndopify.server.api.base-url
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

- An SMTP server on `localhost:1025` (e.g. Mailpit) to receive e-mails sent by `ndopify-server` (agent welcome,
  OTP codes) — see `spring.mail` in `application.yml`.
- `KIMI_API_KEY` for identity-document extraction: `ndopify-server` uses Spring AI's OpenAI-compatible client
  (`spring.ai.openai`, pointed at Moonshot/Kimi) in `IdentificationInfoExtractorAi`.

No RabbitMQ or messaging broker is wired up in `ndopify-server` today — don't assume one exists. MTN Mobile Money credentials (`ndopify.mobile-money.mtn.*` in `application.yml`) default to
sandbox values and don't need to be set for local dev unless you're exercising real MTN calls.

## Architecture Overview

### Module Dependency

```
ndopify-dto  →  ndopify-server
ndopify-dto  →  ndopify-partner-central  ──(REST, runtime)──▶  ndopify-server
```

DTOs live in `ndopify-dto` and are the request/response contracts shared by the server and its clients. `ndopify-dto`
must never depend on `ndopify-server` or `ndopify-partner-central`, and `ndopify-partner-central` must not depend on
`ndopify-server` at build time — only call it over HTTP.

### Domains in `ndopify-server`

Code is organized by domain under `com.wutsi.ndopify.<domain>.server`, each following the same layering:

```
<domain>/server/
    endpoint/   - @RestController classes (*Endpoints.kt)
    service/    - business logic (*Service.kt)
    dao/        - Spring Data JPA repositories (*Repository.kt)
    domain/     - JPA entities (*Entity.kt)
    mapper/     - Entity ↔ DTO mapping (*Mapper.kt)
    io/         - import/export (refdata only, e.g. GeonamesImporter, NeighbourhoodImporter)
```

There are no `@Scheduled` jobs in the codebase today.

Current domains:

- **refdata** — `Tenant`, `Location` (countries/regions/cities/neighbourhoods, imported from GeoNames), `Application`,
  `Role`. This is shared reference data, not tenant-scoped.
- **security** — `User`, `UserApplication` (a user's membership + roles in a given `Application`), `AuthFactor`
  (credentials), JWT issuance (`AccessTokenService`), and the `Authenticator` strategy interface with
  `PasswordAuthenticator` / `GoogleOneTapAuthenticator` / `OtpAuthenticator` implementations, selected via
  `AuthenticatorProvider` based on `AuthType`. One-time codes are created via `POST /v1/otp` (`OtpService`,
  `OtpGenerator`) and e-mailed by `OtpMailet`; `POST /v1/auth` exchanges credentials for a JWT.
  `GET /v1/users/{id}` (`UserEndpoints`) returns the user with its party embedded. It returns 404 `USER_NOT_FOUND`
  for a user of another home tenant, which `UserService.findById` checks with `UserRepository.isVisible` before
  loading, because the eager `party` is `@TenantId`-filtered. Like the rest of the API, it isn't authorized yet
  (`permitAll`).
- **party** — `Party`, `Identification` (+ uploaded `IdentificationImage`s), `KycCase` / `KycVerification`, and
  `PaymentMethod`. KYC cases are verified by pluggable `KycVerifier` strategies (`KycVerifierIdentification`,
  `KycVerifierMoMo`, `KycVerifierManual`) selected via `KycVerifierProvider`. Identity-document data is extracted
  by `IdentificationInfoExtractorAi` (LLM via Spring AI), behind `IdentificationInfoExtractorProvider`.
  KYC case lifecycle: `PENDING` (images uploaded via `/v1/kyc/cases/{id}/images/upload`) → `POST
  /v1/kyc/cases/{id}/submit` → `SUBMITTED` (requires FRONT, plus BACK for two-sided documents: `NATIONAL_ID`,
  `DRIVER_LICENSE`, `RESIDENT_CARD`; SELFIE optional server-side, Partner Central requires it; images frozen) →
  `POST .../verify` → `IN_PROGRESS` → `VERIFIED` / `REJECTED`. `GET /v1/kyc/cases` returns the newest case first.
  Verification is meant to run offline (a future cron job), never from a UI. `KycStatus` is persisted by ordinal:
  inserting or reordering values needs a migration that renumbers existing rows (as
  `V4_1__party-kyc-status-submitted.sql` does). Tenant-scoped (see Multi-Tenancy below).
- **agent** — mobile-money agents (`AgentEntity`; `/v1/agents`: get, search, create, update). `WelcomeMailet` sends
  a welcome e-mail on registration when the tenant has a `partnerCentralUrl`. Tenant-scoped.
- **error** — shared exception types (`WutsiException` subclasses: `BadRequestException`, `NotFoundException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `InternalErrorException`) and a global
  `ControllerErrorHandler`.

There's also a non-domain **`platform`** package holding shared infrastructure (wired by `platform/config`):

- `platform/momo` — mobile-money providers, used by `KycVerifierMoMo` to KYC-verify a mobile-money
  number/holder-name match. `MoMoGateway` is the interface; `MoMoGatewayProvider` picks an implementation from
  `MoMoGatewayType` (`refdata.dto`). Only `MTN` (`mtn/MoMoGatewayMtn`) is wired up today.
- `platform/storage` — file storage (`StorageService`, `StorageServiceProvider`) with `local` and `s3`
  implementations, selected via `ndopify.storage.default-type`.
- `platform/mail` — e-mail sending (`MailService`, `MailBodyResolver`, `MailFilter`); MJML templates under
  `src/main/resources/mail/`.
- `platform/logger` — key-value request logging (`KVLogger`, `KVLoggerFilter`).
- `platform/tenant` — tenant resolution (see Multi-Tenancy below).

### `ndopify-partner-central`

Package `com.wutsi.ndopify.npc`: `ui/` (MVC controllers + Thymeleaf templates in `src/main/resources/templates/`),
`client/` (REST clients for `ndopify-server` and their interceptors), `service/` (tenant + security), `config/`.

- Sign-in is e-mail + one-time code on a single page, `templates/login.html` (`LoginController`: `/login` →
  `/login?step=code` → `/login/verify`). The code step is only shown while a `PendingLogin` (e-mail + expiry) is in
  the HTTP session, and verify reads the e-mail from there, not from the form. It calls
  `/v1/otp` then `/v1/auth` with `AuthType.OTP` and `ApplicationCode.PARTNER_CENTRAL`. The JWT is kept in the
  HTTP-only `__npc_access_token` cookie (`AccessTokenService`) and decoded by `JwtAuthenticationFilter`; everything
  except `/assets/**`, `/login/**`, `/error`, `/locations/**`, `/join/**` requires authentication. `/logout` clears the cookie and session.
- Tenant is resolved by `TenantContext` matching the request URL against each active tenant's `partnerCentralUrl`
  (from `GET /v1/tenants`).
- `RestConfiguration` adds logging, `Authorization` and `X-Tenant-ID` interceptors to every Spring-built
  `RestClient`. `NdTenantClient` uses a plain `RestTemplate` instead, since tenants are global.
- Sign-up is the *Rejoindre Ndopify* wizard (`JoinController`, `ui/join/`), built like the login: one page
  (`join`), five steps (`identity` → `experience` → `area` → `payment` → `review`), `GET /join?step=…`
  shows a step and `POST /join/{step}` validates it (`JoinRules`) into the session's `PendingJoin`. A step only
  opens once all earlier ones are complete; the review submits the session (`POST /v1/agents`), never the page. The
  `templates/join.html` renders only the current step (each its own `<form>`), pre-filled from `answers`, with
  `errors` per field; `JoinTemplateTest` renders it with a real Thymeleaf engine. `public/assets/js/join.js`
  only enhances fields (slim-select city type-ahead and neighbourhoods over `/locations`, intl-tel-input posting
  E.164 through a hidden input, "same as WhatsApp"); every step still posts without it. `src/main/html/` (pages and
  assets, incl. the client-side wizard `rejoindre.js`) is the prototype: never serve it, and never delete or "clean
  up" anything in it. The login page links to `/join`.
- `LocationController` (`GET /locations`) is a public JSON proxy to `GET /v1/locations` for the city/neighbourhood
  dropdowns (slim-select): same query parameters, but only `CITY`/`NEIGHBORHOOD`, country forced to the tenant's,
  `limit` capped. Used by `join.js` on the wizard's area step.
- KYC is `KycController` (`ui/kyc/`), one page (`templates/kyc.html`) that login redirects to. Wizard steps `front` →
  `back` → `selfie` → `review`: `GET /kyc?step=…` shows a step (never past the first photo the case lacks) and
  `POST /kyc/{step}` uploads that photo straight to the agent's PENDING case (`NdKycClient`), so the case is the
  state and the session holds no photos; `POST /kyc/review` submits it. The party is found by looking up the
  signed-in user (`CurrentParty` → `GET /v1/users/{id}`) and taking its newest case (`GET /v1/kyc/cases`, newest
  first). Once the case is no longer PENDING the page is a status view (`?step=confirmation` right after
  submitting); `POST /kyc/restart` opens a new case after a rejection. A user with no party goes to `/logout`.
  Photos are sniffed with Tika (`tika-core`) and capped at 5 Mo (`KycUploadErrorHandler` catches the multipart
  limit). `public/assets/js/kyc.js` only enhances (shrinks photos, in-page selfie camera); every step posts without it.
- `src/main/html/` holds static mockups generated by the `/init-partner-central` command; the served pages are the
  Thymeleaf templates. `application-test.yml` / `application-prod.yml` still contain config copied from another
  project (`koki`) — don't treat it as meaningful.

### Multi-Tenancy — current state

Tenancy is enforced via **Hibernate's built-in multi-tenancy mechanism**, not hand-rolled predicates:

- `TenantContext` (`platform/tenant/TenantContext.kt`) is a `ThreadLocal<Long?>` holding the current request's
  tenant.
- `TenantIdentifierResolver` (`platform/tenant/jpa/TenantIdentifierResolver.kt`) implements Hibernate's
  `CurrentTenantIdentifierResolver<Long>`, resolving from `TenantContext` and failing closed to `NO_TENANT = -1L`
  when unset.
- `TenantContextFilter` (`platform/tenant/servlet/TenantContextFilter.kt`), registered at
  `Ordered.HIGHEST_PRECEDENCE` via `platform/config/TenantConfiguration.kt`, populates `TenantContext` per request —
  preferring the tenant claim on the decoded JWT (`JwtPrincipal.CLAIM_TENANT_ID` / `AccessTokenService`) and falling
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
  `tenantId` and are not filtered. `UserEntity`, `UserApplicationEntity`, `AuthFactorEntity` in the `security`
  domain are not filtered either. `UserEntity.tenantId` exists but is the user's *home* tenant, deliberately not
  `@TenantId`: it's looked up by e-mail before `TenantContext` is known (see `AbstractAuthenticator`), and is null
  for cross-tenant users (e.g. a super-admin on `admin-console`).
- **Known, not-yet-closed gap**: tenant *resolution* itself isn't trustworthy yet. JWTs are issued via
  `Algorithm.none()` (unsigned, in `AccessTokenService` — and `ndopify-partner-central`'s `JwtDecoder` decodes them the
  same way), and `SecurityConfiguration` currently `permitAll()`s every
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

- `AuthorizationAwareEndpointIntegrationTest` — extends `TenantAwareEndpointIntegrationTest`, additionally sends an
  `Authorization: Bearer` JWT (user `11`, tenant `1`, `admin-console` by default) minted via `AccessTokenService`;
  set `anonymousUser = true` to omit it.

Mock Spring beans in `@SpringBootTest` tests with `@MockitoBean`
(`org.springframework.test.context.bean.override.mockito.MockitoBean`), not the removed `@MockBean`.

SQL test fixtures live under `modules/ndopify-server/src/test/resources/db/test/<domain>/`, named after the test
class per convention (`FooEndpointTest.kt` → `FooEndpoint.sql`). `ResetDBTest`/Flyway config in
`src/test/kotlin/com/wutsi/ndopify/FlywayConfiguration.kt` manages schema reset between runs.

### Database Migrations

Flyway, split across two classpath locations composed in `application.yml`
(`db.migration.common` + `db.migration.local`):

- `modules/ndopify-server/src/main/resources/db/migration/common/` — schema, versioned as
  `V{version}__{description}.sql`
- `db/migration/local/` (default profile) and `db/migration/test/` (`application-test.yml`) — environment-specific
  seed data layered on top of `common`
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
  mail, Spring AI, storage, SpringDoc/Swagger groups)
- `modules/ndopify-partner-central/src/main/resources/application.yml` — port 8081, `ndopify.server.api.base-url`
- `application-test.yml` / `application-prod.yml` — profile overrides

## README.md Files

Every module README (`modules/<module>/README.md`) has CI-managed badges as the first lines, right after the
`# <module-name>` heading:

- `main` and `pull_request` GitHub Actions workflow-status badges (all modules; `ndopify-website` has no PR
  workflow yet, so its PR badge points at a missing workflow).
- A JaCoCo coverage badge (`ndopify-server` and `ndopify-partner-central`), pointing at the auto-generated SVG in
  `.github/badges/<module>-jacoco.svg`.

These badges must be preserved whenever a README is edited. Edit module READMEs with a targeted edit (not a
full-file rewrite) so the badge block isn't clobbered — and if a full rewrite is unavoidable, read the existing
file first and carry the badge lines over verbatim.
