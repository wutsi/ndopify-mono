# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

`ndopify-mono` is an early-stage monorepo. The group/package is `com.wutsi.ndopify`. Five modules currently exist:

- `modules/ndopify-dto` — shared DTOs/contracts (no business logic)
- `modules/ndopify-server` — the REST API backend (Spring Boot)
- `modules/ndopify-partner-central` — agent-facing web app (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.npc`. No database; talks to `ndopify-server` over REST.
- `modules/ndopify-esignature` — signer-facing contract sign page (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.esignature`. No database; talks to `ndopify-server` over REST.
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
MySQL instance must be running (see Environment Setup below). `ndopify-partner-central` and `ndopify-esignature` tests are
plain unit tests / standalone `MockMvc` and need neither MySQL nor a running server.

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

cd modules/ndopify-esignature
mvn spring-boot:run          # starts on port 8083, calls ndopify-server like partner-central
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
ndopify-dto  →  ndopify-esignature       ──(REST, runtime)──▶  ndopify-server
```

DTOs live in `ndopify-dto` and are the request/response contracts shared by the server and its clients. `ndopify-dto`
must never depend on `ndopify-server`, `ndopify-partner-central` or `ndopify-esignature`, and `ndopify-partner-central` /
`ndopify-esignature` must not depend on `ndopify-server` at build time — only call it over HTTP.

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
    event/      - application events + their listeners (agent, party, security, mandate so far)
    mail/       - the domain's e-mails (*Mailet.kt: recipient, subject, template context), e.g. WelcomeMailet
```

All of a domain's mail-related classes go in its `mail/` package, never in `service/`: a mailet builds the `Email`
and hands it to `platform/mail`'s `MailService`; its MJML template lives under `src/main/resources/mail/<domain>/`
(e.g. `mail/agent/welcome.mjml`). The service method that triggers it (see *Asynchronous e-mails*) stays in
`service/`. Dates and amounts in e-mails (and generated contracts) go through `TenantFormatter`
(`refdata/server/service/`): the tenant's `dateFormat` / `timeFormat` / `monetaryFormat` / `numberFormat`, first
locale and `timeZone` — never a hard-coded pattern. Mailet unit tests mirror the package (`<domain>/server/mail/*MailetTest.kt`).

Scheduled jobs live in `<domain>/server/job/`: `KycJobs` (party), `MandateJobs` (mandate) and `ContractJobs`
(contract). They loop over the
active tenants, setting `TenantContext` for each.

Current domains:

- **refdata** — `Tenant`, `Location` (countries/regions/cities/neighbourhoods, imported from GeoNames), `Application`,
  `Role`. This is shared reference data, not tenant-scoped.
- **security** — `User` (a global identity, e-mail unique), `Membership` (the user's membership of a tenant, with
  its party there), `UserApplication` (a membership's access + roles in a given `Application`), `AuthFactor`
  (credentials, global), JWT issuance (`AccessTokenService`), and the `Authenticator` strategy interface with
  `PasswordAuthenticator` / `GoogleOneTapAuthenticator` / `OtpAuthenticator` implementations, selected via
  `AuthenticatorProvider` based on `AuthType`. One-time codes are created via `POST /v1/otp` (`OtpService`,
  `OtpGenerator`) and e-mailed by `OtpMailet`, asynchronously (see *Asynchronous e-mails* below): the tenant is
  resolved (`OtpMailet.resolveTenant`) before the hand-off, so a missing/unknown tenant still fails the request,
  but an SMTP failure no longer does — the code is committed and the user asks for a new one; a user who isn't a
  member of the tenant gets 404 `USER_NOT_FOUND`. `POST /v1/auth` exchanges credentials for a JWT:
  `AbstractAuthenticator` requires a tenant (400 `HTTP_MISSING_PARAMETER` otherwise), finds the user by e-mail
  (global), then its membership of the tenant (`AUTH_ACCESS_DENIED` without one), whose roles go in the token. A
  person in two tenants has one user, one set of credentials, and a membership (party, roles) in each. Nothing
  creates a membership on sign-in (not even Google One Tap): only registration (`AgentService.create`) does, and a request
  for signature (`ContractSignerService`), for the parties asked to sign, with `e-signature` access.
  `GET /v1/users/{id}` (`UserEndpoints`) returns the user with its party in the current tenant embedded, and 404
  `USER_NOT_FOUND` for a user who isn't a member of it. Like the rest of the API, it isn't authorized yet
  (`permitAll`). A party's e-mail is its contact e-mail in the tenant, not synced to the user's (global) login
  e-mail.
- **party** — `Party`, `Identification` (+ uploaded `IdentificationImage`s), `KycCase` / `KycVerification`, and
  `PaymentMethod`. KYC cases are verified by pluggable `KycVerifier` strategies (`KycVerifierIdentification`,
  `KycVerifierMoMo`, `KycVerifierManual`) selected via `KycVerifierProvider`. Identity-document data is extracted
  by `IdentificationInfoExtractorAi` (LLM via Spring AI), behind `IdentificationInfoExtractorProvider`.
  KYC case lifecycle: `PENDING` (images uploaded via `/v1/kyc/cases/{id}/images/upload`) → `POST
  /v1/kyc/cases/{id}/submit` → `SUBMITTED` (requires FRONT, plus BACK for two-sided documents: `NATIONAL_ID`,
  `DRIVER_LICENSE`, `RESIDENT_CARD`; SELFIE optional server-side, Partner Central requires it; images frozen) →
  `POST .../verify` → `IN_PROGRESS` → `VERIFIED` / `REJECTED`, which publishes a `KycCaseCompletedEvent`: the
status e-mail (`KycMailet`) is sent by `KycEventListener` whether the endpoint or `KycJobs` verified the case. `GET /v1/kyc/cases` returns the newest case first.
  Verification runs offline (`KycJobs`), never from a UI: `POST .../verify` is an `@OpsEndpoint`, off in prod.
  Tenant-scoped (see Multi-Tenancy below).
- **agent** — mobile-money agents (`AgentEntity`; `/v1/agents`: get, search, create, update). `WelcomeMailet` sends
  a welcome e-mail on registration when the tenant has a `partnerCentralUrl`, asynchronously (see *Asynchronous
  e-mails* below). `joinedAt` is set on the agent's first VERIFIED case by `AgentKycEventListener`, a
  synchronous `@EventListener` on `KycCaseCompletedEvent` (same transaction as the KYC), so the party domain never
  depends on the agent one. Tenant-scoped.
- **customer** — people looking for a property (`CustomerEntity`, linked to a `Party`; `/v1/customers`).
  `CustomerService.findOrCreate` reuses the party and customer of an e-mail. Tenant-scoped.
- **mandate** — a customer's search request (`MandateEntity`; `/v1/mandates`: get, search, create, update).
  `MandateService.create` finds or creates the customer from the request's contact fields and starts the mandate
  as `PENDING_ASSIGNMENT`. It only checks that the contact fields aren't empty (`@NotEmpty`): categories, cities,
  neighbourhoods, prices and enums aren't validated server-side yet. Tenant-scoped.
  **Access** (`MandateAccessService`): every endpoint but create (public) and the ops ones needs a JWT (401
  `AUTH_UNAUTHORIZED`), and the caller must be an admin (`CallerService.isAdmin`: `admin-console` token with the
  `admin` role, `RoleCode`) or the mandate's agent (JWT user → membership → party → agent), else 403
  `MANDATE_ACCESS_DENIED`, after the 404 and before any 409. The agent it's offered to (PENDING assignment) may also
  get it, not change it. An agent's search is restricted to its own mandates (`agent-id` forced; another agent's is a
  403). Reads are checked by `MandateEndpoints`, changes by `MandateService` (under the mandate's lock); the server's own
  reads (dispatch, listeners, jobs) aren't. Partner Central has no admin role: admins only exist in the admin console.
  `POST /{id}/request-approval` (ASSIGNED or PENDING_APPROVAL, else 409 `MANDATE_CANNOT_BE_APPROVED`) →
  `PENDING_APPROVAL`, its contract's content generated and stored, and its signature requested, in 3 transactions so that
  no lock (the tenant's contract sequence, the mandate) is held during the upload: (1) under the mandate's lock, reuse
  its contract or create one (none, or EXPIRED) and link it, so concurrent requests share one contract; (2) generate
  (read-only) then upload, outside any transaction; (3) under the lock again, 409 `MANDATE_VERSION_CONFLICT` if the
  mandate changed since (2), else set the content, PENDING_APPROVAL, request the signature. A failure after (1) leaves
  the mandate ASSIGNED with its DRAFT contract, reused next time: an ASSIGNED mandate can have a `contractId`.
  The last signature of its contract approves it (`MandateEventListener.onContractSigned`, synchronous, in the
  signature's transaction: `APPROVED`, `approvedAt`), publishing `MandateApprovedEvent`: `MandateApprovedMailet` e-mails
  its agent that the customer signed, with the contract's validity and a link to `{partnerCentralUrl}/mandates/{id}`
  (`mail/mandate/approved.mjml`; nothing for a mandate without agent, or no longer APPROVED).
  `POST /{id}/close` (APPROVED only, else 409 `MANDATE_NOT_APPROVED`) → `CLOSED` with `finalPrice`, `closedAt` and
  `age`: whole days from `approvedAt` to closing, the time a mandate takes to close. `POST /{id}/cancel` (any status but
  CLOSED / CANCELLED: 409 `MANDATE_ALREADY_CLOSED`) → `CANCELLED` with `cancellationCode` (default `OTHER`),
  `cancellationReason` and `closedAt` (no age), and expires its PENDING offer, without re-dispatch. Both lock the
  mandate and publish `MandateClosedEvent` (no listener yet). Accepting an offer of a mandate that's no longer
  `PENDING_ASSIGNMENT` is 409 `ASSIGNMENT_NOT_PENDING`.
  Offers of a mandate to agents are `AssignmentEntity` (`T_ASSIGNMENT`, `@Version`-ed; `/v1/assignments`: search
  (newest first), get, `POST /{id}/accept`, `POST /{id}/decline`), see `docs/superpowers/plans/2026-10-14-mandate-dispatch.md`.
  Accept/decline are only for the assigned agent (JWT user → membership → party = agent's party: 401 / 403
  `ASSIGNMENT_ACCESS_DENIED`), then 409 `ASSIGNMENT_NOT_PENDING` if not PENDING or past `expiresAt`. Accept sets the
  mandate's agent and `ASSIGNED`, and publishes `AssignmentAcceptedEvent`: `MandateAssignedMailet` e-mails the
  customer its agent's name, WhatsApp number and biography (`mail/mandate/assigned.mjml`); decline publishes
  `AssignmentClosedEvent`.

  **Dispatch.** `MandateDispatcher.dispatch(mandateId)` offers a `PENDING_ASSIGNMENT` mandate, exclusively and for
  `ttl-minutes`, to its best-scoring candidate (verified agents of its city; not the customer; not an agent who
  declined / ignored it; under `max-load`), scored on neighbourhood match (incl. nearby ones of the same city, via
  `searchNearest(parentId)`), rotation (`lastAssignedAt`, set on offer), `AgentEntity.rating`, acceptance rate and
  load. No offer at night (`DispatchHours`, tenant's `timeZone`), whose hours also pause the TTL; past
  `manual-after-days` it moves the mandate to `MANUAL_ASSIGNMENT` instead (`MandateManualAssignmentEvent`, no listener
  yet). It's called after commit by `MandateEventListener` on `MandateCreatedEvent` (creation) and
  `AssignmentEventListener` on `AssignmentClosedEvent` (decline, expiry), and by `MandateJobs`: `expire` (PENDING
  offers past `expiresAt` → EXPIRED, re-dispatched) and `dispatch` (mandates waiting without a PENDING offer: came at
  night, had no candidate, or their re-dispatch failed). Each offer publishes `AssignmentCreatedEvent`:
  `MandateOfferMailet` e-mails the agent the mandate and its deadline (`mail/mandate/offer.mjml`). The dispatch always
  runs: there's no switch to turn it off. Two ops endpoints (`@OpsEndpoint`, see `platform/endpoint`): `POST
  /v1/mandates/{id}/dispatch` (offers a mandate now, same rules) and `POST /v1/assignments/{id}/expire` (expires an
  offer now). Config: `ndopify.mandate.assignment.*`. `MandateDispatcherTest` covers every case of the algorithm —
  keep it that way when changing it. The wiring is covered by the endpoint tests (`CreateMandateEndpointTest`,
  `DispatchMandateEndpointTest`, `Decline` / `ExpireAssignmentEndpointTest`) and the listener / job unit tests.
- **contract** — contracts to be signed by parties (`ContractEntity` + its `ContractPartyEntity`s, UUID ids;
  `/v1/contracts`: search (`id`, `status`, `party-id`, `contract-party-id` lists, newest first; `ContractSummary`, without parties), create,
  get, `POST /{id}/content`, `POST /{id}/request-signature`, `POST /{id}/sign`, `GET /{id}/signatures/{hash}`). `ContractService.create` checks the type, the parties' roles,
  `durationMonths >= 1` (persisted; the request has no dates) and that each `partyId` is a party of the tenant (404
  `PARTY_NOT_FOUND`), and starts the contract as `DRAFT`, without dates nor document, numbered `<prefix>-<year>-<nnnnnn>` by `ContractNumberGenerator` (`BAIL` for a
  `LEASE_AGREEMENT`, else `MNDT`; year in the tenant's `timeZone`): one gapless sequence per tenant, prefix and year in
  `T_CONTRACT_SEQUENCE`, incremented last in the create transaction (row locked until commit). `content` (`SetContractRequest.url`) sets the
  document's `contentUrl` of a DRAFT or PENDING_SIGNATURE contract, locked like a signature (409 `CONTRACT_EXPIRED` /
  `CONTRACT_CANCELLED` / `CONTRACT_ALREADY_SIGNED` otherwise). `contentHash` (SHA-256 of the uploaded bytes, set by
  `MandateService`; the endpoint clears it) and each party's `signatureHash` (set by `sign`, see `ContractHasher`) are
  stored only, never in a DTO. Each signature has a random `signatureSalt` (`ContractHasher.newSalt`, 16 bytes from
  `SecureRandom`, stored only, never logged), the first input of its `signatureHash`: the hash is printed in the
  signed PDF, and the salt keeps it from being brute-forced back to the signer's IP / user agent / e-mail. `request-signature` moves a DRAFT (or
  PENDING_SIGNATURE) contract, locked like a signature, to `PENDING_SIGNATURE` with `signatureRequestedAt` = now and `signatureDeadline` = now +
  `ndopify.contract.signature.ttl-days` (7), both reset by each request (409 `CONTRACT_EXPIRED` / `CONTRACT_CANCELLED` / `CONTRACT_ALREADY_SIGNED`), and publishes `ContractSignatureRequestedEvent`:
  `ContractSignatureMailet` e-mails each unsigned party whose `signatureRequired` is set (`mail/contract/signature_request.mjml`) its deadline and a single button, never
  the document's URL: the page it opens shows the contract and signs it, `{eSignaturePortalUrl}/sign/{contractPartyId}` (no button without an
  `eSignaturePortalUrl`), served by ndopify-esignature. Sign takes a *contract party* id (`SignContractRequest.partyId`), locks the
  contract (`findByIdForUpdate`), then 409 `CONTRACT_NOT_PENDING` / `CONTRACT_EXPIRED` (past `signatureDeadline`) /
  `CONTRACT_PARTY_SIGNATURE_NOT_REQUIRED` / `CONTRACT_PARTY_ALREADY_SIGNED`; the last signature of the parties whose
  `signatureRequired` is set (default `true`; at least one per contract, else 400) makes it `SIGNED`, valid from then (`validFrom`) for its
  `durationMonths` (`validUntil`, months added in the tenant's `timeZone`).
  **Signed PDF.** The last signature's `ContractSignedEvent` is also handled by `ContractEventListener.onSigned`
  (async, after commit): `SignedContractService.generate` downloads the content (`StorageService.pathOf`, inverse of
  `publicUrl`), checks it against `contentHash` (if set), and `SignedContractRenderer` renders its `<body>` (head
  dropped) plus one signature block per party that signed (QR code of `{eSignaturePortalUrl}/verify/{contractId}/{signatureHash}`,
  legal name, date and time by `TenantFormatter`, hash) to PDF: Jsoup → XHTML → Flying Saucer **9.x** (10.x needs
  Java 21), DejaVu fonts embedded (`resources/contract/fonts/`; the built-in fonts can't print no-break spaces nor
  Ŋ / Ɛ), CSS 2.1 only, and nothing fetched (`OfflineUserAgent`: only `data:` URIs and the library's own stylesheet;
  the content's remote images, ex: the tenant logo, are left out). Stored next to the content as
  `{number}-{token}.pdf` and `{number}-{token}-signature-{n}.png`, `token` random per run (the storage is public and the
  directory is guessable from a QR code; concurrent runs never overwrite each other), never named after the contract
  party (its id is the secret of the sign link); `ContractService.setSignedContent` then saves
  `signedContentUrl` (in `Contract` / `ContractSummary`) and `signedContentHash` (SHA-256 of the PDF, stored only)
  under the lock, first one wins. Nothing is locked during the download / render / upload. No PDF (logged) without
  content in the storage, with a content hash mismatch, a signature without hash or salt, or no
  `eSignaturePortalUrl`. `POST /v1/contracts/{id}/signed-content` (`@OpsEndpoint`, admin) generates it again. The
  QR code opens ndopify-esignature's verify page.
  **Access** (`ContractAccessService`, checked by `ContractEndpoints`, without lock since a contract's parties never
  change): every endpoint but the signature's verification (`GET /{id}/signatures/{hash}`, public: the contract number, legal name, `signedAt` and `signedContentUrl` of a signed contract party with that hash, else 404 `CONTRACT_SIGNATURE_NOT_FOUND`) needs a JWT (401 `AUTH_UNAUTHORIZED`). Get and search are for an admin or one of the
  contract's parties (JWT user → membership → party), else 403 `CONTRACT_ACCESS_DENIED` (after the 404); a non-admin's
  search is restricted to its own party (`party-id` forced; another party's is a 403). With both `party-id` and
  `contract-party-id`, the contract party must be one of those parties' (same `T_CONTRACT_PARTY` row): a co-party of a
  contract finds nothing with another party's contract-party id (the sign page's `not-yours`). Create, content and
  request-signature are admin-only (`MandateService` calls the service directly, unchecked). Sign is only for the
  party itself (the JWT's party must be the contract party's `partyId`): **no admin bypass, nobody signs on another's
  behalf** — never add one. `request-signature` gives each party asked to sign
  (`signatureRequired`, not signed yet) a membership of the tenant, as its party, with `e-signature` access
  (`ContractSignerService`, in the request's transaction, before the save): the one it has (an agent), else the user of
  its `legalEmail` (created if needed). An address already a member's as another party is a 409
  `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`: contract unchanged, no e-mail. Idempotent. So a customer signs in to the
  e-signature portal by OTP once asked to sign; nothing else (not `CustomerService`) creates its membership. A mandate's contract (`MandateService.createContract`) has the customer as
  `MANDATOR` (signs it) and its agent as `AGENT` with `signatureRequired = false`: a party, so it can read the
  contract, but it's neither asked to sign nor waited for.
  `ContractJobs.expire` (daily, `ndopify.contract.expire.cron`) moves to `EXPIRED` the PENDING_SIGNATURE contracts past
  their `signatureDeadline` and the SIGNED ones past their `validUntil` (each locked, in its own transaction), publishing
  `ContractExpiredEvent` (no listener yet). Tenant-scoped.
- **error** — shared exception types (`WutsiException` subclasses: `BadRequestException`, `NotFoundException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `InternalErrorException`) and a global
  `ControllerErrorHandler`.

**Asynchronous e-mails** (welcome, OTP, KYC status, mandate offered, mandate assigned, mandate approved, contract to sign): the service publishes an event
from its transaction (`AgentCreatedEvent`, `OtpCreatedEvent`, `KycCaseCompletedEvent`, `AssignmentCreatedEvent`,
`AssignmentAcceptedEvent`, `MandateApprovedEvent`, `ContractSignatureRequestedEvent` in `<domain>/server/event/`), carrying ids plus the `tenantId` — never entities. Its
listener is `@Async @TransactionalEventListener(AFTER_COMMIT)`, so nothing is sent for a rolled-back transaction. The
executor thread has no request, so the listener sets `TenantContext` and a `DefaultKVLogger` in `KVLoggerThreadLocal`
(which the injected `KVLogger` falls back to), calls `logger.log()` in `finally` to flush it, and removes both
(threads are reused). The work runs in a `@Transactional` service method (`AgentService.sendWelcomeEmail`,
`OtpService.sendEmail`, `KycService.sendStatusEmail`, `AssignmentService.sendOfferEmail` / `sendAssignedEmail`, `MandateService.sendApprovedEmail`,
`ContractService.sendSignatureRequestEmails`); the signed PDF (`ContractSignedEvent` → `SignedContractService.generate`,
not an e-mail, not transactional) follows the same listener pattern. The service methods are
entered *after* `TenantContext` is set since Hibernate resolves the tenant when the session opens. Failures are
logged, never rethrown. Tests must wait for the mail (`smtp.waitForIncomingEmail`);
`BaseEndpointIntegrationTest.tearDown` waits for the running async tasks, so a test's mail can't reach the next one.

**Optimistic locking**: `MandateEntity`, `AgentEntity`, `ContractEntity`, `KycCaseEntity` and `AssignmentEntity` are
`@Version`-ed (`version` column, `V7_2`). Hibernate checks it on flush (`UPDATE ... WHERE version = ?`), so it only
catches concurrent transactions; `ControllerErrorHandler` turns the `ObjectOptimisticLockingFailureException` into a
409 named after the entity (`MANDATE_VERSION_CONFLICT`, `AGENT_VERSION_CONFLICT`, `CONTRACT_VERSION_CONFLICT`,
`KYC_CASE_VERSION_CONFLICT`; `ASSIGNMENT_NOT_PENDING` for an offer). Across requests, `Mandate` / `Agent` expose
`version`, and `UpdateMandateRequest.version` / `UpdateAgentRequest.version` (null: not checked) must still be the
entity's, else the same 409 before anything is saved. With `@Version`, Spring Data treats an entity whose `version` is
null as new: update with `copy()` of a loaded entity, never by building one with an existing id. Any write bumps the
version, incl. the server's own (ex: `AgentService.markAssigned` on dispatch), so an edit form can get a 409 for a
change to a field it doesn't show.

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
- `platform/endpoint` — `@OpsEndpoint`: marks an operational endpoint method (ex: dispatching a mandate by hand).
  `OpsEndpointHandlerMapping` (wired by `EndpointConfiguration`) doesn't map them unless `ndopify.endpoints.ops` is
  `true` (default config; `false` in `application-prod.yml`, and when missing): the route doesn't exist, and
  `ControllerErrorHandler` answers 404 `HTTP_NOT_FOUND` for an unknown path (`NoResourceFoundException`). They're
  unauthenticated, like the rest of the API for now: mark any endpoint that mustn't be open in prod with it.

### `ndopify-partner-central`

Package `com.wutsi.ndopify.npc`: `ui/` (MVC controllers + Thymeleaf templates in `src/main/resources/templates/`),
`client/` (REST clients for `ndopify-server` and their interceptors), `service/` (tenant + security), `config/`.

- Sign-in is e-mail + one-time code on a single page, `templates/login.html` (`LoginController`: `/login` →
  `/login?step=code` → `/login/verify`). The code step is only shown while a `PendingLogin` (e-mail + expiry) is in
  the HTTP session, and verify reads the e-mail from there, not from the form. It calls
  `/v1/otp` then `/v1/auth` with `AuthType.OTP` and `ApplicationCode.PARTNER_CENTRAL`. The JWT is kept in the
  HTTP-only `__npc_access_token` cookie (`AccessTokenService`) and decoded by `JwtAuthenticationFilter`, which clears
  the cookie of a token that doesn't decode (expired) and marks the request `ACCESS_TOKEN_REJECTED`, so that
  `AuthorizationClientHttpRequestInterceptor` doesn't forward it (ndopify-server would answer 401); everything
  except `/assets/**`, `/login/**`, `/error`, `/locations/**`, `/join/**`, `/find-property/**` requires authentication. `/logout` clears the cookie and session.
  The header's account menu (`<nav class="user-menu">`) is wrapped in `th:if="${signedIn}"`, a model attribute
  that `SignedInAdvice` (`ui/layout/`) sets on every page: give any new page's menu the same guard.
- Tenant is resolved by `TenantContext` matching the request URL against each active tenant's `partnerCentralUrl`
  (from `GET /v1/tenants`). Dates and amounts shown to users go through `service/TenantFormatter` (built from the
  `Tenant`, like ndopify-server's): the tenant's `dateFormat` / `monetaryFormat` / `numberFormat`, first locale and
  `timeZone`, with no-break spaces; a field's unit or a message names the currency with `currency()` (the
  `currencySymbol`), never a hard-coded "FCFA". `mandate.js` reads it from the body's `data-currency`.
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
  `limit` capped. Used by `join.js` and `find-property.js` on the wizards' area steps.
- *Trouver un bien* is `FindPropertyController` (`ui/findproperty/`), a **public** wizard for people looking for a
  rental, built like the join wizard: one page (`templates/find-property.html`), seven steps (`need` → `area` →
  `property` → `amenities` → `budget` → `contact` → `review`), `POST /find-property/{step}` validates
  (`FindPropertyRules`) into the session's `PendingMandate`, and the review submits the session as a TENANT/RENTAL
  mandate (`NdMandateClient` → `POST /v1/mandates`, price in the tenant's currency), then shows the confirmation while
  a `SubmittedMandate` is in the session. Since ndopify-server doesn't validate mandates yet, these rules and the
  city/neighbourhood checks are the only validation a mandate gets. "Indifférent" (`ANY`) is a UI-only answer, sent
  as `null`. Without JS the property step shows every follow-up question of the category and the server ignores
  those that don't apply to the type; `public/assets/js/find-property.js` hides them. Prototype:
  `src/main/html/find-property.html`.
- KYC is `KycController` (`ui/kyc/`), one page (`templates/kyc.html`), reached from the Espace agent's banner. Wizard steps `front` →
  `back` → `selfie` → `review`: `GET /kyc?step=…` shows a step (never past the first photo the case lacks) and
  `POST /kyc/{step}` uploads that photo straight to the agent's PENDING case (`NdKycClient`), so the case is the
  state and the session holds no photos; `POST /kyc/review` submits it. The party is found by looking up the
  signed-in user (`CurrentParty` → `GET /v1/users/{id}`) and taking its newest case (`GET /v1/kyc/cases`, newest
  first). Once the case is no longer PENDING the page is a status view (`?step=confirmation` right after
  submitting); `POST /kyc/restart` opens a new case after a rejection. A user with no party goes to `/logout`.
  Photos are sniffed with Tika (`tika-core`) and capped at 5 Mo (`KycUploadErrorHandler` catches the multipart
  limit). `public/assets/js/kyc.js` only enhances (shrinks photos, in-page selfie camera); every step posts without it.
- Login redirects to `/`, the *Espace agent* (`HomeController`, `ui/home/`, `templates/index.html`), whatever the
  KYC status: an agent who isn't `VERIFIED` gets a banner linking to `/kyc`, worded after its newest case (documents
  to send, under review, rejected), since the party's status only changes on verification. Its profile section shows the agent's photo, name,
  area and biography summary. The signed-in agent is `CurrentAgent` (party → `GET /v1/agents?party-id=` → `GET
  /v1/agents/{id}`): agent and party ids always come from the token, never from a form, since ndopify-server doesn't
  check who updates an agent. The profile pages (`ProfileController`, `ui/profile/`) are open whatever the KYC
  status: `GET|POST /profile` (`templates/profile/edit.html`, `POST /v1/agents/{id}`; the name isn't editable) and
  `GET|POST /profile/photo` (`templates/profile/photo.html`, sniffed with Tika, 5 Mo, sent to `POST
  /v1/parties/{id}/photo`). Both go back to `/` with a `saved` flash attribute.
  `join.js` and `kyc.js` enhance them (`data-page` `profile-edit` / `profile-photo`). `SectorValidator`
  (`ui/location/`) is the city/neighbourhood check shared with the join wizard.
  A VERIFIED agent's Espace agent also lists the mandates offered to it (*Mandats proposés*): `HomeController` reads
  its PENDING assignments (`NdAssignmentClient` → `GET /v1/assignments?agent-id=&status=PENDING`, leaving out those
  past `expiresAt`), each one's mandate (`GET /v1/mandates/{id}`, one call per offer, at most `max-load`; one that
  fails is left out) and the location names in one lookup, into `AssignmentView`s (`AssignmentViews`): no customer
  data (no id, no comments) before acceptance. `AssignmentController` posts `POST /assignments/{id}/accept|decline`
  and goes back to `/#assignments-title` with an `assignment` flash attribute (`accepted`, `declined`, `closed` for a 403/404/409,
  `error`); ndopify-server checks the caller is the offer's agent. Without JS, "Refuser" opens the confirmation via
  `GET /?decline={id}`; `public/assets/js/home.js` does it in place and counts the deadline down. The mandate labels
  are shared with the find-property review in `ui/mandate/MandateLabels`.
- A VERIFIED agent's Espace agent also lists its mandates in progress (*Mes mandats*): `HomeController` reads
  `GET /v1/mandates?agent-id=&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED` (search order, capped at 20),
  then one `GET /v1/mandates/{id}` per card (the summary lacks the moving date; one that fails is left out) plus, in
  one `GET /v1/contracts?id=…` for all the cards, the contracts of those PENDING_APPROVAL / APPROVED, for the next
  step's date (without it, the cards show no date); the location names
  of offers and mandates come in one lookup. Cards are `MandateCards` → `MandateCardView`; statuses, tags, party
  wording ("le client" / "le bailleur", after `mandateType`), dates and next
  steps are `ui/mandate/MandateRules`. Each card opens the mandate page, `MandateController` (`ui/mandate/`,
  `templates/mandate.html`): `GET /mandates/{id}` (`MandateViews` → `MandateView`: steps, panel of the status,
  contact from `GET /v1/customers/{id}`, criteria, "accepté le" from the ACCEPTED assignment); only the mandate
  itself is required (contract, customer, offer or names failing just leave their lines out), and a status outside
  `MandateRules.SHOWN` or a 404 is the locked view. Actions are post-redirect-get back to the page with an `outcome`
  flash attribute (`sent`, `resent`, `closed`, `cancelled`, `saved`, `changed` for a 404/409 or a mandate that moved
  on, `email-conflict` for request-approval's 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`: the customer's e-mail is
  another account's, `error`), worded by the controller: `POST /mandates/{id}/request-approval|close|cancel`. The close / cancel
  form opens without JS via `GET /mandates/{id}?end=close|cancel` (ignored when the button wouldn't show) and comes
  back with its errors. "Envoyé le" is the contract's `signatureRequestedAt` (`MandateRules.requestedAt`), and the
  days given to sign are its deadline less that date (`MandateRules.signatureTtlDays`): partner-central has no TTL
  setting of its own, so it can't drift from ndopify-server's `ndopify.contract.signature.ttl-days`. The edit page (`GET|POST /mandates/{id}/edit`, `templates/mandate/edit.html`,
  `MandateEditForm` / `MandateEditRules`) is only for `MandateRules.EDITABLE` statuses (ASSIGNED, PENDING_APPROVAL),
  re-checked on POST since ndopify-server's update doesn't check the status; it posts the mandate's `version` it was
  loaded with (hidden field), and a 409 `MANDATE_VERSION_CONFLICT` shows the form again with the agent's answers, now
  based on the new version (the profile page does the same with `AGENT_VERSION_CONFLICT`); the city can't change, the
  neighbourhoods are checked by `SectorValidator`, and the rest by `FindPropertyRules` (the only validation a mandate
  gets). `public/assets/js/mandate.js` only enhances both pages (`data-page` `mandate` / `mandate-edit`). ndopify-server
  checks ownership (see the mandate domain): another agent's mandate is a 403, which the page shows as the locked view
  (and an action as `changed`, like a 404/409). A 403 / 404 from ndopify-server that no page catches goes to
  `HttpClientErrorHandler` (`ui/layout/`), which answers the same status so that Spring Boot renders
  `templates/error/403.html` / `404.html` (also used for unknown URLs). These pages have no account menu: the signed-in
  agent isn't known in Spring Boot's error controller.
- `src/main/html/` holds static mockups generated by the `/init-partner-central` command; the served pages are the
  Thymeleaf templates. `application-test.yml` / `application-prod.yml` still contain config copied from another
  project (`koki`) — don't treat it as meaningful.

### `ndopify-esignature`

Package `com.wutsi.ndopify.esignature` (port 8083, French UI): the contract sign page, a Spring Boot + Thymeleaf BFF with
no database that talks to `ndopify-server` over REST. Its infrastructure is copied from Partner Central (same class
names and signatures; depends on `ndopify-dto` only, never on partner-central), minus the agent / login parts.

- Tenant is resolved by `service/TenantContext` matching the request URL against each active tenant's
  `eSignaturePortalUrl` (not `partnerCentralUrl`). `ui/layout/TenantAdvice` puts it in every page's model as `tenant`
  (error pages included), for the header's logo (`logoUrl`, falling back to Ndopify's `logo.png`); null when it can't
  be resolved, so that an error page still renders.
- The JWT is kept in the HTTP-only `__nes_access_token` cookie (`AccessTokenService`, `Path=/`, 1 hour like the token's
  TTL) and decoded by `JwtAuthenticationFilter`, like Partner Central's.
- `SecurityConfiguration` permits only `/assets/**`, `/sign/**`, `/verify/**` and `/error`; every other path is denied (nothing else
  is served). The 403 / 404 pages (`templates/error/`) show the logo only, no account menu.
- `client/ndopify/` holds the ndopify-server clients: `NdOtpClient` / `NdAuthClient` (as Partner Central's),
  `NdContractClient` (get, search incl. `contract-party-id`, `sign`, `getSignature`) and the global `NdTenantClient`.
  `client/ContractContentClient` downloads the contract's `contentUrl`: HTML only, sanitized with Jsoup
  `Safelist.relaxed()` (the page renders it with `th:utext`), null on any failure (the page then can't sign), through
  the plain `contentRestClient` bean (`RestClient.create()`, not the Spring builder) so that the `Authorization` /
  `X-Tenant-ID` interceptors never reach a storage URL.
- The sign page is `SignController` (`ui/sign/`), one page (`templates/sign.html`, view model `SignView` in `view`,
  plus `contractPartyId`), all under `/sign/{contractPartyId}`, built like Partner Central's login. Signed out,
  nothing about the contract is shown: the intro asks for the e-mail the link was sent to, `POST /otp` (trimmed,
  lowercased; `resend=true` reuses the pending one) sends a code and records a `PendingSignature` (this link's
  `contractPartyId`, e-mail, expiry) in the session, and `?step=code` only shows while it's there for **this** link;
  `POST /verify` reads the e-mail from it (never the form) and authenticates with `ApplicationCode.E_SIGNATURE`.
  A 404 `USER_NOT_FOUND` (OTP) or 409 `AUTH_ACCESS_DENIED` (auth) is "Aucun contrat n'a été envoyé à cette adresse".
  Signed in (a `JwtAuthentication`), the contract is found by `search(contract-party-id)`, which ndopify-server
  restricts to the caller's party: none is the `not-yours` blocked view, with no contract data. `SignViews.build` then
  picks the step: done (signed, `?step=done`), blocked (`signed`, `not-required`, `cancelled`, `expired` incl. a
  deadline already past, `unavailable`), else `sign`, the only one that fetches the content (`ContractContentClient`),
  and signable (`canSign`) only with it. `POST /` rebuilds the sign view (fetching the content again) and signs only
  if it's still the `sign` step (else `?step=done`, without signing), `canSign`, and the form's hidden `contentUrl`
  (`SignView.contentUrl`, the version shown; only compared, never used to find anything) is still the contract's: else
  the sign step again, with the new content, and "Le contrat a été modifié depuis son affichage…". Then it needs
  `consent=yes`, signs as the URL's contract party with the first
  `X-Forwarded-For` (else `remoteAddr`) and the `User-Agent`, and goes to `?step=done`, also on a 403 / 404 / 409
  (a double tap's `CONTRACT_PARTY_ALREADY_SIGNED`: the done or blocked view says which). `POST /logout` ("Utiliser une
  autre adresse") clears the cookie and the session. Its warn logs carry ndopify-server's status and error code only,
  never the response body (which can name the e-mail).
- `templates/sign.html` renders only the current step (`th:if` on `view.step`), "Étape N sur 3" included, one
  `<form method="post">` per action (intro `/otp`, code `/verify`, resend `/otp` with `resend=true`, sign `/`, logout
  `/logout`; the resend button sits in the code form with `form="resend-form"`, since forms can't nest), so every step
  posts without JS. `view` dates are already formatted by `TenantFormatter`: print them as-is. Nothing of the contract
  in the intro / code steps; the contract's HTML goes through `th:utext` from `view.contentHtml` only (sanitized by
  `ContractContentClient`). No WhatsApp number, no signed-copy promise, no "Précédent" on the intro.
  `public/assets/js/esignature.js` only enhances (digit sync + "Suivant", consent enabling, busy state against double
  submit, focus on the code field / confirmation heading); it keeps no state. `SignTemplateTest` renders the template
  with a real Thymeleaf engine.
- The verify page is `VerifyController` (`ui/verify/`, `templates/verify.html`, view model `VerifyView` in `view`),
  `GET /verify/{contractId}/{signatureHash}`: the page the signed PDF's QR code opens. **Public**: no session, sets no cookie of its own
  (`JwtAuthenticationFilter` still clears an expired token's; a signed-in visitor's valid token is forwarded on the
  `getSignature` call, harmless: the tenant comes from the host), no JS (no `<script>`), `noindex` and `no-referrer`, HTTP 200 for both states. The hash is normalized
  (`trim().lowercase()`); one that isn't 64 hex characters is invalid without calling ndopify-server. Otherwise
  `NdContractClient.getSignature` (`GET /v1/contracts/{id}/signatures/{hash}`): only ndopify-server's 404 `CONTRACT_SIGNATURE_NOT_FOUND` (null) is the invalid view, any
  other error (5xx, any other 404 such as an unknown route or a proxy's, other 4xx, I/O) is thrown to Spring Boot's error page, **never** shown as "not valid". Valid
  whatever the contract's status (the server decides), and the hash printed is the URL's, only once the server matched
  it. It shows only the contract number, the signer's legal name, the date and time (`TenantFormatter`) and the hash:
  never add more, and no link to the signed PDF (whoever has a QR code, ex: a photo of one page, would get the whole
  contract).
- `src/main/html/` (`sign.html`, `verify.html` + assets) is the prototype: never serve it, and never delete or "clean up" anything in
  it; the served pages are `templates/sign.html` and `templates/verify.html`.

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
  back to the `X-Tenant-ID` header (`HttpHeader.TENANT_ID` in `ndopify-dto`) if there's no principal. A token that
  doesn't decode (expired, malformed) is a 401 `AUTH_UNAUTHORIZED`, even on a public endpoint, never a fallback to
  the header.
- Any JPA entity annotated `org.hibernate.annotations.@TenantId` is automatically filtered by Hibernate for every
  query against it — no `CriteriaBuilder` predicate, repository override, or service-level check needed. Today
  that's `PartyEntity`, `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, `KycCaseEntity`,
  `AgentEntity`, `CustomerEntity`, `MandateEntity`, `AssignmentEntity`, `ContractEntity`, `ContractPartyEntity` and
  `MembershipEntity`. Leave the field `null` on construction (see the comment on any of them) so Hibernate's
  `TenantIdGeneration` populates it from `TenantContext` on insert — setting a non-null value yourself makes
  Hibernate treat it as caller-assigned and throw if it doesn't match the resolved tenant.
- `KycVerificationEntity` is deliberately **not** `@TenantId`-annotated: it's never loaded by its own id anywhere in
  the codebase, only reached via the already-tenant-filtered `KycCaseEntity.verifications` association. Same for
  `UserApplicationEntity`, only looked up through an already-filtered `MembershipEntity`.
- If you add tenant-scoped data to a new or existing domain, add `@TenantId` to the entity — don't write a manual
  predicate; that duplicates enforcement and can silently drift from what Hibernate actually filters.
- refdata entities are still global: `TenantEntity`, `LocationEntity`, `ApplicationEntity`, `RoleEntity` have no
  `tenantId` and are not filtered. `UserEntity` and `AuthFactorEntity` in the `security` domain are global too: a
  user is looked up by e-mail before its membership of the tenant is checked (see `AbstractAuthenticator`). There
  are no cross-tenant users: a user only sees the tenants it has a membership of. `T_USER.tenant_id`,
  `T_USER.party_id` and `T_USER_APPLICATION.user_id` are left over from before memberships (`V7_0`), no longer
  mapped or written, and still to be dropped.
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
  set `anonymousUser = true` to omit it, `userId` to call as another user, and override `roles` (ex:
  `arrayOf(RoleCode.ADMIN)`) to call as an admin. The token's tenant is `overrideTenantId` when set. The header is
  `set`, not added, so that the current instance's token wins over the interceptors of earlier instances.

Mock Spring beans in `@SpringBootTest` tests with `@MockitoBean`
(`org.springframework.test.context.bean.override.mockito.MockitoBean`), not the removed `@MockBean`.

The mandate dispatch in the `qa` profile has no night (`night.start == night.end`) and no cron (`-`), so its
endpoint tests don't depend on when they run: their fixtures date mandates `NOW()` and offer deadlines in 2099 / 2020.
The night is unit tested (`DispatchHoursTest`, `MandateDispatcherTest`), and so are the jobs (`MandateJobsTest`, with
the batch methods in `AssignmentServiceTest`). If a test has to mock the `Clock` bean (only `clock.millis()` is
used), mind that the JWT of `AuthorizationAwareEndpointIntegrationTest` then gets its expiry from the mock while it's
checked against the real time; and that every test instance registers itself as an interceptor on the shared
`TestRestTemplate`, so the interceptors of earlier instances still run.

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
- `EnumConverter` / `EnumConverters` — every enum of an entity is stored by **name** in a nullable `VARCHAR(30)`
  column, never by ordinal, through an `@Converter(autoApply = true)` subclass in `EnumConverters.kt` (so entity
  fields need no annotation; `util.jpa` is in `@EntityScan` for that). A NULL column, or a name the enum no
  longer has, reads as the converter's default (`UNKNOWN`, or `null` for an optional enum), so a non-null Kotlin field
  never gets a null. A new enum stored in an entity needs its converter there. Inserting or reordering enum values is
  safe; renaming one needs a migration updating the rows. Native SQL binds `.name`, never `.ordinal`, and SQL fixtures
  write the names (`'VERIFIED'`).
- `EnumListConverter` / `EnumListConverters` — base of the converters storing a `List` of enums as comma-separated
  names (blank items and names the enum no longer has are skipped), set with `@Convert`: one line per enum in
  `EnumListConverters.kt` (ex: `AuthTypeListConverter`).

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
- A JaCoCo coverage badge (`ndopify-server`, `ndopify-partner-central` and `ndopify-esignature`), pointing at the auto-generated SVG in
  `.github/badges/<module>-jacoco.svg`.

These badges must be preserved whenever a README is edited. Edit module READMEs with a targeted edit (not a
full-file rewrite) so the badge block isn't clobbered — and if a full rewrite is unavoidable, read the existing
file first and carry the badge lines over verbatim.
