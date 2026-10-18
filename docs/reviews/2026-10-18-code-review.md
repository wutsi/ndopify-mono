# Code review: architecture, security, concurrency, code quality (2026-10-18)

Reviewed at commit `c4141f0`. This revises the review done earlier the same day at `ccd7d84`. Every earlier finding
was checked again (its status is given below), and the commits since then were reviewed: versioning, mandate and
contract access control, the tenant-header check and tenant formatting.

Most of this comes from reading the code. One thing was run: `mvn clean test -Dtest=CreateOtpEndpointTest` in
`ndopify-server`, against a local MySQL. Its Spring context doesn't start (see **B1**). CI on `main` is red too
(`gh run list`).

**Summary:** the design work since the first review is good, and the access rules are written correctly:

- Mandates, agents, contracts, KYC cases and assignments now have `@Version`.
- `requestApproval` is split into 3 transactions, and every contract write locks the contract.
- Access to mandates, contracts and assignments is checked on the server, through one vocabulary (`CallerService`).
- Signing has no admin bypass.

There are four main risks:

1. **`main` is broken.** The server doesn't start, and CI can't resolve the DTO version. So none of the new tests
   have run.
2. **The new access checks don't protect anything yet.** Tokens are still unsigned, so anyone can forge an
   `admin-console`/`admin` token. And the endpoints next to them (customers, agents, KYC, payment methods, users,
   assignments) are still open.
3. **Two new lock problems:** a deadlock between re-sending a mandate for approval and the customer's last
   signature, and a party's KYC status that a photo upload can undo.
4. **Login is still weak:** Google One Tap tokens aren't verified, and one-time codes have no attempt limit.

Paths are relative to `modules/`. `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`,
`B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`, `T` = `ndopify-server/src/test/kotlin/com/wutsi/ndopify`.

## Blockers

| ID | Issue | Where |
|---|---|---|
| B1 | **The server doesn't start: circular bean dependency.** `TenantConfiguration` now takes `TenantService` in its constructor, for the filter. The same class declares the `HibernatePropertiesCustomizer` that the `entityManagerFactory` needs. So the chain loops: `tenantConfiguration` → `tenantService` → `tenantRepository` → `entityManagerFactory` → `tenantConfiguration`, and you get `BeanCurrentlyInCreationException` at startup. Every `@SpringBootTest` fails with it, as does `spring-boot:run`. **Fix:** move the customizer and the `TenantIdentifierResolver` into their own `@Configuration` with no dependencies (for example back into `platform/config`). Or give the filter bean its dependencies as `@Bean` method parameters, or as an `ObjectProvider`. | `S/config/TenantConfiguration.kt:15-34` |
| B2 | **CI on `main` can't build the server.** `ndopify-dto.version` is `0.0.50-SNAPSHOT`, but only releases (0.0.49) are published. `ndopify-server-main` fails with "Could not find artifact ndopify-dto:jar:0.0.50-SNAPSHOT". So B1 and the tests written since `a7f0c56` never ran in CI. **Fix:** pin the released version, or build the DTO in the same reactor (`mvn -pl modules/ndopify-server -am`). | `pom.xml:47` |
| B3 | **The tenant filter throws from a servlet filter**, before `ControllerErrorHandler` can handle it. So a missing or unknown tenant gets Boot's default `/error` response, a 500 with `"error": "Internal Server Error"`, not a 400 `ErrorResponse`. This was confirmed by running `CreateOtpEndpointTest` once B1 was fixed. Only `/v1/tenants`, `/v1/locations` and `/v1/applications` are excluded, so `/actuator/health`, `/api.html`, `/v3/api-docs` and the local `/storage/*` servlet now need `X-Tenant-ID`. That breaks health checks. Some tests weren't updated: `CreateOtpEndpointTest` (`no tenant`, `unknown tenant`), `AuthenticateOtpEndpointTest:214` and `AuthenticatePasswordEndpointTest:189` still expect the old `HTTP_MISSING_PARAMETER` / `TENANT_NOT_FOUND`. `TenantContextFilterTest` only checks that an exception is thrown. **Fix:** write the `ErrorResponse` from the filter, apply the filter only to tenant-scoped `/v1/**` routes, and add an endpoint test for a missing tenant. | `S/security/server/servlet/TenantContextFilter.kt:27-38,54-67` |

## Security

### Known gaps

| Gap | Status at `c4141f0` |
|---|---|
| `Algorithm.none()` tokens | **Still present.** `S/security/server/service/AccessTokenService.kt:81`, `B/service/security/JwtDecoder.kt:29`. |
| `anyRequest().permitAll()` | **Still present.** `S/config/SecurityConfiguration.kt:25`. |
| `X-Tenant-ID` trusted | **Partly fixed.** Missing, unknown and inactive tenants are rejected, and a token's tenant claim wins over the header. Any active tenant is still accepted with no token. A token that fails to decode still quietly falls back to the header (`TenantContextFilter.kt:69-75`). |
| Ops endpoints unauthenticated | **Still present.** Off in prod (`application-prod.yml:23`), on by default (`application.yml:192`). |
| Signing doesn't check who signs | **Fixed in code.** `S/contract/server/service/ContractAccessService.kt:45-51`: the token's party must be the contract party, with no admin bypass. A forged token still gets through (S1). |
| Partner Central doesn't check mandate ownership | **Fixed on the server.** The server's 403 makes Partner Central show the locked view. Partner Central has no check of its own, which is fine. |

**Endpoints**:

| Access | Endpoints |
|---|---|
| Public by design | `POST /v1/otp`, `POST /v1/auth`, `POST /v1/agents`, `POST /v1/mandates`, `GET /v1/tenants`, `/v1/applications`, `/v1/locations/**` |
| Token + access check (new) | mandates: search, get, update, request-approval, close, cancel; contracts: all; assignments: accept, decline |
| `@OpsEndpoint` | `POST /v1/mandates/{id}/dispatch`, `POST /v1/assignments/{id}/expire`, `POST /v1/kyc/cases/{id}/verify` |
| **No check at all** | `GET /v1/assignments(/{id})`, `GET /v1/users/{id}`, `GET|POST /v1/agents/{id}`, `GET /v1/agents`, `GET /v1/customers(/{id})`, `GET /v1/parties`, `POST /v1/parties/{id}/photo`, `GET /v1/payment-methods(/{id})`, `/v1/identifications/**`, `/v1/kyc/cases/**` (incl. the presigned image URLs) |

What the open endpoints still expose, with any `X-Tenant-ID`:

- **ID documents:** the ID front, back and selfie (`/v1/kyc/cases` → `/images/{id}/url`).
- **Customers:** every customer's name, e-mail and WhatsApp number (`GET /v1/customers`).
- **Payment data:** mobile-money numbers and holder names (`GET /v1/payment-methods`).
- **Writes:** `POST /v1/agents/{id}` can change any agent's WhatsApp number, so customers get the attacker's number
  in the "mandate assigned" e-mail. `POST /v1/parties/{id}/photo` can replace anyone's photo.

`POST /v1/contracts/{id}/content` is now admin-only, though S1 defeats that.

### High

| ID | Issue | Where |
|---|---|---|
| S1 (new) | **Anyone can forge an admin token, in any tenant, and sign as anyone.** An agent is admin when the token says `admin-console` + `admin`. Tokens are unsigned, so a hand-made `alg:none` token with `tenantId: N` lets you list, close or cancel every mandate, and create contracts and swap their content. With a customer's `userId` you can sign for them. An agent can do the same by editing their own `__npc_access_token` cookie. This is no worse than before, when no token was needed at all, but the code now *looks* protected. **Fix:** sign tokens (HS256 or RS256, key from the environment) in both modules, before the server is reachable from the internet. | `S/security/server/service/CallerService.kt:24-34`, `AccessTokenService.kt:81` |
| S2 (new) | **The new checks can be bypassed through the open endpoints next to them.** A mandate's customer is protected on `/v1/mandates/{id}`, but `GET /v1/customers` returns every customer without a token. `GET /v1/assignments?agent-id=X` maps any agent's offers to mandate ids. And see the writes above. **Fix:** check access the same way (owner via token → party, or admin) on customer, agent, party, payment-method, KYC, identification, user and assignment reads and writes. | `S/customer/server/endpoint/CustomerEndpoints.kt:21-35`, `S/agent/server/endpoint/AgentEndpoints.kt:51-57`, `S/security/server/endpoint/UserEndpoints.kt:19-25`, `S/mandate/server/endpoint/AssignmentEndpoints.kt:24-36` |
| H1 | **Still present. Google One Tap login never checks the token with Google.** The client's `sub` and e-mail are taken as given, so anyone can get a token for any member of a tenant. **Fix:** `GoogleIdTokenVerifier` with `aud` set to the client id, then take the e-mail and `sub` from the verified token. | `S/security/server/service/authenticator/GoogleOneTapAuthenticator.kt:62-68` |
| H2 | **Still present. One-time codes can be brute-forced.** There's no attempt limit, lockout or rate limit (the file is unchanged). **Fix:** invalidate a code after ~5 failed attempts, rate-limit per e-mail and per IP, and compare in constant time. | `S/.../authenticator/AbstractPasswordAuthenticator.kt:56-71`, `B/ui/login/LoginController.kt:108` |
| H3 | **Fixed:** customer contacts can no longer be read through Partner Central, thanks to the server's `MandateAccessService` and the locked view. They are still readable directly (S2, S3). | `S/mandate/server/service/MandateAccessService.kt` |
| H4 | **Still present. KYC can be passed with a fake ID.** The LLM's instructions are still in `.user {}`, next to images the attacker controls. ID documents now go to Gemini (`GEMINI_API_KEY`): check the data processing agreement. **Fix:** move the instructions to `.system()`, add deterministic checks (MRZ checksum, a face-match service), and send first verifications to manual review. | `S/party/server/service/kyc/KycInfoExtractorIdentification.kt:70-71`, `KycInfoExtractorSelfie.kt` |
| H5 | **Still present. KYC images may be publicly readable** (check the bucket policy). Party photos are linked with an unsigned `publicUrl`. **Fix:** a separate private bucket for KYC images, with Block Public Access and server-side encryption. | `S/party/server/service/PartyService.kt:136` |

### Medium

| ID | Issue | Where |
|---|---|---|
| S3 (new) | **An agent who is only offered a mandate gets the customer's id and comments before accepting.** It can then read the contact from the open `/v1/customers/{id}` and decline. This breaks "no customer data before acceptance", which only Partner Central's `AssignmentViews` enforces. **Fix:** give an offered agent a reduced view, without `customerId` or `comments`. | `S/mandate/server/service/MandateAccessService.kt:33-36`, `MandateMapper.kt:14,49` |
| S4 (new) | **Mandate update doesn't check the status on the server.** The mandate's agent can change the price, city or criteria of an APPROVED or CLOSED mandate after the customer signed. Only Partner Central's `MandateEditRules.EDITABLE` stops it. **Fix:** 409 outside ASSIGNED and PENDING_APPROVAL, keep the city fixed, and validate neighbourhoods on the server. | `S/mandate/server/service/MandateService.kt:188-235` |
| M1 | **Still present. The server trusts the client's Content-Type for KYC uploads.** **Fix:** check the contents (JPEG, PNG and WEBP only) and re-encode the image. | `S/party/server/service/KycService.kt:440` |
| M2 | **Still present. Login reveals whether an e-mail has an account** (404 `USER_NOT_FOUND`), **and code e-mails aren't limited.** **Fix:** always give the same answer, and rate-limit per e-mail. | `S/security/server/service/OtpService.kt:43-45` |
| M3 | **Still present. The default config is open:** health `show-details: always`, the actuator's `beans` / `mappings` / … endpoints, and ops endpoints, all used by the deployed `test` profile. **Fix:** safe defaults, opened only in a local profile. | `ndopify-server/src/main/resources/application.yml:165-172,192` |
| M4 | **Still present, and worse. Internal error messages are returned to clients.** Line 53 still returns `e.message` for unexpected 500s. The new 409 handler returns Hibernate's message, with the entity class and id (`:88`). `AccessTokenService.kt:74` returns the token library's text. **Fix:** a fixed message, with the details logged. | `S/error/server/endpoint/ControllerErrorHandler.kt:53,88` |
| M5 | **Still present. CSRF protection is off in Partner Central.** | `B/config/SecurityConfiguration.kt:35` |

### Low

- **Still present:**
  - **L1.** The login cookie is `Secure` only when `request.isSecure` (`B/service/security/AccessTokenService.kt:19,29`).
  - **L2.** One-time codes are hashed with salted MD5 (`S/security/server/service/PasswordEncryptor.kt:9`).
  - **L4.** Partner Central doesn't rotate the session id on login.
  - **L5.** The local storage servlet is registered in every profile (`S/platform/config/StorageConfiguration.kt:40-41`).
  - **L6.** Personal data in logs (`S/platform/logger/servlet/KVLoggerFilter.kt:53`).
  - **L7.** Prod allows Flyway clean (`application-prod.yml:13`).
  - **L8.** Prod uses the MTN sandbox (`application.yml:237`).
  - **L9.** CDN scripts aren't pinned or integrity-checked (`autonumeric@4` in `louer.html:359`, `rechercher.html:268`).
  - **L10.** Public forms have no rate limit or captcha.
  - The AWS SDK v1 is end-of-life.
- **L3 has moved to [Concurrency](#concurrency).**
- **New:**
  - **S5.** An expired or invalid token answers 403 instead of 401, so clients can't tell "sign in again" from
    "forbidden" (`AccessTokenService.kt:73`). Throw `UnauthorizedException`.
  - **S6.** Answering 404 before 403 tells a caller whether a mandate or contract id exists, and ids are sequential.
    The filter's separate tenant errors do the same for tenants. Minor; optionally answer 404 to non-admins for both.

### Checked and fine

- **The new access logic itself:**
  - Searches force the caller's agent or party id, and asking for someone else's is a 403.
  - `ensureCanSign` refuses another party, with no admin bypass.
  - A PENDING_ASSIGNMENT mandate (no agent yet) can't pass `ensureCanUpdate`.
  - `prepareApproval` checks access under the mandate's lock.
  - Accept and decline check the assignee before revealing the offer's status.
- **The tenant filter's excluded paths** are matched on the raw URI. A trick like `/v1/tenants/..;/…` leaves no
  tenant set, and Hibernate then filters on `NO_TENANT`, so it fails closed.
- **Unchanged since the first review:**
  - No SQL injection.
  - No `th:utext`.
  - Mustache-escaped e-mails.
  - `SecureRandom` codes.
  - No committed secrets.
  - No open redirects.
  - CORS denied by default.
  - Prod exposes only `health`, without details.

## Concurrency

### Earlier findings

| Finding | Status |
|---|---|
| Concurrent writes overwrite each other | **Mostly fixed.** `@Version` is on `MandateEntity`, `AgentEntity`, `ContractEntity`, `KycCaseEntity` and `AssignmentEntity`. Every save of these is a `copy()` of a loaded entity, and no mapper builds one from a DTO. `MandateService.update` / `AgentService.update` check the request's version first (`MandateService.kt:190-195`, `AgentService.kt:160-166`). `onContractSigned` and `AssignmentService.accept` still read without a lock, but now fail with a 409 instead of overwriting. Still open: `markAssigned` (C4) and max-load (C5). |
| `requestApproval`: two contracts, lock held during the upload | **Fixed.** Step 1 locks the mandate and reuses or creates its contract. The contract-number row is locked only in that short transaction, after the mandate. The upload runs outside any transaction, and step 3 checks the version. `TransactionTemplate` avoids self-invocation. One leftover: on a double click, the second request gets a 409 and leaves an orphaned S3 file. |
| Inconsistent locking on contracts | **Fixed.** `setContent(id)`, `requestSignature`, `sign` and `expire` all use `findByIdForUpdate` (`ContractService.kt:157,170,220,293`), and the public `setContent(entity)` is gone. |
| L3: a one-time code can be used twice | **Still present.** `OtpAuthenticator.checkCredentials` reads without a lock, and `AuthFactorService.consume` saves the whole row unconditionally (`AuthFactorService.kt:80-90`; `AuthFactorEntity` has no version). **Fix:** a conditional `UPDATE … WHERE id = ? AND data = ?`, and check that 1 row changed. |
| KYC job order, `recoverInterrupted` | **Still present.** The job still serves the newest cases first (`KycJobs.kt:56-61` → `KycService.kt:105`). `recoverInterrupted` (`KycService.kt:246-250`) has no limit and no try/catch per case. Each case is now locked and checked again, which is better. |
| No cluster lock | **Still present, lower impact.** The jobs now lock or version what they touch, so a second instance would mostly do duplicate work and log warnings. Add ShedLock before scaling out. |

### New

| ID | Severity | Issue | Where |
|---|---|---|---|
| C1 | High | **Deadlock between `requestApproval` step 3 and the last signature.** `completeApproval` locks the mandate, then the contract (`setContent`, `requestSignature`). `sign` locks the contract, then its synchronous `ContractSignedEvent` listener updates the mandate in `onContractSigned`. Example: an agent re-sends a PENDING_APPROVAL mandate just as the customer signs. Each transaction holds one row and waits for the other. InnoDB kills one, and the user sees a 500 (no handler for `PessimisticLockingFailureException` / `CannotAcquireLockException`). **Fix:** always lock the contract before the mandate. In `completeApproval`, lock the contract first, and map lock failures to 409 (or retry). | `S/mandate/server/service/MandateService.kt:394`, `S/contract/server/service/ContractService.kt:157,170,220`, `S/mandate/server/event/MandateEventListener.kt:33-36` |
| C2 | High | **A photo upload can undo a party's KYC status.** `PartyEntity` has no version and no `@DynamicUpdate`. `uploadPhoto` loads the party, uploads to S3 inside the transaction, then saves the whole row. If `KycJobs` marks the party VERIFIED in the meantime, the old status is written back. The case is VERIFIED and `joinedAt` is set, but the party isn't VERIFIED, so the dispatcher never offers it a mandate. `PartyService.update` has the same problem. **Fix:** `@Version` on `PartyEntity`, or targeted `@Modifying` UPDATEs for `kycStatus` and `photoUrl`, and upload before the transaction. | `S/party/server/service/PartyService.kt:125-150` |
| C3 | Medium | **A customer can sign a document they never saw.** `SignContractRequest` doesn't identify the content that was signed. If the agent re-sends new content between the customer opening the page and clicking "sign", the signature counts for the new document. **Fix:** send the `contentUrl` (or a hash) the customer saw, and 409 if it changed. | `ndopify-dto/.../SignContractRequest.kt`, `ContractService.kt:220` |
| C4 | Medium | **`markAssigned` and `onKycVerified` save the whole agent row**, which bumps its version. So an agent's profile form loaded before an offer gets a 409. An actual collision rolls back the whole dispatch: the listener only logs it, and the mandate waits for the cron. Or it rolls back the whole KYC completion: the case stays IN_PROGRESS, and `recoverInterrupted` re-runs the LLM and MoMo checks. **Fix:** targeted UPDATEs for `lastAssignedAt` / `joinedAt` that don't change the version. | `S/agent/server/service/AgentService.kt:92-93,212`, `MandateDispatcher.kt:135` |
| C5 | Medium | **Max-load isn't enforced.** The load counts only ASSIGNED and PENDING_APPROVAL mandates, not PENDING offers, and `accept` doesn't check it again. An agent one below max-load can get several offers in a row and accept them all. **Fix:** count PENDING offers, and check the load again in `accept` with the agent row locked. | `S/mandate/server/service/MandateDispatcher.kt:72-75,208`, `AssignmentService.kt:84-98` |
| C6 | Low | **Possible deadlock between `accept` and `cancel`.** `accept` doesn't lock the mandate. Its flush order (assignment and mandate) isn't pinned, while `cancel` locks the mandate, then the assignment. **Fix:** `mandateDao.findByIdForUpdate` first in `accept`. | `AssignmentService.kt:84-98`, `MandateService.kt:310-316` |
| C7 | Low | **The 409 handler only catches `ObjectOptimisticLockingFailureException`.** A raw `OptimisticLockingFailureException` or a pessimistic-lock error becomes a 500. The code is named after the entity, so a customer whose signature collides with an agent's edit gets `MANDATE_VERSION_CONFLICT`. **Fix:** catch the base exceptions. | `S/error/server/endpoint/ControllerErrorHandler.kt:71` |
| C8 | Low | **A KYC image can change after submission.** `upload` checks PENDING without a lock, uploads, then saves without checking again. Two first uploads of the same image hit the unique key and return a 500. **Fix:** lock the case and check again inside the save transaction. | `S/party/server/service/KycService.kt:141-170` |
| C9 | Low | **Editing a PENDING_APPROVAL mandate leaves the contract out of date.** The contract out for signature no longer matches the mandate. **Fix:** move an edited mandate back to ASSIGNED, or regenerate the content. Same root cause as S4. | `MandateService.kt:188-235` |
| C10 | Low | **A mandate whose contract expires stays PENDING_APPROVAL**, because `ContractExpiredEvent` has no listener. | `S/contract/server/job/ContractJobs.kt` |

`jakarta.transaction.Transactional` is still used in all 15 transactional server files. I found no self-invocation
bug. But it rolls back only on unchecked exceptions, so a checked `IOException` from storage commits (only
`OtpService.create` sets `rollbackOn`). Switch to Spring's annotation, which also allows `readOnly`.

## Architecture

### Earlier findings

| Finding | Status |
|---|---|
| Validation and access live only in Partner Central | **Partly fixed.** Server-side access is done. Still only in Partner Central: mandate content validation (`FindPropertyRules`) and the editable statuses (S4). `ttl-days` is still copied in Partner Central's `application.yml:65`, and `Contract` has no `requestedAt`. |
| Missing tenants aren't rejected | **Partly fixed.** Missing, unknown and inactive tenants are rejected, and `platform` no longer depends on `security` (the filter moved to `security/server/servlet`). See B3 for the new problems. Partner Central still matches tenants with `startsWith` and never refreshes its list (`B/service/TenantContext.kt:26,31-38`). |
| Home page makes ~30 sequential calls | **Still present.** `B/ui/home/HomeController.kt:120,145`; `CurrentAgent` isn't request-scoped. |
| Tenant set-up copied in listeners and jobs | **Still present.** 7 listeners and 3 job classes. A `TenantScope.run(tenantId) { … }` helper would remove it. |
| Entities point at other domains' entities | **Still present.** `AgentEntity.kt:32`, `CustomerEntity.kt:26`, `MembershipEntity.kt:33` → `PartyEntity`. |

### New

- **A1 (Medium). The `error` domain now depends on four domains.** `ControllerErrorHandler` imports `AgentEntity`,
  `ContractEntity`, `AssignmentEntity`, `MandateEntity` and `KycCaseEntity` to name its 409s. Every domain depends
  on `error`, so this creates a package cycle (`S/error/server/endpoint/ControllerErrorHandler.kt:3-17,78-84`).
  - **Fix:** each domain registers its entity → error-code mapping, or rethrows a `ConflictException` itself.
  - The file's package (`error.endpoint`) also doesn't match its directory (`error/server/endpoint`).
- **A2 (Low). Access checks are placed in two different ways.** Mandate checks writes in `MandateService`, under
  its lock, and reads in the endpoint. Contract checks everything in the endpoint. This is documented, but it ties
  `MandateService.update` / `close` / `cancel` to the request's principal, so a future internal caller gets a 401.
  Pass the caller in explicitly, or split public and internal methods.
- **A3 (Low). The version check is optional.** A `null` `UpdateMandateRequest.version` / `UpdateAgentRequest.version`
  skips it. Partner Central always sends it, but other clients get last-write-wins. After a 409, the form shows the
  user's answers on top of the new version, so a second submit overwrites the other change without saying what it
  was.
- **A4 (Low). `TenantFormatter` exists in both modules, and they already differ.**
  - Partner Central uses `currencySymbol` and U+00A0; the server uses the currency code and a plain space.
  - The server source has literal U+202F / U+00A0 characters (`S/refdata/server/service/TenantFormatter.kt:48-49`).
    Write them as `' '`, so they show in review.
  - `B/ui/mandate/MandateLabels.kt:124` still hard-codes `Locale.FRANCE`.

## Code quality and tooling

| Item | Status |
|---|---|
| Duplicate mandates from "Trouver un bien" (double submit) | **Still present.** `B/ui/findproperty/FindPropertyController.kt:235-244` |
| `JoinController` catches only `HttpClientErrorException` | **Still present.** `B/ui/join/JoinController.kt:176` |
| Root POM: Boot overrides, ~40 koki properties, `spring-boot.version` 4.0.3 vs 4.1.1 parent, `pdfbox.vesion` | **Still present.** `pom.xml:78,84,114,122-126` |
| CI path filters | **Fixed.** |
| No Maven cache in CI | **Still present.** |
| PR coverage gate (90/90) vs POM (98/95 root, 95/90 server) | **Still present.** `ndopify-server-pr.yml:50-51` |
| No branch counter in JaCoCo | **Still present.** `pom.xml:313-322` |
| JUnit `Assertions` / `assertThrows` | **Partly fixed:** 12 → 9 files (`OtpGeneratorTest`, `MailServiceTest`, `UrlUtilsTest`, `PhoneNumberUtilsTest`, `KVLoggerThreadLocalTest`, `MailBodyResolverTest`, `KycInfoExtractorProviderTest`, `MtnUserProvider*Test`). |
| `@Ignore`d tests | **Now 3:** `MtnCollectionProductTest`, `MtnUserProviderSandboxTest`, `MtnUserProviderProductionTest`. |
| `Thread.sleep` in tests | **Now 6:** the 4 earlier ones, plus `RequestMandateApprovalEndpointTest.kt:151` (a concurrency test, timing-dependent) and `BaseEndpointIntegrationTest.kt:88`. |
| `KYC_CASE_NOT_PENDING` returns 400, not 409 | **Still present.** `S/party/server/service/KycService.kt:401` |
| Entities default `createdAt` to `Date()` instead of the `Clock` | **Still present.** 28 occurrences in 16 entities. |
| Partner Central `application-test/prod.yml` are koki config | **Still present.** Prod has no `ndopify.server.api.base-url`. |
| Join / FindProperty duplicate helpers | **Still present.** |
| CLAUDE.md out of date on the party domain | **Still present.** It names `KycVerifier*`, `IdentificationInfoExtractorAi` and `IdentificationImageEntity`, and says `KIMI_API_KEY`. The code has `KycCaseVerifier*`, `KycInfoExtractor*`, `KycImageEntity` and `GEMINI_API_KEY`, but the workflows still pass `KIMI_API_KEY`. |

New:

- **Q1. CLAUDE.md is out of date on the new code.**
  - It places the filter and `TenantConfiguration` in `platform/…`; they're now in `security/server/servlet` and
    `config`.
  - It doesn't document the 400 for a missing or invalid tenant.
  - It cites `V7_2` for the version columns. That migration was folded into `V2_3` / `V4_0` / `V5_0` / `V7_0`,
    which is fine pre-launch, but a local database already migrated with `V7_2` fails Flyway validation and needs a
    reset.
  - It says mandate changes run "under the mandate's lock", but `update` uses `findById` (only `@Version`
    protects it).
- **Q2. More copied helpers:**
  - `PROPERTY_TYPES`, `PERIODICITIES`, `neighbourhoods()` and `budget()` appear in both `MandateOfferMailet` and
    `MandateContractGenerator`.
  - `errorCode(HttpClientErrorException)` is copied in 4 Partner Central controllers (`KycController`,
    `JoinController`, `MandateController`, `ProfileController`).
- **Q3. Test gaps:**
  - `CallerService`, `MandateAccessService`, `ContractAccessService` and `OpenApiConfiguration` have no unit
    tests. The access services are well covered by `MandateAccessEndpointTest` and `ContractAccessEndpointTest`
    (20 cases each), but those haven't run (B1, B2).
  - No test updates a CLOSED mandate through the API.
  - No endpoint test covers the tenant filter (B3).

## Strengths

- **One access-control vocabulary.** `CallerService` (`isAdmin`, `findPartyIdOrNull`) is shared by the mandate,
  contract and assignment checks. Searches force the caller's own ids rather than filtering results afterwards,
  and the 404 → 403 → 409 order is tested.
- **"Nobody signs on another's behalf"** is implemented, and tested (`admin can't sign for a party`).
- **`requestApproval`'s 3-transaction design** removes the double-contract and lock-during-upload problems, and has
  a real concurrency test.
- **Versioning is done properly:** every save is a `copy()` of a loaded entity, each entity gets its own 409, and
  Partner Central's profile and mandate pages handle the conflict the same way.
- **The domain dependencies have no cycle** (apart from `error`, A1): mandate → agent/contract/security, contract →
  security, and contract never depends on mandate.
- **Unchanged since the first review:**
  - Clean layering.
  - KYC keeps slow calls outside transactions.
  - Async e-mails are sent after commit, carrying only ids.
  - Contract numbers come from one gap-free sequence per tenant.
  - Services use the injected `Clock`.
  - `MandateDispatcherTest` covers every case of the dispatch algorithm.
- **Dates and amounts in e-mails, contracts and Partner Central now follow the tenant's formats**
  (`TenantFormatter`), with tests in both modules.

## Recommended order

Each step is small and easy to undo:

1. **Get `main` green:**
   - **B1:** split `TenantConfiguration`.
   - **B2:** pin the DTO version, or build it in the reactor.
   - **B3:** write the error from the filter, exclude actuator, docs and storage, and update the 3 stale tests.
   - Then run the full server suite.
2. **C1:** lock the contract before the mandate in `completeApproval`. **C2:** `@Version` or targeted UPDATEs on
   `PartyEntity`.
3. **S1:** sign tokens. Until this ships, the new access checks protect nothing.
4. **H1 + H2 + L3:** verify Google ID tokens, and limit and atomically consume one-time codes.
5. **S2 + S3 + S4:** access checks on the remaining endpoints, a reduced view for offered agents, and a status
   check on mandate update.
6. **C4 / C5:** targeted UPDATEs on the agent, and a max-load that counts PENDING offers.
7. **Smaller items:** KYC job oldest-first, Spring `@Transactional`, POM clean-up, and CLAUDE.md updates (Q1 and
   the party names).
