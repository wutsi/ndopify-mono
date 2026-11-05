# Web Visit Request and Visit Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a match customer request an `ON_SITE` or `VIDEO` visit from `/match/{id}`, then open `/visits/{id}` to see it in every status and cancel it while it is `REQUESTED` or `CONFIRMED`.

**Architecture:** Two server-rendered controllers in `ndopify-web`. `MatchController` gains the request, loads the open visit and the customer, and keeps the existing `public-portal` cookie pattern. A new `VisitController` owns `/visits/{id}`, handles the `?token=` link exactly like `MatchController`, renders all statuses and posts the cancellation. Both use POST/Redirect/GET, no AJAX.

**Tech Stack:** Kotlin, Spring Boot MVC/Security, `RestClient`, Thymeleaf, jsoup + MockMvc + `MockRestServiceServer`, mockito-kotlin, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-11-04-web-visit-request-design.md`

## Global Constraints

- Do not change `ndopify-server` or `ndopify-dto`; `ndopify-dto.version` stays `0.0.80`.
- Never modify or delete anything under `modules/ndopify-web/src/main/html/` (the prototype). Copy from it.
- Work on a branch: `git checkout -b web-visit-request`. Commit only the files each task names.
- Dates and times go through `TenantFormatter`; the session instant is built in `Tenant.timeZone`. Never hard-code a format.
- `Listing.agentRemarks` is never shown nor put in a model. `ListingPages` already enforces this.
- The server's slots are exact: 7 days starting tomorrow, 08:00–20:00 in the tenant's time zone, on a 30-minute boundary (25 times). Use `VisitType.ON_SITE` / `VisitType.VIDEO`, never the prototype's `IN_PERSON`.
- The request is free; no fee, no payment, no SMS.
- Cancellation is offered only for `REQUESTED` and `CONFIRMED`.
- Never log a token or the visit code.
- UI copy is French; code and comments are English. KDoc says what a class is for and its non-obvious rules.
- The page works without JavaScript: popups open with `:target`, forms post, the server redirects.
- Tests: kotlin.test assertions, mockito-kotlin mocks. Coverage gate in `modules/ndopify-web/pom.xml`: line `0.97`, class `0.80`.
- Commit messages use the module's `feat(web): ...` style.

## Review Focus

1. **Tenant time zone:** a slot posted at 23:30 UTC in Douala must be the next day's first valid slot, not a rejected one. Pinned in `VisitSlotsTest` and `MatchControllerTest`.
2. **No duplicate request:** an open visit hides the form; a double submit lands on `VISIT_ALREADY_EXISTS`, redirects to the page, and the reload shows exactly one visit. Pinned in `MatchControllerTest` and `MatchTemplateTest`.
3. **Token and access:** a visit e-mail link works signed out; another customer's token shows `not-yours` and never the visit; the token leaves the address bar. Pinned in `VisitControllerTest`.
4. **Cancellation race:** a stale tab that cancels after the agent checked in gets `VISIT_CANNOT_BE_CANCELLED`, and the reload shows the current status. Pinned in `VisitControllerTest` and `VisitTemplateTest`.
5. **No JavaScript:** the match request and the cancellation post without scripts; the popups still open with `:target`. Pinned in `MatchTemplateTest` and `VisitTemplateTest` (forms' actions/methods, no passive-only controls).

---

### Task 1: Visit and customer REST clients

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClient.kt`
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdCustomerClient.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClientTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdCustomerClientTest.kt`

**Interfaces:**
- Produces:
  - `class NdVisitClient(baseUrl: String, builder: RestClient.Builder)`
    - `fun create(request: CreateVisitRequest): Long` — `POST /v1/visits`, returns `CreateVisitResponse.visitId`.
    - `fun openForMatch(matchId: Long): VisitSummary?` — `GET /v1/visits?match-id={id}&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1`, first visit or null.
    - `fun get(id: Long): Visit` — `GET /v1/visits/{id}`, returns `GetVisitResponse.visit`.
    - `fun cancel(id: Long, request: CancelVisitRequest)` — `POST /v1/visits/{id}/cancel`, bodiless.
  - `class NdCustomerClient(baseUrl: String, builder: RestClient.Builder)` with `fun get(id: Long): Customer` — `GET /v1/customers/{id}`, returns `GetCustomerResponse.customer`.

  KDoc: these calls use the customer's `public-portal` token (the shared `Authorization` interceptor); ndopify-server answers 401 for a rejected token, 403 for another customer's data, 404 when gone. The clients let those exceptions propagate.

- [ ] **Step 1: Write the failing `NdVisitClientTest`**

```kotlin
@Test
fun create() {
    server.expect(requestTo("http://localhost:8080/v1/visits"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.matchId").value(12))
        .andExpect(jsonPath("$.type").value("ON_SITE"))
        .andExpect(jsonPath("$.note").value("Fin de journée"))
        .andExpect(jsonPath("$.requestedAt").exists())
        .andRespond(withSuccess(json(CreateVisitResponse(visitId = 9)), MediaType.APPLICATION_JSON))

    assertEquals(9L, client.create(CreateVisitRequest(12, VisitType.ON_SITE, Date(), "Fin de journée")))
    server.verify()
}

@Test
fun `open for match`() {
    server.expect(requestTo("http://localhost:8080/v1/visits?match-id=12&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(json(SearchVisitResponse(visits = listOf(VisitSummary(id = 9, status = VisitStatus.REQUESTED)))), MediaType.APPLICATION_JSON))

    assertEquals(9L, client.openForMatch(12)?.id)
    server.verify()
}

@Test
fun `no open visit`() {
    server.expect(requestTo("http://localhost:8080/v1/visits?match-id=12&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1"))
        .andRespond(withSuccess(json(SearchVisitResponse()), MediaType.APPLICATION_JSON))

    assertNull(client.openForMatch(12))
}

@Test
fun get() {
    server.expect(requestTo("http://localhost:8080/v1/visits/9"))
        .andRespond(withSuccess(json(GetVisitResponse(Visit(id = 9, status = VisitStatus.REQUESTED))), MediaType.APPLICATION_JSON))

    assertEquals(VisitStatus.REQUESTED, client.get(9).status)
}

@Test
fun cancel() {
    server.expect(requestTo("http://localhost:8080/v1/visits/9/cancel"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.reason").value("Empêchement"))
        .andRespond(withStatus(HttpStatus.OK))

    client.cancel(9, CancelVisitRequest("Empêchement"))
    server.verify()
}

@Test
fun `get - forbidden`() {
    server.expect(requestTo("http://localhost:8080/v1/visits/9")).andRespond(withStatus(HttpStatus.FORBIDDEN))
    assertFailsWith<HttpClientErrorException.Forbidden> { client.get(9) }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdVisitClientTest`
Expected: compile FAIL (`NdVisitClient` unresolved).

- [ ] **Step 3: Write the failing `NdCustomerClientTest`**

```kotlin
@Test
fun get() {
    server.expect(requestTo("http://localhost:8080/v1/customers/42"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(json(GetCustomerResponse(Customer(id = 42, whatsappNumber = "+237699887766"))), MediaType.APPLICATION_JSON))

    assertEquals("+237699887766", client.get(42).whatsappNumber)
}

@Test
fun `get - not found`() {
    server.expect(requestTo("http://localhost:8080/v1/customers/42")).andRespond(withStatus(HttpStatus.NOT_FOUND))
    assertFailsWith<HttpClientErrorException.NotFound> { client.get(42) }
}
```

Use the same `MockRestServiceServer.bindTo(RestClient.builder())` + `JsonMapper` setup as `NdListingClientTest`.

- [ ] **Step 4: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdCustomerClientTest`
Expected: compile FAIL (`NdCustomerClient` unresolved).

- [ ] **Step 5: Implement both clients**

`NdVisitClient`:
- `create`: `rest.post().uri("/v1/visits").body(request).retrieve().body<CreateVisitResponse>()?.visitId ?: -1`.
- `openForMatch`: `rest.get().uri { it.path("/v1/visits").queryParam("match-id", matchId).queryParam("status", "REQUESTED").queryParam("status", "CONFIRMED").queryParam("status", "CHECKED_IN").queryParam("limit", 1).build() }` → `SearchVisitResponse.visits.firstOrNull()`.
- `get`: `GET /v1/visits/{id}` → `GetVisitResponse.visit ?: Visit()`.
- `cancel`: `POST /v1/visits/{id}/cancel` with body.

`NdCustomerClient`: `GET /v1/customers/{id}` → `GetCustomerResponse.customer ?: Customer()`.

- [ ] **Step 6: Run both tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='NdVisitClientTest,NdCustomerClientTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClient.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdCustomerClient.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClientTest.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdCustomerClientTest.kt
git commit -m "feat(web): visit and customer clients"
```

---

### Task 2: Visit slots and the match page's visit view

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitSlots.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitSlotsTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt`

**Interfaces:**
- Produces:

```kotlin
object VisitSlots {
    /** The 7 days starting tomorrow, labelled like the listing popup's days. */
    fun days(today: LocalDate, formatter: TenantFormatter): List<VisitDay>
    /** 08:00 to 20:00 every 30 minutes as ("08:00", "8h00") … ("20:00", "20h00"). */
    fun times(): List<Pair<String, String>>
    /** The instant of a tenant-local date and time, for CreateVisitRequest.requestedAt. */
    fun requestedAt(date: LocalDate, time: LocalTime, tenant: Tenant): Date
    /** "Sur place · samedi 7 novembre 2026 à 10:30": the long date in the tenant's locale, its time format. */
    fun label(typeLabel: String, at: Date, tenant: Tenant, formatter: TenantFormatter): String
}

data class VisitPanel(val open: OpenVisit?, val form: VisitForm?)
data class OpenVisit(val id: Long, val type: String, val slot: String, val status: VisitStatus)
data class VisitForm(
    val days: List<VisitDay>,
    val times: List<Pair<String, String>>,
    val contact: String?,
    val video: Boolean,
)

// MatchPage gains:
val visit: VisitPanel?,
val visitFlash: String?,
```

- `MatchPages.of(...)` keeps its current parameters and appends, with defaults:
  `customer: Customer? = null, openVisit: VisitSummary? = null, visit: String? = null`.
- Rules:
  - `visit` is non-null when `state == AnswerState.ACCEPTED`, or when `state == AnswerState.UNAVAILABLE` while
    `match.status == MatchStatus.ACCEPTED` and an open visit was loaded (the listing went away but the visit is
    alive). In the second case only `open` is set: `form` stays null.
  - `open` from `openVisit`: type `"Sur place"` / `"Visite vidéo"`, slot `VisitSlots.label(typeLabel, at, tenant, formatter)` with `scheduledAt ?: requestedAt`.
  - `form` only when `state == AnswerState.ACCEPTED && open == null && mandate.status == MandateStatus.APPROVED`; days/times from `VisitSlots`; `video = customer?.whatsappNumber?.isNotBlank() == true`.
  - `contact`: `listOfNotNull(name, whatsapp?.let { "WhatsApp $it" }).joinToString(" · ").ifEmpty { null }`, where name is the customer's `firstName lastName` trimmed.
  - `visitFlash`: `"requested"` and `open != null` → `"Votre demande de visite est envoyée. L'agent vous confirme la date par e-mail sous 48 h."`; `"invalid-slot"` → `"Ce créneau n'est plus disponible. Choisissez-en un autre."`; `"conflict"` → `"La visite n'a pas pu être demandée. Rechargez la page ou contactez votre agent."`; otherwise null.

- [ ] **Step 1: Write the failing `VisitSlotsTest`**

```kotlin
@Test
fun days() {
    val days = VisitSlots.days(LocalDate.of(2026, 10, 31), FORMATTER)

    assertEquals(7, days.size)
    assertEquals(VisitDay("2026-11-01", "dim.", "1", "nov."), days.first())
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
fun requestedAt() {
    assertEquals(
        Date.from(Instant.parse("2026-11-01T09:00:00Z")),
        VisitSlots.requestedAt(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0), TENANT),
    )
}

@Test
fun label() {
    assertEquals(
        "Sur place · samedi 7 novembre 2026 à 10:30",
        VisitSlots.label("Sur place", Date.from(Instant.parse("2026-11-07T09:30:00Z")), TENANT, FORMATTER),
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitSlotsTest`
Expected: compile FAIL (`VisitSlots` unresolved).

- [ ] **Step 3: Implement `VisitSlots`**

`days` = `(1..7).map { today.plusDays(it) }` → `VisitDay(value = it.toString(), dayName = formatter.date(it, "EEE"), day = it.dayOfMonth.toString(), month = formatter.date(it, "MMM"))`.
`times` = `generateSequence(LocalTime.of(8, 0)) { it.plusMinutes(30) }.takeWhile { !it.isAfter(LocalTime.of(20, 0)) }` mapped to `time.toString() to "%dh%02d".format(time.hour, time.minute)`.
`requestedAt` = `Date.from(LocalDateTime.of(date, time).atZone(ZoneId.of(tenant.timeZone)).toInstant())`.
`label` = `val local = at.toInstant().atZone(ZoneId.of(tenant.timeZone)).toLocalDate()` then
`"$typeLabel · ${formatter.date(local, "EEEE d MMMM yyyy")} à ${formatter.time(at)}"`.

- [ ] **Step 4: Write the failing `MatchPagesTest` cases**

Add a helper with `customer: Customer? = null`, `openVisit: VisitSummary? = null`, `visit: String? = null`, and these tests:

```kotlin
@Test
fun `accepted - visit form`() {
    val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
        customer = Customer(party = Party(firstName = "Sandrine", lastName = "Eboa"), whatsappNumber = "+237699887766"))

    assertEquals(7, page.visit?.form?.days?.size)
    assertEquals("2026-11-01", page.visit?.form?.days?.first()?.value)
    assertEquals(25, page.visit?.form?.times?.size)
    assertEquals("Sandrine Eboa · WhatsApp +237699887766", page.visit?.form?.contact)
    assertEquals(true, page.visit?.form?.video)
    assertNull(page.visit?.open)
}

@Test
fun `accepted - open visit replaces the form`() {
    val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
        openVisit = VisitSummary(id = 9, status = VisitStatus.REQUESTED, type = VisitType.VIDEO, requestedAt = date("2026-11-07T09:30:00Z")))

    assertEquals(9L, page.visit?.open?.id)
    assertEquals("Visite vidéo · samedi 7 novembre 2026 à 10:30", page.visit?.open?.slot)
    assertNull(page.visit?.form)
}

@Test
fun `accepted - mandate not approved leaves no form`() {
    val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.PENDING))
    assertNull(page.visit?.form)
}

@Test
fun `unavailable listing still shows an open visit`() {
    val page = page(match = match.copy(status = MatchStatus.ACCEPTED), listing = null,
        openVisit = VisitSummary(id = 9, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE))

    assertEquals(AnswerState.UNAVAILABLE, page.state)
    assertEquals(9L, page.visit?.open?.id)
    assertNull(page.visit?.form)
}

@Test
fun `accepted - no whatsapp hides the video option`() {
    val page = page(match = match.copy(status = MatchStatus.ACCEPTED), mandate = mandate.copy(status = MandateStatus.APPROVED),
        customer = Customer(party = Party(firstName = "Sandrine"), whatsappNumber = null))
    assertEquals(false, page.visit?.form?.video)
    assertEquals("Sandrine", page.visit?.form?.contact)
}

@Test
fun `visit flashes`() {
    assertEquals("Votre demande de visite est envoyée. L'agent vous confirme la date par e-mail sous 48 h.",
        page(match = match.copy(status = MatchStatus.ACCEPTED), openVisit = VisitSummary(id = 9), visit = "requested").visitFlash)
    assertEquals("Ce créneau n'est plus disponible. Choisissez-en un autre.", page(visit = "invalid-slot").visitFlash)
    assertEquals("La visite n'a pas pu être demandée. Rechargez la page ou contactez votre agent.", page(visit = "conflict").visitFlash)
    assertNull(page(visit = "requested").visitFlash) // no open visit: nothing happened
}
```

Use `date(iso)" = Date.from(Instant.parse(iso))` as a local helper.

- [ ] **Step 5: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchPagesTest`
Expected: compile FAIL (`visit`/`VisitPanel` unresolved).

- [ ] **Step 6: Implement `MatchPage` additions and `MatchPages.of`**

Build the panel at the end of `of`:
```kotlin
val open = openVisit?.let { OpenVisit(it.id, typeLabel(it.type), slot(it, formatter), it.status) }
val panel = when {
    state == AnswerState.ACCEPTED -> VisitPanel(
        open,
        if (open == null && mandate.status == MandateStatus.APPROVED) {
            VisitForm(VisitSlots.days(today, formatter), VisitSlots.times(), contact(customer), customer?.whatsappNumber?.isNotBlank() == true)
        } else null,
    )
    state == AnswerState.UNAVAILABLE && match.status == MatchStatus.ACCEPTED && open != null -> VisitPanel(open, null)
    else -> null
}
```
Set `visit = panel`, `visitFlash = flash(visit, panel)`.

- [ ] **Step 7: Run both tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='VisitSlotsTest,MatchPagesTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitSlots.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPage.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitSlotsTest.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt
git commit -m "feat(web): visit slots and match visit view"
```

---

### Task 3: Shared public-portal link helper

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/layout/PortalLink.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchController.kt` (replace its private `JWT_SHAPE` and `withoutToken`)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/layout/PortalLinkTest.kt`

**Interfaces:**
- Produces:

```kotlin
object PortalLink {
    /** True for three base64url segments: all a link's token must look like before it is kept. */
    fun isToken(value: String): Boolean
    /** The request's path and parameters, minus `token`. */
    fun withoutToken(request: HttpServletRequest): String
}
```

- [ ] **Step 1: Write the failing `PortalLinkTest`**

```kotlin
@Test
fun `token shape`() {
    assertTrue(PortalLink.isToken("aaa.bbb.ccc"))
    listOf("abc", "a.b", "a.b.c.d", "a.b;c.d", "a.b c.d", "a.\"b.c", "a.b,c.d").forEach {
        assertFalse(PortalLink.isToken(it), it)
    }
}

@Test
fun `strips only the token`() {
    val request = MockHttpServletRequest("GET", "/visits/9")
    request.addParameter("token", "aaa.bbb.ccc")
    request.addParameter("cancel", "done")

    assertEquals("/visits/9?cancel=done", PortalLink.withoutToken(request))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=PortalLinkTest`
Expected: compile FAIL.

- [ ] **Step 3: Implement `PortalLink`**

Move `MatchController`'s private `JWT_SHAPE` and `withoutToken` bodies into the object. `isToken` uses `[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+){2}`; `withoutToken` keeps `UriComponentsBuilder.newInstance().path(request.requestURI)`, copies every parameter except `token`, then `.build().encode().toUriString()`.

- [ ] **Step 4: Replace the private members in `MatchController`**

Delete `JWT_SHAPE` and `withoutToken`, and call `PortalLink.isToken(token)` / `PortalLink.withoutToken(request)`.

- [ ] **Step 5: Run the helper and match tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='PortalLinkTest,MatchControllerTest'`
Expected: PASS (the existing token tests prove the extraction).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/layout/PortalLink.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchController.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/layout/PortalLinkTest.kt
git commit -m "refactor(web): shared public-portal link helper"
```

---

### Task 4: Match request endpoint and page data

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/VisitRequestForm.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchController.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchControllerTest.kt`

**Interfaces:**
- `MatchController` constructor becomes:
  `MatchController(matchClient, mandateClient, agentClient, listingClient, locationClient, visitClient: NdVisitClient, customerClient: NdCustomerClient, accessTokenService, clock)`.
- `VisitRequestForm(type: String? = null, visitDate: String? = null, visitTime: String? = null, note: String? = null)` with
  `fun toRequest(matchId: Long, tenant: Tenant): CreateVisitRequest?` — null when the type is not `ON_SITE`/`VIDEO`, or the date/time is missing/unparseable; `note` trimmed, blank → null, cut to 500.
- `POST /match/{id}/visit` returns the same `Any` as `accept`/`reject`.

- [ ] **Step 1: Update `MatchControllerTest` setup and write the failing GET tests**

Add mocks and constructor arguments:
```kotlin
private val visitClient = mock<NdVisitClient>()
private val customerClient = mock<NdCustomerClient>()
// ...
MatchController(matchClient, mandateClient, agentClient, listingClient, locationClient, visitClient, customerClient, tokens, clock)
```

New tests:
```kotlin
@Test
fun `accepted - loads the open visit and the customer`() {
    MatchFixtures.signIn()
    doReturn(MATCH.copy(status = MatchStatus.ACCEPTED)).whenever(matchClient).get(12)
    doReturn(Mandate(id = 7, agentId = 3, cityId = 1, status = MandateStatus.APPROVED, customerId = 42)).whenever(mandateClient).get(7)
    doReturn(VisitSummary(id = 9, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).openForMatch(12)
    doReturn(Customer(id = 42, whatsappNumber = "+237699887766")).whenever(customerClient).get(42)

    val page = page(request("/match/12"))

    assertEquals(9L, page.visit?.open?.id)
    assertNull(page.visit?.form)
    verify(customerClient, never()).get(42) // the form is hidden while a visit is open
}
```

Add a second test: accepted + approved + no open visit + customer → `page.visit?.form` not null and `verify(customerClient).get(42)`.

Add a third: accepted + approved + no open visit + `customerClient.get` throws `NotFound` → form still present, `video == false`, `contact == null`.

Add a fourth: accepted, listing `404`, open visit 9 → `page.state == UNAVAILABLE`, `page.visit?.open?.id == 9`, `page.visit?.form == null`, and no customer call.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchControllerTest`
Expected: compile FAIL (`visitClient` unresolved).

- [ ] **Step 3: Write the failing POST tests**

```kotlin
@Test
fun requestVisit() {
    MatchFixtures.signIn()
    doReturn(Mandate(id = 7, agentId = 3, cityId = 1, status = MandateStatus.APPROVED, customerId = 42)).whenever(mandateClient).get(7)
    doReturn(Customer(id = 42)).whenever(customerClient).get(42)

    val result = perform(post("/match/12/visit")
        .param("type", "ON_SITE").param("visitDate", "2026-11-01").param("visitTime", "10:00").param("note", "  Fin de journée  "))

    verify(visitClient).create(
        CreateVisitRequest(
            matchId = 12,
            type = VisitType.ON_SITE,
            requestedAt = Date.from(Instant.parse("2026-11-01T09:00:00Z")),
            note = "Fin de journée",
        ),
    )
    assertEquals(302, result.response.status)
    assertEquals("/match/12?visit=requested", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `request visit - stale slot`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.BAD_REQUEST)).whenever(visitClient).create(any())
    val result = perform(post("/match/12/visit").param("type", "ON_SITE").param("visitDate", "2026-11-01").param("visitTime", "10:00"))
    assertEquals("/match/12?visit=invalid-slot", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `request visit - conflict`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.CONFLICT)).whenever(visitClient).create(any())
    val result = perform(post("/match/12/visit").param("type", "ON_SITE").param("visitDate", "2026-11-01").param("visitTime", "10:00"))
    assertEquals("/match/12?visit=conflict", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `request visit - invalid input`() {
    MatchFixtures.signIn()
    val result = perform(post("/match/12/visit").param("type", "BUS").param("visitDate", "x").param("visitTime", "y"))
    assertEquals("/match/12?visit=invalid-slot", result.response.getHeader(HttpHeaders.LOCATION))
    verify(visitClient, never()).create(any())
}

@Test
fun `request visit - note cut to what ndopify-server takes`() {
    MatchFixtures.signIn()
    perform(post("/match/12/visit").param("type", "VIDEO").param("visitDate", "2026-11-01").param("visitTime", "10:00")
        .param("note", "x".repeat(1200)))
    verify(visitClient).create(CreateVisitRequest(12, VisitType.VIDEO, Date.from(Instant.parse("2026-11-01T09:00:00Z")), "x".repeat(500)))
}

@Test
fun `request visit - signed out`() {
    val result = perform(post("/match/12/visit"))
    assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
    verify(visitClient, never()).create(any())
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchControllerTest`
Expected: compile FAIL (`VisitRequestForm`/route unresolved).

- [ ] **Step 5: Implement the controller and security matcher**

In `index`, add `@RequestParam(name = "visit", required = false) visit: String?` to the signature, then after the mandate/listing are read:
```kotlin
val openVisit = if (match.status == MatchStatus.ACCEPTED) visitClient.openForMatch(id) else null
val customer = if (openVisit == null && mandate.status == MandateStatus.APPROVED && listing != null) {
    try { customerClient.get(mandate.customerId) } catch (ex: HttpClientErrorException.NotFound) { null } catch (ex: HttpClientErrorException.Forbidden) { null }
} else null
val page = MatchPages.of(..., answer, customer, openVisit, visit)
```

Add:
```kotlin
@PostMapping("/match/{id}/visit")
fun requestVisit(@PathVariable id: Long, @ModelAttribute form: VisitRequestForm, model: Model): Any =
    signedIn("POST /match/$id/visit", model) {
        val tenant = model.getAttribute("tenant") as Tenant
        val request = form.toRequest(id, tenant)
            ?: return@signedIn redirect("/match/$id?visit=invalid-slot")
        try {
            visitClient.create(request)
            redirect("/match/$id?visit=requested")
        } catch (ex: HttpClientErrorException.BadRequest) {
            redirect("/match/$id?visit=invalid-slot")
        } catch (ex: HttpClientErrorException.Conflict) {
            redirect("/match/$id?visit=conflict")
        }
    }
```
`toRequest` parses with `runCatching`: `VisitType.entries.find { it.name == type }?.takeIf { it != VisitType.UNKNOWN }`, `LocalDate.parse(visitDate)`, `LocalTime.parse(visitTime)`; the instant uses `VisitSlots.requestedAt(date, time, tenant)`.

`SecurityConfiguration`: add `.requestMatchers(HttpMethod.POST, "/match/*/visit").permitAll()` on the same line group as `accept`/`reject`.

- [ ] **Step 6: Run the controller test**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchControllerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/VisitRequestForm.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchController.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchControllerTest.kt
git commit -m "feat(web): post a visit request from the match page"
```

---

### Task 5: Match template, popup and assets

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/match.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/js/listing.js`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/js/proposal.js`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/proposal.css`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchPage.visit`, `MatchPage.visitFlash`, `MatchController.VIEW`.
- The popup form posts to `/match/{id}/visit` with `type`, `visitDate`, `visitTime`, `note`.

Template rules:
- Add the `i-calendar` and `i-video` symbols to the sprite.
- Replace the `ACCEPTED` price-card content:
  - `page.visit.form != null` → the "Demander une visite" button (`data-modal-open href="#visit"`), too in the bottom bar.
  - `page.visit.open != null` → the `answer__visit` block: "Visite demandée/confirmée/en cours : <slot>", a "Voir ma visite" link to `/visits/{open.id}`, and the status hint. The bottom bar below 1024px shows the same "Voir ma visite" link.
  - both null (mandate not approved) → keep "Pour organiser une visite, contactez <agent>."
- Add the open-visit block to the `UNAVAILABLE` section when `page.visit?.open != null`.
- Render `page.visitFlash` in a `proposal-flash` block next to `page.flash`.
- Add the `#visit` popup after the `#reject` popup, `th:if="${page.visit?.form}"`:
  - title "Demander une visite", subtitle listing title + price.
  - form `data-visit-request-form`, action `@{/match/{id}/visit(id=${page.id})}`, method post.
  - type radios `ON_SITE` (checked) / `VIDEO` (only when `form.video`); labels "Sur place" / "En vidéo"; those two hint paragraphs.
  - contact line when `form.contact != null`: "Vos coordonnées : …".
  - day radios `th:each="day : ${form.days}"`, time select `th:each="time : ${form.times}"`.
  - note textarea `maxlength="500"`.
  - submit "Envoyer la demande".
- `listing.js`: at the top of `initVisitForm`, return when `!form || !done || !phoneInput` so the match popup (which has no `data-interest-form`/`data-interest-done`) is never hijacked.
- `proposal.js`: add `visit: '[name="type"]'` to `FIRST`; for `#visit`, set `aria-disabled` on submit until a day and a time are chosen (never `preventDefault`).
- `proposal.css`: copy the prototype's visit rules from `src/main/html/assets/css/proposition.css`: `.visit-request__contact`, `.visit-type__hint`, `.interest-form__days--week`, `#visit-note`, `.nowrap`, `.answer__visit` (+ `.answer__visit .icon`).

- [ ] **Step 1: Write the failing `MatchTemplateTest` cases**

Extend the `page(...)` helper with `visitPanel: VisitPanel? = null` and `visitFlash: String? = null`, then:

```kotlin
@Test
fun `accepted - visit request popup`() {
    val doc = render(page(MatchStatus.ACCEPTED, visitPanel = VisitPanel(open = null, form = VisitForm(
        days = VisitSlots.days(LocalDate.of(2026, 10, 31), FORMATTER),
        times = VisitSlots.times(),
        contact = "Sandrine Eboa · WhatsApp +237699887766",
        video = true,
    ))))

    assertEquals("post", doc.selectFirst("#visit form")?.attr("method"))
    assertEquals("/match/12/visit", doc.selectFirst("#visit form")?.attr("action"))
    assertEquals(7, doc.select("#visit input[name=visitDate]").size)
    assertEquals("2026-11-01", doc.selectFirst("#visit input[name=visitDate]")?.attr("value"))
    assertEquals(25, doc.select("#visit-time option[value~=.+]").size)
    assertEquals(listOf("ON_SITE", "VIDEO"), doc.select("#visit input[name=type]").map { it.attr("value") })
    assertEquals("Sandrine Eboa · WhatsApp +237699887766", doc.selectFirst(".visit-request__contact")?.text())
    assertEquals("#visit-note", doc.selectFirst("#visit textarea")?.attr("id"))
    assertEquals("Demander une visite", doc.selectFirst(".answer[data-answer=ACCEPTED] .btn")?.text())
}

@Test
fun `accepted - no video without whatsapp`() {
    val doc = render(page(MatchStatus.ACCEPTED, visitPanel = VisitPanel(null, VisitForm(
        VisitSlots.days(LocalDate.of(2026, 10, 31), FORMATTER), VisitSlots.times(), "Sandrine", false))))

    assertEquals(listOf("ON_SITE"), doc.select("#visit input[name=type]").map { it.attr("value") })
}

@Test
fun `accepted - open visit`() {
    val doc = render(page(MatchStatus.ACCEPTED, visitPanel = VisitPanel(
        OpenVisit(9, "Sur place", "Sur place · samedi 7 novembre 2026 à 10:30", VisitStatus.REQUESTED), null)))

    assertEquals("Voir ma visite", doc.selectFirst(".answer__visit a")?.text())
    assertEquals("/visits/9", doc.selectFirst(".answer__visit a")?.attr("href"))
    assertEquals("/visits/9", doc.selectFirst(".cta-bar__actions a[href=/visits/9]")?.attr("href"))
    assertNull(doc.selectFirst("#visit"))
    assertNull(doc.selectFirst(".answer[data-answer=ACCEPTED] a[data-modal-open]"))
}
```

Add `unavailable - open visit keeps the link`: page with `listing = null` and an open visit → `/visits/9` link.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchTemplateTest`
Expected: FAIL (no `#visit` in the template).

- [ ] **Step 3: Implement the template, JS and CSS**

Follow the template rules above. Use `th:with="form=${page.visit.form}"` around the form so the day/time loops read `form.days` / `form.times`.

- [ ] **Step 4: Run the template test and the listing template test**

Run: `cd modules/ndopify-web && mvn test -Dtest='MatchTemplateTest,ListingTemplateTest'`
Expected: PASS (the listing page's prototype form is unchanged; the `listing.js` guard only stops a null form).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/match.html \
  modules/ndopify-web/src/main/resources/public/assets/js/listing.js \
  modules/ndopify-web/src/main/resources/public/assets/js/proposal.js \
  modules/ndopify-web/src/main/resources/public/assets/css/proposal.css \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt
git commit -m "feat(web): request-visit popup on the match page"
```

---

### Task 6: Visit page view

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt`
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class VisitPage(
    val id: Long,
    val status: VisitStatus,
    val statusLabel: String,      // Demandée, Confirmée, En cours, Terminée, Annulée, Expirée, Non effectuée
    val statusTone: String,       // info, success, neutral
    val type: VisitType,
    val typeLabel: String,        // Sur place / Visite vidéo
    val requestedSlot: String?,   // "Sur place · samedi 7 novembre 2026 à 10:30"
    val scheduledSlot: String?,
    val changedSlot: String?,     // only when scheduledAt != requestedAt
    val note: String?,
    val code: String?,
    val address: String?,
    val mapUrl: String?,
    val videoNumber: String?,
    val checkedInAt: String?,
    val cancelledAt: String?,
    val cancelledBy: VisitParty?,
    val cancellationReason: String?,
    val noShowParty: VisitParty?,
    val noShowReason: VisitNoShowReason?,
    val canCancel: Boolean,
    val flash: String?,
    val listing: ListingPage?,
    val agent: VisitAgent?,
    val matchUrl: String,         // /match/{matchId}
)
data class VisitAgent(val name: String, val initials: String, val photoUrl: String?, val whatsappUrl: String?)

object VisitPages {
    fun of(
        visit: Visit,
        listing: Listing?,
        images: List<ListingImage>,
        locations: List<Location>,
        agent: Agent?,
        customer: Customer?,
        tenant: Tenant,
        formatter: TenantFormatter,
        today: LocalDate,
        cancel: String?,
    ): VisitPage
}
```

- Rules:
  - status labels/tones as above; a default block for `UNKNOWN` = "Visite"/"neutral".
  - `requestedSlot` = `VisitSlots.label(typeLabel, visit.requestedAt, tenant, formatter)`; `scheduledSlot` the same with `scheduledAt` when set; `changedSlot` only for `CONFIRMED` with different `scheduledAt`/`requestedAt`: `"Vous aviez demandé ${longDate(visit.requestedAt)} à ${formatter.time(visit.requestedAt)} : l'agent a proposé un autre créneau."`, where `longDate` is `formatter.date(localDate, "EEEE d MMMM yyyy")` in `tenant.timeZone`.
  - `code` = `visit.code?.ifBlank { null }`; `address` = `visit.address?.ifBlank { null }` only when `visit.type == VisitType.ON_SITE` (never the exact address of a VIDEO visit, even if a server payload had one).
  - `mapUrl` only with `address != null && latitude != null && longitude != null`: `https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude`.
  - `videoNumber` = `customer?.whatsappNumber?.ifBlank { null }`.
  - `checkedInAt` = `visit.checkedInAt?.let { formatter.time(it) }`; `cancelledAt` = `visit.cancelledAt?.let { "${formatter.date(it)} à ${formatter.time(it)}" }`.
  - `canCancel` = status `REQUESTED` or `CONFIRMED`.
  - `flash`: `"done"` + status `CANCELLED` + `cancelledBy == CUSTOMER` → `"Votre visite est annulée. L'agent est prévenu."`; `"conflict"` → `"Cette visite ne peut plus être annulée."`; otherwise null.
  - `listing` = `ListingPages.of(listing, images, locations, tenant, formatter, today)` when `listing != null`.
  - `agent` = null when `agent == null`; else name `"first last".trim().ifEmpty { "votre agent" }`, initials from the first letters, photo URL, `https://wa.me/<digits>` when the number has digits.

- [ ] **Step 1: Write the failing `VisitPagesTest` cases**

Use a `visit(status = …, type = …)` fixture with `requestedAt = date("2026-11-07T09:30:00Z")`, `scheduledAt = date("2026-11-07T10:00:00Z")`, `code = "4827"`, `address = "Rue Njo-Njo, portail bleu"`, `latitude = 4.0247`, `longitude = 9.6943`.

```kotlin
@Test
fun `requested`() {
    val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE).copy(note = "Fin de journée"))

    assertEquals("Demandée", page.statusLabel)
    assertEquals("Sur place · samedi 7 novembre 2026 à 10:30", page.requestedSlot)
    assertEquals("Fin de journée", page.note)
    assertTrue(page.canCancel)
    assertNull(page.code)
    assertNull(page.address)
}

@Test
fun `confirmed on site`() {
    val page = of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE))

    assertEquals("Confirmée", page.statusLabel)
    assertEquals("4827", page.code)
    assertEquals("Rue Njo-Njo, portail bleu", page.address)
    assertEquals("https://www.google.com/maps/dir/?api=1&destination=4.0247,9.6943", page.mapUrl)
    assertTrue(page.canCancel)
}

@Test
fun `confirmed video has the number, never the address`() {
    val page = of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766"))

    assertNull(page.address)
    assertNull(page.mapUrl)
    assertEquals("+237699887766", page.videoNumber)
}

@Test
fun `changed slot`() {
    assertEquals(
        "Vous aviez demandé samedi 7 novembre 2026 à 10:30 : l'agent a proposé un autre créneau.",
        of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE)).changedSlot,
    )
}

@Test
fun `checked in cannot cancel`() {
    val page = of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE))
    assertFalse(page.canCancel)
    assertEquals("En cours", page.statusLabel)
}

@Test
fun `ended states`() {
    assertEquals("Terminée", of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)).statusLabel)
    assertEquals("Annulée", of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE)).statusLabel)
    assertEquals("Expirée", of(visit(VisitStatus.EXPIRED, VisitType.ON_SITE)).statusLabel)
    assertEquals("Non effectuée", of(visit(VisitStatus.NO_SHOW, VisitType.ON_SITE)).statusLabel)
    assertFalse(of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE)).canCancel)
}

@Test
fun `cancel flashes`() {
    val cancelled = visit(VisitStatus.CANCELLED, VisitType.ON_SITE).copy(cancelledBy = VisitParty.CUSTOMER)
    assertEquals("Votre visite est annulée. L'agent est prévenu.", of(cancelled, cancel = "done").flash)
    assertEquals("Cette visite ne peut plus être annulée.", of(cancelled, cancel = "conflict").flash)
    assertNull(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), cancel = "done").flash)
}

@Test
fun `listing and agent are optional`() {
    val page = of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE), listing = null, agent = null)
    assertNull(page.listing)
    assertNull(page.agent)
}
```

The `of(...)` helper passes the standard listing/agent/customer and `LocalDate.of(2026, 10, 31)`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitPagesTest`
Expected: compile FAIL.

- [ ] **Step 3: Implement `VisitPage` and `VisitPages`**

Build the fields in the order above. Reuse `ListingPages.of` for the listing card and `VisitSlots.label` for the two slots. `statusTone` and labels in two maps.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitPagesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt
git commit -m "feat(web): visit page view"
```

---

### Task 7: Visit controller, link page and security

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt`
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/CancelVisitForm.kt`
- Create: `modules/ndopify-web/src/main/resources/templates/visit-link.html`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt`

**Interfaces:**
- `class VisitController(visitClient, listingClient, locationClient, agentClient, customerClient, accessTokenService, clock)`.
- Companion: `VIEW = "visit"`, `VIEW_LINK = "visit-link"`, `REASON_EXPIRED = "expired"`, `REASON_NOT_YOURS = "not-yours"`.
- `@GetMapping("/visits/{id}") fun index(@PathVariable id: Long, @RequestParam(name = "token", required = false) token: String?, @RequestParam(name = "cancel", required = false) cancel: String?, request: HttpServletRequest, model: Model): Any`.
- `@PostMapping("/visits/{id}/cancel") fun cancel(@PathVariable id: Long, @ModelAttribute form: CancelVisitForm, model: Model): Any`.
- `data class CancelVisitForm(val reason: String? = null)`.

- [ ] **Step 1: Write the failing token and access tests**

Use the `MatchControllerTest` cookie/real-token helpers and `MatchFixtures.signIn()`. Copy its `request`, `perform`,
`failure` and `page(result)` helpers, swapping `MatchController` for `VisitController`; mock the clients in `setUp`
as in `MatchControllerTest`: listing 123 `PUBLISHED`, one image, locations, agent 3, customer 42.

```kotlin
@Test
fun `token is stored and stripped`() {
    val result = request("/visits/9?token=aaa.bbb.ccc&cancel=done")
    verify(accessTokenService).set("aaa.bbb.ccc")
    assertEquals(302, result.response.status)
    assertEquals("/visits/9?cancel=done", result.response.getHeader(HttpHeaders.LOCATION))
    verify(visitClient, never()).get(9)
}

@Test
fun `invalid token shape`() {
    val result = request("/visits/9?token=abc")
    assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
    assertEquals(VisitController.REASON_EXPIRED, result.modelAndView?.model?.get("reason"))
    verify(accessTokenService, never()).set(any())
}

@Test
fun `signed out`() {
    val result = request("/visits/9")
    assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
    assertEquals("expired", result.modelAndView?.model?.get("reason"))
}

@Test
fun `server unauthorized`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(visitClient).get(9)
    val result = request("/visits/9")
    verify(accessTokenService).remove()
    assertEquals("expired", result.modelAndView?.model?.get("reason"))
}

@Test
fun `another customer`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.FORBIDDEN)).whenever(visitClient).get(9)
    val result = request("/visits/9")
    assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
}

@Test
fun `not found`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.NOT_FOUND)).whenever(visitClient).get(9)
    val result = request("/visits/9")
    assertEquals(404, result.response.status)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitControllerTest`
Expected: compile FAIL.

- [ ] **Step 3: Write the failing page and cancel tests**

```kotlin
@Test
fun page() {
    MatchFixtures.signIn()
    doReturn(Visit(id = 9, matchId = 12, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE))
        .whenever(visitClient).get(9)

    val result = request("/visits/9")

    assertEquals(VisitController.VIEW, result.modelAndView?.viewName)
    val page = result.modelAndView?.model?.get("page") as VisitPage
    assertEquals(9L, page.id)
    assertEquals("Demandée", page.statusLabel)
    assertEquals("/match/12", page.matchUrl)
    verify(listingClient).get(123)
    verify(agentClient).get(3)
    verify(customerClient).get(42)
}

@Test
fun `listing gone is tolerated`() {
    MatchFixtures.signIn()
    doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
    doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).get(123)

    val page = page(request("/visits/9"))
    assertNull(page.listing)
    verify(listingClient, never()).images(123)
}

@Test
fun `images gone is tolerated`() {
    MatchFixtures.signIn()
    doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
    doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).images(123)

    val page = page(request("/visits/9"))
    assertNotNull(page.listing)
    assertTrue(page.listing!!.photos.isEmpty())
}

@Test
fun cancel() {
    MatchFixtures.signIn()
    val result = perform(post("/visits/9/cancel").param("reason", "  Empêchement  "))
    verify(visitClient).cancel(9, CancelVisitRequest("Empêchement"))
    assertEquals("/visits/9?cancel=done", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `cancel - blank reason`() {
    MatchFixtures.signIn()
    perform(post("/visits/9/cancel").param("reason", "  "))
    verify(visitClient).cancel(9, CancelVisitRequest(null))
}

@Test
fun `cancel - conflict`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.CONFLICT)).whenever(visitClient).cancel(any(), any())
    val result = perform(post("/visits/9/cancel"))
    assertEquals("/visits/9?cancel=conflict", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `cancel - signed out`() {
    val result = perform(post("/visits/9/cancel"))
    assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
    verify(visitClient, never()).cancel(any(), any())
}
```

- [ ] **Step 4: Implement the controller, link page and matchers**

- `index`:
  - token branch first: if `!token.isNullOrBlank()`, then `if (!PortalLink.isToken(token)) return link(REASON_EXPIRED, model)`, `accessTokenService.set(token)`, `redirect(PortalLink.withoutToken(request))`; otherwise `signedIn`.
  - in `signedIn`: `visit = visitClient.get(id)`.
  - listing: `try listingClient.get(visit.listingId).takeIf { it.status == ListingStatus.PUBLISHED } catch (NotFound/Forbidden) { null }`.
  - images only if listing != null: `try listingClient.images(id) catch (NotFound/Forbidden) { emptyList() }`; locations from city/neighborhood (a failure there is left to the global handler).
  - agent: `visit.agentId.takeIf { it > 0 }?.let { try agentClient.get(it) catch (NotFound) { Agent() } }`.
  - customer: `try customerClient.get(visit.customerId) catch (NotFound/Forbidden) { null }`.
  - `model["page"] = VisitPages.of(...)`; return `VIEW`.
- `cancel`: `signedIn` → `try visitClient.cancel(id, CancelVisitRequest(form.reason?.trim()?.ifEmpty { null }?.take(500))); redirect("/visits/$id?cancel=done") catch (Conflict) { redirect("/visits/$id?cancel=conflict") }`.
- `signedIn`/`link`/`redirect` copied from `MatchController` (same 401/403 handling), with the visit-link view.
- `visit-link.html`: the match-link page with `<h1>Votre visite</h1>`, `not-yours` → "Ce lien a été envoyé à une autre personne.", otherwise "Ce lien a expiré. Demandez à votre agent de vous renvoyer le lien de la visite.", noindex, tenant logo.
- Security: add `.requestMatchers(HttpMethod.GET, "/visits/*").permitAll()` and `.requestMatchers(HttpMethod.POST, "/visits/*/cancel").permitAll()`.

- [ ] **Step 5: Run the controller test**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/CancelVisitForm.kt \
  modules/ndopify-web/src/main/resources/templates/visit-link.html \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt
git commit -m "feat(web): visit page and cancellation endpoint"
```

---

### Task 8: Visit page template and assets

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/visit.html`
- Create: `modules/ndopify-web/src/main/resources/public/assets/css/visit.css`
- Create: `modules/ndopify-web/src/main/resources/public/assets/js/visit.js`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `VisitPage`, `VisitController.VIEW`.
- Copy `src/main/html/visite.html`'s markup structure and `src/main/html/assets/css/visite.css` into the served names. Do not port `visite.js`'s state faking.

Template rules:
- `<head>`: `noindex, nofollow`, `no-referrer`, title `Votre visite | <tenant name>`, tenant logo/icon, `styles.css`, `listing.css`, `proposal.css`, `visit.css`.
- Header/footer as `match.html`; icon sprite from `visite.html` (`i-calendar`, `i-clock`, `i-pin`, `i-video`, `i-key`, `i-id`, `i-chat`, `i-check`, `i-close`, `i-info`, `i-route`, …).
- Summary: compact listing card when `page.listing` (`photos[0].src`, `title`, `place`, `price`, link to `page.matchUrl`); agent card when `page.agent` (name, initials/photo, WhatsApp link).
- Badge: `page.statusLabel` with class `visit-badge--${page.statusTone}`.
- One block per status, `th:if="${page.status.name() == 'REQUESTED'}"`, etc.:
  - REQUESTED: h2 "Votre demande est envoyée"; `requestedSlot`; note when present; notice "L'agent confirme la visite sous 48 h, par e-mail : il peut vous proposer une autre heure."; the ON_SITE/VIDEO paragraph; "Annuler ma demande" when `page.canCancel`.
  - CONFIRMED: h2 "Votre visite est confirmée"; `scheduledSlot`; `changedSlot` when present; code block "Votre code de visite" (`aria-label`, digits + visually-hidden spaced digits); ON_SITE address block + map "Itinéraire" when `mapUrl`; VIDEO block with the neighbourhood and `videoNumber` when present, "L'adresse exacte vous sera donnée si vous visitez sur place."; "Annuler la visite" when `page.canCancel`.
  - CHECKED_IN: h2 "Visite en cours" / "Appel en cours"; `checkedInAt`; the prototype's short copy.
  - COMPLETED: h2 "Votre visite est terminée"; slot; "Merci, votre visite est terminée." No feedback form.
  - CANCELLED: h2 "Vous avez annulé la visite" when `cancelledBy == CUSTOMER`, else "<agent> a annulé la visite"; `cancelledAt`, the slot (`scheduledSlot ?: requestedSlot`), `cancellationReason` when the agent cancelled; "Demander une autre visite" → `page.matchUrl`.
  - EXPIRED: h2 "Cette demande a expiré"; `requestedSlot`; another-visit link.
  - NO_SHOW: h2 "La visite n'a pas eu lieu"; the slot (`scheduledSlot ?: requestedSlot`); when `noShowParty == CUSTOMER`, "Vous avez refusé de donner le code." for `CODE_REFUSED`, else "Vous n'étiez pas à la visite."; when `noShowParty == AGENT`, "L'agent n'est pas venu / ne vous a pas appelé."; another-visit link.
- `page.flash` → a `proposal-flash` block at the top.
- Cancel popup `#cancel` when `page.canCancel`: title by status, notice "L'agent est prévenu par e-mail. Vous pourrez demander une autre visite.", textarea `name="reason"` `maxlength="500"`, submit "Annuler ma demande"/"Annuler la visite", form action `/visits/{id}/cancel`, "Garder ma visite" close link. Load `main.js`, `listing.js` (generic dialogs), `visit.js`.
- `visit.js`: focus `#cancel textarea` when `#cancel` is opened via `[data-modal-open]`; no fake state, no preventDefault.

- [ ] **Step 1: Write the failing `VisitTemplateTest`**

Use the `MatchTemplateTest` FakeController/Thymeleaf pattern with `VisitController.VIEW`.

```kotlin
@Test
fun requested() {
    val doc = render(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE).copy(note = "Fin de journée")))

    assertEquals("Demandée", doc.selectFirst(".visit-badge")?.text())
    assertEquals("Votre demande est envoyée", doc.selectFirst("[data-visit-state=REQUESTED] h2")?.text())
    assertEquals("Fin de journée", doc.selectFirst(".visit-facts__note")?.text())
    assertEquals("post", doc.selectFirst("#cancel form")?.attr("method"))
    assertEquals("/visits/9/cancel", doc.selectFirst("#cancel form")?.attr("action"))
    assertEquals("500", doc.selectFirst("#cancel textarea")?.attr("maxlength"))
}

@Test
fun `confirmed on site`() {
    val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.ON_SITE)))
    assertEquals("4827", doc.selectFirst(".visit-code__digits span")?.text())
    assertEquals("Rue Njo-Njo, portail bleu", doc.selectFirst(".visit-address")?.text())
    assertEquals("https://www.google.com/maps/dir/?api=1&destination=4.0247,9.6943", doc.selectFirst("a[href^=https://www.google.com/maps]")?.attr("href"))
    assertNotNull(doc.selectFirst("#cancel"))
}

@Test
fun `confirmed video`() {
    val doc = render(of(visit(VisitStatus.CONFIRMED, VisitType.VIDEO), customer = Customer(whatsappNumber = "+237699887766")))
    assertNull(doc.selectFirst(".visit-address"))
    assertTrue(doc.selectFirst("[data-visit-type=VIDEO]")!!.text().contains("+237699887766"))
}

@Test
fun `ended states have no cancel`() {
    listOf(VisitStatus.CHECKED_IN, VisitStatus.COMPLETED, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW).forEach { status ->
        assertNull(render(of(visit(status, VisitType.ON_SITE))).selectFirst("#cancel"), status.name)
    }
}

@Test
fun `cancelled by the agent shows the reason`() {
    val doc = render(of(visit(VisitStatus.CANCELLED, VisitType.ON_SITE).copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Le propriétaire n'est pas disponible.")))
    assertTrue(doc.text().contains("a annulé la visite"))
    assertTrue(doc.text().contains("Le propriétaire n'est pas disponible."))
}

@Test
fun `no feedback form and no prototype links`() {
    val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)))
    assertNull(doc.selectFirst("[name=interest]"))
    assertTrue(doc.select("[href$=.html], [src$=.html]").isEmpty())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL (template missing).

- [ ] **Step 3: Implement the template, CSS and JS**

Follow the template rules above. Reuse `listing.css`'s modal and `proposal.css`'s list/notice styles; put the visit-only rules in `visit.css`. The badge classes `visit-badge--info/success/neutral` come with it.

- [ ] **Step 4: Run the visit template test and the whole module**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitTemplateTest`
Expected: PASS.
Then: `cd modules/ndopify-web && mvn verify`
Expected: BUILD SUCCESS (ktlint, all tests, JaCoCo line 0.97 / class 0.80).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/visit.html \
  modules/ndopify-web/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-web/src/main/resources/public/assets/js/visit.js \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt
git commit -m "feat(web): visit page template"
```

---

### Task 9: Documentation

**Files:**
- Modify: `modules/ndopify-web/CLAUDE.md`
- Modify: `modules/ndopify-web/README.md`
- Include: `docs/superpowers/specs/2026-11-04-web-visit-request-design.md` (committed with this task)
- Include: `docs/superpowers/plans/2026-11-05-web-visit-request-and-visit-page.md` (this plan)

- [ ] **Step 1: Update `CLAUDE.md`**

In *Code Organization*:
- `ui/match/` now loads the customer and the open visit, posts `/match/{id}/visit`, and shows the "Demander une visite" popup, the open-visit block and "Voir ma visite".
- New `ui/visit/` (`VisitController`, `/visits/{id}?token=…`, all statuses, `#cancel` → `/visits/{id}/cancel`, `visit-link.html`).
- New clients `NdVisitClient`, `NdCustomerClient`; `VisitSlots`.
- Security list adds `POST /match/*/visit`, `GET /visits/*`, `POST /visits/*/cancel`.
- `listing.js`'s lead form is guarded; the match popup is not the listing popup.
- Static assets: `proposal.css` gains the visit rules, `visit.css` / `visit.js`.

- [ ] **Step 2: Update `README.md`**

- Listing page feature: unchanged (the anonymous lead stays out).
- Match proposal feature: add the request flow ("Demander une visite", open visit, "Voir ma visite").
- Add a **Visit page** feature: `/visits/{id}`, token link, every status, cancellation before check-in, no feedback.
- Client list: add `NdVisitClient` and `NdCustomerClient`.

- [ ] **Step 3: Run the module's full verification**

Run: `cd modules/ndopify-web && mvn verify`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-web/CLAUDE.md modules/ndopify-web/README.md \
  docs/superpowers/specs/2026-11-04-web-visit-request-design.md \
  docs/superpowers/plans/2026-11-05-web-visit-request-and-visit-page.md
git commit -m "docs(web): visit request and visit page"
```
