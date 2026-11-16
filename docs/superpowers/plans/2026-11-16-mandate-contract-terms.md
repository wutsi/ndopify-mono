# Mandate contract terms (duration + visit fee) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the agent choose the mandate's duration and visit fee when sending the contract, store both on the contract, and state both in the generated contract.

**Architecture:** The terms live on `T_CONTRACT` (`duration_months` already exists; add `visit_fee_amount` + `visit_fee_currency`). `POST /v1/mandates/{id}/request-approval` gains an optional body that applies the terms to the new or reused contract before its content is generated. Partner Central adds a dedicated send page (`GET|POST /mandates/{id}/contract`), and the mandate page shows a *Termes du contrat* section.

**Tech Stack:** Kotlin, Spring Boot, JPA/Flyway, MJML + Mustache, Thymeleaf, `kotlin.test`, `mockito-kotlin`, JaCoCo.

**Spec:** issue #188 (authoritative) and the local design record `docs/superpowers/specs/2026-11-16-mandate-contract-terms-design.md` (uncommitted; never commit it in this story).

## Global Constraints

- UI text in French; code, comments, logs and docs in English.
- Amounts and dates shown to users go through `TenantFormatter`; the fee currency is the tenant's (`Tenant.currencyCode`), never a literal "FCFA".
- Assertions with `kotlin.test`; mocks with `com.nhaarman.mockitokotlin2`.
- A Flyway change is a new script under `db/migration/common/`; never edit an applied one.
- Each commit touches one library module (`modules/ndopify-dto/`) or none. Library commits are `feat(dto): …` with `Refs #188` in the body.
- The root `pom.xml`'s `<ndopify-dto.version>` stays a released version in commits; the local `-SNAPSHOT` bump is never committed.
- `src/main/html/` is the prototype: never serve it, never delete it, and don't commit it in this story (the prototype is already uncommitted in the primary worktree).
- No `ndopify-web` change; no `ndopify-web-core` change.

## Review Focus

- A hand-edited fee (`abc`, `-1`, `1e6`, an empty field): a field error on the page, and a 400 on the API — never a 500 or a stored garbage value.
- A duration outside 1–12 (UI) or 1–36 (API): a field error / 400, and nothing stored.
- A fee currency that is neither blank nor the tenant's: 400 `HTTP_INVALID_PARAMETER`, nothing stored.
- A signed, cancelled or expired contract: term changes refused (409), and the content hash never moves after a signature.
- An existing/cancelled contract: the page pre-fills from it; a mandate that can't be sent redirects instead of showing another agent's data.
- No-JS: the form posts and validates without any script.

---

### Task 1: Prototype gate (no commit)

**Files:**
- Review only: `modules/ndopify-partner-central/src/main/html/mandate/contract.html`, `mandate.html`, `assets/js/partner-central.js`, `assets/css/home.css`, `assets/css/partner-central.css`, `modules/ndopify-esignature/src/main/html/sign.html`.
- Do not edit or commit them: they are already written and uncommitted in the primary worktree (`/Users/herve/Projects/ndopify-mono`), outside this branch.

**Interfaces:**
- Consumes: the plan above.
- Produces: the user's OK on the prototype and the plan before Task 2.

- [ ] **Step 1: Serve the prototype from the primary worktree**

Run, from `/Users/herve/Projects/ndopify-mono/modules/ndopify-partner-central/src/main/html`:

```bash
python3 -m http.server 8099
```

Open `/mandate/contract.html?id=201` (search, not sent), `?id=202` (already sent), `?id=203` (landlord), and `/mandate.html?id=201`, `?id=202`, `?id=204`.

- [ ] **Step 2: Stop for the gate**

Show the user those links and the files listed above. Do not edit any DTO, server or BFF file before their OK on the prototype and the plan. If their feedback changes the design, update this plan, commit and push it to `main` (`docs(mandate): update the mandate contract terms plan`), rebase the branch, and show the prototype again.

---

### Task 2: DTO

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/Contract.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/CreateContractRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/RequestMandateApprovalRequest.kt`
- Modify locally, never commit: root `pom.xml` `<ndopify-dto.version>`

**Interfaces:**
- Produces:
  - `Contract.visitFee: Money = Money()` (`com.wutsi.ndopify.refdata.dto.Money`).
  - `CreateContractRequest.visitFee: Money? = null`.
  - `RequestMandateApprovalRequest(durationMonths: Int? = null, visitFee: Money? = null)`.

- [ ] **Step 1: Add `visitFee` to `Contract`**

After `durationMonths`, add:

```kotlin
val visitFee: Money = Money(), // Per visit, paid to the agent offline; 0 = none
```

Add the `com.wutsi.ndopify.refdata.dto.Money` import.

- [ ] **Step 2: Add `visitFee` to `CreateContractRequest`**

After `durationMonths`, add:

```kotlin
// Null = none; the currency is the tenant's, or blank to mean it
val visitFee: Money? = null,
```

Add the `Money` import.

- [ ] **Step 3: Create `RequestMandateApprovalRequest`**

```kotlin
package com.wutsi.ndopify.mandate.dto

import com.wutsi.ndopify.refdata.dto.Money

/**
 * The terms the agent confirms when sending a mandate's contract. A null value keeps the contract's current one (or
 * the tenant default for a new contract).
 */
data class RequestMandateApprovalRequest(
    val durationMonths: Int? = null,
    val visitFee: Money? = null,
)
```

- [ ] **Step 4: Build and install the DTO locally**

Run:

```bash
mvn -pl modules/ndopify-dto install
```

Expected: BUILD SUCCESS. The module has no tests.

- [ ] **Step 5: Point the root POM at the local snapshot (uncommitted)**

Set `<ndopify-dto.version>0.0.96-SNAPSHOT</ndopify-dto.version>` (the version in `modules/ndopify-dto/pom.xml`). Do not commit this change.

- [ ] **Step 6: Commit only the DTO module**

```bash
git add modules/ndopify-dto
git commit -m "feat(dto): add the contract's visit fee and the approval terms" -m "Refs #188"
```

---

### Task 3: Server — store the visit fee

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V13_0__contract-visit-fee.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/domain/ContractEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mapper/ContractMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/CreateContractEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/GetContractEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/contract/GetContractEndpoint.sql`

**Interfaces:**
- Consumes: `CreateContractRequest.visitFee` from Task 2.
- Produces: `ContractEntity.visitFeeAmount: Long`, `ContractEntity.visitFeeCurrency: String`, and `Contract.visitFee` in `GET /v1/contracts/{id}`.

- [ ] **Step 1: Write the failing create tests**

In `CreateContractEndpointTest`, add:

```kotlin
@Test
fun `create - stores the visit fee`() {
    val response = rest.postForEntity(
        "/v1/contracts",
        request.copy(visitFee = Money(5000, "XAF")),
        CreateContractResponse::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val contract = dao.findById(response.body!!.contractId).get()
    assertEquals(5000, contract.visitFeeAmount)
    assertEquals("XAF", contract.visitFeeCurrency)
}

@Test
fun `create - a blank fee currency is the tenant's`() {
    val response = rest.postForEntity(
        "/v1/contracts",
        request.copy(visitFee = Money(5000, "")),
        CreateContractResponse::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("XAF", dao.findById(response.body!!.contractId).get().visitFeeCurrency)
}

@Test
fun `create - rejects a negative fee or another currency`() {
    assertBadRequest(request.copy(visitFee = Money(-1, "XAF")), ErrorCode.HTTP_INVALID_PARAMETER, "visitFee.amount")
    assertBadRequest(request.copy(visitFee = Money(5000, "EUR")), ErrorCode.HTTP_INVALID_PARAMETER, "visitFee.currency")
}
```

Add the `Money` import.

- [ ] **Step 2: Run the create tests and verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=CreateContractEndpointTest
```

Expected: compilation failure (`visitFeeAmount`, `visitFee` unknown). MySQL must be running.

- [ ] **Step 3: Add the migration**

`V13_0__contract-visit-fee.sql`:

```sql
ALTER TABLE T_CONTRACT
    ADD COLUMN visit_fee_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN visit_fee_currency VARCHAR(3) NOT NULL DEFAULT '';
```

- [ ] **Step 4: Add the entity fields**

In `ContractEntity`, after `durationMonths`:

```kotlin
val visitFeeAmount: Long = 0, // Per visit, paid to the agent offline; 0 = none
val visitFeeCurrency: String = "", // Empty on a legacy row: read it as the tenant's
```

- [ ] **Step 5: Store and map the fee**

In `ContractService.create`, resolve the tenant once (`val tenant = tenantService.findById(tenantId)`, using `tenant.timeZone` for the number generator) and normalize the request's fee with a new private helper:

```kotlin
private fun normalizeVisitFee(money: Money?, tenantCurrency: String): Pair<Long, String> {
    if (money == null) return 0L to tenantCurrency
    if (money.amount < 0) throw invalidParameter("visitFee.amount", money.amount.toString())
    if (money.currency.isNotBlank() && money.currency != tenantCurrency) {
        throw invalidParameter("visitFee.currency", money.currency)
    }
    return money.amount to tenantCurrency
}
```

Save the two values on the new `ContractEntity`.

In `ContractMapper.toContract`, add:

```kotlin
visitFee = Money(entity.visitFeeAmount, entity.visitFeeCurrency),
```

- [ ] **Step 6: Write the failing read test**

In `GetContractEndpointTest.get`, assert `Money(5000, "XAF")` for `c-1`; in the SQL fixture, add `visit_fee_amount, visit_fee_currency` to the `T_CONTRACT` insert with `5000, 'XAF'` for `c-1` and `0, ''` for `c-9`.

Run `mvn test -Dtest=GetContractEndpointTest`; expected FAIL on the missing field/mapping.

- [ ] **Step 7: Run both endpoint tests and verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=CreateContractEndpointTest,GetContractEndpointTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(contract): store a contract's visit fee" -m "Refs #188"
```

---

### Task 4: Server — apply the terms at send

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/service/ContractServiceTest.kt` (new)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/RequestMandateApprovalEndpointTest.kt`

**Interfaces:**
- Consumes: `RequestMandateApprovalRequest`, `ContractService.normalizeVisitFee` from Task 3.
- Produces:
  - `ContractService.setTerms(id: String, durationMonths: Int? = null, visitFee: Money? = null): ContractEntity`.
  - `MandateService.requestApproval(id: Long, request: RequestMandateApprovalRequest? = null): MandateEntity`.
  - `POST /v1/mandates/{id}/request-approval` accepts an optional body.

- [ ] **Step 1: Write the failing `setTerms` tests**

Create `ContractServiceTest` with the service's mocks (dao, partyDao, mandateDao, agentService, partyService, signerService, tenantService, numberGenerator, signatureMailet, eventPublisher, clock, tx, `signatureTtlDays = 7`). In `@BeforeTest`, `TenantContext.set(1L)` and stub `tenantService.findById(1L)` to a `TenantEntity(id = 1L, currencyCode = "XAF")`; in `@AfterTest`, `TenantContext.remove()`.

Tests:

```kotlin
@Test
fun `set terms - applies the duration and the fee`() {
    val contract = ContractEntity(id = "c-1", status = DRAFT, durationMonths = 6)
    doReturn(contract).whenever(dao).findByIdForUpdate("c-1")
    doReturn(contract.copy(durationMonths = 3, visitFeeAmount = 5000, visitFeeCurrency = "XAF"))
        .whenever(dao).save(any())

    val updated = service.setTerms("c-1", durationMonths = 3, visitFee = Money(5000, "XAF"))

    assertEquals(3, updated.durationMonths)
    assertEquals(5000, updated.visitFeeAmount)
    assertEquals("XAF", updated.visitFeeCurrency)
}

@Test
fun `set terms - a blank fee currency is the tenant's and nulls keep the current values`() { /* … */ }

@Test
fun `set terms - refuses an out-of-range duration, a negative fee or a foreign currency`() {
    assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, assertFailsWith<BadRequestException> {
        service.setTerms("c-1", durationMonths = 0)
    }.error.code)
    // … 37, Money(-1, "XAF"), Money(1, "EUR")
}

@Test
fun `set terms - a signed, cancelled or expired contract is locked`() {
    for (status in listOf(SIGNED, CANCELLED, EXPIRED)) {
        doReturn(ContractEntity(id = "c-1", status = status)).whenever(dao).findByIdForUpdate("c-1")
        assertFailsWith<ConflictException> { service.setTerms("c-1", durationMonths = 3) }
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=ContractServiceTest
```

Expected: compilation failure (`setTerms` unknown).

- [ ] **Step 3: Implement `setTerms`**

```kotlin
/**
 * Sets the terms of a contract that can still be signed (DRAFT, PENDING_SIGNATURE). A null value keeps the current
 * one. The duration is 1..36 months; the fee is in the tenant's currency (blank means it) and can't be negative.
 * Locked like every contract write, so a signature can't interleave.
 */
@Transactional
fun setTerms(id: String, durationMonths: Int? = null, visitFee: Money? = null): ContractEntity
```

Lock with `findByIdForUpdate`, `ensureNotClosed`, validate the duration and normalize the fee; save only when a value is non-null.

- [ ] **Step 4: Add the failing endpoint tests**

In `RequestMandateApprovalEndpointTest`:

```kotlin
@Test
fun `request approval with terms - a new contract gets them`() {
    val request = RequestMandateApprovalRequest(durationMonths = 3, visitFee = Money(5000, "XAF"))
    val response = rest.postForEntity("/v1/mandates/1/request-approval", request, Any::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    val captured = argumentCaptor<CreateContractRequest>()
    verify(contractService).create(captured.capture())
    assertEquals(3, captured.firstValue.durationMonths)
    assertEquals(Money(5000, "XAF"), captured.firstValue.visitFee)
}

@Test
fun `request approval with terms - a reused contract is updated before the content`() {
    val request = RequestMandateApprovalRequest(durationMonths = 3, visitFee = Money(5000, "XAF"))
    val response = rest.postForEntity("/v1/mandates/4/request-approval", request, Any::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    verify(contractService).setTerms("c-4", 3, Money(5000, "XAF"))
    val order = inOrder(contractService, contractGenerator)
    order.verify(contractService).setTerms("c-4", 3, Money(5000, "XAF"))
    order.verify(contractGenerator).generate(any(), any())
}

@Test
fun `request approval - an out-of-range duration is 400`() {
    for (duration in listOf(0, 37)) {
        val response = rest.postForEntity(
            "/v1/mandates/1/request-approval",
            RequestMandateApprovalRequest(durationMonths = duration),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body!!.error.code)
    }
    verify(contractService, never()).create(any())
}
```

The existing `request approval` test (no body) stays as the default case.

- [ ] **Step 5: Implement the send path**

`MandateService.requestApproval(id: Long, request: RequestMandateApprovalRequest? = null)`:
- Validate `request?.durationMonths` is in `1..36` when non-null, with `BadRequestException(HTTP_INVALID_PARAMETER, Parameter("durationMonths", PAYLOAD))`.
- Pass the request to `prepareApproval`.

`prepareApproval(id, request)` — preserve the repo's lock order (contract before mandate, like `update` and a signature):
- `val contractId = dao.findContractIdById(id)`
- `contractId?.let { contractService.findByIdForUpdate(it) }`
- `val mandate = findByIdForUpdate(id)`
- If `mandate.contractId != contractId`, throw `ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))` (another request replaced it).
- `access.ensureCanUpdate(mandate)`, `ensureCanBeApproved(mandate)`.
- If the current contract is neither EXPIRED nor CANCELLED: `contractService.setTerms(current.id, request?.durationMonths, request?.visitFee)` and return its id.
- Otherwise `createContract(mandate, request)` with `durationMonths = request?.durationMonths ?: contractDurationMonths` and `visitFee = request?.visitFee`.

`MandateEndpoints.requestApproval`:

```kotlin
@PostMapping("/{id}/request-approval")
fun requestApproval(
    @PathVariable id: Long,
    @RequestBody(required = false) @Valid request: RequestMandateApprovalRequest?,
) {
    service.requestApproval(id, request)
}
```

- [ ] **Step 6: Run the tests and verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=ContractServiceTest,RequestMandateApprovalEndpointTest
```

Expected: PASS, including the existing concurrent-requests test (a second request may get 409, which it allows).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(mandate): apply the contract terms set at send" -m "Refs #188"
```

---

### Task 5: Server — state the terms in the contract

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGenerator.kt`
- Modify: `modules/ndopify-server/src/main/resources/mandate/contract/search.mjml`
- Modify: `modules/ndopify-server/src/main/resources/mandate/contract/listing.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGeneratorTest.kt`

**Interfaces:**
- Consumes: `ContractEntity.visitFeeAmount` / `visitFeeCurrency` from Task 3.
- Produces: template values `visit_fee: String?` and `has_visit_fee: Boolean`.

- [ ] **Step 1: Write the failing generator tests**

Add to `MandateContractGeneratorTest`:

```kotlin
@Test
fun `search - states the visit fee and who receives it`() {
    val html = generator.generate(mandate, contract.copy(visitFeeAmount = 5000, visitFeeCurrency = "XAF"))

    listOf(
        "Frais de visite",
        "5 000 XAF par visite, à régler directement à l'Agent (Kim Possible) avant chaque visite.",
        "Aucun paiement n'est effectué via Ndopify.",
        "Aucun frais n'est dû pour une visite annulée par l'Agent.",
    ).forEach { text -> assertTrue(html.contains(text), "missing: $text") }
}

@Test
fun `search - no visit fee`() {
    val html = generator.generate(mandate, contract)

    assertTrue(html.contains("Aucun frais de visite."))
}

@Test
fun `listing - the visitors pay the fee, not the owner`() {
    val listing = mandate.copy(mandateType = MandateType.LISTING, /* the existing listing fixture */)
    val html = generator.generate(listing, contract.copy(visitFeeAmount = 5000, visitFeeCurrency = "XAF"))

    assertTrue(html.contains("à régler par les visiteurs directement à l'Agent"))
    assertFalse(html.contains("à régler directement à l'Agent (Kim Possible) avant chaque visite"))
}
```

The expected amount is `5 000 XAF` with this test's tenant fixture (no `monetaryFormat`); the deployed tenant formats it as its symbol (ex: `FCFA`). Change the existing listing test's `assertFalse(html.contains("Frais de visites"))` to assert the new line instead.

- [ ] **Step 2: Run the tests and verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=MandateContractGeneratorTest
```

Expected: FAIL (no fee line).

- [ ] **Step 3: Add the template values**

In `MandateContractGenerator.context`:

```kotlin
"visit_fee" to contract.visitFeeAmount
    .takeIf { it > 0 }
    ?.let { formatter.money(it, contract.visitFeeCurrency.ifBlank { tenant.currencyCode }) },
"has_visit_fee" to (contract.visitFeeAmount > 0),
```

`duration_months` already comes from the contract.

- [ ] **Step 4: Replace the fee line in both templates**

In `search.mjml` (§ HONORAIRES ET FRAIS), replace the `<b>Frais de visites</b> : payable avant chaque visite.` item with:

```html
<li>
    <b>Frais de visite</b> : {{#has_visit_fee}}{{visit_fee}} par visite, à régler directement à l'Agent{{#agent_name}}
    ({{agent_name}}){{/agent_name}} avant chaque visite. Aucun paiement n'est effectué via Ndopify. Aucun frais
    n'est dû pour une visite annulée par l'Agent.{{/has_visit_fee}}{{^has_visit_fee}}Aucun frais de visite.{{/has_visit_fee}}
</li>
```

In `listing.mjml` (§ 4. HONORAIRES ET FRAIS), add the same item after *Frais de dossier*, worded for the owner:

```html
<li>
    <b>Frais de visite</b> : {{#has_visit_fee}}{{visit_fee}} par visite, à régler par les visiteurs directement à
    l'Agent{{#agent_name}} ({{agent_name}}){{/agent_name}} avant chaque visite. Aucun paiement n'est effectué via
    Ndopify. Aucun frais n'est dû pour une visite annulée par l'Agent.{{/has_visit_fee}}{{^has_visit_fee}}Aucun frais
    de visite.{{/has_visit_fee}}
</li>
```

Update each template's header comment to list `visit_fee` / `has_visit_fee` under `contract`.

- [ ] **Step 5: Run the generator tests and verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=MandateContractGeneratorTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(contract): state the visit fee in the mandate contract" -m "Refs #188"
```

---

### Task 6: Partner Central — send the terms

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractForm.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractRulesTest.kt` (new)

**Interfaces:**
- Consumes: `RequestMandateApprovalRequest` from Task 2.
- Produces:
  - `NdMandateClient.requestApproval(id: Long, request: RequestMandateApprovalRequest = RequestMandateApprovalRequest())`.
  - `MandateContractRules.DEFAULT_MONTHS = 6`, `MIN_MONTHS = 1`, `MAX_MONTHS = 12`.
  - `MandateContractRules.prefill(contract: Contract?): MandateContractForm`.
  - `MandateContractRules.errors(form: MandateContractForm): Map<String, String>`.
  - `MandateContractRules.request(form: MandateContractForm, currency: String): RequestMandateApprovalRequest`.

- [ ] **Step 1: Write the failing client test**

In `NdMandateClientTest.requestApproval`, add the body expectation:

```kotlin
server.expect(requestTo("http://localhost:8080/v1/mandates/101/request-approval"))
    .andExpect(method(HttpMethod.POST))
    .andExpect(
        content().json("""{"durationMonths":3,"visitFee":{"amount":5000,"currency":"XAF","periodicity":"UNKNOWN"}}"""),
    )
    .andRespond(withSuccess())

client.requestApproval(101, RequestMandateApprovalRequest(3, Money(5000, "XAF")))
```

Keep a no-argument test for the default body (`{}`).

- [ ] **Step 2: Write the failing rules tests**

```kotlin
@Test
fun `prefill - from the contract, or 6 months and no fee`() {
    assertEquals(MandateContractForm("3", "5000"), MandateContractRules.prefill(
        Contract(durationMonths = 3, visitFee = Money(5000, "XAF")),
    ))
    assertEquals(MandateContractForm("6", "0"), MandateContractRules.prefill(null))
}

@Test
fun `errors - duration 1 to 12 and a whole amount`() {
    assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("6", "5 000")))
    assertTrue(MandateContractRules.errors(MandateContractForm("0", "0")).containsKey("durationMonths"))
    assertTrue(MandateContractRules.errors(MandateContractForm("13", "0")).containsKey("durationMonths"))
    assertTrue(MandateContractRules.errors(MandateContractForm("6", "abc")).containsKey("visitFee"))
}

@Test
fun `request - a blank fee is zero, in the tenant currency`() {
    assertEquals(
        RequestMandateApprovalRequest(3, Money(5000, "XAF")),
        MandateContractRules.request(MandateContractForm("3", "5 000"), "XAF"),
    )
    assertEquals(Money(0, "XAF"), MandateContractRules.request(MandateContractForm("6", ""), "XAF").visitFee)
}
```

- [ ] **Step 3: Run the tests and verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdMandateClientTest,MandateContractRulesTest
```

Expected: compilation failure.

- [ ] **Step 4: Implement the form, the rules and the client**

`MandateContractForm`:

```kotlin
data class MandateContractForm(
    val durationMonths: String = "",
    val visitFee: String = "", // Strings, so a hand-edited value is a field error, not a 400
)
```

`MandateContractRules`:
- `prefill(contract)`: duration = `contract?.durationMonths?.takeIf { it in MIN_MONTHS..MAX_MONTHS } ?: DEFAULT_MONTHS`, fee = `contract?.visitFee?.amount ?: 0`.
- `errors(form)`: `durationMonths` must be an integer in 1..12; `visitFee` blank means 0, otherwise digits after removing spaces, dots and NBSP.
- `request(form, currency)`: `RequestMandateApprovalRequest(durationMonths, Money(amount, currency))`.

`NdMandateClient.requestApproval(id, request)` posts `request` as the body.

- [ ] **Step 5: Run the tests and verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdMandateClientTest,MandateContractRulesTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
git commit -m "feat(npc): send the contract terms with the approval request" -m "Refs #188"
```

---

### Task 7: Partner Central — the send page

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractController.kt`
- Create: `modules/ndopify-partner-central/src/main/resources/templates/mandate/contract.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractControllerTest.kt` (new)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractTemplateTest.kt` (new)

**Interfaces:**
- Consumes: `MandateContractForm`, `MandateContractRules`, `NdMandateClient.requestApproval` from Task 6.
- Produces: `GET|POST /mandates/{id}/contract`, view `mandate/contract`, outcomes `sent` / `resent` / `changed` / `email-conflict` / `error`.

- [ ] **Step 1: Write the failing controller tests**

`MandateContractControllerTest` mirrors `MandateCloseControllerTest`: `MockMvcBuilders.standaloneSetup(controller)`, mocked `CurrentAgent`, `NdMandateClient`, `NdContractClient`, `NdCustomerClient`, `TenantContext`, and the fixtures `MandateViewsTest.PENDING`, `MandateCardsTest.CONTRACT`, `ProfileFixtures.AGENT`.

```kotlin
@Test
fun `show - pre-fills from the contract`() {
    doReturn(PENDING.copy(contractId = "c-201")).whenever(mandateClient).get(201)
    doReturn(CONTRACT.copy(durationMonths = 3, visitFee = Money(5000, "XAF"))).whenever(contractClient).get("c-201")

    val result = mvc.perform(get("/mandates/201/contract")).andReturn()

    assertEquals("mandate/contract", result.modelAndView?.viewName)
    assertEquals(MandateContractForm("3", "5000"), model(result)["form"])
    assertEquals(true, model(result)["resend"])
}

@Test
fun `show - redirects a mandate that can't be sent`() {
    doReturn(PENDING.copy(status = APPROVED)).whenever(mandateClient).get(201)

    assertEquals("redirect:/mandates/201", mvc.perform(get("/mandates/201/contract")).andReturn().modelAndView?.viewName)
}

@Test
fun `send - posts the terms and redirects to the mandate`() {
    doReturn(PENDING).whenever(mandateClient).get(201)

    val result = mvc.perform(
        post("/mandates/201/contract").param("durationMonths", "3").param("visitFee", "5 000"),
    ).andReturn()

    assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
    assertEquals("sent", result.flashMap["outcome"])
    verify(mandateClient).requestApproval(201, RequestMandateApprovalRequest(3, Money(5000, "XAF")))
}

@Test
fun `send - invalid terms show the page again`() {
    val result = mvc.perform(post("/mandates/201/contract").param("durationMonths", "13").param("visitFee", "0")).andReturn()

    assertEquals("mandate/contract", result.modelAndView?.viewName)
    assertTrue((model(result)["errors"] as Map<*, *>).containsKey("durationMonths"))
    verify(mandateClient, never()).requestApproval(any(), any())
}

@Test
fun `send - conflict, server error and e-mail conflict`() { /* changed, error, email-conflict, mirroring MandateControllerTest */ }

@Test
fun `send - not signed in`() {
    doReturn(null).whenever(currentAgent).get()

    assertEquals("redirect:/login", mvc.perform(post("/mandates/201/contract")).andReturn().modelAndView?.viewName)
}
```

Use `MandateController.SENT` / `RESENT` / `CHANGED` / `FAILED` / `EMAIL_CONFLICT` / `OUTCOME` for the flash values (make them `const val` on `MandateController`'s companion if they aren't already public).

- [ ] **Step 2: Run the tests and verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateContractControllerTest
```

Expected: compilation failure.

- [ ] **Step 3: Implement the controller**

`@Controller @RequestMapping("/mandates/{id}/contract")`, constructor: `CurrentAgent`, `NdMandateClient`, `NdContractClient`, `NdCustomerClient`, `TenantContext`.

- `@GetMapping fun show(@PathVariable id: String, model: Model): String`
  - Signed-in agent, else `SignInRequiredException`; `ProfileRules.header(agent, model)`.
  - Load the mandate (`id.toLongOrNull()`), only ASSIGNED / PENDING_APPROVAL; otherwise `redirect:/mandates/{id}`. A non-numeric id (or no mandate) goes to `redirect:/`.
  - Render: model `mandate`, `form = MandateContractRules.prefill(contract)`, `errors = emptyMap()`, `resend = status == PENDING_APPROVAL`, `email` (the contract's MANDATOR party's `legalEmail`, else the customer's party e-mail, best effort), `signatureTtlDays = MandateRules.signatureTtlDays(contract)`, `months = 1..12`, `currency = TenantFormatter(tenantContext.getTenant()).currency()`.
- `@PostMapping fun send(@PathVariable id: Long, @ModelAttribute form: MandateContractForm, model: Model, redirect: RedirectAttributes): String`
  - Signed-in agent, else sign-in redirect.
  - `val errors = MandateContractRules.errors(form)`; on errors, reload the page context and re-render the view (if the mandate can't be loaded, redirect with `CHANGED`).
  - Else `act(id, if (again) RESENT else SENT, redirect) { mandateClient.requestApproval(id, MandateContractRules.request(form, tenantContext.getTenant().currencyCode)) }`, where `again` is the mandate's PENDING_APPROVAL status (a failed lookup means `sent`).
  - `act` mirrors `MandateController.act`: 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT` → `EMAIL_CONFLICT`; 403/404/409 → `CHANGED`; 5xx / `RestClientException` → `FAILED`; then `redirect:/mandates/$id`. Duplicate the ~20 lines rather than exposing a private method.

- [ ] **Step 4: Write the failing template test**

`MandateContractTemplateTest` renders the real Thymeleaf engine through the controller (like `MandateCloseTemplateTest`), with `SignedInAdviceTest.signIn()` and `TenantFixtures.TENANT`.

Assertions:
- The form action is `/mandates/201/contract`.
- `select[name=durationMonths]` has 12 options, the selected one matches `form.durationMonths`.
- `input[name=visitFee]` has the pre-filled amount and the tenant's currency symbol beside it.
- The submit reads *Envoyer au client* (ASSIGNED) or *Renvoyer* (PENDING_APPROVAL).
- The hint names the customer's e-mail and *Ndopify ne perçoit aucun paiement.*
- With `errors`, the messages are shown under their fields.
- The page contains no page script that the form depends on: it posts without JavaScript.

- [ ] **Step 5: Implement the template**

Create `templates/mandate/contract.html` from the prototype, with Thymeleaf:

- The standard signed-in page header: `fragments/user-menu`, the same `<head>` links as `templates/mandate/close.html` but only `partner-central.js` (the form needs no page script).
- `<form method="post" th:action="@{/mandates/{id}/contract(id=${mandate.id})}">`.
- A `select` with `th:each="month : ${months}"` and `th:selected="${#strings.toString(month) == form.durationMonths}"` (`months` is the `1..12` model list the controller adds).
- `input type="text" inputmode="numeric" name="visitFee" th:value="${form.visitFee}"`, with the currency suffix `th:text="${currency}"`.
- `th:if="${errors.durationMonths}"` / `th:if="${errors.visitFee}"` under each field.
- `th:text="${resend} ? 'Renvoyer' : 'Envoyer au client'"`.
- The hint: `th:text="${email}"` and, only `th:if="${signatureTtlDays != null}"`, the day count.

- [ ] **Step 6: Run the controller and template tests and verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateContractControllerTest,MandateContractTemplateTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
git commit -m "feat(npc): add the mandate contract send page" -m "Refs #188"
```

---

### Task 8: Partner Central — the mandate page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: the contract page from Task 7.
- Produces: `MandateView.hasContract: Boolean`, `contractDuration: String?`, `visitFee: String?`.

- [ ] **Step 1: Write the failing view tests**

In `MandateViewsTest`, add:

```kotlin
@Test
fun `contract terms`() {
    val view = MandateViews.of(
        APPROVED,
        CONTRACT.copy(durationMonths = 3, visitFee = Money(5000, "XAF")),
        CUSTOMER, ACCEPTED, emptyMap(), NOW, formatter(),
    )

    assertTrue(view.hasContract)
    assertEquals("3 mois", view.contractDuration)
    assertEquals("5 000 XAF", view.visitFee)
}

@Test
fun `contract terms - no fee`() {
    val view = MandateViews.of(APPROVED, CONTRACT, CUSTOMER, ACCEPTED, emptyMap(), NOW, formatter())

    assertEquals("6 mois", view.contractDuration)
    assertNull(view.visitFee)
}
```

- [ ] **Step 2: Write the failing template tests**

In `MandateTemplateTest`, replace the old `form[data-request-approval-form]` assertions with:

- ASSIGNED panel: `a[data-send-link]` with href `/mandates/201/contract`, text *Envoyer au client*; the *Modifier le mandat* link comes before it.
- PENDING panel: `a[data-send-link]` text *Renvoyer*, after *Modifier le mandat*.
- Terms section: once a contract exists, `section[data-terms-section]` shows `Termes du contrat`, `Durée`, `Frais de visite`; `Aucun frais de visite` when the fee is 0; `5 000 XAF` otherwise; `Signé le` / `Valable jusqu'au` only when `approvedAt` is set.
- No terms section without a contract.

- [ ] **Step 3: Run the tests and verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateViewsTest,MandateTemplateTest
```

Expected: FAIL.

- [ ] **Step 4: Add the view fields**

In `MandateView`, after `signatureTtlDays`:

```kotlin
val hasContract: Boolean,
val contractDuration: String?, // "6 mois"; null without a contract
val visitFee: String?, // "5 000 FCFA"; null when there's none
```

In `MandateViews.of`, set:

```kotlin
hasContract = contract != null,
contractDuration = contract?.durationMonths?.takeIf { it > 0 }?.let { "$it mois" },
visitFee = contract?.visitFee?.takeIf { it.amount > 0 }?.let { formatter.money(it.amount, it.currency) },
```

- [ ] **Step 5: Update the mandate template**

- In the ASSIGNED panel, replace the request-approval form/button with:

```html
<div class="mandate-panel__actions mandate-panel__actions--forward-last">
    <a class="btn btn--outline" th:href="@{/mandates/{id}/edit(id=${m.id})}" data-edit-link>Modifier le mandat</a>
    <a class="btn btn--primary" th:href="@{/mandates/{id}/contract(id=${m.id})}" data-send-link>Envoyer au client</a>
</div>
```

- In the PENDING_APPROVAL panel, replace its form/button with the same links, the send one reading *Renvoyer*.
- Insert the *Termes du contrat* section (before `<section ... aria-labelledby="search-title">`), `th:if="${m.hasContract}"`:

```html
<section class="home-section" aria-labelledby="terms-title" data-terms-section>
    <h2 id="terms-title">Termes du contrat</h2>
    <dl class="review">
        <div class="review__item">
            <dt class="review__term">Durée</dt>
            <dd class="review__value" th:text="${m.contractDuration}">6 mois</dd>
        </div>
        <div class="review__item">
            <dt class="review__term">Frais de visite</dt>
            <dd class="review__value" th:text="${m.visitFee ?: 'Aucun frais de visite'}">Aucun frais de visite</dd>
        </div>
        <div class="review__item" th:if="${m.approvedAt}">
            <dt class="review__term">Signé le</dt>
            <dd class="review__value" th:text="${m.approvedAt}">20 octobre 2026</dd>
        </div>
        <div class="review__item" th:if="${m.validUntil}">
            <dt class="review__term">Valable jusqu'au</dt>
            <dd class="review__value" th:text="${m.validUntil}">28 mars 2027</dd>
        </div>
    </dl>
</section>
```

- Remove the `@PostMapping("/{id}/request-approval")` method from `MandateController` (the new controller owns it), update its class KDoc to point at the contract page, and update the template's header comment (line ~26) that still documents the old POST.
- In `public/assets/js/mandate.js`, remove the now-dead `[data-request-approval-form]` guard in `initMandate` (the forms are gone; the page must not keep a handler for markup that no longer exists).

- [ ] **Step 6: Update the controller tests**

Remove from `MandateControllerTest` the request-approval POST tests (`request approval`, `- again`, `- conflict`, `- server error`, `- mandate unavailable`, `- forbidden`, `- customer e-mail of another member`) and the `request-approval` entry of the `actions - not an agent` loop; they are covered by `MandateContractControllerTest`. Keep the `outcome - sent` / `- resent` / `- email conflict` render tests.

- [ ] **Step 7: Run the Partner Central tests and verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
git commit -m "feat(npc): show the contract terms on the mandate page" -m "Refs #188"
```

---

### Task 9: Verification

**Files:** none.

- [ ] **Step 1: Verify each touched module**

```bash
cd modules/ndopify-dto && mvn verify
cd ../ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS in each (MySQL running for `ndopify-server`; ktlint, tests and the JaCoCo gate green).

- [ ] **Step 2: Confirm nothing unrelated is committed**

```bash
git status --short
git log --oneline origin/main..HEAD
```

Expected: the only uncommitted change is the root `pom.xml` `<ndopify-dto.version>` snapshot. The commits are the DTO commit and the server/PC commits, each touching one module (or none). The prototype files and the design record are still uncommitted, untouched.

- [ ] **Step 3: Report to the user**

Tell the user the story is ready for `push 188`: the plan's steps are committed on `feat/<scope>-mandate-contract-terms`, `mvn verify` is green in each touched module, nothing is pushed, and `ndopify-dto` needs its release. Do not push.

---

## Self-Review Notes

- **Spec coverage:** duration bound (Task 4), fee storage (Task 3), terms applied at send (Task 4), clause in both templates (Task 5), send page (Task 7), mandate terms section + actions (Task 8), no web-core change, no `ndopify-web` change (out of scope).
- **Type consistency:** `RequestMandateApprovalRequest(durationMonths, visitFee)`, `ContractService.setTerms(id, durationMonths, visitFee)`, `MandateContractForm(durationMonths, visitFee)`, `MandateClient.requestApproval(id, request)`, `MandateView.hasContract/contractDuration/visitFee` are used consistently across tasks.
- **Review Focus tests:** hand-edited fee (Task 6/7), duration bounds (Task 4/6), foreign currency (Task 3), locked contract (Task 4), prefill from cancelled contract (Task 7), no-JS form (Task 7).
