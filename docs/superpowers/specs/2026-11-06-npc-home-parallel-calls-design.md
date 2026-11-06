# Partner Central — Parallel Home-Page Calls — Design

## Intent

The Espace agent (`GET /`, `HomeController`) makes its ndopify-server calls one after the other, even though most
are independent. For a verified agent with offers, mandates and visits that is up to eleven calls:

| # | Call | Depends on |
|---|------|-----------|
| 1 | `GET /v1/agents/me` | — |
| 2 | `GET /v1/assignments?agent-id=&status=PENDING` | 1 |
| 3 | `GET /v1/mandates?offered-to=` | 2 (only with an open offer) |
| 4 | `GET /v1/mandates?agent-id=&status=…` | 1 |
| 5 | `GET /v1/mandates/stats?mandate-id=…` | 4 |
| 6 | `GET /v1/listings?mandate-id=…&agent-id=` | 4 |
| 7 | `GET /v1/listings/stats?listing-id=…` | 6 |
| 8 | `GET /v1/visits?agent-id=&status=…` | 1 |
| 9 | `GET /v1/listings?id=…` (the visits') | 8 |
| 10 | `GET /v1/customers?id=…` (the visits') | 8 |
| 11 | `GET /v1/locations?id=…` | 2, 3, 4, 9 |

At ~150 ms per call, the page waits ~1.65 s for data. Only some of those dependencies are real: the page can load the
offers, the mandates and the visits at the same time, then whatever each one unlocks, then the location names and the
listing activity.

## Goals

- Run the independent reads at the same time; keep each dependent read after its input.
- Cut the critical path from up to 11 round trips to 4.
- Keep every section's current degradation: a failed call leaves that section `unavailable` or without its line,
  never a failed page.
- No new dependency; no change to the clients, the DTOs or the templates.
- Keep every `Model` write on the request thread (Spring MVC's model and Thymeleaf are not thread-safe).

## Non-goals

- Changing ndopify-server endpoints or their batching (they already answer one section per call).
- Caching the agent, mandates or locations.
- Enabling `spring.threads.virtual.enabled` or reworking the outbound connection pool. Both are separate, app-wide
  changes (see Decisions).
- Parallelizing other pages.

## Constraints

- Kotlin/ktlint; Mockito-Kotlin in tests. Partner Central's tests are plain unit tests (no Spring context), so the
  helper is tested directly.
- Java 25 is already set (`jdk.version` in the root POM, `java.runtime.version` in `system.properties`): virtual
  threads need no preview flag.
- `AuthorizationClientHttpRequestInterceptor` reads the token from `RequestContextHolder`, and
  `TenantIdClientHttpRequestInterceptor` / `TenantContext` resolve the incoming request the same way (ThreadLocals).
  A task that doesn't re-bind the caller's `RequestAttributes` sends no `Authorization` header (401) and no
  `X-Tenant-ID`.
- `TenantContext.getTenant()` must run on the request thread before the first wave: it initialises the singleton's
  tenant cache and the `TenantFormatter`.
- `RestClient` is thread-safe, and so are the mocked clients in the tests.

## Approaches considered

1. **Virtual-thread executor + a `Parallel` helper (chosen).** One `Executors.newVirtualThreadPerTaskExecutor()`;
   `async { }` captures the caller's `RequestAttributes` and re-binds them in the task. Small, local to
   `HomeController`, keeps the existing `try/catch RestClientException → null` semantics. Cost: one more constructor
   dependency and request-context propagation that must not be forgotten.
2. **Structured concurrency (`StructuredTaskScope`).** Cleaner cancellation and failure handling, but still a preview
   API in Java 25: it needs `--enable-preview`, which is not viable for this deployment.
3. **Kotlin coroutines (`runBlocking { async(Dispatchers.IO) }`).** Same result as option 1, adds `kotlinx-coroutines`
   and the same context-propagation work.
4. **Reactive `WebClient`.** Rewrites all fourteen clients and the controller for eleven calls. Far too much for the
   gain.

## Decisions

- **`npc/config/Parallel.kt`** — `class Parallel(executor: ExecutorService = …) : AutoCloseable` with
  `fun <T> async(block: () -> T): CompletableFuture<T>`. It captures `RequestContextHolder.getRequestAttributes()` on
  the caller's thread, sets them in the task, and resets them in `finally`. Virtual threads are named
  `npc-parallel-<n>` so logs and thread dumps identify them. `close()` closes the executor.
- **`npc/config/ParallelConfiguration.kt`** — `@Bean(destroyMethod = "close") fun parallel(): Parallel = Parallel()`.
  Exposing `Parallel` rather than a raw `ExecutorService` keeps it out of Spring's `@Async` / `TaskExecutor`
  resolution.
- **`HomeController`** takes `Parallel` and runs three waves:
  1. `openAssignments(agent, now)`, `mandates(agent)`, `loadVisits(agent)`;
  2. `offers(agent, open)`, `mandateStats(mandates)`, `listings(agent, mandates)`, `loadVisitListings(visits)`,
     `loadVisitCustomers(visits)`;
  3. `names(...)` and `listingStats(listings)`.

  `offers()` is split into `openAssignments()` and `offers(agent, open)` so its second search runs in wave 2 instead
  of extending wave 1. The critical path becomes: agent → assignments → offered mandates → names, or agent → active
  mandates → listings → listing activity → names; four round trips.
- **Threading configuration: none required for the parallelism itself.** Virtual threads have no pool size to tune;
  the only setting is the executor's lifecycle (`destroyMethod = "close"`). Do **not** set
  `spring.task.execution.*`: that configures Spring's `TaskExecutor` (used by `@Async`, which this app doesn't use).
  `spring.threads.virtual.enabled` is a separate, app-wide throughput decision (it doesn't make this page faster), and
  a bounded outbound connection pool (`JdkClientHttpRequestFactory` or Apache HttpClient) is a follow-up if the ~5
  concurrent connections per request ever become a capacity problem.
- **Failure semantics unchanged.** Every loader keeps its `try/catch RestClientException` and returns `null` (or an
  empty map); a task failure surfaces as a `CompletionException` at `join()` only for a bug, not for a server error.
- **The `Model` stays on the request thread.** Tasks return values; all `model.addAttribute` calls happen after the
  joins, as today.
- **Tests.** A new `ParallelTest` pins context propagation (including the `Authorization` header), real concurrency,
  reset after the task, and failure propagation. `HomeControllerTest` gains the constructor argument and an
  `@AfterEach` that closes the executor, plus a case proving no `Model` write happens on a worker thread.

## What is left to the builder

Nothing open. Names, signatures and commands are pinned in the implementation plan.
