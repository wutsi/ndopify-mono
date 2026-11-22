# Buyer Creates a SEARCH Mandate for a Purchase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a buyer say, in the public *Trouver un bien* wizard, that they want to **buy** rather than rent, give their total budget, purchase horizon and declared financing, and — for a plot of land — the title documents they require, so that a SEARCH · SALE mandate is created and shown to an agent with those answers.

**Architecture:** Two new answers travel from the wizard to the mandate: `FinancingType` (required for a sale, never sent for a rental) and `TitleDocumentType[]` (optional, land sale only). They are added to `ndopify-dto` (`CreateMandateRequest`, `Mandate`, `UpdateMandateRequest`), stored on `MandateEntity` (one nullable enum column, one comma-separated enum-list column, `V16_0`), mapped by `MandateMapper`, normalized by `MandateService.create`/`update`, and worded by `MandateMailLabels` for the offer e-mail and Telegram. In Partner Central, `PendingMandate` gains `listingType`, `financingType` and `titleDocuments`; `FindPropertyRules` becomes listing-type aware (sale type set, no furniture, land surface required, `MIN_SALE_PRICE`, financing required); `FindPropertyController` gains the need-step sale choice (preselected from `?type=`), the sale property step and the sale budget step; the page script ports the approved prototype's enhancements. The price per m² is computed in the browser only: it is never sent, never stored, never a criterion.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, the esbuild `partner-central` bundle, Flyway on MySQL 8, JUnit 5 + `kotlin.test`, `MockMvc`, `TestRestTemplate`, mockito-kotlin, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/253

**Design record (epic roadmap):** [`docs/superpowers/plans/2026-11-22-v2-property-sales.md`](https://github.com/wutsi/ndopify-mono/blob/main/docs/superpowers/plans/2026-11-22-v2-property-sales.md) — *Story 1*. Where this plan and the epic's open questions differ, the story's grooming decisions (2026-11-22) win: *Just looking* dropped, one purchase floor, `MovingPeriod` reused, financing not a dispatch input, land documents optional and land-only, no tenant feature flag.

**Approved prototype (the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/find-property.html`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/find-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/README.md` (change list, summary row)

Review it at `http://localhost:8899/find-property.html?type=SALE` (`python3 -m http.server 8899` in that folder).

## Global Constraints

- **The prototype is the design.** Task 7, Task 8 and Task 9 port it into Thymeleaf and the production script; they change no label, order, message or state it already settled. `src/main/html/` is never served and never edited here — it was committed with this plan.
- **French UI, English code.** Every label, legend, hint and error message is French; classes, KDoc, comments, logs, plan and commits are English. Amounts and dates go through `TenantFormatter` / `MandateLabels`, never a hard-coded pattern or "FCFA".
- **`UI: yes`, `Shared code: ndopify-dto`.** The DTO change must be released before the server and Partner Central can deploy (Task 11). Until then both modules build against the DTO `-SNAPSHOT` with `mvn install` at the root.
- **Wording scope.** This story words the wizard, its review, its confirmation, and adds the financing and land-document rows to the agent's offer e-mail, Telegram message and mandate page. The sale wording of everything else (*Locataire* / *Emménagement* / *Loyer* on the agent's cards and pages, the public site, the contracts) stays #259. Do not rename those in this story.
- **The price per m² never leaves the page.** It is not a DTO field, not a request field, not a column, not a criterion, and has no server-side rendering: without JavaScript the line is absent (AC 6).
- **No new validation on the server.** The wizard is the only validation a mandate gets, as today: the server only normalizes (financing kept for a sale and nulled for a rental, documents kept for a land sale and emptied otherwise). No new error code, no new 400.
- **`listingType`, `financingType` and `titleDocuments` are chosen once.** The mandate edit page (`MandateEditController` / `mandate/edit.html`) and `UpdateMandateRequest` carry them for completeness, but this story adds no field to the edit page: the wizard's answers are not editable afterwards.
- **No regression for rentals.** A `RENTAL` search must produce the same request as before (byte-identical fields apart from the two new ones, which stay `null` / empty), and render the same steps.
- Never touch `ndopify-web`, `ndopify-esignature` or `ndopify-web-core`. No new dependency. No `application*.yml` change.
- `mvn verify` green in each touched module (ktlint, tests, JaCoCo gate). `ndopify-server` tests need local MySQL; the other modules need neither MySQL nor a running server.
- Commits are conventional and scoped: `feat(dto): …`, `feat(server): …`, `feat(npc): …`, `chore(deps): …`, each with `Refs #253` in the body.

## Review Focus

- **A rental is unchanged.** `PendingMandate.toCreateMandateRequest` for `RENTAL` must keep `PeriodicityType.MONTHLY`, `FinancingType` null, no documents, and the same rules. Task 5's test compares the whole request, and Task 10's template test asserts the rental steps' text is untouched.
- **Financing/documents sanitized on the server.** A hand-crafted `POST /v1/mandates` with `listingType = RENTAL` and `financingType = CASH` must store null; a non-land sale with documents must store an empty list (AC 9). Task 2's endpoint tests pin both, and the reverse for a land sale.
- **Documents are optional and multi-choice.** An empty list is a valid land-sale answer and must survive as an empty list, not as null (AC 4, AC 9). The review shows *Aucun document exigé*, the e-mail and Telegram omit the line.
- **Land surface is required only for a land purchase.** A land rental and every other type keep it optional; a sale house/villa/apartment/duplex still asks bedrooms, a land purchase asks none (AC 3). Task 4 and Task 7 tests.
- **`?type=` never overwrites a saved answer.** Task 6's controller test posts a saved `RENTAL`, opens `?type=SALE`, and asserts `RENTAL` is still ticked.
- **No JavaScript (AC 12).** Posting every step's form with the rental-only questions still rendered must complete a sale: the server drops furniture/rooms and keeps financing/documents. Task 6–8's `MockMvc` tests post the extra fields; Task 9 only enhances, it never gates submission.
- **The price per m² is never persisted.** No DTO field, no column, no request key; Task 3's `grep` check and Task 9's port leaving it out of `collect`-equivalent payloads.
- **No `/ mois` on a sale budget.** `MandateLabels.price` already omits it for `NONE`; Task 10 must not reintroduce one in the review, the e-mail, the Telegram message or the mandate page.

---

### Task 1: The DTO answers

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/FinancingType.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/TitleDocumentType.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/CreateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/UpdateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Test: none — `ndopify-dto` has no tests; it is covered by the server's and the clients' tests (Task 2, Task 5).

**Interfaces:**
- Produces: `enum class FinancingType { UNKNOWN, CASH, BANK_LOAN, TRANSFER_FROM_ABROAD, UNDECIDED }` — the buyer's declaration, in `com.wutsi.ndopify.mandate.dto` (mandate-only answer, like `MandateType`; it is not a listing property).
- Produces: `enum class TitleDocumentType { UNKNOWN, LAND_TITLE, SURVEY_PLAN, CERTIFICATE_OF_OWNERSHIP }` — *Titre foncier*, *Plan de bornage*, *Certificat de propriété*.
- Produces on all three DTOs, in that order right after `listingType`:
  - `val financingType: FinancingType? = null` — KDoc: how the buyer declares they will pay; null for a rental.
  - `val titleDocuments: List<TitleDocumentType> = emptyList()` — KDoc: the land documents a buyer requires; optional, land purchases only, empty otherwise.
- Consumed by: Task 2 (`MandateEntity`, converters, mapper, service), Task 5 (`PendingMandate`), Task 10 (`MandateLabels`).

- [ ] **Step 1: Add the two enums**

Create both files with the values above and a one-line KDoc each: `FinancingType` says it is a declaration, not a proof; `TitleDocumentType` names the three documents in the buyer's words (*Titre foncier*, *Plan de bornage*, *Certificat de propriété*).

- [ ] **Step 2: Add the two fields to the three DTOs**

`CreateMandateRequest`, `UpdateMandateRequest` and `Mandate` gain the fields after `listingType`. Keep `Mandate`'s and `UpdateMandateRequest`'s member order identical so the mapper diff stays readable. Do not touch `Money`, `PeriodicityType` or `ListingType`.

- [ ] **Step 3: Build the DTO and the reactor against the snapshot**

```bash
cd modules/ndopify-dto && mvn install -Dheadless=true
```

Expect a green build; the other modules now resolve the new fields. Do not release yet (Task 11).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto
git commit -m "feat(dto): add the purchase financing and title documents to mandates" -m "Refs #253"
```

---

### Task 2: The mandate stores and normalizes them

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V16_0__mandate-financing-and-title-documents.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumListConverters.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapperTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateEndpointTest.kt` (assert them on the read side)

**Interfaces:**
- Consumes: `FinancingType`, `TitleDocumentType` (Task 1).
- Produces on `MandateEntity`, after `listingType`:
  - `val financingType: FinancingType? = null` — nullable `@Converter(autoApply = true)` enum, like `FurnitureType`.
  - `val titleDocuments: List<TitleDocumentType> = emptyList()` with `@Convert(converter = TitleDocumentTypeListConverter::class)` — stored comma-separated.
- Produces: `class FinancingTypeConverter : EnumConverter<FinancingType>(FinancingType::class.java, null)` in `EnumConverters` (opt-in: `autoApply = true`).
- Produces: `class TitleDocumentTypeListConverter : EnumListConverter<TitleDocumentType>(TitleDocumentType::class.java)` in `EnumListConverters`, deliberately **not** `autoApply` (set with `@Convert`), like `AuthTypeListConverter`.
- Produces: `MandateMapper.toMandate` / `toMandateSummary` copy both fields through (the summary only needs `finance` if a card shows it — it does not; keep `toMandateSummary` unchanged).
- Produces: in `MandateService.create`, after `val criteria = origin?.criteria ?: request`:
  ```kotlin
  val sale = criteria.listingType == ListingType.SALE
  // ...
  financingType = criteria.financingType?.takeIf { sale },
  titleDocuments = criteria.titleDocuments
      .filter { sale && criteria.propertyType == PropertyType.LAND && it != TitleDocumentType.UNKNOWN }
      .distinct(),
  ```
  `MandateService.update` does the same with `request`. KDoc on both: the answers are the buyer's brief — a rental has no financing and only a land purchase has documents; the price per m² is never stored.

- [ ] **Step 1: Write the failing tests**

`MandateMapperTest`, one case each way:

```kotlin
@Test
fun mapsThePurchaseAnswers() {
    val entity = MandateEntity(
        id = 1L,
        listingType = ListingType.SALE,
        financingType = FinancingType.BANK_LOAN,
        titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
    )

    val mandate = Mapper.toMandate(entity)

    assertEquals(FinancingType.BANK_LOAN, mandate.financingType)
    assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), mandate.titleDocuments)
}

@Test
fun mapsARentalWithNoPurchaseAnswers() {
    val mandate = Mapper.toMandate(MandateEntity(id = 1L, listingType = ListingType.RENTAL))

    assertNull(mandate.financingType)
    assertEquals(emptyList(), mandate.titleDocuments)
}
```

`CreateMandateEndpointTest` (endpoint tests run against MySQL and read the mandate back through `GET /v1/mandates/{id}`):

```kotlin
@Test
fun `create a sale search with its financing and land documents`() { /* POST listingType=SALE, propertyType=LAND,
    financingType=CASH, titleDocuments=[LAND_TITLE] → 200, then GET: financingType CASH, titleDocuments [LAND_TITLE] */ }

@Test
fun `drop the financing of a rental`() { /* POST listingType=RENTAL, financingType=CASH → GET: financingType null */ }

@Test
fun `drop the documents of a non-land sale`() { /* POST listingType=SALE, propertyType=VILLA, titleDocuments=[LAND_TITLE]
    → GET: titleDocuments empty */ }

@Test
fun `keep a land sale with no document`() { /* POST listingType=SALE, propertyType=LAND, titleDocuments=[] → GET: empty */ }
```

Mirror the three sale cases in `UpdateMandateEndpointTest` (`PUT /v1/mandates/{id}`).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateMapperTest+CreateMandateEndpointTest+UpdateMandateEndpointTest'
```

Fails to compile: `financingType`/`titleDocuments` unknown on the entity.

- [ ] **Step 3: Write the migration**

```sql
ALTER TABLE T_MANDATE
    ADD COLUMN financing_type VARCHAR(50) NULL,
    ADD COLUMN title_documents TEXT NULL;
```

No index, no default: both are display-only answers in M1.

- [ ] **Step 4: Entity, converters, mapper, service**

As in *Interfaces*. In `create`, `titleDocuments` is computed once into a local (`val titleDocuments = …`) so the `MandateEntity(...)` call stays readable; use it for the entity.

- [ ] **Step 5: Run the tests**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateMapperTest+CreateMandateEndpointTest+UpdateMandateEndpointTest+GetMandateEndpointTest'
```

Then the module's full gate before committing:

```bash
cd modules/ndopify-server && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): store the purchase financing and title documents on the mandate" -m "Refs #253"
```

---

### Task 3: The offer e-mail and Telegram say them

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/mandate/offer.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt`

**Interfaces:**
- Produces in `MandateMailLabels`:
  - `private val FINANCING_TYPES` — `CASH → "Comptant"`, `BANK_LOAN → "Prêt bancaire"`, `TRANSFER_FROM_ABROAD → "Transfert depuis l'étranger"`, `UNDECIDED → "Pas encore décidé"` (the prototype's `FINANCING_LABELS`).
  - `private val TITLE_DOCUMENTS` — `LAND_TITLE → "Titre foncier"`, `SURVEY_PLAN → "Plan de bornage"`, `CERTIFICATE_OF_OWNERSHIP → "Certificat de propriété"` (the prototype's `TITLE_DOCUMENT_LABELS`).
  - `fun financingType(mandate: MandateEntity): String?` — the label, or null for a rental and for null/`UNKNOWN`.
  - `fun titleDocuments(mandate: MandateEntity): String?` — `"Titre foncier · Plan de bornage"` in the stored order, or null when empty. "·", never a comma: a document name may contain one.
- Produces in `MandateOfferMailet.send`'s search context (the `else` branch, next to `"budget"`): `"financing_type" to MandateMailLabels.financingType(mandate)`, `"title_documents" to MandateMailLabels.titleDocuments(mandate)`. The listing branch gets neither.
- Produces in `offer.mjml`, in the `<ul>`, after the *Budget* `<li>`:
  ```html
  {{#financing_type}}
  <li><b>Financement</b>: {{financing_type}}</li>
  {{/financing_type}}
  {{#title_documents}}
  <li><b>Documents exigés</b>: {{title_documents}}</li>
  {{/title_documents}}
  ```
- Produces in `MandateOfferBotlet.text`: for a search with a financing, append `" · Financement : Comptant"`; with documents, `" · Documents : Titre foncier"` — on the criteria line, after the budget. Extract a `private fun purchase(mandate: MandateEntity): String` returning `""` or the two `" · …"` fragments so the existing line stays one expression.

- [ ] **Step 1: Write the failing tests**

`MandateOfferMailetTest` — extend the existing search-offer case (it already asserts the rendered body) with:

```kotlin
@Test
fun `offer the financing and the land documents of a sale search`() {
    // mandate: listingType SALE, propertyType LAND, financingType CASH, titleDocuments [LAND_TITLE, SURVEY_PLAN]
    // assert the body contains "Financement</b>: Comptant" and "Documents exigés</b>: Titre foncier · Plan de bornage"
}

@Test
fun `say no purchase answer for a rental search`() {
    // mandate: listingType RENTAL → body contains neither "Financement" nor "Documents exigés"
}
```

`MandateOfferBotletTest` likewise on the captured message text (`"Financement : Comptant"`, `"Documents : …"`, both absent for a rental).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest+MandateOfferBotletTest'
```

- [ ] **Step 3: Implement labels, mailet, template, botlet**

As in *Interfaces*. Keep the existing `listingType`/`priceLabel` wording untouched: *Achat*, *Budget*, *Prix* already fit.

- [ ] **Step 4: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest+MandateOfferBotletTest' && mvn verify -Dheadless=true
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): show the purchase financing and title documents in the offer" -m "Refs #253"
```

---

### Task 4: The wizard's rules become listing-type aware

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`

**Interfaces:**
- Produces: `const val MIN_SALE_PRICE = 1_000_000L` — KDoc: the smallest purchase worth a search, one amount for every property type.
- Produces: `fun parseListingType(value: String?): ListingType?` — `SALE`/`RENTAL` by name, null otherwise (never `UNKNOWN`); the counterpart of `parsePropertyType`.
- Produces (replacing today's rental-only signatures — every call site in Task 5–8 follows):
  - `fun listingType(value: ListingType?): String?` → `"Indiquez si vous souhaitez louer ou acheter."`
  - `fun isSale(value: ListingType?): Boolean = value == ListingType.SALE`
  - `fun propertyTypes(category: PropertyCategory, listingType: ListingType?): List<PropertyType>` — today's list for a rental; for a sale, `ROOM` is dropped, `LAND` is offered under `RESIDENTIAL` **and** `COMMERCIAL`, the rest unchanged, in prototype order.
  - `fun typeFits(category: PropertyCategory?, type: String?, listingType: ListingType?): Boolean`
  - `fun needsRooms(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — a sale asks bedrooms only for `HOUSE`, `VILLA`, `APARTMENT`, `DUPLEX` (AC 3), never for a land purchase.
  - `fun needsFurniture(type: PropertyType?, listingType: ListingType?): Boolean` — `false` for any sale, today's set otherwise.
  - `fun asksParkings(type: PropertyType?): Boolean` — unchanged.
  - `fun area(value: Int?, required: Boolean): String?` — `"Indiquez la surface du terrain."` when required and absent; `"Indiquez une surface d'au moins 5 m²."` when a value is unusable; otherwise today's optional message.
  - `fun needsArea(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — `listingType == SALE && type == LAND` (this is what `required` receives).
  - `fun titleDocumentTypes(listingType: ListingType?, type: PropertyType?): List<TitleDocumentType>` — the three documents in prototype order for a land sale, `emptyList()` otherwise.
  - `fun financingType(value: FinancingType?, listingType: ListingType?): String?` — `"Indiquez comment vous financerez votre achat."` for a sale without a usable answer; null for a rental.
  - `fun priceAmount(value: Long?, formatter: TenantFormatter, listingType: ListingType?): String?` — sale: `"Indiquez votre budget total."` without a value, `"Indiquez un budget d'au moins ${formatter.money(MIN_SALE_PRICE)}."` below the floor; rental: today's two messages, unchanged.
  - `fun isPriceAmount(value: Long?, listingType: ListingType?): Boolean` — `>= MIN_SALE_PRICE` for a sale, `>= MIN_RENT` otherwise.
  - `fun movingPeriod(value: MovingPeriod?, listingType: ListingType?): String?` — sale: `"Indiquez quand vous souhaitez acheter."`; rental: today's message.

- [ ] **Step 1: Write the failing tests**

Extend `FindPropertyRulesTest` (kotlin.test assertions):

```kotlin
@Test
fun `a sale offers land to both uses and no room`() {
    assertEquals(
        listOf(PropertyType.STUDIO, PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.VILLA,
            PropertyType.DUPLEX, PropertyType.LAND),
        FindPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.SALE),
    )
    assertFalse(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "ROOM", ListingType.SALE))
    assertTrue(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.SALE))
}

@Test
fun `a sale asks no furniture and a land purchase asks no rooms`() {
    assertFalse(FindPropertyRules.needsFurniture(PropertyType.APARTMENT, ListingType.SALE))
    assertTrue(FindPropertyRules.needsFurniture(PropertyType.APARTMENT, ListingType.RENTAL))
    assertFalse(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
    assertTrue(FindPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.HOUSE, ListingType.SALE))
}

@Test
fun `a sale has its own price floor`() {
    assertNull(FindPropertyRules.priceAmount(1_000_000L, formatter, ListingType.SALE))
    assertNotNull(FindPropertyRules.priceAmount(900_000L, formatter, ListingType.SALE))
    assertNull(FindPropertyRules.priceAmount(5_000L, formatter, ListingType.RENTAL))
}

@Test
fun `the financing is required for a sale only`() {
    assertNotNull(FindPropertyRules.financingType(null, ListingType.SALE))
    assertNull(FindPropertyRules.financingType(FinancingType.CASH, ListingType.SALE))
    assertNull(FindPropertyRules.financingType(null, ListingType.RENTAL))
}
```

Plus one case per new function (`titleDocumentTypes` land vs villa vs rental, `needsArea`, the sale `movingPeriod` message, `parseListingType` on `"SALE"`, `"sale"`, `null`, `"ANY"`).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest
```

- [ ] **Step 3: Implement the rules**

Move today's `CATEGORIES_BY_TYPE` to a private rental map and add a private sale map (or a `when` in `propertyTypes`), so `typeFits` and the property step share one source. Keep `MAX_NEIGHBORHOODS`, `MAX_COMMENTS`, `ANY`, `ROOMS`, `PARKINGS`, `parseAmount`, `comments`, `cityId`, `neighborhoodIds`, `parkings`, `preferredContactPeriod` unchanged.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest
```

The module's other tests do not compile yet — Tasks 5–10 update their call sites; the gate closes at Task 10.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt
git commit -m "feat(npc): add the sale rules to the find-property wizard" -m "Refs #253"
```

---

### Task 5: The session's answers carry the purchase

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt`

**Interfaces:**
- Produces on `PendingMandate`, in this order: `val listingType: ListingType? = null` (after `propertyCategory`), `val financingType: FinancingType? = null` (after `movingPeriod`), `val titleDocuments: List<TitleDocumentType> = emptyList()` (after `area`, next to the property answers).
- Produces: `fun isSale(): Boolean = FindPropertyRules.isSale(listingType)` — the branches below read it instead of repeating the comparison.
- Produces in `isComplete`:
  - `STEP_NEED` → `FindPropertyRules.listingType(listingType) == null && FindPropertyRules.propertyCategory(propertyCategory) == null` (both questions of the step).
  - `STEP_PROPERTY` → `propertyType(propertyCategory, propertyType, listingType)`, `needsRooms(…, listingType)`, `needsFurniture(type, listingType)`, `area(area, FindPropertyRules.needsArea(propertyCategory, type(), listingType))`.
  - `STEP_BUDGET` → `isPriceAmount(priceAmount, listingType) && movingPeriod(movingPeriod, listingType) == null && financingType(financingType, listingType) == null`.
- Produces in `toCreateMandateRequest(currency: String)`:
  - `listingType = listingType ?: ListingType.RENTAL` (never `UNKNOWN`: the old hard-coded rental is the default, not a rule),
  - `price = Money(amount = priceAmount ?: 0, currency = currency, periodicity = if (isSale()) PeriodicityType.NONE else PeriodicityType.MONTHLY)`,
  - `financingType = financingType.takeIf { isSale() }` — a rental never sends one,
  - `titleDocuments = titleDocuments.filter { isSale() && type == PropertyType.LAND }.distinct()` — non-land sales send an empty list, never null,
  - `rooms = rooms?.takeIf { FindPropertyRules.needsRooms(propertyCategory, type, listingType) }` and `furnitureType = …takeIf { FindPropertyRules.needsFurniture(type, listingType) }` with the new signature,
  - every rental term (`securityDeposit`, `advanceRent`, `leaseTerm`, `noticePeriod`) stays absent for a sale, as today (the request's defaults).
  KDoc on the class: the two purchase answers are the buyer's brief; the implied price per m² is not an answer and is never sent.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a rental is the same request as before`() {
    val pending = PendingMandate(
        propertyCategory = PropertyCategory.RESIDENTIAL,
        listingType = ListingType.RENTAL,
        cityId = 1L,
        propertyType = "APARTMENT",
        rooms = "3",
        furnitureType = "UNFURNISHED",
        area = 80,
        priceAmount = 150_000L,
        movingPeriod = MovingPeriod.WITHIN_1_MONTH,
        firstName = "Sandrine", lastName = "Eboa", email = "sandrine@example.com",
        whatsappNumber = "+237600000000",
        preferredContactPeriod = ContactPeriodType.MORNING,
    )

    val request = pending.toCreateMandateRequest("XAF")

    assertEquals(ListingType.RENTAL, request.listingType)
    assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
    assertNull(request.financingType)
    assertEquals(emptyList(), request.titleDocuments)
}

@Test
fun `a land purchase sends its financing and documents at a one-off price`() {
    val pending = /* SALE, RESIDENTIAL, LAND, area 400, price 10_000_000, CASH, [LAND_TITLE, SURVEY_PLAN],
                      rooms "3" and furnitureType "UNFURNISHED" left over from an earlier answer */

    val request = pending.toCreateMandateRequest("XAF")

    assertEquals(ListingType.SALE, request.listingType)
    assertEquals(PeriodicityType.NONE, request.price.periodicity)
    assertEquals(FinancingType.CASH, request.financingType)
    assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), request.titleDocuments)
    assertNull(request.rooms)
    assertNull(request.furnitureType)
}

@Test
fun `a villa purchase sends no document`() { /* SALE, VILLA, documents ticked by hand → empty list */ }

@Test
fun `a land purchase without a document is complete`() { /* isComplete(STEP_PROPERTY) true without documents,
    false without an area */ }
```

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=PendingMandateTest
```

- [ ] **Step 3: Implement `PendingMandate`**

As in *Interfaces*; keep `AMENITIES`, `has`, `firstIncompleteStep` untouched.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=PendingMandateTest
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt
git commit -m "feat(npc): carry the purchase answers in the find-property session" -m "Refs #253"
```

---

### Task 6: The need step asks *Louer / Acheter*, within `?type=`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/NeedForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (only the two project labels of the prototype's step 1)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html` (need step only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `data class NeedForm(val listingType: ListingType? = null, val propertyCategory: PropertyCategory? = null)`.
- Produces: `FindPropertyController.show` gains `@RequestParam(name = "type", required = false) type: String?`; before `firstIncompleteStep()`, when the session has no `listingType`, the request's `?type=SALE|RENTAL` preselects one:
  ```kotlin
  val pending = pending(session).let { saved ->
      if (saved.listingType != null) saved else saved.copy(listingType = FindPropertyRules.parseListingType(type))
  }
  ```
  The copy is for display only: it is not written to the session until the step posts (AC 2).
- Produces: `FindPropertyController.need` stores `listingType = form.listingType` and validates it first:
  `errors("listingType" to FindPropertyRules.listingType(answers.listingType), "propertyCategory" to …)`.
- Produces: `step()`'s `STEP_NEED` branch adds `model.addAttribute("listingTypeLabels", MandateLabels.LISTING_TYPE_LABELS)`.
- Produces in `MandateLabels`: `val LISTING_TYPE_LABELS: Map<ListingType, String> = linkedMapOf(ListingType.RENTAL to "Louer", ListingType.SALE to "Acheter")` — KDoc: the step's verb, not the mandate's noun.
- Produces in `find-property.html`, need step: a `data-group="listingType"` radio fieldset above *Pour quel usage ?*, `name="listingType"`, `required`, `th:checked="${#strings.equals(answers.listingType, 'RENTAL')}"` / `'SALE'`, with the prototype's descriptions (*Louer un bien pour y vivre ou pour une activité* / *Acheter un bien, terrain compris*), a `~{:: error('listingType')}` paragraph, and the h3/p reworded as the prototype does (it no longer says only *louer*).

- [ ] **Step 1: Write the failing tests**

`FindPropertyControllerTest` (standalone `MockMvc`, `MockHttpSession`):

```kotlin
@Test
fun `the need step stores the project`() {
    // POST /find-property/need listingType=SALE propertyCategory=RESIDENTIAL → 3xx, session PendingMandate.listingType SALE
}

@Test
fun `the need step requires the project`() {
    // POST listingType absent → view "find-property", model errors["listingType"] present, session untouched
}

@Test
fun `the type parameter preselects the project`() {
    // GET /find-property?type=SALE → model answers.listingType SALE
}

@Test
fun `the type parameter never overwrites a saved project`() {
    // session PendingMandate(listingType = RENTAL); GET /find-property?type=SALE → model answers.listingType RENTAL
}

@Test
fun `an unknown type parameter is ignored`() {
    // GET /find-property?type=ANY → model answers.listingType null
}
```

`FindPropertyTemplateTest` (real Thymeleaf, jsoup): the need step renders both radios, `SALE` checked for a sale answer, and the *Acheter* label text.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 3: Implement form, controller, label and template**

As in *Interfaces*. `?type=` is read on the `show` mapping only — never on a POST, never stored.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): ask the buyer to rent or buy in the need step" -m "Refs #253"
```

---

### Task 7: The property step offers land and asks the documents

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PropertyForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (the three document labels)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html` (property step only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `PropertyForm` gains `val titleDocuments: List<TitleDocumentType> = emptyList()`.
- Produces: `FindPropertyController.property` keeps the documents only for a land sale and drops the rest:
  ```kotlin
  val documents = form.titleDocuments
      .filter { it in FindPropertyRules.titleDocumentTypes(pending.listingType, type) }
      .distinct()
  ```
  and passes `required = FindPropertyRules.needsArea(pending.propertyCategory, type, pending.listingType)` to `FindPropertyRules.area`, whose "something typed that isn't a number" branch stays. `rooms`, `furnitureType` and `parkings` take the new listing-type-aware rules.
- Produces: `step()`'s `STEP_PROPERTY` branch uses `FindPropertyRules.propertyTypes(answers.propertyCategory!!, answers.listingType)` and adds `model.addAttribute("titleDocumentLabels", MandateLabels.byName(MandateLabels.TITLE_DOCUMENT_LABELS))` plus `model.addAttribute("asksTitleDocuments", FindPropertyRules.titleDocumentTypes(answers.listingType, FindPropertyRules.parsePropertyType(answers.propertyType)).isNotEmpty())`.
- Produces in `MandateLabels`: `val TITLE_DOCUMENT_LABELS: Map<TitleDocumentType, String> = linkedMapOf(LAND_TITLE to "Titre foncier", SURVEY_PLAN to "Plan de bornage", CERTIFICATE_OF_OWNERSHIP to "Certificat de propriété")`.
- Produces in `find-property.html`, property step: a `<fieldset data-group="titleDocuments" th:hidden="${!asksTitleDocuments}">` with legend *Quels documents exigez-vous ?*, the prototype's hint (*Facultatif. Ces documents guident votre agent ; ils ne filtrent pas les biens proposés.*), one `input type="checkbox" name="titleDocuments"` per label, no `required`; and the area label/hint switched to *Surface du terrain* / required wording when a land sale is being answered (`th:with` on the fieldset, JS only re-words live).

- [ ] **Step 1: Write the failing tests**

`FindPropertyControllerTest`:

```kotlin
@Test
fun `a land purchase requires its surface`() {
    // session SALE/RESIDENTIAL, POST property type=LAND without area → view with errors["area"], session untouched
}

@Test
fun `a land purchase keeps any number of documents`() {
    // POST type=LAND titleDocuments=[LAND_TITLE, SURVEY_PLAN] → session titleDocuments both
}

@Test
fun `a villa purchase drops the documents`() {
    // POST type=VILLA titleDocuments=[LAND_TITLE] → session titleDocuments empty
}

@Test
fun `a land rental drops the documents`() {
    // session RENTAL/RESIDENTIAL, POST type=LAND titleDocuments=[LAND_TITLE] → session empty, area optional
}
```

`FindPropertyTemplateTest`: a sale residential step offers *Terrain*, no *Chambre*, no furniture legend; a land sale shows the three checkboxes; a rental shows none; a villa sale hides them.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 3: Implement form, controller, label and template**

As in *Interfaces*. The checkboxes render even when hidden, so a no-JS post still carries them; the server filters.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): offer land and ask the title documents on a purchase" -m "Refs #253"
```

---

### Task 8: The budget step asks the financing at a one-off price

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/BudgetForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (the four financing labels)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html` (budget step only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `BudgetForm` gains `val financingType: FinancingType? = null`.
- Produces: `FindPropertyController.budget` stores `financingType = form.financingType` and validates with `FindPropertyRules.financingType(answers.financingType, answers.listingType)`, plus the sale-aware `priceAmount`/`movingPeriod`.
- Produces: `step()`'s `STEP_BUDGET` branch adds `model.addAttribute("financingLabels", MandateLabels.byName(MandateLabels.FINANCING_LABELS))`.
- Produces in `MandateLabels`: `val FINANCING_LABELS: Map<FinancingType, String> = linkedMapOf(CASH to "Comptant", BANK_LOAN to "Prêt bancaire", TRANSFER_FROM_ABROAD to "Transfert depuis l'étranger", UNDECIDED to "Pas encore décidé")`.
- Produces in `find-property.html`, budget step (the prototype's sale state):
  - `th:with="commercial=…, sale=${#strings.equals(answers.listingType, 'SALE')}"`;
  - legend `<span data-price-label>` *Budget total* for a sale (*Budget mensuel* otherwise), suffix `th:text="|${currency}${sale ? '' : ' / mois'}|"`;
  - intro `data-budget-intro`: *Le montant total que vous êtes prêt à mettre pour votre achat.*;
  - horizon legend `data-move-in-label`: `sale ? 'Quand souhaitez-vous acheter ?' : …`;
  - a `data-group="financingType"` radio fieldset, `th:hidden="${!sale}"`, the four labels, `required` (`th:required="${sale}"`), and `~{:: error('financingType')}`;
  - a `<p class="hint" data-price-per-sqm hidden></p>` after the price field — empty server-side, filled by Task 9;
  - the price hint `data-price-hint` reworded for a sale (the prototype's *Le prix affiché est le budget total…*).

- [ ] **Step 1: Write the failing tests**

`FindPropertyControllerTest`:

```kotlin
@Test
fun `a purchase requires its financing`() {
    // session SALE, POST budget priceAmount=10000000 movingPeriod=WITHIN_3_MONTHS without financingType
    // → view with errors["financingType"], session untouched
}

@Test
fun `a purchase at the floor is accepted`() {
    // priceAmount=1000000 financingType=CASH → 3xx, session has both
}

@Test
fun `a rental refuses a rent below the floor and needs no financing`() {
    // RENTAL, priceAmount=4000 → errors["priceAmount"]; priceAmount=150000 without financingType → 3xx, session financingType null
}
```

`FindPropertyTemplateTest`: a sale budget step reads *Budget total* with no `/ mois`, *Quand souhaitez-vous acheter ?*, four financing radios; a rental keeps *Budget mensuel* + `FCFA / mois`, the moving-in legend, and no financing fieldset; `data-price-per-sqm` exists and is empty.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 3: Implement form, controller, label and template**

As in *Interfaces*. No `rate`/`perSqm` value reaches the model: AC 6 keeps it client-side only.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): ask the purchase budget, horizon and financing" -m "Refs #253"
```

---

### Task 9: The page script ports the prototype's enhancements

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/find-property.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt` (the hooks the script needs: the `data-*` attributes it reads must be rendered)

**Interfaces:**
- Produces in the script, ported from `src/main/html/v2/assets/js/find-property.js` and adapted to the server-rendered, one-step-per-page flow:
  - `listingType` read from `form[data-find-form][data-listing-type]` (the controller renders the attribute; the value is the session answer, `RENTAL` by default) — never from the DOM's checked radio of another step;
  - `isSale`, `isLandSale`, `needsLandArea`, `asksTitleDocuments` as in the prototype;
  - `checkedValues(name)` / `setCheckedAll(name, values)` for the document checkboxes;
  - on the property step: hide the `rooms` / `furnitureType` groups that a sale doesn't ask, show `titleDocuments` for a land sale, and make the area required for a land purchase (the same messages as `FindPropertyRules`);
  - on the budget step: hide the `financingType` group for a rental (and clear its message), switch the price label/suffix/intro/horizon legend/two hints between the rental and sale wordings, and update `[data-price-per-sqm]` on `input` — `"Soit ≈ 25 000 FCFA/m² pour 400 m²"` for a land sale with a valid surface and a budget, hidden otherwise, rounded to the nearest 100 FCFA by `Math.round(budget / area / 100) * 100`;
  - nothing posted or stored for the rate: it never enters a hidden field.
- Consumes: the template hooks of Tasks 6–8. Add a plain `data-listing-type` attribute on every `data-find-form` in `find-property.html`.

- [ ] **Step 1: Write the failing test**

`FindPropertyTemplateTest` — assert the hooks exist, so the script cannot silently no-op:

```kotlin
@Test
fun `the sale budget step has the price per square metre hook`() {
    // budget step, SALE: document.selectFirst("[data-price-per-sqm]") not null and empty
}

@Test
fun `the forms carry the listing type for the script`() {
    // need step: form[data-find-form].attr("data-listing-type") == "RENTAL" by default, "SALE" for a sale session
}
```

- [ ] **Step 2: Run it, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyTemplateTest
```

- [ ] **Step 3: Port the script**

Copy the prototype's functions rather than re-deriving them, keeping its comments' intent (the rate is display-only). The production file is a plain script for the bundle: no framework, no new dependency, ES5-friendly style like its neighbours (`let`/`const` are fine; the rest of `src/main/assets/js` shows the house style).

- [ ] **Step 4: Run the tests and build the assets**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyTemplateTest && mvn generate-resources -Dheadless=true
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): enhance the purchase steps of the find-property script" -m "Refs #253"
```

---

### Task 10: The review, the confirmation and the merchant screens word a purchase

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/SubmittedMandate.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html` (review and confirmation)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`

**Interfaces:**
- Produces: `SubmittedMandate` gains `val listingType: ListingType = ListingType.RENTAL`; `submit` fills it from `pending.listingType`.
- Produces in `MandateLabels`:
  - `fun project(category: PropertyCategory?, listingType: ListingType?): String?` — *Achat, usage résidentiel* / *Achat, usage commercial* for a sale, today's *Location, usage …* otherwise (replaces the one-argument overload; call sites in the wizard only — the agent's cards keep the rental wording until #259).
  - `fun moveInTerm(listingType: ListingType?, category: PropertyCategory?): String` — *Achat prévu* for a sale, today's *Date d'emménagement* / *Date d'installation* otherwise.
  - `fun titleDocuments(documents: List<TitleDocumentType>): String` — `"Titre foncier · Plan de bornage"`, or *Aucun document exigé* when empty.
  - In `criteria(mandate, locations, formatter)`: skip the *Meublé* row when `mandate.listingType == ListingType.SALE` (AC 11: never a furniture row), and append after the surface `("Financement" to FINANCING_LABELS[mandate.financingType])` and `("Documents exigés" to titleDocuments(mandate.titleDocuments))` when they apply. Leave *Locataire*, *Loyer*, *Emménagement* alone — #259 owns them.
- Produces in `FindPropertyController.step()`'s review branch: `categoryLabel = MandateLabels.project(answers.propertyCategory, answers.listingType)`, `priceLabel = answers.priceAmount?.let { formatter().money(it) + if (answers.isSale()) "" else " par mois" }`, `moveInTerm = …`, `financingLabel`, `titleDocumentsLabel`.
- Produces: `show()`'s confirmation branch adds `model.addAttribute("listingType", submitted.listingType)`; the confirmation's three next steps get a sale variant (`th:if`/`th:unless` on `listingType == 'SALE'`): a verified agent **of the area** contacts them, the visit, then *Vous êtes accompagné jusqu'à la signature de l'acte de vente*.
- Produces in `find-property.html`, review step: the project row and the price row use the new model attributes; `titleDocuments` and `financing` rows, each `th:if` on a sale (documents also land-only).

- [ ] **Step 1: Write the failing tests**

`MandateLabelsTest`:

```kotlin
@Test
fun `a sale criteria has no furniture row and shows the purchase answers`() {
    val rows = MandateLabels.criteria(saleLandMandate, locations, formatter)
    assertTrue(rows.none { it.first == "Meublé" })
    assertEquals("Comptant", rows.first { it.first == "Financement" }.second)
    assertEquals("Titre foncier · Plan de bornage", rows.first { it.first == "Documents exigés" }.second)
    assertEquals("10 000 000 FCFA", rows.first { it.first == "Budget" }.second) // no " / mois"
}

@Test
fun `a rental criteria is unchanged`() { /* same rows as before: Meublé, Budget monthly, no Financement/Documents */ }
```

`FindPropertyControllerTest` — review model: `categoryLabel` = *Achat, usage résidentiel*, `priceLabel` without *par mois*, `moveInTerm` = *Achat prévu*, `financingLabel` = *Comptant*, `titleDocumentsLabel` = *Aucun document exigé* for a land sale with none; confirmation model carries `listingType`.

`FindPropertyTemplateTest` — the sale review reads *Achat*, *Budget total*, *Achat prévu*, *Financement*, *Documents exigés*; the rental review is byte-identical to today's labels (assert *Location*, *Budget mensuel*, *Date d'emménagement*, absence of the two new rows); the sale confirmation mentions a sales agent.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateLabelsTest+FindPropertyControllerTest+FindPropertyTemplateTest'
```

- [ ] **Step 3: Implement the labels, controller and templates**

As in *Interfaces*. The sale wording stops at the wizard and at the criteria rows AC 11 names: do not rename the mandate page's title, the cards' facts or the contract pages (#259).

- [ ] **Step 4: Run the module's gate**

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

Every earlier call site now compiles; this is the first green gate of the module.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): word the review, confirmation and mandate criteria for a purchase" -m "Refs #253"
```

---

### Task 11: Release the DTO and pin the version

**Files:**
- Modify: `pom.xml` (`<ndopify-dto.version>`)

**Interfaces:**
- Consumes: Tasks 1–10 merged on `main`.
- Produces: a released `ndopify-dto` version (tag `ndopify-dto-<version>`), and the root POM's `<ndopify-dto.version>` set to it.

- [ ] **Step 1: Merge and let the release workflow run**

The `ndopify-dto` change reaches `main` with Task 1's commit; `.github/workflows/ndopify-dto-main.yml` runs `release:prepare release:perform`, tagging `ndopify-dto-<version>` and leaving the next `-SNAPSHOT`. Confirm the tag appears:

```bash
gh release list --repo wutsi/ndopify-mono | head -5
git fetch --tags && git tag --list 'ndopify-dto-*' | sort -V | tail -3
```

- [ ] **Step 2: Pin the released version**

Set `<ndopify-dto.version>` in the root `pom.xml` to the released version (never a `-SNAPSHOT`: the modules' CI cannot resolve one).

- [ ] **Step 3: Verify the whole reactor**

```bash
mvn verify -Dheadless=true
```

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "chore(deps): use ndopify-dto <version>" -m "Refs #253"
```

---

## Manual verification

1. `mvn spring-boot:run` in `modules/ndopify-server` (MySQL migrated to `V16_0`), `modules/ndopify-partner-central` (port 8081, its tenant seeded), and Mailpit on `localhost:1025`.
2. Open `http://localhost:8081/find-property?type=SALE`: *Acheter* is preselected. Reach the property step: *Terrain* under *Résidentiel*, no *Chambre*, no furniture. Pick *Terrain*: the three documents and a required surface. Then the budget step: *Budget total* without `/ mois`, the four financing answers, and the price per m² line appearing and updating as the budget is typed.
3. Complete the review: *Achat*, *Budget total*, *Achat prévu*, *Financement*, *Documents exigés*. Submit: the sale confirmation.
4. In `ndopify-server`, `GET /v1/mandates?mandateType=SEARCH` shows the mandate with `listingType = SALE`, `financingType`, `titleDocuments`, `price.periodicity = NONE` — and no rate anywhere.
5. Offer it to an agent: the offer card, the e-mail in Mailpit and the Telegram message all show the financing and the documents next to the budget.
6. Open `http://localhost:8081/find-property` with JavaScript disabled: complete a rental and a sale end to end; the price per m² is simply absent.
7. `/find-property?type=SALE` after answering *Louer*: *Louer* stays ticked.
8. An existing rental mandate's pages and e-mails are unchanged.
