# Turn a public listing into a search mandate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A visitor of a `PUBLISHED` listing gets a SEARCH mandate seeded from it — all criteria and the budget from the listing — offered first to the listing's agent, attached to an existing search of the same city and type, and proposed once approved.

**Architecture:** The mandate stores the origin listing's id (a plain id, no relation). The listing domain implements an `OriginListingResolver` interface declared by the mandate domain, so the mandate domain never depends on the listing one: it seeds the mandate's criteria from the resolver, and the dispatcher asks it for the agent to offer first. The match domain creates the match of an existing search at the customer's request (hard rules, no `min-score`) and proposes the origin listing on approval. The public site posts only contact + moving period + `originListingId`; the server ignores every other criterion of the request.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Hibernate multi-tenancy, Flyway (MySQL), Thymeleaf, `kotlin.test`, Mockito-Kotlin, Maven, ktlint, JaCoCo.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/169

## Global Constraints

- Read the root `CLAUDE.md` and `modules/{ndopify-dto,ndopify-server,ndopify-web}/CLAUDE.md` before starting. Their rules win over this plan.
- One commit per library module at most: a commit touching `modules/ndopify-dto/` starts `feat(dto):` / `fix(dto):` and ends its body with `Refs #169`; the other commits touch no library module.
- While developing, `ndopify-dto` is a local `-SNAPSHOT`: `mvn -pl modules/ndopify-dto install`, then set `<ndopify-dto.version>` in the root `pom.xml` to `0.0.94-SNAPSHOT`. **Never commit that change.**
- Every behaviour change has a test; `mvn verify` in each touched module is green (ktlint + tests + JaCoCo). `ndopify-server` needs local MySQL.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. Tests mirror the main package.
- UI text, e-mails and Telegram messages are French; code, comments, KDoc and docs are English.
- Dates and amounts in e-mails/contracts go through `TenantFormatter`; never a hard-coded pattern nor "FCFA".
- A new Flyway script, never an edit of an existing one. Next version: `V12_0`.
- The public page's form must work without JavaScript; scripts only enhance.
- The prototype (`modules/ndopify-web/src/main/html/`) was reworked and approved during grooming and is already committed on `main`: do not redo it, and keep it as the design reference for the served page.
- Never `Closes`/`Fixes` in the PR: the story is closed by `merge`; `Refs #169` only.

## Review Focus

1. **A double submit** (two identical POSTs, or a retry) must not create two mandates: the second finds the active search of the same city/type and attaches. Check the sequence with a search created between the two requests.
2. **A VENDEUR/agent edge**: the target offer must never go to the customer itself, to an agent that already declined/expired this mandate, or to an agent at `max-load` / `max-pending`; the fallback must be the normal dispatch, not no offer.
3. **A listing sold between the page load and the POST**: the mandate must still be created from the listing's criteria, without origin; it must not 500 nor keep the origin.
4. **The attach when the existing search has no agent yet** (`PENDING_ASSIGNMENT`): no match can be created then; it must be created and proposed later, on approval, not lost.
5. **`match.html` shares `listing-body.html`**: the new CTA must live in `listing.html` only; the match page must render unchanged.
6. **A failed POST re-render**: the popup must be open (`.is-open`) without JavaScript, with the French errors and the typed values kept.
7. **No enumeration**: success, honeypot, rate-limited and attach all show the same confirmation; never the agent's name nor whether a search exists.

---

### Task 1: The mandate's origin listing in the contract (DTO)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/CreateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Test: none (the DTO has no tests; the server's endpoint tests cover it)

**Interfaces:**
- Produces: `CreateMandateRequest.originListingId: Long? = null`; `Mandate.originListingId: Long? = null`; `ErrorCode.MANDATE_RATE_LIMITED` = `urn:wutsi:ndopify:error:mandate:rate-limited`.

- [ ] **Step 1: Add the fields**

In `CreateMandateRequest`, after `mandateType`, add:
```kotlin
/** The listing the visitor asked about, when the mandate comes from its public page; null otherwise. */
val originListingId: Long? = null,
```
In `Mandate`, after `contractId`, add the same field with the same KDoc.

In `ErrorCode`, under the mandate group, after `MANDATE_ALREADY_CLOSED`, add:
```kotlin
val MANDATE_RATE_LIMITED = "$PREFIX:mandate:rate-limited"
```

- [ ] **Step 2: Build and install locally**

Run: `mvn -pl modules/ndopify-dto install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Point the reactor at the snapshot**

Set `<ndopify-dto.version>0.0.94-SNAPSHOT</ndopify-dto.version>` in the root `pom.xml`. This edit stays uncommitted.

- [ ] **Step 4: Commit (DTO module only)**

```bash
git add modules/ndopify-dto
git commit -m "feat(dto): add a mandate's origin listing" -m "Refs #169"
```

### Task 2: Store the origin listing (server schema, entity, mapper)

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V12_0__mandate-origin-listing.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapperTest.kt` (create)

**Interfaces:**
- Consumes: Task 1's `Mandate.originListingId`.
- Produces: `MandateEntity.originListingId: Long?`; `MandateMapper.toMandate` copies it.

- [ ] **Step 1: Write the failing mapper test**

```kotlin
@Test
fun `origin listing is returned`() {
    val mandate = MandateEntity(id = 1, customerId = 2, cityId = 3, originListingId = 9)
    assertEquals(9, MandateMapper.toMandate(mandate).originListingId)
}

@Test
fun `no origin listing`() {
    assertNull(MandateMapper.toMandate(MandateEntity(id = 1, customerId = 2, cityId = 3)).originListingId)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateMapperTest`
Expected: FAIL (unresolved `originListingId`).

- [ ] **Step 3: Add the column, the field and the mapping**

`V12_0__mandate-origin-listing.sql`:
```sql
ALTER TABLE T_MANDATE ADD COLUMN origin_listing_id BIGINT;
CREATE INDEX I_MANDATE_origin_listing ON T_MANDATE(origin_listing_id);
ALTER TABLE T_MANDATE ADD CONSTRAINT FK_MANDATE_origin_listing
  FOREIGN KEY(origin_listing_id) REFERENCES T_LISTING(id);
```
In `MandateEntity`, after `contractId`:
```kotlin
/** The listing the mandate was seeded from; null for a mandate created another way. */
@Column(name = "origin_listing_id")
var originListingId: Long? = null,
```
In `MandateMapper.toMandate`, add `originListingId = entity.originListingId`.

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateMapperTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(mandate): store a mandate's origin listing" -m "Refs #169"
```

### Task 3: Resolve a listing without depending on the listing domain

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OriginListingResolver.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingOriginListingResolver.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingOriginListingResolverTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class OriginListing(
    val id: Long,
    val agentId: Long?,
    val published: Boolean,
    val criteria: CreateMandateRequest,
)
interface OriginListingResolver { fun resolve(listingId: Long): OriginListing? }
```
- The criteria carry `mandateType = SEARCH`, the matching criteria (category, listing type, city, neighbourhoods, property type, furniture, rooms, parkings, area, the 15 amenities) and `price = Money(price, currencyCode, pricePeriodicityType)`. They carry **no** contact, no rental terms, no `availableFrom`, no `movingPeriod` (the visitor's).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `resolves a published listing into search criteria`() {
    val listing = ListingEntity(
        id = 7, tenantId = 1, agent = AgentEntity(id = 70, party = PartyEntity(id = 700)),
        status = ListingStatus.PUBLISHED, listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL, cityId = 2370201, neighborhoodId = 11,
        propertyType = PropertyType.APARTMENT, furnitureType = FurnitureType.FURNISHED,
        rooms = 2, parkings = 1, area = 75, waterTank = true,
        price = 175_000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    doReturn(Optional.of(listing)).whenever(dao).findById(7)

    val origin = ListingOriginListingResolver(dao).resolve(7)!!

    assertEquals(7, origin.id)
    assertEquals(70, origin.agentId)
    assertTrue(origin.published)
    assertEquals(MandateType.SEARCH, origin.criteria.mandateType)
    assertEquals(listOf(11), origin.criteria.neighbourhoodIds)
    assertEquals("2", origin.criteria.rooms)
    assertEquals(true, origin.criteria.waterTank)
    assertEquals(Money(175_000, "XAF", PeriodicityType.MONTHLY), origin.criteria.price)
    assertEquals("", origin.criteria.email)
}

@Test
fun `a sold listing resolves, not published`() { /* status SOLD → published == false, same criteria */ }

@Test
fun `an unknown listing resolves to null`() { /* findById empty → null */ }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingOriginListingResolverTest`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement the interface and the resolver**

`OriginListingResolver.kt` (mandate package) holds the interface, `OriginListing` and its KDoc (see Interfaces). `ListingOriginListingResolver` is a `@Service` over `ListingRepository`; it loads the listing, returns `null` when absent, `published = status == PUBLISHED`, `agentId = listing.agent.id`, and maps the criteria as above. Copy `trackInaccessibleByCar` too (it is not an amenity the matcher scores, but it is a criteria field of the request).

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingOriginListingResolverTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(mandate): resolve a listing's criteria for a mandate" -m "Refs #169"
```

### Task 4: Seed the mandate from the listing

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt:142-202`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceTest.kt` (add cases)

**Interfaces:**
- Consumes: `OriginListingResolver.resolve`, `CreateMandateRequest.originListingId`.
- Produces: `MandateService.create` keeps the origin only when the listing is `PUBLISHED`; the mandate's criteria (including the budget) come from the listing; a `originListingId` that resolves to nothing is 404 `LISTING_NOT_FOUND` (no criteria to fall back to).

- [ ] **Step 1: Write the failing service tests**

In `MandateServiceTest`:
```kotlin
@Test
fun `create seeds the criteria and the budget from the origin listing`() {
    // request: contact + movingPeriod + originListingId=7, no criteria
    doReturn(OriginListing(7, 70, true, criteria())).whenever(resolver).resolve(7)
    val mandate = service.create(request)
    assertEquals(2370201, mandate.cityId)
    assertEquals(listOf(11), mandate.neighborhoodIds)
    assertEquals(175_000, mandate.price)
    assertEquals("XAF", mandate.currencyCode)
    assertEquals(MovingPeriod.WITHIN_1_MONTH, mandate.movingPeriod) // the visitor's
    assertEquals(7, mandate.originListingId)
}

@Test
fun `create keeps no origin for a listing no longer published`() {
    doReturn(OriginListing(7, 70, false, criteria())).whenever(resolver).resolve(7)
    val mandate = service.create(request)
    assertEquals(2370201, mandate.cityId) // still seeded
    assertNull(mandate.originListingId)
}

@Test
fun `create rejects an unknown origin listing`() {
    doReturn(null).whenever(resolver).resolve(7)
    assertFailsWith<NotFoundException> { service.create(request) }
}
```
`criteria()` builds a `CreateMandateRequest` with the listing's fields (city 2370201, neighbourhood 11, price 175 000 XAF monthly).

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateServiceTest`
Expected: FAIL.

- [ ] **Step 3: Refactor `create`**

Add `private val originListingResolver: OriginListingResolver` to the constructor (add a `mock()` to `MandateServiceTest`'s factory). In `create`, before `findOrCreate`:
```kotlin
val origin = request.originListingId?.let { id ->
    originListingResolver.resolve(id)
        ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
}
```
Then use `val criteria = origin?.criteria ?: request` for every criteria field of the entity (propertyCategory, mandateType, listingType, cityId, neighbourhoodIds, propertyType, furnitureType, rooms, parkings, area, the 15 amenities, price/currency/periodicity). Keep `movingPeriod = request.movingPeriod`, the contact fields from `request`, and the rental terms from `request`. Set `originListingId = origin?.id?.takeIf { origin.published }`.

- [ ] **Step 4: Add the endpoint cases**

In `CreateMandateEndpointTest`, add fixtures (`@Sql` inserts a PUBLISHED listing 500 for mandate 300, and a SOLD one 501) and:
```kotlin
@Test
fun `a mandate created from a listing takes the listing's criteria`() { /* POST originListingId=500 + tampered criteria → assert city/neighbourhood/price/originListingId from listing 500 */ }

@Test
fun `a sold listing seeds the criteria without origin`() { /* originListingId=501 → originListingId null, city from listing */ }

@Test
fun `an unknown listing is 404`() { /* originListingId=999 → LISTING_NOT_FOUND, no mandate */ }
```
Use `anonymousUser = true` (the endpoint is public) like `AcceptAssignmentEndpointTest`.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateServiceTest,CreateMandateEndpointTest`
Expected: PASS (MySQL required for the endpoint test).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(mandate): seed a mandate from a listing" -m "Refs #169"
```

### Task 5: Attach the listing to an existing search, with the rate cap

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/event/ExistingSearchInterestEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class ExistingSearchInterestEvent(val mandateId: Long, val listingId: Long, val tenantId: Long)
```
- `MandateService.create` returns the existing mandate (id) and publishes the event when the customer already has an active SEARCH mandate of the same `cityId` and `listingType`; otherwise it creates one, after the cap.
- Cap: at most **3** new SEARCH mandates of a customer per 24 h, only for a request with an `originListingId`; a 4th is `ConflictException(MANDATE_RATE_LIMITED)`. Attaching is never capped.

- [ ] **Step 1: Write the failing tests**

In `MandateServiceTest`:
```kotlin
@Test
fun `create attaches to an active search of the same city and type`() {
    val existing = MandateEntity(id = 42, customerId = 10, cityId = 2370201, listingType = ListingType.RENTAL, mandateType = MandateType.SEARCH, status = MandateStatus.ASSIGNED)
    doReturn(existing).whenever(dao).findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(10, MandateType.SEARCH, 2370201, ListingType.RENTAL, listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED))
    val result = service.create(request)
    assertEquals(42, result.id)
    verify(dao, never()).save(any())
    verify(eventPublisher).publishEvent(ExistingSearchInterestEvent(42, 7, TENANT_ID))
}

@Test
fun `another city or listing type creates a second mandate`() { /* existing in another city → save + MandateCreatedEvent */ }

@Test
fun `a fourth new search within 24 hours is refused`() {
    doReturn(3L).whenever(dao).countByCustomerIdAndMandateTypeAndCreatedAtAfter(any(), any(), any())
    assertFailsWith<ConflictException> { service.create(request) }
    // error code: MANDATE_RATE_LIMITED
}

@Test
fun `attaching is not capped`() { /* count stub at 3 + an existing search → returns it, no exception */ }
```
In `CreateMandateEndpointTest`, add:
```kotlin
@Test
fun `an existing search of the same city and type gets the listing as a match`() {
    // POST twice with the same e-mail + originListingId=500; awaitAsyncTasks()
    // assert one T_MANDATE for that customer, one T_MATCH (mandate, 500), and the agent's interest e-mail
}
```
The second assertion depends on Tasks 6–8; mark this test with a `@Disabled`? No — implement it at the end of Task 8 instead. Here, assert the attach response and the single mandate only; Task 8 adds the match assertion.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

Add to `MandateRepository`:
```kotlin
fun findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(
    customerId: Long,
    mandateType: MandateType,
    cityId: Long,
    listingType: ListingType,
    statuses: Collection<MandateStatus>,
): MandateEntity?

@Query("SELECT COUNT(m) FROM MandateEntity m WHERE m.customerId = :customerId AND m.mandateType = :mandateType AND m.createdAt > :after")
fun countByCustomerIdAndMandateTypeAndCreatedAtAfter(customerId: Long, mandateType: MandateType, after: Date): Long
```
In `create`, after resolving the origin and the customer:
```kotlin
if (origin != null) {
    dao.findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(
        customer.id!!, MandateType.SEARCH, origin.criteria.cityId, origin.criteria.listingType,
        listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED),
    )?.let { existing ->
        eventPublisher.publishEvent(ExistingSearchInterestEvent(existing.id!!, origin.id, TenantContext.get()!!))
        return existing
    }
    if (dao.countByCustomerIdAndMandateTypeAndCreatedAtAfter(customer.id!!, MandateType.SEARCH, Date(now.time - DAY_MILLIS)) >= MAX_NEW_SEARCHES_PER_DAY) {
        throw ConflictException(error = Error(code = ErrorCode.MANDATE_RATE_LIMITED))
    }
}
```
`MAX_NEW_SEARCHES_PER_DAY = 3`, `DAY_MILLIS = 86_400_000L`. `now` is computed before the lookup.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateServiceTest,CreateMandateEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(mandate): attach a listing to an existing search" -m "Refs #169"
```

### Task 6: Create a match at the customer's request

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class Interest(val match: MatchEntity, val created: Boolean)
/** The match of (mandate, listing) the customer asked for; null when the pair can't match or an existing row isn't ACTIVE. */
fun ensureInterest(mandateId: Long, listingId: Long): Interest?
```
- Hard matcher rules apply (`Matcher.score` returns null); `min-score` does **not**. An existing `ACTIVE`/`SHORTLISTED`/... row is reused (`created = false`); a `REJECTED` (or any non-`ACTIVE`) row returns null.

- [ ] **Step 1: Write the failing tests**

In `MatchServiceTest`:
```kotlin
@Test
fun `ensureInterest stores a pair under the min score`() {
    // search APPROVED with an agent, PUBLISHED listing, a low score (< min-score)
    val interest = service.ensureInterest(mandateId = 1, listingId = 2)!!
    assertTrue(interest.created)
    assertEquals(MatchStatus.ACTIVE, interest.match.status)
    assertEquals(0.2, interest.match.score)
}

@Test
fun `ensureInterest reuses an existing active match`() {
    // dao.findByMandateAndListing returns an ACTIVE row → created == false, same id
}

@Test
fun `ensureInterest leaves a rejected match rejected`() {
    // existing REJECTED → null, never saved
}

@Test
fun `ensureInterest still applies the hard rules`() {
    // another city → null
}

@Test
fun `ensureInterest needs a published listing and an agent`() {
    // SOLD → null; mandate without agent → null
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

Add `force: Boolean = false` to `store`; change the min-score block to `if (!force && score.total < minScore)`. Every existing `store` call passes the default. Add:
```kotlin
@Transactional
fun ensureInterest(mandateId: Long, listingId: Long): Interest? {
    val mandate = mandateDao.findWithNeighborhoodsById(mandateId) ?: return null
    if (mandate.mandateType != MandateType.SEARCH || mandate.agentId == null) return null
    val listing = listingDao.findById(listingId).orElse(null) ?: return null
    if (listing.status != ListingStatus.PUBLISHED) return null
    val score = matcher.score(mandate, listing, distances(mandate), today(mandate.tenantId!!)) ?: return null
    val existing = dao.findByMandateAndListing(mandate, listing)
    if (existing != null && existing.status != MatchStatus.ACTIVE) return null
    val outcome = try {
        store(mandate, listing, score, force = true)
    } catch (ex: DataIntegrityViolationException) {
        StoreOutcome.STORED // a concurrent run inserted it: read below
    }
    if (outcome != StoreOutcome.STORED) return null
    val match = dao.findByMandateAndListing(mandate, listing) ?: return null
    return Interest(match, created = existing == null)
}
```

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): add a match at the customer's request" -m "Refs #169"
```

### Task 7: Tell the agent its customer is interested

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestNotifier.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/interest.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestNotifierTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailetTest.kt`

**Interfaces:**
- Produces: `MatchInterestNotifier.send(agent: AgentEntity, customer: CustomerEntity, mandate: MandateEntity, listing: ListingEntity)` — Telegram when `AgentTelegramService.chatId(agent.id!!)` is non-null, e-mail otherwise; a failed Telegram send falls back to the e-mail; a failed e-mail is logged and swallowed (the match is already stored).
- `MatchInterestMailet`: subject `"{{tenant_name}}: {{customer_name}} s'intéresse à un logement"`, template `/mail/match/interest.mjml`; context keys: `tenant_logo_url`, `tenant_name`, `recipient_name`, `customer_name`, `listing_title`, `image_url`, `price`, `place`, `mandate_url` (`{partnerCentralUrl}/mandates/{id}`, null without one).
- `MatchInterestBotlet`: text naming the customer and the listing, plus a `Voir le mandat` button to the mandate's Partner Central page (none without a `partnerCentralUrl`).

- [ ] **Step 1: Write the failing notifier test**

```kotlin
@Test
fun `sends over Telegram when the agent is linked`() { /* chatId 99 → verify botlet, never mailet */ }

@Test
fun `e-mails an agent without a chat`() { /* chatId null → verify mailet */ }

@Test
fun `falls back to the e-mail when Telegram fails`() {
    doThrow(RuntimeException("down")).whenever(botlet).send(any(), any(), any(), any(), eq(99))
    notifier.send(agent, customer, mandate, listing)
    verify(mailet).send(agent, customer, mandate, listing)
}
```
In `MatchInterestMailetTest`, capture the `Email` and assert the subject and the `mandate_url`/`listing_title` context (mirror `VisitRequestedMailetTest`).

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestNotifierTest,MatchInterestMailetTest`
Expected: FAIL.

- [ ] **Step 3: Implement the three classes and the template**

Mirror `VisitRequestedNotifier` / `VisitRequestedMailet` / `VisitRequestedBotlet`; copy `/mail/visit/requested.mjml` to `/mail/match/interest.mjml` and adapt the copy:
- title: "Votre client s'intéresse à un logement"
- body: the customer's name, the listing (title, place, price), and "Ce logement a été ajouté à son mandat de recherche. Proposez-lui la visite depuis son mandat."
- CTA: "Voir le mandat" → `mandate_url`.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestNotifierTest,MatchInterestMailetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): tell the agent its customer is interested in a listing" -m "Refs #169"
```

### Task 8: Register the interest on an existing search

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchInterestEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`

**Interfaces:**
- Consumes: `ExistingSearchInterestEvent`, `MatchService.ensureInterest`, `MatchInterestNotifier`.
- Produces:
```kotlin
fun registerInterest(mandateId: Long, listingId: Long): Long?
```
Creates/reuses the match, tells the mandate's agent **only when the match is created** (a notifier failure is logged, never rolled back), and proposes it at once when the mandate is `APPROVED` and the match is in `MatchService.OPEN_STATUSES`. The listener is `@Async @TransactionalEventListener(AFTER_COMMIT)` with the standard `TenantContext` + `DefaultKVLogger` handling.

- [ ] **Step 1: Write the failing service test**

```kotlin
@Test
fun `a created match is notified and proposed when approved`() {
    doReturn(Interest(match(created = true, mandateStatus = APPROVED), true)).whenever(matchService).ensureInterest(1, 2)
    service.registerInterest(1, 2)
    verify(notifier).send(any(), any(), any(), any())
    verify(proposalService).propose(listOf(9L))
}

@Test
fun `a reused match is not notified again`() {
    doReturn(Interest(match(created = false, mandateStatus = ASSIGNED), false)).whenever(matchService).ensureInterest(1, 2)
    service.registerInterest(1, 2)
    verify(notifier, never()).send(any(), any(), any(), any())
    verify(proposalService, never()).propose(any())
}

@Test
fun `a notifier failure does not fail the interest`() {
    doThrow(RuntimeException("smtp down")).whenever(notifier).send(any(), any(), any(), any())
    assertNotNull(service.registerInterest(1, 2))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

`MatchInterestService` (deps: `MatchService`, `MatchProposalService`, `AgentService`, `CustomerService`, `MatchInterestNotifier`, `KVLogger`) as in the Interfaces; wrap `notifier.send` in `try/catch (Exception)` logging the failure. `MatchInterestEventListener` mirrors `MatchEventListener.onMatchProposed`.

- [ ] **Step 4: Extend the endpoint test**

In `CreateMandateEndpointTest`, after the attach test: `awaitAsyncTasks()`, then assert one `T_MATCH` row of `(existing mandate, listing 500)` and one GreenMail message to the mandate's agent. This proves the event → listener → match chain.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest,CreateMandateEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): register a customer's interest on its search" -m "Refs #169"
```

### Task 9: Propose the origin listing on approval

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListener.kt:31-51`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestServiceTest.kt`

**Interfaces:**
- Produces: `MatchInterestService.proposeOrigin(mandateId: Long): Long?` — ensures the match of the mandate's `originListingId` (no agent notification) and proposes it when the mandate is `APPROVED` and the listing still `PUBLISHED`; null when there is no origin, the listing is gone/sold, or the pair can't match.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `proposeOrigin proposes the origin match`() {
    doReturn(approvedMandate(originListingId = 2)).whenever(mandateService).findByIdOrNull(1)
    doReturn(Interest(openMatch(), false)).whenever(matchService).ensureInterest(1, 2)
    service.proposeOrigin(1)
    verify(proposalService).propose(listOf(9L))
}

@Test
fun `no origin, nothing proposed`() { /* originListingId null → null, never propose */ }

@Test
fun `a sold listing is not proposed`() {
    doReturn(null).whenever(matchService).ensureInterest(1, 2)
    assertNull(service.proposeOrigin(1))
    verify(proposalService, never()).propose(any())
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

Add `proposeOrigin` to `MatchInterestService`. In `MatchEventListener.onMandateApproved`, after `service.matchMandate(...)`, call `interestService.proposeOrigin(event.mandateId)` and log its result (`origin_match_id`). Inject `MatchInterestService` into the listener.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest,MatchEventListenerTest`
Expected: PASS (or `MatchEventListenerTest` does not exist: then run the existing listener tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): propose the origin listing on approval" -m "Refs #169"
```

### Task 10: Offer the listing's agent the mandate first

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDispatcher.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDispatcherTest.kt`

**Interfaces:**
- Consumes: `OriginListingResolver.resolve`.
- Produces: on a mandate with an `originListingId`, the dispatch offers first to that listing's agent, bypassing the ranking but keeping every hard filter (`VERIFIED`, same city, not the customer, not `DECLINED`/`EXPIRED` on this mandate, under `max-load` and `max-pending`). Any of those failing → the normal scoring, unchanged.

- [ ] **Step 1: Write the failing tests**

Add `originListingResolver = mock()` to the `dispatcher(...)` factory. Cases:
```kotlin
@Test
fun `the origin listing's agent is offered first, even when it scores less`() {
    // inCity = [agent 20 (covers all N1) , agent 77 (covers nothing)]
    // resolver(5) → OriginListing(agentId = 77, ...); agentService.search(ids=[77]) → [agent 77]
    // dispatch → assertion: saved agentId == 77 (not 20)
}

@Test
fun `the origin's agent is skipped when it is the customer`() { /* agent 77.party.id == CUSTOMER_PARTY_ID → agent 20 */ }

@Test
fun `the origin's agent is skipped when it already declined`() {
    doReturn(listOf(assignment(agentId = 77, DECLINED))).whenever(assignmentDao).findByMandateAndStatusIn(any(), any())
    /* → agent 20 */
}

@Test
fun `the origin's agent is skipped when it is at capacity`() { /* countByAgentIdsAndStatusIn → 5 for 77 → agent 20 */ }

@Test
fun `the origin's agent is skipped when it is not verified`() { /* search(ids) → empty → agent 20 */ }

@Test
fun `an unknown origin listing falls back to the normal dispatch`() { /* resolver → null → agent 20 */ }
```
Use `assertNothingOffered` where the fallback has no candidate.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDispatcherTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

Add `private val originListingResolver: OriginListingResolver` to the constructor (and `originListingResolver = mock()` to the test's `dispatcher(...)` factory). In `dispatchResult`, after the night check:
```kotlin
val distances = distances(mandate)
val excludeIds = assignmentDao.findByMandateAndStatusIn(mandate, listOf(DECLINED, EXPIRED)).map { it.agentId }.toSet()
val customer = customerService.findById(mandate.customerId)
val excluded = mutableMapOf<CandidateRuleOut, Int>()
val preferred = preferredCandidate(mandate, distances, excludeIds, customer, excluded)
val candidates = findCandidates(mandate, distances, excludeIds, customer)
val scored = if (preferred != null) listOf(preferred) else score(mandate, candidates.eligible, distances, excluded)
```
`preferredCandidate`:
```kotlin
val agentId = mandate.originListingId?.let { originListingResolver.resolve(it)?.agentId } ?: return null
if (agentId in excludeIds) return null
val agent = agentService.search(
    SearchAgentRequest(ids = listOf(agentId), kycStatus = listOf(KycStatus.VERIFIED), cityId = mandate.cityId, limit = 1),
    AgentService.SORT_LEAST_RECENTLY_ASSIGNED,
).firstOrNull() ?: return null
if (agent.party.id == customer.party.id) return null
return score(mandate, listOf(agent), distances, excluded).firstOrNull()
```
Pass `excludeIds` and `customer` into `findCandidates` (drop its own queries). The `DispatchDiagnostics` keeps `candidates.searched`; when the preferred wins, the existing log lines report one candidate. No new `DispatchReason` is needed.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDispatcherTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(mandate): offer a listing's agent the mandate first" -m "Refs #169"
```

### Task 11: Create the mandate from the public listing page (web controller)

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdMandateClient.kt`
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestForm.kt`
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRules.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt:34-38`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdMandateClientTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRulesTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingControllerTest.kt`

**Interfaces:**
- Produces:
```kotlin
// NdMandateClient
fun create(request: CreateMandateRequest): Long

// ListingInterestForm
data class ListingInterestForm(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val movingPeriod: String? = null,
    val website: String? = null, // the honeypot
) {
    fun isHoneypotFilled(): Boolean
    fun toRequest(listingId: Long): CreateMandateRequest
}
// ListingInterestRules
fun validate(form: ListingInterestForm): Map<String, String>
```
- `POST /listings/{id}/interest` (permitted in `SecurityConfiguration`): honeypot filled → 302 to the confirmation, no server call; invalid → the page re-rendered (popup open) with French errors and the typed values; success or 409 `MANDATE_RATE_LIMITED` → 302 to `{listing.url}?interest=done#visit` (the same confirmation in every case); a 400 → the page re-rendered with a global French error; a 404 → the app's 404.

- [ ] **Step 1: Write the failing client and rules tests**

`NdMandateClientTest` (MockRestServiceServer): `create` posts `{"originListingId":123,"firstName":"Awa",...}` to `/v1/mandates` and returns `mandateId` from `{"mandateId": 9}`.
`ListingInterestRulesTest`: each missing field returns its French message; a valid form returns an empty map; a non-E.164 phone and an invalid e-mail are refused; `movingPeriod = "UNKNOWN"` is refused; `website` non-blank is refused (or excluded from the rules and handled by the controller — pick the controller, and test the rules only for the fields).

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdMandateClientTest,ListingInterestRulesTest`
Expected: FAIL.

- [ ] **Step 3: Implement the client, the form and the rules**

`NdMandateClient.create`:
```kotlin
fun create(request: CreateMandateRequest): Long =
    rest.post()
        .uri("/v1/mandates")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body<CreateMandateResponse>()
        ?.mandateId ?: -1
```
`ListingInterestForm.toRequest`: trimmed contact, `movingPeriod = MovingPeriod.entries.firstOrNull { it.name == movingPeriod } ?: MovingPeriod.UNKNOWN`, `mandateType = SEARCH`, `originListingId = listingId`; all criteria left at their defaults (the server reads them from the listing).
`ListingInterestRules`: `firstName`, `lastName`, `email` (`^[^\s@]+@[^\s@]+\.[^\s@]+$`), `whatsappNumber` (`^\+[1-9]\d{6,14}$`), `movingPeriod` (one of the three), with the French messages: "Indiquez votre prénom.", "Indiquez votre nom.", "Saisissez une adresse email valide.", "Indiquez un numéro WhatsApp valide.", "Choisissez votre date d'emménagement.".

- [ ] **Step 4: Implement the controller**

Extract `private fun page(id: Long, model: Model, form: ListingInterestForm, errors: Map<String, String>, done: Boolean): String` from `index()` (fetch listing/images/locations/tenant, add `page`, `interestForm`, `errors`, `done`). `index()` reads `request.getParameter("interest") == "done"`.
`interest()`:
```kotlin
@PostMapping("/listings/{id}/interest")
fun interest(@PathVariable id: Long, @ModelAttribute form: ListingInterestForm, model: Model): Any {
    if (form.isHoneypotFilled()) return confirmation(id)
    val errors = ListingInterestRules.validate(form)
    if (errors.isNotEmpty()) return page(id, model, form, errors, done = false)
    return try {
        mandateClient.create(form.toRequest(id))
        confirmation(id)
    } catch (ex: HttpClientErrorException.Conflict) {
        confirmation(id) // rate limited: same screen, nothing created
    } catch (ex: HttpClientErrorException.BadRequest) {
        page(id, model, form, mapOf("global" to "Votre demande n'a pas pu être envoyée. Vérifiez vos informations."), done = false)
    }
}
private fun confirmation(id: Long): RedirectView = RedirectView("${listingClient.get(id).url ?: "/"}?interest=done#visit").apply {
    setStatusCode(HttpStatus.FOUND)
    setExposeModelAttributes(false)
}
```
Add the constructor dependency `NdMandateClient`. Add the security matcher `requestMatchers(HttpMethod.POST, "/listings/*/interest").permitAll()` and update its comment.

- [ ] **Step 5: Write the failing controller tests**

In `ListingControllerTest`, add the client to the constructor and:
```kotlin
@Test fun `interest creates a mandate and redirects to the confirmation`() {
    // POST /listings/123/interest with valid fields
    // 302 Location: /douala/bonapriso/listing/123/appartement?interest=done#visit
    // captor: CreateMandateRequest(originListingId = 123, firstName = "Awa", movingPeriod = WITHIN_1_MONTH)
}
@Test fun `the honeypot shows the confirmation without creating anything`() { /* website = "spam" → 302, verify(client, never()).create(any()) */ }
@Test fun `invalid contact details re-render the form with French errors`() { /* empty POST → view listing, model errors, never create */ }
@Test fun `a rate-limited request shows the same confirmation`() { /* create throws HttpClientErrorException.Conflict → 302 confirmation */ }
```

- [ ] **Step 6: Run the tests**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdMandateClientTest,ListingInterestRulesTest,ListingControllerTest`
Expected: PASS.

- [ ] **Step 7: Commit (with the approved prototype)**

```bash
git add modules/ndopify-web/src/main/html modules/ndopify-web
git commit -m "feat(web): create a search mandate from a listing page" -m "Refs #169"
```

### Task 12: The listing page's call to action and form (web template)

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/js/listing.js`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/listing.css`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt:87-89,161-172`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`

**Interfaces:**
- Consumes: `page`, `interestForm`, `errors`, `done` from the controller.
- Produces: "Visiter ce logement" in the price card and the bottom bar (absent for `SOLD`/`RENTED`); the `#visit` popup is a real form (contact + moving period + honeypot) posting to `/listings/{id}/interest`; the confirmation state is the server-rendered `done` state; no slot picker anywhere.

- [ ] **Step 1: Write the failing template tests**

```kotlin
@Test fun `a published listing shows the visit call to action`() {
    // `.price-card__cta` text == "Visiter ce logement"; `.cta-bar` has the same link; no `data-interest-form`
}
@Test fun `a closed listing shows no call to action`() { /* SOLD → no .price-card__cta, no .cta-bar__actions */ }
@Test fun `the visit form posts to the listing interest endpoint`() {
    // form[data-listing-interest-form] action == "/listings/123/interest", method post
    // fields firstName, lastName, email, whatsappNumber, movingPeriod (3 values), website (honeypot)
    // no input[name=visitDate], no select[name=visitTime], no input[name=visitType]
}
@Test fun `a failed post re-opens the popup with the errors`() {
    // errors = mapOf("firstName" to "Indiquez votre prénom.") → #visit has class is-open; the field-error shows the message; value kept
}
@Test fun `the confirmation replaces the form`() {
    // done = true → form[hidden], [data-listing-interest-done] visible, text "sous 24 h"
}
```
In `ListingPagesTest`, delete the `visit days and times` test; add `assertEquals` on nothing else (the fields are gone).

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest,ListingPagesTest`
Expected: FAIL.

- [ ] **Step 3: Implement the page model first**

Remove `visitDays`, `visitTimes` and `VisitDay` from `ListingPage`; remove `VISIT_DAYS`, `FIRST_VISIT_TIME`, `LAST_VISIT_TIME` and their generation from `ListingPages`.

- [ ] **Step 4: Implement the template**

- Price card: add
  ```html
  <a class="btn btn-primary price-card__cta" data-modal-open th:href="'#visit'" th:if="${page.closedNotice == null}">
      <svg aria-hidden="true" class="icon"><use href="#i-calendar"/></svg>Visiter ce logement
  </a>
  <p class="price-card__hint" th:if="${page.closedNotice == null}">Un agent vous contacte sous 24 h pour convenir de la visite.</p>
  ```
  and the same link in `.cta-bar__actions`.
- `#visit`: `th:classappend="${errors != null && !errors.isEmpty()} ? 'is-open'"`, title "Visiter ce logement"; the form:
  ```html
  <form class="modal__body interest-form" data-listing-interest-form method="post"
        novalidate th:action="@{/listings/{id}/interest(id=${page.id})}" th:hidden="${done}">
  ```
  fields `firstName`, `lastName`, `email`, `whatsappNumber` (with `data-phone-name`), the three `movingPeriod` radios (`option-grid`), the `website` honeypot in a `.visually-hidden` block, the `global` error, the submit "Envoyer ma demande", the privacy line. Every field echoes `interestForm` (`th:value`, `th:checked`) and shows `errors['field']` in its `.field-error`.
- The confirmation block (`th:hidden="${!done}"`) with the check icon, "Merci, votre demande est enregistrée", "Un agent vous contacte sous 24 h par WhatsApp ou par e-mail.", the two steps and "Fermer".
- Keep the `#ask` drawer markup; do not add its trigger.

- [ ] **Step 5: Implement the assets**

`listing.js`: replace `initVisitForm` with `initInterestForm(modal)` — the intl-tel-input binding and the `refresh` gating on `[required]` (presence), **no** `preventDefault` and no fake confirmation; bootstrap it on `#visit` when it has `[data-listing-interest-form]`. Remove the visit-type/date/time validation and the fake text.
`listing.css`: remove the `.interest-form__days` rules; add `.interest-form .option-grid { margin-top: var(--space-sm); }`, the `.interest-done__steps` rules and `.interest-done .btn { margin-top: var(--space-xl); }` (copy from the prototype's `annonce.css`).

- [ ] **Step 6: Run the tests**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest,ListingPagesTest,ListingControllerTest,MatchTemplateTest`
Expected: PASS; `MatchTemplateTest` proves `match.html` is unchanged.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): the listing page's visit call to action" -m "Refs #169"
```

### Task 13: Document the new behaviour

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **mandate**, **listing** and **match** sections)
- Modify: `modules/ndopify-web/CLAUDE.md` (the `ui/listing/` and `config/` sections)

- [ ] **Step 1: Update the server doc**

In **mandate**: `create` takes `originListingId`; the criteria and the budget come from the listing (`OriginListingResolver`, implemented by the listing domain); the origin is kept only for a `PUBLISHED` listing; an unknown id is 404 `LISTING_NOT_FOUND`; an active SEARCH mandate of the same city/type gets the listing as a match instead (`ExistingSearchInterestEvent`); 3 new SEARCH mandates per customer per 24 h (`MANDATE_RATE_LIMITED`); the dispatch offers the origin listing's agent first, with the normal hard filters, then falls back. In **match**: `ensureInterest` (hard rules, no min-score, reuses a row, a rejected one stays), the interest notifier (Telegram/e-mail), and the origin proposal on approval. In **listing**: the resolver's role.

- [ ] **Step 2: Update the web doc**

In `ui/listing/`: the page now has the "Visiter ce logement" CTA (desktop card + mobile bar) and a real form posting to `/listings/{id}/interest` (no slot picker); `ListingInterestForm` / `ListingInterestRules`; the confirmation state (`?interest=done#visit`) is identical in every case; `NdMandateClient.create`; the security matcher.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-web/CLAUDE.md
git commit -m "docs(web): document the listing search-mandate funnel" -m "Refs #169"
```

### Task 14: Verify the whole branch

- [ ] **Step 1: Format and verify each module**

```bash
cd modules/ndopify-dto && mvn verify
cd ../ndopify-server && mvn verify   # local MySQL required
cd ../ndopify-web && mvn verify
```
Expected: BUILD SUCCESS in each; no ktlint violation; JaCoCo coverage at or above the module thresholds.

- [ ] **Step 2: Check the tree**

`git status` is clean except the root `pom.xml` `<ndopify-dto.version>` at `0.0.94-SNAPSHOT` (never commit it).
`git log --oneline origin/main..HEAD` lists only this story's commits, each touching at most one library module (`modules/ndopify-dto` in the first one).

- [ ] **Step 3: Report**

Tell the user the story is ready for `push 169`; do not push.
