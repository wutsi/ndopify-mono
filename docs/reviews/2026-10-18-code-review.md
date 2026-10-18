# Code review: architecture, security, concurrency, code quality (2026-10-18)

Reviewed at commit `f4a7c98`. This is the third pass of the day, after `ccd7d84` and `c4141f0`. Every earlier finding
was checked again (its status is given below), and the commits since `c4141f0` were reviewed:

- the tenant filter and its configuration (B1, B3);
- the DTO version pin (B2);
- non-signing contract parties (the mandate's agent);
- Partner Central's 403 / 404 error pages;
- the prod job crons.

Most of this comes from reading the code. The test results come from CI on `main` (`ndopify-server-main`, run
`39162106602`, at `f4a7c98`): 922 tests, **2 failures**, 3 skipped. Partner Central's last `main` build is green.

**Summary:** the build blockers are mostly fixed:

- The server starts again.
- CI builds against a released DTO.
- A missing or unknown tenant is now a proper 400 `ErrorResponse`.

The contract change (the agent is a party that doesn't sign) is done cleanly. It also gives Partner Central the
contract dates it couldn't read before.

There are four main risks:

1. **`main` is still red:** 2 tests fail. One is a real bug: `/actuator/health` now needs `X-Tenant-ID`.
2. **New regression: an expired token locks agents out of Partner Central.** The server now rejects a request whose
   token doesn't decode (403), even on public endpoints. Partner Central keeps forwarding the expired cookie, so
   sign-in fails with "Cette adresse email n'est pas valide!" (**R1**).
3. **The access checks still don't protect anything** (unchanged): tokens are unsigned, and the endpoints next to
   them are open.
4. **The lock problems and weak login are unchanged:** C1 / C2, Google One Tap, and one-time codes without an
   attempt limit.

Paths are relative to `modules/`. `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`,
`B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`, `T` = `ndopify-server/src/test/kotlin/com/wutsi/ndopify`.

## Blockers

| ID | Issue | Where |
|---|---|---|
| B1 | **Fixed.** The Hibernate customizer and `TenantIdentifierResolver` moved to `TenantJpaConfiguration`, which has no dependencies. Its KDoc says why. The context starts, and CI runs the whole suite. | `S/config/TenantJpaConfiguration.kt` |
| B2 | **Fixed.** `ndopify-dto.version` is pinned to the released `0.0.51`, which includes `signatureRequired`. One thing to watch: every DTO change now needs a release *before* the server can use it. The DTO's `pom.xml` was also reformatted by the release plugin (`<tag>` indentation). | `pom.xml:47` |
| B3 | **Mostly fixed.** The filter hands its `WutsiException` to Spring MVC's `HandlerExceptionResolver`, so a missing, unknown or inactive tenant is a 400 `ErrorResponse`. Swagger (`/api.html`, `/swagger-ui`, `/v3/api-docs`) and `/storage` are excluded. The 3 stale tests were updated, and `TenantContextFilterTest` now checks the resolver. **Still open:** `/actuator` isn't excluded, so `/actuator/health` answers 400 without `X-Tenant-ID`. That breaks load-balancer and Kubernetes health checks, and it's why `ProdProfileEndpointTest.health is exposed, without details` fails in CI. **Fix:** add `/actuator` to `EXCLUDED_PATHS`. | `S/security/server/servlet/TenantContextFilter.kt:35-47` |
| B4 (new) | **A cross-tenant test now passes for the wrong reason, then fails.** `UpdatePartyPhotoEndpointTest.party belongs to a different tenant` sends tenant 2, but its fixture only creates tenant 1. So the filter answers 400 `TENANT_ID_NOT_VALID`, and the test (which expects 404 `PARTY_NOT_FOUND`) fails in CI. It no longer checks tenant isolation. The other `overrideTenantId = 2L` tests pass, so their fixtures have tenant 2. **Fix:** add tenant 2 to `party/UpdatePartyPhotoEndpoint.sql`. | `T/party/endpoint/UpdatePartyPhotoEndpointTest.kt:128-140` |

## Security

### Known gaps

| Gap | Status at `f4a7c98` |
|---|---|
| `Algorithm.none()` tokens | **Still present.** `S/security/server/service/AccessTokenService.kt:82`, `B/service/security/JwtDecoder.kt:29`. |
| `anyRequest().permitAll()` | **Still present.** `S/config/SecurityConfiguration.kt:25`. |
| `X-Tenant-ID` trusted | **Partly fixed.** Missing, unknown and inactive tenants are rejected, and a token's tenant claim wins over the header. A token that fails to decode no longer falls back to the header: it's now rejected (but see R1). Any active tenant is still accepted with no token. |
| Ops endpoints unauthenticated | **Still present.** Off in prod (`application-prod.yml:23`), on by default (`application.yml:192`). |
| Signing doesn't check who signs | **Fixed in code.** The token's party must be the contract party, with no admin bypass. A party whose signature isn't required now gets 409 `CONTRACT_PARTY_SIGNATURE_NOT_REQUIRED`. A forged token still gets through (S1). |
| Partner Central doesn't check mandate ownership | **Fixed on the server.** A 403 now makes Partner Central show the locked view (page), `changed` (action) or `error/403.html` (elsewhere). |

**Endpoints:** unchanged since `c4141f0`.

| Access | Endpoints |
|---|---|
| Public by design | `POST /v1/otp`, `POST /v1/auth`, `POST /v1/agents`, `POST /v1/mandates`, `GET /v1/tenants`, `/v1/applications`, `/v1/locations/**` |
| Token + access check | mandates: search, get, update, request-approval, close, cancel; contracts: all; assignments: accept, decline |
| `@OpsEndpoint` | `POST /v1/mandates/{id}/dispatch`, `POST /v1/assignments/{id}/expire`, `POST /v1/kyc/cases/{id}/verify` |
| **No check at all** | `GET /v1/assignments(/{id})`, `GET /v1/users/{id}`, `GET|POST /v1/agents/{id}`, `GET /v1/agents`, `GET /v1/customers(/{id})`, `GET /v1/parties`, `POST /v1/parties/{id}/photo`, `GET /v1/payment-methods(/{id})`, `/v1/identifications/**`, `/v1/kyc/cases/**` (incl. the presigned image URLs) |

What the open endpoints still expose, with any `X-Tenant-ID`:

- **ID documents:** the ID front, back and selfie (`/v1/kyc/cases` → `/images/{id}/url`).
- **Customers:** every customer's name, e-mail and WhatsApp number (`GET /v1/customers`).
- **Payment data:** mobile-money numbers and holder names (`GET /v1/payment-methods`).
- **Writes:** `POST /v1/agents/{id}` can change any agent's WhatsApp number, so customers get the attacker's number
  in the "mandate assigned" e-mail. `POST /v1/parties/{id}/photo` can replace anyone's photo.

### High

| ID | Issue | Where |
|---|---|---|
| R1 (new) | **An expired token locks an agent out of Partner Central, and blocks the public wizards.** `TenantContextFilter.resolveFromPrincipal` no longer catches decoding errors. So every tenant-scoped request with an expired or invalid `Authorization` header gets 403 `AUTH_UNAUTHORIZED` from the filter, including the public `POST /v1/otp`, `POST /v1/auth`, `POST /v1/agents` and `POST /v1/mandates`. Partner Central's `JwtAuthenticationFilter` treats a token that fails to decode as signed-out, but leaves the cookie in place. Its `AuthorizationClientHttpRequestInterceptor` then forwards the cookie on every call. Tokens last 1 hour (`AbstractAuthenticator.kt:57`), and the cookie is a session cookie, so after an hour: protected pages redirect to `/login`; `POST /login` gets a 403 and shows "Cette adresse email n'est pas valide!"; *Rejoindre* and *Trouver un bien* fail on submit. This lasts until the agent visits `/logout` or clears their cookies. No test covers it: the endpoint tests never send an expired token to a public endpoint. **Fix (both sides):** (1) Partner Central: clear the cookie when the token doesn't decode, and don't forward one that's expired. (2) Server: answer 401, not 403 (S5), so clients know to sign in again. Optionally, ignore a bad token on the public endpoints. | `S/security/server/servlet/TenantContextFilter.kt:90-92`, `S/security/server/service/AccessTokenService.kt:66-79`, `B/service/security/JwtAuthenticationFilter.kt:35-38`, `B/client/AuthorizationClientHttpRequestInterceptor.kt:31-33` |
| S1 | **Still present. Anyone can forge an admin token, in any tenant, and sign as anyone.** An agent is admin when the token says `admin-console` + `admin`. Tokens are unsigned, so a hand-made `alg:none` token with `tenantId: N` lets you list, close or cancel every mandate, and create contracts and swap their content. With a customer's `userId` you can sign for them. An agent can do the same by editing their own `__npc_access_token` cookie. **Fix:** sign tokens (HS256 or RS256, key from the environment) in both modules, before the server is reachable from the internet. | `S/security/server/service/CallerService.kt:24-34`, `AccessTokenService.kt:82` |
| S2 | **Still present. The checks can be bypassed through the open endpoints next to them.** A mandate's customer is protected on `/v1/mandates/{id}`, but `GET /v1/customers` returns every customer without a token. `GET /v1/assignments?agent-id=X` maps any agent's offers to mandate ids. And see the writes above. **Fix:** check access the same way (owner via token → party, or admin) on customer, agent, party, payment-method, KYC, identification, user and assignment reads and writes. | `S/customer/server/endpoint/CustomerEndpoints.kt:21-35`, `S/agent/server/endpoint/AgentEndpoints.kt:51-57`, `S/security/server/endpoint/UserEndpoints.kt:19-25`, `S/mandate/server/endpoint/AssignmentEndpoints.kt:24-36` |
| H1 | **Still present. Google One Tap login never checks the token with Google.** The client's `sub` and e-mail are taken as given, so anyone can get a token for any member of a tenant. **Fix:** `GoogleIdTokenVerifier` with `aud` set to the client id, then take the e-mail and `sub` from the verified token. | `S/security/server/service/authenticator/GoogleOneTapAuthenticator.kt:62-68` |
| H2 | **Still present. One-time codes can be brute-forced.** There's no attempt limit, lockout or rate limit. **Fix:** invalidate a code after ~5 failed attempts, rate-limit per e-mail and per IP, and compare in constant time. | `S/.../authenticator/AbstractPasswordAuthenticator.kt:56-71`, `B/ui/login/LoginController.kt:108` |
| H3 | **Fixed:** customer contacts can no longer be read through Partner Central. They are still readable directly (S2, S3). | `S/mandate/server/service/MandateAccessService.kt` |
| H4 | **Still present. KYC can be passed with a fake ID.** The LLM's instructions are still in `.user {}`, next to images the attacker controls. ID documents go to Gemini (`GEMINI_API_KEY`): check the data processing agreement. **Fix:** move the instructions to `.system()`, add deterministic checks (MRZ checksum, a face-match service), and send first verifications to manual review. | `S/party/server/service/kyc/KycInfoExtractorIdentification.kt:70-71`, `KycInfoExtractorSelfie.kt` |
| H5 | **Still present. KYC images may be publicly readable** (check the bucket policy). Party photos are linked with an unsigned `publicUrl`. **Fix:** a separate private bucket for KYC images, with Block Public Access and server-side encryption. | `S/party/server/service/PartyService.kt:136` |

### Medium

| ID | Issue | Where |
|---|---|---|
| S3 | **Still present. An agent who is only offered a mandate gets the customer's id and comments before accepting.** It can then read the contact from the open `/v1/customers/{id}` and decline. **Fix:** give an offered agent a reduced view, without `customerId` or `comments`. | `S/mandate/server/service/MandateAccessService.kt:33-36`, `MandateMapper.kt:14,49` |
| S4 | **Still present. Mandate update doesn't check the status on the server.** The mandate's agent can change the price, city or criteria of an APPROVED or CLOSED mandate after the customer signed. **Fix:** 409 outside ASSIGNED and PENDING_APPROVAL, keep the city fixed, and validate neighbourhoods on the server. | `S/mandate/server/service/MandateService.kt:191-234` |
| M1 | **Still present. The server trusts the client's Content-Type for KYC uploads.** **Fix:** check the contents (JPEG, PNG and WEBP only) and re-encode the image. | `S/party/server/service/KycService.kt:440` |
| M2 | **Still present. Login reveals whether an e-mail has an account** (404 `USER_NOT_FOUND`), **and code e-mails aren't limited.** **Fix:** always give the same answer, and rate-limit per e-mail. | `S/security/server/service/OtpService.kt:43-45` |
| M3 | **Still present. The default config is open:** health `show-details: always`, the actuator's `beans` / `mappings` / … endpoints, and ops endpoints, all used by the deployed `test` profile. **Fix:** safe defaults, opened only in a local profile. | `ndopify-server/src/main/resources/application.yml:165-172,192` |
| M4 | **Still present, and now reached from the filter too. Internal error messages are returned to clients.** `ControllerErrorHandler.kt:53` returns `e.message` for unexpected 500s, and `:88` returns Hibernate's message, with the entity class and id. `AccessTokenService.kt:76` puts the JWT library's text in the 403 (ex: the expiry date), and every request with a bad token now gets that 403 (R1). **Fix:** a fixed message, with the details logged. | `S/error/server/endpoint/ControllerErrorHandler.kt:53,88`, `AccessTokenService.kt:76` |
| M5 | **Still present. CSRF protection is off in Partner Central.** | `B/config/SecurityConfiguration.kt:35` |

### Low

- **Still present:**
  - **L1.** The login cookie is `Secure` only when `request.isSecure` (`B/service/security/AccessTokenService.kt:19,29`).
  - **L2.** One-time codes are hashed with salted MD5 (`S/security/server/service/PasswordEncryptor.kt:9`).
  - **L4.** Partner Central doesn't rotate the session id on login.
  - **L5.** The local storage servlet is registered in every profile (`S/platform/config/StorageConfiguration.kt:40-41`).
    It's also excluded from the tenant filter now, so it serves any stored file with no tenant at all.
  - **L6.** Personal data in logs (`S/platform/logger/servlet/KVLoggerFilter.kt:53`).
  - **L7.** Prod allows Flyway clean (`application-prod.yml:13`).
  - **L8.** Prod uses the MTN sandbox (`application.yml:237`).
  - **L9.** CDN scripts aren't pinned or integrity-checked (`autonumeric@4` in `louer.html:359`, `rechercher.html:268`).
  - **L10.** Public forms have no rate limit or captcha.
  - The AWS SDK v1 is end-of-life.
  - **S5.** An expired or invalid token answers 403 instead of 401, so clients can't tell "sign in again" from
    "forbidden" (`AccessTokenService.kt:74`). Throw `UnauthorizedException`. **More urgent now, since it's half
    of the R1 fix.**
  - **S6.** Answering 404 before 403 tells a caller whether a mandate or contract id exists, and ids are sequential.
    The filter's separate tenant errors do the same for tenants.
- **New:**
  - **S7.** The tenant filter has no order any more. The `HIGHEST_PRECEDENCE` it had was dropped with the
    `FilterRegistrationBean` rewrite, so it's `LOWEST_PRECEDENCE`, like `KVLoggerFilter`, and their relative order
    is undefined. Spring Security's chain (order -100) now runs before it. That's harmless while it's `permitAll`,
    but a future authentication filter would run with no `TenantContext`. Set `order = Ordered.HIGHEST_PRECEDENCE`
    again (`S/config/TenantConfiguration.kt:18-22`).

### Checked and fine

- **The filter's error path.** A `WutsiException` goes through `HandlerExceptionResolver` with a `null` handler, so
  `ControllerErrorHandler` builds the usual `ErrorResponse`. If nothing resolves it, it's rethrown, and
  `TenantContext` is never set for a rejected request. Excluded paths are matched exactly or as `path/`, so
  `/v1/tenantsX` isn't excluded.
- **Non-signing contract parties.** `signatureRequired` is persisted and mapped. At least one party must sign
  (else 400). The signature e-mails skip non-signing parties, `sign` refuses them (409) after the access check, and
  the last *required* signature makes the contract SIGNED. Tests cover all of it. Being a party lets the mandate's
  agent read its contract through the normal party rule, with no mandate-specific policy.
- **Partner Central's error pages.** `HttpClientErrorHandler` maps an uncaught 403 / 404 from the server to the same
  status instead of a 500. The pages don't render server data, and the mandate page counts a 403 as `changed`.
- **The access logic itself (unchanged):**
  - Searches force the caller's agent or party id, and asking for someone else's is a 403.
  - `ensureCanSign` refuses another party, with no admin bypass.
  - A PENDING_ASSIGNMENT mandate (no agent yet) can't pass `ensureCanUpdate`.
  - `prepareApproval` checks access under the mandate's lock.
  - Accept and decline check the assignee before revealing the offer's status.
- **Unchanged since the first review:**
  - No SQL injection.
  - No `th:utext`.
  - Mustache-escaped e-mails.
  - `SecureRandom` codes.
  - No committed secrets.
  - No open redirects.
  - CORS denied by default.
  - Prod exposes only `health`, without details (once B3's `/actuator` exclusion is in).

## Concurrency

None of these files changed since `c4141f0`, except `MandateService` (the contract's agent party) and
`ContractService` (`signatureRequired`). Neither change touches lock order.

### Earlier findings

| Finding | Status |
|---|---|
| Concurrent writes overwrite each other | **Mostly fixed.** `@Version` is on `MandateEntity`, `AgentEntity`, `ContractEntity`, `KycCaseEntity` and `AssignmentEntity`. Every save is a `copy()` of a loaded entity. Still open: `markAssigned` (C4) and max-load (C5). |
| `requestApproval`: two contracts, lock held during the upload | **Fixed.** One leftover: on a double click, the second request gets a 409 and leaves an orphaned S3 file. `createContract` now also loads the agent, inside step 1 (under the mandate's lock), which is fine. |
| Inconsistent locking on contracts | **Fixed.** `setContent`, `requestSignature`, `sign` and `expire` all lock (`ContractService.kt:161,174,224,302`). |
| L3: a one-time code can be used twice | **Still present.** `OtpAuthenticator.checkCredentials` reads without a lock, and `AuthFactorService.consume` saves the whole row (`AuthFactorService.kt:80-90`). **Fix:** a conditional `UPDATE … WHERE id = ? AND data = ?`, and check that 1 row changed. |
| KYC job order, `recoverInterrupted` | **Still present.** Newest cases first (`KycJobs.kt:56-61` → `KycService.kt:105`). `recoverInterrupted` has no limit and no try/catch per case. The default verify cron is now every 15 minutes (prod: hourly), so each run does less. |
| No cluster lock | **Still present, lower impact.** Prod now sets all four job crons explicitly. Add ShedLock before scaling out. |

### Open

| ID | Severity | Issue | Where |
|---|---|---|---|
| C1 | High | **Still present. Deadlock between `requestApproval` step 3 and the last signature.** `completeApproval` locks the mandate, then the contract. `sign` locks the contract, then its synchronous listener updates the mandate in `onContractSigned`. InnoDB kills one, and the user sees a 500. **Fix:** always lock the contract before the mandate, and map lock failures to 409 (or retry). | `S/mandate/server/service/MandateService.kt:405-406`, `S/contract/server/service/ContractService.kt:161,174,224`, `S/mandate/server/event/MandateEventListener.kt:33-36` |
| C2 | High | **Still present. A photo upload can undo a party's KYC status.** `PartyEntity` has no version and no `@DynamicUpdate`. `uploadPhoto` uploads inside the transaction, then saves the whole row, possibly over a VERIFIED status set in the meantime. Then the dispatcher never offers that agent a mandate. **Fix:** `@Version` on `PartyEntity`, or targeted UPDATEs for `kycStatus` and `photoUrl`, and upload before the transaction. | `S/party/server/service/PartyService.kt:125-150` |
| C3 | Medium | **Still present. A customer can sign a document they never saw.** `SignContractRequest` doesn't identify the content that was signed. **Fix:** send the `contentUrl` (or a hash) the customer saw, and 409 if it changed. | `ndopify-dto/.../SignContractRequest.kt`, `ContractService.kt:224` |
| C4 | Medium | **Still present. `markAssigned` and `onKycVerified` save the whole agent row**, which bumps its version (409 on a profile form loaded before an offer), and an actual collision rolls back the whole dispatch or KYC completion. **Fix:** targeted UPDATEs for `lastAssignedAt` / `joinedAt`. | `S/agent/server/service/AgentService.kt:92-93,212`, `MandateDispatcher.kt:135` |
| C5 | Medium | **Still present. Max-load isn't enforced.** The load doesn't count PENDING offers, and `accept` doesn't check it again. **Fix:** count PENDING offers, and check the load again in `accept` with the agent row locked. | `S/mandate/server/service/MandateDispatcher.kt:72-75,208`, `AssignmentService.kt:84-98` |
| C6 | Low | **Still present. Possible deadlock between `accept` and `cancel`.** **Fix:** `mandateDao.findByIdForUpdate` first in `accept`. | `AssignmentService.kt:84-98`, `MandateService.kt:311-312` |
| C7 | Low | **Still present. The 409 handler only catches `ObjectOptimisticLockingFailureException`**, so pessimistic-lock errors (C1) are 500s. | `S/error/server/endpoint/ControllerErrorHandler.kt:71` |
| C8 | Low | **Still present. A KYC image can change after submission**, and two first uploads of the same image return a 500. | `S/party/server/service/KycService.kt:141-170` |
| C9 | Low | **Still present. Editing a PENDING_APPROVAL mandate leaves the contract out of date.** Same root cause as S4. | `MandateService.kt:191-234` |
| C10 | Low | **Still present. A mandate whose contract expires stays PENDING_APPROVAL**: `ContractExpiredEvent` has no listener. | `S/contract/server/job/ContractJobs.kt` |

`jakarta.transaction.Transactional` is still used in all the transactional server files. It rolls back only on
unchecked exceptions, so a checked `IOException` from storage commits. Switch to Spring's annotation.

## Architecture

| Finding | Status |
|---|---|
| Validation and access live only in Partner Central | **Partly fixed.** Server-side access is done. Mandate content validation (`FindPropertyRules`) and the editable statuses (S4) are still only in Partner Central. `ttl-days` is still copied in Partner Central's `application.yml:65`, and `Contract` has no `requestedAt`. |
| Partner Central couldn't read a mandate's contract | **Fixed.** The agent is now a non-signing party, so the home cards and the mandate page get the contract dates. Contracts created before this change have no agent party; that's fine pre-launch. |
| Missing tenants aren't rejected | **Fixed on the server** (B3 aside). Partner Central still matches tenants with `startsWith` and never refreshes its list (`B/service/TenantContext.kt:26,31-38`). |
| Home page makes ~30 sequential calls | **Still present.** `B/ui/home/HomeController.kt:120,145`. |
| Tenant set-up copied in listeners and jobs | **Still present.** A `TenantScope.run(tenantId) { … }` helper would remove it. |
| Entities point at other domains' entities | **Still present.** `AgentEntity.kt:32`, `CustomerEntity.kt:26`, `MembershipEntity.kt:33` → `PartyEntity`. |
| A1. The `error` domain depends on four domains | **Still present.** `ControllerErrorHandler` imports five entities to name its 409s (`S/error/server/endpoint/ControllerErrorHandler.kt:3-17,78-84`). |
| A2. Access checks are placed in two different ways | **Still present.** |
| A3. The version check is optional | **Still present.** |
| A4. `TenantFormatter` exists in both modules, and they already differ | **Still present.** |

**New:**

- **A5 (Low). The tenant filter needs two configurations to stay apart.** `TenantJpaConfiguration` must never get a
  dependency on a repository or service, or B1 comes back. Its KDoc says so. A test that loads the context (any
  `@SpringBootTest`) would catch a regression, so this is just a note.
- **A6 (Low). The tenant filter depends on Spring MVC's `handlerExceptionResolver` bean** (by `@Qualifier`), so the
  error format of a servlet filter depends on MVC wiring. It's the usual pattern and it's tested; just keep the
  `?: throw e` fallback.

## Code quality and tooling

| Item | Status |
|---|---|
| Duplicate mandates from "Trouver un bien" (double submit) | **Still present.** `B/ui/findproperty/FindPropertyController.kt:235-244` |
| `JoinController` catches only `HttpClientErrorException` | **Still present.** `B/ui/join/JoinController.kt:176` |
| Root POM: Boot overrides, ~40 koki properties, `spring-boot.version` mismatch, `pdfbox.vesion` | **Still present.** |
| No Maven cache in CI | **Still present.** |
| PR coverage gate (90/90) vs POM (98/95 root, 95/90 server) | **Still present.** `ndopify-server-pr.yml:50-51` |
| No branch counter in JaCoCo | **Still present.** |
| JUnit `Assertions` / `assertThrows` | **Still 9 files.** |
| `@Ignore`d tests | **Still 3** (the MTN ones; CI reports 3 skipped). |
| `Thread.sleep` in tests | **Still 6.** |
| `KYC_CASE_NOT_PENDING` returns 400, not 409 | **Still present.** `S/party/server/service/KycService.kt:401` |
| Entities default `createdAt` to `Date()` instead of the `Clock` | **Still present.** |
| Partner Central `application-test/prod.yml` are koki config | **Still present.** Prod has no `ndopify.server.api.base-url`. |
| Join / FindProperty duplicate helpers | **Still present.** |
| Q2. Copied helpers (`MandateOfferMailet` / `MandateContractGenerator`, `errorCode(...)` in 4 controllers) | **Still present.** |
| Renovate's Kotlin 2.4.21 PR | **Fails CI** (the same server tests as `main`, plus its own). Re-run it once `main` is green. |

**Q1. CLAUDE.md is out of date.** Its contract and Partner Central parts were updated in these commits. Still wrong:

- **Party domain:** it names `KycVerifier*`, `IdentificationInfoExtractorAi`, `IdentificationImageEntity` and
  `KIMI_API_KEY`. The code has `KycCaseVerifier*`, `KycInfoExtractor*`, `KycImageEntity` and `GEMINI_API_KEY`.
- **Tenant filter:** it places `TenantContextFilter` in `platform/tenant/servlet` and `TenantConfiguration` in
  `platform/config`, registered at `HIGHEST_PRECEDENCE`. They're in `security/server/servlet` and `config` (plus
  `TenantJpaConfiguration`), with no order (S7).
- It doesn't document the filter's 400s (`TENANT_MISSING_HEADER`, `TENANT_ID_NOT_VALID`, `TENANT_NOT_ACTIVE`), its
  excluded paths, or the 403 for a token that doesn't decode.
- It cites `V7_2` for the version columns, a migration that was folded into earlier ones.
- It says mandate changes run "under the mandate's lock", but `update` uses `findById`.

**Q3. Test gaps:**

- No test sends an expired token to a public endpoint (R1), in either module.
- No endpoint test checks `/actuator/health` without a tenant in the default profile. `ProdProfileEndpointTest`
  catches it for prod.
- `CallerService`, `MandateAccessService`, `ContractAccessService` and `OpenApiConfiguration` have no unit tests.
  They are well covered by `MandateAccessEndpointTest` and `ContractAccessEndpointTest`, which now run green in CI.
- No test updates a CLOSED mandate through the API.

**New, minor:**

- `application-prod.yml`'s KYC cron comment says "Every hourss", and its indentation differs from the lines above
  it.
- `ndopify-dto/pom.xml`: the release plugin rewrote `<scm>` with broken indentation.

## Strengths

- **Fast, targeted fixes for the blockers:** B1 and the B3 error format were each fixed in small, well-commented
  changes, with tests (`TenantContextFilterTest` checks the resolver).
- **Non-signing parties fit the model.** One boolean, enforced in create, the e-mails and sign, and documented.
  It solves Partner Central's contract access without a mandate-specific rule.
- **Partner Central degrades properly:** an uncaught 403 / 404 is an error page, not a 500, with a template test.
- **One access-control vocabulary** (`CallerService`), searches that force the caller's own ids, and the tested
  404 → 403 → 409 order.
- **"Nobody signs on another's behalf"** is implemented and tested, now including non-signing parties.
- **`requestApproval`'s 3-transaction design** and **versioning done properly**.
- **The domain dependencies have no cycle** (apart from `error`, A1).
- **Unchanged since the first review:** clean layering, slow KYC calls outside transactions, async e-mails after
  commit, a gap-free contract sequence per tenant, the injected `Clock`, and the exhaustive `MandateDispatcherTest`.

## Recommended order

Each step is small and easy to undo:

1. **Get `main` green:**
   - **B3:** add `/actuator` to the filter's excluded paths.
   - **B4:** add tenant 2 to `UpdatePartyPhotoEndpoint.sql`.
   - **S7:** put back `HIGHEST_PRECEDENCE`.
2. **R1 (before anyone uses Partner Central for over an hour):** clear the cookie in Partner Central when the token
   doesn't decode, and don't forward an expired one. Answer 401 on the server (S5). Add a test for each side.
3. **C1:** lock the contract before the mandate in `completeApproval`. **C2:** `@Version` or targeted UPDATEs on
   `PartyEntity`.
4. **S1:** sign tokens. Until this ships, the access checks protect nothing.
5. **H1 + H2 + L3:** verify Google ID tokens, and limit and atomically consume one-time codes.
6. **S2 + S3 + S4:** access checks on the remaining endpoints, a reduced view for offered agents, and a status
   check on mandate update.
7. **C4 / C5:** targeted UPDATEs on the agent, and a max-load that counts PENDING offers.
8. **Smaller items:** KYC job oldest-first, Spring `@Transactional`, POM clean-up, and the CLAUDE.md updates (Q1).
