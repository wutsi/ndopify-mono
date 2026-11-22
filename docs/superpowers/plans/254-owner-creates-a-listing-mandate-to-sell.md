# Owner Creates a LISTING Mandate to Sell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a property owner say, in the public *Proposer un bien* wizard, that they want to **sell** rather than rent, give their asking price and the ownership documents they hold, and see the implied price per m² of a plot, so that a LISTING · SALE mandate is created and shown to an agent with those answers.

**Architecture:** Two answers travel from the wizard to the mandate: `listingType` (already on the DTOs, hard-coded to `RENTAL` by the wizard) and `ownershipDocuments` (`List<TitleDocumentType>`, reusing #253's enum, a new field on `CreateMandateRequest`, `UpdateMandateRequest` and `Mandate`). They are stored on `MandateEntity` (one comma-separated enum-list column, `V16_1` after #253's `V16_0`), mapped by `MandateMapper`, normalized by `MandateService.create`/`update`, and worded by `MandateMailLabels` for the listing offer e-mail and Telegram. In Partner Central, `PendingListing` gains `listingType` and `ownershipDocuments`; `OfferPropertyRules` becomes listing-type aware (sale type set, no furniture, land surface required, `MIN_SALE_PRICE`, land documents); `OfferPropertyController` gains the project choice (preselected from `?type=`), the sale conditions step, the sale features step with the documents and the read-only rate, and the sale review/confirmation. The price per m² is computed in the browser only: never sent, never stored, never a criterion.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, the esbuild `partner-central` bundle, Flyway on MySQL 8, JUnit 5 + `kotlin.test`, `MockMvc`, `TestRestTemplate`, mockito-kotlin, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/254

**Design record (epic roadmap):** [`docs/superpowers/plans/2026-11-22-v2-property-sales.md`](https://github.com/wutsi/ndopify-mono/blob/main/docs/superpowers/plans/2026-11-22-v2-property-sales.md) — *Story 2*.

**Approved prototype (the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/offer-property.html`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/offer-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/partner-central.js` (the plot-of-land sale sample, mandate 302)
- `modules/ndopify-partner-central/src/main/html/v2/mandate.html`
- `modules/ndopify-partner-central/src/main/html/v2/README.md` (change list, sale states)

Review it at `http://localhost:8899/offer-property.html?type=SALE` and `http://localhost:8899/mandate.html?id=302` (`python3 -m http.server 8899` in that folder).

## Global Constraints

- **The prototype is the design.** Tasks 6–10 port it into Thymeleaf and the production script; they change no label, order, message or state it already settled. `src/main/html/` is never served and never edited here — it was committed with this plan.
- **French UI, English code.** Every label, legend, hint and error message is French; classes, KDoc, comments, logs, plan and commits are English. Amounts and dates go through `TenantFormatter` / `MandateLabels`, never a hard-coded pattern or "FCFA".
- **`UI: yes`, `Shared code: ndopify-dto`.** The DTO change must be released before the server and Partner Central can deploy (Task 11). Until then both modules build against the DTO `-SNAPSHOT` with `mvn install` at the root.
- **#253 is merged first.** This plan reuses `TitleDocumentType`, `FindPropertyRules.parseListingType` / `isSale`, and the two-argument `MandateLabels.project(category, listingType)`. If #253 is not merged when a task starts, stop and land #253 first; only the DTO enum and the two `FindPropertyRules` helpers may be added here as a fallback, named exactly as #253 names them.
- **Ownership documents are the seller's brief**: asked for a SALE of a *Terrain* only, optional, several, kept as an empty list when none is ticked, and **never** a match criterion in this story. The field is `ownershipDocuments`, distinct from #253's `titleDocuments` (the buyer's requirements); both use `TitleDocumentType`.
- **The price per m² never leaves the page.** Not a DTO field, not a request field, not a column, not on the review, and no server-side rendering: without JavaScript the line is absent (AC 11).
- **No new validation on the server.** The wizard is the only validation a mandate gets, as today: the server only normalizes (documents kept for a SALE · LAND and emptied otherwise, one-off price and no advance/deposit already carried by the request). No new error code, no new 400.
- **No regression for rentals.** A `RENTAL` offer must produce the same request as before (`MONTHLY` price, advance, deposit), apart from `listingType = RENTAL` and an empty document list, and render the same steps.
- **Sale wording scope.** This story words the wizard, its review, its confirmation, and adds the documents row to the offer e-mail, Telegram message and mandate page. The rest of the sale wording (*Bailleur* / *Loyer* on the agent's cards, the public site's sale page, the contracts) stays #259. Do not rename those.
- Never touch `ndopify-web`, `ndopify-esignature` or `ndopify-web-core`. No new dependency. No `application*.yml` change.
- `mvn verify` green in each touched module (ktlint, tests, JaCoCo gate). `ndopify-server` tests need local MySQL; `ndopify-partner-central` and `ndopify-dto` need neither MySQL nor a running server.
- Commits are conventional and scoped: `feat(dto): …`, `feat(server): …`, `feat(npc): …`, `chore(deps): …`, each with `Refs #254` in the body.

## Review Focus

- **A rental is unchanged.** `PendingListing.toCreateMandateRequest` for `RENTAL` must keep `PeriodicityType.MONTHLY`, its advance and deposit, no documents, and the same rules. Task 5's test compares the whole request, Task 6–9's template tests assert the rental steps' text is untouched.
- **Documents are sanitized on the server.** A hand-crafted `POST /v1/mandates` with `listingType = SALE` and a villa must store an empty list; a `RENTAL` with documents must store an empty list; a land sale with none must store an empty list, not null (AC 9). Task 2's endpoint tests pin all three.
- **No document can leak from a *Terrain* to another type or a rental.** Task 5's test changes the type after ticking and asserts an empty list in the request; Task 8's controller test posts documents with a villa and asserts the session has none.
- **Land surface is required only for a land sale.** A land rental keeps it optional, every other type too; a land sale asks no bedrooms and no parking (AC 4). Tasks 4 and 8 test both sides.
- **The price per m² is never persisted.** No DTO field, no column, no request key, no review row: Task 3's `grep` check and Task 10's port leaving it out of the payload.
- **`?type=` never overwrites a saved answer.** Task 6's controller test posts a saved `RENTAL`, opens `?type=SALE`, and asserts `RENTAL` is still ticked.
- **No `/ mois` on a sale price.** `MandateLabels.price` omits it for `NONE`; Tasks 7–10 must not reintroduce one in the terms step, the review, the e-mail, the Telegram message or the mandate page.

---

### Task 1: The DTO documents field

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/CreateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/UpdateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Test: none — `ndopify-dto` has no tests; it is covered by the server's and the clients' tests (Tasks 2, 5).

**Interfaces:**
- Consumes: `com.wutsi.ndopify.mandate.dto.TitleDocumentType` (`UNKNOWN`, `LAND_TITLE`, `SURVEY_PLAN`, `CERTIFICATE_OF_OWNERSHIP`) from #253.
- Produces on all three DTOs, in that order right after `titleDocuments`:
  - `val ownershipDocuments: List<TitleDocumentType> = emptyList()` — KDoc: the documents of ownership the seller declares; optional, a land sale only, empty otherwise.

- [ ] **Step 1: Add the field to the three DTOs**

`CreateMandateRequest`, `UpdateMandateRequest` and `Mandate` gain the field after `titleDocuments`, in that order. Keep `Mandate`'s and `UpdateMandateRequest`'s member order identical so the mapper diff stays readable. Do not touch `Money`, `PeriodicityType` or `ListingType`.

- [ ] **Step 2: Build the DTO and the reactor against the snapshot**

```bash
cd modules/ndopify-dto && mvn install -Dheadless=true
```

Expect a green build; the other modules now resolve the new field. Do not release yet (Task 11).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto
git commit -m "feat(dto): add the seller's ownership documents to mandates" -m "Refs #254"
```

---

### Task 2: The mandate stores and normalizes them

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V16_1__mandate-ownership-documents.sql` (the number after #253's `V16_0`; if #253's migration is absent, the next free number)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapperTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateEndpointTest.kt` (assert it on the read side)

**Interfaces:**
- Consumes: `TitleDocumentType`, `TitleDocumentTypeListConverter` (both #253), `ownershipDocuments` (Task 1).
- Produces on `MandateEntity`, after `titleDocuments`:
  `val ownershipDocuments: List<TitleDocumentType> = emptyList()` with `@Convert(converter = TitleDocumentTypeListConverter::class)` — comma-separated, like `titleDocuments`.
- Produces: `MandateMapper.toMandate` copies it through (`toMandateSummary` unchanged).
- Produces: in `MandateService.create`, after `val criteria = origin?.criteria ?: request`:

```kotlin
val documents = criteria.ownershipDocuments
    .filter { criteria.listingType == ListingType.SALE && criteria.propertyType == PropertyType.LAND }
    .filter { it != TitleDocumentType.UNKNOWN }
    .distinct()
```

  and `ownershipDocuments = documents` on the entity; `update` does the same with `request`. KDoc on both: the documents are the seller's brief — a rental and any non-land sale have none; they filter nothing.

- [ ] **Step 1: Write the failing tests**

`MandateMapperTest`, both ways:

```kotlin
@Test
fun mapsTheOwnershipDocuments() {
    val entity = MandateEntity(
        id = 1L,
        listingType = ListingType.SALE,
        ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
    )

    val mandate = Mapper.toMandate(entity)

    assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), mandate.ownershipDocuments)
}

@Test
fun mapsARentalWithNoOwnershipDocument() {
    val mandate = Mapper.toMandate(MandateEntity(id = 1L, listingType = ListingType.RENTAL))

    assertEquals(emptyList(), mandate.ownershipDocuments)
}
```

`CreateMandateEndpointTest` (create then read it back through `GET /v1/mandates/{id}`):

```kotlin
@Test
fun `create a land sale with its ownership documents`() { /* POST LISTING, SALE, LAND, ownershipDocuments=[LAND_TITLE, SURVEY_PLAN]
    → 200, then GET: ownershipDocuments both, price periodicity NONE */ }

@Test
fun `drop the documents of a non-land sale`() { /* POST SALE, VILLA, ownershipDocuments=[LAND_TITLE] → GET: empty */ }

@Test
fun `drop the documents of a rental`() { /* POST RENTAL, LAND, ownershipDocuments=[LAND_TITLE] → GET: empty */ }

@Test
fun `keep a land sale with no document`() { /* POST SALE, LAND, ownershipDocuments=[] → GET: empty, not null */ }
```

Mirror the four cases in `UpdateMandateEndpointTest` (`PUT /v1/mandates/{id}`).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateMapperTest+CreateMandateEndpointTest+UpdateMandateEndpointTest'
```

Fails to compile: `ownershipDocuments` unknown on the entity.

- [ ] **Step 3: Write the migration**

```sql
ALTER TABLE T_MANDATE
    ADD COLUMN ownership_documents TEXT NULL;
```

No index, no default: a display-only answer in M1.

- [ ] **Step 4: Entity, mapper, service**

As in *Interfaces*. Compute `documents` once into a local so the `MandateEntity(...)` call stays readable.

- [ ] **Step 5: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateMapperTest+CreateMandateEndpointTest+UpdateMandateEndpointTest+GetMandateEndpointTest'
cd modules/ndopify-server && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): store the seller's ownership documents on the mandate" -m "Refs #254"
```

---

### Task 3: The listing offer e-mail and Telegram say them

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/mandate/offer_listing.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt`

**Interfaces:**
- Produces in `MandateMailLabels`:
  - `private val TITLE_DOCUMENTS` — `LAND_TITLE → "Titre foncier"`, `SURVEY_PLAN → "Plan de bornage"`, `CERTIFICATE_OF_OWNERSHIP → "Certificat de propriété"` (the prototype's `OWNERSHIP_DOCUMENT_LABELS`).
  - `fun ownershipDocuments(mandate: MandateEntity): String?` — the labels in stored order joined by `" · "` (`"Titre foncier · Plan de bornage"`), or null when empty or not a `SALE` · `LAND` mandate. "·", never a comma: a document name may contain one.
- Produces in `MandateOfferMailet.send`'s listing branch (next to `"available_from"`): `"ownership_documents" to MandateMailLabels.ownershipDocuments(mandate)`.
- Produces in `offer_listing.mjml`, after the price `<li>`:

```html
{{#ownership_documents}}
<li><b>Document de propriété</b>: {{ownership_documents}}</li>
{{/ownership_documents}}
```

- Produces in `MandateOfferBotlet.details`: `MandateMailLabels.ownershipDocuments(mandate)?.let { "document de propriété : $it" }` as the last element of the line.

- [ ] **Step 1: Write the failing tests**

`MandateOfferMailetTest` — extend the listing-offer case with:

```kotlin
@Test
fun `offer the ownership documents of a land sale`() {
    // mandate: LISTING, SALE, LAND, ownershipDocuments [LAND_TITLE, SURVEY_PLAN]
    // assert the body contains "Document de propriété</b>: Titre foncier · Plan de bornage"
}

@Test
fun `say no ownership document for a villa sale`() {
    // LISTING, SALE, VILLA, documents [LAND_TITLE] → body contains neither "Document de propriété" nor "Titre foncier"
}
```

`MandateOfferBotletTest` likewise on the captured text (`"document de propriété : Titre foncier · Plan de bornage"`, absent otherwise).

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest+MandateOfferBotletTest'
```

- [ ] **Step 3: Implement labels, mailet, template, botlet**

As in *Interfaces*. Keep `listing_type` / `price_label` / `budget` untouched: *Vente* and *Prix* already fit.

- [ ] **Step 4: Run the tests, then the module**

```bash
cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest+MandateOfferBotletTest' && mvn verify -Dheadless=true
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main modules/ndopify-server/src/test
git commit -m "feat(server): show the seller's ownership documents in the offer" -m "Refs #254"
```

---

### Task 4: The offer wizard's rules become listing-type aware

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`

**Interfaces:**
- Consumes: `FindPropertyRules.parseListingType(value: String?): ListingType?` and `FindPropertyRules.isSale(value: ListingType?): Boolean` (#253).
- Produces:
  - `const val MIN_SALE_PRICE = 1_000_000L` — KDoc: the smallest sale price worth a listing, one amount for every property type.
  - `fun listingType(value: ListingType?): String?` → `"Indiquez si vous souhaitez mettre votre bien en location ou le vendre."`
  - `fun propertyTypes(category: PropertyCategory, listingType: ListingType?): List<PropertyType>` — today's order for a rental; for a sale, `ROOM` dropped, `LAND` offered under `RESIDENTIAL` **and** `COMMERCIAL`, the rest unchanged (prototype order: APARTMENT, HOUSE, ROOM, STUDIO, DUPLEX, BUILDING, OFFICE, RETAIL, INDUSTRIAL, LAND).
  - `fun typeFits(category: PropertyCategory?, type: String?, listingType: ListingType?): Boolean`
  - `fun needsFurniture(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — `false` for any sale, today's set otherwise.
  - `fun needsRooms(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — `LAND` added to the types that ask none (a land sale asks none; a plot can't have bedrooms).
  - `fun needsParking(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — `LAND` asks none.
  - `fun needsArea(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean` — `category == COMMERCIAL` or a `SALE` of a `LAND` (this is what `required` receives).
  - `fun area(value: Int?, required: Boolean): String?` — `"Indiquez la surface du terrain."` when required and absent; `"Indiquez une surface d'au moins 5 m²."` when unusable; otherwise today's optional message.
  - `fun ownershipDocumentTypes(listingType: ListingType?, type: PropertyType?): List<TitleDocumentType>` — `LAND_TITLE`, `SURVEY_PLAN`, `CERTIFICATE_OF_OWNERSHIP` for a `SALE` of a `LAND`, `emptyList()` otherwise.
  - `fun priceAmount(value: Long?, formatter: TenantFormatter, listingType: ListingType?): String?` — sale: `"Indiquez le prix de vente souhaité."` without a value, `"Indiquez un prix d'au moins ${formatter.money(MIN_SALE_PRICE)}."` below the floor; rental: today's two messages, unchanged.
  - `fun isPriceAmount(value: Long?, listingType: ListingType?): Boolean` — `>= MIN_SALE_PRICE` for a sale, `>= MIN_RENT` otherwise.

- [ ] **Step 1: Write the failing tests**

Extend `OfferPropertyRulesTest` (kotlin.test assertions):

```kotlin
@Test
fun `a sale offers land to both uses and no room`() {
    assertEquals(
        listOf(PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.STUDIO, PropertyType.DUPLEX,
            PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL,
            PropertyType.LAND),
        OfferPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.SALE),
    )
    assertFalse(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "ROOM", ListingType.SALE))
    assertTrue(OfferPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.SALE))
    assertTrue(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
}

@Test
fun `a sale asks no furniture, no rooms nor parking for land`() {
    assertFalse(OfferPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.APARTMENT, ListingType.SALE))
    assertTrue(OfferPropertyRules.needsFurniture(PropertyCategory.RESIDENTIAL, PropertyType.APARTMENT, ListingType.RENTAL))
    assertFalse(OfferPropertyRules.needsRooms(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
    assertFalse(OfferPropertyRules.needsParking(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
}

@Test
fun `only a land sale requires its surface`() {
    assertTrue(OfferPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.SALE))
    assertFalse(OfferPropertyRules.needsArea(PropertyCategory.RESIDENTIAL, PropertyType.LAND, ListingType.RENTAL))
    assertEquals("Indiquez la surface du terrain.", OfferPropertyRules.area(null, required = true))
}

@Test
fun `the documents are a land sale's only`() {
    assertEquals(
        listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN, TitleDocumentType.CERTIFICATE_OF_OWNERSHIP),
        OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.LAND),
    )
    assertEquals(emptyList(), OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.VILLA))
    assertEquals(emptyList(), OfferPropertyRules.ownershipDocumentTypes(ListingType.RENTAL, PropertyType.LAND))
}

@Test
fun `a sale has its own price floor`() {
    assertNull(OfferPropertyRules.priceAmount(1_000_000L, formatter, ListingType.SALE))
    assertNotNull(OfferPropertyRules.priceAmount(900_000L, formatter, ListingType.SALE))
    assertNull(OfferPropertyRules.priceAmount(5_000L, formatter, ListingType.RENTAL))
}
```

Plus a rental-side case for every new signature (unchanged results) and `listingType(null)`.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest
```

- [ ] **Step 3: Implement the rules**

Move today's `CATEGORIES_BY_TYPE` to a private rental map and add a private sale map so `typeFits` and the features step share one source (`LAND` under both uses; `ROOM` absent). Keep `MAX_AVAILABILITY_DAYS`, `MIN_RENT`, `NO_PARKING`, `ROOMS`, `PARKINGS`, `ROAD_ACCESS`, `AMENITIES`, `parseDate` unchanged.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest
```

The module's other tests do not compile yet — Tasks 5–10 update their call sites; the gate closes at Task 10.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt
git commit -m "feat(npc): add the sale rules to the offer-property wizard" -m "Refs #254"
```

---

### Task 5: The session's answers carry the sale

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListing.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListingTest.kt`

**Interfaces:**
- Produces on `PendingListing`: `val listingType: ListingType? = null` (after `propertyCategory`), `val ownershipDocuments: List<TitleDocumentType> = emptyList()` (after `area`), and `fun isSale(): Boolean = FindPropertyRules.isSale(listingType)`.
- Produces in `isComplete` (new signatures from Task 4):
  - `STEP_PROPERTY` → `listingType(listingType) == null && propertyCategory(propertyCategory) == null`.
  - `STEP_TERMS` → `isPriceAmount(priceAmount, listingType)`; advance/deposit only counted when `!isSale()`; `availableFrom` unchanged.
  - `STEP_FEATURES` → `propertyType(propertyCategory, propertyType, listingType)`, `needsRooms(…, listingType)`, `needsFurniture(type, listingType)`, `needsParking(type)` (a sale's land answers none), `area(area, OfferPropertyRules.needsArea(propertyCategory, type(), listingType))`.
- Produces in `toCreateMandateRequest(currency: String)`:
  - `listingType = listingType ?: ListingType.RENTAL` (never `UNKNOWN`),
  - `price = Money(amount = priceAmount ?: 0, currency = currency, periodicity = if (isSale()) PeriodicityType.NONE else PeriodicityType.MONTHLY)`,
  - `advanceRent = advanceRent.takeIf { !isSale() }`, `securityDeposit = securityDeposit.takeIf { !isSale() }`,
  - `ownershipDocuments = ownershipDocuments.filter { isSale() && type() == PropertyType.LAND }.distinct()` — an empty list otherwise, never null,
  - `rooms`, `furnitureType`, `parkings`, `area` keep their `takeIf` with the new signatures.
  KDoc: the two answers are the seller's brief; the price per m² is not an answer and is never sent.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a rental is the same request as before`() {
    val pending = PendingListing(
        propertyCategory = PropertyCategory.RESIDENTIAL, listingType = ListingType.RENTAL,
        cityId = 1L, neighborhoodId = 2L, propertyType = "APARTMENT", rooms = "3",
        furnitureType = "UNFURNISHED", parkings = "1", area = 80, priceAmount = 150_000L,
        advanceRent = 2, securityDeposit = 2, availableFrom = LocalDate.of(2026, 12, 1),
        firstName = "Sandrine", lastName = "Eboa", email = "s@example.com", whatsappNumber = "+237600000000",
        preferredContactPeriod = ContactPeriodType.MORNING,
    )

    val request = pending.toCreateMandateRequest("XAF")

    assertEquals(ListingType.RENTAL, request.listingType)
    assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
    assertEquals(2, request.advanceRent)
    assertEquals(2, request.securityDeposit)
    assertEquals(emptyList(), request.ownershipDocuments)
}

@Test
fun `a land sale sends its documents at a one-off price and no rental terms`() {
    val pending = /* SALE, RESIDENTIAL, LAND, area 800, price 45_000_000, documents [LAND_TITLE, SURVEY_PLAN],
                      advanceRent 2, securityDeposit 2, rooms "3", furnitureType "UNFURNISHED" left over */

    val request = pending.toCreateMandateRequest("XAF")

    assertEquals(ListingType.SALE, request.listingType)
    assertEquals(PeriodicityType.NONE, request.price.periodicity)
    assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), request.ownershipDocuments)
    assertNull(request.advanceRent)
    assertNull(request.securityDeposit)
    assertNull(request.rooms)
    assertNull(request.furnitureType)
}

@Test
fun `a villa sale sends no document`() { /* SALE, VILLA, documents ticked by hand → empty list */ }

@Test
fun `a land sale without a document is complete`() { /* isComplete(STEP_FEATURES) true without documents,
    false without an area */ }
```

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=PendingListingTest
```

- [ ] **Step 3: Implement `PendingListing`**

As in *Interfaces*; keep `AMENITIES`, `has`, `firstIncompleteStep` untouched.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=PendingListingTest
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListing.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListingTest.kt
git commit -m "feat(npc): carry the sale answers in the offer-property session" -m "Refs #254"
```

---

### Task 6: The first step asks *Mettre en location / Vendre*, and the exit link follows

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyForms.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (the two project labels and `project`)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html` (first step and header)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `data class PropertyForm(val listingType: ListingType? = null, val propertyCategory: PropertyCategory? = null)`.
- Produces: `OfferPropertyController.show` gains `@RequestParam(name = "type", required = false) type: String?`; when the session has no `listingType`, `?type=SALE|RENTAL` preselects one for display only:

```kotlin
val pending = pending(session).let { saved ->
    if (saved.listingType != null) saved else saved.copy(listingType = FindPropertyRules.parseListingType(type))
}
```

- Produces: `OfferPropertyController.property` stores `listingType = form.listingType` and validates it first:
  `errors("listingType" to OfferPropertyRules.listingType(answers.listingType), "propertyCategory" to …)`.
- Produces: `exitUrl` becomes a per-step model attribute, computed from the answers, replacing the `@ModelAttribute`:

```kotlin
private fun exitUrl(answers: PendingListing): String? =
    tenantContext.getTenant().publicPortalUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        ?.let { if (answers.isSale()) "$it/owner/sell" else "$it/owner" }
```

  `step()` sets `model.addAttribute("exitUrl", exitUrl(answers))`; the confirmation branch sets it from `SubmittedMandate.listingType` (Task 9). The template keeps reading `exitUrl`.
- Produces in `MandateLabels`: `fun project(category: PropertyCategory?, listingType: ListingType?): String?` — *Vente, usage …* for a sale, today's *Location, usage …* otherwise (the two-argument overload of #253; keep the one-argument call sites of the agent's cards on it or on the two-argument one with `null`).
- Produces: `step()`'s `STEP_PROPERTY` branch adds `model.addAttribute("listingTypeLabels", MandateLabels.LISTING_TYPE_LABELS)` (from #253: `RENTAL → "Mettre en location"`, `SALE → "Vendre"`), `model.addAttribute("sale", answers.isSale())`, and `model.addAttribute("categoryDetailLabels", MandateLabels.CATEGORY_LABELS)`.
- Produces in `offer-property.html`, first step: a `data-group="listingType"` radio fieldset above *Pour quel usage ?*, `name="listingType"`, `required`, `th:checked="${#strings.equals(answers.listingType, 'RENTAL')}"` / `'SALE'`, the prototype's detail texts (*Votre agent trouve votre locataire.* / *Votre agent présente votre bien aux acheteurs.*), a `~{:: error('listingType')}` paragraph, and the h3/p reworded as the prototype (*Quel est votre projet ?*) with the rental/sale intro paragraphs rendered by `th:if="${sale}"`.

- [ ] **Step 1: Write the failing tests**

`OfferPropertyControllerTest` (standalone `MockMvc`, `MockHttpSession`):

```kotlin
@Test
fun `the first step stores the project`() {
    // POST /offer-property listingType=SALE propertyCategory=RESIDENTIAL → 3xx, session PendingListing.listingType SALE
}

@Test
fun `the first step requires the project`() {
    // POST without listingType → view "offer-property", model errors["listingType"] present, session untouched
}

@Test
fun `the type parameter preselects the project`() {
    // GET /offer-property?type=SALE → model answers.listingType SALE
}

@Test
fun `the type parameter never overwrites a saved project`() {
    // session PendingListing(listingType = RENTAL); GET /offer-property?type=SALE → model answers.listingType RENTAL
}

@Test
fun `an unknown type parameter is ignored`() {
    // GET /offer-property?type=ANY → model answers.listingType null
}

@Test
fun `the exit link follows the project`() {
    // GET with a saved SALE → model exitUrl "http://localhost:8081/owner/sell" (tenant fixture);
    // with RENTAL → ".../owner"
}
```

`OfferPropertyTemplateTest` (real Thymeleaf, jsoup): the first step renders both radios, `SALE` checked for a sale answer, the *Vendre* label, and no *Loyer* wording.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 3: Implement form, controller, label and template**

As in *Interfaces*. `?type=` is read on the `show` mapping only — never on a POST, never stored.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): ask the owner to let or sell in the first step" -m "Refs #254"
```

---

### Task 7: The conditions step asks the sale price, without the rental terms

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html` (terms step only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `OfferPropertyController.terms` stores `advanceRent = …takeIf { !pending.isSale() }` and `securityDeposit = …takeIf { !pending.isSale() }`, and validates with the sale-aware `priceAmount(answers.priceAmount, formatter(), answers.listingType)`.
- Produces: `step()`'s `STEP_TERMS` branch adds `model.addAttribute("sale", answers.isSale())`; the template renders:
  - the label *Loyer mensuel souhaité* / *Prix de vente souhaité* by `th:if="${sale}"`,
  - the suffix `th:text="|${currency}${sale ? '' : ' / mois'}|"`,
  - the advance and deposit fields only when `!sale`,
  - the terms intro and the hint panel by `th:if` on `sale` (the prototype's copy),
  - the availability date unchanged (*Disponible à partir du*, required).

- [ ] **Step 1: Write the failing tests**

`OfferPropertyControllerTest`:

```kotlin
@Test
fun `a sale refuses a price below the floor`() {
    // session SALE; POST terms priceAmount=900000 availableFrom=tomorrow → view with errors["priceAmount"]
}

@Test
fun `a sale needs no advance nor deposit`() {
    // session SALE; POST terms priceAmount=45000000 availableFrom=tomorrow advanceRent=2 securityDeposit=2
    // → 3xx, session advanceRent null, securityDeposit null
}

@Test
fun `a rental keeps its advance, deposit and floor`() {
    // RENTAL; priceAmount=150000 advanceRent=2 securityDeposit=2 → 3xx, session keeps both;
    // priceAmount=4000 → errors["priceAmount"]
}
```

`OfferPropertyTemplateTest`: a sale terms step reads *Prix de vente souhaité* with no `/ mois`, shows no advance/deposit field, keeps *Disponible à partir du*; a rental keeps *Loyer mensuel souhaité* + `FCFA / mois`, both fields, its hint.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 3: Implement controller and template**

As in *Interfaces*. No amount reaches the model as a rate.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): ask the sale price without the rental terms" -m "Refs #254"
```

---

### Task 8: The features step offers the sale types, asks the documents and shows the rate

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyForms.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (the three document labels)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html` (features step only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces: `FeaturesForm` gains `val ownershipDocuments: List<TitleDocumentType> = emptyList()`.
- Produces: `OfferPropertyController.features` keeps the documents only for a land sale and drops the rest:

```kotlin
val documents = form.ownershipDocuments
    .filter { it in OfferPropertyRules.ownershipDocumentTypes(pending.listingType, type) }
    .distinct()
```

  and passes `required = OfferPropertyRules.needsArea(pending.propertyCategory, type, pending.listingType)` to `OfferPropertyRules.area`, whose "something typed that isn't a number" branch stays. `rooms`, `furnitureType`, `parkings` take the new listing-type-aware rules.
- Produces: `step()`'s `STEP_FEATURES` branch uses `OfferPropertyRules.propertyTypes(answers.propertyCategory!!, answers.listingType)`, adds `model.addAttribute("sale", answers.isSale())`, `model.addAttribute("ownershipDocumentLabels", MandateLabels.byName(MandateLabels.TITLE_DOCUMENT_LABELS))` (from #253: *Titre foncier* / *Plan de bornage* / *Certificat de propriété*) and `model.addAttribute("asksOwnershipDocuments", OfferPropertyRules.ownershipDocumentTypes(answers.listingType, FindPropertyRules.parsePropertyType(answers.propertyType)).isNotEmpty())`.
- Produces in `offer-property.html`, features step:
  - a `<fieldset data-group="ownershipDocuments" th:hidden="${!asksOwnershipDocuments}">` with legend *Document de propriété* and `(facultatif)`, the prototype's hint (*Facultatif : vous pouvez cocher plusieurs documents, ou aucun. Votre agent les vérifie lors de l'audit.*), one `input type="checkbox" name="ownershipDocuments"` per label, no `required`;
  - the area label *Surface du terrain estimée* when the type is a land sale (`th:text`) and the required hint, keeping today's label otherwise;
  - `<p class="hint" data-price-per-sqm hidden></p>` after the area field — empty server-side, filled by Task 10;
  - the type radios for the sale set (no *Chambre*; *Terrain* offered under both categories), with `th:hidden`/filter driven by `propertyTypes`.

- [ ] **Step 1: Write the failing tests**

`OfferPropertyControllerTest`:

```kotlin
@Test
fun `a land sale requires its surface`() {
    // session SALE/RESIDENTIAL; POST features type=LAND without area → view with errors["area"], session untouched
}

@Test
fun `a land sale keeps any number of documents`() {
    // POST type=LAND ownershipDocuments=[LAND_TITLE, SURVEY_PLAN] → session ownershipDocuments both
}

@Test
fun `a villa sale drops the documents`() {
    // POST type=VILLA ownershipDocuments=[LAND_TITLE] → session empty
}

@Test
fun `a land rental drops the documents`() {
    // session RENTAL/RESIDENTIAL; POST type=LAND ownershipDocuments=[LAND_TITLE] → session empty, area optional
}

@Test
fun `a sale stores no rooms nor furniture`() {
    // session SALE; POST type=HOUSE rooms=3 furnitureType=UNFURNISHED → session both null
}
```

`OfferPropertyTemplateTest`: a sale residential step offers *Terrain*, no *Chambre*, no *Ameublement*; a land sale shows the three checkboxes and *Surface du terrain estimée*; a villa sale hides them; a rental shows none; the `[data-price-per-sqm]` hook exists and is empty.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 3: Implement form, controller, labels and template**

As in *Interfaces*. The checkboxes render even when hidden, so a no-JS post still carries them; the server filters. Add `data-listing-type="${answers.listingType ?: 'RENTAL'}"` to the features form for the script (Task 10).

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): offer the sale types and ask the ownership documents" -m "Refs #254"
```

---

### Task 9: The review, the confirmation and the merchant screens word a sale

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/SubmittedMandate.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (`criteria`, `ownershipDocuments`)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html` (review and confirmation)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html` (the criteria rows)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`

**Interfaces:**
- Produces: `SubmittedMandate` gains `val listingType: ListingType = ListingType.RENTAL`; `submit` fills it from `pending.listingType`; `show`'s confirmation branch adds `model.addAttribute("listingType", submitted.listingType)` and `model.addAttribute("exitUrl", exitUrl(submitted.listingType))`.
- Produces in `MandateLabels`:
  - `fun ownershipDocuments(documents: List<TitleDocumentType>): String` — the labels in order joined by `" · "`, or *Aucun document déclaré* when empty.
  - In `criteria(mandate, locations, formatter)`: skip the *Meublé* row when `mandate.listingType == ListingType.SALE`, and append after the surface `("Document de propriété" to ownershipDocuments(mandate.ownershipDocuments))` when the mandate is a `SALE` · `LAND` with any. Leave *Locataire* / *Propriétaire*, *Loyer*, *Disponibilité* alone — #259 owns them.
- Produces: `OfferPropertyController`'s review branch: `categoryLabel = MandateLabels.project(answers.propertyCategory, answers.listingType)`, the price line `answers.priceAmount?.let { formatter().money(it) + if (answers.isSale()) "" else " par mois" }`, no advance/deposit lines for a sale, `ownershipDocumentsLabel = answers.ownershipDocuments.takeIf { answers.isSale() && …LAND }?.let { MandateLabels.ownershipDocuments(it) }`.
- Produces in `offer-property.html`, review step: the project row and the price row use the model attributes, and a *Document de propriété* row `th:if` on the sale · land answer; the confirmation's three next steps get a sale variant (`th:if="${listingType?.name() == 'SALE'}"`): a verified agent of the area audits, photographs and presents the property to the buyers, then *Vous validez l'annonce*, then the sale closing wording of the prototype.
- Produces in `mandate.html`: the criteria list renders `MandateLabels.criteria` as today — no template change beyond what the list already does (confirm the new pair renders, add nothing else).

- [ ] **Step 1: Write the failing tests**

`MandateLabelsTest`:

```kotlin
@Test
fun `a sale criteria has no furniture row and shows the documents`() {
    val rows = MandateLabels.criteria(landSaleMandate, locations, formatter)

    assertTrue(rows.none { it.first == "Meublé" })
    assertEquals("Titre foncier · Plan de bornage", rows.first { it.first == "Document de propriété" }.second)
}

@Test
fun `a rental criteria is unchanged`() { /* same rows as before: Meublé, Loyer monthly, no Document de propriété */ }

@Test
fun `no declared document reads so`() {
    assertEquals("Aucun document déclaré", MandateLabels.ownershipDocuments(emptyList()))
}
```

`OfferPropertyControllerTest` — review model: `categoryLabel` = *Vente, usage résidentiel*, the price line without *par mois*, `ownershipDocumentsLabel` = *Aucun document déclaré* for a land sale with none and absent for a villa; confirmation model carries `listingType` and the sale `exitUrl`.

`OfferPropertyTemplateTest` — the sale review reads *Vente*, *Prix de vente*, *Document de propriété*, no *Avance* nor *Caution*; the rental review is byte-identical to today's labels; the sale confirmation mentions a sales agent and links `/owner/sell`.

- [ ] **Step 2: Run them, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateLabelsTest+OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 3: Implement the labels, controller and templates**

As in *Interfaces*.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateLabelsTest+OfferPropertyControllerTest+OfferPropertyTemplateTest'
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): word a sale on the offer review and confirmation" -m "Refs #254"
```

---

### Task 10: The page script ports the prototype's sale enhancements

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/offer-property.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt` (the hooks the script needs are rendered)

**Interfaces:**
- Produces in the script, ported from `src/main/html/v2/assets/js/offer-property.js` and adapted to the server-rendered, one-step-per-page flow:
  - `listingType` read from the form's `data-listing-type` (the controller renders it; `RENTAL` when unanswered) — never from another step's checked radio;
  - `isSale`, `categoriesOf` / `SALE_CATEGORIES` (`ROOM` none, `LAND` both), `needsOwnershipDocuments`, `checkedValues` / `setCheckedAll`;
  - on the features step: hide `rooms` / `furnitureType` / `hasParking` / `parkings` that a sale doesn't ask, show `ownershipDocuments` for a land sale, make the area required for a land sale with `OfferPropertyRules`' messages, and update `[data-price-per-sqm]` on `input` — `"Soit ≈ 56 000 FCFA/m² pour 800 m²"` from the saved asking price and the typed surface, rounded to the nearest 100 FCFA, hidden otherwise;
  - on the first step: switch the project's intro and category detail texts when the radio changes;
  - nothing posted or stored for the rate: it never enters a hidden field.

- [ ] **Step 1: Write the failing test**

`OfferPropertyTemplateTest` — assert the hooks exist, so the script cannot silently no-op:

```kotlin
@Test
fun `the sale features step has the price per square metre hook`() {
    // features step, SALE · LAND: document.selectFirst("[data-price-per-sqm]") not null and empty
}

@Test
fun `the forms carry the listing type for the script`() {
    // first step: form[data-offer-form].attr("data-listing-type") == "RENTAL" by default, "SALE" for a sale session
}
```

- [ ] **Step 2: Run it, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyTemplateTest
```

- [ ] **Step 3: Port the script**

Copy the prototype's functions rather than re-deriving them, keeping its comments' intent (the rate is display-only). The production file is a plain script for the bundle: no framework, no new dependency, the house style of `src/main/assets/js`.

- [ ] **Step 4: Run the tests and build the assets**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyTemplateTest && mvn generate-resources -Dheadless=true
```

- [ ] **Step 5: Run the module gate**

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): enhance the sale steps of the offer-property script" -m "Refs #254"
```

---

### Task 11: Release the DTO and verify the reactor

**Files:**
- Modify: `modules/ndopify-dto/pom.xml` (version)
- Modify: `pom.xml` (`ndopify-dto.version`)

**Interfaces:**
- Consumes: Task 1's DTO and every module built against its `-SNAPSHOT`.
- Produces: a released `ndopify-dto` every deploying module resolves, per *Versioning and Release* in `modules/ndopify-dto/CLAUDE.md`: bump the module's version, run the `ndopify-dto-main.yml` workflow, tag `ndopify-dto-<version>`, bump `ndopify-dto.version` in the root POM.

- [ ] **Step 1: Bump and release the DTO**

Follow `modules/ndopify-dto/CLAUDE.md` exactly (patch bump for an additive field). Expect the release workflow green and the package visible on GitHub Packages.

- [ ] **Step 2: Bump the version property and verify the reactor**

```bash
mvn install -Dheadless=true
```

`ndopify-server` needs local MySQL for its endpoint tests; the other modules need neither.

- [ ] **Step 3: Commit**

```bash
git add pom.xml modules/ndopify-dto/pom.xml
git commit -m "chore(deps): release ndopify-dto with the seller's documents" -m "Refs #254"
```

- [ ] **Step 4: No production behaviour without #257**

#254 must not deploy before #257 (sale LISTING contract): record that in the pull request's description. Sale
dispatch is not a dependency: M1 offers a sale listing to every verified agent of the area (no opt-in, see the epic
plan).

---

## Self-review

- **Spec coverage:** AC 1–2 (Task 6), AC 3 (Task 7), AC 4 (Tasks 4, 8), AC 5 (Tasks 8, 10), AC 6 (Tasks 4, 5, 8), AC 7 (Task 9), AC 8 (Task 9), AC 9 (Tasks 1–2, 5), AC 10 (Tasks 2–3, 9), AC 11 (Tasks 8, 10), AC 12 (Tasks 5, 6–9 tests).
- **Names:** `ownershipDocuments` (DTO, entity, forms, session, request), `OfferPropertyRules.ownershipDocumentTypes`, `MandateMailLabels.ownershipDocuments`, `MandateLabels.ownershipDocuments`, `MIN_SALE_PRICE`, `needsArea(category, type, listingType)`.
- **Review Focus:** each line has its test in the owning task (Tasks 2, 5, 8, 10, 6, 7).
