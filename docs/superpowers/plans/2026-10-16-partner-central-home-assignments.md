# Espace agent — Mandats proposés Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the signed-in agent, on the Espace agent (`/`), the mandates ndopify-server currently offers them
(`PENDING` assignments), each with its deadline, and let them accept or decline it — without JavaScript, enhanced by
it.

**Architecture:** `HomeController` loads the agent's `PENDING` assignments (`GET /v1/assignments?agent-id=&status=`),
the mandate of each (`GET /v1/mandates/{id}`) and the location names in one batch, and turns them into
`AssignmentView`s (French labels, no customer data). A new `AssignmentController` posts accept / decline to
ndopify-server and redirects back to `/` with a flash outcome (post-redirect-get, like the profile pages). The
labels the find-property wizard already has move to a shared `MandateLabels`, so both pages word a mandate the same
way. `home.js` only enhances: live countdown, in-place decline confirmation.

**Tech Stack:** Kotlin, Spring Boot MVC + Thymeleaf, Spring `RestClient`, JUnit 5 + mockito-kotlin + `kotlin.test`,
standalone `MockMvc`, `MockRestServiceServer`, Jsoup (template tests), plain ES5 JS.

**Spec:** this conversation's design + the prototype `modules/ndopify-partner-central/src/main/html/index.html`
(section *Mandats proposés*, lines 96–322) and its JS `src/main/html/assets/js/partner-central.js`
(`initAssignments`, lines 937–1145) and CSS `src/main/html/assets/css/home.css` (lines 93–232). Server side:
`docs/superpowers/plans/2026-10-14-mandate-dispatch.md`.

## Global Constraints

- Module: `modules/ndopify-partner-central`, package `com.wutsi.ndopify.npc`. Never depend on `ndopify-server` at
  build time — REST only, DTOs from `ndopify-dto` (`com.wutsi.ndopify.mandate.dto.*`).
- **Never modify or delete anything under `src/main/html/`** (the prototype). Copy from it, never edit it.
- UI copy is French, copied from the prototype where it exists.
- **Anti-bypass:** before acceptance, the page shows no customer data: no `customerId`, no `comments` (free text
  that may hold a phone number), only the city and neighbourhoods as location.
- Agent id always comes from `CurrentAgent` (the token), never from a request parameter. The assignment id may come
  from the URL: ndopify-server checks the caller is its assignee (401/403 `ASSIGNMENT_ACCESS_DENIED`).
- Assertions: `kotlin.test` only (`assertEquals`, `assertNull`, …), never JUnit's `Assertions`.
- Every page works without JS; JS only enhances.
- Gate: `cd modules/ndopify-partner-central && mvn verify` (ktlint + tests + JaCoCo thresholds) must pass at the end
  of every task. Auto-format with `mvn antrun:run@ktlint-format`.

## Review Focus

1. **An offer past its `expiresAt` that the server still reports `PENDING`** (the expire job runs every few minutes):
   it must not be listed — accepting it would get a 409. → `AssignmentViewsTest.expired offers are left out`.
2. **One mandate of the list can't be loaded** (404/5xx): only that offer is left out, the others still show. →
   `HomeControllerTest.one mandate unavailable - the other offers still show`.
3. **The offer was taken/expired between page load and click** (409 `ASSIGNMENT_NOT_PENDING`) or isn't the agent's
   (403): the agent gets "Ce mandat n'est plus disponible", never an error page. →
   `AssignmentControllerTest.accept - no longer pending` / `decline - not the assignee`.
4. **Mandate comments leaking customer contact**: `comments` is never rendered. →
   `AssignmentViewsTest.comments and customer are never shown` + `HomeTemplateTest` asserts the text is absent.
5. **`?decline=<id>` with an id that isn't one of the agent's offers** (tampered URL): no confirmation panel, no
   error. → `HomeControllerTest.decline confirmation - unknown offer ignored`.

Known, out of scope (pre-existing): CSRF is disabled app-wide (`SecurityConfiguration`), so accept/decline are as
CSRF-exposed as the profile forms; `GET /v1/mandates/{id}` isn't scoped to the assignee server-side.

---

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAssignmentClient.kt` | `/v1/assignments`: pending offers of an agent, accept, decline |
| Modify `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt` | add `get(id)` |
| Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` | French labels of a mandate, shared by find-property and home |
| Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt` | use `MandateLabels` (no behaviour change) |
| Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentView.kt` | what a card shows |
| Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentViews.kt` | assignment + mandate + names → `AssignmentView` |
| Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt` | loads the offers, `?decline=` |
| Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/assignment/AssignmentController.kt` | `POST /assignments/{id}/accept|decline` |
| Modify `src/main/resources/templates/index.html` | the *Mandats proposés* section |
| Modify `src/main/resources/public/assets/css/home.css` | port the prototype's assignment styles |
| Create `src/main/resources/public/assets/js/home.js` | countdown + in-place decline confirmation |
| Tests under `src/test/kotlin/com/wutsi/ndopify/npc/…` mirroring each | |
| Modify `CLAUDE.md` (repo root) | document the section |

All paths below are relative to `modules/ndopify-partner-central/` unless they start with `CLAUDE.md` / `docs/`.

---

### Task 1: REST clients

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAssignmentClient.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAssignmentClientTest.kt`,
  `src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`

**Interfaces:**
- Produces:
  - `NdAssignmentClient.pending(agentId: Long): List<AssignmentSummary>`
  - `NdAssignmentClient.accept(id: Long)` / `NdAssignmentClient.decline(id: Long)` — throw `RestClientException`
    (`HttpClientErrorException` for 4xx) like every client
  - `NdMandateClient.get(id: Long): Mandate`

- [ ] **Step 1: Write the failing tests**

`NdAssignmentClientTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdAssignmentClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdAssignmentClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdAssignmentClient("http://localhost:8080", builder)
    }

    @Test
    fun pending() {
        server.expect(requestTo("http://localhost:8080/v1/assignments?agent-id=20&status=PENDING"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"assignments":[{"id":7,"mandateId":101,"agentId":20,"status":"PENDING","score":0.8,""" +
                        """"createdAt":0,"modifiedAt":0,"expiresAt":4102444800000}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val assignments = client.pending(20)

        server.verify()
        assertEquals(listOf(7L), assignments.map { it.id })
        assertEquals(101L, assignments[0].mandateId)
        assertEquals(AssignmentStatus.PENDING, assignments[0].status)
    }

    @Test
    fun accept() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/accept"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.accept(7)

        server.verify()
    }

    @Test
    fun decline() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/decline"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.decline(7)

        server.verify()
    }

    @Test
    fun `accept - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/accept"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.accept(7) }
    }
}
```

Append to `NdMandateClientTest.kt` (add `import com.wutsi.ndopify.mandate.dto.MandateType` is already there):

```kotlin
    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/101"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"mandate":{"id":101,"mandateType":"TENANT","cityId":2232593,"neighborhoodIds":[3001]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val mandate = client.get(101)

        server.verify()
        assertEquals(101L, mandate.id)
        assertEquals(MandateType.TENANT, mandate.mandateType)
        assertEquals(listOf(3001L), mandate.neighborhoodIds)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn test -Dtest='NdAssignmentClientTest,NdMandateClientTest'`
Expected: compilation failure (`NdAssignmentClient`, `get` unresolved).

- [ ] **Step 3: Implement**

`NdAssignmentClient.kt`:

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server assignment endpoints (`/v1/assignments`): the offers of a mandate to an agent.
 * Accept and decline are only for the offer's agent: ndopify-server checks it from the `Authorization` token.
 */
@Service
class NdAssignmentClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The agent's open offers, newest first. */
    fun pending(agentId: Long): List<AssignmentSummary> =
        rest.get()
            .uri("/v1/assignments?agent-id={agentId}&status={status}", agentId, AssignmentStatus.PENDING)
            .retrieve()
            .body<SearchAssignmentResponse>()
            ?.assignments
            ?: emptyList()

    fun accept(id: Long) {
        rest.post().uri("/v1/assignments/{id}/accept", id).retrieve().toBodilessEntity()
    }

    fun decline(id: Long) {
        rest.post().uri("/v1/assignments/{id}/decline", id).retrieve().toBodilessEntity()
    }
}
```

In `NdMandateClient.kt` add imports `com.wutsi.ndopify.mandate.dto.GetMandateResponse`,
`com.wutsi.ndopify.mandate.dto.Mandate`, and:

```kotlin
    fun get(id: Long): Mandate =
        rest.get()
            .uri("/v1/mandates/{id}", id)
            .retrieve()
            .body<GetMandateResponse>()
            ?.mandate
            ?: Mandate()
```

- [ ] **Step 4: Run them to see them pass**

Run: `mvn test -Dtest='NdAssignmentClientTest,NdMandateClientTest'` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAssignmentClient.kt \
        src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt \
        src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAssignmentClientTest.kt \
        src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt
git commit -m "npc: assignment client, get a mandate"
```

---

### Task 2: Shared mandate labels

Move the labels out of `FindPropertyController`'s companion into `ui/mandate/MandateLabels`, add what the home page
needs. The find-property tests (`FindPropertyControllerTest`, `FindPropertyTemplateTest`) must pass **unchanged**:
they are the refactor's safety net.

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt:64-140`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`

**Interfaces:**
- Produces (`object MandateLabels`):
  - `PROPERTY_TYPE_LABELS: Map<PropertyType, String>`, `ROOM_LABELS: Map<String, String>`,
    `FURNITURE_LABELS: Map<String, String>`, `PARKING_LABELS: Map<String, String>`,
    `MOVE_IN_LABELS: Map<MovingPeriod, String>`, `AMENITY_LABELS: Map<String, String>` (moved verbatim)
  - `formatNumber(value: Number): String` (moved verbatim)
  - `mandateType(type: MandateType): String` — `TENANT` → "Locataire", `OWNER` → "Bailleur", else "Mandat"
  - `category(category: PropertyCategory): String?` — "Résidentiel" / "Commercial" / null
  - `propertyType(type: PropertyType?): String` — label, or "Tout type de bien"
  - `furniture(type: FurnitureType?): String` — "Oui" / "Non" / "Indifférent" (null or UNKNOWN)
  - `price(money: Money): String` — "150 000 FCFA par mois" (`XAF` → "FCFA"; `MONTHLY` → " par mois")
  - `amenityGroups(mandate: Mandate): List<Pair<String, String>>` — ("Accès à l'eau", "Compteur d'eau, Forage"),
    … only non-empty groups, in the order eau, électricité, routier, sécurité

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateLabelsTest {
    @Test
    fun mandateType() {
        assertEquals("Locataire", MandateLabels.mandateType(MandateType.TENANT))
        assertEquals("Bailleur", MandateLabels.mandateType(MandateType.OWNER))
        assertEquals("Mandat", MandateLabels.mandateType(MandateType.UNKNOWN))
    }

    @Test
    fun category() {
        assertEquals("Résidentiel", MandateLabels.category(PropertyCategory.RESIDENTIAL))
        assertEquals("Commercial", MandateLabels.category(PropertyCategory.COMMERCIAL))
        assertNull(MandateLabels.category(PropertyCategory.UNKNOWN))
    }

    @Test
    fun propertyType() {
        assertEquals("Appartement", MandateLabels.propertyType(PropertyType.APARTMENT))
        assertEquals("Tout type de bien", MandateLabels.propertyType(null))
        assertEquals("Tout type de bien", MandateLabels.propertyType(PropertyType.UNKNOWN))
    }

    @Test
    fun furniture() {
        assertEquals("Oui", MandateLabels.furniture(FurnitureType.FULLY_FURNISHED))
        assertEquals("Non", MandateLabels.furniture(FurnitureType.UNFURNISHED))
        assertEquals("Indifférent", MandateLabels.furniture(null))
    }

    @Test
    fun price() {
        assertEquals(
            "150 000 FCFA par mois",
            MandateLabels.price(Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
        )
        assertEquals("2 500 CAD", MandateLabels.price(Money(amount = 2500, currency = "CAD")))
    }

    @Test
    fun amenityGroups() {
        val mandate = Mandate(waterMeter = true, waterWell = true, securityGuard = true)

        assertEquals(
            listOf("Accès à l'eau" to "Compteur d'eau, Forage", "Sécurité" to "Gardien"),
            MandateLabels.amenityGroups(mandate),
        )
        assertEquals(emptyList(), MandateLabels.amenityGroups(Mandate()))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn test -Dtest=MandateLabelsTest` — Expected: compilation failure (`MandateLabels` unresolved).

- [ ] **Step 3: Implement**

`MandateLabels.kt` — move `PROPERTY_TYPE_LABELS`, `ROOM_LABELS`, `FURNITURE_LABELS`, `PARKING_LABELS`,
`MOVE_IN_LABELS`, `AMENITY_LABELS` and `formatNumber` **verbatim** from `FindPropertyController`'s companion (lines
70–139; make the private ones public; `FURNITURE_LABELS` / `PARKING_LABELS` use `FindPropertyRules.ANY`, keep that
import). Then add:

```kotlin
    private val MANDATE_TYPE_LABELS = mapOf(MandateType.TENANT to "Locataire", MandateType.OWNER to "Bailleur")

    private val CATEGORY_NAMES = mapOf(
        PropertyCategory.RESIDENTIAL to "Résidentiel",
        PropertyCategory.COMMERCIAL to "Commercial",
    )

    // The prototype's "Voir les détails" rows; keys are AMENITY_LABELS'.
    private val AMENITY_GROUPS = linkedMapOf(
        "Accès à l'eau" to listOf("waterMeter", "dedicatedWaterMeter", "waterTank", "waterWell"),
        "Accès à l'électricité" to listOf("electricityMeter", "dedicatedElectricityMeter", "generatorSet", "solarPanel"),
        "Accès routier" to listOf("roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar"),
        "Sécurité" to listOf("securedFenced", "securityGuard", "videoSurveillance"),
    )

    fun mandateType(type: MandateType): String = MANDATE_TYPE_LABELS[type] ?: "Mandat"

    fun category(category: PropertyCategory): String? = CATEGORY_NAMES[category]

    fun propertyType(type: PropertyType?): String = PROPERTY_TYPE_LABELS[type] ?: "Tout type de bien"

    fun furniture(type: FurnitureType?): String = when (type) {
        FurnitureType.FULLY_FURNISHED -> "Oui"
        FurnitureType.UNFURNISHED -> "Non"
        else -> "Indifférent"
    }

    /** "150 000 FCFA par mois". */
    fun price(money: Money): String {
        val currency = if (money.currency == "XAF") "FCFA" else money.currency
        val period = if (money.periodicity == PeriodicityType.MONTHLY) " par mois" else ""
        return "${formatNumber(money.amount)} $currency$period"
    }

    fun amenityGroups(mandate: Mandate): List<Pair<String, String>> {
        val checked = mapOf(
            "waterMeter" to mandate.waterMeter,
            "dedicatedWaterMeter" to mandate.dedicatedWaterMeter,
            "waterTank" to mandate.waterTank,
            "waterWell" to mandate.waterWell,
            "electricityMeter" to mandate.electricityMeter,
            "dedicatedElectricityMeter" to mandate.dedicatedElectricityMeter,
            "generatorSet" to mandate.generatorSet,
            "solarPanel" to mandate.solarPanel,
            "roadPaved" to mandate.roadPaved,
            "trackAccessibleByCar" to mandate.trackAccessibleByCar,
            "trackInaccessibleByCar" to mandate.trackInaccessibleByCar,
            "securedFenced" to mandate.securedFenced,
            "securityGuard" to mandate.securityGuard,
            "videoSurveillance" to mandate.videoSurveillance,
        )
        return AMENITY_GROUPS.mapNotNull { (group, keys) ->
            val labels = keys.filter { checked[it] == true }.mapNotNull { AMENITY_LABELS[it] }
            if (labels.isEmpty()) null else group to labels.joinToString(", ")
        }
    }
```

Header KDoc: `/** The French wording of a mandate, shared by the find-property review and the Espace agent's offers. */`

In `FindPropertyController`: delete the moved members from the companion (keep `CATEGORY_LABELS` and
`CONTACT_PERIOD_LABELS`, only used there), and replace every use with `MandateLabels.X` (or
`import com.wutsi.ndopify.npc.ui.mandate.MandateLabels.PROPERTY_TYPE_LABELS` etc.). Remove the now-unused
`java.util.Locale` / `MovingPeriod` / `PropertyType` imports if ktlint flags them.

- [ ] **Step 4: Run the labels and find-property tests**

Run: `mvn test -Dtest='MandateLabelsTest,FindProperty*Test'` — Expected: PASS, find-property tests untouched.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate \
        src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt
git commit -m "npc: share the mandate labels"
```

---

### Task 3: Assignment view model

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentView.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentViews.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentViewsTest.kt`

**Interfaces:**
- Consumes: `MandateLabels.*` (Task 2), `FindPropertyRules.needsRooms/needsFurniture/asksParkings(type: String?)`,
  `ProfileRules.area(city: Location?, neighborhoods: List<Location>): String?`
- Produces:
  - `data class AssignmentView(id: Long, title: String, sectorTerm: String, sector: String?, priceTerm: String,
    price: String, movingTerm: String, moving: String?, expiresAt: Long, remaining: String,
    details: List<Pair<String, String>>)`
  - `AssignmentViews.isOpen(assignment: AssignmentSummary, now: Long): Boolean`
  - `AssignmentViews.of(assignment: AssignmentSummary, mandate: Mandate, locations: Map<Long, Location>, now: Long): AssignmentView`
  - `AssignmentViews.remaining(ms: Long): String` — "1 h 05 min", "52 min", "moins d'1 min"

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AKWA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BONAPRISO
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.LOCATIONS
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssignmentViewsTest {
    companion object {
        const val NOW = 1_800_000_000_000L
        private const val MINUTE = 60_000L

        val ASSIGNMENT = AssignmentSummary(
            id = 7,
            mandateId = 101,
            agentId = 20,
            status = AssignmentStatus.PENDING,
            expiresAt = Date(NOW + 52 * MINUTE),
        )

        val MANDATE = Mandate(
            id = 101,
            customerId = 555,
            mandateType = MandateType.TENANT,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            furnitureType = FurnitureType.UNFURNISHED,
            rooms = "2",
            parkings = "1",
            area = 80,
            cityId = DOUALA,
            neighborhoodIds = listOf(BONAPRISO, AKWA),
            waterMeter = true,
            waterWell = true,
            securityGuard = true,
            price = Money(amount = 150000, currency = "XAF", periodicity = PeriodicityType.MONTHLY),
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            comments = "Appelez-moi au 677 12 34 56",
        )

        val NAMES = LOCATIONS.associateBy { it.id }
    }

    @Test
    fun card() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW)

        assertEquals(7L, view.id)
        assertEquals("Locataire · Appartement", view.title)
        assertEquals("Secteurs", view.sectorTerm)
        assertEquals("Bonapriso, Akwa — Douala", view.sector)
        assertEquals("Budget", view.priceTerm)
        assertEquals("150 000 FCFA par mois", view.price)
        assertEquals("Emménagement", view.movingTerm)
        assertEquals("Dans moins d'1 mois", view.moving)
        assertEquals(NOW + 52 * 60_000L, view.expiresAt)
        assertEquals("52 min", view.remaining)
    }

    @Test
    fun details() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW)

        assertEquals(
            listOf(
                "Catégorie" to "Résidentiel",
                "Type de propriété" to "Appartement",
                "Meublé" to "Non",
                "Chambres" to "2",
                "Parking" to "1",
                "Surface souhaitée" to "80 m²",
                "Accès à l'eau" to "Compteur d'eau, Forage",
                "Sécurité" to "Gardien",
            ),
            view.details,
        )
    }

    @Test
    fun `owner - one neighbourhood`() {
        val view = AssignmentViews.of(
            ASSIGNMENT,
            MANDATE.copy(mandateType = MandateType.OWNER, neighborhoodIds = listOf(BONAPRISO)),
            NAMES,
            NOW,
        )

        assertEquals("Bailleur · Appartement", view.title)
        assertEquals("Secteur", view.sectorTerm)
        assertEquals("Loyer", view.priceTerm)
        assertEquals("Disponibilité", view.movingTerm)
        assertEquals("Surface", view.details.first { it.first.startsWith("Surface") }.first)
    }

    @Test
    fun `questions that don't apply to the type are left out`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE.copy(propertyType = PropertyType.LAND), NAMES, NOW)

        val terms = view.details.map { it.first }
        assertFalse("Chambres" in terms)
        assertFalse("Parking" in terms)
        assertFalse("Meublé" in terms)
    }

    @Test
    fun `comments and customer are never shown`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, NAMES, NOW)

        assertFalse(view.toString().contains("677"))
        assertFalse(view.toString().contains("555"))
    }

    @Test
    fun `location names unavailable`() {
        val view = AssignmentViews.of(ASSIGNMENT, MANDATE, emptyMap(), NOW)

        assertEquals(null, view.sector)
    }

    @Test
    fun `expired offers are left out`() {
        assertTrue(AssignmentViews.isOpen(ASSIGNMENT, NOW))
        assertFalse(AssignmentViews.isOpen(ASSIGNMENT.copy(expiresAt = Date(NOW)), NOW))
        assertFalse(AssignmentViews.isOpen(ASSIGNMENT.copy(status = AssignmentStatus.ACCEPTED), NOW))
    }

    @Test
    fun remaining() {
        assertEquals("1 h 05 min", AssignmentViews.remaining(65 * 60_000L))
        assertEquals("52 min", AssignmentViews.remaining(52 * 60_000L + 30_000L))
        assertEquals("moins d'1 min", AssignmentViews.remaining(30_000L))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn test -Dtest=AssignmentViewsTest` — Expected: compilation failure.

- [ ] **Step 3: Implement**

(`ProfileRules.area(city, neighborhoods)` returns null without a city; `FindPropertyRules.WITHOUT_FURNITURE` is
LAND, BUILDING, INDUSTRIAL and `WITHOUT_PARKINGS` LAND, ROOM — so LAND drops Meublé, Chambres and Parking.)

`AssignmentView.kt`:

```kotlin
package com.wutsi.ndopify.npc.ui.home

/**
 * An offer, as its card on the Espace agent shows it. Anti-bypass: nothing about the customer (no id, no comments),
 * and the neighbourhoods are the only location, until the agent accepts.
 */
data class AssignmentView(
    val id: Long,
    val title: String,
    val sectorTerm: String,
    val sector: String?,
    val priceTerm: String,
    val price: String,
    val movingTerm: String,
    val moving: String?,
    /** Epoch ms, for home.js' countdown. */
    val expiresAt: Long,
    /** The time left when the page was rendered: what the page shows without JS. */
    val remaining: String,
    /** The "Voir les détails" rows: term → value. */
    val details: List<Pair<String, String>>,
)
```

`AssignmentViews.kt`:

```kotlin
package com.wutsi.ndopify.npc.ui.home

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.refdata.dto.Location

/** Builds the offers' cards of the Espace agent (see [AssignmentView]), worded as the prototype's index.html. */
object AssignmentViews {
    private const val MINUTE = 60_000L

    /** Still open: PENDING and before its deadline (ndopify-server's expire job may not have run yet). */
    fun isOpen(assignment: AssignmentSummary, now: Long): Boolean =
        assignment.status == AssignmentStatus.PENDING && assignment.expiresAt.time > now

    fun of(assignment: AssignmentSummary, mandate: Mandate, locations: Map<Long, Location>, now: Long): AssignmentView {
        val owner = mandate.mandateType == MandateType.OWNER
        val neighborhoods = mandate.neighborhoodIds.mapNotNull { locations[it] }
        return AssignmentView(
            id = assignment.id,
            title = MandateLabels.mandateType(mandate.mandateType) + " · " + MandateLabels.propertyType(mandate.propertyType),
            sectorTerm = if (mandate.neighborhoodIds.size > 1) "Secteurs" else "Secteur",
            sector = ProfileRules.area(locations[mandate.cityId], neighborhoods),
            priceTerm = if (owner) "Loyer" else "Budget",
            price = MandateLabels.price(mandate.price),
            movingTerm = if (owner) "Disponibilité" else "Emménagement",
            moving = MandateLabels.MOVE_IN_LABELS[mandate.movingPeriod],
            expiresAt = assignment.expiresAt.time,
            remaining = remaining(assignment.expiresAt.time - now),
            details = details(mandate, owner),
        )
    }

    /** "1 h 05 min", "52 min", "moins d'1 min". */
    fun remaining(ms: Long): String {
        val minutes = ms / MINUTE
        return when {
            minutes < 1 -> "moins d'1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
        }
    }

    private fun details(mandate: Mandate, owner: Boolean): List<Pair<String, String>> {
        val type = mandate.propertyType?.name
        return listOfNotNull(
            MandateLabels.category(mandate.propertyCategory)?.let { "Catégorie" to it },
            "Type de propriété" to MandateLabels.propertyType(mandate.propertyType),
            ("Meublé" to MandateLabels.furniture(mandate.furnitureType)).takeIf { FindPropertyRules.needsFurniture(type) },
            mandate.rooms?.let { "Chambres" to it }?.takeIf { FindPropertyRules.needsRooms(type) },
            mandate.parkings?.let { "Parking" to it }?.takeIf { FindPropertyRules.asksParkings(type) },
            mandate.area?.let { (if (owner) "Surface" else "Surface souhaitée") to MandateLabels.formatNumber(it) + " m²" },
        ) + MandateLabels.amenityGroups(mandate)
    }
}
```


- [ ] **Step 4: Run it to see it pass**

Run: `mvn test -Dtest=AssignmentViewsTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentView*.kt \
        src/test/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentViewsTest.kt
git commit -m "npc: offers' cards of the Espace agent"
```

---

### Task 4: HomeController loads the offers

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt:71-74` (constructor)

**Interfaces:**
- Consumes: `NdAssignmentClient.pending`, `NdMandateClient.get` (Task 1), `AssignmentViews` (Task 3), `Clock` bean
  (`config/ClockConfiguration`)
- Produces (model attributes of `index`, used by Task 6):
  - `assignments: List<AssignmentView>?` — null when the agent isn't VERIFIED (section not rendered)
  - `assignmentsUnavailable: Boolean` — the search failed; `assignments` is then an empty list
  - `declining: Long?` — the offer whose decline confirmation is open (`GET /?decline={id}`), only if listed
- Constructor: `HomeController(currentAgent, kycClient, assignmentClient, mandateClient, locationClient, tenantContext, clock)`

- [ ] **Step 1: Write the failing tests**

In `HomeControllerTest`: add mocks and wire the new constructor.

```kotlin
    private val assignmentClient = mock<NdAssignmentClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val clock = mock<Clock>()
```

In `setUp()`, before building `mvc`:

```kotlin
        doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(any())
```

and build with
`HomeController(currentAgent, kycClient, assignmentClient, mandateClient, locationClient, tenantContext, clock)`.

New tests:

```kotlin
    @Test
    fun `offers shown`() {
        val other = ASSIGNMENT.copy(id = 8, mandateId = 102)
        doReturn(listOf(ASSIGNMENT, other)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)
        doReturn(MANDATE.copy(id = 102)).whenever(mandateClient).get(102)

        val result = mvc.perform(get("/")).andReturn()

        @Suppress("UNCHECKED_CAST")
        val assignments = model(result)["assignments"] as List<AssignmentView>
        assertEquals(listOf(7L, 8L), assignments.map { it.id })
        assertEquals("Bonapriso, Akwa — Douala", assignments[0].sector)
        assertEquals(false, model(result)["assignmentsUnavailable"])
        assertNull(model(result)["declining"])
    }

    @Test
    fun `no offer`() {
        val result = mvc.perform(get("/")).andReturn()

        assertEquals(emptyList<AssignmentView>(), model(result)["assignments"])
    }

    @Test
    fun `expired offer left out`() {
        doReturn(listOf(ASSIGNMENT.copy(expiresAt = Date(AssignmentViewsTest.NOW - 1))))
            .whenever(assignmentClient).pending(AGENT.id)

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(emptyList<AssignmentView>(), model(result)["assignments"])
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `one mandate unavailable - the other offers still show`() {
        doReturn(listOf(ASSIGNMENT, ASSIGNMENT.copy(id = 8, mandateId = 102))).whenever(assignmentClient).pending(AGENT.id)
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(101)
        doReturn(MANDATE.copy(id = 102)).whenever(mandateClient).get(102)

        val result = mvc.perform(get("/")).andReturn()

        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(8L), (model(result)["assignments"] as List<AssignmentView>).map { it.id })
    }

    @Test
    fun `offers unavailable - page still shown`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).pending(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(emptyList<AssignmentView>(), model(result)["assignments"])
        assertEquals(true, model(result)["assignmentsUnavailable"])
    }

    @Test
    fun `not verified - no offers section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["assignments"])
        verify(assignmentClient, never()).pending(any())
    }

    @Test
    fun `decline confirmation`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

        val result = mvc.perform(get("/").param("decline", "7")).andReturn()

        assertEquals(7L, model(result)["declining"])
    }

    @Test
    fun `decline confirmation - unknown offer ignored`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

        val result = mvc.perform(get("/").param("decline", "999")).andReturn()

        assertNull(model(result)["declining"])
    }
```

Imports to add: `AssignmentViewsTest.Companion.ASSIGNMENT` / `MANDATE`, `NdAssignmentClient`, `NdMandateClient`,
`AssignmentSummary`, `java.time.Clock`, `java.util.Date`, `com.nhaarman.mockitokotlin2.doThrow`,
`org.springframework.http.HttpStatus`, `org.springframework.web.client.HttpClientErrorException`.

In `ProfileTemplateTest.setUp()`, the `HomeController(...)` call becomes
`HomeController(currentAgent, kycClient, assignmentClient, mandateClient, locationClient, tenantContext, clock)`
with the same three mocks and the same two `doReturn`s (empty offers) — its existing assertions don't change.

- [ ] **Step 2: Run them to see them fail**

Run: `mvn test -Dtest='HomeControllerTest,ProfileTemplateTest'` — Expected: compilation failure (constructor).

- [ ] **Step 3: Implement**

In `HomeController`: new constructor parameters `private val assignmentClient: NdAssignmentClient`,
`private val mandateClient: NdMandateClient`, `private val clock: Clock`; update the class KDoc ("its profile
section, and the mandates offered to the agent ([assignments])"); then:

```kotlin
    @GetMapping("/")
    fun show(@RequestParam(required = false) decline: Long?, model: Model): String {
        val agent = currentAgent.get() ?: return SIGN_OUT

        ProfileRules.header(agent, model)
        model.addAttribute("fullName", ProfileRules.fullName(agent))
        model.addAttribute("photoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("biography", agent.biography?.trim()?.ifEmpty { null })
        model.addAttribute("area", area(agent))
        model.addAttribute("kyc", kyc(agent))
        if (agent.party.kycStatus == KycStatus.VERIFIED) {
            val assignments = assignments(agent)
            model.addAttribute("assignments", assignments ?: emptyList<AssignmentView>())
            model.addAttribute("assignmentsUnavailable", assignments == null)
            // GET /?decline={id}: the decline confirmation without JS, for one of the listed offers only.
            model.addAttribute("declining", assignments?.firstOrNull { it.id == decline }?.id)
        }
        return VIEW
    }

    /**
     * The offers still open, newest first; null when they can't be had. An offer whose mandate can't be loaded is
     * left out, so one bad mandate doesn't hide the others.
     */
    private fun assignments(agent: Agent): List<AssignmentView>? {
        val now = clock.millis()
        val open =
            try {
                assignmentClient.pending(agent.id).filter { AssignmentViews.isOpen(it, now) }
            } catch (ex: RestClientException) {
                LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
                return null
            }

        val mandates = open.mapNotNull { assignment ->
            try {
                assignment to mandateClient.get(assignment.mandateId)
            } catch (ex: RestClientException) {
                LOGGER.warn("No mandate ${assignment.mandateId} for offer ${assignment.id}: ${ex.message}")
                null
            }
        }
        val names = names(mandates.flatMap { (_, mandate) -> listOf(mandate.cityId) + mandate.neighborhoodIds })
        return mandates.map { (assignment, mandate) -> AssignmentViews.of(assignment, mandate, names, now) }
    }

    /** One lookup for every card; without the names, the cards still show (no sector). */
    private fun names(ids: List<Long>): Map<Long, Location> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            try {
                sector.locations(ids.distinct())
            } catch (ex: RestClientException) {
                LOGGER.warn("No location names for the offers: ${ex.message}")
                emptyMap()
            }
        }
```

`sector.locations(ids: List<Long>): Map<Long, Location>` is the one `area()` already uses. Add imports
(`RequestParam`, `Clock`, `Location`, the two clients).

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest='HomeControllerTest,ProfileTemplateTest'` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
        src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
        src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt
git commit -m "npc: load the agent's offers on the Espace agent"
```

---

### Task 5: Accept / decline

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/assignment/AssignmentController.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/assignment/AssignmentControllerTest.kt`

**Interfaces:**
- Consumes: `NdAssignmentClient.accept/decline` (Task 1)
- Produces: `POST /assignments/{id}/accept`, `POST /assignments/{id}/decline` → `redirect:/` with flash attribute
  `assignment` = `AssignmentController.ACCEPTED` ("accepted") | `DECLINED` ("declined") | `CLOSED` ("closed") |
  `FAILED` ("error"). `/assignments/**` is authenticated by `SecurityConfiguration`'s `anyRequest()` — no change there.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.assignment

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals

class AssignmentControllerTest {
    private val client = mock<NdAssignmentClient>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(AssignmentController(client)).build()
    }

    private fun outcome(result: MvcResult) = result.flashMap["assignment"]

    @Test
    fun accept() {
        val result = mvc.perform(post("/assignments/7/accept")).andReturn()

        verify(client).accept(7)
        assertEquals("/", result.response.redirectedUrl)
        assertEquals("accepted", outcome(result))
    }

    @Test
    fun decline() {
        val result = mvc.perform(post("/assignments/7/decline")).andReturn()

        verify(client).decline(7)
        assertEquals("/", result.response.redirectedUrl)
        assertEquals("declined", outcome(result))
    }

    @Test
    fun `accept - no longer pending`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(client).accept(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/accept")).andReturn()))
    }

    @Test
    fun `decline - not the assignee`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(client).decline(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/decline")).andReturn()))
    }

    @Test
    fun `accept - unknown offer`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(client).accept(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/accept")).andReturn()))
    }

    @Test
    fun `accept - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(client).accept(7)

        val result = mvc.perform(post("/assignments/7/accept")).andReturn()

        assertEquals("/", result.response.redirectedUrl)
        assertEquals("error", outcome(result))
    }

    @Test
    fun `decline - unreachable`() {
        doThrow(ResourceAccessException("timeout")).whenever(client).decline(7)

        assertEquals("error", outcome(mvc.perform(post("/assignments/7/decline")).andReturn()))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn test -Dtest=AssignmentControllerTest` — Expected: compilation failure.

- [ ] **Step 3: Implement**

```kotlin
package com.wutsi.ndopify.npc.ui.assignment

import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Accepting or declining an offer of the Espace agent, then back to it with the outcome (flash attribute
 * [OUTCOME]). The assignment id comes from the URL: ndopify-server only lets the offer's agent (from the token) act
 * on it. An offer that's no longer open (expired, already answered: 409; someone else's: 403; gone: 404) is
 * [CLOSED], not an error: the page it was on was just stale.
 */
@Controller
@RequestMapping("/assignments")
class AssignmentController(
    private val client: NdAssignmentClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssignmentController::class.java)

        const val OUTCOME = "assignment"
        const val ACCEPTED = "accepted"
        const val DECLINED = "declined"
        const val CLOSED = "closed"
        const val FAILED = "error"

        private val CLOSED_STATUSES = setOf(403, 404, 409)
    }

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: Long, redirect: RedirectAttributes): String =
        answer(id, ACCEPTED, redirect) { client.accept(id) }

    @PostMapping("/{id}/decline")
    fun decline(@PathVariable id: Long, redirect: RedirectAttributes): String =
        answer(id, DECLINED, redirect) { client.decline(id) }

    private fun answer(id: Long, outcome: String, redirect: RedirectAttributes, call: () -> Unit): String {
        val result =
            try {
                call()
                outcome
            } catch (ex: HttpClientErrorException) {
                LOGGER.warn("Offer $id not $outcome: ${ex.statusCode}")
                if (ex.statusCode.value() in CLOSED_STATUSES) CLOSED else FAILED
            } catch (ex: RestClientException) {
                LOGGER.error("Offer $id not $outcome", ex)
                FAILED
            }
        redirect.addFlashAttribute(OUTCOME, result)
        return "redirect:/"
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `mvn test -Dtest=AssignmentControllerTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/npc/ui/assignment src/test/kotlin/com/wutsi/ndopify/npc/ui/assignment
git commit -m "npc: accept or decline an offer"
```

---

### Task 6: The *Mandats proposés* section (template + CSS)

**Files:**
- Modify: `src/main/resources/templates/index.html` (after the profile `</section>`, before `</div></main>`)
- Modify: `src/main/resources/public/assets/css/home.css` (append)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: model attributes of Task 4 (`assignments`, `assignmentsUnavailable`, `declining`), flash `assignment`
  of Task 5, `AssignmentView` fields of Task 3.
- Produces (DOM hooks for Task 7): `[data-assignments]`, `[data-assignment][data-assignment-id][data-expires-at]`,
  `[data-timer-value]`, `[data-actions]`, `a[data-decline]`, `[data-decline-confirm]`, `[data-decline-cancel]`,
  `[data-assignments-status]`, `[data-assignments-empty]`, `[data-assignment-outcome]`.

- [ ] **Step 1: Write the failing template test**

`HomeTemplateTest.kt` — same engine setup as `ProfileTemplateTest` (copy its `setUp`/`tearDown`/`render`, with
`HomeController(currentAgent, kycClient, assignmentClient, mandateClient, locationClient, tenantContext, clock)` and
`AssignmentController(assignmentClient)` in `standaloneSetup`, `SignedInAdviceTest.signIn()`,
`ProfileFixtures.mockLocations`, `doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()`,
`doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)`,
`doReturn(MANDATE).whenever(mandateClient).get(101)`). Tests:

```kotlin
    @Test
    fun offer() {
        val page = render(get("/"))

        val card = page.selectFirst("[data-assignment]")!!
        assertEquals("7", card.attr("data-assignment-id"))
        assertEquals((AssignmentViewsTest.NOW + 52 * 60_000L).toString(), card.attr("data-expires-at"))
        assertEquals("Locataire · Appartement", card.selectFirst("h3")?.text())
        assertEquals("52 min", card.selectFirst("[data-timer-value]")?.text())
        assertEquals("Bonapriso, Akwa — Douala", card.selectFirst("[data-fact=sector]")?.text())
        assertEquals("150 000 FCFA par mois", card.selectFirst("[data-fact=price]")?.text())
        assertEquals("/assignments/7/accept", card.selectFirst("form[data-accept-form]")?.attr("action"))
        assertEquals("/?decline=7#assignment-7", card.selectFirst("a[data-decline]")?.attr("href"))
        assertTrue(card.selectFirst("[data-decline-confirm]")!!.hasAttr("hidden"))
        assertEquals("Gardien", card.select(".review__item").last()?.selectFirst(".review__value")?.text())
        assertFalse(page.text().contains("677")) // the mandate's comments
        assertTrue(page.selectFirst("[data-assignments-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `decline confirmation without JS`() {
        val card = render(get("/?decline=7")).selectFirst("[data-assignment]")!!

        assertFalse(card.selectFirst("[data-decline-confirm]")!!.hasAttr("hidden"))
        assertTrue(card.selectFirst("[data-actions]")!!.hasAttr("hidden"))
        assertEquals("/assignments/7/decline", card.selectFirst("[data-decline-confirm] form")?.attr("action"))
        assertEquals("/#assignment-7", card.selectFirst("[data-decline-cancel]")?.attr("href"))
    }

    @Test
    fun `no offer`() {
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(AGENT.id)

        val page = render(get("/"))

        assertNull(page.selectFirst("[data-assignment]"))
        assertFalse(page.selectFirst("[data-assignments-empty]")!!.hasAttr("hidden"))
    }

    @Test
    fun `offers unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).pending(any())

        val page = render(get("/"))

        assertNotNull(page.selectFirst("[data-assignments-unavailable]"))
        assertNull(page.selectFirst("[data-assignments-empty]:not([hidden])"))
    }

    @Test
    fun `not verified - no section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()
        doThrow(ResourceAccessException("no case")).whenever(kycClient).current(any())

        assertNull(render(get("/")).selectFirst("#assignments-title"))
    }

    @ParameterizedTest
    @CsvSource(
        "accepted, Mandat accepté",
        "declined, Mandat refusé",
        "closed, Ce mandat n'est plus disponible",
        "error, Action impossible pour le moment",
    )
    fun outcome(outcome: String, title: String) {
        val page = render(get("/").flashAttr("assignment", outcome))

        assertEquals(title, page.selectFirst("[data-assignment-outcome] .alert__title")?.text())
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn test -Dtest=HomeTemplateTest` — Expected: FAIL (no `[data-assignment]` in the page).

- [ ] **Step 3: Implement the template**

Insert after the profile `</section>` of `templates/index.html`. Markup and copy come from the prototype
(`src/main/html/index.html:101-322`); copy its SVGs verbatim where marked `<!-- svg: … -->`.

```html
            <section class="home-section" aria-labelledby="assignments-title" th:if="${assignments != null}"
                     data-assignments-section>
                <h2 id="assignments-title">Mandats proposés</h2>
                <p>Chaque mandat vous est proposé en exclusivité. Acceptez-le ou refusez-le avant la fin du délai :
                    passé ce délai, il est proposé à un autre agent.</p>
                <p class="visually-hidden" role="status" data-assignments-status></p>

                <th:block th:switch="${assignment}">
                    <div class="alert alert--success" role="status" th:case="'accepted'" data-assignment-outcome>
                        <!-- svg: the check icon of the profile's [data-profile-saved] alert -->
                        <div><p class="alert__title">Mandat accepté</p>
                            <p>Le client est prévenu et reçoit vos coordonnées : il va vous contacter.</p></div>
                    </div>
                    <div class="alert alert--info" role="status" th:case="'declined'" data-assignment-outcome>
                        <!-- svg: the clock icon of the KYC "pending" banner -->
                        <div><p class="alert__title">Mandat refusé</p>
                            <p>Il sera proposé à un autre agent.</p></div>
                    </div>
                    <div class="alert alert--warning" role="status" th:case="'closed'" data-assignment-outcome>
                        <!-- svg: the warning icon of the KYC "todo" banner -->
                        <div><p class="alert__title">Ce mandat n'est plus disponible</p>
                            <p>Son délai a expiré ou il a déjà été traité.</p></div>
                    </div>
                    <div class="alert alert--error" role="alert" th:case="'error'" data-assignment-outcome>
                        <!-- svg: the cross icon of the KYC "rejected" banner -->
                        <div><p class="alert__title">Action impossible pour le moment</p>
                            <p>Réessayez dans quelques instants.</p></div>
                    </div>
                </th:block>

                <p class="alert alert--warning" th:if="${assignmentsUnavailable}" data-assignments-unavailable>
                    Vos mandats ne peuvent pas être affichés pour le moment. Réessayez dans quelques instants.</p>

                <ul class="assignments" data-assignments th:unless="${#lists.isEmpty(assignments)}">
                    <li class="assignment" th:each="a : ${assignments}" th:id="|assignment-${a.id}|" data-assignment
                        th:attr="data-assignment-id=${a.id},data-expires-at=${a.expiresAt}"
                        th:with="confirming=${declining == a.id}">
                        <article th:attr="aria-labelledby=|assignment-${a.id}-title|">
                            <div class="assignment__head">
                                <h3 th:id="|assignment-${a.id}-title|" class="assignment__type" th:text="${a.title}">
                                    Locataire · Appartement</h3>
                                <p class="assignment__timer" data-timer>
                                    <!-- svg: the prototype's clock icon (index.html:113-114) -->
                                    <span>Il reste <strong data-timer-value th:text="${a.remaining}">52 min</strong></span>
                                </p>
                            </div>
                            <dl class="assignment__facts">
                                <div th:if="${a.sector}">
                                    <dt th:text="${a.sectorTerm}">Secteur</dt>
                                    <dd data-fact="sector" th:text="${a.sector}">Bonapriso, Akwa — Douala</dd>
                                </div>
                                <div>
                                    <dt th:text="${a.priceTerm}">Budget</dt>
                                    <dd data-fact="price" th:text="${a.price}">150 000 FCFA par mois</dd>
                                </div>
                                <div th:if="${a.moving}">
                                    <dt th:text="${a.movingTerm}">Emménagement</dt>
                                    <dd data-fact="moving" th:text="${a.moving}">Dans moins d'1 mois</dd>
                                </div>
                            </dl>

                            <details class="assignment__details">
                                <summary>Voir les détails</summary>
                                <div class="assignment__details-body">
                                    <dl class="review">
                                        <div class="review__item" th:each="row : ${a.details}">
                                            <dt class="review__term" th:text="${row.first}">Catégorie</dt>
                                            <dd class="review__value" th:text="${row.second}">Résidentiel</dd>
                                        </div>
                                    </dl>
                                    <div class="hint">
                                        <!-- svg: the prototype's info icon (index.html:152-153) -->
                                        <p>Seuls les quartiers sont indiqués. Une fois le mandat accepté, le client
                                            est prévenu et reçoit vos coordonnées.</p>
                                    </div>
                                </div>
                            </details>

                            <div class="assignment__actions" data-actions th:attr="hidden=${confirming} ? 'hidden' : null">
                                <form method="post" th:action="@{/assignments/{id}/accept(id=${a.id})}" data-accept-form>
                                    <button type="submit" class="btn btn--primary" data-accept>Accepter</button>
                                </form>
                                <a class="btn btn--outline" data-decline
                                   th:href="@{|/?decline=${a.id}#assignment-${a.id}|}">Refuser</a>
                            </div>
                            <div class="alert alert--warning assignment__confirm" data-decline-confirm
                                 th:attr="hidden=${confirming} ? null : 'hidden'">
                                <!-- svg: the prototype's warning icon (index.html:165) -->
                                <div>
                                    <p class="alert__title">Refuser ce mandat ?</p>
                                    <p>Il sera proposé à un autre agent et ne vous sera plus proposé.</p>
                                    <form class="assignment__actions" method="post"
                                          th:action="@{/assignments/{id}/decline(id=${a.id})}">
                                        <button type="submit" class="btn btn--primary" data-decline-confirmed>
                                            Confirmer le refus</button>
                                        <a class="btn btn--outline" data-decline-cancel
                                           th:href="@{|/#assignment-${a.id}|}">Annuler</a>
                                    </form>
                                </div>
                            </div>
                        </article>
                    </li>
                </ul>

                <p class="assignments__empty" data-assignments-empty
                   th:attr="hidden=${#lists.isEmpty(assignments) and !assignmentsUnavailable} ? null : 'hidden'">
                    Aucun mandat ne vous est proposé pour le moment. Vous recevrez un e-mail dès qu'un client de
                    votre secteur vous est attribué.
                </p>
            </section>
```

Update the template's header comment (`<!--/* … */-->`) to list `assignments`, `assignmentsUnavailable`,
`declining` and the `assignment` flash attribute. Add `<script th:src="@{/assets/js/home.js}" defer></script>`
after `partner-central.js` in `<head>` (the file arrives in Task 7; a missing script is harmless until then).

Note: the prototype hint says "ses coordonnées vous sont dévoilées ici" — we don't show the customer yet (no
`mandate.html` page), so the copy says the customer receives the agent's details instead, which is what
`MandateAssignedMailet` does.

- [ ] **Step 4: Port the CSS**

Append to `src/main/resources/public/assets/css/home.css` the prototype's *Assignments* block
(`src/main/html/assets/css/home.css`, lines 93–232, from `/* ---------- Assignments ---------- */` up to, not
including, `/* ---------- Mandate (mandate.html) ---------- */`) verbatim, then add:

```css
/* The accept button and "Refuser" are in a form and a link: keep them side by side. */
.assignment__actions form {
    display: contents;
}
```

Update the file's header comment: "KYC banner, profile and assignments sections".

- [ ] **Step 5: Run the template tests**

Run: `mvn test -Dtest='HomeTemplateTest,ProfileTemplateTest'` — Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/index.html src/main/resources/public/assets/css/home.css \
        src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "npc: Mandats proposés section on the Espace agent"
```

---

### Task 7: `home.js` — countdown and in-place decline

**Files:**
- Create: `src/main/resources/public/assets/js/home.js`

**Interfaces:**
- Consumes: the DOM hooks of Task 6.

No JS test harness exists in this module; verify by hand (Step 2).

- [ ] **Step 1: Write the script**

Ported from the prototype's `initAssignments` (`src/main/html/assets/js/partner-central.js:937-1145`), minus
everything marked PROTOTYPE (simulated calls, `localStorage`, `mandate.html` link): the forms post for real.

```js
/*
 * index.html (Espace agent) enhancements. The page works without this file: "Refuser" opens the confirmation via
 * /?decline={id}, the forms post to /assignments/{id}/accept|decline, and the time left is the one at render.
 * - the time left of each offer counts down; under 15 min the card is marked urgent; at 0 its actions are replaced
 *   by "Délai expiré", so a stale offer can't be posted;
 * - "Refuser" / "Annuler" toggle the confirmation in place instead of reloading the page;
 * - a button that posted is disabled, so a double click doesn't post twice.
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: initAssignments).
 */
(function () {
    "use strict";

    var URGENT_MS = 15 * 60 * 1000;

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    function announce(region, message) {
        if (!region) return;
        region.textContent = "";
        window.setTimeout(function () {
            region.textContent = message;
        }, 50);
    }

    function formatRemaining(ms) {
        var total = Math.max(0, Math.floor(ms / 1000));
        var h = Math.floor(total / 3600);
        var m = Math.floor((total % 3600) / 60);
        var s = total % 60;
        if (h > 0) return h + " h " + String(m).padStart(2, "0") + " min";
        if (m > 0) return m + " min " + String(s).padStart(2, "0") + " s";
        return s + " s";
    }

    function expire(item) {
        item.setAttribute("data-closed", "warning");
        item.removeAttribute("data-urgent");
        $("[data-actions]", item).hidden = true;
        $("[data-decline-confirm]", item).hidden = true;
        $all("[data-timer]", item).forEach(function (el) {
            el.hidden = true;
        });
        var alert = document.createElement("div");
        alert.className = "alert alert--warning";
        alert.innerHTML = '<div><p class="alert__title">Délai expiré</p>' +
            "<p>Ce mandat a été proposé à un autre agent.</p></div>";
        $("article", item).appendChild(alert);
    }

    function initAssignments() {
        var list = $("[data-assignments]");
        if (!list) return;
        var status = $("[data-assignments-status]");
        var items = $all("[data-assignment]", list);

        items.forEach(function (item) {
            var actions = $("[data-actions]", item);
            var confirm = $("[data-decline-confirm]", item);

            $("[data-decline]", item).addEventListener("click", function (event) {
                event.preventDefault();
                actions.hidden = true;
                confirm.hidden = false;
                $("[data-decline-cancel]", item).focus();
            });

            $("[data-decline-cancel]", item).addEventListener("click", function (event) {
                event.preventDefault();
                confirm.hidden = true;
                actions.hidden = false;
                $("[data-decline]", item).focus();
            });

            $all("form", item).forEach(function (form) {
                form.addEventListener("submit", function () {
                    $all("button", form).forEach(function (button) {
                        button.disabled = true;
                    });
                });
            });
        });

        function tick() {
            var now = Date.now();
            items.forEach(function (item) {
                if (item.hasAttribute("data-closed")) return;
                var left = Number(item.getAttribute("data-expires-at")) - now;
                if (left <= 0) {
                    expire(item);
                    announce(status, "Un mandat a expiré.");
                    return;
                }
                $all("[data-timer-value]", item).forEach(function (el) {
                    el.textContent = formatRemaining(left);
                });
                if (left < URGENT_MS) item.setAttribute("data-urgent", "");
            });
        }

        tick();
        window.setInterval(tick, 1000);
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "agent-home") initAssignments();
    });
})();
```

- [ ] **Step 2: Check by hand**

Run ndopify-server (`cd modules/ndopify-server && mvn spring-boot:run`) and partner-central
(`cd modules/ndopify-partner-central && mvn spring-boot:run`). Sign in as a VERIFIED agent with a PENDING offer (create
one with `POST /v1/mandates` from *Trouver un bien* in its city, or `POST /v1/mandates/{id}/dispatch`). Check:
1. the card shows, the timer counts down every second;
2. "Refuser" opens the confirmation in place, "Annuler" closes it, focus moves to the shown button;
3. "Confirmer le refus" → back on `/` with "Mandat refusé", the card is gone;
4. with JS disabled: "Refuser" reloads with the confirmation open (`/?decline=…#assignment-…`), both forms post;
5. "Accepter" → "Mandat accepté", and the customer's mailbox (Mailpit, `localhost:8025`) has the *assigned* e-mail;
6. `POST /v1/assignments/{id}/expire` on the server, then "Accepter" on the stale page → "Ce mandat n'est plus disponible".

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/public/assets/js/home.js
git commit -m "npc: live countdown and in-place decline on the Espace agent"
```

---

### Task 8: Docs and final gate

**Files:**
- Modify: `CLAUDE.md` (repo root), the `ndopify-partner-central` section, after the *Espace agent* bullet.

- [ ] **Step 1: Document**

Add to the *Espace agent* bullet of `CLAUDE.md`:

```markdown
  A VERIFIED agent's Espace agent also lists the mandates offered to it (*Mandats proposés*): `HomeController` reads
  its PENDING assignments (`NdAssignmentClient` → `GET /v1/assignments?agent-id=&status=PENDING`, leaving out those
  past `expiresAt`), each one's mandate (`GET /v1/mandates/{id}`; one that fails is left out) and the location names
  in one lookup, into `AssignmentView`s (`AssignmentViews`): no customer data (no id, no comments) before
  acceptance. `AssignmentController` posts `POST /assignments/{id}/accept|decline` and goes back to `/` with an
  `assignment` flash attribute (`accepted`, `declined`, `closed` for a 403/404/409, `error`); ndopify-server checks
  the caller is the offer's agent. Without JS, "Refuser" opens the confirmation via `GET /?decline={id}`;
  `public/assets/js/home.js` does it in place and counts the deadline down. The mandate labels are shared with the
  find-property review in `ui/mandate/MandateLabels`.
```

- [ ] **Step 2: Full gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, all tests, JaCoCo thresholds). If ktlint fails: `mvn antrun:run@ktlint-format`,
re-run.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: Mandats proposés on the Espace agent"
```
