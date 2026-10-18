# Code review: architecture, security, code quality (2026-10-18)

Reviewed at commit `ccd7d84`. Everything below comes from reading the code, not from running it: no builds were
run, because the server tests need MySQL.

**Summary:** the code is in good shape for an early-stage project. The layers are clean, nothing reaches into
another domain's repositories, multi-tenancy is enforced in one place, and the async e-mails and KYC transactions
are well designed. There are three main risks:

1. **Concurrent requests can overwrite each other's changes**, which corrupts data.
2. **Login can be bypassed or brute-forced.** This is in addition to the gaps already known.
3. **The rules about what a mandate may contain and who may touch it live only in Partner Central.**

Paths are relative to `modules/`. `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`,
`B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`.

## Security

### Known gaps: all still present

- `Algorithm.none()` in `S/security/server/service/AccessTokenService.kt` and in `B/service/security/JwtDecoder.kt`.
- `anyRequest().permitAll()` in `S/config/SecurityConfiguration.kt`.
- The `X-Tenant-ID` header is trusted (`TenantContextFilter.kt`).
- Ops endpoints are unauthenticated.
- Signing a contract doesn't check who signs.
- Partner Central doesn't check mandate ownership.

What these gaps already expose, if the server is reachable from the internet (repeat with any `X-Tenant-ID` to
reach other tenants):

- **ID documents:** `GET /v1/kyc/cases` lists cases with their image ids, then
  `GET /v1/kyc/cases/images/{id}/url` returns a presigned S3 URL for the ID front, back and selfie.
- **Customers:** `GET /v1/customers` with no filter returns every customer's name, e-mail and WhatsApp number.
- **Payment data:** `GET /v1/payment-methods` returns mobile-money numbers and holder names.
- **People:** `GET /v1/parties?email=` and `GET /v1/users/{id}` (sequential ids).
- **Writes:**
  - `POST /v1/agents/{id}` can change any agent's WhatsApp number, so customers get the attacker's number in the
    "mandate assigned" e-mail.
  - `POST /v1/parties/{id}/photo` replaces any party's photo.
  - `POST /v1/contracts/{id}/content` swaps the contract document's URL before signing.

### High

| ID | Issue | Where |
|---|---|---|
| H1 | **Google One Tap login never checks the token with Google.** It accepts whatever `sub` and e-mail the client sends, so anyone can get a token for any member of a tenant. This stays exploitable even after JWTs are signed. **Fix:** verify the ID token (`GoogleIdTokenVerifier`, with `aud` set to the client id), take the e-mail and `sub` from the verified token, and compare `sub` with the stored value. | `S/security/server/service/authenticator/GoogleOneTapAuthenticator.kt:58-67` |
| H2 | **One-time codes can be brute-forced.** There's no attempt limit, lockout or rate limit. There are 900k possible codes, each valid 5 to 10 minutes, and new codes can be requested without limit. At ~300 requests/s an attacker covers about 10% of the codes per window, so roughly an hour to take over an account. **Fix:** invalidate a code after about 5 failed attempts, rate-limit per e-mail and per IP on both the server and Partner Central, and compare codes in constant time. | `S/.../authenticator/AbstractPasswordAuthenticator.kt:56-71`, `B/ui/login/LoginController.kt:108` |
| H3 | **Anyone can read customer contacts through Partner Central.** Sign up through `/join` with your own e-mail and log in by code (no KYC needed). You can then step through `/mandates/{1..N}` and collect the name, e-mail and WhatsApp number of the customer of every assigned, approved or closed mandate. You can also close or cancel other agents' mandates. **Fix:** check `mandate.agentId == currentAgent.id` in Partner Central now, and on the server later. | `B/ui/mandate/MandateController.kt:107` |
| H4 | **KYC can be passed with a fake ID.** The LLM alone decides whether the document is forged and whether the selfie matches. Its instructions sit in the same user message as the images, which the attacker controls, so text printed on a fake ID can steer it. The only check done in code is the name, compared with a name the attacker chose at sign-up. ID documents are also sent to Gemini, a third party: check that the data processing agreement and data residency allow it. **Fix:** move the instructions to `.system()` and tell the model that text in images is data. Add deterministic checks (MRZ checksum, a dedicated face-match service), and send first verifications to manual review. | `S/party/server/service/kyc/KycInfoExtractorIdentification.kt:71-80`, `KycInfoExtractorSelfie.kt:41-50`, `KycCaseVerifierSelfie.kt:37` |
| H5 | **KYC images may be publicly readable (check the bucket policy).** Party photos are linked by plain (unsigned) S3 URLs, which only work on a public bucket. The test bucket also hosts the public website (`--acl public-read`). **Fix:** move KYC images to a separate private bucket with Block Public Access and server-side encryption, and serve photos from a public prefix or a CDN. | `S/party/server/service/PartyService.kt:136`, `.github/workflows/ndopify-website-main.yml:39-42` |

### Medium

| ID | Issue | Where |
|---|---|---|
| M1 | **The server trusts the client's Content-Type for KYC uploads**, including `image/svg+xml`. Partner Central checks the file contents with Tika, but the server doesn't. **Fix:** check contents on the server, allow only JPEG, PNG and WEBP, re-encode the image, and set `Content-Disposition: attachment`. | `S/party/server/service/KycService.kt:440-441` |
| M2 | **Login reveals whether an e-mail has an account, and there's no limit on code e-mails**, so an address can be flooded. **Fix:** always give the same generic answer, and rate-limit per e-mail. | `S/security/server/service/OtpService.kt:46`, `B/ui/login/LoginController.kt:95` |
| M3 | **The deployed `test` profile runs with the open defaults:** ops endpoints, Swagger, and the actuator's `beans` / `mappings` / `flyway` / `scheduledtasks` and full `health`. It uses a real S3 bucket and a real LLM key. **Fix:** make the defaults safe, and turn the open settings on only in a local profile. | `ndopify-server/src/main/resources/application.yml:165-172` |
| M4 | **Raw exception messages are returned for unexpected 500s**, leaking SQL, Hibernate and Jackson details. **Fix:** return a generic message for any 5xx that isn't a `WutsiException`. | `S/error/server/endpoint/ControllerErrorHandler.kt:48` |
| M5 | **CSRF protection is off in Partner Central.** It relies only on the cookie's SameSite=Lax, which doesn't stop a page on a sibling subdomain from posting to `/mandates/{id}/cancel`. **Fix:** turn Spring's CSRF protection back on (Thymeleaf `th:action` forms add the token). | `B/config/SecurityConfiguration.kt:35` |

### Low

- **L1. Login cookie isn't always `Secure`.** It's only set when `request.isSecure` is true, which can be false
  behind a TLS proxy (`B/service/security/AccessTokenService.kt:19,29`). Set it always, or set
  `server.forward-headers-strategy`.
- **L2. One-time codes are hashed with salted MD5** (`S/security/server/service/PasswordEncryptor.kt:9`), which is
  trivial to reverse for a 6-digit code. Use an HMAC with a server secret, and bcrypt or argon2 if passwords are ever
  enabled.
- **L3. A code can be used twice** by two simultaneous `/v1/auth` requests. Consume it atomically (a conditional
  UPDATE or a lock).
- **L4. Partner Central doesn't rotate the session id on login.** Low impact since the token has its own cookie. Call
  `request.changeSessionId()`.
- **L5. The local storage servlet is registered in every profile, prod included**
  (`S/platform/config/StorageConfiguration.kt:40`). Its "presigned" URLs are permanent links, and only Tomcat
  protects it against path traversal (`StorageServletLocal.kt:20`). Register it only for local storage, and check the
  path stays under the storage directory.
- **L6. Personal data in logs.** Query parameters are logged as-is, e.g. `?email=`
  (`S/platform/logger/servlet/KVLoggerFilter.kt:54`). Partner Central logs server error bodies that contain e-mails
  (`LoginController.kt:91,121`).
- **L7. Prod allows Flyway clean:** `spring.flyway.clean-disabled: false` in `application-prod.yml:13`. Set it to
  `true`.
- **L8. Prod uses the MTN sandbox:** `application.yml:237` isn't overridden for prod, so the mobile-money KYC check
  means nothing there.
- **L9. CDN scripts aren't pinned.** They have no integrity hashes, and `autonumeric@4` floats on the website
  (`louer.html:359`, `rechercher.html:268`). Pin exact versions and add integrity hashes.
- **L10. Public forms have no abuse protection.**
  - `/find-property`, `/join` and `/v1/locations/import` have no rate limit or captcha. The import stays an open
    endpoint by decision; it only needs a rate limit.
  - A mandate can be submitted with someone else's e-mail, so that person receives an agent's WhatsApp number.
  - `PartyService.findOrCreate` reuses an existing person's record by e-mail.
- **AWS SDK v1 is end-of-life.**

### Checked and fine

- **SQL injection:** none; native queries use bound parameters.
- **Thymeleaf output:** no `th:utext`, and dynamic links have fixed `tel:`, `mailto:` or `wa.me` prefixes.
- **E-mail templates:** Mustache-escaped, and a test covers it.
- **One-time code generation:** `SecureRandom`, 6 digits, single use, lifetime 60–600 s, never logged.
- **Secrets:** none committed; only `${ENV}` placeholders.
- **Redirects and file names:** redirect targets are fixed (no open redirect), and S3 keys are built from server
  UUIDs (no path traversal).
- **Prod config:** only `health` is exposed, without details, and Swagger is off.
- **CORS:** not configured, so cross-origin calls get the default deny.

## Architecture and correctness

1. **Concurrent writes overwrite each other.** Every write reads the entity, copies it and saves the whole row, but
   only `AssignmentEntity` has a version column.
   - `MandateService.update` (`MandateService.kt:177-217`) can reopen a CLOSED mandate, or overwrite an approval
     that has just come in. `MandateService.onContractSigned` (`:256-271`) and `AssignmentService.accept`
     (`AssignmentService.kt:95`) write the mandate without locking it.
   - `AgentService.markAssigned` and an agent's own profile edit (`AgentService.kt:92,155`) overwrite each other.
   - Two mandates dispatched at the same time can both pass the maximum-load check for the same agent
     (`MandateDispatcher.kt:85` only locks the mandate).
   - **Fix:** add a version column to `MandateEntity`, `AgentEntity`, `ContractEntity` and `KycCaseEntity`, and
     return 409 when a version check fails. Make single-field changes such as `markAssigned` targeted `UPDATE`s.
2. **`requestApproval` can create two contracts and holds a lock during the S3 upload**
   (`MandateService.kt:222,363-376`).
   - It reads the mandate without a lock. Two quick clicks both see no contract, so both create one, both use up a
     contract number and both e-mail the customer.
   - The per-tenant contract-number row (`ContractNumberGenerator.kt:36-46`) stays locked during the upload, so other
     contract creations in the tenant wait on S3.
   - **Fix:** lock the mandate first (`findByIdForUpdate`), and upload before any database write or after commit.
3. **Locking on contracts is inconsistent.** `requestSignature` doesn't lock (`ContractService.kt:175`), so it can
   write `PENDING_SIGNATURE` over `EXPIRED` while the expiry job runs. The public `setContent(entity)` method
   (`:162`) skips the lock. **Fix:** lock in `requestSignature`, make the entity version private, and have mandate
   call `setContent(id, …)`.
4. **Validation and access checks live in Partner Central.**
   - Checking a mandate's content (`FindPropertyRules`) and which statuses can be edited (`MandateController.kt:193`)
     happens only there.
   - The server checks the caller for assignments (`AssignmentService.ensureAssignee`) but not for mandates or
     contracts.
   - Partner Central has its own copy of the server's `ndopify.contract.signature.ttl-days` setting.
   - **Fix:** move these checks into `MandateService.update`, add one access policy on the server, and expose
     `requestedAt` on `Contract`.
5. **Missing tenants aren't rejected.**
   - Server: a request without a tenant reads nothing (`NO_TENANT`), and its writes fail with a 500. An unknown or
     inactive tenant ID is accepted. A JWT that fails to decode quietly falls back to the header
     (`TenantContextFilter.kt:36-40`). `platform` also depends on the `security` domain, which inverts the layering.
   - Partner Central: the tenant is matched against the request URL with `startsWith` (`npc/service/TenantContext.kt:22-28`),
     and the tenant list is loaded once and never refreshed (`:31-38`).
   - **Fix:** require a valid, active tenant on tenant-scoped routes (400 otherwise), match the host exactly, and
     cache tenants with a TTL.
6. **The home page makes about 30 sequential calls to the server.** That's 3 calls to find the signed-in agent
   (`CurrentAgent.kt:17-20`, not cached per request), plus one call per offer (`HomeController.kt:118-125`) and one
   per mandate card (`:143-150`, up to 20). **Fix:** add the missing fields to `MandateSummary`, or a batch
   `GET /v1/mandates?id=…`, and make `CurrentAgent` `@RequestScope`.
7. **The KYC job serves the newest cases first.** `KycJobs.kt:56-61` reuses `KycService.search`, which sorts
   newest-first (`KycService.kt:105`). Once more than 100 cases are waiting, the oldest ones may never be verified.
   `recoverInterrupted` (`KycService.kt:246-250`) has no limit and no try/catch per case, so one failing case stops
   the rest from being recovered.
8. **`jakarta.transaction.Transactional` is used instead of Spring's annotation** (all services), so read-only
   transactions aren't possible. Lazy mapping only works because open-in-view is on.
9. **The tenant set-up and clean-up code is copied** in 8 async listener methods and in 3 job classes. There's also
   no cluster lock (e.g. ShedLock), so a second instance would run every job twice. **Fix:** a
   `TenantScope.run(tenantId) { … }` helper, then ShedLock before running more than one instance.
10. **Entities persist DTO enums and point at other domains' entities.** `AgentEntity`, `CustomerEntity` and
    `MembershipEntity` → `PartyEntity`. Mandate gets this right by storing plain ids: follow that pattern for new
    links.

## Code quality and tooling

- **Duplicate mandates from "Trouver un bien"** (`B/ui/findproperty/FindPropertyController.kt:230-244`): the session
  is only cleared after the server call succeeds, so a double click submits twice. Each copy is dispatched to an
  agent.
- **The join wizard shows a 500 page when the server fails**, losing what the person typed: `JoinController.kt:176`
  only catches `HttpClientErrorException`. `FindPropertyController` correctly catches `RestClientException`.
- **The root POM overrides Boot 4.1.1's managed versions** (`pom.xml:47-134`), e.g. `flyway.version` and
  `spring-security.version`. It also has about 42 unused properties left over from koki, `spring-boot.version` 4.0.3
  next to the 4.1.1 parent, and a typo (`pdfbox.vesion`).
- **CI path filters point at the wrong file:** `ndopify-server-pr.yml:7` / `ndopify-partner-central-pr.yml:7` list
  the `-main.yml` workflows instead of their own. There's no Maven cache, and the PR coverage gate (90%) doesn't
  match the POM's 95/98%.
- **The coverage gate has no branch counter** (`pom.xml:309-322`). Only line and class coverage are checked, which
  misses many Kotlin null-check branches.
- **12 server test files use JUnit `Assertions` / `assertThrows`**, against the `kotlin.test` convention (e.g.
  `OtpGeneratorTest`, `MailServiceTest`, `UrlUtilsTest`). `MtnUserProviderProductionTest` is `@Ignore`d.
  `Thread.sleep` is used in 4 tests (`AuthenticatePasswordEndpointTest`, `AuthenticateGoogleOneTapEndpointTest`,
  `AuthenticateUnknownEndpointTest`, `UpdateAgentEndpointTest`).
- **`KYC_CASE_NOT_PENDING` returns 400** (`KycService.kt:399-405`), while every other state conflict returns 409.
- **Entities default `createdAt` / `modifiedAt` to `Date()`** instead of using the injected `Clock`.
- **Partner Central's `application-test.yml` / `application-prod.yml` are still koki config.** The prod profile
  doesn't even set `ndopify.server.api.base-url`.
- **`JoinController` and `FindPropertyController` repeat about 8 helpers word for word.** A `WizardFlow<T>` helper
  could replace them.
- **CLAUDE.md is out of date on the party domain.** It names `KycVerifier*` / `IdentificationInfoExtractorAi` /
  `IdentificationImageEntity`, but the code has `KycCaseVerifier*` / `KycInfoExtractor*` / `KycImageEntity`.

## Strengths

- **Clean layering:** endpoints never touch repositories, repositories stay inside their own domain, and mappers have
  no dependencies.
- **Contract and mandate are decoupled through events** (`ContractSignedEvent` → synchronous `MandateEventListener`),
  while mandate approval stays in the same transaction.
- **KYC keeps slow calls (LLM, MTN) outside any transaction**: claim (locked) → verify → complete, with recovery of
  interrupted runs.
- **Async e-mails are sent after commit**, their events carry only ids, and the tenant and logger are set and cleared
  on the executor thread.
- **Contract numbers come from one gap-free sequence per tenant**, and the dispatcher locks the mandate and re-checks
  its state before offering.
- **`MandateDispatcherTest` covers every case of the dispatch algorithm**, services use an injected `Clock`
  throughout, and every mock uses `@MockitoBean`.

## Recommended order

Each step is small and easy to undo:

1. **H1 + H2:** verify Google ID tokens, and add an attempt counter and rate limit to one-time codes.
2. **H3:** ownership check in Partner Central's `MandateController`.
3. **Concurrency:** a version column on `MandateEntity` plus a lock in `requestApproval`, then the same for agents
   and contracts.
4. **H5:** check the S3 bucket policy for KYC images.
5. **Smaller items:** sort the KYC job oldest-first, and clean up the POM and the CI path filters.
