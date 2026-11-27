# Customer, party, KYC, user and assignment reads — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Only the record's own party (or the agent a customer's mandate is assigned to) or an admin reads a customer,
a party, an identification, a payment method, a user or an assignment; a non-admin's search is narrowed to its own
records (or is admin-only), and the agent search by Mobile Money number is admin-only.

**Architecture:** One access service per domain holds the rule. The endpoints run it for the reads (the module rule: an
endpoint runs the domain's read access check), so the server's own reads (`CustomerService.findById`,
`AssignmentService.findById`, `AgentService.search` in the dispatcher, `UserService.findById`, the payment-method and
identification loads) stay unchecked. The customer domain needs the mandate→agent relation: it declares a
`CustomerMandateLookup` interface, implemented by the mandate domain (the `OriginListingResolver` pattern: the consumer
declares, the provider implements), so the customer package never depends on the mandate repository.

**Tech Stack:** Kotlin, Spring Boot, `TestRestTemplate` against MySQL, JUnit 5 / `kotlin.test`, mockito-kotlin,
Flyway test fixtures under `db/test/`.

**Spec:** GitHub issue #354 (enriched body: *Context*, *Web app callers*, *Acceptance criteria*, *Decisions and
assumptions*, *Risks*). This plan is a revision of the 2026-11-26 draft against `origin/main` at `b04e555d` (after
#379 / #380 / #297); it keeps the draft's design and corrects it where the code moved. The web-app caller audit in the
issue is what pins the customer-search decision: an admin-only customer search would break Partner Central, so a
non-admin's search is narrowed to its own customers instead.

## Global Constraints

- `UI: no` — no prototype, no HTML/Thymeleaf, no web-app change. `Shared code: none` — no `ndopify-dto` or
  `ndopify-web-core` change, no library release. No migration.
- 403 on a record of the tenant that the caller may not read, after the 404: `HTTP_ACCESS_DENIED` for customers,
  parties, identifications, payment methods, users and the agent Mobile Money filter; `ASSIGNMENT_ACCESS_DENIED` for
  assignments. An unknown or other-tenant id keeps its 404 (`@TenantId` / `findById`).
- **Admin** is `CallerService.isAdmin` exactly: an `admin-console` token with the `admin` role. It reads anything.
- **Customer get:** the customer itself (`customer.party.id` = the caller's party), the agent of a mandate *assigned*
  to it (`MandateEntity.agentId`), or an admin. An agent with only a PENDING offer is refused.
- **Customer search:** an admin's as asked; a non-admin's limited to the customer of its own party plus, for an agent,
  the customers of its mandates (any status). A request naming only foreign ids returns nothing, never a 403. A
  non-admin with no party is 403.
- **Identification / payment-method search:** a non-admin is silently forced to its own party (as the KYC case search
  already is); a caller with no party is 403. Their get and the party search are as in the tasks.
- **Assignment search:** a non-admin is forced to its own agent; a request naming another `agent-id` is 403; a caller
  without an agent is 403.
- The checks live at the endpoints (through the access services). The server's own reads stay unchecked.
- **Order:** 401 `AUTH_UNAUTHORIZED` without a token (the security filter), then the load's 404, then the 403, then
  today's response. An unknown or other-tenant id therefore keeps its 404, and a caller with no membership or no party
  gets a 403 on a get — never a 500, never an empty 200.
- **Compatibility (audited at `origin/main`):** Partner Central reads a customer only on its own mandates/visits and
  searches only its own customers' ids; its `GET /v1/assignments?mandate-id=` relies on the forced own-agent rule
  (same result); its mandate page already says the client's details unlock on acceptance and treats the contact block
  as optional ("Null when the customer can't be loaded"). ndopify-web reads the match's customer with the customer's
  own `public-portal` token. The e-signature portal calls none of these endpoints. No wider read is needed.
- Test conventions: `kotlin.test` assertions, mockito-kotlin mocks, one endpoint test class per operation with its
  SQL fixture. The base class's caller is user **11**, tenant 1, `admin-console`, `ROLE_USER`; admin cases override
  `roles = arrayOf(RoleCode.ADMIN)` (the contract tests' `private var admin` getter pattern). A caller without a
  membership/party is user **13** (or **14** where a fixture already uses 13).
- `mvn verify` green (ktlint, tests, JaCoCo); ndopify-server tests need a local MySQL.

## Review Focus

1. **An agent with only a PENDING offer on a customer's mandate** asks for the customer → 403, not 200. Task 2.
2. **A non-admin searches customers with a foreign `id` or `party-id`** → none of that customer, no 403; and **with no
   filter** → only the customers of its own mandates. Task 3.
3. **A caller with no party** (no membership, or a membership without a party) on any read → 403, never a 500 or an
   empty 200. Tasks 1, 4–13.
4. **The server's own reads without a caller** (`AgentService.search` in `MandateDispatcher`, `CustomerService.findById`
   in the match/visit/listener paths, `AssignmentService.findById` in the Telegram callbacks and jobs) → still work;
   no check moved into those methods. Tasks 1, 10, 11, 12.
5. **The user itself** (`GET /v1/users/{its id}`) → 200, while another user → 403; unknown, deleted and non-member
   ids stay 404 before the 403. Task 10.

---

### Task 1: The customer read rule

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/service/CustomerMandateLookup.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateCustomerLookup.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/service/CustomerAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/dao/CustomerRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/service/CustomerService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/service/CustomerAccessServiceTest.kt`

**Interfaces — Produces:**

```kotlin
/** The mandate relation a customer read needs: implemented by the mandate domain (see OriginListingResolver). */
interface CustomerMandateLookup {
    /** The ids of the customers of [agentId]'s mandates, any status. */
    fun findCustomerIds(agentId: Long): List<Long>

    /** Whether one of [customerId]'s mandates is assigned to [agentId]. */
    fun isCustomerOfAgent(customerId: Long, agentId: Long): Boolean
}

@Service
class CustomerAccessService(
    private val callerService: CallerService,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mandateLookup: CustomerMandateLookup,
) {
    fun ensureCanRead(customer: CustomerEntity)
    /** Null when the caller can read no customer (it is neither a customer nor an agent). */
    fun restrict(request: SearchCustomerRequest): SearchCustomerRequest?
}
```

- `MandateCustomerLookup` is a `@Service` in `mandate/server/service` implementing `CustomerMandateLookup` with
  `dao.findCustomerIdsByAgentId(agentId)` and `dao.existsByCustomerIdAndAgentId(customerId, agentId)`.
- `MandateRepository` gains
  `@Query("SELECT DISTINCT m.customerId FROM MandateEntity m WHERE m.agentId = :agentId") fun findCustomerIdsByAgentId(@Param("agentId") agentId: Long): List<Long>`
  and `fun existsByCustomerIdAndAgentId(customerId: Long, agentId: Long): Boolean`.
- `CustomerRepository` gains `fun findByPartyId(partyId: Long): CustomerEntity?` (`findByParty` stays for
  `findOrCreate`); `CustomerService` gains
  `fun findByPartyIdOrNull(partyId: Long): CustomerEntity? = dao.findByPartyId(partyId)`.
- `ensureCanRead`: `getPrincipal()` (401 without a token); return for `isAdmin(principal)`; return when
  `customer.party.id != null && customer.party.id == findPartyIdOrNull(principal)`; return when the caller's agent
  (`agentService.findByPartyIdOrNull(partyId)`) has `customer.id` as one of its mandates
  (`mandateLookup.isCustomerOfAgent`); else `ForbiddenException(Error(ErrorCode.HTTP_ACCESS_DENIED))`. A null party id
  is a 403 before the agent lookup (never an NPE, never a repository call with a null id).
- `restrict`: return `request` for an admin; 403 when `findPartyIdOrNull` is null; build the allowed ids from
  `customerService.findByPartyIdOrNull(partyId)?.id` plus, when its party is an agent,
  `mandateLookup.findCustomerIds(agentId)`; `null` when that set is empty, so the endpoint answers an empty list (never a
  403 for a query); `request.copy(ids = allowed)` when the request has no `ids`, else
  `request.copy(ids = request.ids.filter { it in allowed }.distinct())`, and `null` when that intersection is empty.
  `party-id` is left to the query and only narrows.
- KDoc on the class: the rule and its boundary — "reads the customer itself, the agent of one of its assigned
  mandates, or an admin; a non-admin's search is narrowed to those customers, and the server's own reads don't call
  this".

- [ ] **Step 1: Write the failing test** `CustomerAccessServiceTest`:

```kotlin
class CustomerAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val mandateLookup = mock<CustomerMandateLookup>()
    private val principal = mock<JwtPrincipal>()
    private val service = CustomerAccessService(callerService, customerService, agentService, mandateLookup)
    private val customer = CustomerEntity(id = 1, party = PartyEntity(id = 100))

    @BeforeEach fun setUp() = doReturn(principal).whenever(callerService).getPrincipal()

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    // ensureCanRead: admin, itself, agent of its assigned mandate, another agent, offered-only, no party.
    // restrict: admin unchanged, own customer, agent's customers, foreign id -> null, no party -> 403,
    // a party that is neither -> null, the request's ids intersected.
}
```

Implement all the cases above; e.g. the offered-only case stubs
`agentService.findByPartyIdOrNull(200L)` → `AgentEntity(id = 20)`, `mandateLookup.isCustomerOfAgent(1L, 20L)` → `false`
and asserts 403.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CustomerAccessServiceTest`
Expected: FAIL — the classes do not exist.

- [ ] **Step 3: Implement** the interface, the mandate lookup, the two repository methods, the service method and
  `CustomerAccessService` with the signatures above. Import `AgentService`, `CallerService`, `JwtPrincipal`,
  `ForbiddenException`, `Error`, `ErrorCode`, `SearchCustomerRequest`.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=CustomerAccessServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateCustomerLookup.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/service/CustomerAccessServiceTest.kt
git commit -m "feat(server): add the customer read rule" -m "Refs #354"
```

---

### Task 2: `GET /v1/customers/{id}` — itself, its assigned agent, or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/endpoint/CustomerEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/endpoint/GetCustomerEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/customer/GetCustomerEndpoint.sql`

**Interfaces — Consumes:** `CustomerAccessService.ensureCanRead(customer: CustomerEntity)` (Task 1).

- [ ] **Step 1: Extend the fixture** — add party 101 (another customer), party 102 (an agent), a location, agent 20,
  customer 3 (party 101), the caller's user/membership rows, a mandate of customer 1 assigned to agent 20, and a
  PENDING offer of a mandate of customer 3 to agent 20:

```sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (101, 1, 'kim.possible@gmail.com', 'Kim', 'Possible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 1, 'agent@gmail.com', 'The', 'Agent', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_CUSTOMER (id, tenant_id, party_id, whatsapp_number) VALUES
    (3, 1, 101, '+23763333333');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 102, 2370201);

-- The caller is user 11 (party 100, customer 1), 12 (party 101, customer 3), 13 (party 102, agent 20);
-- user 14 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'agent@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false),
    (13, 1, 13, 102, false);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (11, 1, 1, 20, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (13, 1, 3, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_ASSIGNMENT(id, tenant_id, mandate_id, agent_id, status, score, created_at, modified_at, expires_at) VALUES
    (1, 1, 13, 20, 'PENDING', 0.75, '2024-06-10 10:00:00', '2024-06-10 10:30:00', '2099-01-01 00:00:00');
```

- [ ] **Step 2: Write the failing tests** — add the `admin`/`roles` getter and:

```kotlin
private var admin = false
override val roles: Array<String>
    get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

@Test fun `agent reads the customer of its mandate`() {
    userId = 13L
    assertEquals(HttpStatus.OK, rest.getForEntity("/v1/customers/1", GetCustomerResponse::class.java).statusCode)
}

@Test fun `another customer - forbidden`() {
    userId = 12L
    val response = rest.getForEntity("/v1/customers/1", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `offered agent - forbidden`() {
    userId = 13L
    val response = rest.getForEntity("/v1/customers/3", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `admin reads another customer`() {
    admin = true
    assertEquals(HttpStatus.OK, rest.getForEntity("/v1/customers/3", GetCustomerResponse::class.java).statusCode)
}

@Test fun `caller without a party - forbidden`() {
    userId = 14L
    assertEquals(HttpStatus.FORBIDDEN, rest.getForEntity("/v1/customers/1", ErrorResponse::class.java).statusCode)
}
```

The existing `get` runs as user 11 (the owner) and stays green; `customer not found` and `customer from another
tenant` stay 404. The offered-agent 403 is the read Partner Central's mandate page already treats as optional (its
customer block is nullable), so no web-app change is needed.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetCustomerEndpointTest`
Expected: FAIL — the forbidden/offered tests return 200.

- [ ] **Step 4: Implement** — inject `private val accessService: CustomerAccessService` and check after the load:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: Long): GetCustomerResponse {
    val customer = service.findById(id)
    accessService.ensureCanRead(customer)
    return GetCustomerResponse(customer = mapper.toCustomer(customer))
}
```

KDoc: `/** The customer itself, the agent of one of its assigned mandates, or an admin ([CustomerAccessService]). */`

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetCustomerEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/endpoint/CustomerEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/endpoint/GetCustomerEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/customer/GetCustomerEndpoint.sql
git commit -m "feat(server): only the customer, its agent or an admin reads a customer" -m "Refs #354"
```

---

### Task 3: `GET /v1/customers` — only the caller's own customers

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/endpoint/CustomerEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/endpoint/SearchCustomerEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/customer/SearchCustomerEndpoint.sql`

**Interfaces — Consumes:** `CustomerAccessService.restrict(request): SearchCustomerRequest?` (Task 1).

- [ ] **Step 1: Extend the fixture** — add party 103 (an agent), a location, agent 20, a mandate of customers 1 and 2
  assigned to it, a mandate of customer 3 not assigned, and the caller rows (user 11 → party 100, 12 → party 101,
  13 → party 103; user 14 has no membership):

```sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (103, 1, 'agent@gmail.com', 'The', 'Agent', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 103, 2370201);

INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'agent@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false),
    (13, 1, 13, 103, false);

INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
    rooms, price, price_periodicity_type, currency_code, created_at, modified_at
) VALUES
    (11, 1, 1, 20, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (12, 1, 2, 20, 'ASSIGNED', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (13, 1, 3, null, 'PENDING_ASSIGNMENT', 'TENANT', 'SEARCH', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

- [ ] **Step 2: Write the failing tests** — the existing tests run as an admin (`private var admin = true`), which
  keeps their expectations; add a non-admin block:

```kotlin
private var admin = true
override val roles: Array<String>
    get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

@Test fun `agent sees only the customers of its mandates`() {
    admin = false
    userId = 13L
    val response = rest.getForEntity("/v1/customers", SearchCustomerResponse::class.java)
    assertEquals(listOf(1L, 2L), response.body!!.customers.map { it.id })
}

@Test fun `agent asking for another agent's customer gets none`() {
    admin = false
    userId = 13L
    val response = rest.getForEntity("/v1/customers?id=3&party-id=102", SearchCustomerResponse::class.java)
    assertTrue(response.body!!.customers.isEmpty())
}

@Test fun `customer sees only itself`() {
    admin = false
    userId = 11L
    val response = rest.getForEntity("/v1/customers", SearchCustomerResponse::class.java)
    assertEquals(listOf(1L), response.body!!.customers.map { it.id })
}

@Test fun `caller without a party - forbidden`() {
    admin = false
    userId = 14L
    val response = rest.getForEntity("/v1/customers", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchCustomerEndpointTest`
Expected: FAIL — a non-admin still gets every customer.

- [ ] **Step 4: Implement** — restrict before searching; a null restriction returns an empty response:

```kotlin
@GetMapping
fun search(@ModelAttribute request: SearchCustomerRequest): SearchCustomerResponse {
    val restricted = accessService.restrict(request) ?: return SearchCustomerResponse()
    val customers = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
    return SearchCustomerResponse(customers = customers.map { customer -> mapper.toCustomerSummary(customer) })
}
```

KDoc: `/** A non-admin gets only its own customer and the customers of its mandates ([CustomerAccessService.restrict]). */`

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchCustomerEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/endpoint/CustomerEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/customer/server/endpoint/SearchCustomerEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/customer/SearchCustomerEndpoint.sql
git commit -m "feat(server): restrict a customer search to the caller's customers" -m "Refs #354"
```

---

### Task 4: `GET /v1/parties` — admin only

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/PartyAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PartyEndpoints.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/PartyAccessServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchPartyEndpointTest.kt`

**Interfaces — Produces:** `PartyAccessService.ensureCanSearch()` — 401 without a token, 403 `HTTP_ACCESS_DENIED`
unless `CallerService.isAdmin(principal)`.

- [ ] **Step 1: Write the failing unit test** `PartyAccessServiceTest`: admin → no throw; non-admin → 403
  `HTTP_ACCESS_DENIED` (mock `CallerService` and `JwtPrincipal`, `doReturn(principal).whenever(callerService).getPrincipal()`).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=PartyAccessServiceTest`
Expected: FAIL — `ensureCanSearch` does not exist.

- [ ] **Step 3: Implement** `ensureCanSearch` with the class's existing 403 exception (extract a private
  `accessDenied()` if not present). Widen the class KDoc from "who may change a party" to "who may read or change a
  party": `ensureIsParty` for a write, `ensureCanSearch` for the search, admin excepted.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=PartyAccessServiceTest`
Expected: PASS.

- [ ] **Step 5: Write the failing endpoint test** — in `SearchPartyEndpointTest`, add the `admin`/`roles` getter with
  `private var admin = true` (the legacy tests keep today's expectations), and:

```kotlin
@Test fun `non-admin - forbidden`() {
    admin = false
    val response = rest.getForEntity("/v1/parties", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchPartyEndpointTest`
Expected: FAIL — the non-admin gets 200.

- [ ] **Step 7: Implement** — inject `private val accessService: PartyAccessService` into `PartyEndpoints` and call
  `accessService.ensureCanSearch()` first in `search`.

- [ ] **Step 8: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchPartyEndpointTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/PartyAccessService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PartyEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/PartyAccessServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchPartyEndpointTest.kt
git commit -m "feat(server): only an admin searches parties" -m "Refs #354"
```

---

### Task 5: The identification and payment-method read rules

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycAccessService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/KycAccessServiceTest.kt`

**Interfaces — Produces:**

```kotlin
/** The identification's own party or an admin. */
fun ensureCanRead(identification: IdentificationEntity)

/** The payment method's own party or an admin. */
fun ensureCanRead(paymentMethod: PaymentMethodEntity)

/** A non-admin's search is forced to its own party, whatever partyId it passes. */
fun restrict(request: SearchIdentificationRequest): SearchIdentificationRequest
fun restrict(request: SearchPaymentMethodRequest): SearchPaymentMethodRequest
```

- Both `ensureCanRead` overloads delegate to `ensureIsPartyOrAdmin(entity.party.id)`.
- Both `restrict` overloads follow the existing KYC one: return the request for an admin; else 403 when the caller has
  no party; else `request.copy(partyId = callerPartyId)`. Extract the shared "admin → null, no party → 403, else the
  caller's party" step into a private helper and reuse it in the existing `restrict(SearchKycCaseRequest)`.

- [ ] **Step 1: Write the failing tests** — extend `KycAccessServiceTest` with:

```kotlin
@Test fun `party reads its own identification`() {
    whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
    service.ensureCanRead(IdentificationEntity(id = "id-1", party = PartyEntity(id = 100)))
}

@Test fun `another party may not read the identification`() {
    whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
    assertDenied { service.ensureCanRead(IdentificationEntity(id = "id-1", party = PartyEntity(id = 200))) }
}

@Test fun `admin reads another party's payment method`() {
    whenever(callerService.isAdmin(principal)).thenReturn(true)
    service.ensureCanRead(PaymentMethodEntity(id = "pm-1", party = PartyEntity(id = 200)))
}

@Test fun `identification search is forced to the caller party`() {
    whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
    assertEquals(100L, service.restrict(SearchIdentificationRequest(partyId = 999L)).partyId)
}

@Test fun `payment method search of a party-less caller is denied`() {
    whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
    assertDenied { service.restrict(SearchPaymentMethodRequest()) }
}

@Test fun `admin identification search is not restricted`() {
    whenever(callerService.isAdmin(principal)).thenReturn(true)
    val request = SearchIdentificationRequest(partyId = 999L)
    assertSame(request, service.restrict(request))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=KycAccessServiceTest`
Expected: FAIL — the overloads do not exist.

- [ ] **Step 3: Implement** the overloads and the shared private helper. Keep the existing KYC test green.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=KycAccessServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycAccessService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/KycAccessServiceTest.kt
git commit -m "feat(server): add the identification and payment-method read rules" -m "Refs #354"
```

---

### Task 6: `GET /v1/identifications/{id}` — its party or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/IdentificationEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetIdentificationEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/party/GetIdentificationEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureCanRead(identification: IdentificationEntity)` (Task 5).

- [ ] **Step 1: Extend the fixture** — add party 101, identification `id-101`, and the caller rows (user 11 → party
  100, 12 → party 101; user 13 has no membership).

- [ ] **Step 2: Write the failing tests** — add the `admin`/`roles` getter and:

```kotlin
@Test fun `another party - forbidden`() {
    userId = 12L
    val response = rest.getForEntity("/v1/identifications/id-100", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `admin reads another party's identification`() {
    admin = true
    assertEquals(HttpStatus.OK, rest.getForEntity("/v1/identifications/id-101", GetIdentificationResponse::class.java).statusCode)
}

@Test fun `caller without a party - forbidden`() {
    userId = 13L
    assertEquals(HttpStatus.FORBIDDEN, rest.getForEntity("/v1/identifications/id-100", ErrorResponse::class.java).statusCode)
}
```

The existing `get` runs as user 11 (party 100) and stays green; the 404 cases stay 404.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetIdentificationEndpointTest`
Expected: FAIL — the forbidden tests get 200.

- [ ] **Step 4: Implement** — inject `private val accessService: KycAccessService` and check after the load:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: String): GetIdentificationResponse {
    val identification = service.findById(id)
    accessService.ensureCanRead(identification)
    return GetIdentificationResponse(identification = mapper.toIdentification(identification))
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetIdentificationEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/IdentificationEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetIdentificationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/GetIdentificationEndpoint.sql
git commit -m "feat(server): only the identification's party or an admin reads it" -m "Refs #354"
```

---

### Task 7: `GET /v1/identifications` — a non-admin sees only its own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/IdentificationEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchIdentificationEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/party/SearchIdentificationEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.restrict(request: SearchIdentificationRequest)` (Task 5).

- [ ] **Step 1: Extend the fixture** — add the caller rows (user 11 → party 100, 12 → party 101; user 13 has no
  membership).
- [ ] **Step 2: Write the failing tests** — the legacy tests run as an admin (`private var admin = true`); add:

```kotlin
@Test fun `another partyId is ignored`() {
    admin = false
    userId = 11L
    val response = rest.getForEntity("/v1/identifications?partyId=101", SearchIdentificationResponse::class.java)
    assertEquals(setOf("id-100-a", "id-100-b"), response.body!!.identifications.map { it.id }.toSet())
}

@Test fun `caller without a party - forbidden`() {
    admin = false
    userId = 13L
    val response = rest.getForEntity("/v1/identifications", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchIdentificationEndpointTest`
Expected: FAIL — a non-admin still gets party 101's identification.

- [ ] **Step 4: Implement** — restrict before searching:

```kotlin
@GetMapping("")
fun search(@ModelAttribute request: SearchIdentificationRequest): SearchIdentificationResponse {
    val restricted = accessService.restrict(request)
    val identifications = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
    return SearchIdentificationResponse(
        identifications = identifications.map { identification -> mapper.toIdentificationSummary(identification) },
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchIdentificationEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/IdentificationEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchIdentificationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/SearchIdentificationEndpoint.sql
git commit -m "feat(server): restrict an identification search to the caller's party" -m "Refs #354"
```

---

### Task 8: `GET /v1/payment-methods/{id}` — its party or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PaymentMethodEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetPaymentMethodEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/party/GetPaymentMethodEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureCanRead(paymentMethod: PaymentMethodEntity)` (Task 5).

- [ ] **Step 1: Extend the fixture** — add party 101, payment method `pm-101`, and the caller rows (user 11 → party
  100, 12 → party 101; user 13 has no membership).
- [ ] **Step 2: Write the failing tests** — same shape as Task 6: another party → 403 `HTTP_ACCESS_DENIED`, admin
  reads `pm-101`, a caller without a party → 403. The existing `get` stays green as user 11 (party 100).
- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetPaymentMethodEndpointTest`
Expected: FAIL — the forbidden tests get 200.

- [ ] **Step 4: Implement** — inject `private val accessService: KycAccessService` and check after the load:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: String): GetPaymentMethodResponse {
    val paymentMethod = service.findById(id)
    accessService.ensureCanRead(paymentMethod)
    return GetPaymentMethodResponse(paymentMethod = mapper.toPaymentMethod(paymentMethod))
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetPaymentMethodEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PaymentMethodEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetPaymentMethodEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/GetPaymentMethodEndpoint.sql
git commit -m "feat(server): only the payment method's party or an admin reads it" -m "Refs #354"
```

---

### Task 9: `GET /v1/payment-methods` — a non-admin sees only its own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PaymentMethodEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchPaymentMethodEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/party/SearchPaymentMethodEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.restrict(request: SearchPaymentMethodRequest)` (Task 5).

- [ ] **Step 1: Extend the fixture** — add the caller rows (user 11 → party 100, 12 → party 101; user 13 has no
  membership).
- [ ] **Step 2: Write the failing tests** — the legacy tests run as an admin (`private var admin = true`); add the
  "another partyId is ignored" (user 11, `?partyId=101` → only `pm-100-*`) and "caller without a party - forbidden"
  tests, as in Task 7.
- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchPaymentMethodEndpointTest`
Expected: FAIL — a non-admin still gets party 101's payment method.

- [ ] **Step 4: Implement** — restrict before searching:

```kotlin
@GetMapping
fun search(@ModelAttribute request: SearchPaymentMethodRequest): SearchPaymentMethodResponse {
    val restricted = accessService.restrict(request)
    val paymentMethods = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
    return SearchPaymentMethodResponse(
        paymentMethods = paymentMethods.map { paymentMethod -> mapper.toPaymentMethodSummary(paymentMethod) },
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchPaymentMethodEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PaymentMethodEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchPaymentMethodEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/SearchPaymentMethodEndpoint.sql
git commit -m "feat(server): restrict a payment method search to the caller's party" -m "Refs #354"
```

---

### Task 10: `GET /v1/users/{id}` — the user itself or an admin

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/UserAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/endpoint/UserEndpoints.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/UserAccessServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/GetUserEndpointTest.kt`

**Interfaces — Produces:**

```kotlin
@Service
class UserAccessService(private val callerService: CallerService) {
    /** The user itself or an admin. 401 without a token, 403 HTTP_ACCESS_DENIED otherwise. */
    fun ensureCanRead(user: UserEntity)
}
```

- `ensureCanRead`: `getPrincipal()` (401); return for `isAdmin(principal)`; return when
  `principal.getUserId() == user.id`; else 403 `HTTP_ACCESS_DENIED`. A null `user.id` is a 403.

- [ ] **Step 1: Write the failing unit test** `UserAccessServiceTest`: admin → no throw; self
  (`whenever(principal.getUserId()).thenReturn(1L)`, `UserEntity(id = 1)`) → no throw; another user → 403.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=UserAccessServiceTest`
Expected: FAIL — `UserAccessService` does not exist.

- [ ] **Step 3: Implement** the service and inject it into `UserEndpoints`, checking after `service.findById(id)`.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=UserAccessServiceTest`
Expected: PASS.

- [ ] **Step 5: Write the failing endpoint tests** — the existing fixture already has users 1–5, so no fixture change.
  Set `userId = 1L` in `get` and in `party of the tenant of the request` (with `overrideTenantId = 2L`), and
  `userId = 2L` in `user without party`; the 404 cases keep running as user 1. Add the `admin`/`roles` getter and:

```kotlin
@Test fun `another user - forbidden`() {
    userId = 2L
    val response = rest.getForEntity("/v1/users/1", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `admin reads another user`() {
    admin = true
    assertEquals(HttpStatus.OK, rest.getForEntity("/v1/users/1", GetUserResponse::class.java).statusCode)
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetUserEndpointTest`
Expected: FAIL — the forbidden test gets 200.

- [ ] **Step 7: Implement** the endpoint check, then run the tests to verify they pass:

Run: `cd modules/ndopify-server && mvn test -Dtest=GetUserEndpointTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/UserAccessService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/endpoint/UserEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/UserAccessServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/GetUserEndpointTest.kt
git commit -m "feat(server): only the user itself or an admin reads a user" -m "Refs #354"
```

---

### Task 11: `GET /v1/agents?mobile-money-number=` — admin only

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/SearchAgentEndpointTest.kt`

**Interfaces — Produces:** `AgentAccessService.ensureCanSearchByMobileMoneyNumber(request: SearchAgentRequest)` —
returns immediately when `request.mobileMoneyNumber == null`; else 401 without a token and 403 `HTTP_ACCESS_DENIED`
unless `CallerService.isAdmin(principal)`.

- [ ] **Step 1: Write the failing unit tests** — extend `AgentAccessServiceTest`:

```kotlin
@Test fun `admin searches by mobile money number`() {
    whenever(callerService.isAdmin(principal)).thenReturn(true)
    service.ensureCanSearchByMobileMoneyNumber(SearchAgentRequest(mobileMoneyNumber = "+237600000000"))
}

@Test fun `non-admin may not search by mobile money number`() {
    assertDenied { service.ensureCanSearchByMobileMoneyNumber(SearchAgentRequest(mobileMoneyNumber = "+237600000000")) }
}

@Test fun `non-admin may search without the mobile money filter`() {
    service.ensureCanSearchByMobileMoneyNumber(SearchAgentRequest(cityId = 1L))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentAccessServiceTest`
Expected: FAIL — the method does not exist.

- [ ] **Step 3: Implement** the method, then run it to verify it passes:

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentAccessServiceTest`
Expected: PASS.

- [ ] **Step 4: Write the failing endpoint test** — in `SearchAgentEndpointTest`, add the `admin`/`roles` getter with
  `private var admin = false`; set `admin = true` in `by mobile money number`; add:

```kotlin
@Test fun `mobile money search of a non-admin - forbidden`() {
    val uri = URI.create("/v1/agents?mobile-money-number=%2B237600000000")
    val response = rest.getForEntity(uri, ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

The other tests stay non-admin (no filter) and keep today's expectations.

- [ ] **Step 5: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchAgentEndpointTest`
Expected: FAIL — the mobile-money test gets 200 for a non-admin.

- [ ] **Step 6: Implement** — inject `private val accessService: AgentAccessService` into `AgentEndpoints` and call
  `accessService.ensureCanSearchByMobileMoneyNumber(request)` first in `search`; run the tests to verify they pass:

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchAgentEndpointTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/SearchAgentEndpointTest.kt
git commit -m "feat(server): only an admin searches agents by mobile money number" -m "Refs #354"
```

---

### Task 12: `GET /v1/assignments/{id}` — the offer's agent or an admin

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AssignmentEndpoints.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetAssignmentEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/mandate/GetAssignmentEndpoint.sql`

**Interfaces — Produces:**

```kotlin
@Service
class AssignmentAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
) {
    fun ensureCanRead(assignment: AssignmentEntity)  // its agent or an admin; 403 ASSIGNMENT_ACCESS_DENIED
    fun callerAgentId(): Long  // 401 without a token, 403 ASSIGNMENT_ACCESS_DENIED without an agent
}
```

The `restrict(request: SearchAssignmentRequest)` method is added in Task 13.

- `ensureCanRead`: `getPrincipal()` (401); return for `isAdmin(principal)`; else the caller's agent
  (`findPartyIdOrNull(principal)` → `agentService.findByPartyIdOrNull` → id) must equal `assignment.agentId`, else 403
  `ASSIGNMENT_ACCESS_DENIED`. A null party id or no agent is a 403 before the entity comparison.
- `callerAgentId` is the moved private helper of `AssignmentService`; `AssignmentService` replaces its
  `callerService` constructor parameter with `private val accessService: AssignmentAccessService`, calls
  `accessService.callerAgentId()` in `accept` / `decline` (each keeps `findById(id)` first: 404 before 401/403), and
  drops its private `callerAgentId` and the `CallerService` / `JwtPrincipal` imports. Update `AssignmentServiceTest`'s
  constructor to `accessService = mock()` (in place of `callerService = mock()`).

- [ ] **Step 1: Write the failing unit test** `AssignmentAccessServiceTest`: admin → allowed; own agent → allowed;
  another agent → 403; no agent → 403; no party → 403; `callerAgentId` returns the agent id or 403. Use
  `AssignmentEntity(id = 1, agentId = 20L)` and mock `CallerService`/`AgentService`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentAccessServiceTest`
Expected: FAIL — the class does not exist.

- [ ] **Step 3: Implement** the access service and the `AssignmentService` refactor; update `AssignmentServiceTest`.

- [ ] **Step 4: Run the service tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentAccessServiceTest,AssignmentServiceTest`
Expected: PASS.

- [ ] **Step 5: Extend the fixture** — add agent 30 and the caller rows:

```sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe');

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (30, 1, 300, 2370201);

-- User 11 is agent 20 (the assignment's), 12 is agent 30, 13 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false);
```

- [ ] **Step 6: Write the failing endpoint tests** — add the `admin`/`roles` getter and:

```kotlin
@Test fun `another agent - forbidden`() {
    userId = 12L
    val response = rest.getForEntity("/v1/assignments/1", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `caller without an agent - forbidden`() {
    userId = 13L
    assertEquals(HttpStatus.FORBIDDEN, rest.getForEntity("/v1/assignments/1", ErrorResponse::class.java).statusCode)
}

@Test fun `admin reads another agent's assignment`() {
    admin = true
    assertEquals(HttpStatus.OK, rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java).statusCode)
}
```

The existing `get` runs as user 11 (agent 20) and stays green; the 404 cases stay 404.

- [ ] **Step 7: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetAssignmentEndpointTest`
Expected: FAIL — the forbidden tests get 200.

- [ ] **Step 8: Implement** — inject `private val accessService: AssignmentAccessService` into `AssignmentEndpoints`
  and check after the load:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: Long): GetAssignmentResponse {
    val assignment = service.findById(id)
    accessService.ensureCanRead(assignment)
    return GetAssignmentResponse(assignment = mapper.toAssignment(assignment))
}
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetAssignmentEndpointTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AssignmentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetAssignmentEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/mandate/GetAssignmentEndpoint.sql
git commit -m "feat(server): only the offer's agent or an admin reads an assignment" -m "Refs #354"
```

---

### Task 13: `GET /v1/assignments` — a non-admin sees only its own offers

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AssignmentEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/SearchAssignmentEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/mandate/SearchAssignmentEndpoint.sql`

**Interfaces — Consumes:** `AssignmentAccessService` (Task 12). **Produces:**
`AssignmentAccessService.restrict(request: SearchAssignmentRequest): SearchAssignmentRequest` — an admin's as asked;
else the caller's agent id (403 without an agent), 403 when `request.agentIds` names another agent, else
`request.copy(agentIds = listOf(callerAgentId))`.

- [ ] **Step 1: Write the failing unit tests** — extend `AssignmentAccessServiceTest`: admin unchanged; an own
  `agent-id` returns `[20]`; no `agent-id` is forced to `[20]`; a foreign `agent-id` is 403; no agent is 403.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentAccessServiceTest`
Expected: FAIL — `restrict` does not exist.

- [ ] **Step 3: Implement** `restrict`, then run the unit test to verify it passes:

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentAccessServiceTest`
Expected: PASS.

- [ ] **Step 4: Extend the fixture** — add only the caller rows (user 11 → party 200/agent 20, 12 → party 300/agent
  30, 13 has no membership); the parties and agents already exist in this fixture.

- [ ] **Step 5: Write the failing endpoint tests** — the legacy tests run as an admin (`private var admin = true`);
  add:

```kotlin
@Test fun `agent sees only its own assignments`() {
    admin = false
    userId = 11L
    assertEquals(listOf(4L, 3L, 1L), search("").map { it.id })
    assertEquals(listOf(3L), search("?mandate-id=2").map { it.id })
    assertEquals(listOf(4L), search("?status=PENDING").map { it.id })
}

@Test fun `another agent's id - forbidden`() {
    admin = false
    userId = 11L
    val response = rest.getForEntity("/v1/assignments?agent-id=30", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `caller without an agent - forbidden`() {
    admin = false
    userId = 13L
    assertEquals(HttpStatus.FORBIDDEN, rest.getForEntity("/v1/assignments", ErrorResponse::class.java).statusCode)
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchAssignmentEndpointTest`
Expected: FAIL — a non-admin still sees every agent's assignments.

- [ ] **Step 7: Implement** — restrict before searching:

```kotlin
@GetMapping
fun search(@ModelAttribute request: SearchAssignmentRequest): SearchAssignmentResponse {
    val restricted = accessService.restrict(request)
    val assignments = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
    return SearchAssignmentResponse(
        assignments = assignments.map { assignment -> mapper.toAssignmentSummary(assignment) }
    )
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchAssignmentEndpointTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AssignmentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentAccessServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/SearchAssignmentEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/mandate/SearchAssignmentEndpoint.sql
git commit -m "feat(server): restrict an assignment search to the caller's offers" -m "Refs #354"
```

---

### Task 14: Document the rules and verify the module

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

- [ ] **Step 1: Update the `security` domain paragraph** — replace *"It needs a token (see *Authentication* below) but
  doesn't check whose user it is yet."* with: *"It needs a token (see *Authentication* below) and is the user itself or
  an admin (`UserAccessService`)."*
- [ ] **Step 2: Update the `customer` domain paragraph** — add: *"`GET /v1/customers/{id}` is the customer itself, the
  agent a mandate of it is assigned to, or an admin (`CustomerAccessService`); a non-admin's `GET /v1/customers` is
  limited to its own customer and the customers of its mandates (any status), and a request naming only foreign ids
  returns none."*
- [ ] **Step 3: Update the `party` domain paragraph** — add: *"`GET /v1/parties` is admin-only (`PartyAccessService`);
  `GET /v1/identifications/{id}` and `GET /v1/payment-methods/{id}` are the record's own party or an admin, and their
  searches are forced to the caller's own party (`KycAccessService`)."*
- [ ] **Step 4: Update the `agent` domain paragraph** — add: *"`GET /v1/agents?mobile-money-number=` is admin-only
  (`AgentAccessService`); its other filters stay open to any signed-in caller."*
- [ ] **Step 5: Update the `mandate` domain assignment paragraph** — add: *"`GET /v1/assignments/{id}` is the offer's
  agent or an admin, and a non-admin's `GET /v1/assignments` is forced to its own agent (`AssignmentAccessService`); a
  request naming another `agent-id` is 403 `ASSIGNMENT_ACCESS_DENIED`."*
- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document who may read customers, parties, KYC data, users and assignments" -m "Refs #354"
```

- [ ] **Step 7: Run the module's gate** (a local MySQL must be running)

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, every test green (including the new access-service and endpoint tests), JaCoCo
thresholds met.

- [ ] **Step 8: Confirm the web apps still build**

Run: `cd modules/ndopify-partner-central && mvn test`
Run: `cd modules/ndopify-web && mvn test`
Run: `cd modules/ndopify-esignature && mvn test`
Expected: BUILD SUCCESS (their own tests mock ndopify-server; no code change expected — the e-signature portal calls
none of these endpoints and is run because the story names it).

- [ ] **Step 9: Confirm nothing outside the story changed**

Run: `git status --porcelain && git diff --stat origin/main...HEAD`
Expected: only the server's customer/party/security/mandate/agent sources, tests, fixtures and `CLAUDE.md`; no DTO,
web-app, prototype, migration or root `pom.xml` change.

---

## Self-Review

- **Spec coverage:** the enriched body's scope (all seven read groups) and its twelve acceptance criteria have a task —
  customer get/search: Tasks 1–3; party search: Task 4; identifications/payment methods: Tasks 5–9; users: Task 10;
  agent mobile-money search: Task 11; assignments: Tasks 12–13; the endpoint-wide 401/404/403 order and the web-app
  regression checks: Global Constraints and Task 14. The web-app audit is in the spec and is what fixes the narrowed
  customer search.
- **Step scan:** every task names its files, the exact access-service signature and the assertions. The only product
  choices are fixed in Global Constraints (customer search narrowed, offered-only agent refused, forced own party for
  KYC reads, forced own agent for assignment searches), so no task decides one.
- **Type consistency:** `CustomerMandateLookup.findCustomerIds` / `isCustomerOfAgent`,
  `CustomerService.findByPartyIdOrNull`, `CustomerAccessService.restrict` returning `SearchCustomerRequest?`, the
  `KycAccessService.restrict` overloads (`SearchIdentificationRequest`, `SearchPaymentMethodRequest`, distinct from
  the existing `SearchKycCaseRequest`) and `AssignmentAccessService.callerAgentId` / `restrict` are used with the same
  names in the tasks that consume them.
- **Review Focus:** the five failure modes are pinned to Tasks 1–4, 10, 11 and 12.
