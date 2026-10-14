# Partner Central "Trouver un bien" page (`/find-property`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the "Trouver un bien" prototype (`src/main/html/find-property.html`) as a real, server-rendered,
public page at `/find-property` in `ndopify-partner-central` (the BFF). A visitor describes the rental they're
looking for in seven steps, and the review step creates a `TENANT` mandate on `ndopify-server`
(`POST /v1/mandates`).

**Architecture:** This is a copy of the *Rejoindre* wizard (`JoinController`). There is one Thymeleaf page
(`find-property.html`) that renders only the current step. `GET /find-property?step=…` shows a step, and
`POST /find-property/{step}` validates it (`FindPropertyRules`) into the session's `PendingMandate`. A step only
opens once every earlier step is complete. The review submits the **session**, never the page. After that, a
`SubmittedMandate` in the session keeps the confirmation visible across reloads. The page doesn't need sign-in,
like `/join`. A new `NdMandateClient` is the only new server call. `find-property.js` only adds progressive
enhancement on top: it hides follow-up questions that don't apply, formats amounts, adds the slim-select city and
neighbourhood pickers, intl-tel-input, and the comment counter. Every step still posts without it.

**Tech Stack:** Kotlin, Spring Boot MVC + Security + `RestClient`, Thymeleaf, JUnit 5 + mockito-kotlin +
`kotlin.test`, standalone MockMvc, `MockRestServiceServer`, Jsoup (template tests).

**Spec:** There is no written spec. The prototype is the spec: `src/main/html/find-property.html` (markup and
copy), `src/main/html/assets/js/find-property.js` (rules: `isStepComplete`, `toRequest`, `needsRooms`,
`needsFurniture`, `asksParkings`, `typeFits`, the error messages) and `src/main/html/assets/css/find-property.css`.
Every `PROTOTYPE:` comment marks something this plan replaces with a real call. **Never edit or delete anything
under `src/main/html/`.**

All paths below are relative to `modules/ndopify-partner-central/` unless they start with `modules/` or `docs/`.

## Decisions made in this plan (review these first)

1. **The URL and step keys are in English, like `/join`.** The prototype uses `?etape=besoin|secteur|bien|equipements|budget|contact|revue`.
   The BFF uses `?step=need|area|property|amenities|budget|contact|review`, plus `confirmation`. This matches
   `JoinController` (`identity|experience|area|…`), not the prototype.
2. **The page is public.** `/find-property/**` is added to the `permitAll()` list. Visitors are prospective
   tenants, not agents, and they have no account.
3. **The BFF is the only validator.** `MandateService.create` on `ndopify-server` only checks
   `@NotEmpty` on the contact fields. It doesn't check the category, the city, the neighbourhoods, the price or
   the enums. Closing that gap on the server is **out of scope** (follow-up ticket, see Risks). Until then,
   `FindPropertyRules` and the location checks in the controller are what stand between a crafted POST and bad
   data.
4. **The currency is the tenant's**, `Tenant.currencyCode`. The prototype hard-codes `XAF`. `MIN_RENT = 5000`
   stays a constant, since it only makes sense in FCFA, and every current tenant is Cameroonian.
5. **The listing type is always `RENTAL` and the mandate type always `TENANT`.** The visitor isn't asked about
   either, as in the prototype.
6. **"Indifférent" (`ANY`) is a UI-only answer.** It's stored in the session as `"ANY"`, so the radio stays
   checked when the step is shown again. It's sent to the server as `null`, in `toCreateMandateRequest`. This
   applies to `propertyType`, `furnitureType` and `parkings`.
7. **The follow-up questions of the property step depend on the type chosen in that same step.** Without JS,
   the server can't hide them, so the template renders every follow-up question that fits the **category**
   (known from step 1). The server only validates the ones that apply to the posted type
   (`needsRooms`/`needsFurniture`), and `toCreateMandateRequest` nulls out the ones that don't. With JS,
   `find-property.js` hides the ones that don't apply, as the prototype does.
8. **The DTO spells it `neighbourhoodIds`; the form and the session use `neighborhoodIds`.** The form and the
   session follow `/join` and the prototype. Only `toCreateMandateRequest` uses the DTO's spelling.

## Global Constraints

- French UI copy, taken verbatim from the prototype, for errors, labels and the confirmation.
- `MAX_NEIGHBORHOODS = 3`, `MAX_COMMENTS = 500`, `MIN_RENT = 5000`, area `5..1_000_000` m² or empty.
- Phone numbers are E.164: `^\+[1-9]\d{6,14}$` (same as `JoinRules`).
- The first step has no "Précédent" button (memory: *No "Précédent" on wizard step 1*).
- Assertions use `kotlin.test` only. Controllers are tested with standalone MockMvc, and the template with a real
  Thymeleaf engine (as in `JoinTemplateTest`).
- `ListingType`, `PropertyCategory` and `PropertyType` live in package **`com.wutsi.koki.listing.dto`** (sic) in
  `ndopify-dto`. `FurnitureType`, `MovingPeriod` and `ContactPeriodType` are in `com.wutsi.ndopify.listing.dto`.
  Import them from those packages. Don't "fix" the package in this plan.
- ktlint (`mvn validate`) and the JaCoCo gate (`mvn verify`) must pass for the module.
- Don't touch `src/main/html/`.

## Review Focus

1. **A crafted POST skips steps or posts values that aren't offered** (a city outside the tenant's country, a
   neighbourhood of another city, `propertyType=LAND` with `propertyCategory=…` mismatch, `rooms=7`). Expected:
   the step is shown again with a field error, and nothing reaches the session. Pinned in Task 4
   (`areaRejectsForeignCity`, `areaRejectsNeighbourhoodOfOtherCity`, `propertyRejectsTypeOfOtherCategory`,
   `propertyRejectsUnknownRooms`) and Task 2.
2. **The visitor changes the category or the type after answering the follow-ups.** For example: APARTMENT with
   3 rooms, then back to step 1 → COMMERCIAL. Expected: the property step becomes incomplete again (the type no
   longer fits), so the review isn't reachable, and stale `rooms` is never sent. Pinned in Task 3
   (`changingCategoryInvalidatesProperty`, `staleRoomsAreNotSent`).
3. **The server rejects the mandate** (4xx/5xx or a timeout). Expected: the review is shown again with
   "Envoi impossible pour le moment…", the session answers are kept, and nothing is shown as sent. Pinned in Task 4
   (`submitServerError`).
4. **Double submit or reload after sending.** Expected: the review's button can't be pressed twice (JS). A
   reload of `?step=confirmation` shows the confirmation again. A reload of `POST /review` with an empty session
   goes back to step 1 and creates no second mandate. Pinned in Task 4 (`submitTwiceCreatesOneMandate`).
5. **Budget typed with thousands separators** (`"150 000"`, `"150.000"`, with a non-breaking space from
   `toLocaleString`). Expected: read as 150000. Pinned in Task 2 (`parseAmount`).

---

### Task 1: `NdMandateClient`

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`

**Interfaces:**
- Produces: `NdMandateClient.create(request: CreateMandateRequest): CreateMandateResponse`

- [ ] **Step 1: Write the failing test** (mirrors `NdAgentClientTest`)

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdMandateClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMandateClient

    private val request = CreateMandateRequest(
        mandateType = MandateType.TENANT,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        cityId = 2220957,
        neighbourhoodIds = listOf(1001),
        firstName = "Ray",
        lastName = "Sponsible",
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdMandateClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/mandates"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json(
                    """{"mandateType":"TENANT","listingType":"RENTAL","propertyCategory":"RESIDENTIAL",
                       "cityId":2220957,"neighbourhoodIds":[1001],"email":"ray@gmail.com"}""",
                ),
            )
            .andRespond(withSuccess("""{"mandateId":42}""", MediaType.APPLICATION_JSON))

        assertEquals(42L, client.create(request).mandateId)
        server.verify()
    }

    @Test
    fun createBadRequest() {
        server.expect(requestTo("http://localhost:8080/v1/mandates"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException> { client.create(request) }
    }
}
```

- [ ] **Step 2: Run it and check that it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdMandateClientTest`
Expected: compilation failure, `Unresolved reference: NdMandateClient`.

- [ ] **Step 3: Implement it**

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server mandate endpoints (`/v1/mandates`).
 * Mandates are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant receives it.
 */
@Service
class NdMandateClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** Records a mandate; the server finds or creates the customer from its contact details. */
    fun create(request: CreateMandateRequest): CreateMandateResponse =
        rest.post()
            .uri("/v1/mandates")
            .body(request)
            .retrieve()
            .body<CreateMandateResponse>()
            ?: CreateMandateResponse()
}
```

- [ ] **Step 4: Run it and check that it passes.** Same command, expect PASS.
- [ ] **Step 5: Commit:** `git commit -m "npc: add NdMandateClient"`

---

### Task 2: `FindPropertyRules`, the field rules

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`

**Interfaces:**
- Produces (each returns the French message or `null` when valid):
  `propertyCategory(PropertyCategory?)`, `cityId(Long?)`, `neighborhoodIds(List<Long>)`,
  `propertyType(category: PropertyCategory?, type: String?)`, `rooms(String?)`, `furnitureType(String?)`,
  `parkings(String?)`, `area(Int?)`, `priceAmount(Long?)`, `movingPeriod(MovingPeriod?)`,
  `preferredContactPeriod(ContactPeriodType?)`, `comments(String?)`.
  It also has the predicates `needsRooms(type: String?)`, `needsFurniture(type: String?)`, `asksParkings(type: String?)`,
  `typeFits(category, type)`, and `parseAmount(String?): Long?`. The constants are `MAX_NEIGHBORHOODS`,
  `MAX_COMMENTS`, `MIN_RENT`, `ANY = "ANY"`.
- Names, e-mail and WhatsApp reuse `JoinRules.firstName/lastName/email/whatsappNumber/normalizePhone`. The
  messages are identical in both prototypes.

`propertyType`, `furnitureType` and `parkings` are kept as `String?`, because they can be `"ANY"`. They're
converted to enums only in `PendingMandate.toCreateMandateRequest`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.MovingPeriod
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindPropertyRulesTest {
    @Test
    fun propertyCategory() {
        assertNull(FindPropertyRules.propertyCategory(PropertyCategory.RESIDENTIAL))
        assertEquals(
            "Indiquez si le bien est pour y habiter ou pour une activité.",
            FindPropertyRules.propertyCategory(PropertyCategory.UNKNOWN),
        )
        assertEquals(
            "Indiquez si le bien est pour y habiter ou pour une activité.",
            FindPropertyRules.propertyCategory(null),
        )
    }

    @Test
    fun propertyTypeIsOptionalButMustFitCategory() {
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, null))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "ANY"))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "APARTMENT"))
        assertNull(FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "LAND"))
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "APARTMENT"),
        )
        assertEquals(
            "Choisissez un type de bien proposé.",
            FindPropertyRules.propertyType(PropertyCategory.RESIDENTIAL, "CASTLE"),
        )
    }

    @Test
    fun followUps() {
        assertTrue(FindPropertyRules.needsRooms("APARTMENT"))
        assertFalse(FindPropertyRules.needsRooms("STUDIO"))
        assertFalse(FindPropertyRules.needsRooms(null))
        assertFalse(FindPropertyRules.needsFurniture("LAND"))
        assertTrue(FindPropertyRules.needsFurniture(null))
        assertTrue(FindPropertyRules.needsFurniture("ANY"))
        assertFalse(FindPropertyRules.asksParkings("ROOM"))
        assertTrue(FindPropertyRules.asksParkings(null))
    }

    @Test
    fun rooms() {
        assertNull(FindPropertyRules.rooms("5+"))
        assertEquals("Indiquez le nombre de chambres.", FindPropertyRules.rooms(null))
        assertEquals("Indiquez le nombre de chambres.", FindPropertyRules.rooms("7"))
    }

    @Test
    fun furnitureAndParkings() {
        assertNull(FindPropertyRules.furnitureType("ANY"))
        assertNull(FindPropertyRules.furnitureType("FULLY_FURNISHED"))
        assertEquals("Indiquez si le bien doit être meublé.", FindPropertyRules.furnitureType(null))
        assertNull(FindPropertyRules.parkings(null)) // optional
        assertNull(FindPropertyRules.parkings("3+"))
        assertEquals("Choisissez un nombre de places proposé.", FindPropertyRules.parkings("9"))
    }

    @Test
    fun area() {
        assertNull(FindPropertyRules.area(null))
        assertNull(FindPropertyRules.area(5))
        assertEquals("Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.", FindPropertyRules.area(4))
        assertEquals("Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.", FindPropertyRules.area(1_000_001))
    }

    @Test
    fun priceAmount() {
        assertNull(FindPropertyRules.priceAmount(5000L))
        assertEquals("Indiquez votre budget mensuel.", FindPropertyRules.priceAmount(null))
        assertEquals("Indiquez un loyer d'au moins 5 000 FCFA par mois.", FindPropertyRules.priceAmount(4999L))
    }

    @Test
    fun parseAmount() {
        assertEquals(150000L, FindPropertyRules.parseAmount("150 000"))
        assertEquals(150000L, FindPropertyRules.parseAmount("150\u202F000")) // fr-FR toLocaleString
        assertEquals(150000L, FindPropertyRules.parseAmount("150.000"))
        assertNull(FindPropertyRules.parseAmount(""))
        assertNull(FindPropertyRules.parseAmount("abc"))
        assertNull(FindPropertyRules.parseAmount("99999999999999999999")) // overflow, not a crash
    }

    @Test
    fun movingPeriod() {
        assertNull(FindPropertyRules.movingPeriod(MovingPeriod.WITHIN_1_MONTH))
        assertEquals("Indiquez quand vous souhaitez emménager.", FindPropertyRules.movingPeriod(MovingPeriod.UNKNOWN))
    }

    @Test
    fun comments() {
        assertNull(FindPropertyRules.comments(null))
        assertNull(FindPropertyRules.comments("a".repeat(500)))
        assertEquals("Votre message ne doit pas dépasser 500 caractères.", FindPropertyRules.comments("a".repeat(501)))
    }

    @Test
    fun neighborhoodIds() {
        assertEquals("Choisissez au moins un quartier.", FindPropertyRules.neighborhoodIds(emptyList()))
        assertEquals("Choisissez au plus 3 quartiers.", FindPropertyRules.neighborhoodIds(listOf(1L, 2L, 3L, 4L)))
    }
}
```

- [ ] **Step 2: Run it and check that it fails:** `mvn test -Dtest=FindPropertyRulesTest` → doesn't compile.

- [ ] **Step 3: Implement it**

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod

/**
 * Field rules of the "Trouver un bien" wizard, shared by [FindPropertyController] (to validate what a step posts)
 * and [PendingMandate] (to tell which steps are complete). Each returns the French error message, or null when
 * valid. Messages and thresholds are the ones of the `find-property.js` prototype. ndopify-server doesn't re-check
 * any of this yet, so these rules are the only validation a mandate gets.
 */
object FindPropertyRules {
    const val MAX_NEIGHBORHOODS = 3
    const val MAX_COMMENTS = 500
    const val MIN_RENT = 5000L
    private const val MIN_AREA = 5
    private const val MAX_AREA = 1_000_000

    /** "Indifférent": a UI answer only, sent to the server as null. */
    const val ANY = "ANY"

    val ROOMS = listOf("1", "2", "3", "4", "5+")
    val PARKINGS = listOf("0", "1", "2", "3+", ANY)

    /** Which categories each property type can be searched under (prototype's PROPERTY_TYPES). */
    val CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> = mapOf(
        PropertyType.ROOM to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.STUDIO to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.APARTMENT to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.HOUSE to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.VILLA to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.DUPLEX to setOf(PropertyCategory.RESIDENTIAL),
        PropertyType.RETAIL to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.OFFICE to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.INDUSTRIAL to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.BUILDING to setOf(PropertyCategory.COMMERCIAL),
        PropertyType.LAND to setOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL),
    )

    private val WITH_ROOMS = setOf("APARTMENT", "HOUSE", "VILLA", "DUPLEX")
    private val WITHOUT_FURNITURE = setOf("LAND", "BUILDING", "INDUSTRIAL")
    private val WITHOUT_PARKINGS = setOf("LAND", "ROOM")

    fun needsRooms(type: String?): Boolean = type in WITH_ROOMS

    fun needsFurniture(type: String?): Boolean = type !in WITHOUT_FURNITURE

    fun asksParkings(type: String?): Boolean = type !in WITHOUT_PARKINGS

    /** No type, or "Indifférent", fits any category; otherwise the type must be offered for the category. */
    fun typeFits(category: PropertyCategory?, type: String?): Boolean {
        if (type.isNullOrEmpty() || type == ANY) return true
        val known = PropertyType.entries.firstOrNull { it.name == type } ?: return false
        return CATEGORIES_BY_TYPE[known]?.contains(category) == true
    }

    fun propertyCategory(value: PropertyCategory?): String? =
        if (value == null || value == PropertyCategory.UNKNOWN) {
            "Indiquez si le bien est pour y habiter ou pour une activité."
        } else {
            null
        }

    fun cityId(value: Long?): String? =
        if (value == null || value <= 0) "Choisissez la ville où vous cherchez." else null

    fun neighborhoodIds(value: List<Long>): String? = when {
        value.isEmpty() -> "Choisissez au moins un quartier."
        value.size > MAX_NEIGHBORHOODS -> "Choisissez au plus $MAX_NEIGHBORHOODS quartiers."
        else -> null
    }

    fun propertyType(category: PropertyCategory?, type: String?): String? =
        if (typeFits(category, type)) null else "Choisissez un type de bien proposé."

    fun rooms(value: String?): String? = if (value in ROOMS) null else "Indiquez le nombre de chambres."

    fun furnitureType(value: String?): String? =
        if (value == ANY || FurnitureType.entries.any { it != FurnitureType.UNKNOWN && it.name == value }) {
            null
        } else {
            "Indiquez si le bien doit être meublé."
        }

    fun parkings(value: String?): String? =
        if (value.isNullOrEmpty() || value in PARKINGS) null else "Choisissez un nombre de places proposé."

    fun area(value: Int?): String? =
        if (value == null || value in MIN_AREA..MAX_AREA) {
            null
        } else {
            "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide."
        }

    fun priceAmount(value: Long?): String? = when {
        value == null || value <= 0 -> "Indiquez votre budget mensuel."
        value < MIN_RENT -> "Indiquez un loyer d'au moins 5 000 FCFA par mois."
        else -> null
    }

    fun movingPeriod(value: MovingPeriod?): String? =
        if (value == null || value == MovingPeriod.UNKNOWN) "Indiquez quand vous souhaitez emménager." else null

    fun preferredContactPeriod(value: ContactPeriodType?): String? =
        if (value == null || value == ContactPeriodType.UNKNOWN) {
            "Indiquez quand votre agent peut vous contacter."
        } else {
            null
        }

    fun comments(value: String?): String? =
        if ((value?.length ?: 0) > MAX_COMMENTS) "Votre message ne doit pas dépasser $MAX_COMMENTS caractères." else null

    /** "150 000", "150 000" or "150.000" → 150000. Blank, non-numeric or out-of-range → null. */
    fun parseAmount(value: String?): Long? =
        value.orEmpty().filter { it.isDigit() }.takeIf { it.isNotEmpty() && it.length <= 12 }?.toLong()
}
```

`cityId` copy: the prototype's `cityId` error isn't visible in the JS slice read for this plan. **Check the
`#cityId-error` text in `find-property.html` and use it verbatim**, then update the assertion. The same goes for
any message above that differs from the prototype.

- [ ] **Step 4: Run it and check that it passes**
- [ ] **Step 5: Commit:** `git commit -m "npc: add FindPropertyRules"`

---

### Task 3: `PendingMandate` and `SubmittedMandate`, the session state

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/SubmittedMandate.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/{NeedForm,AreaForm,PropertyForm,AmenitiesForm,BudgetForm,ContactForm}.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt`

**Interfaces:**
- Consumes: `FindPropertyRules` (Task 2).
- Produces:
  - `PendingMandate(propertyCategory: PropertyCategory?, cityId: Long?, neighborhoodIds: List<Long>, propertyType: String?, rooms: String?, furnitureType: String?, parkings: String?, area: Int?, amenities: Set<String>, priceAmount: Long?, movingPeriod: MovingPeriod?, firstName: String?, lastName: String?, email: String?, whatsappNumber: String?, preferredContactPeriod: ContactPeriodType?, comments: String?) : Serializable`.
    It has `SESSION_ATTRIBUTE = "npc.pending-mandate"`, `isComplete(step: String)`, `firstIncompleteStep(): String`,
    `has(amenity: String): Boolean` and `toCreateMandateRequest(currency: String): CreateMandateRequest`.
  - `PendingMandate.AMENITIES: List<String>` holds the 14 boolean field names of `CreateMandateRequest`, in form
    order (prototype `AMENITIES`).
  - `SubmittedMandate(email: String, whatsappNumber: String, preferredContactPeriod: ContactPeriodType?) : Serializable`,
    with `SESSION_ATTRIBUTE = "npc.submitted-mandate"`.
  - The step constants live on `FindPropertyController.Companion` (Task 4): `STEP_NEED="need"`, `STEP_AREA="area"`,
    `STEP_PROPERTY="property"`, `STEP_AMENITIES="amenities"`, `STEP_BUDGET="budget"`, `STEP_CONTACT="contact"`,
    `STEP_REVIEW="review"`, `STEP_CONFIRMATION="confirmation"` and `STEPS`. Create the controller class with only
    its companion in this task, so the code compiles. Task 4 fills it in.

Amenities are kept as a `Set<String>` of field names rather than 14 booleans. That keeps the form, the session
and the template loops small. `toCreateMandateRequest` maps them back to the DTO's named booleans.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingMandateTest {
    companion object {
        val COMPLETE = PendingMandate(
            propertyCategory = PropertyCategory.RESIDENTIAL,
            cityId = 2220957,
            neighborhoodIds = listOf(1001, 1002),
            propertyType = "APARTMENT",
            rooms = "3",
            furnitureType = "ANY",
            parkings = "1",
            area = 80,
            amenities = setOf("waterMeter", "securityGuard"),
            priceAmount = 150_000,
            movingPeriod = MovingPeriod.WITHIN_1_MONTH,
            firstName = "Ray",
            lastName = "Sponsible",
            email = "ray@gmail.com",
            whatsappNumber = "+237670000001",
            preferredContactPeriod = ContactPeriodType.EVENING,
            comments = "Près d'une école",
        )
    }

    @Test
    fun emptyStartsAtNeed() = assertEquals("need", PendingMandate().firstIncompleteStep())

    @Test
    fun completeGoesToReview() = assertEquals("review", COMPLETE.firstIncompleteStep())

    @Test
    fun amenitiesAreAlwaysComplete() = assertTrue(PendingMandate().isComplete("amenities"))

    @Test
    fun roomsRequiredOnlyWhenTheTypeHasRooms() {
        assertFalse(COMPLETE.copy(rooms = null).isComplete("property"))
        assertTrue(COMPLETE.copy(propertyType = "STUDIO", rooms = null).isComplete("property"))
    }

    @Test
    fun changingCategoryInvalidatesProperty() {
        val changed = COMPLETE.copy(propertyCategory = PropertyCategory.COMMERCIAL)
        assertEquals("property", changed.firstIncompleteStep())
    }

    @Test
    fun toCreateMandateRequest() {
        val request = COMPLETE.toCreateMandateRequest("XAF")

        assertEquals(MandateType.TENANT, request.mandateType)
        assertEquals(ListingType.RENTAL, request.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, request.propertyCategory)
        assertEquals(2220957L, request.cityId)
        assertEquals(listOf(1001L, 1002L), request.neighbourhoodIds)
        assertEquals(PropertyType.APARTMENT, request.propertyType)
        assertEquals("3", request.rooms)
        assertNull(request.furnitureType) // ANY
        assertEquals("1", request.parkings)
        assertEquals(80, request.area)
        assertTrue(request.waterMeter)
        assertTrue(request.securityGuard)
        assertFalse(request.solarPanel)
        assertEquals(150_000L, request.price.amount)
        assertEquals("XAF", request.price.currency)
        assertEquals(PeriodicityType.MONTHLY, request.price.periodicity)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, request.movingPeriod)
        assertEquals("+237670000001", request.whatsappNumber)
        assertEquals(ContactPeriodType.EVENING, request.preferredContactPeriod)
        assertEquals("Près d'une école", request.comments)
    }

    @Test
    fun staleRoomsAreNotSent() {
        // Rooms answered for an APARTMENT, then the type changed to STUDIO: rooms no longer applies.
        val request = COMPLETE.copy(propertyType = "STUDIO").toCreateMandateRequest("XAF")
        assertNull(request.rooms)
    }

    @Test
    fun furnitureAndParkingsNotSentForLand() {
        val request = COMPLETE.copy(propertyType = "LAND", rooms = null, furnitureType = "FULLY_FURNISHED")
            .toCreateMandateRequest("XAF")
        assertNull(request.furnitureType)
        assertNull(request.parkings)
    }

    @Test
    fun anyTypeIsSentAsNull() {
        val request = COMPLETE.copy(propertyType = "ANY").toCreateMandateRequest("XAF")
        assertNull(request.propertyType)
        assertNull(request.rooms)
        assertEquals(null, request.furnitureType)
    }

    @Test
    fun furnished() {
        val request = COMPLETE.copy(furnitureType = "FULLY_FURNISHED").toCreateMandateRequest("XAF")
        assertEquals(FurnitureType.FULLY_FURNISHED, request.furnitureType)
    }

    @Test
    fun blankCommentsAreNull() = assertNull(COMPLETE.copy(comments = "").toCreateMandateRequest("XAF").comments)
}
```

- [ ] **Step 2: Run it and check that it fails**

- [ ] **Step 3: Implement it**

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.koki.listing.dto.ListingType
import com.wutsi.koki.listing.dto.PropertyCategory
import com.wutsi.koki.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyController.Companion.STEPS
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyController.Companion.STEP_REVIEW
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.io.Serializable

/**
 * The answers of a "Trouver un bien" request in progress, kept in the HTTP session until the review submits them.
 * Answers are only stored once their step validated them, so the session is the trusted copy. Follow-up answers
 * that no longer apply (rooms of a studio, …) may linger here; [toCreateMandateRequest] never sends them.
 */
data class PendingMandate(
    val propertyCategory: PropertyCategory? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val propertyType: String? = null,
    val rooms: String? = null,
    val furnitureType: String? = null,
    val parkings: String? = null,
    val area: Int? = null,
    val amenities: Set<String> = emptySet(),
    val priceAmount: Long? = null,
    val movingPeriod: MovingPeriod? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val preferredContactPeriod: ContactPeriodType? = null,
    val comments: String? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-mandate"

        /** The boolean criteria of CreateMandateRequest, in form order. */
        val AMENITIES = listOf(
            "waterMeter", "dedicatedWaterMeter", "waterTank", "waterWell",
            "electricityMeter", "dedicatedElectricityMeter", "generatorSet", "solarPanel",
            "roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar",
            "securedFenced", "securityGuard", "videoSurveillance",
        )
    }

    fun has(amenity: String): Boolean = amenities.contains(amenity)

    fun isComplete(step: String): Boolean = when (step) {
        FindPropertyController.STEP_NEED -> FindPropertyRules.propertyCategory(propertyCategory) == null

        FindPropertyController.STEP_AREA ->
            FindPropertyRules.cityId(cityId) == null && FindPropertyRules.neighborhoodIds(neighborhoodIds) == null

        FindPropertyController.STEP_PROPERTY ->
            FindPropertyRules.propertyType(propertyCategory, propertyType) == null &&
                (!FindPropertyRules.needsRooms(propertyType) || FindPropertyRules.rooms(rooms) == null) &&
                (!FindPropertyRules.needsFurniture(propertyType) || FindPropertyRules.furnitureType(furnitureType) == null) &&
                FindPropertyRules.area(area) == null

        FindPropertyController.STEP_AMENITIES -> true

        FindPropertyController.STEP_BUDGET ->
            FindPropertyRules.priceAmount(priceAmount) == null && FindPropertyRules.movingPeriod(movingPeriod) == null

        FindPropertyController.STEP_CONTACT -> listOf(
            JoinRules.firstName(firstName),
            JoinRules.lastName(lastName),
            JoinRules.email(email),
            JoinRules.whatsappNumber(whatsappNumber),
            FindPropertyRules.preferredContactPeriod(preferredContactPeriod),
            FindPropertyRules.comments(comments),
        ).all { it == null }

        else -> false
    }

    /** The first step that still needs answers, or the review step when they're all done. */
    fun firstIncompleteStep(): String = STEPS.dropLast(1).firstOrNull { step -> !isComplete(step) } ?: STEP_REVIEW

    fun toCreateMandateRequest(currency: String): CreateMandateRequest {
        val type = propertyType?.takeIf { it != FindPropertyRules.ANY }
        return CreateMandateRequest(
            mandateType = MandateType.TENANT,
            // Only rentals for now: the visitor isn't asked.
            listingType = ListingType.RENTAL,
            propertyCategory = propertyCategory ?: PropertyCategory.UNKNOWN,
            cityId = cityId ?: -1,
            neighbourhoodIds = neighborhoodIds,
            propertyType = type?.let { PropertyType.valueOf(it) },
            furnitureType = furnitureType
                ?.takeIf { FindPropertyRules.needsFurniture(propertyType) && it != FindPropertyRules.ANY }
                ?.let { FurnitureType.valueOf(it) },
            rooms = rooms?.takeIf { FindPropertyRules.needsRooms(propertyType) },
            parkings = parkings?.takeIf { FindPropertyRules.asksParkings(propertyType) && it != FindPropertyRules.ANY },
            area = area,
            waterMeter = has("waterMeter"),
            dedicatedWaterMeter = has("dedicatedWaterMeter"),
            waterTank = has("waterTank"),
            waterWell = has("waterWell"),
            electricityMeter = has("electricityMeter"),
            dedicatedElectricityMeter = has("dedicatedElectricityMeter"),
            generatorSet = has("generatorSet"),
            solarPanel = has("solarPanel"),
            roadPaved = has("roadPaved"),
            trackAccessibleByCar = has("trackAccessibleByCar"),
            trackInaccessibleByCar = has("trackInaccessibleByCar"),
            securedFenced = has("securedFenced"),
            securityGuard = has("securityGuard"),
            videoSurveillance = has("videoSurveillance"),
            price = Money(amount = priceAmount ?: 0, currency = currency, periodicity = PeriodicityType.MONTHLY),
            movingPeriod = movingPeriod ?: MovingPeriod.UNKNOWN,
            firstName = firstName.orEmpty(),
            lastName = lastName.orEmpty(),
            email = email.orEmpty(),
            whatsappNumber = whatsappNumber.orEmpty(),
            preferredContactPeriod = preferredContactPeriod,
            comments = comments?.takeIf { it.isNotBlank() },
        )
    }
}
```

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

import com.wutsi.ndopify.listing.dto.ContactPeriodType
import java.io.Serializable

/** A sent request, kept in the session after `POST /find-property/review` so the confirmation survives a reload. */
data class SubmittedMandate(
    val email: String,
    val whatsappNumber: String,
    val preferredContactPeriod: ContactPeriodType?,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.submitted-mandate"
    }
}
```

These are the forms, one file each, matching `SectorForm`. Fields are nullable so missing params bind to null:

```kotlin
/** Step "need". */            data class NeedForm(val propertyCategory: PropertyCategory? = null)
/** Step "area". */            data class AreaForm(val cityId: Long? = null, val neighborhoodIds: List<Long> = emptyList())
/** Step "property". */        data class PropertyForm(val propertyType: String? = null, val rooms: String? = null, val furnitureType: String? = null, val parkings: String? = null, val area: String? = null)
/** Step "amenities": the checked boxes' names. */ data class AmenitiesForm(val amenities: List<String> = emptyList())
/** Step "budget". */          data class BudgetForm(val priceAmount: String? = null, val movingPeriod: MovingPeriod? = null)
/** Step "contact". */         data class ContactForm(val firstName: String = "", val lastName: String = "", val email: String = "", val whatsappNumber: String = "", val preferredContactPeriod: ContactPeriodType? = null, val comments: String = "")
```

`area` and `priceAmount` are posted as `String`, because they can come with thousands separators. The controller
parses them with `FindPropertyRules.parseAmount`. The amenity checkboxes post `amenities=<fieldName>`, which
**differs from the prototype's `name="waterMeter"`**. The template changes this (Task 5).

The minimal controller stub for this task:

```kotlin
package com.wutsi.ndopify.npc.ui.findproperty

class FindPropertyController {
    companion object {
        const val VIEW = "find-property"
        const val STEP_NEED = "need"
        const val STEP_AREA = "area"
        const val STEP_PROPERTY = "property"
        const val STEP_AMENITIES = "amenities"
        const val STEP_BUDGET = "budget"
        const val STEP_CONTACT = "contact"
        const val STEP_REVIEW = "review"
        const val STEP_CONFIRMATION = "confirmation"
        const val RETURN_TO_REVIEW = "review"

        val STEPS = listOf(STEP_NEED, STEP_AREA, STEP_PROPERTY, STEP_AMENITIES, STEP_BUDGET, STEP_CONTACT, STEP_REVIEW)
    }
}
```

- [ ] **Step 4: Run it and check that it passes:** `mvn test -Dtest=PendingMandateTest`
- [ ] **Step 5: Commit:** `git commit -m "npc: add find-property session state"`

---

### Task 4: `FindPropertyController` and the public route

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt` (stub → full)
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt` (add `.requestMatchers("/find-property", "/find-property/**").permitAll()` next to `/join`)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`

**Interfaces:**
- Consumes: `NdMandateClient.create` (Task 1), `NdLocationClient.search`, `TenantContext.getTenant()`,
  `FindPropertyRules` (Task 2), `PendingMandate` / `SubmittedMandate` / forms (Task 3), and
  `LoginController.maskEmail(String)`.
- Produces the routes `GET /find-property[?step=…&returnTo=review]`, `POST /find-property/{need|area|property|amenities|budget|contact|review}`
  and `GET /find-property?step=confirmation`. It also produces these model attributes for Task 5's template:
  - Every step: `tenant`, `step`, `stepNumber`, `stepCount` (7), `answers`, `errors`
    (`Map<String,String>`, keyed by field name: `propertyCategory`, `cityId`, `neighborhoodIds`, `propertyType`,
    `rooms`, `furnitureType`, `parkings`, `area`, `priceAmount`, `movingPeriod`, `firstName`, `lastName`, `email`,
    `whatsappNumber`, `preferredContactPeriod`, `comments`), and `returning`.
  - `area`, `review`: `city: Location?`, `neighborhoods: List<Location>`.
  - `property`: `propertyTypes: List<PropertyType>` (the ones fitting `answers.propertyCategory`, in prototype order).
  - `review`: `categoryLabel`, `propertyLabel`, `priceLabel` ("150 000 FCFA par mois"), `movingPeriodLabel`,
    `contactPeriodLabel`, `amenityLabels: List<String>` and `error: String?`.
  - `confirmation`: `maskedEmail`, `whatsappNumber`, `contactPeriodLabel`.

This has the same structure as `JoinController`. Copy its `show`, `save`, `step`, `isAllowed`, `redirect`,
`errors`, `pending`, `locations` and `sectorErrors` helpers, renamed for this wizard. Below are only the parts
that differ. The engineer must write the whole class, not leave references to Join.

```kotlin
@Controller
@RequestMapping("/find-property")
class FindPropertyController(
    private val mandateClient: NdMandateClient,
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FindPropertyController::class.java)
        // … constants from Task 3 …

        private val CATEGORY_LABELS = mapOf(
            PropertyCategory.RESIDENTIAL to "usage résidentiel",
            PropertyCategory.COMMERCIAL to "usage commercial",
        )
        val PROPERTY_TYPE_LABELS = linkedMapOf(
            PropertyType.ROOM to "Chambre", PropertyType.STUDIO to "Studio", PropertyType.APARTMENT to "Appartement",
            PropertyType.HOUSE to "Maison", PropertyType.VILLA to "Villa", PropertyType.DUPLEX to "Duplex",
            PropertyType.RETAIL to "Boutique, local commercial", PropertyType.OFFICE to "Bureau",
            PropertyType.INDUSTRIAL to "Entrepôt, local industriel", PropertyType.BUILDING to "Immeuble",
            PropertyType.LAND to "Terrain",
        )
        private val ROOM_LABELS = mapOf("1" to "1 chambre", "2" to "2 chambres", "3" to "3 chambres", "4" to "4 chambres", "5+" to "5 chambres et +")
        private val FURNITURE_LABELS = mapOf("FULLY_FURNISHED" to "Meublé", "UNFURNISHED" to "Non meublé", "ANY" to "Meublé ou non")
        private val PARKING_LABELS = mapOf("0" to "Sans parking", "1" to "1 place de parking", "2" to "2 places de parking", "3+" to "3 places de parking et +", "ANY" to "Parking indifférent")
        private val MOVE_IN_LABELS = mapOf(
            MovingPeriod.WITHIN_1_MONTH to "Dans moins d'1 mois",
            MovingPeriod.WITHIN_3_MONTHS to "Dans 1 à 3 mois",
            MovingPeriod.AFTER_3_MONTHS to "Dans plus de 3 mois",
        )
        private val CONTACT_PERIOD_LABELS = mapOf(
            ContactPeriodType.MORNING to "le matin",
            ContactPeriodType.AFTERNOON to "l'après-midi",
            ContactPeriodType.EVENING to "le soir",
        )
        // Same order and copy as the prototype's AMENITIES.
        private val AMENITY_LABELS = linkedMapOf(
            "waterMeter" to "Compteur d'eau", "dedicatedWaterMeter" to "Compteur d'eau individuel",
            "waterTank" to "Citerne", "waterWell" to "Forage",
            "electricityMeter" to "Compteur électrique", "dedicatedElectricityMeter" to "Compteur électrique individuel",
            "generatorSet" to "Groupe électrogène", "solarPanel" to "Panneaux solaires",
            "roadPaved" to "Route bitumée", "trackAccessibleByCar" to "Piste accessible en voiture",
            "trackInaccessibleByCar" to "Piste accessible à pied seulement",
            "securedFenced" to "Clôturé et sécurisé", "securityGuard" to "Gardien", "videoSurveillance" to "Vidéosurveillance",
        )
    }

    @ModelAttribute("tenant")
    fun tenant(): Tenant = tenantContext.getTenant()

    @GetMapping
    fun show(/* step, returnTo, session, model, as in JoinController */): String {
        val submitted = session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE) as SubmittedMandate?
        if (requestedStep == STEP_CONFIRMATION && submitted != null) {
            model.addAttribute("step", STEP_CONFIRMATION)
            model.addAttribute("maskedEmail", LoginController.maskEmail(submitted.email))
            model.addAttribute("whatsappNumber", submitted.whatsappNumber)
            model.addAttribute("contactPeriodLabel", CONTACT_PERIOD_LABELS[submitted.preferredContactPeriod])
            return VIEW
        }
        // … same min(requested, allowed) logic as JoinController.show …
    }

    @PostMapping("/$STEP_NEED")
    fun need(@ModelAttribute form: NeedForm, @RequestParam(required = false) returnTo: String?, session: HttpSession, model: Model): String {
        val answers = pending(session).copy(propertyCategory = form.propertyCategory)
        val errors = errors("propertyCategory" to FindPropertyRules.propertyCategory(answers.propertyCategory))
        return save(STEP_NEED, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AREA")
    fun area(@ModelAttribute form: AreaForm, /* … */): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AREA, pending)) return redirect(pending.firstIncompleteStep())
        val answers = pending.copy(cityId = form.cityId, neighborhoodIds = form.neighborhoodIds.distinct())
        val errors = errors(
            "cityId" to FindPropertyRules.cityId(answers.cityId),
            "neighborhoodIds" to FindPropertyRules.neighborhoodIds(answers.neighborhoodIds),
        ).ifEmpty { sectorErrors(answers.cityId!!, answers.neighborhoodIds) } // copied from JoinController
        return save(STEP_AREA, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_PROPERTY")
    fun property(@ModelAttribute form: PropertyForm, /* … */): String {
        val pending = pending(session)
        if (!isAllowed(STEP_PROPERTY, pending)) return redirect(pending.firstIncompleteStep())
        val type = form.propertyType?.takeIf { it.isNotEmpty() }
        val areaText = form.area?.trim().orEmpty()
        val area = FindPropertyRules.parseAmount(areaText)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        val answers = pending.copy(
            propertyType = type,
            rooms = form.rooms?.takeIf { FindPropertyRules.needsRooms(type) },
            furnitureType = form.furnitureType?.takeIf { FindPropertyRules.needsFurniture(type) },
            parkings = form.parkings?.takeIf { it.isNotEmpty() && FindPropertyRules.asksParkings(type) },
            area = area,
        )
        val errors = errors(
            "propertyType" to FindPropertyRules.propertyType(answers.propertyCategory, type),
            "rooms" to if (FindPropertyRules.needsRooms(type)) FindPropertyRules.rooms(answers.rooms) else null,
            "furnitureType" to if (FindPropertyRules.needsFurniture(type)) FindPropertyRules.furnitureType(answers.furnitureType) else null,
            "parkings" to FindPropertyRules.parkings(answers.parkings),
            // Something typed that isn't a number is an error too, not a silent "no area".
            "area" to if (areaText.isNotEmpty() && area == null) FindPropertyRules.area(0) else FindPropertyRules.area(area),
        )
        return save(STEP_PROPERTY, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_AMENITIES")
    fun amenities(@ModelAttribute form: AmenitiesForm, /* … */): String {
        val pending = pending(session)
        if (!isAllowed(STEP_AMENITIES, pending)) return redirect(pending.firstIncompleteStep())
        // Unknown names are dropped, not errors: there's nothing the visitor could fix.
        val answers = pending.copy(amenities = form.amenities.filter { it in PendingMandate.AMENITIES }.toSet())
        return save(STEP_AMENITIES, answers, emptyMap(), returnTo, session, model)
    }

    @PostMapping("/$STEP_BUDGET")
    fun budget(@ModelAttribute form: BudgetForm, /* … */): String {
        // isAllowed guard, then:
        val answers = pending.copy(priceAmount = FindPropertyRules.parseAmount(form.priceAmount), movingPeriod = form.movingPeriod)
        val errors = errors(
            "priceAmount" to FindPropertyRules.priceAmount(answers.priceAmount),
            "movingPeriod" to FindPropertyRules.movingPeriod(answers.movingPeriod),
        )
        return save(STEP_BUDGET, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_CONTACT")
    fun contact(@ModelAttribute form: ContactForm, /* … */): String {
        // isAllowed guard, then:
        val answers = pending.copy(
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            email = form.email.trim().lowercase(),
            whatsappNumber = JoinRules.normalizePhone(form.whatsappNumber),
            preferredContactPeriod = form.preferredContactPeriod,
            comments = form.comments.trim(),
        )
        val errors = errors(
            "firstName" to JoinRules.firstName(answers.firstName),
            "lastName" to JoinRules.lastName(answers.lastName),
            "email" to JoinRules.email(answers.email),
            "whatsappNumber" to JoinRules.whatsappNumber(answers.whatsappNumber),
            "preferredContactPeriod" to FindPropertyRules.preferredContactPeriod(answers.preferredContactPeriod),
            "comments" to FindPropertyRules.comments(answers.comments),
        )
        return save(STEP_CONTACT, answers, errors, returnTo, session, model)
    }

    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val pending = pending(session)
        if (pending.firstIncompleteStep() != STEP_REVIEW) return redirect(pending.firstIncompleteStep())

        try {
            mandateClient.create(pending.toCreateMandateRequest(tenantContext.getTenant().currencyCode))
        } catch (ex: RestClientException) { // 4xx, 5xx and I/O alike: nothing the visitor can fix but retry
            LOGGER.error("Unable to create the mandate", ex)
            model.addAttribute("error", "Envoi impossible pour le moment. Réessayez dans quelques instants.")
            return step(STEP_REVIEW, pending, null, emptyMap(), model)
        }

        session.removeAttribute(PendingMandate.SESSION_ATTRIBUTE)
        session.setAttribute(
            SubmittedMandate.SESSION_ATTRIBUTE,
            SubmittedMandate(pending.email.orEmpty(), pending.whatsappNumber.orEmpty(), pending.preferredContactPeriod),
        )
        return redirect(STEP_CONFIRMATION)
    }
}
```

In `step(…)`, add per step:
- `STEP_AREA`, `STEP_REVIEW`: the `city`/`neighborhoods` lookup, copied from `JoinController.step`, capped at
  `FindPropertyRules.MAX_NEIGHBORHOODS`.
- `STEP_PROPERTY`: `propertyTypes = PROPERTY_TYPE_LABELS.keys.filter { FindPropertyRules.CATEGORIES_BY_TYPE[it]!!.contains(answers.propertyCategory) }`,
  plus `propertyTypeLabels = PROPERTY_TYPE_LABELS`.
- `STEP_REVIEW`: `categoryLabel = "Location, " + CATEGORY_LABELS[...]`. `propertyLabel` is built the way the
  prototype's `describeProperty` does it: the type label or "Tout type de bien", then the rooms / furniture /
  parking labels that apply, then `"80 m²"` when an area is set, joined with `", "`. The other labels are
  `priceLabel = "%,d".format(Locale.FRANCE, amount).replace('\u202F', ' ') + " FCFA par mois"`,
  `movingPeriodLabel`, `contactPeriodLabel`, and `amenityLabels`, which come from `AMENITY_LABELS` filtered by
  `answers.has`. **Check `describeProperty` in the prototype** (`find-property.js:827-836`) for the exact join and
  area format before writing it.

`save(…)` is `JoinController.save` with `JoinedAgent` replaced by `SubmittedMandate`. A new answer clears an old
confirmation.

- [ ] **Step 1: Write the failing tests.** Set up `FindPropertyControllerTest` exactly like `JoinControllerTest`
  (mocks for `NdMandateClient`, `NdLocationClient` and `TenantContext`, the same `locations` fixture, and
  `InternalResourceViewResolver("/templates/", ".html")`). Use `PendingMandateTest.COMPLETE` as the complete
  session. Write at least these tests:

```kotlin
@Test fun show()                                    // GET → step "need", stepNumber 1, stepCount 7, empty answers/errors
@Test fun showNeverSkipsAnIncompleteStep()          // session missing budget, GET ?step=contact → "budget"
@Test fun showUnknownStep()                         // ?step=nimportequoi → "need"
@Test fun need()                                    // POST propertyCategory=RESIDENTIAL → redirect:/find-property?step=area, session updated
@Test fun needMissing()                             // POST nothing → view, errors["propertyCategory"], session untouched
@Test fun areaRejectsForeignCity()                  // cityId=MONTREAL → errors["cityId"]
@Test fun areaRejectsNeighbourhoodOfOtherCity()     // YAOUNDE + 2001 (Akwa, Douala) → errors["neighborhoodIds"]
@Test fun areaRejectsTooManyNeighbourhoods()        // 4 ids → "Choisissez au plus 3 quartiers."
@Test fun areaBeforeNeedRedirects()                 // empty session, POST /area → redirect:/find-property
@Test fun propertyRejectsTypeOfOtherCategory()      // RESIDENTIAL session, propertyType=OFFICE → errors["propertyType"]
@Test fun propertyRejectsUnknownRooms()             // APARTMENT, rooms=7 → errors["rooms"]
@Test fun propertyStudioIgnoresRooms()              // STUDIO, rooms=3, furnitureType=ANY → saved with rooms=null
@Test fun propertyAreaWithSeparators()              // area="1 200" → answers.area == 1200
@Test fun propertyAreaNotANumber()                  // area="abc" → errors["area"]
@Test fun propertyTypesFilteredByCategory()         // GET ?step=property with COMMERCIAL → propertyTypes == [RETAIL, OFFICE, INDUSTRIAL, BUILDING, LAND]
@Test fun amenitiesDropsUnknownNames()              // amenities=waterMeter&amenities=pool → {waterMeter}
@Test fun amenitiesNoneChecked()                    // POST nothing → saved, redirect to budget
@Test fun budgetBelowMinimum()                      // priceAmount=4 999 → "Indiquez un loyer d'au moins 5 000 FCFA par mois."
@Test fun budget()                                  // priceAmount="150 000", movingPeriod=WITHIN_1_MONTH → 150000 saved
@Test fun contact()                                 // whatsappNumber="00237 670 00 00 01" → "+237670000001"
@Test fun contactCommentsTooLong()                  // 501 chars → errors["comments"]
@Test fun returnToReview()                          // complete session, POST /budget?returnTo=review → redirect ?step=review
@Test fun reviewLabels()                            // GET ?step=review → priceLabel "150 000 FCFA par mois", propertyLabel starts "Appartement, 3 chambres"
@Test fun submit()                                  // captor: request.mandateType TENANT, currency = tenant.currencyCode, neighbourhoodIds; redirect ?step=confirmation; Pending removed, Submitted set
@Test fun submitIncompleteRedirects()               // session missing contact → redirect ?step=contact, client never called
@Test fun submitServerError()                       // client throws HttpServerErrorException(500) → view, step review, model["error"], Pending kept
@Test fun submitTwiceCreatesOneMandate()            // submit, then POST /review again → second redirects to /find-property; verify(mandateClient, times(1)).create(any())
@Test fun confirmation()                            // Submitted in session, GET ?step=confirmation → maskedEmail, whatsappNumber, contactPeriodLabel "le soir"
@Test fun confirmationWithoutSubmissionShowsWizard()// no Submitted → step "need"
```

Give each one a full body in the style of `JoinControllerTest` (MockMvc perform, `model(result)`,
`kotlin.test` assertions). Here are three of them in full, as a model for the rest:

```kotlin
@Test
fun propertyRejectsTypeOfOtherCategory() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(propertyType = null, rooms = null))

    val result = mvc.perform(
        post("/find-property/property").session(session).param("propertyType", "OFFICE").param("furnitureType", "ANY"),
    ).andReturn()

    assertEquals("find-property", result.modelAndView?.viewName)
    assertEquals("property", model(result)["step"])
    assertEquals("Choisissez un type de bien proposé.", errors(result)["propertyType"])
    assertNull((session.getAttribute(PendingMandate.SESSION_ATTRIBUTE) as PendingMandate).propertyType)
}

@Test
fun submit() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
    doReturn(CreateMandateResponse(mandateId = 42)).whenever(mandateClient).create(any())

    val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

    assertEquals("redirect:/find-property?step=confirmation", result.modelAndView?.viewName)
    val request = argumentCaptor<CreateMandateRequest>()
    verify(mandateClient).create(request.capture())
    assertEquals(MandateType.TENANT, request.firstValue.mandateType)
    assertEquals("XAF", request.firstValue.price.currency)
    assertEquals(listOf(1001L, 1002L), request.firstValue.neighbourhoodIds)
    assertNull(session.getAttribute(PendingMandate.SESSION_ATTRIBUTE))
    assertEquals("ray@gmail.com", (session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE) as SubmittedMandate).email)
}

@Test
fun submitServerError() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
    doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).create(any())

    val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

    assertEquals("review", model(result)["step"])
    assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    assertEquals(complete, session.getAttribute(PendingMandate.SESSION_ATTRIBUTE))
    assertNull(session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE))
}
```

(`setUp` stubs `tenantContext.getTenant()` with `Tenant(id = 1, countryCode = "CM", currencyCode = "XAF")`.
`complete` is `PendingMandateTest.COMPLETE` with the fixture's ids. `errors(result)` is
`model(result)["errors"] as Map<*, *>`.)

- [ ] **Step 2: Run it and check that it fails:** `mvn test -Dtest=FindPropertyControllerTest`
- [ ] **Step 3: Implement the controller and the security matcher**
- [ ] **Step 4: Run it and check that it passes.** Also run `mvn test` for the whole module to catch regressions
  in `JoinControllerTest`.
- [ ] **Step 5: Commit:** `git commit -m "npc: add /find-property wizard controller"`

---

### Task 5: The Thymeleaf template, CSS, and template test

**Files:**
- Create: `src/main/resources/templates/find-property.html`
- Create: `src/main/resources/public/assets/css/find-property.css` (copy of `src/main/html/assets/css/find-property.css`, with the header comment's `rejoindre.css` changed to `join.css`)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: the model attributes listed in Task 4.

Build the template from the prototype markup, using `templates/join.html` as the template for every mechanic:

1. **Head:** same as `join.html` (fonts, intl-tel-input, slim-select CDN at the same versions,
   `partner-central.css`, `join.css`, and then `find-property.css`). Scripts: `util.js`, then `find-property.js`.
   The title is "Trouver un bien — Ndopify", or "Demande envoyée — Ndopify" on the confirmation. Keep the
   prototype's `<meta name="description">` and `noindex`.
2. **Header:** copy `join.html`'s header (tenant logo with fallback, "Quitter" → `tenant.publicPortalUrl`).
   The brand link goes to `tenant.publicPortalUrl` (falling back to `@{/find-property}`), not `/login`: visitors
   have no account.
3. **Progress:** "Étape N sur 7" with `aria-valuenow=stepNumber` and `scaleX(stepNumber/stepCount)`,
   exactly as `join.html` renders it.
4. **One `<form method="post">` per step**, `th:action="@{/find-property/{s}(s=${step},returnTo=${returning} ? 'review' : null)}"`,
   rendered with `th:if="${step == '…'}"`. Copy each step's inner markup **verbatim** from the prototype's
   `<fieldset data-step="…">`, then change these things:
   - Pre-fill radios/checkboxes with `th:checked="${answers.x == 'VALUE'}"` (for enum fields compare
     `${answers.propertyCategory?.name() == 'RESIDENTIAL'}`). Text inputs get `th:value`. Budget/area get
     `th:value="${answers.priceAmount} ? ${#numbers.formatInteger(answers.priceAmount, 1, 'WHITESPACE')} : ''"`.
   - Error blocks: keep the prototype's `#x-error` markup and show it with `th:unless="${errors['x'] == null}"`,
     the text being `th:text="${errors['x']}"`. Add `aria-invalid="true"` on the field when it's in error, as
     `join.html` does.
   - **need:** no "Précédent" (step 1). The "Suivant" button is a real `type="submit"`, with no `aria-disabled`.
   - **area:** use the `<select>`s from `join.html`'s area step (initial `<option>`s from `city`/`neighborhoods`,
     `data-locations-url="@{/locations}"`). Keep the prototype's labels and counter.
   - **property:** render the `propertyType` choices with `th:each="type : ${propertyTypes}"` and
     `propertyTypeLabels`, then the "Indifférent" choice (`value="ANY"`). Render the `rooms`, `furnitureType`
     and `parkings` groups **without** `hidden` (decision 7), each with `data-group` so the JS can hide it.
     Rooms are only rendered when `answers.propertyCategory == RESIDENTIAL`.
   - **amenities:** checkboxes become `name="amenities" th:value="'waterMeter'" th:checked="${answers.has('waterMeter')}"`.
     Keep the four fieldsets (Eau, Électricité, Accès, Sécurité) and the prototype's labels.
   - **contact:** the WhatsApp field follows `join.html`'s identity step pattern: the visible input plus the
     hidden `whatsappNumber` that `util.js` fills with E.164, and a no-JS fallback where the visible field posts
     itself. Copy that pattern exactly. Comments: `<textarea name="comments" maxlength="500">` with
     `th:text="${answers.comments}"`.
   - **review:** a `<dl>` from the review labels, where each "Modifier" is a link
     `@{/find-property(step='…',returnTo='review')}` (as in `join.html`'s review, not the prototype's buttons).
     Show `error` in `.alert--error` when it's set. The submit button posts to `/find-property/review`.
   - Drop the prototype's client-only bits: `data-incomplete`, `aria-disabled` and `data-sending` (the JS can
     set that one).
5. **Confirmation** (`th:if="${step == 'confirmation'}"`): the prototype's `<section class="confirmation">`
   verbatim, with `maskedEmail`, `whatsappNumber` (formatted by `util.js`'s `formatPhone` when JS runs; the raw
   E.164 is acceptable without it) and `contactPeriodLabel` (fallback "au moment choisi"). "Retour à l'accueil"
   → `tenant.publicPortalUrl`.
6. Add a `<!--/* … */-->` header comment like `join.html`'s that explains the one-page / one-form-per-step
   contract.

- [ ] **Step 1: Write the failing template test.** Copy `JoinTemplateTest.setUp` (real `SpringTemplateEngine`,
  `ClassLoaderTemplateResolver` on `templates/`, `ThymeleafViewResolver`). Write at least these tests:

```kotlin
@Test fun needStepHasNoPrevious()            // doc.select("[data-prev]").isEmpty(); form action "/find-property/need"
@Test fun needPrefilled()                    // RESIDENTIAL in session → input[value=RESIDENTIAL] has checked
@Test fun needError()                        // POST empty → #propertyCategory-error visible with the message
@Test fun areaShowsChosenCity()              // option[value=2220957][selected] text "Yaoundé"; 2 neighbourhood options selected
@Test fun propertyShowsOnlyCategoryTypes()   // COMMERCIAL → no input[value=APARTMENT], has input[value=OFFICE] and [value=ANY]; no [data-group=rooms]
@Test fun amenitiesPrefilled()               // input[name=amenities][value=waterMeter] checked, [value=solarPanel] not
@Test fun budgetFormatted()                  // #priceAmount value "150 000"
@Test fun contactPrefilledAndEscaped()       // comments "<script>" rendered as text, not markup
@Test fun returningShowsBackToReview()       // ?step=budget&returnTo=review → button text "Revenir à la revue", action has returnTo=review
@Test fun reviewRendersLabels()              // contains "Location, usage résidentiel", "150 000 FCFA par mois", "Yaoundé — Bastos, Essos", amenity labels
@Test fun reviewEditLinks()                  // a[href="/find-property?step=budget&returnTo=review"] exists
@Test fun reviewServerError()                // client throws → .alert--error contains "Envoi impossible"
@Test fun confirmation()                     // h2 "Demande envoyée"; masked email; "le soir"; link to publicPortalUrl
@Test fun headerUsesTenant()                 // img src = tenant.logoUrl; [data-exit] href = publicPortalUrl
```

Write each with a full body, in the style of `JoinTemplateTest` (perform → `Jsoup.parse(result.response.contentAsString)`
→ assert on selectors). Check the exact `"Yaoundé — Bastos, Essos"` format against the prototype's review
(`find-property.js` `render`: `city + " — " + names.join(", ")`).

- [ ] **Step 2: Run it and check that it fails:** `mvn test -Dtest=FindPropertyTemplateTest` (template missing).
- [ ] **Step 3: Write the template and copy the CSS**
- [ ] **Step 4: Run it and check that it passes.** Then run the app (`mvn spring-boot:run` here and in
  `ndopify-server`) and walk through all seven steps **with JS disabled**, checking that every step posts and
  that a mandate row appears (`GET http://localhost:8080/v1/mandates`).
- [ ] **Step 5: Commit:** `git commit -m "npc: add find-property template"`

---

### Task 6: `find-property.js`, the progressive enhancement

**Files:**
- Create: `src/main/resources/public/assets/js/find-property.js`

**Interfaces:**
- Consumes: the template's DOM from Task 5 (`data-page="find-property"`, `data-group="rooms|furnitureType|parkings|propertyType"`,
  `data-locations-url`, `#priceAmount`, `#area`, `#comments` with `[data-comments-counter]`, and the WhatsApp
  input pair), plus `window.NdUtil` (`util.js`: `bindPhone`, `formatPhone`) and `GET /locations`
  (`LocationController`).

Start from `public/assets/js/join.js`, not from the prototype. The prototype drives the whole wizard client-side
(sessionStorage, history, simulated server), and none of that belongs in the BFF. Take these parts from
`join.js` **as they are**: the helpers (`$`, `$all`, `announce`, `setFieldError`, `fold`, `setActive`), the
`searchLocations`/`searchAllLocations`, `slimSelect`, `initNext`, `initPhones` and `initSector` functions, and
the double-submit guard. Take these parts from the prototype `find-property.js`:

- `bindNumber` / `digitsOf` / `formatNumber`, wired on `#priceAmount` (max 9 digits) and `#area` (max 7 digits).
  The server parses separators (Task 2), so posting `"150 000"` is fine.
- `needsRooms` / `needsFurniture` / `asksParkings`, plus an `updateQuestions()` that hides the follow-up groups
  that don't apply to the checked `propertyType`, both on `change` and on load. Hidden groups also get their
  inputs `disabled`, so they don't post, which matches what the server keeps.
- the comment counter, "N sur 500 caractères".
- On the confirmation, format `[data-done-whatsapp]` with `NdUtil.formatPhone`.

The file header comment follows `join.js`'s: what the server does, then the list of enhancements, ending with
"every step still posts without it".

- [ ] **Step 1: Write the script** (no JS test harness exists in this module; checks are manual, below)
- [ ] **Step 2: Check it by hand in a browser** (`mvn spring-boot:run`, `/find-property`):
  - property step: picking STUDIO hides "Nombre de chambres", picking LAND hides "Meublé ?" and parking, and
    "Indifférent" shows furniture and parking.
  - budget: typing `150000` shows `150 000`, and the caret stays after the digit typed.
  - area: the city type-ahead works, and the neighbourhoods load for the city and stop at 3.
  - contact: a foreign number via the country picker posts E.164, and the counter updates.
  - review: a double-click on "Envoyer ma demande" sends one POST (Network tab).
  - the browser console shows no errors on any step.
- [ ] **Step 3: Commit:** `git commit -m "npc: enhance find-property wizard"`

---

### Task 7: Docs and the full verification

**Files:**
- Modify: `CLAUDE.md`. Under `ndopify-partner-central`, add a bullet for `/find-property`
  (`FindPropertyController`, `ui/findproperty/`): it's public, it has seven steps, it uses `PendingMandate` /
  `SubmittedMandate`, and it posts `POST /v1/mandates` via `NdMandateClient`. It's also the only validator of
  mandates (decision 3). Add `/find-property/**` to the list of unauthenticated paths. Under "Current
  domains", add **mandate** / **customer**, which aren't documented today.
- Modify: `modules/ndopify-partner-central/README.md`, **only if** it lists pages. Use a targeted edit and keep
  the badges.

- [ ] **Step 1: Make the doc edits**
- [ ] **Step 2: Run the full gate:** `cd modules/ndopify-partner-central && mvn verify`. ktlint and JaCoCo must
  pass. If the coverage gate fails, add tests for the uncovered branches (likely `step()` label branches). Don't
  lower the threshold.
- [ ] **Step 3: Commit:** `git commit -m "docs: document /find-property"`

---

## Risks and follow-ups (not in this plan)

- **Mandates aren't validated on the server.** Any client other than this BFF can create mandates with
  `UNKNOWN` enums, `cityId = -1`, or neighbourhoods from another city. Open a ticket to add the same rules to
  `MandateService.create` / `CreateMandateRequest`, then `400` with `ErrorCode`s. When that lands, map those
  codes to field errors in `FindPropertyController.submit`.
- **Spam and abuse.** The page is public and unauthenticated, and each submit creates a customer and a party.
  There's no rate limit or captcha. Consider one before linking the page from the public website.
- **The `com.wutsi.koki.listing.dto` package** in `ndopify-dto` is a leftover from copied code. Renaming it is
  a separate DTO release, which touches the server too.
- **Entry point:** nothing links to `/find-property` yet. The link from `ndopify-website` / the tenant's public
  portal is a separate change.
