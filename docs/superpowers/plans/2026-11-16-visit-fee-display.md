# Visit fee display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the search mandate contract's per-visit fee to the customer (match page, request popup, visit page) and to the agent (visit page), resolved from the contract set at send.

**Architecture:** The contract's `visitFee` (`Contract.visitFee`, #188) is read through at request time: `Mandate.visitFee` feeds the customer's match page (which loads the mandate before any visit exists), and `Visit.visitFee` feeds both visit pages. Each web app formats it with `TenantFormatter` and hides the "to collect" cues once the visit ends; the shared public conditions note becomes neutral on the listing page and states the fee on the match page.

**Tech Stack:** Kotlin, Spring Boot, JPA (no schema change), Thymeleaf, JaCoCo, `kotlin.test`, `mockito-kotlin`.

**Spec:** issue #189 (authoritative) — https://github.com/wutsi/ndopify-mono/issues/189 — and #188 (Done) for `Contract.visitFee`.

## Global Constraints

- UI text in French; code, comments, logs and docs in English.
- Amounts go through `TenantFormatter` (`formatter.money(amount, currency)`, the tenant's `currencyCode`); never a hard-coded "FCFA".
- Assertions with `kotlin.test`; mocks with `com.nhaarman.mockitokotlin2`. Test classes mirror the main package.
- Each commit touches one library module (`modules/ndopify-dto/`) or none. Library commits are `feat(dto): …` with `Refs #189` in the body.
- The root `pom.xml` pins a **released** `<ndopify-dto.version>`; while developing, bump it to `0.0.97-SNAPSHOT` and `mvn install` the DTO (a local, uncommitted edit). Never commit the `-SNAPSHOT`.
- No `ndopify-web-core` change; no Flyway script (no schema change).
- **#190 must merge before #189 is merged/deployed**: it removes the Pass Visite promise from the served home/agent pages. This branch may be built and reviewed meanwhile; rebase on `main` once #190 lands.
- `src/main/html/mvp/` is the **source of truth** for the public prototype; `src/main/html/v1/` is a backup and stays untouched. The pre-move fee prototype edits are in the backup at
  `/private/var/folders/pp/4ml4vgt916vghjjjv4q16r4r0000gn/T/ndopify-189-prototype-backup/tracked-changes.patch`; port the web ones into `mvp/` (the partner-central ones are already on valid paths).
- `mvn verify` in each touched module is green (JaCoCo gate). `ndopify-server` endpoint tests need local MySQL.
- Every page still works without JavaScript; scripts only enhance.

## Review Focus

- A mandate with no contract, or a legacy contract with fee `0` / blank currency: both sides read exactly *Aucun frais de visite.*, never *Frais de visite : , à encaisser…*.
- A co-brokerage visit read by the listing agent (B): never the fee (B gets `CoBrokerageVisitSummary` / 403 on the visit), and its cards keep no amount.
- An ended visit (COMPLETED / CANCELLED / EXPIRED / NO_SHOW): no fee-to-collect line on either visit page, although the DTO still carries it.
- A closed listing (empty `terms`): the shared conditions section is not rendered, so no orphan fee note.
- The public listing page (no mandate): neutral note, and no "Pass Visite" anywhere in the rendered HTML.
- A fee in a tenant currency: formatted by `TenantFormatter`, never the literal "FCFA" in code.

---

### Task 1: Prototype (UI gate)

**Files:**
- Modify: `modules/ndopify-web/src/main/html/mvp/proposition.html`
- Modify: `modules/ndopify-web/src/main/html/mvp/visite.html`
- Modify: `modules/ndopify-web/src/main/html/mvp/annonce.html`
- Modify: `modules/ndopify-web/src/main/html/mvp/assets/css/proposition.css`
- Modify (already in the backup): `modules/ndopify-partner-central/src/main/html/visit.html`
- Modify (already in the backup): `modules/ndopify-partner-central/src/main/html/assets/css/visit.css`
- Modify (already in the backup): `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`

**Interfaces:**
- Consumes: nothing.
- Produces: the approved prototype the later tasks mirror.

- [ ] **Step 1: Port the web fee copy into the MVP prototype**

In `mvp/proposition.html`:

- Header comment (line ~30): replace *"Visits are free: nothing to pay."* with *"The visit fee is paid to the agent offline, never through Ndopify."*
- *Conditions de location* note (~line 279):
  `<p class="terms-note">Les visites sont organisées par votre agent. Frais de visite&nbsp;: <strong>5&nbsp;000&nbsp;FCFA</strong> par visite, réglés directement à lui.</p>`
- ACCEPTED price-card hint (~line 332):
  `<p class="price-card__hint">Visite&nbsp;: <strong>5&nbsp;000&nbsp;FCFA</strong> de frais, réglés directement à Serge. Il vous confirme la date par e-mail.</p>`
- `#visit` popup, just before the submit (~line 571):
  `<p class="visit-request__fee"><svg aria-hidden="true" class="icon"><use href="#i-info"/></svg><span>Frais de visite&nbsp;: <strong>5&nbsp;000&nbsp;FCFA</strong>, à régler directement à Serge lors de la visite. Ndopify ne perçoit aucun paiement.</span></p>`
- Update the popup's header comment (the *"Visits are free"* sentence).

In `mvp/visite.html`:

- Header comment: replace *"Visits are free: no fee, no payment."* with *"The visit fee is paid to the agent offline, never through Ndopify."*
- After the agent block in the summary (~line 123), add:
  `<p class="notice notice--info" data-visit-only="REQUESTED CONFIRMED CHECKED_IN"><svg aria-hidden="true" class="icon"><use href="#i-info"/></svg><span>Frais de visite&nbsp;: <strong>5&nbsp;000&nbsp;FCFA</strong>, à régler directement à Serge lors de la visite. Ndopify ne perçoit aucun paiement.</span></p>`
- In the CONFIRMED on-site *À prévoir* list (~line 235), add:
  `<li><svg aria-hidden="true" class="icon"><use href="#i-info"/></svg>5&nbsp;000&nbsp;FCFA pour les frais de visite, à régler à Serge.</li>`
- In `mvp/assets/js/visite.js`, hide every `[data-visit-only]` whose list of statuses does not contain the shown state (the same switch that shows `[data-visit-state]`); no change if it already does.

In `mvp/annonce.html`, keep the public listing page neutral: under the terms table, add
`<p class="terms-note">Les visites sont organisées par votre agent.</p>`.

In `mvp/assets/css/proposition.css`, add:
```css
.visit-request__fee { display: flex; align-items: flex-start; gap: var(--space-sm); margin: var(--space-md) 0 0; font-size: 0.875rem; line-height: 1.5; }
.visit-request__fee .icon { flex: none; width: 1.125rem; height: 1.125rem; margin-top: 0.15em; color: var(--color-text-info); }
```

- [ ] **Step 2: Confirm the Partner Central prototype from the backup**

The backup already carries `visit.html`'s `.visit-fee` paragraph, `visit.css`'s `.visit-fee` rule and `visit.js`'s hide rule. Apply them to the worktree if they are not there, unchanged.

- [ ] **Step 3: Show the prototype and stop**

Serve both prototypes and open the fee states:
```bash
cd modules/ndopify-web/src/main/html/mvp && python3 -m http.server 8099
cd modules/ndopify-partner-central/src/main/html && python3 -m http.server 8098
```
Show `proposition.html?state=accepted` (note, price hint, popup), `visite.html` (notice, *À prévoir*), `annonce.html` (neutral note), and Partner Central `visit.html` (header line). Stop and wait for the user's OK before any DTO / server / BFF edit.

- [ ] **Step 4: Commit the prototype**

```bash
git add modules/ndopify-web/src/main/html/mvp
git commit -m "feat(web): prototype the visit fee display" -m "Refs #189"
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the visit fee" -m "Refs #189"
```

---

### Task 2: DTO carriers

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/Visit.kt`

**Interfaces:**
- Produces: `Mandate.visitFee: Money = Money()` and `Visit.visitFee: Money = Money()`. `MandateSummary`, `VisitSummary`, `Match` and `CoBrokerageVisitSummary` are unchanged.

- [ ] **Step 1: Add the field**

In `Mandate.kt`, next to `contractId`:
```kotlin
    /** The signed contract's per-visit fee, paid to the agent offline; 0 = none. */
    val visitFee: Money = Money(),
```
In `Visit.kt`, after `type`:
```kotlin
    // The search mandate's contract fee, paid to the agent offline; 0 = none
    val visitFee: Money = Money(),
```
(`Money` is already imported in `Mandate.kt`; add `com.wutsi.ndopify.refdata.dto.Money` to `Visit.kt`.)

- [ ] **Step 2: Build and install the DTO locally**

```bash
cd modules/ndopify-dto && mvn -q install
```
Expected: BUILD SUCCESS. Then set the root `pom.xml`'s `<ndopify-dto.version>` to `0.0.97-SNAPSHOT` (local, uncommitted).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-dto
git commit -m "feat(dto): carry the visit fee on the mandate and the visit" -m "Refs #189"
```

---

### Task 3: Server resolves the fee

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mapper/VisitMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/VisitEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateEndpoint.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/GetVisitEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/resources/db/test/visit/GetVisitEndpoint.sql`

**Interfaces:**
- Consumes: `Mandate.contractId`, `Contract.visitFee` (#188), `Mandate.visitFee` / `Visit.visitFee` (Task 2).
- Produces:
  - `MandateService.visitFee(mandate: MandateEntity): Money`
  - `MandateMapper.toMandate(entity: MandateEntity, visitFee: Money = Money()): Mandate`
  - `VisitMapper.toVisit(visit: VisitEntity, listing: ListingEntity, showCode: Boolean, visitFee: Money = Money()): Visit`

- [ ] **Step 1: Write the failing endpoint tests**

In `GetMandateEndpoint.sql`, add a signed contract and link mandate 1:
```sql
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, duration_months, visit_fee_amount, visit_fee_currency, created_at)
VALUES ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'SIGNED', 6, 5000, 'XAF', '2024-05-20 00:00:00');
UPDATE T_MANDATE SET contract_id = 'c-1' WHERE id = 1;
```
In `GetMandateEndpointTest.get`, add:
```kotlin
assertEquals(Money(5000, "XAF"), mandate.visitFee)
```
(import `com.wutsi.ndopify.refdata.dto.Money`.)

In `GetVisitEndpoint.sql`, add the same `T_CONTRACT` row (`c-1`) and `UPDATE T_MANDATE SET contract_id = 'c-1' WHERE id = 1;` (mandate 2 has none).
In `GetVisitEndpointTest`, add:
```kotlin
    @Test
    fun `the visit fee comes from the mandate's contract`() {
        val response = rest.getForEntity("/v1/visits/1", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(Money(5000, "XAF"), response.body!!.visit.visitFee)
    }

    @Test
    fun `no contract, no visit fee`() {
        val response = rest.getForEntity("/v1/visits/4", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(Money(), response.body!!.visit.visitFee)
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=GetMandateEndpointTest,GetVisitEndpointTest
```
Expected: FAIL — `visitFee` unresolved.

- [ ] **Step 3: Implement the resolution**

In `MandateService`, next to `findById`:
```kotlin
    /** The signed contract's per-visit fee, or no fee when the mandate has no contract (or a legacy blank currency). */
    fun visitFee(mandate: MandateEntity): Money =
        mandate.contractId?.let { contractService.findByIdOrNull(it)?.visitFee } ?: Money()
```
In `MandateMapper.toMandate`, add the `visitFee: Money = Money()` parameter and pass it as `visitFee = visitFee`.
In `MandateEndpoints.get`:
```kotlin
return GetMandateResponse(mandate = mapper.toMandate(mandate, service.visitFee(mandate)))
```
In `VisitMapper.toVisit`, add the `visitFee: Money = Money()` parameter and pass it as `visitFee = visitFee`.
In `VisitEndpoints.get`:
```kotlin
val visitFee = mandateService.findByIdOrNull(visit.mandateId)?.let { mandateService.visitFee(it) } ?: Money()
return GetVisitResponse(visit = mapper.toVisit(visit, listing, showCode = access.isCustomer(visit), visitFee = visitFee))
```
(import `com.wutsi.ndopify.refdata.dto.Money` where needed.)

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=GetMandateEndpointTest,GetVisitEndpointTest
```
Expected: PASS (MySQL running).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate \
        modules/ndopify-server/src/test/resources/db/test/mandate
git commit -m "feat(mandate): return the contract's visit fee" -m "Refs #189"
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit \
        modules/ndopify-server/src/test/resources/db/test/visit
git commit -m "feat(visit): return the visit fee on the visit read" -m "Refs #189"
```

---

### Task 4: Public web app

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/match.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/proposal.css`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/visit.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`, `ListingTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt`, `MatchTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt`, `VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `Mandate.visitFee` / `Visit.visitFee` (Task 2), `TenantFormatter`.
- Produces:
  - `ListingPage.termsNote: String`
  - `MatchPage.visitFee: String?` (the formatted amount, null when there is no fee)
  - `VisitPage.visitFee: String?` (the complete sentence, null when the visit ended)

- [ ] **Step 1: Write the failing view tests**

`ListingPagesTest`:
```kotlin
@Test
fun `the conditions note is neutral`() {
    assertEquals("Les visites sont organisées par votre agent.", page().termsNote)
}
```
`MatchPagesTest`:
```kotlin
@Test
fun `the visit fee is stated before the request`() {
    val page = page(mandate = mandate.copy(visitFee = Money(5000, "XAF")))

    assertEquals("5 000 FCFA", page.visitFee!!.replace('\u00a0', ' '))
    assertEquals(
        "Les visites sont organisées par votre agent. Frais de visite : 5 000 FCFA par visite, réglés directement à lui."
            .replace('\u00a0', ' '),
        page.listing!!.termsNote.replace('\u00a0', ' '),
    )
}

@Test
fun `no fee reads aucun frais de visite`() {
    val page = page()

    assertNull(page.visitFee)
    assertEquals(
        "Les visites sont organisées par votre agent. Aucun frais de visite.",
        page.listing!!.termsNote,
    )
}
```
`VisitPagesTest`:
```kotlin
@Test
fun `an open visit states the fee`() {
    val page = of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF")))

    assertTrue(page.visitFee!!.replace('\u00a0', ' ').contains("5 000 FCFA"))
    assertTrue(page.visitFee!!.contains("Ndopify ne perçoit aucun paiement"))
}

@Test
fun `an ended visit states no fee`() {
    listOf(VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach { status ->
        assertNull(of(visit(status, VisitType.ON_SITE).copy(visitFee = Money(5000, "XAF"))).visitFee)
    }
}

@Test
fun `no fee`() {
    assertEquals("Aucun frais de visite.", of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE)).visitFee)
}

@Test
fun `a video visit states the same fee`() {
    val page = of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO).copy(visitFee = Money(5000, "XAF")))

    assertTrue(page.visitFee!!.replace('\u00a0', ' ').contains("5 000 FCFA"))
}
```
`ListingTemplateTest`, `MatchTemplateTest`, `VisitTemplateTest`: render the page and assert the markup.

- `ListingTemplateTest`: `assertTrue(doc.selectFirst(".terms-note")!!.text().contains("Les visites sont organisées par votre agent."))` and `assertFalse(doc.text().contains("Pass Visite"))`.
- `MatchTemplateTest`: add a `visitFee: String? = null` parameter to the test's `page(...)` helper, set it on `MatchPage`, and when it is non-null set `listing = listing?.copy(termsNote = "Les visites sont organisées par votre agent. Frais de visite : $visitFee par visite, réglés directement à lui.")`. Then:
  ```kotlin
  @Test
  fun `the visit fee is stated before the request`() {
      val doc = render(page(visitFee = "5 000 FCFA"))

      assertTrue(doc.selectFirst(".terms-note")!!.text().contains("Frais de visite : 5 000 FCFA"))
      assertTrue(doc.selectFirst(".answer[data-answer=ACCEPTED] .price-card__hint")!!.text().contains("5 000 FCFA"))
      assertTrue(doc.selectFirst("#visit .visit-request__fee")!!.text().contains("Ndopify ne perçoit aucun paiement"))
      assertFalse(doc.text().contains("Pass Visite"))
  }
  ```
- `VisitTemplateTest`: render a CONFIRMED visit carrying `visitFee = Money(5000, "XAF")` and `assertTrue(doc.selectFirst("[data-visit-fee]")!!.text().contains("5 000 FCFA"))`; render the same COMPLETED visit and `assertNull(doc.selectFirst("[data-visit-fee]"))`.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest,ListingTemplateTest,MatchPagesTest,MatchTemplateTest,VisitPagesTest,VisitTemplateTest
```
Expected: FAIL — `termsNote` / `visitFee` unresolved or absent.

- [ ] **Step 3: Implement the view models**

`ListingPage`: add `val termsNote: String,`.
`ListingPages.of`: set `termsNote = "Les visites sont organisées par votre agent.",`.
`fragments/listing-body.html`: replace the hard-coded note with
```html
<p class="terms-note" th:if="${listing.termsNote}" th:text="${listing.termsNote}">Les visites sont organisées par votre agent.</p>
```
`MatchPage`: add `val visitFee: String?,` (the formatted amount).
`MatchPages.of`:
```kotlin
val visitFee = mandate.visitFee.takeIf { it.amount > 0 }?.let { formatter.money(it.amount, it.currency) }
val termsNote = "Les visites sont organisées par votre agent. " +
    (visitFee?.let { "Frais de visite : $it par visite, réglés directement à lui." } ?: "Aucun frais de visite.")
val listingPage = listing?.takeIf { state != AnswerState.UNAVAILABLE }
    ?.let { ListingPages.of(it, images, locations, tenant, formatter, today).copy(termsNote = termsNote) }
```
and pass `listing = listingPage`, `visitFee = visitFee`.
`match.html`:
- ACCEPTED price-card hint:
```html
<p class="price-card__hint" th:if="${page.visitFee}">Visite&nbsp;: <strong th:text="${page.visitFee}">5 000 FCFA</strong> de frais, réglés directement à <span th:text="${page.agentFirstName}">Serge</span>. Il vous confirme la date par e-mail.</p>
<p class="price-card__hint" th:unless="${page.visitFee}">Aucun frais de visite. <span th:text="${page.agentFirstName}">Serge</span> vous confirme la date par e-mail.</p>
```
- `#visit` popup, before the submit:
```html
<p class="visit-request__fee" th:if="${page.visitFee}">
    <svg aria-hidden="true" class="icon"><use href="#i-info"/></svg>
    <span>Frais de visite&nbsp;: <strong th:text="${page.visitFee}">5 000 FCFA</strong>, à régler directement à <span th:text="${page.agentFirstName}">Serge</span> lors de la visite. Ndopify ne perçoit aucun paiement.</span>
</p>
<p class="visit-request__fee" th:unless="${page.visitFee}">
    <svg aria-hidden="true" class="icon"><use href="#i-info"/></svg><span>Aucun frais de visite.</span>
</p>
```
`proposal.css`: add the `.visit-request__fee` rule from Task 1 (the served design tokens are the same).
`VisitPage`: add `val visitFee: String?,`.
`VisitPages.of`:
```kotlin
val visitFee = if (visit.status in setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)) {
    visit.visitFee.takeIf { it.amount > 0 }?.let {
        "Frais de visite : ${formatter.money(it.amount, it.currency)}, à régler directement à ${agent?.party?.firstName?.ifBlank { null } ?: "votre agent"} lors de la visite. Ndopify ne perçoit aucun paiement."
    } ?: "Aucun frais de visite."
} else {
    null
}
```
and pass `visitFee = visitFee`.
`visit.html`, inside `.visit-summary` after the agent block:
```html
<p class="notice notice--info" data-visit-fee th:if="${page.visitFee}">
    <svg aria-hidden="true" class="icon"><use href="#i-info"/></svg>
    <span th:text="${page.visitFee}">Frais de visite : 5 000 FCFA, à régler directement à Serge lors de la visite. Ndopify ne perçoit aucun paiement.</span>
</p>
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest,ListingTemplateTest,MatchPagesTest,MatchTemplateTest,VisitPagesTest,VisitTemplateTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): show the visit fee on the match and visit pages" -m "Refs #189"
```

---

### Task 5: Partner Central

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `Visit.visitFee` (Task 2), `VisitRules.OPEN`, `TenantFormatter`.
- Produces: `VisitPage.visitFee: String?` (the complete sentence, null when the visit ended).

- [ ] **Step 1: Write the failing tests**

In `VisitTemplateTest`, with a requested / confirmed / checked-in visit carrying `visitFee = Money(5000, "XAF")`:
```kotlin
assertTrue(panel.text().contains("Frais de visite : 5 000 FCFA, à encaisser auprès du client à la visite."))
```
with a completed / cancelled / expired / no-show visit carrying the same fee:
```kotlin
assertNull(panel.selectFirst("[data-visit-fee]"))
```
with a visit carrying `Money()`:
```kotlin
assertEquals("Aucun frais de visite.", panel.selectFirst("[data-visit-fee]")!!.text())
```
Add `visitFee = Money(5000, "XAF")` to `VisitFixtures.requested()` so the existing render tests carry a fee.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest
```
Expected: FAIL — no `[data-visit-fee]`.

- [ ] **Step 3: Implement**

`VisitPage`: add `val visitFee: String?,`.
`VisitViews.of`, after `typeLabel`:
```kotlin
val visitFee = if (visit.status in VisitRules.OPEN) {
    visit.visitFee.takeIf { it.amount > 0 }?.let {
        "Frais de visite : ${formatter.money(it.amount, it.currency)}, à encaisser auprès du client à la visite."
    } ?: "Aucun frais de visite."
} else {
    null
}
```
and pass `visitFee = visitFee`.
`templates/visit.html`, under `.visit-type` in the header:
```html
<p class="visit-fee" data-visit-fee th:if="${p.visitFee}">
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M10 9v5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="10" cy="6.25" r="1" fill="currentColor"/></svg>
    <span th:text="${p.visitFee}">Frais de visite : 5 000 FCFA, à encaisser auprès du client à la visite.</span>
</p>
```
`public/assets/css/visit.css`: add
```css
.visit-fee { display: flex; align-items: flex-start; gap: var(--space-2); font-size: var(--step-hint); line-height: 1.5; }
.visit-fee .icon { flex: none; width: 1.125rem; height: 1.125rem; margin-top: 0.1em; color: var(--color-info-fg); }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): show the visit fee on the visit page" -m "Refs #189"
```

---

### Task 6: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (mandate and visit domains)
- Modify: `modules/ndopify-web/CLAUDE.md` (match and visit pages, the listing note)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (visit page)

- [ ] **Step 1: Document the new reads and rules**

- Server *mandate*: `GET /v1/mandates/{id}` carries `visitFee`, read through from the mandate's contract (`MandateService.visitFee`); no fee when the mandate has no contract; `MandateSummary` doesn't carry it.
- Server *visit*: `GET /v1/visits/{id}` carries `visitFee` from the mandate's contract; `VisitSummary` and `CoBrokerageVisitSummary` don't (so the listing agent never sees it).
- Web: the match page states the fee in the *Conditions de location* note, the request card and the popup; the visit page in its summary, only while REQUESTED / CONFIRMED / CHECKED_IN; the public listing page's note is neutral.
- Partner Central: the visit page header states the fee while REQUESTED / CONFIRMED / CHECKED_IN (the check-in window included), hidden once done or called off.

- [ ] **Step 2: Verify every touched module**

```bash
cd modules/ndopify-dto && mvn verify
cd modules/ndopify-server && mvn verify      # MySQL required
cd modules/ndopify-web && mvn verify
cd modules/ndopify-partner-central && mvn verify
```
Expected: BUILD SUCCESS in each (ktlint, tests, JaCoCo gate).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-web/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(visit): document the visit fee display" -m "Refs #189"
```
