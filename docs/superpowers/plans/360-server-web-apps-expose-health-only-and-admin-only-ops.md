# Health-only actuator and admin-only ops endpoints — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Outside local, the four apps expose only `/actuator/health` without details, and every `@OpsEndpoint` of
ndopify-server requires an `admin-console` token with the `admin` role.

**Architecture:** One `HandlerInterceptor` (`EndpointConfiguration`) checks the mapped `HandlerMethod` for
`@OpsEndpoint` and answers 403 `HTTP_ACCESS_DENIED` to a non-admin; the server's TEST profile locks
`management.endpoints.web.exposure` to `health` and hides details (PROD already does); Partner Central permits
`/actuator/health` and denies the rest of `/actuator/**`, and ndopify-web / the e-signature portal add
`/actuator/health` to the only paths they permit. Each web app's `application-test.yml` gets the same health-only,
no-details exposure as its `application-prod.yml`.

**Tech Stack:** Kotlin, Spring Boot 4 / Spring Framework 7 (MVC interceptors, Spring Security request matchers),
`TestRestTemplate` against MySQL for the server, `MockMvc` (`webAppContextSetup` + `springSecurity()`) for the web
apps, `YamlPropertySourceLoader` for the profile configuration, JUnit 5 / `kotlin.test`, mockito-kotlin.

**Spec:** story #360 — https://github.com/wutsi/ndopify-mono/issues/360 — enriched body (*Context*, *Scope*,
*Decisions and assumptions*, *Acceptance criteria*, *Risks*, *Out of scope*).

## Global Constraints

- `UI: no`, `Shared code: none`: no Thymeleaf template, no `src/main/html/` prototype, no `ndopify-dto` /
  `ndopify-web-core` change, no release, no migration.
- **Admin** is `CallerService.isAdmin` exactly: an `admin-console` token whose roles contain `RoleCode.ADMIN` (`admin`).
  No new role, no Partner Central admin, no bypass.
- Non-admin on an ops route → 403 `HTTP_ACCESS_DENIED`, the endpoint never runs. Anonymous → 401 `AUTH_UNAUTHORIZED`
  (the security filter, before the check). Ops disabled (`ndopify.endpoints.ops: false`, as in PROD) → 404
  `HTTP_NOT_FOUND`, admin or not (the route is not mapped).
- TEST keeps the ops routes enabled: `ndopify.endpoints.ops: true` stays, written explicitly in
  `application-test.yml`.
- Health details: `never` in `test` and `prod`; `always` in local (`application.yml`) and in the server's `qa` profile
  (the test suite reads the components; `qa` is never deployed).
- The local profile keeps today's server behaviour: all six actuator endpoints exposed, `/actuator/**` public, details
  shown. `/actuator/**` stays in `PublicEndpoints` — on the server the TEST/PROD exposure is what hides the others.
- `/actuator/health` includes its groups (`/actuator/health/llm`); unchanged.
- No new public endpoint besides `/actuator/health` on the web apps (status only when details are off).
- Test conventions: `kotlin.test` assertions, mockito-kotlin mocks, one SQL fixture per server endpoint test.
  `mvn verify` in each module (ktlint, tests, JaCoCo); `ndopify-server` needs local MySQL, `ndopify-partner-central`
  and `ndopify-web` need network for `npm ci` on a clean build.

## Files

| Module | Create | Modify |
|---|---|---|
| ndopify-server | `platform/endpoint/OpsEndpointAuthorizationInterceptor.kt`, `test/.../OpsEndpointAuthorizationInterceptorTest.kt`, `test/.../OpsEndpointAuthorizationEndpointTest.kt`, `test/.../config/TestProfileConfigurationTest.kt` | `config/platform/EndpointConfiguration.kt`, `test/.../AuthorizationAwareEndpointIntegrationTest.kt`, the 7 ops-endpoint test classes listed in Task 1, `application-test.yml`, `application.yml`, `application-prod.yml`, `platform/endpoint/OpsEndpoint.kt`, `mandate/server/endpoint/MandateDocumentEndpoints.kt`, `modules/ndopify-server/README.md`, `modules/ndopify-server/CLAUDE.md` |
| ndopify-partner-central | — | `config/SecurityConfiguration.kt`, `application-test.yml`, `test/.../config/SecurityConfigurationTest.kt`, `modules/ndopify-partner-central/CLAUDE.md` |
| ndopify-web | — | `config/SecurityConfiguration.kt`, `application-test.yml`, `test/.../config/SecurityFilterChainTest.kt`, `modules/ndopify-web/CLAUDE.md` |
| ndopify-esignature | `test/.../config/SecurityConfigurationTest.kt` | `config/SecurityConfiguration.kt`, `application-test.yml`, `modules/ndopify-esignature/CLAUDE.md` |

## Review Focus

1. **A non-admin calls an ops route that has no domain access check** (`POST /v1/kyc/cases/{id}/verify`): 403
   `HTTP_ACCESS_DENIED`, and the case stays `SUBMITTED` — nothing is verified. Task 1.
2. **An anonymous call to an ops route**: 401 `AUTH_UNAUTHORIZED`, not 403 — the security filter answers before the
   role check. Task 1.
3. **A signed-in Partner Central agent calls `/actuator/beans`**: 403, not a bean/mapping dump; `/actuator/health`
   without a session: 200. Task 3.
4. **TEST on ndopify-server**: `/actuator/beans`, `/actuator/mappings`, `/actuator/info` answer 404, and the body of
   `/actuator/health` has no component — pinned by the profile test, because the exposure (not the security list) is
   what hides them. Task 2.
5. **Ops off (PROD) with an admin token**: 404, not 403 — the absent mapping wins. Existing
   `DispatchMandateOpsEndpointDisabledTest` (`roles = admin`) already pins it and must stay green. Task 1.

---

### Task 1: ndopify-server — an `@OpsEndpoint` is an admin's

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/endpoint/OpsEndpointAuthorizationInterceptor.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EndpointConfiguration.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/endpoint/OpsEndpointAuthorizationInterceptorTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/endpoint/OpsEndpointAuthorizationEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/AuthorizationAwareEndpointIntegrationTest.kt`
- Modify the ops-endpoint tests: `party/endpoint/VerifyKycCaseEndpointTest.kt`,
  `mandate/server/endpoint/ExtractMandateDocumentsByTypeEndpointTest.kt`,
  `mandate/server/endpoint/ExtractMandateDocumentEndpointTest.kt`,
  `mandate/server/endpoint/ExpireAssignmentEndpointTest.kt`,
  `match/server/endpoint/DismissMatchEndpointTest.kt`, `match/server/endpoint/ShortlistMatchEndpointTest.kt`,
  `match/server/endpoint/ProposeMatchEndpointTest.kt`
- Modify docs: `platform/endpoint/OpsEndpoint.kt`, `mandate/server/endpoint/MandateDocumentEndpoints.kt`,
  `modules/ndopify-server/src/main/resources/application.yml`,
  `modules/ndopify-server/src/main/resources/application-prod.yml`, `modules/ndopify-server/README.md`,
  `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: `CallerService.getPrincipal(): JwtPrincipal` (401 without a token), `CallerService.isAdmin(principal: JwtPrincipal): Boolean`,
  `ForbiddenException(Error(code = ErrorCode.HTTP_ACCESS_DENIED))` (403 through `ControllerErrorHandler`).
- Produces: `OpsEndpointAuthorizationInterceptor(callerService: CallerService) : HandlerInterceptor` (checks every
  `HandlerMethod` annotated `@OpsEndpoint`); in the tests,
  `createAccessToken(application: String = ApplicationCode.ADMIN_CONSOLE, roles: Array<String> = this.roles): String`
  and `asAdmin(block: () -> T): T`.

- [ ] **Step 1: Write the failing unit test**

```kotlin
package com.wutsi.ndopify.platform.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpsEndpointAuthorizationInterceptorTest {
    private class Endpoints {
        @OpsEndpoint
        fun ops() {}

        fun regular() {}
    }

    private val caller = mock<CallerService>()
    private val interceptor = OpsEndpointAuthorizationInterceptor(caller)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()

    private fun handler(name: String) = HandlerMethod(Endpoints(), Endpoints::class.java.getMethod(name))

    @Test
    fun `an admin calls an ops endpoint`() {
        val principal = mock<JwtPrincipal>()
        doReturn(principal).whenever(caller).getPrincipal()
        doReturn(true).whenever(caller).isAdmin(principal)

        assertTrue(interceptor.preHandle(request, response, handler("ops")))
    }

    @Test
    fun `a non-admin is forbidden`() {
        val principal = mock<JwtPrincipal>()
        doReturn(principal).whenever(caller).getPrincipal()
        doReturn(false).whenever(caller).isAdmin(principal)

        assertFailsWith<ForbiddenException> { interceptor.preHandle(request, response, handler("ops")) }
    }

    @Test
    fun `another handler is not checked`() {
        assertTrue(interceptor.preHandle(request, response, handler("regular")))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=OpsEndpointAuthorizationInterceptorTest` (MySQL up).
Expected: compilation failure — `OpsEndpointAuthorizationInterceptor` is unresolved.

- [ ] **Step 3: Create `OpsEndpointAuthorizationInterceptor`**

```kotlin
package com.wutsi.ndopify.platform.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * An [OpsEndpoint] is an admin's: an `admin-console` token with the `admin` role ([CallerService.isAdmin]). A
 * non-admin gets 403 `HTTP_ACCESS_DENIED` before the endpoint runs; a request without a token is a 401 from the
 * security filter, so [CallerService.getPrincipal] never throws here.
 */
class OpsEndpointAuthorizationInterceptor(private val callerService: CallerService) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && AnnotatedElementUtils.hasAnnotation(handler.method, OpsEndpoint::class.java)) {
            if (!callerService.isAdmin(callerService.getPrincipal())) {
                throw ForbiddenException(Error(code = ErrorCode.HTTP_ACCESS_DENIED))
            }
        }
        return true
    }
}
```

- [ ] **Step 4: Run the unit test, verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=OpsEndpointAuthorizationInterceptorTest`
Expected: 3 tests pass.

- [ ] **Step 5: Write the failing integration test**

`POST /v1/mandates/999/dispatch` is the probe: mandate 999 does not exist, so an admin reaches the endpoint and gets
its 404, while a non-admin is stopped at the rule with 403 — the order the rule promises.

```kotlin
package com.wutsi.ndopify.platform.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchMandateEndpoint.sql"])
class OpsEndpointAuthorizationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private fun dispatch() = rest.postForEntity("/v1/mandates/999/dispatch", null, ErrorResponse::class.java)

    @Test
    fun `a non-admin is forbidden`() {
        val response = dispatch()

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `an admin reaches the endpoint`() {
        admin = true

        val response = dispatch()

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `an anonymous caller is unauthorized`() {
        anonymousUser = true

        val response = dispatch()

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
```

- [ ] **Step 6: Run the integration test, verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=OpsEndpointAuthorizationEndpointTest`
Expected: `a non-admin is forbidden` fails: 404 `MANDATE_NOT_FOUND` (the endpoint ran), not 403.

- [ ] **Step 7: Wire the interceptor in `EndpointConfiguration`**

```kotlin
@Configuration
open class EndpointConfiguration(
    // Off when missing: an environment without the setting never exposes them
    @param:Value("\${ndopify.endpoints.ops:false}") private val opsEnabled: Boolean,
    private val callerService: CallerService,
) : WebMvcConfigurer {
    @Bean
    open fun webMvcRegistrations(): WebMvcRegistrations { /* unchanged body */ }

    // Applied to the mapping webMvcRegistrations() returns too: Spring Boot sets its interceptors on every
    // RequestMappingHandlerMapping it creates (WebMvcConfigurationSupport.initHandlerMapping).
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(OpsEndpointAuthorizationInterceptor(callerService))
    }
}
```

Add the `CallerService`, `InterceptorRegistry` and `WebMvcConfigurer` imports; keep the existing
`webMvcRegistrations()` body as is.

- [ ] **Step 8: Run the integration test, verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=OpsEndpointAuthorizationEndpointTest`
Expected: 3 tests pass.

- [ ] **Step 9: Widen the test base's token helper**

`AuthorizationAwareEndpointIntegrationTest` builds its `Authorization` header in `intercept`, *after* any header a test
sets on the request, so a test that needs one admin call (the `/v1/matches/run` fixture) needs the base's support:

- add `private var accessTokenOverride: String? = null`;
- in `intercept`, use `accessTokenOverride ?: createAccessToken(application)`;
- widen `createAccessToken` to
  `protected open fun createAccessToken(application: String = ApplicationCode.ADMIN_CONSOLE, roles: Array<String> = this.roles): String`.

Existing call sites keep working (`createAccessToken()` and `createAccessToken(application)`).

- [ ] **Step 10: Add the `asAdmin` helper to the test base**

```kotlin
    /** Runs [block] as an `admin-console` admin, whatever the test's own caller. */
    protected fun <T> asAdmin(block: () -> T): T {
        accessTokenOverride = createAccessToken(roles = arrayOf(RoleCode.ADMIN))
        try {
            return block()
        } finally {
            accessTokenOverride = null
        }
    }
```

Import `com.wutsi.ndopify.refdata.dto.RoleCode`.

- [ ] **Step 11: Adapt the ops-endpoint tests that call with a non-admin token**

- `party/endpoint/VerifyKycCaseEndpointTest` — add
  `private var admin = true` and
  `override val roles: Array<String> get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")`
  (import `com.wutsi.ndopify.refdata.dto.RoleCode`), then add the Review-Focus-1 test:

```kotlin
    @Test
    fun `a non-admin is forbidden and nothing is verified`() {
        admin = false

        val response = verifyWithError("kyc-case-submitted")

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertCase(KycStatus.SUBMITTED, null)
    }
```

- `mandate/server/endpoint/ExtractMandateDocumentsByTypeEndpointTest`,
  `mandate/server/endpoint/ExtractMandateDocumentEndpointTest`,
  `mandate/server/endpoint/ExpireAssignmentEndpointTest` — add
  `override val roles = arrayOf(RoleCode.ADMIN)` (their other calls are reads an admin may make; the anonymous test in
  `ExtractMandateDocumentEndpointTest` stays 401).
- `match/server/endpoint/DismissMatchEndpointTest` (`a run leaves it dismissed`),
  `match/server/endpoint/ShortlistMatchEndpointTest` (`a run leaves it shortlisted`) and
  `match/server/endpoint/ProposeMatchEndpointTest` (`a run leaves it proposed`) — wrap the
  `POST /v1/matches/run?mandate-id=1` setup call: `asAdmin { rest.postForEntity(...) }`; the endpoint under test keeps
  the class's usual caller.
- `GenerateSignedContentEndpointTest` already covers the non-admin 403 (`admin = false`); leave it. Its non-admin is
  now stopped by the new rule instead of `ensureCanUpdate`, same status, same result.

- [ ] **Step 12: Fix the docs that call the ops endpoints unauthenticated**

- `platform/endpoint/OpsEndpoint.kt` — KDoc: "Only an admin may call it: an `admin-console` token with the `admin` role
  (`CallerService.isAdmin`), else 403 `HTTP_ACCESS_DENIED` ([OpsEndpointAuthorizationInterceptor])."
- `application.yml` — `ops: true # @OpsEndpoint endpoints (admin-only): off in prod`.
- `application-prod.yml` — `ops: false # The @OpsEndpoint endpoints are admin-only, and still off here`.
- `mandate/server/endpoint/MandateDocumentEndpoints.kt` — "No access check: it's operational only." becomes
  "Admin-only (the `@OpsEndpoint` rule); no domain access check."
- `modules/ndopify-server/CLAUDE.md` (`platform/endpoint` bullet) — replace "They need a token (any user's: none checks
  a role yet)" with the admin rule (401 without a token, 403 `HTTP_ACCESS_DENIED` for a non-admin, 404 when disabled).
- `modules/ndopify-server/README.md` (`platform.endpoint` bullet) — add "and admin-only (an `admin-console` token with
  the `admin` role)".

- [ ] **Step 13: Run the module and commit**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS (ktlint, all tests, JaCoCo).

```bash
git add modules/ndopify-server
git commit -m "feat(server): require an admin for the ops endpoints" -m "Refs #360"
```

### Task 2: ndopify-server — TEST exposes only health, without details

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application-test.yml`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/TestProfileConfigurationTest.kt`

**Interfaces:**
- Consumes: nothing (configuration).
- Produces: the TEST profile's `management` block and its explicit `ndopify.endpoints.ops: true`.

- [ ] **Step 1: Write the failing profile test**

```kotlin
package com.wutsi.ndopify.config

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The deployed TEST profile (`application-test.yml`): only the health endpoint, without details; the ops routes stay
 * enabled (admin-only). PROD is already health-only with details off (`application-prod.yml`).
 */
class TestProfileConfigurationTest {
    private val profile = YamlPropertySourceLoader()
        .load("test", ClassPathResource("application-test.yml"))
        .single()
        .source

    @Test
    fun `only health is exposed`() =
        assertEquals("health", profile.getProperty("management.endpoints.web.exposure.include"))

    @Test
    fun `health details are never shown`() =
        assertEquals("never", profile.getProperty("management.endpoint.health.show-details"))

    @Test
    fun `the ops endpoints stay enabled`() =
        assertEquals("true", profile.getProperty("ndopify.endpoints.ops").toString())
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TestProfileConfigurationTest`
Expected: the three assertions fail with `null` (the profile sets none of the keys).

- [ ] **Step 3: Lock the TEST profile**

Add at the top of `application-test.yml` (same shape as `application-prod.yml`):

```yaml
management:
    endpoint:
        health:
            show-details: never
    endpoints:
        web:
            exposure:
                include: "health"
```

and inside the existing `ndopify:` block, before `database:`:

```yaml
    endpoints:
        ops: true                          # Kept on in TEST: admin-only (platform/endpoint)
```

- [ ] **Step 4: Run it, verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TestProfileConfigurationTest`
Expected: 3 tests pass.

- [ ] **Step 5: Run the health tests and commit**

Run: `cd modules/ndopify-server && mvn test -Dtest='LlmHealthEndpointTest,SesEmailVerifierHealthEndpointTest,TelegramHealthEndpointTest'`
Expected: pass (`qa` keeps details on; nothing changed there).

```bash
git add modules/ndopify-server/src/main/resources/application-test.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/TestProfileConfigurationTest.kt
git commit -m "feat(server): expose only health in the TEST profile" -m "Refs #360"
```

### Task 3: ndopify-partner-central — only `/actuator/health` is reachable

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/application-test.yml`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/SecurityConfigurationTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the deployment note, "reduced actuator exposure")

**Interfaces:**
- Consumes: Spring Security's `requestMatchers(...).permitAll()/denyAll()` (first match wins).
- Produces: the `npc` chain permits `/actuator/health` and denies every other `/actuator/**`; the test profile is
  health-only, no details.

- [ ] **Step 1: Extend the security test (fails first)**

Add to `SecurityConfigurationTest` (keep the renewal test):

```kotlin
    @Autowired
    private lateinit var context: WebApplicationContext

    @Test
    fun `health answers without a session`() {
        val response = mvc().perform(get("/actuator/health")).andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"status\":\"UP\""))
    }

    @Test
    fun `the other actuator endpoints are denied`() {
        val response = mvc().perform(get("/actuator/beans")).andReturn().response

        assertEquals(403, response.status)
    }

    @Test
    fun `the test profile exposes only health, without details`() {
        val profile = YamlPropertySourceLoader()
            .load("test", ClassPathResource("application-test.yml"))
            .single()
            .source

        assertEquals("health", profile.getProperty("management.endpoints.web.exposure.include"))
        assertEquals("never", profile.getProperty("management.endpoint.health.show-details"))
    }

    private fun mvc(): MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(SecurityMockMvcConfigurers.springSecurity())
        .build()
```

Imports: `org.springframework.web.context.WebApplicationContext`, `org.springframework.test.web.servlet.MockMvc`,
`MockMvcBuilders`, `org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers`,
`org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get`,
`org.springframework.boot.env.YamlPropertySourceLoader`, `org.springframework.core.io.ClassPathResource`,
`kotlin.test.assertEquals`, `kotlin.test.assertTrue`.

- [ ] **Step 2: Run it, verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SecurityConfigurationTest`
Expected: `health answers without a session` fails (the login interstitial's HTML, or a 302 to `/login`, has no
`"status":"UP"`), `the other actuator endpoints are denied` fails (the login interstitial is 200, or the redirect is
302), and the profile test fails on `null`s.

- [ ] **Step 3: Permit health, deny the rest, and refuse an anonymous actuator call without the login page**

In `SecurityConfiguration.filterChain`, before `.anyRequest().authenticated()`:

```kotlin
                    .requestMatchers("/actuator/health").permitAll() // Heroku's probe, without a session
                    .requestMatchers("/actuator/**").denyAll() // never beans, mappings, info or the others
```

Spring Security sends an *anonymous* denial to the entry point, whatever the rule: with `SignInTargetEntryPoint` that is
the login page (an interstitial or a 302), not 403. Replace `.exceptionHandling { it.authenticationEntryPoint(SignInTargetEntryPoint("/login")) }`
with an entry point that keeps the sign-in behaviour for pages and answers 403 for `/actuator/**`:

```kotlin
    private val signInEntryPoint = SignInTargetEntryPoint("/login")

    // An anonymous call to an actuator path is refused with 403, never sent to the sign-in page: only
    // /actuator/health is permitted, the rest is denied (the rule above).
    private val entryPoint = AuthenticationEntryPoint { request, response, exception ->
        if (request.requestURI.startsWith("/actuator/")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
        } else {
            signInEntryPoint.commence(request, response, exception)
        }
    }
```

and use `.exceptionHandling { it.authenticationEntryPoint(entryPoint) }`. Imports:
`jakarta.servlet.http.HttpServletResponse`, `org.springframework.security.web.AuthenticationEntryPoint`.

- [ ] **Step 4: Lock the test profile**

Add at the top of `application-test.yml`, above `ndopify:`:

```yaml
management:
    endpoint:
        health:
            show-details: never
    endpoints:
        web:
            exposure:
                include: "health"
```

- [ ] **Step 5: Run it, verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SecurityConfigurationTest`
Expected: 4 tests pass.

- [ ] **Step 6: Update the module note and commit**

In `modules/ndopify-partner-central/CLAUDE.md` (last line of the `src/main/html/` bullet), replace
"reduced actuator exposure" with "only `/actuator/health` (the rest of `/actuator/**` is denied), no health details".

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): expose only the actuator health endpoint" -m "Refs #360"
```

### Task 4: ndopify-web — only `/actuator/health` is reachable

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Modify: `modules/ndopify-web/src/main/resources/application-test.yml`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/config/SecurityFilterChainTest.kt`
- Modify: `modules/ndopify-web/CLAUDE.md` (the permitted-paths bullet)

**Interfaces:**
- Consumes: the same Spring Security matchers as Task 3; the chain already ends with `anyRequest().denyAll()`.
- Produces: `/actuator/health` permitted; every other `/actuator/**` stays denied by `anyRequest().denyAll()`.

- [ ] **Step 1: Extend the chain test (fails first)**

Add to `SecurityFilterChainTest` the same three tests as Task 3 Step 1 (health 200, beans 403, profile keys), with the
same imports and `mvc()` helper.

- [ ] **Step 2: Run it, verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=SecurityFilterChainTest`
Expected: `health answers without a session` fails (403), the profile test fails on `null`s.

- [ ] **Step 3: Permit health**

In `SecurityConfiguration.filterChain`, with the other `permitAll()` rules:

```kotlin
                    .requestMatchers("/actuator/health").permitAll() // Heroku's probe, no session, no details
```

- [ ] **Step 4: Lock the test profile**

The same `management:` block as Task 3 Step 4, at the top of `application-test.yml`.

- [ ] **Step 5: Run it, verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=SecurityFilterChainTest`
Expected: 3 tests pass.

- [ ] **Step 6: Update the module note and commit**

In `modules/ndopify-web/CLAUDE.md` (the `config/` bullet listing the permitted paths), add `/actuator/health` to the
paths `SecurityConfiguration` permits, next to `/manifest.json` and `/error`.

```bash
git add modules/ndopify-web
git commit -m "feat(web): expose only the actuator health endpoint" -m "Refs #360"
```

### Task 5: ndopify-esignature — only `/actuator/health` is reachable

**Files:**
- Modify: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/config/SecurityConfiguration.kt`
- Modify: `modules/ndopify-esignature/src/main/resources/application-test.yml`
- Create: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/config/SecurityConfigurationTest.kt`
- Modify: `modules/ndopify-esignature/CLAUDE.md` (the permitted-paths bullet)

**Interfaces:**
- Consumes: the same matchers; this chain is `anyRequest().denyAll()`, like ndopify-web's.
- Produces: a `@SpringBootTest` security test for the module (its first), plus `/actuator/health` reached without a
  session.

- [ ] **Step 1: Write the failing security test**

Same three tests and `mvc()` helper as Task 3 Step 1, in
`modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/config/SecurityConfigurationTest.kt`, with
`@SpringBootTest` and the `context` injection (`org.springframework.boot.test.context.SpringBootTest`).

- [ ] **Step 2: Run it, verify it fails**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=SecurityConfigurationTest`
Expected: health 403, profile `null`s — or a context-startup failure if the test context needs a property; if so, add
`@TestPropertySource(properties = ["ndopify.server.api.base-url=http://localhost:8080"])` and rerun (the base
`application.yml` already defaults it).

- [ ] **Step 3: Permit health**

In `SecurityConfiguration.filterChain`, with the other `permitAll()` rules:

```kotlin
                    .requestMatchers("/actuator/health").permitAll() // Heroku's probe, no session, no details
```

- [ ] **Step 4: Lock the test profile**

The same `management:` block as Task 3 Step 4, at the top of `application-test.yml`.

- [ ] **Step 5: Run it, verify it passes**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=SecurityConfigurationTest`
Expected: 3 tests pass.

- [ ] **Step 6: Update the module note and commit**

In `modules/ndopify-esignature/CLAUDE.md` (the `SecurityConfiguration` bullet), add `/actuator/health` to the permitted
paths — "permits only `/assets/**`, `/sign/**`, `/verify/**`, `/actuator/health` and `/error`".

```bash
git add modules/ndopify-esignature
git commit -m "feat(esignature): expose only the actuator health endpoint" -m "Refs #360"
```
