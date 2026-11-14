# Co-Brokerage Visits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the listing agent (B) a read-only backstage participant in a visit another agent (A) organizes on B's co-brokered listing: B is notified, sees the visit on his listing page, and hosts it — without ever learning A's customer.

**Architecture:** The visit stays A's (`VisitEntity.agentId = A`); B is resolved from `listing.agent` at read and send time. A new listing-agent-only endpoint returns a minimal `CoBrokerageVisitSummary`; a new `CoBrokerageVisitMailet` sends B confirmed/reminder/cancelled/no-show mails; `MandateClosedEvent` gains a synchronous visit listener that cancels the open visits of the ended mandate (search side and listing side). Partner Central adds a read-only visits block to the owner's listing page and fixes A's visit-page listing link.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), MJML + Mustache mail templates, mockito-kotlin, `kotlin.test`, `TestRestTemplate`/GreenMail integration tests, Thymeleaf + MockMvc for Partner Central.

**Spec:** `docs/superpowers/specs/2026-11-14-co-brokerage-visits-design.md`

## Global Constraints

- Work on branch `feat/npc-co-brokerage-visits`.
- No migration: `T_VISIT` keeps `agent_id = A`; B is `listing.agent`, resolved at read/send time.
- DTO change: add the two types to `ndopify-dto` (module version is already `0.0.89-SNAPSHOT`), set `<ndopify-dto.version>0.0.89-SNAPSHOT</ndopify-dto.version>` in the root `pom.xml`, and `mvn install` the DTO module before building any dependent module.
- Multi-tenant: every read and mail runs with `TenantContext`; `T_VISIT` is `@TenantId`-scoped.
- Anti-sautage is a hard invariant: B never receives A's customer's name, e-mail, WhatsApp, code, note, address, report, feedback, mandate, match or search criteria. A's customer never learns B's identity, agency or contact. A and B may exchange contacts.
- B is informed, never asked: no B acceptance state, no new visit status, no participant column, no new event, no new scheduler.
- The existing customer/agent visit e-mails and their voice stay unchanged. New B mails use their own templates.
- B is notified only on `CONFIRMED`, the 24 h reminder, a cancellation after `CONFIRMED`, and a customer-caused no-show (`CUSTOMER_ABSENT`, `CODE_REFUSED`). Never on `REQUESTED`, `EXPIRED`, `COMPLETED`, the agent's own no-show, or an own-inventory visit (`visit.agentId == listing.agent.id`).
- System cancellation uses `VisitParty.SYSTEM` with the neutral wording « La visite est annulée. » and never copies the mandate's cancellation reason.
- A mandate ending (`CLOSED` or `CANCELLED`) cancels its `REQUESTED`/`CONFIRMED` visits on the search side and the listing side; `CHECKED_IN` visits stay.
- Partner Central reads are secondary: a failed visits read leaves the block out; a failed agent read keeps the row with « un autre agent » and no link.
- The listing-page block is owner-only (`ListingView.coBrokerage == false`) and only for `PUBLISHED`, `RENTED` or `SOLD` listings. A co-broker lens never gets it.
- A's visit-page listing link becomes `/listings/{listingId}?match={matchId}`.
- French UI and e-mail wording, matching the existing visit pages.
- Coverage gates of `ndopify-server`: line `0.95`, class `0.90`. Endpoint/job tests need local MySQL.

## Review Focus

1. **A customer's name, e-mail, code, note or report reaching B** — any leak in the new e-mails, the endpoint JSON or the listing-page block is a Critical defect.
2. **An own-inventory visit** (`visit.agentId == listing.agent.id`) must never produce a B mail, a block row or an endpoint result.
3. **A mandate ending with a `CHECKED_IN` visit** must leave it alone; `REQUESTED`/`CONFIRMED` on both the search side and the listing side must be cancelled with `SYSTEM` and no mandate reason in any mail.
4. **A Partner Central failure** — a visits read failure must leave the rest of the listing page rendering; an agent read failure must keep the date/type row with a generic « un autre agent ».
5. **The listing page lenses** — a co-broker lens never reads or renders the block; a draft never reads it; the owner publishes/rents/sells and sees it.

---

### Task 1: DTO types for the listing-page read

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/CoBrokerageVisitSummary.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/SearchCoBrokerageVisitResponse.kt`
- Modify: `pom.xml` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces:
  - `CoBrokerageVisitSummary(id: Long = -1, listingId: Long = -1, agentId: Long = -1, status: VisitStatus = VisitStatus.UNKNOWN, type: VisitType = VisitType.UNKNOWN, scheduledAt: Date? = null, checkedInAt: Date? = null)`
  - `SearchCoBrokerageVisitResponse(visits: List<CoBrokerageVisitSummary> = emptyList())`

- [ ] **Step 1: Add the two DTO types**

`CoBrokerageVisitSummary.kt`:

```kotlin
package com.wutsi.ndopify.visit.dto

import java.util.Date

/**
 * A visit another agent organizes on the listing agent's property. It carries only what the listing agent may see:
 * never the customer, the note, the code, the address, the report or the feedback.
 */
data class CoBrokerageVisitSummary(
    val id: Long = -1,
    val listingId: Long = -1,
    /** A, the search agent; never A's customer. */
    val agentId: Long = -1,
    val status: VisitStatus = VisitStatus.UNKNOWN,
    val type: VisitType = VisitType.UNKNOWN,
    val scheduledAt: Date? = null,
    val checkedInAt: Date? = null,
)
```

`SearchCoBrokerageVisitResponse.kt`:

```kotlin
package com.wutsi.ndopify.visit.dto

data class SearchCoBrokerageVisitResponse(
    val visits: List<CoBrokerageVisitSummary> = emptyList(),
)
```

- [ ] **Step 2: Pin the working-tree DTO version**

In `pom.xml`, set:

```xml
<ndopify-dto.version>0.0.89-SNAPSHOT</ndopify-dto.version>
```

- [ ] **Step 3: Install the DTO locally**

Run:

```bash
mvn -pl modules/ndopify-dto install -DskipTests
```

Expected: `BUILD SUCCESS`, and `~/.m2/repository/com/wutsi/ndopify-dto/0.0.89-SNAPSHOT/` contains the jar.

- [ ] **Step 4: Commit**

```bash
git add pom.xml \
    modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/CoBrokerageVisitSummary.kt \
    modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/SearchCoBrokerageVisitResponse.kt
git commit -m "feat(dto): expose a co-brokerage visit summary"
```

---

### Task 2: Server — the listing-agent read

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mapper/VisitMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/VisitEndpoints.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/visit/CoBrokerageVisitEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CoBrokerageVisitEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt`

**Interfaces:**
- Consumes: `CoBrokerageVisitSummary` (Task 1), `VisitAccessService`, `ListingService`, `VisitMapper`.
- Produces:
  - `VisitRepository.findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(listingId: Long, agentId: Long, statuses: Collection<VisitStatus>): List<VisitEntity>`
  - `VisitAccessService.ensureIsListingAgent(listing: ListingEntity)`
  - `VisitService.findCoBrokerageOnListing(listing: ListingEntity): List<VisitEntity>`
  - `VisitMapper.toCoBrokerageSummary(visit: VisitEntity): CoBrokerageVisitSummary`
  - `GET /v1/visits/co-brokerage?listing-id={id}` → `SearchCoBrokerageVisitResponse`

- [ ] **Step 1: Write the failing endpoint test and its fixture**

`CoBrokerageVisitEndpoint.sql` (loaded after `clean.sql` and `VisitFixtures.sql`):

```sql
-- The co-brokerage match: agent 30's search (mandate 4) on agent 20's listing 101
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at, co_brokerage
) VALUES
    (50, 1, 4, 101, 30, 'ACCEPTED', 0.9, 150000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
     '2026-10-01 10:00:00', '2026-10-01 10:00:00', TRUE);

-- 1, 2: another agent's visits on listing 101 (CONFIRMED, CHECKED_IN: shown)
-- 3: REQUESTED (hidden)  4: COMPLETED (hidden)  5: own visit (hidden)
-- 6: another listing (hidden)  9: another tenant
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, checked_in_at, code, created_at
) VALUES
    (1, 1, 50, 4, 101, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-02 10:00:00', NULL, '1234', '2026-10-01 10:00:00'),
    (2, 1, 50, 4, 101, 30, 10, 'CHECKED_IN', 'VIDEO',
     '2026-10-01 10:00:00', '2026-12-01 15:00:00', '2026-12-01 15:05:00', '5678', '2026-10-01 10:00:00'),
    (3, 1, 50, 4, 101, 30, 10, 'REQUESTED', 'ON_SITE',
     '2026-10-01 10:00:00', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 50, 4, 101, 30, 10, 'COMPLETED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-11-01 10:00:00', '2026-11-01 10:05:00', '9012', '2026-10-01 10:00:00'),
    (5, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-03 10:00:00', NULL, '3456', '2026-10-01 10:00:00'),
    (6, 1, 50, 4, 102, 30, 10, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-04 10:00:00', NULL, '7890', '2026-10-01 10:00:00'),
    (9, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-12-05 10:00:00', NULL, '1111', '2026-10-01 10:00:00');
```

`CoBrokerageVisitEndpointTest` extends `AuthorizationAwareEndpointIntegrationTest`, annotated:

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
```

Tests (default caller user 11 = agent 20, listing 101's owner):

```kotlin
@Test
fun `the listing agent sees the other agent's confirmed and checked-in visits`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", SearchCoBrokerageVisitResponse::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(listOf(2L, 1L), response.body!!.visits.map { it.id }) // scheduledAt ascending
    assertEquals(listOf(30L, 30L), response.body!!.visits.map { it.agentId })
    assertEquals(VisitStatus.CHECKED_IN, response.body!!.visits.first().status)
    assertEquals(VisitType.VIDEO, response.body!!.visits.first().type)
    assertEquals(VisitStatus.CONFIRMED, response.body!!.visits.last().status)
}

@Test
fun `the answer carries no customer, note, code, address, report or feedback`() {
    val body = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", String::class.java).body!!

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
fun `an empty listing answers an empty list`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=104", SearchCoBrokerageVisitResponse::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    assertTrue(response.body!!.visits.isEmpty())
}

@Test
fun `another agent is forbidden`() {
    userId = 12L // agent 30, not listing 101's agent

    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", ErrorResponse::class.java)

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
}

@Test
fun `the customer is forbidden`() {
    userId = 13L

    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", ErrorResponse::class.java)

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
}

@Test
fun `a missing listing is 404`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=999", ErrorResponse::class.java)

    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
}

@Test
fun `a non-positive listing id is 400`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=0", ErrorResponse::class.java)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
}

@Test
fun `a missing listing id is 400`() {
    val response = rest.getForEntity("/v1/visits/co-brokerage", ErrorResponse::class.java)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
}

@Test
fun anonymous() {
    anonymousUser = true

    assertEquals(
        HttpStatus.UNAUTHORIZED,
        rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", Any::class.java).statusCode,
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitEndpointTest
```

Expected: FAIL — 404 on the unknown route (or compilation failure once the DTO is present).

- [ ] **Step 3: Add the repository query**

In `VisitRepository`:

```kotlin
// Another agent's visits on a listing, soonest first: the co-brokerage block
fun findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
    listingId: Long,
    agentId: Long,
    statuses: Collection<VisitStatus>,
): List<VisitEntity>
```

- [ ] **Step 4: Add the access check**

In `VisitAccessService` (import `com.wutsi.ndopify.listing.server.domain.ListingEntity`):

```kotlin
/** The listing's agent only: the co-brokerage block. No admin bypass, no customer. */
fun ensureIsListingAgent(listing: ListingEntity) {
    val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
    if (partyId == null || agentService.findByPartyIdOrNull(partyId)?.id != listing.agent.id) {
        throw accessDenied()
    }
}
```

- [ ] **Step 5: Add the service read**

In `VisitService`:

```kotlin
/** The CONFIRMED/CHECKED_IN visits another agent organizes on [listing], soonest first. */
fun findCoBrokerageOnListing(listing: ListingEntity): List<VisitEntity> =
    dao.findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
        listing.id!!,
        listing.agent.id,
        setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
    )
```

- [ ] **Step 6: Add the mapper**

In `VisitMapper`:

```kotlin
fun toCoBrokerageSummary(visit: VisitEntity): CoBrokerageVisitSummary =
    CoBrokerageVisitSummary(
        id = visit.id ?: -1,
        listingId = visit.listingId,
        agentId = visit.agentId,
        status = visit.status,
        type = visit.type,
        scheduledAt = visit.scheduledAt,
        checkedInAt = visit.checkedInAt,
    )
```

- [ ] **Step 7: Add the endpoint**

In `VisitEndpoints`, before the `GET /{id}` mapping (the `\\d+` pattern keeps `/co-brokerage` unambiguous):

```kotlin
// The listing agent sees the visits another agent organizes on its property, never their customer
@GetMapping("/co-brokerage")
fun coBrokerage(@RequestParam(name = "listing-id") listingId: Long): SearchCoBrokerageVisitResponse {
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
    return SearchCoBrokerageVisitResponse(
        visits = service.findCoBrokerageOnListing(listing).map { mapper.toCoBrokerageSummary(it) },
    )
}
```

Add the imports for `SearchCoBrokerageVisitResponse`, `RequestParam`, `BadRequestException`, `Error`, `ErrorCode`, `Parameter`, `ParameterType`.

- [ ] **Step 8: Run the endpoint tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitEndpointTest
```

Expected: PASS.

- [ ] **Step 9: Add the repository query test**

In `VisitRepositoryTest`, add method-level `@Sql` and:

```kotlin
@Test
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
fun `co-brokerage visits excludes own visits and closed statuses`() {
    val listing = listingService.findById(101)

    assertEquals(
        listOf(2L, 1L),
        dao.findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
            listing.id!!,
            listing.agent.id,
            setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
        ).map { it.id },
    )
}
```

Add `@Autowired lateinit var listingService: ListingService` and the needed imports. Run:

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitRepositoryTest
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitAccessService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mapper/VisitMapper.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/VisitEndpoints.kt \
    modules/ndopify-server/src/test/resources/db/test/visit/CoBrokerageVisitEndpoint.sql \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CoBrokerageVisitEndpointTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt
git commit -m "feat(visit): let the listing agent read co-brokerage visits"
```

---

### Task 3: Server — notify the listing agent

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_confirmed.mjml`
- Create: `modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_reminder.mjml`
- Create: `modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_cancelled.mjml`
- Create: `modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_no_show.mjml`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailetTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitTemplateTest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Consumes: `VisitEntity`, `ListingEntity.agent`, `TenantService`, `MailService`, `MailBodyResolver`, `ListingImageService`, `AgentMailContext`, `TenantFormatter`.
- Produces:
  - `CoBrokerageVisitMailet(tenantService, mailService, mailBodyResolver, imageService)`
  - `sendConfirmed(visit, listing, agent)`, `sendReminder(...)`, `sendCancelled(...)`, `sendNoShow(...)` where `agent` is A
  - constants `TEMPLATE_CONFIRMED`, `TEMPLATE_REMINDER`, `TEMPLATE_CANCELLED`, `TEMPLATE_NO_SHOW`, `SUBJECT_CONFIRMED`, `SUBJECT_REMINDER`, `SUBJECT_CANCELLED`, `SUBJECT_NO_SHOW`, `LABEL_BY_AGENT`, `LABEL_BY_CUSTOMER`, `LABEL_BY_SYSTEM`, `LABEL_CUSTOMER_ABSENT`, `LABEL_CODE_REFUSED`
  - `VisitService` constructor gains `coBrokerageVisitMailet: CoBrokerageVisitMailet` after `expiredMailet`.

- [ ] **Step 1: Write the failing mailet tests**

`CoBrokerageVisitMailetTest` fixture: tenant 1 `Ndopify`, `partnerCentralUrl = https://partner.ndopify.com`, `publicPortalUrl = https://www.ndopify.com`, date `dd/MM/yyyy`, time `HH:mm`, time zone `Africa/Douala`; listing agent 30 `John Doe` (`john.doe@gmail.com`, WhatsApp `+237677889900`); search agent 20 `Kim Possible` (`kim.possible@gmail.com`, WhatsApp `+237699000001`); search customer 10 `Secret Client` (`secret@example.com`, code `1234`); listing 101 `Villa Bastos`, mandate 11; visit `CONFIRMED`, `scheduledAt = 2026-12-02 10:00 Douala`, `note = "Client pressé"`; cover `https://cdn.ndopify.com/7.jpg`.

```kotlin
@Test
fun `confirmed - the listing agent is the recipient, A is the sender`() {
    mailet.sendConfirmed(visit, listing, searchAgent)

    val email = email("john.doe@gmail.com")
    assertEquals("John Doe", email.recipient.displayName)
    assertEquals("Kim Possible", email.sender?.displayName)
    assertEquals("Ndopify: visite de votre bien confirmée", email.subject)

    val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
    assertEquals("Villa Bastos", context["listing_title"])
    assertEquals("02/12/2026", context["visit_date"])
    assertEquals("10:00", context["visit_time"])
    assertEquals("Sur place", context["type_label"])
    assertEquals(true, context["on_site"])
    assertEquals("Kim Possible", context["agent_full_name"])
    assertEquals("https://partner.ndopify.com/agent?id=20", context["agent_profile_url"])
    assertEquals("https://wa.me/237699000001", context["agent_whatsapp_url"])
    assertEquals("https://partner.ndopify.com/listings/101#visits-title", context["listing_url"])
    assertEquals("https://cdn.ndopify.com/7.jpg", context["image_url"])
    assertFalse(context.toString().contains("Secret Client"))
    assertFalse(context.toString().contains("secret@example.com"))
    assertFalse(context.toString().contains("1234"))
    assertFalse(context.toString().contains("Client pressé"))
}

@Test
fun `confirmed - VIDEO tells B the customer is remote`() {
    mailet.sendConfirmed(visit.copy(type = VisitType.VIDEO), listing, searchAgent)

    val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
    assertEquals("Visite vidéo", context["type_label"])
    assertEquals(false, context["on_site"])
}

@Test
fun `reminder - the same facts, the reminder template`() {
    mailet.sendReminder(visit, listing, searchAgent)

    assertEquals("Ndopify: rappel de la visite de votre bien", email("john.doe@gmail.com").subject)
    assertEquals("Villa Bastos", context(CoBrokerageVisitMailet.TEMPLATE_REMINDER)["listing_title"])
}

@Test
fun `cancelled - the actor's neutral label, never the mandate reason`() {
    mailet.sendCancelled(
        visit.copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Mandat clôturé"),
        listing,
        searchAgent,
    )

    assertEquals("Ndopify: la visite de votre bien est annulée", email("john.doe@gmail.com").subject)
    val context = context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)
    assertEquals("J'ai annulé la visite.", context["cancelled_by_label"])
    assertNull(context["cancellation_reason"])
}

@Test
fun `cancelled - the customer and the system labels`() {
    mailet.sendCancelled(visit.copy(cancelledBy = VisitParty.CUSTOMER), listing, searchAgent)
    assertEquals("Le client a annulé la visite.", context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)["cancelled_by_label"])

    mailet.sendCancelled(visit.copy(cancelledBy = VisitParty.SYSTEM), listing, searchAgent)
    assertEquals("La visite est annulée.", context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)["cancelled_by_label"])
}

@Test
fun `no-show - both customer-caused labels`() {
    mailet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CUSTOMER_ABSENT), listing, searchAgent)
    assertEquals("Le client ne s'est pas présenté à la visite.", context(CoBrokerageVisitMailet.TEMPLATE_NO_SHOW)["reason_label"])

    mailet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CODE_REFUSED), listing, searchAgent)
    assertEquals("Le client a refusé de donner le code de visite.", context(CoBrokerageVisitMailet.TEMPLATE_NO_SHOW)["reason_label"])
}

@Test
fun `no WhatsApp - no link, the name stays`() {
    mailet.sendConfirmed(visit, listing, searchAgent.copy(whatsappNumber = null))

    val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
    assertNull(context["agent_whatsapp_url"])
    assertEquals("Kim Possible", context["agent_full_name"])
}
```

Helpers:

```kotlin
private fun email(recipient: String): Email {
    val captor = argumentCaptor<Email>()
    verify(mailService, atLeastOnce()).send(captor.capture())
    return captor.allValues.single { it.recipient.email == recipient }
}

private fun context(template: String): Map<String, Any?> {
    val captor = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver, atLeastOnce()).resolve(eq(template), captor.capture())
    return captor.lastValue
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitMailetTest
```

Expected: compilation fails with unresolved reference `CoBrokerageVisitMailet`.

- [ ] **Step 3: Implement `CoBrokerageVisitMailet`**

Constructor: `tenantService`, `mailService`, `mailBodyResolver`, `imageService`. A private `send(...)` builds the common context and sends one `Email` to `listing.agent.party`, with `sender = AgentMailContext.from(agent)`, `subject` resolved with the tenant name, and `body = mailBodyResolver.resolve(template, context + AgentMailContext.of(agent, formatter))`.

Common context:

```kotlin
mapOf(
    "tenant_logo_url" to tenant.logoUrl,
    "tenant_name" to tenant.name,
    "recipient_name" to listing.agent.party.fullName(),
    "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
    "image_url" to imageService.cover(listing)?.url?.ifBlank { null },
    "visit_date" to visit.scheduledAt?.let { formatter.date(it) },
    "visit_time" to visit.scheduledAt?.let { formatter.time(it) },
    "type_label" to if (visit.type == VisitType.ON_SITE) "Sur place" else "Visite vidéo",
    "on_site" to (visit.type == VisitType.ON_SITE),
    "agent_profile_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/agent?id=${agent.id}" },
    "listing_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/listings/${listing.id}#visits-title" },
)
```

Per kind:
- `sendConfirmed` adds nothing; subject `SUBJECT_CONFIRMED`.
- `sendReminder` same; subject `SUBJECT_REMINDER`.
- `sendCancelled` adds `cancelled_by_label` (`AGENT` → `LABEL_BY_AGENT`, `CUSTOMER` → `LABEL_BY_CUSTOMER`, else `LABEL_BY_SYSTEM`) and `cancellation_reason = null`; subject `SUBJECT_CANCELLED`.
- `sendNoShow` adds `reason_label` (`CODE_REFUSED` → `LABEL_CODE_REFUSED`, else `LABEL_CUSTOMER_ABSENT`); subject `SUBJECT_NO_SHOW`.

Constants:

```kotlin
const val SUBJECT_CONFIRMED = "{{tenant_name}}: visite de votre bien confirmée"
const val SUBJECT_REMINDER = "{{tenant_name}}: rappel de la visite de votre bien"
const val SUBJECT_CANCELLED = "{{tenant_name}}: la visite de votre bien est annulée"
const val SUBJECT_NO_SHOW = "{{tenant_name}}: la visite de votre bien n'a pas eu lieu"
const val TEMPLATE_CONFIRMED = "/mail/visit/co_brokerage_confirmed.mjml"
const val TEMPLATE_REMINDER = "/mail/visit/co_brokerage_reminder.mjml"
const val TEMPLATE_CANCELLED = "/mail/visit/co_brokerage_cancelled.mjml"
const val TEMPLATE_NO_SHOW = "/mail/visit/co_brokerage_no_show.mjml"
const val LABEL_BY_AGENT = "J'ai annulé la visite."
const val LABEL_BY_CUSTOMER = "Le client a annulé la visite."
const val LABEL_BY_SYSTEM = "La visite est annulée."
const val LABEL_CUSTOMER_ABSENT = "Le client ne s'est pas présenté à la visite."
const val LABEL_CODE_REFUSED = "Le client a refusé de donner le code de visite."
```

- [ ] **Step 4: Write the four templates**

Model them on `confirmed_agent.mjml`, `reminder.mjml`, `cancelled.mjml` and `no_show.mjml`, with the tenant logo/divider/sign-off and the shared agent footer (`{{> fragments/agent-footer}}` or the existing include used by those templates). Required copy:

- **confirmed** — « J'ai organisé une visite de votre bien le {{visit_date}} à {{visit_time}}. Je vous y retrouve avec mon client. » For `on_site == false`: « Je serai sur place pour vous ouvrir ; le client suivra en visio. »
- **reminder** — « Rappel : la visite de votre bien a lieu le {{visit_date}} à {{visit_time}}. » Same video variant.
- **cancelled** — `{{cancelled_by_label}}` and the date/time; never `cancellation_reason`.
- **no-show** — `{{reason_label}}` and the date/time.

Every template shows the listing title/image, the type (`{{type_label}}`), A's name, A's profile link when present, A's WhatsApp link when present, and a « Voir l'annonce » button when `listing_url` is present. Never render a customer field: the context has none.

- [ ] **Step 5: Write the template tests**

`CoBrokerageVisitTemplateTest` mirrors `VisitConfirmedTemplateTest` (real `MailBodyResolver`, `MjmlRenderer`, `DefaultMustacheFactory`, `MailFilterNull`). Render each template with a context containing `"recipient_name" to "John Doe"`, `"agent_full_name" to "Kim Possible"`, `"listing_title" to "Villa Bastos"`, `"visit_date" to "02/12/2026"`, `"visit_time" to "10:00"`, `"type_label" to "Sur place"`, `"on_site" to true`, `"cancelled_by_label" to "J'ai annulé la visite."`, `"reason_label" to "Le client ne s'est pas présenté à la visite."`, `"agent_profile_url" to "https://partner.ndopify.com/agent?id=20"`, `"agent_whatsapp_url" to "https://wa.me/237699000001"`, `"listing_url" to "https://partner.ndopify.com/listings/101#visits-title"`.

Assertions per template: `html.contains("Villa Bastos")`, `html.contains("Kim Possible")`, `html.contains("https://wa.me/237699000001")`, `html.contains("https://partner.ndopify.com/listings/101#visits-title")`, and the absence of `"Secret Client"`, `"secret@example.com"`, `"1234"`, `"Client pressé"`. Add the video variant (`on_site = false`) asserting « suivra en visio » / « Visite vidéo ».

- [ ] **Step 6: Run the mailet and template tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitMailetTest,CoBrokerageVisitTemplateTest
```

Expected: PASS.

- [ ] **Step 7: Write the failing `VisitService` condition tests**

In `VisitServiceTest`, add `private val coBrokerageVisitMailet = mock<CoBrokerageVisitMailet>()` and pass it to the constructor after `expiredMailet`. Add a helper listing fixture with an owner agent:

```kotlin
private val owner = AgentEntity(id = 30L, party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe"))
private val coBrokerageListing = ListingEntity(id = 101L, agent = owner)
```

In `setUp`, stub `doReturn(coBrokerageListing).whenever(listingService).findById(101L)` and `doReturn(visit).whenever(dao).findById(1L)`.

Tests:

```kotlin
@Test
fun `confirmed - the listing agent is mailed when the visit belongs to another agent`() {
    doReturn(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0)))
        .whenever(dao).findById(1L)

    service.sendConfirmedEmails(1L)

    verify(coBrokerageVisitMailet).sendConfirmed(any(), eq(coBrokerageListing), eq(agent20))
}

@Test
fun `confirmed - an own-inventory visit does not mail the listing agent`() {
    doReturn(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0)))
        .whenever(dao).findById(1L)
    doReturn(ListingEntity(id = 101L, agent = agent20)).whenever(listingService).findById(101L)

    service.sendConfirmedEmails(1L)

    verify(coBrokerageVisitMailet, never()).sendConfirmed(any(), any(), any())
}

@Test
fun `reminder - the listing agent is mailed when the visit belongs to another agent`() {
    doReturn(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0)))
        .whenever(dao).findById(1L)

    service.sendReminderEmails(1L)

    verify(coBrokerageVisitMailet).sendReminder(any(), eq(coBrokerageListing), eq(agent20))
}

@Test
fun `cancelled after confirmed - the listing agent is mailed`() {
    doReturn(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = VisitParty.AGENT, scheduledAt = date(2026, 12, 2, 10, 0)))
        .whenever(dao).findById(1L)

    service.sendCancelledEmail(1L)

    verify(coBrokerageVisitMailet).sendCancelled(any(), eq(coBrokerageListing), eq(agent20))
}

@Test
fun `cancelled while requested - the listing agent was never told`() {
    doReturn(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = VisitParty.CUSTOMER, scheduledAt = null))
        .whenever(dao).findById(1L)

    service.sendCancelledEmail(1L)

    verify(coBrokerageVisitMailet, never()).sendCancelled(any(), any(), any())
}

@Test
fun `customer no-show - the listing agent is mailed`() {
    doReturn(visit.copy(status = VisitStatus.NO_SHOW, noShowParty = VisitParty.CUSTOMER, noShowReason = VisitNoShowReason.CUSTOMER_ABSENT))
        .whenever(dao).findById(1L)

    service.sendNoShowEmails(1L)

    verify(coBrokerageVisitMailet).sendNoShow(any(), eq(coBrokerageListing), eq(agent20))
}

@Test
fun `agent no-show, completion and expiry - no listing-agent mail`() {
    listOf(
        visit.copy(status = VisitStatus.NO_SHOW, noShowParty = VisitParty.AGENT),
        visit.copy(status = VisitStatus.COMPLETED),
        visit.copy(status = VisitStatus.EXPIRED),
    ).forEach { current ->
        doReturn(current).whenever(dao).findById(1L)
        service.sendNoShowEmails(1L)
        service.sendCompletedEmail(1L)
        service.sendExpiredEmails(1L)
    }

    verify(coBrokerageVisitMailet, never()).sendNoShow(any(), any(), any())
}
```

`agent20` is `AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))`; `agentService.findById(20L)` and `customerService.findById(10L)` must be stubbed in `setUp` for the existing mailet mocks.

- [ ] **Step 8: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest
```

Expected: compilation fails because `VisitService` has no `coBrokerageVisitMailet` and no B calls.

- [ ] **Step 9: Wire the mailet into `VisitService`**

Add the constructor parameter. In `sendConfirmedEmails`, `sendReminderEmails`, `sendCancelledEmail` and `sendNoShowEmails`, load the listing into a local `val listing = listingService.findById(visit.listingId)` and pass that same value to both the existing mailet and the new one — do not call `listingService.findById` twice per method. Reuse the `agent` each method already loaded; do not reload it.

```kotlin
val coBrokerage = visit.agentId != listing.agent.id

// sendConfirmedEmails / sendReminderEmails: after the existing mailet call
if (coBrokerage) {
    coBrokerageVisitMailet.sendConfirmed(visit, listing, agent)
}

// sendCancelledEmail: after the existing cancelledMailet.send
if (coBrokerage && visit.scheduledAt != null) {
    coBrokerageVisitMailet.sendCancelled(visit, listing, agent)
}

// sendNoShowEmails: after the existing noShowMailet.send
if (coBrokerage && visit.noShowParty == VisitParty.CUSTOMER) {
    coBrokerageVisitMailet.sendNoShow(visit, listing, agent)
}
```

No B call in `sendCompletedEmail`, `sendExpiredEmails` or `sendRequestedEmail`.

- [ ] **Step 10: Run the service tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest,CoBrokerageVisitMailetTest,CoBrokerageVisitTemplateTest
```

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt \
    modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_confirmed.mjml \
    modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_reminder.mjml \
    modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_cancelled.mjml \
    modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_no_show.mjml \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailetTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitTemplateTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): notify the listing agent of a co-brokerage visit"
```

---

### Task 4: Server — cancel open visits when a mandate ends

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListener.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/visit/CancelMandateVisitsEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CancelMandateVisitsEndpointTest.kt`

**Interfaces:**
- Consumes: `MandateClosedEvent`, `VisitCancelledMailet` (Task 3), `VisitService` (Task 3).
- Produces:
  - `VisitRepository.findOpenByMandateOrListingMandate(mandateId: Long, statuses: Collection<VisitStatus>): List<VisitEntity>`
  - `VisitService.cancelForEndedMandate(mandateId: Long): Int`
  - `VisitMandateEventListener(service: VisitService)` with `@EventListener fun onMandateClosed(event: MandateClosedEvent)`
  - `VisitCancelledMailet` constant `LABEL_BY_SYSTEM` and a `SYSTEM` branch.

- [ ] **Step 1: Write the failing system-cancellation mailet test**

In `VisitCancelledMailetTest`:

```kotlin
@Test
fun `the system cancelled - both sides get the neutral wording, never the mandate reason`() {
    mailet.send(
        visit.copy(cancelledBy = VisitParty.SYSTEM, cancellationReason = "Mandat clôturé"),
        customer,
        agent,
        listing,
    )

    val emails = argumentCaptor<Email>()
    verify(mailService, times(2)).send(emails.capture())
    assertEquals(
        setOf("ray.sponsible@gmail.com", "kim.possible@gmail.com"),
        emails.allValues.map { it.recipient.email }.toSet(),
    )

    val contexts = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver, times(2)).resolve(eq(VisitCancelledMailet.TEMPLATE), contexts.capture())
    contexts.allValues.forEach { context ->
        assertEquals("La visite est annulée.", context["cancelled_by_label"])
        assertNull(context["cancellation_reason"])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitCancelledMailetTest
```

Expected: FAIL — only one e-mail is sent and the label is « J'ai annulé la visite. ».

- [ ] **Step 3: Add the `SYSTEM` branch to `VisitCancelledMailet`**

Replace the single-recipient logic with a `when (visit.cancelledBy)`:
- `CUSTOMER`: one mail to the agent, no sender, `LABEL_BY_CUSTOMER`, reason shown (existing behaviour).
- `AGENT`: one mail to the customer, sender `AgentMailContext.from(agent)`, `LABEL_BY_AGENT`, reason shown (existing behaviour).
- `SYSTEM`: two mails, one to the customer (sender A, customer portal URL with token if available) and one to the agent (no sender, partner-central visit URL). Both use `LABEL_BY_SYSTEM` and force `cancellation_reason = null`.
- `null`: no mail.

Add `const val LABEL_BY_SYSTEM = "La visite est annulée."`. Keep `SUBJECT` unchanged.

- [ ] **Step 4: Run the mailet test to verify it passes**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitCancelledMailetTest
```

Expected: PASS.

- [ ] **Step 5: Write the failing repository and service tests**

In `VisitRepositoryTest`, with the Task 2 fixture (or a dedicated one) loaded:

```kotlin
@Test
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
fun `open visits by mandate or listing mandate`() {
    // Search side: mandate 4 has visits 1..4 and 6 (CONFIRMED, CHECKED_IN, REQUESTED, COMPLETED, CONFIRMED)
    assertEquals(
        setOf(1L, 3L, 6L),
        dao.findOpenByMandateOrListingMandate(
            4,
            setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
        ).map { it.id }.toSet(),
    )

    // Listing side: listing mandate 11 owns listing 101, which holds visits 1..5
    assertEquals(
        setOf(1L, 3L, 5L),
        dao.findOpenByMandateOrListingMandate(
            11,
            setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
        ).map { it.id }.toSet(),
    )
}
```

In `VisitServiceTest`:

```kotlin
@Test
fun `an ended mandate cancels the open visits the query returns`() {
    val requested = visit.copy(id = 2L, status = VisitStatus.REQUESTED, scheduledAt = null)
    val confirmed = visit.copy(id = 1L, status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))
    // The repository query already excludes CHECKED_IN/COMPLETED; VisitRepositoryTest pins that exclusion.
    doReturn(listOf(requested, confirmed)).whenever(dao)
        .findOpenByMandateOrListingMandate(eq(1L), any())

    assertEquals(2, service.cancelForEndedMandate(1L))

    verify(dao, times(2)).save(any())
    verify(eventPublisher, times(2)).publishEvent(any<VisitCancelledEvent>())
}
```

Capture the saved entities and assert `status == CANCELLED`, `cancelledBy == SYSTEM`, `cancelledAt != null`, `cancellationReason == null`.

- [ ] **Step 6: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitRepositoryTest,VisitServiceTest
```

Expected: compilation fails because the query and `cancelForEndedMandate` do not exist.

- [ ] **Step 7: Add the repository query**

```kotlin
// The REQUESTED/CONFIRMED visits the ended mandate makes moot: its own (search side) and its listing's
@Query(
    "SELECT v FROM VisitEntity v WHERE v.status IN :statuses AND (" +
        "v.mandateId = :mandateId OR v.listingId IN (" +
        "SELECT l.id FROM ListingEntity l WHERE l.mandateId = :mandateId))"
)
fun findOpenByMandateOrListingMandate(
    mandateId: Long,
    statuses: Collection<VisitStatus>,
): List<VisitEntity>
```

- [ ] **Step 8: Implement `cancelForEndedMandate`**

In `VisitService`:

```kotlin
/**
 * The mandate ended (CLOSED or CANCELLED): its open visits and those on its listing become CANCELLED by the system,
 * with no reason copied from the mandate. A CHECKED_IN visit stays on; A closes it. Synchronous, inside the mandate's
 * transaction. Returns how many were cancelled.
 */
@Transactional
fun cancelForEndedMandate(mandateId: Long): Int {
    val now = Date(clock.millis())
    val visits = dao.findOpenByMandateOrListingMandate(
        mandateId,
        setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
    )
    return visits.count { visit ->
        val saved = dao.save(
            visit.copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.SYSTEM,
                cancelledAt = now,
                cancellationReason = null,
            ),
        )
        eventPublisher.publishEvent(VisitCancelledEvent(visitId = saved.id!!, tenantId = saved.tenantId!!))
        true
    }
}
```

- [ ] **Step 9: Add the listener**

`VisitMandateEventListener.kt`:

```kotlin
package com.wutsi.ndopify.visit.server.event

import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.visit.server.service.VisitService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class VisitMandateEventListener(
    private val service: VisitService,
) {
    /**
     * Synchronous, inside the mandate's closing (or cancelling) transaction, like `ListingMandateEventListener`: a
     * mandate never commits with an open visit it just ended. `CHECKED_IN` visits are left alone.
     */
    @EventListener
    fun onMandateClosed(event: MandateClosedEvent) {
        service.cancelForEndedMandate(event.mandateId)
    }
}
```

- [ ] **Step 10: Write the failing mandate-end integration test and fixture**

`CancelMandateVisitsEndpoint.sql`:

```sql
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, time_zone, date_format, time_format, logo_url, partner_central_url, public_portal_url)
VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'Africa/Douala', 'dd/MM/yyyy', 'HH:mm',
     'https://cdn.ndopify.com/logo.png', 'https://partner.ndopify.com', 'https://www.ndopify.com');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (400, 1, 'owner@gmail.com', 'Secret', 'Owner');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 400);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id, whatsapp_number) VALUES
    (20, 1, 200, 2370201, '+237699000001'),
    (30, 1, 300, 2370201, '+237699000002');

-- 1: A's search (agent 20)   2: another search (agent 20)   11: B's listing mandate (agent 30)
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, property_type, rooms, price, price_periodicity_type, currency_code)
VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id, property_type, rooms, price, currency_code, price_periodicity_type, title, landmark, access_directions, latitude, longitude)
VALUES
    (101, 1, 11, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, NULL, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
     'Villa Bastos', 'Rue 1.234', 'Derrière la pharmacie', 4.051100, 9.767900);

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, created_at, modified_at, co_brokerage)
VALUES
    (50, 1, 1, 101, 20, 'ACCEPTED', 0.9, 150000, '2026-10-01 10:00:00', '2026-10-01 10:00:00', TRUE);

-- Visit 1: CONFIRMED via A's search   Visit 2: CHECKED_IN   Visit 3: REQUESTED
-- Visit 4: CONFIRMED via search 2, on the same listing (the listing side)
INSERT INTO T_VISIT(id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type, requested_at, scheduled_at, checked_in_at, code, created_at)
VALUES
    (1, 1, 50, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-02 10:00:00', NULL, '1234', '2026-10-01 10:00:00'),
    (2, 1, 50, 1, 101, 20, 10, 'CHECKED_IN', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-01 10:00:00', '2026-12-01 10:05:00', '5678', '2026-10-01 10:00:00'),
    (3, 1, 50, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2026-10-01 10:00:00', NULL, NULL, NULL, '2026-10-01 10:00:00'),
    (4, 1, 50, 2, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2026-10-01 10:00:00', '2026-12-03 10:00:00', NULL, '9012', '2026-10-01 10:00:00');
```

`CancelMandateVisitsEndpointTest` extends `AuthorizationAwareEndpointIntegrationTest`, overrides `roles = arrayOf(RoleCode.ADMIN)`, `@Sql(clean.sql, CancelMandateVisitsEndpoint.sql)`, and autowires `JdbcTemplate`.

```kotlin
@Test
fun `cancelling a search mandate cancels its open visits, keeps the checked-in one, and never copies its reason`() {
    rest.postForEntity(
        "/v1/mandates/1/cancel",
        CancelMandateRequest(
            cancellationCode = MandateCancellationCode.FOUND_INDEPENDENTLY,
            cancellationReason = "J'ai trouvé un appartement par un ami",
        ),
        Any::class.java,
    )

    assertEquals("CANCELLED", visit(1)["status"])
    assertEquals("SYSTEM", visit(1)["cancelled_by"])
    assertNotNull(visit(1)["cancelled_at"])
    assertNull(visit(1)["cancellation_reason"])
    assertEquals("CANCELLED", visit(3)["status"])
    assertEquals("CHECKED_IN", visit(2)["status"])

    assertTrue(smtp.waitForIncomingEmail(5000, 5), "customer + A for both cancelled visits, plus B for visit 1")
    val bodies = smtp.receivedMessages.toList().joinToString("\n") { it.content.toString() }
    assertTrue(bodies.contains("La visite est annulée."))
    assertFalse(bodies.contains("J'ai trouvé un appartement par un ami"))
    assertTrue(bodies.contains("Ndopify: la visite de votre bien est annulée"))
}

@Test
fun `cancelling a listing mandate cancels the visits on its listing`() {
    rest.postForEntity("/v1/mandates/11/cancel", CancelMandateRequest(cancellationCode = MandateCancellationCode.OTHER), Any::class.java)

    assertEquals("CANCELLED", visit(1)["status"])
    assertEquals("CANCELLED", visit(3)["status"])
    assertEquals("CANCELLED", visit(4)["status"])
    assertEquals("CHECKED_IN", visit(2)["status"])
}

private fun visit(id: Long): Map<String, Any?> =
    jdbc.queryForMap("SELECT status, cancelled_by, cancelled_at, cancellation_reason FROM T_VISIT WHERE id=?", id)
```

- [ ] **Step 11: Run the mandate tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=VisitRepositoryTest,VisitServiceTest,VisitCancelledMailetTest,CancelMandateVisitsEndpointTest
```

Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListener.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt \
    modules/ndopify-server/src/test/resources/db/test/visit/CancelMandateVisitsEndpoint.sql \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CancelMandateVisitsEndpointTest.kt
git commit -m "feat(visit): cancel open visits when their mandate ends"
```

---

### Task 5: Partner Central — read and model the visits block

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: `CoBrokerageVisitSummary`, `SearchCoBrokerageVisitResponse` (Task 1).
- Produces:
  - `NdVisitClient.coBrokerageOnListing(listingId: Long): List<CoBrokerageVisitSummary>` → `GET /v1/visits/co-brokerage?listing-id={id}`
  - `ListingVisitView(at: String, typeLabel: String, inProgress: Boolean, agentName: String?, agentUrl: String?, whatsappUrl: String?)`
  - `ListingView.visits: List<ListingVisitView> = emptyList()`
  - `ListingViews.visits(visits: List<CoBrokerageVisitSummary>, agents: Map<Long, Agent>, listingId: Long, tenant: Tenant, formatter: TenantFormatter): List<ListingVisitView>`

- [ ] **Step 1: Write the failing client test**

In `NdVisitClientTest`:

```kotlin
@Test
fun `co-brokerage visits of a listing`() {
    server.expect(requestTo("http://localhost:8080/v1/visits/co-brokerage?listing-id=501"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                json(
                    SearchCoBrokerageVisitResponse(
                        listOf(
                            CoBrokerageVisitSummary(
                                id = 7001,
                                listingId = 501,
                                agentId = 20,
                                status = VisitStatus.CONFIRMED,
                                type = VisitType.ON_SITE,
                            ),
                        ),
                    ),
                ),
                MediaType.APPLICATION_JSON,
            ),
        )

    val visits = client.coBrokerageOnListing(501)

    assertEquals(listOf(7001L), visits.map { it.id })
    assertEquals(20L, visits.single().agentId)
    server.verify()
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest
```

Expected: compilation fails with unresolved reference `coBrokerageOnListing`.

- [ ] **Step 3: Implement the client call**

```kotlin
/** The visits another agent organizes on [listingId]; the listing agent's block. */
fun coBrokerageOnListing(listingId: Long): List<CoBrokerageVisitSummary> =
    rest.get()
        .uri { uri -> uri.path("/v1/visits/co-brokerage").queryParam("listing-id", listingId).build() }
        .retrieve()
        .body<SearchCoBrokerageVisitResponse>()
        ?.visits
        ?: emptyList()
```

- [ ] **Step 4: Write the failing view-model tests**

In `ListingViewsTest`:

```kotlin
@Test
fun `co-brokerage visits - formatted, labelled and linked`() {
    val visits = listOf(
        CoBrokerageVisitSummary(
            id = 1,
            listingId = 501,
            agentId = 20,
            status = VisitStatus.CONFIRMED,
            type = VisitType.ON_SITE,
            scheduledAt = date(2026, 11, 22, 10, 30),
        ),
        CoBrokerageVisitSummary(
            id = 2,
            listingId = 501,
            agentId = 21,
            status = VisitStatus.CHECKED_IN,
            type = VisitType.VIDEO,
            scheduledAt = date(2026, 11, 23, 9, 0),
        ),
    )

    val rows = ListingViews.visits(
        visits,
        mapOf(20L to agent(20, "Serge", "Ngono", "+237699000001")),
        501,
        TENANT,
        TenantFormatter(TENANT),
    )

    assertEquals(2, rows.size)
    assertEquals("sam. 22 novembre 2026 à 10:30", rows[0].at)
    assertEquals("Sur place", rows[0].typeLabel)
    assertFalse(rows[0].inProgress)
    assertEquals("Serge Ngono", rows[0].agentName)
    assertEquals("/agent?id=20&back=/listings/501%23visits-title", rows[0].agentUrl)
    assertEquals("https://wa.me/237699000001", rows[0].whatsappUrl)

    assertEquals("Visite vidéo", rows[1].typeLabel)
    assertTrue(rows[1].inProgress)
    assertNull(rows[1].agentName) // unreadable agent: the row stays
    assertNull(rows[1].agentUrl)
    assertNull(rows[1].whatsappUrl)
}

@Test
fun `co-brokerage visits - no WhatsApp, no link`() {
    val rows = ListingViews.visits(
        listOf(CoBrokerageVisitSummary(id = 1, listingId = 501, agentId = 20, scheduledAt = date(2026, 11, 22, 10, 30))),
        mapOf(20L to agent(20, "Serge", "Ngono", null)),
        501,
        TENANT,
        TenantFormatter(TENANT),
    )

    assertEquals("Serge Ngono", rows.single().agentName)
    assertNull(rows.single().whatsappUrl)
}

@Test
fun `co-brokerage visits - empty stays empty`() {
    assertTrue(ListingViews.visits(emptyList(), emptyMap(), 501, TENANT, TenantFormatter(TENANT)).isEmpty())
}
```

Add small `agent(...)` and `date(...)` helpers in the test; use `TenantFixtures.TENANT` (its time zone is `Africa/Douala`, date format `dd/MM/yyyy`, time format `HH:mm`). Verify the exact `agentUrl` encoding matches `AgentProfileRules.profileUrl` (adjust the expected string if the encoder leaves `/` unescaped; the existing `ListingViewsTest` assertions for `profileUrl` are the reference).

- [ ] **Step 5: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest
```

Expected: compilation fails because `ListingVisitView` and `ListingViews.visits` do not exist.

- [ ] **Step 6: Add the view model**

In `ListingView.kt`:

```kotlin
/**
 * A visit another agent organizes on the owner's listing, on `listing.html`: never the customer, the note, the code
 * or the report.
 */
data class ListingVisitView(
    /** "samedi 22 novembre 2026 à 10:30", in the tenant's time zone. */
    val at: String,
    /** "Sur place" / "Visite vidéo". */
    val typeLabel: String,
    /** CHECKED_IN: the "En cours" tag. */
    val inProgress: Boolean,
    /** A's full name; null when the agent can't be read. */
    val agentName: String?,
    /** A's profile, back to the visits block; null without a name. */
    val agentUrl: String?,
    /** A's wa.me link; null without a number. */
    val whatsappUrl: String?,
)
```

Add to `ListingView`:

```kotlin
/** The other agents' upcoming visits on this listing, owner lens only; empty when none or unreadable. */
val visits: List<ListingVisitView> = emptyList(),
```

In `ListingViews.of`, add `visits: List<ListingVisitView> = emptyList()` after `stats` and pass it to `ListingView(visits = visits)` (before `backUrl` or using named arguments).

- [ ] **Step 7: Add `ListingViews.visits`**

```kotlin
/**
 * The owner's visits block: soonest first, the agent's name/profile/WhatsApp when it could be read, "un autre agent"
 * (null name) otherwise. Never a customer field.
 */
fun visits(
    visits: List<CoBrokerageVisitSummary>,
    agents: Map<Long, Agent>,
    listingId: Long,
    tenant: Tenant,
    formatter: TenantFormatter,
): List<ListingVisitView> =
    visits
        .sortedWith(compareBy<CoBrokerageVisitSummary> { it.scheduledAt?.time }.thenBy { it.id })
        .map { visit ->
            val agent = agents[visit.agentId]
            val name = agent?.let { ProfileRules.fullName(it) }?.ifBlank { null }
            ListingVisitView(
                at = visit.scheduledAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) }.orEmpty(),
                typeLabel = VisitRules.typeLabel(visit.type),
                inProgress = visit.status == VisitStatus.CHECKED_IN,
                agentName = name,
                agentUrl = name?.let { AgentProfileRules.profileUrl(visit.agentId, "/listings/$listingId#visits-title") },
                whatsappUrl = AgentProfileRules.whatsappUrl(agent?.whatsappNumber),
            )
        }
```

- [ ] **Step 8: Run the client and view tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=NdVisitClientTest,ListingViewsTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): model the co-brokerage visits block"
```

---

### Task 6: Partner Central — render the block and fix A's listing link

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt`

**Interfaces:**
- Consumes: `NdVisitClient.coBrokerageOnListing` (Task 5), `ListingViews.visits` (Task 5), `Parallel`.
- Produces:
  - `ListingController(currentAgent, listingClient, mandateClient, locationClient, tenantContext, matchClient, agentClient, visitClient: NdVisitClient, parallel: Parallel)`
  - Owner + `PUBLISHED`/`RENTED`/`SOLD` → `ListingView.visits` populated; otherwise empty
  - `VisitViews` listing link `/listings/{listingId}?match={matchId}`

- [ ] **Step 1: Write the failing controller tests**

In `ListingControllerTest`, add `visitClient = mock<NdVisitClient>()`, `parallel = Parallel()`, pass them to the controller, and `@AfterEach fun closeParallel() = parallel.close()`.

```kotlin
@Test
fun `owner of a published listing sees the co-brokerage visits`() {
    val published = LISTING.copy(status = ListingStatus.PUBLISHED)
    doReturn(published).whenever(listingClient).get(501)
    doReturn(listOf(CoBrokerageVisitSummary(id = 1, listingId = 501, agentId = 20, scheduledAt = SENT)))
        .whenever(visitClient).coBrokerageOnListing(501)
    doReturn(AGENT).whenever(agentClient).get(20)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertEquals(1, view.visits.size)
    assertEquals("Serge Ngono", view.visits.single().agentName)
    assertEquals("https://wa.me/237670000001", view.visits.single().whatsappUrl)
    verify(agentClient, times(1)).get(20)
}

@Test
fun `a co-broker lens does not read the co-brokerage visits`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT, description = "Public"))
        .whenever(listingClient).get(501)
    doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
        .whenever(matchClient).get(9009)
    doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
        .whenever(mandateClient).get(204)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    mvc.perform(get("/listings/501").param("match", "9009"))

    verify(visitClient, never()).coBrokerageOnListing(any())
}

@Test
fun `a draft does not read the co-brokerage visits`() {
    mvc.perform(get("/listings/501"))

    verify(visitClient, never()).coBrokerageOnListing(any())
}

@Test
fun `a visits read failure leaves the block empty`() {
    doReturn(LISTING.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)
    doThrow(ResourceAccessException("timeout")).whenever(visitClient).coBrokerageOnListing(501)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertTrue(view.visits.isEmpty())
}

@Test
fun `an unreadable agent keeps the row without contact`() {
    doReturn(LISTING.copy(status = ListingStatus.PUBLISHED)).whenever(listingClient).get(501)
    doReturn(listOf(CoBrokerageVisitSummary(id = 1, listingId = 501, agentId = 20, scheduledAt = SENT)))
        .whenever(visitClient).coBrokerageOnListing(501)
    doThrow(ResourceAccessException("timeout")).whenever(agentClient).get(20)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertEquals(1, view.visits.size)
    assertNull(view.visits.single().agentName)
    assertNull(view.visits.single().agentUrl)
}
```

- [ ] **Step 2: Write the failing template tests**

In `ListingTemplateTest`, add the same `visitClient`/`parallel` mocks and constructor arguments (and close `parallel`). Then:

```kotlin
@Test
fun `owner of a published listing sees the visits block`() {
    val published = COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT, description = "Public")
    doReturn(published).whenever(listingClient).get(501)
    doReturn(
        listOf(
            CoBrokerageVisitSummary(
                id = 1, listingId = 501, agentId = 20,
                status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE,
                scheduledAt = Date.from(
                    LocalDateTime.of(2026, 11, 22, 10, 30).atZone(ZoneId.of("Africa/Douala")).toInstant(),
                ),
            ),
        ),
    ).whenever(visitClient).coBrokerageOnListing(501)
    doReturn(ProfileFixtures.AGENT).whenever(agentClient).get(20)

    val page = render(published)

    val section = page.selectFirst("[data-listing-visits]")
    assertNotNull(section)
    assertEquals("Visites d'autres agents", section.selectFirst("h2")!!.text())
    assertTrue(section.text().contains("Serge Ngono"))
    assertTrue(section.text().contains("Sur place"))
    assertTrue(section.text().contains("sam. 22 novembre 2026 à 10:30"))
    assertTrue(section.select("a[href*=wa.me]").isNotEmpty())
    assertFalse(page.text().contains("Secret Client"))
    assertFalse(page.text().contains("secret@example.com"))
}

@Test
fun `no visit, no block`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)).whenever(listingClient).get(501)

    assertNull(render(COMPLETE.copy(status = ListingStatus.PUBLISHED, publishedAt = SENT)).selectFirst("[data-listing-visits]"))
}

@Test
fun `a co-broker lens has no visits block`() {
    doReturn(
        COMPLETE.copy(
            status = ListingStatus.PUBLISHED,
            agentId = 999,
            publishedAt = SENT,
            description = "Public",
            url = "/douala/bonapriso/listing/501/appartement",
        ),
    ).whenever(listingClient).get(501)
    doReturn(Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE))
        .whenever(matchClient).get(9009)
    doReturn(Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED))
        .whenever(mandateClient).get(204)
    doReturn(ProfileFixtures.AGENT.copy(id = 999)).whenever(agentClient).get(999)

    assertNull(render(match = "9009").selectFirst("[data-listing-visits]"))
    verify(visitClient, never()).coBrokerageOnListing(any())
}
```

Add the imports `java.time.LocalDateTime`, `java.time.ZoneId`, `java.util.Date`, and `com.nhaarman.mockitokotlin2.never`, `verify` where missing. `ListingTemplateTest` already imports `ProfileFixtures`; `AGENT` is imported as `ProfileFixtures.AGENT`; use the fully qualified form in the co-broker setup.

- [ ] **Step 3: Write the failing visit-link test**

In `VisitViewsTest`:

```kotlin
@Test
fun `the listing link carries the co-brokerage match`() {
    val page = VisitViews.of(
        visit = VisitFixtures.confirmed().copy(listingId = 501, matchId = 9001),
        customer = null,
        listing = null,
        locations = emptyMap(),
        now = VisitFixtures.NOW,
        tenant = VisitFixtures.TENANT,
        formatter = TenantFormatter(VisitFixtures.TENANT),
    )

    assertEquals("/listings/501?match=9001", page.listingUrl)
}
```

Use the existing `VisitFixtures` helpers; adjust names to those in the file.

- [ ] **Step 4: Run the three test classes to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=ListingControllerTest,ListingTemplateTest,VisitViewsTest
```

Expected: FAIL — the controller never calls `coBrokerageOnListing`, no block is rendered, and `listingUrl` has no `?match=`.

- [ ] **Step 5: Wire the controller**

Append `private val visitClient: NdVisitClient` and `private val parallel: Parallel` to the constructor. In `show`, after `stats`:

```kotlin
val visits = if (result is PublishedListingAccess.Owner && listing.status in PUBLIC_OR_CLOSED) {
    coBrokerageVisits(listing)
} else {
    emptyList()
}
```

with:

```kotlin
private val PUBLIC_OR_CLOSED = setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD)

/**
 * The other agents' visits on this listing, read secondarily. The agents are read once per distinct id, in parallel; an
 * unreadable one keeps its row with "un autre agent". A failed visits read empties the block.
 */
private fun coBrokerageVisits(listing: Listing): List<ListingVisitView> {
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
    return ListingViews.visits(visits, agents, listing.id, tenantContext.getTenant(), formatter())
}
```

Pass `visits = visits` to `ListingViews.of`. Add the imports for `NdVisitClient`, `Parallel`, `CoBrokerageVisitSummary`, `ListingVisitView`.

- [ ] **Step 6: Render the section**

In `resources/templates/listing.html`, insert after the gallery/agent card and before the published description (the `<!-- The public words of the listing, once published. -->` block):

```html
<!-- Another agent's visits on this property: the owner hosts them, never their customer. -->
<section class="home-section listing-visits" aria-labelledby="visits-title"
         th:if="${!l.coBrokerage and !#lists.isEmpty(l.visits)}" data-listing-visits>
    <h2 id="visits-title">Visites d'autres agents</h2>
    <p>Un autre agent organise ces visites de votre bien : vous accueillez l'agent et son client sur place.</p>
    <ul>
        <li th:each="v : ${l.visits}">
            <p><strong th:text="${v.at}">samedi 22 novembre 2026 à 10:30</strong> ·
                <span th:text="${v.typeLabel}">Sur place</span>
                <span class="status-tag status-tag--info" th:if="${v.inProgress}">En cours</span></p>
            <p>avec
                <a th:if="${v.agentUrl}" th:href="${v.agentUrl}" th:text="${v.agentName}">Serge Ngono</a>
                <span th:unless="${v.agentUrl}" th:text="${v.agentName} ?: 'un autre agent'">un autre agent</span>
                <a th:if="${v.whatsappUrl}" th:href="${v.whatsappUrl}" rel="noopener" target="_blank">WhatsApp</a>
            </p>
        </li>
    </ul>
</section>
```

Add a comment to the template's head comment block describing the owner-only visits section. Add CSS in `resources/public/assets/css/listing.css` under the co-brokerage section: a `.listing-visits` block with stacked rows, a clear date/type line, and 44 px minimum height for the profile and WhatsApp links; on phones, the rows stack and the links keep their 44 px targets. No new colours.

- [ ] **Step 7: Fix A's visit-page listing link**

In `VisitViews`:

```kotlin
listingUrl = "/listings/${visit.listingId}?match=${visit.matchId}",
```

- [ ] **Step 8: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=ListingControllerTest,ListingTemplateTest,VisitViewsTest,VisitTemplateTest,VisitControllerTest
```

Expected: PASS. If `VisitTemplateTest` or `VisitControllerTest` asserts the old link, update it to the new `?match=` value.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
    modules/ndopify-partner-central/src/main/resources/templates/listing.html \
    modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css \
    modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt \
    modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt
git commit -m "feat(npc): show the other agents' visits on the listing page"
```

---

### Task 7: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **visit** bullet)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the listing page bullet)

**Interfaces:**
- Consumes: nothing.
- Produces: the written rule the next slices inherit.

- [ ] **Step 1: Document the server rule**

Append to the **visit** bullet of `modules/ndopify-server/CLAUDE.md`, after the search-filter sentence:

> Co-brokerage visits: the listing's agent (B) is a read-only backstage participant. `GET /v1/visits/co-brokerage?listing-id=` returns only `CONFIRMED`/`CHECKED_IN` visits of another agent (`agentId != listing.agent.id`), as `CoBrokerageVisitSummary` (no customer, note, code, address, report or feedback), to the listing's agent only. `CoBrokerageVisitMailet` mails B on confirmation, the 24 h reminder, a cancellation after confirmation and a customer-caused no-show, never on `REQUESTED`/`EXPIRED`/`COMPLETED`/agent no-show or an own-inventory visit. `MandateClosedEvent` now also reaches `VisitMandateEventListener`, which cancels the mandate's `REQUESTED`/`CONFIRMED` visits (search side and listing side) as `SYSTEM` with no mandate reason; `CHECKED_IN` stays. B never learns A's customer.

- [ ] **Step 2: Document the Partner Central rule**

Append to the listing-page paragraph of `modules/ndopify-partner-central/CLAUDE.md`:

> The owner of a `PUBLISHED`/`RENTED`/`SOLD` listing also sees *Visites d'autres agents* (`ListingView.visits`, `ListingViews.visits`): `GET /v1/visits/co-brokerage?listing-id=` (`NdVisitClient.coBrokerageOnListing`), owner lens only, with the slot, the type, an "En cours" tag, and A's name/profile/WhatsApp; an unreadable agent keeps the row as "un autre agent", a failed visits read hides the block. No customer field ever reaches this block. A visit page's listing link now carries `?match={matchId}` so a co-brokerage listing opens instead of the locked page.

- [ ] **Step 3: Run the server gate**

```bash
cd modules/ndopify-server && mvn verify
```

Expected: `BUILD SUCCESS`; JaCoCo line coverage ≥ `0.95`, class coverage ≥ `0.90`.

- [ ] **Step 4: Run the Partner Central gate**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(visit): document co-brokerage visits for the listing agent"
```

---

## Notes for the controller

- The DTO module version in the root `pom.xml` stays `0.0.89-SNAPSHOT` on this branch. Once the `ndopify-dto` CI releases `0.0.89`, a follow-up "fix dependencies" commit pins it to `0.0.89` (the repository convention); that follow-up is out of scope here.
- Task 3 changes the `VisitService` constructor; Task 4 and Task 6 reuse it. Do not reorder the parameters between tasks.
- Task 6 changes the `ListingController` constructor; update both `ListingControllerTest` and `ListingTemplateTest` in the same task.
- No migration is expected. If a test reveals a missing index, do not add one in this slice (the design explicitly says no migration); note it for the performance backlog.
