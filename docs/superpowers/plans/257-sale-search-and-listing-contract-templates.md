# Sale SEARCH and LISTING Contract Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a SALE mandate (buyer's SEARCH, seller's LISTING) its own signed contract — its own type and name, its own document, a commission as a percentage of the sale price typed by the agent (default 5 %), the duration, and the seller's exclusivity — instead of the rental contract that promises *1 mois de loyer*.

**Architecture:** Two `ContractType` values (`SALE_SEARCH`, `SALE_LISTING`) tell the e-mail, the e-signature page and the document apart from a rental. The commission rate is one new column on `T_CONTRACT`, set by the agent on *Envoyer le mandat* through `RequestMandateApprovalRequest.commissionRate`, validated 1..20, defaulted from `ndopify.mandate.contract.sale-commission-rate` when the API omits it, and printed by two new MJML templates that `MandateContractGenerator` picks from the mandate's `mandateType` + `listingType = SALE`. Partner Central ports the approved prototype; the e-signature portal only gains the two labels.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, MJML + mustache (`MailBodyResolver`), Flyway on MySQL 8, the esbuild `partner-central` bundle, JUnit 5 + `kotlin.test`, `MockMvc`, `TestRestTemplate`, mockito-kotlin, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/257

**Design record (epic roadmap):** [`docs/superpowers/plans/2026-11-22-v2-property-sales.md`](https://github.com/wutsi/ndopify-mono/blob/main/docs/superpowers/plans/2026-11-22-v2-property-sales.md) — *Story 10*.

**Approved prototype (the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/mandate/contract.html` (the *Commission* field)
- `modules/ndopify-partner-central/src/main/html/v2/mandate.html` (*Termes du contrat* with the sale's *Commission* row)
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/partner-central.js` (contract data, pre-fill, check)
- `modules/ndopify-partner-central/src/main/html/v2/assets/css/home.css` (the `% du prix` suffix's room)
- `modules/ndopify-partner-central/src/main/html/v2/README.md` (change list, samples)

Review at `http://localhost:8899` (`python3 -m http.server 8899` in that folder): `mandate/contract.html?id=302|301|104|201`,
`mandate.html?id=301|302|202|203|104`.

## Global Constraints

- **The prototype is the design.** Tasks 2 and its template test port it into Thymeleaf, the production script and CSS; they change no label, order, message or state it settled. `src/main/html/` is never served and never edited here — it is committed with this plan.
- **French UI, English code.** Every label, hint, error and contract clause is French; classes, KDoc, comments, logs, plan and commits are English. Amounts and dates go through `TenantFormatter`, never a hard-coded pattern or "FCFA".
- **`UI: yes`, `Shared code: ndopify-dto`.** The DTO change must be released before the server, Partner Central and the e-signature portal can deploy (Task 8).
- **The rate is a whole percentage, 1..20, default 5.** No decimal, no floating point; a blank/0/21/`5,5` is a field error on the page and a 400 from the API.
- **Each mandate states its own party's commission.** The buyer's SEARCH sale contract says the buyer owes it; the seller's LISTING sale contract says the seller owes it. Never both on one document, never the rental's *1 mois de loyer* on a sale.
- **Only the seller's sale mandate is exclusive.** The SEARCH sale template has no exclusivity clause.
- **Duration unchanged:** 1–12 months on the page, default 6, 1–36 accepted by the server. No expiry/reminder work (story 11 is M3).
- **No rental regression.** A `RENTAL` mandate (`listingType` `RENTAL` **or** legacy `UNKNOWN`) keeps its type, template, name, number, terms and page: byte-identical documents, and a commission rate sent through the API is stored as none.
- **No financing, documents or ownership-documents rows in the contracts.** Those are the agent's brief (#253, #254), shown in the offer and on the mandate page — not the signed mandate.
- Never touch `ndopify-web`. No new dependency. No `application*.yml` change beyond the one default of Task 5.
- `mvn verify` green in each touched module (ktlint, tests, JaCoCo gate). `ndopify-server` tests need local MySQL; the other modules need neither MySQL nor a running server.
- Commits are conventional and scoped: `feat(dto): …`, `feat(server): …`, `feat(npc): …`, `feat(esignature): …`, `chore(deps): …`, each with `Refs #257` in the body.

## Review Focus

- **A rental is untouched.** Its `ContractType`, its template, the *MANDAT DE RECHERCHE IMMOBILIÈRE (LOCATION)* / *MANDAT EXCLUSIF DE MISE EN LOCATION* titles, its *1 mois de loyer* clause, its name in the e-mail and on the sign page, and its page. Tasks 3, 4 and 6 pin it; Task 5's test posts a rate on a rental and asserts null.
- **A legacy mandate (`listingType = UNKNOWN`) takes the rental path.** The generator must not fall into the `SALE` branch on a null/unknown type. Task 4's test is on `UNKNOWN`.
- **A sale contract without a rate can't exist.** `ContractService.create` refuses a sale type with a null rate; the API's 400 is what stops a hand-made contract. Task 3.
- **The document states no amount.** Only `X % du prix de vente`: a buyer's budget is a maximum and the final price is recorded at closing (#258). Task 4's tests assert `"5 %"`, never a money amount next to it.
- **The field still posts without JavaScript**, and the server re-checks it. Task 2's template test asserts `required` and the field's presence; Task 5's endpoint test sends the raw JSON.
- **Re-sending keeps the rate** (`setTerms` applies it with the duration and the content, in one transaction). Task 5.
- **The signed PDF follows the content.** The renderer is unchanged; Task 4 renders a sale document through `SignedContractRenderer`'s existing coverage (no new code) and Task 3's contract hash still gates the signature.

## File Structure

- `ndopify-dto` — the enum and the three request/response fields (Task 1).
- `ndopify-server` — contract storage (Task 3), the two documents and their selection (Task 4), the mandate's terms and the default (Task 5), the e-mail's name (Task 6).
- `ndopify-partner-central` — the send page, its rules, the mandate page's terms (Task 2).
- `ndopify-esignature` — the sign page's name (Task 7).

---

### Task 1: The DTO's sale contract types and commission rate

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/ContractType.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/Contract.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/CreateContractRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/RequestMandateApprovalRequest.kt`
- Test: none — `ndopify-dto` has no tests; it is covered by the server's and the clients' tests (Tasks 3–7).

**Interfaces:**
- Produces: `enum class ContractType { UNKNOWN, SEARCH, LISTING, SALE_SEARCH, SALE_LISTING, LEASE_AGREEMENT }` — `SALE_SEARCH` is a buyer's sale mandate, `SALE_LISTING` a seller's; both keep the `MNDT` number prefix.
- Produces on `Contract`, right after `visitFee`:
  `val commissionRate: Int? = null` — KDoc: the success commission in % of the sale price (1..20); a sale mandate only, null otherwise.
- Produces on `CreateContractRequest`, right after `visitFee`: `val commissionRate: Int? = null` — KDoc: a sale contract's commission; ignored by the other types.
- Produces on `RequestMandateApprovalRequest`, after `visitFee`: `val commissionRate: Int? = null`, and the class KDoc gains "…and, for a sale mandate, its commission in % of the sale price".

- [ ] **Step 1: Add the two enum values, in that order**

`SALE_SEARCH` and `SALE_LISTING` go after `LISTING`, before `LEASE_AGREEMENT`. Do not reorder the existing values (they are stored by name, but the reader's diff stays small).

- [ ] **Step 2: Add the field to the three types**

Same name and KDoc on all three, in the position above. Keep `Contract`'s member order otherwise untouched so `ContractMapper`'s diff is readable.

- [ ] **Step 3: Build the DTO for the reactor**

```bash
cd modules/ndopify-dto && mvn install -Dheadless=true
```

Expect a green build; the other modules now resolve the new values and fields. Do not release yet (Task 8).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/RequestMandateApprovalRequest.kt
git commit -m "feat(dto): add the sale contract types and their commission rate" -m "Refs #257"
```

---

### Task 2: Port the approved prototype into Partner Central

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/contract.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/home.css` (the prototype's `input-unit--percent` rule and the one-field-per-line comment)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `RequestMandateApprovalRequest.commissionRate`, `Contract.commissionRate` (Task 1).
- Produces: `data class MandateContractForm(val durationMonths: String = "", val visitFee: String = "", val commissionRate: String = "")` — still strings, so a hand-edited value is a field error, not a 400.
- Produces in `MandateContractRules`:
  - `const val DEFAULT_COMMISSION = 5`, `const val MIN_COMMISSION = 1`, `const val MAX_COMMISSION = 20`
  - `fun prefill(contract: Contract?): MandateContractForm` — third answer `(contract?.commissionRate?.takeIf { it > 0 } ?: DEFAULT_COMMISSION).toString()`.
  - `fun errors(form: MandateContractForm, currentDuration: Int? = null, sale: Boolean = false): Map<String, String>` — when `sale`, `errors["commissionRate"] = "Indiquez un pourcentage entier, de 1 à 20 (par exemple 5)."` unless the trimmed value is digits only and in 1..20; the duration and visit-fee checks are unchanged.
  - `fun request(form: MandateContractForm, currency: String, sale: Boolean = false): RequestMandateApprovalRequest` — `commissionRate = if (sale) commission(form.commissionRate) else null`, with `private fun commission(value: String): Int?` (spaces/NBSP trimmed, digits only, `toIntOrNull()?.takeIf { it in MIN_COMMISSION..MAX_COMMISSION }`).
- Produces in `MandateContractController`: `render(agent, mandate, contract, form, errors, model)` adds `model.addAttribute("sale", mandate.listingType == ListingType.SALE)`; `send` computes `val sale = mandate?.listingType == ListingType.SALE` and passes it to `errors` and `request`.
- Produces on `MandateView`: `val commission: String?` — "5 % du prix de vente"; null without a contract or for a rental. `MandateViews.toView` fills it from `contract.commissionRate` when `mandate.listingType == ListingType.SALE`.
- Produces in `templates/mandate/contract.html`, after the *Durée* field, the prototype's field verbatim (`label Commission`, `input-unit input-unit--percent`, `inputmode="numeric"`, `required`, suffix `% du prix`, the hint *Du prix de vente final, due seulement si la vente se conclut grâce à vous. Réglée directement à vous.*, the error paragraph), wrapped in `th:if="${sale}"`, with `th:value="${form.commissionRate}"`, `aria-invalid` off `errors['commissionRate']`. The `mandate-terms__fields` comment says one field per line at every width, as the prototype.
- Produces in `templates/mandate.html`, in *Termes du contrat*, after the *Durée* row: a *Commission* row `th:if="${m.commission}"` with `th:text="${m.commission}"`.

- [ ] **Step 1: Write the failing rules tests**

Extend `MandateContractRulesTest`:

```kotlin
@Test
fun `prefill - the sale rate, or the default`() {
    assertEquals("7", MandateContractRules.prefill(Contract(durationMonths = 6, commissionRate = 7)).commissionRate)
    assertEquals("5", MandateContractRules.prefill(Contract(durationMonths = 6)).commissionRate)
}

@Test
fun `errors - a sale's commission is a whole percentage from 1 to 20`() {
    assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("6", "0", "5"), sale = true))
    assertTrue(MandateContractRules.errors(MandateContractForm("6", "0", ""), sale = true).containsKey("commissionRate"))
    for (invalid in listOf("0", "21", "-1", "5,5", "abc")) {
        assertTrue(
            MandateContractRules.errors(MandateContractForm("6", "0", invalid), sale = true).containsKey("commissionRate"),
            invalid,
        )
    }
    // A rental is never checked, whatever the hidden field holds
    assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("6", "0", "")))
}

@Test
fun `request - the rate on a sale, null on a rental`() {
    assertEquals(7, MandateContractRules.request(MandateContractForm("6", "0", "7"), "XAF", sale = true).commissionRate)
    assertNull(MandateContractRules.request(MandateContractForm("6", "0", "7"), "XAF", sale = false).commissionRate)
}
```

Update the existing three tests to the three-answer `MandateContractForm` (`prefill` now returns `"5"`/`"7"` as its third value) and to `RequestMandateApprovalRequest(3, Money(5000, "XAF"), null)` in `request`.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateContractRulesTest
```

Fails to compile: `commissionRate` unknown.

- [ ] **Step 3: Implement the form and the rules**

As in *Interfaces*. Keep `DEFAULT_MONTHS`, `MIN_MONTHS`, `MAX_MONTHS`, `months` and `amount` untouched.

- [ ] **Step 4: Write the failing controller, view and template tests**

`MandateContractControllerTest` (standalone `MockMvc`):

```kotlin
@Test
fun `the send page of a sale shows the commission pre-filled`() { /* SALE mandate, no contract → model form.commissionRate "5", model sale true */ }

@Test
fun `the send page of a rental has no sale terms`() { /* RENTAL mandate → model sale false */ }

@Test
fun `a sale without a commission is a field error`() { // POST SALE, duration 6, visitFee 0, commissionRate "" → view, errors["commissionRate"] present, contract client never called }

@Test
fun `a sale at 7 percent sends the rate`() { /* POST SALE commissionRate "7" → 3xx, request.commissionRate 7 */ }

@Test
fun `a rental ignores a commission`() { /* POST RENTAL commissionRate "7" → 3xx, request.commissionRate null */ }
```

`MandateViewsTest`:

```kotlin
@Test
fun `a sale contract shows its commission`() {
    // mandate SALE + contract commissionRate 7 → view.commission == "7 % du prix de vente"
}

@Test
fun `a rental has no commission row`() { /* mandate RENTAL + contract commissionRate null → view.commission null */ }
```

`MandateContractTemplateTest` (real Thymeleaf, jsoup): a sale renders the `#commissionRate` input, enabled, `required`, `% du prix`, the hint and the error paragraph; a rental renders no `#commissionRate`. `MandateTemplateTest`: a sale with a commission renders the *Commission* row and its text; a rental renders none.

- [ ] **Step 5: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateContractControllerTest+MandateViewsTest+MandateContractTemplateTest+MandateTemplateTest'
```

- [ ] **Step 6: Implement the controller, the views and the templates**

As in *Interfaces*; the template markup and CSS copy the approved prototype (`src/main/html/v2/mandate/contract.html`, `v2/assets/css/home.css`, `v2/mandate.html`) exactly — labels, suffix, hint, error text, row order.

- [ ] **Step 7: Run the module gate**

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): send a sale mandate its commission, duration and terms" -m "Refs #257"
```

---

### Task 3: The contract stores and validates the sale commission

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V16_0__contract-commission-rate.sql` (the next free number above `V15_0`; if #253/#254 landed their `V16_x` first, the one after)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/domain/ContractEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mapper/ContractMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/service/ContractServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/CreateContractEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/GetContractEndpointTest.kt`

**Interfaces:**
- Consumes: `ContractType.SALE_SEARCH`, `ContractType.SALE_LISTING`, `CreateContractRequest.commissionRate`, `Contract.commissionRate` (Task 1).
- Produces on `ContractEntity`, after `visitFeeCurrency`: `val commissionRate: Int? = null` — KDoc: the success commission in % of the sale price; null for a rental or a legacy row.
- Produces in `ContractService`:
  - `private val SALE_TYPES = setOf(ContractType.SALE_SEARCH, ContractType.SALE_LISTING)` in the companion.
  - In `create`, before saving: a present rate outside 1..20 is `invalidParameter("commissionRate", …)`; a sale type without one is `invalidParameter("commissionRate", "null")`; a non-sale type stores null whatever was sent.
  - `fun setTerms(id: String, durationMonths: Int? = null, visitFee: Money? = null, commissionRate: Int? = null): ContractEntity` — the same 1..20 check, applied only when the stored `contract.type in SALE_TYPES` (a rental ignores it), a null keeping the current value.
- Produces: `ContractMapper.toContract` copies `commissionRate`; `toContractSummary` is unchanged.

- [ ] **Step 1: Write the failing tests**

`ContractServiceTest`:

```kotlin
@Test
fun `create - a sale contract keeps its commission`() { /* type SALE_LISTING, commissionRate 7 → entity.commissionRate 7 */ }

@Test
fun `create - a sale contract needs a commission`() { /* SALE_SEARCH without one → BadRequestException, ErrorCode.HTTP_INVALID_PARAMETER */ }

@Test
fun `create - an out-of-range commission is 400`() { /* 0 and 21 → BadRequestException */ }

@Test
fun `create - a rental ignores the commission`() { /* type SEARCH, commissionRate 7 → entity.commissionRate null */ }

@Test
fun `set terms - a sale's commission, a rental's ignored`() { /* SALE: 7 stored; RENTAL: setTerms(..., 7) leaves null */ }
```

`CreateContractEndpointTest` / `GetContractEndpointTest` (MySQL): POST a `SALE_SEARCH` with `commissionRate = 7` → 200, GET → `commissionRate 7`; POST a `SALE_SEARCH` without it → 400 `HTTP_INVALID_PARAMETER` on `commissionRate`; POST a `SEARCH` with 7 → GET `commissionRate` null.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='ContractServiceTest+CreateContractEndpointTest+GetContractEndpointTest'
```

Fails to compile: `commissionRate` unknown on the entity.

- [ ] **Step 3: Write the migration**

```sql
ALTER TABLE T_CONTRACT
    ADD COLUMN commission_rate INT NULL;
```

Nullable, no default: a rental and every legacy contract have none.

- [ ] **Step 4: Entity, mapper, service**

As in *Interfaces*. Keep the existing duration and visit-fee validation in the same order (type, duration, parties, then the rate, then the last signature-required check), so the error a caller sees first is unchanged for the existing fields.

- [ ] **Step 5: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='ContractServiceTest+CreateContractEndpointTest+GetContractEndpointTest' && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): store a sale contract's commission rate" -m "Refs #257"
```

---

### Task 4: The two sale contract documents

**Files:**
- Create: `modules/ndopify-server/src/main/resources/mandate/contract/search_sale.mjml`
- Create: `modules/ndopify-server/src/main/resources/mandate/contract/listing_sale.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGenerator.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/SearchSaleTemplateTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/ListingSaleTemplateTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGeneratorTest.kt` (the rental cases stay; add the sale ones)

**Interfaces:**
- Consumes: `ContractEntity.commissionRate` (Task 3), `MandateEntity.listingType` (`SALE` / `RENTAL` / legacy `UNKNOWN`).
- Produces in `MandateContractGenerator`:
  - `private val SALE_TEMPLATES = mapOf(MandateType.SEARCH to "/mandate/contract/search_sale.mjml", MandateType.LISTING to "/mandate/contract/listing_sale.mjml")` next to `TEMPLATES`.
  - `fun generate(mandate, contract)` picks `SALE_TEMPLATES` when `mandate.listingType == ListingType.SALE`, else `TEMPLATES`; an unknown `mandateType` still throws the existing `IllegalArgumentException`.
  - In `context`: `"commission" to contract.commissionRate?.takeIf { it > 0 }?.let { rate -> "$rate %" }` — the rental templates don't reference it.
- Produces: the two documents. Copy each rental template, then apply exactly these edits.

**`search_sale.mjml` (copy of `search.mjml`):**
1. Header comment: `Mandat de recherche immobilière (achat).` and add `commission` to the `contract:` line of the value list.
2. Title `<h2>`: `MANDAT DE RECHERCHE IMMOBILIÈRE (ACHAT)`.
3. *Objet du mandat*: `Le Mandant confie au Mandataire la mission de rechercher un bien immobilier à acheter répondant aux critères suivants :`.
4. *Le bien*: drop the `{{#furniture}}` block (a purchase never asks furniture).
5. Terms heading `Les conditions d'achat`; keep only `<li><b>Budget maximum</b> : {{budget}}</li>`; delete the *Caution*, *Avance sur loyer*, *Durée du bail*, *Préavis* and *Emménagement souhaité* blocks.
6. Sections numbered `2. DURÉE`, `3. HONORAIRES ET FRAIS`, `4. ENGAGEMENTS DES PARTIES`.
7. In *HONORAIRES ET FRAIS*, keep *Frais de dossiers* and *Frais de visite* verbatim, and replace the *Commission de succès* item with:
   `{{commission}} du prix de vente, exigible uniquement à la signature de l'acte de vente du bien acheté par l'intermédiaire du Mandataire. Aucun paiement n'est effectué via Ndopify.`
8. *ENGAGEMENTS DES PARTIES*: `Le Mandataire s'engage à proposer des biens conformes aux critères du Mandant, à vérifier les documents de propriété des biens proposés et à accompagner le Mandant lors des visites et jusqu'à la signature de l'acte de vente.` and `Le Mandant s'engage à verser la commission convenue si l'achat est conclu sur un bien présenté par {{tenant_name}}`.
9. No exclusivity section.

**`listing_sale.mjml` (copy of `listing.mjml`):**
1. Header comment: `Mandat exclusif de vente.` and add `commission` to the `contract:` line.
2. Title: `MANDAT EXCLUSIF DE VENTE`.
3. *Objet du mandat*: `Le Mandant confie au Mandataire, à titre exclusif, la mission de rechercher un acquéreur pour le bien immobilier décrit ci-dessous, et de le présenter au Mandant en vue de la signature de l'acte de vente.`
4. *Le bien*: drop the `{{#furniture}}` block; heading `Les conditions de vente` with `<li><b>Prix de vente demandé</b> : {{budget}}</li>` and nothing else (delete *Caution*, *Avance sur loyer*, *Durée du bail*, *Préavis*, *Disponible à partir du*).
5. Keep the owner-declaration note, `2. DURÉE` verbatim.
6. *EXCLUSIVITÉ*: `Pendant toute la durée du mandat, le Mandant s'interdit de vendre le bien, directement ou par l'intermédiaire d'une autre agence ou d'un autre intermédiaire. Il transmet au Mandataire toute demande d'achat qu'il reçoit.`
7. *HONORAIRES ET FRAIS*: keep *Frais de dossier* and *Frais de visite*, replace *Commission de succès* with:
   `{{commission}} du prix de vente, exigible uniquement à la signature de l'acte de vente avec un acquéreur présenté par le Mandataire.`
8. *VISITE D'ÉVALUATION ET PUBLICATION*: `… avant sa mise en vente.`; `toute modification du prix de vente …`; `… conditions de vente et localisation approximative …`; `… elle n'est communiquée qu'aux acquéreurs, pour les visites.`; `L'annonce est retirée dès la vente du bien ou à la fin du mandat.`
9. *ENGAGEMENTS DES PARTIES*: `… présenter au Mandant des acquéreurs sérieux, que le Mandant reste libre d'accepter ou de refuser.`; `Le Mandant déclare être propriétaire du bien, ou habilité à le vendre …`; `… à verser la commission convenue si la vente est conclue avec un acquéreur présenté par {{tenant_name}}.`

- [ ] **Step 1: Write the failing template tests**

Mirror `SearchRentalTemplateTest` / `ListingRentalTemplateTest`:

```kotlin
class SearchSaleTemplateTest {
    private val required = mapOf(
        "contract_number" to "MNDT-2026-000001", "contract_date" to "17/10/2026", "duration_months" to "6",
        "client_name" to "Ray Sponsible", "tenant_name" to "Ndopify", "property_type" to "Appartement",
        "city" to "Yaoundé", "budget" to "10 000 000 XAF", "commission" to "5 %",
    )

    @Test
    fun `a purchase, its budget and its commission`() {
        val html = resolver.resolve("/mandate/contract/search_sale.mjml", required + mapOf("rooms" to "3 chambres"))

        assertTrue(html.contains("MANDAT DE RECHERCHE IMMOBILIÈRE (ACHAT)"))
        assertTrue(html.contains("rechercher un bien immobilier à acheter"))
        assertTrue(html.contains("10 000 000 XAF"))
        assertTrue(html.contains("5 % du prix de vente"))
        assertFalse(html.contains("/ mois"))
        assertFalse(html.contains("1 mois de loyer"))
        assertFalse(html.contains("Caution"))
        assertFalse(html.contains("Durée du bail"))
        assertFalse(html.contains("EXCLUSIVITÉ"))
        assertFalse(html.contains("Meublé"))
    }

    @Test
    fun `every optional criterion`() { /* the rental test's context minus furniture/rental terms, plus commission */ }
}
```

`ListingSaleTemplateTest` likewise: title `MANDAT EXCLUSIF DE VENTE`, `rechercher un acquéreur`, `Prix de vente demandé`, `5 % du prix de vente`, `EXCLUSIVITÉ`, no `/ mois`, no `Caution`, no `Disponible à partir du`, no `1 mois de loyer`, no `Meublé`.

`MandateContractGeneratorTest`, one case each:

```kotlin
@Test
fun `a sale search uses the sale template and its commission`() {
    // mandate SALE + SEARCH, contract commissionRate 7 → html contains "MANDAT DE RECHERCHE IMMOBILIÈRE (ACHAT)" and "7 %"
}

@Test
fun `a sale listing uses the sale template`() { /* SALE + LISTING → "MANDAT EXCLUSIF DE VENTE" */ }

@Test
fun `an unknown listing type keeps the rental template`() { /* listingType UNKNOWN + SEARCH → "(LOCATION)" */ }
```

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='SearchSaleTemplateTest+ListingSaleTemplateTest+MandateContractGeneratorTest'
```

Fails: no such template / `commission` not in the context.

- [ ] **Step 3: Write the two templates**

Copy the rental files and apply the edits above; keep `<mj-style inline="inline">{{__css__}}</mj-style>`, the logo block, the parties list and the contact/comments section as they are.

- [ ] **Step 4: Update the generator**

As in *Interfaces*.

- [ ] **Step 5: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='SearchSaleTemplateTest+ListingSaleTemplateTest+MandateContractGeneratorTest+SearchRentalTemplateTest+ListingRentalTemplateTest' && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): add the sale search and listing contract documents" -m "Refs #257"
```

---

### Task 5: The send page's terms become the sale contract's

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (`ndopify.mandate.contract`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/RequestMandateApprovalEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/resources/db/test/mandate/RequestMandateApprovalEndpoint.sql`

**Interfaces:**
- Consumes: `ContractType.SALE_SEARCH` / `SALE_LISTING`, `RequestMandateApprovalRequest.commissionRate` (Task 1), `ContractService.setTerms(…, commissionRate)` (Task 3).
- Produces in `MandateService`:
  - `@param:Value("\${ndopify.mandate.contract.sale-commission-rate}") private val saleCommissionRate: Int` after `contractDurationMonths`.
  - `createContract`: `val sale = mandate.listingType == ListingType.SALE`; `type` becomes `SALE_SEARCH` / `SALE_LISTING` for a sale, the existing `SEARCH` / `LISTING` otherwise; `commissionRate = if (sale) request?.commissionRate ?: saleCommissionRate else null`.
  - `requestApproval`: `request?.commissionRate?.let { rate -> if (rate !in 1..20) throw invalid("commissionRate") }`, before the duration check's siblings.
  - `withTerms`: `commissionRate = request?.commissionRate ?: commissionRate` (the in-memory copy the content is generated from; `setTerms` normalizes a rental).
  - `completeApproval`: `contractService.setTerms(contractId, request?.durationMonths, request?.visitFee, request?.commissionRate)`.
- Produces in `application.yml`, under `ndopify.mandate.contract`: `sale-commission-rate: 5   # A sale mandate's success commission, in % of the sale price`.

- [ ] **Step 1: Add the sale mandates to the fixture**

In `RequestMandateApprovalEndpoint.sql`, add after mandate 5:

```sql
    (6, 1, 10, 20, null, 'ASSIGNED', 'SEARCH', 'SALE', 2370201, 'VILLA', null, 45000000, 'NONE', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (7, 1, 10, 20, null, 'ASSIGNED', 'LISTING', 'SALE', 2370201, 'LAND', null, 15000000, 'NONE', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

- [ ] **Step 2: Write the failing tests**

```kotlin
@Test
fun `request approval - a sale search gets a sale contract and the default commission`() {
    val response = rest.postForEntity("/v1/mandates/6/request-approval", null, Any::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    val captured = argumentCaptor<CreateContractRequest>()
    verify(contractService).create(captured.capture())
    assertEquals(ContractType.SALE_SEARCH, captured.firstValue.type)
    assertEquals(5, captured.firstValue.commissionRate)
}

@Test
fun `request approval - a sale listing keeps the rate it is sent`() {
    val response = rest.postForEntity(
        "/v1/mandates/7/request-approval",
        RequestMandateApprovalRequest(durationMonths = 12, commissionRate = 7),
        Any::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val generated = argumentCaptor<ContractEntity>()
    verify(contractGenerator).generate(any(), generated.capture())
    assertEquals(7, generated.firstValue.commissionRate)
    verify(contractService).setTerms("c-7", 12, null, 7)
}

@Test
fun `request approval - a rental keeps no commission`() { /* POST mandate 1 with commissionRate 7 → create's rate null */ }

@Test
fun `request approval - an out-of-range commission is 400`() { /* 0 and 21 → HTTP_INVALID_PARAMETER, create never */ }
```

Update the existing `setTerms("c-4", 3, Money(5000, "XAF"))` verification to `setTerms("c-4", 3, Money(5000, "XAF"), null)`.

- [ ] **Step 3: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest=RequestMandateApprovalEndpointTest
```

Fails to compile: the fourth `setTerms` argument, `CreateContractRequest.commissionRate` untouched by `createContract`.

- [ ] **Step 4: Implement the service and the default**

As in *Interfaces*. Give `saleCommissionRate` a KDoc line: the rate a sale contract gets when the request omits it (the API's default, like `duration-months`).

- [ ] **Step 5: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='RequestMandateApprovalEndpointTest' && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): send a sale mandate's terms to its contract" -m "Refs #257"
```

---

### Task 6: The signature e-mail names the sale contract

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt`

**Interfaces:**
- Produces in `ContractSignatureMailet.TYPES`: `ContractType.SALE_SEARCH to "Mandat de recherche (achat)"`, `ContractType.SALE_LISTING to "Mandat de vente"`. The three existing entries stay verbatim.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a sale search is a purchase search`() {
    mailet.send(contract(type = ContractType.SALE_SEARCH), party) // as the existing case does

    assertEquals("Mandat de recherche (achat)", context.firstValue["contract_type"])
}

@Test
fun `a sale listing is a sale mandate`() { /* SALE_LISTING → "Mandat de vente" */ }
```

Build them from the existing `contract_type` assertions (the file already scopes a context captor).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest=ContractSignatureMailetTest
```

Expect `Contrat` (the fallback) for both.

- [ ] **Step 3: Add the two labels**

As in *Interfaces*; nothing else changes.

- [ ] **Step 4: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='ContractSignatureMailetTest+ContractSignatureTemplateTest' && mvn verify -Dheadless=true
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): name a sale contract in the signature request" -m "Refs #257"
```

---

### Task 7: The e-signature portal names the sale contract

**Files:**
- Modify: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignViews.kt`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignViewsTest.kt`

**Interfaces:**
- Produces in `SignViews.TYPES`: the same two entries and labels as Task 6 (*Mandat de recherche (achat)*, *Mandat de vente*), so the page names the contract as the e-mail did. The comment above the map already says so.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a sale contract is named as in its e-mail`() {
    // contract(type = ContractType.SALE_LISTING, a signer party) → view.contractType == "Mandat de vente"
    // SALE_SEARCH → "Mandat de recherche (achat)"
}
```

- [ ] **Step 2: Run it, expect failure**

```bash
cd modules/ndopify-esignature && mvn test -Dtest=SignViewsTest
```

- [ ] **Step 3: Add the two labels**

- [ ] **Step 4: Run the module gate**

```bash
cd modules/ndopify-esignature && mvn verify -Dheadless=true
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-esignature
git commit -m "feat(esignature): name a sale contract on the sign page" -m "Refs #257"
```

---

### Task 8: Release the DTO and verify the reactor

**Files:**
- Modify: `modules/ndopify-dto/pom.xml` (version)
- Modify: `pom.xml` (`ndopify-dto.version`)

**Interfaces:**
- Consumes: Task 1's DTO and every module built against its `-SNAPSHOT`.
- Produces: a released `ndopify-dto` that the server, Partner Central and the e-signature portal resolve, per *Versioning and Release* in `modules/ndopify-dto/CLAUDE.md` (patch bump for additive fields and enum values).

- [ ] **Step 1: Bump and release the DTO**

Follow `modules/ndopify-dto/CLAUDE.md` exactly; expect the `ndopify-dto-main.yml` workflow green and the package on GitHub Packages.

- [ ] **Step 2: Bump the version property and verify the reactor**

```bash
mvn install -Dheadless=true
```

`ndopify-server` needs local MySQL for its endpoint tests; the other modules need neither.

- [ ] **Step 3: Commit**

```bash
git add pom.xml modules/ndopify-dto/pom.xml
git commit -m "chore(deps): release ndopify-dto with the sale contract types" -m "Refs #257"
```

- [ ] **Step 4: State the deploy order**

In the pull request's description: release the DTO, then deploy the server, Partner Central and the e-signature portal together. #253 and #254 must not deploy before this story (the epic's dependency); this story needs neither of their fields.

---

## Self-review

- **Spec coverage:** AC 1–6 (Task 4, `search_sale.mjml`), AC 7–12 (Task 4, `listing_sale.mjml`), AC 13–15 (Tasks 1, 2, 5), AC 16 (Tasks 2, 3, 5), AC 17 (Task 2, `MandateView`), AC 18 (Tasks 6, 7), AC 19 (Tasks 3–7's rental cases: `SearchRentalTemplateTest`, `ListingRentalTemplateTest`, `SignViewsTest`'s existing case, `ContractSignatureMailetTest`), AC 20 (Tasks 3, 5: the number prefix's `else` branch, the signature tests untouched).
- **Names:** `ContractType.SALE_SEARCH` / `SALE_LISTING`, `commissionRate` (DTO, entity, form, request, view), `MandateContractRules.DEFAULT_COMMISSION` / `MIN_COMMISSION` / `MAX_COMMISSION`, `MandateService.saleCommissionRate`, `ndopify.mandate.contract.sale-commission-rate`, `/mandate/contract/search_sale.mjml` / `listing_sale.mjml`, `MandateContractGenerator.SALE_TEMPLATES`.
- **Review Focus:** each line has its test in the owning task (rental unchanged: Tasks 3, 4, 6, 7; `UNKNOWN` legacy: Task 4; sale contract needs a rate: Task 3; no amount in the document: Task 4; posts without JS: Task 2; re-send keeps the rate: Task 5; signed PDF: Task 4 renders the content the renderer already covers).
