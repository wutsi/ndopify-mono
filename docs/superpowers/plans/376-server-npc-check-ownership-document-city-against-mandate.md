# The ownership document's city against the mandate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A LISTING mandate's owner verdict fails with `OWNERSHIP_PROPERTY_MISMATCH` when the proof-of-ownership title names the customer but its locality is a known city other than the mandate's, and every unresolved or missing comparison stays a neutral hint on the mandate page.

**Architecture:** `ndopify-dto` gains `OwnershipLocation` (the mandate's city, the title's locality, `UNKNOWN` / `MATCH` / `MISMATCH` / `UNRESOLVED`) on `OwnershipVerification`. `OwnershipVerdictService` (#297) resolves the title's `locality`, else its `arrondissement`, through `LocationService.findCity` — an exact, normalised, country-scoped `CITY` match — and classifies the ownership check as pass, property mismatch or unresolved. Partner Central words the same verdict: a neutral info hint for `UNRESOLVED` (the two places side by side, a link to Documents) and the new reason with those places for `MISMATCH`.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, `ndopify-dto`, Thymeleaf, `kotlin.test` + `mockito-kotlin`, endpoint tests against MySQL, jsoup template tests, the approved static prototype.

**Spec:** GitHub issue #376 — the enriched story *is* the design (its *The rule (v1.1)*, *Contract*, *Acceptance criteria*, *Decisions and assumptions* and *Prototype (approved 2026-11-27)*), plus the approved prototype in `modules/ndopify-partner-central/src/main/html/v2/`: `mandate.html` (the hint block and the reason's places), `assets/js/mandate-ownership.js` (the sample states), `assets/js/ownership-copy.js` (the new wording), `assets/css/ownership.css` (the two places), and `README.md` (the per-state review table).

## Global Constraints

- Three modules: `modules/ndopify-dto` (contract, released), `modules/ndopify-server` (the rule), `modules/ndopify-partner-central` (the hint and the reason).
- `UI: yes`: the approved prototype is the baseline. The first NPC task ports its markup, copy and styles; it does not design them again.
- `Shared code: ndopify-dto`: `OwnershipLocation` / `OwnershipLocationStatus` / `OwnershipVerification.location` need a release before the server and Partner Central deploy. The branch must not end on a `-SNAPSHOT`.
- No new table, migration, scheduled job, write, event or config property: the comparison is derived on every read, with the documents.
- The reason code is a plain string (`OwnershipReasonCodes`), like #297's; the code itself needs no DTO release.
- Never log a city name, a locality, a name, a document id or another extracted value: `mandate_id`, `ownership_status` and the reason codes only (the #327 rule).
- `MISMATCH` is the only new failure. A locality that doesn't resolve, a title with no locality, a mandate with no `cityId` (or a `cityId` that isn't a `CITY`) is `UNRESOLVED`: never `FAILED`.
- Resolution is an **exact** normalised match (`lowercase`, accents removed, punctuation removed, no substring), against `CITY` locations **in the mandate city's country** only.
- The valid-candidate rule of #297 carries over: one customer-naming title in the mandate's city is enough, whatever another title says.
- A title that names someone else keeps `OWNERSHIP_DOCUMENT_NAME_MISMATCH`; its city is never compared.
- French UI copy, English code / comments / docs. Assertions with `kotlin.test`; mocks with `com.nhaarman.mockitokotlin2`; tests mirror the main package.
- `mvn validate` (ktlint) and the JaCoCo gate must pass: `mvn verify` in each touched module (`ndopify-server` needs the local MySQL).
- #375 and #377 touch the same ownership copy and the mandate page in parallel. Where this plan says `MandateOwnershipViews`, put the wording in `OwnershipCopy` instead if #375 has landed; the approved prototype's `?ownership=overridden` state (#377) stays untouched and shows no hint.

## Review Focus

1. **A title naming someone else and another city** — the reason must stay `OWNERSHIP_DOCUMENT_NAME_MISMATCH`, never the property one; the city is not compared on a title that doesn't establish the customer's ownership (Task 3).
2. **A city name that exists only in another country, or a substring** — must stay `UNRESOLVED`, never a false `MISMATCH`; the country filter and the exact equality are the guard (Task 2, Task 3).
3. **A mandate with no city, or a `cityId` that is not a `CITY`** — must be `UNRESOLVED`, never a failure; a missing city is missing data, not a contradiction (Task 3).
4. **A village or quarter, or no locality at all** — must not fail an honest owner; the title stays a valid proof of ownership and the page shows the hint (Task 3, Task 5).
5. **An old payload or client without `location`** — the DTO default is `UNKNOWN`, so no hint, no NPE, and the page renders #298's section as before (Task 1, Task 5).

---

## File Structure

- `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/` — `OwnershipLocationStatus.kt`, `OwnershipLocation.kt`, and the modified `OwnershipVerification.kt`.
- `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/LocationService.kt` — the exact, country-scoped city lookup.
- `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/` — `OwnershipReasonCodes.kt` (the new code), `OwnershipVerdictService.kt` (the comparison; still the only place the verdict is derived).
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/` — `MandateOwnershipView.kt` (the hint and places views), `MandateOwnershipViews.kt` (the wording), and `OwnershipCopy.kt` if #375 has landed.
- `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html` — the hint block and the reason's places.
- `modules/ndopify-partner-central/src/main/assets/css/ownership.css` — the two places, stacked below 480 px.
- Tests: the service / endpoint / view / template tests beside their production classes, and the SQL fixtures under `src/test/resources/db/test/`.
- Docs: the mandate domain bullet of `modules/ndopify-server/CLAUDE.md` and the owner-verification paragraph of `modules/ndopify-partner-central/CLAUDE.md`.

---

### Task 1: The `location` contract and its `ndopify-dto` release

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipLocationStatus.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipLocation.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification.kt`
- Modify: `pom.xml:52` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `OwnershipLocationStatus { UNKNOWN, MATCH, MISMATCH, UNRESOLVED }`.
- Produces: `OwnershipLocation(mandateCity: String = "", documentLocality: String = "", status: OwnershipLocationStatus = OwnershipLocationStatus.UNKNOWN)`.
- Produces: `OwnershipVerification(status, reasons, location: OwnershipLocation = OwnershipLocation())` — `location` after `reasons` (after `overridden` when #377 has landed).

- [ ] **Step 1: Write the two DTOs**

```kotlin
// OwnershipLocationStatus.kt
package com.wutsi.ndopify.mandate.dto

/**
 * How a listing mandate's ownership document compares to its city (#376): `UNKNOWN` when nothing was compared (no
 * customer-naming title, or the ownership check is missing / in flight / unusable), `MATCH` when the title's city is
 * the mandate's, `MISMATCH` when it is a known other city, `UNRESOLVED` when the locality can't be matched or one
 * side is missing — the last two never decide a verdict by themselves beyond `MISMATCH`'s reason.
 */
enum class OwnershipLocationStatus {
    UNKNOWN,
    MATCH,
    MISMATCH,
    UNRESOLVED,
}
```

```kotlin
// OwnershipLocation.kt
package com.wutsi.ndopify.mandate.dto

/**
 * The city comparison of a listing mandate's owner verdict (#376): the mandate's city and the ownership document's
 * locality, with how they compare. Display names only — never a document id, a page, a path or a storage URL.
 */
data class OwnershipLocation(
    val mandateCity: String = "",
    val documentLocality: String = "",
    val status: OwnershipLocationStatus = OwnershipLocationStatus.UNKNOWN,
)
```

- [ ] **Step 2: Add `location` to `OwnershipVerification`**

```kotlin
data class OwnershipVerification(
    val status: OwnershipVerificationStatus = OwnershipVerificationStatus.UNKNOWN,
    val reasons: List<OwnershipVerificationReason> = emptyList(),
    val location: OwnershipLocation = OwnershipLocation(),
)
```

Extend its KDoc: the verdict now also carries the city comparison; `location.status` is a display hint and only `MISMATCH` adds a reason.

- [ ] **Step 3: Build the DTO locally and point the server at it**

Run: `cd modules/ndopify-dto && mvn -q install`
Set `<ndopify-dto.version>0.0.114-SNAPSHOT</ndopify-dto.version>` in the root `pom.xml` while developing (use the next release if #377 has already released `0.0.114`).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipLocationStatus.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipLocation.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification.kt
git commit -m "feat(dto): add the ownership location comparison" -m "Refs #376"
```

- [ ] **Step 5: Release the DTO**

Run the developer agent's shared-module release procedure for `ndopify-dto` (the profile's `shared_modules`): push the DTO change, wait for `ndopify-dto-main.yml`, then commit the released `<ndopify-dto.version>` (`fix dependencies: ndopify-dto <version>`). The branch must not end on a `-SNAPSHOT`.

---

### Task 2: `LocationService.findCity` — an exact, country-scoped city name

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/LocationService.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/refdata/server/service/LocationServiceFindCityTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/refdata/LocationServiceFindCity.sql`

**Interfaces:**
- Produces: `fun findCity(name: String?, country: String?): LocationEntity?` — the `CITY` of [country] whose name equals [name] after normalisation; null when either is blank, when no city matches exactly, or when only a neighbourhood or a city of another country matches.

- [ ] **Step 1: Write the failing integration test**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/LocationServiceFindCity.sql"])
class LocationServiceFindCityTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: LocationService

    @Test
    fun `an exact city name matches case and accents`() {
        assertEquals(2370101L, service.findCity("YAOUNDÉ", "CM")?.id)
    }

    @Test
    fun `punctuation and spacing are ignored`() {
        assertEquals(2370301L, service.findCity("Sainte Anne", "CM")?.id)
    }

    @Test
    fun `a substring does not match`() {
        assertNull(service.findCity("Yaound", "CM"))
    }

    @Test
    fun `a neighbourhood does not match`() {
        assertNull(service.findCity("Bastos", "CM"))
    }

    @Test
    fun `a city of another country does not match`() {
        assertNull(service.findCity("Montreal", "CM"))
    }

    @Test
    fun `a blank name or country does not match`() {
        assertNull(service.findCity(" ", "CM"))
        assertNull(service.findCity("Yaounde", null))
    }
}
```

The fixture `LocationServiceFindCity.sql` inserts, after `clean.sql`: `2370101` `CITY` `CM` `Yaounde` / `Yaounde`; `2370201` `CITY` `CM` `Douala` / `Douala`; `2370301` `CITY` `CM` `Sainte-Anne` / `Sainte-Anne`; `237010101` `NEIGHBORHOOD` `CM` `Bastos` / `Bastos` (parent `2370101`); `151401` `CITY` `CA` `Montreal` / `Montreal`; and the two `COUNTRY` rows if the fixture's foreign key needs them.

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=LocationServiceFindCityTest`
Expected: FAIL — `findCity` is unresolved.

- [ ] **Step 3: Implement `findCity`**

```kotlin
/**
 * The [LocationType.CITY] named [name] in [country], compared after [normalize] — accents, case and punctuation
 * ignored, no substring. Null when [name] or [country] is blank, when only a neighbourhood or a city of another
 * country matches, or when no city name is exactly equal. The `search` prefix narrows the candidates; the equality
 * is checked here, in Kotlin.
 */
fun findCity(name: String?, country: String?): LocationEntity? {
    val keyword = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (country.isNullOrBlank()) return null
    val wanted = normalize(keyword)
    return search(
        SearchLocationRequest(
            keyword = keyword,
            types = listOf(LocationType.CITY),
            country = country,
            limit = 100,
        ),
    ).firstOrNull { city -> normalize(city.name) == wanted }
}

/** The comparable form of a city or locality name: accents, case and punctuation removed. */
private fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(REGEX_UNACCENT, "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
```

Import `com.wutsi.ndopify.refdata.dto.SearchLocationRequest`. `limit = 100` is `OffsetLimitPageRequest.MAX_LIMIT`; a city name with more than 100 prefix matches is not resolved (fail-open, a hint).

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=LocationServiceFindCityTest`
Expected: PASS, every case.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/LocationService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/refdata/server/service/LocationServiceFindCityTest.kt \
        modules/ndopify-server/src/test/resources/db/test/refdata/LocationServiceFindCity.sql
git commit -m "feat(refdata): resolve an exact city name for the ownership check" -m "Refs #376"
```

---

### Task 3: The city comparison in `OwnershipVerdictService`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipReasonCodes.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt`

**Interfaces:**
- Consumes: `LocationService.findCity(name: String?, country: String?): LocationEntity?` (Task 2); `OwnershipLocation`, `OwnershipLocationStatus` (Task 1).
- Produces: `OwnershipReasonCodes.PROPERTY_MISMATCH = "OWNERSHIP_PROPERTY_MISMATCH"`.
- Produces: `OwnershipVerdictService(..., locationService: LocationService, ...)`.
- Produces: `internal fun evaluate(documents, customerName, today, mandateCity: LocationEntity? = null, resolveCity: (String) -> LocationEntity? = { null }): OwnershipVerification`.

- [ ] **Step 1: Write the failing tests**

Add a `locationService` mock to the test class and to the `service(threshold)` helper, a `city(id, name, country, type = LocationType.CITY)` helper, and a `resolve` map helper. Keep every existing test call compiling through the new defaults. Add:

```kotlin
@Test
fun `a title in the mandate's city matches case and accents`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "yaoundé")),
        mandateCity = yaounde,
        resolveCity = { name -> if (name == "yaoundé") yaounde else null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
    assertEquals("Yaounde", verification.location.mandateCity)
    assertEquals("yaoundé", verification.location.documentLocality)
}

@Test
fun `a title in another known city fails the owner`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val douala = city(2370201, "Douala", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        mandateCity = yaounde,
        resolveCity = { name -> if (name == "Douala") douala else null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.PROPERTY_MISMATCH)
    assertEquals(OwnershipLocationStatus.MISMATCH, verification.location.status)
    assertEquals("Yaounde", verification.location.mandateCity)
    assertEquals("Douala", verification.location.documentLocality)
}

@Test
fun `an unresolved quarter is a hint, not a failure`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Mvog-Ada")),
        mandateCity = yaounde,
        resolveCity = { null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("Yaounde", verification.location.mandateCity)
    assertEquals("Mvog-Ada", verification.location.documentLocality)
}

@Test
fun `the arrondissement resolves when the locality is blank`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(
            DocumentType.LAND_TITLE,
            DocumentStatus.EXTRACTED,
            landTitle("Jean Paul Mbarga", locality = null, arrondissement = "Yaoundé"),
        ),
        mandateCity = yaounde,
        resolveCity = { name -> if (name == "Yaoundé") yaounde else null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
    assertEquals("Yaoundé", verification.location.documentLocality)
}

@Test
fun `no mandate city is a hint, never a mismatch`() {
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        mandateCity = null,
        resolveCity = { null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("", verification.location.mandateCity)
    assertEquals("Douala", verification.location.documentLocality)
}

@Test
fun `a non-city mandate location is a hint`() {
    val neighborhood = city(237010101, "Bastos", "CM", LocationType.NEIGHBORHOOD)
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        mandateCity = neighborhood,
        resolveCity = { null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("Bastos", verification.location.mandateCity)
}

@Test
fun `a title with no locality is a hint`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        mandateCity = yaounde,
        resolveCity = { null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("", verification.location.documentLocality)
}

@Test
fun `a title naming someone else keeps the name mismatch`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val douala = city(2370201, "Douala", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Pierre Durand", locality = "Douala")),
        mandateCity = yaounde,
        resolveCity = { name -> if (name == "Douala") douala else null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH)
    assertEquals(OwnershipLocationStatus.UNKNOWN, verification.location.status)
}

@Test
fun `a valid title in the mandate's city wins over another city`() {
    val yaounde = city(2370101, "Yaounde", "CM")
    val douala = city(2370201, "Douala", "CM")
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Yaoundé")),
        mandateCity = yaounde,
        resolveCity = { name -> if (name == "Yaoundé") yaounde else if (name == "Douala") douala else null },
    )

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
}
```

Add a `verify()` test: `mandate.cityId = 2370101`, `locationService.findByIdOrNull(2370101)` → Yaounde, `locationService.findCity("Yaoundé", "CM")` → Yaounde, the title's locality `Yaoundé` → `VERIFIED` and `location.status = MATCH`; and a `cityId = -1` test → `UNRESOLVED`, `mandateCity = ""`. The existing `mockMandate` helper must stub `locationService.findByIdOrNull(-1)` or rely on Mockito's null default; add the `locationService` to the constructor.

Add the log test: with the same `verify` setup (title locality `Douala`, mandate city Yaounde), assert `logger.entries.none { it.second?.contains("Douala") == true || it.second?.contains("Yaounde") == true }` — the log line carries the mandate id, the status and the reason codes only (acceptance criterion 12).

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: FAIL — the constructor and `evaluate` don't accept the city, and `PROPERTY_MISMATCH` is unresolved.

- [ ] **Step 3: Add the reason code**

In `OwnershipReasonCodes`, after `DOCUMENT_NAME_MISMATCH`:

```kotlin
const val PROPERTY_MISMATCH = "OWNERSHIP_PROPERTY_MISMATCH"
```

- [ ] **Step 4: Implement the comparison**

In `OwnershipVerdictService`:

1. Add `private val locationService: LocationService` after `tenantService`; import `LocationService`, `LocationEntity`, `LocationType`, and the two new DTOs.
2. In `verify`:
   ```kotlin
   val mandateCity = mandate.cityId.takeIf { it > 0 }?.let { locationService.findByIdOrNull(it) }
   val resolveCity: (String) -> LocationEntity? = { name ->
       mandateCity
           ?.takeIf { it.type == LocationType.CITY }
           ?.let { city -> locationService.findCity(name, city.country) }
   }
   val verification = evaluate(documents, customerName, today, mandateCity, resolveCity)
   ```
   Keep the log line as is: `mandate_id`, `ownership_status`, `ownership_reason_codes`; never the city nor the locality.
3. `evaluate` gains `mandateCity: LocationEntity? = null` and `resolveCity: (String) -> LocationEntity? = { null }`; pass both to `ownershipOutcome`. Set the verdict's `location` from the ownership outcome:
   ```kotlin
   val location = when (ownership) {
       is Outcome.Pass -> ownership.location
       is Outcome.PropertyMismatch -> ownership.location
       else -> OwnershipLocation()
   }
   return OwnershipVerification(status = status, reasons = ..., location = location)
   ```
4. `Outcome` becomes:
   ```kotlin
   sealed interface Outcome {
       data class Pass(val location: OwnershipLocation = OwnershipLocation()) : Outcome
       object Missing : Outcome
       object InFlight : Outcome
       object Expired : Outcome
       object Mismatch : Outcome
       data class PropertyMismatch(val location: OwnershipLocation) : Outcome
       data class Unusable(val details: List<String>) : Outcome
   }
   ```
   The identity check's `Pass` uses the default `OwnershipLocation()`. `isDecisive()` includes `this is Outcome.PropertyMismatch`. `reason(PropertyMismatch)` returns `OwnershipReasonCodes.PROPERTY_MISMATCH` with empty details.
5. `ownershipOutcome` first computes the existing name outcome. When a customer-naming `EXTRACTED` title exists (the current `valid` branch), call the new classification instead of returning `Pass` immediately:
   ```kotlin
   private fun ownershipOutcome(
       candidates: List<MandateDocumentEntity>,
       customerName: String,
       mandateCity: LocationEntity?,
       resolveCity: (String) -> LocationEntity?,
   ): Outcome
   ```
   - `named` = the `EXTRACTED` candidates whose decoded `TitleData.owners` name the customer, in the repository's order.
   - If `named` is empty, the existing `InFlight` / `Unusable` / `Mismatch` / `Unusable` order is unchanged, with no location.
   - `mandateCityId = mandateCity?.takeIf { it.type == LocationType.CITY }?.id`; the name for display is `mandateCity?.name.orEmpty()`.
   - For each named title, `resolveTitle(data, resolveCity)` returns `Pair<String, LocationEntity?>`: try the locality, then the arrondissement, each trimmed and non-blank; the first value that resolves wins; when none resolves, return the first value tried (locality, else arrondissement) with null. A title with neither returns `"" to null`.
   - If `mandateCityId != null` and one resolved title's city id equals it → `Pass(OwnershipLocation(name, value, MATCH))`.
   - Else if `mandateCityId != null` and one resolved title's city id differs → `PropertyMismatch(OwnershipLocation(name, value, MISMATCH))` (first in document order).
   - Else → `Pass(OwnershipLocation(name, firstNamedValue, UNRESOLVED))`.
   A non-`CITY` mandate location or a null one makes `mandateCityId` null: `UNRESOLVED`, never `MISMATCH`.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: PASS, the existing cases and the new ones.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipReasonCodes.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt
git commit -m "feat(server): compare the ownership document's city with the mandate" -m "Refs #376"
```

---

### Task 4: The endpoint integration test and its fixture

**Files:**
- Modify: `modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateOwnershipEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateOwnershipEndpointTest.kt`

**Interfaces:**
- Consumes: the verdict's `location` and `OwnershipReasonCodes.PROPERTY_MISMATCH` (Task 3); `OwnershipLocationStatus` (Task 1).
- Produces: nothing — the read endpoint already returns the enriched `OwnershipVerification`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a title in the mandate's city verifies with a match`() {
    val verification = get(108)

    assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
    assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
    assertEquals("Yaounde", verification.location.mandateCity)
    assertEquals("Yaoundé", verification.location.documentLocality)
}

@Test
fun `a title in another city fails on the property`() {
    val verification = get(109)

    assertEquals(OwnershipVerificationStatus.FAILED, verification.status)
    assertEquals(listOf(OwnershipReasonCodes.PROPERTY_MISMATCH), verification.reasons.map { it.code })
    assertEquals(OwnershipLocationStatus.MISMATCH, verification.location.status)
    assertEquals("Yaounde", verification.location.mandateCity)
    assertEquals("Douala", verification.location.documentLocality)
}

@Test
fun `an unresolved quarter is a hint, not a failure`() {
    val verification = get(110)

    assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("Bonabéri", verification.location.documentLocality)
}

@Test
fun `a mandate without a city is a hint`() {
    val verification = get(111)

    assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("", verification.location.mandateCity)
    assertEquals("Douala", verification.location.documentLocality)
}

@Test
fun `a title without a locality is a hint`() {
    val verification = get(112)

    assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
    assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
    assertEquals("", verification.location.documentLocality)
}
```

Extend the existing no-leak test for mandate 109: the body may contain `Yaounde` and `Douala` (the two places), but never `TF-`, `mandate/`, a document id, a document type or a page reference.

- [ ] **Step 2: Run them and see them fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateOwnershipEndpointTest`
Expected: FAIL — mandates 108–112 are 404 (the fixture lacks them).

- [ ] **Step 3: Add the fixture rows**

In `GetMandateOwnershipEndpoint.sql`:
- Add `2370202` `CITY` `CM` `Douala` / `Douala`, and `237020101` `NEIGHBORHOOD` `CM` `Bonabéri` / `Bonaberi` (parent `2370201`).
- Add mandates 108–112 on tenant 1, agent 20, customer 10, `LISTING`/`RENTAL`, all with `city_id = 2370201` except 111 (`-1`):
  - 108: ID `EXTRACTED` + `LAND_TITLE` `{"owners":["Customer One"],"locality":"Yaoundé"}`.
  - 109: ID + title `locality: "Douala"`.
  - 110: ID + title `locality: "Bonabéri"`.
  - 111: ID + title `locality: "Douala"`, `city_id = -1`.
  - 112: ID + title with no `locality` nor `arrondissement`.
- Give every new document its own id (1014–1023), `data_version = 1`, `"valid":true`, and a `fieldConfidences` containing the fields used, including `"locality":0.9` where present.

- [ ] **Step 4: Run the tests again**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateOwnershipEndpointTest`
Expected: PASS, every case (needs the local MySQL).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateOwnershipEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateOwnershipEndpointTest.kt
git commit -m "test(server): cover the ownership city comparison end to end" -m "Refs #376"
```

---

### Task 5: The verdict's hint and places in the mandate page's view

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt`
- Modify (only if #375 has landed): `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopy.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt`

**Interfaces:**
- Consumes: `OwnershipVerification.location`, `OwnershipLocation`, `OwnershipLocationStatus` (Task 1).
- Produces: `OwnershipPlacesView(mandateCity: String, documentLocality: String)`.
- Produces: `OwnershipLocationView(places: OwnershipPlacesView, text: String, action: String)`.
- Produces: `OwnershipSectionView(..., location: OwnershipLocationView?)`; `OwnershipReasonView(..., places: OwnershipPlacesView?)`.

- [ ] **Step 1: Write the failing view tests**

In `MandateOwnershipViewsTest`, add a `location(...)` helper and:

```kotlin
@Test
fun `an unresolved comparison adds the neutral hint`() {
    val view = section(
        OwnershipVerification(
            VERIFIED,
            location = OwnershipLocation("Yaounde", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
        ),
    )

    assertEquals(OwnershipPlacesView("Yaounde", "Bonabéri"), view.location!!.places)
    assertEquals(
        "La localité du titre n'a pas pu être rattachée à une ville connue : vérifiez que le titre et le mandat " +
            "désignent le même bien.",
        view.location!!.text,
    )
    assertEquals("Vérifier le titre dans Documents", view.location!!.action)
}

@Test
fun `a blank place reads non precisée`() {
    val view = section(
        OwnershipVerification(
            VERIFIED,
            location = OwnershipLocation("", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
        ),
    )

    assertEquals("non précisée", view.location!!.places.mandateCity)
    assertEquals("Bonabéri", view.location!!.places.documentLocality)
}

@Test
fun `both places blank shows no hint`() {
    val view = section(
        OwnershipVerification(VERIFIED, location = OwnershipLocation("", "", OwnershipLocationStatus.UNRESOLVED)),
    )

    assertNull(view.location)
}

@Test
fun `a match and an unknown comparison add no hint`() {
    assertNull(section(OwnershipVerification(VERIFIED, location = OwnershipLocation("Yaounde", "Yaounde", MATCH))).location)
    assertNull(section(OwnershipVerification(VERIFIED)).location)
}

@Test
fun `a property mismatch shows the places in its reason`() {
    val view = section(
        OwnershipVerification(
            FAILED,
            listOf(reason("OWNERSHIP_PROPERTY_MISMATCH")),
            location = OwnershipLocation("Douala", "Yaoundé", OwnershipLocationStatus.MISMATCH),
        ),
    )

    assertEquals("Le document de propriété", view.reasons[0].check)
    assertEquals(OwnershipPlacesView("Douala", "Yaoundé"), view.reasons[0].places)
    assertEquals(
        "Le document désigne un bien dans une autre ville que celle du mandat : il ne justifie pas la propriété de " +
            "ce bien.",
        view.reasons[0].text,
    )
    assertEquals(
        "Vérifiez le document dans Documents : il doit désigner un bien situé dans la ville du mandat.",
        view.reasons[0].action,
    )
    assertNull(view.location)
}
```

Use a private `section(verification) = MandateOwnershipViews.section(mandate, verification, null, labels)` helper; `reason(code)` is the existing helper.

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest`
Expected: FAIL — `location`, `OwnershipPlacesView` and the new reason are unresolved.

- [ ] **Step 3: Add the views**

In `MandateOwnershipView.kt`:

```kotlin
/** The two display names of the city comparison; a blank one reads "non précisée" (set by the view builder). */
data class OwnershipPlacesView(
    val mandateCity: String,
    val documentLocality: String,
)

/** The UNRESOLVED comparison's neutral hint: the two places, the sentence and the Documents link's label. */
data class OwnershipLocationView(
    val places: OwnershipPlacesView,
    val text: String,
    val action: String,
)
```

Add `val location: OwnershipLocationView?` to `OwnershipSectionView` and `val places: OwnershipPlacesView?` to `OwnershipReasonView`. Update their KDocs: the verdict's only property data, never a document id, type, title, page, path or URL.

- [ ] **Step 4: Word the verdict**

In `MandateOwnershipViews`:
- Add:
  ```kotlin
  private const val LOCATION_HINT =
      "La localité du titre n'a pas pu être rattachée à une ville connue : vérifiez que le titre et le mandat " +
          "désignent le même bien."
  private const val LOCATION_ACTION = "Vérifier le titre dans Documents"
  private const val UNKNOWN_PLACE = "non précisée"
  private const val PROPERTY_MISMATCH = "OWNERSHIP_PROPERTY_MISMATCH"
  ```
- Add the reason wording:
  ```kotlin
  PROPERTY_MISMATCH to Wording(
      "Le document désigne un bien dans une autre ville que celle du mandat : il ne justifie pas la propriété de " +
          "ce bien.",
      "Vérifiez le document dans Documents : il doit désigner un bien situé dans la ville du mandat.",
  ),
  ```
- `check(code)`: `code == PROPERTY_MISMATCH -> DOCUMENT_CHECK` before the `startsWith("OWNERSHIP_DOCUMENT_")` branch.
- Add `private fun places(location: OwnershipLocation): OwnershipPlacesView?` returning null when both names are blank, else `OwnershipPlacesView(location.mandateCity.ifBlank { UNKNOWN_PLACE }, location.documentLocality.ifBlank { UNKNOWN_PLACE })`.
- In `section`, compute `val places = places(verification.location)`; set `location = if (verification.location.status == OwnershipLocationStatus.UNRESOLVED) places?.let { OwnershipLocationView(it, LOCATION_HINT, LOCATION_ACTION) } else null`; pass `places = if (verification.location.status == OwnershipLocationStatus.MISMATCH) places else null` to `reason`.
- In `reason`, set `places = if (reason.code == PROPERTY_MISMATCH) places else null`.
- The unavailable view (`UNKNOWN`) gets `location = null`.

If `OwnershipCopy.kt` exists (#375 landed), put `LOCATION_HINT`, `LOCATION_ACTION`, the `PROPERTY_MISMATCH` wording and the check kind there, and have `MandateOwnershipViews` read them; do not duplicate a sentence.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest`
Expected: PASS, the existing cases and the new ones.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt
git commit -m "feat(npc): word the owner verification's city comparison" -m "Refs #376"
```

---

### Task 6: Port the approved hint and places into the mandate page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/ownership.css`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: Task 5's `OwnershipSectionView.location` and `OwnershipReasonView.places`.
- Produces: `[data-ownership-location]` (the hint) and `[data-reason-places]` (the mismatch's two places), exactly as the approved prototype.

- [ ] **Step 1: Write the failing template tests**

In `MandateTemplateTest`, add a `location(...)` helper and:

```kotlin
@Test
fun `an unresolved comparison renders the neutral hint`() {
    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.VERIFIED,
            location = OwnershipLocation("Douala", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
        ),
    ).whenever(mandateClient).ownership(201)

    val hint = verificationSection().selectFirst("[data-ownership-location]")!!

    assertTrue(hint.text().contains("Ville du mandat"))
    assertTrue(hint.text().contains("Localité du titre"))
    assertTrue(hint.text().contains("Douala"))
    assertTrue(hint.text().contains("Bonabéri"))
    assertTrue(hint.text().contains("La localité du titre n'a pas pu être rattachée à une ville connue"))
    assertEquals("#documents-title", hint.selectFirst("a[href='#documents-title']")!!.attr("href"))
    assertEquals("Vérifié", verificationSection().selectFirst(".status-tag")!!.text())
}

@Test
fun `a blank place reads non precisée and both blanks hide the hint`() {
    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.VERIFIED,
            location = OwnershipLocation("", "Bonabéri", OwnershipLocationStatus.UNRESOLVED),
        ),
    ).whenever(mandateClient).ownership(201)
    assertTrue(verificationSection().selectFirst("[data-ownership-location]")!!.text().contains("non précisée"))

    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.VERIFIED,
            location = OwnershipLocation("", "", OwnershipLocationStatus.UNRESOLVED),
        ),
    ).whenever(mandateClient).ownership(201)
    assertNull(verificationSection().selectFirst("[data-ownership-location]"))
}

@Test
fun `a property mismatch renders its places, not a hint`() {
    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.FAILED,
            listOf(reason("OWNERSHIP_PROPERTY_MISMATCH")),
            location = OwnershipLocation("Douala", "Yaoundé", OwnershipLocationStatus.MISMATCH),
        ),
    ).whenever(mandateClient).ownership(201)

    val section = verificationSection()
    val places = section.selectFirst("[data-reason-places]")!!

    assertTrue(section.text().contains("Le document de propriété"))
    assertTrue(places.text().contains("Douala"))
    assertTrue(places.text().contains("Yaoundé"))
    assertTrue(section.text().contains("il ne justifie pas la propriété de ce bien"))
    assertNull(section.selectFirst("[data-ownership-location]"))
}

@Test
fun `a match renders no hint`() {
    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.VERIFIED,
            location = OwnershipLocation("Douala", "Douala", OwnershipLocationStatus.MATCH),
        ),
    ).whenever(mandateClient).ownership(201)

    assertNull(verificationSection().selectFirst("[data-ownership-location]"))
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateTemplateTest`
Expected: FAIL — the template has no `[data-ownership-location]` nor `[data-reason-places]`.

- [ ] **Step 3: Port the prototype**

From `src/main/html/v2/mandate.html` (the `#376` comment and the `data-ownership-location` block) and `v2/assets/css/ownership.css` (the `.ownership__places` rules), port:
- The neutral hint after the VERIFIED block and before the `<template>`: `th:if="${section.location}"`, the `dl.ownership__places` with the two `div.ownership__place`, `dt` / `dd` values from `section.location.places`, the `p` from `section.location.text`, the `a href="#documents-title"` from `section.location.action`. Use `data-ownership-location` and the `data-location-*` attributes as in the prototype.
- The reason template: a `dl.ownership__places` after the reason's `p` and before the `ul.ownership__problems`, `th:if="${reason.places}"`, the two values from `reason.places`. Use `data-reason-places`.
- The CSS block from the prototype (`ownership__location`, `ownership__places`, `ownership__place`, the 479.98 px media query) into `src/main/assets/css/ownership.css`, using the existing tokens. Do not add a colour or a script.
- Update the template's header comment: the section now carries the verdict's city comparison; the hint is `UNRESOLVED` only, a blank place reads *non précisée*, both blank hide it; the mismatch reason shows the two places; no document id, type, title, page, path or URL.
- The `build-assets.mjs` already lists `ownership`; no change.

- [ ] **Step 4: Run the template tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateTemplateTest`
Expected: PASS, the existing and the new cases.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html \
        modules/ndopify-partner-central/src/main/assets/css/ownership.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show the owner verification's city comparison" -m "Refs #376"
```

---

### Task 7: Documentation, the sample evaluation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the mandate domain's **Ownership verdict** paragraph)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the **owner verification** paragraph)
- Modify: this plan's `## Sample evaluation` section

**Interfaces:** none — documentation and the measurement record.

- [ ] **Step 1: Document the server rule**

In `modules/ndopify-server/CLAUDE.md`, after the ownership paragraph's reason sentence, add: `#376` adds the city comparison — a customer-naming `LAND_TITLE` / `CERTIFICATE_OF_OWNERSHIP` whose `locality` (else `arrondissement`) resolves by exact normalised name (`LocationService.findCity`, `CITY` only, the mandate city's country) to a city other than `mandate.cityId` fails `OWNERSHIP_PROPERTY_MISMATCH`; an unresolved locality, no locality, a missing/non-`CITY` `cityId` or a country mismatch never fails, and the verdict carries `location` (`mandateCity`, `documentLocality`, `UNKNOWN` / `MATCH` / `MISMATCH` / `UNRESOLVED`) for the mandate page's hint. Never log the city or the locality.

- [ ] **Step 2: Document the page**

In `modules/ndopify-partner-central/CLAUDE.md`'s owner-verification paragraph, add: `#376` adds the verdict's city comparison — `UNRESOLVED` is a neutral `.hint` under the state's block (whatever the status) with *Ville du mandat* / *Localité du titre*, *La localité du titre n'a pas pu être rattachée à une ville connue : vérifiez que le titre et le mandat désignent le même bien.* and *Vérifier le titre dans Documents*; a blank place reads *non précisée*, both blank hide it; `MISMATCH` is the `OWNERSHIP_PROPERTY_MISMATCH` reason with the same two places inside *Le document de propriété*; `MATCH` and `UNKNOWN` show nothing. No document id, type, title, page, path, URL or other extracted value.

- [ ] **Step 3: Record the sample evaluation**

The plan's `## Sample evaluation` section (below) is the record of the accepted exception; do not commit any personal document and do not add a measurement task to the branch.

- [ ] **Step 4: Verify**

Run, in each touched module:
- `cd modules/ndopify-dto && mvn verify`
- `cd modules/ndopify-server && mvn verify` (needs the local MySQL)
- `cd modules/ndopify-partner-central && mvn verify` (builds the asset bundle; a clean run needs npm network)

Expected: BUILD SUCCESS, ktlint clean, JaCoCo gate met in each.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(server,npc): document the ownership city comparison" -m "Refs #376"
```

---

## Sample evaluation

The story's *Done when* asks for a real-title resolution rate. No real land title is available in the repository — only the identity samples under `src/test/resources/files/identity/`; #297's *titre foncier* and *certificat de propriété* were never attached, and its sample evaluation could not measure the ownership half either.

**Accepted exception (2026-11-27, the user):** the rule ships unmeasured, as #297 accepted its unvalidated name threshold. The measurement is a follow-up, not a condition of this plan:

- When real titles are available, run the title's `locality` / `arrondissement` through `LocationService.findCity` for its mandate's city, record the resolved / unresolved counts in a comment on #376, and never commit a personal document.
- A low resolution rate is not a regression: the unresolved cases stay a hint. A high rate confirms the `OWNERSHIP_PROPERTY_MISMATCH` failure is worth keeping; a zero rate means the failure never fires and the check is a hint-only display.
- The plan's tests pin the behaviour on synthetic cities; the real-world rate is the measurement this exception defers.

## Parallel work

- **#375** (`npc: explain why a verified listing is not published`) extracts #298's French copy into `OwnershipCopy` and adds the listing page's owner block. If it has landed, add the `PROPERTY_MISMATCH` wording and the `LOCATION_*` constants to `OwnershipCopy` (Task 5) and let the listing block word the sentence with its shared copy; the hint and the two places stay on the mandate page.
- **#377** (`ops: override the owner verification verdict`) adds `OwnershipVerification.overridden` and the *Vérifiée par Ndopify* state, with no hint. Add `location` after `overridden` when it has landed, and keep the overridden view's `location = null`; the approved prototype's `?ownership=overridden` state is unchanged.
- Neither story changes this plan's server rule or its tests.
