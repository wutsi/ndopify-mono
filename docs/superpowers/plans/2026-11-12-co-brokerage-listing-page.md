# Co-Brokerage Read-Only Listing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open another agent's published listing from a co-brokerage match or an agent profile as a read-only checklist page: no customer or mandate data, no write actions, and a boxed card with the publishing agent's photo, name and WhatsApp.

**Architecture:** `ListingAccess` gains a `ReadOnly` result for another agent's `PUBLISHED` listing. `ListingController` renders it through the existing checklist template with `shared = true`; `ListingViews` drops the section links and write flags and adds a `ListingAgentView`. The template ports the approved prototype's `Bien d'un autre agent` badge, boxed agent card and read-only rows. Match cards and agent-profile cards pass a validated `?back=` so the page returns to where it was opened.

**Tech Stack:** Kotlin, Spring MVC, Thymeleaf, MockMvc, mockito-kotlin, Jsoup, vanilla CSS with the existing design tokens.

**Spec:** the approved prototype at `modules/ndopify-partner-central/src/main/html/listing.html?id=5001` and the *Design* section below. This plan is the design record for the BFF slice.

## Design

- **Access rule.** Another agent's `PUBLISHED` listing is readable read-only. Any other status (`DRAFT`, `PENDING_VERIFICATION`, `UNDER_VERIFICATION`, `VERIFIED`, `PUBLISHING`, `RENTED`, `SOLD`, `UNKNOWN`) stays locked. The rule is deliberately not gated on a co-brokerage match: the agent profile already lists another agent's published listings, and the listing data is public. This slice changes presentation, not who may see a published property.
- **Anti-sautage.** The page never loads the listing's mandate or customer. It shows only the listing, its checklist, its public link and the publishing agent's card. The co-agent's mandate page remains locked.
- **Writes.** `shared` listings are never editable: no `Envoyer pour vérification`, no section links, no photo upload/delete. The existing `agentId` checks in the write paths stay, and `ListingSectionController` / `ListingPhotoController` treat `ReadOnly` as `CHANGED`.
- **Back links.** `?back=` accepts only a same-origin path (`AgentProfileRules.back`). A match card passes `/mandates/{searchMandateId}#matches-title`; an agent-profile card passes its own `/agent?id={agentId}&page={page}`. Without a valid `back`, a shared listing returns to the Espace agent (`/#mandates-title`). Own listings keep `/mandates/{mandateId}`.
- **Agent card.** `NdAgentClient.get(listing.agentId)` supplies the agent. A 404 or read failure does not hide the listing: the page renders with a short "coordonnées indisponibles" note and no card.
- **Prototype parity.** The prototype's boxed card is compact: photo left, `Publié par <name> ↗` and the WhatsApp number right; 3.5rem photo and 12px gaps on phones, 4rem and 16px from 640px, 3rem and 8px below 360px. The number itself is the `wa.me` link.
- **Match card.** A co-brokerage match adds `Origine · Bien d'un autre agent` to its facts, and its title link carries the `?back=` path.

## Global Constraints

- BFF only: change `modules/ndopify-partner-central`; no `ndopify-server` or `ndopify-dto` change.
- Another agent's `PUBLISHED` listing is read-only; every other status is locked.
- Never read or render the listing's mandate, customer or customer contact on a shared listing.
- No stats, no section links, no send/upload/delete action on a shared listing.
- French UI copy; English code and comments.
- Reuse the prototype's `.listing-agent` / `.listing-origin` rules and the existing `partner-central.css` tokens; add no colours, fonts or spacing values.
- Own-listing behaviour and its tests stay unchanged.
- Tests with `kotlin.test`, mocks with mockito-kotlin; run `mvn test` from `modules/ndopify-partner-central`.

## Review Focus

1. **Another agent's non-published listing** (`DRAFT`, `PENDING`, `RENTED`, `SOLD`) → the locked view, never the read-only page. Pinned by Task 2.
2. **The listing agent cannot be read** (404 or server/network failure) → the listing still renders, with a contact-unavailable note and no card. Pinned by Tasks 2 and 3.
3. **A hostile `?back=`** (`https://…`, `//host`, `javascript:`) → ignored, and the page returns to the Espace agent. Pinned by Task 2.
4. **A long agent name or WhatsApp number at 320px** → wraps inside the card, no horizontal scroll. Verified manually in Task 3 with the prototype at 320, 390 and 1280px.
5. **An action on a shared listing** (section save, photo upload/delete) → `CHANGED`, never written. Pinned by Task 2.

---

### Task 1: View model for a shared listing

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Produces:
  - `ListingAgentView(name: String, photoUrl: String?, initials: String, whatsappUrl: String?, whatsappDisplay: String?, profileUrl: String)`
  - `ListingView.shared: Boolean = false` and `ListingView.agent: ListingAgentView? = null`
  - `ChecklistRow.url: String?` — null for a shared listing
  - `ListingViews.of(..., stats: ListingStats? = null, shared: Boolean = false, agent: Agent? = null): ListingView`
- Consumes: `Agent`, `ProfileRules.fullName`, `KycRules.initials`, `AgentProfileRules.whatsappUrl`, `AgentProfileRules.pageUrl`.

- [ ] **Step 1: Write the failing view tests**

Add to `ListingViewsTest`:

```kotlin
@Test
fun `shared - read-only, no section links, agent card`() {
    val view = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT),
        IMAGES,
        emptyMap(),
        formatter,
        shared = true,
        agent = ProfileFixtures.AGENT,
    )

    assertTrue(view.shared)
    assertEquals("Appartement, Bonapriso", view.title)
    assertFalse(view.editable)
    assertFalse(view.sendable)
    assertTrue(view.rows.all { it.url == null })
    assertEquals("Serge Ngono", view.agent?.name)
    assertEquals("SN", view.agent?.initials)
    assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", view.agent?.photoUrl)
    assertEquals("https://wa.me/237670000001", view.agent?.whatsappUrl)
    assertEquals("+237 6 70 00 00 01", view.agent?.whatsappDisplay)
    assertEquals("/agent?id=20&page=1&back=/listings/501", view.agent?.profileUrl)
}

@Test
fun `shared - the agent may be unknown`() {
    val view = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT),
        IMAGES,
        emptyMap(),
        formatter,
        shared = true,
    )

    assertNull(view.agent)
}

@Test
fun `own listing - title and section links unchanged`() {
    val view = ListingViews.of(LISTING, IMAGES, emptyMap(), formatter)

    assertFalse(view.shared)
    assertEquals("Annonce · Appartement, Bonapriso", view.title)
    assertEquals("/listings/501/location", view.rows.first().url)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest`
Expected: compilation fails — `shared` and `agent` are unresolved.

- [ ] **Step 3: Implement the view model**

In `ListingView.kt`:
- Change `ChecklistRow.url` to `String?` with the comment "The section page; null on another agent's listing, whose sections are not reachable."
- Add `val shared: Boolean = false` and `val agent: ListingAgentView? = null` to `ListingView`.
- Add:

```kotlin
/** The agent who publishes a co-brokerage listing, on its read-only page. */
data class ListingAgentView(
    val name: String,
    val photoUrl: String?,
    val initials: String,
    val whatsappUrl: String?,
    /** "+237 6 70 00 00 01", or the number as stored for a non-Cameroonian one. */
    val whatsappDisplay: String?,
    /** `/agent?id={id}&page=1&back=/listings/{listingId}`. */
    val profileUrl: String,
)
```

In `ListingViews.of`:
- Add `shared: Boolean = false, agent: Agent? = null` after `stats`.
- `editable = !shared && ListingRules.editable(listing)`.
- `title = title(listing, place, shared)`, where a shared title omits the `Annonce · ` prefix.
- `panel = if (shared) null else ...`; `stats = if (shared) null else ...`.
- `ChecklistRow.url = if (shared) null else "/listings/${listing.id}/${ListingRules.slug(section)}"`.
- `sendable = !shared && editable && left.isEmpty()`.
- When `agent != null`, set `agent = agentCard(agent, listing)`; the private helper returns `ListingAgentView` with `ProfileRules.fullName`, `agent.party.photoUrl?.ifBlank { null }`, `KycRules.initials(...)`, `AgentProfileRules.whatsappUrl`, and `AgentProfileRules.pageUrl(agent.id, 1, "/listings/${listing.id}")`.
- Add a private `displayPhone` identical to `MandateViews`'s Cameroon formatter (`+237 6 70 00 00 01`).
- Update the two existing `ListingControllerTest` assertions that call `it.url.endsWith(...)` to `it.url.orEmpty().endsWith(...)`; own-listing rows remain non-null.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): model a co-brokerage listing's read-only view"
```

---

### Task 2: Read-only access and controller

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingAccess.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt`

**Interfaces:**
- Produces:
  - `ListingAccess.ReadOnly(val listing: Listing) : Result`
  - `ListingController(currentAgent, listingClient, agentClient, mandateClient, locationClient, tenantContext)` — `agentClient` after `listingClient`
  - `ListingController.show(@PathVariable id: String, @RequestParam(name = "back", required = false) back: String?, model: Model)`
  - model attributes `backUrl` and `backLabel`
- Consumes: `ListingViews.of(..., shared, agent)` (Task 1), `NdAgentClient.get`.

- [ ] **Step 1: Write the failing controller tests**

Add `agentClient = mock<NdAgentClient>()` to `ListingControllerTest`, pass it to the controller constructor, and add:

```kotlin
@Test
fun `another agent's published listing is read-only`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED, publishedAt = SENT))
        .whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val result = mvc.perform(get("/listings/501")).andReturn()
    val view = model(result)["listing"] as ListingView

    assertTrue(view.shared)
    assertEquals("Serge Ngono", view.agent?.name)
    assertNull(model(result)["locked"])
    assertEquals("/#mandates-title", model(result)["backUrl"])
    assertEquals("← Retour à l'espace agent", model(result)["backLabel"])
    verify(listingClient, never()).stats(any())
}

@Test
fun `another agent's published listing honours a back path`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED))
        .whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val result = mvc.perform(get("/listings/501?back=/mandates/204%23matches-title")).andReturn()

    assertEquals("/mandates/204#matches-title", model(result)["backUrl"])
    assertEquals("← Retour", model(result)["backLabel"])
}

@Test
fun `another agent's published listing ignores an external back`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED))
        .whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val result = mvc.perform(get("/listings/501?back=https://evil.example")).andReturn()

    assertEquals("/#mandates-title", model(result)["backUrl"])
}

@Test
fun `another agent's rented listing is locked`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.RENTED))
        .whenever(listingClient).get(501)

    assertEquals(true, model(mvc.perform(get("/listings/501")).andReturn())["locked"])
}

@Test
fun `shared - the agent read fails`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED))
        .whenever(listingClient).get(501)
    doThrow(ResourceAccessException("down")).whenever(agentClient).get(999)

    val result = mvc.perform(get("/listings/501")).andReturn()
    val view = model(result)["listing"] as ListingView

    assertTrue(view.shared)
    assertNull(view.agent)
    assertNull(model(result)["locked"])
}

@Test
fun `own listing - ignores back`() {
    val result = mvc.perform(get("/listings/501?back=/agent?id=30")).andReturn()

    assertEquals("/mandates/203", model(result)["backUrl"])
    assertEquals("← Retour au mandat", model(result)["backLabel"])
}
```

Clone the existing `save - another agent's listing` test in `ListingSectionControllerTest`, set
`status = ListingStatus.PUBLISHED` on the listing, and assert `ListingController.CHANGED` with no
`listingClient.update(...)` call. Clone the existing `upload - another agent's listing` test in
`ListingPhotoControllerTest` the same way, with `status = ListingStatus.PUBLISHED`, and assert
`ListingController.CHANGED` with no upload.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingControllerTest,ListingSectionControllerTest,ListingPhotoControllerTest`
Expected: compilation fails — `ReadOnly`, `agentClient` and the `back` parameter are unresolved.

- [ ] **Step 3: Implement the access rule and controller**

`ListingAccess.kt`:

```kotlin
data class ReadOnly(val listing: Listing) : Result

fun load(id: Long, agent: Agent): Result =
    try {
        val listing = listingClient.get(id)
        when {
            listing.agentId == agent.id && listing.status != ListingStatus.UNKNOWN -> Found(listing)
            listing.agentId != agent.id && listing.status == ListingStatus.PUBLISHED -> ReadOnly(listing)
            else -> Locked
        }
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("No listing $id: ${ex.statusCode}")
        Locked
    } catch (ex: RestClientException) {
        LOGGER.error("No listing $id", ex)
        Unavailable
    }
```

Update the doc comment: another agent's `PUBLISHED` listing is `ReadOnly`, every other foreign status is `Locked`.

`ListingController.kt`:
- Add `private val agentClient: NdAgentClient` after `listingClient`; add the `NdAgentClient` and `AgentProfileRules` imports, and `org.springframework.web.bind.annotation.RequestParam`.
- Add `const val BACK_URL = "backUrl"` and `const val BACK_LABEL = "backLabel"` to the companion.
- Change `show` to accept `@RequestParam(name = "back", required = false) back: String?`.
- At the start of `show`, set the fallback model attributes:

```kotlin
model.addAttribute(BACK_URL, "/#mandates-title")
model.addAttribute(BACK_LABEL, "← Retour à l'espace agent")
```

- Replace `load` with one returning `Loaded(listing: Listing, shared: Boolean)`; map `Found` to false, `ReadOnly` to true, `Locked` to null, `Unavailable` to null with the existing flag.
- After loading:

```kotlin
val (listing, shared) = id.toLongOrNull()?.let { load(it, agent, model) } ?: return locked(model)
val listingAgent = if (shared) {
    lookup("agent ${listing.agentId}") { agentClient.get(listing.agentId) }
} else {
    null
}
val stats = if (!shared && listing.status == ListingStatus.PUBLISHED) {
    lookup("stats of listing ${listing.id}") { listingClient.stats(listOf(listing.id)) }?.get(listing.id)
} else {
    null
}
val validBack = if (shared) AgentProfileRules.back(back) else null
model.addAttribute(BACK_URL, when {
    !shared -> "/mandates/${listing.mandateId}"
    validBack != null -> validBack
    else -> "/#mandates-title"
})
model.addAttribute(BACK_LABEL, when {
    !shared -> "← Retour au mandat"
    validBack != null -> "← Retour"
    else -> "← Retour à l'espace agent"
})
val view = ListingViews.of(
    listing,
    images,
    locations(listing),
    formatter(),
    tenantContext.getTenant().publicPortalUrl,
    stats,
    shared = shared,
    agent = listingAgent,
)
```

- Keep `locked(model)` only setting `locked` / `unavailable`; the back attributes are already set.

`ListingSectionController.kt` // `save`: add `ListingAccess.ReadOnly -> return back(id, ListingController.CHANGED, redirect)` to the `when`.
`ListingPhotoController.kt` // `upload`: add `ListingAccess.ReadOnly -> ListingController.CHANGED` to the `when`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingControllerTest,ListingSectionControllerTest,ListingPhotoControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingAccess.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionController.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt
git commit -m "feat(npc): open another agent's published listing read-only"
```

---

### Task 3: Template and styles

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/listing-row.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: `ListingView.shared`, `ListingView.agent`, `ChecklistRow.url: String?` (Task 1); model `backUrl` / `backLabel` (Task 2).
- Produces DOM hooks: `[data-listing-origin]`, `[data-listing-agent]`, `[data-listing-agent-unavailable]`, `[data-agent-name]`, `[data-agent-profile]`, `[data-agent-whatsapp]`, `div.checklist__row`.

- [ ] **Step 1: Write the failing template tests**

Add `agentClient = mock<NdAgentClient>()` to `ListingTemplateTest`, pass it to the controller constructor, and add:

```kotlin
@Test
fun `shared - read-only with the agent card`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED, publishedAt = SENT))
        .whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val page = render()

    assertEquals("Bien d'un autre agent", page.selectFirst("[data-listing-origin] .status-tag")?.text())
    assertNull(page.selectFirst("[data-listing-meta]"))
    assertNull(page.selectFirst("[data-listing-send]"))
    assertNull(page.selectFirst("[data-listing-panel]"))
    assertNull(page.selectFirst("[data-listing-stats]"))
    assertEquals(0, page.select("a.checklist__row").size)
    assertEquals(7, page.select("div.checklist__row").size)

    val card = page.selectFirst(".listing-agent")!!
    assertEquals("Serge Ngono", card.selectFirst("[data-agent-name]")?.text())
    assertEquals(
        "https://wa.me/237670000001",
        card.selectFirst("[data-agent-whatsapp]")?.attr("href"),
    )
    assertEquals("+237 6 70 00 00 01", card.selectFirst("[data-agent-whatsapp]")?.text())
    assertEquals(
        "/agent?id=20&page=1&back=/listings/501",
        card.selectFirst("[data-agent-profile]")?.attr("href"),
    )
    assertNull(page.selectFirst("[data-listing-agent-unavailable]"))
}

@Test
fun `shared - no WhatsApp, no contact line`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED))
        .whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999, whatsappNumber = null)).whenever(agentClient).get(999)

    val page = render()

    assertNull(page.selectFirst("[data-agent-contact]"))
    assertEquals("Serge Ngono", page.selectFirst("[data-agent-name]")?.text())
}

@Test
fun `shared - the agent is unavailable`() {
    doReturn(LISTING.copy(agentId = 999, status = ListingStatus.PUBLISHED))
        .whenever(listingClient).get(501)
    doReturn(null).whenever(agentClient).get(999)

    val page = render()

    assertNull(page.selectFirst(".listing-agent"))
    assertEquals(
        "Les coordonnées de l'agent ne peuvent pas être affichées pour le moment.",
        page.selectFirst("[data-listing-agent-unavailable]")?.text(),
    )
}

@Test
fun `shared - public link stays`() {
    doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()
    doReturn(
        LISTING.copy(
            agentId = 999,
            status = ListingStatus.PUBLISHED,
            url = "/douala/bonapriso/listing/501/appartement",
        ),
    ).whenever(listingClient).get(501)
    doReturn(AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val page = render()

    assertEquals(
        "https://ndopify.com/douala/bonapriso/listing/501/appartement",
        page.selectFirst("[data-listing-public-url]")?.attr("href"),
    )
}
```

Also assert the own-listing back link in the existing `draft` test team:

```kotlin
assertEquals("/mandates/203", page.selectFirst(".back-link a")?.attr("href"))
assertEquals("← Retour au mandat", page.selectFirst(".back-link a")?.text())
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: FAIL — the badge, card and read-only rows are missing.

- [ ] **Step 3: Implement the template and styles**

`templates/listing.html`:
- Back link: `<a th:href="${backUrl}" th:text="${backLabel}">← Retour au mandat</a>`.
- Mandate meta: add `th:if="${!l.shared}"`.
- Add, after the intro block:

```html
<p class="listing-origin" th:if="${l.shared}" data-listing-origin>
    <span class="status-tag status-tag--info">Bien d'un autre agent</span>
    <span>Ce bien est publié par un autre agent. Ses coordonnées sont ci-dessous.</span>
</p>
```

- Add the agent card section, matching the prototype:

```html
<section class="home-section" aria-labelledby="listing-agent-title" th:if="${l.shared}" data-listing-agent>
    <h2 id="listing-agent-title" class="visually-hidden">L'agent qui publie ce bien</h2>
    <div class="listing-agent" th:if="${l.agent}" th:with="a=${l.agent}">
        <a class="listing-agent__photo" th:href="${a.profileUrl}" aria-hidden="true" tabindex="-1">
            <img th:if="${a.photoUrl}" th:src="${a.photoUrl}" alt="">
            <span th:unless="${a.photoUrl}" th:text="${a.initials}">CM</span>
        </a>
        <div class="listing-agent__info">
            <p class="listing-agent__headline">
                <span class="listing-agent__eyebrow">Publié par</span>
                <a class="listing-agent__name" th:href="${a.profileUrl}" data-agent-profile>
                    <span th:text="${a.name}" data-agent-name>Clarisse Mbarga</span>
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg>
                </a>
            </p>
            <p class="listing-agent__contact" th:if="${a.whatsappUrl}" data-agent-contact>
                <svg class="icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413Z"/></svg>
                <a th:href="${a.whatsappUrl}" target="_blank" rel="noopener"
                   th:text="${a.whatsappDisplay}" data-agent-whatsapp>+237 6 70 00 00 01</a>
            </p>
        </div>
    </div>
    <p class="hint" th:unless="${l.agent}" data-listing-agent-unavailable>Les coordonnées de l'agent ne peuvent pas être affichées pour le moment.</p>
</section>
```

- Panels: add `!l.shared and` to each `th:if`.
- Checklist rows: use the new fragment for both the link and the plain row:

```html
<li th:each="row : ${l.rows}">
    <a class="checklist__row" th:if="${row.url != null}" th:href="@{${row.url}}">
        <th:block th:replace="~{fragments/listing-row :: content(${row})}"></th:block>
    </a>
    <div class="checklist__row" th:unless="${row.url != null}">
        <th:block th:replace="~{fragments/listing-row :: content(${row})}"></th:block>
    </div>
</li>
```

Create `templates/fragments/listing-row.html` with the three spans:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<th:block th:fragment="content(row)">
    <span class="checklist__title" th:text="${row.label}" data-section-title>Emplacement</span>
    <span th:class="|status-tag status-tag--${row.state.style}|" th:text="${row.state.label}"
          data-section-state>À vérifier</span>
    <span class="checklist__summary" th:text="${row.summary}" data-section-summary></span>
</th:block>
</body>
</html>
```

- Send section: `th:if="${!l.shared and l.editable}"`.
- Port the prototype's `.listing-origin` and `.listing-agent*` rules from
  `modules/ndopify-partner-central/src/main/html/assets/css/listing.css` into
  `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css`.
  Keep the `≤359px` and `≥640px` breakpoints exactly as the prototype has them.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: PASS.

- [ ] **Step 5: Verify the mobile breakpoints**

Open the prototype at `modules/ndopify-partner-central/src/main/html/listing.html?id=5001`
in a browser at 320px, 390px and 1280px. Expected: the photo stays left, the information right,
long names/numbers wrap inside the box, and no horizontal scroll. The served page uses the
same rules.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/listing.html \
    modules/ndopify-partner-central/src/main/resources/templates/fragments/listing-row.html \
    modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt
git commit -m "feat(npc): render a co-brokerage listing read-only with the agent card"
```

---

### Task 4: Entry points and back links

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt`

**Interfaces:**
- Produces `ListingRules.url(id: Long, back: String? = null): String`.
- Consumes: nothing new.

- [ ] **Step 1: Write the failing URL and entry-point tests**

Add to `ListingRulesTest`:

```kotlin
@Test
fun `url - without a back path`() {
    assertEquals("/listings/501", ListingRules.url(501))
}

@Test
fun `url - with a back path`() {
    assertEquals(
        "/listings/501?back=/mandates/204%23matches-title",
        ListingRules.url(501, "/mandates/204#matches-title"),
    )
    assertEquals(
        "/listings/5001?back=/agent?id%3D30%26page%3D1",
        ListingRules.url(5001, "/agent?id=30&page=1"),
    )
}
```

Add to `MatchViewsTest`:

```kotlin
@Test
fun `co-brokerage match - origin and back to the search`() {
    val section = section(listOf(MATCH_A.copy(coBrokerage = true)))

    val view = section.matches.single()
    assertEquals("/listings/3012?back=/mandates/204%23matches-title", view.listingUrl)
    assertEquals("Bien d'un autre agent", view.facts.first { it.first == "Origine" }.second)
}

@Test
fun `own match - no origin and no back`() {
    val view = section(listOf(MATCH_A)).matches.single()

    assertEquals("/listings/3012", view.listingUrl)
    assertTrue(view.facts.none { it.first == "Origine" })
}
```

Add to `AgentProfileViewsTest`:

```kotlin
@Test
fun `listing card - returns to the profile`() {
    val view = AgentProfileViews.of(AGENT, page, images, LOCATIONS, formatter, null)

    assertEquals("/listings/5001?back=/agent?id%3D30%26page%3D1", view.listings.first().url)
}
```

Update `AgentProfileTemplateTest`'s expected listing card hrefs from `/listings/5001` and
`/listings/5004` to the `ListingRules.url(...)` values with their agent profile `back`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingRulesTest,MatchViewsTest,AgentProfileViewsTest,AgentProfileTemplateTest`
Expected: compilation fails — `ListingRules.url` is unresolved.

- [ ] **Step 3: Implement the URL builder and entry points**

`ListingRules.url`:

```kotlin
/** `/listings/{id}`, with `?back=` when [back] is given (encoded so its own query and fragment survive). */
fun url(id: Long, back: String? = null): String =
    UriComponentsBuilder.fromPath("/listings/$id")
        .apply { back?.let { queryParam("back", it) } }
        .build()
        .encode()
        .toUriString()
```

`MatchViews.view`:
- `listingUrl = ListingRules.url(listing.id, "/mandates/${mandate.id}#matches-title".takeIf { match.coBrokerage })`.
- Change the `facts` list to `listOfNotNull` and add `if (match.coBrokerage) "Origine" to "Bien d'un autre agent" else null` before the rent line.

`AgentProfileViews`:
- Compute `val listingBack = AgentProfileRules.pageUrl(agent.id, page, back)` after `page`.
- Pass it to `card(...)`; the card's `url = ListingRules.url(summary.id, listingBack)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingRulesTest,MatchViewsTest,AgentProfileViewsTest,AgentProfileTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViewsTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt
git commit -m "feat(npc): link co-brokerage listings with a return path"
```

---

### Task 5: Documentation and module verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` — the **Listing** bullet, and the **Matched properties** bullet if it describes the match card.

**Interfaces:**
- Consumes: nothing.
- Produces: the written rule the next co-brokerage slices inherit.

- [ ] **Step 1: Document the read-only listing rule**

In the **Listing** bullet of `modules/ndopify-partner-central/CLAUDE.md`, append:

> Another agent's `PUBLISHED` listing opens read-only (`ListingAccess.ReadOnly`): no mandate number, no customer, no stats, no section links and no send/upload/delete action. A boxed card shows the publishing agent (photo or initials, name linked to `/agent`, WhatsApp number as the `wa.me` link); an unreadable agent leaves a contact-unavailable note. A valid same-origin `?back=` returns to where it was opened, otherwise the Espace agent; own listings still return to their mandate.

In the **Matched properties** bullet, append:

> A co-brokerage match shows `Origine · Bien d'un autre agent`, and its title link carries `?back=/mandates/{searchMandateId}#matches-title`; an agent-profile listing card carries `?back=` to its profile page.

- [ ] **Step 2: Run the module tests**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS, including every test added above.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the co-brokerage read-only listing page"
```

---

## Self-Review

- **Spec coverage:** access rule (Task 2), view model (Task 1), visual page (Task 3), entry points/back links (Task 4), docs (Task 5). The prototype's `Bien d'un autre agent` badge, boxed agent card, compact breakpoints, read-only rows, public link and match origin are all covered.
- **Type consistency:** `ListingAccess.ReadOnly`, `Loaded`, `ListingAgentView`, `ListingView.shared/agent`, `ChecklistRow.url: String?`, `ListingRules.url(id, back)`, model `backUrl/backLabel` are used with the same names across tasks.
- **Review focus:** non-published statuses (Task 2), agent read failure (Tasks 2 and 3), hostile `back` (Task 2), mobile wrapping (Task 3 manual step), write actions (Task 2).
- **Out of scope:** visits, closing, commission, customer-facing proposal wording, match-gated access, and any `ndopify-server` / `ndopify-dto` change.
