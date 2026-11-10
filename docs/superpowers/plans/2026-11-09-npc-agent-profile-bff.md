# NPC Agent Profile BFF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the approved agent profile at `GET /agent?id={id}` from Partner Central: the compact identity card, inline sectors, the agent's published listings with server-side pagination, and the “Agent introuvable” state.

**Architecture:** `AgentProfileController` (`ui/agent/`) fetches the viewed agent, one page of PUBLISHED listings, the location names and the visible listings' hero images — the independent reads in parallel — then builds an `AgentProfileView` for a Thymeleaf `agent.html` adapted from the prototype. The pager is server-rendered links, so the page works without JavaScript. Two small shared prerequisites are included: a month/year date pattern on `TenantFormatter`, and an exact `total` on the listing search for the “Page X sur Y” pager.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, JUnit 5, MockMvc, mockito-kotlin, `MockRestServiceServer`, the ndopify-server REST API.

**Spec:** `docs/superpowers/specs/2026-11-09-npc-agent-profile-prototype-design.md`
**Prototype:** `modules/ndopify-partner-central/src/main/html/agent.html`, `assets/css/agent.css`

## Global Constraints

- `/agent` requires a signed-in agent (`SecurityConfiguration` already requires authentication for every path outside its public list). `CurrentAgent.get()` null → `SignInRequiredException`.
- The viewer's header is `ProfileRules.header(viewer, model)`; the viewed agent comes only from the `id` request parameter. Never use the viewer's id for the viewed profile.
- Listings are PUBLIC only: `NdListingClient.searchByAgent` sends `agent-id={viewedId}` **and** `status=PUBLISHED`; the server also restricts another agent to PUBLISHED.
- The listing cards link to NPC's `GET /listings/{id}`. That page is currently the listing owner's checklist and locks another agent; the co-brokerage read-only listing page is **out of scope** (see Dependencies). The links are correct; the destination will unlock when that page ships.
- The card labels are generated from `ListingSummary` (type + rooms, place, area · furniture), not the free-text `title`, to avoid repeated place/rooms.
- Pagination is server-side: 3 per page, `GET /agent?id={id}&page={n}`; the page is clamped; the pager is hidden for one page; the page works without JS.
- `back` accepts a same-origin path only (starts with `/`, not `//`, no `:` or `\`); otherwise the back link falls back to `/` and “← Retour à l'espace agent”. The pager preserves a valid `back`.
- The agent's experience/tenure and rating are not shown; no e-mail; no agent directory; no co-brokerage actions on this page.
- The hero image comes from `GET /v1/listings/{id}/images`; the card falls back to the prototype's placeholder when there is no image or the read fails.
- French UI copy; English code and comments. No new colours, fonts or spacing outside the design tokens; the prototype's `agent.css` is copied to the served assets.
- The `SearchListingResponse` change is backward-compatible (`total` defaults to 0). The DTO is released to GitHub Packages with the module.
- BFF tests are plain unit / standalone MockMvc tests (no MySQL). The server total test uses the existing MySQL-backed endpoint test.

## Review Focus

1. **Unknown or missing id:** `GET /agent`, `GET /agent?id=abc`, and an agent the server doesn't know must render the “Agent introuvable” panel with a 404, never a half profile. Task 5/6 tests.
2. **Listings unavailable or out of range:** a failed listing read leaves the profile usable and shows the section's unavailable line; a requested page above the last one clamps to the last page, never an empty pager. Task 6 tests.
3. **Back/pager safety:** `javascript:`, `//host`, and `https://host` backs are rejected; a valid `/listings/501` is kept in the back link and in the previous/next URLs. Task 4/5 tests.
4. **Missing agent fields:** no photo, biography, WhatsApp, city/neighbourhood or joined date hides its element, with no empty dots or dead button. Task 4/6 tests.
5. **Sparse listing summaries:** null rooms/area/furniture/price/hero image gives a sensible title, an empty facts line where needed, a placeholder image and no broken link. Task 4/6 tests.

## Dependencies / Not in this plan

- **Co-brokerage read-only listing view.** `/listings/{id}` is the owner's checklist (`ListingController.ensure`) and locks any other agent. The agent profile's cards link there now; the read-only listing page (or a PUBLISHED access relaxation) is a separate feature. Until it ships, a colleague sees the locked view.
- **Server total.** The pager needs an exact page count; `SearchListingResponse` currently has no `total`. Task 2 adds it. A BFF-only alternative would be fetching `limit + 1` and showing “Page X” without “sur Y”; this plan chooses the total to keep the reviewed design.
- **Agent images.** The listing hero image is resolved through the existing image endpoint; `ListingSummary.heroImageId` alone is not a URL.
- **DTO release.** `SearchListingResponse.total` is in `ndopify-dto`; release it before the BFF deploy (the reactor builds it locally).

## File Structure

- `modules/ndopify-web-core/.../service/TenantFormatter.kt` — add `date(Date, pattern)`.
- `modules/ndopify-dto/.../listing/dto/SearchListingResponse.kt` — add `total`.
- `modules/ndopify-server/.../listing/server/service/ListingService.kt` — share the specification and add `count`.
- `modules/ndopify-server/.../listing/server/endpoint/ListingEndpoints.kt` — return the total.
- `modules/ndopify-partner-central/.../npc/client/ndopify/NdAgentClient.kt` — add `get`.
- `modules/ndopify-partner-central/.../npc/client/ndopify/NdListingClient.kt` — add `searchByAgent`.
- `modules/ndopify-partner-central/.../npc/ui/agent/` — `AgentProfileController.kt`, `AgentProfileRules.kt`, `AgentProfileViews.kt`, `AgentProfileView.kt`, `AgentListingCardView.kt`, `AgentListingsPage.kt`.
- `modules/ndopify-partner-central/src/main/resources/templates/agent.html` — the served page.
- `modules/ndopify-partner-central/src/main/resources/public/assets/css/agent.css` — copied from the prototype.
- Tests mirror the packages: `ui/agent/*Test.kt`, `client/ndopify/NdAgentClientTest.kt`, `client/ndopify/NdListingClientTest.kt`, `web/core/service/TenantFormatterTest.kt`, `server/listing/server/endpoint/SearchListingEndpointTest.kt`.

---

### Task 1: A date pattern on `TenantFormatter`

**Files:**
- Modify: `modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt`
- Test: `modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatterTest.kt`

**Interfaces:**
- Produces: `fun date(date: Date, pattern: String): String` — the tenant's `timeZone` and first locale, like the existing `date(LocalDate, pattern)`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `date in a custom pattern`() {
    // date = 2026-10-16T23:30Z; Africa/Douala is already 17 Oct
    assertEquals("octobre 2026", TenantFormatter(TENANT).date(date, "MMMM yyyy"))
    assertEquals("October 2026", TenantFormatter(TENANT.copy(locales = listOf("en-CM"))).date(date, "MMMM yyyy"))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=TenantFormatterTest`
Expected: FAIL — no `date(Date, String)` overload.

- [ ] **Step 3: Implement the overload**

```kotlin
fun date(date: Date, pattern: String): String =
    date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=TenantFormatterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt \
        modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatterTest.kt
git commit -m "feat(web-core): format a date with an explicit pattern"
```

---

### Task 2: The listing search total

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/SearchListingResponse.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt`

**Interfaces:**
- Produces: `SearchListingResponse(listings, total: Long = 0)`.
- Produces: `ListingService.count(request: SearchListingRequest): Long`.
- `ListingEndpoints.search` returns `SearchListingResponse(listings = …, total = service.count(restricted))`.

- [ ] **Step 1: Write the failing test**

In `SearchListingEndpointTest` (admin role, three listings on the fixture):

```kotlin
@Test
fun `total - the whole filtered set, not the page`() {
    val response = rest.getForEntity("/v1/listings?agent-id=20&limit=1", SearchListingResponse::class.java)
    assertEquals(3, response.body!!.total)
    assertEquals(1, response.body!!.listings.size)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchListingEndpointTest`
Expected: FAIL — `SearchListingResponse` has no `total` (compile error). Requires the local MySQL from the module setup.

- [ ] **Step 3: Implement the total**

Add `val total: Long = 0` to `SearchListingResponse`. In `ListingService`, extract the `Specification` used by `search` into `private fun specification(request): Specification<ListingEntity>` and add:

```kotlin
fun count(request: SearchListingRequest): Long = dao.count(specification(request))
```

In `ListingEndpoints.search`, keep the restricted request and return:

```kotlin
SearchListingResponse(listings = …, total = service.count(restricted))
```

- [ ] **Step 4: Run the server test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchListingEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/SearchListingResponse.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt
git commit -m "feat(server): expose the listing search total"
```

---

### Task 3: Client reads for the viewed agent and their listings

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt`

**Interfaces:**
- `NdAgentClient.get(id: Long): Agent?` — null on 404, every other error propagates.
- `NdListingClient.searchByAgent(agentId: Long, limit: Int, offset: Int): SearchListingResponse` — `GET /v1/listings?agent-id={agentId}&status=PUBLISHED&limit={limit}&offset={offset}`.

- [ ] **Step 1: Write the failing client tests**

```kotlin
@Test
fun `get - unknown agent`() {
    server.expect(requestTo("http://localhost:8080/v1/agents/99"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND))
    assertNull(client.get(99))
}

@Test
fun searchByAgent() {
    server.expect(requestTo(
        "http://localhost:8080/v1/listings?agent-id=30&status=PUBLISHED&limit=3&offset=3",
    ))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            json(SearchListingResponse(
                listings = listOf(ListingSummary(id = 5004, agentId = 30, status = ListingStatus.PUBLISHED)),
                total = 6,
            )),
            MediaType.APPLICATION_JSON,
        ))

    val page = client.searchByAgent(30, 3, 3)
    assertEquals(6, page.total)
    assertEquals(listOf(5004L), page.listings.map { it.id })
    server.verify()
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdAgentClientTest,NdListingClientTest`
Expected: FAIL — the methods don't exist.

- [ ] **Step 3: Implement the client reads**

`NdAgentClient.get` catches `HttpClientErrorException` with `NOT_FOUND` and returns null, like `me()`; other errors propagate. `NdListingClient.searchByAgent` builds the query with `agent-id`, `status=PUBLISHED`, `limit`, `offset`, and returns the body or `SearchListingResponse()`.

- [ ] **Step 4: Run the client tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdAgentClientTest,NdListingClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt
git commit -m "feat(npc): read an agent and its published listings"
```

---

### Task 4: Profile view model and rules

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileView.kt`
- Create: `.../ui/agent/AgentListingCardView.kt`
- Create: `.../ui/agent/AgentListingsPage.kt`
- Create: `.../ui/agent/AgentProfileRules.kt`
- Create: `.../ui/agent/AgentProfileViews.kt`
- Test: `.../ui/agent/AgentProfileRulesTest.kt`, `.../ui/agent/AgentProfileViewsTest.kt`

**Interfaces:**
- `AgentProfileRules.PAGE_SIZE = 3`
- `pages(total: Long): Int`, `page(total: Long, requested: Int): Int`
- `back(value: String?): String?`, `pageUrl(id: Long, page: Int, back: String?): String`
- `listingTitle(summary: ListingSummary): String`, `listingPlace(summary, locations): String?`,
  `listingFacts(summary, formatter): String?`, `listingPrice(summary, formatter): String?`,
  `whatsappUrl(number: String?): String?`
- `AgentListingsPage(listings: List<ListingSummary>, page: Int, pages: Int)`
- `AgentProfileViews.of(agent: Agent, listings: AgentListingsPage?, images: Map<Long, List<ListingImage>>,
  locations: Map<Long, Location>, formatter: TenantFormatter, back: String?): AgentProfileView`
- `AgentProfileView`: `id`, `name`, `initials`, `photoUrl`, `verified`, `typeLabel`, `city`, `since`,
  `sectorCity`, `sectorNeighborhoods`, `biography`, `whatsappUrl`, `listings`, `page`, `pages`,
  `prevUrl`, `nextUrl`, `listingsUnavailable`.
- `AgentListingCardView`: `id`, `title`, `place`, `facts`, `price`, `photoUrl`, `url`.

- [ ] **Step 1: Write the failing rules/view tests**

Use a `VIEWED_AGENT` (Clarisse, id 30, verified, independent, Douala/Bonapriso/Akwa, joined 14 Mar 2023,
WhatsApp +237699123456) and three `ListingSummary` fixtures. Assert:

```kotlin
assertEquals("Clarisse Mbarga", view.name)
assertEquals("CM", view.initials)
assertTrue(view.verified)
assertEquals("Indépendant", view.typeLabel)
assertEquals("Douala", view.sectorCity)
assertEquals(listOf("Bonapriso", "Akwa"), view.sectorNeighborhoods)
assertEquals("Membre depuis mars 2023", view.since)
assertEquals("https://wa.me/237699123456", view.whatsappUrl)
assertEquals("Appartement 2 chambres", card.title)
assertEquals("Bonapriso, Douala", card.place)
assertEquals("80 m² · Meublé", card.facts)
assertEquals("250K FCFA / mois", card.price)
assertEquals("/listings/5001", card.url)
assertEquals(2, AgentProfileRules.pages(6))
assertEquals(2, AgentProfileRules.page(6, 99))
assertEquals(1, AgentProfileRules.page(6, 0))
assertEquals("/agent?id=30&page=2", AgentProfileRules.pageUrl(30, 2, null))
val backUrl = AgentProfileRules.pageUrl(30, 2, "/listings/501")
assertEquals("/listings/501",
    UriComponentsBuilder.fromUriString(backUrl).build().getQueryParams().getFirst("back"))
assertNull(AgentProfileRules.back("javascript:alert(1)"))
assertNull(AgentProfileRules.back("//evil.example"))
assertNull(AgentProfileRules.back("https://evil.example"))
assertEquals("/listings/501", AgentProfileRules.back("/listings/501"))
```

Add sparse-data tests: a blank biography, no photo, no WhatsApp, no city/neighbourhoods, no joined date,
and a listing with no rooms/area/furniture/price/hero image → nulls/empty, title “Bien”, placeholder image,
empty facts, no pager for a single page.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileRulesTest,AgentProfileViewsTest`
Expected: FAIL — the classes don't exist.

- [ ] **Step 3: Implement the view model and rules**

- Labels: `JoinRules.AGENT_TYPE_LABELS`, `MandateLabels.shortPropertyType`, `MandateLabels.FURNITURE_TYPE_LABELS`,
  `MandateLabels.price(Money(…), formatter, compact = true)`, `formatter.count(area)`.
- The title appends “N chambre(s)” except for `PropertyType.STUDIO` and `PropertyType.ROOM`; falls back to “Bien”.
- The place joins the neighbourhood and city names with “, ”; the facts join the area and furniture with “ · ”.
- The hero image is `images[id].find { it.id == heroImageId } ?: firstOrNull()`, URL if non-blank.
- The pager URLs include `id` and `page`, and a valid `back`; `prevUrl`/`nextUrl` are null at the ends.
- A null `AgentListingsPage` means `listingsUnavailable = true`, empty cards, one page.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileRulesTest,AgentProfileViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent
git commit -m "feat(npc): add the agent profile view rules"
```

---

### Task 5: The controller

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileController.kt`
- Test: `.../ui/agent/AgentProfileControllerTest.kt`

**Interfaces:**
- `@GetMapping("/agent") fun show(id: String?, page: String?, back: String?, model: Model, response: HttpServletResponse): String`
- View name `AgentProfileController.VIEW = "agent"`.
- Model: `profile` (`AgentProfileView?`), `missing` (`Boolean`), `backUrl`, `backLabel`, plus the header's
  `userPhotoUrl`/`initials` from `ProfileRules.header(viewer, model)`.

- [ ] **Step 1: Write the failing controller tests**

Standalone MockMvc with mocked `CurrentAgent`, `NdAgentClient`, `NdListingClient`, `NdLocationClient`, `TenantContext`;
a real `Parallel()` closed in `@AfterEach`; `ProfileFixtures.mockLocations`; `HttpClientErrorHandler`. In `@BeforeEach`
give every test a default `SearchListingResponse()` from `searchByAgent(any(), any(), any())` and an empty list from
`images(any())`; the page tests override them. Cover:

```kotlin
@Test
fun show() {
    val result = mvc.perform(get("/agent?id=30")).andReturn()
    assertEquals("agent", result.modelAndView?.viewName)
    val view = result.modelAndView!!.model["profile"] as AgentProfileView
    assertEquals("Clarisse Mbarga", view.name)
    assertEquals(1, view.page)
    assertEquals(2, view.pages)
    verify(listingClient).searchByAgent(30, 3, 0)
    verify(listingClient).images(5001)
}

@Test
fun `missing agent - 404 and the panel`() {
    doReturn(null).whenever(agentClient).get(99)
    val result = mvc.perform(get("/agent?id=99")).andReturn()
    assertEquals(404, result.response.status)
    assertEquals(true, result.modelAndView!!.model["missing"])
    assertNull(result.modelAndView!!.model["profile"])
}

@Test
fun `page 99 clamps to the last page`() {
    // searchByAgent(30, 3, 294) returns empty with total 6;
    // then searchByAgent(30, 3, 3) returns the second page.
    val view = model(mvc.perform(get("/agent?id=30&page=99")).andReturn())["profile"] as AgentProfileView
    assertEquals(2, view.page)
}

@Test
fun `unsafe back is ignored`() {
    val result = mvc.perform(get("/agent?id=30&back=javascript:alert(1)")).andReturn()
    assertEquals("/", result.modelAndView!!.model["backUrl"])
    assertEquals("← Retour à l'espace agent", result.modelAndView!!.model["backLabel"])
}
```

Also test `id` absent/invalid → 404 missing, a listings read failure (`ResourceAccessException`) →
`listingsUnavailable`, a valid `/listings/501` back → `backUrl`/`backLabel`, `agentClient.get` 500 propagates,
and `CurrentAgent.get()` null → `SignInRequiredException`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileControllerTest`
Expected: FAIL — the controller doesn't exist.

- [ ] **Step 3: Implement the controller**

- Parse `id` with `toLongOrNull()`; null → missing view (404).
- `val viewer = currentAgent.get() ?: throw SignInRequiredException()`; `ProfileRules.header(viewer, model)`.
- Read `tenantContext.getTenant()` and build `TenantFormatter` on the request thread.
- Wave 1 with `Parallel`: `agentClient.get(id)` and `listings(id, page)`. `listings()` catches `RestClientException`
  and returns null; it fetches the requested page, reads `total`, clamps, and refetches if the clamp moved.
- Agent null → set `missing = true`, `backUrl`/`backLabel`, `response.status = 404`, return `VIEW`.
- Wave 2: the visible listings' images (one parallel call each, failures → empty) and
  `sector.locations(agent city/neighbourhoods + listing city/neighbourhood ids)` (failure → empty map).
- Build `profile = AgentProfileViews.of(...)`; `model.addAttribute("profile", profile)`, `missing = false`,
  `backUrl`/`backLabel`.
- `back = AgentProfileRules.back(back)`; fallback `/` with “← Retour à l'espace agent”.

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileControllerTest.kt
git commit -m "feat(npc): serve another agent's profile"
```

---

### Task 6: The served template and stylesheet

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/agent.html`
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/css/agent.css`
- Test: `.../ui/agent/AgentProfileTemplateTest.kt`

**Interfaces:**
- Renders `AgentProfileController.VIEW` with `profile`, `missing`, `backUrl`, `backLabel`, `signedIn`,
  `userPhotoUrl`, `initials`.
- `data-page="agent"`; links `partner-central.css` + `agent.css`; loads `partner-central.js` only (the user menu).
- No `agent.js`: the pager is server links.

- [ ] **Step 1: Copy the prototype stylesheet**

Copy `src/main/html/assets/css/agent.css` to `src/main/resources/public/assets/css/agent.css` unchanged.
The hero grid, listing cards, pager and the `h1.alert__title` rule are already in it; it uses only
`partner-central.css` tokens. Delete nothing from the prototype.

- [ ] **Step 2: Write the failing template test**

Use the same controller mocks and a real `SpringTemplateEngine` + `ClassLoaderTemplateResolver` +
`SignedInAdvice`, as `ListingTemplateTest` does. Parse with Jsoup. Assert:

```kotlin
val page = render() // GET /agent?id=30
assertEquals("Clarisse Mbarga — Ndopify Partner Central", page.title())
assertEquals("Clarisse Mbarga", page.selectFirst("h1")?.text())
assertEquals("Vérifié", page.selectFirst(".agent-badge")?.text())
assertEquals("Indépendant · Douala", page.selectFirst(".agent-hero__meta")?.text())
assertEquals("Membre depuis mars 2023", page.selectFirst(".agent-hero__since")?.text())
assertEquals(listOf("Bonapriso", "Akwa"),
    page.select(".agent-sectors__chip").map { it.text() })
assertEquals("https://wa.me/237699123456", page.selectFirst(".agent-hero__contact")?.attr("href"))
assertEquals(listOf("Appartement 2 chambres", "Studio", "Maison 3 chambres"),
    page.select(".agent-listing__title").map { it.text() })
assertEquals("/listings/5001", page.selectFirst(".agent-listing")?.attr("href"))
assertEquals("Page 1 sur 2", page.selectFirst(".agent-pagination__page")?.text())
assertEquals("/agent?id=30&page=2", page.selectFirst("[data-listings-next]")?.attr("href"))
assertNotNull(page.selectFirst(".agent-pagination__button:disabled")) // prev
assertNull(page.selectFirst("script[src=/assets/js/agent.js]"))
```

Also render:
- missing (`agentClient.get(99)` null) → `.alert__title` “Agent introuvable”, no profile, back `/`;
- no listings (empty total) → no `#listings`/pager;
- listings unavailable → the “Annonces indisponibles” hint;
- page 2 → the second-page cards and the page-2 pager.

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileTemplateTest`
Expected: FAIL — the template doesn't exist.

- [ ] **Step 4: Implement the template**

Adapt the prototype's `agent.html` markup to Thymeleaf:

- The standard head (title from `profile.name` or “Agent introuvable”, description, noindex, icon, fonts,
  `partner-central.css`, `agent.css`), header/account menu (`th:if="${signedIn}"`, viewer photo/initials,
  logout form), back link from `backUrl`/`backLabel`, `data-page="agent"`.
- The missing panel (`th:if="${missing}"`): `h1.alert__title` “Agent introuvable”, the message and the back link.
- The profile (`th:unless="${missing}"`): the compact hero (photo or initials, name, badge, type · city,
  member-since, inline sectors, WhatsApp), the listings section with the cards (image or placeholder, title,
  place, facts, price, `th:href="@{${card.url}}"`), the unavailable hint, the pager (enabled `<a>` with
  `data-listings-prev`/`data-listings-next`, disabled `<button type="button" disabled>`), and “À propos”.
- Reuse the prototype classes; do not invent new tokens. The pager label is “Page X sur Y”.

- [ ] **Step 5: Run the template and controller tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentProfileTemplateTest,AgentProfileControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/agent.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/agent.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt
git commit -m "feat(npc): style and render the agent profile page"
```

---

### Task 7: Verification

- [ ] **Step 1: Run the BFF module tests**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: all PASS.

- [ ] **Step 2: Run ktlint**

Run: `cd modules/ndopify-partner-central && mvn validate`
Expected: no violations.

- [ ] **Step 3: Run the shared tests**

Run: `cd modules/ndopify-web-core && mvn test`
Run: `cd modules/ndopify-server && mvn test -Dtest=SearchListingEndpointTest` (MySQL)
Expected: all PASS.

- [ ] **Step 4: Run the design detector on the served template**

Run:
`/Users/herve/.config/opencode/skills/impeccable/scripts/impeccable detect --json --scope layout modules/ndopify-partner-central/src/main/resources/templates/agent.html`
Expected: `[]`. Fix found layout issues without new tokens.

- [ ] **Step 5: Review the served page manually**

Run the server and Partner Central (`ndopify-server` on 8080, NPC on 8081) with the local tenant; sign in as
an agent; open `/agent?id={another agent}`. Check the compact hero, sectors, listing cards, pager, back link,
the no-listings and not-found states, and 320/390/1280 widths. The listing cards will show the locked view
on click until the co-brokerage read-only listing page ships.

- [ ] **Step 6: Commit any fixes**

```bash
git add modules/ndopify-partner-central
git commit -m "chore(npc): verify the agent profile page"
```

---

## Self-Review

- **Spec coverage:** identity/verification/type/city/since/sectors/bio/WhatsApp (Tasks 4–6); listings with
  generated labels, PUBLISHED only and hero images (Tasks 3–6); pagination (Tasks 2, 4–6); back safety
  (Tasks 4–6); not-found (Tasks 5–6); no-JS server pager (Task 6); no experience/rating/e-mail (constraints);
  no listing actions (out of scope).
- **Type consistency:** `SearchListingResponse.total` → `AgentListingsPage` → `AgentProfileView.pages`;
  `NdListingClient.searchByAgent` → `listings()` → `AgentProfileViews.of`; `back` sanitization → `backUrl`,
  `pageUrl` and the pager URLs.
- **Review Focus tests:** every line maps to Task 4, 5 or 6 tests above; the sparse listing/agent cases are
  in Task 4.
- **Deviation recorded:** the listing cards open the owner's `/listings/{id}` and show the locked view until
  the co-brokerage read-only listing page ships — deliberately not part of this plan.
