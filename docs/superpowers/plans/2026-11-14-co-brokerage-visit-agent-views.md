# Co-Brokerage Visit Agent Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make both agents of a co-brokerage visit visible where the work happens: A sees the listing agent (B) on its visit cards and visit page, and B sees the visit on its home page and listing-mandate page.

**Architecture:** The visit stays A's (`T_VISIT.agent_id = A`); B stays resolved from `listing.agent` at read time. The server's co-brokerage read gains an optional `listing-id`: without it, it returns the caller agent's co-brokerage visits across all his live listings. Partner Central reuses the listing page's boxed agent card and co-brokerage block, adding them to the Espace agent, the listing-mandate page and A's visit pages.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), Thymeleaf, `mockito-kotlin`, `kotlin.test`, `TestRestTemplate`/GreenMail integration tests (server), MockMvc/real-Thymeleaf tests (Partner Central).

**Spec:** GitHub issue #118 (`https://github.com/wutsi/ndopify-mono/issues/118`); the enriched description holds the intent and acceptance criteria, this plan is the design record.

## Global Constraints

- Work on branch `feat/npc-co-brokerage-visit-agent-views`.
- No migration, no new column: B is `listing.agent`, resolved at read time.
- No `ndopify-dto` nor `ndopify-web-core` change: no library release, no root `pom.xml` version bump.
- Anti-sautage is a hard invariant: B never receives A's customer, mandate, match, note, code, address, report, feedback or cancellation reason; A's customer never learns B. The endpoint JSON and both rendered pages must pin this with tests.
- Only `CONFIRMED` and `CHECKED_IN` visits are exposed to B, and only on `PUBLISHED`, `RENTED` or `SOLD` listings.
- B is read-only: no check-in, cancel, no-show, report, nor a link to A's visit page.
- Own-inventory visits (`visit.agentId == listing.agentId`) never show an "Avec" line and never enter B's block.
- `src/main/html/` is the prototype: update it first, never serve it, never delete anything there.
- French UI text; dates through `TenantFormatter`/`VisitSlots`.
- Tests use `kotlin.test` assertions and `mockito-kotlin`; tests mirror the main package.
- `mvn verify` (ktlint + tests + JaCoCo) must pass in every touched module before a push; `ndopify-server` needs local MySQL.
- PR order: the `ndopify-server` PR first, then the `ndopify-partner-central` PR; no library release in between.

## Review Focus

1. **A customer's data reaching B** — the no-listing-id JSON, the home block and the mandate block must never carry A's customer, mandate, match, note, code, address, report or feedback.
2. **An own-inventory visit** — must never show an "Avec" line nor enter B's block, even when the listing agent equals the visit agent.
3. **A failing co-brokerage or agent read** — the home page, the mandate page and the visit page must still render; only the block/card is absent.
4. **Profile back links** — A's visit-card link and B's block link must return to the page the card was opened from, not the listing page by default; `back` must stay a same-origin path.
5. **A customer or party-less caller on the endpoint** — must be 403 `VISIT_ACCESS_DENIED` without `listing-id`, never a 200 with another agent's data.

---

### Task 1: Prototype the agent views

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/visit.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/listing.css` (only if a class is missing)

**Interfaces:**
- Produces: the validated look of the "Avec B" card line, the "Visite organisée avec" visit-page card and the *Visites d'autres agents* blocks. Tasks 3 and 4 must match it.

- [ ] **Step 1: Add a co-brokerage visit fixture**

In `assets/js/visit.js`, add one `VISITS` entry from A's side on a listing published by another agent, for example:

```javascript
// Co-brokerage: the listing is published by Clarisse Mbarga (agent 30), not by the signed-in agent
7020: { mandateId: "223", listingId: "2987", status: "CONFIRMED", createdAt: NOW - 2 * DAY,
    scheduledAt: at(2, "14:00"), agent: { id: "30", name: "Clarisse Mbarga" } },
```

Give the fixture's `LISTINGS` entry for `2987` an owner other than the signed-in agent (the prototype's `listings()` data) if it does not already have one.

- [ ] **Step 2: Render the "Avec B" line on the cards**

In `assets/js/visit.js`'s `renderHomeVisits` card builder, after the listing line, fill a new `<p class="visit-card__agent" data-visit-agent>` when `v.agent` is set. The markup is a footer (top rule) with a person icon, the word "Avec" on its own, and a link holding the agent's name plus an external-arrow icon:

```javascript
var agent = $("[data-visit-agent]", item);
if (agent) {
    agent.hidden = !v.agent;
    if (v.agent) {
        var name = [v.agent.firstName, v.agent.lastName].filter(Boolean).join(" ");
        $("[data-visit-agent-link]", agent).href = "agent.html?id=" + encodeURIComponent(v.agent.id) +
            "&back=" + encodeURIComponent("index.html#visits-title");
        $("[data-visit-agent-name]", agent).textContent = name;
    }
}
```

Add the same line to `<template data-visit-card-template>` in `index.html`. In `assets/js/partner-central.js`'s `fillVisit`, add the same line to the mandate page's inline visit detail (`visit-detail.html`'s prototype counterpart), reading `visit.agent`; its "Avec" is a separate span in the markup.

- [ ] **Step 3: Add the *Visites d'autres agents* block to the prototype home page**

In `index.html`, after the `data-visits-section` section, add the section and its card template:

```html
<section class="home-section listing-visits" aria-labelledby="co-brokerage-visits-title" data-co-brokerage-visits
         hidden>
    <h2 id="co-brokerage-visits-title">Visites d'autres agents</h2>
    <p>Un autre agent organise ces visites de votre bien : vous accueillez l'agent et son client sur place.</p>
    <ul data-co-brokerage-visit-list></ul>
    <template data-co-brokerage-visit-template>
        <li class="co-visit-card">
            <article>
                <div class="co-visit-card__head">
                    <p class="co-visit-card__when"><svg calendar/><span data-co-brokerage-at></span></p>
                    <p class="status-tag status-tag--info" data-co-brokerage-progress hidden>En cours</p>
                </div>
                <p class="co-visit-card__listing">
                    <svg house data-co-brokerage-type-icon="ON_SITE"></svg>
                    <svg video hidden data-co-brokerage-type-icon="VIDEO"></svg>
                    <span data-co-brokerage-type></span><span aria-hidden="true">·</span>
                    <a data-co-brokerage-listing></a>
                </p>
                <div class="co-visit-card__agent">
                    <span class="co-visit-card__avatar" data-co-brokerage-initials aria-hidden="true"></span>
                    <div class="co-visit-card__agent-info">
                        <p class="co-visit-card__agent-line">
                            <span>Avec</span>
                            <a data-co-brokerage-agent><span data-co-brokerage-agent-name></span><svg external/></a>
                        </p>
                        <a class="co-visit-card__whatsapp" data-co-brokerage-whatsapp>WhatsApp</a>
                    </div>
                </div>
            </article>
        </li>
    </template>
</section>
```

Add `assets/css/listing.css` to `index.html`'s `<head>` and add the `.co-visit-card` styles there (the same ones the served `listing.css` gets in Task 4): a bordered card, the when + "En cours" head, the type/listing line, and the organizer footer (avatar, "Avec …" link, WhatsApp) under a top rule. Add a `renderCoBrokerageVisits()` function in `assets/js/visit.js` that fills the list from two mock `CoBrokerageVisitSummary`s (one `CONFIRMED`, one `CHECKED_IN`): the date/time, the type and its icon, "En cours" when checked in, the listing, the agent's initials and name linked to `agent.html`, and the WhatsApp link.

- [ ] **Step 4: Add the block to the prototype mandate page**

In `mandate.html`, add the same section and card template (same classes, `data-co-brokerage-visits`) after `data-listing-proposals`, with `data-listing-proposals`'s section id unchanged. Fill it with the same cards; A's profile link returns to `mandate.html?id=203#co-brokerage-visits-title`.

- [ ] **Step 5: Add the boxed agent card to the prototype visit page**

In `visit.html`, after `div.visit-header`, add the listing-page agent card with the "Visite organisée avec" eyebrow (copy the `listing-agent` block from `listing.html` and give its nodes `data-visit-agent-*` hooks). Fill it in `assets/js/visit.js`'s `renderVisit` from `VISITS[id].agent` when set:

```html
<section class="home-section" aria-labelledby="visit-agent-title" data-visit-agent-section hidden>
    <h2 id="visit-agent-title" class="visually-hidden">L'agent qui organise la visite</h2>
    <div class="listing-agent">
        <a class="listing-agent__photo" data-visit-agent-profile href="#" aria-hidden="true" tabindex="-1">
            <span data-visit-agent-initials>CM</span>
        </a>
        <div class="listing-agent__info">
            <p class="listing-agent__headline">
                <span class="listing-agent__eyebrow">Visite organisée avec</span>
                <a class="listing-agent__name" data-visit-agent-profile href="#">
                    <span data-visit-agent-name>Clarisse Mbarga</span>
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg>
                </a>
            </p>
            <p class="listing-agent__contact" data-visit-agent-contact>
                <a data-visit-agent-whatsapp href="#">+237 6 99 12 34 56</a>
            </p>
        </div>
    </div>
</section>
```

- [ ] **Step 6: Check the prototype**

Run a static server and open the four pages:

```bash
cd modules/ndopify-partner-central/src/main/html && python3 -m http.server 8090
```

Check `http://localhost:8090/index.html` (the "Avec Clarisse Mbarga" line and the *Visites d'autres agents* block), `visit.html?id=7020` (the boxed card), `mandate.html?id=203` (the block) and `listing.html?id=5001` (unchanged). Fix any overflow/long-name issue at phone width.

- [ ] **Step 7: Commit and stop for review**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the co-brokerage visit agent views"
```

**Stop here and show the prototype to the user. Do not start Task 2 until it is approved.**

---

### Task 2: Server — the agent-wide co-brokerage read

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/VisitEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CoBrokerageVisitEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Consumes: `VisitEntity`, `ListingEntity`, `ListingStatus`, `CoBrokerageVisitSummary`, the existing `VisitMapper.toCoBrokerageSummary`.
- Produces:
  - `VisitRepository.findCoBrokerageByListingAgent(agentId: Long, statuses: Collection<VisitStatus>, listingStatuses: Collection<ListingStatus>): List<VisitEntity>`
  - `VisitAccessService.requireAgent(): AgentEntity`
  - `VisitService.findCoBrokerageForAgent(agent: AgentEntity): List<VisitEntity>`
  - `GET /v1/visits/co-brokerage?listing-id=` (still) and `GET /v1/visits/co-brokerage` (new, no parameter) → `SearchCoBrokerageVisitResponse`

- [ ] **Step 1: Write the failing endpoint tests**

In `CoBrokerageVisitEndpointTest`, remove `a missing listing id is 400` and add:

```kotlin
@Test
fun `no listing id answers the caller's co-brokerage visits across its listings`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage", SearchCoBrokerageVisitResponse::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(listOf(2L, 1L, 6L), response.body!!.visits.map { it.id }) // scheduledAt ascending
    assertEquals(listOf(101L, 101L, 102L), response.body!!.visits.map { it.listingId })
    assertEquals(listOf(30L, 30L, 30L), response.body!!.visits.map { it.agentId })
    assertEquals(VisitStatus.CHECKED_IN, response.body!!.visits.first().status)
}

@Test
fun `no listing id - the answer carries no customer field`() {
    val body = rest.getForEntity("/v1/visits/co-brokerage", String::class.java).body!!

    assertFalse(body.contains("Ray Sponsible"))
    assertFalse(body.contains("ray.sponsible"))
    assertFalse(body.contains("1234"))
    assertFalse(body.contains("5678"))
    assertFalse(body.contains("note"))
    assertFalse(body.contains("address"))
    assertFalse(body.contains("feedback"))
    assertFalse(body.contains("agentComment"))
}

@Test
fun `no listing id - the customer is forbidden`() {
    userId = 13L

    val response = rest.getForEntity("/v1/visits/co-brokerage", ErrorResponse::class.java)

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
}

@Test
fun `no listing id - a caller without an agent is forbidden`() {
    userId = 17L

    val response = rest.getForEntity("/v1/visits/co-brokerage", ErrorResponse::class.java)

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitEndpointTest
```

Expected: FAIL — the no-parameter call is 400 (or 404 once the parameter is optional but the branch is missing).

- [ ] **Step 3: Add the repository query and its test**

In `VisitRepository` (import `com.wutsi.ndopify.listing.dto.ListingStatus`):

```kotlin
// The co-brokerage visits on an agent's live listings, soonest first: the listing agent's home and mandate views
@Query(
    "SELECT v FROM VisitEntity v WHERE v.agentId <> :agentId AND v.status IN :statuses AND " +
        "v.listingId IN (SELECT l.id FROM ListingEntity l WHERE l.agent.id = :agentId AND l.status IN :listingStatuses) " +
        "ORDER BY v.scheduledAt ASC, v.id ASC"
)
fun findCoBrokerageByListingAgent(
    agentId: Long,
    statuses: Collection<VisitStatus>,
    listingStatuses: Collection<ListingStatus>,
): List<VisitEntity>
```

In `VisitRepositoryTest` (import `com.wutsi.ndopify.listing.dto.ListingStatus`), below the existing co-brokerage test:

```kotlin
@Test
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
fun `co-brokerage visits of an agent cover every live listing`() {
    assertEquals(
        listOf(2L, 1L, 6L),
        dao.findCoBrokerageByListingAgent(
            20L,
            setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
            setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD),
        ).map { it.id },
    )
}
```

- [ ] **Step 4: Add the access check**

In `VisitAccessService` (import `AgentEntity`):

```kotlin
/** The caller's own agent: the co-brokerage read of an agent's home and mandate pages. No admin bypass. */
fun requireAgent(): AgentEntity =
    callerService.findPartyIdOrNull(callerService.getPrincipal())
        ?.let { partyId -> agentService.findByPartyIdOrNull(partyId) }
        ?: throw accessDenied()
```

- [ ] **Step 5: Add the service read and its test**

In `VisitService` (import `AgentEntity` and `ListingStatus`):

```kotlin
/** The CONFIRMED/CHECKED_IN co-brokerage visits on an agent's live listings, soonest first. */
fun findCoBrokerageForAgent(agent: AgentEntity): List<VisitEntity> =
    dao.findCoBrokerageByListingAgent(
        agent.id!!,
        setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
        setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD),
    )
```

In `VisitServiceTest`:

```kotlin
@Test
fun `the co-brokerage read covers the agent's live listings`() {
    val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L))
    doReturn(listOf(visit)).whenever(dao).findCoBrokerageByListingAgent(eq(20L), any(), any())

    assertEquals(listOf(visit), service.findCoBrokerageForAgent(agent))

    verify(dao).findCoBrokerageByListingAgent(
        20L,
        setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
        setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD),
    )
}
```

- [ ] **Step 6: Make `listing-id` optional in the endpoint**

In `VisitEndpoints.coBrokerage`, replace the signature and body with:

```kotlin
@GetMapping("/co-brokerage")
fun coBrokerage(@RequestParam(name = "listing-id", required = false) listingId: Long?): SearchCoBrokerageVisitResponse {
    val visits = if (listingId == null) {
        service.findCoBrokerageForAgent(access.requireAgent())
    } else {
        if (listingId <= 0) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "listing-id", type = ParameterType.PARAMETER_TYPE_QUERY),
                ),
            )
        }
        val listing = listingService.findById(listingId)
        access.ensureIsListingAgent(listing)
        service.findCoBrokerageOnListing(listing)
    }
    return SearchCoBrokerageVisitResponse(visits = visits.map { mapper.toCoBrokerageSummary(it) })
}
```

- [ ] **Step 7: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitEndpointTest,VisitRepositoryTest,VisitServiceTest
```

Expected: PASS.

- [ ] **Step 8: Run the module's full verification**

```bash
cd modules/ndopify-server && mvn verify
```

Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gates). Local MySQL must be running.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitAccessService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/VisitEndpoints.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CoBrokerageVisitEndpointTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): read an agent's co-brokerage visits across its listings"
```

---

### Task 3: Partner Central — A sees B on the cards and the visit page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-card.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-detail.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitFixtures.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt` (constructor call)

**Interfaces:**
- Consumes: `ListingSummary.agentId`, `NdAgentClient.get(id): Agent?`, `ListingAgentView`, `AgentProfileRules.profileUrl(id, back)`, `ProfileRules.fullName`, `KycRules.initials`, `ListingStatus`.
- Produces:
  - `VisitCardView.agentName: String? = null`, `VisitCardView.agentUrl: String? = null`
  - `VisitCards.of(..., agents: Map<Long, Agent> = emptyMap(), back: String? = null)` — an agent line only when `back != null` and the listing's agent differs from the visit's
  - `VisitPage.agent: ListingAgentView? = null`
  - `VisitViews.of(..., agent: Agent? = null)`
  - `VisitController(currentAgent, visitClient, listingClient, customerClient, agentClient, locationClient, tenantContext, clock)`
  - `HomeController(..., customerClient, agentClient, locationClient, ...)`

- [ ] **Step 1: Add the fixtures**

In `VisitFixtures`, next to `AGENT_ID`/`LISTING`:

```kotlin
val OTHER_AGENT_ID = 30L
val OTHER_AGENT = Agent(
    id = OTHER_AGENT_ID,
    party = Party(firstName = "Clarisse", lastName = "Mbarga", photoUrl = "https://cdn.ndopify.com/clarisse.jpg"),
    whatsappNumber = "+237699000002",
)

fun coBrokerageListing() = LISTING.copy(agentId = OTHER_AGENT_ID)
```

Import `com.wutsi.ndopify.agent.dto.Agent`.

- [ ] **Step 2: Write the failing card and page view tests**

In `VisitCardsTest`:

```kotlin
@Test
fun `a co-brokerage visit names the listing agent`() {
    val cards = VisitCards.of(
        listOf(VisitFixtures.confirmed()),
        mapOf(3012L to VisitFixtures.coBrokerageListing()),
        mapOf(300L to VisitFixtures.CUSTOMER),
        LOCATIONS,
        VisitFixtures.NOW,
        VisitFixtures.TENANT,
        VisitFixtures.FORMATTER,
        agents = mapOf(30L to VisitFixtures.OTHER_AGENT),
        back = "/#visits-title",
    )

    assertEquals("Clarisse Mbarga", cards.upcoming.single().agentName)
    assertEquals("/agent?id=30&back=/%23visits-title", cards.upcoming.single().agentUrl)
}

@Test
fun `an own-inventory visit has no agent line`() {
    val cards = VisitCards.of(
        listOf(VisitFixtures.confirmed()),
        mapOf(3012L to VisitFixtures.LISTING),
        mapOf(300L to VisitFixtures.CUSTOMER),
        LOCATIONS,
        VisitFixtures.NOW,
        VisitFixtures.TENANT,
        VisitFixtures.FORMATTER,
        agents = mapOf(20L to VisitFixtures.OTHER_AGENT),
        back = "/#visits-title",
    )

    assertNull(cards.upcoming.single().agentName)
    assertNull(cards.upcoming.single().agentUrl)
}

@Test
fun `an unreadable listing agent keeps the line as un autre agent`() {
    val cards = VisitCards.of(
        listOf(VisitFixtures.confirmed()),
        mapOf(3012L to VisitFixtures.coBrokerageListing()),
        mapOf(300L to VisitFixtures.CUSTOMER),
        LOCATIONS,
        VisitFixtures.NOW,
        VisitFixtures.TENANT,
        VisitFixtures.FORMATTER,
        agents = emptyMap(),
        back = "/#visits-title",
    )

    assertEquals("un autre agent", cards.upcoming.single().agentName)
    assertNull(cards.upcoming.single().agentUrl)
}
```

In `VisitViewsTest` (add `agent: Agent? = null` to the existing `of` helper and pass it as the last argument of `VisitViews.of`):

```kotlin
@Test
fun `a co-brokerage visit carries the listing agent's card`() {
    val page = of(agent = VisitFixtures.OTHER_AGENT)

    assertEquals("Clarisse Mbarga", page.agent?.name)
    assertEquals("CM", page.agent?.initials)
    assertEquals("https://cdn.ndopify.com/clarisse.jpg", page.agent?.photoUrl)
    assertEquals("/agent?id=30&back=%2Fvisits%2F7001", page.agent?.profileUrl)
    assertEquals("https://wa.me/237699000002", page.agent?.whatsappUrl)
}

@Test
fun `an own-inventory visit has no agent card`() {
    assertNull(of(agent = null).agent)
}
```

- [ ] **Step 3: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=VisitCardsTest,VisitViewsTest
```

Expected: FAIL — `agentName`, `agentUrl`, `page.agent` and the `agents`/`back` parameters do not exist.

- [ ] **Step 4: Add the card line**

`VisitCardView`:

```kotlin
/** The listing's agent when this is a co-brokerage visit: "Clarisse Mbarga", or "un autre agent" unreadable. */
val agentName: String? = null,
/** The listing agent's profile, back to the page the card is on; null without a name or without a back link. */
val agentUrl: String? = null,
```

`VisitCards.of` gains `agents: Map<Long, Agent> = emptyMap()` and `back: String? = null`. Add a private helper and call it in both the requested and upcoming mappings:

```kotlin
private fun agent(
    visit: Visit,
    listings: Map<Long, ListingSummary>?,
    agents: Map<Long, Agent>,
    back: String?,
): Pair<String?, String?> {
    if (back == null) return null to null
    val listing = listings?.get(visit.listingId) ?: return null to null
    if (listing.agentId == visit.agentId) return null to null
    val agent = agents[listing.agentId]
    val name = agent?.let { ProfileRules.fullName(it) }?.ifBlank { null } ?: "un autre agent"
    return name to agent?.let { AgentProfileRules.profileUrl(listing.agentId, back) }
}
```

Use it in `VisitCardView(...)` as `agentName = agent.first, agentUrl = agent.second` for each mapping (compute once per visit). Imports: `com.wutsi.ndopify.agent.dto.Agent`, `com.wutsi.ndopify.npc.ui.agent.AgentProfileRules`, `com.wutsi.ndopify.npc.ui.profile.ProfileRules`.

- [ ] **Step 5: Add the visit-page card**

`VisitPage` gains `val agent: ListingAgentView? = null` (import `com.wutsi.ndopify.npc.ui.listing.ListingAgentView`).

`VisitViews.of` gains `agent: Agent? = null` after `formatter`, and sets:

```kotlin
agent = agent?.let {
    ListingAgentView(
        id = it.id,
        name = ProfileRules.fullName(it),
        initials = KycRules.initials(it.party.firstName, it.party.lastName),
        photoUrl = it.party.photoUrl?.ifBlank { null },
        whatsappUrl = AgentProfileRules.whatsappUrl(it.whatsappNumber),
        whatsappNumber = it.whatsappNumber,
        profileUrl = AgentProfileRules.profileUrl(it.id, "/visits/${visit.id}"),
    )
},
```

Imports: `com.wutsi.ndopify.agent.dto.Agent`, `com.wutsi.ndopify.npc.ui.agent.AgentProfileRules`, `com.wutsi.ndopify.npc.ui.kyc.KycRules`, `com.wutsi.ndopify.npc.ui.listing.ListingAgentView`, `com.wutsi.ndopify.npc.ui.profile.ProfileRules`.

- [ ] **Step 6: Wire the controllers**

`VisitController`: add `private val agentClient: NdAgentClient` after `customerClient`, and after loading the listing:

```kotlin
val listingAgent = if (listing != null && listing.agentId != agent.id) {
    lookup("agent ${listing.agentId} of visit $visitId") { agentClient.get(listing.agentId) }
} else {
    null
}
```

Pass it as the last argument of `VisitViews.of(...)`.

`HomeController`: add `private val agentClient: NdAgentClient` after `customerClient`. In wave 3 (after `visitListings`):

```kotlin
val visitAgentsCall = parallel.async { visitAgents(visits, visitListings) }
val visitAgents = visitAgentsCall.join()
```

Pass `agents = visitAgents, back = "/#visits-title"` to `VisitCards.of`. Add:

```kotlin
/** The listing agents of the co-brokerage visits, by id, read in parallel; an unreadable one is left out. */
private fun visitAgents(visits: List<VisitSummary>?, listings: Map<Long, ListingSummary>?): Map<Long, Agent> {
    val ids = visits.orEmpty()
        .mapNotNull { listings?.get(it.listingId) }
        .filter { it.agentId > 0 }
        .map { it.agentId }
        .distinct()
    val calls = ids.associateWith { id ->
        parallel.async {
            try {
                agentClient.get(id)
            } catch (ex: RestClientException) {
                LOGGER.warn("No agent $id for a visit: ${ex.message}")
                null
            }
        }
    }
    return calls.mapNotNull { (id, call) ->
        val agent = try {
            call.join()
        } catch (ex: Exception) {
            null
        }
        agent?.let { id to it }
    }.toMap()
}
```

`MandateController` (already has `agentClient`): in `render`, after `visitListings`, load the same map with a private `visitAgents(visits, visitListings)` helper and pass `agents = visitAgents, back = "/mandates/${mandate.id}#matches-title"` to `VisitCards.of`.

- [ ] **Step 7: Add the template lines**

In `fragments/visit-card.html`, after the `visit-card__listing` paragraph of both fragments:

```html
<p class="visit-card__agent" th:if="${v.agentName != null}" data-visit-agent>
    <svg class="icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true" focusable="false"><circle cx="10" cy="6.5" r="3.25"/><path d="M4 17c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" stroke-linecap="round"/></svg>
    <span>Avec</span>
    <a th:if="${v.agentUrl}" th:href="${v.agentUrl}" data-visit-agent-link>
        <span th:text="${v.agentName}">Clarisse Mbarga</span>
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg>
    </a>
    <span th:unless="${v.agentUrl}" th:text="${v.agentName}">un autre agent</span>
</p>
```

Add the same line to `fragments/visit-detail.html`, after the `visit-detail__when` paragraph (without the top rule).

In `visit.html`, after `div.visit-header`, add the boxed card (same `listing-agent` markup as `listing.html`, eyebrow `Visite organisée avec`, names `data-visit-agent`, no new CSS needed because `listing.css` is already linked):

```html
<section class="home-section" aria-labelledby="visit-agent-title" th:if="${p.agent != null}" data-visit-agent>
    <h2 id="visit-agent-title" class="visually-hidden">L'agent qui organise la visite</h2>
    <div class="listing-agent">
        <a class="listing-agent__photo" th:href="${p.agent.profileUrl}" aria-hidden="true" tabindex="-1">
            <img th:if="${p.agent.photoUrl}" th:src="${p.agent.photoUrl}" alt="">
            <span th:unless="${p.agent.photoUrl}" th:text="${p.agent.initials}">CM</span>
        </a>
        <div class="listing-agent__info">
            <p class="listing-agent__headline">
                <span class="listing-agent__eyebrow">Visite organisée avec</span>
                <a class="listing-agent__name" th:href="${p.agent.profileUrl}" data-visit-agent-profile>
                    <span th:text="${p.agent.name}">Clarisse Mbarga</span>
                    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg>
                </a>
            </p>
            <p class="listing-agent__contact" th:if="${p.agent.whatsappUrl != null}">
                <svg class="icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413Z"/></svg>
                <a th:href="${p.agent.whatsappUrl}" target="_blank" rel="noopener" th:text="${p.agent.whatsappNumber}">+237 6 99 12 34 56</a>
            </p>
        </div>
    </div>
</section>
```

Add to `public/assets/css/visit.css`: `.visit-card__agent` is a footer under a top rule (person icon, "Avec", a semibold name link with a small external-arrow icon); `.visit-detail__agent` is the same line without the rule. Both keep the 44px target on the link and an info-coloured arrow.

- [ ] **Step 8: Update the controller and template tests**

- `VisitControllerTest` / `VisitTemplateTest`: add `private val agentClient = mock<NdAgentClient>()`; pass it to the `VisitController` constructor; stub `doReturn(VisitFixtures.OTHER_AGENT).whenever(agentClient).get(30L)`.
- Add `a co-brokerage visit loads the listing agent` (verify `agentClient.get(30)`), `an own-inventory visit does not load another agent` (verify never), `an unreadable listing agent leaves the page without the card` (doThrow, `page.agent == null`, view "visit").
- `VisitTemplateTest`: a rendered co-brokerage visit contains `Visite organisée avec`, `Clarisse Mbarga`, `https://wa.me/237699000002` and `/agent?id=30&back=%2Fvisits%2F7001`; an own-inventory visit has no `data-visit-agent`.
- `HomeControllerTest`: stub `agentClient.get(30L)`, make `visitClient.search` return a visit on `LISTING.copy(agentId = 30)` and `listingClient.searchByIds` return it; assert `visits.upcoming.single().agentName == "Clarisse Mbarga"` and `agentUrl == "/agent?id=30&back=/%23visits-title"`; verify an unreadable agent leaves `agentName == "un autre agent"` and the page still renders.
- `HomeTemplateTest`: the home visit card shows `Avec Clarisse Mbarga`.
- `MandateControllerTest` / `MandateTemplateTest`: the signed-search visit card shows `Avec Clarisse Mbarga` with `back=/mandates/204%23matches-title`.
- Update the `HomeController` constructor calls in `HomeControllerTest`, `HomeTemplateTest` and `ProfileTemplateTest` with the new `agentClient` mock.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=VisitCardsTest,VisitViewsTest,VisitControllerTest,VisitTemplateTest,HomeControllerTest,HomeTemplateTest,MandateControllerTest,MandateTemplateTest,ProfileTemplateTest
```

Expected: PASS.

- [ ] **Step 10: Run the module's verification**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
    modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-card.html \
    modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-detail.html \
    modules/ndopify-partner-central/src/main/resources/templates/visit.html \
    modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt
git commit -m "feat(npc): show the listing agent on co-brokerage visit cards and page"
```

---

### Task 4: Partner Central — B sees the hosted visits

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/co-brokerage-visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `GET /v1/visits/co-brokerage` (Task 2), `CoBrokerageVisitSummary`, `ListingSummary`, `Agent`, `ListingVisitView`.
- Produces:
  - `NdVisitClient.coBrokerage(): List<CoBrokerageVisitSummary>` → `GET /v1/visits/co-brokerage` (no query)
  - `ListingVisitView.listingTitle: String? = null`, `ListingVisitView.listingUrl: String? = null`
  - `ListingVisitView.agentInitials: String? = null`, `ListingVisitView.video: Boolean = false`
  - `ListingViews.visits(visits: List<CoBrokerageVisitSummary>, agents: Map<Long, Agent>, tenant: Tenant, formatter: TenantFormatter, back: String, listingLabels: Map<Long, String> = emptyMap()): List<ListingVisitView>`
  - `ListingViews.listingLabel(summary: ListingSummary, locations: Map<Long, Location>): String` and `listingLabel(listing: Listing, locations: Map<Long, Location>): String`
  - `HomeController` model `coBrokerageVisits: List<ListingVisitView>`
  - `MandateController` model `coBrokerageVisits: List<ListingVisitView>`

- [ ] **Step 1: Write the failing client and view tests**

In `NdVisitClientTest`, mirror the `coBrokerageOnListing` test:

```kotlin
@Test
fun `co-brokerage without a listing reads the caller's visits`() {
    server.expect(requestTo("/v1/visits/co-brokerage"))
        .andRespond(withSuccess("""{"visits":[{"id":1,"listingId":501,"agentId":30,"status":"CONFIRMED"}]}""", MediaType.APPLICATION_JSON))

    val visits = client.coBrokerage()

    assertEquals(listOf(1L), visits.map { it.id })
    assertEquals(501L, visits.single().listingId)
}
```

In `ListingViewsTest`, update the four `ListingViews.visits(...)` calls to the new signature and add:

```kotlin
@Test
fun `co-brokerage visits carry the listing on the home block`() {
    val rows = ListingViews.visits(
        listOf(CoBrokerageVisitSummary(id = 1, listingId = 501, agentId = 20, scheduledAt = date(2026, 11, 22, 10, 30))),
        mapOf(20L to agent(20, "Serge", "Ngono", "+237699000001")),
        TENANT,
        TenantFormatter(TENANT),
        "/#co-brokerage-visits-title",
        mapOf(501L to "Appartement 2 chambres · Bonapriso"),
    )

    assertEquals("Appartement 2 chambres · Bonapriso", rows.single().listingTitle)
    assertEquals("/listings/501#visits-title", rows.single().listingUrl)
    assertEquals("/agent?id=20&back=/%23co-brokerage-visits-title", rows.single().agentUrl)
    assertEquals("SN", rows.single().agentInitials)
    assertFalse(rows.single().video)
}

@Test
fun `listing labels read the type, rooms and place`() {
    assertEquals(
        "Appartement 2 chambres · Bonapriso",
        ListingViews.listingLabel(
            ListingSummary(
                id = 501,
                cityId = 1000,
                neighborhoodId = 1001,
                propertyType = PropertyType.APARTMENT,
                rooms = 2,
            ),
            mapOf(1001L to Location(id = 1001, name = "Bonapriso")),
        ),
    )
}
```

Use the existing `date` and `agent` fixtures in `ListingViewsTest`. In the existing co-brokerage tests, also assert the card's avatar and icon: `agentInitials == "SN"` for a readable agent, `null` for an unreadable one, and `video == true` for the `VIDEO` row.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest,ListingViewsTest
```

Expected: FAIL — `coBrokerage()` and the new `visits` signature/labels do not exist.

- [ ] **Step 3: Add the client method**

In `NdVisitClient`, next to `coBrokerageOnListing`:

```kotlin
/** The caller's co-brokerage visits across all its listings; the home page's block. */
fun coBrokerage(): List<CoBrokerageVisitSummary> =
    rest.get()
        .uri("/v1/visits/co-brokerage")
        .retrieve()
        .body<SearchCoBrokerageVisitResponse>()
        ?.visits
        ?: emptyList()
```

- [ ] **Step 4: Extend the view and the builder**

`ListingVisitView` gains:

```kotlin
/** The listing's "type rooms · place", on the home and mandate blocks; null on the listing page. */
val listingTitle: String? = null,
/** The listing's page, when [listingTitle] is shown. */
val listingUrl: String? = null,
/** The organizer's initials, for the card's avatar; null when the agent can't be read. */
val agentInitials: String? = null,
/** A video visit: the card shows the video icon instead of the house. */
val video: Boolean = false,
```

`ListingViews.visits` becomes (import `com.wutsi.ndopify.visit.dto.VisitType`):

```kotlin
fun visits(
    visits: List<CoBrokerageVisitSummary>,
    agents: Map<Long, Agent>,
    tenant: Tenant,
    formatter: TenantFormatter,
    back: String,
    listingLabels: Map<Long, String> = emptyMap(),
): List<ListingVisitView> =
    visits
        .sortedWith(compareBy<CoBrokerageVisitSummary> { it.scheduledAt?.time }.thenBy { it.id })
        .map { visit ->
            val agent = agents[visit.agentId]
            val name = agent?.let { ProfileRules.fullName(it) }?.ifBlank { null }
            val initials = agent?.let { KycRules.initials(it.party.firstName, it.party.lastName) }?.ifBlank { null }
            val title = listingLabels[visit.listingId]
            ListingVisitView(
                at = visit.scheduledAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) }.orEmpty(),
                typeLabel = VisitRules.typeLabel(visit.type),
                inProgress = visit.status == VisitStatus.CHECKED_IN,
                agentName = name,
                agentUrl = name?.let { AgentProfileRules.profileUrl(visit.agentId, back) },
                whatsappUrl = name?.let { AgentProfileRules.whatsappUrl(agent?.whatsappNumber) },
                listingTitle = title,
                listingUrl = title?.let { "/listings/${visit.listingId}#visits-title" },
                agentInitials = initials,
                video = visit.type == VisitType.VIDEO,
            )
        }
```

Add the labels:

```kotlin
/** "Appartement 2 chambres · Bonapriso": the listing's line on the co-brokerage blocks. */
fun listingLabel(summary: ListingSummary, locations: Map<Long, Location>): String =
    label(summary.propertyType, summary.rooms, summary.neighborhoodId, summary.cityId, locations)

fun listingLabel(listing: Listing, locations: Map<Long, Location>): String =
    label(listing.propertyType, listing.rooms, listing.neighborhoodId, listing.cityId, locations)

private fun label(
    type: PropertyType?,
    rooms: Int?,
    neighborhoodId: Long?,
    cityId: Long?,
    locations: Map<Long, Location>,
): String {
    val typeLabel = MandateLabels.shortPropertyType(type) ?: "Bien"
    val roomsLabel = rooms?.let { MatchRules.rooms(it) }
    val place = listOfNotNull(neighborhoodId, cityId).firstNotNullOfOrNull { locations[it]?.name }
    return listOfNotNull(typeLabel, roomsLabel, place?.let { "· $it" }).joinToString(" ")
}
```

Update `ListingController.coBrokerageVisits` to:

```kotlin
return ListingViews.visits(visits, agents, tenantContext.getTenant(), formatter(), "/listings/${listing.id}#visits-title")
```

- [ ] **Step 5: Add the shared block fragment**

Create `templates/fragments/co-brokerage-visit.html`:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!-- One card of *Visites d'autres agents*: never A's customer, note, code, address, report or feedback. -->
<li th:fragment="coBrokerageVisit(v)" class="co-visit-card">
    <article>
        <div class="co-visit-card__head">
            <p class="co-visit-card__when">
                <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>
                <span th:text="${v.at}">dimanche 22 novembre 2026 à 10:30</span>
            </p>
            <p class="status-tag status-tag--info" th:if="${v.inProgress}">En cours</p>
        </div>
        <p class="co-visit-card__listing">
            <svg class="icon" th:unless="${v.video}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M3 10.5 12 3l9 7.5V21H3z"/><path d="M9 21v-6h6v6"/></svg>
            <svg class="icon" th:if="${v.video}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><rect x="2" y="6" width="14" height="12" rx="2"/><path d="m16 10 6-3v10l-6-3z"/></svg>
            <span th:text="${v.typeLabel}">Sur place</span>
            <th:block th:if="${v.listingTitle != null}">
                <span aria-hidden="true">·</span>
                <a th:if="${v.listingUrl != null}" th:href="@{${v.listingUrl}}" th:text="${v.listingTitle}">Appartement 2 chambres · Bonapriso</a>
                <span th:unless="${v.listingUrl != null}" th:text="${v.listingTitle}">Appartement 2 chambres · Bonapriso</span>
            </th:block>
        </p>
        <div class="co-visit-card__agent">
            <span class="co-visit-card__avatar" th:if="${v.agentInitials != null}" th:text="${v.agentInitials}"
                  aria-hidden="true">CM</span>
            <span class="co-visit-card__avatar" th:unless="${v.agentInitials != null}" aria-hidden="true">
                <svg class="icon" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" focusable="false"><circle cx="10" cy="6.5" r="3.25"/><path d="M4 17c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" stroke-linecap="round"/></svg>
            </span>
            <div class="co-visit-card__agent-info">
                <p class="co-visit-card__agent-line">
                    <span>Avec</span>
                    <a th:if="${v.agentUrl}" th:href="${v.agentUrl}" data-co-brokerage-agent>
                        <span th:text="${v.agentName}">Serge Ngono</span>
                        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg>
                    </a>
                    <span th:unless="${v.agentUrl}" th:text="${v.agentName} ?: 'un autre agent'" data-co-brokerage-agent>un autre agent</span>
                </p>
                <a class="co-visit-card__whatsapp" th:if="${v.whatsappUrl}" th:href="${v.whatsappUrl}" rel="noopener"
                   target="_blank" data-co-brokerage-whatsapp>
                    <svg class="icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413Z"/></svg>
                    WhatsApp
                </a>
            </div>
        </div>
    </article>
</li>
</body>
</html>
```

Add the `.co-visit-card` rules to `public/assets/css/listing.css`: keep the `.listing-visits ul` reset, then the bordered card, the when + "En cours" head, the type/listing line, and the organizer footer (avatar, "Avec …" link, WhatsApp) under a top rule. Replace the old `.listing-visits li` row rules (padding-block + `border-top` between rows) — the cards carry their own borders and spacing now.

Replace the inline `<li>` loop in `listing.html` with the fragment (keep the `th:block` pattern the other pages use, so `th:each` and `th:replace` don't fight):

```html
<th:block th:each="v : ${l.visits}">
    <li th:replace="~{fragments/co-brokerage-visit :: coBrokerageVisit(${v})}"></li>
</th:block>
```

- [ ] **Step 6: Add the home page reads and block**

`HomeController` (after Task 3's `agentClient`): add `com.wutsi.ndopify.agent.dto.Agent`, `com.wutsi.ndopify.listing.dto.ListingSummary` (already imported), `com.wutsi.ndopify.npc.ui.listing.ListingViews`, and `com.wutsi.ndopify.visit.dto.CoBrokerageVisitSummary` imports.

```kotlin
// Wave 1
val coBrokerageCall = parallel.async { loadCoBrokerage() }
...
val coBrokerage = coBrokerageCall.join()

// Wave 2
val coBrokerageListingsCall = parallel.async { coBrokerage?.let { loadCoBrokerageListings(it) } }
...
val coBrokerageListings = coBrokerageListingsCall.join()

// Wave 3
val coBrokerageAgentsCall = parallel.async { loadCoBrokerageAgents(coBrokerage) }
...
val coBrokerageAgents = coBrokerageAgentsCall.join()
```

Add the co-brokerage listings' locations to the `names(...)` call:

```kotlin
coBrokerageListings.orEmpty().values.flatMap { list -> listOfNotNull(list.cityId, list.neighborhoodId) }
```

Build the block:

```kotlin
val coBrokerageLabels = coBrokerageListings.orEmpty()
    .mapValues { (_, listing) -> ListingViews.listingLabel(listing, names) }
model.addAttribute(
    "coBrokerageVisits",
    coBrokerage?.let {
        ListingViews.visits(it, coBrokerageAgents, tenant, formatter, "/#co-brokerage-visits-title", coBrokerageLabels)
    }.orEmpty(),
)
```

Add the loaders:

```kotlin
/** The caller's co-brokerage visits; null when they can't be read (the block is left out). */
private fun loadCoBrokerage(): List<CoBrokerageVisitSummary>? =
    try {
        visitClient.coBrokerage()
    } catch (ex: RestClientException) {
        LOGGER.warn("No co-brokerage visits: ${ex.message}")
        null
    }

/** The visits' listings by id; null when they can't be read (the rows keep their number only). */
private fun loadCoBrokerageListings(visits: List<CoBrokerageVisitSummary>): Map<Long, ListingSummary>? =
    if (visits.isEmpty()) {
        emptyMap()
    } else {
        try {
            listingClient.searchByIds(visits.map { it.listingId }.distinct()).associateBy { it.id }
        } catch (ex: RestClientException) {
            LOGGER.warn("No listings for the co-brokerage visits: ${ex.message}")
            null
        }
    }

/** The agents of the co-brokerage visits, by id, read in parallel; an unreadable one is left out. */
private fun loadCoBrokerageAgents(visits: List<CoBrokerageVisitSummary>?): Map<Long, Agent> {
    val ids = visits.orEmpty().map { it.agentId }.filter { it > 0 }.distinct()
    val calls = ids.associateWith { id ->
        parallel.async {
            try {
                agentClient.get(id)
            } catch (ex: RestClientException) {
                LOGGER.warn("No agent $id for a co-brokerage visit: ${ex.message}")
                null
            }
        }
    }
    return calls.mapNotNull { (id, call) ->
        val agent = try {
            call.join()
        } catch (ex: Exception) {
            null
        }
        agent?.let { id to it }
    }.toMap()
}
```

Add the hosted visits to the triage's `todayCount`:

```kotlin
model.addAttribute(
    "todayCount",
    visitsView.upcoming.count { card ->
        card.at?.let { VisitSlots.localDate(Date(it), tenant) == today } == true
    } + coBrokerage.orEmpty().count { visit ->
        visit.scheduledAt?.let { VisitSlots.localDate(it, tenant) == today } == true
    },
)
```

In `index.html`, add `assets/css/listing.css` to the `<head>` and the block after the `data-visits-section` section:

```html
<section class="home-section listing-visits" aria-labelledby="co-brokerage-visits-title"
         th:if="${!#lists.isEmpty(coBrokerageVisits)}" data-co-brokerage-visits>
    <h2 id="co-brokerage-visits-title">Visites d'autres agents</h2>
    <p>Un autre agent organise ces visites de votre bien : vous accueillez l'agent et son client sur place.</p>
    <ul>
        <th:block th:each="v : ${coBrokerageVisits}">
            <li th:replace="~{fragments/co-brokerage-visit :: coBrokerageVisit(${v})}"></li>
        </th:block>
    </ul>
</section>
```

- [ ] **Step 7: Add the listing-mandate block**

In `MandateController.render`, after `listing` and `clients`:

```kotlin
val coBrokerage = listing
    ?.takeIf { it.status in setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD) }
    ?.let { coBrokerageVisits(mandate, it) }
    .orEmpty()
model.addAttribute("coBrokerageVisits", coBrokerage)
```

Add:

```kotlin
/**
 * The other agents' visits on the mandate's listing, soonest first, with their agents read in parallel. Secondary:
 * a failed visits read leaves the block out; an unreadable agent keeps its row as "un autre agent".
 */
private fun coBrokerageVisits(mandate: Mandate, listing: Listing): List<ListingVisitView> {
    val visits = lookup("co-brokerage visits of listing ${listing.id}") {
        visitClient.coBrokerageOnListing(listing.id)
    } ?: return emptyList()
    if (visits.isEmpty()) return emptyList()

    val calls = visits.map { it.agentId }.filter { it > 0 }.distinct()
        .associateWith { id -> parallel.async { lookup("agent $id") { agentClient.get(id) } } }
    val agents = calls.mapNotNull { (id, call) ->
        val agent = try {
            call.join()
        } catch (ex: Exception) {
            null
        }
        agent?.let { id to it }
    }.toMap()
    val locations = lookup("location names of listing ${listing.id}") {
        sector.locations(listOfNotNull(listing.cityId, listing.neighborhoodId))
    } ?: emptyMap()
    return ListingViews.visits(
        visits,
        agents,
        tenantContext.getTenant(),
        TenantFormatter(tenantContext.getTenant()),
        "/mandates/${mandate.id}#co-brokerage-visits-title",
        mapOf(listing.id to ListingViews.listingLabel(listing, locations)),
    )
}
```

In `mandate.html`, after `data-listing-proposals`, add the same section as the home page (id `co-brokerage-visits-title`, `data-co-brokerage-visits`).

- [ ] **Step 8: Write/complete the page tests**

- `HomeControllerTest`: stub `visitClient.coBrokerage()` with one `CoBrokerageVisitSummary` (listing 501, agent 30, CONFIRMED, today), `listingClient.searchByIds` with `LISTING` 501, `agentClient.get(30)` with `OTHER_AGENT`; assert `coBrokerageVisits.single().agentName == "Clarisse Mbarga"`, `agentInitials == "CM"`, `video == false`, `listingTitle` and `todayCount == 1`. Add `a failed co-brokerage read leaves the block out` (doThrow `ResourceAccessException`; the model has an empty list and the view is "index").
- `HomeTemplateTest`: the rendered home contains the section, `Visites d'autres agents`, the `co-visit-card` card with `CM` avatar, `Clarisse Mbarga`, `https://wa.me/237699000002` and `/listings/501#visits-title`; no `Sandrine Eboa`/`sandrine@gmail.com`/`1234` in the block. A `VIDEO` visit renders the video icon, not the house.
- `MandateControllerTest`: a PUBLISHED listing mandate stubs `visitClient.coBrokerageOnListing(501)`, `agentClient.get(30)`; asserts `coBrokerageVisits.single().agentName` and `agentInitials`; a DRAFT or a search mandate never calls `coBrokerageOnListing`; a failure leaves an empty list and still renders "mandate".
- `MandateTemplateTest`: the block renders the `co-visit-card`, A's avatar/link and WhatsApp, and nothing of A's customer.
- `ListingControllerTest` / `ListingTemplateTest`: the listing page's block still renders through the fragment (unchanged expected output).

- [ ] **Step 9: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest,ListingViewsTest,ListingControllerTest,ListingTemplateTest,HomeControllerTest,HomeTemplateTest,MandateControllerTest,MandateTemplateTest
```

Expected: PASS.

- [ ] **Step 10: Run the module's verification**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
    modules/ndopify-partner-central/src/main/resources/templates/fragments/co-brokerage-visit.html \
    modules/ndopify-partner-central/src/main/resources/templates/index.html \
    modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
    modules/ndopify-partner-central/src/main/resources/templates/listing.html \
    modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show hosted co-brokerage visits on the home and mandate pages"
```

---

### Task 5: Keep the module guidance true and verify the whole change

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (visit domain)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (visits, listing page, mandate page, Espace agent)

**Interfaces:**
- Consumes: everything produced by Tasks 2–4.
- Produces: the documentation that matches the deployed behaviour.

- [ ] **Step 1: Update the server guidance**

In the **visit** bullet of `modules/ndopify-server/CLAUDE.md`, after the `GET /v1/visits/co-brokerage?listing-id=` sentence, say that without `listing-id` it returns the caller agent's `CONFIRMED`/`CHECKED_IN` co-brokerage visits across all its `PUBLISHED`/`RENTED`/`SOLD` listings, soonest first; a caller without an agent is 403 `VISIT_ACCESS_DENIED`; the payload is the same `CoBrokerageVisitSummary` and never A's customer. No new DTO, no migration.

- [ ] **Step 2: Update the Partner Central guidance**

In `modules/ndopify-partner-central/CLAUDE.md`:
- In the Espace agent *Visites* paragraph, say a co-brokerage visit's card names the listing agent ("Avec …", linked to `/agent` with a back link), and that a new *Visites d'autres agents* block lists `GET /v1/visits/co-brokerage` (no `listing-id`) across the agent's live listings, counting in `todayCount` but not in `requestedCount`, never showing A's customer, and absent when the read fails.
- In the visit page paragraph, say the page shows the listing agent's boxed card ("Visite organisée avec", profile/WhatsApp) when the listing's agent isn't the visit's, loaded secondarily.
- In the listing mandate paragraph, say the same *Visites d'autres agents* block renders for a `PUBLISHED`/`RENTED`/`SOLD` listing, backed by `GET /v1/visits/co-brokerage?listing-id=`, and that the listing page's block now uses the shared fragment.

- [ ] **Step 3: Run both modules' full verification**

```bash
cd modules/ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
```

Expected: both BUILD SUCCESS. Confirm the root `pom.xml` still has `ndopify-dto.version` and `ndopify-web-core.version` unchanged (no release needed).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs: document the co-brokerage visit agent views"
```

- [ ] **Step 5: Prepare the PRs**

The story workflow owns the PRs. Open the `ndopify-server` PR first (`feat(visit): read an agent's co-brokerage visits`, `Refs #118`), then the `ndopify-partner-central` one (`feat(npc): show both agents on a co-brokerage visit`, `Closes #118`), each with green checks. No `ndopify-dto` or `ndopify-web-core` PR: neither module changed.
