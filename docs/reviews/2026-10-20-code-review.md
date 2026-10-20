# Code review: architecture, performance, security, quality (2026-10-20)

Reviewed at commit `90f32c2`, 113 commits after the previous review (`f4a7c98`,
[2026-10-18](2026-10-18-code-review.md)). Every earlier finding was checked again; its status is given below.
New since then:

- the `ndopify-esignature` module (sign page, public verify page);
- contract signing and the signed PDF (`ContractSigner`/`ContractHasher`/`SignedContract*`, `GET /v1/contracts/{id}/signatures/{hash}`);
- the R1 fix (expired tokens);
- Partner Central changes (rooms / LAND rules, login double-submit guard);
- the mail changes (`JavaMailSender`, layout);
- the Heroku deployment workflows.

**Performance** is reviewed for the first time.

This review comes from reading the code; no load test was run. CI on `main` is **green** for the server
(`39426073068`), Partner Central (`39425412120`) and e-signature (`39413313662`). Workflows run `mvn -q`, so test
counts only show on failure. The last one that printed them (`39419336935`, `e0af8d8`) showed the server at 995
tests, 2 failures (fixed since, one is flaky: see Q-flaky) and 3 skipped. Coverage from the CI artifacts:

| Module | Line | Branch |
|---|---|---|
| Server | 95.40% | 87.9% |
| Partner Central | 99.12% | 88.1% |
| E-signature | 99.14% | 88.9% |

**Summary:**

- **Fixed since the last review:** all four blockers, R1 (on both sides), S5, L7, the Flyway-clean-in-prod
  setting, `MailService` going around `JavaMailSender`, and the deploy workflow's secrets in the server.
- **The e-signature module is well built.** It depends only on the DTO, uses `denyAll` for everything it doesn't
  serve, has a verify page with no session and no JS, sanitizes content, and is at 99% coverage. The signature hash
  has a salt, is bound to the content hash, and the PDF is rendered offline.
- **But signatures now carry legal weight and rest on the weakest parts of the system.** Main risks:
  1. **Anyone can obtain a valid token for a customer and sign in their name** through the unverified Google One
     Tap login (**N-S2**). This works even once tokens are signed. With unsigned tokens (S1), every access check is
     still forgeable.
  2. **The public verify API returns the URL of the whole signed PDF** (**N-S1**). That's exactly what the verify
     page was designed never to show, and the PDF sits in public storage.
  3. **Lock order: a signature can deadlock or be rolled back by an edit of the mandate** (C1 / N-C2). A cancelled
     mandate's contract also stays signable (N-C1).
  4. **Production readiness:**
     - a 4-connection DB pool with open-in-view on (P1);
     - no timeouts anywhere: SMTP, REST clients, AI (P2);
     - N+1 queries (P3);
     - a signed PDF that can be lost on restart, with no recovery in prod (N-C4).

Paths are relative to `modules/`. `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`,
`B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`,
`E` = `ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature`,
`T` = `ndopify-server/src/test/kotlin/com/wutsi/ndopify`.

## Blockers from the previous review

| ID | Status |
|---|---|
| B1 | **Fixed** (unchanged). |
| B2 | **Fixed.** `ndopify-dto.version` = `0.0.58` (released; HEAD's DTO sources match the tag). The cost is visible: 7 DTO releases and ~14 SNAPSHOT/release flips in 113 commits, and the "sign contract" runs failed on an unpublished `0.0.56-SNAPSHOT` (see N-A8). |
| B3 | **Fixed.** `/actuator` is excluded from the tenant filter (`S/security/server/servlet/TenantContextFilter.kt:53`). |
| B4 | **Fixed.** Tenant 2 is in `UpdatePartyPhotoEndpoint.sql:4`. |

There's no new build blocker. Before e-signature is used for real contracts, N-S1 and N-S2 should be treated as
blockers.

## Security

### Known gaps

| Gap | Status at `90f32c2` |
|---|---|
| `Algorithm.none()` tokens | **Still present, now in 3 modules.** `S/security/server/service/AccessTokenService.kt:82`, `B/service/security/JwtDecoder.kt:29`, `E/service/security/JwtDecoder.kt:29`. |
| `anyRequest().permitAll()` | **Still present.** `S/config/SecurityConfiguration.kt:25`. |
| `X-Tenant-ID` trusted | **Partly fixed** (unchanged). Any active tenant id is accepted without a token. |
| Ops endpoints unauthenticated | **Still present, and live on the public test app.** Off in prod (`application-prod.yml:40`). But the only deployed app runs the `test` profile (`ndopify-server-main.yml`, `SPRING_PROFILES_ACTIVE=test`), which inherits `ndopify.endpoints.ops: true` (`application.yml:191`). New ops endpoint: `POST /v1/contracts/{id}/signed-content` (also admin-checked). |
| Signing doesn't check who signs | **Fixed in code** (`S/contract/server/service/ContractAccessService.kt:45-51`), no admin bypass. Bypassable through S1 and N-S2. |
| Partner Central mandate ownership | **Fixed** (unchanged). |

**Endpoints with no access check at all (unchanged):**

- `GET /v1/assignments(/{id})`
- `GET /v1/users/{id}`
- `GET|POST /v1/agents(/{id})`
- `GET /v1/customers(/{id})`
- `GET /v1/parties`, `POST /v1/parties/{id}/photo`
- `/v1/payment-methods/**`
- `/v1/identifications/**`
- `/v1/kyc/cases/**`, including the image URLs

**New public endpoint:** `GET /v1/contracts/{id}/signatures/{hash}` (see N-S1).

### High

| ID | Issue | Where |
|---|---|---|
| N-S1 (new) | **The public signature API leaks the whole signed contract.** `GET /v1/contracts/{id}/signatures/{hash}` needs only `X-Tenant-ID`, a small integer. It returns `ContractSignature.signedContentUrl`. The contract id and hash are in the QR code on every page's signature block, and the server is a public Heroku app. So anyone with a photo of one page can download the whole PDF from public storage: names, e-mails, prices. This defeats the rule in CLAUDE.md and `VerifyController` ("no link to the signed PDF: whoever has a QR code … would get the whole contract"). The original HTML (`MandateService.kt:429-432`) is public and permanent too. **Fix:** (1) remove `signedContentUrl` from `ContractSignature` (DTO release first); (2) store contents and signed PDFs under a private prefix, served by short-lived presigned URLs to the contract's parties after `ensureCanRead`. | `S/contract/server/mapper/ContractMapper.kt:49-56`, `ContractEndpoints.kt:61-69`, `ndopify-dto/.../ContractSignature.kt:10`, `SignedContractService.kt:82` |
| N-S2 (new) | **Anyone can sign as a customer through Google One Tap.** `GoogleOneTapAuthenticator` takes the client's e-mail and `sub` as given (H1). `checkApplicationAccess` grants any application without a stored grant (`findByMembershipAndApplicationOrCreate`), `e-signature` included. Every customer asked to sign has a membership (`ContractSignerService`). So `POST /v1/auth` with `authType=GOOGLE_ONE_TAP`, `applicationCode=e-signature`, the customer's e-mail and any `sub` returns a **valid token minted by the server**, and `POST /v1/contracts/{id}/sign` records a signature in their name. Signing tokens (S1) doesn't stop this. **Fix:** verify the ID token with `GoogleIdTokenVerifier` (`aud` = client id); until then, disable One Tap, or at least refuse it for `e-signature`; never create application access on sign-in. | `S/security/server/service/authenticator/GoogleOneTapAuthenticator.kt:58-75`, `UserApplicationService.kt:19-31` |
| S1 | **Still present.** Anyone can forge an admin token in any tenant, or a party's token, and sign for them. The e-signature cookie is forgeable the same way. **Fix:** sign tokens (HS256/RS256, key from the environment) and verify them in all three modules. | `S/security/server/service/CallerService.kt:24-27`, `AccessTokenService.kt:82` |
| S2 | **Still present.** The open endpoints above bypass the checks (customer contacts, ID images, payment data, writes to agents and photos). | `CustomerEndpoints.kt:21,29`, `AgentEndpoints.kt`, `UserEndpoints.kt:19`, `AssignmentEndpoints.kt:24,32`, `PartyEndpoints.kt:23,34` |
| H1 | **Still present, and worse** because of N-S2. | `GoogleOneTapAuthenticator.kt:58-75` |
| H2 | **Still present, with a new entry point.** No attempt limit, lockout or rate limit on one-time codes. The comparison isn't constant-time (`PasswordEncryptor.kt:13`). E-signature's `/sign/{id}/verify` is a second public way in. | `AbstractPasswordAuthenticator.kt:56-71`, `E/ui/sign/SignController.kt:141-171` |
| H4 | **Still present.** The LLM's instructions are still in `.user {}`, next to images the attacker controls. ID documents go to Gemini by default (`application.yml:13-18`) and to Moonshot/Kimi in the deployed `test` profile (`application-test.yml:3-8`): check both providers' data-processing terms. | `S/party/server/service/kyc/KycInfoExtractorIdentification.kt:71`, `KycInfoExtractorSelfie.kt:41` |
| H5 | **Still present, larger scope.** KYC images, contract contents and signed PDFs share the same storage with public URLs. KYC keys are sequential (`kyc/{caseId}/images/{imageId}.ext`). If the bucket can be listed or read publicly, they can be enumerated. **Fix:** a private bucket (Block Public Access, SSE) for KYC and contracts. | `PartyService.kt:136`, `KycService.kt:157`, `MandateService.kt:429-432`, `SignedContractService.kt:82` |

### Medium

| ID | Issue | Where |
|---|---|---|
| N-S3 (new) | **The signer's recorded IP can be spoofed.** `SignController` takes the *first* `X-Forwarded-For` entry. Heroku appends the real IP, so the first entry is the client's choice. The server also accepts `ipAddress` / `userAgent` from any holder of the party's token. Both go into the signature hash as evidence. A value over 45 characters overflows `ip_address VARCHAR(45)`, and the sign fails with a 500. **Fix:** `server.forward-headers-strategy: native` and `remoteAddr`; validate IP format and length on the server; in the longer term, accept these fields only from the e-signature backend. | `E/ui/sign/SignController.kt:203`, `S/contract/server/service/ContractService.kt:276-277`, `V4_0__contract.sql:43` |
| N-S4 (new; was C3) | **A signature isn't bound to the document the signer saw.** Only the e-signature page compares `contentUrl`, before calling `sign`. The server checks nothing, so a `setContent` between that check and the server's lock gets signed unseen. `POST /{id}/content` sets `contentHash = null`. The signature hash then covers an empty content hash, and PDF generation skips the integrity check (`SignedContractService.kt:57`). The page shows the sanitized HTML, but the PDF renders the raw HTML. **Fix:** the page hashes the bytes it fetched and sends the hash in `SignContractRequest`; the server compares it with `contentHash` under the lock (409 if different); refuse `request-signature` and `sign` while `contentHash` is null. | `E/ui/sign/SignController.kt:184-199`, `ContractService.kt:186-190,252-281`, `ndopify-dto/.../SignContractRequest.kt` |
| N-S5 (new) | **`contentUrl` isn't restricted (SSRF and path traversal, combined with S1).** `ContractContentClient` GETs any URL, with no host allowlist, no timeout and no size cap. With a forged admin token, an attacker sets `contentUrl` to an internal URL; once the contract is sent for signature, the e-signature server fetches that URL and shows the page. The safelist (`relaxed()` plus `style` and `class` on every element) also allows remote images (a beacon revealing the signer's IP) and CSS overlays over the consent and sign controls. On the server, `StorageService.pathOf` URL-decodes the path, so `%2e%2e/` leaves the local storage directory (dev), and on S3 any key can be targeted (for example `kyc/...`). That object is then rendered into a public PDF. **Fix:** `setContent` accepts only URLs under the storage's public prefix and rejects `..`; allowlist and timeouts in the content client; drop `class` and restrict `style`; add a CSP (`img-src 'self' data: <storage>`). | `E/client/ContractContentClient.kt:23-43`, `E/config/RestConfiguration.kt:37`, `S/platform/storage/StorageService.kt:32-38`, `StorageServiceLocal.kt:51` |
| N-S6 (new) | **The e-signature sign-in brings H2, M2 and L10 to a new public page.** `/sign/{id}/otp` tells "no contract sent to this address" (`USER_NOT_FOUND`) apart from "code sent", so it reveals which e-mails have an account. It sends a code to any member of the tenant, agents included, with unlimited resends (mail bombing) and unlimited guesses. **Fix:** rate-limit per e-mail and per IP, lock the code after ~5 failures, give the same answer either way, and add `applicationCode` to `CreateOtpRequest`. | `E/ui/sign/SignController.kt:113-125`, `S/security/server/service/OtpService.kt:40-48` |
| S3 | **Still present.** An offered agent gets `customerId` and `comments` before accepting. | `MandateAccessService.kt:27-38`, `MandateMapper.kt:14,49` |
| S4 | **Still present.** Mandate update doesn't check the status (an APPROVED or CLOSED mandate can be edited after signing), lets the city change, and doesn't lock. | `S/mandate/server/service/MandateService.kt:195-238` |
| M1 | **Still present.** KYC uploads only check that the client's `Content-Type` starts with `image/`, so SVG is accepted. | `KycService.kt:155,440-442` |
| M2 | **Still present, now on e-signature too** (N-S6). | `OtpService.kt:40-48` |
| M3 | **Still present.** `show-details: always`, a wide actuator exposure and ops endpoints are on by default, and the deployed `test` profile doesn't override them. Partner Central's and e-signature's default `application.yml` expose `beans`, `flyway`, `mappings`… too (e-signature's `denyAll` blocks them). | `ndopify-server/src/main/resources/application.yml:164,171,191`, `ndopify-partner-central/src/main/resources/application.yml` |
| M4 | **Partly fixed.** The filter now answers 401 without the library's text. Still present: `ControllerErrorHandler.kt:53` (`e.message` for unexpected errors) and `:88` (Hibernate text). `AccessTokenService.kt:74-76` still throws a 403 with the JWT library's message to any other caller (N-Q11). | `S/error/server/endpoint/ControllerErrorHandler.kt:53,88` |
| M5 | **Still present, now in e-signature too.** CSRF is off. `SameSite=Lax` stops a cross-site sign, but not a cross-site POST to `/sign/{id}/logout` or `/sign/{id}/otp`. Thymeleaf's `th:action` adds the token automatically once CSRF is on. | `B/config/SecurityConfiguration.kt:35`, `E/config/SecurityConfiguration.kt:31` |

### Low

- **Still present:**
  - **L1, now in both web apps:** cookies are `Secure` only when `request.isSecure`. No yml sets
    `server.forward-headers-strategy`, so behind Heroku `isSecure` is always false, and neither the token cookies nor
    `JSESSIONID` are ever `Secure`. `JSESSIONID` has no `SameSite` either.
    (`B/service/security/AccessTokenService.kt:23,33`, `E/service/security/AccessTokenService.kt:27,38`)
  - **L2:** one-time codes hashed with salted MD5.
  - **L4:** no session-id rotation on login, in either web app.
  - **L5:** the local storage servlet is registered in every profile.
  - **L6:** personal data in logs (`KVLoggerFilter.kt:53-54`).
  - **L8:** the MTN sandbox in prod.
  - **L10:** no rate limit or captcha anywhere.
  - **AWS SDK v1.**
  - **S6, partly:** contract ids are UUIDs now, so contracts can't be enumerated. Mandates still answer 404 before
    403 with sequential ids.
  - **S7:** the tenant filter still has no order (`S/config/TenantConfiguration.kt:17-22`), yet CLAUDE.md says it's
    `HIGHEST_PRECEDENCE`.
- **Partly fixed:** **L9.** Partner Central pins exact CDN versions. The website still loads `autonumeric@4`
  (`ndopify-website/src/main/html/louer.html:359`, `rechercher.html:268`), and no script has an SRI `integrity`.
- **Fixed:**
  - **L7:** prod sets `clean-disabled: true`. The test profile still allows Flyway clean.
  - **S5:** the filter answers 401.
- **New:**
  - **N-S9. Workflows:**
    - The server deploy now passes secrets through `env:` (`90f32c2`).
    - Partner Central and e-signature still write `${{secrets.NDOPIFY_SERVER_URL}}` inline and unquoted in `run:`
      (`ndopify-partner-central-main.yml:92`, `ndopify-esignature-main.yml:92`).
    - The Heroku CLI comes from an unpinned `curl … | sh`.
    - Third-party actions are pinned by tag, not by SHA.
    - `permissions: contents: write` applies to the whole workflow, deploy job included.
  - **N-S10. Personal data in messages and logs:**
    - The 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT` message contains the e-mail, and it's returned and logged
      (`ContractSignerService.kt:43`).
    - `ContractContentClient.kt:41` logs the content URL.
    - E-signature's `LoggingClientHttpRequestInterceptor.kt:29` logs URIs, including `contract-party-id`, the secret
      of the sign link.

### Checked and fine

- **R1 (fixed on both sides):**
  - The server maps a decode failure to 401 `AUTH_UNAUTHORIZED` (`TenantContextFilter.kt:97-105`).
  - Partner Central and e-signature clear the cookie and mark `ACCESS_TOKEN_REJECTED`, and the interceptor stops
    forwarding the token.
  - Tests cover it (`CreateOtpEndpointTest.kt:252`, `JwtAuthenticationFilterTest`,
    `AuthorizationClientHttpRequestInterceptorTest`).
- **Signature hashes:**
  - A random 16-byte salt and SHA-256. The salt is never in a DTO or a log.
  - The verify endpoint gives the same 404 for an unknown contract, a wrong hash or an unsigned party.
  - E-signature rejects a malformed hash without calling the server.
- **Verify page:** no session, no JS, `noindex`, `no-referrer`, the minimum data, no PDF link. Only the server's
  `CONTRACT_SIGNATURE_NOT_FOUND` counts as "not valid". (The PDF still leaks through the API: N-S1.)
- **Contract access:**
  - Sign has no admin bypass.
  - A non-admin's search is forced to its own party, and `contract-party-id` must match on the same row.
  - Create, content and request-signature are admin-only.
- **PDF renderer:**
  - `OfflineUserAgent` opens nothing but `data:` and the library's own resources.
  - Scripts are stripped, the page is built from a Jsoup DOM (no XXE), and the signature block goes through
    escaped Mustache.
  - File names use a random token, never the contract party's id.
- **`ContractSignerService`:**
  - Only signers get accounts, with `e-signature` access only.
  - The membership conflict is rejected before anything is saved, and the grant is idempotent.
- **E-signature module:**
  - `denyAll` for everything else.
  - The e-mail comes from the session, never the form.
  - `PendingSignature` is bound to its link.
  - A separate content `RestClient` never sends `Authorization` / `X-Tenant-ID` to storage.
  - Jsoup strips scripts, event handlers and `javascript:` URLs.
  - The token cookie is `HttpOnly`, `SameSite=Lax`, 1 hour.
  - Default security headers are on.
  - Redirects always go to `/sign/`, so there are no open redirects.
  - Warn logs carry the status and code only.
- **Mail:** `MailService` uses `JavaMailSender.send`, so the SMTP auth and STARTTLS settings apply. The CSS comes
  from a static classpath file.
- **Unchanged:**
  - No committed secrets: the yml files only reference `${ENV}` variables, and the `AKIA…` string in
    `StorageServiceS3Test.kt:83` is a fake fixture.
  - No SQL injection.
  - PR workflows use `pull_request`, not `pull_request_target`.
  - CORS denied by default.

## Concurrency and transactions

### Earlier findings

| Finding | Status |
|---|---|
| Concurrent writes overwrite each other | **Mostly fixed** (unchanged). `PartyEntity` still has no version (C2). |
| `requestApproval` | **Fixed.** The leftover is unchanged: a double click leaves the loser's HTML upload in storage (`MandateService.kt:252-261,445-447`). |
| Contract locking | **Fixed.** All contract writes lock (`ContractService.kt:187,200,253,310,345`), including the new `setSignedContent`. |
| L3: a one-time code can be used twice | **Still present.** `AuthFactorService.kt:80-90`, `OtpAuthenticator.kt:48-63`. |
| KYC job order, `recoverInterrupted` | **Still present.** Newest first (`KycService.kt:105`). `recoverInterrupted` has no limit and no try/catch per case (`:246-250`), so one bad case stops the rest. |
| No cluster lock | **Still present.** 6 `@Scheduled` methods now. Add ShedLock before running more than one dyno. |
| `jakarta.transaction.Transactional` | **Still present** in 15 files; Spring's in none. A checked `IOException` from storage commits. |

### Open

| ID | Severity | Issue | Where |
|---|---|---|---|
| C1 | High | **Still present.** `completeApproval` locks the mandate, then the contract. `sign` locks the contract, then its synchronous listener updates the mandate. Real case: the agent re-sends while the customer signs, MySQL deadlocks, and someone gets a 500. **Fix:** always lock the contract before the mandate, and map lock errors to 409 (C7). | `MandateService.kt:443,450`, `ContractService.kt:187,253,297`, `MandateEventListener.kt:33-36` |
| N-C2 (new) | Medium | **Any concurrent write to the mandate rolls back the customer's last signature.** `onContractSigned` reads the mandate without a lock, then saves it with `@Version`. An agent's edit (allowed while PENDING_APPROVAL, C9) makes the whole signature roll back with a 409 `MANDATE_VERSION_CONFLICT`. E-signature treats a 409 as "settled" and goes to `?step=done`, which shows the sign step again with no message. **Fix:** `findByIdForUpdate` in `onContractSigned`, with contract → mandate order everywhere (this also fixes C1). | `MandateService.kt:271-283`, `E/ui/sign/SignController.kt:208-215` |
| N-C1 (new) | Medium | **A cancelled mandate's contract stays signable.** `cancel` never touches the contract, and nothing ever sets `ContractStatus.CANCELLED`. The customer can still sign, and the contract becomes SIGNED with a PDF, for a CANCELLED mandate. E-signature's `cancelled` view can never show. **Fix:** in `cancel`, lock the contract (contract → mandate) and cancel it unless it's SIGNED. | `MandateService.kt:341-363`, `ContractService.kt:371` |
| N-C3 (new) | Medium | **`ContractSignerService` creates users and memberships with check-then-insert.** Two concurrent request-approvals for the same customer (or the same e-mail in two tenants, since `T_USER.email` is global) both INSERT. The second fails on `UNIQUE(email)` with a `DataIntegrityViolationException`, a 500 (C7), after its upload. The same race exists on `T_MEMBERSHIP` and `T_USER_APPLICATION`. **Fix:** create in `REQUIRES_NEW`, catch the duplicate and read again (or upsert); map `DataIntegrityViolationException` to 409. | `ContractSignerService.kt:30-48`, `UserService.kt:69-76`, `MembershipService.kt:48-68` |
| N-C4 (new) | Medium | **Async work can be lost, and a lost signed PDF has no recovery in prod.** No `spring.task.execution` settings, so Boot's defaults apply: 8 threads, an unbounded queue, no waiting on shutdown. A deploy or dyno restart drops pending PDFs and e-mails. The only retry, `POST /v1/contracts/{id}/signed-content`, is an `@OpsEndpoint`, off in prod, and no job looks for SIGNED contracts without a PDF. **Fix:** a sweep job (SIGNED, `signedContentUrl` null, older than ~10 min); `queue-capacity`, `shutdown.await-termination=true`; a small dedicated executor for PDFs. | `ContractEventListener.kt`, `ContractJobs.kt`, `ContractEndpoints.kt:88-90` |
| C2 | High | **Still present.** A photo upload saves the whole party row, possibly over a VERIFIED status set in the meantime. | `PartyService.kt:124-140` |
| C4 | Medium | **Still present.** `markAssigned` / `onKycVerified` save the whole agent row. | `AgentService.kt:92-93,208-213`, `MandateDispatcher.kt:135` |
| C5 | Medium | **Still present.** Max-load doesn't count PENDING offers, and `accept` doesn't check it again. | `MandateDispatcher.kt:71-74,208,213`, `AssignmentService.kt:84-98` |
| C6 | Low | **Still present.** `accept` vs `cancel`. | `AssignmentService.kt:84-98` |
| C7 | Low | **Still present, more reachable now.** Only the optimistic-lock error is a 409; deadlocks, lock timeouts and unique violations (N-C3, N-C6) are 500s. | `ControllerErrorHandler.kt:71` |
| C8 | Low | **Still present.** KYC image race. | `KycService.kt:131-170` |
| C9 | Low | **Still present.** Editing a PENDING_APPROVAL mandate leaves the contract out of date, and now also causes N-C2. | `MandateService.kt:194-238` |
| C10 | Low | **Still present.** `ContractExpiredEvent` has no listener. | `ContractService.kt:354` |
| N-C5 (new) | Low | **A losing PDF run leaves its files public.** `setSignedContent` keeps the first PDF, and the loser's full-contract PDF stays public at a random path. **Fix:** delete the loser's files; add an S3 lifecycle rule for orphans. | `ContractService.kt:309-315`, `SignedContractService.kt` |
| N-C6 (new) | Low | **Possible deadlock on a year's first contract.** Concurrent `INSERT … ON DUPLICATE KEY UPDATE` on a missing key. **Fix:** seed the rows, or retry. | `ContractNumberGenerator.kt:36-46` |
| N-C7 (new) | Low | **The tenant cache in the web apps isn't thread-safe and is never refreshed.** `lateinit var tenants` is filled without synchronization; a new tenant needs a restart; an empty answer is cached forever; an unknown host is a 500. **Fix:** a TTL cache that never caches an empty list. | `B/service/TenantContext.kt`, `E/service/TenantContext.kt` |

## Performance (new)

| ID | Severity | Issue | Where |
|---|---|---|---|
| P1 | High | **4 DB connections, held longer than needed.** `database.pool-size: 4`, not overridden in prod, shared by ~200 Tomcat threads, 8 async and 4 scheduler threads. `open-in-view` is on (Boot's default), so a request keeps its connection until the response is written, including the S3 uploads that were moved out of transactions on purpose. The `@Transactional` e-mail methods hold a connection while SMTP sends. Four slow uploads or sends and everything else waits 30 s, then fails with a 500. **Fix:** 10–20 connections in prod; build e-mails in the transaction and send them after commit; `leak-detection-threshold` ≈ 5 s; turn open-in-view off once P3 is done. | `ndopify-server/src/main/resources/application.yml:2,32` |
| P2 | High | **No timeouts anywhere.** **SMTP:** no `mail.smtp.connectiontimeout` / `timeout` / `writetimeout`, so JavaMail waits forever. **Partner Central and e-signature:** `NdTenantClient`'s `RestTemplate()`, the Spring `RestClient`s (JDK client, no read timeout), and `ContractContentClient` (no size cap either). **AI:** none on the KYC calls, so a hung call blocks the KYC job for good. `GeonamesImporter.kt:252-253` has connect and read swapped. **Fix:** SMTP 5 s / 10 s; `spring.http.client.connect-timeout=2s`, `read-timeout=10s`; AI ≈ 60 s. | `application.yml:61-79`, `B/config/RestConfiguration.kt`, `E/config/RestConfiguration.kt:37-42`, `KycInfoExtractorIdentification.kt:80` |
| P3 | Medium | **N+1 queries.** The `@ManyToOne` / `@OneToOne` associations are eager by default, and each one loads with its own SELECT under Specification queries; the lazy `neighborhoodIds` are read in the mappers. Estimates: `GET /v1/agents?limit=100` ≈ 100 extra party SELECTs; `GET /v1/mandates?limit=100` ≈ 100 neighbourhood SELECTs; one dispatch ≈ 200+ queries **while holding the mandate's `FOR UPDATE` lock**. **Fix (one line first):** `hibernate.default_batch_fetch_size=50`; then `LAZY` plus `@EntityGraph` where needed. | `AgentEntity`, `CustomerEntity`, `AssignmentEntity`, `MembershipEntity`, `MandateMapper.kt:72`, `MandateDispatcher.kt:159-237` |
| P4 | Medium | **The Partner Central home page makes 8 + N + M sequential calls** (still present). Typically 15 (2 offers, 5 mandates), up to 48. Each mandate GET runs ~7–8 SQL queries on the server, so ~100 SQL queries per typical view, one after the other. Every page pays 3 calls to identify the agent. **Fix:** add the moving date and contract dates to `MandateSummary`, or a bulk get by id; put `partyId` / `agentId` in the token or the session; run independent calls in parallel. | `B/ui/home/HomeController.kt:69-160` |
| P5 | Low–Med | **The location type-ahead scans the table.** `UPPER(ascii_name) LIKE 'X%'` can't use an index, and there's no index on `ascii_name`. Every keystroke scans the country's locations. **Fix:** drop `UPPER` (the collation already ignores case); index `(country, type, ascii_name)`. | `S/refdata/server/service/LocationService.kt:81`, `V1_1__refdata-location.sql:17-19` |
| P6 | Low | **No status index on `T_KYC_CASE`** for the KYC job's searches. **Fix:** index `(tenant_id, status, modified_at)`. | `V2_3__party-kyc.sql` |
| P7 | Low | **Unbounded or wasteful queries:** `recoverInterrupted` has no limit; paged searches run a COUNT, including the dispatcher's two 100-agent searches. **Fix:** a slice, or a plain limit. | `KycService.kt:248`, `MandateDispatcher.kt` |
| P8 | Low | **E-signature downloads the contract twice per signature** (sign-step GET and POST), with no timeout or size cap. A URL's content never changes (each upload gets a new path), so it can be cached about 10 minutes. | `E/ui/sign/SignController.kt:230-233`, `ContractContentClient.kt` |
| P9 | Low | **The tenant is read from the DB on every request**, and again by services. `@EnableCaching` is on but nothing is cached. **Fix:** `@Cacheable` on `TenantService.findByIdOrNull`, with a short TTL. | `TenantContextFilter.resolveTenant`, `Application.kt:16` |
| P10 | Low | **Each PDF render registers 3 fonts (~1.8 MB) and compiles the Mustache template again.** Memory is fine. | `SignedContractRenderer.kt` |

**Checked and fine:**

- **Searches:** every search caps `limit` at 100.
- **Dispatch load:** counted in 2 grouped queries.
- **Jobs:** they work in batches of 100, one transaction per item.
- **KYC calls:** verification calls storage, the AI and MoMo outside any transaction.
- **Indexes:** present for the frequent assignment, contract, contract-party, membership and mandate lookups.
- **Contract search:** uses a subquery, so no duplicate rows.
- **Verify page:** one call.
- **Uploads:** Partner Central holds them in memory, capped at 5 MB.

## Architecture

| Finding | Status |
|---|---|
| Validation and access live only in the BFF | **Partly fixed.** `ttl-days` is no longer copied (`Contract.signatureRequestedAt`, `MandateRules.signatureTtlDays`). Mandate validation and the editable statuses (S4) are still only in Partner Central, and e-signature repeats the pattern for "content changed since display" (N-S4). |
| Tenant matching uses `startsWith` and is never refreshed | **Still present, now in 2 copies** (`B/service/TenantContext.kt`, `E/service/TenantContext.kt`). See N-C7. |
| Home page makes sequential calls | **Still present.** Contracts are fetched in one batch now, but the per-item loops are unchanged (P4). |
| Tenant set-up copied in listeners and jobs | **Worse:** 11 listener methods and 3 job loops repeat the same set / try / finally / remove. A `TenantScope.run(tenantId) { … }` helper is due. |
| Entities point at other domains' entities | **Still present** (`AgentEntity.kt:32`, `CustomerEntity.kt:26`, `MembershipEntity.kt:33`). `ContractPartyEntity` (`partyId: Long`) is the model to follow. |
| A1. `error` depends on 4 domains | **Still present.** It's the server's only package-level cycle. The handler's package (`com.wutsi.ndopify.error.endpoint`) doesn't match its directory. |
| A2. Access checks placed in different ways | **Now 3 ways:** endpoint (`ContractEndpoints`, `MandateEndpoints`), service (`MandateService`), inline (`AssignmentService.kt:220`). |
| A3. The version check is optional | **Still present.** |
| A4. `TenantFormatter` duplicated, and the copies differ | **Now 3 copies** (server, Partner Central, e-signature) with different behaviour (`time()`, `currency()`, no-break spaces). |
| A5 / A6 | Notes, unchanged and acceptable. |

**Domain dependencies in the server** (rows import columns): there are no cycles apart from `error`. Mandate depends on
contract, and contract never imports mandate; it listens to nothing from it. Agent depends on party through
`KycCaseCompletedEvent`. Contract depends on security (`ContractSignerService`). No endpoint imports a DAO, no domain
imports another's `dao`, and every `*Mailet` is in a `mail/` package. **The module boundaries hold:** e-signature and
Partner Central depend only on `ndopify-dto`, and the DTO depends on no module.

**New:**

| ID | Severity | Issue | Where |
|---|---|---|---|
| N-A1 | High | **Legally meaningful signatures rest on unsigned tokens and `permitAll`.** Who signs, the recorded IP and user agent, and the hash all rest on a token anyone can make (S1) or obtain (N-S2). This is the top architectural risk before e-signature goes to prod. | see S1, N-S2 |
| N-A2 | High | **The signed PDF is fire-and-forget, and its only recovery is off in prod.** Same as N-C4: a sweep job, or a non-ops admin endpoint. | `ContractEventListener`, `ContractJobs` |
| N-A3 | Medium | **Contract storage is done by the mandate domain.** `MandateService` (466 lines, 13 dependencies) stores the HTML, computes `contentHash`, and passes it to `ContractService.setContent`. The content-and-hash invariant is split across two domains, which is why the endpoint path loses the hash (N-S4). **Fix:** `ContractService.uploadContent(id, bytes, type)` stores, hashes and sets under the lock. | `MandateService.kt:260,428-433` |
| N-A4 | Medium | **Partner Central and e-signature duplicate ~500–700 lines of BFF infrastructure.** 19 files: interceptors, the JWT filter / decoder, `AccessTokenService`, `TenantContext`, `TenantFormatter`, `Nd*Client`s, `HttpClientErrorHandler`, `RestConfiguration`, `SecurityConfiguration`. Six are byte-identical, and the copies have started to drift. The fixes for S1, N-C7 and P2 would otherwise be made twice, and an admin console would make a third copy. **Fix:** an `ndopify-bff-core` reactor module (SNAPSHOT inside the reactor, not another released artifact), with the cookie name, application code and tenant URL field as properties. | `B/client/*`, `B/service/*`, `E/…` |
| N-A5 | Low | **Leftovers in packages and POMs.** `KVLoggerFilter` is declared in `com.wutsi.koki.platform.logger.servlet`. `KycUtils` (name matching) is domain logic in `platform`. `TenantConfiguration` / `SecurityConfiguration` sit in a top-level `config/` package. E-signature's POM has unused dependencies (`libphonenumber`, `commons-net`, `commons-text`, `aspectj`). Both web POMs still declare `maven.pkg.github.com/koki/*`. | `S/platform/logger/servlet/KVLoggerFilter.kt:1`, `ndopify-esignature/pom.xml:27`, `ndopify-partner-central/pom.xml:26` |
| N-A8 | Low | **The DTO release flow is manual and churns.** Each module's CI builds alone against GitHub Packages, so a change that touches the DTO and a module needs a release in between. **Fix:** build in the reactor (`mvn -pl modules/x -am`) and release the DTO only for outside consumers, or automate the bump. | `pom.xml:49`, `.github/workflows/*` |
| N-A9 | Low | **Deployment.** Every workflow deploys to test only. Jobs and `@Async` run in the web dyno (N-C4). CI exports `KIMI_API_KEY`, but the default config reads `GEMINI_API_KEY`. | `.github/workflows/`, `application.yml:14` |
| N-A10 | Low | **The 8 MJML templates repeat the head, logo and divider.** `ClasspathIncludeResolver` is configured, but no template uses `mj-include`. | `ndopify-server/src/main/resources/mail/**` |

## Code quality and tooling

| Item | Status |
|---|---|
| Duplicate mandates from "Trouver un bien" | **Partly fixed.** The JS locks the button (`find-property.js:450-482`). The server side is unchanged, so no-JS, a reload or a second tab still create two mandates (`FindPropertyController.kt:232-255`). |
| `JoinController` catches only `HttpClientErrorException` | **Still present** (`B/ui/join/JoinController.kt:176`), and the same pattern is in `E/ui/sign/SignController.kt:120,161,208`. A 5xx or a timeout shows a 500 page. |
| Root POM leftovers | **Still present, with an effect:** see N-Q1. |
| No Maven cache in CI | **Still present.** `setup-java` just needs `cache: maven`. |
| PR gate (90/90) vs POM gates (98/95 root, 95/90 server) | **Still present.** The server is at 95.40% line, 0.4 point above its gate. |
| No branch counter in JaCoCo | **Still present.** Branch coverage is 88–89% in every module. |
| JUnit `Assertions` / `assertThrows` | **Still 9 files**, all in the server (`MailServiceTest`, `MailBodyResolverTest`, `UrlUtilsTest`, `PhoneNumberUtilsTest`, `KVLoggerThreadLocalTest`, `OtpGeneratorTest`, `KycInfoExtractorProviderTest`, 2 MTN tests). |
| `@Ignore`d tests | **Still 3** (MTN). |
| `Thread.sleep` in tests | **7** (was 6; new poll in `SignContractEndpointTest:148`). |
| `KYC_CASE_NOT_PENDING` is a 400 | **Still present** (`KycService.kt:401-402`). |
| Entities default `createdAt` to `Date()` instead of the `Clock` | **Still present** (16 entities). This is what makes `UpdateAgentEndpointTest` flaky (Q-flaky). |
| Partner Central `application-test/prod.yml` are koki config | **Fixed.** CLAUDE.md still says they are. |
| Join / FindProperty duplicate helpers | **Still present.** `errorCode(HttpClientErrorException)` now has **6** copies (4 in Partner Central, 2 in e-signature). |
| Q2. Copied helpers | **Partly fixed.** The mailets share `MandateMailLabels`; `MandateContractGenerator` still has its own maps. |
| Renovate | **#28 Kotlin 2.4.21** fails with "smart cast impossible" (`SignedContractService.kt:73`, `MandateDispatcher.kt:138`, `KycService.kt:184`, `AuthFactorEntity.kt:35,43`); fix with local `val`s. **#56 flying-saucer 10** needs Java 21: close it or pin the version. **#58 mysql-connector-j** is green and can be merged. |
| Q3. Test gaps | **Partly fixed.** Expired tokens are now tested. **Regressed:** `ProdProfileEndpointTest` was deleted (`afa987a`), so nothing tests the prod profile's health, actuator or Swagger exposure (N-Q3). Still no unit tests for `CallerService`, `MandateAccessService`, `ContractAccessService` or the new `ContractSignerService`, and still no test updating a CLOSED mandate. |
| Minor | `application-prod.yml:56` "Every hours" with off indentation; `ndopify-dto/pom.xml:16-19` `<scm>` indentation. Still present. |

**New:**

| ID | Severity | Issue | Where |
|---|---|---|---|
| N-Q1 | High | **Leftover POM properties silently override Boot's versions.** `mvn dependency:list` resolves **Flyway 10.18.0** under Boot 4.1.1, which manages 11.x. `slf4j.version`, `thymeleaf.version` and Kotlin are pinned the same way. `spring-boot.version` (4.0.3) doesn't match the parent (4.1.1). About 43 properties are never used. Renovate bumps them one at a time without knowing they override Boot. **Fix:** delete the unused properties, and comment the overrides that are meant. | `pom.xml:48-133` (`:80` flyway, `:116` `pdfbox.vesion`, `:125` spring-boot) |
| Q-flaky | Medium | **`UpdateAgentEndpointTest.update:69` is flaky.** It failed run `39419336935`. It compares `createdAt` (MySQL `DEFAULT NOW()`, second precision) with a `now` taken in milliseconds before `@Sql` runs. The `Thread.sleep(100)` doesn't help. **Fix:** date the fixture explicitly (`'2020-01-01'`). | `T/agent/server/endpoint/UpdateAgentEndpointTest.kt:47,69` |
| N-Q2 | Medium | **E-mails have no `From` header.** `MailService` never calls `setFrom`, `Email.sender` is never read, and `mail.smtp.from` sets only the envelope sender. SES requires a verified `From`. **Fix:** `msg.setFrom(email.sender ?: configured address)`, plus a test. | `S/platform/mail/MailService.kt:16-31`, `Email.kt:6`, `application.yml:72` |
| N-Q3 | Medium | **The prod profile is untested** since `ProdProfileEndpointTest` was deleted. **Fix:** restore it, with dummy env values (`@TestPropertySource`). | deleted in `afa987a` |
| N-Q4 | Medium | **The web apps have no context-load test**, and `**/config/*` is excluded from JaCoCo. E-signature's `denyAll` / `permitAll` list and the interceptor wiring are never exercised. **Fix:** one MockMvc `@SpringBootTest` per web app that checks the permitted and denied paths. | `pom.xml:285`, `E/config/SecurityConfiguration.kt:22-28` |
| N-Q5 | Medium | **No JavaScript tests (~2,430 lines).** The rooms / furniture / parking rules exist 3 times (`FindPropertyRules.kt`, `find-property.js:34-36`, `mandate.js:23-25`). The double-submit and `pageshow` code exists 5 times (`partner-central.js`, `kyc.js`, `find-property.js`, `join.js`, `esignature.js`). **Fix:** one `busy()` helper in `util.js`; render the rules into a `data-` attribute from Kotlin; optionally a Playwright smoke test. | `*/src/main/resources/public/assets/js/` |
| N-Q6 | Low | **Mail CSS is HTML-escaped.** `{{__css__}}` (double braces) in all the `.mjml` templates turns the CSS's quotes into `&quot;`. Gmail then drops the whole `<style>` block (the logo size, `.main-content`, the divider). The `.replace("&#10;", "\n")` in `MailBodyResolver` is a symptom of the same thing. `font-face:` isn't a CSS property (`mail.css:7`, should be `font-family`). The CSS is read on every e-mail and its stream is never closed, and the class imports `okio.FileNotFoundException`. **Fix:** `{{{__css__}}}`, `font-family`, `java.io.FileNotFoundException`, and cache the CSS. | `ndopify-server/src/main/resources/mail/**/*.mjml`, `mail.css:7`, `S/platform/mail/MailBodyResolver.kt:5,20-22` |
| N-Q7 | Low | **Logs:** Partner Central logs the server's response body at ERROR for expected 4xx answers. 13 log lines start with `">>>"`. There's a `// TODO: Remove this from KYC?`. | `B/ui/login/LoginController.kt:91,121`, `B/ui/join/JoinController.kt:177`, `KycService.kt:227` |
| N-Q8 | Low | **Workflows** (besides N-S9): 11 `heroku config:set` calls, so 11 releases and restarts per deploy (use one call); `mvn -q -B -X` in the DTO release; 11 near-identical workflows (a reusable `workflow_call`); no `concurrency:` on the deploy jobs; badge pushes use `continue-on-error`. | `.github/workflows/` |
| N-Q9 | Low | **Misleading commit:** `f9c0471` "increase frequency of kyc" changes the **contract** expiry cron to every 30 minutes (`application.yml:197`); the KYC cron is unchanged. Check which one was meant. | `ndopify-server/src/main/resources/application.yml:197,222` |

**Q1. CLAUDE.md is out of date.**

| Area | CLAUDE.md says | The code has |
|---|---|---|
| Party domain classes | `KycVerifier*` / `KycVerifierProvider` | `KycCaseVerifier*` / `KycCaseVerifierProvider` |
| | `IdentificationInfoExtractorAi` / `IdentificationInfoExtractorProvider` | `KycInfoExtractor*` / `KycInfoExtractorProvider` |
| | `IdentificationImageEntity` | `KycImageEntity` |
| AI key | `KIMI_API_KEY` | `GEMINI_API_KEY` by default; Kimi only in `application-test.yml` |
| Tenant filter location | `platform/tenant/servlet` and `platform/config`, at `HIGHEST_PRECEDENCE` | `security/server/servlet` and `config/`, with no order (S7), plus `TenantJpaConfiguration` |
| Version columns | `V7_2` | No `V7_2`; the version columns are spread over earlier migrations |
| Leftover columns | `T_USER.tenant_id` / `party_id` left over from `V7_0` | Those columns are gone; memberships come from `V3_1` |
| Mandate update | Runs "under the mandate's lock" | `update` uses `findById` |
| `ContractJobs.expire` | "daily" | Every 30 minutes by default; daily only in prod |
| Partner Central profiles | "koki config" | Clean now |

Not documented at all:

- the filter's 400s (`TENANT_MISSING_HEADER`, `TENANT_ID_NOT_VALID`, `TENANT_NOT_ACTIVE`) and its excluded paths;
- that Partner Central and e-signature share copied infrastructure that must be kept in sync.

**Checked and fine:**

- **E-signature quality:** 22 test files, the 98/95 gate passes, and the README has its three badges.
- **README badges:** present in every module.
- **Default local config:** points at Mailpit `localhost:1025`, as CLAUDE.md says.
- **Working tree:** clean.

## Strengths

- **The e-signature module.** Strict scope (`denyAll`), no state in the JS, every step posts without JS, a careful
  verify page, a separate content client, and 99% coverage. It was built against written rules (CLAUDE.md), and
  they show in the code.
- **Signature design.** Salted hashes bound to the content hash, an offline PDF renderer with embedded fonts, files
  named with random tokens, and first-one-wins under the lock.
- **Fast, complete fix of R1**, on both sides, with tests.
- **Domain direction is clean.** Contract knows nothing about mandates; party knows nothing about agents (both go
  through events). No DAO is used across domains, and `ContractPartyEntity` references parties by id.
- **`requestApproval`'s 3 transactions**, careful async e-mails (ids plus tenant, after commit, failures logged), and
  `@OpsEndpoint` routes that don't exist at all in prod.
- **Unchanged:** clean layering, the injected `Clock`, a gap-free contract sequence per tenant, and the exhaustive
  `MandateDispatcherTest`.

## Recommended order

Each step is small and easy to undo:

1. **Before any real contract is signed:**
   - **N-S2:** refuse Google One Tap for `e-signature`, or turn it off, until ID tokens are verified (H1). Stop
     creating application access on sign-in.
   - **N-S1:** remove `signedContentUrl` from `ContractSignature` (DTO release), and make contract storage private
     (H5).
   - **S1:** sign tokens in all three modules.
2. **Signature integrity:**
   - **C1 + N-C2:** contract → mandate lock order everywhere, with `findByIdForUpdate` in `onContractSigned`.
   - **N-C1:** cancel the contract when its mandate is cancelled.
   - **N-S4:** send the content hash in `SignContractRequest`, and refuse to sign without a `contentHash`.
   - **N-S3:** forwarded headers, and the IP from `remoteAddr`.
3. **Production readiness** (mostly configuration):
   - **P2:** timeouts on SMTP, REST clients and AI.
   - **P1:** pool size, plus leak detection.
   - **P3:** `default_batch_fetch_size`.
   - **N-C4:** a PDF sweep job, and the async executor settings.
   - **L1 / N-S7:** `forward-headers-strategy`.
4. **Quick quality wins:**
   - **N-Q1:** remove the POM overrides (watch the Flyway upgrade from 10 to 11).
   - **N-Q2:** set the `From` header.
   - **N-Q6:** `{{{__css__}}}` in the mail templates.
   - **Q-flaky:** date the `UpdateAgentEndpointTest` fixture.
   - **N-Q3:** restore `ProdProfileEndpointTest`.
   - Merge Renovate #58.
5. **H2 + N-S6 + L3:** attempt limit, rate limit, and atomic consumption of one-time codes.
6. **S2 + S3 + S4:** access checks on the remaining endpoints, a reduced view for offered agents, and a status check
   on mandate update.
7. **Structure:**
   - **N-A4:** `ndopify-bff-core` before an admin console makes a third copy.
   - **N-A3:** move contract storage into the contract domain.
   - **`TenantScope`**, and the **C4 / C5** targeted UPDATEs.
8. **Smaller items:**
   - Spring `@Transactional`.
   - KYC job oldest-first.
   - P4–P10.
   - The CLAUDE.md updates (Q1).
