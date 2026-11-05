# Partner Central Visits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the agent a *Visites* section on the Espace agent and a `/visits/{id}` page to confirm or refuse a requested visit, start it with the customer's code (and an optional position), report a no-show, and finish it with the private report, in every status.

**Architecture:** A new `ui/visit/` package plus `NdVisitClient` in `ndopify-partner-central`. `HomeController` loads the agent's open visits (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`) and renders their cards; a new `VisitController` owns `/visits/{id}` and its `schedule`, `cancel`, `check-in`, `no-show` and `check-out` POSTs. Both are server-rendered Thymeleaf with POST/Redirect/GET, no AJAX and no database; the visit's listing and customer are secondary reads.

**Tech Stack:** Kotlin, Spring Boot MVC/Security, `RestClient`, Thymeleaf, jsoup + MockMvc + `MockRestServiceServer`, mockito-kotlin, kotlin.test.

**Spec:** `docs/superpowers/plans/2026-11-02-visit-management.md` (*Design* and *Out*) and the prototypes `modules/ndopify-partner-central/src/main/html/visit.html`, the *Visites* section of `modules/ndopify-partner-central/src/main/html/index.html`, `modules/ndopify-partner-central/src/main/html/assets/js/visit.js` and `.../assets/css/visit.css`. The API is `VisitEndpoints` / `VisitService` in `modules/ndopify-server`.

## Global Constraints

- Do not change `ndopify-server` or `ndopify-dto`; `ndopify-dto.version` stays `0.0.80`.
- Never modify or delete anything under `modules/ndopify-partner-central/src/main/html/` (the prototype). Copy from it.
- Work on a branch: `git checkout -b npc-visits`. Commit only the files each task names.
- Dates and times go through `TenantFormatter`; the tenant's `timeZone` decides the day and time. Never hard-code a format.
- `VisitSlots` is exact: the 7 days starting **tomorrow**, 08:00–20:00 in the tenant's time zone, on a 30-minute boundary, so 25 times (`08:00` … `20:00`).
- Open visits are `REQUESTED`, `CONFIRMED`, `CHECKED_IN`; only those appear on the Espace agent.
- The server's windows: check-in from `scheduledAt − 1 h` to `scheduledAt + 4 h`; a customer no-show from `scheduledAt + 15 min` to `+4 h`; check-out at least 10 min after check-in; a request expires 48 h after `createdAt`; five wrong codes lock the code for good. These are the server's; the page mirrors them.
- The agent never receives the visit code: `GET /v1/visits/{id}` returns `code = null` for the agent. Never log the code or a token. The page shows the address from the listing (`landmark` + `accessDirections`), not from `Visit.address`, which is the customer's only.
- The API exposes neither `codeAttempts` nor the no-show position. The page keeps a session hint (`npc.visit.code-locked.<id>`) after `VISIT_CODE_LOCKED` so the check-in form stays hidden for this agent; the server still enforces the lock. The no-show panel never shows a distance.
- The listing and the customer are secondary reads: a failure leaves their line out, never fails the page. Only the visit itself is required.
- On the Espace agent, show only open visits: requested by expiry (soonest first), upcoming by scheduled time. Drop a requested visit already past its 48 h and a confirmed one after its `+4 h` window, even if the server's job has not moved it yet.
- Visits are free: no fee, no payment, no SMS.
- UI copy is French; code and comments are English. KDoc says what a class is for and its non-obvious rules.
- The page works without JavaScript: every action is a form that posts, the controller redirects, the outcome arrives as a flash attribute. Scripts only enhance.
- `Listing.agentRemarks` is never shown nor put in a model.
- Tests use kotlin.test assertions and mockito-kotlin mocks. Coverage gate in the root POM: line `0.98`, class `0.95`.
- Commit messages use the module's `feat(npc): ...` style.

## Review Focus

1. **The agent never sees the code.** The page renders the address from the listing, never `Visit.code` or `Visit.address`; a fixture carrying `code = "4827"` never prints it (Task 3 view test, Task 5 template test).
2. **Wrong code, then locked.** `VISIT_CODE_INVALID` shows a generic code error; `VISIT_CODE_LOCKED` hides the check-in form and shows the locked message, and the visit stays `CONFIRMED`. The session hint keeps it hidden after a reload for this agent (Task 4 controller test, Task 5 template test).
3. **Double post.** Scheduling, cancelling or checking in a visit whose status moved on is `changed` without a second server call or e-mail (Task 4 controller test).
4. **Tenant time zone.** A slot built at 23:30 UTC in Douala is already the next day there; the valid first day is not dropped (Task 2 `VisitSlotsTest`).
5. **Another agent's visit.** A server 403 or a visit whose `agentId` is not the signed-in agent's is the locked page, with no listing, customer or action call (Task 4 controller test, Task 5 template test).
6. **Listing gone.** The visit page still renders and its actions still work without the address, map or listing card (Task 4 controller test).
7. **No JavaScript.** The schedule, cancel, check-in, no-show and check-out forms all post; the position inputs stay optional (Task 4 controller test, Task 5 template test).

---

### Task 1: Visit REST client

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Service
class NdVisitClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,
    builder: RestClient.Builder,
) {
    fun get(id: Long): Visit
    fun search(agentId: Long, statuses: List<VisitStatus>, limit: Int = 20): List<VisitSummary>
    fun schedule(id: Long, request: ScheduleVisitRequest)
    fun cancel(id: Long, request: CancelVisitRequest)
    fun checkIn(id: Long, request: CheckInVisitRequest)
    fun noShow(id: Long, request: NoShowVisitRequest)
    fun checkOut(id: Long, request: CheckOutVisitRequest)
}
```

KDoc: these are the agent's endpoints; ndopify-server answers 403 for another agent's visit, 404 when it is gone, and 409 when its status no longer allows the action. The code is never returned to the agent. Calls are tenant-scoped by `X-Tenant-ID` (the shared interceptor).

- [ ] **Step 1: Write the failing client test**

Use the `MockRestServiceServer.bindTo(RestClient.builder())` setup of `NdMatchClientTest`, with `NdVisitClient("http://localhost:8080", builder)`. Cover:

```kotlin
@Test
fun get() {
    server.expect(requestTo("http://localhost:8080/v1/visits/7001"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                json(GetVisitResponse(Visit(id = 7001, status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE))),
                MediaType.APPLICATION_JSON,
            ),
        )

    val visit = client.get(7001)
    assertEquals(VisitStatus.CONFIRMED, visit.status)
    assertNull(visit.code)
    server.verify()
}

@Test
fun search() {
    server.expect(
        requestTo("http://localhost:8080/v1/visits?agent-id=20&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=20"),
    )
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                json(
                    SearchVisitResponse(
                        listOf(
                            VisitSummary(id = 7001, status = VisitStatus.REQUESTED),
                            VisitSummary(id = 7002, status = VisitStatus.CONFIRMED),
                        ),
                    ),
                ),
                MediaType.APPLICATION_JSON,
            ),
        )

    assertEquals(
        listOf(7001L, 7002L),
        client.search(20, listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)).map { it.id },
    )
}
```

Add one POST test per action, asserting the URL, `method(POST)`, the JSON field(s) and the parsing-free call:

- `schedule`: `/v1/visits/7001/schedule`, `jsonPath("$.scheduledAt").exists()`, `jsonPath("$.type").value("ON_SITE")`.
- `cancel`: `/v1/visits/7001/cancel`, `jsonPath("$.reason").value("Empêchement")`.
- `checkIn`: `/v1/visits/7001/check-in`, `jsonPath("$.code").value("4827")`, `jsonPath("$.latitude").value(4.0247)`, `jsonPath("$.longitude").value(9.6943)`.
- `noShow`: `/v1/visits/7001/no-show`, `jsonPath("$.reason").value("CUSTOMER_ABSENT")`.
- `checkOut`: `/v1/visits/7001/check-out`, `jsonPath("$.comment").value("Client intéressé, reviendra avec son épouse.")`.
- `get - another agent`: a `403` response → `assertFailsWith<HttpClientErrorException.Forbidden> { client.get(7001) }`.
- `check-in - conflict`: a `409` response → `assertFailsWith<HttpClientErrorException.Conflict> { client.checkIn(7001, request) }`.

Values for the requests: `ScheduleVisitRequest(Date.from(Instant.parse("2026-11-07T09:30:00Z")), VisitType.ON_SITE)`, `CancelVisitRequest("Empêchement")`, `CheckInVisitRequest("4827", 4.0247, 9.6943, 12.0)`, `NoShowVisitRequest(VisitNoShowReason.CUSTOMER_ABSENT)`, `CheckOutVisitRequest("Client intéressé, reviendra avec son épouse.")`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest`
Expected: compile FAIL (`NdVisitClient` unresolved).

- [ ] **Step 3: Implement the client**

Use the pattern of `NdMatchClient`: `rest.get().uri(...).retrieve().body<T>()`, and `rest.post().uri(...).body(request).retrieve().toBodilessEntity()` for the actions. `search` builds the query with one `status` parameter per status; `get` returns `GetVisitResponse.visit ?: Visit()`; `search` returns `SearchVisitResponse.visits`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt
git commit -m "feat(npc): visit client"
```

---

### Task 2: Visit slots, panels and wording

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitSlots.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitFixtures.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitSlotsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRulesTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class VisitDay(val value: String, val label: String)

object VisitSlots {
    fun days(today: LocalDate, formatter: TenantFormatter): List<VisitDay>
    fun times(): List<Pair<String, String>>
    fun at(date: LocalDate, time: LocalTime, tenant: Tenant): Date
    fun localDate(at: Date, tenant: Tenant): LocalDate
    fun localTime(at: Date, tenant: Tenant): LocalTime
    /** "sam. 7 novembre 2026 à 10:30", without the type. */
    fun dateTimeLabel(at: Date, tenant: Tenant, formatter: TenantFormatter): String
    /** "Sur place · samedi 7 novembre 2026 à 10:30". */
    fun label(typeLabel: String, at: Date, tenant: Tenant, formatter: TenantFormatter): String
}

object VisitRules {
    val OPEN: List<VisitStatus> = listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)

    enum class Panel { REQUESTED, CONFIRMED, WINDOW, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED, NO_SHOW, UNKNOWN }
    data class Tag(val label: String, val style: String)

    fun panel(visit: Visit, now: Long): Panel
    fun tag(panel: Panel): Tag
    fun typeLabel(type: VisitType): String
    fun nextStep(visit: Visit, now: Long): String?
    fun expiresAt(visit: Visit): Long
    fun expiresIn(left: Long): String
    fun checkInFrom(visit: Visit): Long?
    fun noShowFrom(visit: Visit): Long?
    fun checkOutFrom(visit: Visit): Long?
    fun canCancel(visit: Visit): Boolean
    fun canCheckIn(visit: Visit, now: Long): Boolean
    fun canNoShow(visit: Visit, now: Long): Boolean
    fun checkOutReady(visit: Visit, now: Long): Boolean
    fun whenLabel(at: Date, today: LocalDate, tenant: Tenant, formatter: TenantFormatter): String
    fun duration(start: Date?, end: Date?): String?
    fun meters(value: Int?): String?

    val REJECTION_LABELS: Map<MatchRejectionReason, String>
}
```

**Rules to implement exactly:**

- `days(today)` = `(1..7).map { today.plusDays(it) }`, each `VisitDay(value = date.toString(), label = formatter.date(date, "EEE d MMMM yyyy"))`. With the `TenantFixtures` tenant and `2026-10-31`, the first is `VisitDay("2026-11-01", "dim. 1 novembre 2026")` and the last is `"2026-11-07"`.
- `times()` = 08:00 every 30 min through 20:00, mapped to `time.toString() to "%dh%02d".format(hour, minute)`; 25 pairs, first `"08:00" to "8h00"`, last `"20:00" to "20h00"`.
- `at` = `Date.from(LocalDateTime.of(date, time).atZone(ZoneId.of(tenant.timeZone)).toInstant())`.
- `dateTimeLabel` = `"${formatter.date(localDate, "EEE d MMMM yyyy")} à ${formatter.time(at)}"`.
- `label` = `"$typeLabel · ${dateTimeLabel(at, tenant, formatter)}"`.
- `panel`: `CONFIRMED` in `[scheduledAt − 1 h, scheduledAt + 4 h]` (both inclusive) is `WINDOW`; every other status is itself (`UNKNOWN` for `UNKNOWN`). A `CONFIRMED` visit without `scheduledAt` stays `CONFIRMED`.
- Tags: `REQUESTED` → `"À confirmer"` / `"warning"`; `CONFIRMED` → `"Confirmée"` / `"success"`; `WINDOW` → `"À démarrer"` / `"info"`; `CHECKED_IN` → `"En cours"` / `"info"`; `COMPLETED` → `"Terminée"` / `"success"`; `CANCELLED` → `"Annulée"` / `"neutral"`; `EXPIRED` → `"Expirée"` / `"neutral"`; `NO_SHOW` → `"Absence"` / `"warning"`; `UNKNOWN` → `"Visite"` / `"neutral"`.
- `typeLabel`: `ON_SITE` → `"Sur place"`, `VIDEO` → `"Visite vidéo"`, else `"Visite"`.
- `nextStep`, exactly the prototype's `nextStepOf`:
  - `REQUESTED` → `"Confirmez la date avec le client."`
  - `CHECKED_IN`: under 10 min since `checkedInAt` → `"Faites visiter le bien, puis terminez la visite."`; else `"Terminez la visite avec votre compte rendu."`
  - `WINDOW`, from `noShowFrom`: `VIDEO` → `"Le client ne répond pas ? Déclarez son absence."`; else `"Démarrez avec son code, ou déclarez son absence."`
  - `WINDOW`, before `noShowFrom`: `VIDEO` → `"Appelez le client, puis démarrez avec son code."`; else `"Démarrez la visite avec le code du client."`
  - `CONFIRMED` outside the window: `VIDEO` → `"Appelez le client en vidéo, depuis le bien."`; else `"Le jour J, demandez son code au client."`
  - every closed status → `null`.
- `expiresAt` = `createdAt.time + 48 h`; `expiresIn` = `"${floor(hours)} h"` when at least an hour, else `"${max(1, ceil(minutes))} min"`.
- `checkInFrom` = `scheduledAt − 1 h`; `noShowFrom` = `scheduledAt + 15 min`; `checkOutFrom` = `checkedInAt + 10 min`; each null without the source date.
- `canCancel` = `REQUESTED` or `CONFIRMED`; `canCheckIn` = panel `WINDOW`; `canNoShow` = panel `WINDOW` and `now >= noShowFrom`; `checkOutReady` = `CHECKED_IN` and `now >= checkOutFrom`.
- `whenLabel`: same tenant-local day as `today` → `"Aujourd'hui · ${formatter.time(at)}"`, else `dateTimeLabel(at, tenant, formatter)`.
- `duration`: null without both ends; minutes rounded to the nearest minute; under 60 → `"${minutes} min"`, else `"${hours} h ${"%02d".format(minutes % 60)}"`.
- `meters`: null for null; under 1000 → `"$value m"`; else one decimal and a comma (`"1,2 km"`).
- `REJECTION_LABELS`: `PRICE` `"trop cher"`, `LOCATION` `"le quartier ne convient pas"`, `SIZE` `"trop petit ou trop grand"`, `AMENITIES` `"il manque des équipements"`, `AVAILABILITY` `"disponible trop tard"`, `OTHER` `"autre raison"`. `UNKNOWN` is absent.

**Test fixtures (`VisitFixtures.kt`)** — shared by Tasks 2–6:

```kotlin
object VisitFixtures {
    val NOW: Long = Instant.parse("2026-11-05T10:00:00Z").toEpochMilli()
    val TENANT = TenantFixtures.TENANT
    val FORMATTER = TenantFixtures.FORMATTER
    val AGENT_ID = 20L
    val CUSTOMER_ID = 300L

    val CUSTOMER = Customer(
        id = CUSTOMER_ID,
        party = Party(firstName = "Sandrine", lastName = "Eboa", email = "sandrine@gmail.com"),
        whatsappNumber = "+237699887766",
    )

    val LISTING = Listing(
        id = 3012,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        cityId = 2232593, neighborhoodId = 3001,
        propertyType = PropertyType.APARTMENT, rooms = 3,
        price = 240_000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
        landmark = "Rue Njo-Njo, portail bleu", accessDirections = "Derrière la pharmacie",
        latitude = 4.0247, longitude = 9.6943,
    )

    fun requested() = Visit(
        id = 7001, matchId = 9001, mandateId = 204, listingId = 3012,
        agentId = AGENT_ID, customerId = CUSTOMER_ID,
        status = VisitStatus.REQUESTED, type = VisitType.ON_SITE,
        requestedAt = date("2026-11-07T09:30:00Z"), note = "Je viendrai avec mon mari.",
        createdAt = date("2026-11-05T04:00:00Z"),
    )

    fun confirmed() = requested().copy(status = VisitStatus.CONFIRMED, scheduledAt = date("2026-11-07T09:30:00Z"))
    fun window() = requested().copy(status = VisitStatus.CONFIRMED, scheduledAt = date("2026-11-05T09:30:00Z"))
    fun checkedIn() = requested().copy(
        status = VisitStatus.CHECKED_IN, scheduledAt = date("2026-11-05T09:30:00Z"),
        checkedInAt = date("2026-11-05T09:05:00Z"), checkInDistanceMeters = 820, checkInFlagged = true,
    )
    fun completed() = checkedIn().copy(
        status = VisitStatus.COMPLETED, checkedOutAt = date("2026-11-05T09:35:00Z"),
        agentComment = "Client intéressé, reviendra avec son épouse.",
        interest = VisitInterest.NOT_INTERESTED, feedbackReasonCode = MatchRejectionReason.PRICE,
        feedbackComment = "Un peu cher pour le quartier.",
    )

    fun date(iso: String): Date = Date.from(Instant.parse(iso))

    fun summary(visit: Visit) = VisitSummary(
        id = visit.id, matchId = visit.matchId, mandateId = visit.mandateId, listingId = visit.listingId,
        agentId = visit.agentId, customerId = visit.customerId, status = visit.status, type = visit.type,
        requestedAt = visit.requestedAt, note = visit.note, scheduledAt = visit.scheduledAt,
        checkedInAt = visit.checkedInAt, checkInDistanceMeters = visit.checkInDistanceMeters,
        checkInFlagged = visit.checkInFlagged, checkedOutAt = visit.checkedOutAt,
        noShowParty = visit.noShowParty, noShowReason = visit.noShowReason,
        cancelledBy = visit.cancelledBy, cancellationReason = visit.cancellationReason,
        cancelledAt = visit.cancelledAt, createdAt = visit.createdAt, version = visit.version,
    )

    fun listingSummary() = ListingSummary(
        id = LISTING.id, mandateId = 204, agentId = AGENT_ID, status = ListingStatus.PUBLISHED,
        cityId = LISTING.cityId, neighborhoodId = LISTING.neighborhoodId,
        propertyType = LISTING.propertyType, rooms = LISTING.rooms,
        price = LISTING.price, currencyCode = LISTING.currencyCode,
        pricePeriodicityType = LISTING.pricePeriodicityType,
    )

    fun customerSummary() = CustomerSummary(
        id = CUSTOMER_ID, party = CUSTOMER.party, whatsappNumber = CUSTOMER.whatsappNumber,
    )

    val LOCATIONS: Map<Long, Location> = listOf(
        Location(id = 2232593, name = "Douala"),
        Location(id = 3001, name = "Bonapriso"),
    ).associateBy { it.id }
}
```

`window()` is scheduled 30 min before `NOW`; add its check-in from/to in the tests, not in the fixture.

- [ ] **Step 1: Write the failing `VisitSlotsTest`**

```kotlin
@Test
fun days() {
    val days = VisitSlots.days(LocalDate.of(2026, 10, 31), FORMATTER)

    assertEquals(7, days.size)
    assertEquals(VisitDay("2026-11-01", "dim. 1 novembre 2026"), days.first())
    assertEquals("2026-11-07", days.last().value)
}

@Test
fun times() {
    val times = VisitSlots.times()

    assertEquals(25, times.size)
    assertEquals("08:00" to "8h00", times.first())
    assertEquals("20:00" to "20h00", times.last())
    assertTrue(times.none { it.first == "20:30" })
}

@Test
fun at() {
    assertEquals(
        Date.from(Instant.parse("2026-11-01T09:00:00Z")),
        VisitSlots.at(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), TENANT),
    )
}

@Test
fun label() {
    assertEquals(
        "Sur place · samedi 7 novembre 2026 à 10:30",
        VisitSlots.label("Sur place", Date.from(Instant.parse("2026-11-07T09:30:00Z")), TENANT, FORMATTER),
    )
}

@Test
fun `date time label`() {
    assertEquals(
        "sam. 7 novembre 2026 à 10:30",
        VisitSlots.dateTimeLabel(Date.from(Instant.parse("2026-11-07T09:30:00Z")), TENANT, FORMATTER),
    )
}

@Test
fun `tenant time zone decides the first day`() {
    // 23:30 UTC is already 00:30 on the 6th in Douala: tomorrow is the 7th, not the 6th.
    val now = Date.from(Instant.parse("2026-11-05T23:30:00Z"))
    val days = VisitSlots.days(VisitSlots.localDate(now, TENANT), FORMATTER)

    assertEquals("2026-11-07", days.first().value)
    assertEquals("2026-11-13", days.last().value)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitSlotsTest`
Expected: compile FAIL (`VisitSlots` unresolved).

- [ ] **Step 3: Implement `VisitSlots` and `VisitDay`**

- [ ] **Step 4: Write the failing `VisitRulesTest`**

Cover, with `VisitFixtures`:

```kotlin
@Test
fun `panel boundaries`() {
    val visit = confirmed()
    val at = visit.scheduledAt!!.time
    assertEquals(VisitRules.Panel.WINDOW, VisitRules.panel(visit, at - 3600_000))
    assertEquals(VisitRules.Panel.CONFIRMED, VisitRules.panel(visit, at - 3600_001))
    assertEquals(VisitRules.Panel.WINDOW, VisitRules.panel(visit, at + 4 * 3600_000))
    assertEquals(VisitRules.Panel.CONFIRMED, VisitRules.panel(visit, at + 4 * 3600_000 + 1))
}
```

Also assert: every tag; the three type labels; each `nextStep` case (requested, confirmed video/on-site, window before/after the no-show, checked-in under/over 10 min, closed null); `expiresAt`/`expiresIn` (`42 h`, `45 min`, `1 min` at zero); `canCancel`/`canCheckIn`/`canNoShow`/`checkOutReady` at their boundaries; `whenLabel` for today (`"Aujourd'hui · 10:30"`) and another day (`"sam. 7 novembre 2026 à 10:30"`); `duration` (`"1 h 05"`, `"25 min"`, null); `meters` (`"820 m"`, `"1,2 km"`, null); and each `REJECTION_LABELS` value.

- [ ] **Step 5: Run the rules test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitRulesTest`
Expected: compile FAIL (`VisitRules` unresolved).

- [ ] **Step 6: Implement `VisitRules`**

- [ ] **Step 7: Run both tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='VisitSlotsTest,VisitRulesTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitSlots.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRules.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitFixtures.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitSlotsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRulesTest.kt
git commit -m "feat(npc): visit slots and rules"
```

---

### Task 3: Visit page and card view models

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class VisitPage(
    val id: Long,
    val panel: VisitRules.Panel,
    val tag: VisitRules.Tag,
    val type: VisitType,
    val typeLabel: String,
    val customerName: String,
    val customerFirstName: String,
    /** E.164 for `tel:`. */
    val phone: String?,
    /** Digits for `https://wa.me/`. */
    val phoneDigits: String?,
    val mandateId: Long,
    val listingId: Long,
    val listingTitle: String?,
    val listingMeta: String?,
    val listingUrl: String,
    val address: String?,
    val mapUrl: String?,
    val requestedAt: String,
    val scheduledAt: String?,
    val windowFrom: String?,
    val expiresAt: String?,
    val expiresIn: String?,
    val note: String?,
    val checkedInAt: String?,
    val checkOutFrom: String?,
    val checkOutReady: Boolean,
    val checkedOutAt: String?,
    val duration: String?,
    val startDistance: String?,
    val checkInFlagged: Boolean,
    val distance: String?,
    val distanceKnown: Boolean,
    val agentComment: String?,
    val interest: VisitInterest?,
    val feedbackTag: String?,
    val feedbackTone: String?,
    val feedbackReason: String?,
    val feedbackComment: String?,
    val cancelledTitle: String?,
    val cancelledText: String?,
    val cancellationReason: String?,
    val plannedAt: String?,
    val expiredText: String?,
    val noShowTitle: String?,
    val noShowText: String?,
    val canCancel: Boolean,
    val canCheckIn: Boolean,
    val canNoShow: Boolean,
    val days: List<VisitDay>,
    val times: List<Pair<String, String>>,
    val selectedDay: String,
    val selectedTime: String,
    val videoPossible: Boolean,
)

object VisitViews {
    fun of(
        visit: Visit,
        customer: Customer?,
        listing: Listing?,
        locations: Map<Long, Location>,
        now: Long,
        tenant: Tenant,
        formatter: TenantFormatter,
    ): VisitPage
}

data class VisitCardView(
    val id: Long,
    val customerName: String,
    /** Null for a requested visit (the card shows the timer instead). */
    val tag: VisitRules.Tag?,
    val whenLabel: String,
    val listing: String,
    val expiresAt: Long?,
    val expiresIn: String?,
    val urgent: Boolean,
    val nextStep: String,
)

data class VisitCardsView(
    val requested: List<VisitCardView>,
    val upcoming: List<VisitCardView>,
    val unavailable: Boolean = false,
)

object VisitCards {
    fun of(
        visits: List<VisitSummary>?,
        listings: Map<Long, ListingSummary>?,
        customers: Map<Long, CustomerSummary>,
        locations: Map<Long, Location>,
        now: Long,
        tenant: Tenant,
        formatter: TenantFormatter,
    ): VisitCardsView
}
```

**`VisitViews` rules:**

- Name: `customer.party.firstName + " " + customer.party.lastName`, trimmed, `"Client"` when blank or without a customer; `customerFirstName` = the first name, or `"Le client"`.
- `phone` = the E.164 number, displayed like `MandateViews` (`"+237699887766"` → `"+237 6 99 88 77 66"`, other numbers as they are); `phoneDigits` = its digits.
- `listingTitle` = `listOfNotNull(type, rooms).joinToString(" ")` then, with a place, `"$title · $place"`; `type` is `MandateLabels.shortPropertyType(listing.propertyType)` (fallback `"Bien"`), `rooms` is `listing.rooms?.let { MatchRules.rooms(it) }`, and `place` is the neighbourhood name, else the city name. Null without a listing. ("Appartement 3 chambres · Bonapriso".)
- `listingMeta` = `"Annonce n° ${listing.id} · ${MandateLabels.price(Money(listing.price, listing.currencyCode, listing.pricePeriodicityType), formatter, compact = true)}"`.
- `listingUrl` = `/listings/${visit.listingId}`.
- `address` = `listOfNotNull(landmark, accessDirections)` trimmed, non-blank, joined with `", "`; null if empty. `mapUrl` = `https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=18/$latitude/$longitude` only with both coordinates.
- `requestedAt` use `VisitSlots.dateTimeLabel`; `scheduledAt` and `windowFrom` the same; `checkedInAt` and `checkOutFrom` use `formatter.time`; `checkedOutAt` uses `dateTimeLabel`.
- `expiresAt` = `VisitSlots.dateTimeLabel(Date(VisitRules.expiresAt(visit)))`; `expiresIn` = `VisitRules.expiresIn(VisitRules.expiresAt(visit) - now)`.
- `duration` = `VisitRules.duration(checkedInAt, checkedOutAt)`.
- `startDistance` = `"Non relevée"` without a distance, else `"À ${VisitRules.meters(it)} du bien"` plus `" (à vérifier par l'équipe Ndopify)"` when flagged.
- `distance` = `VisitRules.meters(checkInDistanceMeters)`, `distanceKnown` = it is not null.
- Feedback: `feedbackTag` = `"Intéressé"` / `"Pas intéressé"`, `feedbackTone` = `"success"` / `"neutral"`, `feedbackReason` = `REJECTION_LABELS[feedbackReasonCode]` only when `NOT_INTERESTED`, `feedbackComment` as sent.
- Cancelled: `wasRequest = visit.scheduledAt == null`; `byAgent = cancelledBy == VisitParty.AGENT`.
  - title: `wasRequest` → `byAgent ? "Demande refusée" : "Demande annulée"`, else `"Visite annulée"`.
  - text: `byAgent` → `"Vous avez refusé la demande le …"` / `"Vous avez annulé la visite le …"`, else `"${customerFirstName} a annulé sa demande le …"` / `"${customerFirstName} a annulé la visite le …"`; use `dateTimeLabel(cancelledAt)`.
  - `cancellationReason` = `"« $reason »"` when present; `plannedAt` = `dateTimeLabel(scheduledAt ?: requestedAt)`.
- Expired: `"Vous n'avez pas confirmé la demande sous 48 h : elle a expiré le ${dateTimeLabel(expiresAt)}. ${customerFirstName} peut en faire une nouvelle."`.
- No-show: title `AGENT` → `"Visite non démarrée"`; `CUSTOMER` + `CODE_REFUSED` → `"Code refusé par le client"`; `CUSTOMER` + `CUSTOMER_ABSENT` + `VIDEO` → `"Le client ne répond pas"`; else `"Client absent"`. Text: `AGENT` → `"Vous n'avez pas démarré la visite : elle est comptée comme une absence de votre part."`; else `"Déclaré par vous : l'absence du client est enregistrée."`. `plannedAt` as above. No distance.
- `canCancel`/`canCheckIn`/`canNoShow`/`checkOutReady` from `VisitRules`.
- `days` = `VisitSlots.days(today, formatter)` with `today` from `now` in the tenant's zone; `times` = `VisitSlots.times()`; `selectedDay`/`selectedTime` from `scheduledAt ?: requestedAt`, but only if they are in the lists, else the first day/time.
- `videoPossible` = the customer's WhatsApp is non-blank **or** the visit is already `VIDEO`.

**`VisitCards` rules:**

- `visits == null` → `VisitCardsView(emptyList(), emptyList(), unavailable = true)`.
- Requested: `status == REQUESTED && VisitRules.expiresAt(visit) > now`, sorted by expiry ascending. `tag = null`, `whenLabel = "Demandée pour le ${VisitSlots.dateTimeLabel(requestedAt)}"`, `expiresAt`/`expiresIn`, `urgent = left < 12 h`.
- Upcoming: `CHECKED_IN`, or `CONFIRMED` whose `scheduledAt + 4 h >= now`; sorted by `scheduledAt` ascending (nulls last). `tag = VisitRules.tag(VisitRules.panel(visit, now))`; `whenLabel` = `"En cours depuis ${formatter.time(checkedInAt)}"` for `CHECKED_IN`, else `VisitRules.whenLabel(scheduledAt, today, ...)`.
- `customerName` from the summaries map, `"Client"` when missing.
- `listing` = `"${VisitRules.typeLabel(visit.type)} · "` + the listing's `"type rooms"` and its place (as in `VisitViews`), when found, else `"Annonce n° ${visit.listingId}"`.
- `nextStep = VisitRules.nextStep(visit, now)` (non-null for open visits).

- [ ] **Step 1: Write the failing `VisitViewsTest`**

`of(...)` is the test's helper:

```kotlin
private fun of(
    visit: Visit = VisitFixtures.requested(),
    customer: Customer? = VisitFixtures.CUSTOMER,
    listing: Listing? = VisitFixtures.LISTING,
) = VisitViews.of(
    visit, customer, listing, VisitFixtures.LOCATIONS,
    VisitFixtures.NOW, VisitFixtures.TENANT, VisitFixtures.FORMATTER,
)
```

```kotlin
@Test
fun requested() {
    val page = of(VisitFixtures.requested())

    assertEquals(VisitRules.Panel.REQUESTED, page.panel)
    assertEquals("À confirmer", page.tag.label)
    assertEquals("warning", page.tag.style)
    assertEquals("Sur place", page.typeLabel)
    assertEquals("Sandrine Eboa", page.customerName)
    assertEquals("Sandrine", page.customerFirstName)
    assertEquals("+237 6 99 88 77 66", page.phone)
    assertEquals("237699887766", page.phoneDigits)
    assertEquals("Appartement 3 chambres · Bonapriso", page.listingTitle)
    assertEquals("Annonce n° 3012 · 240K\u00a0FCFA / mois", page.listingMeta)
    assertEquals("sam. 7 novembre 2026 à 10:30", page.requestedAt)
    assertEquals("Je viendrai avec mon mari.", page.note)
    assertEquals("42 h", page.expiresIn)
    assertTrue(page.canCancel)
    assertFalse(page.canCheckIn)
    assertEquals("2026-11-07", page.selectedDay)
    assertEquals("10:30", page.selectedTime)
    assertTrue(page.videoPossible)
}
```

Add tests for:

- `confirmed`: panel `CONFIRMED` before the window, tag "Confirmée", address `"Rue Njo-Njo, portail bleu, Derrière la pharmacie"`, `mapUrl` starts with `https://www.openstreetmap.org/`, `windowFrom` is `"sam. 7 novembre 2026 à 09:30"`, `canCancel` true, `canCheckIn` false.
- `window`: panel `WINDOW`, tag "À démarrer" / info, `canCheckIn` true, `canNoShow` true for the fixture (scheduled 30 min before `NOW`).
- `checked in`: panel `CHECKED_IN`, tag "En cours", `distance "820 m"`, `distanceKnown`, `checkInFlagged`, `checkOutReady` false before 10 min and true after.
- `completed`: panel `COMPLETED`, tag "Terminée", `duration "30 min"`, `startDistance` with the flagged suffix, `agentComment`, feedback "Pas intéressé" / neutral / "trop cher" / the comment.
- `cancelled by the agent before confirmation`: title "Demande refusée"; `cancelled by the agent after confirmation`: title "Visite annulée"; `cancelled by the customer`: `"Sandrine a annulé …"`; the reason is quoted; `plannedAt`.
- `expired`: the 48 h sentence.
- `no show`: the four titles (agent, code refused, absent video, absent on site) and the text; no distance field exists.
- `video`: `typeLabel "Visite vidéo"`, `address` still from the listing, `videoPossible` true with a number; false without a number when the visit isn't VIDEO.
- `missing customer and listing`: name "Client", `phone null`, `listingTitle null`, `address null`, no exception.
- `no code`: give the fixture `code = "4827"` and assert `page.toString().contains("4827")` is false (the view has no code field).
- `time zone`: `requestedAt` for `2026-11-07T23:30:00Z` is `"dim. 8 novembre 2026 à 00:30"` in Douala.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitViewsTest`
Expected: compile FAIL.

- [ ] **Step 3: Implement `VisitPage` and `VisitViews`**

- [ ] **Step 4: Write the failing `VisitCardsTest`**

Cover with `VisitSummary` fixtures derived from `VisitFixtures`:

```kotlin
@Test
fun requested() {
    val cards = VisitCards.of(
        listOf(summary(VisitFixtures.requested())),
        mapOf(3012L to listingSummary()),
        mapOf(300L to customerSummary()),
        LOCATIONS,
        VisitFixtures.NOW,
        VisitFixtures.TENANT,
        VisitFixtures.FORMATTER,
    )

    val card = cards.requested.single()
    assertEquals(7001L, card.id)
    assertEquals("Sandrine Eboa", card.customerName)
    assertNull(card.tag)
    assertEquals("Demandée pour le sam. 7 novembre 2026 à 10:30", card.whenLabel)
    assertEquals("Sur place · Appartement 3 chambres · Bonapriso", card.listing)
    assertEquals("42 h", card.expiresIn)
    assertFalse(card.urgent)
    assertEquals("Confirmez la date avec le client.", card.nextStep)
    assertTrue(cards.upcoming.isEmpty())
}
```

Add: urgent requested (`expiresAt - now < 12 h`); upcoming confirmed and window tags; checked-in `"En cours depuis 10:05"`; a stale requested (past 48 h) and a stale confirmed (past +4 h) dropped; empty lists; `visits = null` → `unavailable`; missing listing → `"Sur place · Annonce n° 3012"`; missing customer → `"Client"`.

- [ ] **Step 5: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitCardsTest`
Expected: compile FAIL.

- [ ] **Step 6: Implement `VisitCardView` and `VisitCards`**

- [ ] **Step 7: Run both view tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='VisitViewsTest,VisitCardsTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt
git commit -m "feat(npc): visit page and card views"
```

---

### Task 4: Visit controller and actions

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitForms.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class VisitScheduleForm(val date: String? = null, val time: String? = null, val type: String? = null) {
    fun toRequest(tenant: Tenant, fallback: VisitType): ScheduleVisitRequest?
}
data class VisitCancelForm(val reason: String? = null) {
    fun toRequest(): CancelVisitRequest
}
data class VisitCheckInForm(
    val code: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): CheckInVisitRequest?
}
data class VisitNoShowForm(
    val reason: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): NoShowVisitRequest?
}
data class VisitCheckOutForm(
    val comment: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val accuracyMeters: String? = null,
) {
    fun toRequest(): CheckOutVisitRequest? // null for a blank or over-2,000-character comment
}

@Controller
@RequestMapping("/visits")
class VisitController(
    private val currentAgent: CurrentAgent,
    private val visitClient: NdVisitClient,
    private val listingClient: NdListingClient,
    private val customerClient: NdCustomerClient,
    locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        const val VIEW = "visit"
        const val LOCKED_NOT_YOURS = "not-yours"
        const val LOCKED_UNAVAILABLE = "unavailable"
        const val OUTCOME = "visitOutcome"
        const val FLASH_TEXT = "flashText"
        const val FLASH_STYLE = "flashStyle"
        const val CHECK_IN_LOCKED = "checkInLocked"
        const val SESSION_CODE_LOCKED = "npc.visit.code-locked."

        const val CONFIRMED = "confirmed"
        const val REFUSED = "refused"
        const val CANCELLED = "cancelled"
        const val CHECKED_IN = "checked-in"
        const val NO_SHOW = "no-show"
        const val COMPLETED = "completed"
        const val INVALID_SLOT = "invalid-slot"
        const val CODE_MISSING = "code-missing"
        const val CODE_INVALID = "code-invalid"
        const val CODE_LOCKED = "code-locked"
        const val COMMENT_MISSING = "comment-missing"
        const val COMMENT_TOO_LONG = "comment-too-long"
        const val REASON_MISSING = "reason-missing"
        const val TOO_EARLY = "too-early"
        const val WHATSAPP_MISSING = "whatsapp-missing"
        const val CHANGED = "changed"
        const val FAILED = "error"
    }

    @GetMapping("/{id}")
    fun show(@PathVariable id: String, model: Model, session: HttpSession): String

    @PostMapping("/{id}/schedule")
    fun schedule(@PathVariable id: String, @ModelAttribute form: VisitScheduleForm, redirect: RedirectAttributes): String

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String, @ModelAttribute form: VisitCancelForm, redirect: RedirectAttributes): String

    @PostMapping("/{id}/check-in")
    fun checkIn(@PathVariable id: String, @ModelAttribute form: VisitCheckInForm, redirect: RedirectAttributes): String

    @PostMapping("/{id}/no-show")
    fun noShow(@PathVariable id: String, @ModelAttribute form: VisitNoShowForm, redirect: RedirectAttributes): String

    @PostMapping("/{id}/check-out")
    fun checkOut(@PathVariable id: String, @ModelAttribute form: VisitCheckOutForm, redirect: RedirectAttributes): String
}
```

- `toRequest` for the schedule parses `date` and `time` with `runCatching`; null if either is missing/unparseable. The type is `VisitType.entries.find { it.name == type }?.takeIf { it == ON_SITE || it == VIDEO } ?: fallback` (a hand-edited unknown value keeps the visit's requested type). It builds `scheduledAt` with `VisitSlots.at(date, time, tenant)`.
- Position parsing: `latitude`, `longitude` and `accuracyMeters` each `toDoubleOrNull()`; a blank or malformed value is `null`. A malformed position never fails the action.
- `VisitCancelForm.toRequest` trims the reason, blank → null, cuts to 500.
- `VisitCheckInForm.toRequest` trims the code; null unless it matches `^\d{4}$` (the server's codes are 4 digits).
- `VisitNoShowForm.toRequest` maps `CUSTOMER_ABSENT` / `CODE_REFUSED`; null otherwise.
- `VisitCheckOutForm.toRequest` trims the comment; null when blank or over 2000 characters. The controller checks the raw comment first: blank → `COMMENT_MISSING`, trimmed length over 2000 → `COMMENT_TOO_LONG`; only then does it call `toRequest()`.

**Controller behaviour:**

- `show`:
  - `currentAgent.get() ?: throw SignInRequiredException()`; `ProfileRules.header(agent, model)`.
  - Malformed `id` → locked `LOCKED_UNAVAILABLE`.
  - `visitClient.get(id)`; `403` → locked `LOCKED_NOT_YOURS`; other `RestClientException` → locked `LOCKED_UNAVAILABLE`; a `visit.agentId != agent.id` → locked `LOCKED_NOT_YOURS`.
  - Secondary reads (each through a `lookup` that logs and returns null): `listingClient.get(visit.listingId)`, `customerClient.get(visit.customerId)`; the location names of the listing's city/neighbourhood in one `SectorValidator.locations` call.
  - `model["page"] = VisitViews.of(visit, customer, listing, locations, clock.millis(), tenant, formatter)`.
  - `model[CHECK_IN_LOCKED] = session.getAttribute(SESSION_CODE_LOCKED + id) == true`.
  - Flash: the `OUTCOME` flash attribute (if any) is worded by a private `flash(outcome, visit, customer, tenant, formatter)`; set `FLASH_TEXT`/`FLASH_STYLE`. When it is `CODE_LOCKED`, also set `CHECK_IN_LOCKED` true.
  - `model["currency"] = formatter.currency()`; return `VIEW`.

- Every action:
  - `currentAgent.get() ?: throw SignInRequiredException()`.
  - Malformed id → `redirect:/`.
  - Load the visit first (same 403/404/409 handling as `MatchController`): a failure flashes `CHANGED` (403/404/409) or `FAILED` (network/5xx) and redirects to `/visits/{id}`; no action call.
  - Check `visit.agentId == agent.id`; otherwise flash `CHANGED` and redirect.
  - Check the status the action applies to; otherwise flash `CHANGED` and redirect, without a call. This is what makes a double post harmless.
  - Call the client, map `HttpClientErrorException` by status and, where needed, by `ErrorResponse.error.code` (the `MandateController.errorCode` helper):
    - `schedule`: applies to `REQUESTED`. Form invalid → `INVALID_SLOT`. `VISIT_INVALID_SLOT` → `INVALID_SLOT`; `VISIT_WHATSAPP_MISSING` → `WHATSAPP_MISSING`; 403/404/409 → `CHANGED`; else `FAILED`. Success → `CONFIRMED`.
    - `cancel`: applies to `REQUESTED`/`CONFIRMED`. Invalid reason is not an error (it is optional). Success → `REFUSED` when the visit was `REQUESTED`, else `CANCELLED`; 409 → `CHANGED`.
    - `checkIn`: applies to panel `WINDOW`; the session lock (if set) → `CODE_LOCKED` without a call. Invalid code shape → `CODE_MISSING`. `VISIT_CODE_INVALID` → `CODE_INVALID`; `VISIT_CODE_LOCKED` → `CODE_LOCKED` **and set the session attribute**; `VISIT_OUTSIDE_WINDOW` / `VISIT_NOT_CONFIRMED` → `CHANGED`; success → `CHECKED_IN`.
    - `noShow`: applies to panel `WINDOW` and `now >= noShowFrom`; otherwise `CHANGED` (or `TOO_EARLY` with the same wording; use `CHANGED` for a hand-edited post before the window). Invalid reason → `REASON_MISSING`. Success → `NO_SHOW`; 409 → `CHANGED`.
    - `checkOut`: applies to `CHECKED_IN`. Blank comment → `COMMENT_MISSING`; over 2,000 → `COMMENT_TOO_LONG`; before `checkOutFrom` → `TOO_EARLY` without a call. `VISIT_OUTSIDE_WINDOW` → `TOO_EARLY`; `VISIT_NOT_CHECKED_IN` → `CHANGED`; success → `COMPLETED`.
  - Add the outcome as a flash attribute and `redirect:/visits/{id}`.

- Flash wording (the controller's `flash` helper; French, through `VisitSlots.dateTimeLabel`):

| Outcome | Style | Text |
|---|---|---|
| `CONFIRMED` | success | `"Visite confirmée pour le ${dateTimeLabel(scheduledAt)} : le client a reçu par e-mail la date, l'adresse et son code."` + a video hint when `VIDEO` |
| `REFUSED` | success | `"Demande refusée : le client est prévenu par e-mail."` |
| `CANCELLED` | success | `"Visite annulée : le client est prévenu par e-mail."` |
| `CHECKED_IN` | success | `"Visite démarrée à ${formatter.time(checkedInAt)}. Terminez-la ici une fois la visite finie."` |
| `NO_SHOW` | success | `"Absence du client enregistrée : il est prévenu par e-mail."` |
| `COMPLETED` | success | `"Visite terminée. Le client reçoit un e-mail pour dire si le bien l'intéresse."` |
| `INVALID_SLOT` | warning | `"Ce créneau n'est plus disponible. Choisissez-en un autre."` |
| `CODE_MISSING` | error | `"Saisissez les 4 chiffres du code du client."` |
| `CODE_INVALID` | warning | `"Code incorrect. Vérifiez le code du client."` |
| `CODE_LOCKED` | warning | `"Trop d'essais : contactez le support ou déclarez le client absent."` |
| `COMMENT_MISSING` | error | `"Écrivez votre compte rendu de la visite."` |
| `COMMENT_TOO_LONG` | error | `"Votre compte rendu dépasse 2 000 caractères."` |
| `REASON_MISSING` | error | `"Choisissez ce qui s'est passé."` |
| `TOO_EARLY` | warning | `"Vous pourrez terminer la visite 10 minutes après son démarrage."` |
| `WHATSAPP_MISSING` | warning | `"Le client n'a pas de numéro WhatsApp : choisissez une visite sur place."` |
| `CHANGED` | warning | `"Cette visite a changé entre-temps : vérifiez son état avant de recommencer."` |
| `FAILED` | error | `"Action impossible pour le moment. Réessayez dans quelques instants."` |

- [ ] **Step 1: Write the failing GET tests**

Set up `MockMvcBuilders.standaloneSetup(VisitController(...))` with `ProfileFixtures.mockLocations` and `SignedInAdviceTest.signIn()`; `currentAgent.get()` → `ProfileFixtures.AGENT` (id 20); `clock.millis()` → `VisitFixtures.NOW`; stub `visitClient.get(7001)`, `listingClient.get(3012)`, `customerClient.get(300)`. Use `InternalResourceViewResolver("/templates/", ".html")`.

```kotlin
@Test
fun page() {
    val result = mvc.perform(get("/visits/7001")).andReturn()

    assertEquals("visit", result.modelAndView?.viewName)
    val page = result.modelAndView!!.model["page"] as VisitPage
    assertEquals(7001L, page.id)
    assertEquals("Sandrine Eboa", page.customerName)
    assertEquals("Appartement 3 chambres · Bonapriso", page.listingTitle)
    verify(listingClient).get(3012)
    verify(customerClient).get(300)
}
```

Add:

- `another agent`: `doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(visitClient).get(7001)` → `locked = "not-yours"`, no `page`, `verify(listingClient, never()).get(any())`, same for the customer.
- `visit of another agent`: `doReturn(VisitFixtures.requested().copy(agentId = 99)).whenever(visitClient).get(7001)` → locked `"not-yours"`.
- `listing gone`: `doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(listingClient).get(3012)` → page present, `listingTitle`/`address`/`mapUrl` null, customer still read.
- `customer gone`: `doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(300)` → page present, `phone` null, `customerName` `"Client"`.
- `server unavailable`: `doThrow(ResourceAccessException("timeout")).whenever(visitClient).get(7001)` → locked `"unavailable"`.
- `malformed id`: `GET /visits/abc` → locked `"unavailable"`, no client call.
- `flash`: stub `confirmed()` and `GET /visits/7001` with `.flashAttr("visitOutcome", "confirmed")` → `flashText` starts with `"Visite confirmée pour le "`, `flashStyle` `"success"`.
- `code locked flash`: `.flashAttr("visitOutcome", "code-locked")` → `checkInLocked` true.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest`
Expected: compile FAIL (`VisitController` unresolved).

- [ ] **Step 3: Write the failing action tests**

Use `post()` and assert `result.response.redirectedUrl` and `result.flashMap["visitOutcome"]`. With the visit `REQUESTED` and `NOW`:

```kotlin
@Test
fun schedule() {
    val result = mvc.perform(
        post("/visits/7001/schedule")
            .param("date", "2026-11-07").param("time", "10:30").param("type", "ON_SITE"),
    ).andReturn()

    verify(visitClient).schedule(
        7001,
        ScheduleVisitRequest(Date.from(Instant.parse("2026-11-07T09:30:00Z")), VisitType.ON_SITE),
    )
    assertEquals("/visits/7001", result.response.redirectedUrl)
    assertEquals("confirmed", result.flashMap["visitOutcome"])
}
```

Add:

- `schedule - stale slot`: form `date = "x"` → `invalid-slot`, `verify(visitClient, never()).schedule(any(), any())`.
- `schedule - status moved`: visit `CONFIRMED` → `changed`, no call.
- `schedule - server slot`: `VISIT_INVALID_SLOT` error response → `invalid-slot`.
- `schedule - video without WhatsApp`: `VISIT_WHATSAPP_MISSING` → `whatsapp-missing`.
- `schedule - server error`: 502 → `error`.
- `cancel - requested`: `REQUESTED`, reason `"  Empêchement  "` → `CancelVisitRequest("Empêchement")`, outcome `refused`.
- `cancel - confirmed`: `CONFIRMED` → outcome `cancelled`.
- `cancel - blank reason`: reason `"  "` → `CancelVisitRequest(null)`.
- `cancel - checked in`: `CHECKED_IN` → `changed`, no call.
- `cancel - conflict`: 409 → `changed`.
- `check in`: window visit, `.param("code", "4827").param("latitude", "4.0247").param("longitude", "9.6943").param("accuracyMeters", "12")` → `CheckInVisitRequest("4827", 4.0247, 9.6943, 12.0)`, outcome `checked-in`.
- `check in - without position`: `.param("code", "4827")` → `CheckInVisitRequest("4827", null, null, null)`, outcome `checked-in` (the position is never required).
- `check in - short code`: `.param("code", "12")` → `code-missing`, no call.
- `check in - invalid`: `VISIT_CODE_INVALID` → `code-invalid`.
- `check in - locked`: `VISIT_CODE_LOCKED` → `code-locked`; then assert `session.getAttribute("npc.visit.code-locked.7001") == true`.
- `check in - locked again`: use one `MockHttpSession` for both POSTs; with the attribute set by the first, the second POST → `code-locked` without a client call.
- `check in - locked survives a reload`: with that same session, `GET /visits/7001` → `checkInLocked` true (the session hint).
- `check in - outside the window`: `VISIT_OUTSIDE_WINDOW` → `changed`.
- `no show`: window visit after `+15 min`, `.param("reason", "CUSTOMER_ABSENT")` → `NoShowVisitRequest(CUSTOMER_ABSENT, ...)`, outcome `no-show`.
- `no show - no reason`: → `reason-missing`, no call.
- `no show - too early`: window visit at `NOW` (which is 30 min after the slot, so it is not too early; use a visit scheduled 5 min before `NOW` to test) → `changed`, no call.
- `check out`: checked-in visit with `checkedInAt = NOW - 20 min`, `.param("comment", "  Client intéressé.  ")` → `CheckOutVisitRequest("Client intéressé.", ...)`, outcome `completed`.
- `check out - blank`: → `comment-missing`, no call.
- `check out - too long`: a 2,001-character comment → `comment-too-long`, no call.
- `check out - too early`: checked in 5 min ago → `too-early`, no call.
- `check out - not checked in`: `CONFIRMED` → `changed`, no call.
- `unknown visit`: `doThrow(NotFound)` on `visitClient.get` → `changed`.
- `another agent's visit`: `agentId = 99` → `changed`, no call.

Use a helper for a coded error:

```kotlin
private fun error(status: HttpStatus, code: String): HttpClientErrorException {
    val ex = HttpClientErrorException(status)
    ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
    return ex
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest`
Expected: compile FAIL (`VisitForms`/`VisitController` unresolved).

- [ ] **Step 5: Implement the forms and the controller**

- [ ] **Step 6: Run the controller test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitForms.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt
git commit -m "feat(npc): visit page controller and actions"
```

---

### Task 5: Visit page template, styles and script

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes `VisitPage`, `VisitController.VIEW`, `flashText` / `flashStyle`, `checkInLocked`, `locked`.
- `body data-page="visit" data-currency="…"`.

- [ ] **Step 1: Write the failing template test**

Use the real Thymeleaf setup of `MandateTemplateTest` (`SpringTemplateEngine` + `ClassLoaderTemplateResolver` on `templates/`), `SignedInAdvice`, and a mocked `VisitController` dependency set. Stub `visitClient.get(7001)` with each state of `VisitFixtures`, the listing, the customer, the locations and `clock.millis()`; render `GET /visits/7001` and parse with jsoup.

Assert for `REQUESTED`:

```kotlin
val page = render(VisitFixtures.requested())
val panel = page.selectFirst("[data-panel=REQUESTED]")!!

assertEquals("Visite de Sandrine Eboa", page.selectFirst(".visit-intro h1")?.text())
assertEquals("À confirmer", page.selectFirst(".visit-intro .status-tag")?.text())
assertEquals("post", panel.selectFirst("form[data-schedule-form]")?.attr("method"))
assertEquals("/visits/7001/schedule", panel.selectFirst("form[data-schedule-form]")?.attr("action"))
assertEquals(7, panel.select("select[name=date] option").size)
assertEquals(25, panel.select("select[name=time] option").size)
assertEquals("2026-11-07", panel.selectFirst("select[name=date] option[selected]")?.attr("value"))
assertEquals("10:30", panel.selectFirst("select[name=time] option[selected]")?.attr("value"))
assertEquals(listOf("ON_SITE", "VIDEO"), panel.select("input[name=type]").map { it.attr("value") })
assertEquals("/visits/7001/cancel", panel.selectFirst("form[data-cancel-form]")?.attr("action"))
assertTrue(panel.text().contains("Je viendrai avec mon mari."))
```

Add:

- `CONFIRMED`: panel `CONFIRMED`, address, map link, cancel form, no check-in form; `windowFrom` text.
- `WINDOW`: panel `WINDOW`, check-in form action `/visits/7001/check-in`, a `name=code` input and exactly three `input[data-position-input]`; no-show form action `/visits/7001/no-show`; cancel form.
- `WINDOW` + `canNoShow = false`: no `[data-noshow-form]`.
- `WINDOW` + `checkInLocked = true`: no check-in form, `[data-code-locked]` present.
- `CHECKED_IN`: panel `CHECKED_IN`, check-out form action `/visits/7001/check-out`, `textarea[name=comment]` with `maxlength="2000"`, three position inputs, `aria-disabled="true"` on the submit when `checkOutReady = false`, the "10 min après son démarrage" sentence.
- `COMPLETED`: panel `COMPLETED`, start/end/duration, the agent comment, the customer's feedback tag/reason/comment, no form.
- `CANCELLED`: panel `CANCELLED`, the title, text, reason and planned time.
- `EXPIRED`: panel `EXPIRED`, the 48 h sentence.
- `NO_SHOW`: panel `NO_SHOW`, the party/reason title and text.
- `locked`: `[data-visit-locked]` present, no `[data-panel]`; `"not-yours"` and `"unavailable"` wordings.
- `flash`: `.alert--success` with the text; `code-locked` shows `.alert--warning` and hides the check-in form.
- `no code`: a fixture with `code = "4827"` → `assertFalse(page.text().contains("4827"))`.
- Every server action is a form with `method="post"` and a `/visits/7001/...` action. The only JS-only control is the position retry (hidden without JS); the visit still starts/ends without a position.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL (template `visit.html` missing).

- [ ] **Step 3: Implement the template**

Copy the prototype's structure (`src/main/html/visit.html`) and replace its fixture markup with Thymeleaf:

- Head: `partner-central.css`, `home.css`, `listing.css`, `visit.css`; scripts `partner-central.js`, `visit.js`; `th:href` / `th:src` for every asset.
- Header: the served site header with the brand and the account menu guarded by `th:if="${signedIn}"`; use `initials` / `userPhotoUrl` from `ProfileRules.header`.
- Back link `→ Retour à l'espace agent` to `@{/#visits-title}`.
- Locked alert `th:if="${locked != null}"`, worded by `locked`: `"not-yours"` → "Cette visite n'est pas disponible. Retrouvez les visites de vos clients dans votre espace agent."; `"unavailable"` → "Cette visite ne peut pas être affichée pour le moment. Réessayez dans quelques instants." The visit content is `th:unless="${locked != null}"`.
- Intro: `h1` "Visite de …", `status-tag status-tag--${page.tag.style}` with `page.tag.label`, "Visite n° … · Mandat n° …" linking to `/mandates/{mandateId}#matches-title`.
- Header: phone `tel:` and WhatsApp `wa.me` links when `page.phone != null`; the listing link when `page.listingTitle != null`; the type icon (`ON_SITE` house, `VIDEO` camera) from `page.type`; `page.typeLabel`.
- Flash: `.alert.alert--${flashStyle}` with `flashText`, `role="status"`, `tabindex="-1"`.
- One panel per `page.panel.name()`, with `data-panel="…"`; use `th:if`:
  - `REQUESTED`: requested type/slot, note when present, "À confirmer avant le …" with `page.expiresAt`/`page.expiresIn`, the schedule form (`data-schedule-form`, `data-customer-first-name`, day/time selects from `page.days`/`page.times`, type radios, submit "Confirmer"), the type hint, and the refuse form (`data-cancel-form`, optional reason, "Refuser la demande").
  - `CONFIRMED`: scheduled slot, address/map when present, the code hint, the cancel form ("Annuler la visite").
  - `WINDOW`: scheduled slot, address/map, the video call block for `VIDEO`, the check-in form unless `checkInLocked` (code, three hidden position inputs, position status, "Démarrer"), the locked alert when `checkInLocked`, the no-show `<details id="no-show">` when `page.canNoShow` (reason radios with type-specific labels, hidden position inputs, "Déclarer l'absence"), the cancel form.
  - `CHECKED_IN`: "Visite en cours depuis …", the distance line (or the flagged warning / "position non relevée"), the check-out form (`data-checkout-form`, comment textarea `maxlength="2000"` and required, counter `data-counter`, three position inputs, submit `data-checkout-submit` with `aria-disabled` until `checkOutReady`, the wait sentence `data-checkout-wait` with `checkOutFrom`).
  - `COMPLETED`: the summary (`type`, started, ended, duration, start distance, agent comment with "(jamais montré au client)") and the customer's answer (`feedbackTag`, `feedbackTone`, `feedbackReason`, `feedbackComment`) or "Le client n'a pas encore répondu. …".
  - `CANCELLED`, `EXPIRED`, `NO_SHOW`: the alert boxes with the view's title/text/reason/planned time.
- No prototype state switcher, no "Simuler" button, no code display.

- [ ] **Step 4: Add `visit.css`**

Copy `modules/ndopify-partner-central/src/main/html/assets/css/visit.css` into `src/main/resources/public/assets/css/visit.css`. Keep the card rules (the Espace agent section) and the visit-page rules; drop only the prototype-only rules (`[data-visit-reset]` and the `.visit-prototype*` selectors). Add any `.choices` / `.status-tag` rule the page needs from the served `listing.css` / `home.css` only if those files are not linked; the template links both, so no copy is needed.

- [ ] **Step 5: Add `visit.js`**

Port only the enhancements, guarded by `document.body.getAttribute("data-page") === "visit"`:

- Focus the flash.
- Digit-only 4-character code input; clear the inline error on input.
- Type hint update on the schedule form's type change (use `data-customer-first-name`).
- Comment counter (`0 / 2000`) on the check-out form.
- Position capture: on load and on "Relever à nouveau", call `navigator.geolocation.getCurrentPosition` for each visible form with `[data-position-input]`; fill the hidden inputs; show "Position relevée · précision N m", the weak-position warning over 50 m, or the form's `data-none-text`; never block the submit.
- Open `#no-show` when the URL hash is `#no-show`.
- Never `preventDefault` on a form: the server always re-checks. The page is rendered at load; a reload shows the next time-bound panel (check-in window, no-show, check-out, expiry). No AJAX/WebSocket.

- [ ] **Step 6: Run the template test and the listing/join template tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='VisitTemplateTest,ListingTemplateTest,JoinTemplateTest'`
Expected: PASS (the new CSS/JS links do not affect the other pages).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/visit.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt
git commit -m "feat(npc): visit page"
```

---

### Task 6: Visits on the Espace agent

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- `HomeController` constructor gains, after `matchClient`:
  ```kotlin
  private val visitClient: NdVisitClient,
  private val customerClient: NdCustomerClient,
  ```
  Update every construction in the tests.
- Model attribute `visits: VisitCardsView?` (null for an agent who isn't verified).
- `VisitCards` and the fixtures are from Task 3; `VisitRules.OPEN` from Task 2.

**Controller changes:**

- In the verified branch, after `mandates`, load:
  ```kotlin
  val visits = loadVisits(agent)
  val visitListings = visits?.let { loadVisitListings(it) }
  val visitCustomers = visits?.let { loadVisitCustomers(it) } ?: emptyMap()
  ```
- Add the visit listings' city/neighbourhood ids to the one `names` lookup:
  ```kotlin
  visitListings.orEmpty().values.flatMap { list -> listOfNotNull(list.cityId, list.neighborhoodId) }
  ```
- Add `model["visits"] = VisitCards.of(visits, visitListings, visitCustomers, names, now, tenant, formatter)`. When `visits` is null, `VisitCards.of` returns the `unavailable` view.
- Helpers, each logging and returning null/empty on `RestClientException`:
  - `loadVisits(agent)` → `visitClient.search(agent.id, VisitRules.OPEN)`; null on failure.
  - `loadVisitListings(visits)` → `listingClient.searchByIds(visits.map { it.listingId }.distinct()).associateBy { it.id }`; null on failure.
  - `loadVisitCustomers(visits)` → `customerClient.search(visits.map { it.customerId }.distinct())`, keyed by the non-null `CustomerSummary.id`; empty on failure.
- For an unverified agent, do not set `visits` (no section, like the mandates).

**Template changes (`templates/index.html`):**

- Add `<link rel="stylesheet" th:href="@{/assets/css/visit.css}">` next to `home.css`.
- Insert the *Visites* section after the *Mandats proposés* `</section>` and before the *Mes mandats* section, `th:if="${visits != null}"`:
  - `<section class="home-section" aria-labelledby="visits-title" data-visits-section>`; `<h2 id="visits-title">Visites</h2>`.
  - Unavailable alert `th:if="${visits.unavailable}"` with `data-visits-unavailable`: "Vos visites ne peuvent pas être affichées pour le moment. Réessayez dans quelques instants."
  - Requested group `th:if="${!#lists.isEmpty(visits.requested)}"` with `data-visit-group="requested"`, `<h3 class="visit-group__title">À confirmer</h3>`, `<ul class="assignments visit-cards" data-visit-list>`.
  - Upcoming group `th:if="${!#lists.isEmpty(visits.upcoming)}"` with `data-visit-group="upcoming"`, title "À venir".
  - Each card: `<li class="mandate-card visit-card" data-visit-card th:each="v : ${visits.requested}" th:attr="data-visit-id=${v.id},data-visit-expires-at=${v.expiresAt},data-urgent=${v.urgent} ? '' : null">`; `article` with `aria-labelledby="visit-${v.id}-title"`; `h4` id; `<a data-visit-link th:href="@{/visits/{id}(id=${v.id})}" th:text="${v.customerName}">`; the tag `data-visit-tag` when `v.tag != null`; `v.whenLabel` in `data-visit-when`; `v.listing` in `data-visit-listing`; the timer `data-visit-timer` when `v.expiresIn != null` ("Il reste <strong data-visit-left>…</strong> pour confirmer"); "Prochaine étape :" + `v.nextStep` in `data-visit-next`.
  - Empty state `th:if="${#lists.isEmpty(visits.requested) and #lists.isEmpty(visits.upcoming) and !visits.unavailable}"`: "Aucune visite prévue. Quand un client demande à visiter un bien que vous lui avez envoyé, sa demande apparaît ici et vous êtes prévenu par e-mail."
- No `home.js` change: the cards are server-rendered; the countdown and the check-in window are refreshed by reloading the page.

- [ ] **Step 1: Update `HomeControllerTest` and write the failing visit tests**

- Add `visitClient`, `customerClient` mocks; stub `visitClient.search(any(), any())` → empty, `customerClient.search(any())` → empty in `setUp`; pass both to the constructor.
- Add:
  - `visits`: stub two summaries (requested + confirmed), their listing and customer, and assert `model["visits"]` is a `VisitCardsView` with one requested and one upcoming card, the customer name, the listing line and the next step; `verify(visitClient).search(AGENT.id, VisitRules.OPEN)`.
  - `visits unavailable`: `ResourceAccessException` on `visitClient.search` → `VisitCardsView(unavailable = true)`.
  - `not verified - no visits`: pending KYC → `model["visits"]` null and `verify(visitClient, never()).search(any(), any())`.
  - `no customers`: `ResourceAccessException` on `customerClient.search` → cards still present, customer name `"Client"`.
- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeControllerTest`
Expected: compile FAIL (constructor/client unresolved).

- [ ] **Step 3: Implement the controller changes**

- [ ] **Step 4: Run the controller test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeControllerTest`
Expected: PASS.

- [ ] **Step 5: Update `HomeTemplateTest` and write the failing section tests**

- Add the two mocks and constructor arguments; default stubs return empty lists.
- `visits`: stub a requested and a confirmed summary, their listing and customer; render `GET /`; assert:
  ```kotlin
  assertEquals("Visites", page.selectFirst("#visits-title")?.text())
  val requested = page.selectFirst("[data-visit-group=requested]")!!
  assertEquals(1, requested.select("[data-visit-card]").size)
  val card = requested.selectFirst("[data-visit-card]")!!
  assertEquals("/visits/7001", card.selectFirst("[data-visit-link]")?.attr("href"))
  assertEquals("Sandrine Eboa", card.selectFirst("[data-visit-link]")?.text())
  assertEquals("Demandée pour le sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
  assertEquals("Sur place · Appartement 3 chambres · Bonapriso", card.selectFirst("[data-visit-listing]")?.text())
  assertEquals("42 h", card.selectFirst("[data-visit-left]")?.text())
  assertEquals("Confirmez la date avec le client.", card.selectFirst("[data-visit-next]")?.text())
  ```
- `no visits - empty state`: both lists empty → `[data-visits-empty]` visible with the wording.
- `visits unavailable`: `ResourceAccessException` on `visitClient.search` → `[data-visits-unavailable]`.
- `not verified - no visit section`: pending KYC → `#visits-title` null.
- `upcoming`: confirmed/window/checked-in tags and `whenLabel`; no timer on those cards.

- [ ] **Step 6: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeTemplateTest`
Expected: FAIL (no `#visits-title` in the template).

- [ ] **Step 7: Implement the template section and the CSS link**

- [ ] **Step 8: Run the home tests and the full module tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='HomeControllerTest,HomeTemplateTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
  modules/ndopify-partner-central/src/main/resources/templates/index.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "feat(npc): visits on the Espace agent"
```

---

### Task 7: Documentation and final verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`
- Modify: `README.md`

- [ ] **Step 1: Update `CLAUDE.md`**

- Add `visit` to the `ui/<feature>/` list.
- Add a *Pages* bullet for the visits:
  - The *Visites* section of the Espace agent: `HomeController` reads `GET /v1/visits?agent-id=&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN` (`NdVisitClient.search`), one `GET /v1/listings?id=` for their listings and one `GET /v1/customers?id=` for the names; requested by expiry, upcoming by time; stale requested/confirmed ones dropped; a failed search is the section's "unavailable", a missing listing/customer only leaves its line out.
  - `GET /visits/{id}` (`VisitController`, `templates/visit.html`) with `VisitViews` / `VisitRules` / `VisitSlots`: the prototype's panels, the schedule/refuse/cancel/check-in/no-show/check-out forms, the listing and customer as secondary reads, the tenant's slots and time formats, the server's windows, the session `npc.visit.code-locked.<id>` hint, the address from the listing, no code and no no-show distance (not exposed by the API).
  - The actions are PRG back to `/visits/{id}` with the `visitOutcome` flash; 403/404/409 is `changed`; an action whose visit moved on is `changed` without a call.
  - The page is authenticated by the default rule; no security matcher is added.
- Replace the final paragraph "ndopify-server now supports visits … Partner Central has no visit UI yet: the 'a visit' next step is text only." with the shipped state: the page and the *Visites* section exist; the mandate page's per-match visit line (and its `NpcVisits.forMatch` prototype) is still text-only, and the check-in attempts / no-show position are not exposed by the API.
- Keep the existing "Not built" list, minus what ships.

- [ ] **Step 2: Update `README.md`**

Add "visits" to the `ndopify-partner-central` row: "…, matched properties, their visits (confirm, check in/out, no-show), plus the public …".

- [ ] **Step 3: Run ktlint and the whole module**

Run: `cd modules/ndopify-partner-central && mvn validate`
Expected: no ktlint error.

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS, including the JaCoCo line `0.98` / class `0.95` gate.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md README.md
git commit -m "docs(npc): visits on the Espace agent and the visit page"
```

---

## Out

- **The mandate page's per-match visit line.** The prototype's `partner-central.js` (`NpcVisits.forMatch`) shows a match's visit on the mandate page, and `MatchRules.nextStep` already says "proposez-lui une visite"; this plan leaves that line text-only. The visit is reachable from the *Visites* section. A later plan can add the match's visit lookup and the visit tag to `MatchViews` / `MandateController`.
- **Live state.** The home cards and the visit page are server-rendered; the agent reloads after a time boundary (window opening, no-show opening, check-out opening, expiry). No AJAX/WebSocket.
- **Remaining code attempts.** `Visit` does not expose `codeAttempts`; the page cannot say "il vous reste N essais" and uses a generic code error. The session hint keeps the locked panel after a `VISIT_CODE_LOCKED` for that agent, but a fresh session shows the form again and the server still refuses.
- **No-show position.** The API does not return the no-show latitude/longitude or a distance; the panel shows the party and reason only. The position is still sent and stored by the server.
- **Customer feedback form, anonymous visit requests, the admin console, a visit fee/payment, SMS, a video room other than WhatsApp, and the public listing page's visit request** are out, as in `2026-11-02-visit-management.md`.
