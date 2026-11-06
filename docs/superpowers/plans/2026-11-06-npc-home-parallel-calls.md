# Partner Central Parallel Home-Page Calls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load the Espace agent's independent sections at the same time, cutting `HomeController` from up to 11 sequential ndopify-server calls to a 4-round-trip critical path, with no change to what the page shows or how a section degrades.

**Architecture:** A new `npc/config/Parallel` runs each read on a virtual thread, re-binding the caller's `RequestAttributes` so the web-core interceptors still send `Authorization` and `X-Tenant-ID`. `HomeController` starts three waves of calls (independent reads, what they unlock, then the join), and keeps every `Model` write on the request thread.

**Tech Stack:** Kotlin, Spring Boot 4.1, Java 25 virtual threads (`Executors.newThreadPerTaskExecutor`), `CompletableFuture`, Spring MVC, Thymeleaf, Maven, Mockito-Kotlin, `MockRestServiceServer`, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-11-06-npc-home-parallel-calls-design.md`

## Global Constraints

- Java 25 is already configured; virtual threads need **no** `--enable-preview` and no new dependency.
- Do **not** set `spring.task.execution.*` and do **not** enable `spring.threads.virtual.enabled` in this change.
- Each task must re-bind `RequestContextHolder.getRequestAttributes()` before calling a client, and reset it in
  `finally`: the `Authorization` and `X-Tenant-ID` interceptors read the incoming request through ThreadLocals.
- `tenantContext.getTenant()` runs on the request thread **before** the first wave (it initialises the tenant cache
  and the `TenantFormatter`).
- Every `model.addAttribute` stays on the request thread; tasks only return values.
- A failed section keeps today's behaviour: the loader catches `RestClientException` and returns `null` / an empty
  map, so the page still renders.
- Code and docs in English; user-facing text stays French. ktlint formats on build.
- Tests are plain unit tests: no Spring context, Mockito-Kotlin mocks, `kotlin.test` assertions.

## Review Focus

1. **A worker thread without the request attributes** would send no access token (401) and no tenant header. Pinned in
   Task 1 by `sends the agent's access token` and `binds the caller's request attributes`.
2. **No real concurrency** would leave the page as slow as before. Pinned in Task 1 by `runs the tasks at the same
   time`, which deadlocks if the tasks run one after the other.
3. **One failing section** must not fail the whole page: the existing `offers unavailable`, `mandates unavailable`,
   `visits unavailable`, `listings unavailable`, `stats unavailable` and `customers unavailable` cases now run through
   the parallel path in Task 2.
4. **A `Model` write on a worker thread** would corrupt Spring MVC's model / Thymeleaf. Pinned in Task 2 by
   `model is written on the request thread`.
5. **A task's exception must surface** instead of hanging the page. Pinned in Task 1 by `propagates the failure`; the
   loaders' `RestClientException` handling keeps server errors out of that path.

---

### Task 1: The `Parallel` helper

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/Parallel.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/ParallelConfiguration.kt`
- Create: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/ParallelTest.kt`

**Interfaces:**
- Produces: `class Parallel(executor: ExecutorService = …) : AutoCloseable` with
  `fun <T> async(block: () -> T): CompletableFuture<T>` and `override fun close()`. Task 2 injects this type.
- Produces: `@Bean(destroyMethod = "close") fun parallel(): Parallel` in `ParallelConfiguration`.
- Consumes: `AuthorizationClientHttpRequestInterceptor(cookieName: String)` from `ndopify-web-core` (test only).

- [ ] **Step 1: Write the failing test**

Create `ParallelTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.web.core.client.AuthorizationClientHttpRequestInterceptor
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.concurrent.Callable
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelTest {
    private val parallel = Parallel()

    @AfterEach
    fun close() = parallel.close()

    @Test
    fun `binds the caller's request attributes`() {
        val request = MockHttpServletRequest().apply { setAttribute("npc.test", "yes") }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            val seen = parallel.async {
                RequestContextHolder.getRequestAttributes()?.getAttribute("npc.test")
            }.join()

            assertEquals("yes", seen)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `sends the agent's access token`() {
        val builder = RestClient.builder().baseUrl("http://localhost:8080")
        val server = MockRestServiceServer.bindTo(builder).build()
        val rest = builder
            .requestInterceptor(AuthorizationClientHttpRequestInterceptor("__npc_access_token"))
            .build()
        val request = MockHttpServletRequest().apply {
            setCookies(Cookie("__npc_access_token", "token-123"))
        }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            server.expect(requestTo("http://localhost:8080/v1/agents/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

            parallel.async { rest.get().uri("/v1/agents/me").retrieve().body<String>() }.join()

            server.verify()
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `runs the tasks at the same time`() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)

        val first = parallel.async {
            started.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
            1
        }
        val second = parallel.async {
            started.countDown()
            assertTrue(release.await(2, TimeUnit.SECONDS))
            2
        }

        assertTrue(started.await(2, TimeUnit.SECONDS), "both tasks must start before either finishes")
        release.countDown()
        assertEquals(1, first.join())
        assertEquals(2, second.join())
    }

    @Test
    fun `clears the request attributes after the task`() {
        val executor = Executors.newSingleThreadExecutor()
        val single = Parallel(executor)
        try {
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
            single.async { Unit }.join()
            RequestContextHolder.resetRequestAttributes()

            val leaked = executor.submit(Callable { RequestContextHolder.getRequestAttributes() }).get()

            assertNull(leaked)
        } finally {
            single.close()
            RequestContextHolder.resetRequestAttributes()
        }
    }

    @Test
    fun `propagates the failure`() {
        val failure = assertFailsWith<CompletionException> {
            parallel.async<Unit> { throw IllegalStateException("boom") }.join()
        }

        assertEquals("boom", failure.cause?.message)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ParallelTest`

Expected: **compilation failure** — unresolved reference `Parallel` (and `ParallelConfiguration` doesn't exist yet).

- [ ] **Step 3: Implement `Parallel`**

Create `Parallel.kt`:

```kotlin
package com.wutsi.ndopify.npc.config

import org.springframework.web.context.request.RequestContextHolder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs a page's independent REST calls at the same time, one virtual thread each.
 *
 * The REST interceptors (`Authorization`, `X-Tenant-ID`) and `TenantContext` read the incoming request through
 * ThreadLocals: each task re-binds the caller's `RequestAttributes`, or ndopify-server answers 401 and the tenant is
 * lost.
 */
class Parallel(
    private val executor: ExecutorService = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("npc-parallel-", 0).factory(),
    ),
) : AutoCloseable {
    fun <T> async(block: () -> T): CompletableFuture<T> {
        val attributes = RequestContextHolder.getRequestAttributes()
        return CompletableFuture.supplyAsync(
            {
                RequestContextHolder.setRequestAttributes(attributes, true)
                try {
                    block()
                } finally {
                    RequestContextHolder.resetRequestAttributes()
                }
            },
            executor,
        )
    }

    override fun close() = executor.close()
}
```

- [ ] **Step 4: Declare the bean**

Create `ParallelConfiguration.kt`:

```kotlin
package com.wutsi.ndopify.npc.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The executor behind a page's parallel REST calls (see [Parallel]). */
@Configuration
class ParallelConfiguration {
    @Bean(destroyMethod = "close")
    fun parallel(): Parallel = Parallel()
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ParallelTest`

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/Parallel.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/ParallelConfiguration.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/ParallelTest.kt
git commit -m "feat(npc): add a virtual-thread helper for parallel page calls"
```

---

### Task 2: Run the home page's reads in parallel

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (Espace agent bullet)

**Interfaces:**
- Consumes: `Parallel.async(block: () -> T): CompletableFuture<T>` from Task 1.
- Produces: nothing outside the controller. `openAssignments(agent: Agent, now: Long): List<AssignmentSummary>?` and
  `offers(agent: Agent, open: List<AssignmentSummary>?): List<Pair<AssignmentSummary, Mandate>>?` replace the old
  `offers(agent: Agent, now: Long)`.

- [ ] **Step 1: Update the test for the new dependency**

In `HomeControllerTest.kt`:

Add these imports:

```kotlin
import com.wutsi.ndopify.npc.config.Parallel
import org.junit.jupiter.api.AfterEach
import org.springframework.ui.ExtendedModelMap
import org.springframework.ui.ModelMap
```

Add the fields and make the controller reachable by the new test:

```kotlin
    private val clock = mock<Clock>()
    private val parallel = Parallel()
    private lateinit var mvc: MockMvc
    private lateinit var controller: HomeController
```

In `setUp()`, change the local `val controller = HomeController(...)` to `controller = HomeController(...)` and pass
`parallel` after `clock`:

```kotlin
        controller = HomeController(
            currentAgent,
            kycClient,
            assignmentClient,
            mandateClient,
            listingClient,
            visitClient,
            customerClient,
            locationClient,
            tenantContext,
            clock,
            parallel,
        )
```

Add, next to `setUp()`:

```kotlin
    @AfterEach
    fun closeParallel() = parallel.close()
```

Add the new test and its model helper at the end of the class:

```kotlin
    @Test
    fun `model is written on the request thread`() {
        val model = RequestThreadModel(Thread.currentThread())

        controller.show(null, model)

        assertEquals("SN", model["initials"])
    }

    private class RequestThreadModel(private val requestThread: Thread) : ExtendedModelMap() {
        override fun addAttribute(attributeName: String, attributeValue: Any?): ModelMap {
            check(Thread.currentThread() === requestThread) { "Model written from ${Thread.currentThread()}" }
            return super.addAttribute(attributeName, attributeValue)
        }

        override fun addAttribute(attributeValue: Any): ModelMap {
            check(Thread.currentThread() === requestThread) { "Model written from ${Thread.currentThread()}" }
            return super.addAttribute(attributeValue)
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeControllerTest`

Expected: **compilation failure** — `HomeController` has no constructor parameter of type `Parallel`.

- [ ] **Step 3: Add `Parallel` to `HomeController`**

Add the import:

```kotlin
import com.wutsi.ndopify.npc.config.Parallel
```

Add the constructor parameter after `private val clock: Clock,`:

```kotlin
    private val parallel: Parallel,
```

- [ ] **Step 4: Split `offers()` into its two searches**

Replace the existing `offers(agent, now)` (lines 123–141, KDoc included) with:

```kotlin
    /** The agent's PENDING offers still open, newest first; null when they can't be had. */
    private fun openAssignments(agent: Agent, now: Long): List<AssignmentSummary>? =
        try {
            assignmentClient.pending(agent.id).filter { AssignmentViews.isOpen(it, now) }
        } catch (ex: RestClientException) {
            LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
            null
        }

    /**
     * The mandates of [open] offers (one search for all of them); null when they can't be had, or when [open] is null.
     * An offer whose mandate isn't returned (no longer offered since) is left out.
     */
    private fun offers(agent: Agent, open: List<AssignmentSummary>?): List<Pair<AssignmentSummary, Mandate>>? =
        when {
            open == null -> null
            open.isEmpty() -> emptyList()
            else ->
                try {
                    val mandates = mandateClient.offered(agent.id).associateBy { it.id }
                    open.mapNotNull { assignment ->
                        mandates[assignment.mandateId]?.let { summary -> assignment to MandateSummaries.toMandate(summary) }
                    }
                } catch (ex: RestClientException) {
                    LOGGER.warn("No offers for agent ${agent.id}: ${ex.message}")
                    null
                }
        }
```

- [ ] **Step 5: Rewrite `show()` into three waves**

Replace the body of `show()` (lines 78–121) with:

```kotlin
    @GetMapping("/")
    fun show(@RequestParam(required = false) decline: String?, model: Model): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()

        ProfileRules.header(agent, model)
        model.addAttribute("photoMissing", agent.party.photoUrl.isNullOrBlank())
        model.addAttribute("biographyMissing", agent.biography.isNullOrBlank())
        model.addAttribute("kyc", kyc(agent))
        if (agent.party.kycStatus == KycStatus.VERIFIED) {
            val now = clock.millis()

            // On the request thread, before any task: this initialises TenantContext's cache and the formatter.
            val tenant = tenantContext.getTenant()
            val formatter = TenantFormatter(tenant)

            // Wave 1: the independent reads.
            val openCall = parallel.async { openAssignments(agent, now) }
            val mandatesCall = parallel.async { mandates(agent) }
            val visitsCall = parallel.async { loadVisits(agent) }
            val open = openCall.join()
            val mandates = mandatesCall.join()
            val visits = visitsCall.join()

            // Wave 2: what the first wave unlocks.
            val offersCall = parallel.async { offers(agent, open) }
            val mandateStatsCall = parallel.async { mandates?.let { mandateStats(it) } }
            val listingsCall = parallel.async { mandates?.let { listings(agent, it) } }
            val visitListingsCall = parallel.async { visits?.let { loadVisitListings(it) } }
            val visitCustomersCall = parallel.async { visits?.let { loadVisitCustomers(it) } ?: emptyMap() }
            val offers = offersCall.join()
            val mandateStats = mandateStatsCall.join()
            val listings = listingsCall.join()
            val visitListings = visitListingsCall.join()
            val visitCustomers = visitCustomersCall.join()

            // Wave 3: the two reads that join the rest.
            val namesCall = parallel.async {
                names(
                    (offers.orEmpty().map { (_, mandate) -> mandate } + mandates.orEmpty())
                        .flatMap { mandate -> listOf(mandate.cityId) + mandate.neighborhoodIds } +
                        visitListings.orEmpty().values.flatMap { list -> listOfNotNull(list.cityId, list.neighborhoodId) },
                )
            }
            val listingStatsCall = parallel.async { listings?.let { listingStats(it.values) } }
            val names = namesCall.join()
            val stats = listingStatsCall.join()

            // The Model and the view stay on the request thread.
            val assignments = offers?.map { (assignment, mandate) ->
                AssignmentViews.of(assignment, mandate, names, now, formatter)
            }
            model.addAttribute("assignments", assignments ?: emptyList<AssignmentView>())
            model.addAttribute("assignmentsUnavailable", assignments == null)
            // GET /?decline={id}: the decline confirmation without JS, for one of the listed offers only. A String,
            // so that a hand-edited id is ignored instead of failing the page.
            val declineId = decline?.toLongOrNull()
            model.addAttribute("declining", assignments?.firstOrNull { it.id == declineId }?.id)

            model.addAttribute(
                "mandates",
                mandates?.map { mandate -> MandateCards.of(mandate, listings, names, formatter, mandateStats, stats) }
                    ?: emptyList<MandateCardView>(),
            )
            model.addAttribute("mandatesUnavailable", mandates == null)
            model.addAttribute("visits", VisitCards.of(visits, visitListings, visitCustomers, names, now, tenant, formatter))
        }
        return VIEW
    }
```

- [ ] **Step 6: Run the controller tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeControllerTest`

Expected: all existing tests pass, including `model is written on the request thread`.

- [ ] **Step 7: Run the whole module**

Run: `cd modules/ndopify-partner-central && mvn test`

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 8: Update the module documentation**

In `modules/ndopify-partner-central/CLAUDE.md`, inside the `Login redirects to /` bullet, insert this sentence after
"`home.js` is unchanged (the cards are server-rendered, and a reload shows the next time-bound panel).":

> `HomeController` loads the sections' independent reads together on virtual threads (`config/Parallel`), so the page
> waits on four waves instead of eleven round trips; each section still degrades on its own, every `Model` write stays
> on the request thread, and each task re-binds the request attributes the `Authorization` / `X-Tenant-ID`
> interceptors read.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "perf(npc): load the home page's sections in parallel"
```

## Manual verification (after the branch is running)

With ndopify-server running, open `/` as a verified agent with offers, mandates and visits:

- The data is identical to before (offers, *Mes mandats*, *Visites*, location names, activity lines).
- In the `LoggingClientHttpRequestInterceptor` output, calls from the same wave start within a few milliseconds and
  their `[ms]` latencies overlap instead of following each other.
- The page's time to first byte drops from roughly the sum of the calls to four calls' worth. A temporary
  `LOGGER.info("home in {} ms", …)` around the body gives the exact before/after number.
