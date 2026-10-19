# e-Signature — page de signature (BFF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the prototype `sign.html` from `ndopify-esignature` at `{eSignaturePortalUrl}/sign/{contractPartyId}`
(the link `ContractSignatureMailet` already sends): the signer signs in with their e-mail and a one-time code, reads
the contract, and signs it. Every step works without JS; JS only enhances.

**Architecture:** Signers get a **membership**, created only when needed: `ContractService.requestSignature` gives
each party that must sign (and hasn't) a membership with `e-signature` access, reusing the one it has (an agent's)
or creating the user and membership. Nothing is created when an agent or customer is. From there everything already
exists: `POST /v1/otp`,
`POST /v1/auth` (`AuthType.OTP`, `ApplicationCode.E_SIGNATURE`), `GET /v1/contracts/{id}` and `POST .../sign`, whose
rule (JWT party == contract party, no admin bypass) is unchanged. The only other server change is a `contract-party-id`
search filter, so the BFF can go from the link's contract party to its contract. The BFF (`SignController`) is built
like Partner Central's login: one Thymeleaf page, post-redirect-get, a session `PendingSignature` for the code step, and
the JWT in an HTTP-only cookie.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA (MySQL), Spring MVC + Thymeleaf, `RestClient`, Jsoup, JUnit 5 +
mockito-kotlin + `kotlin.test`, `TestRestTemplate` endpoint tests, standalone `MockMvc`, `MockRestServiceServer`,
plain ES5 JS.

**Spec:**
- `modules/ndopify-esignature/src/main/html/sign.html` (+ `assets/js/esignature.js`, `assets/css/esignature.css`):
  the design and **the source of every French string this plan doesn't quote**.
- `modules/ndopify-esignature/ux/journeys/esignature-journeys.md`: steps, blocked states, persona requirements.
- User decisions (2026-10-19): **memberships** for signers, **created when the signature is requested** (not when
  the agent / customer is, to avoid junk data); **the signer types their e-mail** on the intro (no new
  OTP endpoint); **nothing about the contract is shown before sign-in**; **no signed-copy e-mail** (out of scope, so
  the copy that promises one is reworded).

## Global Constraints

- **Never modify or delete anything under `modules/ndopify-esignature/src/main/html/`** (the prototype). Copy from it.
- `ndopify-esignature` depends on `ndopify-dto` only. Copy the Partner Central infrastructure classes it needs, in
  package `com.wutsi.ndopify.esignature`.
- Signing stays personal: `ContractAccessService` is **not** changed. No admin bypass, nobody signs on another's behalf.
- The contract party, contract and party ids come from the URL and the token, never from a form field.
- No membership is created when an agent or customer is created for this feature: only `requestSignature` creates
  one, and only for the parties asked to sign.
- `ndopify-dto` changes → root `pom.xml` `ndopify-dto.version` = `0.0.53-SNAPSHOT` while developing (the version in
  `modules/ndopify-dto/pom.xml`), switched to the released `0.0.53` in Task 7.
- Assertions: `kotlin.test` only. `@MockitoBean`, never `@MockBean`.
- Gates: `cd modules/ndopify-server && mvn verify` (MySQL + Mailpit running) for Tasks 1–2;
  `cd modules/ndopify-esignature && mvn verify` for Tasks 3–7. Format with `mvn antrun:run@ktlint-format`.
- Every task updates `CLAUDE.md` for what it changes.

## Decisions taken without the user (check these while reading)

1. **Only the parties asked to sign** (`signatureRequired && !signed`) get access: a mandate's agent
   (`signatureRequired = false`) gets nothing. The new user's e-mail is the contract party's `legalEmail`, where the
   signature e-mail goes, so the code reaches the same inbox. Granted in `requestSignature`'s transaction: if it
   fails, the request fails and no e-mail goes out.
2. **E-mail already used by another party's membership** in the tenant (the party's e-mail was changed since): 409
   `CONTRACT_PARTY_MEMBERSHIP_CONFLICT` and nothing is requested, rather than a signer who can never sign.
3. **Wrong account**: signed in, but the account's party isn't the link's contract party (the search comes back
   empty) → the view `not-yours`, with "Utiliser une autre adresse" (clears the cookie, back to the intro).
4. **Blocked states** (expired, cancelled, already signed, signature not required, unavailable, not-yours) come after
   sign-in, as the intro's alerts. The agent is named (the `AGENT` party's `legalName`) **without the WhatsApp number**.
5. **Contract details** (type, sender, number, deadline) move from the intro to the top of the `sign` step.
6. **Contract content**: the BFF downloads `contentUrl` (HTML) and renders it inline, sanitized with Jsoup
   `Safelist.relaxed()`. If it can't be fetched, the sign step shows an error and **no sign button**.
7. **Cookie** `__nes_access_token`, `Path=/`, 1 hour (the token's TTL): one sign-in covers every link of the same
   person.

## Review Focus

1. **Signed in as someone else** (shared family phone, previous signer's cookie): never show another party's contract.
   The search is restricted server-side to the caller's party; Task 5 `other account sees not yours`.
2. **E-mail typed with spaces or capitals** (`" Ray.Sponsible@Gmail.com "`): trimmed and lowercased, as Partner
   Central's login. Task 5 `email is normalized`.
3. **A signer who already has a membership** (an agent signing as a customer, or a second request of the same
   contract): reused, `e-signature` added once, no second user. Task 1 `existing membership is reused`, `second request
   creates nothing`.
4. **A contract whose HTML can't be fetched, or contains `<script>`**: never signable unseen, never runs script.
   Task 4 `script is stripped`, Task 5 `no sign button without content`.
5. **Double submit of "Signer électroniquement"**: the second POST's 409 `CONTRACT_PARTY_ALREADY_SIGNED` lands on the
   confirmation, not an error. Task 5 `second sign shows done`.

---

## Part A — ndopify-dto + ndopify-server

### Task 1: Signers get `e-signature` access when the signature is requested

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractSignerService.kt`
- Modify: `.../contract/server/service/ContractService.kt` (`requestSignature` calls it, after `ensureNotClosed`,
  before the save)
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Modify: `pom.xml` (root): `ndopify-dto.version` → `0.0.53-SNAPSHOT`
- Test: `.../contract/server/endpoint/RequestContractSignatureEndpointTest.kt` (+ fixture),
  `.../security/server/endpoints/AuthenticateOtpEndpointTest.kt` (+ fixture)

**Interfaces:**
- Consumes: `MembershipService.findByPartyIdOrNull(partyId)`, `UserService.findByEmailOrCreate(email)`,
  `MembershipService.findOrCreate(user, party)`, `MembershipService.grantAccess(membership, ApplicationCode.E_SIGNATURE)`,
  `PartyService.findById(id)`.
- Produces:
  - `ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT = "$PREFIX:contract:party-membership-conflict"`
  - `ContractSignerService.grantAccess(parties: List<ContractPartyEntity>)`, for each party with
    `signatureRequired && !signed`: membership = `findByPartyIdOrNull(partyId)` ?: `findOrCreate(findByEmailOrCreate(
    legalEmail), party)`; if that membership's party isn't `partyId` → 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`; then
    `grantAccess(membership, E_SIGNATURE)`. Idempotent. Runs in `requestSignature`'s transaction.
  - Nothing else creates a membership for a customer: `CustomerService` and `AgentService` are unchanged.

- [ ] **Step 1: Write the failing tests.** `RequestContractSignatureEndpointTest`: `signer gets e-signature access`
  (MANDATOR party with no user → one `T_USER` with its `legal_email`, one `T_MEMBERSHIP` for its `party_id` in tenant
  1, one `T_USER_APPLICATION` for application 4); `party not asked to sign gets nothing` (the AGENT party with
  `signature_required = false` and no membership → still none); `existing membership is reused` (party with a
  membership + partner-central access → no new user, applications 3 and 4); `second request creates nothing` (two
  requests → one of each); `email of another party's member` (`legal_email` = a user who is a member as another party →
  409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`, contract still DRAFT, no e-mail sent). `AuthenticateOtpEndpointTest`:
  `signer signs in to e-signature` (membership with app 4, OTP, `applicationCode = E_SIGNATURE` → 200, token's
  `application == "e-signature"`); `member without access cannot sign in to e-signature` (409 `AUTH_ACCESS_DENIED`).
- [ ] **Step 2: Run** `cd modules/ndopify-dto && mvn install -Dheadless=true`, then
  `cd ../ndopify-server && mvn test -Dtest='RequestContractSignatureEndpointTest,AuthenticateOtpEndpointTest'`.
  Expected: the new cases FAIL.
  One test, for the shape of the others (the fixture's MANDATOR `cp-11` has `party_id = 100`, no user):

```kotlin
@Test
fun `signer gets e-signature access`() {
    val result = rest.postForEntity("/v1/contracts/c-1/request-signature", null, Any::class.java)

    assertEquals(HttpStatus.OK, result.statusCode)
    val user = userDao.findByEmail("ray.sponsible@gmail.com")!!
    val membership = membershipDao.findByUser(user)!!
    assertEquals(100L, membership.party?.id)
    val application = applicationDao.findByCode(ApplicationCode.E_SIGNATURE)!!
    assertEquals(1, userApplicationDao.findByMembershipAndApplication(membership, application).toList().size)
}
```

- [ ] **Step 3: Implement** `ContractSignerService`, then call it from `requestSignature`:

```kotlin
/**
 * Gives the parties asked to sign a contract what they need to sign in to the e-signature portal: a membership of the
 * tenant, as their party, with `e-signature` access. Created here, when a signature is requested, rather than when an
 * agent or a customer is: only the people actually asked to sign get an account. Runs in the caller's transaction.
 */
@Service
class ContractSignerService(
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val partyService: PartyService,
) {
    fun grantAccess(parties: List<ContractPartyEntity>) {
        parties
            .filter { party -> party.signatureRequired && !party.signed }
            .forEach { party -> grantAccess(party) }
    }

    private fun grantAccess(contractParty: ContractPartyEntity) {
        // An agent already has one; anyone else gets a user from the address the signature e-mail goes to
        val membership = membershipService.findByPartyIdOrNull(contractParty.partyId)
            ?: membershipService.findOrCreate(
                user = userService.findByEmailOrCreate(contractParty.legalEmail),
                party = partyService.findById(contractParty.partyId),
            )

        // The address is already a member's, as another party: that member could never sign as this one
        if (membership.party?.id != contractParty.partyId) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT,
                    message = "${contractParty.legalEmail} is a member as another party",
                )
            )
        }
        membershipService.grantAccess(membership, ApplicationCode.E_SIGNATURE)
    }
}
```

```kotlin
// ContractService.requestSignature
val contract = findByIdForUpdate(id)

ensureNotClosed(contract)
signerService.grantAccess(partyDao.findByContract(contract)) // Before the save: a 409 leaves the contract as it was
val pending = dao.save(
```

- [ ] **Step 4: Run** the same, then `mvn verify` (the mandate's `request-approval` tests go through
  `requestSignature` too). Expected: PASS.
- [ ] **Step 5:** `CLAUDE.md`: security ("only registration (`AgentService.create`) does" → and a request for signature
  (`ContractSignerService`), for the parties asked to sign, with `e-signature` access); contract ("Customers have no
  membership yet … can't sign until a customer sign-in exists" → they get one when asked to sign, and sign in to the
  e-signature portal by OTP; the 409).
- [ ] **Step 6: Commit** `feat(contract): signers get e-signature access when asked to sign`.

### Task 2: `contract-party-id` search filter

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/SearchContractRequest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt` (`search`)
- Test: `.../contract/server/endpoint/SearchContractEndpointTest.kt` (+ fixture)

**Interfaces:**
- Produces: `SearchContractRequest.contractPartyIds: List<String>` bound to `contract-party-id`; `search` filters with
  a subquery on `ContractPartyEntity.id`, like the `partyIds` one. Access unchanged: a non-admin's search is still
  forced to its own `party-id`, so another party's contract party returns nothing.

- [ ] **Step 1: Write the failing tests**: `search by contract party` (`?contract-party-id=cp-11` as the party of cp-11
  → `[c-1]`); `contract party of another party returns nothing` (`?contract-party-id=cp-21` as party of cp-11 → `[]`).
- [ ] **Step 2: Run** `cd modules/ndopify-dto && mvn install -Dheadless=true`, then
  `cd ../ndopify-server && mvn test -Dtest=SearchContractEndpointTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** The DTO field, after `partyIds`, and the predicate in `ContractService.search`, after the
  `partyIds` one:

```kotlin
@param:BindParam("contract-party-id") val contractPartyIds: List<String> = emptyList(),
```

```kotlin
if (request.contractPartyIds.isNotEmpty()) {
    val sub = query!!.subquery(String::class.java)
    val party = sub.from(ContractPartyEntity::class.java)
    sub.select(party.get<ContractEntity>("contract").get("id"))
        .where(party.get<String>("id").`in`(request.contractPartyIds))
    predicates.add(root.get<String>("id").`in`(sub))
}
```
- [ ] **Step 4: Run** the same, then `mvn verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(contract): search contracts by contract party`.

---

## Part B — ndopify-esignature

### Task 3: BFF infrastructure

**Files:** (package `com.wutsi.ndopify.esignature`, copied from `com.wutsi.ndopify.npc` and adapted)
- Modify: `modules/ndopify-esignature/pom.xml`: add `org.jsoup:jsoup` (`${jsoup.version}`); remove `tika-core`,
  `select2-java-selenium`, `selenium-java`, `bcprov-jdk18on`.
- Modify: `src/main/resources/application.yml`: drop the `multipart` block (no upload here).
- Create: `config/ClockConfiguration.kt`, `config/RestConfiguration.kt`, `config/SecurityConfiguration.kt` (permit
  `/assets/**`, `/sign/**`, `/error`; nothing else is served)
- Create: `client/LoggingClientHttpRequestInterceptor.kt`, `client/TenantIdClientHttpRequestInterceptor.kt`,
  `client/AuthorizationClientHttpRequestInterceptor.kt`, `client/ndopify/NdTenantClient.kt`
- Create: `service/TenantContext.kt` (matches `tenant.eSignaturePortalUrl`), `service/TenantFormatter.kt`
- Create: `service/security/AccessTokenService.kt` (cookie `__nes_access_token`), `JwtDecoder.kt`,
  `JwtAuthentication.kt`, `JwtAuthenticationFilter.kt`
- Create: `ui/layout/HttpClientErrorHandler.kt`, `templates/error/403.html`, `404.html` (header: logo only)
- Delete: `src/test/kotlin/com/wutsi/ndopify/esignature/DummyTest.kt`
- Test: Partner Central's test of each copied class, adapted (`TenantContextTest` matches `eSignaturePortalUrl`, not
  `partnerCentralUrl`).

**Interfaces:**
- Produces: the same names and signatures as Partner Central's classes, in the new package. In particular
  `AccessTokenService.set(accessToken)`, `.get(): String?`, `.remove()` (cookie `__nes_access_token`, `Path=/`), and
  `JwtAuthenticationFilter`, which puts a `JwtAuthentication` in the security context for a token that decodes.

- [ ] **Step 1: Write the failing tests** (copied and adapted).
- [ ] **Step 2: Run** `cd modules/ndopify-esignature && mvn test`. Expected: FAIL (compile).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `mvn test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(esignature): BFF infrastructure`.

### Task 4: ndopify-server clients

**Files:**
- Create: `client/ndopify/NdOtpClient.kt`, `NdAuthClient.kt` (copies), `NdContractClient.kt`,
  `client/ContractContentClient.kt`
- Modify: `config/RestConfiguration.kt` (`contentRestClient` bean)
- Test: one `MockRestServiceServer` test per client (`ContractContentClientTest` binds the server to a
  `RestClient.builder()` it passes to the constructor)

**Interfaces:**
- Produces:
  - `NdOtpClient.create(request: CreateOtpRequest)`, `NdAuthClient.authenticate(request: AuthenticateRequest):
    AuthenticateResponse` (as Partner Central's)
  - `NdContractClient`: Partner Central's (`get(id: String): Contract`, `search(request: SearchContractRequest):
    List<ContractSummary>`), plus `request.contractPartyIds.forEach { uri.queryParam("contract-party-id", it) }` in
    `search` and `sign(id: String, request: SignContractRequest)` (`POST /v1/contracts/{id}/sign`).
  - `RestConfiguration.contentRestClient(): RestClient` (`@Bean`, `RestClient.create()`): not built from the Spring
    `RestClient.Builder`, so the `Authorization` / `X-Tenant-ID` interceptors never reach a storage URL.
  - `ContractContentClient(@Qualifier("contentRestClient") rest: RestClient).fetch(url: String): String?`

```kotlin
/** The contract's document (HTML), cleaned to be shown in the page. Null when it can't be: the page then can't sign. */
fun fetch(url: String): String? =
    try {
        val response = rest.get().uri(URI.create(url)).retrieve().toEntity(String::class.java)
        if (response.headers.contentType?.isCompatibleWith(MediaType.TEXT_HTML) != true) {
            null
        } else {
            // Body only, and no script, style or event handler: the page renders it with th:utext
            response.body?.let { html -> Jsoup.clean(Jsoup.parse(html).body().html(), Safelist.relaxed()) }
        }
    } catch (ex: Exception) {
        LOGGER.warn("Unable to fetch the contract content: $url", ex)
        null
    }
```

- [ ] **Step 1: Write the failing tests**: paths, query parameters and bodies; `ContractContentClientTest`:
  `returns body html`, `script is stripped` (`<script>`, `onclick` gone; `<h4>`, `<ul>` kept), `null on 404`,
  `null on non html`, `no authorization header`.
- [ ] **Step 2: Run** `mvn test -Dtest='Nd*ClientTest,ContractContentClientTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same. Expected: PASS.
- [ ] **Step 5: Commit** `feat(esignature): ndopify-server clients`.

### Task 5: `SignController`

**Files:**
- Create: `ui/sign/SignController.kt`, `ui/sign/PendingSignature.kt`, `ui/sign/SignForm.kt`, `ui/sign/VerifyForm.kt`,
  `ui/sign/SignView.kt`, `ui/sign/SignViews.kt`
- Test: `ui/sign/SignControllerTest.kt` (standalone `MockMvc`), `ui/sign/SignViewsTest.kt`

**Interfaces:**
- Consumes: Task 3's `AccessTokenService`, `TenantFormatter`; Task 4's clients.
- Produces:
  - `PendingSignature(contractPartyId: String, email: String, expiresAt: Long) : Serializable`
    (`SESSION_ATTRIBUTE`); the code step shows only while it's in the session for **this** `contractPartyId`, and
    verify reads the e-mail from it, never from the form.
  - `SignView` (model `view`): `step` (`intro|code|sign|done|blocked`), `blocked`
    (`expired|cancelled|signed|not-required|unavailable|not-yours`, else null), `contractType`, `contractNumber`,
    `senderName` (nullable), `deadline`, `signerName`, `signerFirstName`, `signDate`, `signedAt`, `contentHtml`
    (nullable), `canSign`, `maskedEmail`, `codeExpiresAt`.
  - `SignViews` (`@Service`, `Clock` + `TenantContext` for the `TenantFormatter`):
    `build(contract: Contract?, contractPartyId: String, requestedStep: String?, fetchContent: (String) -> String?):
    SignView`. The content is only fetched for the `sign` step. Type labels: copy `ContractSignatureMailet.TYPES`.
    `maskEmail`: copy `LoginController.maskEmail`.

```kotlin
fun build(
    contract: Contract?,
    contractPartyId: String,
    requestedStep: String?,
    fetchContent: (String) -> String?,
): SignView {
    // Not found by a search restricted to the caller's party: the link is someone else's
    contract ?: return blocked(NOT_YOURS, null)
    val party = contract.parties.find { party -> party.id == contractPartyId }
        ?: return blocked(UNAVAILABLE, contract)

    val now = Date(clock.millis())
    val reason = when {
        party.signed -> if (requestedStep == STEP_DONE) return done(contract, party) else SIGNED
        !party.signatureRequired -> NOT_REQUIRED
        contract.status == ContractStatus.CANCELLED -> CANCELLED
        // EXPIRED is only set by the daily job: a deadline already past is expired too
        contract.status == ContractStatus.EXPIRED || contract.signatureDeadline?.before(now) == true -> EXPIRED
        contract.status == ContractStatus.PENDING_SIGNATURE -> null
        else -> UNAVAILABLE
    }
    if (reason != null) {
        return blocked(reason, contract)
    }

    val content = contract.contentUrl?.let(fetchContent)
    return sign(contract, party, content).copy(canSign = content != null)
}
```

  - Routes, under `/sign/{contractPartyId}`:
    - `GET ?step=`: signed in (a `JwtAuthentication` in the security context) → `search(contract-party-id)`, first
      result → `get(id)` (none → `not-yours`); signed out → `code` if `step=code` and pending, else `intro`.
    - `POST /otp` (`email`, or `resend=true` reusing the pending e-mail): trimmed + lowercased, `NdOtpClient.create(ttl
      = 300)`, pending set, redirect `?step=code`. 404 `USER_NOT_FOUND` → intro, the typed e-mail kept, error
      `"Aucun contrat n'a été envoyé à cette adresse. Utilisez l'adresse e-mail à laquelle vous avez reçu ce lien."`;
      other errors → the prototype's `data-intro-error` text.
    - `POST /verify` (`code`, digits only): no pending → redirect intro. `authenticate(email, E_SIGNATURE, OTP, code)`
      → `AccessTokenService.set`, pending removed, redirect `?step=sign`. Errors: as `LoginController.toMessage`, with
      the prototype's wording for expired / wrong, and `AUTH_ACCESS_DENIED` → the `USER_NOT_FOUND` text above. The
      typed code stays in the field.
    - `POST /` (`consent`): no token → redirect intro. `consent != "yes"` → sign step with `"Cochez la case pour signer
      le contrat."`. Else `sign(contractId, SignContractRequest(partyId = contractPartyId, ipAddress = first
      X-Forwarded-For else remoteAddr, userAgent))` → redirect `?step=done`; 403 / 404 / 409 → redirect `?step=done`
      too (`SignViews` shows done if the party has signed, else the blocked state); other errors → sign step with the
      prototype's `data-sign-error` text.
    - `POST /logout`: `AccessTokenService.remove()`, session invalidated, redirect to the intro.

  The heart of the controller (the rest follows `LoginController`):

```kotlin
@GetMapping
fun show(
    @PathVariable contractPartyId: String,
    @RequestParam(name = "step", required = false) step: String?,
    session: HttpSession,
    model: Model,
): String {
    if (!signedIn()) {
        val pending = pending(session, contractPartyId)
        return if (step == STEP_CODE && pending != null) codeStep(pending, model) else introStep(null, model)
    }

    val view = views.build(findContract(contractPartyId), contractPartyId, step) { url -> contentClient.fetch(url) }
    return render(contractPartyId, view, model)
}

@PostMapping("/verify")
fun verify(
    @PathVariable contractPartyId: String,
    @ModelAttribute form: VerifyForm,
    session: HttpSession,
    model: Model,
): String {
    // The e-mail comes from the session, never the form: it can't be swapped at verify time
    val pending = pending(session, contractPartyId) ?: return "redirect:/sign/$contractPartyId"
    val code = form.code.filter { it.isDigit() }

    val accessToken = try {
        authClient.authenticate(
            AuthenticateRequest(
                email = pending.email,
                applicationCode = ApplicationCode.E_SIGNATURE,
                authType = AuthType.OTP,
                secret = code,
            ),
        ).accessToken
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)
        model.addAttribute("error", toCodeMessage(ex))
        model.addAttribute("code", code)
        return codeStep(pending, model)
    }

    session.removeAttribute(PendingSignature.SESSION_ATTRIBUTE)
    accessTokenService.set(accessToken)
    return "redirect:/sign/$contractPartyId?step=$STEP_SIGN"
}

@PostMapping
fun sign(
    @PathVariable contractPartyId: String,
    @ModelAttribute form: SignForm,
    request: HttpServletRequest,
    model: Model,
): String {
    if (!signedIn()) return "redirect:/sign/$contractPartyId"
    val contract = findContract(contractPartyId) ?: return "redirect:/sign/$contractPartyId"
    if (form.consent != "yes") {
        model.addAttribute("error", CONSENT_REQUIRED)
        return render(contractPartyId, views.build(contract, contractPartyId, STEP_SIGN, contentClient::fetch), model)
    }

    try {
        contractClient.sign(
            contract.id,
            SignContractRequest(
                partyId = contractPartyId,
                ipAddress = request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.ifEmpty { null }
                    ?: request.remoteAddr,
                userAgent = request.getHeader(HttpHeaders.USER_AGENT),
            ),
        )
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)
        if (ex.statusCode.value() !in setOf(403, 404, 409)) {
            model.addAttribute("error", SIGN_FAILED)
            return render(contractPartyId, views.build(contract, contractPartyId, STEP_SIGN, contentClient::fetch), model)
        }
        // Signed already (a double tap), or no longer signable: the page says which
    }
    return "redirect:/sign/$contractPartyId?step=$STEP_DONE"
}

private fun signedIn(): Boolean = SecurityContextHolder.getContext().authentication is JwtAuthentication

// Restricted by ndopify-server to the caller's party: another party's contract party finds nothing
private fun findContract(contractPartyId: String): Contract? =
    contractClient.search(SearchContractRequest(contractPartyIds = listOf(contractPartyId), limit = 1))
        .firstOrNull()
        ?.let { summary -> contractClient.get(summary.id) }
```

- [ ] **Step 1: Write the failing tests.** `SignViewsTest`: one per rule above, `deadline past is expired`, `sender is
  agent party`, `no sender without agent party`. `SignControllerTest`: `intro without token`, `code step needs
  pending`, `pending of another link shows intro`, `email is normalized` (`" Ray.Sponsible@Gmail.com "` →
  `ray.sponsible@gmail.com` sent), `unknown email keeps it in the field`, `resend reuses pending email`, `verify sets
  cookie and opens sign` (`applicationCode == "e-signature"`), `wrong code keeps code`, `sign step shows content`, `no
  sign button without content`, `other account sees not yours` (search returns `[]`; `get` and content never called),
  `consent required`, `signs with ip and user agent`, `second sign shows done`, `logout clears cookie`, `blocked states
  never show contract content`.
- [ ] **Step 2: Run** `mvn test -Dtest='SignControllerTest,SignViewsTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same. Expected: PASS.
- [ ] **Step 5: Commit** `feat(esignature): sign controller`.

### Task 6: Template, assets and JS

**Files:**
- Create: `src/main/resources/templates/sign.html` (from the prototype)
- Create: `src/main/resources/public/assets/css/esignature.css`, `public/assets/images/{logo,icon}.png` (copies),
  `public/assets/js/esignature.js` (served version)
- Test: `ui/sign/SignTemplateTest.kt` (real Thymeleaf engine + Jsoup, like Partner Central's `JoinTemplateTest`)

**Interfaces:**
- Consumes: Task 5's `SignView` and model attributes `error`, `email`, `code`, `resent`.

Template rules (where it differs from the prototype):
- One `<form method="post">` per action, posting to Task 5's routes; only the current step is rendered (`th:if`).
  "Étape N sur 3" server-rendered. Précédent on `code` links to `/sign/{id}`.
- **Intro** (nothing about the contract): no `dl.review`, no masked e-mail. Copy: `"Un contrat attend votre
  signature. Pour votre sécurité, seule la personne à qui il est destiné peut le signer. Saisissez l'adresse e-mail à
  laquelle vous avez reçu ce lien : nous allons vous y envoyer un code de vérification à 6 chiffres."` Field: label
  `"Adresse e-mail"`, `type="email"`, `autocomplete="email"`, `required`, pre-filled from `email`. CTA
  `"S'authentifier"`. The hint's second paragraph becomes `"Ce message ne vous était pas destiné ? Ne continuez pas, et
  prévenez la personne qui vous l'a envoyé. Chaque nouvelle demande de code remplace la précédente."`
- **Code**: as the prototype, with `maskedEmail` (the typed e-mail) and the expiry from `codeExpiresAt` via
  `TenantFormatter`. "Recevoir un nouveau code" is the submit button of a second form posting `/otp` with
  `resend=true`. No `maxlength`.
- **Sign**: the intro's `dl.review` (Contrat, Envoyé par (omitted when `senderName` is null), Référence, À signer
  avant le) at the top; `<article class="contract" th:utext="${view.contentHtml}">`; without content, `"Le contrat ne
  peut pas être affiché pour le moment. Réessayez dans quelques instants."` and no consent / button.
- **Blocked** alerts: as the prototype, plus `not-required` (`"Votre signature n'est pas requise pour ce contrat."` /
  `"Il n'y a rien à faire de votre côté."`), `unavailable` (`"Ce contrat n'est pas disponible."` / `"Contactez la
  personne qui vous l'a envoyé."`) and `not-yours` (`"Ce contrat n'a pas été envoyé à cette adresse."` /
  `"Identifiez-vous avec l'adresse e-mail à laquelle vous avez reçu ce lien."`, button `"Utiliser une autre adresse"`
  posting `/logout`). Agent contact: `"Contactez votre agent, {senderName}."` (no WhatsApp). `signed`'s second
  sentence becomes `"Il n'y a rien d'autre à faire."`
- **Done**: drop "Une copie du contrat signé est envoyée automatiquement à …"; keep `"Vous n'avez rien d'autre à
  faire."`
- The sign step's form (the review `dl`, hints and SVG icons as in the prototype):

```html
<form th:if="${view.step == 'sign'}" class="form" method="post" novalidate data-sign-form
      th:action="@{/sign/{id}(id=${contractPartyId})}">
    <fieldset class="step" tabindex="-1">
        <!-- legend, h3, intro sentence, dl.review: as the prototype -->

        <!-- Cleaned by ContractContentClient (Jsoup Safelist.relaxed()): never put unsanitized HTML here -->
        <article class="contract" aria-label="Contrat" th:if="${view.contentHtml != null}"
                 th:utext="${view.contentHtml}"></article>
        <p role="alert" class="field__error" th:unless="${view.canSign}">
            <span>Le contrat ne peut pas être affiché pour le moment. Réessayez dans quelques instants.</span>
        </p>

        <th:block th:if="${view.canSign}">
            <!-- no-payment hint, dl.signature with view.signerName / view.signDate: as the prototype -->
            <div class="field">
                <label class="choice">
                    <input type="checkbox" id="consent" name="consent" value="yes" required
                           aria-describedby="consent-hint" th:aria-invalid="${error != null} ? 'true' : null">
                    <span>J'ai lu le contrat et j'accepte de le signer électroniquement.</span>
                </label>
                <!-- #consent-hint: as the prototype -->
            </div>
            <p role="alert" class="field__error" th:if="${error != null}"><span th:text="${error}"></span></p>
            <div class="cta-row">
                <!-- Enabled without JS; esignature.js disables it until the box is ticked -->
                <button class="btn btn--primary" type="submit" aria-describedby="consent-hint" data-submit>
                    Signer électroniquement</button>
            </div>
        </th:block>
    </fieldset>
</form>
```

- `esignature.js` (served): from the prototype, **remove** state/localStorage, fake latency, prototype codes,
  `?state=`, slide switching/history; **keep** digit sync + Suivant enabling, consent enabling, busy state on submit
  (no double submit), focus on the code field / confirmation heading.

- [ ] **Step 1: Write the failing tests**: `intro asks for email and shows no contract` (an `input[type=email]`, no
  `MNDT-`, no `dl.review`), `code step has one time code field` (`autocomplete="one-time-code"`,
  `inputmode="numeric"`, no `maxlength`, error `role="alert"`), `sign step shows details and content`, `sign step
  without content has no button`, one test per blocked state (title, no sign form; `not-yours` has the logout form),
  `done has no copy promise` (no "copie"), `header has logo only` (no `nav`).
- [ ] **Step 2: Run** `mvn test -Dtest=SignTemplateTest`. Expected: FAIL.
- [ ] **Step 3: Implement** template, assets, JS.
- [ ] **Step 4: Run** `mvn verify`. Expected: PASS. Then by hand: server on 8080, `mvn spring-boot:run` (8083); create a
  mandate on *Trouver un bien*, accept it and request approval in Partner Central, open the e-mail's link from Mailpit,
  sign, with and without JS.
- [ ] **Step 5: Commit** `feat(esignature): sign page`.

### Task 7: Release, CI, docs

**Files:**
- Modify: `pom.xml` (root): `ndopify-dto.version` → `0.0.53` once released
- Modify: `modules/ndopify-esignature/pom.xml`: remove the `jacoco.threshold.*` `0.00` overrides
- Create: `.github/workflows/ndopify-esignature-pr.yml` (copy of `ndopify-partner-central-pr.yml`, module path changed;
  the README's PR badge points at it)
- Modify: `modules/ndopify-esignature/README.md` (targeted edit, badges kept), `CLAUDE.md`

- [ ] **Step 1:** Ask the user to release ndopify-dto `0.0.53`; set the root property; `mvn install -Dheadless=true` at
  the root. Expected: BUILD SUCCESS.
- [ ] **Step 2:** `cd modules/ndopify-esignature && mvn verify` with the parent thresholds. Expected: PASS (add tests
  if not, never lower them).
- [ ] **Step 3:** `CLAUDE.md`: "Five modules"; a `### ndopify-esignature` section (route, sign-in by e-mail + OTP with
  `e-signature`, cookie, nothing shown before sign-in, `not-yours`, prototype off-limits, port 8083); the dependency
  diagram; the *Running* commands.
- [ ] **Step 4: Commit** `chore(esignature): dto 0.0.53, PR workflow, docs`.
