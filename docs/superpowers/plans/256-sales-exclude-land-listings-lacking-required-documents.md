# Exclude Land Listings Lacking the Buyer's Required Documents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A SALE · LAND search that requires title documents is no longer matched with a SALE · LAND listing whose seller declared none of them, and the agent sees a short note explaining that filter on the mandate page.

**Architecture:** A new `MatchRuleOut.TITLE_DOCUMENTS` in `Matcher.ruleOut` reads `search.titleDocuments` (from #253) and `listing.mandate.ownershipDocuments` (from #254); `MatchService` already deletes an own `ACTIVE` row for any rule and counts the rule in `MATCH_SUMMARY`, so it needs tests, not code. Partner Central promotes the *Documents exigés* row from the folded criteria into the visible ones and renders the approved note from a new `MandateView` boolean. No DTO field, no migration, no new query.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5 + `kotlin.test`, mockito-kotlin, `TestRestTemplate` on MySQL 8, Thymeleaf 3, jsoup, `MockMvc`.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/256

**Design record (epic roadmap):** [`docs/superpowers/plans/2026-11-22-v2-property-sales.md`](https://github.com/wutsi/ndopify-mono/blob/main/docs/superpowers/plans/2026-11-22-v2-property-sales.md) — *Story 8*.

**Approved prototype (the baseline to port in Task 3, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/mandate.html`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/partner-central.js`
- `modules/ndopify-partner-central/src/main/html/v2/README.md`

Review at `http://localhost:8899/index.html` (serve `modules/ndopify-partner-central/src/main/html/v2`), accept offer **104** and open its mandate in the same tab; `&documents=none` shows the no-requirement state. `src/main/html/` was committed with this plan and is never edited here.

## Before you start

This plan builds on #253 and #254. Confirm both are on `main`; if any check fails, stop and land the missing story first.

```bash
git grep -n "titleDocuments" -- modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt
git grep -n "ownershipDocuments" -- modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt
git grep -n "title_documents\|ownership_documents" -- modules/ndopify-server/src/main/resources/db/migration/common/
git grep -n "Documents exigés" -- modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt
```

Expected: `List<TitleDocumentType> titleDocuments` and `List<TitleDocumentType> ownershipDocuments` on the entity/DTO, both columns in the `V16_*` migrations, and the `"Documents exigés"` row in `MandateLabels.criteria`.

## Global Constraints

- **Grooming decisions win.** Empty seller list = *none declared* (rule out). **Any one** required document is enough. Hard rule-out, no score penalty, no ranking by coverage. Land sale only: a rental, a non-`LAND` search or a non-`LAND` listing never fires the rule.
- **Re-matching follows the other rules.** An own `ACTIVE` row ruled out is deleted by `MatchService.discard`; a co-brokerage row is never deleted (sticky). No new status, flag or transition.
- **The prototype is the design.** Task 3 ports it exactly: the *Documents exigés* row stays among the visible criteria, and the note is the approved copy, `.hint` component, below the criteria. Change no other label, order or state. French UI, English code, comments, plan and commits.
- **Exact note copy** (one paragraph, static in the template): « Les terrains à vendre dont le vendeur n'a déclaré aucun de ces documents ne vous sont pas proposés. Un seul de ces documents déclaré suffit. »
- **No shared code.** No `ndopify-dto` change and no DTO release; `MatchRuleOut` is server-internal. No Flyway migration.
- **No new query.** `ListingEntity.mandate` is an eager `@OneToOne` already read by the own-property rule; `Matcher` reads `ownershipDocuments` from it. Prove it with the endpoint test, never with a new fetch.
- Only `ndopify-server` and `ndopify-partner-central` are touched. Never touch `ndopify-web`, `ndopify-esignature`, `ndopify-web-core`, `ndopify-dto` or `src/main/html/`.
- `mvn verify -Dheadless=true` green in each touched module (ktlint, tests, JaCoCo). `ndopify-server` needs local MySQL.
- Commits are conventional and scoped, each with `Refs #256` in the body.

## Review Focus

- **Empty seller list vs no requirement.** A buyer who ticked nothing must not filter anything; a buyer who ticked one document must exclude a seller who ticked none. Tasks 1 and 2 pin both sides.
- **Any one is enough.** A listing declaring one of two required documents matches and is scored exactly as if the buyer required nothing. Task 1 pins the equal score.
- **The rule never leaks outside a land sale.** A rental or non-land search hand-crafted with documents, and a non-land listing, must never be refused by `TITLE_DOCUMENTS`. Task 1 pins each case.
- **The customer's explicit interest bypasses the rule.** `scoreInterest` must still offer a pair this rule would refuse. Task 1 pins it.
- **The note is absent where it means nothing.** No note for a rental, a non-land sale, a seller's mandate or a land search requiring no document; the row reads *Aucun document exigé* in that last case. Task 3 pins all four.

---

### Task 1: The `TITLE_DOCUMENTS` rule in `Matcher`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchResult.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Produces: `MatchRuleOut.TITLE_DOCUMENTS`; `Matcher.evaluate` returns `RuledOut(TITLE_DOCUMENTS, "required=… declared=…")` when the rule fires.
- Consumes: `MandateEntity.titleDocuments`, `ListingEntity.mandate.ownershipDocuments`, `TitleDocumentType`, `ListingType`, `PropertyType`.

- [ ] **Step 1: Add the enum value**

In `MatchResult.kt`, add `TITLE_DOCUMENTS` after `CATEGORY` (the chain reads in rule order):

```kotlin
enum class MatchRuleOut {
    OWN_PROPERTY,
    LISTING_TYPE,
    CITY,
    CATEGORY,
    TITLE_DOCUMENTS,
    PROPERTY_TYPE,
    SIBLING_ROOMS,
    CURRENCY,
    PERIODICITY,
    OVER_BUDGET,
}
```

(The order of the constants is documentation only; `evaluate` decides the precedence.)

- [ ] **Step 2: Write the failing `MatcherTest` cases**

Add imports for `TitleDocumentType`, then the fixtures and tests:

```kotlin
private val landSearch = search.copy(
    listingType = ListingType.SALE,
    propertyType = PropertyType.LAND,
    rooms = null,
    price = 15_000_000,
    pricePeriodicityType = PeriodicityType.NONE,
    titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
)
private val landListing = listingA.copy(
    listingType = ListingType.SALE,
    propertyType = PropertyType.LAND,
    rooms = null,
    price = 12_000_000,
    pricePeriodicityType = PeriodicityType.NONE,
    mandate = listingA.mandate.copy(ownershipDocuments = emptyList()),
)

@Test fun `ruled out - the seller declared none of the required documents`() {
    assertNull(score(landSearch, landListing))
    assertEquals(MatchRuleOut.TITLE_DOCUMENTS, rule(landSearch, landListing))
}

@Test fun `ruled out - the seller declared only other documents`() {
    val listing = landListing.copy(
        mandate = landListing.mandate.copy(ownershipDocuments = listOf(TitleDocumentType.CERTIFICATE_OF_OWNERSHIP)),
    )

    assertEquals(MatchRuleOut.TITLE_DOCUMENTS, rule(landSearch, listing))
}

@Test fun `one required document is enough`() {
    val listing = landListing.copy(
        mandate = landListing.mandate.copy(ownershipDocuments = listOf(TitleDocumentType.SURVEY_PLAN)),
    )

    assertEquals(score(landSearch.copy(titleDocuments = emptyList()), listing), score(landSearch, listing))
    assertNull(rule(landSearch, listing))
}

@Test fun `no required document, no filter`() {
    assertNotNull(score(landSearch.copy(titleDocuments = emptyList()), landListing))
    assertNull(rule(landSearch.copy(titleDocuments = emptyList()), landListing))
}

@Test fun `an unknown required document is not a requirement`() {
    assertNull(rule(landSearch.copy(titleDocuments = listOf(TitleDocumentType.UNKNOWN)), landListing))
}

@Test fun `a rental never fires the documents rule`() {
    assertNull(rule(search.copy(titleDocuments = listOf(TitleDocumentType.LAND_TITLE)), listingA))
}

@Test fun `a non-land sale never fires the documents rule`() {
    val listing = landListing.copy(propertyType = PropertyType.HOUSE, rooms = 3)

    assertNull(rule(landSearch.copy(propertyType = PropertyType.HOUSE, rooms = "3"), listing))
}

@Test fun `a non-land listing is refused on its type`() {
    assertEquals(MatchRuleOut.PROPERTY_TYPE, rule(landSearch, landListing.copy(propertyType = PropertyType.VILLA)))
}

@Test fun `the listing type is reported before the documents`() {
    assertEquals(MatchRuleOut.LISTING_TYPE, rule(landSearch, landListing.copy(listingType = ListingType.RENTAL)))
}

@Test fun `the documents are reported before the budget`() {
    assertEquals(MatchRuleOut.TITLE_DOCUMENTS, rule(landSearch, landListing.copy(price = 30_000_000)))
}

@Test fun `the customer's explicit interest is never refused on documents`() {
    assertNotNull(matcher.scoreInterest(landSearch, landListing, distances, TODAY))
}
```

- [ ] **Step 3: Run them, expect failure**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatcherTest
```

Expected: FAIL — the rule always returns a score (or `null` for another rule), never `TITLE_DOCUMENTS`.

- [ ] **Step 4: Implement the rule**

In `Matcher.ruleOut`, insert the check after `categoryMismatch` and before the currency check:

```kotlin
documentsMismatch(search, listing) ->
    MatchResult.RuledOut(
        MatchRuleOut.TITLE_DOCUMENTS,
        "required=${search.titleDocuments} declared=${listing.mandate.ownershipDocuments}",
    )
```

and add, beside `categoryMismatch`:

```kotlin
// Any one of the required documents is enough; only a land sale, where both answers exist, filters at all.
private fun documentsMismatch(search: MandateEntity, listing: ListingEntity): Boolean {
    val required = search.titleDocuments.filter { it != TitleDocumentType.UNKNOWN }
    return required.isNotEmpty() &&
        search.listingType == ListingType.SALE &&
        search.propertyType == PropertyType.LAND &&
        listing.listingType == ListingType.SALE &&
        listing.propertyType == PropertyType.LAND &&
        listing.mandate.ownershipDocuments.none { it != TitleDocumentType.UNKNOWN && it in required }
}
```

Update the `Matcher` class KDoc's rule list: insert as rule 4 « a land purchase requiring a document the seller didn't declare (any one of the required documents is enough) » and renumber currency / periodicity / over-budget / sibling-rooms.

- [ ] **Step 5: Run the tests, expect success**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatcherTest
```

Expected: PASS, every existing case included.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchResult.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt
git commit -m "feat(server): rule out a land listing without the buyer's required documents" -m "Refs #256"
```

---

### Task 2: Matching storage, logging and the end-to-end proof

`MatchService` needs no production change: `matchAll` already counts any `RuledOut` rule in `MATCH_SUMMARY`, and `discard` deletes an own `ACTIVE` row for every rule. This task proves it against the real entity, the persisted enum lists and the eager `listing.mandate`.

**Files:**
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/RunMatchTitleDocumentsEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/match/RunMatchTitleDocumentsEndpoint.sql`

**Interfaces:**
- Consumes: `MatchRuleOut.TITLE_DOCUMENTS` (Task 1), `MatchService.matchMandate`, `MatchService.matchAll`, `RunMatchResponse`, `SearchMatchResponse`.
- Fixture columns: `T_MANDATE.title_documents` and `T_MANDATE.ownership_documents` (comma-separated names, `NULL` / `''` when empty).

- [ ] **Step 1: Write the `MatchServiceTest` cases**

Put this beside the existing `ruledOut` fixture:

```kotlin
private val ruledOutDocuments =
    MatchResult.RuledOut(MatchRuleOut.TITLE_DOCUMENTS, "required=[LAND_TITLE] declared=[]")
```

Then two tests, mirroring `matchMandate deletes an ACTIVE match no longer matching` and `matchMandate never deletes a co-brokerage match` but with `ruledOutDocuments`:

```kotlin
@Test
fun `matchMandate deletes an ACTIVE own match ruled out on documents`() {
    val existing = MatchEntity(id = 7L, mandate = search, listing = listing1, status = MatchStatus.ACTIVE)
    doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(existing).whenever(matchDao).findByMandateAndListing(search, listing1)
    doReturn(ruledOutDocuments).whenever(matcher).evaluate(any(), any(), any(), anyOrNull())

    assertEquals(0, service.matchMandate(MANDATE_ID))
    verify(matchDao).delete(existing)
    verify(matchDao, never()).save(any<MatchEntity>())
}

@Test
fun `matchMandate keeps a co-brokerage match ruled out on documents`() {
    val existing = MatchEntity(
        id = 7L,
        mandate = search,
        listing = otherListing,
        status = MatchStatus.ACTIVE,
        coBrokerage = true,
    )
    doReturn(emptyList<ListingEntity>()).whenever(listingDao)
        .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(existing).whenever(matchDao).findByMandateAndListing(search, otherListing)
    doReturn(listOf(otherListing)).whenever(listingDao)
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(CITY_ID, ListingType.RENTAL, ListingStatus.PUBLISHED, AGENT_ID)
    doReturn(ruledOutDocuments).whenever(matcher).evaluate(any(), any(), any(), anyOrNull())

    assertEquals(0, service.matchMandate(MANDATE_ID))
    verify(matchDao, never()).delete(any<MatchEntity>())
    verify(matchDao, never()).save(any<MatchEntity>())
}
```

- [ ] **Step 2: Run them; they must pass without a `MatchService` change**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest
```

Expected: PASS. These tests document that the existing `discard` / `MATCH_SUMMARY` paths already handle the new rule. If one fails, the fixture is wrong; do not add production code to `MatchService` for this story.

- [ ] **Step 3: Create the sale-land SQL fixture**

Copy the tenant / locations / parties / user / membership / customer / agent block from `RunMatchEndpoint.sql`, then add one SALE · LAND search and three SALE · LAND listings:

```sql
-- 1: the land purchase, requiring one document. 11..13: the sellers' mandates, declaring none, another, and the required one.
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, price, price_periodicity_type, currency_code, moving_period, title_documents, ownership_documents
) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 15000000, 'NONE', 'XAF', 'WITHIN_3_MONTHS', 'LAND_TITLE', NULL),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, ''),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, 'SURVEY_PLAN'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'SALE', 'RESIDENTIAL', 2370201, 'LAND', 12000000, 'NONE', 'XAF', NULL, NULL, 'LAND_TITLE,CERTIFICATE_OF_OWNERSHIP');

INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES (1, 1001);

INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, price, currency_code, price_periodicity_type, available_from, title
) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'SALE', 'PUBLISHED', 2370201, 1001, 'LAND', 12000000, 'XAF', 'NONE', NULL, 'C');
```

Adjust a column only if a migration renamed it; never add a column to make the fixture easier.

- [ ] **Step 4: Write the failing endpoint test**

```kotlin
package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.RunMatchResponse
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** #256: a land purchase is only matched with a land whose seller declared one of the required documents. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RunMatchTitleDocumentsEndpoint.sql"])
class RunMatchTitleDocumentsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `only the listing declaring a required document matches`() {
        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body!!.count)
        val matches = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java).body!!.matches
        assertEquals(listOf(103L), matches.map { it.listingId })
    }

    @Test
    fun `a re-run deletes an ACTIVE match now ruled out`() {
        jdbc.update(
            "INSERT INTO T_MATCH(tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price) " +
                "VALUES (1, 1, 102, 20, 'ACTIVE', 0.9, 12000000)"
        )

        rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM T_MATCH WHERE mandate_id=1 AND listing_id=102",
                Int::class.java,
            ),
        )
    }
}
```

- [ ] **Step 5: Run the endpoint test**

```bash
cd modules/ndopify-server && mvn test -Dtest=RunMatchTitleDocumentsEndpointTest
```

Expected: PASS after Task 1 — the first test stores only `103`; the second deletes the stale `102` row. If the first test stores `101` too, the ownership documents were read as unknown/empty — fix the fixture, not the rule.

- [ ] **Step 6: Run the module gate and commit**

```bash
cd modules/ndopify-server && mvn verify -Dheadless=true
git add modules/ndopify-server/src/test
git commit -m "test(server): cover the title-documents matching rule end to end" -m "Refs #256"
```

Expected: ktlint, the whole server test suite and the JaCoCo gate green.

---

### Task 3: The agent's mandate page note

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Produces on `MandateView`: `val titleDocumentsRequired: Boolean` — true only for a `SEARCH` · `SALE` · `LAND` mandate with at least one `TitleDocumentType` other than `UNKNOWN`. The template shows the note on it.
- Consumes: `Mandate.titleDocuments` (#253), `MandateLabels.criteria`'s `"Documents exigés"` row (#253), `ListingType`, `PropertyType`, `TitleDocumentType`.

- [ ] **Step 1: Port the approved prototype into the production page**

This step is the port, not a new design: `templates/mandate.html` gets the note below the visible criteria, and `MandateViews` keeps the *Documents exigés* row visible. The exact markup and computation are in Steps 3 and 5; do not change the copy, the `.hint` component or the row's place.

- [ ] **Step 2: Write the failing view tests**

In `MandateViewsTest`, add the imports for `ListingType`, `PropertyType` and `TitleDocumentType`, then a companion fixture and four tests (mirror the existing `criteria` test's assertion style):

```kotlin
// In the companion object: an approved land purchase requiring two documents.
val LAND_SALE = APPROVED.copy(
    listingType = ListingType.SALE,
    propertyType = PropertyType.LAND,
    titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
    price = Money(15_000_000, "XAF", PeriodicityType.NONE),
)

@Test
fun `a land purchase keeps the documents visible and shows the note`() {
    val view = view(LAND_SALE)

    assertTrue(view.titleDocumentsRequired)
    assertTrue(view.keyCriteria.contains("Documents exigés" to "Titre foncier · Plan de bornage"))
    assertFalse(view.moreCriteria.any { it.first == "Documents exigés" })
}

@Test
fun `a land purchase without documents shows the row, not the note`() {
    val view = view(LAND_SALE.copy(titleDocuments = emptyList()))

    assertFalse(view.titleDocumentsRequired)
    assertTrue(view.keyCriteria.contains("Documents exigés" to "Aucun document exigé"))
}

@Test
fun `a rental has no documents row nor note`() {
    val view = view(MANDATE)

    assertFalse(view.titleDocumentsRequired)
    assertFalse((view.keyCriteria + view.moreCriteria).any { it.first == "Documents exigés" })
}

@Test
fun `a seller's land mandate has no documents exigés row nor note`() {
    val view = view(
        MANDATE.copy(
            mandateType = MandateType.LISTING,
            listingType = ListingType.SALE,
            propertyType = PropertyType.LAND,
            ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE),
        ),
    )

    assertFalse(view.titleDocumentsRequired)
    assertFalse((view.keyCriteria + view.moreCriteria).any { it.first == "Documents exigés" })
}
```

- [ ] **Step 3: Run the view tests, expect failure**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateViewsTest
```

Expected: compile failure — `titleDocumentsRequired` is unknown and no *Documents exigés* row is in `keyCriteria`.

- [ ] **Step 4: Implement `MandateView.titleDocumentsRequired` and the visible row**

In `MandateView`, after `moreCriteria`:

```kotlin
/** A land purchase requiring at least one title document: the page shows the documents-filter note. */
val titleDocumentsRequired: Boolean,
```

In `MandateViews`, add `private const val DOCUMENTS = "Documents exigés"` beside `TYPE` / `ROOMS` / `COMMENTS`, and in `criteria(mandate, locations, formatter)` keep the row visible:

```kotlin
val key = rows.take(KEY_FACTS) +
    listOf("Type de bien" to listOfNotNull(byTerm[TYPE], rooms).joinToString(", ")) +
    listOfNotNull(byTerm[COMMENTS]?.let { COMMENTS to it }) +
    listOfNotNull(byTerm[DOCUMENTS]?.let { DOCUMENTS to it })
val more = rows.drop(KEY_FACTS).filter { (term, _) -> term !in setOf(TYPE, ROOMS, COMMENTS, DOCUMENTS) }
```

In `MandateViews.of`, compute the flag and pass it at construction (after `moreCriteria`):

```kotlin
titleDocumentsRequired = mandate.mandateType == MandateType.SEARCH &&
    mandate.listingType == ListingType.SALE &&
    mandate.propertyType == PropertyType.LAND &&
    mandate.titleDocuments.any { it != TitleDocumentType.UNKNOWN },
```

No other `MandateView` construction exists; update it and the imports.

- [ ] **Step 5: Port the note markup**

In `templates/mandate.html`, after the visible criteria `<dl data-mandate-key-criteria>…</dl>` and before the `<details class="assignment__details mandate-more" …>`, insert exactly the prototype's hint:

```html
<div class="hint" th:if="${m.titleDocumentsRequired}" data-mandate-documents-note>
    <svg aria-hidden="true" class="icon" focusable="false" viewBox="0 0 20 20">
        <circle cx="10" cy="10" fill="none" r="8.25" stroke="currentColor" stroke-width="1.5"/>
        <path d="M10 9v5" fill="none" stroke="currentColor" stroke-linecap="round" stroke-width="1.5"/>
        <circle cx="10" cy="6.25" fill="currentColor" r="1"/>
    </svg>
    <p>Les terrains à vendre dont le vendeur n'a déclaré aucun de ces documents ne vous sont pas proposés. Un seul
        de ces documents déclaré suffit.</p>
</div>
```

It is plain server-rendered HTML: no script, no model flag from the prototype's `?documents=none`.

- [ ] **Step 6: Run the view tests, expect success, then write the template tests**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateViewsTest
```

Expected: PASS. In `MandateTemplateTest`, import `LAND_SALE` from `MandateViewsTest.Companion` and add:

```kotlin
@Test
fun `documents note for a land purchase`() {
    val page = render(LAND_SALE)

    val key = page.select("[data-mandate-key-criteria] .review__item")
    assertEquals("Documents exigés", key.last().selectFirst("dt")?.text())
    assertEquals("Titre foncier · Plan de bornage", key.last().selectFirst("dd")?.text())
    assertEquals(
        "Les terrains à vendre dont le vendeur n'a déclaré aucun de ces documents ne vous sont pas proposés. " +
            "Un seul de ces documents déclaré suffit.",
        page.selectFirst("[data-mandate-documents-note]")?.text(),
    )
}

@Test
fun `no documents note without a requirement`() {
    val page = render(LAND_SALE.copy(titleDocuments = emptyList()))

    assertNull(page.selectFirst("[data-mandate-documents-note]"))
    assertTrue(
        page.select("[data-mandate-key-criteria] .review__item")
            .any { it.selectFirst("dt")?.text() == "Documents exigés" && it.selectFirst("dd")?.text() == "Aucun document exigé" },
    )
}

@Test
fun `no documents note for a rental`() {
    assertNull(render(APPROVED).selectFirst("[data-mandate-documents-note]"))
}
```

- [ ] **Step 7: Run the template tests and the module gate**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=MandateTemplateTest
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

Expected: PASS, then the module's ktlint, tests and JaCoCo gate green.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): note the documents filter on a land search mandate" -m "Refs #256"
```

---

## Manual verification

With MySQL, `ndopify-server` on 8080 and Partner Central on 8081 (tenant seeded), create through the two public wizards a SALE · LAND SEARCH requiring *Titre foncier* and two SALE · LAND LISTING mandates — one with no document, one with *Titre foncier* — both assigned to the same agent and published, then `POST /v1/matches/run?mandate-id=<search>`. The *Biens correspondants* section shows only the second plot, and the mandate page shows the visible *Documents exigés* and the note. A rental and a land purchase that requires nothing are unchanged, and the note is absent (`Aucun document exigé` on the row).

## Self-review

- Every acceptance criterion has a task: 1 → Task 1 tests, 2 → Task 1 `one required document is enough`, 3 → Task 1 `no required document, no filter`, 4 → Task 1 rental / non-land tests, 5 → Task 1 `scoreInterest`, 6 → Task 2 both tests, 7 → Task 3, 8 → Task 2 (the existing `MATCH_SUMMARY` and `NO_MATCH` paths), 9 → Task 1's unchanged existing tests plus Task 2's rental fixture review.
- No DTO, migration, shared-module or production `MatchService` change; `src/main/html/` is not edited by any task.
- Names are consistent across tasks: `TITLE_DOCUMENTS`, `titleDocumentsRequired`, `[data-mandate-documents-note]`.
