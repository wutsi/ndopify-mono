# NPC Any-Agent Read-Only Listing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Any signed-in agent opens another agent's `PUBLISHED`, `RENTED` or `SOLD` listing and sees the existing read-only sheet, with `?match=` and `?back=` only choosing the return link.

**Architecture:** `PublishedListingAccess` admits the owner, or any agent for a public-status listing; a `?match=` is resolved best-effort to a co-brokerage return link. `ListingController.show` projects the viewer as `readOnly`, resolves the return link and builds the listing agent's card; `ListingViews` / `listing.html` keep the owner lens and strip the owner-only data from the read-only one.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, Jsoup template tests, MockMvc, mockito-kotlin, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-11-13-npc-published-listing-design.md` and story #124 (which supersedes the design's co-broker-only `PUBLISHED` rule).

## Global Constraints

- UI copy is French, exactly as the existing prototype/template; code, comments, logs and docs are English.
- `modules/ndopify-partner-central/src/main/html/` is the prototype: never served, never deleted. Task 1 updates it first; the implementer stops for the user's OK on it before any Kotlin or Thymeleaf change.
- The read-only lens is a projection, not CSS/JS hiding: `ListingView` must not carry `publicUrl`, `stats`, `privateLocation`, GPS or access directions, and the template must not render the mandate number, a customer, the public URL, stats, GPS, access directions or the verification trail.
- The owner lens is unchanged: public link, share, stats, exact position/Maps/access directions, verification trail, section links, send action.
- `ListingAccess` still guards the section, photo and verify pages: another agent gets the locked view / `changed`, whatever the status.
- Dates and amounts go through `TenantFormatter`; no hard-coded `FCFA`, date pattern or new colour.
- No `ndopify-server`, `ndopify-dto` or `ndopify-web-core` change.
- `mvn verify` in `modules/ndopify-partner-central` is green (ktlint + tests + JaCoCo); coverage of the module must not drop.
- Work on branch `feat/npc-any-agent-listing`.

## Review Focus

1. Another agent's non-public status (`DRAFT`, `PENDING_VERIFICATION`, `UNDER_VERIFICATION`, `VERIFIED`, `PUBLISHING`) → the locked view, and the response contains no listing title, mandate number, customer, GPS, access directions, public URL or stats.
2. A stale or bogus `?match=` (another listing's, dismissed/rejected, another agent's mandate, a non-search mandate, or a match/mandate read failing with 4xx, 5xx or network) → the read-only sheet still renders with the `?back=`/Espace agent return link; never locked or unavailable.
3. Another agent's `RENTED` or `SOLD` listing → the read-only sheet, no owner-only block, the listing agent's card.
4. A `?back=` that isn't a same-origin path (`//host`, `https://…`, a backslash, blank) → ignored; the return link goes to `/` with `← Retour à l'espace agent`.
5. An unreadable listing agent → the page renders without the card; an unreadable image read → the sheet without a gallery; neither is a 500.

---

### Task 1: Prototype — the any-agent entry and return link

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/agent.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/agent.js`
- Modify: `modules/ndopify-partner-central/src/main/html/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`

**Interfaces:**
- Consumes: nothing (static prototype).
- Produces: the design reference for Task 4's return link and the profile-card entry.

- [ ] **Step 1: Make the profile cards carry `?back=`**

In `agent.js`, change `listingUrlOf(listing)` so the card opens `listing.html?id={id}&back={encoded profile URL}` (the profile URL is `agent.html?id={current.id}`, plus `page={current.page}` when it is > 1, plus the profile's own `back` when it has one). Set `current.page` before rendering the items so the URL uses the page being shown. Update the static card hrefs in `agent.html` (`listing.html?id=5001` → `listing.html?id=5001&back=agent.html%3Fid%3D1001`, same for 5002–5006).

- [ ] **Step 2: Read `?back=` in the listing prototype**

In `partner-central.js`'s `initListing()`, rename the listing-lens local `coBrokerage` to `readOnly` (keep the `Match` fixture's `coBrokerage` and the match-origin code untouched) and set the back link:

```js
var back = params.get("back");
var sameOrigin = back && back.indexOf("://") < 0 && back.indexOf("//") !== 0 && back.charAt(0) !== "\\" ? back : null;
var mandateLink = sameOrigin
    ? sameOrigin
    : readOnly
        ? "mandate.html?id=204#match-9009-title"
        : "mandate.html?id=" + encodeURIComponent(listing.mandateId);
$all("[data-mandate-link]").forEach(function (a) {
    a.href = mandateLink;
    if (sameOrigin) a.textContent = "← Retour";
    else if (readOnly) a.textContent = "← Retour aux biens correspondants";
});
```

The origin line (`data-listing-origin`) and the agent card stay shown for `readOnly`, with or without a `back`.

- [ ] **Step 3: Document the state**

In the `listing.html` head comment, add the any-agent state: `?id=5001&back=…` opens another agent's public listing from that page (the agent profile's card), read-only, with `← Retour`; `?id=5001` alone keeps the co-brokerage demo with `← Retour aux biens correspondants`.

- [ ] **Step 4: Check the three states, then stop for the user's OK**

Open `agent.html?id=1001`, follow a listing card → the read-only sheet with `← Retour`; open `listing.html?id=5001` → the same sheet with `← Retour aux biens correspondants`; open an owner listing → the owner lens. Show the files to the user and wait for their OK before Task 2 (the prototype gate).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/agent.html \
    modules/ndopify-partner-central/src/main/html/assets/js/agent.js \
    modules/ndopify-partner-central/src/main/html/listing.html \
    modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js
git commit -m "feat(npc): prototype opening another agent's public listing"
```

---

### Task 2: Admit any agent to a public-status listing

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccess.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt` (compile only: `CoBroker` → `ReadOnly`, the public-status set)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccessTest.kt`

**Interfaces:**
- Consumes: `NdListingClient.get`, `NdMatchClient.get`, `NdMandateClient.get`, `MatchRules.ORDER`, `MandateType.SEARCH`.
- Produces:
  - `ListingRules.PUBLIC_STATUSES: Set<ListingStatus> = setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD)`
  - `PublishedListingAccess.Result`: `Owner(listing: Listing)`, `ReadOnly(listing: Listing, match: Match? = null)`, `Locked`, `Unavailable`
  - `PublishedListingAccess.load(id: Long, agent: Agent, matchId: Long?): Result`

- [ ] **Step 1: Write the failing access tests**

Replace the lock-based cases in `PublishedListingAccessTest` with these (keep the listing failures and the owner case):

```kotlin
@Test
fun `another agent's published listing - read-only without a match`() {
    stub(listing = listing.copy(agentId = 999))

    assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, null))
    verify(matchClient, never()).get(any())
    verify(mandateClient, never()).get(any())
}

@Test
fun `another agent's rented and sold listings - read-only`() {
    for (status in listOf(ListingStatus.RENTED, ListingStatus.SOLD)) {
        val other = listing.copy(agentId = 999, status = status)
        doReturn(other).whenever(listingClient).get(501)

        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, null))
    }
}

@Test
fun `another agent's non-public listing - locked`() {
    val statuses = listOf(
        ListingStatus.DRAFT, ListingStatus.PENDING_VERIFICATION, ListingStatus.UNDER_VERIFICATION,
        ListingStatus.VERIFIED, ListingStatus.PUBLISHING,
    )
    for (status in statuses) {
        val other = listing.copy(agentId = 999, status = status)
        doReturn(other).whenever(listingClient).get(501)

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, null))
    }
    verify(matchClient, never()).get(any())
    verify(mandateClient, never()).get(any())
}

@Test
fun `a valid co-brokerage match - read-only with its context`() {
    stub(listing = listing.copy(agentId = 999))

    assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999), match), access.load(501, AGENT, 9009))
}

@Test
fun `a stale or bogus match - ignored, still read-only`() {
    val cases = listOf(
        match.copy(coBrokerage = false),
        match.copy(listingId = 502),
        match.copy(status = MatchStatus.DISMISSED),
        match.copy(status = MatchStatus.REJECTED),
    )
    for (candidate in cases) {
        stub(listing = listing.copy(agentId = 999), match = candidate)

        assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))
    }
}

@Test
fun `a match of another agent or a non-search mandate - ignored`() {
    stub(listing = listing.copy(agentId = 999), search = search.copy(agentId = 999))
    assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))

    stub(listing = listing.copy(agentId = 999), search = search.copy(mandateType = MandateType.LISTING))
    assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))
}

@Test
fun `a match or mandate read failure - ignored, still read-only`() {
    val other = listing.copy(agentId = 999)
    stub(listing = other)
    doThrow(notFound()).whenever(matchClient).get(9009)
    assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

    doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(matchClient).get(9009)
    assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

    doThrow(ResourceAccessException("down")).whenever(matchClient).get(9009)
    assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

    stub(listing = other)
    doThrow(notFound()).whenever(mandateClient).get(204)
    assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

    doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).get(204)
    assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))
}

@Test
fun `a valid match on a rented listing - the context stays`() {
    val rented = listing.copy(agentId = 999, status = ListingStatus.RENTED)
    stub(listing = rented)

    assertEquals(PublishedListingAccess.ReadOnly(rented, match), access.load(501, AGENT, 9009))
}

@Test
fun `a non-positive id - locked without a read`() {
    assertEquals(PublishedListingAccess.Locked, access.load(0, AGENT, 9009))
    verify(listingClient, never()).get(any())
}
```

Keep `owner`, `listing not found - locked`, `listing failure - unavailable`, `listing network failure - unavailable`, and `unknown status - locked`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingAccessTest`
Expected: FAIL — `ReadOnly` does not exist and the old `CoBroker` cases no longer compile.

- [ ] **Step 3: Implement the access**

In `ListingRules`, add `PUBLIC_STATUSES` (the KDoc: "The statuses a listing keeps its public page in: any agent may read them"). Use it in `ListingViews` (replace its private `PUBLIC`) and in `ListingController` (replace `PUBLIC_OR_CLOSED`).

In `PublishedListingAccess`, replace `CoBroker` with `ReadOnly(listing, match: Match? = null)` and change `load` after the owner check:

```kotlin
if (listing.status !in ListingRules.PUBLIC_STATUSES) return Locked
if (matchId == null) return ReadOnly(listing)
return ReadOnly(listing, visibleMatch(matchId, listing, agent))
```

`visibleMatch` reads the match and its mandate, catching every `RestClientException` (4xx, 5xx, network) and returning `null`; it returns the match only when `match.listingId == listing.id`, `match.coBrokerage`, `match.status in MatchRules.ORDER`, `mandate.agentId == agent.id` and `mandate.mandateType == MandateType.SEARCH`. The listing fetch keeps its 4xx → `Locked`, other → `Unavailable` policy.

In `ListingController`, rename `is PublishedListingAccess.CoBroker` to `is PublishedListingAccess.ReadOnly` and guard the old match-only card/back code with `result.match != null` so the module still compiles and behaves as before Task 4.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingAccessTest`
Expected: PASS. Then run `mvn test` (the renamed `ReadOnly` must compile in the controller tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccess.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccessTest.kt
git commit -m "feat(npc): admit any agent to a public listing"
```

---

### Task 3: Name the lens `readOnly` and let the agent card return to the listing

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt` (KDoc only)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt` (call site only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`

**Interfaces:**
- Consumes: Task 2's `ReadOnly`.
- Produces:
  - `ListingView.readOnly: Boolean = false` (replaces `coBrokerage`)
  - `ListingViews.of(..., readOnly: Boolean = false, agent: ListingAgentView? = null, backUrl: String = ..., backLabel: String = ...)`
  - `ListingViews.agent(agent: Agent, listing: Listing, listingUrl: String): ListingAgentView` — its `profileUrl`'s `back` is exactly `listingUrl`.

- [ ] **Step 1: Update the tests to the new names and signature**

In `ListingViewsTest`, replace `coBrokerage = true` with `readOnly = true` and every `view.coBrokerage` with `view.readOnly`. In `ListingControllerTest`, `assertTrue(view.coBrokerage)` → `assertTrue(view.readOnly)`. Add:

```kotlin
@Test
fun `agent - the profile returns to the listing page`() {
    val view = ListingViews.agent(AGENT, COMPLETE, "/listings/501?match=9009")

    val back = UriComponentsBuilder.fromUriString(view.profileUrl).build().getQueryParams().getFirst("back")
    assertEquals("/listings/501?match=9009", UriUtils.decode(back, "UTF-8"))
}
```

In `ListingTemplateTest`, the existing co-broker render still uses the `match` parameter; no assertion changes beyond the template field name.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingViewsTest,ListingControllerTest'`
Expected: FAIL — `readOnly` and the new `ListingViews.agent` signature do not exist.

- [ ] **Step 3: Rename the lens and change the card's back target**

- `ListingView`: `coBrokerage` → `readOnly`; KDoc "Another agent's read-only lens: the published sheet without the owner's blocks nor the checklist."
- `ListingViews.of`: parameter and every use `coBrokerage` → `readOnly`; use `ListingRules.PUBLIC_STATUSES`.
- `PublishedListingViews` KDoc: "another agent gets the shared note instead of the private location."
- `ListingViews.agent(agent, listing, match: Match)` → `ListingViews.agent(agent, listing, listingUrl: String)`; `profileUrl = AgentProfileRules.profileUrl(agent.id, listingUrl)`.
- `templates/listing.html`: the four `l.coBrokerage` → `l.readOnly`.
- `ListingController`: pass `readOnly = result is ReadOnly` and, for the card, `ListingViews.agent(it, listing, "/listings/${listing.id}?match=${result.match!!.id}")` (the `!!` is safe under the `match != null` guard; Task 4 replaces it with the real listing URL).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS — behavior unchanged, only the names and the card's back argument.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/main/resources/templates/listing.html \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt
git commit -m "refactor(npc): name the listing lens read-only"
```

---

### Task 4: The any-agent page — return link, agent card, no owner extras

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: Task 2's `ReadOnly(listing, match)`, Task 3's `readOnly`/`ListingViews.agent(..., listingUrl)`, `AgentProfileRules.back`.
- Produces: `ListingController.show(id: String, match: String?, back: String?, model: Model): String`; the read-only `ListingView` with `backUrl`/`backLabel` and the listing agent's card.

- [ ] **Step 1: Write the failing controller tests**

```kotlin
@Test
fun `show - another agent's published listing is read-only without a match`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT))
        .whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertTrue(view.readOnly)
    assertNotNull(view.agent)
    assertNull(view.publicUrl)
    assertNull(view.stats)
    assertTrue(view.rows.isEmpty())
    assertEquals("/", view.backUrl)
    assertEquals("← Retour à l'espace agent", view.backLabel)
    verify(listingClient, never()).stats(any())
    verify(mandateClient, never()).get(any())
    verify(visitClient, never()).coBrokerageOnListing(any())
}

@Test
fun `show - another agent's rented and sold listings are read-only`() {
    for (status in listOf(ListingStatus.RENTED, ListingStatus.SOLD)) {
        doReturn(COMPLETE.copy(status = status, agentId = 999)).whenever(listingClient).get(501)
        doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

        val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

        assertTrue(view.readOnly)
        assertNotNull(view.published)
        assertNull(view.published?.privateLocation)
    }
}

@Test
fun `show - a valid back returns there with a generic label`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(
        mvc.perform(get("/listings/501").param("back", "/agent?id=30&page=1")).andReturn(),
    )["listing"] as ListingView

    assertEquals("/agent?id=30&page=1", view.backUrl)
    assertEquals("← Retour", view.backLabel)
}

@Test
fun `show - an off-origin back is ignored`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    for (back in listOf("//evil.com", "https://evil.com", "\\evil", "")) {
        val view = model(mvc.perform(get("/listings/501").param("back", back)).andReturn())["listing"] as ListingView

        assertEquals("/", view.backUrl)
    }
}

@Test
fun `show - a stale match falls back to the read-only lens`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(matchClient).get(9009)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(
        mvc.perform(get("/listings/501").param("match", "9009").param("back", "/agent?id=30")).andReturn(),
    )["listing"] as ListingView

    assertTrue(view.readOnly)
    assertEquals("/agent?id=30", view.backUrl)
    verify(mandateClient, never()).get(any())
}

@Test
fun `show - a valid match wins over back`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
        .whenever(matchClient).get(9009)
    doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
        .whenever(mandateClient).get(204)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(
        mvc.perform(get("/listings/501").param("match", "9009").param("back", "/agent?id=30")).andReturn(),
    )["listing"] as ListingView

    assertEquals("/mandates/204#matches-title", view.backUrl)
    assertEquals("← Retour aux biens correspondants", view.backLabel)
}

@Test
fun `show - the agent card returns to the listing page`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(
        mvc.perform(get("/listings/501").param("back", "/agent?id=30")).andReturn(),
    )["listing"] as ListingView

    val back = UriComponentsBuilder.fromUriString(view.agent!!.profileUrl).build().getQueryParams().getFirst("back")
    assertEquals("/listings/501?back=/agent?id=30", UriUtils.decode(back, "UTF-8"))
}

@Test
fun `show - an unreadable agent leaves the card out`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)
    doThrow(ResourceAccessException("down")).whenever(agentClient).get(999)

    val result = mvc.perform(get("/listings/501")).andReturn()

    assertTrue((model(result)["listing"] as ListingView).readOnly)
    assertNull((model(result)["listing"] as ListingView).agent)
    assertNull(model(result)["locked"])
}
```

Replace the old `show - co-brokerage listing` and `show - co-brokerage listing without a match is locked`; keep `show - another agent's listing is locked` for a `DRAFT` (rename it to `show - another agent's draft is locked`).

- [ ] **Step 2: Write the failing template tests**

Update the `render` helper to accept `back: String? = null` and add `request.param("back", back)`. Add:

```kotlin
@Test
fun `another agent's published listing - read-only, back to the Espace agent`() {
    doReturn(COMPLETE.copy(
        status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT,
        description = "Texte public", accessDirections = "Porte de droite",
    )).whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val page = render()

    assertEquals("/", page.selectFirst(".back-link a")?.attr("href"))
    assertEquals("← Retour à l'espace agent", page.selectFirst(".back-link a")?.text())
    assertNotNull(page.selectFirst("[data-listing-gallery]"))
    assertNotNull(page.selectFirst("[data-listing-sheet]"))
    assertEquals(
        "L'adresse exacte et l'itinéraire sont communiqués par l'agent avant la visite.",
        page.selectFirst(".listing-place__note span")?.text(),
    )
    assertNotNull(page.selectFirst("[data-listing-agent]"))
    assertTrue(page.selectFirst("[data-listing-origin]")!!.text().contains("Bien d'un autre agent"))
    assertNull(page.selectFirst("[data-listing-meta]"))
    assertNull(page.selectFirst("[data-listing-public-url]"))
    assertNull(page.selectFirst("[data-listing-share]"))
    assertNull(page.selectFirst("[data-listing-stats]"))
    assertNull(page.selectFirst("[data-listing-verification]"))
    assertNull(page.selectFirst(".listing-private"))
    assertNull(page.selectFirst("[data-listing-visits]"))
    assertFalse(page.text().contains("Mandat n°"))
    assertFalse(page.text().contains("Porte de droite"))
    assertFalse(page.text().contains("4.03350"))
}

@Test
fun `another agent's rented and sold listings - read-only`() {
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)
    for (status in listOf(ListingStatus.RENTED, ListingStatus.SOLD)) {
        val page = render(COMPLETE.copy(status = status, agentId = 999, publishedAt = SENT))
        assertNotNull(page.selectFirst("[data-listing-sheet]"))
        assertNull(page.selectFirst("[data-listing-public-url]"))
    }
}

@Test
fun `opened from the agent profile - back to the profile`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT))
        .whenever(listingClient).get(501)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val page = render(back = "/agent?id=30&page=1")

    assertEquals("/agent?id=30&page=1", page.selectFirst(".back-link a")?.attr("href"))
    assertEquals("← Retour", page.selectFirst(".back-link a")?.text())
}

@Test
fun `another agent's draft - locked, no sheet`() {
    doReturn(COMPLETE.copy(status = ListingStatus.DRAFT, agentId = 999)).whenever(listingClient).get(501)

    val page = render()

    assertNotNull(page.selectFirst("[data-listing-locked]"))
    assertNull(page.selectFirst("[data-listing-sheet]"))
    assertNull(page.selectFirst("[data-listing-agent]"))
}
```

Note: the `listing.html` line 120 renders `[data-listing-origin]` for read-only, so remove the `assertNull` on the origin and instead assert its text contains `Bien d'un autre agent`; the origin is expected for any another-agent lens. Keep the `[data-listing-meta]` (mandate number) absent.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,ListingTemplateTest'`
Expected: FAIL — `show` has no `back` parameter and the read-only page still returns the locked view without a match.

- [ ] **Step 4: Implement the controller**

In `show`, add `@RequestParam(required = false) back: String?` and compute:

```kotlin
val readOnly = result is PublishedListingAccess.ReadOnly
val match = (result as? PublishedListingAccess.ReadOnly)?.match
val backPath = AgentProfileRules.back(back)
val listingUrl = when {
    match != null -> UriComponentsBuilder.fromPath("/listings/${listing.id}")
        .queryParam("match", match.id).build().encode().toUriString()
    backPath != null -> ListingRules.url(listing.id, backPath)
    else -> "/listings/${listing.id}"
}
val backUrl = when {
    match != null -> "/mandates/${match.mandateId}#matches-title"
    backPath != null -> backPath
    readOnly -> "/"
    else -> "/mandates/${listing.mandateId}"
}
val backLabel = when {
    match != null -> "← Retour aux biens correspondants"
    backPath != null -> "← Retour"
    readOnly -> "← Retour à l'espace agent"
    else -> "← Retour au mandat"
}
```

Keep the owner's stats read (`result is Owner && listing.status == PUBLISHED`) and visits read (`result is Owner && listing.status in ListingRules.PUBLIC_STATUSES`). Build the card for every `ReadOnly`: `lookup("listing agent ${listing.agentId}") { agentClient.get(listing.agentId) }?.let { ListingViews.agent(it, listing, listingUrl) }`. Pass `readOnly = readOnly, agent = card, backUrl = backUrl, backLabel = backLabel` to `ListingViews.of`. Drop the old co-broker block. Update the `show` KDoc: any agent may open a public-status listing; `match` and `back` only choose the return link.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,ListingTemplateTest'`
Expected: PASS. Then `mvn test` for the module.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt
git commit -m "feat(npc): open any agent's public listing read-only"
```

---

### Task 5: Document the new gate

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces: the module guide matching the code.

- [ ] **Step 1: Rewrite the listing paragraph**

Replace the lines that describe `ListingAccess.ReadOnly` and "Another agent's `PUBLISHED` listing opens read-only" (around lines 235–237 and 263–273) with:

- ndopify-server checks listing access itself (PUBLISHED, SOLD and RENTED are public reads); the BFF's `PublishedListingAccess` admits the listing's own agent (the owner lens) or **any** agent on a `PUBLISHED`, `RENTED` or `SOLD` listing (the read-only lens), and locks the other statuses.
- A `?match=` is only a context hint: a valid co-brokerage match of one of the viewer's searches sets the return link to `/mandates/{id}#matches-title`; a stale/bogus match, or a failure to read it or its mandate, is ignored. Otherwise a valid same-origin `?back=` is the return link (`← Retour`), else the Espace agent (`← Retour à l'espace agent`). The owner still returns to the mandate.
- The read-only lens is a projection without the mandate number, the customer, the public link/share, the stats, the exact position/GPS, the Maps link, the access directions, the verification trail or the section/photo/verify actions; it shows the public place, the "shared before the visit" note and the listing agent's card. `ListingAccess` still guards the section, photo and verify pages for the owner only.

- [ ] **Step 2: Verify the module**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, every test, JaCoCo thresholds).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the any-agent listing gate"
```

---

## Final Verification

- [ ] `cd modules/ndopify-partner-central && mvn verify` — green (ktlint + tests + coverage).
- [ ] `git status` is clean; the commits touch only `modules/ndopify-partner-central/`, and the first is the prototype.
- [ ] Re-read story #124's acceptance criteria: every one maps to a test in Tasks 2–4 (1–2 access/controller/template, 3 owner tests unchanged, 4 access/controller, 5 listing failures, 6–7 template absence/note, 8 card/absence, 9 no mandate/customer call, 10 profile card + template, 11 match back link, 12 back precedence, 13 `ListingAccessTest` unchanged).
- [ ] Compare the rendered page with the prototype at 390px and 1024px: another agent's listing shows the sheet, the public place + note, the agent card and the right back link; no owner block.
