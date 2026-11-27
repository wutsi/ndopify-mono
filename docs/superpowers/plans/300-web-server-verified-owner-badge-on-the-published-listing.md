# Verified-owner badge on the published listing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A published listing shows « Propriété auditée » in green when the owner's identity and land title were verified before publication, in yellow when only the agent's on-site audit exists, with the audit note listing what was checked — and the site copy stops saying Ndopify ignores the property's documents.

**Architecture:** `ndopify-dto` gains `Listing.ownershipVerified`; `ndopify-server` stores it on `T_LISTING` (`V22_0`) and freezes it in the publication gate's save (`ListingService.publish(listing)`, shared by `POST /{id}/publish` and `ListingJobs.publish`'s first pass), then maps it in `ListingMapper` — `ListingSummary` and the search are untouched. `ndopify-web` carries it on `ListingPage` and renders the approved prototype's badge and note list in `fragments/listing-body.html`, plus three site-copy fixes.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, MySQL + Flyway (schema migrations, SQL fixtures), Thymeleaf, Jsoup template tests, `kotlin.test`, `mockito-kotlin`, `TestRestTemplate`.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/300 — the enriched story is the design (its *Enriched (groom, 2026-11-27)* section). Approved prototype: `modules/ndopify-web/src/main/html/v2/` (`annonce.html`, `proposition.html`, `acheter.html`, `vendre.html`, `README.md`). #297 supplies the verdict, #299 the gate.

## Global Constraints

- UI text is French, exactly the strings below; code, comments, logs and docs are English.
- The page reads without JavaScript: both states are **server-rendered** from `ListingPage`; no `listing.js` change, no prototype `?owner=` / `?audit=` switch in production.
- No new query: the badge and the note read the listing row that `GET /v1/listings/{id}` already loads. `ListingSummary`, `SearchListingResponse` and the search's predicates/ordering gain nothing.
- The frozen fact is written only by the publication gate; never a read-time verdict, never a backfill. Absent = `false`.
- Never modify an existing migration: add `V22_0__listing-ownership-verified.sql` after `V21_0__email-address.sql`.
- `ndopify-dto` is a released artifact: the branch ends with `<ndopify-dto.version>` on the released version, never a `-SNAPSHOT`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. `mvn validate` (ktlint) and the JaCoCo gates must pass.
- `src/main/html/` is the design record: never served, never deleted, never referenced by production markup.
- The exact badge label is « Propriété auditée » for a rental, a villa, a building and a SOLD page — never « Logement audité » nor « Bien audité » any more. The exact limit is « Ndopify ne consulte pas le registre foncier. ».

## Review Focus

1. **A legacy listing is not "un-audited".** `ownershipVerified = false` with an audit date renders the **warning** state (yellow badge, yellow note, the two owner items worded as not verified). Only a null `verifiedAt` hides the badge and the note. Getting this wrong un-badges every listing published before #299.
2. **The freeze is in the gate, not the reader.** A `PUBLISHING` listing at deploy, or a listing whose documents change after publication, keeps what the gate wrote; no `OwnershipVerdictService` call on any read path.
3. **A refused publication writes nothing.** The 409 `LISTING_OWNERSHIP_NOT_VERIFIED` path must leave the row untouched (`ownershipVerified` stays `false`, status unchanged).
4. **The note is shared with the match page.** Its copy must never claim a land-registry check, and the buyer/seller FAQs must keep their three limits; the match page keeps its pre-existing badge with no ownership suffix and gets no *new* badge.
5. **The search is untouched.** A summary JSON carrying `ownershipVerified` — or a join added for it — is out of scope and must fail the test that pins it.

---

### Task 1: The DTO field, released

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/Listing.kt`
- Modify: `pom.xml:52` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `Listing.ownershipVerified: Boolean = false` — read by `ListingMapper` (Task 3) and `ListingPages` (Task 4).

- [ ] **Step 1: Add the field**

In `Listing`, right after `verifiedAt`:

```kotlin
    /** True when the owner's identity and land title were verified before the listing was published (#297, #299). */
    val ownershipVerified: Boolean = false,
```

- [ ] **Step 2: Build and install the DTO locally**

```bash
cd modules/ndopify-dto && mvn -q install
```

Expected: BUILD SUCCESS. (`ndopify-dto` has no tests: the field is exercised by Tasks 2–5.)

- [ ] **Step 3: Point the modules at the DTO snapshot while developing**

In the root `pom.xml`, set `<ndopify-dto.version>0.0.114-SNAPSHOT</ndopify-dto.version>` (the DTO module's current version).

- [ ] **Step 4: Release the DTO**

Run the developer agent's shared-module release procedure for `ndopify-dto` (the profile's `shared_modules`): push the DTO change, wait for `ndopify-dto-main.yml` to release `0.0.114`, then set `<ndopify-dto.version>0.0.114</ndopify-dto.version>` and commit it as `fix(deps): update ndopify-dto to 0.0.114`. The branch must not end on a `-SNAPSHOT`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/Listing.kt pom.xml
git commit -m "feat(dto): expose the listing's ownership verification" -m "Refs #300"
```

---

### Task 2: Freeze the fact at the publication gate

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V22_0__listing-ownership-verified.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingEntity.kt:113` (after `verifiedAt`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt:344-365` (`publish(listing)`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **listing** section's **Publish** paragraph)

**Interfaces:**
- Consumes: `ListingService.publish(listing: ListingEntity): ListingEntity` and `OwnershipVerdictService.verify(mandate)` (already there, #299).
- Produces: `ListingEntity.ownershipVerified: Boolean = false` — mapped by Task 3; the column `T_LISTING.ownership_verified` for the fixtures.

- [ ] **Step 1: Write the failing endpoint tests**

In `PublishListingEndpointTest`, add two tests (the fixtures already hold the rows: 100 publishes, 103 is blocked by the owner verdict):

```kotlin
    @Test
    fun `publish freezes the ownership verification`() {
        assertEquals(HttpStatus.OK, publish(100L).statusCode)

        assertTrue(dao.findById(100L).get().ownershipVerified)
    }

    @Test
    fun `a refused publication freezes nothing`() {
        assertEquals(HttpStatus.CONFLICT, publish(103L).statusCode)

        assertFalse(dao.findById(103L).get().ownershipVerified)
    }
```

- [ ] **Step 2: Write the failing job test**

In `PublishVerifiedListingsJobTest`, after `jobs.publish()` in `publishes only the listing whose owner is verified`, pin the frozen column (the test uses `JdbcTemplate` because the job runs per tenant):

```kotlin
        assertEquals(1, jdbc.queryForObject("SELECT ownership_verified FROM T_LISTING WHERE id = 100", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT ownership_verified FROM T_LISTING WHERE id = 101", Int::class.java))
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest='PublishListingEndpointTest,PublishVerifiedListingsJobTest'
```

Expected: FAIL to compile — `ownershipVerified` is unresolved on `ListingEntity`.

- [ ] **Step 4: Add the entity field and the migration**

In `ListingEntity`, after `verifiedAt`:

```kotlin
    /** The owner's identity and title were verified when the gate let the listing through (#297, #299); frozen, never re-read. */
    val ownershipVerified: Boolean = false,
```

`V22_0__listing-ownership-verified.sql`:

```sql
-- #300: the owner-verification fact, frozen when the publication gate saves the listing (#297/#299). Every existing
-- row reads false: a listing published before the gate has no frozen fact and shows the warning state, never a
-- backfill, never un-badged.
ALTER TABLE T_LISTING ADD COLUMN ownership_verified BOOLEAN NOT NULL DEFAULT false;
```

- [ ] **Step 5: Freeze it in the gate's save**

In `ListingService.publish(listing: ListingEntity)`, inside the existing `dao.save(listing.copy(...))`, add:

```kotlin
                ownershipVerified = true, // frozen from this gate's VERIFIED verdict; never re-derived at read time
```

Keep the copy's other fields (`status = PUBLISHING`, `retries = 0`, `modifiedAt`) as they are. `ListingPublisherService` (the `PUBLISHING` second pass) is not touched.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest='PublishListingEndpointTest,PublishVerifiedListingsJobTest'
```

Expected: PASS.

- [ ] **Step 7: Document it**

In `modules/ndopify-server/CLAUDE.md`, in the **listing** section's **Publish** paragraph, after « `retries` reset. », add:

> The same save freezes `ownershipVerified` (`T_LISTING.ownership_verified`, `V22_0`) from the verdict the gate just
> read: the public page reads that fact, never the documents. The `PUBLISHING` second pass writes nothing there, so a
> listing published before #299, or already `PUBLISHING` at deploy, reads `false` (no backfill, no un-badging).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V22_0__listing-ownership-verified.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(listing): freeze the owner verification at publication" -m "Refs #300"
```

---

### Task 3: Serve the fact, without touching the search

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapper.kt:66-80` (`toListing`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapperTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/GetListingEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/listing/GetListingEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt`

**Interfaces:**
- Consumes: `ListingEntity.ownershipVerified` (Task 2), `Listing.ownershipVerified` (Task 1).
- Produces: `ListingMapper.toListing` copies the fact; `GET /v1/listings/{id}` answers `ownershipVerified`; `ListingSummary` keeps no such field.

- [ ] **Step 1: Write the failing mapper test**

In `ListingMapperTest`:

```kotlin
    @Test
    fun `toListing maps the ownership verification both ways`() {
        val mapper = ListingMapper(ImageServiceLocal())

        assertTrue(mapper.toListing(ListingEntity(ownershipVerified = true), emptyList()).ownershipVerified)
        assertFalse(mapper.toListing(ListingEntity(), emptyList()).ownershipVerified)
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingMapperTest
```

Expected: FAIL — `Listing.ownershipVerified` is never mapped, both assertions read `false`.

- [ ] **Step 3: Map it**

In `ListingMapper.toListing`, next to `verifiedAt = entity.verifiedAt`:

```kotlin
            ownershipVerified = entity.ownershipVerified,
```

Leave `toListingSummary` alone: `ListingSummary` gains nothing.

- [ ] **Step 4: Run it to verify it passes**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingMapperTest
```

Expected: PASS.

- [ ] **Step 5: Pin the read endpoint, both states**

In `GetListingEndpoint.sql`, add `ownership_verified` to the `T_LISTING` column list (after `published_at`) and `true` for listing 100, `false` for 101, 102 and 900. In `GetListingEndpointTest.get()`, add `assertTrue(listing.ownershipVerified)`, and add:

```kotlin
    @Test
    fun `a listing published before the gate has no frozen fact`() {
        val listing = rest.getForEntity("/v1/listings/102", GetListingResponse::class.java).body!!.listing

        assertFalse(listing.ownershipVerified)
    }
```

- [ ] **Step 6: Pin the search as untouched**

In `SearchListingEndpointTest`:

```kotlin
    @Test
    fun `a summary carries no ownership flag`() {
        val body = rest.getForEntity("/v1/listings?mandate-id=1", String::class.java).body!!

        assertFalse(body.contains("ownershipVerified"), body)
        assertEquals(listOf(100L), search("mandate-id=1"))
    }
```

(Add the `assertFalse` import; the test asserts both that the search's items are unchanged and that the field never leaks into a summary.)

- [ ] **Step 7: Run the tests**

```bash
cd modules/ndopify-server && mvn test -Dtest='GetListingEndpointTest,SearchListingEndpointTest,ListingMapperTest'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapper.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapperTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/GetListingEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/listing/GetListingEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt
git commit -m "feat(listing): serve the ownership verification on the listing" -m "Refs #300"
```

---

### Task 4: Carry the fact on the page model

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt` (`verifiedAt`)
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt:122` (the `ListingPage(...)` construction)
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`

**Interfaces:**
- Consumes: `Listing.ownershipVerified` (Task 1).
- Produces: `ListingPage.ownershipVerified: Boolean` — read by `fragments/listing-body.html` in Task 5; `verifiedAt: String?` stays the audit date, and `verifiedAt != null` is what makes the badge and the note exist at all.

- [ ] **Step 1: Write the failing test**

In `ListingPagesTest`:

```kotlin
    @Test
    fun `the page carries the owner verification`() {
        assertFalse(page().ownershipVerified)
        assertTrue(page(listing = listing.copy(ownershipVerified = true)).ownershipVerified)
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest
```

Expected: FAIL — `ownershipVerified` unresolved on `ListingPage`.

- [ ] **Step 3: Add the field and set it**

In `ListingPage`, right after `verifiedAt`:

```kotlin
    /** True when the owner's identity and title were verified at publication; false with a [verifiedAt] is the warning state. */
    val ownershipVerified: Boolean,
```

In `ListingPages.of`, next to `verifiedAt = listing.verifiedAt?.let { formatter.date(it) }`:

```kotlin
            ownershipVerified = listing.ownershipVerified,
```

- [ ] **Step 4: Run it to verify it passes**

```bash
cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest
```

Expected: PASS (every other `ListingPage` is built by `ListingPages.of`, `MatchPages` included).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt \
        modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt
git commit -m "feat(web): carry the owner verification on the listing page" -m "Refs #300"
```

---

### Task 5: The badge and the audit note

This task **ports** the approved prototype (`modules/ndopify-web/src/main/html/v2/annonce.html` and `assets/css/annonce.css`) into the shared fragment and the listing stylesheet; it designs nothing. The prototype's `?owner=unverified` / `?audit=none` switches and its `styles.css` `[hidden]` guards are **not** ported: the server renders the state.

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html` (`head` and `content` fragments)
- Modify: `modules/ndopify-web/src/main/assets/css/listing.css:116-126` (the `.audit-note` block)
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`
- Modify: `modules/ndopify-web/CLAUDE.md` (the `ui/listing/` bullet)

**Interfaces:**
- Consumes: `ListingPage.ownershipVerified`, `ListingPage.verifiedAt`, `ListingPage.sale`.
- Produces: the rendered `span.badge` (« Propriété auditée ») and `div.audit-note > ul.audit-note__list`; no new class beyond `.audit-note--warning`, `.audit-note__list`, `.audit-note__item`, `.audit-note__limit`.

- [ ] **Step 1: Update the fixtures and write the failing template tests**

In `ListingTemplateTest`, set `ownershipVerified = true` on the `listing` fixture (so the sale and building copies inherit it) and pin its audit date so the note's lead is assertable, as `ListingPagesTest` does:

```kotlin
        verifiedAt = Date.from(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("Africa/Douala")).toInstant()),
        ownershipVerified = true,
```

Then replace the two assertions of `a sale listing page speaks of a bien` that read the old badge and the old sentence:

```kotlin
        assertEquals("Propriété auditée", doc.selectFirst(".listing__badges .badge--success")?.text())
        assertFalse(doc.selectFirst(".audit-note")!!.text().contains("Ndopify vérifie l'agent, pas les documents du bien"))
```

and add three tests:

```kotlin
    @Test
    fun `the audit note lists what was checked`() {
        val doc = render()

        assertEquals("Propriété auditée par Ndopify le 12 oct. 2026.", doc.selectFirst(".audit-note > div > p")?.text())
        assertEquals(
            listOf(
                "Un agent a visité le logement.",
                "Position GPS contrôlée sur place.",
                "Identité du propriétaire vérifiée.",
                "Titre de propriété vérifié.",
                "Ndopify ne consulte pas le registre foncier.",
            ),
            doc.select(".audit-note__list li").map { it.text() },
        )
        assertEquals(listOf("i-check", "i-check", "i-check", "i-check", "i-info"), icons(doc))
    }

    @Test
    fun `an owner verified at publication is the success state`() {
        val doc = render()

        assertEquals("Propriété auditée", doc.selectFirst(".listing__badges .badge--success")?.text())
        val note = doc.selectFirst(".audit-note")!!
        assertTrue(note.classNames().contains("audit-note"))
        assertFalse(note.classNames().contains("audit-note--warning"))
    }

    @Test
    fun `an owner not verified is the warning state, in words`() {
        val doc = render(page(listing = listing.copy(ownershipVerified = false)))

        val badge = doc.selectFirst(".listing__badges .badge--warning")!!
        assertEquals("Propriété auditée, propriétaire non vérifié", badge.text())
        val note = doc.selectFirst(".audit-note")!!
        assertTrue(note.classNames().contains("audit-note--warning"))
        assertEquals(
            listOf(
                "Un agent a visité le logement.",
                "Position GPS contrôlée sur place.",
                "Identité du propriétaire non vérifiée.",
                "Titre de propriété non vérifié.",
                "Ndopify ne consulte pas le registre foncier.",
            ),
            note.select(".audit-note__list li").map { it.text() },
        )
        assertEquals(listOf("i-check", "i-check", "i-alert", "i-alert", "i-info"), icons(doc))
    }

    @Test
    fun `a listing never audited has neither badge nor note`() {
        val doc = render(page(listing = listing.copy(verifiedAt = null)))

        assertTrue(doc.select(".listing__badges .badge--success, .listing__badges .badge--warning").isEmpty())
        assertTrue(doc.select(".audit-note").isEmpty())
    }
```

with the helper:

```kotlin
    private fun icons(doc: Document) =
        doc.select(".audit-note__list li svg use").map { it.attr("href").removePrefix("#") }
```

In `MatchTemplateTest`, add:

```kotlin
    @Test
    fun `the match page keeps its audited badge and note, without the ownership suffix`() {
        val doc = render(page(listing = listing.copy(verifiedAt = Date(), ownershipVerified = true)))

        assertEquals("Propriété auditée", doc.selectFirst(".listing__badges .badge--success")?.text())
        assertTrue(doc.selectFirst(".audit-note__list")!!.text().contains("Identité du propriétaire vérifiée."))
        assertEquals("Serge a visité le logement.", doc.selectFirst(".audit-note__list li")?.text())
    }
```

(Adjust the agent's first name to the test's fixture, and add the `java.util.Date` import if the file lacks it; the point is that the shared fragment names the agent there, and the `head` fragment carries no ownership suffix for a verified match.)

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-web && mvn test -Dtest='ListingTemplateTest,MatchTemplateTest'
```

Expected: FAIL — the badge still reads « Logement audité » / « Bien audité », the note has two paragraphs, no list.

- [ ] **Step 3: The `head` fragment**

In `templates/fragments/listing-body.html`, replace the existing `badge--success` span (the `verifiedAt` one, lines 18-21) with one:

```html
            <span class="badge" th:classappend="${listing.ownershipVerified} ? 'badge--success' : 'badge--warning'"
                  th:if="${listing.verifiedAt}">
                <svg aria-hidden="true" class="icon"><use href="#i-shield"/></svg>Propriété auditée<span
                    class="visually-hidden" th:unless="${listing.ownershipVerified}">, propriétaire non vérifié</span>
            </span>
```

- [ ] **Step 4: The `content` fragment**

Replace the existing `div.audit-note` block (lines 59-70, the sale/rental paragraphs inside it) with one:

```html
    <div class="audit-note" th:classappend="${listing.ownershipVerified} ? null : 'audit-note--warning'"
         th:if="${listing.verifiedAt}">
        <svg aria-hidden="true" class="icon icon--lg"><use href="#i-shield"/></svg>
        <div>
            <p><strong th:text="'Propriété auditée par Ndopify le ' + ${listing.verifiedAt} + '.'">Propriété auditée par
                Ndopify le 12 octobre 2026.</strong></p>
            <ul class="audit-note__list">
                <li class="audit-note__item">
                    <svg aria-hidden="true" class="icon"><use href="#i-check"/></svg>
                    <span th:text="${agent} != null ? ${agent} + (${listing.sale} ? ' a visité le bien.' : ' a visité le logement.')
                        : (${listing.sale} ? 'Un agent a visité le bien.' : 'Un agent a visité le logement.')">Un agent a
                        visité le logement.</span>
                </li>
                <li class="audit-note__item">
                    <svg aria-hidden="true" class="icon"><use href="#i-check"/></svg>
                    <span>Position GPS contrôlée sur place.</span>
                </li>
                <li class="audit-note__item">
                    <svg aria-hidden="true" class="icon"><use th:href="${listing.ownershipVerified} ? '#i-check' : '#i-alert'"/></svg>
                    <span th:text="${listing.ownershipVerified} ? 'Identité du propriétaire vérifiée.'
                        : 'Identité du propriétaire non vérifiée.'">Identité du propriétaire vérifiée.</span>
                </li>
                <li class="audit-note__item">
                    <svg aria-hidden="true" class="icon"><use th:href="${listing.ownershipVerified} ? '#i-check' : '#i-alert'"/></svg>
                    <span th:text="${listing.ownershipVerified} ? 'Titre de propriété vérifié.'
                        : 'Titre de propriété non vérifié.'">Titre de propriété vérifié.</span>
                </li>
                <li class="audit-note__item audit-note__limit">
                    <svg aria-hidden="true" class="icon"><use href="#i-info"/></svg>
                    <span>Ndopify ne consulte pas le registre foncier.</span>
                </li>
            </ul>
        </div>
    </div>
```

The label « Logement audité »/« Bien audité » and the old sentence « Ndopify vérifie l'agent, pas les documents du bien » disappear from this fragment; the `#i-alert` triangle replaces the prototype's `#i-warning` (the sprite has `i-alert`, not `i-warning`).

- [ ] **Step 5: The styles**

In `src/main/assets/css/listing.css`, after `.audit-note p + p` (keep both existing `p` rules — the lead is still a `p`), add, as in the approved prototype:

```css
/* #300: audited before the owner-verification gate: the whole block takes the warning tokens, like the badge. */
.audit-note--warning {
  background: var(--color-bg-warning);
  border-color: var(--color-border-warning);
  color: var(--color-text-warning);
}
/* #300: what was checked, one item per fact: one column on a phone, two from 640px (1024px included). */
.audit-note__list {
  list-style: none;
  margin: var(--space-md) 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-md) var(--space-xl);
  font-size: 0.875rem;
}
@media (min-width: 640px) {
  .audit-note__list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.audit-note__item { display: flex; align-items: flex-start; gap: var(--space-md); }
.audit-note__list .icon { margin-top: 0.2em; }
/* #300: the registry limit is a footnote, not a fact like the others: full width and set back. */
.audit-note__limit {
  grid-column: 1 / -1;
  font-size: 0.8125rem;
  opacity: 0.85;
}
```

`.badge--warning` already exists in this file — do not add it again.

- [ ] **Step 6: Run the template tests**

```bash
cd modules/ndopify-web && mvn test -Dtest='ListingTemplateTest,MatchTemplateTest'
```

Expected: PASS. Then the whole module: `mvn -q verify` → PASS (ktlint, tests, coverage).

- [ ] **Step 7: Document it**

In `modules/ndopify-web/CLAUDE.md`, in the `ui/listing/` bullet, after the SOLD/RENTED sentence, add:

> Its head badge « Propriété auditée » (`fragment/listing-body.html`, shared with `match.html`) is green when the owner's
> identity and title were verified at publication (`ListingPage.ownershipVerified`) and yellow when only the agent's
> audit exists, with a visually-hidden « propriétaire non vérifié » so the state never rests on colour; a listing with
> no audit date renders neither the badge nor the note. The note is the audit date followed by a list — the agent's
> visit, the GPS check, the owner's identity, the title, and the quiet « Ndopify ne consulte pas le registre foncier. » —
> one column below 640px, two equal columns above it.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html \
        modules/ndopify-web/src/main/assets/css/listing.css \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt \
        modules/ndopify-web/CLAUDE.md
git commit -m "feat(web): show the audited property and its owner check" -m "Refs #300"
```

---

### Task 6: The copy that denies the check

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/acheter.html:184-190` (FAQ 5)
- Modify: `modules/ndopify-web/src/main/resources/templates/owner/sell.html:224-230` (FAQ 5)
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/buyer/BuyerTemplateTest.kt`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/owner/SellTemplateTest.kt`

**Interfaces:**
- Consumes: nothing (copy only). The facts stated must match #297's rule: the ID (national ID or passport) and the title (land title or certificate of ownership) are read, name-matched to the owner, before publication.

- [ ] **Step 1: Write the failing tests**

In `BuyerTemplateTest`, in `the page promises only what holds`, replace `"Ndopify vérifie l'agent, pas les documents du bien"` with:

```kotlin
            "pièce d'identité du propriétaire",
            "vérifie que le titre est à son nom",
            "ne consulte pas le registre foncier",
            "services des domaines",
```

and add `"Ndopify vérifie l'agent, pas les documents du bien"` to the forbidden list below.

In `SellTemplateTest`, in `the page promises only what holds`, replace `"Ndopify ne les contrôle pas"` with:

```kotlin
            "lit votre pièce d'identité et votre titre de propriété",
            "vérifie que le titre est à votre nom",
            "ne consulte pas le registre foncier",
```

and keep a new forbidden entry `"Ndopify ne les contrôle pas"` next to `"titre foncier vérifié"` (the page states the limit, never a land-registry check).

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-web && mvn test -Dtest='BuyerTemplateTest,SellTemplateTest'
```

Expected: FAIL — the templates still carry the old sentences.

- [ ] **Step 3: Rewrite the buyer FAQ**

`templates/acheter.html`, the `faq-5` panel:

```html
                    <div class="faq-panel" hidden id="faq-5"><p>Avant de publier une annonce, Ndopify lit la pièce
                        d'identité du propriétaire et le titre de propriété, et vérifie que le titre est à son nom. Il
                        ne consulte pas le registre foncier : avant d'acheter, faites vérifier le titre auprès des
                        services des domaines et signez l'acte de vente chez un notaire de votre choix.</p></div>
```

- [ ] **Step 4: Rewrite the seller FAQ**

`templates/owner/sell.html`, the `faq-5` panel:

```html
                    <div class="faq-panel" hidden id="faq-5"><p>Avant de publier votre annonce, Ndopify lit votre
                        pièce d'identité et votre titre de propriété, et vérifie que le titre est à votre nom ; il ne
                        consulte pas le registre foncier. La vente, elle, se fait chez le notaire, entre vous et votre
                        acheteur. Ndopify vous met en relation avec un agent et des acheteurs ; il ne prend pas part à
                        l'acte de vente ni au paiement.</p></div>
```

- [ ] **Step 5: Run them to verify they pass**

```bash
cd modules/ndopify-web && mvn test -Dtest='BuyerTemplateTest,SellTemplateTest'
```

Expected: PASS.

- [ ] **Step 6: Verify the whole reactor**

```bash
cd modules/ndopify-web && mvn -q verify
cd ../ndopify-server && mvn -q verify     # needs the local MySQL of the endpoint tests
cd ../ndopify-dto && mvn -q install
```

Expected: three BUILD SUCCESS, ktlint and the coverage gates included. The branch ends on the released `<ndopify-dto.version>`.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/acheter.html \
        modules/ndopify-web/src/main/resources/templates/owner/sell.html \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/buyer/BuyerTemplateTest.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/owner/SellTemplateTest.kt
git commit -m "fix(web): state what Ndopify checks before publishing" -m "Refs #300"
```

---

## Self-review of this plan against the story

- **Freeze at publication** — Tasks 2 and 3 (entity, migration, gate, mapper, endpoint + job tests).
- **Public page** — Tasks 4 and 5 (page model, badge, note list, both states, no-JS, not colour-alone, closed pages keep the state).
- **Copy fix** — Task 5 (shared sale note) and Task 6 (buyer and seller FAQs).
- **Search untouched** — Task 3's `a summary carries no ownership flag`.
- **No new query** — the badge/note read the row `GET /v1/listings/{id}` already loads; no repository or predicate change.
- **Shared code** — Task 1 releases `ndopify-dto`; Tasks 2–6 build against the released version.
- **Out of scope respected** — no `ownershipVerifiedAt`, no cards/e-mails/Telegram/Partner Central change, no backfill, no un-badging, no buyer verification.
