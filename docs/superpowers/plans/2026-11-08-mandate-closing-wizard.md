# Closing a Mandate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close a mandate from a dedicated, no-JS wizard that records the `ACCEPTED` match behind the deal, and closes both mandates when the agent owns the listing.

**Architecture:** `ndopify-dto` adds two scalar deal fields to `Mandate` and a `CloseMatchRequest`. `ndopify-server` keeps `POST /v1/mandates/{id}/close` for an outside closure and adds `POST /v1/matches/{id}/close` in the match domain (which may depend on mandate and listing) to close both sides atomically. `ndopify-partner-central` gets a three-step server-rendered wizard (`?step=`), a session draft, and a link from the mandate page.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, JUnit + MockMvc + mockito-kotlin, Flyway, MySQL.

**Spec:** `docs/superpowers/specs/2026-11-08-mandate-closing-wizard-design.md`

## Global Constraints

- Kotlin formatted by ktlint; `mvn validate` must pass before commit.
- UI text, e-mails and contracts in French; code, comments, logs and docs in English.
- All user-visible dates/amounts go through `TenantFormatter`; never hard-code `FCFA`.
- Every page works without JavaScript; scripts only enhance.
- `ndopify-dto` fields all have defaults; the DTO has no tests.
- Development version flow: set `<ndopify-dto.version>` in the root `pom.xml` to the DTO's current `-SNAPSHOT`, `mvn install` the DTO locally; after the DTO merges to `main` and releases, set it to the released version. The other modules' CI cannot resolve a `-SNAPSHOT`.
- The `mandate` domain must never depend on `match` or `listing`; the deal close lives in the `match` domain and calls `MandateService` with plain ids.
- Migrations are `db/migration/common/V10_4__mandate-close-deal.sql` (next after `V10_3`).
- Error codes already exist; do not invent new ones.

## Review Focus

1. **Reverse-order close.** The listing side closes first; its listing is no longer `PUBLISHED`. Closing the search afterwards must record the same deal, not 409. Test in Task 5.
2. **Listing unpublished with its mandate still open.** Closing the deal must 409 `LISTING_NOT_PUBLISHED`, never close a mandate while its listing stays on the market. Test in Task 5.
3. **Stale or hand-edited match id.** It must be rejected at the counter step and never reach the session or the close call. Test in Task 9.
4. **Closed/cancelled mandate.** The close link must be hidden, the close page locked, and a stale draft must not close it. Test in Tasks 9 and 11.
5. **Landlord mandate with no listing or an unpublished one.** It must still be closable as an outside deal — no dead end. Test in Tasks 9 and 11.

---

### Task 1: ndopify-dto — deal fields and `CloseMatchRequest`

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/CloseMatchRequest.kt`
- Modify: `pom.xml` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `Mandate.dealMatchId: Long?`, `Mandate.dealOutside: Boolean`,
  `CloseMatchRequest(finalPrice: Money = Money())`.

- [ ] **Step 1: Add the deal fields to `Mandate`**

Next to `finalPrice` / `cancellationCode`, add:

```kotlin
val dealMatchId: Long? = null,   // The ACCEPTED match this mandate was closed on, when a deal was recorded
val dealOutside: Boolean = false, // Closed with no match (a property/client outside the agent's parties)
```

- [ ] **Step 2: Create `CloseMatchRequest`**

```kotlin
package com.wutsi.ndopify.match.dto

import com.wutsi.ndopify.refdata.dto.Money

/** Closes the deal of an ACCEPTED match: both mandates, when both are the agent's. */
data class CloseMatchRequest(
    val finalPrice: Money = Money(),
)
```

- [ ] **Step 3: Point the root POM at the DTO's current snapshot**

Set `<ndopify-dto.version>0.0.85-SNAPSHOT</ndopify-dto.version>` in `pom.xml` (it
currently says `0.0.84`; confirm the DTO module's `pom.xml` version first).

- [ ] **Step 4: Install the DTO locally**

Run: `cd modules/ndopify-dto && mvn install -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto pom.xml
git commit -m "feat(dto): close a mandate on its match"
```

- [ ] **Step 6: Release note (after merge)**

The DTO release workflow publishes `0.0.85`; then set `<ndopify-dto.version>` to
`0.0.85` (no `-SNAPSHOT`) in a follow-up "fix dependencies" commit, as
`modules/ndopify-dto/CLAUDE.md` describes.

---

### Task 2: ndopify-server — schema, entity, mapper

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V10_4__mandate-close-deal.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateEndpointTest.kt`

**Interfaces:**
- Produces: `MandateEntity.dealMatchId: Long?`, `MandateEntity.dealOutside: Boolean`;
  `MandateMapper.toMandate` maps them.

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE T_MANDATE
    ADD COLUMN deal_match_id BIGINT NULL,
    ADD COLUMN deal_outside BOOLEAN NOT NULL DEFAULT FALSE;
```

No foreign key: the match domain owns `T_MATCH`, the same way `contract_id` is a
plain scalar.

- [ ] **Step 2: Extend `MandateEntity`**

Add, next to `finalPrice`:

```kotlin
val dealMatchId: Long? = null,
val dealOutside: Boolean = false,
```

- [ ] **Step 3: Map the fields in `MandateMapper.toMandate`**

```kotlin
dealMatchId = entity.dealMatchId,
dealOutside = entity.dealOutside,
```

- [ ] **Step 4: Assert the defaults in `GetMandateEndpointTest.get`**

```kotlin
assertNull(mandate.dealMatchId)
assertFalse(mandate.dealOutside)
```

- [ ] **Step 5: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateEndpointTest`
Expected: PASS (MySQL required; the migration runs on the test schema).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V10_4__mandate-close-deal.sql \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateEndpointTest.kt
git commit -m "feat(server): store the deal a mandate was closed on"
```

---

### Task 3: ndopify-server — outside close

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CloseMandateEndpointTest.kt`

**Interfaces:**
- Consumes: `MandateEntity.dealOutside`, `MandateEntity.dealMatchId` (Task 2).
- Produces: `MandateService.close` sets `dealOutside = true`, `dealMatchId = null`,
  and rejects `finalPrice.amount < 1` with 400 `HTTP_INVALID_PARAMETER`.

- [ ] **Step 1: Extend the failing test**

In `CloseMandateEndpointTest.close`, add:

```kotlin
assertTrue(mandate.dealOutside)
assertNull(mandate.dealMatchId)
```

Add a new test:

```kotlin
@Test
fun `zero price`() {
    val response = rest.postForEntity(
        "/v1/mandates/1/close",
        CloseMandateRequest(finalPrice = Money(amount = 0, currency = "XAF")),
        ErrorResponse::class.java,
    )
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CloseMandateEndpointTest`
Expected: FAIL on the new assertions.

- [ ] **Step 3: Implement**

In `MandateService.close`, after the status checks:

```kotlin
if (request.finalPrice.amount < 1) {
    throw BadRequestException(
        Error(
            code = ErrorCode.HTTP_INVALID_PARAMETER,
            parameter = Parameter(name = "finalPrice", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
        ),
    )
}
```

and in the saved copy:

```kotlin
dealMatchId = null,
dealOutside = true,
```

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=CloseMandateEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): record an outside mandate closure"
```

---

### Task 4: ndopify-server — close the deal (happy path)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/endpoint/MatchEndpoints.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/match/CloseMatchEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/CloseMatchEndpointTest.kt`

**Interfaces:**
- Consumes: `CloseMatchRequest` (Task 1), `MandateService.closeDeal(...)`.
- Produces:
  - `MandateService.closeDeal(searchMandateId: Long, counterpartMandateId: Long?, matchId: Long, finalPrice: Money): List<MandateEntity>`
  - `MatchService.close(matchId: Long, request: CloseMatchRequest)`
  - `POST /v1/matches/{id}/close`

- [ ] **Step 1: Write the SQL fixture**

Start from `CloseMandateEndpoint.sql` and add:

```sql
-- Another agent, for a listing the caller doesn't own
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (201, 1, 'other.agent@gmail.com', 'Other', 'Agent');
INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES (21, 1, 201, 2370201);

-- 8: another agent's LISTING mandate, 80 its published listing
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type,
                      city_id, property_type, price, currency_code, created_at, modified_at, approved_at)
VALUES
    (8, 1, 10, 21, 'APPROVED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', 150000, 'XAF',
     '2024-06-01 00:00:00', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, listing_type, status, city_id, modified_at)
VALUES (80, 1, 8, 21, 'RENTAL', 'PUBLISHED', 2370201, '2024-06-10 00:00:00');

-- 100: search 1 x listing 60, the deal
-- 101: search 1 x listing 50, proposed (not accepted)
-- 102: search 1 x listing 80, another agent's listing
-- 103: search 1 x listing 70, listing only DRAFT
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, listing_price, created_at, modified_at)
VALUES
    (100, 1, 1, 60, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 1, 1, 50, 20, 'PROPOSED', 50000000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 1, 80, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (103, 1, 1, 70, 20, 'ACCEPTED', 150000, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

The base fixture already defines mandates 1 (SEARCH, APPROVED), 6 (LISTING,
APPROVED), 7 (LISTING, APPROVED, DRAFT listing 70), listings 50/60/70, customer
10, agent 20.

- [ ] **Step 2: Write the failing happy-path tests**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/match/CloseMatchEndpoint.sql"])
class CloseMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    private val request = CloseMatchRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

    @Test
    fun `closing the search on its match closes both mandates`() {
        val response = rest.postForEntity("/v1/matches/100/close", request, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        listOf(1L, 6L).forEach { id ->
            val mandate = mandateDao.findById(id).get()
            assertEquals(MandateStatus.CLOSED, mandate.status)
            assertEquals(140000L, mandate.finalPrice)
            assertEquals(100L, mandate.dealMatchId)
            assertFalse(mandate.dealOutside)
        }
        assertEquals(
            "RENTED",
            jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=60", String::class.java),
        )
        assertEquals(
            "ACCEPTED",
            jdbc.queryForObject("SELECT status FROM T_MATCH WHERE id=100", String::class.java),
        )
    }

    @Test
    fun `closing the listing on its match closes both mandates`() {
        // The same operation: the match is the deal, whichever page sends it.
        rest.postForEntity("/v1/matches/100/close", request, Any::class.java)
        assertEquals(MandateStatus.CLOSED, mandateDao.findById(1L).get().status)
        assertEquals(MandateStatus.CLOSED, mandateDao.findById(6L).get().status)
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CloseMatchEndpointTest`
Expected: FAIL (404, `POST /v1/matches/{id}/close` not mapped).

- [ ] **Step 4: Implement `MandateService.closeDeal`**

```kotlin
/**
 * Closes both sides of a deal (or one) with the same price: only APPROVED sides,
 * a side already CLOSED/CANCELLED is left as is, 409 when no side is left.
 * Locks in ascending id order; stores [matchId] on each closed side.
 */
@Transactional
fun closeDeal(
    searchMandateId: Long,
    counterpartMandateId: Long?,
    matchId: Long,
    finalPrice: Money,
): List<MandateEntity>
```

Implementation notes:
- ids = `listOfNotNull(searchMandateId, counterpartMandateId).distinct().sorted()`.
- `ids.forEach { dao.findByIdForUpdate(it) }` (ascending), collect the entities.
- 404 `MANDATE_NOT_FOUND` for a missing one (lock method already throws).
- to close = entities.filter { status != CLOSED && status != CANCELLED }.
- if to close is empty → 409 `MANDATE_ALREADY_CLOSED`.
- if any to close is not APPROVED → 409 `MANDATE_NOT_APPROVED`.
- `now = Date(clock.millis())`; save each with `status = CLOSED`, `finalPrice = request.amount`,
  `closedAt = now`, `age = age(approvedAt ?: createdAt, now)`, `dealMatchId = matchId`,
  `dealOutside = false`, `modifiedAt = now`.
- publish `MandateClosedEvent(mandateId, tenantId)` for each saved.
- return the saved list.

- [ ] **Step 5: Implement `MatchService.close`**

Inject `MandateService` into `MatchService`. Add:

```kotlin
/**
 * Closes the deal of an ACCEPTED match. 404 MATCH_NOT_FOUND, 409
 * MATCH_NOT_ACCEPTED, 409 LISTING_NOT_PUBLISHED when the listing side will be
 * closed and its listing isn't PUBLISHED, 400 invalid price.
 */
@Transactional
fun close(matchId: Long, request: CloseMatchRequest): List<MandateEntity> {
    val match = findById(matchId)
    if (match.status != MatchStatus.ACCEPTED) {
        throw ConflictException(Error(ErrorCode.MATCH_NOT_ACCEPTED))
    }
    if (request.finalPrice.amount < 1) {
        throw BadRequestException(/* see Task 3's parameter */)
    }

    val search = match.mandate
    val listingMandate = match.listing.mandate
    val closeListing = listingMandate.agentId == search.agentId &&
        listingMandate.status != MandateStatus.CLOSED &&
        listingMandate.status != MandateStatus.CANCELLED
    if (closeListing && match.listing.status != ListingStatus.PUBLISHED) {
        throw ConflictException(Error(ErrorCode.LISTING_NOT_PUBLISHED))
    }
    return mandateService.closeDeal(
        searchMandateId = search.id!!,
        counterpartMandateId = listingMandate.id!!.takeIf { closeListing },
        matchId = matchId,
        finalPrice = request.finalPrice,
    )
}
```

- [ ] **Step 6: Add the endpoint**

```kotlin
// The deal of an ACCEPTED match: closes both mandates when both are the agent's.
@PostMapping("/{id}/close")
fun close(@PathVariable id: Long, @RequestBody request: CloseMatchRequest) {
    access.ensureCanUpdate(service.findById(id).mandate)
    service.close(id, request)
}
```

- [ ] **Step 7: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=CloseMatchEndpointTest`
Expected: PASS.

- [ ] **Step 8: Test the lock order and the no-op rule**

Create `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceCloseDealTest.kt`
with mocked dependencies (as the domain's service tests do) and:

```kotlin
@Test
fun `locks the mandates in ascending id order`() {
    service.closeDeal(searchMandateId = 6, counterpartMandateId = 1, matchId = 100, finalPrice = price)
    val order = inOrder(dao)
    order.verify(dao).findByIdForUpdate(1)
    order.verify(dao).findByIdForUpdate(6)
}

@Test
fun `both sides closed is a conflict`() {
    // both entities CLOSED → assertFailsWith<ConflictException> and its ErrorCode
}
```

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateServiceCloseDealTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): close both mandates of a deal"
```

---

### Task 5: ndopify-server — deal-close validations and edge cases

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt` (only if a test exposes a gap)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/CloseMatchEndpointTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4.

- [ ] **Step 1: Add the failing tests**

Add tests for:

```kotlin
@Test fun `match not accepted`()      // POST /v1/matches/101/close → 409 MATCH_NOT_ACCEPTED
@Test fun `match not found`()         // POST /v1/matches/999/close → 404 MATCH_NOT_FOUND
@Test fun `listing not published`()   // POST /v1/matches/103/close → 409 LISTING_NOT_PUBLISHED, mandates 1 and 7 unchanged
@Test fun `both sides already closed`() {
    // close 1 and 6 with the outside endpoint, then the match → 409 MANDATE_ALREADY_CLOSED
}
@Test fun `the listing side closed first`() {
    // close mandate 6 with the outside endpoint (listing 60 → RENTED)
    // then POST /v1/matches/100/close → 200; mandate 1 CLOSED, dealMatchId=100
}
@Test fun `another agent's listing is only recorded`() {
    // POST /v1/matches/102/close → 200; mandate 1 CLOSED, dealMatchId=102;
    // mandate 8 still APPROVED; listing 80 still PUBLISHED
}
@Test fun `zero price`()             // 400 HTTP_INVALID_PARAMETER
@Test fun `not the agent`()          // mirror MandateAccessEndpointTest's non-agent token → 403 MANDATE_ACCESS_DENIED
```

- [ ] **Step 2: Run and fix**

Run: `cd modules/ndopify-server && mvn test -Dtest=CloseMatchEndpointTest`
Expected: the new tests fail on the documented behavior; fix `MatchService.close`
(or `closeDeal`) until all pass. Keep the error order from the spec:
404 match, 401/403 access, 409 not accepted, 409 listing not published, 400
price, then mandate status/closed errors.

- [ ] **Step 3: Run the neighbouring suites**

Run: `cd modules/ndopify-server && mvn test -Dtest='CloseMatchEndpointTest,CloseMandateEndpointTest,GetMandateEndpointTest'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server
git commit -m "test(server): cover closing a deal's edge cases"
```

---

### Task 6: BFF — `NdMatchClient.close`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMatchClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMatchClientTest.kt`

**Interfaces:**
- Consumes: `CloseMatchRequest`.
- Produces: `NdMatchClient.close(id: Long, request: CloseMatchRequest)` → `POST /v1/matches/{id}/close`.

- [ ] **Step 1: Write the failing client test**

```kotlin
@Test
fun close() {
    server.expect(requestTo("$baseUrl/v1/matches/9001/close"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"finalPrice":{"amount":240000,"currency":"XAF"}}"""))
        .andRespond(withSuccess())
    client.close(9001, CloseMatchRequest(finalPrice = Money(240000, "XAF")))
    server.verify()
}
```

(Mirror the existing `NdMatchClientTest` setup and imports.)

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdMatchClientTest#close`
Expected: FAIL (unresolved `close`).

- [ ] **Step 3: Implement**

```kotlin
/** Closes the deal of an ACCEPTED match: both mandates, when both are the agent's. */
fun close(id: Long, request: CloseMatchRequest) {
    rest.post().uri("/v1/matches/{id}/close", id).body(request).retrieve().toBodilessEntity()
}
```

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdMatchClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): close a match's deal"
```

---

### Task 7: BFF — session draft, forms and rules

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/PendingMandateClose.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseForm.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/PendingMandateCloseTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRulesTest.kt`

**Interfaces:**
- Produces:
  - `PendingMandateClose(matchId: Long?, outside: Boolean, priceAmount: Long?)` with
    `SESSION_ATTRIBUTE = "npc.pending-mandate-close"`, `Serializable`,
    `isComplete(step, counterpartApplicable): Boolean`,
    `firstIncompleteStep(counterpartApplicable): String`.
  - `CounterpartForm(counterpart: String?)` (a match id, or `"outside"`).
  - `PriceForm(finalPrice: String?)`.
  - `MandateCloseRules.amount(value: String?): Long?`, `closeMatch(draft, currency)`,
    `closeMandate(draft, currency)`.

- [ ] **Step 1: Write the failing tests**

`PendingMandateCloseTest`:

```kotlin
@Test fun `counterpart steps with a match`() {
    val draft = PendingMandateClose(matchId = 100)
    assertTrue(draft.isComplete(STEP_COUNTERPART, true))
    assertEquals(STEP_PRICE, draft.firstIncompleteStep(true))
}

@Test fun `counterpart steps with outside`() {
    val draft = PendingMandateClose(outside = true)
    assertTrue(draft.isComplete(STEP_COUNTERPART, true))
}

@Test fun `counterpart is skipped without candidates`() {
    val draft = PendingMandateClose()
    assertEquals(STEP_PRICE, draft.firstIncompleteStep(false))
}

@Test fun `all answered`() {
    val draft = PendingMandateClose(outside = true, priceAmount = 240000)
    assertEquals(STEP_CONFIRM, draft.firstIncompleteStep(true))
}
```

`MandateCloseRulesTest`:
- `"240 000"` → 240000; `"0"`/`""`/`"240k"` → null.
- `closeMatch(draft, "XAF")` and `closeMandate(draft, "XAF")` build the right
  `CloseMatchRequest`/`CloseMandateRequest` with `Money(amount, currency, MONTHLY)`.

- [ ] **Step 2: Run them and see them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='PendingMandateCloseTest,MandateCloseRulesTest'`
Expected: FAIL (classes missing).

- [ ] **Step 3: Implement the draft, forms and rules**

Declare the step constants once, in `PendingMandateClose`'s companion (the
controller in Task 9 uses them):

```kotlin
const val STEP_COUNTERPART = "counterpart"
const val STEP_PRICE = "price"
const val STEP_CONFIRM = "confirm"
```

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='PendingMandateCloseTest,MandateCloseRulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): the closing wizard's session draft and rules"
```

---

### Task 8: BFF — shared interested-candidate reads

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateDealCandidates.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
  (`listingClients` uses the new read)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateDealCandidatesTest.kt`

**Interfaces:**
- Consumes: `NdMatchClient`, `NdMandateClient`, `NdCustomerClient`.
- Produces:
  - `data class ClientCandidate(val match: Match, val mandate: MandateSummary, val customer: Customer?)`
  - `data class ListingCandidate(val match: Match, val listing: ListingSummary?)`
  - `fun clients(listingId: Long, statuses: List<MatchStatus>, agentId: Long): List<ClientCandidate>`
  - `fun listings(mandateId: Long, statuses: List<MatchStatus>): List<ListingCandidate>`

- [ ] **Step 1: Write the failing tests**

With mocked clients:
- `clients` asks `searchByListings(listingId, statuses)`, then `searchByIds(match.mandateIds, agentId)`, keeps only mandates whose `agentId == agentId`, then `customerClient.search(customerIds)`; a customer failure yields `customer = null`, not an empty list.
- `listings` asks `search(mandateIds, statuses)` then `searchByIds(listingIds)`; a listing missing from the result is left with `listing = null`.
- `listings` makes no `searchByIds` call when there are no matches.

- [ ] **Step 2: Run and see them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDealCandidatesTest`
Expected: FAIL (class missing).

- [ ] **Step 3: Implement the helper**

Keep the HTTP concerns here; no formatting, no labels. A secondary read failure
for customers/listings degrades that row, never the whole list (as the mandate
page does today).

- [ ] **Step 4: Refactor `MandateController.listingClients`**

Replace its matches/mandates/customers reads with `candidates.clients(listingId, MatchRules.SENT, agentId)`;
keep the visits and the existing view mapping. The proposals' rows must keep the
same content (another agent's client remains unnamed).

- [ ] **Step 5: Run the mandate page suites**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateDealCandidatesTest,MandateControllerTest,MandateViewsTest,MandateTemplateTest'`
Expected: PASS (same behavior as before the refactor).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "refactor(npc): share the mandate deal-candidate reads"
```

---

### Task 9: BFF — the closing controller

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViews.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViewsTest.kt`

**Interfaces:**
- Consumes: Tasks 6–8; `MandateDealCandidates`, `PendingMandateClose`,
  `MandateCloseRules`, `NdMatchClient.close`, `NdMandateClient.close`.
- Produces:
  - `GET /mandates/{id}/close` and `?step=counterpart|price|confirm`
  - `POST /mandates/{id}/close/counterpart|price|confirm`
  - `MandateCloseView` (mandate summary, type, candidates, progress, step, form
    values, errors, currency).
  - `CloseCandidateView(matchId, title, reference, rent, interestLabel)`.

- [ ] **Step 1: Write the failing controller tests**

```kotlin
@Test fun `locked when not the agent's`()          // get /mandates/201/close → VIEW, locked
@Test fun `locked when not approved`()              // state ASSIGNED
@Test fun `counterpart is skipped without candidates`() // searches: get?step=counterpart → step price
@Test fun `counterpart - a listing outside the candidates is refused`()
@Test fun `counterpart - outside is accepted`()      // session stores outside = true
@Test fun `counterpart - a match is stored`()        // session stores matchId
@Test fun `price - invalid`()                        // re-renders with the error, no redirect
@Test fun `price - kept between steps`()
@Test fun `confirm - match`() {
    // GET ?step=confirm with a draft match reads the match/listings;
    // POST confirm → verify(matchClient).close(100, CloseMatchRequest(Money(240000, "XAF")))
    // flash outcome = closed; redirect /mandates/201
}
@Test fun `confirm - outside`() {
    // verify(mandateClient).close(201, CloseMandateRequest(Money(...)))
}
@Test fun `confirm - a match that is no longer a candidate sends back to step 1`()
@Test fun `confirm - 409 is changed`()
@Test fun `confirm - 5xx is error`()
@Test fun `no step clears a stale draft`()          // GET without step resets the session
```

Use the existing `MandateControllerTest` fixtures (`TenantFixtures`, `ProfileFixtures`,
`SignedInAdviceTest.signIn`) and `MockMvcBuilders.standaloneSetup`.

- [ ] **Step 2: Run and see them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateCloseControllerTest`
Expected: FAIL (controller missing).

- [ ] **Step 3: Implement the controller**

- `@RequestMapping("/mandates/{id}/close")`, `@Controller`.
- `CurrentAgent` + `agentClient`/`mandateClient.get(id)`: locked view unless the
  mandate is the agent's and `APPROVED`.
- `GET`: no `step` clears the draft; read candidates via `MandateDealCandidates`
  (`ACCEPTED`); build applicable steps; show the first incomplete/allowed step.
- `POST counterpart`: `CounterpartForm`; `"outside"` → `outside=true`, else the id
  must be in the live candidates; otherwise re-render with the error. Store and
  redirect to `?step=price`.
- `POST price`: `MandateCloseRules.amount`; store; redirect to `?step=confirm`.
- `GET ?step=confirm`: re-read candidates; if the stored match is gone, clear it
  and redirect to `?step=counterpart` with a `conflict` flash (or price when none).
- `POST confirm`: call `NdMatchClient.close` when `matchId != null`, else
  `NdMandateClient.close`; clear the draft; redirect to `/mandates/{id}` with
  `MandateController.OUTCOME = CLOSED`. Map 403/404/409 to `CHANGED`, 5xx to
  `FAILED`; use `MandateCloseRules` for the request bodies.
- `Next=/Précédent` model attributes for the template; on step 1, Back returns to
  the mandate page.

- [ ] **Step 4: Implement the views and their test**

`MandateCloseViews` builds each candidate's label from `Match` + `ListingSummary`
(title, reference, neighbourhood, rent) and the interest label from
`Match.answeredAt` / its visit. Test labels for both mandate types and for an
outside-only list.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateCloseControllerTest,MandateCloseViewsTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): the closing wizard controller"
```

---

### Task 10: BFF — the closing page

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/mandate/close.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseTemplateTest.kt`

**Interfaces:**
- Consumes: `MandateCloseController.VIEW = "mandate/close"`, `MandateCloseView`.

- [ ] **Step 1: Write the failing template test**

Render the page with a real Thymeleaf engine for:
- a LISTING mandate with two candidates: both client names, the outside option;
- a SEARCH mandate with two listings: their references and rents, the outside option;
- the no-candidate case: no counterpart fieldset, the price step shown;
- the confirm step: the recap (property, client, price, note) and the final button;
- a field error: the message and `aria-invalid`.

- [ ] **Step 2: Run and see it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateCloseTemplateTest`
Expected: FAIL (template missing).

- [ ] **Step 3: Write the template**

Port the prototype (`src/main/html/mandate/close.html`), adding `th:`:
- title and mandate number; progress from `stepNumber`/`stepCount`;
- `th:each` candidate radios, `name="counterpart"`, value `matchId`, plus the
  outside radio;
- price input with `th:value`, `errors['finalPrice']`, the `currency`;
- confirm recap and `data-recap`-equivalent text;
- each step its own `<form>` posting to its route;
- Back button/link: `?step=<previous>` or the mandate page on the first step.

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateCloseTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): the closing wizard page"
```

---

### Task 11: BFF — mandate page link, redirect and deal line

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEndForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt` / `MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `dealMatchId`, `dealOutside` on `Mandate`; the close page route.
- Produces: the mandate page's close link; `?end=close` → `redirect:/mandates/{id}/close`;
  the closed deal line.

- [ ] **Step 1: Update the failing controller tests**

- `GET /mandates/201?end=close` → 3xx redirect to `/mandates/201/close`.
- The close form is gone: `POST /mandates/201/close` no longer maps (remove the
  old close tests from `MandateControllerTest`; their coverage moves to
  `MandateCloseControllerTest`).
- A CLOSED mandate with `dealMatchId = 100`: `MandateController` reads
  `matchClient.get(100)` + the listing/counterpart/customer and the model has the
  deal line; a `matchClient` failure leaves it out.
- A CLOSED LISTING mandate with `dealOutside = true`: the line says the client is
  outside the agent's searches; the listing is still named (found by mandate id).
- An APPROVED mandate: the close link is present; a CLOSED one: absent.

- [ ] **Step 2: Run and see them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateControllerTest`
Expected: FAIL on the new cases.

- [ ] **Step 3: Implement the controller and template**

- Replace the inline close form with
  `<a th:href="@{/mandates/{id}/close(id=${m.id})}" data-close-link>Fermer le mandat</a>`
  (only when `closable`).
- Keep the cancel form and its `?end=cancel` handling; remove `END_CLOSE`, the
  close price parsing and the close POST. `?end=close` redirects to the new page.
- `MandateView` gains the deal line fields: listing label, counterpart label,
  client label, `outside` text. `MandateController` builds them for a CLOSED
  mandate from `dealMatchId` (match → listing/mandate → customer, all secondary)
  or `dealOutside`; for `CANCELLED`, nothing.
- `MandateEndForm` keeps only `cancellationCode`/`cancellationReason`.
- `mandate.js`: `initEnd` handles the cancel form only; remove the close-form
  validation and its `Fermer le mandat` opener (`data-page` `mandate`).

- [ ] **Step 4: Run the mandate page tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): open the closing wizard from the mandate page"
```

---

### Task 12: End-to-end walkthrough

**Files:** none.

- [ ] **Step 1: Run the stack**

MySQL and Mailpit running; `cd modules/ndopify-server && mvn spring-boot:run`;
`cd modules/ndopify-partner-central && mvn spring-boot:run`.

- [ ] **Step 2: Close a search on a listing**

Create/accept/approve a search and a landlord's listing so the matcher produces
an `ACCEPTED` match; open the search's mandate page → *Fermer le mandat* → step 1
shows the interested listing → price prefilled → confirm. Verify:
- both mandates `CLOSED` on their pages, same final price;
- the listing `RENTED`;
- the closed panel names the listing and the counterpart;
- the client's page says the deal.

- [ ] **Step 3: Close a listing with a client**

On the landlord's mandate page, same flow with the client list. Verify the
client's search is closed too and the listing is `RENTED`.

- [ ] **Step 4: Outside closures**

Close a mandate with no candidates: the wizard starts at the price step, the
closed page says the deal was outside, and no listing/counterpart is touched.

- [ ] **Step 5: No-JS and guards**

Disable JavaScript: the three steps still work. Check the locked view for a
non-approved/closed mandate, an already-closed deal (409), and the double-submit.

---

## Execution notes

- Tasks 1–5 are server-side and depend on Task 1's local DTO install; Tasks 6–11
  are BFF-side.
- The DTO release is a separate merge (Task 1, Step 6) before the other modules'
  CI can build.
- The prototype is the visual reference; the served page may differ in markup
  but not in wording or order of steps.
