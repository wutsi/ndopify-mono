# Code review: quality, architecture, security (2026-10-28)

Reviewed at commit `c31b1cc`, 60 commits after the last rescan of the previous review (`0ab6347`,
[2026-10-23](2026-10-23-code-review.md)). Every earlier finding was checked again. New since the previous review:

- the **listing** domain in ndopify-server: entities, sections, photos, the verifiers and their job, publishing (LLM
  generated content, e-mails on publishing);
- the Partner Central listing pages: checklist, section pages, photos page, create / open a listing from the mandate page;
- two LLMs (`e1a580b`): Kimi for vision, DeepSeek for text;
- Google One Tap sign-in was removed;
- `DatabaseResetConfiguration` now runs on the `test` profile (`50ca5ab`).

The findings come from reading the code. Builds and tests were run locally at `c31b1cc` (MySQL up). No load test.
CI on `main` is green, except `ndopify-server-main` run `40603181718` (ktlint, a commit pushed straight to `main`).

| Module | Tests | Line (gate) | Class (gate) |
|---|---|---|---|
| Server | 1236 passed, 3 skipped | 96.94% (0.95) | 96.58% (0.90) |
| Partner Central | 925 passed | 98.75% (0.98) | 97.44% (0.95) |
| E-signature | 173 passed | 99.16% (0.98) | 96.43% (0.95) |
| Web | 39 passed | 97.62% (0.97) | 87.50% (**0.80**) |

Paths are relative to `modules/`. Short prefixes:
- `S` = `ndopify-server/src/main/kotlin/com/wutsi/ndopify`
- `R` = `ndopify-server/src/main/resources`
- `B` = `ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc`
- `E` = `ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature`
- `W` = `ndopify-web/src/main/kotlin/com/wutsi/ndopify/web`

New findings use the `Y-` prefix.

## Summary

**Fixed since the last review:**
- **N-S2 / H1:** `GoogleOneTapAuthenticator` is deleted; `GOOGLE_ONE_TAP` gives a 400 `AUTH_TYPE_NOT_SUPPORTED`
  (`S/security/server/service/AuthenticatorProvider.kt:13-18`). `ensureCanSign` checks the application
  (`S/contract/server/service/ContractAccessService.kt:48-51`). That closes signing as someone else.
- **X-S6:** the OTP change was never committed (`AuthenticatorProvider.kt:16` still maps `OTP -> otp`).
- **X-P2, AI half:** Kimi and DeepSeek are in the base config, and the server workflow sets both keys.

**Top risks now:**

1. **Listings have no server-side access check** (**Y-S1**). Any token in the tenant can update, verify, publish, add
   or delete photos on any listing. A customer's e-signature OTP token is enough. Partner Central checks ownership,
   but ndopify-server is public, so a direct call skips that check.
2. **Anonymous reads expose homes that aren't published** (**Y-S2**). `GET /v1/listings` needs only `X-Tenant-ID`
   and returns every status, with the exact GPS, access directions, agent remarks and interior photos.
3. **Listing verification never runs** (**Y-C1**). The endpoint sets `UNDER_VERIFICATION` and the job only picks
   `PENDING_VERIFICATION`, so every listing an agent sends stays stuck. No test covers sending and then verifying.
4. **Configuration left over from testing is on the deployed app** (**Y-S3**, **Y-P1**, **Y-P2**):
   - the test database is wiped on every boot;
   - the prod listing crons run every minute, not every hour;
   - night dispatch is effectively off (06:59–07:00).
5. **Unchanged from last time:** a mandate can be edited while its contract is out for signature (**X-A1 / X-C1**).
   The ops endpoints, `/actuator/**` and the LLM health check (**X-P1**, now 2 paid calls per probe) are still open
   to anyone.

There's no build blocker. **Before real listings go public, treat Y-S1, Y-S2 and Y-C1 as blockers. Before real
contracts are signed, treat X-A1 / X-C1 as blockers.**

## Security

### Earlier findings

| ID | Status at `c31b1cc` |
|---|---|
| N-S2, H1 | **Fixed.** Leftovers: `R/db/migration/common/V1_3__refdata-application.sql:29` still lists `GOOGLE_ONE_TAP` for `public-portal`, and `UserApplicationService.kt:19` (`...OrCreate`) has no callers left. Remove both so nobody wires them back. |
| X-S6 | **Fixed** (never committed). |
| S2 (any token is enough) | **Worse:** the listing writes add 6 endpoints (see the list below). |
| Ops endpoints | **Still present.** No role check on `MandateEndpoints.kt:81-86`, `AssignmentEndpoints.kt:49-53` or `KycEndpoints.kt:58-62`; they're on in `test` (`R/application.yml:218`). |
| X-P1 | **Worse:** 2 LLM health checks, Kimi and DeepSeek (`S/config/platform/AIConfiguration.kt:37-43`). They're still public, uncached, and show the provider's error (Y-S6). |
| M3 | **Still present** (`R/application.yml:198`; Partner Central `application.yml:53`). |
| X-S5 | **Worse**, see Y-S3. |
| X-S7 | **Still present** (`B/ui/login/LoginController.kt:90,120`, `E/ui/sign/SignController.kt:121,162`). |
| X-S8 | **Still present** (`AccessTokenService.kt:98-99`). |
| H2 | **Still present.** There's no attempt limit, the caller chooses the TTL, and each new request resets the code (`AbstractPasswordAuthenticator.kt:56-71`, `AuthFactorService.kt:58-66`). |
| H4 | **Still present.** The instructions are in `.user{}` (`KycInfoExtractorIdentification.kt:71`, `KycInfoExtractorSelfie.kt:43`). |
| H5 | **Partly.** The images use presigned URLs, but any token can reach them through `/v1/kyc/cases?party-id=`. |
| M1 | **Still present.** `KycService.kt:440-442` only checks `image/*`, so SVG passes. |
| M2 / N-S6 | **Still present.** The e-mail appears in error messages (`OtpService.kt:41-48`, `AbstractAuthenticator.kt:113`). |
| M4 | **Still present.** Exception text goes into responses (`ControllerErrorHandler.kt:55,91,106`). |
| M5, L4, X-E3 | **Still present.** CSRF is off in all 3 apps, logout is a GET, `JSESSIONID` has no SameSite, the session id isn't rotated, and there's no CSP or `Referrer-Policy`. |
| N-S5 | **Still present** (`E/client/ContractContentClient.kt:38`). |
| X-B1, X-B2, X-B3, X-B4 / N-S10 / N-Q7 | **Still present.** One more instance of X-B4: `B/ui/join/JoinController.kt:177`. |
| S3, S4, X-S2, X-S3, X-S4 | **Still present.** |
| L2, L5, L6, L8, L10, S6 | **Still present.** L2 is salted MD5 compared without constant time. L5 is the local storage servlet, mapped in every profile. L8 is MTN `sandbox` in prod. |
| N-S3 (server) | **Fixed** (since `0ab6347`). |

### Endpoints with no access check

Any tenant token is enough, unless the entry says otherwise:

- `GET /v1/assignments(/{id})`
- `GET /v1/users/{id}`
- `GET /v1/agents`, `GET|POST /v1/agents/{id}`
- `GET /v1/customers(/{id})`
- `GET /v1/parties`, `POST /v1/parties/{id}/photo`
- `GET /v1/payment-methods(/{id})`
- `/v1/identifications/**`
- `/v1/kyc/cases/**`, including `/images/{id}/url`
- **New:** `POST /v1/listings`, `POST /v1/listings/{id}`, `/{id}/verify`, `/{id}/publish`, `/{id}/images/upload`,
  `DELETE /v1/listings/images/{id}`
- **New, anonymous** (`X-Tenant-ID` only), every status: `GET /v1/listings`, `/{id}`, `/{id}/images`
- The ops endpoints: `POST /v1/mandates/{id}/dispatch`, `POST /v1/assignments/{id}/expire`,
  `POST /v1/kyc/cases/{id}/verify`
- `/actuator/**`, including `health/llm`, and `/storage/**`. `GET /v1/locations/import` is open by design.

### New

| ID | Sev | Issue | Where | Fix (smallest first) |
|---|---|---|---|---|
| **Y-S1** | **High** | **No access check on any listing write.** Any token can update, verify or publish any listing, or upload to it. Publishing costs an LLM call and sends e-mails. **Exploit:** an agent, or a customer with an e-signature OTP token, calls ndopify-server directly: rewrites a competitor's price or GPS and publishes it, or deletes any photo through `DELETE /v1/listings/images/{id}` (sequential ids). `POST /v1/listings {mandateId}` works on any mandate, so it gets around `MandateAccessService` and copies the mandate's property and price into a listing (made public by Y-S2). Partner Central's check (`B/ui/listing/ListingAccess.kt`) is the only one; the KDoc says so (`B/client/ndopify/NdListingClient.kt:22,86-87`). | `S/listing/server/endpoint/ListingEndpoints.kt:51-85`, `S/listing/server/service/ListingService.kt:36,124-128` | A `ListingAccessService`: the listing's agent or an admin, checked after the 404 and before the 409, else 403 `LISTING_ACCESS_DENIED`. `create` calls `MandateAccessService.ensureCanUpdate(mandate)`. Scope the delete: `DELETE /v1/listings/{id}/images/{imageId}`. |
| **Y-S2** | **High** | **Anonymous reads return unpublished listings and private fields.** `search` adds no status filter unless the caller asks for one (`ListingService.kt:65`). `Listing` carries the exact `latitude` / `longitude`, `landmark`, `accessDirections`, `agentRemarks`, `mandateId`, `agentId`, `errorCodes` and the raw `errorMessage` (LLM or SQL exception text). `/{id}/images` returns PENDING and rejected photos with the reasons. **Exploit:** walk the sequential ids and map owners' homes before anything is published. | `S/security/server/servlet/PublicEndpoints.kt:42-44`, `S/listing/server/mapper/ListingMapper.kt:16-71,114`, `ListingEndpoints.kt:39-49,88-95` | Without an agent or admin token: force `statuses = [PUBLISHED]`, give a 404 for any other status, return only VERIFIED images, and use a public DTO with no exact GPS, directions, remarks, ids or error fields. |
| **Y-S3** | **Medium** | **The deployed test app wipes its database on every boot.** `50ca5ab` moved `DatabaseResetConfiguration` from `@Profile("none")` to `"test"`, the profile Heroku runs, and `application-test.yml:3` allows clean. That's fine for one reset after renumbering migrations (pre-launch, allowed), but it stays on. Every deploy, dyno restart or scale-up runs `clean()` + `migrate()`, and two dynos booting together clean at the same time. **Exploit:** ids restart, and a token issued before the reset is still valid (same `JWT_SECRET`, up to an hour). Its `userId` now points to whoever gets that id next. | `S/config/DatabaseResetConfiguration.kt:10` | Back to `@Profile("none")` (or a `reset-db` profile turned on by hand once). Rotate `JWT_SECRET` after any reset. |
| Y-S4 | Medium | **Photo upload limits are only in Partner Central.** The server trusts the client's `Content-Type` (Partner Central checks the bytes with Tika, the server doesn't). It has no cap on the number of photos (Partner Central's 20 is check-then-act, `ListingPhotoController.kt:106-111`) and no status check, so a photo added to a PUBLISHED listing stays PENDING and visible. It stores the file before checking. With Y-S1, any token can push unlimited 10 MB files to public storage, and each one later costs a vision-LLM call. | `S/listing/server/service/ListingImageService.kt:57-64,94-110` | Require DRAFT, cap the count on the server, check the magic bytes, and store the file only after these checks pass. |
| Y-S5 | Medium | **Listing fields aren't validated on the server.** Lengths, ranges and choices are only in `B/ui/listing/ListingRules.kt:19,28-31,81-82`. `landmark` over 255 characters gives a 500; `rooms` and the other counts accept negative values. This is X-A2 again for listings. | `ndopify-dto/.../listing/dto/UpdateListingRequest.kt:19-61`, `ListingService.kt:199-222` | `@Size` / `@Min(0)` / `@DecimalMin`/`Max` on the request; `@Valid` on the endpoint. |
| Y-S6 | Low | One `/actuator/health/llm` hit makes 2 paid calls and shows each provider's error and model name. | `AIConfiguration.kt:37-43`, `R/application.yml:190-191` | Make only `/actuator/health` and `/actuator/info` public; cache the result for about 5 minutes. |
| Y-S7 | Low | **The LLM's `heroImageId` is used unchecked.** It can be a made-up id, a position ("Image #1"), or another listing's image. The image titles in that prompt come from the vision model's reading of uploaded photos, so this is an indirect prompt-injection path. | `S/listing/server/service/publisher/ListingPublisherService.kt:103` | Accept it only if it's one of the listing's VERIFIED images; otherwise use the fallback. |
| Y-S8 | Low | The browser's `originalFilename` is logged (CR/LF log forging), shown in the error message, and sent to the server, with no length cap. | `B/ui/listing/ListingPhotoController.kt:112,127,131`, `B/client/ndopify/NdListingClient.kt:72` | Keep the base name only, strip control characters, cap it at about 100 characters. |
| Y-S9 | Low | The DTO release runs `mvn -X`, so debug output (settings, resolved properties) ends up in the Actions logs. | `.github/workflows/ndopify-dto-main.yml:42` | Drop `-X`. |

### Checked and fine

- The three new entities have `@TenantId`. The listing jobs set and clear `TenantContext` for each tenant.
- Photo storage paths contain a random UUID. The verifier downloads URLs the server built, so there's no SSRF.
- Mail templates have no `{{{`. Listing pages have no `th:utext`. The only `innerHTML` (`listing.js:259`) uses
  constant SVG.
- Partner Central: ids are parsed as `Long`, the agent comes from the token, redirects are built from numeric ids, and
  Tika checks the photo bytes (5 MB each, 10 MB per request).
- No secret is committed or logged. The server deploy now passes secrets through `env:`.

## Correctness, concurrency and transactions

### Earlier findings

| ID | Status |
|---|---|
| X-A1, C9 | **Still present.** `update` takes no lock and checks no status (`S/mandate/server/service/MandateService.kt:206-251`). |
| X-C1, N-C2 | **Still present** (`MandateService.kt:285-290,360-369`). |
| X-C2 | **Still present** (`R/application.yml:96-107`). |
| X-C3 | **Still present** (`KycService.kt:300-307`). |
| X-C4, C2 | **Still present**, and copied into `ListingImageService` (Y-C7). |
| C4 / X-A3 | **Still present** (`AgentService.kt:92-93`, `MandateDispatcher.kt:136`). |
| C7 | **Still present**, with 2 more cases (Y-C5). |
| N-C3, C5, C10, N-C5, N-C6 | **Still present.** |
| X-E2 | **Still present.** A 409 version conflict still counts as signed (`E/ui/sign/SignController.kt:82,216`). |

### New

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| **Y-C1** | **High** | **Listings sent from the UI are never verified.** `ListingService.verify` sets `UNDER_VERIFICATION`. `ListingJobs.verify` picks only `PENDING_VERIFICATION`, and `ListingVerifierService` refuses anything else (`:38`). Only the verifier's own retry sets `PENDING_VERIFICATION`. `VerifyListingEndpointTest` asserts `UNDER_VERIFICATION`, and no test sends a listing and then runs the job. | `ListingService.kt:287`, `S/listing/server/job/ListingJobs.kt:37`, `S/listing/server/service/verifier/ListingVerifierService.kt:38-51` | Set `PENDING_VERIFICATION` in `verify()`; fix the test and the KDocs; add a test that sends a listing and then runs the job. |
| Y-C2 | Medium | **An interrupted verification is never recovered.** If the dyno dies after the save that sets `UNDER_VERIFICATION`, nothing picks the listing up again. KYC has `recoverInterrupted`; listing has nothing like it. | `ListingVerifierService.kt:44-51` | A recovery like KYC's: `UNDER_VERIFICATION` with `modifiedAt` older than N minutes goes back to `PENDING_VERIFICATION`. |
| Y-C3 | Medium | **The vision LLM's text overflows the image columns** (`title VARCHAR(100)`, `description VARCHAR(255)`, saved without truncation). In strict mode the save fails, the photo stays PENDING, the listing is retried 3 times (3 LLM calls), then goes back to DRAFT with `INTERNAL_ERROR`. The publisher truncates its fields; the image verifier doesn't. | `ListingVerifierImage.kt:57-66`, `R/db/migration/common/V8_1__listing-image.sql:9-10` | `.take(100)` / `.take(255)`. |
| Y-C4 | Medium | **No lifecycle link between mandate and listing.** `create` checks neither `type == LISTING` nor the status, so a SEARCH, CLOSED or CANCELLED mandate can get a listing. Partner Central checks this, the server doesn't. Cancelling or closing a mandate leaves its listing PUBLISHED. | `ListingService.kt:124-130` | 409 unless the mandate is LISTING and APPROVED; a listener on the mandate's closed and cancelled events that unpublishes the listing. |
| Y-C5 | Low | Two concurrent creates hit `UNIQUE(mandate_id)`, and two concurrent first saves of a section hit `UNIQUE(listing_id, section)`. Both give a 500, not a 409. | `ListingService.kt:128`, `ListingSectionService.kt:47-52`, `V8_0__listing.sql:77` | Map `DataIntegrityViolationException` to a 409 (C7). |
| Y-C6 | Low | The publisher doesn't claim the listing. On 2 dynos both generate the content (2 LLM calls), then one fails on `@Version`. The verifier's first save does act as a claim. | `ListingPublisherService.kt:44-64` | A short claim transaction, as the verifier does. |
| Y-C7 | Low | X-C4 copied: the S3 upload runs inside `@Transactional`, and a failed save leaves an orphan file. | `ListingImageService.kt:56-79` | Upload first, then save in a short transaction. |

**Checked and fine:**
- `ListingEventListener` is `@Async` after commit, sets and clears `TenantContext` / `KVLogger`, and its events carry
  ids only.
- `@Version` plus the request's version turns concurrent update, verify or publish into a 409.
- The verifier's and publisher's LLM calls run outside DB transactions.
- No REJECTED listing status: a failed verification goes back to DRAFT with `errorCodes`.

## Performance and production readiness

| ID | Status / issue |
|---|---|
| P1 | **Still present.** `open-in-view` is on, and e-mails hold a connection during SMTP, now also in `ListingService.sendPublishedEmails` (`:317-331`). |
| P2 | **Partly.** No `spring.ai.retry.*` is configured (Y-P4), and `UrlUtils` has no timeout (Y-P3). |
| P5, P6, P7, P9, P10 | **Still present.** |
| X-P2 | **Partly fixed** (the AI keys). It still deploys `test`, and `test` now resets the DB (Y-S3). |
| X-P3, X-P5 | **Still present.** |
| X-P4 | **Still present**, and `ListingJobs` (`:49-62`) has no KV logger either. |

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| **Y-P1** | **Medium** | **The prod listing crons run every minute.** `"0 * */1 * * ?"` has `*` in the minute field; the comment says "Every hour". During an LLM outage the publisher uses its 3 retries in 3 minutes, then publishes with no title or description. It also multiplies the LLM cost. | `R/application-prod.yml:55-57` | `"0 0 * * * ?"`. |
| **Y-P2** | **Medium** | **Night dispatch is effectively off everywhere.** `night.start` is `06:59` (it was `23:00`), and neither `test` nor `prod` overrides it. Agents get offers at 3 a.m., the 1-hour TTL runs overnight, and mandates reach MANUAL_ASSIGNMENT sooner. It looks like a testing leftover. | `R/application.yml:250-252` | Back to `23:00`. |
| Y-P3 | Medium | **The verifier's photo download has no timeout and leaks temp files.** `HttpURLConnection` sets no connect or read timeout, there's no size cap, and the temp file is never deleted. A stalled read blocks a scheduler thread (4 threads for 7 jobs), so mandate expiry and dispatch wait; the dyno disk fills. | `S/platform/util/UrlUtils.kt:10-31`, `ListingVerifierImage.kt:54` | Read through `StorageService` using the path, or set 5 s / 30 s timeouts; delete the file in a `finally`. |
| Y-P4 | Low | **Long LLM jobs share a small scheduler.** Spring AI's retry is left at its defaults. One verify run (100 listings × at least 5 photos × LLM time and retries) can hold scheduler threads for a long time. | `R/application.yml:2-23,108-114` | Set `spring.ai.retry.max-attempts` (about 2) and the backoff; a time budget per run, or a separate scheduler for the LLM jobs. |
| Y-P5 | Low | The job query (tenant + status) has only single-column indexes. | `V8_0__listing.sql:86-87` | An index on `(tenant_id, status, modified_at)`, which Y-C2 also needs. |

## Architecture

### Earlier findings

| ID | Status |
|---|---|
| X-A2 | **Still present**, and repeated for listings (Y-S5). |
| X-A4, X-A6, N-A3, N-A9, N-A10, MandateService god class (491 lines) | **Still present.** |
| N-A5 | **Still present.** `KVLoggerFilter` (and its test) are still in package `com.wutsi.koki`; the `koki` repository is in 3 POMs. |
| A1 | **Worse:** `error` now also imports `ListingEntity` (`ControllerErrorHandler.kt:15,83`), so it depends on 5 domains. |
| X-A5 | **Worse:** `errorCode()` ×8 (was 7), `lookup()` ×4 in Partner Central controllers, and `TenantFormatter` has drifted (B adds `count()`, `compactMoney`, `today`; E has `time()`). |
| X-A9, X-E1, X-W4, X-W5, X-Q4 | **Still present.** X-E1 still uses JUnit `assertThrows` (`TenantAdviceTest.kt:8,26`). |

### Domain dependencies (`ndopify-server`)

Rows import columns (`exc` = error's exceptions only):

| ↓ imports → | refdata | security | party | agent | customer | contract | mandate | listing | error |
|---|---|---|---|---|---|---|---|---|---|
| security | domain, service | – | domain, mapper | | | | | | exc |
| party | service | | – | | | | | | exc |
| agent | service | service | domain, event, mapper, service | – | | | | | exc |
| customer | | | domain, mapper, service | | – | | | | exc |
| contract | service | service | service | | | – | | | exc |
| mandate | service | service | | domain, service | domain, service | domain, event, mapper, service | – | **service** | exc |
| **listing** | domain, service | domain | domain, service | domain, service | domain, service | | **domain, service** | – | exc |
| error | | | domain | domain | | domain | domain | **domain** | – |

- **New cycle, listing ↔ mandate.** `MandateEndpoints.kt:7,44,60` calls `ListingService.findIdByMandateId` to fill
  `GetMandateResponse.listingId`. It isn't a bean cycle, since `MandateService` doesn't inject listing.
- **Listing holds other domains' entities as JPA associations:** `@ManyToOne MandateEntity` and `AgentEntity`
  (`ListingEntity.kt:43-48`). `create` copies about 30 fields from the mandate.
- No domain uses another's `dao`. Contract still doesn't import mandate.

### New

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| Y-A1 | Medium | The listing ↔ mandate cycle and the entity associations above. A change to the mandate's schema or lifecycle now breaks listing at compile time and through lazy loading. `error` → listing adds to A1. | `MandateEndpoints.kt:60`, `ListingEntity.kt:43-48`, `ControllerErrorHandler.kt:15,83` | `ListingEntity` keeps plain `mandateId` / `agentId`. Partner Central gets `listingId` from `GET /v1/listings?mandate-id=`, and `GetMandateResponse` drops it. Map the version conflicts by entity name. |
| Y-A2 | Low | `ListingJobs` is a third hand-written per-tenant loop (with `ContractJobs`, `MandateJobs` and `KycJobs` there are four), and none of them sets a KV logger (X-P4). | `ListingJobs.kt:49-86` | A shared `TenantJobRunner` in `platform`. |
| Y-A3 | Low | The minimum photo count is set twice (`MIN_PHOTOS = 5` and `ndopify.listing.min-images: 5`). | `ListingSectionService.kt:23`, `ListingVerifierImage.kt:27` | One source. |
| Y-B1 | Low | **Partner Central maps photo errors wrongly.** On upload, every 4xx except 409 shows as "n'est pas une photo JPEG, PNG ou WEBP" (including 403/404). On delete, a 4xx except 409 counts as success. | `B/ui/listing/ListingPhotoController.kt:129,157-159` | 403/404 → `changed()`; other 4xx → `UPLOAD_FAILED` / `DELETE_FAILED`. |

**Strengths:**
- The listing pages work without JS: manual GPS fields that accept a decimal comma, and a multi-file form.
- A 409 re-renders the form with the agent's answers on top of the new version.
- Dates and amounts go through `TenantFormatter`.
- 176 new Partner Central tests, all with kotlin.test, and a test class for each new server listing class.

## Code quality and tooling

| ID | Status |
|---|---|
| N-Q1 | **Still present.** flyway 10.18.0 and spring-boot 4.0.3 against a 4.1.2 parent; slf4j, thymeleaf, jackson and spring-security are pinned; `pdfbox.vesion` (`pom.xml:81,87,117,123-133`). |
| JUnit assertions | **Still 10 files** (9 in the server plus `TenantAdviceTest`). None of them is new. |
| `Thread.sleep` / `@Ignore` | 6 (was 7) / 3. |
| N-Q4, N-Q5 | **Still present.** N-Q5 is worse: 3,529 lines of JS and no JS tests. |
| No Maven cache in CI, X-Q1 | **Still present.** The consumers pin DTO 0.0.70, the latest release. |
| X-Q2 | **Partly fixed.** CLAUDE.md is accurate except `CLAUDE.md:120` ("there is no `CONTRIBUTING.md`"). `CONTRIBUTING.md:93` names `IdentificationInfoExtractorAi` and `README.md:58` names `KycVerifier`; neither class exists. The README's domain list has no listing domain (`README.md:50-67`). |
| X-Q3, X-W3, X-W6, X-Q6, X-Q7 | **Still present.** |
| X-Q5 | **Partly fixed** (`commons-validator` is used now). `pdfbox` is still compile scope, `tika-langdetect` and `thymeleaf` are unused, AWS SDK v1 is still in, and `mockitokotlin2` appears in 618 imports. |
| X-W1 | **Mostly fixed.** The heading is still `# Ndopify Website`. |
| N-S9 / X-W7 | **Partly fixed:** the server deploy uses `env:`. Partner Central, e-signature and web still put `${{secrets.NDOPIFY_SERVER_URL}}` inline in `run:`, pipe an unpinned `curl … \| sh`, and grant `contents: write` to the whole workflow. |
| README badges | Present in all 5 module READMEs. |

| ID | Sev | Issue | Where | Fix |
|---|---|---|---|---|
| Y-Q1 | Medium | `DEEPSEEK_API_KEY` has no default, but CONTRIBUTING still says "two secrets" and doesn't list it, so a new developer's server doesn't start. | `CONTRIBUTING.md:31-44`, `R/application.yml:19` | Add it to the table and to the `export` block. |
| Y-Q2 | Low | No test covers listing access (the tests for Y-S1 and Y-S2) or sending a listing and then verifying it (Y-C1). That's why Y-C1 shipped. | `ndopify-server/src/test/.../listing/` | Write them as part of those fixes. |
| Y-Q3 | Low | Commits go straight to `main`, so ktlint only catches a problem after the merge (run `40603181718`). | `.github/workflows/*-pr.yml` | Protect `main` and require the PR checks, or run `mvn validate` in a local pre-push hook. |
| Y-Q4 | Low | Renovate PRs #70 (surefire) and #71 (jacoco) are open despite automerge. | `renovate.json` | Check why `_pr.yml` doesn't go green on them; add `packageRules`. |

## Recommended order

Small and easy to undo first:

1. **One-line config fixes:**
   - **Y-S3:** `DatabaseResetConfiguration` back to `@Profile("none")`, then rotate `JWT_SECRET`;
   - **Y-P1:** the prod listing crons;
   - **Y-P2:** `night.start: "23:00"`.
2. **Y-C1:** set `PENDING_VERIFICATION` in `verify()`, with the send-then-verify test. In the same change, **Y-C3**
   (truncate the LLM text) and **Y-P3** (download timeouts and temp file deletion).
3. **Y-S2:** anonymous listing reads return only PUBLISHED listings, through a public DTO.
4. **Y-S1 + Y-C4:** `ListingAccessService`, the mandate check in `create`, and the scoped image delete. Then **Y-S4**
   and **Y-S5** (server-side upload limits and field validation).
5. **X-P1 / M3 / ops endpoints:** expose only `health` and `info`, cache the LLM check, and require `isAdmin` on the ops
   endpoints.
6. **X-A1 + X-C1:** lock before loading in `onContractSigned`, `cancel` and `update`, plus **X-E2**.
7. **Y-C2:** recover interrupted verifications. **Y-P4:** `spring.ai.retry` settings.
8. **X-E1:** nullable `TenantAdvice` in e-signature and web, with `assertNull`.
9. **M5 + X-B1:** CSRF, logout as a POST, a SameSite session cookie, and a captcha on the public wizards.
10. **Docs and housekeeping:** Y-Q1, X-Q2, the One Tap leftovers, N-A5, X-Q3 / X-W3 / X-Q5, N-Q1 / X-Q6.
11. **Larger work:**
    - Y-A1: break the listing ↔ mandate cycle;
    - X-A5: a shared front-end core;
    - S2: access checks on the remaining open endpoints;
    - H2: OTP attempt limits.
