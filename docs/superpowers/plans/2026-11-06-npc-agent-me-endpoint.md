# Partner Central One-Call Current Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Partner Central's two-call agent lookup (`GET /v1/agents?party-id=…` then `GET /v1/agents/{id}`) with one call to a new `GET /v1/agents/me` that returns `GetAgentResponse`.

**Architecture:** `AgentEndpoints` gains `/me`: it resolves the caller's party from the JWT with `CallerService`, finds the agent with `AgentService.findByPartyIdOrNull`, and returns the full agent through `AgentMapper.toAgent`. Partner Central's `NdAgentClient.me()` maps 404 to `null`; `CurrentAgent` delegates to it and no longer depends on `CurrentParty`.

**Tech Stack:** Kotlin, Spring Boot, Spring Security (JWT), JPA/Hibernate (`@TenantId`), Maven, `kotlin.test`, Mockito-Kotlin, `MockRestServiceServer`.

**Spec:** `docs/superpowers/specs/2026-11-06-npc-agent-me-endpoint-design.md`

## Global Constraints

- Endpoint is `GET /v1/agents/me`, returns `GetAgentResponse`, requires a JWT, and is **not** added to `PublicEndpoints`.
- 404 `AGENT_NOT_FOUND` when the caller has no membership/party in the current tenant or its party isn't an agent.
- Partner Central maps **exactly 404** to `null`; 401, 5xx, timeouts and connection failures propagate unchanged.
- The response is the full `Agent` (`biography`, `version`, `neighborhoodIds`, …), not `AgentSummary`.
- No DTO change, no pom version bump, no Flyway migration, no caching, no JWT claim change.
- Deploy ndopify-server before Partner Central: an old server answers 400 on `/v1/agents/me`.
- Server tests use `kotlin.test` assertions; Partner Central tests use Mockito-Kotlin and `MockRestServiceServer`.
- Code/docs in English; UI text stays French. ktlint runs on build.

## Review Focus

1. **No token:** `/v1/agents/me` must answer 401 `AUTH_UNAUTHORIZED` from security, not be treated as "not an agent". Pinned in Task 1 (`anonymous`).
2. **Authenticated party that isn't an agent:** 404 `AGENT_NOT_FOUND`, and the client returns `null` so the page signs out as today. Pinned in Task 1 (`not an agent`) and Task 2 (`me - not an agent`).
3. **Membership without a party (e.g. admin):** 404 `AGENT_NOT_FOUND`, no crash, client returns `null`. Pinned in Task 1 (`caller without party`).
4. **`/me` shadowed by `/{id}`:** the literal path must return the agent, not a 400 from converting `"me"` to `Long`. Pinned in Task 1 (`me`) and Task 2 (`me`).
5. **Server failure on `/me`:** only 404 means "not an agent"; a 5xx/timeout must surface as an error instead of silently signing the user out. Pinned in Task 2 (`me - server error`).

---

### Task 1: Server endpoint `GET /v1/agents/me`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt:23-41`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentMeEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md:104` (agent domain line)

**Interfaces:**
- Consumes: `CallerService.findPartyIdOrNull(principal: JwtPrincipal): Long?`, `CallerService.getPrincipal(): JwtPrincipal`,
  `AgentService.findByPartyIdOrNull(partyId: Long): AgentEntity?`, `AgentMapper.toAgent(entity: AgentEntity): Agent`.
- Produces: `AgentEndpoints.me(): GetAgentResponse` at `GET /v1/agents/me`. Task 2 consumes this route.

- [ ] **Step 1: Write the failing test**

Create `GetAgentMeEndpointTest` extending `AbstractAgentEndpointTest`. The fixture scripts provide tenant 1, party 100
and agent 1 (biography `"Top agent in town"`); the statements add the token users and memberships:

```kotlin
package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        "INSERT INTO T_USER(id, email) VALUES (11, 'ray.sponsible@gmail.com'), (12, 'no.agent@example.com'), (13, 'no.party@example.com')",
        "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES (101, 1, 'no.agent@example.com', 'No', 'Agent')",
        "INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id) VALUES (11, 1, 11, 100), (12, 1, 12, 101), (13, 1, 13, NULL)",
    ],
)
class GetAgentMeEndpointTest : AbstractAgentEndpointTest() {
    @Test
    fun me() {
        val response = rest.getForEntity("/v1/agents/me", GetAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val agent = response.body!!.agent
        assertEquals(1L, agent.id)
        assertEquals(100L, agent.party.id)
        assertEquals("Top agent in town", agent.biography)
    }

    @Test
    fun `not an agent`() {
        userId = 12L

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `caller without party`() {
        userId = 13L

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/agents/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetAgentMeEndpointTest`

Expected: FAIL — `me`, `not an agent` and `caller without party` get 400 because `/v1/agents/me` currently matches
`@GetMapping("/{id}")` and cannot convert `"me"` to `Long`. `anonymous` passes (security rejects it first).

- [ ] **Step 3: Implement the endpoint**

In `AgentEndpoints`, add `private val callerService: CallerService` to the constructor and add:

```kotlin
/**
 * The caller's own agent in the current tenant, resolved from the access token: 404 AGENT_NOT_FOUND when the
 * caller has no party, or its party isn't an agent.
 */
@GetMapping("/me")
fun me(): GetAgentResponse {
    val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
    val agent = service.findByPartyIdOrNull(partyId)
        ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
    return GetAgentResponse(agent = mapper.toAgent(agent))
}
```

Add imports for `Error`, `ErrorCode`, `NotFoundException` and `CallerService`. Place `me()` next to `get()`; Spring's
path-pattern matcher prefers the literal segment, so declaration order is irrelevant.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetAgentMeEndpointTest`

Expected: PASS — 4/4 tests.

- [ ] **Step 5: Update the server docs**

In `modules/ndopify-server/CLAUDE.md:104`, change the agent-domain list to `get, search, me, create, update` and add:
"`GET /v1/agents/me` resolves the caller's agent from its token (`CallerService`): 404 `AGENT_NOT_FOUND` when the
caller has no party or isn't an agent."

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentMeEndpointTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): expose the caller's agent at /v1/agents/me"
```

---

### Task 2: Partner Central client `NdAgentClient.me()`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt:31-50`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt:110-141`

**Interfaces:**
- Consumes: `GET /v1/agents/me` from Task 1, returning `GetAgentResponse`.
- Produces: `NdAgentClient.me(): Agent?` (null on 404, throws otherwise). Task 3 consumes it.

- [ ] **Step 1: Write the failing tests**

Add to `NdAgentClientTest`; keep `findByPartyId` and its tests for now (Task 3 removes them):

```kotlin
@Test
fun me() {
    server.expect(requestTo("http://localhost:8080/v1/agents/me"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """{"agent":{"id":20,"party":{"id":100,"firstName":"Ray","lastName":"Sponsible","email":"ray@gmail.com","kycStatus":"VERIFIED","createdAt":0,"modifiedAt":0},"agentType":"INDEPENDANT","biography":"Bio","cityId":1,""" +
                    """"neighborhoodIds":[1,2],"experienceLevel":"SENIOR","whatsappNumber":"+237670000001",""" +
                    """"createdAt":0,"modifiedAt":0,"rating":3.0,"version":2}}""",
                MediaType.APPLICATION_JSON,
            ),
        )

    val agent = client.me()

    assertEquals(20L, agent?.id)
    assertEquals("Ray", agent?.party?.firstName)
    assertEquals("Bio", agent?.biography)
    assertEquals(2L, agent?.version)
    server.verify()
}

@Test
fun `me - not an agent`() {
    server.expect(requestTo("http://localhost:8080/v1/agents/me"))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":{"code":"${ErrorCode.AGENT_NOT_FOUND}"}}"""),
        )

    assertNull(client.me())
}

@Test
fun `me - server error`() {
    server.expect(requestTo("http://localhost:8080/v1/agents/me"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

    assertFailsWith<HttpServerErrorException> { client.me() }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdAgentClientTest`

Expected: FAIL to compile — `client.me()` is unresolved.

- [ ] **Step 3: Implement `me()`**

Add to `NdAgentClient`, keeping `findByPartyId` until Task 3:

```kotlin
/**
 * The signed-in agent, resolved by ndopify-server from the access token; null when the caller has no party or isn't
 * an agent (404). Every other error propagates: only "not an agent" means "sign in again".
 */
fun me(): Agent? =
    try {
        rest.get()
            .uri("/v1/agents/me")
            .retrieve()
            .body<GetAgentResponse>()
            ?.agent
    } catch (ex: HttpClientErrorException) {
        if (ex.statusCode == HttpStatus.NOT_FOUND) null else throw ex
    }
```

Add imports for `HttpClientErrorException` and `HttpStatus`. `GetAgentResponse` is already imported.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdAgentClientTest`

Expected: PASS — the 3 new tests and the existing ones.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt
git commit -m "feat(npc): read the signed-in agent from /v1/agents/me"
```

---

### Task 3: `CurrentAgent` uses `me()`; remove the dead two-call path

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/security/CurrentAgent.kt:10-21`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt` (remove `findByPartyId`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/security/CurrentParty.kt:19-25` (remove `id()`)
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/service/security/CurrentAgentTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt` (remove the two `findByPartyId` tests)
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/service/security/CurrentPartyTest.kt:73-97` (remove the four `id()` tests)
- Modify: `modules/ndopify-partner-central/README.md:96-98`
- Modify: `modules/ndopify-partner-central/CLAUDE.md:68,125`
- Test: `CurrentAgentTest`, `NdAgentClientTest`, `CurrentPartyTest`

**Interfaces:**
- Consumes: `NdAgentClient.me(): Agent?` from Task 2.
- Produces: `CurrentAgent(agentClient: NdAgentClient)` with `get(): Agent?`; no other public API changes.

- [ ] **Step 1: Write the failing test**

Replace the body of `CurrentAgentTest` with:

```kotlin
package com.wutsi.ndopify.npc.service.security

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentAgentTest {
    private val agentClient = mock<NdAgentClient>()
    private val currentAgent = CurrentAgent(agentClient)

    @Test
    fun agent() {
        doReturn(Agent(id = 20L)).whenever(agentClient).me()

        assertEquals(20L, currentAgent.get()?.id)
    }

    @Test
    fun `not an agent`() {
        doReturn(null).whenever(agentClient).me()

        assertNull(currentAgent.get())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=CurrentAgentTest`

Expected: FAIL to compile — `CurrentAgent` still takes `(CurrentParty, NdAgentClient)` and `CurrentParty` is unused in
the test.

- [ ] **Step 3: Implement `CurrentAgent`**

```kotlin
/**
 * The signed-in agent, with its profile and its party (name, e-mail, photo, KYC status): ndopify-server resolves it
 * from the access token. Null when the caller has no party or isn't an agent: those must sign in again.
 */
@Service
class CurrentAgent(private val agentClient: NdAgentClient) {
    fun get(): Agent? = agentClient.me()
}
```

Remove the now-unused `CurrentParty` import.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=CurrentAgentTest`

Expected: PASS — 2/2 tests.

- [ ] **Step 5: Delete the unused `NdAgentClient.findByPartyId`**

Delete `findByPartyId` and the `findByPartyId` / `findByPartyId - not an agent` tests from `NdAgentClientTest`.
Remove imports that become unused.

- [ ] **Step 6: Delete the unused `CurrentParty.id()`**

Delete `CurrentParty.id()` and its four tests in `CurrentPartyTest` (`id from the token…`, `id looked up…`,
`no id for a user without party`, `no id when anonymous`). Simplify the `signIn` helper to take only `userId` and
drop the `partyId` claim it no longer needs. `CurrentParty.get()` stays: `KycController` uses it.

- [ ] **Step 7: Run the three test classes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdAgentClientTest,CurrentAgentTest,CurrentPartyTest`

Expected: PASS — no reference to `findByPartyId` or `CurrentParty.id()`.

- [ ] **Step 8: Update the Partner Central docs**

- `README.md:96-98`: replace the two-call chain with "The signed-in agent — `CurrentAgent` calls
  `GET /v1/agents/me`, which ndopify-server resolves from the access token (never from a form); null when the caller
  isn't an agent, which signs it out."
- `CLAUDE.md:125`: replace the `CurrentAgent (...)` parenthetical with "`CurrentAgent` (`GET /v1/agents/me`: the
  server resolves the agent from the token)" and keep "agent and party ids always come from the token, never from a
  form".
- `CLAUDE.md:68`: change "when `CurrentAgent` / `CurrentParty` finds nobody" to "when the caller isn't an agent
  (`CurrentAgent`) or has no party (`CurrentParty`)".

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/security/CurrentAgent.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/security/CurrentParty.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/service/security/CurrentAgentTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/service/security/CurrentPartyTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClientTest.kt \
        modules/ndopify-partner-central/README.md \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): resolve the signed-in agent in one call"
```

---

### Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the server agent endpoint tests**

Prerequisite: local MySQL on `localhost:3306`, database `ndopify` (see `modules/ndopify-server/README.md`).

Run: `cd modules/ndopify-server && mvn test -Dtest=GetAgentEndpointTest,GetAgentMeEndpointTest`

Expected: BUILD SUCCESS, no failures. `GetAgentEndpointTest` proves existing get/search behaviour still works;
`GetAgentMeEndpointTest` proves the new route.

- [ ] **Step 2: Run the Partner Central module tests**

Run: `cd modules/ndopify-partner-central && mvn test`

Expected: BUILD SUCCESS, no failures. This covers every controller that mocks `CurrentAgent` (home, mandate,
listing, profile, visit, KYC) and the client/security tests.

- [ ] **Step 3: Check formatting**

Run: `cd modules/ndopify-server && mvn validate && cd ../ndopify-partner-central && mvn validate`

Expected: BUILD SUCCESS (ktlint finds no violations).
