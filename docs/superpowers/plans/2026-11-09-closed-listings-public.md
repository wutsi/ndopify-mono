# Sold/Rented Listings Remain Public Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a sold or rented listing publicly reachable in ndopify-web, marked "Vendu"/"Loué", with no price shown.

**Architecture:** ndopify-server opens its existing anonymous listing reads to `SOLD`/`RENTED`; ndopify-web derives one `closedNotice` field from the listing status and uses it to replace the type badge, show the notice, sanitize the meta description, drop the price terms and hide every price block.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, JUnit 5, kotlin.test, Maven.

**Spec:** `docs/superpowers/specs/2026-11-09-closed-listings-public-design.md`

## Global Constraints

- French UI copy: `SOLD` → "Vendu", `RENTED` → "Loué"; notices are "Ce bien a été vendu." / "Ce bien a été loué." (append " le <date>." when `closedAt` is set).
- No price may appear on a closed listing: no price card, no bottom bar, no conditions table, no price in the meta description.
- Public search keeps returning `PUBLISHED` listings only.
- `DRAFT`, `PENDING`, `PENDING_VERIFICATION`, `UNDER_VERIFICATION`, `VERIFIED`, `PUBLISHING`, `EXPIRED` and `CANCELLED` stay hidden from anonymous callers.
- Match and visit pages keep treating any non-`PUBLISHED` listing as `UNAVAILABLE`.

## Review Focus

- A closed listing with `closedAt = null` (legacy row or a closing job race) must still render, with a notice without a date.
- A `PUBLISHED` listing must keep `closedNotice = null` and render exactly as today.
- A generated `summary` that quotes the price must not leak into the closed page's meta description.
- The label comes from the status, not the listing type: `SOLD` is "Vendu" and `RENTED` is "Loué".
- Anonymous image reads of a closed listing must work too, still restricted to `VERIFIED` images.

---

### Task 1: Open anonymous listing reads to SOLD and RENTED

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingAccessService.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/listing/GetListingEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/GetListingEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md:260-263`

**Interfaces:**
- Consumes: `ListingStatus` from `com.wutsi.ndopify.listing.dto`.
- Produces: `ensureCanRead(listing: ListingEntity)` accepts `SOLD` and `RENTED` for anonymous callers; no signature change.

- [ ] **Step 1: Add SOLD and RENTED fixtures**

In `GetListingEndpoint.sql`, append two mandates to the `T_MANDATE` insert (after mandate 9):

```sql
    (4, 1, 10, 20, 'RESIDENTIAL', 'CLOSED', 'LISTING', 'SALE', 2220957, 80000000, 'NONE', 'XAF'),
    (5, 1, 10, 20, 'RESIDENTIAL', 'CLOSED', 'LISTING', 'RENTAL', 2370201, 150000, 'MONTHLY', 'XAF');
```

Append two listings to the `T_LISTING` insert (after listing 900; keep the existing column order):

```sql
    (103, 1, 0, 4, 20, 'RESIDENTIAL', 'SALE', 'SOLD', 2220957, null,
     null, null, null, null,
     'APARTMENT', null, 3, 1, 1, 1, null, 120, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     80000000, 'XAF', 'NONE', null, null, null, null,
     '2027-03-01', null, null, null, null,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (104, 1, 0, 5, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 111,
     null, null, null, null,
     'APARTMENT', null, 2, 1, 1, 1, null, 70, null, null,
     false, false, false, false,
     false, false, false, false,
     false, false, false,
     false, false, false,
     150000, 'XAF', 'MONTHLY', null, null, null, null,
     null, null, null, null, null,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', '2026-10-01 10:00:00');
```

Append two images for listing 103 to the `T_LISTING_IMAGE` insert:

```sql
    (1030, 1, 103, 'listings/103/images/1.png', 'https://localhost/listings/103/images/1.png', 'VERIFIED'),
    (1031, 1, 103, 'listings/103/images/2.png', 'https://localhost/listings/103/images/2.png', 'PENDING_VERIFICATION');
```

- [ ] **Step 2: Write the failing anonymous-read tests**

In `GetListingEndpointTest`, add the `SearchListingImageResponse` import and these tests after `public - not published, not found without a token`:

```kotlin
    @Test
    fun `public - sold`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/103", GetListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ListingStatus.SOLD, response.body!!.listing.status)
    }

    @Test
    fun `public - rented`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/104", GetListingResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ListingStatus.RENTED, response.body!!.listing.status)
    }

    @Test
    fun `public - sold images, verified only`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/103/images", SearchListingImageResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(1030L), response.body!!.images.map { it.id })
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetListingEndpointTest`
Expected: the three new tests FAIL with HTTP 404 / `LISTING_NOT_FOUND`.

- [ ] **Step 4: Allow SOLD and RENTED in `ensureCanRead`**

In `ListingAccessService`, replace the body of `ensureCanRead` and add a companion constant:

```kotlin
@Service
class ListingAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
) {
    companion object {
        val PUBLIC_STATUSES = setOf(ListingStatus.PUBLISHED, ListingStatus.SOLD, ListingStatus.RENTED)
    }

    fun ensureCanRead(listing: ListingEntity) {
        if (listing.status !in PUBLIC_STATUSES && !canReadAll(listing)) {
            throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        }
    }
```

Update the class KDoc to say the reads are public "for the PUBLISHED, SOLD and RENTED listings".

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetListingEndpointTest`
Expected: PASS, including the existing `public - not published, not found without a token` test for the DRAFT listing.

- [ ] **Step 6: Update the module documentation**

In `modules/ndopify-server/CLAUDE.md`, change "for PUBLISHED listings only (`ListingAccessService`)" to "for PUBLISHED, SOLD and RENTED listings (`ListingAccessService`)", and leave the search sentence saying a search is forced to PUBLISHED.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingAccessService.kt \
  modules/ndopify-server/src/test/resources/db/test/listing/GetListingEndpoint.sql \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/GetListingEndpointTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(listing): keep sold and rented listings publicly readable"
```

---

### Task 2: Derive the closed state in the listing page model

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`

**Interfaces:**
- Produces: `ListingPage.closedNotice: String?` (default `null`).
- Produces: for `SOLD`/`RENTED`, `ListingPages.of` sets `typeBadge` to "Vendu"/"Loué", `closedNotice` to the notice, `metaDescription` to `"$closedNotice $title"`, and `terms`/`asideTerms` to empty lists.

- [ ] **Step 1: Write the failing model tests**

Add to `ListingPagesTest`, after `full listing`:

```kotlin
    @Test
    fun `sold listing`() {
        val page = page(
            listing.copy(
                status = ListingStatus.SOLD,
                listingType = ListingType.SALE,
                closedAt = Date.from(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("Africa/Douala")).toInstant()),
            ),
        )

        assertEquals("Vendu", page.typeBadge)
        assertEquals("Ce bien a été vendu le 12 oct. 2026.", page.closedNotice)
        assertEquals(
            "Ce bien a été vendu le 12 oct. 2026. Appartement meublé 2 chambres à Bonapriso",
            page.metaDescription,
        )
        assertEquals(emptyList(), page.terms)
        assertEquals(emptyList(), page.asideTerms)
    }

    @Test
    fun `rented listing`() {
        val page = page(
            listing.copy(
                status = ListingStatus.RENTED,
                closedAt = Date.from(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("Africa/Douala")).toInstant()),
            ),
        )

        assertEquals("Loué", page.typeBadge)
        assertEquals("Ce bien a été loué le 12 oct. 2026.", page.closedNotice)
    }

    @Test
    fun `closed listing without a closing date`() {
        val page = page(listing.copy(status = ListingStatus.SOLD, closedAt = null))

        assertEquals("Ce bien a été vendu.", page.closedNotice)
    }

    @Test
    fun `published listing has no closed notice`() {
        assertNull(page().closedNotice)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest`
Expected: compilation FAILS because `ListingPage.closedNotice` does not exist.

- [ ] **Step 3: Add `closedNotice` to `ListingPage`**

In `ListingPage`, add the field at the end of the constructor with a default:

```kotlin
    val visitTimes: List<Pair<String, String>>,
    /** "Vendu" / "Loué" notice for a closed listing; null while it is on the market. */
    val closedNotice: String? = null,
```

- [ ] **Step 4: Build the closed state in `ListingPages.of`**

At the start of `ListingPages.of`, after `title`, compute:

```kotlin
        val closedNotice = closedNotice(listing, formatter)
        val closed = closedNotice != null
```

Then use it in the constructor:

```kotlin
            metaDescription = closedNotice?.let { "$it $title" } ?: (listing.summary?.ifBlank { null } ?: title),
            typeBadge = when (listing.status) {
                ListingStatus.SOLD -> "Vendu"
                ListingStatus.RENTED -> "Loué"
                else -> if (listing.listingType == ListingType.SALE) "À vendre" else "À louer"
            },
```

Replace the `terms =` and `asideTerms =` blocks with:

```kotlin
            terms = if (closed) {
                emptyList()
            } else {
                listOfNotNull(
                    "Loyer" to price,
                    listing.securityDeposit?.let { "Caution" to months(it, listing, formatter) },
                    listing.advanceRent?.let { "Avance sur loyer" to months(it, listing, formatter) },
                    listing.leaseTerm?.let { "Durée du bail" to "$it mois" },
                    listing.noticePeriod?.let { "Préavis" to "$it mois" },
                    "Disponible" to (available?.let { "À partir du $it" } ?: "Immédiatement"),
                )
            },
            asideTerms = if (closed) {
                emptyList()
            } else {
                listOfNotNull(
                    listing.securityDeposit?.let { "Caution : $it mois" },
                    listing.advanceRent?.let { "Avance : $it mois" },
                    available?.let { "Disponible le $it" } ?: "Disponible immédiatement",
                )
            },
```

Add `closedNotice = closedNotice` to the `ListingPage(...)` call, and add the helper:

```kotlin
    private fun closedNotice(listing: Listing, formatter: TenantFormatter): String? {
        val label = when (listing.status) {
            ListingStatus.SOLD -> "vendu"
            ListingStatus.RENTED -> "loué"
            else -> return null
        }
        val date = listing.closedAt?.let { formatter.date(it) }
        return "Ce bien a été $label" + (date?.let { " le $it" } ?: "") + "."
    }
```

Add the `ListingStatus` import.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest`
Expected: PASS, including the existing `full listing` test.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt
git commit -m "feat(web): mark sold and rented listings in the page model"
```

---

### Task 3: Render the mention and hide every price

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html`
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/listing.css`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Modify: `modules/ndopify-web/CLAUDE.md:5`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt:16-23`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdListingClient.kt:10-13`

**Interfaces:**
- Consumes: `ListingPage.closedNotice` from Task 2.
- Produces: a rendered `.badge--warning` type badge, a `[data-closed-notice]` notice, no `.price-card`, no `.cta-bar`, no `.terms-table` and no `.modal__price` for a closed page.

- [ ] **Step 1: Write the failing template test**

Add to `ListingTemplateTest` (add the `ZoneId` import):

```kotlin
    @Test
    fun `closed listing`() {
        val doc = render(
            page(
                listing = listing.copy(
                    status = ListingStatus.RENTED,
                    closedAt = Date.from(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.of("Africa/Douala")).toInstant()),
                ),
            ),
        )

        assertEquals("Loué", doc.selectFirst(".listing__badges .badge")?.text())
        assertTrue(doc.selectFirst(".listing__badges .badge")!!.hasClass("badge--warning"))
        assertEquals("Ce bien a été loué le 12 oct. 2026.", doc.selectFirst("[data-closed-notice]")?.text())
        assertNull(doc.selectFirst(".price-card"))
        assertNull(doc.selectFirst(".cta-bar"))
        assertNull(doc.selectFirst(".terms-table"))
        assertNull(doc.selectFirst(".modal__price"))
    }

    @Test
    fun `closed listing without a closing date`() {
        val doc = render(page(listing = listing.copy(status = ListingStatus.SOLD, closedAt = null)))

        assertEquals("Vendu", doc.selectFirst(".listing__badges .badge")?.text())
        assertEquals("Ce bien a été vendu.", doc.selectFirst("[data-closed-notice]")?.text())
    }
```

Add the `ListingStatus` import.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest`
Expected: FAIL — the type badge still says "À louer" and the price blocks are rendered.

- [ ] **Step 3: Render the badge and notice in the shared head**

In `fragments/listing-body.html`, replace the type badge with:

```html
            <span class="badge" th:classappend="${listing.closedNotice} ? 'badge--warning' : 'badge--info'"
                  th:text="${listing.typeBadge}">À louer</span>
```

After the `listing__location` paragraph, add:

```html
    <p class="listing__closed" data-closed-notice th:if="${listing.closedNotice}" th:text="${listing.closedNotice}">
        Ce bien a été vendu.
    </p>
```

Add `th:unless="${#lists.isEmpty(listing.terms)}"` to the conditions `<section aria-labelledby="terms-heading" class="listing__section">`.

- [ ] **Step 4: Hide the price blocks in `listing.html`**

Add `th:if="${page.closedNotice == null}"` to the `<aside aria-label="Prix et contact" class="listing__aside">`, to the `<div class="cta-bar">`, and to the visit popup's `<span class="modal__price">`.

- [ ] **Step 5: Style the warning badge and notice**

In `listing.css`, after `.badge--success`, add:

```css
.badge--warning { background: var(--color-bg-warning); color: var(--color-text-warning); border-color: var(--color-border-warning); }
```

After `.listing__location`, add:

```css
.listing__closed {
  display: inline-flex;
  margin: var(--space-lg) 0 0;
  padding: var(--space-md) var(--space-lg);
  border: 1px solid var(--color-border-warning);
  border-radius: var(--radius-full);
  background: var(--color-bg-warning);
  color: var(--color-text-warning);
  font-weight: 600;
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest`
Expected: PASS, including the existing `listing content` test that still finds `.price-card__price` for a published listing.

- [ ] **Step 7: Update the stale web comments and documentation**

In `modules/ndopify-web/CLAUDE.md`, change "a PUBLISHED listing's `/v1/listings/{id}`" to "a PUBLISHED, SOLD or RENTED listing's `/v1/listings/{id}`". In `ListingController`'s KDoc and `NdListingClient`'s KDoc, replace "only returns a PUBLISHED listing" with "only returns a PUBLISHED, SOLD or RENTED listing".

- [ ] **Step 8: Run the whole web module test suite**

Run: `cd modules/ndopify-web && mvn test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html \
  modules/ndopify-web/src/main/resources/templates/listing.html \
  modules/ndopify-web/src/main/resources/public/assets/css/listing.css \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt \
  modules/ndopify-web/CLAUDE.md \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdListingClient.kt
git commit -m "feat(web): show sold and rented listings without a price"
```
