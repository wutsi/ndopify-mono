# Code review: quality, architecture, security (2026-10-23)

Reviewed at commit `84f027d`, 71 commits after the previous review (`90f32c2`,
[2026-10-20](2026-10-20-code-review.md)). **Rescanned at `0ab6347`** (10 commits later, same day) plus the uncommitted
working tree; see *Update at `0ab6347`* just below. Every earlier finding was checked again. New since the previous review:

- the `ndopify-web` module (Spring Boot + Thymeleaf public site, port 8082), which replaced the static `ndopify-website`;
- the public *Proposer un bien* wizard (`OfferPropertyController`) and LISTING mandates (`availableFrom`, listing contract);
- the "improve security" commit (`f495627`), the N+1 fixes, the HTTP / MTN timeouts, the AI health check.

This review comes from reading the code. No load test was run, and no build was run locally. CI on `main` is
**green** at `f495627` for the server, Partner Central and e-signature. Web last passed at `7eac605`. Coverage from the
CI artifacts:

| Module | Line (gate) | Class (gate) | Branch (no gate) |
|---|---|---|---|
| Server | 95.92% (0.95) | 97.6% (0.95) | 88.0% |
| Partner Central | 99.09% (0.98) | 96.9% (0.95) | 87.3% |
| E-signature | 99.15% (0.98) | 96.4% (0.95) | 89.6% |
| Web | 97.62% (0.97) | 87.5% (**0.80**) | 93.8% |

Paths are relative to `modules/`. Short prefixes:
- `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`
- `R` = `ndopify-server/src/main/resources`
- `B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`
- `E` = `ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature`
- `W` = `ndopify-web/src/main/kotlin/com/wutsi/ndopify/web`
- `T` = `ndopify-server/src/test/kotlin/com/wutsi/ndopify`

## Update at `0ab6347`

The 10 commits are `03a85fd` "improve security", `c185ade`, `bd65880`, `6ebf37c`, `3006560` "update endpoints",
`a9c2eec` "sign the jwt", and the DTO `0.0.62` release. They touch authentication only (67 files, about half of them
tests). Nothing in mandate, dispatch, KYC, the front-end wizards or web changed, so every finding not listed here is
**unchanged**. CI on `main` at `0ab6347`: partner-central and e-signature **green**; the server run was still in progress
(`3006560` green).

**Fixed:**
- **S1 (unsigned tokens):** HS256 with `ndopify.security.jwt.secret`, at least 32 bytes or the server doesn't start
  (`S/security/server/service/AccessTokenService.kt:37-41`). `test` and `prod` take `JWT_SECRET` with no default, and
  `ndopify-server-main.yml` sets it on Heroku. Partner Central and e-signature no longer verify with `Algorithm.none()`:
  they decode the claims (expiry and application only, documented in `JwtDecoder`), and a 401 from the server clears
  the cookie (`HttpClientErrorHandler.unauthorized`). This works because each claim they use (`partyId`, the user id)
  is only used to choose what to ask the server, and the server checks the signature again.
- **`anyRequest().permitAll()`:** every endpoint needs a token except `PublicEndpoints`
  (`S/security/server/servlet/PublicEndpoints.kt`): GLOBAL (refdata, Swagger, storage, actuator, `/error`) and TENANT
  (`POST /v1/otp`, `/v1/auth`, `/v1/agents`, `/v1/mandates`, `GET /v1/contracts/*/signatures/*`). The 401 goes through
  `ControllerErrorHandler`.
- **`X-Tenant-ID` trusted:** the header is used only on a TENANT public endpoint without a token. With a token, a
  different header is a 403. Elsewhere without a token, the response is a 401 (`TenantContextFilter.kt:59-73`).
- **N-S2 step (1):** `ensureCanSign` requires an `e-signature` token (`ContractAccessService.kt:48-52`), so a One Tap
  token can no longer sign. Steps (2) and (3) are still open, see below.
- **N-S3 (server):** `ipAddress` is `@Size(max = 45)` and must be an IPv4 / IPv6 address (`InetAddressValidator`, no
  DNS); `userAgent` is `@Size(max = 1024)` (`SignContractRequest.kt`, `ContractService.kt:413-420`).
- **N-A1:** signatures no longer rest on unsigned tokens and `permitAll`.
- **X-P1, mostly:** `ChatClientHealthEndpointGroupsPostProcessor` removes `chatClient` from the default group. The
  default group no longer calls the LLM and no longer goes DOWN when the LLM does. The rest of the finding is below.

**Changed severity or scope:**

| ID | Now | Why |
|---|---|---|
| N-S2 | **High → Medium** | Signing is closed. Still open: One Tap trusts the `sub` the caller sends (`GoogleOneTapAuthenticator.kt:62-68`) and grants `public-portal` (`...OrCreate`, `:70-75`). A `public-portal` token for an agent's e-mail is a real, signed token, and only contract signing and admin checks look at the application. Mandate reads, offer accept / decline and `POST /v1/agents/{id}` don't, so anyone can still act as any agent. **Fix:** remove `GOOGLE_ONE_TAP` from `public-portal` (a migration) until the ID token is verified. Don't do it by disabling OTP (**X-S6**). |
| S2 (open endpoints) | High, **narrower** | They now need a token, but **any** token from the tenant: a customer who signed in to the e-signature portal by OTP, or a One Tap token, can still `POST /v1/agents/{id}` with their own `whatsappNumber`, and read customers, parties, KYC cases and images. The list of endpoints is unchanged. |
| Ops endpoints | Medium, **narrower** | They need a token, but any user's (none checks a role). They are still mapped on the deployed `test` profile (X-P2). **Fix:** `callerService.isAdmin` in each one, as `POST /v1/contracts/{id}/signed-content` already does. |
| X-P1 | **High → Low** | `/actuator/health/llm` is still public (`PublicEndpoints.GLOBAL` has `/actuator/**`), has no cache, and shows the provider's error (`withException`, `show-details: always` outside `prod`). A curl loop on it still runs up the bill. **Fix:** keep the group out of the exposure, or `show-details: when-authorized` plus a role, and cache the result for about 5 minutes. |
| M3 | Medium, **server side now explicit** | `PublicEndpoints.GLOBAL` opens all of `/actuator/**`, and the base `application.yml:196` exposes `beans`, `flyway`, `mappings` and `scheduledtasks`. The deployed `test` profile doesn't narrow this. Before, `permitAll` exposed the same thing implicitly. **Fix:** only `/actuator/health` and `/actuator/info` public, or `exposure.include: health` in the base file. |
| X-Q2 | Medium, **partly fixed** | CLAUDE.md now describes the signed tokens, `PublicEndpoints` and the tenant filter. Still out of date: the static `ndopify-website` (`CLAUDE.md:15`, `:686`), "no `CONTRIBUTING.md`" (`:89`), `KIMI_API_KEY` / `IdentificationInfoExtractorAi` (`:99-100`, `:173`). |

**New:**

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| **X-S6** | **High (uncommitted)** | **The working tree turns off OTP sign-in, not One Tap.** `AuthType.OTP -> null`, with the comment "Not supported until we support OneTap verification in GoogleOneTapAuthenticator", and the test now asserts `null` for OTP. OTP is the **only** sign-in of Partner Central and the e-signature portal, so this change stops every agent from signing in and every customer from signing. Meanwhile `GOOGLE_ONE_TAP -> googleOneTap` stays on: the hole the comment talks about stays open. | `S/security/server/service/AuthenticatorProvider.kt:19`, `T/security/server/service/AuthenticatorProviderTest.kt:29` | Revert the OTP line and its test. If the goal is to disable One Tap, set `AuthType.GOOGLE_ONE_TAP -> null` (and its test), or better, remove it from `public-portal`'s `auth_types` (N-S2). |
| X-S7 | Low | **A token the server rejects blocks sign-in until the cookie expires.** Partner Central's and e-signature's interceptors forward the cookie's token on `POST /v1/otp` and `/v1/auth`. A token that hasn't expired but the server rejects (every token issued before `a9c2eec`, which were `alg: none`, or any token after a `JWT_SECRET` change) gets a 401 even on these public endpoints (`TenantContextFilter.kt:82-90`). `LoginController.kt:90,120` and `SignController.kt:121,162` catch every `HttpClientErrorException`, so the 401 never reaches `HttpClientErrorHandler.unauthorized`. The user sees "Cette adresse email n'est pas valide!" (or OTP_FAILED), and the cookie isn't cleared. Visiting `/` (or the sign link) clears it, so a user can get out of it, but only by accident. | `B/ui/login/LoginController.kt:88-97,108-125`, `E/ui/sign/SignController.kt:119-128,149-166` | Don't send `Authorization` on `/v1/otp` and `/v1/auth`. Or rethrow `HttpClientErrorException.Unauthorized` in those catches. |
| X-S8 | Low | The server's `getPrincipalOrNull` still puts the JWT library's message in a 403 `AUTH_UNAUTHORIZED` (`AccessTokenService.kt:90-102`). It isn't reached today: `JwtAuthenticationFilter` turns a bad token into a 401 first, and `TenantContextFilter` hides the message. It is one refactor away from leaking again (M4). | `S/security/server/service/AccessTokenService.kt:90-102` | Return the 401 here, with no message. |

**Checked and fine in the new code:**
- `JWT.require(HMAC256).withIssuer(...)` checks the signature, `exp` and the issuer. `alg: none` and other algorithms
  are refused (`AccessTokenServiceTest`).
- `JwtAuthenticationFilter` runs before `AnonymousAuthenticationFilter`. `TenantContextFilter` (a plain
  `FilterRegistrationBean`, lowest precedence) runs after Spring Security, so a bad token on a protected endpoint is a
  401 before any tenant lookup.
- E-signature's `unauthorized()` redirects only to `/sign/{id}` or `/verify/...` taken from its own URI, so it isn't an
  open redirect.
- No secret is logged; the dummy secrets are clearly marked and differ between local and QA.
- The tests cover the new behaviour: `AccessControlEndpointTest`, `JwtAuthenticationFilterTest`,
  `TenantContextFilterTest`, the `SignContractEndpointTest` application and IP cases, and the LLM group.
- JUnit assertions: still **10 files**, none of them new.

**Recommended order, updated:** (0) **revert X-S6** before committing; (1) N-S2 step (2): remove One Tap from
`public-portal`; (2) admin checks on the ops endpoints, and M3 / X-P1: narrow `/actuator/**`; (3) X-E1; (4) X-A1 + X-C1;
then the rest of the list below, with S1 struck off "Larger work".

## Summary

**Fixed since the last review:**
- **N-S1:** the verify API no longer returns the signed PDF's link.
- **N-S4:** a signature is bound to the content hash, under the contract's lock.
- **N-S3:** the signer's IP is now `remoteAddr`.
- **C1 / N-C1:** the lock order is contract first, and `cancel` cancels the contract.
- **N-A2:** an hourly sweep rebuilds lost PDFs.
- **P2:** timeouts on every REST client, SMTP and MTN.
- **P3 / P4:** N+1 queries and the home page fan-out.

**Nothing changed in authentication or authorization.** Unsigned tokens, `permitAll`, the unverified One Tap login,
the open endpoints and OTP brute force are all as before.

**Top risks now:**

1. **Anyone can still sign a contract as the customer** (**N-S2**, path corrected). Google One Tap through
   `public-portal` trusts the `sub` the caller sends. The server issues a real token, and `ensureCanSign` never checks
   which application the token was issued for. This is a few lines to close.
2. **The AI health check calls the paid LLM on every `/actuator/health`** (**X-P1**, new). The endpoint is public in
   every profile, prod included. This means:
   - anyone can run up the AI bill;
   - an LLM outage marks the whole server DOWN (503);
   - a probe can hang for minutes.
3. **A mandate can be edited while its contract is out for signature, and the signature still approves it** (**X-A1**,
   new). Under concurrency, the locking query doesn't refresh an entity that's already loaded (**X-C1**). The last
   signature can then be rolled back with a 409, which the sign page shows as "done".
4. **The deployed apps run the `test` profile** (**X-P2**). Ops endpoints, the wide actuator exposure, Swagger and
   Flyway clean are all on there. `prod` was never deployed, and it points at a different LLM (`GEMINI_API_KEY`, which
   no workflow sets).
5. **The copied front-end infrastructure now exists in 3 copies and has started to drift** (**X-A5**). E-signature's
   `TenantAdvice` now throws instead of returning null (**X-E1**, a regression in `f9b0bb9`), so its error pages fail to
   render when the tenant can't be resolved. Web copied that version.

There's no build blocker. **Before real contracts are signed, N-S2, X-A1 / X-C1 and X-P1 should be treated as
blockers.**

## Security

### Known gaps

| Gap | Status at `84f027d` |
|---|---|
| `Algorithm.none()` tokens (S1) | **Still present** in 3 modules: `S/security/server/service/AccessTokenService.kt:87-89`, `B/service/security/JwtDecoder.kt:29`, `E/service/security/JwtDecoder.kt:29`. New `partyId` claim: the server ignores it, but Partner Central's `CurrentParty.id()` (`B/service/security/CurrentParty.kt:24`) trusts it to choose which agent it reads and updates. |
| `anyRequest().permitAll()` | **Still present.** `S/config/SecurityConfiguration.kt:25`. |
| `X-Tenant-ID` trusted | **Partly fixed** (unchanged). Any active tenant id is accepted without a token (`S/security/server/servlet/TenantContextFilter.kt:82-109`). |
| Ops endpoints unauthenticated | **Still present, live on the deployed app.** `R/application.yml:213` (`ops: true`). Only `application-prod.yml:40` turns them off, and prod isn't deployed (X-P2). |
| Signing checks who signs | **Fixed in code** (`S/contract/server/service/ContractAccessService.kt:45-51`, no admin bypass). Bypassed through S1 and N-S2. |

**Endpoints with no access check (unchanged):**
- `GET /v1/assignments(/{id})`
- `GET /v1/users/{id}`
- `GET|POST /v1/agents(/{id})`
- `GET /v1/customers(/{id})`
- `GET /v1/parties`, `POST /v1/parties/{id}/photo`
- `/v1/payment-methods/**`
- `/v1/identifications/**`
- `/v1/kyc/cases/**`, including `/images/{id}/url`
- the ops endpoints `POST /v1/mandates/{id}/dispatch`, `POST /v1/assignments/{id}/expire`, `POST /v1/kyc/cases/{id}/verify`

**A concrete exploit of the open endpoints:** an anonymous `POST /v1/agents/{id}` with an attacker's
`whatsappNumber`. The "mandate assigned" e-mail then gives that number to customers.

### High

| ID | Issue | Where | Fix (smallest first) |
|---|---|---|---|
| **N-S2** (corrected) | **Sign as anyone through One Tap.** The last review's exploit path was wrong: `e-signature` only accepts OTP (`R/db/migration/common/V1_3__refdata-application.sql:29-32`). The real path is `public-portal` (`PASSWORD,GOOGLE_ONE_TAP`). Exploit: `POST /v1/auth {authType: GOOGLE_ONE_TAP, applicationCode: "public-portal", email: <customer>, googleOneTapPayload: {sub: "x"}}`. The server checks nothing in the payload, overwrites the stored `sub`, and grants the application (`...OrCreate`). The token it returns is accepted by `POST /v1/contracts/{id}/sign`, because `ensureCanSign` doesn't check `getApplication()`. The same token works for an agent's e-mail (read mandates, accept offers). | `S/security/server/service/authenticator/GoogleOneTapAuthenticator.kt:62-75`, `S/security/server/service/AuthFactorService.kt:59-66`, `S/security/server/service/UserApplicationService.kt:19-31`, `ContractAccessService.kt:45-51` | (1) `ensureCanSign` requires `principal.getApplication() == E_SIGNATURE`. (2) Remove `GOOGLE_ONE_TAP` from `public-portal` until the ID token is verified (`GoogleIdTokenVerifier`, `aud` = client id). (3) Use `findByMembershipAndApplicationOrNull` in place of `...OrCreate`. |
| **X-P1** (new) | **The health check calls the paid LLM** (`client.prompt().user("Hi").call()`) on every `/actuator/health`. The endpoint is public in every profile, prod included (`show-details: never` hides the details but still runs every indicator). Effects: a curl loop runs up the bill or exhausts the quota, which breaks KYC extraction; an LLM outage turns the whole server DOWN (503); each probe can hold a thread for up to about 4 minutes (60 s timeout × 4 attempts). On the test app, `withException` also shows the provider's error text. The mail health check (SMTP login) and `S3HealthIndicator` also run on every probe. | `S/platform/ai/ChatClientHealthIndicator.kt:17-33`, `S/config/platform/AIConfiguration.kt:14-15` | Drop the bean, or keep it out of the default health group (`management.endpoint.health.group.*`). If it must stay, cache the result for about 5 minutes and drop `withException`. Consider `management.health.mail.enabled=false`. |
| S1, S2, H1, H2, H4, H5 | **Still present** (see the previous review). Updates: <br>• **H2:** the caller chooses the OTP `ttl` (60–600 s), and each new request resets the code. <br>• **H5:** KYC ids are UUIDs, so the risk comes from the open `GET /v1/kyc/cases?party-id=` endpoint (sequential party ids), not from guessing storage keys. | `AbstractPasswordAuthenticator.kt:56-71`, `KycInfoExtractorIdentification.kt:68-79`, `MandateService.kt:452-457` | As before. |

### Medium

| ID | Issue | Where | Fix |
|---|---|---|---|
| X-B1 (new) | **The public *Proposer un bien* wizard can be abused by a script:** no captcha, no rate limit, no e-mail check. Each run creates a party, a customer and a LISTING mandate, which sends offer e-mails to real agents. The e-mail address the script chooses later receives "mandate assigned" mail. Each run also opens a session. *Trouver un bien* has the same problem (L10). | `B/ui/offerproperty/OfferPropertyController.kt:272-297` | Turnstile / hCaptcha plus a honeypot on both review POSTs, a per-IP limit, then a rate limit on `POST /v1/mandates`. |
| M3 | **Actuator exposure.** Partner Central's `/actuator/**` falls under `authenticated()`, so any signed-in (or forged, S1) agent reads `beans`, `mappings` and health details on the test profile. | `ndopify-partner-central/.../application.yml:53`, `B/config/SecurityConfiguration.kt:32`, `R/application.yml:186,193` | Expose only `health` in the base `application.yml`. |
| M5 | **CSRF is off.** The Lax token cookie protects signed-in POSTs. Not covered: **login CSRF**, since `JSESSIONID` has no SameSite (a cross-site page can sign the victim in as the attacker, and KYC photos then go to the attacker's case), and `GET /logout`. | `B/config/SecurityConfiguration.kt:36`, `E/config/SecurityConfiguration.kt:31`, `B/ui/logout/LogoutController.kt:20` | Turn CSRF on (`th:action` adds the token by itself). Make logout a POST. Set `server.servlet.session.cookie.same-site: lax`. |
| N-S5 | **Partly fixed.** Timeouts were added. Still missing: a host allowlist and a size cap on the contract content download (`toEntity(ByteArray)`, out-of-memory risk). The safelist allows `style` and remote `img`, and there's no CSP. | `E/client/ContractContentClient.kt:29,38` | Allowlist the storage host, cap the body size, add a CSP (X-E3). |
| N-S3 (server) | The server still stores any `ipAddress` / `userAgent` it's given. Over 45 characters, the IP overflows `VARCHAR(45)` and causes a 500. | `ContractService.kt:282-283`, `V4_0__contract.sql:43` | Validate the length and format. |
| N-S6, M1, M2, M4, S3, S4 | **Still present.** New M4 instance: the lock-failure handler returns Hibernate's message, including the SQL, in a 409. | `ControllerErrorHandler.kt:54,89,104`, `OtpService.kt:42-48`, `KycService.kt:155-157` | `message = null` for 5xx and lock errors. |

### Low

| ID | Issue | Where |
|---|---|---|
| X-S2 (new) | Every contract party (including the mandate's agent) gets the other parties' IP address, user agent and `legalEmail`. | `ContractMapper.kt:66-67` |
| X-S3 (new) | An offered agent can still read the mandate after `expiresAt`, until the 15-minute expiry job runs. | `MandateAccessService.kt:34-35`, `MandateService.kt:101-111` |
| X-S4 (new) | `POST /v1/agents` with a customer's e-mail reuses that party, grants it `partner-central`, and sets the attacker's WhatsApp and mobile-money numbers on it. | `AgentService.kt:109-123`, `CustomerService.kt:59-70` |
| X-S5 (new) | `DatabaseResetConfiguration` (`flyway.clean()` at boot, `@Profile("none")`) is in main code, and the test app allows clean. | `S/config/DatabaseResetConfiguration.kt:10-19`, `application-test.yml:11` |
| X-B2 (new) | Two concurrent review POSTs both read `PendingListing`, so two mandates are created. | `OfferPropertyController.kt:274-287` |
| X-B3 (new) | `JoinRules` NAME / EMAIL have no length cap, and they are the only validation a listing gets. | `B/ui/join/JoinRules.kt:14-15` |
| X-B4 / N-S10 / N-Q7 | Response bodies (which can contain an e-mail) logged at ERROR. The sign link's secret (`contract-party-id`) is logged in request URIs. | `OfferPropertyController.kt:282`, `FindPropertyController.kt:240`, `E/client/LoggingClientHttpRequestInterceptor.kt:29` |
| X-E2 (new) | A temporary 409 (`*_VERSION_CONFLICT`, `HTTP_VERSION_CONFLICT`) counts as "settled", so the signer sees the sign step again with no message and thinks they signed. This is made worse by X-C1. | `E/ui/sign/SignController.kt:82,216` |
| X-E3 (new) | No CSP or `Referrer-Policy` header in any front-end app. This matters most in e-signature, which renders `th:utext`. | `*/config/SecurityConfiguration.kt` |
| L4 | No session id rotation on login. | `B/ui/login/LoginController.kt:127`, `E/ui/sign/SignController.kt:169` |
| N-S9 / X-W7 | Workflows: `${{secrets.NDOPIFY_SERVER_URL}}` inline in `run:`, unpinned `curl … \| sh`, `contents: write` for the whole workflow. Now copied into the web workflow. | `.github/workflows/ndopify-{partner-central,esignature,web}-main.yml:13,87,92` |
| L2, L5, L6, L8, L9, L10, S6, S7 | Still present (L9: versions are pinned, but there's no SRI). | see the previous review |

### Checked and fine

- **Signing:** a null content hash is refused; each signature has a random 16-byte salt; no salt or hash appears in any
  DTO; the verify lookup gives the same 404 for every failure; the PDF re-checks the content hash and uses random file
  names.
- **Templates:** no `{{{` in mail or contract templates. No `th:utext` except the sanitized contract. No `innerHTML` /
  `eval` in the JS enhancers. Web's JSON-LD is escaped.
- **SQL and secrets:** native SQL is parameterized; no secrets are committed; no CORS is configured.
- **Ids:** agent and party ids come from the token or the session, never from forms. Redirects are built from constants.
- **Front-end apps:** e-signature and web `denyAll` everything they don't serve. Token cookies are `HttpOnly`,
  `SameSite=Lax`, and now `Secure` behind the proxy.

## Concurrency and transactions

### Earlier findings

| ID | Status |
|---|---|
| C1 | **Fixed.** `completeApproval` and `cancel` lock the contract first (`MandateService.kt:360-363,467-468`). |
| N-C1 | **Fixed** (`ContractService.kt:330-337`). |
| N-C2 | **Partly. The fix doesn't work**, see X-C1. |
| N-C4 | **Mostly fixed:** a sweep job and a bounded executor. The rejection policy is still the default (X-C2). |
| C6 | **Fixed** by `@Version`. |
| C7 | **Partly:** lock errors are a 409; `DataIntegrityViolationException` is still a 500. |
| C4 | **Partly:** no lost update anymore, but dispatch's `markAssigned` causes spurious `AGENT_VERSION_CONFLICT` (X-A3). |
| C2 | **Worse:** the S3 upload now sits between the read and the full-row save (X-C4). |
| N-C3, C5, C9, C10, N-C5, N-C6, N-C7, L3 | **Still present.** N-C7 is now in 3 copies (`*/service/TenantContext.kt:18-38`). |
| No cluster lock | Still present, now **Low**: the jobs turned out mostly idempotent. The exceptions are duplicate PDFs (N-C5) and X-C3. |

### New

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| **X-A1** | **High** | **A PENDING_APPROVAL mandate can be edited while its contract is out for signature, and the signature still approves it.** `update` takes no lock and checks no status (a CLOSED or CANCELLED mandate can be edited too). `onContractSigned` doesn't compare the mandate with the version the contract was generated from. The customer signs version N; the mandate becomes APPROVED with version N+1's terms. | `MandateService.kt:207-251,284-301` | In `update`: lock, allow only ASSIGNED / PENDING_APPROVAL, and for PENDING_APPROVAL cancel the contract (contract first) and go back to ASSIGNED. Alternative: store the mandate version on the contract and refuse approval when it differs. |
| **X-C1** | **Medium** | **The lock doesn't refresh an entity that's already loaded.** `onContractSigned` loads the mandate (`findByContractId`), then calls `findByIdForUpdate`, a locking JPQL query. Hibernate takes the row lock but returns the instance it already has, without refreshing it. So the status and `version` it checks are the old values. An agent's edit that commits while the signature waits makes the signature's save fail with an optimistic lock error, and the **last signature is rolled back** (409, shown as "done", X-E2). In `cancel`, the "linked meanwhile" branch (`:366-369`) can never be true. | `MandateService.kt:285-290,360-369`, `MandateRepository.kt:22-26` | Lock before loading anything: select the id by `contractId`, then `findByIdForUpdate`; in `cancel`, call `entityManager.refresh(mandate, PESSIMISTIC_WRITE)`. |
| X-C2 | Medium | The async executor keeps `AbortPolicy` with a queue of 100. In a burst (for example `MandateJobs.expire` → 100 dispatches → 100 offer e-mails), or during shutdown, tasks are rejected and the e-mails are lost. Only the PDF and dispatch have recovery jobs. | `R/application.yml:95-112` | `CallerRunsPolicy` through a `ThreadPoolTaskExecutorCustomizer`; an outbox later. |
| X-C3 | Low | KYC `complete()` doesn't check that the case is still IN_PROGRESS. A verification longer than 15 minutes gets recovered and run again on another instance: duplicate e-mails, LLM cost paid twice. | `KycService.kt:300-307` | Lock and require IN_PROGRESS. |
| X-C4 | Low | `uploadPhoto` uploads up to 5 MB to S3 inside `@Transactional`, holding a pooled connection and widening C2. | `PartyService.kt:124-140` | Upload first, then save only `photoUrl`. |

## Performance and production readiness

| ID | Status / issue |
|---|---|
| P1 | **Partly fixed.** The pool is 8 (16 in test/prod), but `open-in-view` is still on and `send*Email` holds a connection during SMTP (`R/application.yml:33,35,215`). |
| P2 | **Mostly fixed.** Remaining: AI runs on Spring AI's defaults (60 s, 3 retries), S3 on the SDK v1 defaults, and `ContractContentClient` has no size cap. |
| P3, P4 | **Fixed** (`default_batch_fetch_size: 50`; the home page makes about 7 sequential calls, no longer growing with the number of mandates). |
| P5–P10 | **Still present.** P5 is worse: `UPPER(country)` also makes `I_LOCATION_country` unusable (`LocationService.kt:81,93`). |
| **X-P2** (Medium) | **The deployed apps run `SPRING_PROFILES_ACTIVE=test`** (`.github/workflows/ndopify-server-main.yml:116`), so the prod hardening never applies: ops endpoints, actuator `beans` / `mappings`, Swagger, and `flyway.clean-disabled: false`. `prod` falls back to Gemini (`GEMINI_API_KEY`, set by no workflow) while `test` runs Kimi: expect prod to fail at startup, or to run a model that was never tested. **Fix:** deploy with `prod` (or `test,prod`), and set the AI key per profile. |
| X-P3 (Low) | Heroku kills the dyno 30 s after SIGTERM, but shutdown can wait 30 s (requests) + 30 s (async) + 30 s (jobs). In-flight e-mails are lost. Keep the total under about 25 s. |
| X-P4 (Low) | Jobs never install a `DefaultKVLogger`, so the dispatcher's and KYC's log fields are lost for job-driven runs (`ContractJobs.kt:38-52`, `MandateJobs.kt:31-45`, `KycJobs.kt:28-40`). Set and flush one in `forEachTenant`. |
| X-P5 (Low) | `T_MANDATE` has no `(status, created_at)` index for `findIdsWithoutAssignment` (`V7_0__mandate.sql:68-70`). It's fine at today's volume. |

**Checked and fine:**
- All 8 async listeners set and clean `TenantContext` and `KVLogger` and never rethrow.
- No path locks the mandate before the contract.
- No row lock is held during S3, SMTP, HTTP or LLM calls.
- Multi-step flows use `TransactionTemplate`, so no proxy bypass.
- Jobs run in batches of 100, with one transaction per item.
- Search limits are capped at 100.
- Indexes cover the assignment, contract and membership lookups.

## Architecture

### Earlier findings

| ID | Status |
|---|---|
| N-A1 (signatures rest on unsigned tokens and `permitAll`) | **Still present.** |
| N-A2 | **Fixed** (`ContractJobs.kt:33-36`). |
| N-A3 (mandate hashes and stores the contract content) | **Still present** (`MandateService.kt:272-275,452-457`). |
| N-A4 / X-A5 (copied front-end infrastructure) | **Worse**, see X-A5. |
| N-A5 (koki leftovers) | **Still present, and copied into web.** `S/platform/logger/servlet/KVLoggerFilter.kt:1` is in package `com.wutsi.koki…`; the `koki` repository is in 3 POMs. |
| N-A8 (DTO release churn) | **Worse**, see X-Q1. 3 more DTO releases. The DTO is at `0.0.62-SNAPSHOT`, and every consumer pins `0.0.61`. |
| N-A9, N-A10, A1–A4, S4 | **Still present.** N-A10: 11 mail and 2 contract templates, none using `mj-include`. A1: `error` is still the only package cycle. |

### Domain dependencies (`ndopify-server`)

Rows import columns:

| ↓ imports → | refdata | security | party | agent | customer | contract | mandate |
|---|---|---|---|---|---|---|---|
| security | service | – | domain, mapper | | | | |
| party | service | | – | | | | |
| agent | service | service | domain, service, mapper, event | – | | | |
| customer | | | domain, service, mapper | | – | | |
| contract | service | service | service | | | – | |
| mandate | service | service | | domain, service | domain | domain, service, mapper, event | – |
| error | | | domain | domain | | domain | domain |

What the matrix shows:
- **The domain direction holds.** No domain uses another's `dao`, no endpoint uses a DAO, contract doesn't import
  mandate, and party doesn't import agent.
- **The only cycle is `error`** (A1).
- **`PartyEntity` is the shared entity** of agent, customer and security.
- **Mandate's mailets take other domains' entities**: `AgentEntity`, `CustomerEntity`, `ContractEntity`.

### New

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| X-A1 | High | See *Concurrency*: edits of a mandate whose contract is out for signature. | | |
| X-A5 | Medium | **3 copies of the front-end infrastructure, now behaving differently.** `TenantContext`, `NdTenantClient`, `ClockConfiguration`, `RestConfiguration` and `SecurityConfiguration` exist ×3; JWT, interceptors, `AccessTokenService`, `HttpClientErrorHandler` and `TenantFormatter` ×2 (×3 with the server's); `errorCode()` ×7; JS `pageshow` / busy code ×6. Concrete regression: **X-E1** below. | `*/service/`, `*/config/`, `*/client/` | A small `ndopify-bff-core` with only the stable, non-policy pieces: `TenantContext(selector)` with refresh and an exact host match, `NdTenantClient`, the interceptors, `JwtDecoder` / `AccessTokenService` / filter parameterized by cookie name, `TenantFormatter`. `SecurityConfiguration`, `TenantAdvice` and the error handlers stay per app. Do it together with S1 (same files) and X-Q1 (reactor CI). |
| X-E1 | Medium | **E-signature `TenantAdvice` throws instead of returning null** (changed in `f9b0bb9`). The KDoc, CLAUDE.md and the error templates (`tenant?.logoUrl`) all assume null. If the host is unknown or ndopify-server is down, every 403/404/error page becomes Tomcat's bare 500. The test was changed to JUnit `assertThrows`, which breaks the kotlin.test rule. Web copied the throwing version (X-W4). | `E/ui/layout/TenantAdvice.kt:16`, `ndopify-esignature/src/test/.../ui/layout/TenantAdviceTest.kt:8,26`, `W/ui/layout/TenantAdvice.kt:15` | Return `Tenant?`, catching and returning null. Restore `assertNull`. |
| X-A2 | Medium | **`MandateEntity` is one type for two concepts** (SEARCH: criteria and a budget; LISTING: a property, an asking rent and `availableFrom`), with about 40 flat fields, and **the server validates none of them**. A listing's unvalidated values now go into a contract that gets signed. | `MandateEntity.kt:43-94`, `MandateService.kt:133-191` | Don't split the aggregate yet. Add server-side `MandateRules.validate(type, request)` (a port of `FindPropertyRules` / `OfferPropertyRules`) in `create` and `update`. |
| X-A3 | Medium | **Dispatch writes agent state.** `markAssigned` saves `lastAssignedAt` and bumps the agent's `@Version` on every offer, so an agent editing their profile gets a 409. | `MandateDispatcher.kt:135`, `AgentService.kt:92-93` | Derive rotation from `MAX(T_ASSIGNMENT.created_at)` (one grouped query, like the existing load query). |
| X-A9 | Medium | **Public wizards live in the agent app.** `/find-property` and `/offer-property` are in Partner Central, while `ndopify-web` (the public site) only links to them. Their abuse (X-B1) and session growth hit the agents' app. | `B/config/SecurityConfiguration.kt:29-30` | Plan to move them to `ndopify-web` once the shared core (X-A5) exists. |
| X-A4 | Low | Legal contracts are rendered with the mail toolchain (`MailBodyResolver`, MJML, CSS inlining), and their labels are a third copy of the label maps. A change to e-mail styling silently changes signed documents. | `MandateContractGenerator.kt:13,31,34-97` | A `DocumentRenderer` shared by mail and contracts, plus one snapshot test per contract template. |
| X-A6 | Low | `ContractType.SEARCH` / `LISTING` copy mandate concepts into the contract domain. | `ndopify-dto/.../contract/dto/ContractType.kt:5-6` | Rename them now (no deployed data): `SEARCH_MANDATE` / `LISTING_MANDATE`. |
| God class | Low | `MandateService` is 491 lines with 13 collaborators: lifecycle, the 3-step approval saga, contract creation, content hashing and storage, the reaction to signatures, e-mail. | `MandateService.kt` | Extract `MandateApprovalService` (about 150 lines), and move hashing and storage to `ContractService.uploadContent` (N-A3). |

**Strengths:**
- The listing contracts were added without the contract domain learning about mandates.
- `@TenantId` covers all 12 tenant-scoped entities, and the two exceptions are deliberate.
- The lock order is applied consistently.
- Updates use `copy()` with `@Version`, and events carry ids only.

## Code quality and tooling

### Earlier findings

| ID | Status |
|---|---|
| N-Q1 (POM properties override Boot) | **Still present.** `flyway.version` 10.18.0 and `spring-boot.version` 4.0.3 against a 4.1.2 parent, plus pinned `slf4j`, `thymeleaf`, `jackson` and `spring-security`; `pdfbox.vesion` is misspelled (`pom.xml:48-133`). CI logs 401s from Flyway's extra repositories. |
| JUnit assertions | **Worse: 10 files** (9 in the server, plus `TenantAdviceTest` in e-signature). |
| N-Q4 (no context-load test) | **Still present, now 3 apps.** No `@SpringBootTest` in Partner Central, e-signature or web, and `config/` is excluded from coverage, so no permit/deny list is tested. |
| N-Q5 (no JS tests) | **Worse:** about 3,080 lines of JS. The offer-property rules exist in both Kotlin and JS. |
| Q-flaky, `Thread.sleep` ×7, `@Ignore` ×3, Q2, Q3, N-Q2, N-Q3, N-Q6, N-Q7, N-Q8, `errorCode` copies (now 7) | **Still present.** |
| No Maven cache in CI | **Still present**, now with a visible cost: a Maven Central 502 failed Renovate #28. |
| N-Q9 | **Moot** (the crons are clear now). |

### New

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| X-Q1 | Medium | **A DTO change breaks main.** Each module workflow builds alone against the released DTO. After `7e2d5f6` (`0.0.59-SNAPSHOT`), main failed until the DTO release (run `39703927322`). The DTO PR workflow doesn't rebuild the modules that use it. | `.github/workflows/*-main.yml`, `*-pr.yml` | Build with `mvn -pl modules/<m> -am` against the reactor's DTO. Publish only when an outside consumer appears. |
| X-Q2 | Medium | **Docs out of date.** <br>• CLAUDE.md still describes a static `ndopify-website` (S3, not in the reactor); `ndopify-web` is a Spring Boot module in the reactor. <br>• Old names: `KycVerifier*`, `IdentificationInfoExtractorAi`, `IdentificationImageEntity`, `KIMI_API_KEY`, the tenant filter in `platform/tenant/servlet` at `HIGHEST_PRECEDENCE`, `T_USER.tenant_id`. <br>• The e-signature `TenantAdvice` sentence (X-E1). <br>• "No CONTRIBUTING.md" (there is one). | `CLAUDE.md:7,15-16,89,97-98,168-171,488,555-563,665`, `README.md`, `CONTRIBUTING.md:38,44,101` | Targeted edits. |
| X-Q3 | Medium | `select2-java-selenium` is at **compile** scope in Partner Central and web, and nothing imports it, so Selenium ships in the runtime jars. | `ndopify-partner-central/pom.xml:118`, `ndopify-web/pom.xml:128` | Remove it, or make it test scope. |
| X-W1 | Low | Web README: badges point at `ndopify-website-{main,pr}.yml`, which don't exist; no JaCoCo badge although `.github/badges/ndopify-web-jacoco.svg` is generated; heading `# Ndopify Website`. | `ndopify-web/README.md:1-4` | A targeted edit. |
| X-W3 | Low | The web POM is a copy of Partner Central's. Unused dependencies: java-jwt, libphonenumber, commons-net, aspectj, commons-text, tika-core, bcprov, selenium, jsoup. Also the `koki` repository, `@EnableAspectJAutoProxy` / `@ServletComponentScan`, and leftover `application.yml` keys. | `ndopify-web/pom.xml:22-150`, `W/Application.kt:9-10` | Prune them. |
| X-W4 | Low | Web: an unknown host, or ndopify-server down, gives a 500 on every page. No error templates. An empty tenant list is cached until restart. Unchecked `as Tenant` casts ×3. | `W/ui/layout/TenantAdvice.kt:15`, `W/service/TenantContext.kt:33-38`, `W/ui/*/…Controller.kt:24` | Nullable advice, `error/404.html` and `5xx.html`, an expiring cache. |
| X-W5 | Low | Web has no `server.forward-headers-strategy`. Tenant matching compares `https://` URLs, and works only because Boot detects Heroku. | `ndopify-web/src/main/resources/application.yml:20` | `native`, as in the other two apps. |
| X-W6 | Low | Web's class gate was lowered to 0.80 with no reason given (the root is 0.95). Its PR workflow has never run. | `ndopify-web/pom.xml:31-32` | Restore 0.95, or document why not. |
| X-Q4 | Low | `denyAll` in e-signature and web also blocks `/actuator/health`, so their health config is dead and they can't be probed. | `E/config/SecurityConfiguration.kt:24-28`, `W/config/SecurityConfiguration.kt:18-21` | Permit `/actuator/health`. |
| X-Q5 | Low | Server dependencies: `pdfbox` is compile scope but test-only; `tika-langdetect`, `commons-validator` and `thymeleaf(-spring6)` are unused; AWS SDK v1 is past end of support; `mockitokotlin2` 2.2.0 (2019) is in 479 imports. | `ndopify-server/pom.xml:86-105,195-205`, `pom.xml:180-184` | Scope or remove them. Switch to `org.mockito.kotlin` (a sed of the imports). |
| X-Q6 | Low | Renovate has no rules (`config:base` plus automerge). It keeps reopening #56 (flying-saucer 10, which needs Java 21) and bumps the properties that override Boot. #28 (Kotlin 2.4.21) is blocked on 5 smart-cast errors. | `renovate.json` | `packageRules`: flying-saucer `<10`; drop the Boot-managed properties (N-Q1). Fix the 5 smart casts. |
| X-Q7 | Low | `ControllerErrorHandler` is in package `error.endpoint` but its file is in `error/server/endpoint`. It's also the server's largest coverage gap (56 of 148 lines missed). | `S/error/server/endpoint/ControllerErrorHandler.kt` | Fix the package; test the handlers. |

**Conventions that are clean:**
- No `@MockBean`.
- No hard-coded date pattern or "FCFA" outside `TenantFormatter` and the seed data.
- All mailets are in a `mail/` package, and all 26 entity enums have a converter.
- No `.DS_Store` or `target/` in git; no `println` in main code.
- The new code ships with tests (web: 39 tests at 97.6% line coverage; listing contract; offer-property; the AI health
  check; `GenerateSignedContentJobTest` uses `UTC_TIMESTAMP()`).

## Recommended order

Small and easy to undo first:

1. **N-S2:** check the application in `ensureCanSign`, and remove One Tap from `public-portal`. A few lines, closes
   signing as someone else.
2. **X-P1:** drop the LLM health indicator, or keep it out of the default group.
3. **X-E1:** revert e-signature's `TenantAdvice` to nullable (and web's copy), and restore the `kotlin.test` assertion.
4. **X-A1 + X-C1:** lock before loading in `onContractSigned`, `cancel` and `update`. In `update`, check the status, and
   invalidate the pending contract for PENDING_APPROVAL. Treat `*_VERSION_CONFLICT` as a failed signature (X-E2).
5. **X-P2 / M3:** expose only `health` in every base `application.yml`; turn ops endpoints off for the deployed app, or
   deploy `prod`; fix the AI key per profile.
6. **X-Q1:** reactor builds (`-pl … -am`) in CI, plus `cache: maven`.
7. **M5 + X-B1:** CSRF, logout as a POST, a session cookie with SameSite; captcha and rate limit on the public wizards.
8. **X-C2:** `CallerRunsPolicy`.
9. **X-A2:** server-side mandate validation.
10. **Docs and housekeeping:** X-Q2 / X-W1 (CLAUDE.md, READMEs), X-Q3 / X-W3 / X-Q5 (POMs), N-Q1 / X-Q6 (Boot
    overrides, Renovate rules).
11. **Larger work:**
    - S1: signed tokens, in all three modules;
    - X-A5: a shared front-end core, done together with S1;
    - S2: access checks on the open endpoints;
    - H2: OTP attempt limits.
