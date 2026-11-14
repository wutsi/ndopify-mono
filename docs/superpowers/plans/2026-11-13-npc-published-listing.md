# NPC Published Listing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the published listing page the default for the listing agent and a co-broker, with one shared gallery + property sheet and audience-specific owner/co-broker blocks.

**Architecture:** `ListingController.show` resolves the viewer through a new `PublishedListingAccess`: the listing's agent is the owner; otherwise an explicit `match` id must be a visible co-brokerage match of one of the signed-in agent's searches. `ListingViews.of` projects the page into a shared `PublishedListingView` plus owner fields (public URL, stats, private location) and co-broker fields (publisher card). The template renders one page with audience blocks; the co-broker payload never contains the owner-only data.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, `TenantFormatter`, mockito-kotlin, `kotlin.test`, Jsoup template tests, MockMvc, vanilla CSS/JS.

**Spec:** `docs/superpowers/specs/2026-11-13-npc-published-listing-design.md`

## Global Constraints

- UI copy is French, exactly as the approved prototype (`modules/ndopify-partner-central/src/main/html/listing.html`, `?id=5002` owner, `?id=5001` co-broker); code/comments/docs are English.
- The prototypes in `src/main/html/` are the approved design reference: do not serve them, delete them, or “clean them up”; only `src/main/resources` and `src/main/kotlin` change in this plan.
- Dates and amounts go through `TenantFormatter`; no hard-coded `FCFA`, month names or date patterns.
- No new colours: use the existing tier-2 tokens in `partner-central.css`; reuse the existing `viewer__*` styles.
- The co-broker `ListingView` must not carry `publicUrl`, `stats`, `latitude`, `longitude`, `accessDirections` or a private location; the template must not receive them. Hiding with CSS/JS is not enough.
- `agentRemarks` is shown to both audiences (owner decision, 2026-11-13).
- Owner access is unchanged: section, photo and verify pages keep using `ListingAccess`; a co-broker can never open them.
- ndopify-server is unchanged; its listing read has no access check, so the BFF owns the co-brokerage decision.
- Tests use `kotlin.test`; mocks use mockito-kotlin; controller/template tests use `MockMvc`/real Thymeleaf, as the existing listing tests do.
- Format with `mvn validate` (ktlint) before the final commit.
- Work on branch `feat/npc-published-listing`.

## Review Focus

1. **A co-broker without a valid `match`** → the locked view, and the response contains no listing title, mandate id, customer, coordinates, access directions, public URL or stats.
2. **A match id for another listing, another agent's mandate, a dismissed match, or an unpublished listing** → locked, never the co-broker lens.
3. **An image read failure** → the sheet and page still render, with no gallery or “Voir les N photos” button, and no 500.
4. **An owner published listing with no GPS or access directions** → no empty “Accès et position” block and no co-broker note; the rest of the sheet renders.
5. **More than five photos** → the grid shows the hero + four, the viewer opens the clicked photo or the first for “Voir les N photos”, and closing returns focus to the visible trigger, never a hidden photo.
6. **A published listing with a public URL** → the public link, share button and stats appear only for the owner; the co-broker page contains none of them.

---

### Task 1: Published sheet view model

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViewsTest.kt`

**Interfaces:**
- Consumes: `Listing`, `ListingImage`, `Location`, `TenantFormatter`, `Amenity`, `MandateLabels`, `ImageStatus`.
- Produces:
  - `data class PublishedListingView(description, photos, facts, property, place, placeNote, amenities, terms, remarks, privateLocation)`
  - `data class GalleryPhotoView(url: String, alt: String)`
  - `data class FactView(icon: String, value: String)`
  - `data class SpecView(label: String, value: String)`
  - `data class PlaceView(neighbourhood: String?, city: String?, landmark: String?)`
  - `data class AmenityGroupView(label: String, items: List<String>)`
  - `data class PrivateLocationView(coordinates: String?, mapsUrl: String?, accessDirections: String?)`
  - `data class ListingAgentView(id, name, initials, photoUrl, whatsappUrl, whatsappNumber, profileUrl)`
  - `PublishedListingViews.of(listing: Listing, images: List<ListingImage>?, locations: Map<Long, Location>, formatter: TenantFormatter, owner: Boolean): PublishedListingView`

- [ ] **Step 1: Write the failing builder tests**

Use `ListingViewsTest.Companion` (`COMPLETE`, `IMAGES`, `LISTING`) and `ProfileFixtures.LOCATIONS`/`TENANT`; build an owner listing with `description = "Un appartement lumineux.\n\nLibre en novembre."`, `agentRemarks = "Libre depuis le 1er novembre."`, `accessDirections = "Immeuble bleu, 3e porte."`, `heroImageId = 2`.

```kotlin
@Test
fun `owner - the whole sheet`() {
    val view = PublishedListingViews.of(listing, IMAGES, locations, formatter, owner = true)

    assertEquals("Un appartement lumineux.\n\nLibre en novembre.", view.description)
    assertEquals(listOf(FactView("bed", "2 chambres"), FactView("area", "85 m²")), view.facts)
    assertEquals(
        listOf(SpecView("Type de bien", "Appartement"), SpecView("Chambres", "2"), SpecView("Surface", "85 m²")),
        view.property,
    )
    assertEquals(PlaceView("Bonapriso", "Douala", "Derrière l'école publique"), view.place)
    assertEquals(
        listOf(
            AmenityGroupView("Accès à l'eau", listOf("Compteur d'eau")),
            AmenityGroupView("Accès à l'électricité", listOf("Groupe électrogène")),
            AmenityGroupView("Accès routier", listOf("Route bitumée")),
        ),
        view.amenities,
    )
    assertEquals(
        listOf(
            SpecView("Loyer", "150\u00a0000\u00a0FCFA / mois"),
            SpecView("Caution", "3 mois (450\u00a0000\u00a0FCFA)"),
            SpecView("Disponible", "À partir du 01 nov. 2026"),
        ),
        view.terms,
    )
    assertEquals("Libre depuis le 1er novembre.", view.remarks)
    assertNull(view.placeNote)
    assertEquals(
        PrivateLocationView("4.03350, 9.69420", "https://maps.google.com/?q=4.0335,9.6942", "Immeuble bleu, 3e porte."),
        view.privateLocation,
    )
}

@Test
fun `co-broker - no private location and the shared note`() {
    val view = PublishedListingViews.of(listing, IMAGES, locations, formatter, owner = false)

    assertNull(view.privateLocation)
    assertEquals("L'adresse exacte et l'itinéraire sont communiqués par l'agent avant la visite.", view.placeNote)
    assertEquals(listing.description, view.description)
    assertEquals(listing.agentRemarks, view.remarks)
}

@Test
fun `photos - hero first, rejected and blank left out`() {
    val images = IMAGES + ListingImage(id = 4, url = "https://cdn/4.jpg", status = ImageStatus.VERIFIED)
        + ListingImage(id = 5, url = "", status = ImageStatus.VERIFIED)
    val view = PublishedListingViews.of(listing.copy(heroImageId = 4), images, locations, formatter, owner = true)

    assertEquals(listOf("https://cdn/4.jpg", "https://cdn/1.jpg", "https://cdn/2.jpg"), view.photos.map { it.url })
    assertEquals("Photo 1", view.photos.first().alt)
}

@Test
fun `no gps or access - no private block`() {
    val view = PublishedListingViews.of(
        listing.copy(latitude = null, longitude = null, accessDirections = null),
        IMAGES,
        locations,
        formatter,
        owner = true,
    )

    assertNull(view.privateLocation)
}

@Test
fun `images unavailable - the sheet still renders`() {
    val view = PublishedListingViews.of(listing, null, locations, formatter, owner = true)

    assertTrue(view.photos.isEmpty())
    assertTrue(view.facts.isNotEmpty())
}
```

- [ ] **Step 2: Run it**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingViewsTest`
Expected: FAIL — the classes do not exist.

- [ ] **Step 3: Implement the data classes and builder**

Create `PublishedListingView.kt` with the data classes above; `PublishedListingViews` implements:
- key facts in order: bed (`2 chambres`), bath (`1 salle de bains`), area (`85 m²`), furnished only for `FULLY_FURNISHED`, parking;
- property specs: type, furniture, rooms, bathrooms, living rooms, kitchens, surface, parkings — skip nulls;
- place: neighbourhood/city names from `locations`, `landmark`;
- amenities: `Amenity.of(listing).groupBy { it.group }`, preserving enum order, labels only;
- terms: rent via `MandateLabels.price(Money(price, currencyCode, pricePeriodicityType), formatter)` when `price > 0`; deposit/advance as `"N mois (money)"` when the count is > 0, `"Sans caution"` / `"Sans avance"` when 0; availability via `formatter.date(LocalDate)`; lease term and notice period;
- photos: non-blank, non-`REJECTED`, hero (`heroImageId`) first, others in image order; alt from `title`, else `description`, else `Photo N` where N is the position in the returned list (hero first);
- private location only when `owner` and there is a GPS pair or access directions; `placeNote` only when not owner.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViewsTest.kt
git commit -m "feat(npc): build the published listing sheet"
```

---

### Task 2: The owner/co-broker lens on `ListingView`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: Task 1's `PublishedListingView`, `PublishedListingViews.of`, `ListingAgentView`.
- Produces:
  - `ListingView.coBrokerage: Boolean = false`, `published: PublishedListingView? = null`, `agent: ListingAgentView? = null`, `backUrl: String = "/mandates/$mandateId"`, `backLabel: String = "← Retour au mandat"`.
  - `ListingViews.of(..., coBrokerage: Boolean = false, agent: ListingAgentView? = null, backUrl: String = ..., backLabel: String = ...)`
  - `ListingViews.agent(agent: Agent, listing: Listing, match: Match): ListingAgentView`

- [ ] **Step 1: Write the failing lens tests**

Add to `ListingViewsTest`:

```kotlin
@Test
fun `published owner - the sheet, the public url and the stats`() {
    val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT, description = "Texte public",
        url = "/douala/bonapriso/listing/501/appartement")
    val view = ListingViews.of(
        listing, IMAGES, locations, formatter, "https://ndopify.com",
        ListingStats(501, 2, 1, 2),
    )

    assertNotNull(view.published)
    assertEquals("Texte public", view.published.description)
    assertEquals("https://ndopify.com/douala/bonapriso/listing/501/appartement", view.publicUrl)
    assertEquals("2 personnes intéressées · 3 visites", view.stats)
    assertEquals(7, view.rows.size)
    assertEquals("/mandates/203", view.backUrl)
    assertFalse(view.coBrokerage)
}

@Test
fun `published co-broker - no public url, stats, checklist or private location`() {
    val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT, description = "Texte public",
        accessDirections = "Porte de droite", url = "/douala/bonapriso/listing/501/appartement")
    val agent = ListingAgentView(30, "Clarisse Mbarga", "CM", null, null, "+237699123456", "/agent?id=30")
    val view = ListingViews.of(
        listing, IMAGES, locations, formatter, "https://ndopify.com", ListingStats(501, 2, 1, 2),
        coBrokerage = true, agent = agent, backUrl = "/mandates/204#matches-title",
        backLabel = "← Retour aux biens correspondants",
    )

    assertEquals("Appartement, Bonapriso", view.title)
    assertTrue(view.coBrokerage)
    assertNull(view.publicUrl)
    assertNull(view.stats)
    assertTrue(view.rows.isEmpty())
    assertEquals("", view.progress)
    assertEquals("", view.missing)
    assertFalse(view.editable)
    assertNull(view.published?.privateLocation)
    assertEquals(agent, view.agent)
    assertEquals("/mandates/204#matches-title", view.backUrl)
    assertEquals("← Retour aux biens correspondants", view.backLabel)
}

@Test
fun `draft - the checklist stays the page`() {
    val view = of()

    assertNull(view.published)
    assertFalse(view.coBrokerage)
    assertEquals(7, view.rows.size)
}
```

- [ ] **Step 2: Run it**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest`
Expected: FAIL — the new fields/parameters do not exist.

- [ ] **Step 3: Implement the lens**

In `ListingView.kt` add the fields (with defaults) and in `ListingViews.of`:
- compute `title`; for `coBrokerage`, strip the `"Annonce · "` prefix;
- set `published = if (listing.status in PUBLIC) PublishedListingViews.of(listing, images, locations, formatter, owner = !coBrokerage) else null`;
- set `publicUrl` only when `!coBrokerage`; set `stats` only when `!coBrokerage`;
- set `rows = emptyList()`, `progress = ""`, `missing = ""`, `editable = false`, `sendable = false`, `panel = null` for a co-broker;
- pass `coBrokerage`, `agent`, `backUrl`, `backLabel`.

Add `ListingViews.agent(agent, listing, match)`:
- name `ProfileRules.fullName(agent)`, initials `KycRules.initials(...)`, photo from `agent.party.photoUrl`;
- `whatsappUrl = AgentProfileRules.whatsappUrl(agent.whatsappNumber)`, display `agent.whatsappNumber`;
- `profileUrl = UriComponentsBuilder.fromPath("/agent").queryParam("id", agent.id).queryParam("back", "/listings/${listing.id}?match=${match.id}").build().encode().toUriString()`.

Keep `heroImageUrl` for now; the template still uses it until Task 5.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest`
Expected: PASS. (`ListingControllerTest`/`ListingTemplateTest` still pass: the controller does not pass the new arguments yet, and the defaults preserve the owner lens.)

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): add the published listing owner and co-broker lenses"
```

---

### Task 3: Co-brokerage page access

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccess.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccessTest.kt`

**Interfaces:**
- Consumes: `NdListingClient.get`, `NdMatchClient.get`, `NdMandateClient.get`, `MatchRules.ORDER`, `Agent`, `Listing`, `Match`, `Mandate`.
- Produces:
  - `sealed interface PublishedListingAccess.Result`
  - `data class Owner(val listing: Listing) : Result`
  - `data class CoBroker(val listing: Listing, val match: Match) : Result`
  - `data object Locked : Result`, `data object Unavailable : Result`
  - `fun load(id: Long, agent: Agent, matchId: Long?): Result`

- [ ] **Step 1: Write the failing access tests**

Use `ProfileFixtures.AGENT`, `ListingViewsTest.COMPLETE`, and a local fixture:

```kotlin
private val listing = COMPLETE.copy(status = ListingStatus.PUBLISHED)
private val match = Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE)
private val search = Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH,
    status = MandateStatus.APPROVED)
```

Tests (each stubs the three clients):
- owner: `listing.agentId == AGENT.id` → `Owner`, and `verify(matchClient, never()).get(any())`.
- co-broker: `listing.copy(agentId = 999)`, valid match/search → `CoBroker`.
- no `matchId` → `Locked`.
- listing `UNKNOWN` status → `Locked`.
- non-co-brokerage match → `Locked`.
- match for another listing → `Locked`.
- `DISMISSED`/`REJECTED` match → `Locked`.
- another agent's search (`search.agentId = 999`) → `Locked`.
- `RENTED` listing for a co-broker → `Locked`.
- listing 404 → `Locked`; listing 500/`ResourceAccessException` → `Unavailable`.
- match 404 → `Locked`; match 500 → `Unavailable`.
- mandate 404 → `Locked`; mandate 500 → `Unavailable`.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingAccessTest`
Expected: FAIL — class missing.

- [ ] **Step 2: Implement `PublishedListingAccess`**

Map the listing fetch with the same error policy as `ListingAccess` (4xx → `Locked`, other `RestClientException` → `Unavailable`); reject `id <= 0` or `ListingStatus.UNKNOWN` first. After the owner check:
- require `matchId != null` and `listing.status == ListingStatus.PUBLISHED`;
- `matchClient.get(matchId)`; require `match.listingId == listing.id`, `match.coBrokerage`, `match.status in MatchRules.ORDER`;
- `mandateClient.get(match.mandateId)`; require `mandate.agentId == agent.id && mandate.mandateType == MandateType.SEARCH`;
- return `CoBroker(listing, match)`.

- [ ] **Step 3: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PublishedListingAccessTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccess.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingAccessTest.kt
git commit -m "feat(npc): authorize the co-brokerage listing page by match"
```

---

### Task 4: Controller wiring and the match link

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html:397`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: Tasks 2 and 3.
- Produces:
  - `ListingController(currentAgent, listingClient, mandateClient, locationClient, tenantContext, matchClient, agentClient)`
  - `show(@PathVariable id, @RequestParam(required = false) match: String?, model)`
  - `MatchView.listingUrl` includes `?match={matchId}` for co-brokerage matches.

- [ ] **Step 1: Write the failing controller tests**

Add `matchClient`/`agentClient` mocks to `ListingControllerTest`/`ListingTemplateTest` setups. Add:

```kotlin
@Test
fun `show - co-brokerage listing`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT, description = "Public"))
        .whenever(listingClient).get(501)
    doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
        .whenever(matchClient).get(9009)
    doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
        .whenever(mandateClient).get(204)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val view = model(mvc.perform(get("/listings/501?match=9009")).andReturn())["listing"] as ListingView

    assertTrue(view.coBrokerage)
    assertNotNull(view.agent)
    assertNull(view.publicUrl)
    assertNull(view.stats)
    assertEquals("/mandates/204#matches-title", view.backUrl)
    verify(listingClient, never()).stats(any())
}

@Test
fun `show - co-brokerage listing without a match is locked`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999)).whenever(listingClient).get(501)

    val result = mvc.perform(get("/listings/501")).andReturn()

    assertEquals(true, model(result)["locked"])
}
```

Add to `MatchViewsTest`:

```kotlin
@Test
fun `co-brokerage card links with its match`() {
    val section = section(listOf(MATCH_A.copy(coBrokerage = true)))

    assertEquals("/listings/3012?match=9001", section.matches.single().listingUrl)
}
```

Add to `MandateTemplateTest` a render of a co-brokerage match whose `data-match-link` href is `/listings/3012?match=9001` (use the existing match fixture and `copy(coBrokerage = true)`).

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,MatchViewsTest,MandateTemplateTest'`
Expected: FAIL — the constructor/param/link do not exist.

- [ ] **Step 2: Implement the controller**

Constructor: add `matchClient: NdMatchClient`, `agentClient: NdAgentClient`; keep `mandateClient`; replace the `ListingAccess` field with `private val publishedAccess = PublishedListingAccess(listingClient, matchClient, mandateClient)`.
`show`: add `@RequestParam(required = false) match: String?`. Resolve `publishedAccess.load(id, agent, match?.toLongOrNull())`; map `Locked`/`Unavailable` to the existing locked view (`Unavailable` also sets `UNAVAILABLE`). Keep the `outcome` handling.
For the owner: `stats` only when `listing.status == PUBLISHED`; `backUrl = "/mandates/${listing.mandateId}"`, `backLabel = "← Retour au mandat"`.
For the co-broker: `stats = null`; `agent` card via `lookup("listing agent ...") { agentClient.get(listing.agentId) }?.let { ListingViews.agent(it, listing, result.match) }`; `backUrl = "/mandates/${result.match.mandateId}#matches-title"`, `backLabel = "← Retour aux biens correspondants"`.
Pass `coBrokerage`, `agent`, `backUrl`, `backLabel` to `ListingViews.of`.

- [ ] **Step 3: Implement the match link**

In `MatchViews.view`: `listingUrl = if (match.coBrokerage) "/listings/${listing.id}?match=${match.id}" else "/listings/${listing.id}"`.
In `templates/mandate.html:397`: change `th:href="@{${match.listingUrl}}"` to `th:href="${match.listingUrl}"` so the query string survives.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,MatchViewsTest,MandateTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt \
    modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): route the co-brokerage match to the published listing"
```

---

### Task 5: The published template

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt` (remove `heroImageUrl` if unused)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt` (same)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: Tasks 2/4 (`l.published`, `l.coBrokerage`, `l.agent`, `l.backUrl`, `l.backLabel`).
- Produces: the server-rendered page; the data attributes below are the JS contract.

- [ ] **Step 1: Write the failing template tests**

Update the existing `published`/hero tests and add:

```kotlin
@Test
fun `published - gallery, about and sheet`() {
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT, heroImageId = 2,
        description = "Un appartement lumineux.\n\nLibre en novembre."))

    val gallery = page.selectFirst("[data-listing-gallery]")!!
    assertEquals("https://cdn/2.jpg", gallery.selectFirst("[data-photo-link] img")?.attr("src"))
    assertEquals("Voir les 2 photos", gallery.selectFirst("[data-photo-open]")?.text())
    assertEquals("https://cdn/2.jpg", gallery.selectFirst("[data-photo-open]")?.attr("href"))
    assertNotNull(page.selectFirst("[data-listing-about]"))
    assertEquals(
        "Un appartement lumineux.\n\nLibre en novembre.",
        page.selectFirst("[data-listing-about] .listing-description")?.wholeText(),
    )
    assertNotNull(page.selectFirst("[data-listing-details]"))
    assertNotNull(page.selectFirst("[data-listing-sheet]"))
    assertEquals("2 chambres", page.selectFirst("[data-listing-facts] li")?.text())
}

@Test
fun `published - more than five photos are in the viewer, five in the grid`() {
    val images = (1L..7L).map { ListingImage(id = it, url = "https://cdn/$it.jpg", status = ImageStatus.VERIFIED) }
    doReturn(images).whenever(listingClient).images(501)
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

    assertEquals(7, page.select("[data-photo-link]").size)
    assertEquals(2, page.select("[data-photo-link].listing-gallery__item--extra").size)
    assertEquals("Voir les 7 photos", page.selectFirst("[data-photo-open]")?.text())
}

@Test
fun `published - images unavailable leaves the sheet, without a gallery`() {
    doThrow(ResourceAccessException("down")).whenever(listingClient).images(501)
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

    assertNull(page.selectFirst("[data-listing-gallery]"))
    assertNotNull(page.selectFirst("[data-listing-details]"))
}

@Test
fun `published owner - no gps, no access, no private block`() {
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT,
        latitude = null, longitude = null, accessDirections = null))

    assertNull(page.selectFirst(".listing-private"))
    assertNull(page.selectFirst(".listing-place__note"))
}

@Test
fun `published - owner keeps the public link, share, stats and private location`() {
    doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()
    doReturn(mapOf(501L to ListingStats(501, 2, 1, 2))).whenever(listingClient).stats(listOf(501L))
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT,
        url = "/douala/bonapriso/listing/501/appartement", accessDirections = "Porte de droite"))

    assertNotNull(page.selectFirst("[data-listing-public-url]"))
    assertNotNull(page.selectFirst("[data-listing-share]"))
    assertEquals(
        "2 personnes intéressées · 3 visites",
        page.selectFirst("[data-listing-stats] > span > span:last-child")?.text(),
    )
    assertNotNull(page.selectFirst(".listing-private"))
    assertTrue(page.selectFirst(".listing-private")!!.text().contains("4.03350, 9.69420"))
    assertTrue(page.selectFirst(".listing-private")!!.text().contains("Porte de droite"))
}

@Test
fun `published - verification is collapsed`() {
    val page = render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT))

    val details = page.selectFirst("[data-listing-verification]")!!
    assertFalse(details.hasAttr("open"))
    assertEquals("published", details.attr("data-mode"))
    assertEquals("Vérification de l'annonce", details.selectFirst("summary")?.text())
    assertEquals("Sections vérifiées", details.selectFirst("h2")?.text())
}

@Test
fun `draft - verification stays open and the checklist is the page`() {
    val details = render().selectFirst("[data-listing-verification]")!!

    assertTrue(details.hasAttr("open"))
    assertEquals("edit", details.attr("data-mode"))
    assertEquals(7, render().select("a.checklist__row").size)
    assertNull(render().selectFirst("[data-listing-gallery]"))
}

@Test
fun `co-broker - no owner-only block, agent card instead`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT,
        description = "Public", url = "/douala/bonapriso/listing/501/appartement"))
        .whenever(listingClient).get(501)
    doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
        .whenever(matchClient).get(9009)
    doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
        .whenever(mandateClient).get(204)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    val page = render(match = "9009")

    assertNull(page.selectFirst("[data-listing-public-url]"))
    assertNull(page.selectFirst("[data-listing-share]"))
    assertNull(page.selectFirst("[data-listing-stats]"))
    assertNull(page.selectFirst("[data-listing-verification]"))
    assertNull(page.selectFirst(".listing-private"))
    assertEquals("/mandates/204#matches-title", page.selectFirst(".back-link a")?.attr("href"))
    assertEquals("← Retour aux biens correspondants", page.selectFirst(".back-link a")?.text())
    assertNotNull(page.selectFirst("[data-listing-agent]"))
    assertNotNull(page.selectFirst("[data-listing-origin]"))
    assertNull(page.selectFirst("[data-listing-meta]"))
    assertNotNull(page.selectFirst("[data-listing-gallery]"))
    assertNotNull(page.selectFirst("[data-listing-details]"))
}
```

The `render` helper takes an optional `match` string and appends `?match=` to the request.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingTemplateTest,ListingViewsTest'`
Expected: FAIL — the template has no gallery/sheet/verification.

- [ ] **Step 2: Implement the template shell**

Follow the approved prototype markup (`src/main/html/listing.html`) and the server model:
- Back link: `th:href="${l.backUrl}"`, `th:text="${l.backLabel}"`.
- Meta line: `p.mandate__meta[data-listing-meta]` with `th:if="${!l.coBrokerage}"`.
- Origin line: `th:if="${l.coBrokerage}"`.
- Public link/share: existing, `th:if="${l.publicUrl}"` (null for a co-broker).
- Stats: existing, `th:if="${l.stats}"`.

- [ ] **Step 3: Add the gallery and “À propos”**

- Gallery, `th:if="${l.published != null and !#lists.isEmpty(l.published.photos)}"`:
  - `section.listing-gallery[data-listing-gallery]`, `div.listing-gallery__grid`.
  - `a.listing-gallery__item[data-photo-link]` for every photo; `--hero` on `stat.first`; `--extra` when `stat.index >= 5`; `href="${photo.url}"`, `img src="${photo.url}" alt="${photo.alt}"`.
  - `a.btn.btn--secondary.btn--compact.listing-gallery__all[data-photo-open]` with `href` of the first photo and `th:text="|Voir les ${#lists.size(l.published.photos)} photos|"`.
- About, `th:if="${l.published != null and l.published.description != null}"`: `section[data-listing-about]`, `div.listing-description` with `th:text`.

- [ ] **Step 4: Add the property sheet**

`section[data-listing-details]` when `l.published != null`, `ul.listing-facts[data-listing-facts]` with `th:each="fact : ${l.published.facts}"` and a `th:switch` on `fact.icon` for the five SVGs; `div.listing-sheet[data-listing-sheet]`:
- *Le bien*: `dl.listing-specs` over `l.published.property`;
- *Emplacement*: `p.listing-place` with pin icon + city/neighbourhood/landmark; `.listing-private` over `l.published.privateLocation` (lock icon + “Accès et position” + “Visible par vous uniquement”, coords, Maps link, access); else the note from `l.published.placeNote` when non-null;
- *Équipements*: over `l.published.amenities`, groups with h4 + check list;
- *Loyer et conditions*: over `l.published.terms`;
- *Remarques de l'agent*: over `l.published.remarks`.

- [ ] **Step 5: Add the verification disclosure and the publisher card**

- Verification: `<details data-listing-verification th:if="${!l.coBrokerage}" th:attr="open=${l.published == null}, data-mode=${l.published == null ? 'edit' : 'published'}">`, summary “Vérification de l’annonce”, the existing checklist section with h2 “Sections vérifiées”.
- Co-broker publisher card, `th:if="${l.agent != null}"`: the prototype's `listing-agent` markup, with `agent.photoUrl`/initials, name, WhatsApp link/display, profile link; `data-listing-agent`.
- Remove the old `<img class="listing-hero" data-listing-hero>` and the `heroImageUrl` field/usages if no longer referenced.

- [ ] **Step 6: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingTemplateTest,ListingViewsTest'`
Expected: PASS. Update the old `hero image` tests to the gallery assertions.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/listing.html \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): render the published listing sheet for owners and co-brokers"
```

---

### Task 6: Gallery, sheet and private-location styles

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css`

**Interfaces:**
- Consumes: the class/attribute contract from Task 5.
- Produces: no new colours; all rules use the existing tier-2 tokens.

- [ ] **Step 1: Port the styles**

Port the approved prototype’s `listing-gallery`, `listing-description`, `listing-facts`, `listing-sheet`, `listing-notes`, `listing-private`, `listing-verification` rules from
`modules/ndopify-partner-central/src/main/html/assets/css/listing.css` into the production stylesheet. Add:

```css
.listing-gallery__item--extra { display: none; }
.listing-description { white-space: pre-line; }
```

Keep the existing `.listing-public`, `.listing-hero` only if still referenced (Task 5 removes the old hero element; delete the rule with it). Do not touch the existing `.viewer__*` rules.

- [ ] **Step 2: Verify the stylesheet**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: PASS (Markup unaffected). There is no CSS unit test; open the rendered page in a browser and compare it with the prototype at 390px and 1024px: hero + 4 thumbnails from 640px, swipeable strip below, sheet hairline blocks, blue private box, collapsed verification.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css
git commit -m "feat(npc): style the published listing gallery and sheet"
```

---

### Task 7: Reuse the photo viewer on the listing page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js`

**Interfaces:**
- Consumes: `[data-photo-link]` (gallery anchors) and `[data-photo-open]` (the “Voir les N photos” anchor) from Task 5.
- Produces: the existing `viewer` dialog; no new dependency.

- [ ] **Step 1: Wire the viewer**

In `initListing()`, call `initViewer()` (already defined for the photos page). In `initViewer()`:
- keep `links = $all("[data-photo-link]")`; do **not** add the `[data-photo-open]` anchor to `links`;
- guard the note lookup (`var item = link.closest("li"); var note = item ? $("[data-photo-note]", item) : null;`);
- store the click target as the opener and pass it to `open(i, opener)`;
- bind every `[data-photo-open]` to `open(0, button)` with `preventDefault`, so no-JS still follows the anchor to the first photo;
- on `dialog` close, focus the stored opener (not `links[current]`, which may be a hidden extra);
- add a `figcaption` from `note?.textContent || thumb.alt` so the listing photos keep their caption.

- [ ] **Step 2: Verify**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: PASS (markup). In a browser: click the hero and each thumbnail (opens the right photo), click “Voir les N photos” (opens the first), arrow/swipe through all photos, `Escape`, and check focus returns to the trigger. With JavaScript disabled, the anchors open the full photo in a tab.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js
git commit -m "feat(npc): open the published gallery in the photo viewer"
```

---

## Final Verification

- [ ] Run the module’s full test suite: `cd modules/ndopify-partner-central && mvn test`
- [ ] Run the style gate: `cd modules/ndopify-partner-central && mvn validate`
- [ ] Confirm this plan’s commits contain no new `src/main/html/` changes (the prototype is the design reference).
- [ ] Compare the rendered production page with the prototype (`?id=5002` owner, `?id=5001` co-broker) at 390px and 1024px: owner-only blocks, co-broker card, remarks, collapsed verification, gallery, private location, no public URL/share/stats for the co-broker.
