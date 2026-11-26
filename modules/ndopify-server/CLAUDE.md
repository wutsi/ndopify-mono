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
    event/      - application events + their listeners (agent, contract, listing, mandate, match, party, security, visit)
    mail/       - the domain's e-mails (*Mailet.kt: recipient, subject, template context), e.g. WelcomeMailet
    bot/        - the domain's Telegram messages (*Botlet.kt) and the callbacks answering them (*CallbackHandler.kt)
```

All of a domain's mail-related classes go in its `mail/` package, never in `service/`: a mailet builds the `Email`
and hands it to `platform/mail`'s `MailService`; its MJML template lives under `src/main/resources/mail/<domain>/`
(e.g. `mail/agent/welcome.mjml`). The service method that triggers it (see *Asynchronous e-mails*) stays in
`service/`. Dates and amounts in e-mails (and generated contracts) go through `TenantFormatter`
(`refdata/server/service/`): the tenant's `dateFormat` / `timeFormat` / `monetaryFormat` / `numberFormat`, first
locale and `timeZone` — never a hard-coded pattern. Mailet unit tests mirror the package (`<domain>/server/mail/*MailetTest.kt`).

A `*Botlet` is the Telegram counterpart of a `*Mailet`: a `@Service` that builds one French message and hands it to
`platform/telegram`'s `TelegramClient`, with no shared interface and no template; its `send` takes the recipient's chat
id and throws when Telegram refuses. A `*CallbackHandler` in the same package answers the taps on a bot message's
buttons. Ex: `MandateOfferBotlet` / `TelegramAssignmentCallbackHandler`.
The agent's Telegram messages share one shape, distinct from the e-mails': an amount is compact and without currency
(`500K/mois`, `25M`, `1,5M`, `TenantFormatter.compactNumber` through `MandateMailLabels.compactPrice`), a property is
one line `{title} - {price}` (`ListingTelegramLabels`; a blank title falls back to
`Appartement à louer, Yaoundé (Mballa II)`), with no neighbourhood nor `Prix :` / `Budget :` label after the title. A
message tied to a customer or a co-agent carries `Contacter sur WhatsApp` (`PhoneNumberUtils.toWhatsappUrl`, never a
number in the text, no button without one), a mandate message `Voir le mandat`
(`{partnerCentralUrl}/mandates/{id}`, none without a `partnerCentralUrl`), a visit message `Voir la visite`
(`{partnerCentralUrl}/visits/{id}`, first button; the co-brokerage one opens the listing agent's home card
`/#co-visit-{id}` instead, on the confirmation and the reminder only). A visit message reads `<object> : <date/time>`, then
`{title} - {price} - Sur place|Vidéo`, then `Agent : <organizer>` on a co-brokerage visit only (`VisitTelegramLabels`):
the visit's agent is the organizer A, the listing's agent the co-agent B; A's messages contact B on WhatsApp and B's
contact A. The e-mails keep their own wording and full amounts (`MandateMailLabels.price`, `MatchAnsweredLabels`,
`VisitFeedbackLabels`, `CoBrokerageVisitLabels`), so a Telegram-only capitalisation or wording happens in the botlet.

Scheduled jobs live in `<domain>/server/job/`: `KycJobs` (party), `MandateJobs` (mandate), `ContractJobs`
(contract), `ListingJobs` (listing, every 15 minutes: `verify`, the UNDER_VERIFICATION listings through
`ListingVerifierService`; `publish`, the VERIFIED ones sent for publication, then the PUBLISHING ones through `ListingPublisherService`;
`close`, daily, the PUBLISHED listings of closed mandates) and
`MatchJobs` (match: `notifyAgents` hourly, `autoPropose` every 15 minutes (hourly in prod), `rematch` and `expire`
daily, `remindAcceptedWithoutVisit` and `nudgeAcceptedWithoutVisitAgent` daily, see *match* below), and
`VisitJobs` (visit: `expire` hourly, `reminder` every 15 minutes, `noShow` hourly, see *visit* below). They loop over the
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
  member of the tenant gets 404 `USER_NOT_FOUND`. **OTP lockout**: `ndopify.security.otp.max-failed-attempts` (5)
  wrong codes lock the account for `lock-duration` (1h), counted on the user's `OTP` row (`failed_attempts`,
  `locked_until`, `V11_0`). While locked, `POST /v1/auth` answers 409 `AUTH_ACCOUNT_LOCKED` with `data.lockedUntil`
  whatever the code (a right one included, and it isn't consumed), and `POST /v1/otp` the same without sending an
  e-mail nor touching the stored code. A wrong code before the lock answers 409 `AUTH_INVALID_CREDENTIALS` with
  `data.remainingAttempts`; the count survives a new code, a successful login clears it, a lock that expired starts
  it over, and password logins count nothing (`AuthenticationService` is `noRollbackFor` `ConflictException`, so the
  409 doesn't undo the count). `OtpAuthenticator` reads the row under a write lock
  (`AuthFactorRepository.findByUserAndAuthTypeForUpdate`), so codes sent in parallel each count instead of
  overwriting each other. `POST /v1/auth` exchanges credentials for a JWT:
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
  The profile photo (`Party.photoUrl`, the storage's public URL of `party/{id}/photo/{uuid}.{ext}`) is changed only
  by the party itself (`PartyAccessService.ensureIsParty`: JWT user → membership → party, else 403
  `HTTP_ACCESS_DENIED`), no admin bypass. It goes straight to the storage (`POST /v1/parties/{id}/photo/presign`, a PUT,
  then `/attach`, through `DirectUploadService`; errors in order: 401 `AUTH_UNAUTHORIZED` (security
  filter), 404 `PARTY_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED`, then the upload's 400s),
  S3 only: presign answers `{url:null}` on local storage, and the multipart `POST /v1/parties/{id}/photo` stays the
  fallback. A replaced photo's object is kept, so e-mails already sent keep showing it.
  Tenant-scoped (see Multi-Tenancy below). The image writes are the case's own party's only (`CallerService`, through
  the token's membership; no admin bypass): 404 `KYC_CASE_NOT_FOUND` (another tenant too) → 403 `HTTP_ACCESS_DENIED` →
  400 `KYC_CASE_NOT_PENDING` → the upload's 400s. `create`, `submit`, `get` and `search` stay tenant-scoped only.
  **Direct upload** (#205, on #127's `DirectUploadService`): `POST /v1/kyc/cases/{id}/images/presign?identificationType=&imageType=`
  hands out a PUT URL for `kyc/{caseId}/images/{uuid}.{ext}` (`{url: null}` with local storage: the caller falls back to
  the multipart upload, which stays, and also stores each photo at a new `{uuid}.{ext}`; older photos at
  `{imageId}.{ext}` stay readable), then `POST .../images/attach` verifies the
  object and **replaces** the case's one image for that (identification type, image type): the row is updated, and the
  previous object deleted with its own `storageType` (the default storage when it has none) after the save (a failed
  deletion is only logged; the multipart upload deletes it too). Idempotent on `path`; a path another image of the case has is a
  400. Both writes save through `KycService.saveImage`: with the case locked (`findStatusForUpdateById`) it re-checks
  `PENDING_VERIFICATION` and re-reads the step's row (`KycImageRepository.findRow`) — both native, since the
  persistence context may hold what was read before the storage I/O — so a case submitted during the I/O keeps its
  rows, concurrent writes on one step update one row, and the shared-path refusal holds under the lock too. A failed
  save deletes the new object unless a row of the case records its path; since every write has its own path, no
  write overwrites another's object. KYC files are private: only read through presigned GETs (`GET /v1/kyc/cases/images/{id}/url`), never `publicUrl`.
- **agent** — mobile-money agents (`AgentEntity`; `/v1/agents`: get, search, me, create, update). `GET /v1/agents/me`
  resolves the caller's agent from its token (`CallerService`): 404 `AGENT_NOT_FOUND` when the caller has no party or
  isn't an agent. `WelcomeMailet` sends
  a welcome e-mail on registration when the tenant has a `partnerCentralUrl`, asynchronously (see *Asynchronous
  e-mails* below). `AgentTelegramService.findByChatId` reads the bot link of a chat (#135's global `T_AGENT_TELEGRAM`):
  its row names the tenant and the agent, which is how a Telegram update — no JWT, no `X-Tenant-ID` — acts in them. `joinedAt` is set on the agent's first VERIFIED case by `AgentKycEventListener`, a
  synchronous `@EventListener` on `KycCaseCompletedEvent` (same transaction as the KYC), so the party domain never
  depends on the agent one. **Telegram**: `AgentTelegramEntity` (`T_AGENT_TELEGRAM`) pairs an agent with its chat; a
  global row like `T_USER` (no `@TenantId`), since the bot's webhook has no tenant. `POST /v1/agents/me/telegram/code`
  stores a 6-digit code (`TelegramCodeGenerator`) valid `ndopify.agent.telegram.code-ttl-minutes` (15), replacing any
  previous one, and `AgentTelegramCodeEventListener` e-mails it to the login address (`AgentTelegramCodeMailet`,
  `mail/agent/telegram_code.mjml`); an agent whose chat is linked gets 409 `AGENT_TELEGRAM_ALREADY_CONNECTED`, and
  `Agent.telegramConnected` reports the link. The bot's `/connect <code>` (`AgentTelegramCommand`) links the chat
  through `AgentTelegramService.connect` (code found without a tenant, single use, one chat per account; a chat linked
  to another account is refused); its `/start` (`AgentTelegramStartCommand`) welcomes the chat with those two steps, or
  tells a linked chat it is already connected (`AgentTelegramService.isLinked`). No reconnect nor disconnect.
  Tenant-scoped but for the link row.
- **customer** — the people behind the mandates: tenants searching for a property and owners offering one
  (`CustomerEntity`, linked to a `Party`; `/v1/customers`). `CustomerService.findOrCreate` reuses the party and
  customer of an e-mail. `POST /v1/mandates/{id}/customer` (`MandateService.updateCustomer`) corrects the mandate's
  customer: its party's names and e-mail, and its WhatsApp number, for the mandate's agent or an admin. A
  `PENDING_APPROVAL` mandate whose e-mail changes cancels its contract and returns to `ASSIGNED` (the customer must
  not sign a contract sent to the old address); a closed or cancelled mandate is a 409 `MANDATE_ALREADY_CLOSED`, an
  e-mail another party owns a 409 `PARTY_EMAIL_ALREADY_EXISTS` with nothing written. Tenant-scoped.
- **mandate** — a customer's SEARCH (tenant) or LISTING (owner) mandate (`MandateEntity`; `/v1/mandates`: get,
  search, create, update).
  `MandateService.create` finds or creates the customer from the request's contact fields and starts the mandate
  as `PENDING_ASSIGNMENT`. It only checks that the contact fields aren't empty (`@NotEmpty`): categories, cities,
  neighbourhoods, prices and enums aren't validated server-side yet. A request with an `originListingId` — the public
  listing page's (`docs/superpowers/plans/2026-11-15-listing-search-mandate.md`) — takes the mandate's criteria **and
  its budget** from that listing (`OriginListingResolver`, implemented by the listing domain), never from the request:
  the visitor posts only its contact, its moving period and the id. The origin (`T_MANDATE.origin_listing_id`,
  `V12_0`) is kept only while the listing is still PUBLISHED (a sold one still seeds the mandate), and an unknown id is
  404 `LISTING_NOT_FOUND`. A customer who already has an open SEARCH mandate of the same city and listing type gets no
  second one: the listing is kept as its origin (when it had none) and `ExistingSearchInterestEvent` published (see
  *match*). Otherwise, at most 3 new SEARCH mandates per customer per 24 h (409 `MANDATE_RATE_LIMITED`); attaching to an
  existing search is never capped. `availableFrom` (`V7_0`) is a LISTING's
  availability date (the date from which the property can be let): stored and returned, not checked by the server.
  A buyer's purchase answers are stored on the mandate (`V16_0`): `financingType` (`FinancingType`, nullable) and
  `titleDocuments` (`TitleDocumentType` list, comma-separated, empty when none). `MandateService.create`/`update`
  normalize them — financing kept only for a `SALE` listing type, both document lists kept for every `SALE` whatever its
  property type, `UNKNOWN` dropped, distinct, and an empty list preserved — the matcher's document veto reads the
  buyer's list against the seller's; the delivered price per m² and per unit are display-only and never stored. A building sale's two counts
  (`T_MANDATE.levels`/`units`, `V18_0`) — its étages and its unités — are kept only for a `SALE` mandate whose
  `propertyType` is `BUILDING`, and cleared for any other type (a rental building keeps today's model: neither count);
  the server validates neither, and the wizards and edit pages bound them. The offer message and the sale contract
  word them (`MandateMailLabels.building`, *3 étages · 12 unités*), not the rooms or amenities a building doesn't have.
  Tenant-scoped.
  **Access** (`MandateAccessService`): every endpoint but create (public) needs a JWT (401
  `AUTH_UNAUTHORIZED`), and the caller must be an admin (`CallerService.isAdmin`: `admin-console` token with the
  `admin` role, `RoleCode`) or the mandate's agent (JWT user → membership → party → agent), else 403
  `MANDATE_ACCESS_DENIED`, after the 404 and before any 409. The agent it's offered to (PENDING assignment) may also
  get it, not change it. An agent's search is restricted to its own mandates (`agent-id` forced; another agent's is a
  403), or with `offered-to` (its own id only, else 403) to the mandates with a PENDING offer to it: those it may get.
  `GET /{id}` is also open to the mandate's customer (`ensureCanReadAsCustomerOrAgent`, any application, any
  status), for its match proposal page.
  **Documents.** A mandate's documents (`MandateDocumentEntity` + `MandateDocumentPageEntity`, `V19_0`) are its agent's
  private papers: identity cards, payslips, land titles. `/v1/mandates/{id}/documents`: `GET` (search, newest first),
  `GET /{documentId}`, `POST` (create from presigned `paths`), `POST /presign`, `POST /{documentId}` (rename), `POST
  /{documentId}/pages` (add), `POST /{documentId}/pages/{pageId}` (replace), `DELETE /{documentId}/pages/{pageId}`,
  `DELETE /{documentId}`, plus the `upload` variants (`POST /upload`, `/{documentId}/pages/upload`,
  `/{documentId}/pages/{pageId}/upload`) that post a `MultipartFile` instead of a presigned path. Only the mandate's
  agent or an admin may use them — never the agent it's offered to, nor its customer — on the reads too: the search and
  get check `MandateAccessService.ensureCanUpdate` (`MandateDocumentEndpoints`), the writes through
  `MandateDocumentService.loadWritable`. The customer reads its mandate via `GET /v1/mandates/{id}` alone, which carries
  no document. A page's file is private: read through a short-lived presigned URL (`pageUrl`, TTL
  `ndopify.mandate.document.url-ttl-seconds`), the storage `path` (under `mandate/{id}/documents/`) never handed out nor
  served from a public URL. Errors, in order: 404 `MANDATE_NOT_FOUND`, 403 `MANDATE_ACCESS_DENIED`, then for the URLs
  naming a document/page 404 `MANDATE_DOCUMENT_NOT_FOUND` / `MANDATE_DOCUMENT_PAGE_NOT_FOUND`, 409
  `MANDATE_ALREADY_CLOSED` (closed or cancelled; create/presign check it right after the access), then 400
  `HTTP_INVALID_PARAMETER` (create: `type` UNKNOWN, `title` > 100 chars, `paths` empty/duplicate, a path outside the
  mandate's folder, missing/oversized/not JPEG-PNG-WEBP, or already a page's; rename: blank or > 100 chars, or 409
  `MANDATE_DOCUMENT_VERSION_CONFLICT`). Any page change (add, replace, delete) resets the document to `UPLOADED` and
  clears its extracted fields (`resetExtraction`); a document's last page can't be deleted (400
  `MANDATE_DOCUMENT_LAST_PAGE`). Writes run under the mandate's lock (`saveLocked`, re-checking CLOSED/CANCELLED), and
  every object deletion — a replaced/removed page's, or each page's of a deleted document — runs after the commit,
  quietly (a failure is logged, never fatal); a failed save deletes the new objects no page references. A legacy page
  with no `storageType` was written to the default storage.
  A search's `MandateSummary` has what a card shows (criteria, amenities, `movingPeriod`, `availableFrom`) and its
  `contractId` only (a client needing the contract's dates searches `/v1/contracts?id=`), not the terms nor the
  customer's comments. `GET /v1/mandates/{id}` carries `visitFee` (`Money`), read through from the mandate's signed
  contract (`MandateService.visitFee`): no fee when the mandate has no contract, or a legacy contract with a blank
  currency. `MandateSummary` doesn't carry it. Reads are checked by `MandateEndpoints`, changes by `MandateService` (under the mandate's lock); the server's own
  reads (dispatch, listeners, jobs) aren't. Partner Central has no admin role: admins only exist in the admin console.
  `POST /v1/mandates/{id}` (update) locks the mandate's contract, then the mandate (see *Lock order*), then 409
  `MANDATE_ALREADY_CLOSED` (CLOSED / CANCELLED), `MANDATE_ALREADY_APPROVED`, then `MANDATE_VERSION_CONFLICT` (stale
  `version`, or a contract replaced since it was read). A PENDING_APPROVAL mandate goes back to ASSIGNED and its
  contract is cancelled, so the customer can't sign the terms of before the change; request-approval then creates a
  new contract (a CANCELLED one is replaced, like an EXPIRED one). A lock is always taken before the entity is loaded
  in the transaction (ids / `contractId` read through projections first): Hibernate doesn't refresh an entity it
  already has when `findByIdForUpdate` locks it, so a check would run on stale values.
  `POST /{id}/request-approval` (ASSIGNED or PENDING_APPROVAL, else 409 `MANDATE_CANNOT_BE_APPROVED`) takes an
  optional `RequestMandateApprovalRequest` (`durationMonths` 1..36 and a sale's `commissionRate` 1..20, else 400
  `HTTP_INVALID_PARAMETER`; `visitFee`), applies those terms to the mandate's contract, and moves it to
  `PENDING_APPROVAL`, its content generated and stored,
  and its signature requested, in 3 transactions so that no lock (the tenant's contract sequence, the mandate) is held
  during the upload: (1) under the contract's lock then the mandate's, reuse its contract or create one (none, EXPIRED
  or CANCELLED, with the terms) and link it, so concurrent requests share one contract; (2) generate (read-only) from an
  in-memory copy of the contract with the terms, then upload, outside any transaction; (3) under the locks again, 409
  `MANDATE_VERSION_CONFLICT` if the mandate changed since (2), else apply the terms to a reused contract, set the
  content, PENDING_APPROVAL, request the signature — one transaction, so a contract the customer can still sign never
  states terms other than its content's (a signature since (1) makes it 409 `CONTRACT_ALREADY_SIGNED`). A failure after
  (1) leaves the mandate as it was, with its DRAFT contract, reused next time: an ASSIGNED mandate can have a
  `contractId`.
  The last signature of its contract approves it (`MandateEventListener.onContractSigned`, synchronous, in the
  signature's transaction: `APPROVED`, `approvedAt`), publishing `MandateApprovedEvent`: `MandateApprovedNotifier`
  messages its agent over Telegram when its chat is linked (`MandateApprovedBotlet`, via
  `AgentTelegramService.chatId`) and e-mails the others (`MandateApprovedMailet`, `mail/mandate/approved.mjml`),
  falling back to the e-mail when Telegram fails (the failure is logged, without the token nor the URL; nothing for
  a mandate without agent, or no longer APPROVED). The message is the `✅ Le client a signé le mandat.` line, then
  `Recherche : …` (a SEARCH) or `Bien : …` (a LISTING) with the mandate's one property line
  (`MandateTelegramLabels`: criteria, neighbourhoods, compact ` - {price}`), and a `Contacter sur WhatsApp` button to the
  customer (none without a number) before a `Voir le mandat` one to `{partnerCentralUrl}/mandates/{id}` (none without a
  `partnerCentralUrl`); the customer's name, the contract number and the validity dates stay on the mandate page
  (summary only).
  `POST /{id}/close` (APPROVED only, else 409 `MANDATE_NOT_APPROVED`) → `CLOSED` with `finalPrice`, `closedAt` and
  `age`: whole days from `approvedAt` to closing, the time a mandate takes to close. `POST /{id}/cancel` (any status but
  CLOSED / CANCELLED: 409 `MANDATE_ALREADY_CLOSED`) → `CANCELLED` with `cancellationCode` (default `OTHER`),
  `cancellationReason` and `closedAt` (no age), expires its PENDING offer, without re-dispatch, and cancels its contract
  (`ContractService.cancel`, in the same transaction: a DRAFT, PENDING_SIGNATURE or SIGNED one becomes `CANCELLED`; an
  EXPIRED one is left as is; the contract is locked before the mandate, see *Lock order*). Both lock the
  mandate and publish `MandateClosedEvent`: `ListingMandateEventListener` (listing domain, synchronous, in the
  closing's transaction) takes the mandate's PUBLISHED listing off the market (see *listing*). Accepting an offer of a mandate that's no longer
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
  declined / ignored it; under `max-load`; fewer than `max-pending` (3) PENDING offers, so an agent who doesn't answer
  isn't flooded), scored on neighbourhood match (incl. nearby ones of the same city, via
  `searchNearest(parentId)`), rotation (`lastAssignedAt`, set on offer), `AgentEntity.rating`, acceptance rate and
  load. A mandate with an origin listing first offers it to that listing's agent, which bypasses the ranking (scored
  alone) but not the hard filters — verified, the mandate's city, not the customer, no DECLINED / EXPIRED offer on it,
  under `max-load` / `max-pending`; a failing filter, or an unknown listing, falls back to the ranking. No offer at night (`DispatchHours`, tenant's `timeZone`), whose hours also pause the TTL; past
  `manual-after-days` it moves the mandate to `MANUAL_ASSIGNMENT` instead (`MandateManualAssignmentEvent`, no listener
  yet). It's called after commit by `MandateEventListener` on `MandateCreatedEvent` (creation) and
  `AssignmentEventListener` on `AssignmentClosedEvent` (decline, expiry), and by `MandateJobs`: `expire` (PENDING
  offers past `expiresAt` → EXPIRED, re-dispatched) and `dispatch` (mandates waiting without a PENDING offer: came at
  night, had no candidate, or their re-dispatch failed). Each offer publishes `AssignmentCreatedEvent`:
  `AssignmentOfferNotifier` messages a linked agent (`AgentTelegramService.chatId(agentId)`) through
  `MandateOfferBotlet`, e-mails the others (`MandateOfferMailet`, `mail/mandate/offer.mjml`), and falls back to the
  e-mail when Telegram fails (the failure is logged, without the token nor the URL). The offer's message stays short:
  the mandate's one property line (plus a purchase's financing and documents), its expiry, then two rows of buttons —
  `Accepter` / `Refuser`, and `Voir détails` to the offer's card on the Partner Central home page
  (`{partnerCentralUrl}/#assignment-{id}`, none without a `partnerCentralUrl`) — so the agent decides from the chat.
  Tapping `Accepter` / `Refuser`
  answers through `AssignmentService.acceptForAgent` / `declineForAgent` (agent-scoped, no JWT): the offer's message is
  edited, with a `Voir le mandat` URL button to the tenant's `{partnerCentralUrl}/mandates/{id}` page on accept (none
  without a `partnerCentralUrl`), and an offer already closed is only reported as `Ce mandat n'est plus disponible.`;
  the bot never acts on another agent's offer nor on an unknown assignment. The mandate e-mails
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
  `T_CONTRACT_SEQUENCE`, incremented last in the create transaction (row locked until commit). `visitFeeAmount` /
  `visitFeeCurrency` (`T_CONTRACT`, `V13_0`) hold the per-visit fee the visitors pay the agent offline (0 = none) in
  the tenant's currency (a blank request currency means it, any other is 400), exposed as `Contract.visitFee`;
  `ContractService.setTerms` applies a mandate's duration (1..36) and fee to a DRAFT / PENDING_SIGNATURE contract
  (409 `CONTRACT_EXPIRED` / `CONTRACT_CANCELLED` / `CONTRACT_ALREADY_SIGNED` otherwise), under the contract's lock.
  `commissionRate` (`T_CONTRACT`, `V17_0`) is a sale contract's success commission, a whole percentage of its sale
  price (1..20): required for `SALE_SEARCH` / `SALE_LISTING` and normalised to null for the other types on create,
  applied with the terms by `setTerms` on those two types only, exposed as `Contract.commissionRate`; a sale mandate
  sent without one gets `ndopify.mandate.contract.sale-commission-rate` (5). `MandateContractGenerator` picks the
  document from the mandate's `listingType`: `SALE` gets `mandate/contract/search_sale.mjml` / `listing_sale.mjml`
  (the buyer's, or the seller's, own party owes the commission; only the seller's is exclusive), a rental and a
  legacy `UNKNOWN` keep `search.mjml` / `listing.mjml`.
  `content` (`SetContractRequest.url`) sets the
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
  `V8_2`). `/v1/listings`: search, get, create, update, `GET /stats`, `POST /{id}/verify`, `POST /{id}/publish`, `POST /{id}/images/upload`, `POST /{id}/images/presign`, `POST /{id}/images/attach`, `GET /{id}/images`,
  `DELETE /images/{id}`. `GET /stats?listing-id=` aggregates each requested listing's interested clients (its ACCEPTED
  matches, any agent's) and its upcoming/completed visits in the database: one row per distinct id, in request order,
  zeros included, no counters on `ListingEntity`; it needs a token and the agent of every listing (404
  `LISTING_NOT_FOUND` before 403 `HTTP_ACCESS_DENIED`; 400 `HTTP_MISSING_PARAMETER` without an id). Search, get and the images list are public (`PublicEndpoints.TENANT`), for PUBLISHED, SOLD
  and RENTED listings (`ListingAccessService`): another status is only for an admin or the listing's agent, a 404
  `LISTING_NOT_FOUND` for anybody else (get, images), with or without a token; a search is forced to PUBLISHED but an
  admin's, or an agent's with its own `agent-id`; the images to VERIFIED but for an admin or the listing's agent. The fields aren't trimmed. The changes (create, update, verify, publish, upload or delete a photo) are only
  for an admin or the listing's agent (the mandate's, for create): 401 without a token, 403 `HTTP_ACCESS_DENIED`
  otherwise, after the 404 and before any 409, checked by the services. `ListingService.create` copies the mandate's
  property (404 `MANDATE_NOT_FOUND`, 409 `MANDATE_NOT_ASSIGNED` without agent, since the listing's agent is the
  mandate's; 409 `LISTING_ALREADY_EXISTS`) and starts it as `DRAFT`, copying its `levels` and `units` too (a sold
  building's counts, `T_LISTING.units`, `V18_1`). A mandate's listing is found with
  `GET /v1/listings?mandate-id=` (repeatable; with the agent's own `agent-id` for a DRAFT): the `mandate` domain doesn't depend on
  `listing`, so `Mandate` has no `listingId`. **Checklist**: a sold `BUILDING` has six sections
  (`ListingSectionService.soldBuilding`): `AMENITIES` leaves its checklist and a save of that section is 400, and its
  `PROPERTY` section drops the bathrooms and parkings an agent posts. An update saves *one* `ListingSection` (`LOCATION`, `GPS`, `PROPERTY`, `AMENITIES`,
  `TERMS`, `REMARKS`), only that section's fields, as sent (a null clears an optional field), and marks it
  `VERIFIED` (`ListingSectionService.verify`): a section counts only once the agent saved it, the landlord's answers
  alone don't. `PHOTOS` is never saved: it's `VERIFIED` once there are `MIN_PHOTOS` (5) images, none `REJECTED` and at
  least one of them a `PHOTO` (a plan or a map doesn't count as the photo; it does count toward the five).
  `REMARKS` is optional. `Listing.sections` carries each section's `TO_VERIFY` / `VERIFIED` status. Update errors:
  409 `LISTING_VERSION_CONFLICT` (`UpdateListingRequest.version`), 409 `LISTING_NOT_DRAFT` once sent, then 400 for no
  section or `PHOTOS`, a missing required field, or a neighbourhood outside the listing's city. The city never
  changes: it's the mandate's, the agent's area. Photos (JPEG / PNG / WEBP) are added `PENDING_VERIFICATION`, and
  can only be deleted from a DRAFT listing; the file stays in the storage (a deleted photo isn't removed from it).
  A photo can also be uploaded straight to the storage, so its bytes never go through the server:
  `POST /{id}/images/presign` (`PresignUploadRequest` → `PresignUploadResponse`, `{url: null}` on local storage)
  then `POST /{id}/images/attach` (`AttachUploadRequest` → `AttachListingImageResponse`), through
  `DirectUploadService`: 404 `LISTING_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED`, then the upload's 400s (path outside
  `listings/{id}/images`, missing object, over 5 MB, bytes not JPEG/PNG/WEBP or not matching the extension, the
  object deleted). Attach is idempotent on `path`: the listing's image recorded for it is returned, never a second
  one. The multipart upload stays the no-JS / local / fallback path.
  **Send**: `POST /{id}/verify` moves a DRAFT to `UNDER_VERIFICATION`, checked asynchronously: 404
  `LISTING_NOT_FOUND`, 409 `LISTING_NOT_DRAFT`, then 409 `LISTING_INCOMPLETE` while a required section of the
  checklist isn't `VERIFIED`. `ListingJobs.verify` runs an UNDER_VERIFICATION listing through
  `ListingVerifierService`: `VERIFIED`, or back to `DRAFT` with the verifiers' `errorCodes` for the agent to fix and
  send again; a failed attempt leaves it UNDER_VERIFICATION for the next run, then after `MAX_RETRIES` back to `DRAFT`
  with `INTERNAL_ERROR`. Each image is classified `PHOTO` / `FLOORPLAN` / `MAP` by the vision model, prompted with the
  listing's property type, listing type, lot area and city: a floor plan is valid on any property, a parcel map only on
  a `LAND` (or when the property type is unset and the parcel is identifiable). The verifier answers
  `LISTING_NOT_ENOUGH_IMAGES` below `ndopify.listing.min-images`, then `LISTING_MISSING_PHOTO` without a `VERIFIED`
  photo, else it passes. `PENDING_VERIFICATION` isn't used for a listing (only for its photos). `VerifyListingsJobTest`
  covers the send and the job together, end to end: keep it so. There is no rejected status:
  a DRAFT with `errorCodes` is one sent back. A send-back publishes `ListingSentBackEvent`, in the transaction that
  saves DRAFT with `errorCodes` (a verifier rejection, or `INTERNAL_ERROR` after `MAX_RETRIES`):
  `ListingSentBackNotifier` messages a linked agent (`ListingSentBackBotlet`, via `AgentTelegramService.chatId`) and
  e-mails the others (`ListingSentBackMailet`, `mail/listing/sent_back_agent.mjml`), falling back to the e-mail on a
  failed send. The message is generic (no per-reason wording, the panel on the page lists what to fix), names the
  property (`n° <id>` plus its neighbourhood and city) and carries a `Corriger l'annonce` button to
  `{partnerCentralUrl}/listings/{id}` (none without a `partnerCentralUrl`); nothing is sent for a successful
  verification, a retry left UNDER_VERIFICATION, nor a listing no longer DRAFT with errors (resubmitted meanwhile).
  **Publish**: `POST /{id}/publish` moves a `VERIFIED` listing
  to `PUBLISHING` (404 `LISTING_NOT_FOUND`, then 409 `LISTING_NOT_VERIFIED`), `retries` reset; `ListingJobs.publish`
  then has `ListingPublisherService` generate its title, summary and description (`ListingContentGenerator`, text
  LLM, `ChatClients.TEXT`, from the listing's details, verified images and agent remarks; a sold building is described
  by its units and étages instead of its bedrooms), and its hero image (the model's when that image is a verified
  `PHOTO`, else the verified `PHOTO` of highest quality, then largest: a plan or a map is never the hero, and only a
  listing with no verified photo at all falls back to all its verified images), and its public `url`, the path `/<city>/<neighborhood>/listing/<id>/<slug of the title>` (no host: the
  tenant's `publicPortalUrl`; no neighbourhood nor title segment without them): `PUBLISHED`, or still `PUBLISHING` to be retried after a failure; after `MAX_RETRIES` failed attempts
  it's `PUBLISHED` without title, summary nor description (the UIs handle it), its last error kept. Either way it
  publishes `ListingPublishedEvent`: `ListingPublishedNotifier` messages a linked agent (its link's chat) through
  `ListingPublishedBotlet` — the cover photo (`ListingImageService.cover`, none without one) is resized to 1280×1280 at
  quality 80 through the `ImageService` and handed to `sendPhoto` as a URL, or read from the storage and uploaded when
  the URL can't be resized (local), with the text as caption — `🏠 Votre annonce est publiée :` and the listing's one
  property line (`{title} - {price}`) — and a `Voir l'annonce` keyboard link to
  `{publicPortalUrl}{url}`; a failed resize, download or upload only drops the photo, never sending the original —
  and e-mails the others
  (`ListingPublishedMailet`, `mail/listing/published_agent.mjml`; no link without either), falling back to the e-mail
  when the text send fails. The customer (the property's owner) is told nothing for now
  (`published_customer.mjml` is kept for when it is). **Close**: a mandate's
  closing (`MandateClosedEvent`, `ListingMandateEventListener`, synchronous, so it commits or rolls back with the
  mandate; a listing `@Version` conflict fails the closing) moves its listing, if
  PUBLISHED, to `SOLD` / `RENTED` (its `listingType`; an UNKNOWN one is left as is) once CLOSED, or `CANCELLED` once
  CANCELLED, with `closedAt` (`ListingService.onMandateClosed`); a listing in any other status is left as is.
  `ListingJobs.close` (daily, `ndopify.listing.close.cron`) does the same for the PUBLISHED listings of closed mandates
  the listener missed (closed before it existed). `EXPIRED` isn't set by any code yet.
  `ListingOriginListingResolver` implements the mandate domain's `OriginListingResolver`: it turns a listing into the
  SEARCH mandate's criteria and budget that a visitor of its public page gets (the origin of the mandate). The listing
  domain implements an interface the mandate declares, never the reverse.
  Tenant-scoped.
- **match** — the listings of an agent's inventory matching its SEARCH mandates (`MatchEntity`, `T_MATCH`, `V9_0`,
  one row per (mandate, listing), `@Version`-ed), see `docs/superpowers/plans/2026-10-31-search-listing-matching.md`
  for the algorithm. Depends on `mandate` and `listing`, never the reverse: `MatchEventListener` (async, after commit)
  runs `MatchService.matchMandate` on `MandateApprovedEvent` and `matchListing` on `ListingPublishedEvent`. Only an
  APPROVED SEARCH mandate with an agent is matched: against that agent's PUBLISHED listings first, then, only when it
  has no viable own match (`existsViableOwnMatch`: PUBLISHED, not `DISMISSED` nor `REJECTED`), against the other
  agents' PUBLISHED listings of the same city and listing type (co-brokerage, `coBrokerage`, `V10_5`). Co-brokerage
  notifications (`V10_6`): the search agent is e-mailed the other agent's listing and contact when the match is
  created (`CoBrokerageNotificationService.notifySearchAgents`, through `CoBrokerageMatchMailet`). The listing agent
  is told nothing there: it learns a co-agent's interest from the visits and the proposals themselves, never as a
  digest of other agents' searches (the digest, its botlet, mailet and notifier are gone; the
  `T_MATCH.listing_agent_notified_at` column stays unused). Both e-mails ride
  `MatchJobs.notifyAgents` (`MatchNotificationService.notifyAgents` + `notifySearchAgents`), `notifiedAt` being the
  one-shot mark. **A customer's interest** (a
  listing's public page): `ExistingSearchInterestEvent` → `MatchInterestService.registerInterest` records the pair
  `ACCEPTED` (`MatchService.acceptInterest`; `answeredAt` = now; any prior status reopened, the rejection reason cleared;
  an ACCEPTED row untouched; every Matcher rule but the customer's own property bypassed, `min-score` too), tells the
  search's agent once when the row moved (`MatchInterestNotifier`: `MatchInterestBotlet` over Telegram when its chat is
  linked, `MatchInterestMailet` otherwise or as the fallback: the customer's name, the listing, `Contacter sur
  WhatsApp` and `Voir le mandat`), and e-mails the customer the accepted proposal link (`MatchInterestAcceptedEvent` →
  `MatchProposalService.sendAcceptedEmail`). Nothing is proposed for an existing search any more. A search without an
  agent keeps the listing as its `originListingId`, and `proposeOrigin` (called by
  `MatchEventListener.onMandateApproved` after `matchMandate`) accepts that match at approval, with the customer's
  e-mail and no agent notification. Anti-sautage: a customer never
  learns the counterpart agent's identity/contact, and a counterpart agent never learns the customer's
  identity/contact/mandate; the customer-facing proposal still names only the search agent. The same `Matcher` and
  distances apply to the matcher path, and a co-brokerage row is never deleted (sticky when own inventory appears
  later); an own-inventory row keeps the re-score/delete behaviour. `GET /v1/matches` lists the agent's own inventory
  first, then co-brokerage, best score first. `matchListing` never creates co-brokerage rows: the daily `rematch`
  picks a newly published listing up. `Matcher` (pure, unit
  tested case by case in `MatcherTest`) rules a pair out (the customer's own property, another listing type / city /
  category — skipped only for a specified `BUILDING` or `LAND` pair — currency or periodicity, over budget +
  `price-tolerance`, an incompatible property type) or scores it (neighbourhood, price, rooms, amenities, availability,
  furniture) multiplied by a property-type factor: 1.0 exact or no preference, 0.9 `OFFICE`/`RETAIL`, 0.85
  `BUILDING`/`OFFICE`/`RETAIL`, 0.8 `INDUSTRIAL` siblings, 0.0 veto (`LAND` against non-land, residential
  cross-types, unknown listed type). A `SALE` `BUILDING`↔`BUILDING` pair uses the building weight set instead:
  neighbourhood 0.30, price 0.25, units 0.20, levels 0.15, availability 0.10, with rooms, amenities and furniture
  neither weighed nor listed as gaps; its `units`/`levels` partials are the closeness `1 - |listing - search| / search`
  clamped 0..1 and `1.0` when either side states none (never a rule-out), stored as `unitsScore`/`levelsScore`
  (`T_MATCH`, `V18_2`). Every other pair keeps today's six weights. Under `min-score` it isn't stored. Each score also keeps what explains it to
  the agent (`V9_1`, `V10_3`): `distanceKm` (to the nearest searched neighbourhood: 0 inside one, null when not nearby
  or none searched), `missingAmenities` (the amenities searched that the listing lacks, by DTO field name, ex:
  `waterTank`) and `typeScore` (the property-type factor). `ACTIVE` rows are created, re-scored or deleted; any other status (`SHORTLISTED`, `DISMISSED`,
  `PROPOSED`, `ACCEPTED`, `REJECTED`, `EXPIRED`) is never touched. Each row keeps
  `listingPrice` (when last scored, or dismissed, proposed or rejected): the
  price a match was turned down at, to tell a price drop later (re-proposing after one isn't built yet). The run isn't one transaction (each save is its own, a conflict skips
  the pair), so it reads the mandates with their neighbourhoods (`@EntityGraph`). `Match.agentId` is the search mandate's agent (`T_MATCH.agent_id`, never
  the listing's), so a listing agent can name whose client a co-brokerage match was sent to. `/v1/matches`: search
  (`mandate-id` and / or `listing-id`, repeatable, at least one of them, else 400 `HTTP_MISSING_PARAMETER`, each one
  checked: a mandate as below, a listing by its agent or an admin, `ListingAccessService`: 404 `LISTING_NOT_FOUND`
  then 403 `HTTP_ACCESS_DENIED`, ex: who a published listing interests with `status=ACCEPTED`; `status` default
  ACTIVE; best score first across them, only while the mandate is APPROVED and the listing
  PUBLISHED), get, `POST /{id}/shortlist` (the agent keeps it to propose later: ACTIVE → SHORTLISTED, else 409
  `MATCH_NOT_ACTIVE`), `POST /{id}/dismiss` (ACTIVE, SHORTLISTED or EXPIRED, `MatchService.OPEN_STATUSES`, else 409 `MATCH_NOT_ACTIVE`), access as the mandate's (`MandateAccessService`:
  404, then 403 `MANDATE_ACCESS_DENIED`); `POST /run?mandate-id=` (`@OpsEndpoint`) re-runs a mandate. `MatchJobs.rematch` (daily, `ndopify.match.rematch.cron`, `MatchService.matchAll`) re-runs every APPROVED SEARCH mandate with an agent: it catches up on a run lost after an approval or a publication (the listener only logs a failure) and re-scores against today (availability); a mandate failing is logged and skipped.
  **Agent alert** (`docs/superpowers/plans/2026-10-31-match-agent-alert.md`): `notifiedAt` (`V9_2`) is null until the
  agent is told, and never touched by the matcher (a re-score or re-run doesn't alert again). `MatchJobs.notifyAgents`
  (hourly, `ndopify.match.notify.cron`) has `MatchNotificationService` e-mail each agent, once for all its ACTIVE
  matches not notified yet (mandate APPROVED, listing PUBLISHED), grouped by mandate (`MatchNotificationMailet`,
  `mail/match/new_matches.mjml`, at most 5 listings per mandate), then mark them (`MatchRepository.markNotified`, a bulk
  update that skips `@Version`, so a dismiss meanwhile stays). A failed e-mail leaves the agent's matches to the next
  run. A match re-proposed after a price drop must get `notifiedAt = null` again.
  **Proposal** (`docs/superpowers/plans/2026-10-31-match-proposal.md`, `MatchProposalService`): `POST /{id}/propose`,
  or several matches of one mandate with `POST /propose` (`ProposeMatchRequest.matchIds`, 1 to 20; 400
  `HTTP_INVALID_PARAMETER` when of several mandates), by the mandate's agent or an admin, as dismiss: ACTIVE,
  SHORTLISTED or EXPIRED → PROPOSED, `proposedAt`, all or none; 409 `MATCH_NOT_ACTIVE`, `MANDATE_NOT_APPROVED`,
  `LISTING_NOT_PUBLISHED`. It publishes one `MatchProposedEvent` (`matchIds`): `MatchProposedMailet` sends the
  customer one e-mail with the listings, best score first, each with a link to its proposal page,
  `{publicPortalUrl}/match/{matchId}?token={jwt}` (`mail/match/proposed.mjml`), where the customer answers it.
  `POST /{id}/resend` e-mails that link again, for a PROPOSED or ACCEPTED match only and without changing its status
  (`MatchProposalService.resend`, after commit `MatchResentEvent` → `sendResentEmail`): 409 `MATCH_NOT_PROPOSED`,
  `MANDATE_NOT_APPROVED`, `LISTING_NOT_PUBLISHED`, then `MATCH_RATE_LIMITED` when the last resend is younger than
  `ndopify.match.resend.min-interval` (15m, `resent_at` `V12_2`). A PROPOSED match gets a fresh `proposedAt`; an
  ACCEPTED one keeps its `answeredAt` and `proposedAt`, and its e-mail carries the visit invitation line (the
  template's `accepted` flag). The
  customer reads it with `GET /v1/matches/{id}` (and its mandate), only once PROPOSED, ACCEPTED, REJECTED or EXPIRED (else 403
  `MANDATE_ACCESS_DENIED`; as `ensureCanRead` for an admin or agent: an agent only its own / offered mandates). The
  token (`MatchLinkTokenService`, one per e-mail) is a `public-portal` JWT of the customer's own user and party, so
  that the link signs it in; it lives `ndopify.security.access-token.ttl.match-link` (365d) and isn't scoped to the
  match: whoever has the e-mail is signed in as the customer until then. The visit e-mails' customer links go through
  the same service and key, so they live 365d too. The customer gets a membership of the tenant
  with `public-portal` access first (its own, else the user of its e-mail); no `token` without an e-mail, or when
  that address is already a member's as another party. `POST /{id}/accept` and
  `POST /{id}/reject` (`RejectMatchRequest.reasonCode`, a `MatchRejectionReason`, `OTHER` when missing or `UNKNOWN`,
  and `reason`, a comment, optional, at most 1000) are for the mandate's customer only
  (`MandateAccessService.ensureIsCustomer`: the JWT's party is the customer's, any application; **no admin bypass,
  the agent can't answer for its customer**): PROPOSED → ACCEPTED / REJECTED, final, `answeredAt`, else 409
  `MATCH_NOT_PROPOSED`. The answer publishes `MatchAnsweredEvent` inside the accept / reject transaction, and
  `MatchEventListener.onMatchAnswered` (async, after commit) calls `MatchProposalService.sendAnsweredNotification`:
  `MatchAnsweredNotifier` messages a linked agent (`AgentTelegramService.chatId(match.agentId)`) through
  `MatchAnsweredBotlet`, e-mails the others (`MatchAnsweredMailet`, `mail/match/answered.mjml`), and falls back to the
  e-mail when Telegram fails (logged, without the token nor the URL). The message names the customer and the
  listing's one property line (`{title} - {price}`, no `Achat —` / `Vente —` nor `Prix :`), then a rejection's reason
  — capitalised for Telegram, the shared `MatchAnsweredLabels` keeping their lowercase wording — and comment. The
  buttons contact the customer on WhatsApp (none without a number), then open
  `{partnerCentralUrl}/mandates/{mandateId}` (none without a `partnerCentralUrl`); a blank title falls back to
  `Bien à louer (…)`. It's final, and the listing's agent is never told. An `EXPIRED` proposal notifies
  nobody.
  **Expiry**: `MatchJobs.expire` (daily, `ndopify.match.expire.cron`) has `MatchProposalService.expireDue` move to
  `EXPIRED` the PROPOSED matches whose `proposedAt` is older than `ndopify.match.expire.days` (7), oldest first, 100
  per run; each save is its own transaction, so a match answered meanwhile fails on its `@Version` and is skipped. No
  e-mail, no event. The customer can no longer answer it (409 `MATCH_NOT_PROPOSED`); the agent may propose it again
  (new `proposedAt` and e-mail) or dismiss it. A resend of a PROPOSED match also restarts `proposedAt`, so it gets 7
  more days; an ACCEPTED one keeps its clock (see *Proposal*). Config: `ndopify.match.*`. Tenant-scoped.
  **Auto-proposal**: `MatchJobs.autoPropose` (every 15 minutes, hourly in prod, `ndopify.match.auto-propose.cron`)
  has `MatchAutoProposalService.autoProposeDue` propose the `ACTIVE` matches of an `APPROVED` search whose listing is
  `PUBLISHED` and that the agent hasn't touched for `after-hours` (24): `batch-size` (100) matches per run, grouped by
  mandate, at most 20 per proposal, through `MatchProposalService.propose` — so the customer gets the normal proposal
  e-mail and can accept/reject it. `SHORTLISTED`, `DISMISSED`, `PROPOSED`, `ACCEPTED`, `REJECTED` and `EXPIRED` are
  never touched; an agent answering meanwhile fails on `@Version` and skips that mandate. No new status nor flag:
  auto-proposals look like manual ones (logged per mandate).
  **Visit reminder** (`docs/superpowers/plans/2026-11-16-match-visit-reminder.md`): `visitRemindedAt` (`V12_1`) is set
  once the customer of an `ACCEPTED` match was reminded that it can book a visit. `MatchJobs.remindAcceptedWithoutVisit`
  (daily, `ndopify.match.visit-reminder.cron`) has `MatchVisitReminderService.remindDue` select the `ACCEPTED` matches
  with no open visit (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`), answered at least `delay-days` (3) ago, not reminded
  yet, whose mandate is `APPROVED` and listing `PUBLISHED`; oldest first, `batch-size` (100) per run. One French e-mail
  per match (`MatchVisitReminderMailet`, `mail/match/visit_reminder.mjml`): the listing's photo, title and price, the
  agent's signature, and a fresh `MatchLinkTokenService` link to `{publicPortalUrl}/match/{id}` (none without a
  `publicPortalUrl`). The row is marked only after a successful send, so a failure retries on the next run; a customer
  without e-mail is marked without an e-mail; a match answered, closed or visited meanwhile is skipped (`@Version` /
  re-check). One reminder per match in v1, and the agent isn't told.
  **Agent nudge** (`docs/superpowers/plans/2026-11-18-agent-scheduled-visits.md`): `agentVisitNudgedAt` (`V14_1`) is
  set once the agent was told its ACCEPTED match still has no open visit. `MatchJobs.nudgeAcceptedWithoutVisitAgent`
  (daily, `ndopify.match.agent-visit-nudge.cron`) has `MatchVisitAgentNudgeService.nudgeDue` select the same matches as
  the customer reminder, answered at least `delay-hours` (48) ago, whose agent wasn't nudged yet, oldest first,
  `batch-size` (100) per run. `MatchVisitAgentNudgeNotifier` messages a linked agent over Telegram
  (`MatchVisitAgentNudgeBotlet`) and e-mails the others (`MatchVisitAgentNudgeMailet`,
  `mail/match/agent_visit_nudge.mjml`), falling back to the e-mail on a failed send; both carry the customer, the
  listing and its price, and two buttons — `Contacter sur WhatsApp` (`wa.me`) and `Voir le mandat`
  (`{partnerCentralUrl}/mandates/{mandateId}#match-{matchId}`). The Telegram message is three lines —
  `⏰ Appelez <customer> pour planifier la visite`, the listing's one property line, `Proposition acceptée il y a plus
  de 48 h.` — with the WhatsApp button first, then `Voir le mandat`. The row is marked after the notifier returns (an agent
  with no channel at all is marked too: nothing to retry), and a failed send leaves it to the next run; a match
  answered, closed or visited meanwhile is skipped (`@Version` / re-check). The nudge goes to `match.agentId` only,
  never the listing agent, and never carries its customer.
- **visit** — a customer's visit of a listing an agent matched to its search (`VisitEntity`, `T_VISIT`, `V10_0`,
  `@Version`-ed), see `docs/superpowers/plans/2026-11-02-visit-management.md`. The customer requests an `ON_SITE` or
  `VIDEO` visit (the agent, at the property, calls it on WhatsApp video) of one of its ACCEPTED matches, on a slot in the
  7 days starting tomorrow, 08:00-20:00 in the tenant's `timeZone`, on a 30-minute boundary (`VisitSlots`); a `VIDEO`
  request needs its `whatsappNumber`. The agent, or an admin, schedules it (the requested date and type may change) with a
  4-digit code (`VisitCodeGenerator`, `SecureRandom`); a `CONFIRMED` visit less than 24 h ahead is its own reminder
  (`reminderSentAt`). The agent (or an admin) also **books** a visit for an ACCEPTED match that has no open visit, in
  one step: `POST /v1/matches/{id}/visits` (`CreateMatchVisitRequest`: the type and the slot) locks the match, then
  runs the customer's request checks plus `schedule`'s slot and code, and writes it straight to `CONFIRMED` with
  `createdBy = AGENT` (`V14_0`); the customer's e-mail says the agent planned it and can be cancelled, the
  co-brokerage notice is unchanged, and no *visite demandée* message is sent. The agent checks the customer in with that code from 1 h before to 4 h after the time (a wrong code
  is counted, the 5th locks the check-in for good: `VISIT_CODE_INVALID` / `VISIT_CODE_LOCKED`), marks the customer's
  no-show from 15 min to 4 h after (`CUSTOMER_ABSENT`, `CODE_REFUSED`), checks out at least 10 min after the check-in with
  a private comment, and cancels before the check-in. The customer gives its feedback once `COMPLETED`. A location, when
  given, is kept as its distance to the listing (`checkInDistanceMeters`, flagged over 300 m) and never refused. The exact
  address (the listing's `landmark`, `accessDirections`, position) is only in the confirmed e-mail and in the customer's
  `GET` of a confirmed `ON_SITE` visit; the code is only ever returned to the customer. Access (`VisitAccessService`): the
  customer alone requests / answers / cancels as itself, the visit's agent alone checks in, marks a no-show and checks out
  (no admin bypass); schedule and cancel-as-agent follow `MandateAccessService`. A search filters by `mandate-id`,
  `match-id`, `listing-id`, `agent-id` and `customer-id` (repeatable), and is restricted to the caller's own visits
  (`agent-id` or `customer-id` forced). `GET /v1/visits/{id}` carries `visitFee` (`Money`), read through from the
  mandate's contract (`MandateService.visitFee`); `VisitSummary` and `CoBrokerageVisitSummary` don't, so the listing
  agent (B) never sees it, and neither does a search. Co-brokerage visits: the listing's agent (B) is a read-only backstage
  participant. `GET /v1/visits/co-brokerage?listing-id=` returns only `CONFIRMED`/`CHECKED_IN` visits of another
  agent (`agentId != listing.agent.id`), as `CoBrokerageVisitSummary` (no customer, note, code, address, report or
  feedback), to the listing's agent only. Without `listing-id`, the same endpoint returns the caller agent's
  `CONFIRMED`/`CHECKED_IN` co-brokerage visits across all its `PUBLISHED`/`RENTED`/`SOLD` listings, soonest first (a
  caller without an agent is 403 `VISIT_ACCESS_DENIED`); a present but blank or non-positive `listing-id` is 400
  `HTTP_INVALID_PARAMETER`, never the agent-wide list; the payload is the same and never A's customer.
  `CoBrokerageVisitNotifier` Telegram-messages a linked B (`CoBrokerageVisitBotlet`, via
  `AgentTelegramService.chatId(listing.agent.id)`) and e-mails the others (`CoBrokerageVisitMailet`), falling back to
  the e-mail on a failed send. It keeps the mailet's four triggers and exclusions exactly: confirmation, the 24 h
  reminder, a cancellation after confirmation while the listing's mandate is still live, and a customer-caused
  no-show; never `REQUESTED`/`EXPIRED`/`COMPLETED`/agent no-show or an own-inventory visit. The message uses the same
  visit format as the search agent's (`VisitTelegramLabels`): `<object> : <date/time>`, the listing's property line and
  its type, then `Agent : <A>` — the organizer is A, the recipient B — and only the event's own line (the
  cancellation's actor, or `Le client ne s'est pas présenté`). It drops the e-mail's *Visite organisée par …* signature
  and the *je vous y retrouve avec mon client* sentence, and carries `Voir la visite` to
  `{partnerCentralUrl}/#co-visit-{id}` — the card on B's Partner Central home page, since B can't read
  `/visits/{id}` — then `Contacter sur WhatsApp` to A, on the confirmation and the reminder only; a cancellation or a
  no-show carries only the WhatsApp button. Each button is none without the URL / A's number. It
  never A's customer nor its data. The e-mail keeps its own wording through `CoBrokerageVisitLabels`; its
  confirmation and reminder link the button and the photo to the same `/#co-visit-{id}` anchor, its cancellation and
  no-show carry neither. **Cancellation notification**:
  a cancellation is notified only when a party cancelled a **scheduled** visit — the agent over Telegram
  (`VisitCancelledBotlet` through `VisitCancelledNotifier`, via `AgentTelegramService.chatId`, with a `Voir la visite`
  button to `{partnerCentralUrl}/visits/{id}`) or e-mail (`VisitCancelledMailet.sendToAgent`), the customer by e-mail
  (`sendToCustomer`); a `SYSTEM` cancellation (mandate ended, sweep) and a cancelled `REQUESTED` visit notify nobody.
  The message reads `❌ Visite annulée : <slot>`, the listing's property line and type, `Agent : <B>` on a
  co-brokerage visit, then `Annulée par le client · Motif : …` — the slot is the scheduled one, or the requested one
  for a visit cancelled before confirmation — and never the check-in code, the exact address nor the customer's token.
  A co-brokerage visit's cancellation goes to B through `CoBrokerageVisitBotlet` instead (see above). **Request
  notification**:
  `VisitRequestedNotifier` messages the agent over Telegram when its chat is linked (`VisitRequestedBotlet`, via
  `AgentTelegramService.chatId`), e-mails it otherwise (`VisitRequestedMailet`, `mail/visit/requested.mjml`), and
  falls back to the e-mail on a failed send (the failure is logged, without the token nor the URL). The message reads
  `📅 Demande de visite : <requested slot>`, the listing's property line and type, `Agent : <B>` on a co-brokerage visit,
  then the note in guillemets (`Note : « … »`), with a `Voir la visite` button to `{partnerCentralUrl}/visits/{id}`
  (none without a `partnerCentralUrl`) then a `Contacter sur WhatsApp` one to the customer's — or B's, on a co-brokerage
  visit — `wa.me` link (never the number in the text). **Feedback notification**:
  `VisitFeedbackNotifier` messages the visit's agent when the customer answers the feedback form — over Telegram when
  its chat is linked (`VisitFeedbackBotlet`), e-mailed otherwise (`VisitFeedbackMailet`, `mail/visit/feedback.mjml`), with
  the e-mail as the fallback of a failed send (the failure is logged, without the token nor the URL). The message
  reads `⭐ Avis de visite : <slot>`, the listing's property line and type, `Agent : <B>` on a co-brokerage visit, then
  `Pas intéressé · Raison : …` (the reason capitalised for Telegram only) and the comment in guillemets, with the same
  two buttons; never the visit's code, exact address or the customer's `public-portal` token. Only a new answer
  notifies: a 409 (`VISIT_FEEDBACK_ALREADY_SENT` / `VISIT_NOT_COMPLETED`) does not. `MandateClosedEvent` now also reaches
  `VisitMandateEventListener`, an async
  after-commit listener that cancels the mandate's `REQUESTED`/`CONFIRMED` visits (search side and listing side) as
  `SYSTEM` with no mandate reason; `CHECKED_IN` stays. A failed async cancellation never fails the closing:
  `VisitJobs.cancelEndedMandates` sweeps the open visits of ended mandates hourly (100/tenant/run, first run back-fills
  and e-mails). B never learns A's customer. `VisitJobs`: `expire` hourly moves a `REQUESTED` visit at least
  48 h old and past its requested slot to `EXPIRED`, `reminder` every 15 min notifies both sides 24 h ahead (once): the
  customer by e-mail (`VisitReminderMailet.sendCustomer`), the agent over Telegram when its chat is linked
  (`VisitReminderNotifier` / `VisitReminderBotlet`, the shared visit format with `aujourd'hui` / `demain`, and no
  video-call note: agents know how to hold a visit by video) or by e-mail (`VisitReminderMailet.sendAgent`) otherwise
  and as the
  fallback of a failed send — the co-brokerage reminder keeps its own channel (`CoBrokerageVisitNotifier`) —, `noShow`
  hourly marks the `CONFIRMED` visits
  whose agent didn't check in 4 h after the time as `NO_SHOW` (both sides e-mailed). The delays and tolerances above are
  `ndopify.visit.policy.*` (overridable); the job crons are `ndopify.visit.*`. Tenant-scoped.
- **error** — shared exception types (`WutsiException` subclasses: `BadRequestException`, `NotFoundException`,
  `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `InternalErrorException`) and a global
  `ControllerErrorHandler`.

**Asynchronous e-mails** (welcome, OTP, KYC status, mandate offered, mandate assigned, mandate approved, contract to sign, listing published, listing sent back, new matches, match proposed, match answered, visit requested / confirmed / completed / cancelled / no-show / expired / reminded / feedback): the service publishes an event
from its transaction (`AgentCreatedEvent`, `OtpCreatedEvent`, `KycCaseCompletedEvent`, `AssignmentCreatedEvent`,
`AssignmentAcceptedEvent`, `MandateApprovedEvent`, `ContractSignatureRequestedEvent`, `ListingPublishedEvent`,
`ListingSentBackEvent`, `MatchProposedEvent`,
`MatchAnsweredEvent`, `VisitRequestedEvent`, `VisitConfirmedEvent`, `VisitCompletedEvent`, `VisitCancelledEvent`, `VisitNoShowEvent`, `VisitExpiredEvent`,
`VisitReminderEvent`, `VisitFeedbackSentEvent` in `<domain>/server/event/`), carrying ids plus the `tenantId` — never entities. Its
listener is `@Async @TransactionalEventListener(AFTER_COMMIT)`, so nothing is sent for a rolled-back transaction. The
executor thread has no request, so the listener sets `TenantContext` and a `DefaultKVLogger` in `KVLoggerThreadLocal`
(which the injected `KVLogger` falls back to), calls `logger.log()` in `finally` to flush it, and removes both
(threads are reused). The work runs in a `@Transactional` service method (`AgentService.sendWelcomeEmail`,
`OtpService.sendEmail`, `KycService.sendStatusEmail`, `AssignmentService.sendOfferNotification` / `sendAssignedEmail`, `MandateService.sendApprovedNotification`,
`ContractService.sendSignatureRequestEmails`, `ListingService.sendPublishedNotification` / `sendSentBackNotification`,
`MatchProposalService.sendProposedEmail` /
`sendAnsweredNotification`,
`VisitService.sendRequestedNotification` / `sendConfirmedEmails` / `sendCompletedEmail` / `sendCancelledNotification` / `sendNoShowEmails` /
`sendExpiredEmails` / `sendReminderNotifications` / `sendFeedbackNotification`); the signed PDF (`ContractSignedEvent` → `SignedContractService.generate`,
not an e-mail, not transactional) follows the same listener pattern. The requested-visit, cancellation, reminder,
feedback, mandate-approval, listing sent-back and match-answered entries are the ones that don't always go by e-mail:
`VisitRequestedNotifier` / `VisitCancelledNotifier` / `VisitReminderNotifier` / `VisitFeedbackNotifier` (see *visit*),
`MandateApprovedNotifier` (see *mandate*), `ListingSentBackNotifier` (see *listing*) and `MatchAnsweredNotifier` (see *match*)
message a linked agent over Telegram, and e-mail the others or as the fallback. The service methods are
entered *after* `TenantContext` is set since Hibernate resolves the tenant when the session opens. Failures are
logged, never rethrown. Tests must wait for the mail (`smtp.waitForIncomingEmail`);
`BaseEndpointIntegrationTest.tearDown` waits for the running async tasks, so a test's mail can't reach the next one.

**Lock order**: a write that locks both a mandate and its contract locks the **contract first**, the order of a
signature (`ContractService.sign`, then `MandateService.onContractSigned`): `MandateService.cancel`, `update` and
request-approval's step (3) (`ContractService.findByIdForUpdate`). The reverse order deadlocks with a last signature.
`ControllerErrorHandler` answers a lock not acquired (`PessimisticLockingFailureException`: deadlock, lock wait timeout)
with 409 `HTTP_VERSION_CONFLICT` rather than 500.

**Optimistic locking**: `MandateEntity`, `MandateDocumentEntity`, `AgentEntity`, `ContractEntity`, `KycCaseEntity`, `AssignmentEntity`,
`ListingEntity`, `MatchEntity` and `VisitEntity` are `@Version`-ed (`version` column, `V7_0` / `V7_1` / `V8_0` / `V10_0`; a listing's conflict is
`LISTING_VERSION_CONFLICT`, and `UpdateListingRequest.version` is checked like the mandate's). Hibernate checks it on flush (`UPDATE ... WHERE version = ?`), so it only
catches concurrent transactions; `ControllerErrorHandler` turns the `ObjectOptimisticLockingFailureException` into a
409 named after the entity (`MANDATE_VERSION_CONFLICT`, `MANDATE_DOCUMENT_VERSION_CONFLICT`, `AGENT_VERSION_CONFLICT`,
`CONTRACT_VERSION_CONFLICT`, `KYC_CASE_VERSION_CONFLICT`; `ASSIGNMENT_NOT_PENDING` for an offer, `MATCH_NOT_ACTIVE` for a match). Across requests, `Mandate` / `Agent` expose
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
  implementations, selected via `ndopify.storage.default-type`. `upload/DirectUploadService` presigns a browser upload
  under a prefix (`presignedUploadUrl`, TTL `ndopify.storage.upload-url-ttl-seconds`, 600) and verifies the uploaded
  file (path under the prefix, presence, size, sniffed type; deletes a rejected one): image-only (JPEG / PNG / WEBP,
  5 MB). Domains call it from their own *presign* / *attach* endpoints, after their access checks, and keep their
  multipart endpoint as the fallback. S3 only: on `local` the presign answers `{url: null}`.
- `platform/image` — resizing through ImageKit, wired by `config/platform/ImageConfiguration` from `ndopify.image.*`:
  `ImageKit` rewrites a storage URL into an `ik.imagekit.io` transformation URL
  (`tr:w-…,h-…,f-…,ar-…,c-…,cm-…,q-…`), `ImageServiceLocal` returns it unchanged. `ListingTelegramSender` uses it for
  Telegram (see *listing*); the e-mail mailets through `MailImages` (see *platform/mail*).
- `platform/mail` — e-mail sending (`MailService`, `MailBodyResolver`, `MailFilter`); MJML templates under
  `src/main/resources/mail/`. E-mail listing images always go through `MailImages` (`COVER` 1200×800, `THUMBNAIL`
  160×120, JPEG quality 75), never the cover's original URL; a missing cover still sends no image.
- `platform/logger` — key-value request logging (`KVLogger`, `KVLoggerFilter`).
- `platform/tenant` — tenant resolution (see Multi-Tenancy below).
- `platform/ai` — the two Spring AI `ChatClient`s (`ChatClients.VISION` for images, `ChatClients.TEXT` for text) and
  their cached health indicators, wired by `config/platform/AIConfiguration`.
- `platform/util` — small helpers (`PhoneNumberUtils`, `MimeUtils`, `UrlUtils`, `KycUtils`, HTTP utilities).
  `platform/util/http`'s `Http` has `postMultipart` (a `multipart/form-data` POST — `String` fields written as-is, the
  others JSON-serialized, plus one `HttpFile`) beside its JSON `post`.
- `platform/telegram` — the Telegram bot, wired by `config/platform/TelegramConfiguration`: `TelegramClient` (a thin
  Bot API client over `platform/util/http/Http`; `sendMessage`, `sendPhoto` and `editMessageText` give each button its
  own keyboard row, while `sendMessageRows` sends the keyboard's rows given explicitly, ex: an offer's accept/refuse
  row then its details row; `sendPhoto` uploads an image, or sends a URL Telegram fetches itself (the resized listing
  photo), with its
  caption and keyboard), `TelegramUpdateHandler` (routes a `callback_query` to its
  `TelegramCallbackHandler` — the mandate domain's offer buttons — gives a `message` the reply of the first
  `TelegramCommand` bean that recognizes it — the agent domain's `/start` and `/connect` — else `OK`, and ignores
  every other
  update type), two transports (`TelegramPollingTransport` locally: long polling on a virtual thread,
  clearing the webhook first; `TelegramWebhookTransport` in TEST/PROD: registers
  `${SERVER_URL}/telegram/webhook` at startup) and `TelegramHealthIndicator` (cached `getMe`, health id `telegram` in
  the default group). The webhook is public (`PublicEndpoints.GLOBAL`), protected only by the
  `X-Telegram-Bot-Api-Secret-Token` header (403 without it); `ndopify.telegram.transport` picks one transport, never
  both. Config under `ndopify.telegram.*`; the token is required and never logged. A callback acts in the tenant its
  chat's link names, which the request got without a token: `TenantSessionScope.reopen()` replaces the request's
  session (opened before that tenant was known) so the tenant-scoped services that follow run in it.
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
  `TENANT` (`POST /v1/otp`, `POST /v1/auth`, `POST /v1/agents`, `POST /v1/mandates`, `GET /v1/listings`,
  `GET /v1/listings/{id}`, `GET /v1/listings/{id}/images`, `GET /v1/contracts/{id}/signatures/{hash}`: called before
  the caller has a token). Without a token, anything else,
  incl. an unknown path, is a 401 `AUTH_UNAUTHORIZED` (`ErrorResponse`, through `ControllerErrorHandler`). A new
  endpoint called before sign-in must be added to `PublicEndpoints.TENANT`. `POST /v1/auth/refresh` is the opposite:
  authenticated (it re-issues the caller's own token, no body) and never in `PublicEndpoints`. Authentication only: who
  may do what is still each domain's access service (`MandateAccessService`, `ContractAccessService`...), and many
  endpoints have none yet.
- Any JPA entity annotated `org.hibernate.annotations.@TenantId` is automatically filtered by Hibernate for every
  query against it — no `CriteriaBuilder` predicate, repository override, or service-level check needed. Today
  that's `PartyEntity`, `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, `KycCaseEntity`,
  `AgentEntity`, `CustomerEntity`, `MandateEntity`, `AssignmentEntity`, `MandateDocumentEntity`,
  `MandateDocumentPageEntity`, `ContractEntity`, `ContractPartyEntity`,
  `MembershipEntity`, `ListingEntity`, `ListingImageEntity`, `ListingSectionEntity`, `MatchEntity` and `VisitEntity`. Leave the field `null` on construction (see the comment on any of them) so Hibernate's
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
  `T_USER.party_id` and `T_USER_APPLICATION.user_id` are left over from before memberships (`V3_1`), no longer
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
  It also carries the session's start (`sessionStartedAt`, read as `iat` for an older token), and
  `AuthenticationService.refresh` re-issues it for at most `max-lifetime` (90d) from that start: a renewal gives the
  token's own lifetime again, or what the cap leaves, and never shortens a longer-lived token (a match link keeps its
  `exp`). Partner Central's TTL is 30d because `ndopify-web-core` renews an agent's token once a day; past the cap a
  renewal is a 409 `AUTH_CREDENTIALS_EXPIRED`, and a membership or application access removed since sign-in is a 409
  `AUTH_ACCESS_DENIED`.

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
