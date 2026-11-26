# The agent itself or an admin updates an agent's profile — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Only the agent whose party is the caller's, or an admin, may change an agent's profile; any other caller
gets 403 `HTTP_ACCESS_DENIED` and nothing changes.

**Architecture:** A new `AgentAccessService` in `ndopify-server`'s agent domain holds the rule. `AgentService.update`
runs it right after loading the agent, inside its transaction — as `MandateService.update` does with
`MandateAccessService` — so the error order stays 404 `AGENT_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED`, 409
`AGENT_VERSION_CONFLICT`. `AgentEndpoints` keeps mapping HTTP only.

**Tech Stack:** Kotlin, Spring Boot, `TestRestTemplate` against MySQL, JUnit 5 / `kotlin.test`, mockito-kotlin,
Flyway test fixtures under `db/test/agent/`.

**Spec:** GitHub issue #353 (enriched body: *Context*, *Acceptance criteria*, *Decisions and assumptions*, *Risks*,
*Out of scope*).

## Global Constraints

- `UI: no` — no prototype, no HTML/Thymeleaf, no Partner Central change. `Shared code: none` — no `ndopify-dto` or
  `ndopify-web-core` change, no library release.
- A caller who may not update an agent gets **403 `HTTP_ACCESS_DENIED`** (the generic code, not a new
  `AGENT_ACCESS_DENIED`), never 404. An unknown agent keeps its 404, checked **before** the 403; the version conflict
  stays 409, checked **after** the 403.
- **Admin** is `CallerService.isAdmin` exactly: an `admin-console` application token with the `admin` role. It may
  update any agent. Everybody else must be the agent itself: the caller's party (JWT user → membership → party) is the
  agent's party. A caller with no party gets 403.
- The route already requires a token (`SecurityConfiguration`, `anyRequest().authenticated()`): an anonymous caller
  keeps its 401.
- The check goes in **`AgentService.update` only**. The agent reads (`get`, `search`, `me`), `create` and the internal
  helpers (`sendWelcomeEmail`, `sendTelegramCode`, `onKycVerified`, `markAssigned`) stay unchecked.
- No new endpoint, no migration, no response-shape change, no lock.
- Test conventions: `kotlin.test` assertions, mockito-kotlin mocks, the endpoint test class owns its SQL fixture. Test
  users: **11** is party 100 (agent 100), **12** is party 101 (agent 101), **13** is party 102 (agent 102), **14** has
  no membership. Admin cases reuse user 11 with `roles = arrayOf(RoleCode.ADMIN)` (`application` stays the default
  `ADMIN_CONSOLE`).
- `mvn verify` green (ktlint, tests, JaCoCo); ndopify-server tests need a local MySQL.

## Review Focus

1. **Another agent, or a customer, updates an agent of the same tenant** → 403 `HTTP_ACCESS_DENIED`, and
   `whatsappNumber`, `agentType`, `experienceLevel`, `cityId`, the neighbourhoods, `biography` and `modifiedAt` are
   untouched. Task 2.
2. **A caller whose token has no party** (no membership in the tenant, or a membership without a party) → 403, never a
   500 or a silent write. Task 1 (no NPE on a null party id) and Task 2 (the endpoint case).
3. **An admin console token with the `admin` role updates another agent** → 200 and the change is saved. Task 2.
4. **The agent's own Partner Central save still works**, and a stale `version` still answers 409 after the access
   check rather than 403. Task 2.
5. **An anonymous caller** → 401, unchanged. Task 2.

---

### Task 1: `AgentAccessService` — the one rule

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessServiceTest.kt`

**Interfaces — Produces:**

```kotlin
/**
 * Who may change an agent's profile: the agent itself (JWT user → membership → party is the agent's party) or an
 * admin ([CallerService.isAdmin]). 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED otherwise.
 */
@Service
class AgentAccessService(private val callerService: CallerService) {
    fun ensureCanUpdate(agent: AgentEntity)
}
```

- `ensureCanUpdate`: `val principal = callerService.getPrincipal()` (401 without a token), return when
  `callerService.isAdmin(principal)`, else allow when `agent.party.id != null && callerService.findPartyIdOrNull(principal) ==
  agent.party.id`, else `ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))`. A null party id is a
  403, never an NPE.

- [ ] **Step 1: Write the failing test** `AgentAccessServiceTest` (mock `CallerService` and `JwtPrincipal`; the agent
  is `AgentEntity(id = 10, party = PartyEntity(id = 100))`; `assertDenied` asserts
  `ForbiddenException.error.code == ErrorCode.HTTP_ACCESS_DENIED`):

```kotlin
class AgentAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = AgentAccessService(callerService)
    private val agent = AgentEntity(id = 10, party = PartyEntity(id = 100))

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may update any agent`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanUpdate(agent)
    }

    @Test
    fun `agent may update itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureCanUpdate(agent)
    }

    @Test
    fun `another agent may not update it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        assertDenied { service.ensureCanUpdate(agent) }
    }

    @Test
    fun `caller without a party is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanUpdate(agent) }
    }

    @Test
    fun `agent without a party id is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanUpdate(AgentEntity(id = 11, party = PartyEntity())) }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentAccessServiceTest`
Expected: FAIL — `AgentAccessService` does not exist / does not compile.

- [ ] **Step 3: Implement `AgentAccessService`** with the signature above. A private
  `accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))` keeps the throw short.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentAccessServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentAccessServiceTest.kt
git commit -m "feat(server): add the agent profile access rule" -m "Refs #353"
```

---

### Task 2: `POST /v1/agents/{id}` — the agent itself or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentService.kt` (`update`,
  constructor, imports)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt` (`update`
  KDoc)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/UpdateAgentEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/agent/UpdateAgentEndpoint.sql`

**Interfaces — Consumes:** `AgentAccessService.ensureCanUpdate(agent: AgentEntity)` (Task 1).

- [ ] **Step 1: Update the fixture** — append the caller rows (agents and parties stay as they are; tenant 1 is already
  inserted):

```sql
-- The callers: 11 → party 100 (agent 100), 12 → party 101 (agent 101), 13 → party 102 (agent 102);
-- 14 has no membership, hence no party.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'james.bond@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'samuel.eto.caller@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'no.party@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false),
    (13, 1, 13, 102, false);
```

- [ ] **Step 2: Write the failing tests** in `UpdateAgentEndpointTest`:

  - Add the admin switch next to the existing fields:
    ```kotlin
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")
    ```
    (imports: `com.wutsi.ndopify.refdata.dto.RoleCode`).
  - The existing tests now act as the agent itself: add `userId = 12L` to `current version - saved ...`,
    `stale version - 409 ...` and `partial update of a single field` (they update agent 101), and `userId = 13L` to
    `no neighborhoodIds keeps existing neighborhoods` (agent 102). `update` (agent 100) and `agent not found` (999)
    keep the default user 11.
  - Add:
    ```kotlin
    @Test
    fun `another agent's profile - forbidden, nothing saved`() {
        val response = rest.postForEntity(
            "/v1/agents/101",
            UpdateAgentRequest(whatsappNumber = "+237600000888", biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)

        val agent = dao.findById(101L).get()
        assertNull(agent.whatsappNumber)
        assertNull(agent.biography)
        assertEquals(AgentType.UNKNOWN, agent.agentType)
        assertEquals(Date.from(Instant.parse("2024-06-10T00:00:00Z")), agent.modifiedAt)
    }

    @Test
    fun `caller without a party - forbidden, nothing saved`() {
        userId = 14L
        val response = rest.postForEntity(
            "/v1/agents/100",
            UpdateAgentRequest(biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertNull(dao.findById(100L).get().biography)
    }

    @Test
    fun `admin updates another agent`() {
        admin = true
        val response = rest.postForEntity(
            "/v1/agents/101",
            UpdateAgentRequest(biography = "Admin edit"),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Admin edit", dao.findById(101L).get().biography)
    }

    @Test
    fun `anonymous caller - unauthorized`() {
        anonymousUser = true
        val response = rest.postForEntity(
            "/v1/agents/100",
            UpdateAgentRequest(biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(dao.findById(100L).get().biography)
    }
    ```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateAgentEndpointTest`
Expected: FAIL — the forbidden tests get 200 and the agent is changed; the admin test passes already.

- [ ] **Step 4: Implement** — inject `private val access: AgentAccessService` into `AgentService` and check right after
  the load, before the version check:

```kotlin
@Transactional
fun update(id: Long, request: UpdateAgentRequest): AgentEntity {
    val agent = findById(id)
    access.ensureCanUpdate(agent)
    if (request.version != null && request.version != agent.version) {
        throw ConflictException(Error(ErrorCode.AGENT_VERSION_CONFLICT))
    }
    // ... unchanged
}
```

  Add the errors to its KDoc, in order: 404 `AGENT_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED` but for the agent itself or an
  admin, then 409 `AGENT_VERSION_CONFLICT`. In `AgentEndpoints.update`, add
  `/** The agent's profile: only the agent itself or an admin ([AgentService], through [AgentAccessService]). */`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateAgentEndpointTest`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/UpdateAgentEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/agent/UpdateAgentEndpoint.sql
git commit -m "feat(server): only the agent itself or an admin updates its profile" -m "Refs #353"
```

---

### Task 3: Document the rule

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the `agent` bullet under *Domains in `ndopify-server`*)

- [ ] **Step 1: Update the `agent` bullet** — after the `/v1/agents` description, add one sentence carrying the rule
  and the error order:

```
  `POST /v1/agents/{id}` is the agent's own (its party, through the token's membership) or an admin's
  (`AgentAccessService`): 404 `AGENT_NOT_FOUND` → 403 `HTTP_ACCESS_DENIED` → 409 `AGENT_VERSION_CONFLICT`. The reads
  and `create` stay open to any token as they are.
```

- [ ] **Step 2: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document who may update an agent's profile" -m "Refs #353"
```

---

### Task 4: Full verification

- [ ] **Step 1: Run the module's gate** (a local MySQL must be running):

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, every test green (including `AgentAccessServiceTest` and
`UpdateAgentEndpointTest`), JaCoCo thresholds met.

- [ ] **Step 2: Confirm Partner Central still builds and its profile tests pass** (no code change expected: its
  `/profile` page saves the signed-in agent's own profile)

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: BUILD SUCCESS (`ProfileControllerTest`, `ProfileTemplateTest` unchanged and green).

- [ ] **Step 3: Confirm nothing outside the story changed**

Run: `git status --porcelain && git diff --stat origin/main...HEAD`
Expected: only the files listed in Tasks 1–3 (the access service and its test, the agent service/endpoint, the
endpoint test and its fixture, the module `CLAUDE.md`); no DTO, web-core, web-app, prototype, migration or root
`pom.xml` change.
