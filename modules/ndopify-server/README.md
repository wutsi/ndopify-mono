# ndopify-server

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-server-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-server-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-server-jacoco.svg)

REST API backend for Ndopify — a multi-tenant rental platform: mobile-money agents and their KYC verification,
customers' property-search mandates and their dispatch to agents, contracts and their e-signature, party /
identification records, payment methods, and mobile-money payment integrations across Africa.

Built with Kotlin + Spring Boot. Depends on [ndopify-dto](../ndopify-dto) for shared request/response contracts.

## Domains

Code is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same layering
(`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

| Domain       | Responsibility                                                                                    |
|--------------|-----------------------------------------------------------------------------------------------------|
| **refdata**  | Shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`. Global, not tenant-scoped. |
| **security** | `User` (a global identity, e-mail unique), its `Membership` of each tenant (party, roles per `Application`), credentials (`AuthFactor`), JWT issuance (`AccessTokenService`), pluggable authentication (`PasswordAuthenticator`, `GoogleOneTapAuthenticator`, `OtpAuthenticator`) selected by `AuthenticatorProvider`. One-time codes are issued by `OtpService` and e-mailed by `OtpMailet`. Users are global; memberships are tenant-scoped. |
| **party**    | Parties, identifications (with uploaded images), KYC cases/verifications, and payment methods. KYC cases are verified offline (`KycJobs`) via pluggable `KycVerifier` strategies (`KycVerifierIdentification`, `KycVerifierMoMo`, `KycVerifierManual`). Identity-document data is extracted by an LLM via Spring AI (`IdentificationInfoExtractorAi`). Tenant-scoped. |
| **agent**    | Mobile-money agents. Sends a welcome e-mail (`WelcomeMailet`) when a tenant has a `partnerCentralUrl`. Tenant-scoped. |
| **customer** | People looking for a property, linked to a party (`CustomerService.findOrCreate`). Tenant-scoped. |
| **mandate**  | A customer's search request and its lifecycle (pending assignment → assigned → pending approval → approved → closed / cancelled). `MandateDispatcher` offers each new mandate, exclusively and for a limited time, to its best-scoring agent (`Assignment`); `MandateJobs` expires and re-dispatches offers. Access: an admin or the mandate's agent. Tenant-scoped. |
| **contract** | Contracts to be signed by their parties, numbered per tenant (`ContractNumberGenerator`). A request for signature gives the signers an `e-signature` membership and e-mails them a link to [ndopify-esignature](../ndopify-esignature); the last required signature makes the contract `SIGNED`. `ContractJobs` expires them. Access: an admin or one of the contract's parties; only the party itself can sign. Tenant-scoped. |
| **error**    | Shared exception types (`WutsiException` subclasses) and a global `ControllerErrorHandler`. |

Tenant-scoped entities are filtered by Hibernate's `@TenantId`. E-mails are sent asynchronously, after the commit
of the transaction that triggered them (`<domain>/server/event/` + `<domain>/server/mail/`), and scheduled jobs live
in `<domain>/server/job/`.

A non-domain **`platform`** package holds infrastructure shared across domains:

- `platform.momo` — mobile-money gateway integration (`MoMoGateway`, `MoMoGatewayProvider`); only the MTN
  implementation (`momo.mtn`) is wired up today.
- `platform.storage` — file storage abstraction (`StorageService`, `StorageServiceProvider`) with `local` (disk +
  servlet) and `s3` implementations, selected via `ndopify.storage.default-type`.
- `platform.mail` — e-mail sending (`MailService`, `MailBodyResolver`), with templates under `src/main/resources/mail`.
- `platform.tenant` — per-request tenant resolution (`TenantContext`, `TenantContextFilter`,
  `TenantIdentifierResolver`) that feeds Hibernate's multi-tenancy.
- `platform.logger` — structured key-value request logging (`KVLogger`, `KVLoggerFilter`).
- `platform.endpoint` — `@OpsEndpoint`, for operational endpoints (ex: dispatching a mandate by hand), only mapped
  when `ndopify.endpoints.ops` is `true` (off in prod).
- `platform.config` — Spring `@Configuration` classes wiring the above.

See the root [CLAUDE.md](../../CLAUDE.md) for architecture notes aimed at AI-assisted development, and
[Multi-Tenancy](../../CLAUDE.md#multi-tenancy--current-state) for the tenancy model in detail.

## API Documentation

The REST API is documented with SpringDoc OpenAPI — refer to Swagger rather than to this README for endpoints,
parameters and payloads. With the server running locally:

- **Swagger UI**: <http://localhost:8080/api.html>. The API is split into groups — *Agent API*, *Contract API*,
  *Customer API*, *Mandates API*, *Party API*, *RefData API*, *Security API* — picked from the "Select a
  definition" list at the top right.
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs/<group>`, with `<group>` one of `agent`, `contract`,
  `customer`, `mandates`, `party`, `refdata`, `security` (e.g. to import into Postman or generate a client).

Groups are configured under `springdoc.group-configs` in `src/main/resources/application.yml`: an endpoint only
appears in Swagger UI if its path matches one of a group's `paths-to-match`, so add new paths there.

## Environment Setup

Required for local development:

- JDK 17, Maven 3.9+
- MySQL 8+, with the `root` user having **no password** (matches the default `application.yml` datasource)

No RabbitMQ or messaging broker is wired up today. E-mails (agent welcome, OTP codes, KYC status, mandate offers,
contracts to sign) go to the SMTP server
configured under `spring.mail` — `localhost:1025` by default, e.g. [Mailpit](https://mailpit.axllent.org/).
The LLM calls need `KIMI_API_KEY` (vision model, `spring.ai.openai`: KYC documents, listing pictures) and
`DEEPSEEK_API_KEY` (text model, `spring.ai.deepseek`). MTN Mobile Money credentials
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
  `X-Device-ID` header), `TenantAwareEndpointIntegrationTest` (adds `X-Tenant-ID: 1`) and
  `AuthorizationAwareEndpointIntegrationTest` (adds an `Authorization: Bearer` JWT; `anonymousUser`, `userId` and
  `roles` change the caller).
- **Asynchronous e-mails**: a test must wait for its mail (`smtp.waitForIncomingEmail`).
- **Mocking beans** in `@SpringBootTest` integration tests: use `@MockitoBean`
  (`org.springframework.test.context.bean.override.mockito.MockitoBean`), not the removed `@MockBean`.
- **SQL fixtures** live under `src/test/resources/db/test/<domain>/`, named after the test class
  (`FooEndpointTest.kt` → `FooEndpoint.sql`).

## Database Migrations

Flyway-managed, versioned as `V{version}__{description}.sql`: the schema in `src/main/resources/db/migration/common/`,
plus environment-specific seed data in `db/migration/local/` (default profile) or `db/migration/test/` (`test`
profile). Never modify an existing migration — always add a new one.
