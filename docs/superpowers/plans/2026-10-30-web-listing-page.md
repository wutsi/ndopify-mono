# Public Listing Page (ndopify-web) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve a PUBLISHED listing's public page at its `Listing.url` (`/<city>/<neighborhood>/listing/<id>/<slug>`) on
ndopify-web, from the prototype `src/main/html/annonce.html`, with live data from ndopify-server.

**Architecture:** A `ListingController` reads the listing, its photos and its locations from ndopify-server
(`GET /v1/listings/{id}`, `/v1/listings/{id}/images`, `/v1/locations?id=…`, all public with `X-Tenant-ID`), builds a
`ListingPage` view with `ListingPages.of(...)` (pure, all formatting through `TenantFormatter`) and renders
`templates/listing.html`. The "Demander une visite" popup and the "Poser une question" drawer are carried over **as
prototype**: same markup and the prototype's script, served as `listing.js` (which fakes the confirmation and the replies client-side);
no lead or chat endpoint, no server call — they are implemented later.

**Tech Stack:** Kotlin, Spring Boot (MVC, Security, `RestClient`), Thymeleaf, jsoup + MockMvc + `MockRestServiceServer`
in tests, mockito-kotlin, kotlin.test.

**Spec:** the prototype `modules/ndopify-web/src/main/html/annonce.html` (+ `assets/js/annonce.js`,
`assets/css/annonce.css`), and the request: "integrate the listing page into the ndopify-web BFF; keep the chat and
request-visit features as prototype for now".

## Global Constraints

- Never modify or delete anything under `modules/ndopify-web/src/main/html/` (prototype); copy from it.
- ndopify-web must not depend on ndopify-server nor another web app at build time; copied infrastructure keeps the
  source's class names and signatures (`TenantFormatter`, `TenantIdClientHttpRequestInterceptor`,
  `LoggingClientHttpRequestInterceptor`, `HttpClientErrorHandler`).
- Dates and amounts go through `TenantFormatter` — never a hard-coded pattern or "FCFA".
- `Listing.agentRemarks` is never shown on the page nor put in the model. ndopify-server's public read returns it:
  accepted, the UI ignores it (no server change).
- The page works without JavaScript (gallery = links, popups open with `:target`); scripts only enhance.
- UI text in French, code/comments in English; KDoc says what a class is for and its non-obvious rules.
- Tests: kotlin.test assertions, mockito-kotlin mocks. Coverage gate: `jacoco.threshold.line` 0.97,
  `jacoco.threshold.class` 0.80 (`modules/ndopify-web/pom.xml`).
- Work on a branch (`git checkout -b web-listing-page`); `main` has the user's uncommitted prototype changes — commit
  only the files each task names.

## Review Focus

1. **Stale or wrong slug** (`/douala/listing/123/old-title`, the title regenerated, a hand-typed link) → 301 to the
   listing's current `url`, never a second copy of the page. Test in Task 4.
2. **Listing no longer public** (RENTED/EXPIRED, a draft, another tenant's id) → ndopify-server's 404 becomes the 404
   page, not a 500 — from the listing *or* its images call. Test in Task 4.
3. **Published without generated content** (the publisher's fallback: no title/summary/description) → a title built
   from the property type and place, no empty "À propos" section. Test in Task 3.
4. **No verified photo / no hero** → no gallery, no "Voir les 0 photos", `og:image` falls back to the tenant's logo.
   Tests in Tasks 3 and 5.
5. **Sparse listing** (no neighborhood, no landmark, null rooms/deposit/lease, `availableFrom` past or null) → those
   facts and rows are left out, "Disponible : Immédiatement". Test in Task 3.

---

### Task 1: ndopify-web — `TenantFormatter`

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/service/TenantFormatter.kt` — copy of
  `modules/ndopify-partner-central/.../npc/service/TenantFormatter.kt` (package changed), plus one method.
- Create: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/service/TenantFormatterTest.kt` — copy of npc's
  `TenantFormatterTest.kt` (package changed; inline any `TenantFixtures` it uses), plus the tests below.

**Interfaces:**
- Produces: `class TenantFormatter(tenant: Tenant)` with npc's `date(Date)`, `date(LocalDate)`, `today(Clock)`,
  `currency()`, `money(Long, String)`, `compactMoney(Long, String)`, `count(Long)`, and new
  `fun date(date: LocalDate, pattern: String): String` — the pattern in the tenant's locale, for the visit days.

- [ ] **Step 1: Write the failing test** `date with a pattern in the tenant's locale`:
  `assertEquals("sam.", TenantFormatter(Tenant(locales = listOf("fr"))).date(LocalDate.of(2026, 10, 31), "EEE"))`
  and `"oct."` for `"MMM"`.
- [ ] **Step 2: Run** `cd modules/ndopify-web && mvn test -Dtest=TenantFormatterTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** the copy + `date(date, pattern) = date.format(DateTimeFormatter.ofPattern(pattern, locale))`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): TenantFormatter`

---

### Task 2: ndopify-web — REST clients for listings and locations

**Files:**
- Create: `web/client/TenantIdClientHttpRequestInterceptor.kt`, `web/client/LoggingClientHttpRequestInterceptor.kt` —
  copies of npc's (package changed).
- Modify: `web/config/RestConfiguration.kt` — add npc's `loggingRestClientCustomizer()` and
  `tenantIdRestClientCustomizer(tenantContext)` beans (no authorization one: no sign-in here). `NdTenantClient` stays on
  the plain `RestTemplate`: it must not get the tenant interceptor (it's what resolves the tenant).
- Create: `web/client/ndopify/NdListingClient.kt`, `web/client/ndopify/NdLocationClient.kt`
- Test: copies of npc's `TenantIdClientHttpRequestInterceptorTest`, `LoggingClientHttpRequestInterceptorTest`; new
  `NdListingClientTest`, `NdLocationClientTest` (`MockRestServiceServer.bindTo(RestClient.builder())`, as npc's).

(`web/` = `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/`, tests mirror it under `src/test`.)

**Interfaces:**
- Produces:
  - `NdListingClient(baseUrl: String, builder: RestClient.Builder)`: `fun get(id: Long): Listing`
    (`GET /v1/listings/{id}`), `fun images(id: Long): List<ListingImage>` (`GET /v1/listings/{id}/images`). KDoc: no
    token, so ndopify-server only returns a PUBLISHED listing and its VERIFIED images; 404 otherwise
    (`HttpClientErrorException.NotFound` propagates).
  - `NdLocationClient(baseUrl, builder)`: `fun search(ids: List<Long>): List<Location>`
    (`GET /v1/locations?id=…&id=…&limit=<ids.size>`).

- [ ] **Step 1: Write the failing tests**: `get` returns the `listing` of the body; `images` returns the `images`;
  `get` of an unknown listing → `assertFailsWith<HttpClientErrorException.NotFound>`; `search` sends
  `/v1/locations?id=2370201&id=5&limit=2` and returns the `locations`.
- [ ] **Step 2: Run** `mvn test -Dtest='Nd*ClientTest,*InterceptorTest'` — Expected: compile FAIL.
- [ ] **Step 3: Implement** the clients and copies.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): listing and location clients`

---

### Task 3: ndopify-web — `ListingPage` view

All the page's text, built once from the DTOs; the template only prints it.

**Files:**
- Create: `web/ui/listing/ListingPage.kt` (data classes), `web/ui/listing/ListingPages.kt` (builder + labels)
- Test: `web/ui/listing/ListingPagesTest.kt`

**Interfaces:**
- Consumes: `TenantFormatter` (Task 1); `Listing`, `ListingImage`, `Location`, `Tenant` DTOs.
- Produces:

```kotlin
data class ListingPage(
    val id: Long,
    val title: String,               // h1 and <title>
    val metaDescription: String,     // listing.summary ?: title
    val canonicalUrl: String?,       // publicPortalUrl (trimEnd '/') + listing.url; null without either
    val typeBadge: String,           // "À louer" (RENTAL) / "À vendre" (SALE)
    val place: String,               // "Bonapriso, Douala", or "Douala" without neighborhood
    val landmark: String?,           // "près de la boulangerie Zepol"
    val verifiedAt: String?,         // formatter.date(listing.verifiedAt)
    val photos: List<Photo>,         // hero first, then the others in server order
    val keyFacts: List<Fact>,
    val description: List<String>,   // paragraphs; empty without description
    val specs: List<Pair<String, String>>,
    val amenities: List<AmenityGroup>,
    val price: String,               // "175K FCFA / mois"
    val terms: List<Pair<String, String>>,
    val asideTerms: List<String>,    // the price card's "Caution : 2 mois", "Avance : 3 mois", "Disponible …"
    val schemaType: String,          // JSON-LD @type
    val area: Int?, val rooms: Int?, val bathrooms: Int?,
    val latitude: Double?, val longitude: Double?, val city: String?, val countryCode: String,
    val visitDays: List<VisitDay>,
    val visitTimes: List<Pair<String, String>>,  // "10:00" to "10h00", 10:00..21:00 every 30 min
)
data class Photo(val src: String, val alt: String)
data class Fact(val icon: String, val count: String?, val label: String)  // icon: "bed","bath","area","sofa","car"
data class AmenityGroup(val title: String, val items: List<String>)
data class VisitDay(val value: String, val dayName: String, val day: String, val month: String)

object ListingPages {
    fun of(listing: Listing, images: List<ListingImage>, locations: List<Location>, tenant: Tenant,
           formatter: TenantFormatter, today: LocalDate): ListingPage
}
```

Rules the tests pin (French copy exactly as the prototype):
- `title`: `listing.title`, else `"<type> à louer à <neighborhood ?: city>"` (`"Appartement à louer à Bonapriso"`;
  "à vendre" for SALE; `"Logement"` without type). Property-type and furniture labels: as npc's `MandateLabels`
  `PROPERTY_TYPE_LABELS`, copied.
- `photos`: hero = `heroImageId`'s image, else the first; `alt` = `title ?: description ?: page title`.
- `keyFacts` in order, each only when set: rooms `"chambre(s)"`, bathrooms `"salle(s) de bains"`, area
  `formatter.count(area)` + `"m²"`, furniture `"Meublé"`/`"Non meublé"` (no count), parkings `"parking(s)"`.
- `specs`, each only when set: Type de bien, Ameublement, Chambres, Salles de bains, Salons, Cuisines, Surface
  (`"95 m²"`), Étage (`"2e sur 3"` from `floorLevel`/`levels`, `"Rez-de-chaussée"` for 0), Parkings.
- `amenities`: groups Eau / Électricité / Accès / Sécurité with labels copied from npc's `ui/mandate/Amenity.kt`; only
  the true flags; a dedicated meter replaces the plain one (not both); a group with nothing is left out.
- `price`: `formatter.compactMoney(price, currencyCode)` + `" / mois"` (MONTHLY), `" / an"` (YEARLY), `" / semaine"`,
  `" / jour"`, nothing otherwise.
- `terms` (only when set): Loyer; Caution `"2 mois (350K FCFA)"` (the amount only for a MONTHLY price); Avance sur
  loyer, same; Durée du bail `"12 mois"`; Préavis `"1 mois"`; Disponible `"À partir du <formatter.date>"`, or
  `"Immédiatement"` when null or ≤ `today`. The prototype's "Commission Ndopify" row and the cash note stay static in
  the template.
- `description`: split on blank lines (then single newlines), trimmed, empties dropped.
- `schemaType`: APARTMENT/STUDIO → `Apartment`, HOUSE/VILLA/DUPLEX → `House`, ROOM → `Room`, else `Accommodation`.
- `visitDays`: `today + 1` … `today + 8`; `value` ISO, `dayName` `formatter.date(d, "EEE")`, `day` `d.dayOfMonth`,
  `month` `formatter.date(d, "MMM")`.

- [ ] **Step 1: Write the failing tests** in `ListingPagesTest` (a full listing fixture matching the prototype: 2
  rooms, 2 bathrooms, 95 m², furnished, 1 parking, 175 000 XAF monthly, deposit 2, advance 3, lease 12, notice 1,
  floor 2 of 3, Bonapriso/Douala, landmark "la boulangerie Zepol"):
  - `full listing` — `assertEquals("175K FCFA / mois", page.price)`,
    `assertEquals("Caution" to "2 mois (350K FCFA)", page.terms[1])`, key facts labels/counts, `"2e sur 3"`,
    `place == "Bonapriso, Douala"`, `canonicalUrl == "https://www.ndopify.cm/douala/bonapriso/listing/123/appartement"`
  - `hero photo first` — `heroImageId = 3` of images 1,2,3 → `photos.map { it.src }` starts with image 3's
  - `no photo` — `assertTrue(page.photos.isEmpty())`
  - `published without generated content` — title `"Appartement à louer à Bonapriso"`, `metaDescription == title`,
    `description` empty
  - `sparse listing` — no neighborhood → `place == "Douala"`, `landmark == null`; null rooms/deposit/lease → absent
    from `keyFacts`/`terms`; `availableFrom = today - 1` → `"Disponible" to "Immédiatement"`
  - `dedicated meter replaces the plain one` — `waterMeter = dedicatedWaterMeter = true` → Eau items
    `["Compteur d'eau dédié"]`; no security flag → no Sécurité group
  - `description paragraphs` — `"A\n\nB\nC"` → `["A", "B", "C"]`
  - `visit days` — today 2026-10-30 → 8 days, first `VisitDay("2026-10-31", "sam.", "31", "oct.")`
  - `never the agent's remarks` — `agentRemarks = "Clé chez le gardien"` →
    `assertFalse(page.toString().contains("gardien"))`
- [ ] **Step 2: Run** `mvn test -Dtest=ListingPagesTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** `ListingPage.kt` and `ListingPages.of(...)` per the rules above.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): listing page view`

---

### Task 4: ndopify-web — `ListingController`, 404 and security

**Files:**
- Create: `web/ui/listing/ListingController.kt`
- Create: `web/ui/layout/HttpClientErrorHandler.kt` — npc's, reduced to its 403/404 handler (no sign-in here)
- Create: `modules/ndopify-web/src/main/resources/templates/error/404.html` — "Page introuvable", the site's header
  and footer with Ndopify's own logo (`@{/assets/images/logo.png}`), `noindex`, a link to `/`; no `tenant` (Spring
  Boot's error view is rendered outside the page that failed)
- Modify: `web/config/SecurityConfiguration.kt` — `.requestMatchers(HttpMethod.GET, "/*/listing/*", "/*/listing/*/*",
  "/*/*/listing/*", "/*/*/listing/*/*").permitAll()` (GET only: the prototype forms' POST stays denied)
- Test: `web/ui/listing/ListingControllerTest.kt`, `web/ui/layout/HttpClientErrorHandlerTest.kt`,
  `web/config/SecurityConfigurationTest.kt` (only if a test of the matchers is needed to keep coverage; else covered by
  the controller test)

**Interfaces:**
- Consumes: `NdListingClient.get/images`, `NdLocationClient.search` (Task 2), `ListingPages.of` (Task 3),
  `TenantFormatter` (Task 1), `Clock` (`ClockConfiguration`).
- Produces: `ListingController.VIEW = "listing"`; model attribute `page: ListingPage` (plus `tenant` from
  `TenantAdvice`).

```kotlin
@GetMapping("/{city}/listing/{id}", "/{city}/listing/{id}/{slug}",
            "/{city}/{neighborhood}/listing/{id}", "/{city}/{neighborhood}/listing/{id}/{slug}")
fun index(@PathVariable id: Long, request: HttpServletRequest, model: Model): Any  // String view or RedirectView
```

Order: `get(id)` → if `listing.url != null && listing.url != request.requestURI` → `RedirectView(listing.url)` with
`MOVED_PERMANENTLY` (the query string isn't kept) → `images(id)` → `search(listOfNotNull(cityId, neighborhoodId))` →
`model["page"] = ListingPages.of(..., formatter.today(clock))`. A `NotFound` from any call reaches
`HttpClientErrorHandler` → `response.sendError(404)`.

- [ ] **Step 1: Write the failing tests** (`MockMvc` standalone with `TenantAdvice` and `HttpClientErrorHandler`, as
  `OwnerControllerTest`):
  - `listing page` — GET `/douala/bonapriso/listing/123/appartement` (= `listing.url`) → view `listing`, `page.id ==
    123`, `page.title` set
  - `redirects a stale slug to the listing's URL` — GET `/douala/bonapriso/listing/123/old` → status 301,
    `Location: /douala/bonapriso/listing/123/appartement`; `images` never called (`verify(listingClient, never())`)
  - `redirects a URL without slug` — GET `/douala/listing/123` → 301 to the url
  - `404 when the server doesn't publish it` — `get(123)` throws `HttpClientErrorException.NotFound` → status 404
  - `404 when its images are gone` — `images(123)` throws NotFound → 404
  - `HttpClientErrorHandlerTest`: a NotFound → `sendError(404)`, a Forbidden → `sendError(403)`
- [ ] **Step 2: Run** `mvn test -Dtest='ListingControllerTest,HttpClientErrorHandlerTest'` — Expected: compile FAIL.
- [ ] **Step 3: Implement** controller, handler, `error/404.html`, security matchers. Controller KDoc: the canonical
  URL rule, and that the visit and question forms are still the prototype's (no handler).
- [ ] **Step 4: Run** — Expected: PASS; `mvn test` whole module PASS.
- [ ] **Step 5: Commit** `feat(web): listing page controller`

---

### Task 5: ndopify-web — `listing.html` template and assets

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/listing.html` from `src/main/html/annonce.html`
- Create: `src/main/resources/public/assets/css/listing.css`, `public/assets/js/listing.js` — copies of the prototype's
  `annonce.css` / `annonce.js`, renamed: served files have English names (the prototype keeps its own); update the
  header comment of `listing.js` if it names the file
- Modify: `src/main/resources/public/assets/css/styles.css` — carry over the prototype `styles.css`'s changes since it
  was copied (`git diff` of the two: `--color-border-strong`, `--radius-control`, the form-control rules for
  `.field select/textarea`, …), keeping what's served-only
- Modify: `modules/ndopify-web/CLAUDE.md` — `ui/listing/` in *Code Organization*; ndopify-server calls are now
  `/v1/tenants`, `/v1/listings/{id}`(+`/images`), `/v1/locations` (with `X-Tenant-ID`); the security list includes the
  listing paths; the visit popup and the question drawer are still the prototype's
- Test: `web/ui/listing/ListingTemplateTest.kt` (real Thymeleaf, as `OwnerTemplateTest`)

**Interfaces:**
- Consumes: model `tenant`, `page` (Task 4), `ListingController.VIEW`.

Template rules:
- `<head>`: `<title>` = `page.title + ' | ' + tenant name`, description, canonical / `og:url` (when
  `page.canonicalUrl`), `og:image` = first photo's src else the tenant logo (as `owner.html`), icons as `owner.html`;
  JSON-LD (`th:inline="javascript"`) from `schemaType`, rooms, bathrooms, area, city, countryCode, geo — fields left out
  when null.
- Header/footer as `owner.html` (tenant logo, links `/`, `/owner`, `/agent`, no `.html` link).
- Gallery only when `page.photos` isn't empty: first 5, hero large, each `<a href=src data-photo=index>`; "Voir les N
  photos" only when N > 5. `#listing-photos` JSON = `page.photos` (inlined).
- Sections from the view, each left out when its list is empty (`th:unless="${#lists.isEmpty(...)}"`); audit note only
  with `verifiedAt`; description paragraphs with `th:text` (never `utext`).
- Visit popup and question drawer: the prototype's markup as is (action `#visit` / `#ask`, `method="post"`), with
  `visitDays` / `visitTimes` from the view and the subtitle's title/price from `page`; the chat's greeting says "ce
  logement" instead of the prototype's hard-coded place. A `<!--/* */-->` comment says they're still the prototype's:
  `listing.js` fakes the confirmation and the replies.
- Scripts: intl-tel-input CDN (as the prototype), `@{/assets/js/main.js}`, `@{/assets/js/listing.js}`; stylesheets `@{/assets/css/styles.css}`, `@{/assets/css/listing.css}`.

- [ ] **Step 1: Write the failing tests** in `ListingTemplateTest`:
  - `listing content` — `h1` text = title, `.price-card__price` contains `"175K"`, `.key-facts li` count 5,
    `.terms-table tr` rows, `link[rel=canonical]` href = `canonicalUrl`
  - `gallery and photo viewer` — 8 photos → 5 `.gallery__item`, `.gallery__all` text `"Voir les 8 photos"`,
    `#listing-photos` data contains the 8th src
  - `no gallery without photo` — `assertNull(page.selectFirst(".gallery"))`, `og:image` = tenant logo
  - `sections left out when empty` — no description/amenities/verifiedAt → no `#description-heading`,
    `#amenities-heading`, `.audit-note`
  - `description is escaped` — paragraph `"<b>x</b>"` → `selectFirst("#description-heading ~ p").text() == "<b>x</b>"`
  - `visit days and times` — 8 `input[name=visitDate]`, first value `"2026-10-31"`; 23 `#visit-time option[value!=]`
  - `no link to the prototype` — `select("[href$=.html], [src$=.html]")` empty
- [ ] **Step 2: Run** `mvn test -Dtest=ListingTemplateTest` — Expected: FAIL (template missing).
- [ ] **Step 3: Implement** the template, copy the assets, update `styles.css` and `CLAUDE.md`.
- [ ] **Step 4: Run** `cd modules/ndopify-web && mvn verify` — Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate).
  Then manual check: server + web running locally, a PUBLISHED listing's `http://localhost:8082<url>` renders, the
  photo viewer, the popup and the drawer open, and the page works with JavaScript disabled.
- [ ] **Step 5: Commit** `feat(web): listing page template`
