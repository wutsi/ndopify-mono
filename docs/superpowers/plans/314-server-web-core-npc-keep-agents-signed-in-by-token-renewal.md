# Access-token Renewal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An agent who opens Partner Central at least once in 30 days never signs in again, and nobody stays signed in more than 90 days from the first sign-in, by renewing the current access token through a new authenticated `POST /v1/auth/refresh` and a `ndopify-web-core` filter both web apps register.

**Architecture:** ndopify-server gains one endpoint that re-issues the caller's own token with a fresh life, bounded by a new `max-lifetime` cap and a new `sessionStartedAt` claim copied across renewals. `ndopify-web-core` gains the client that calls it and a `OncePerRequestFilter` that renews the cookie's token once a day, silently ignoring every failure; `ndopify-partner-central` and `ndopify-web` only register it. No refresh token, no table, no `ndopify-dto` change.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Security 7, `java-jwt` (HS256), `RestTemplate`, `kotlin.test` + `mockito-kotlin`, `MockRestServiceServer`, `MockHttpServletRequest`, Thymeleaf apps without a database, `ndopify-web-core` released to GitHub Packages.

**Spec:** GitHub issue #314 (`https://github.com/wutsi/ndopify-mono/issues/314`) — its description holds the intent, the decisions settled with the requester on 2026-11-24, the design, the acceptance criteria and the implementation calls. `UI: no`, so there is no prototype.

## Global Constraints

- **Two pull requests, in this order, never the reverse.**
  1. `feat/314-server-web-core-access-token-renewal`: Tasks 1–6, `ndopify-server` + `ndopify-web-core`. Its merge deploys the endpoint and releases `ndopify-web-core-0.0.11` (the module moves to `0.0.12-SNAPSHOT`).
  2. `feat/314-web-npc-access-token-renewal`: Tasks 7–8, the root `pom.xml` bump to `0.0.11` + Partner Central + `ndopify-web`, whose body says `Closes #314`.
  The apps resolve `ndopify-web-core` from GitHub Packages, never from the reactor: the bump and the wiring cannot build before the release exists.
- **The values, verbatim** — `partner-central: 30d` (was `7d`), `e-signature: 1h`, `public-portal: 5d`, `match-link: 365d`, `default-ttl: 1h` all unchanged, plus `max-lifetime: 90d`; the web apps' renewal interval is 1 day.
- **The renewal rule, verbatim** — `iat` is the issue time of *this* token, so `exp - iat` is its lifetime class; `sessionStartedAt` (epoch millis) is copied across renewals and read as `iat` when the token has no claim. A renewal first refuses past the cap (`now >= sessionStartedAt + max-lifetime` → `409 AUTH_CREDENTIALS_EXPIRED`), then re-issues with `ttl = min(lifetime, sessionStartedAt + max-lifetime - now)`; a token whose lifetime class is longer than `max-lifetime` (a match link) is re-issued with `ttl = exp - now`, so its `exp` never moves. A renewal never shortens a token and never extends one past the cap.
- **No `ndopify-dto` change, no Flyway migration, no new environment variable, no revocation.** The `sessionStartedAt` claim name is a constant on ndopify-server's `AccessTokenService`, not on `JwtPrincipal`.
- **The endpoint is authenticated**: `POST /v1/auth/refresh`, no request body, answers the existing `AuthenticateResponse`; it is not added to `PublicEndpoints`, and `POST /v1/auth`'s matcher stays exact.
- **The filter is silent**: any failure (network, 409, 5xx, a token it cannot decode) is logged at debug and ignored; the page renders with the current cookie. It never runs for static assets.
- **No UI change**: no template, no page, no text, no script. The e-signature portal is untouched.
- Assertions are `kotlin.test` (`assertEquals`, `assertTrue`, `assertFailsWith`, …), mocks are `mockito-kotlin`; UI text is French, code, comments and docs English.
- Every touched module ends green: `mvn -s ../../settings.xml verify` from the module directory (ktlint + tests + JaCoCo); `ndopify-server` needs its local MySQL. From the repo root: `mvn -s settings.xml verify -pl modules/<module>`.
- Commits carry `Refs #314`.

## Review Focus

1. **A token renewed on day 20 of its 30-day life** — it must get 30 more days, not the 10 it has left; that is what makes the idle window the TTL. Task 2's `renewal` table and Task 3's `refresh`.
2. **A session near or past the cap** — a 30-day token issued on day 85 gets 5 days, not 30; from day 90 the refresh is a 409 and the still-valid token is untouched. Task 2, Task 3, Task 4.
3. **A 365-day match link** — renewal returns the same `exp`: never `now + 365d`, never `sessionStartedAt + 90d`. Task 2, Task 3.
4. **A renewal that fails** (server down, 409, 5xx, malformed token) — the page still renders, no 500, the cookie is left as it was. Task 6.
5. **A request that must not renew** — a static asset, `/error`, or no cookie at all: the server is never called. Task 6.

---

### Task 1: The token says when its session started

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenServiceTest.kt`

**Interfaces:**
- Consumes: `AccessTokenProperties` (the TTL map), the injected `Clock` and `HttpServletRequest`.
- Produces, for Tasks 2–4:
  - `AccessTokenService.CLAIM_SESSION_STARTED_AT = "sessionStartedAt"`
  - `data class AccessToken(val principal: JwtPrincipal, val issuedAt: Instant, val expiresAt: Instant, val sessionStartedAt: Instant)` (top-level in `AccessTokenService.kt`, same package)
  - `fun create(application: String, userId: Long, roles: Array<String>, tenantId: Long?, ttlSeconds: Int = properties.ttl(application).seconds.toInt(), partyId: Long? = null, issuedAt: Instant = Instant.ofEpochMilli(clock.millis()), sessionStartedAt: Instant = issuedAt): String`
  - `fun getAccessToken(): AccessToken` — reads and verifies the `Authorization` header; 401 `AUTH_UNAUTHORIZED` without a header, the same `ForbiddenException` as `getPrincipalOrNull` for a token that does not verify.

- [ ] **Step 1: Write the failing tests**

Add to `AccessTokenServiceTest` (keep the existing tests; they must still pass). Use instants relative to `Instant.now()` — `decode` verifies `exp` against the system clock, not the mocked `Clock`:

```kotlin
    @Test
    fun `create with an issue date and a session start`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val sessionStartedAt = issuedAt.minus(Duration.ofDays(6))

        val token = service.create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 111L,
            roles = emptyArray(),
            tenantId = 1L,
            ttlSeconds = Duration.ofDays(30).seconds.toInt(),
            issuedAt = issuedAt,
            sessionStartedAt = sessionStartedAt,
        )

        val jwt = JWT.decode(token)
        assertEquals(issuedAt, jwt.issuedAtAsInstant)
        assertEquals(issuedAt.plus(Duration.ofDays(30)), jwt.expiresAtAsInstant)
        assertEquals(sessionStartedAt.toEpochMilli(), jwt.getClaim(AccessTokenService.CLAIM_SESSION_STARTED_AT).asLong())
    }

    @Test
    fun `getAccessToken - valid token`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val sessionStartedAt = issuedAt.minus(Duration.ofDays(6))
        val token = service.create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 111L,
            roles = arrayOf("ROLE_USER"),
            tenantId = 222L,
            partyId = 333L,
            ttlSeconds = 3600,
            issuedAt = issuedAt,
            sessionStartedAt = sessionStartedAt,
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val accessToken = service.getAccessToken()

        assertEquals(111L, accessToken.principal.getUserId())
        assertEquals(222L, accessToken.principal.getTenantId())
        assertEquals(333L, accessToken.principal.getPartyId())
        assertEquals(issuedAt, accessToken.issuedAt)
        assertEquals(issuedAt.plusSeconds(3600), accessToken.expiresAt)
        assertEquals(sessionStartedAt, accessToken.sessionStartedAt)
    }

    @Test
    fun `getAccessToken - a token without sessionStartedAt reads it from iat`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val token = JWT.create()
            .withIssuer(AccessTokenService.ISSUER)
            .withIssuedAt(issuedAt)
            .withExpiresAt(issuedAt.plusSeconds(3600))
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 111L)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 222L)
            .sign(Algorithm.HMAC256(SECRET))
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        assertEquals(issuedAt, service.getAccessToken().sessionStartedAt)
    }

    @Test
    fun `getAccessToken - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<UnauthorizedException> { service.getAccessToken() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getAccessToken - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getAccessToken() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AccessTokenServiceTest`
Expected: compilation fails — `create` has no `issuedAt`, `getAccessToken` does not exist.

- [ ] **Step 3: Implement it**

In `AccessTokenService`:

- Add `const val CLAIM_SESSION_STARTED_AT = "sessionStartedAt"` to the companion.
- Add the two parameters to `create`, and build the token from them: `.withIssuedAt(issuedAt)`, `.withExpiresAt(issuedAt.plusSeconds(ttlSeconds.toLong()))`, `.withClaim(CLAIM_SESSION_STARTED_AT, sessionStartedAt.toEpochMilli())`. `partyId` and `tenantId` are written exactly as today.
- Extract the verifying part of `decode` into `private fun verify(accessToken: String): DecodedJWT` (the `JWT.require(getAlgorithm()).withIssuer(ISSUER).build().verify(...)`), have `decode` return `JwtPrincipal(verify(accessToken))`, and add:

```kotlin
    /** The current request's token, verified, with the instants a renewal needs. */
    fun getAccessToken(): AccessToken {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?: throw UnauthorizedException(error = Error(code = ErrorCode.AUTH_UNAUTHORIZED))
        val jwt = try {
            verify(header.removePrefix("Bearer "))
        } catch (ex: Exception) {
            throw ForbiddenException(Error(code = ErrorCode.AUTH_UNAUTHORIZED, message = ex.message), ex)
        }
        val issuedAt = requireNotNull(jwt.issuedAtAsInstant)
        return AccessToken(
            principal = JwtPrincipal(jwt),
            issuedAt = issuedAt,
            expiresAt = requireNotNull(jwt.expiresAtAsInstant),
            sessionStartedAt = jwt.getClaim(CLAIM_SESSION_STARTED_AT).asLong()
                ?.let { Instant.ofEpochMilli(it) } ?: issuedAt,
        )
    }
```

Update the class KDoc: a token now records the session it belongs to, so a renewal can cap it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AccessTokenServiceTest`
Expected: PASS (the existing tests included).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenServiceTest.kt
git commit -m "feat(server): record when a token's session started" -m "Refs #314"
```

### Task 2: The renewal window, as a pure rule

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenProperties.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenPropertiesTest.kt` (new)

**Interfaces:**
- Produces, for Task 3:
  - `val maxLifetime: Duration = Duration.ofDays(90)` on `AccessTokenProperties`
  - `fun renewal(lifetime: Duration, now: Instant, expiresAt: Instant, sessionStartedAt: Instant): Duration?` — the life a renewal gives, `null` past the cap.

- [ ] **Step 1: Write the failing test**

```kotlin
class AccessTokenPropertiesTest {
    private val properties = AccessTokenProperties()
    private val now = Instant.parse("2026-11-24T00:00:00Z")

    @Test
    fun `a renewal gives the token's own lifetime, not what is left of it`() {
        assertEquals(
            Duration.ofDays(30),
            properties.renewal(
                lifetime = Duration.ofDays(30),
                now = now,
                expiresAt = now.plus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(20)),
            ),
        )
    }

    @Test
    fun `a renewal near the cap gives only what the cap leaves`() {
        assertEquals(
            Duration.ofDays(5),
            properties.renewal(
                lifetime = Duration.ofDays(30),
                now = now,
                expiresAt = now.plus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(85)),
            ),
        )
    }

    @Test
    fun `a session at or past the cap is not renewed`() {
        assertNull(properties.renewal(Duration.ofDays(30), now, now.plus(Duration.ofDays(10)), now.minus(Duration.ofDays(90))))
        assertNull(properties.renewal(Duration.ofDays(30), now, now.plus(Duration.ofDays(10)), now.minus(Duration.ofDays(91))))
    }

    @Test
    fun `a longer-lived token keeps its original expiry`() {
        val expiresAt = now.plus(Duration.ofDays(355))

        assertEquals(
            Duration.ofDays(355),
            properties.renewal(Duration.ofDays(365), now, expiresAt, now.minus(Duration.ofDays(10))),
        )
    }

    @Test
    fun `a longer-lived token past the cap is not renewed`() {
        assertNull(properties.renewal(Duration.ofDays(365), now, now.plus(Duration.ofDays(265)), now.minus(Duration.ofDays(100))))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AccessTokenPropertiesTest`
Expected: compilation fails — `maxLifetime` and `renewal` do not exist.

- [ ] **Step 3: Implement it**

Add the property and the rule, with a KDoc that states the two branches (cap first, then the long-lived token keeps its expiry):

```kotlin
    fun renewal(lifetime: Duration, now: Instant, expiresAt: Instant, sessionStartedAt: Instant): Duration? {
        val cap = sessionStartedAt + maxLifetime
        if (!now.isBefore(cap)) {
            return null
        }
        return if (lifetime > maxLifetime) {
            Duration.between(now, expiresAt)
        } else {
            minOf(lifetime, Duration.between(now, cap))
        }
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=AccessTokenPropertiesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenProperties.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenPropertiesTest.kt
git commit -m "feat(server): bound a session to 90 days and never shorten a renewal" -m "Refs #314"
```

### Task 3: `AuthenticationService.refresh`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AuthenticationService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AuthenticationServiceTest.kt` (new)

**Interfaces:**
- Consumes: `AccessTokenService.getAccessToken()` / `create(...)` (Task 1), `AccessTokenProperties.renewal(...)` (Task 2), `UserService.findByIdOrNull`, `MembershipService.findByUserOrNull`, `ApplicationService.findByCodeOrNull`, `UserApplicationService.findByMembershipAndApplicationOrNull`, `Clock`.
- Produces, for Task 4: `fun refresh(): String` (the new token), and the primary constructor
  `AuthenticationService(provider, tokenService, userService, membershipService, applicationService, userApplicationService, properties, clock)`.

- [ ] **Step 1: Write the failing tests**

Seven tests, with all collaborators mocked and a `@BeforeEach` that stubs the happy path. The instants are relative to `Instant.now()` (the service stamps `issuedAt = clock.instant()`):

```kotlin
class AuthenticationServiceTest {
    private val provider = mock<AuthenticatorProvider>()
    private val tokenService = mock<AccessTokenService>()
    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val applicationService = mock<ApplicationService>()
    private val userApplicationService = mock<UserApplicationService>()
    private val properties = AccessTokenProperties(ttl = mapOf(ApplicationCode.PARTNER_CENTRAL to Duration.ofDays(30)))
    private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    private val now = clock.instant()
    private val service = AuthenticationService(
        provider, tokenService, userService, membershipService, applicationService, userApplicationService, properties, clock,
    )

    private val user = UserEntity(id = 11, email = "ray.sponsible@gmail.com")
    private val membership = MembershipEntity(id = 11, tenantId = 1, user = user, party = PartyEntity(id = 100))
    private val application = ApplicationEntity(id = 3, code = ApplicationCode.PARTNER_CENTRAL)
    private val access = UserApplicationEntity(
        id = 11,
        membership = membership,
        application = application,
        roles = listOf(RoleEntity(id = 1, code = "admin")),
    )

    @BeforeEach
    fun setUp() {
        doReturn(user).whenever(userService).findByIdOrNull(11L)
        doReturn(membership).whenever(membershipService).findByUserOrNull(user)
        doReturn(application).whenever(applicationService).findByCodeOrNull(any())
        doReturn(access).whenever(userApplicationService).findByMembershipAndApplicationOrNull(membership, application)
        doReturn("new-token").whenever(tokenService).create(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `refresh gives the same claims, the current party and a new life`() {
        // issued 20 days ago, 30-day lifetime, session started 40 days ago: 30 more days, not the 10 left
        doReturn(token(now.minus(Duration.ofDays(20)), now.minus(Duration.ofDays(40)))).whenever(tokenService).getAccessToken()

        assertEquals("new-token", service.refresh())

        verify(tokenService).create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 11L,
            roles = arrayOf("admin"),
            tenantId = 1L,
            ttlSeconds = Duration.ofDays(30).seconds.toInt(),
            partyId = 100L,
            issuedAt = now,
            sessionStartedAt = now.minus(Duration.ofDays(40)),
        )
    }

    @Test
    fun `refresh near the cap gives only what the cap leaves`() {
        // issued 1 day ago, 30-day lifetime, session started 85 days ago: 5 days to the cap, not 30
        doReturn(token(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(85)))).whenever(tokenService).getAccessToken()

        service.refresh()

        val captor = argumentCaptor<Int>()
        verify(tokenService).create(any(), any(), any(), any(), captor.capture(), any(), any(), any())
        assertEquals(Duration.ofDays(5).seconds.toInt(), captor.firstValue)
    }

    @Test
    fun `refresh of a match link keeps its original expiry`() {
        // a 365-day public-portal token, 10 days old, session started with it
        doReturn(
            token(
                issuedAt = now.minus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(10)),
                lifetime = Duration.ofDays(365),
                application = ApplicationCode.PUBLIC_PORTAL,
            ),
        ).whenever(tokenService).getAccessToken()

        service.refresh()

        val captor = argumentCaptor<Int>()
        verify(tokenService).create(any(), any(), any(), any(), captor.capture(), any(), any(), any())
        assertEquals(Duration.ofDays(355).seconds.toInt(), captor.firstValue) // now + 355 days == its original exp
    }

    @Test
    fun `refresh past the cap`() {
        doReturn(token(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(90))))
            .whenever(tokenService).getAccessToken()

        val ex = assertFailsWith<ConflictException> { service.refresh() }

        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, ex.error.code)
        verify(tokenService, never()).create(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `refresh for a user who is no longer a member`() {
        doReturn(null).whenever(userService).findByIdOrNull(11L)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }

    @Test
    fun `refresh for a membership that is gone`() {
        doReturn(null).whenever(membershipService).findByUserOrNull(user)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }

    @Test
    fun `refresh when the application access was removed`() {
        doReturn(null).whenever(userApplicationService).findByMembershipAndApplicationOrNull(membership, application)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }
```

`token(issuedAt, sessionStartedAt, lifetime = Duration.ofDays(30), application = ApplicationCode.PARTNER_CENTRAL)` builds
`AccessToken(JwtPrincipal(JWT.decode(...)), issuedAt, issuedAt.plus(lifetime), sessionStartedAt)` from a java-jwt token carrying
`userId: 11`, `tenantId: 1` and that application.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AuthenticationServiceTest`
Expected: compilation fails — no `refresh()`.

- [ ] **Step 3: Implement it**

Give `AuthenticationService` the six new constructor parameters and add:

```kotlin
    /**
     * Re-issues the caller's current token for the same application, user, tenant and party, with a fresh life bounded
     * by `max-lifetime`: 409 `AUTH_ACCESS_DENIED` when the user, its membership or the application access is gone,
     * 409 `AUTH_CREDENTIALS_EXPIRED` past the cap.
     */
    fun refresh(): String {
        val token = tokenService.getAccessToken()
        val principal = token.principal
        val user = userService.findByIdOrNull(principal.getUserId())
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val membership = membershipService.findByUserOrNull(user)
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val application = applicationService.findByCodeOrNull(principal.getApplication())
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val userApplication = userApplicationService.findByMembershipAndApplicationOrNull(membership, application)
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))

        val now = clock.instant()
        val ttl = properties.renewal(
            lifetime = Duration.between(token.issuedAt, token.expiresAt),
            now = now,
            expiresAt = token.expiresAt,
            sessionStartedAt = token.sessionStartedAt,
        ) ?: throw ConflictException(Error(code = ErrorCode.AUTH_CREDENTIALS_EXPIRED))
        if (ttl <= Duration.ZERO) {
            throw ConflictException(Error(code = ErrorCode.AUTH_CREDENTIALS_EXPIRED))
        }

        return tokenService.create(
            application = principal.getApplication(),
            userId = user.id ?: -1,
            roles = userApplication.roles.map { role -> role.code }.toTypedArray(),
            tenantId = membership.tenantId,
            ttlSeconds = ttl.seconds.toInt(),
            partyId = membership.party?.id,
            issuedAt = now,
            sessionStartedAt = token.sessionStartedAt,
        )
    }
```

`authenticate` is unchanged. No transaction: nothing is written.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AuthenticationServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AuthenticationService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AuthenticationServiceTest.kt
git commit -m "feat(server): refresh an access token within its session's cap" -m "Refs #314"
```

### Task 4: `POST /v1/auth/refresh`, configured and documented

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/endpoint/AuthenticationEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (the TTL block at `ndopify.security.access-token`, the `security` springdoc group)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/AuthorizationAwareEndpointIntegrationTest.kt` (`createAccessToken` becomes `open`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/AuthenticateOtpEndpointTest.kt` (`Duration.ofDays(7)` → `Duration.ofDays(30)`, *partner-central's ttl*)
- Modify: `modules/ndopify-server/CLAUDE.md` (*Authentication*, *Token signing*)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/RefreshAuthenticationEndpointTest.kt` (new)
- Test data: `modules/ndopify-server/src/test/resources/db/test/security/RefreshAuthenticationEndpoint.sql` (new)

**Interfaces:**
- Consumes: `AuthenticationService.refresh()` (Task 3).
- Produces: `POST /v1/auth/refresh` → `AuthenticateResponse`; the yml values.

- [ ] **Step 1: Write the test data**

`RefreshAuthenticationEndpoint.sql` — tenant 1 (`localhost`), a party and a `partner-central` member with that access:

```sql
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_USER (id, email, deleted) VALUES (11, 'ray.sponsible@gmail.com', false);

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES (11, 1, 11, 100, false);

-- application 3 is partner-central (V1_3)
INSERT INTO T_USER_APPLICATION (id, membership_id, application_id) VALUES (11, 11, 3);
```

- [ ] **Step 2: Write the failing endpoint tests**

Make `AuthorizationAwareEndpointIntegrationTest.createAccessToken` `open` (one keyword), so the cap test can mint an older token:

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/security/RefreshAuthenticationEndpoint.sql"])
class RefreshAuthenticationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application: String = ApplicationCode.PARTNER_CENTRAL

    @Autowired
    private lateinit var tokenService: AccessTokenService

    private var issuedAt: Instant = Instant.now()
    private var sessionStartedAt: Instant = issuedAt
    private var ttlSeconds: Int = 3600

    override fun createAccessToken(application: String): String = tokenService.create(
        application = application,
        userId = USER_ID,
        roles = roles,
        tenantId = TENANT_ID,
        ttlSeconds = ttlSeconds,
        issuedAt = issuedAt,
        sessionStartedAt = sessionStartedAt,
    )

    @Test
    fun refresh() {
        val startedAt = Instant.now()

        val response = rest.postForEntity("/v1/auth/refresh", null, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val token = tokenService.decode(response.body!!.accessToken)
        assertEquals(11L, token.getUserId())
        assertEquals(1L, token.getTenantId())
        assertEquals(100L, token.getPartyId()) // re-read from the membership; the request's token carries none
        assertEquals(ApplicationCode.PARTNER_CENTRAL, token.getApplication())

        val jwt = JWT.decode(response.body!!.accessToken)
        assertEquals(ttlSeconds.toLong(), Duration.between(jwt.issuedAtAsInstant, jwt.expiresAtAsInstant).seconds)
        assertFalse(jwt.expiresAtAsInstant.isBefore(startedAt.plusSeconds(ttlSeconds - 60L)))
        assertEquals(sessionStartedAt.toEpochMilli(), jwt.getClaim(AccessTokenService.CLAIM_SESSION_STARTED_AT).asLong())
    }

    @Test
    fun `past the absolutely cap`() {
        issuedAt = Instant.now().minus(Duration.ofDays(10))
        sessionStartedAt = Instant.now().minus(Duration.ofDays(91))
        ttlSeconds = Duration.ofDays(30).seconds.toInt()

        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, response.body?.error?.code)
    }

    @Test
    @Sql(statements = ["UPDATE T_MEMBERSHIP SET deleted = true WHERE id = 11"])
    fun `membership removed`() { assertDenied() }

    @Test
    @Sql(statements = ["DELETE FROM T_USER_APPLICATION WHERE membership_id = 11"])
    fun `application access removed`() { assertDenied() }

    @Test
    fun `no token`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    private fun assertDenied() {
        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }
}
```

The exact `exp` comparisons may differ by a whole second (java-jwt writes second precision): compare `issuedAt`/`expiresAt` as instants and compare lifetimes with `assertEquals` on the two `Duration`s — both are read from tokens the same way, so they match.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=RefreshAuthenticationEndpointTest`
Expected: 404 from the endpoint (no mapping yet), so the assertions fail.

- [ ] **Step 4: Implement the endpoint and the configuration**

`AuthenticationEndpoints`:

```kotlin
    @PostMapping("/refresh")
    fun refresh(): AuthenticateResponse = AuthenticateResponse(service.refresh())
```

`application.yml`, under `ndopify.security`:

```yaml
        access-token:
            ttl: # Per ApplicationCode: also how long the web apps keep their cookie
                public-portal: 5d
                admin-console: 1h
                partner-central: 30d   # The idle window: an agent who opens Partner Central once a month stays signed in
                e-signature: 1h
                match-link: 365d       # The token of the customer links of the proposal and visit e-mails (MatchLinkTokenService)
            max-lifetime: 90d          # Absolute cap on a session, from its first sign-in
            default-ttl: 1h            # An application not listed above
```

and add `/v1/auth/**` to the `security` springdoc group's `paths-to-match` (below the existing `/v1/auth`), so the endpoint appears in the Security API too.

Then update `AuthenticateOtpEndpointTest.login`: the `partner-central` lifetime it asserts is now `Duration.ofDays(30)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='RefreshAuthenticationEndpointTest,AuthenticateOtpEndpointTest'`
Expected: PASS (MySQL running).

- [ ] **Step 6: Update `modules/ndopify-server/CLAUDE.md`**

In *Authentication*, after the sentence listing what needs a token, note that `POST /v1/auth/refresh` is authenticated (never in `PublicEndpoints`) and re-issues the caller's token. In *Token signing*, after the TTL sentence: the token also carries the session's start, a renewal is bounded by `max-lifetime` (90d) and never shortens a longer-lived token, and Partner Central's TTL is 30d because its agents renew it daily (see `ndopify-web-core`).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/endpoint/AuthenticationEndpoints.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/AuthorizationAwareEndpointIntegrationTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/AuthenticateOtpEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/RefreshAuthenticationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/security/RefreshAuthenticationEndpoint.sql \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): renew the caller's access token" -m "Refs #314"
```

### Task 5: `NdTokenClient`

**Files:**
- Create: `modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/client/ndopify/NdTokenClient.kt`
- Test: `modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/client/ndopify/NdTokenClientTest.kt`

**Interfaces:**
- Produces, for Task 6: `@Service class NdTokenClient internal constructor(baseUrl: String, rest: RestTemplate)` with `fun refresh(accessToken: String): String` — the new token, `""` when the answer has none.

- [ ] **Step 1: Write the failing test** (mirror `NdTenantClientTest`)

```kotlin
class NdTokenClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdTokenClient

    @BeforeEach
    fun setUp() {
        val rest = RestTemplate()
        server = MockRestServiceServer.bindTo(rest).build()
        client = NdTokenClient("http://localhost:8080", rest)
    }

    @Test
    fun refresh() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old-token"))
            .andExpect(headerDoesNotExist("X-Tenant-ID"))
            .andRespond(withSuccess("""{"accessToken":"new-token"}""", MediaType.APPLICATION_JSON))

        assertEquals("new-token", client.refresh("old-token"))
        server.verify()
    }

    @Test
    fun `a refused renewal`() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.refresh("old-token") }
    }

    @Test
    fun `an answer without a token`() {
        server.expect(requestTo("http://localhost:8080/v1/auth/refresh"))
            .andRespond(withSuccess("""{}""", MediaType.APPLICATION_JSON))

        assertEquals("", client.refresh("old-token"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=NdTokenClientTest`
Expected: compilation fails — no `NdTokenClient`.

- [ ] **Step 3: Implement it**

Same shape as `NdTenantClient`: a plain `RestTemplate` (no interceptors, no `X-Tenant-ID`; the token is passed explicitly), `UriComponentsBuilder.fromUriString(baseUrl).path("/v1/auth/refresh").build().toUri()`, `HttpHeaders().apply { setBearerAuth(accessToken) }`, `rest.postForEntity(uri, HttpEntity<Void>(headers), AuthenticateResponse::class.java).body?.accessToken ?: ""`. KDoc: the endpoint is authenticated by the token it renews and takes no body.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=NdTokenClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/client/ndopify/NdTokenClient.kt \
        modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/client/ndopify/NdTokenClientTest.kt
git commit -m "feat(web-core): call the access-token refresh endpoint" -m "Refs #314"
```

### Task 6: `AccessTokenRenewalFilter`

**Files:**
- Create: `modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/security/AccessTokenRenewalFilter.kt`
- Test: `modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/security/AccessTokenRenewalFilterTest.kt`
- Modify: `modules/ndopify-web-core/CLAUDE.md` (code organization + the cookie bullet)

**Interfaces:**
- Consumes: `AccessTokenService` (the cookie), `JwtDecoder` (expiry + application), `NdTokenClient` (Task 5), `Clock`.
- Produces, for Tasks 7–8: `class AccessTokenRenewalFilter(accessTokenHolder: AccessTokenService, jwtDecoder: JwtDecoder, tokenClient: NdTokenClient, clock: Clock, renewalInterval: Duration = Duration.ofDays(1)) : OncePerRequestFilter()`.

- [ ] **Step 1: Write the failing tests**

One fixture with a fixed `NOW`, a real `AccessTokenService` over `MockHttpServletRequest`/`Response`, a real `JwtDecoder("partner-central")`, a mocked `NdTokenClient`, and a token helper shaped like `JwtAuthenticationFilterTest`'s (claims + `iat` + `exp`):

```kotlin
class AccessTokenRenewalFilterTest {
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val chain = MockFilterChain()
    private val now = Instant.parse("2026-11-24T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val tokenClient = mock<NdTokenClient>()
    private val service = AccessTokenService(request, response, "__npc_access_token", clock = clock)
    private val filter = AccessTokenRenewalFilter(service, JwtDecoder("partner-central"), tokenClient, clock)

    @Test
    fun `renews a token older than the interval`() {
        val token = token(issuedAt = now.minus(Duration.ofDays(2)), expiresAt = now.plus(Duration.ofDays(28)))
        doReturn("new-token").whenever(tokenClient).refresh(token)
        request.setCookies(Cookie("__npc_access_token", token))

        filter.doFilter(request, response, chain)

        verify(tokenClient).refresh(token)
        assertTrue(setCookie().startsWith("__npc_access_token=new-token;"), setCookie())
        assertNotNull(chain.request) // the page renders
    }

    @Test
    fun `leaves a younger token untouched`() {
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(30)))))

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `renews a token that expires within the interval`() {
        doReturn("new-token").whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(12)))))

        filter.doFilter(request, response, chain)

        verify(tokenClient).refresh(any())
        assertTrue(setCookie().startsWith("__npc_access_token=new-token;"), setCookie())
    }

    @Test
    fun `keeps the cookie when the renewal fails`() {
        doThrow(RestClientException("boom")).whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

        filter.doFilter(request, response, chain)

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
        assertNotNull(chain.request)
    }

    @Test
    fun `never renews a token it cannot decode`() {
        request.setCookies(Cookie("__npc_access_token", "not-a-token"))

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNotNull(chain.request)
    }

    @Test
    fun `never renews another application's token`() {
        request.setCookies(
            Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)), application = "public-portal")),
        )

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
    }

    @Test
    fun `skips static assets and the error page`() {
        doReturn("new-token").whenever(tokenClient).refresh(any())
        for (path in listOf("/assets/dist/app.min.js", "/sw.js", "/offline.html", "/manifest.json", "/favicon.ico", "/error")) {
            request.servletPath = path
            request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

            filter.doFilter(request, response, MockFilterChain())
        }

        verify(tokenClient, never()).refresh(any())
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `does nothing without a cookie`() {
        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNotNull(chain.request)
    }

    @Test
    fun `never sets an empty cookie`() {
        doReturn("").whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

        filter.doFilter(request, response, chain)

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    // The same claims as ndopify-server's AccessTokenService, signed with any secret (we never check it).
    private fun token(issuedAt: Instant, expiresAt: Instant, application: String = "partner-central"): String =
        JWT.create()
            .withIssuer(JwtDecoder.ISSUER)
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 1L)
            .withClaim(JwtPrincipal.CLAIM_APPLICATION, application)
            .withArrayClaim(JwtPrincipal.CLAIM_ROLE, emptyArray<String>())
            .sign(Algorithm.HMAC256("any-secret"))

    private fun setCookie(): String = response.getHeader(HttpHeaders.SET_COOKIE) ?: ""
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=AccessTokenRenewalFilterTest`
Expected: compilation fails — no `AccessTokenRenewalFilter`.

- [ ] **Step 3: Implement it**

```kotlin
class AccessTokenRenewalFilter(
    private val accessTokenHolder: AccessTokenService,
    private val jwtDecoder: JwtDecoder,
    private val tokenClient: NdTokenClient,
    private val clock: Clock,
    private val renewalInterval: Duration = Duration.ofDays(1),
) : OncePerRequestFilter() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AccessTokenRenewalFilter::class.java)
        private val SKIPPED_PATHS = setOf("/sw.js", "/offline.html", "/manifest.json", "/favicon.ico", "/error")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.servletPath.startsWith("/assets/") || request.servletPath in SKIPPED_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            renew()
        } catch (ex: Exception) {
            LOGGER.debug("Access token not renewed", ex)
        }
        chain.doFilter(request, response)
    }

    private fun renew() {
        val accessToken = accessTokenHolder.get() ?: return
        jwtDecoder.decode(accessToken) // expired, foreign or malformed: left to JwtAuthenticationFilter
        val jwt = JWT.decode(accessToken)
        val issuedAt = jwt.issuedAtAsInstant ?: return
        val expiresAt = jwt.expiresAtAsInstant ?: return
        val now = clock.instant()
        val aged = Duration.between(issuedAt, now) >= renewalInterval
        val expiring = Duration.between(now, expiresAt) < renewalInterval
        if (!aged && !expiring) {
            return
        }
        val newToken = tokenClient.refresh(accessToken)
        if (newToken.isNotBlank()) {
            accessTokenHolder.set(newToken)
        }
    }
}
```

The KDoc states the two rules the tests pin: renewing on **age**, not only near expiry, is what makes the idle window the token's full life (a visit on day 20 of 30 must extend it); the current request keeps the old token (valid for at least the interval) and later requests use the new cookie; every failure is invisible.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-web-core && mvn test -Dtest=AccessTokenRenewalFilterTest`
Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-web-core/CLAUDE.md`**

Add the two new classes to *Code Organization* (`client/ndopify/NdTokenClient` — `POST /v1/auth/refresh` with the cookie's token, on the plain `RestTemplate`; `service/security/AccessTokenRenewalFilter` — the apps that opt in declare it before `JwtAuthenticationFilter`, it renews the cookie's token once a day and ignores failures), and extend the `AccessTokenService` bullet: the cookie still lives until the token's `exp`, which a renewal moves forward within the session's 90-day cap.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/security/AccessTokenRenewalFilter.kt \
        modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/security/AccessTokenRenewalFilterTest.kt \
        modules/ndopify-web-core/CLAUDE.md
git commit -m "feat(web-core): renew the access-token cookie once a day" -m "Refs #314"
```

**End of pull request 1.** Open `feat/314-server-web-core-access-token-renewal`, let `<module>-pr.yml` run for both modules, merge, and wait for `ndopify-web-core-0.0.11` (tag) before starting Task 7.

### Task 7: Partner Central registers the filter

**Files:**
- Modify: `pom.xml` (`ndopify-web-core.version` `0.0.10` → `0.0.11`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/WebCoreConfiguration.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/SecurityConfigurationTest.kt` (new)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (sign-in bullet, testing patterns)

**Interfaces:**
- Consumes: `NdTokenClient` (Task 5), `AccessTokenRenewalFilter` (Task 6).
- Produces: a `SecurityFilterChain` whose `AccessTokenRenewalFilter` runs immediately before `JwtAuthenticationFilter`.

- [ ] **Step 1: Write the failing wiring test**

The apps' only context test; `@SpringBootTest` in its default `MOCK` mode starts a servlet context without a port, no database and no ndopify-server call:

```kotlin
@SpringBootTest
class SecurityConfigurationTest {
    @Autowired
    private lateinit var chain: SecurityFilterChain

    @Test
    fun `renews the access token before authenticating the request`() {
        val filters = (chain as DefaultSecurityFilterChain).filters
        val renewal = filters.indexOfFirst { it is AccessTokenRenewalFilter }
        val jwt = filters.indexOfFirst { it is JwtAuthenticationFilter }

        assertTrue(renewal >= 0, "the renewal filter is in the chain")
        assertTrue(jwt > renewal, "the renewal runs before JwtAuthenticationFilter")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SecurityConfigurationTest`
Expected: FAIL — `renewal` is `-1`.

- [ ] **Step 3: Wire it**

- Root `pom.xml`: `<ndopify-web-core.version>0.0.11</ndopify-web-core.version>`.
- `WebCoreConfiguration`: `@Import(NdTenantClient::class, NdTokenClient::class)`.
- `SecurityConfiguration`: take `NdTokenClient` and `Clock` in the constructor, and build the filter inside the chain — a private factory, **not** a `@Bean`: Spring Boot also registers every `Filter` bean as a plain servlet filter, which would renew twice per request.

```kotlin
    private fun accessTokenRenewalFilter(): AccessTokenRenewalFilter =
        AccessTokenRenewalFilter(accessTokenService, jwtDecoder, tokenClient, clock)
```

and, in `filterChain`, register the JWT filter first and the renewal one after it (Spring Security needs the anchor already registered):

```kotlin
            .addFilterBefore(authorizationFilter(), AnonymousAuthenticationFilter::class.java)
            .addFilterBefore(accessTokenRenewalFilter(), JwtAuthenticationFilter::class.java)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SecurityConfigurationTest`
Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the sign-in bullet (the `__npc_access_token` sentence): the token is renewed by `AccessTokenRenewalFilter` on the first page view of each day, so an agent who opens the app at least once in 30 days never signs in again (90 days from the first sign-in at most), and a failed renewal only leaves them with the current token. In *Testing Patterns*, note the one `@SpringBootTest` (`config/SecurityConfigurationTest`) that checks the filter order, and that everything else stays plain unit tests.

- [ ] **Step 6: Commit**

```bash
git add pom.xml \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/WebCoreConfiguration.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/SecurityConfigurationTest.kt \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): renew the agents' access token" -m "Refs #314"
```

### Task 8: ndopify-web registers the same filter

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/WebCoreConfiguration.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/config/SecurityConfigurationTest.kt` (new)
- Modify: `modules/ndopify-web/CLAUDE.md` (the `__nweb_access_token` sentence, testing patterns)

**Interfaces:**
- Consumes: the same two web-core classes; the root bump from Task 7.
- Produces: the same filter order in the public site's chain.

- [ ] **Step 1: Write the failing wiring test**

As Task 7's, in package `com.wutsi.ndopify.web.config`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=SecurityConfigurationTest`
Expected: FAIL — `renewal` is `-1`.

- [ ] **Step 3: Wire it**

Exactly as Task 7 (`@Import(NdTenantClient::class, NdTokenClient::class)`, the private `accessTokenRenewalFilter()` factory — not a `@Bean` — and the two `addFilterBefore` calls in that order).

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=SecurityConfigurationTest`
Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-web/CLAUDE.md`**

In the `__nweb_access_token` sentence: the cookie's token also goes through `AccessTokenRenewalFilter`; a match link's renewal keeps its original 365-day `exp`, so nothing changes for the visitor.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/WebCoreConfiguration.kt \
        modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/config/SecurityConfigurationTest.kt \
        modules/ndopify-web/CLAUDE.md
git commit -m "feat(web): renew the match-link token through the same filter" -m "Refs #314"
```

**End of pull request 2** — its body says `Closes #314`.

### Task 9: Verify the four modules

**Files:** none.

- [ ] **Step 1: Run every touched module's build**

```bash
cd modules/ndopify-server && mvn -s ../../settings.xml verify      # MySQL must be up
cd modules/ndopify-web-core && mvn -s ../../settings.xml verify
cd modules/ndopify-partner-central && mvn -s ../../settings.xml verify
cd modules/ndopify-web && mvn -s ../../settings.xml verify
```

Expected: BUILD SUCCESS for each — ktlint, tests and the JaCoCo gate.

- [ ] **Step 2: Check the release landed**

```bash
git fetch --tags && git tag --list 'ndopify-web-core-0.0.11'
grep -n 'ndopify-web-core.version' pom.xml   # 0.0.11
```

Expected: the tag exists and the root POM points at it.
