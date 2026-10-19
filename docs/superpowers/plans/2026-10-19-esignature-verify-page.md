# e-Signature — page de vérification (BFF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the prototype `verify.html` from `ndopify-esignature` at `{eSignaturePortalUrl}/verify/{contractId}/{signatureHash}`,
the URL that `SignedContractService` already prints as a QR code next to each signature of the signed PDF: anyone who
scans it sees, without signing in, whether the signature is genuine, with the contract's number, the signer's legal
name, the date and time of the signature and its hash, and can download the signed PDF.

**Architecture:** Every `/v1/contracts` endpoint needs a JWT today, so ndopify-server gets one **public** read:
`GET /v1/contracts/{id}/signatures/{hash}`, which finds the signed contract party of that contract whose
`signatureHash` is `{hash}` (tenant-filtered by `@TenantId`, like everything else) and returns only what the page shows,
else 404. The BFF adds `VerifyController` (`ui/verify/`), one Thymeleaf page with two states (`valid` / `invalid`), no
session, no cookie, no JS, built from the prototype.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA (MySQL), Spring MVC + Thymeleaf, `RestClient`, JUnit 5 +
mockito-kotlin + `kotlin.test`, `TestRestTemplate` endpoint tests, standalone `MockMvc`, `MockRestServiceServer`, Jsoup.

**Spec:**
- `modules/ndopify-esignature/src/main/html/verify.html` (+ the `Verification` block at the end of
  `src/main/html/assets/css/esignature.css`): the design and **the source of every French string this plan doesn't
  quote**. Its comment's `verify.html?id={contractPartyId}` is outdated: the URL is the one `SignedContractService`
  prints, `/verify/{contractId}/{signatureHash}`.
- `CLAUDE.md` (contract domain, *Signed PDF*): "The verify page isn't built yet."

## Global Constraints

- **Never modify or delete anything under `modules/ndopify-esignature/src/main/html/`** (the prototype). Copy from it.
- `ndopify-esignature` depends on `ndopify-dto` only; it calls ndopify-server over HTTP.
- The page is public and shows **only** the contract number, the signer's `legalName`, the signature's date and time,
  the hash and the signed PDF's link: never an e-mail, IP address, user agent, contract-party id, party id, salt or the
  content. The server DTO carries no more than that.
- `signatureHash` stays "stored only, never in a DTO" (`ContractHasher`): the endpoint takes it as input and doesn't
  echo it; the page prints the hash of its URL, once the server matched it.
- An unknown contract, an unknown hash, and another tenant's contract are the **same** 404
  (`CONTRACT_SIGNATURE_NOT_FOUND`): the endpoint is no oracle on contract ids.
- The endpoint is not `@OpsEndpoint` (it must be live in prod) and has no access check (no `ContractAccessService` call).
- Dates go through the BFF's `TenantFormatter`, printed like the PDF's block: `"${date(signedAt)} à ${time(signedAt)}"`.
- `ndopify-dto` changes → root `pom.xml` `ndopify-dto.version` = `0.0.57-SNAPSHOT` while developing (the version in
  `modules/ndopify-dto/pom.xml`), switched to the released `0.0.57` in Task 4.
- Assertions: `kotlin.test` only. `@MockitoBean`, never `@MockBean`.
- Gates: `cd modules/ndopify-server && mvn verify` (MySQL + Mailpit running) for Task 1;
  `cd modules/ndopify-esignature && mvn verify` for Tasks 2–4. Format with `mvn antrun:run@ktlint-format`.
- Every task updates `CLAUDE.md` for what it changes.

## Decisions taken without the user (check these while reading)

1. **Status-independent.** A signature recorded by Ndopify is "valide" whatever the contract's status since
   (`SIGNED`, `EXPIRED` past `validUntil`): the page certifies the signature, not that the contract is still in force.
   Only a contract party with `signed = true` and that exact hash matches.
2. **No download button without a signed PDF** (`signedContentUrl` null: its generation failed, or the contract still
   waits for another signer). The button links straight to the public storage URL (`download` attribute kept; browsers
   ignore it cross-origin and open the PDF, which is fine).
3. **Malformed hash** (not 64 hex characters after `trim().lowercase()`) → `invalid`, without calling the server.
   Uppercase hex is accepted (some QR readers upper-case URLs).
4. **Server unavailable** (5xx, I/O error, any 4xx but 404) → the exception propagates to Spring Boot's error page,
   **never** the `invalid` view: telling someone a genuine signature is forged because of an outage is the worst
   failure this page can have. No new copy is invented for it.
5. **HTTP 200 for both states**, `noindex`, `no-referrer` (as the prototype). No JS: the served `esignature.js` isn't
   loaded on this page (the prototype's `renderVerify` is prototype-only, `?state=invalid`).

## Review Focus

1. **Outage shown as forgery** — ndopify-server answers 500 or times out → error page, not "Cette signature n'est pas
   valide." (Task 3, `server error is not invalid`).
2. **QR reader mangling** — upper-cased hash, trailing whitespace → still `valid` (Task 3, `hash is normalized`).
3. **Cross-tenant QR** — a tenant-2 contract's QR opened on tenant 1's portal → 404, `invalid` (Task 1,
   `other tenant`).
4. **Leaking signer data** — the JSON has no `legalEmail`, `ipAddress`, `userAgent`, `partyId`, contract-party `id`
   (Task 1, `exposes nothing else`).
5. **Signature without PDF** — valid signature, `signedContentUrl` null → no button, no empty `href` (Task 3 template
   test `valid without pdf`).

---

### Task 1: ndopify-server — `GET /v1/contracts/{id}/signatures/{hash}`

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/ContractSignature.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/GetContractSignatureResponse.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (next to the `CONTRACT_*` codes)
- Modify: `pom.xml` (root): `ndopify-dto.version` → `0.0.57-SNAPSHOT`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/dao/ContractPartyRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mapper/ContractMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/endpoint/ContractEndpoints.kt`
  (route + class KDoc: "…except a signature's verification, which is public")
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/GetContractSignatureEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/resources/db/test/contract/GetContractSignatureEndpoint.sql`
- Modify: `CLAUDE.md`

**Interfaces:**
- Produces (dto):
  ```kotlin
  // What the public verification page shows of one signature: nothing else (no e-mail, IP, user agent, ids, hash)
  data class ContractSignature(
      val contractId: String = "",
      val contractNumber: String = "",
      val legalName: String = "",
      val signedAt: Date = Date(),
      val signedContentUrl: String? = null,
  )
  data class GetContractSignatureResponse(val signature: ContractSignature = ContractSignature())
  ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND = "$PREFIX:contract:signature-not-found"
  ```
- Produces (server): `GET /v1/contracts/{id}/signatures/{hash}` → `GetContractSignatureResponse`, 404
  `CONTRACT_SIGNATURE_NOT_FOUND`. Needs `X-Tenant-ID` (or a JWT's tenant) like every tenant-scoped read; no JWT needed.
- `ContractPartyRepository.findByContractAndSignatureHash(contract: ContractEntity, signatureHash: String): ContractPartyEntity?`
- `ContractService.findSignature(id: String, hash: String): Pair<ContractEntity, ContractPartyEntity>` — `findByIdOrNull`,
  then the repository method, keeping only `signed && signedAt != null`; anything missing →
  `NotFoundException(Error(code = ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND))`, including an unknown contract.
- `ContractMapper.toContractSignature(contract: ContractEntity, party: ContractPartyEntity): ContractSignature`

- [ ] **Step 1: Fixture `GetContractSignatureEndpoint.sql`** — tenant 1: contract `c-100` (`SIGNED`, number
  `MNDT-2026-000042`, `signed_content_url` `https://s3.example/c/MNDT-2026-000042-t0k3n.pdf`) with contract party
  `cp-100` (`MANDATOR`, `legal_name` `Ray Sponsible`, `legal_email` `ray@example.com`, signed, `signed_at`
  `2026-10-18 14:32:00`, `ip_address` / `user_agent` set, `signature_hash` = 64 × `a`) and `cp-101` (`AGENT`,
  unsigned, `signature_hash` NULL); contract `c-110` (`PENDING_SIGNATURE`, `signed_content_url` NULL) with signed
  `cp-110`, hash 64 × `c`; tenant 2: contract `c-200` with signed `cp-200`, hash 64 × `b`. Write enums by name.

- [ ] **Step 2: Write the failing tests** (`GetContractSignatureEndpointTest : TenantAwareEndpointIntegrationTest`,
  no JWT, `@Sql` the fixture):
  - `signature` — `GET /v1/contracts/c-100/signatures/aaa…a` → 200, `contractId == "c-100"`,
    `contractNumber == "MNDT-2026-000042"`, `legalName == "Ray Sponsible"`, `signedAt` equals the fixture's instant,
    `signedContentUrl` the fixture's.
  - `exposes nothing else` — same call as `String`; parse with Jackson: the `signature` object's field names are
    exactly `{contractId, contractNumber, legalName, signedAt, signedContentUrl}`.
  - `pending contract` — `c-110` / 64 × `c` → 200, `signedContentUrl == null`.
  - `unknown hash` — `c-100` / 64 × `f` → 404 `CONTRACT_SIGNATURE_NOT_FOUND`.
  - `unknown contract` — `xxx` / 64 × `a` → 404 `CONTRACT_SIGNATURE_NOT_FOUND` (not `CONTRACT_NOT_FOUND`).
  - `hash of another contract` — `c-110` / 64 × `a` → 404 `CONTRACT_SIGNATURE_NOT_FOUND`.
  - `other tenant` — `c-200` / 64 × `b` with `X-Tenant-ID: 1` → 404 `CONTRACT_SIGNATURE_NOT_FOUND`.

- [ ] **Step 3: Run** `cd modules/ndopify-server && mvn test -Dtest=GetContractSignatureEndpointTest` — FAIL (no route:
  404 `HTTP_NOT_FOUND` / missing DTO classes). Install the DTO first: `cd modules/ndopify-dto && mvn install`.

- [ ] **Step 4: Implement** the DTOs, the error code, the repository method, `findSignature`, the mapper and the
  `@GetMapping("/{id}/signatures/{hash}")` endpoint (no `access` call; a one-line comment saying why it's public).

- [ ] **Step 5: Run** the test class, then `mvn verify` — PASS, coverage gate green.

- [ ] **Step 6: `CLAUDE.md`** — in the contract domain: add the endpoint to the `/v1/contracts` list; in **Access**,
  "every endpoint needs a JWT" → "every endpoint but the signature's verification (`GET /{id}/signatures/{hash}`,
  public: the contract number, legal name, `signedAt` and `signedContentUrl` of a signed contract party with that
  hash, else 404 `CONTRACT_SIGNATURE_NOT_FOUND`) needs a JWT".

- [ ] **Step 7: Commit** — `git commit -m "contract: public signature verification endpoint"`

---

### Task 2: BFF client — `NdContractClient.getSignature`

**Files:**
- Modify: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/client/ndopify/NdContractClient.kt`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/client/ndopify/NdContractClientTest.kt`

**Interfaces:**
- Consumes: `GetContractSignatureResponse`, `ContractSignature` (Task 1).
- Produces: `fun getSignature(id: String, hash: String): ContractSignature?` — `GET /v1/contracts/{id}/signatures/{hash}`;
  `null` on 404; every other error is rethrown (decision 4).

- [ ] **Step 1: Failing tests** (`MockRestServiceServer`, as the class's other tests):
  `getSignature` (request URI `/v1/contracts/c-1/signatures/<hash>`, body mapped, `legalName` / `signedAt` asserted),
  `getSignature not found` (404 → `assertNull`), `getSignature server error` (500 →
  `assertFailsWith<HttpServerErrorException>`).
- [ ] **Step 2: Run** `cd modules/ndopify-esignature && mvn test -Dtest=NdContractClientTest` — FAIL.
- [ ] **Step 3: Implement** with `.retrieve()` + `onStatus { it.value() == 404 }` or a caught
  `HttpClientErrorException.NotFound`, whichever matches the file's style.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `git commit -m "esignature: contract signature client"`

---

### Task 3: BFF page — `/verify/{contractId}/{signatureHash}`

**Files:**
- Create: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyView.kt`
- Create: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyController.kt`
- Create: `modules/ndopify-esignature/src/main/resources/templates/verify.html`
- Modify: `modules/ndopify-esignature/src/main/resources/public/assets/css/esignature.css` — append the prototype's
  `/* ---------- Verification ---------- */` block (`h2.alert__title`, `.hash`) verbatim
- Modify: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/config/SecurityConfiguration.kt` —
  `.requestMatchers("/verify/**").permitAll()`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyControllerTest.kt`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyTemplateTest.kt`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `NdContractClient.getSignature(id, hash): ContractSignature?` (Task 2); `TenantContext.getTenant()`,
  `TenantFormatter(tenant).date(...)` / `.time(...)` (as `SignViews` builds it).
- Produces:
  ```kotlin
  // What verify.html shows (model attribute `view`); everything null when invalid
  data class VerifyView(
      val valid: Boolean,
      val contractNumber: String? = null,
      val signerName: String? = null,
      val signedAt: String? = null,      // "18 octobre 2026 à 14 h 32", by TenantFormatter
      val signatureHash: String? = null, // the URL's, normalized: only set when the server matched it
      val signedContentUrl: String? = null,
  )
  VerifyController.VIEW = "verify"
  ```
  `GET /verify/{contractId}/{signatureHash}` → `"verify"` with `view`. Normalize `signatureHash.trim().lowercase()`;
  not `^[0-9a-f]{64}$` → `VerifyView(valid = false)` without any call.

- [ ] **Step 1: Failing controller tests** (standalone `MockMvc`, mocked `NdContractClient` and `TenantContext`,
  fixtures from `TenantFixtures`):
  - `valid` — client returns a signature → `view.valid`, number, name, `signedAt == formatter.date(d) + " à " +
    formatter.time(d)`, `signatureHash` = the URL's, `signedContentUrl`.
  - `hash is normalized` — URL hash upper-case (`%20`-padded) → client called with the lower-case 64-char hash, valid.
  - `not found` — client returns null → `VerifyView(valid = false)`.
  - `malformed hash` — `/verify/c-1/xyz` → invalid, `verifyNoInteractions(client)`.
  - `server error is not invalid` — client throws `HttpServerErrorException(500)` → the exception propagates
    (`assertFailsWith<ServletException>` around `perform`, or no `view` rendered): never the invalid view.
- [ ] **Step 2: Failing template tests** (`VerifyTemplateTest`, the `SignTemplateTest` harness with
  `@GetMapping("/verify/{id}/{hash}")`):
  - `valid` — `h1` "Vérification de la signature"; `#valid-title` "Cette signature est valide."; the four `dd`s show
    the view's values; `.hash` holds the hash; `a[download]` `href` = `signedContentUrl`; no `#invalid-title`.
  - `valid without pdf` — no `a[download]`, and no `.cta-row`.
  - `invalid` — `#invalid-title` "Cette signature n'est pas valide."; no `dl.review`, no `#valid-title`.
  - `tenant logo` — `tenant.logoUrl` set → header `img` `src` is it; `tenant` null → `/assets/images/logo.png`.
  - `no script` — no `script[src]` in the page (decision 5).
- [ ] **Step 3: Run** `mvn test -Dtest='Verify*Test'` — FAIL.
- [ ] **Step 4: `templates/verify.html`** — copy `src/main/html/verify.html`: `th:href` / `th:src` assets as in
  `sign.html`, the tenant-logo header of `sign.html`, `data-page="verify"`, drop the `<script>`, replace the prototype
  comment with a Thymeleaf comment (`<!--/* … */-->`) naming `VerifyController` / `VerifyView`; render only the
  section of the state (`th:if="${view.valid}"` / `th:unless`, no `hidden`, no `data-verify-state`); the CTA row
  `th:if="${view.signedContentUrl != null}"`. All French copy verbatim from the prototype.
- [ ] **Step 5: `VerifyView`, `VerifyController`, the CSS block and the security matcher.**
- [ ] **Step 6: Run** `mvn verify` — PASS, coverage gate green.
- [ ] **Step 7: Manual check** — server (8080) + esignature (8083) running, a SIGNED contract with its PDF: scan /
  open the PDF's QR URL → valid page, PDF downloads; change one hash character → invalid page; stop the server →
  error page, not "invalide".
- [ ] **Step 8: `CLAUDE.md`** — `ndopify-esignature`: `SecurityConfiguration` permits `/verify/**` too; a
  `VerifyController` bullet (public, no session/cookie/JS, normalization, 404 → invalid, any other error → error
  page, never shows more than the five fields); contract domain: drop "The verify page isn't built yet."
- [ ] **Step 9: Commit** — `git commit -m "esignature: signature verification page"`

---

### Task 4: Release, docs

**Files:**
- Modify: `pom.xml` (root): `ndopify-dto.version` → `0.0.57`
- Modify: `modules/ndopify-esignature/README.md` (targeted edit, badges untouched): the `/verify/{contractId}/{hash}` route
- Modify: `modules/ndopify-server/README.md` only if it lists endpoints (targeted edit)

- [ ] **Step 1:** Ask the user to release ndopify-dto `0.0.57`; set the root property; `mvn install -Dheadless=true` at
  the root — BUILD SUCCESS.
- [ ] **Step 2:** README edits; `git diff` shows the badge lines unchanged.
- [ ] **Step 3: Commit** — `git commit -m "esignature: verify page docs, ndopify-dto 0.0.57"`
