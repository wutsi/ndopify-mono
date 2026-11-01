# ndopify-server

Guidance for `modules/ndopify-server` (the REST API, port 8080, Swagger UI at `/api.html`). Build, test and
environment setup, and the rules shared by all modules, are in the root `CLAUDE.md`.

## Code Organization

Under `src/main/kotlin/com/wutsi/ndopify/`:

- `<domain>/server/` — one package per business domain (see *Domains* below), layered as shown there. A domain's
  DTOs live in `ndopify-dto` under `com.wutsi.ndopify.<domain>.dto`, never here.
- `platform/` — shared infrastructure with no business logic (mail, storage, mobile money, logging, tenant, AI,
  ops endpoints, utilities), see *platform* below.
- `config/` — Spring configuration: security, tenant / JPA tenancy, OpenAPI, `Clock`, test-database reset;
  `config/platform/` wires the `platform` beans.
- `util/jpa/` — JPA converters and naming strategy, see *Custom JPA Utilities* below.

Under `src/main/resources/`: `application*.yml`, Flyway migrations (`db/migration/`), MJML e-mail templates
(`mail/<domain>/`), contract templates (`mandate/`), the signed-PDF assets (`contract/`: fonts, signature block),
refdata seed files (`refdata/`) and `messages.properties`.

Conventions in every domain:

- An endpoint class only maps HTTP to the service and mapper, and runs the domain's access check (`*AccessService`
  for reads); the business rules and the locks are in the service.
- Errors are thrown as `WutsiException` subclasses (`NotFoundException`, `ConflictException`...) carrying an
  `ErrorCode` from `ndopify-dto` (`<ENTITY>_NOT_FOUND`, `<ENTITY>_VERSION_CONFLICT`, `<ENTITY>_NOT_<STATUS>`...);
  `ControllerErrorHandler` turns them into an `ErrorResponse`. A new error code goes in `ErrorCode.kt`.
- A search takes a `Search<Entity>Request` bound with `@ModelAttribute`, whose query parameters are kebab-case
  (`@param:BindParam("agent-id")`), list parameters repeated (`status=A&status=B`), and `limit` capped through
  `OffsetLimitPageRequest.limit`. It returns `<Entity>Summary`s; get returns the full `<Entity>`.
- Writes are `POST` (`POST /v1/<entities>/{id}` updates, `POST /v1/<entities>/{id}/<action>` for a transition;
  `DELETE` only to remove a child, ex: a listing photo), and a KDoc on the service method lists its error codes in
  the order they're checked.

## Domains in `ndopify-server`

Each domain under `com.wutsi.ndopify.<domain>.server` follows the same layering:

```
<domain>/server/
    endpoint/   - @RestController classes (*Endpoints.kt)
    service/    - business logic (*Service.kt)
    dao/        - Spring Data JPA repositories (*Repository.kt)
    domain/     - JPA entities (*Entity.kt)
    mapper/     - Entity ↔ DTO mapping (*Mapper.kt)
    io/         - import/export (refdata only, e.g. GeonamesImporter, NeighbourhoodImporter)
    event/      - application events + their listeners (agent, party, security, mandate, listing so far)
    mail/       - the domain's e-mails (*Mailet.kt: recipient, subject, template context), e.g. WelcomeMailet
```

All of a domain's mail-related classes go in its `mail/` package, never in `service/`: a mailet builds the `Email`
and hands it to `platform/mail`'s `MailService`; its MJML template lives under `src/main/resources/mail/<domain>/`
(e.g. `mail/agent/welcome.mjml`). The service method that triggers it (see *Asynchronous e-mails*) stays in
`service/`. Dates and amounts in e-mails (and generated contracts) go through `TenantFormatter`
(`refdata/server/service/`): the tenant's `dateFormat` / `timeFormat` / `monetaryFormat` / `numberFormat`, first
locale and `timeZone` — never a hard-coded pattern. Mailet unit tests mirror the package (`<domain>/server/mail/*MailetTest.kt`).

Scheduled jobs live in `<domain>/server/job/`: `KycJobs` (party), `MandateJobs` (mandate), `ContractJobs`
(contract), `ListingJobs` (listing, every 15 minutes: `verify`, the UNDER_VERIFICATION listings through
`ListingVerifierService`; `publish`, the VERIFIED ones sent for publication, then the PUBLISHING ones through `ListingPublisherService`) and
`MatchJobs` (match, hourly: `notifyAgents`, see *match* below). They loop over the
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
  (global), then its membership of the tenant (`AUTH_ACCESS_DENIED` without one), whose roles go in the token, with
  its party's id (`partyId` claim, `JwtPrincipal.getPartyId`; none for a party-less member): for clients only:
  `CallerService` still goes through the membership. A
  person in two tenants has one user, one set of credentials, and a membership (party, roles) in each. Nothing
  creates a membership on sign-in (not even Google One Tap): only registration (`AgentService.create`) does, and a request
  for signature (`ContractSignerService`), for the parties asked to sign, with `e-signature` access, and a match
  proposal e-mail (`MatchLinkTokenService`), for the customer, with `public-portal` access.
  `GET /v1/users/{id}` (`UserEndpoints`) returns the user with its party in the current tenant embedded, and 404
  `USER_NOT_FOUND` for a user who isn't a member of it. It needs a token (see *Authentication* below) but
  doesn't check whose user it is yet. A party's e-mail is its contact e-mail in the tenant, not synced to the user's (global) login
  e-mail.
- **party** — `Party`, `Identification` (+ uploaded `IdentificationImage`s), `KycCase` / `KycVerification`, and
  `PaymentMethod`. KYC cases are verified by pluggable `KycVerifier` strategies (`KycVerifierIdentification`,
  `KycVerifierMoMo`, `KycVerifierManual`) selected via `KycVerifierProvider`. Identity-document data is extracted
  by `KycInfoExtractorIdentification` / `KycInfoExtractorSelfie` (vision LLM via Spring AI, `ChatClients.VISION`),
  behind `KycInfoExtractorProvider`.
  KYC case lifecycle: `PENDING_VERIFICATION` (images uploaded via `/v1/kyc/cases/{id}/images/upload`) → `POST
  /v1/kyc/cases/{id}/submit` → `SUBMITTED` (requires FRONT, plus BACK for two-sided documents: `NATIONAL_ID`,
  `DRIVER_LICENSE`, `RESIDENT_CARD`; SELFIE optional server-side, Partner Central requires it; images frozen) →
  `POST .../verify` → `UNDER_VERIFICATION` → `VERIFIED` / `REJECTED`, which publishes a `KycCaseCompletedEvent`: the
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
  neighbourhoods, prices and enums aren't validated server-side yet. `availableFrom` (`V7_2`) is a LISTING's
  availability date (the date from which the property can be let): stored and returned, not checked by the server.
  Tenant-scoped.
  **Access** (`MandateAccessService`): every endpoint but create (public) needs a JWT (401
  `AUTH_UNAUTHORIZED`), and the caller must be an admin (`CallerService.isAdmin`: `admin-console` token with the
  `admin` role, `RoleCode`) or the mandate's agent (JWT user → membership → party → agent), else 403
  `MANDATE_ACCESS_DENIED`, after the 404 and before any 409. The agent it's offered to (PENDING assignment) may also
  get it, not change it. An agent's search is restricted to its own mandates (`agent-id` forced; another agent's is a
  403), or with `offered-to` (its own id only, else 403) to the mandates with a PENDING offer to it: those it may get.
  `GET /{id}` is also open to the mandate's customer (`ensureCanReadAsCustomerOrAgent`, any application, any
  status), for its match proposal page.
  A search's `MandateSummary` has what a card shows (criteria, amenities, `movingPeriod`, `availableFrom`) and its
  `contractId` only (a client needing the contract's dates searches `/v1/contracts?id=`), not the terms nor the
  customer's comments. Reads are checked by `MandateEndpoints`, changes by `MandateService` (under the mandate's lock); the server's own
  reads (dispatch, listeners, jobs) aren't. Partner Central has no admin role: admins only exist in the admin console.
  `POST /v1/mandates/{id}` (update) locks the mandate's contract, then the mandate (see *Lock order*), then 409
  `MANDATE_ALREADY_CLOSED` (CLOSED / CANCELLED), `MANDATE_ALREADY_APPROVED`, then `MANDATE_VERSION_CONFLICT` (stale
  `version`, or a contract replaced since it was read). A PENDING_APPROVAL mandate goes back to ASSIGNED and its
  contract is cancelled, so the customer can't sign the terms of before the change; request-approval then creates a
  new contract (a CANCELLED one is replaced, like an EXPIRED one). A lock is always taken before the entity is loaded
  in the transaction (ids / `contractId` read through projections first): Hibernate doesn't refresh an entity it
  already has when `findByIdForUpdate` locks it, so a check would run on stale values.
  `POST /{id}/request-approval` (ASSIGNED or PENDING_APPROVAL, else 409 `MANDATE_CANNOT_BE_APPROVED`) →
  `PENDING_APPROVAL`, its contract's content generated and stored, and its signature requested, in 3 transactions so that
  no lock (the tenant's contract sequence, the mandate) is held during the upload: (1) under the mandate's lock, reuse
  its contract or create one (none, EXPIRED or CANCELLED) and link it, so concurrent requests share one contract; (2) generate
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
  `cancellationReason` and `closedAt` (no age), expires its PENDING offer, without re-dispatch, and cancels its contract
  (`ContractService.cancel`, in the same transaction: a DRAFT, PENDING_SIGNATURE or SIGNED one becomes `CANCELLED`; an
  EXPIRED one is left as is; the contract is locked before the mandate, see *Lock order*). Both lock the
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
  `MandateOfferMailet` e-mails the agent the mandate and its deadline (`mail/mandate/offer.mjml`). The mandate e-mails
  (offer, assigned, approved) have a template per mandate type: a LISTING one uses `<name>_listing.mjml` (the owner's
  property, its rent or price), anything else `<name>.mjml`; type-specific wording goes in the template, not the mailet. The dispatch always
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
  contract (`findByIdForUpdate`), after a 400 `HTTP_INVALID_PARAMETER` for an `ipAddress` that isn't an IPv4 / IPv6
  address (`InetAddressValidator`, no DNS) or over 45 characters, or a `userAgent` over 1024 (`@Size` on the request), then 409 `CONTRACT_NOT_PENDING` / `CONTRACT_EXPIRED` (past `signatureDeadline`) /
  `CONTRACT_PARTY_SIGNATURE_NOT_REQUIRED` / `CONTRACT_PARTY_ALREADY_SIGNED` / `CONTRACT_CONTENT_MISMATCH`: the request's
  `contentHash` (required, 400 if empty; the SHA-256 of the content the signer was shown, case-insensitive) must be the
  contract's `contentHash`, so a contract without one (ex: content set by `POST /{id}/content`) can't be signed; the last signature of the parties whose
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
  `signedContentUrl` (in `Contract` only, never in `ContractSummary`: a search, or a mandate search's embedded
  contract, mustn't hand out the signed PDF's link) and `signedContentHash` (SHA-256 of the PDF, stored only)
  under the lock, first one wins. Nothing is locked during the download / render / upload. No PDF (logged) without
  content in the storage, with a content hash mismatch, a signature without hash or salt, or no
  `eSignaturePortalUrl`. A PDF lost on the way (failure, server stopped, executor queue full) is generated by
  `ContractJobs.generateSignedContent` (`SignedContractService.generateMissing`, hourly,
  `ndopify.contract.signed-content.*`): the SIGNED contracts without `signedContentUrl` signed between `window-days` (7)
  and `delay-minutes` (10) ago, so that it doesn't race the listener nor retry forever one that can't be generated.
  `POST /v1/contracts/{id}/signed-content` (`@OpsEndpoint`, admin) generates it again. The
  QR code opens ndopify-esignature's verify page.
  **Access** (`ContractAccessService`, checked by `ContractEndpoints`, without lock since a contract's parties never
  change): every endpoint but the signature's verification (`GET /{id}/signatures/{hash}`, public: the contract number, legal name and `signedAt` of a signed contract party (never the signed PDF: whoever has a QR code would get the whole contract) with that hash, else 404 `CONTRACT_SIGNATURE_NOT_FOUND`) needs a JWT (401 `AUTH_UNAUTHORIZED`). Get and search are for an admin or one of the
  contract's parties (JWT user → membership → party), else 403 `CONTRACT_ACCESS_DENIED` (after the 404); a non-admin's
  search is restricted to its own party (`party-id` forced; another party's is a 403). With both `party-id` and
  `contract-party-id`, the contract party must be one of those parties' (same `T_CONTRACT_PARTY` row): a co-party of a
  contract finds nothing with another party's contract-party id (the sign page's `not-yours`). Create, content and
  request-signature are admin-only (`MandateService` calls the service directly, unchecked). Sign is only for the
  party itself, from the e-signature portal (the JWT's application must be `e-signature`, else 403
  `CONTRACT_ACCESS_DENIED`, and its party the contract party's `partyId`): **no admin bypass, nobody signs on another's
  behalf** — never add one. `request-signature` gives each party asked to sign
  (`signatureRequired`, not signed yet) a membership of the tenant, as its party, with `e-signature` access
  (`ContractSignerService`, in the request's transaction, before the save): the one it has (an agent), else the user of
  its `legalEmail` (created if needed). An address already a member's as another party is a 409
  `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`: contract unchanged, no e-mail. Idempotent. So a customer signs in to the
  e-signature portal by OTP once asked to sign; only that and a match proposal (`MatchLinkTokenService`) create its
  membership, never `CustomerService`. A mandate's contract (`MandateService.createContract`) has the customer as
  `MANDATOR` (signs it) and its agent as `AGENT` with `signatureRequired = false`: a party, so it can read the
  contract, but it's neither asked to sign nor waited for.
  `ContractJobs.expire` (daily, `ndopify.contract.expire.cron`) moves to `EXPIRED` the PENDING_SIGNATURE contracts past
  their `signatureDeadline` and the SIGNED ones past their `validUntil` (each locked, in its own transaction), publishing
  `ContractExpiredEvent` (no listener yet). Tenant-scoped.
- **listing** — the property a LISTING mandate offers, as checked on site by its agent (`ListingEntity`, one per
  mandate, `@Version`-ed; its photos `ListingImageEntity`; its checklist `ListingSectionEntity`; `V8_0`, `V8_1`,
  `V8_3`). `/v1/listings`: search, get, create, update, `POST /{id}/verify`, `POST /{id}/publish`, `POST /{id}/images/upload`, `GET /{id}/images`,
  `DELETE /images/{id}`. Search, get and the images list are public (`PublicEndpoints.TENANT`), for PUBLISHED
  listings only (`ListingAccessService`): another status is only for an admin or the listing's agent, a 404
  `LISTING_NOT_FOUND` for anybody else (get, images), with or without a token; a search is forced to PUBLISHED but an
  admin's, or an agent's with its own `agent-id`; the images to VERIFIED but for an admin or the listing's agent. The fields aren't trimmed. The changes (create, update, verify, publish, upload or delete a photo) are only
  for an admin or the listing's agent (the mandate's, for create): 401 without a token, 403 `HTTP_ACCESS_DENIED`
  otherwise, after the 404 and before any 409, checked by the services. `ListingService.create` copies the mandate's
  property (404 `MANDATE_NOT_FOUND`, 409 `MANDATE_NOT_ASSIGNED` without agent, since the listing's agent is the
  mandate's; 409 `LISTING_ALREADY_EXISTS`) and starts it as `DRAFT`. A mandate's listing is found with
  `GET /v1/listings?mandate-id=` (repeatable; with the agent's own `agent-id` for a DRAFT): the `mandate` domain doesn't depend on
  `listing`, so `Mandate` has no `listingId`. **Checklist**: an update saves *one* `ListingSection` (`LOCATION`, `GPS`, `PROPERTY`, `AMENITIES`,
  `TERMS`, `REMARKS`), only that section's fields, as sent (a null clears an optional field), and marks it
  `VERIFIED` (`ListingSectionService.verify`): a section counts only once the agent saved it, the landlord's answers
  alone don't. `PHOTOS` is never saved: it's `VERIFIED` once there are `MIN_PHOTOS` (5) photos and none `REJECTED`.
  `REMARKS` is optional. `Listing.sections` carries each section's `TO_VERIFY` / `VERIFIED` status. Update errors:
  409 `LISTING_VERSION_CONFLICT` (`UpdateListingRequest.version`), 409 `LISTING_NOT_DRAFT` once sent, then 400 for no
  section or `PHOTOS`, a missing required field, or a neighbourhood outside the listing's city. The city never
  changes: it's the mandate's, the agent's area. Photos (JPEG / PNG / WEBP) are added `PENDING_VERIFICATION`, and
  can only be deleted from a DRAFT listing; the file stays in the storage (`StorageService` can't delete one).
  **Send**: `POST /{id}/verify` moves a DRAFT to `UNDER_VERIFICATION`, checked asynchronously: 404
  `LISTING_NOT_FOUND`, 409 `LISTING_NOT_DRAFT`, then 409 `LISTING_INCOMPLETE` while a required section of the
  checklist isn't `VERIFIED`. `ListingJobs.verify` runs an UNDER_VERIFICATION listing through
  `ListingVerifierService`: `VERIFIED`, or back to `DRAFT` with the verifiers' `errorCodes` for the agent to fix and
  send again; a failed attempt leaves it UNDER_VERIFICATION for the next run, then after `MAX_RETRIES` back to `DRAFT`
  with `INTERNAL_ERROR`. `PENDING_VERIFICATION` isn't used for a listing (only for its photos). `VerifyListingsJobTest`
  covers the send and the job together, end to end: keep it so. There is no rejected status:
  a DRAFT with `errorCodes` is one sent back. **Publish**: `POST /{id}/publish` moves a `VERIFIED` listing
  to `PUBLISHING` (404 `LISTING_NOT_FOUND`, then 409 `LISTING_NOT_VERIFIED`), `retries` reset; `ListingJobs.publish`
  then has `ListingPublisherService` generate its title, summary and description (`ListingContentGenerator`, text
  LLM, `ChatClients.TEXT`) and its hero image (the model's, else the verified photo of highest quality, then
  largest), and its public `url`, the path `/<city>/<neighborhood>/listing/<id>/<slug of the title>` (no host: the
  tenant's `publicPortalUrl`; no neighbourhood nor title segment without them): `PUBLISHED`, or still `PUBLISHING` to be retried after a failure; after `MAX_RETRIES` failed attempts
  it's `PUBLISHED` without title, summary nor description (the UIs handle it), its last error kept. Either way it
  publishes `ListingPublishedEvent`: `ListingPublishedMailet` e-mails the agent (not the customer, for now), with a
  link to `{publicPortalUrl}{url}` (`mail/listing/published_customer.mjml` / `published_agent.mjml`; no link without
  either). `RENTED`, `SOLD`, `EXPIRED`, `CANCELLED` aren't set by any code yet.
  Tenant-scoped.
- **match** — the listings of an agent's inventory matching its SEARCH mandates (`MatchEntity`, `T_MATCH`, `V9_0`,
  one row per (mandate, listing), `@Version`-ed), see `docs/superpowers/plans/2026-10-31-search-listing-matching.md`
  for the algorithm. Depends on `mandate` and `listing`, never the reverse: `MatchEventListener` (async, after commit)
  runs `MatchService.matchMandate` on `MandateApprovedEvent` and `matchListing` on `ListingPublishedEvent`. Only an
  APPROVED SEARCH mandate with an agent is matched, against that agent's PUBLISHED listings; `Matcher` (pure, unit
  tested case by case in `MatcherTest`) rules a pair out (the customer's own property, another type / category / city
  / property type, currency or periodicity, over budget + `price-tolerance`) or scores it (neighbourhood, price, rooms,
  amenities, availability, furniture); under `min-score` it isn't stored. Each score also keeps what explains it to
  the agent (`V9_1`): `distanceKm` (to the nearest searched neighbourhood: 0 inside one, null when not nearby or none
  searched) and `missingAmenities` (the amenities searched that the listing lacks, by DTO field name, ex:
  `waterTank`). `ACTIVE` rows are created, re-scored or deleted; any other status (`SHORTLISTED`, `DISMISSED`,
  `PROPOSED`, `ACCEPTED`, `REJECTED`) is never touched. Each row keeps
  `listingPrice` (when last scored, or dismissed, proposed or rejected): the
  price a match was turned down at, to tell a price drop later (re-proposing after one isn't built yet). The run isn't one transaction (each save is its own, a conflict skips
  the pair), so it reads the mandates with their neighbourhoods (`@EntityGraph`). `/v1/matches`: search
  (`mandate-id` and / or `listing-id`, repeatable, at least one of them, else 400 `HTTP_MISSING_PARAMETER`, each one
  checked: a mandate as below, a listing by its agent or an admin, `ListingAccessService`: 404 `LISTING_NOT_FOUND`
  then 403 `HTTP_ACCESS_DENIED`, ex: who a published listing interests with `status=ACCEPTED`; `status` default
  ACTIVE; best score first across them, only while the mandate is APPROVED and the listing
  PUBLISHED), get, `POST /{id}/shortlist` (the agent keeps it to propose later: ACTIVE → SHORTLISTED, else 409
  `MATCH_NOT_ACTIVE`), `POST /{id}/dismiss` (ACTIVE or SHORTLISTED, else 409 `MATCH_NOT_ACTIVE`), access as the mandate's (`MandateAccessService`:
  404, then 403 `MANDATE_ACCESS_DENIED`); `POST /run?mandate-id=` (`@OpsEndpoint`) re-runs a mandate.
  **Agent alert** (`docs/superpowers/plans/2026-10-31-match-agent-alert.md`): `notifiedAt` (`V9_2`) is null until the
  agent is told, and never touched by the matcher (a re-score or re-run doesn't alert again). `MatchJobs.notifyAgents`
  (hourly, `ndopify.match.notify.cron`) has `MatchNotificationService` e-mail each agent, once for all its ACTIVE
  matches not notified yet (mandate APPROVED, listing PUBLISHED), grouped by mandate (`MatchNotificationMailet`,
  `mail/match/new_matches.mjml`, at most 5 listings per mandate), then mark them (`MatchRepository.markNotified`, a bulk
  update that skips `@Version`, so a dismiss meanwhile stays). A failed e-mail leaves the agent's matches to the next
  run. A match re-proposed after a price drop must get `notifiedAt = null` again.
  **Proposal** (`docs/superpowers/plans/2026-10-31-match-proposal.md`, `MatchProposalService`): `POST /{id}/propose`,
  or several matches of one mandate with `POST /propose` (`ProposeMatchRequest.matchIds`, 1 to 20; 400
  `HTTP_INVALID_PARAMETER` when of several mandates), by the mandate's agent or an admin, as dismiss: ACTIVE or
  SHORTLISTED → PROPOSED, `proposedAt`, all or none; 409 `MATCH_NOT_ACTIVE`, `MANDATE_NOT_APPROVED`,
  `LISTING_NOT_PUBLISHED`. It publishes one `MatchProposedEvent` (`matchIds`): `MatchProposedMailet` sends the
  customer one e-mail with the listings, best score first, each with a link to its proposal page,
  `{publicPortalUrl}/match/{matchId}?token={jwt}` (`mail/match/proposed.mjml`), where the customer answers it. The
  customer reads it with `GET /v1/matches/{id}` (and its mandate), only once PROPOSED, ACCEPTED or REJECTED (else 403
  `MANDATE_ACCESS_DENIED`; as `ensureCanRead` for an admin or agent: an agent only its own / offered mandates). The
  token (`MatchLinkTokenService`, one per e-mail) is a `public-portal` JWT of the customer's own user and party, so
  that the link signs it in; it lives `ndopify.security.access-token.ttl.match-link` (2d) and isn't scoped to the
  match: whoever has the e-mail is signed in as the customer until then. The customer gets a membership of the tenant
  with `public-portal` access first (its own, else the user of its e-mail); no `token` without an e-mail, or when
  that address is already a member's as another party. `POST /{id}/accept` and
  `POST /{id}/reject` (`RejectMatchRequest.reasonCode`, a `MatchRejectionReason`, `OTHER` when missing or `UNKNOWN`,
  and `reason`, a comment, optional, at most 1000) are for the mandate's customer only
  (`MandateAccessService.ensureIsCustomer`: the JWT's party is the customer's, any application; **no admin bypass,
  the agent can't answer for its customer**): PROPOSED → ACCEPTED / REJECTED, final, `answeredAt`, else 409
  `MATCH_NOT_PROPOSED`. The agent isn't told of the answer yet. Config: `ndopify.match.*`. Tenant-scoped.
- **error** — shared exception types (`WutsiException` subclasses: `BadRequestException`, `NotFoundException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `InternalErrorException`) and a global
  `ControllerErrorHandler`.

**Asynchronous e-mails** (welcome, OTP, KYC status, mandate offered, mandate assigned, mandate approved, contract to sign, listing published, match proposed): the service publishes an event
from its transaction (`AgentCreatedEvent`, `OtpCreatedEvent`, `KycCaseCompletedEvent`, `AssignmentCreatedEvent`,
`AssignmentAcceptedEvent`, `MandateApprovedEvent`, `ContractSignatureRequestedEvent`, `ListingPublishedEvent`, `MatchProposedEvent` in `<domain>/server/event/`), carrying ids plus the `tenantId` — never entities. Its
listener is `@Async @TransactionalEventListener(AFTER_COMMIT)`, so nothing is sent for a rolled-back transaction. The
executor thread has no request, so the listener sets `TenantContext` and a `DefaultKVLogger` in `KVLoggerThreadLocal`
(which the injected `KVLogger` falls back to), calls `logger.log()` in `finally` to flush it, and removes both
(threads are reused). The work runs in a `@Transactional` service method (`AgentService.sendWelcomeEmail`,
`OtpService.sendEmail`, `KycService.sendStatusEmail`, `AssignmentService.sendOfferEmail` / `sendAssignedEmail`, `MandateService.sendApprovedEmail`,
`ContractService.sendSignatureRequestEmails`, `ListingService.sendPublishedEmails`, `MatchProposalService.sendProposedEmail`); the signed PDF (`ContractSignedEvent` → `SignedContractService.generate`,
not an e-mail, not transactional) follows the same listener pattern. The service methods are
entered *after* `TenantContext` is set since Hibernate resolves the tenant when the session opens. Failures are
logged, never rethrown. Tests must wait for the mail (`smtp.waitForIncomingEmail`);
`BaseEndpointIntegrationTest.tearDown` waits for the running async tasks, so a test's mail can't reach the next one.

**Lock order**: a write that locks both a mandate and its contract locks the **contract first**, the order of a
signature (`ContractService.sign`, then `MandateService.onContractSigned`): `MandateService.cancel`, `update` and
request-approval's step (3) (`ContractService.findByIdForUpdate`). The reverse order deadlocks with a last signature.
`ControllerErrorHandler` answers a lock not acquired (`PessimisticLockingFailureException`: deadlock, lock wait timeout)
with 409 `HTTP_VERSION_CONFLICT` rather than 500.

**Optimistic locking**: `MandateEntity`, `AgentEntity`, `ContractEntity`, `KycCaseEntity`, `AssignmentEntity`,
`ListingEntity` and `MatchEntity` are `@Version`-ed (`version` column, `V7_0` / `V7_1` / `V8_0`; a listing's conflict is
`LISTING_VERSION_CONFLICT`, and `UpdateListingRequest.version` is checked like the mandate's). Hibernate checks it on flush (`UPDATE ... WHERE version = ?`), so it only
catches concurrent transactions; `ControllerErrorHandler` turns the `ObjectOptimisticLockingFailureException` into a
409 named after the entity (`MANDATE_VERSION_CONFLICT`, `AGENT_VERSION_CONFLICT`, `CONTRACT_VERSION_CONFLICT`,
`KYC_CASE_VERSION_CONFLICT`; `ASSIGNMENT_NOT_PENDING` for an offer, `MATCH_NOT_ACTIVE` for a match). Across requests, `Mandate` / `Agent` expose
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
  `ControllerErrorHandler` answers 404 `HTTP_NOT_FOUND` for an unknown path (`NoResourceFoundException`). They need a
  token (any user's: none checks a role yet), like every non-public endpoint: mark any endpoint that mustn't be open
  in prod with it.
## Multi-Tenancy — current state

Tenancy is enforced via **Hibernate's built-in multi-tenancy mechanism**, not hand-rolled predicates:

- `TenantContext` (`platform/tenant/TenantContext.kt`) is a `ThreadLocal<Long?>` holding the current request's
  tenant.
- `TenantIdentifierResolver` (`platform/tenant/jpa/TenantIdentifierResolver.kt`) implements Hibernate's
  `CurrentTenantIdentifierResolver<Long>`, resolving from `TenantContext` and failing closed to `NO_TENANT = -1L`
  when unset.
- `TenantContextFilter` (`security/server/servlet/TenantContextFilter.kt`), registered via
  `config/TenantConfiguration.kt`, populates `TenantContext` per request from the tenant claim of the decoded JWT
  (`JwtPrincipal.CLAIM_TENANT_ID` / `AccessTokenService`); an `X-Tenant-ID` header (`HttpHeader.TENANT_ID` in
  `ndopify-dto`) naming another tenant is a 403 `AUTH_ACCESS_DENIED`. Only without a token (or with one without
  tenant), and only on a `PublicEndpoints.TENANT` endpoint, does the tenant come from the header; any other is a 401
  `AUTH_UNAUTHORIZED`, so that the header never opens a tenant's data. A token that doesn't decode (expired,
  malformed) is a 401 `AUTH_UNAUTHORIZED`, even on a public endpoint, never a fallback to the header.
- **Authentication**: `SecurityConfiguration` denies by default: every endpoint needs a token
  (`JwtAuthenticationFilter` authenticates a decoded `Authorization: Bearer` one) but `PublicEndpoints`, the single
  list shared with `TenantContextFilter`: `GLOBAL` (refdata, Swagger, storage, actuator, `/error`; no tenant) and
  `TENANT` (`POST /v1/otp`, `POST /v1/auth`, `POST /v1/agents`, `POST /v1/mandates`,
  `GET /v1/contracts/{id}/signatures/{hash}`: called before the caller has a token). Without a token, anything else,
  incl. an unknown path, is a 401 `AUTH_UNAUTHORIZED` (`ErrorResponse`, through `ControllerErrorHandler`). A new
  endpoint called before sign-in must be added to `PublicEndpoints.TENANT`. Authentication only: who may do what is
  still each domain's access service (`MandateAccessService`, `ContractAccessService`...), and many endpoints have none
  yet.
- Any JPA entity annotated `org.hibernate.annotations.@TenantId` is automatically filtered by Hibernate for every
  query against it — no `CriteriaBuilder` predicate, repository override, or service-level check needed. Today
  that's `PartyEntity`, `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, `KycCaseEntity`,
  `AgentEntity`, `CustomerEntity`, `MandateEntity`, `AssignmentEntity`, `ContractEntity`, `ContractPartyEntity`,
  `MembershipEntity`, `ListingEntity`, `ListingImageEntity`, `ListingSectionEntity` and `MatchEntity`. Leave the field `null` on construction (see the comment on any of them) so Hibernate's
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
- **Token signing**: `AccessTokenService` signs and verifies the JWTs with HS256 (`ndopify.security.jwt.secret`, at
  least 32 bytes, else the server doesn't start): a dummy secret in `application.yml` (local dev) and
  `application-qa.yml` (tests), the `JWT_SECRET` environment variable in `application-test.yml` / `application-prod.yml`
  (no default: the server doesn't start without it), set on Heroku by `ndopify-server-main.yml` from the GitHub secret
  `JWT_SECRET`. A token not signed with it (incl. `alg: none`) is a 401 `AUTH_UNAUTHORIZED`. Only ndopify-server has
  the secret: Partner Central's and the e-signature portal's `JwtDecoder` read the claims **without checking the
  signature** (only the expiry and the application; not the algorithm nor the issuer, which anyone can write), and
  rely on ndopify-server checking it on each
  of their calls: their `HttpClientErrorHandler` answers a 401 from it by clearing the cookie (back to `/login`, or the
  sign link / verify page). So a web app never acts on a claim alone, only uses it to pick what to ask ndopify-server.
  Changing `JWT_SECRET` signs everyone out. A token lives as long as its application's TTL
  (`ndopify.security.access-token.ttl.<ApplicationCode>`, `AccessTokenProperties`, else `default-ttl`; an environment
  overrides one in its `application-<profile>.yml`); the web apps keep their cookie until the token's `exp`.

## Testing Patterns

Tests mirror the main package (`<domain>/server/<layer>/<Class>Test.kt`) and come in two kinds:

- **Endpoint tests** (`<domain>/server/endpoint/`): one class per endpoint operation, named after it
  (`GetListingEndpointTest`, `UpdateListingEndpointTest`, `AcceptAssignmentEndpointTest`...), extending one of the
  base classes below, calling the real server through `rest` (`TestRestTemplate`) against MySQL, with
  `@Sql(value = ["/db/test/clean.sql", "/db/test/<domain>/<Name>Endpoint.sql"])` on the class. A test covers the
  happy path, then each error code (status + `ErrorResponse.error.code`), then reloads the entity through the
  repository (`@Autowired dao`) to check what was saved. E-mails are received by the GreenMail `smtp` server the
  base class starts (`smtp.waitForIncomingEmail`). These tests also cover the wiring (events, listeners, access).
- **Unit tests** (service, dispatcher, mailets, listeners, jobs, renderers, `platform`): plain classes built with
  their mocked collaborators (`mockito-kotlin`, `com.nhaarman.mockitokotlin2`: `mock`, `doReturn(...).whenever(...)`,
  `argumentCaptor`, `verify`), no Spring context. Pure algorithms (`MandateDispatcher`, `DispatchHours`,
  `ContractHasher`, the contract templates) are tested here, case by case.

All `@SpringBootTest` tests run with the `qa` profile (`src/test/resources/application-qa.yml`). Mail templates
for tests are under `src/test/resources/mail/`, identity-document samples under `src/test/resources/files/`.

Base classes (`src/test/kotlin/com/wutsi/ndopify/`):

- `BaseEndpointIntegrationTest` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, injects the
  `X-Device-ID` header via a `ClientHttpRequestInterceptor`.
- `TenantAwareEndpointIntegrationTest` — extends `BaseEndpointIntegrationTest`, additionally injects
  `X-Tenant-ID: 1` (override via `ignoreTenantIdHeader = true` in a test to omit it). Without a token, only for the
  public endpoints (`PublicEndpoints`): any other gets a 401.

- `AuthorizationAwareEndpointIntegrationTest` — extends `TenantAwareEndpointIntegrationTest`, additionally sends an
  `Authorization: Bearer` JWT (user `11`, tenant `1`, `admin-console` by default) minted via `AccessTokenService`;
  set `anonymousUser = true` to omit it, `userId` to call as another user, and override `roles` (ex:
  `arrayOf(RoleCode.ADMIN)`) to call as an admin. The token's tenant is `overrideTenantId` when set. The header is
  `set`, not added, so that the current instance's token wins over the interceptors of earlier instances.

Mock Spring beans in `@SpringBootTest` tests with `@MockitoBean`
(`org.springframework.test.context.bean.override.mockito.MockitoBean`), not the removed `@MockBean`.

The mandate dispatch in the `qa` profile has no night (`night.start == night.end`) and no cron (`-`), so its
endpoint tests don't depend on when they run: their fixtures date mandates `NOW()` and offer deadlines in 2099 / 2020.
A fixture whose date must be minutes from now (ex: `GenerateSignedContentJob.sql`) uses `UTC_TIMESTAMP()`: the
datasource reads dates as UTC (`serverTimezone=UTC`), so `NOW()` is off by the MySQL server's offset.
The night is unit tested (`DispatchHoursTest`, `MandateDispatcherTest`), and so are the jobs (`MandateJobsTest`, with
the batch methods in `AssignmentServiceTest`). If a test has to mock the `Clock` bean (only `clock.millis()` is
used), mind that the JWT of `AuthorizationAwareEndpointIntegrationTest` then gets its expiry from the mock while it's
checked against the real time; and that every test instance registers itself as an interceptor on the shared
`TestRestTemplate`, so the interceptors of earlier instances still run.

SQL test fixtures live under `modules/ndopify-server/src/test/resources/db/test/<domain>/`, named after the test
class per convention (`FooEndpointTest.kt` → `FooEndpoint.sql`). `clean.sql` empties the tables first: a new table
goes there too, children before parents. `ResetDBTest`/Flyway config in
`src/test/kotlin/com/wutsi/ndopify/FlywayConfiguration.kt` manages schema reset between runs.

## Database Migrations

Flyway, split across two classpath locations composed in `application.yml`
(`db.migration.common` + `db.migration.local`):

- `modules/ndopify-server/src/main/resources/db/migration/common/` — schema, versioned as
  `V{version}__{description}.sql`
- `db/migration/local/` (default profile) and `db/migration/test/` (`application-test.yml`) — environment-specific
  seed data layered on top of `common`
- The test environment's database is deployed (since 2026-10-31): never modify an existing migration (Flyway's
  checksum would fail), always add a new one after the highest (ex: `V9_1__match-details.sql`).

## Custom JPA Utilities (`util/jpa`)

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
- `OffsetLimitPageRequest` — the `Pageable` of the searches (`offset` / `limit` rather than page numbers);
  `OffsetLimitPageRequest.limit` caps a request's `limit`.
- `EnumListConverter` / `EnumListConverters` — base of the converters storing a `List` of enums as comma-separated
  names (blank items and names the enum no longer has are skipped), set with `@Convert`: one line per enum in
  `EnumListConverters.kt` (ex: `AuthTypeListConverter`).
