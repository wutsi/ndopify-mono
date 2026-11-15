# OTP Account Lockout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock a member's account for an hour (configurable in the yml) after 5 wrong one-time codes, and tell the two
OTP screens how many attempts are left and how long the lock lasts.

**Architecture:** The user's `OTP` row in `T_AUTH_FACTOR` gains `failed_attempts` and `locked_until`.
`OtpAuthenticator` refuses any code while locked and counts a wrong one; `OtpService` refuses to issue a code while
locked; `AuthenticationService` stops rolling the count back with the 409. A new `AUTH_ACCOUNT_LOCKED` error code
carries `error.data.lockedUntil`, and `AUTH_INVALID_CREDENTIALS` carries `error.data.remainingAttempts`, which
Partner Central's login and the e-signature sign page render in French.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, JUnit 5 / `kotlin.test`, `mockito-kotlin`, JaCoCo, ktlint, MySQL
endpoint tests, Flyway, Maven.

**Spec:** [story #126](https://github.com/wutsi/ndopify-mono/issues/126) (the design record is its description).

## Global Constraints

- UI text is French; code, comments, logs, docs and server error messages are English.
- Assertions use `kotlin.test`; mocks use `mockito-kotlin`.
- Config: `ndopify.security.otp.max-failed-attempts` (5) and `ndopify.security.otp.lock-duration` (1h). No
  threshold, duration or copy may hard-code 5 or an hour: the pages take them from the error's `data`, the server
  from `OtpProperties`.
- The count is per user and survives a new code; a successful login clears it; an expired lock starts the count over.
- OTP only: `PasswordAuthenticator`'s behaviour must not change.
- The lock is per user, not per tenant (`T_AUTH_FACTOR` has no `tenant_id`).
- Schema changes go in a new Flyway script (`V11_0__auth-factor-lockout.sql`), never in an older one.
- One library module per commit: `feat(dto): …` with `Refs #126` in the body. Never commit the root POM's local
  `<ndopify-dto.version>0.0.90-SNAPSHOT` pin.
- No `ndopify-web-core` change: each web app owns its French copy, as `toMessage` / `toCodeMessage` already do.
- Every page works without JavaScript; `src/main/html/` is the prototype, never served and never deleted.
- `/v1/auth` and `/v1/otp` answer the lock with 409, like every other authentication error.

## Review Focus

- **A right code while locked:** no token, `last_logged_in_at` and `expires_at` unchanged (the code isn't consumed
  and stays usable if the lock is lifted early).
- **The 5th wrong code's count outliving its 409:** `AuthenticationService.authenticate` is transactional, so
  without `noRollbackFor` the increment disappears and every attempt starts at 1.
- **A lock that expired:** the next wrong code reports 4 attempts left, never locks again immediately (the stale
  `failed_attempts = 5` must not be incremented).
- **`POST /v1/otp` while locked:** no e-mail, and the row's `data` / `expires_at` untouched (a code silently
  replaced would still be dead for an hour).
- **Password logins:** 5 wrong passwords still answer `AUTH_INVALID_CREDENTIALS` forever, and no OTP row is
  written for a user who has none.

---

### Task 1: Prototype the lock on both OTP screens

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js` (constants at 9–23,
  `initLoginCode` at 269–381)
- Modify: `modules/ndopify-esignature/src/main/html/assets/js/esignature.js` (constants at 15–30, `bindCode` at
  256–295)

**Interfaces:**
- Produces: the approved copy and states the served templates mirror in Tasks 6–7: the countdown warning after a
  wrong code, and the locked state with the wait.

- [x] **Step 1: Model the account lock in Partner Central's prototype**

`OTP_LOCK_MS = 60 * 60 * 1000` and a `KEYS.otpLockedUntil = "npc.otpLockedUntil"` next to `otpAttempts`.
`initLoginCode` changes:

- `isLocked()` is `Date.now() < Number(store.get(KEYS.otpLockedUntil) || 0)`; `sync()` requires `!isExpired() &&
  !isLocked()`.
- A submit while locked shows the lock message (below) and stops.
- A wrong code (`WRONG_CODE`) still stores `attempts + 1`, but the last one stores
  `otpLockedUntil = Date.now() + OTP_LOCK_MS` and shows the lock message; the earlier ones show
  « Code incorrect. Encore N tentatives avant le blocage de votre compte. » (N = `OTP_MAX_ATTEMPTS - used`,
  `tentative` when N is 1).
- The lock message is « Trop de tentatives. Réessayez dans N minutes. » (N = minutes left, rounded up, at least 1,
  `minute` when N is 1).
- A successful code removes `otpAttempts`, `otpLockedUntil` and `otpExpiry`.
- Resend keeps `otpAttempts` and `otpLockedUntil` (a new code buys no attempts). While locked it sends nothing and
  says the lock message in `[data-status]`; otherwise it keeps today's behaviour.

- [x] **Step 2: Model it in the e-signature prototype**

Add `failedAttempts` and `lockedUntil` to the persisted state. A new code (`update({ codeExpiresAt: … })`) does
**not** reset them — a resend buys no attempts, as on ndopify-server; only a verified code clears both, and a lock
that expired starts the count over. `bindCode` counts a wrong code (`WRONG_CODE`) the same way and shows the same two
messages from `MESSAGES` (as functions of the number). `[data-resend]` while locked shows the lock message and
sends nothing, as does the intro's `/otp`.

- [x] **Step 3: Check both in a browser**

Run: `cd modules/ndopify-partner-central/src/main/html && python3 -m http.server 8000`, open
`http://localhost:8000/login.html`, request a code, type `000000` five times: the 1st–4th warn, the 5th locks and
the button is disabled. Same for `modules/ndopify-esignature/src/main/html` (`sign.html`, code `111111`).

- [x] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the OTP account lock"
git add modules/ndopify-esignature/src/main/html
git commit -m "feat(esignature): prototype the OTP account lock"
```

**Stop here.** Show the user both prototype states and wait for their OK on the prototype and the plan before Task
2. If the feedback changes the design, update this plan, commit and push it to `main`, then rebase.

---

### Task 2: Add `AUTH_ACCOUNT_LOCKED` to ndopify-dto

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt:16`
- Modify locally (never committed): `pom.xml:52`

**Interfaces:**
- Produces: `ErrorCode.AUTH_ACCOUNT_LOCKED = "urn:wutsi:ndopify:error:auth:account-locked"`.

- [x] **Step 1: Add the code**

`val AUTH_ACCOUNT_LOCKED = "$PREFIX:auth:account-locked"`, above `AUTH_ACCESS_DENIED` (alphabetical inside the
`AUTH_` block).

- [x] **Step 2: Install it locally and pin the server to the snapshot**

```bash
cd modules/ndopify-dto && mvn install -DskipTests
cd ../.. && sed -i '' 's#<ndopify-dto.version>0.0.89</ndopify-dto.version>#<ndopify-dto.version>0.0.90-SNAPSHOT</ndopify-dto.version>#' pom.xml
git diff -- pom.xml
```

Expected: only `<ndopify-dto.version>`, `0.0.89` → `0.0.90-SNAPSHOT` (the DTO POM is already `0.0.90-SNAPSHOT`).

- [x] **Step 3: Commit only the DTO file**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add AUTH_ACCOUNT_LOCKED" -m "Refs #126"
```

`git status --short` keeps `pom.xml` modified (the local pin), nothing else.

---

### Task 3: Store the attempts, and the policy that locks

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V11_0__auth-factor-lockout.sql`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/OtpProperties.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/service/AuthFactorServiceTest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/domain/AuthFactorEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AuthFactorService.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml:246-256`

**Interfaces:**
- Produces:
  - `AuthFactorEntity.failedAttempts: Int = 0`, `AuthFactorEntity.lockedUntil: Date? = null`,
    `AuthFactorEntity.isLocked(clock: Clock): Boolean`.
  - `AuthFactorService.recordFailedAttempt(authFactor: AuthFactorEntity, maxAttempts: Int, lockDuration: Duration): AuthFactorEntity` — counts one failure and locks when it reaches `maxAttempts`.
  - `OtpProperties(maxFailedAttempts: Int = 5, lockDuration: Duration = Duration.ofHours(1))` on
    `ndopify.security.otp`, enabled with `@EnableConfigurationProperties(OtpProperties::class)` on
    `OtpAuthenticator` in Task 4.

- [x] **Step 1: Write the migration**

```sql
ALTER TABLE T_AUTH_FACTOR
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME DEFAULT NULL;
```

- [x] **Step 2: Write the failing unit test**

`AuthFactorServiceTest` with a mocked `AuthFactorRepository` and `Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"),
UTC)`. In `setUp`: `whenever(dao.save(any())).thenAnswer { it.arguments[0] }`.
`FACTOR = AuthFactorEntity(id = 1, authType = AuthType.OTP, data = "hash", salt = "salt")`.

```kotlin
@Test fun `first failure counts one`() {
    val saved = service.recordFailedAttempt(FACTOR, 5, Duration.ofHours(1))
    assertEquals(1, saved.failedAttempts)
    assertNull(saved.lockedUntil)
}

@Test fun `fifth failure locks for the duration`() {
    val saved = service.recordFailedAttempt(FACTOR.copy(failedAttempts = 4), 5, Duration.ofHours(1))
    assertEquals(5, saved.failedAttempts)
    assertEquals(now.toEpochMilli(), saved.lockedUntil!!.time - 3_600_000)
}

@Test fun `an expired lock starts the count over`() {
    val expired = FACTOR.copy(failedAttempts = 5, lockedUntil = Date(now.toEpochMilli() - 1000))
    val saved = service.recordFailedAttempt(expired, 5, Duration.ofHours(1))
    assertEquals(1, saved.failedAttempts)
    assertNull(saved.lockedUntil)
}

@Test fun `consume clears the count and the lock`() {
    val saved = service.consume(FACTOR.copy(failedAttempts = 3, lockedUntil = Date(now.toEpochMilli() + 60_000)))
    assertEquals(0, saved.failedAttempts)
    assertNull(saved.lockedUntil)
}
```

- [x] **Step 3: Run it to watch it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AuthFactorServiceTest`
Expected: FAIL — `recordFailedAttempt` unresolved.

- [x] **Step 4: Add the entity fields and `isLocked`**

`AuthFactorEntity`: `failedAttempts: Int = 0` and `lockedUntil: Date? = null` after `expiresAt`, plus

```kotlin
/** Whether a wrong-code streak locked this account: true until [lockedUntil] passes. */
fun isLocked(clock: Clock): Boolean = lockedUntil != null && lockedUntil.time > clock.millis()
```

- [x] **Step 5: Add the counting rule and reset it on `consume`**

```kotlin
/** Counts one wrong code, locks the account when [maxAttempts] is reached. A lock that expired starts over. */
fun recordFailedAttempt(
    authFactor: AuthFactorEntity,
    maxAttempts: Int,
    lockDuration: Duration,
): AuthFactorEntity {
    val millis = clock.millis()
    val attempts = if (authFactor.lockedUntil != null) 1 else authFactor.failedAttempts + 1
    return dao.save(
        authFactor.copy(
            failedAttempts = attempts,
            lockedUntil = if (attempts >= maxAttempts) Date(millis + lockDuration.toMillis()) else null,
            modifiedAt = Date(millis),
        )
    )
}
```

`consume` also sets `failedAttempts = 0, lockedUntil = null`.

- [x] **Step 6: Add `OtpProperties` and the yml keys**

```kotlin
/**
 * `ndopify.security.otp`: how many wrong codes lock an account, and for how long. Policy, not an invariant: an
 * environment may tune them without a rebuild.
 */
@ConfigurationProperties("ndopify.security.otp")
data class OtpProperties(
    /** Wrong codes after which the account is locked. */
    val maxFailedAttempts: Int = 5,
    /** How long the account stays locked. */
    val lockDuration: Duration = Duration.ofHours(1),
)
```

In `application.yml`, under `ndopify.security`, next to `access-token`:

```yaml
        otp:
            max-failed-attempts: 5        # Wrong codes after which the account is locked
            lock-duration: 1h             # How long the account stays locked
```

- [x] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AuthFactorServiceTest`
Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add modules/ndopify-server/src
git commit -m "feat(security): store OTP failed attempts and lock the account"
```

---

### Task 4: Refuse and count OTP logins

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/authenticator/AbstractPasswordAuthenticator.kt:57-75`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/authenticator/OtpAuthenticator.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AuthenticationService.kt:15-22`
- Modify: `modules/ndopify-server/src/test/resources/db/test/security/AuthenticateOtpEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/AuthenticateOtpEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/AuthenticatePasswordEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/AuthenticateOtpLockConfigEndpointTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.AUTH_ACCOUNT_LOCKED` (Task 2), `OtpProperties`, `AuthFactorService.recordFailedAttempt`,
  `AuthFactorEntity.isLocked` (Task 3).
- Produces: `AbstractPasswordAuthenticator.invalidCredentials(authFactor: AuthFactorEntity?): Nothing` (open, the
  default throws `AUTH_INVALID_CREDENTIALS`), overridden by `OtpAuthenticator` to count and to lock.

- [x] **Step 1: Add two fixtures to `AuthenticateOtpEndpoint.sql`**

- User 9 `locked-otp@gmail.com`, tenant 1 membership 9, `T_USER_APPLICATION` 91 (application 3), OTP factor 9 with
  `failed_attempts = 5`, `locked_until = '2099-01-01 00:00:00'`.
- User 10 `expired-lock-otp@gmail.com`, membership 10, `T_USER_APPLICATION` 101, OTP factor 10 with
  `failed_attempts = 5`, `locked_until = '2020-01-01 00:00:00'`.

Both codes are `123456-test-salt` / `test-salt` with `expires_at = '2099-01-01 00:00:00'`, like factor 1.

- [x] **Step 2: Write the failing endpoint tests in `AuthenticateOtpEndpointTest`**

```kotlin
@Test fun `wrong codes count down, the fifth locks`() {
    for (remaining in 4 downTo 1) {
        val response = loginError("ray.sponsible@gmail.com", "654321")
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
        assertEquals(remaining, response.body?.error?.data?.get("remainingAttempts"))
    }
    val locked = loginError("ray.sponsible@gmail.com", "654321")
    assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, locked.body?.error?.code)
    assertNotNull(locked.body?.error?.data?.get("lockedUntil"))
    assertNotNull(authFactorRepository.findById(1L).get().lockedUntil)
}

@Test fun `the right code is refused while locked`() {
    val response = loginError("locked-otp@gmail.com", CODE)
    assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, response.body?.error?.code)
    assertEquals("2099-01-01T00:00:00Z", response.body?.error?.data?.get("lockedUntil"))
    val authFactor = authFactorRepository.findById(9L).get()
    assertNull(authFactor.lastLoggedInAt)
    assertEquals("2099-01-01T00:00:00Z", authFactor.expiresAt!!.toInstant().toString())
}

@Test fun `an expired lock lets the right code through`() {
    assertEquals(HttpStatus.OK, login("expired-lock-otp@gmail.com", CODE).statusCode)
    val authFactor = authFactorRepository.findById(10L).get()
    assertEquals(0, authFactor.failedAttempts)
    assertNull(authFactor.lockedUntil)
}

@Test fun `an expired lock starts the count over`() {
    val response = loginError("expired-lock-otp@gmail.com", "654321")
    assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    assertEquals(4, response.body?.error?.data?.get("remainingAttempts"))
}

@Test fun `a login resets the count`() {
    loginError("ray.sponsible@gmail.com", "654321")
    assertEquals(HttpStatus.OK, login("ray.sponsible@gmail.com", CODE).statusCode)
    val response = loginError("ray.sponsible@gmail.com", "654321")
    assertEquals(4, response.body?.error?.data?.get("remainingAttempts"))
}
```

For `expires_at` use `authFactorRepository.findById(9L).get().expiresAt!!.toInstant().toString() ==
"2099-01-01T00:00:00Z"`, matching the DATETIME precision.

- [x] **Step 3: Run them to watch them fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AuthenticateOtpEndpointTest`
Expected: FAIL — no `remainingAttempts`, `AUTH_INVALID_CREDENTIALS` on the 5th and while locked.

- [x] **Step 4: Add the `invalidCredentials` hook**

In `AbstractPasswordAuthenticator`, the two `throw ConflictException(Error(AUTH_INVALID_CREDENTIALS))` inside
`checkCredentials` become `invalidCredentials(null)` (no row) and `invalidCredentials(authFactor)` (wrong code), and:

```kotlin
/** What a wrong code answers: counts it in a subclass, e.g. to lock the account after too many. */
protected open fun invalidCredentials(authFactor: AuthFactorEntity?): Nothing =
    throw ConflictException(error = Error(code = ErrorCode.AUTH_INVALID_CREDENTIALS))
```

`PasswordAuthenticator` inherits it, unchanged.

- [x] **Step 5: Lock and count in `OtpAuthenticator`**

Inject `OtpProperties` (rename the constructor parameter to `properties`), add
`@EnableConfigurationProperties(OtpProperties::class)` on the class.

```kotlin
override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
    val authFactor = authFactorService.findByUserAndAuthTypeOrNull(user, AuthType.OTP) ?: invalidCredentials(null)
    if (authFactor.isLocked(clock)) {
        throw ConflictException(lockedError(authFactor))
    }
    val credential = super.checkCredentials(request, user)
    if (credential.isConsumed()) {
        throw ConflictException(error = Error(code = ErrorCode.AUTH_CREDENTIALS_ALREADY_USED))
    }
    return credential
}

/** A wrong code is counted even though the call fails: the 5th locks the account. */
override fun invalidCredentials(authFactor: AuthFactorEntity?): Nothing {
    if (authFactor == null || authFactor.authType != AuthType.OTP) {
        super.invalidCredentials(authFactor)
    }
    val saved = authFactorService.recordFailedAttempt(authFactor, properties.maxFailedAttempts, properties.lockDuration)
    if (saved.isLocked(clock)) {
        throw ConflictException(lockedError(saved))
    }
    throw ConflictException(
        error = Error(
            code = ErrorCode.AUTH_INVALID_CREDENTIALS,
            data = mapOf("remainingAttempts" to properties.maxFailedAttempts - saved.failedAttempts),
        )
    )
}

private fun lockedError(authFactor: AuthFactorEntity) = Error(
    code = ErrorCode.AUTH_ACCOUNT_LOCKED,
    data = mapOf("lockedUntil" to authFactor.lockedUntil!!.toInstant().toString()),
)
```

`clock` is already a constructor parameter of `AbstractAuthenticator` (`protected val clock`).

- [x] **Step 6: Stop the 409 rolling the count back**

`AuthenticationService.authenticate` →
`@Transactional(noRollbackFor = [ConflictException::class])` (`com.wutsi.ndopify.error.server.exception.ConflictException`),
with a KDoc line: the wrong-code count is written before the 409, and must survive it.

- [x] **Step 7: Add the two remaining tests**

- `AuthenticatePasswordEndpointTest`: `5 wrong passwords still answer AUTH_INVALID_CREDENTIALS` — a loop of five
  `AUTH_INVALID_CREDENTIALS`, none `AUTH_ACCOUNT_LOCKED`.
- `AuthenticateOtpLockConfigEndpointTest`: `@TestPropertySource(properties = ["ndopify.security.otp.max-failed-attempts=2"])`,
  the same `@Sql`, `TenantAwareEndpointIntegrationTest`: one wrong code is `AUTH_INVALID_CREDENTIALS`, the second is
  `AUTH_ACCOUNT_LOCKED`.

- [x] **Step 8: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='Authenticate*EndpointTest'`
Expected: PASS (needs local MySQL).

- [x] **Step 9: Commit**

```bash
git add modules/ndopify-server/src
git commit -m "feat(security): lock an account after 5 wrong OTP codes"
```

---

### Task 5: Refuse to issue a code while locked

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/OtpService.kt:24-73`
- Modify: `modules/ndopify-server/src/test/resources/db/test/security/CreateOtpEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/CreateOtpEndpointTest.kt`

**Interfaces:**
- Consumes: `AuthFactorEntity.isLocked`, `ErrorCode.AUTH_ACCOUNT_LOCKED` (Task 2).
- Produces: `POST /v1/otp` answers 409 `AUTH_ACCOUNT_LOCKED` (with `data.lockedUntil`) for a locked member, before
  generating or storing anything.

- [x] **Step 1: Add the fixtures**

User 9 `locked-otp@gmail.com` (membership 9, `T_USER_APPLICATION` 91) with an OTP factor locked until
`'2099-01-01 00:00:00'`, `failed_attempts = 5`, `data = 'old-hash'`; user 10 `expired-lock-otp@gmail.com`
(membership 10, application 101) with `failed_attempts = 5` and `locked_until = '2020-01-01 00:00:00'`.

- [x] **Step 2: Write the failing endpoint tests**

```kotlin
@Test fun `a locked account gets no code`() {
    val response = rest.postForEntity("/v1/otp", CreateOtpRequest(email = "locked-otp@gmail.com", ttl = 300), ErrorResponse::class.java)
    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, response.body?.error?.code)
    assertEquals("2099-01-01T00:00:00Z", response.body?.error?.data?.get("lockedUntil"))
    assertEquals("old-hash", authFactorRepository.findById(9L).get().data)
    assertEquals(0, smtp.receivedMessages.size) // the test waits for async tasks in tearDown
}

@Test fun `an expired lock gets a code`() {
    assertEquals(HttpStatus.OK, rest.postForEntity("/v1/otp", CreateOtpRequest(email = "expired-lock-otp@gmail.com", ttl = 300), Void::class.java).statusCode)
    assertTrue(smtp.waitForIncomingEmail(MAIL_TIMEOUT_MS, 1), "no email received")
}
```

- [x] **Step 3: Run them to watch them fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateOtpEndpointTest`
Expected: FAIL — `POST /v1/otp` issues a code for a locked account.

- [x] **Step 4: Refuse in `OtpService.create`**

Inject `java.time.Clock` and, right after the membership check — before `otpGenerator.generate()` —:

```kotlin
authFactorService.findByUserAndAuthTypeOrNull(user, AuthType.OTP)
    ?.takeIf { it.isLocked(clock) }
    ?.let {
        throw ConflictException(
            error = Error(
                code = ErrorCode.AUTH_ACCOUNT_LOCKED,
                data = mapOf("lockedUntil" to it.lockedUntil!!.toInstant().toString()),
            ),
        )
    }
```

The KDoc gains: a locked account gets no code — the e-mail isn't sent and the stored code is left as it is.

- [x] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateOtpEndpointTest`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add modules/ndopify-server/src
git commit -m "feat(security): refuse an OTP to a locked account"
```

---

### Task 6: Partner Central's login says how many attempts are left, and how long the lock lasts

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/OtpLockMessage.kt`
- Create: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/OtpLockMessageTest.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginController.kt:86-176`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginControllerTest.kt`

**Interfaces:**
- Produces:
  - `OtpLockMessage.locked(data: Map<String, Any>?, now: Long): String` — « Trop de tentatives. Réessayez dans N
    minutes. » (`minute` when 1, minutes rounded up, at least 1), « Trop de tentatives. Réessayez plus tard. » when
    `lockedUntil` is missing or unreadable.
  - `OtpLockMessage.invalid(data: Map<String, Any>?, fallback: String): String` — « Code incorrect. Encore N
    tentatives avant le blocage de votre compte. » (`tentative` when 1) from `remainingAttempts`, else `fallback`.

- [x] **Step 1: Write the failing unit test**

```kotlin
private val now = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()

@Test fun `locked - plural minutes`() {
    assertEquals("Trop de tentatives. Réessayez dans 42 minutes.", OtpLockMessage.locked(mapOf("lockedUntil" to "2026-11-15T10:42:00Z"), now))
}

@Test fun `locked - one minute`() {
    assertEquals("Trop de tentatives. Réessayez dans 1 minute.", OtpLockMessage.locked(mapOf("lockedUntil" to "2026-11-15T10:00:30Z"), now))
}

@Test fun `locked - no data`() {
    assertEquals("Trop de tentatives. Réessayez plus tard.", OtpLockMessage.locked(null, now))
}

@Test fun `invalid - remaining attempts`() {
    assertEquals("Code incorrect. Encore 2 tentatives avant le blocage de votre compte.", OtpLockMessage.invalid(mapOf("remainingAttempts" to 2), "Code incorrect."))
    assertEquals("Code incorrect. Encore 1 tentative avant le blocage de votre compte.", OtpLockMessage.invalid(mapOf("remainingAttempts" to 1), "Code incorrect."))
    assertEquals("Code incorrect.", OtpLockMessage.invalid(null, "Code incorrect."))
}
```

- [x] **Step 2: Run it to watch it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OtpLockMessageTest`
Expected: FAIL — `OtpLockMessage` unresolved.

- [x] **Step 3: Implement `OtpLockMessage`**

Parse `lockedUntil` with `Instant.parse` in a `try`, read `remainingAttempts` as a `Number`, and format both
sentences from the numbers (never from the server's message). This copy is deliberately local to the app, like
`toMessage` itself: the two web apps word their own pages.

- [x] **Step 4: Wire it into `LoginController`**

- `toMessage` parses the `Error` once (`ex.getResponseBodyAs(ErrorResponse::class.java)?.error`), keeps every current
  branch, and adds `AUTH_INVALID_CREDENTIALS -> OtpLockMessage.invalid(error?.data, "Code incorrect. Vérifiez les 6
  chiffres reçus par email.")` and `AUTH_ACCOUNT_LOCKED -> OtpLockMessage.locked(error?.data, clock.millis())`.
- `submit`'s catch: when the error code is `AUTH_ACCOUNT_LOCKED`, show `OtpLockMessage.locked(...)` instead of
  « Cette adresse email n'est pas valide! ». The pending login is still cleared, as today.

- [x] **Step 5: Extend `LoginControllerTest`**

`assertVerifyError(code, message, data = null)` gains the `data` parameter, passed to `Error(code = code, data =
data)`. New cases:

```kotlin
@Test fun verifyLocked() = assertVerifyError(
    ErrorCode.AUTH_ACCOUNT_LOCKED,
    "Trop de tentatives. Réessayez dans 42 minutes.",
    mapOf("lockedUntil" to Instant.ofEpochMilli(now).plus(42, ChronoUnit.MINUTES).toString()),
)

@Test fun verifyWrongCodeShowsRemainingAttempts() = assertVerifyError(
    ErrorCode.AUTH_INVALID_CREDENTIALS,
    "Code incorrect. Encore 2 tentatives avant le blocage de votre compte.",
    mapOf("remainingAttempts" to 2),
)
```

Plus `submit - locked` and `resend - locked` (the `otpClient.create` throws
`HttpClientErrorException(HttpStatus.CONFLICT)` with the lock body): the model's `error` is the lock sentence and the
pending login is cleared.

- [x] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=LoginControllerTest,OtpLockMessageTest`
Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src
git commit -m "feat(npc): show the OTP attempts left and the account lock"
```

---

### Task 7: The e-signature sign page says the same

**Files:**
- Create: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/ui/sign/OtpLockMessage.kt`
- Create: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/sign/OtpLockMessageTest.kt`
- Modify: `modules/ndopify-esignature/src/main/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignController.kt:62-80,110-170,270-290`
- Modify: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignControllerTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.AUTH_ACCOUNT_LOCKED`, `error.data`.
- Produces: same `OtpLockMessage` API as Task 6 (`locked(data, now)`, `invalid(data, fallback)`), duplicated in this
  app with its own wording choices — the two apps keep their own copy.

- [x] **Step 1: Write the failing unit test**

Copy `OtpLockMessageTest` from Task 6 (same four assertions; the wording is identical in both apps).

- [x] **Step 2: Run it to watch it fail**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=OtpLockMessageTest`
Expected: FAIL — unresolved.

- [x] **Step 3: Implement `OtpLockMessage` and wire it into `SignController`**

- Same object as Task 6.
- In `toCodeMessage`'s `when`: `AUTH_INVALID_CREDENTIALS -> OtpLockMessage.invalid(error?.data, CODE_WRONG)` and
  `AUTH_ACCOUNT_LOCKED -> OtpLockMessage.locked(error?.data, clock.millis())`.
- `otp`'s catch: `AUTH_ACCOUNT_LOCKED` → `OtpLockMessage.locked(...)`, before the `USER_NOT_FOUND` / `OTP_FAILED`
  branches. `errorCode(ex)` becomes a helper returning the whole `Error` (code + data) so both handlers read it
  once; keep `warn(ex)`'s log line.

- [x] **Step 4: Extend `SignControllerTest`**

- `otp - locked`: `otpClient.create` throws 409 `AUTH_ACCOUNT_LOCKED` with `lockedUntil` 42 minutes out → intro
  step with `error` = « Trop de tentatives. Réessayez dans 42 minutes. » and no `PendingSignature` in the session.
- `verify - locked`: `authClient.authenticate` throws it → code step with `error` = the same sentence, the pending
  signature kept.
- `verify - wrong code shows remaining attempts`: `AUTH_INVALID_CREDENTIALS` with `remainingAttempts = 1` → « Code
  incorrect. Encore 1 tentative avant le blocage de votre compte. ».

- [x] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=SignControllerTest,OtpLockMessageTest`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add modules/ndopify-esignature/src
git commit -m "feat(esignature): show the OTP attempts left and the account lock"
```

---

### Task 8: Document the rule and verify everything

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **security** bullet, line 72)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the sign-in bullet, lines 59–66)
- Modify: `modules/ndopify-esignature/CLAUDE.md` (the sign-in bullet, lines 50–55)

**Interfaces:**
- Consumes: everything above.

- [x] **Step 1: Document the server rule**

In the **security** bullet: one-time codes lock the account after `ndopify.security.otp.max-failed-attempts` (5)
wrong codes, for `lock-duration` (1h); `failed_attempts` / `locked_until` on the `OTP` `T_AUTH_FACTOR` row;
`AUTH_ACCOUNT_LOCKED` with `data.lockedUntil`, `AUTH_INVALID_CREDENTIALS` with `data.remainingAttempts`;
`POST /v1/otp` refuses while locked; the count survives a new code and a successful login clears it; password logins
count nothing.

- [x] **Step 2: Document both pages**

Partner Central: the code step shows the attempts left and the lock wait from `error.data`, and the e-mail step
shows the lock sentence when the account is locked. E-signature: the same on `/otp` and `/verify`.

- [x] **Step 3: Verify every touched module**

```bash
cd modules/ndopify-dto && mvn verify
cd ../ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
cd ../ndopify-esignature && mvn verify
```

Expected: BUILD SUCCESS everywhere, ktlint clean, JaCoCo gates met (the server needs local MySQL).

- [x] **Step 4: Check the tree**

Run: `git status --short`
Expected: only `pom.xml` modified (`<ndopify-dto.version>0.0.90-SNAPSHOT`, the local pin), nothing else. Never
commit it.

- [x] **Step 5: Commit the docs**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md modules/ndopify-esignature/CLAUDE.md
git commit -m "docs(security): document the OTP account lock"
```

- [x] **Step 6: Report**

Tell the user the story is implemented and green, and that it is ready for `push 126`. Do not push.
