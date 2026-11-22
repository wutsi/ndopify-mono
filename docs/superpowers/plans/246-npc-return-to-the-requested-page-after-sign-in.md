# Partner Central Sign-in Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send a signed-out agent back to the Partner Central page — and anchor — they asked for once OTP sign-in succeeds, instead of the Espace agent's root page.

**Architecture:** A small serializable `SignInTarget` keeps a validated local path, its query string and an optional fragment in the HTTP session. A `SignInTargetEntryPoint` saves it when Spring Security redirects an unauthenticated GET/HEAD to `/login`; `LoginController.verify` redirects to it on success, then clears it. Because a plain 302 would drop the deep link's `#fragment` (fragments are never sent to the server), the entry point answers a tiny interstitial page for a remembered GET whose script appends `location.hash` to the login URL before navigating; without JavaScript the interstitial's meta refresh still reaches `/login`, at the top. The login page's existing script then lifts `location.hash` into a hidden form field for the two form posts.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Spring Security 7, Thymeleaf 3, the esbuild `partner-central` bundle, JUnit 5 + `kotlin.test`, `MockMvc`, mockito-kotlin, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/246

## Global Constraints

- `modules/ndopify-partner-central` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, `ndopify-web` or `ndopify-esignature` change; no database, Flyway or `application*.yml` change; no new dependency.
- `UI: no`: no visible change. The French copy, layout and sign-in steps stay byte-identical; code, comments and KDoc stay English. The only markup addition is a hidden input.
- Never serve and never edit `src/main/html/` — it is the prototype.
- The target stays server-side in the HTTP session: never in a cookie, never in a `next`/`returnUrl` request parameter, never an absolute URL. Sign-in still rotates the session id (`request.changeSessionId()`).
- Only GET and HEAD requests are remembered; a POST body is never replayed.
- `mvn verify` in `modules/ndopify-partner-central` must be green (ktlint, tests, JaCoCo gate). The module's tests need neither MySQL nor a running ndopify-server.
- Commits are conventional, scoped `npc`, with `Refs #246` in the body.
- No prototype step: `UI: no` means the approved prototype does not apply here; Task 4's hidden field is the whole visual delta.

## Review Focus

- **Open redirect through a tampered session target:** a remembered `https://evil.example/...`, `//evil.example/...` or backslash path must resolve to `/`, never to another host. Task 1's `SignInTarget.of`/`redirectUrl` and Task 3's `verifyIgnoresAnUnsafeTarget` pin it.
- **Query string lost or mangled:** `/listings/42?match=7` must survive byte-for-byte, with no second `?` and no re-encoding. Task 1's `keepsTheQueryString` and Task 3's `verifyPreservesTheQueryString`.
- **Fragment lost between the entry point and the two POSTs:** a 302 redirect drops the deep link's `#match-7`, so the entry point must answer an interstitial whose script appends `location.hash` to `/login`. With JavaScript the browser must land on `#match-7` and the card must be in view; without JavaScript it must still reach `/mandates/42` at the top. Task 2's interstitial tests, Task 4's controller and template tests and `SignInTargetFlowTest`, plus the manual check at the end; the fragment cannot travel in a request by itself.
- **Target reused on a later sign-in, or dropped after a wrong code:** the target is kept across a failed verify, a resend and a rejected e-mail, and cleared exactly once on success. Task 3's `verifyKeepsTheTargetAfterAWrongCode` and `verifyClearsTheTarget`.
- **Login and public paths remembered:** `/login`, `/logout`, `/assets/**`, `/error`, `/join`, `/find-property`, `/offer-property`, `/locations` and the static PWA files must never become a target. Task 1's `rejectsLoginAndPublicPaths` and Task 2's `ignoresAnExcludedPath`.

---

### Task 1: The sign-in target value

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTarget.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTargetTest.kt`

**Interfaces:**
- Produces: `data class SignInTarget(val path: String, val query: String? = null, val fragment: String? = null) : Serializable`
  - `companion object { const val SESSION_ATTRIBUTE = "npc.sign-in-target"; const val MAX_FRAGMENT_LENGTH = 200 }`
  - `companion object fun of(path: String?, query: String? = null): SignInTarget?` — returns `null` for anything that is not a local, non-excluded absolute path.
  - `companion object fun fragmentOf(raw: String?): String?` — strips a leading `#`, returns `null` for empty, over-long or unsafe values.
  - `fun redirectUrl(): String?` — re-validates `path`/`query`, appends `?query` and `#fragment` when present, returns `null` when the stored value is not safe.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.login

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignInTargetTest {
    @Test
    fun keepsTheRequestedPath() {
        assertEquals("/mandates/42", SignInTarget.of("/mandates/42")?.redirectUrl())
    }

    @Test
    fun keepsTheHomePage() {
        assertEquals("/", SignInTarget.of("/")?.redirectUrl())
    }

    @Test
    fun keepsTheQueryString() {
        assertEquals("/listings/42?match=7", SignInTarget.of("/listings/42", "match=7")?.redirectUrl())
    }

    @Test
    fun keepsTheFragment() {
        val target = SignInTarget.of("/mandates/42", "match=7")?.copy(fragment = "match-7")
        assertEquals("/mandates/42?match=7#match-7", target?.redirectUrl())
    }

    @Test
    fun rejectsNonLocalPaths() {
        assertNull(SignInTarget.of("https://evil.example/mandates/42"))
        assertNull(SignInTarget.of("//evil.example/mandates/42"))
        assertNull(SignInTarget.of("/\\evil.example/mandates/42"))
        assertNull(SignInTarget.of("/mandates/42\r\nSet-Cookie: x=y"))
        assertNull(SignInTarget.of("mandates/42"))
        assertNull(SignInTarget.of(null))
        assertNull(SignInTarget.of("/mandates/42", "a\r\nb"))
    }

    @Test
    fun rejectsLoginAndPublicPaths() {
        listOf(
            "/login", "/login/connection", "/logout", "/error", "/error/403", "/assets/dist/app.min.js",
            "/join", "/join/identity", "/find-property", "/offer-property", "/locations",
            "/sw.js", "/offline.html", "/manifest.json", "/favicon.ico",
        ).forEach { assertNull(SignInTarget.of(it), it) }
    }

    @Test
    fun validatesTheFragment() {
        assertEquals("match-7", SignInTarget.fragmentOf("#match-7"))
        assertEquals("visits-title", SignInTarget.fragmentOf("visits-title"))
        assertNull(SignInTarget.fragmentOf(null))
        assertNull(SignInTarget.fragmentOf(""))
        assertNull(SignInTarget.fragmentOf("#"))
        assertNull(SignInTarget.fragmentOf("a".repeat(SignInTarget.MAX_FRAGMENT_LENGTH + 1)))
        assertNull(SignInTarget.fragmentOf("match 7"))
        assertNull(SignInTarget.fragmentOf("bad\r\nheader"))
    }

    @Test
    fun ignoresAnUnsafeStoredTarget() {
        assertNull(SignInTarget("https://evil.example/mandates/42").redirectUrl())
        assertNull(SignInTarget("//evil.example/mandates/42").redirectUrl())
        assertNull(SignInTarget("/login").redirectUrl())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SignInTargetTest`
Expected: compilation failure, `Unresolved reference 'SignInTarget'`.

- [ ] **Step 3: Implement `SignInTarget`**

`path` is valid when it starts with `/`, does not start with `//`, and contains neither a backslash, `#`, `?` nor a control character (`\u0000`–`\u001F`, `\u007F`). `query` is valid when it contains neither a backslash, `#` nor a control character; an empty query becomes `null`. The exclusions are exact `/login`, `/logout`, `/error`, `/sw.js`, `/offline.html`, `/manifest.json`, `/favicon.ico`, and the prefixes `/login/`, `/error/`, `/assets/`, `/join`, `/find-property`, `/offer-property`, `/locations`.

The fragment pattern is `[A-Za-z0-9._~!$&'()*+,;=:@/?%-]{1,200}` (no space, `#`, quote, angle bracket or control character). `redirectUrl()` runs `of(path, query)` again and returns `null` if it fails, so a value written into the session by any other means is still refused. `Serializable` mirrors `PendingLogin`, since it lives in the HTTP session.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SignInTargetTest`
Expected: `BUILD SUCCESS`, 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTarget.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTargetTest.kt
git commit -m "feat(npc): add the sign-in target value" -m "Refs #246"
```

---

### Task 2: Remember the requested page at the login entry point

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SignInTargetEntryPoint.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt` (the `exceptionHandling` line, currently `LoginUrlAuthenticationEntryPoint("/login")`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/SignInTargetEntryPointTest.kt`

**Interfaces:**
- Consumes: `SignInTarget.of(path, query)` and `SignInTarget.SESSION_ATTRIBUTE` from Task 1.
- Produces: `class SignInTargetEntryPoint(loginFormUrl: String = "/login") : LoginUrlAuthenticationEntryPoint(loginFormUrl)`, overriding `commence` to store a `SignInTarget` in the session for GET/HEAD requests only. A remembered GET answers a tiny interstitial page (script `location.replace('/login' + location.hash)`, with a `<meta http-equiv="refresh">` to `/login` as the no-JavaScript fallback) instead of delegating to `super.commence`, so the deep link's fragment isn't dropped; HEAD and POST still delegate to `super.commence`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.ui.login.SignInTarget
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.InsufficientAuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInTargetEntryPointTest {
    private val entryPoint = SignInTargetEntryPoint("/login")
    private val error = InsufficientAuthenticationException("sign in")

    @Test
    fun remembersAGetTarget() {
        val request = MockHttpServletRequest("GET", "/mandates/42").apply { queryString = "match=7" }
        val response = MockHttpServletResponse()

        entryPoint.commence(request, response, error)

        assertEquals(
            SignInTarget("/mandates/42", "match=7"),
            request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE),
        )
        assertTrue(response.redirectedUrl!!.endsWith("/login"))
    }

    @Test
    fun remembersAHeadTarget() {
        val request = MockHttpServletRequest("HEAD", "/listings/42")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertEquals(SignInTarget("/listings/42"), request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }

    @Test
    fun ignoresAPost() {
        val request = MockHttpServletRequest("POST", "/mandates/42/close")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }

    @Test
    fun ignoresAnExcludedPath() {
        val request = MockHttpServletRequest("GET", "/login")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SignInTargetEntryPointTest`
Expected: compilation failure, `Unresolved reference 'SignInTargetEntryPoint'`.

- [ ] **Step 3: Implement the entry point and wire it in `SecurityConfiguration`**

`commence` stores `SignInTarget.of(request.requestURI, request.queryString)` under `SignInTarget.SESSION_ATTRIBUTE` in `request.getSession(true)` when the method is `GET` or `HEAD`. A `null` target stores nothing. For a remembered `GET`, write a tiny interstitial page (status 200, `text/html;charset=UTF-8`) whose script is `location.replace('/login' + location.hash)` with a `<meta http-equiv="refresh" content="0; url=/login">` fallback, instead of delegating to `super.commence`; for `HEAD`, `POST` and a `GET` with no remembered target, delegate to `super.commence(request, response, authException)` so the redirect to `/login` is unchanged. Replace the inline `LoginUrlAuthenticationEntryPoint("/login")` with `SignInTargetEntryPoint("/login")`; no other security rule changes.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=SignInTargetEntryPointTest`
Expected: `BUILD SUCCESS`, 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SignInTargetEntryPoint.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/config/SignInTargetEntryPointTest.kt
git commit -m "feat(npc): remember the requested page at sign-in" -m "Refs #246"
```

---

### Task 3: Return to the target after a successful verify

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginController.kt` (`verify`, and its KDoc)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginControllerTest.kt`

**Interfaces:**
- Consumes: `SignInTarget` and `SignInTarget.SESSION_ATTRIBUTE` from Task 1.
- Produces: `LoginController.verify` redirects to `SignInTarget.redirectUrl()` when the session holds a valid target, otherwise to `/`, and removes the attribute on every successful verify.

- [ ] **Step 1: Write the failing tests**

Add these to `LoginControllerTest`, reusing its `pending`, `session` and `authClient` fixtures:

```kotlin
@Test
fun verifyReturnsToTheRequestedPage() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42"))
    doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

    val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals("redirect:/mandates/42", result.modelAndView?.viewName)
    assertNull(session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
}

@Test
fun verifyPreservesTheQueryString() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/listings/42", "match=7"))
    doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

    val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals("redirect:/listings/42?match=7", result.modelAndView?.viewName)
}

@Test
fun verifyReplaysTheFragment() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42", fragment = "match-7"))
    doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

    val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals("redirect:/mandates/42#match-7", result.modelAndView?.viewName)
}

@Test
fun verifyIgnoresAnUnsafeTarget() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("https://evil.example/mandates/42"))
    doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

    val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals("redirect:/", result.modelAndView?.viewName)
    assertNull(session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
}

@Test
fun verifyKeepsTheTargetAfterAWrongCode() {
    val target = SignInTarget("/mandates/42", fragment = "match-7")
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, target)
    doThrow(conflict(ErrorCode.AUTH_INVALID_CREDENTIALS)).whenever(authClient).authenticate(any())

    mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals(target, session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
}

@Test
fun verifyClearsTheTargetForTheNextSignIn() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42"))
    doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())
    mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

    assertEquals("redirect:/", result.modelAndView?.viewName)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=LoginControllerTest`
Expected: the new tests fail with `redirect:/` instead of the target; the existing `verify` test still passes.

- [ ] **Step 3: Implement the redirect in `verify`**

After `request.changeSessionId()` and `accessTokenService.set(accessToken)`, read the `SignInTarget` from the session, remove the attribute, and return `"redirect:" + (target?.redirectUrl() ?: "/")`. Keep the existing `PendingLogin` removal, the session-id rotation, the wrong-code and no-pending branches untouched. Update the class KDoc to say that a sign-in returns to the remembered page, and `/` when there is none.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=LoginControllerTest`
Expected: `BUILD SUCCESS`, every existing and new test green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginControllerTest.kt
git commit -m "fix(npc): return to the requested page after sign-in" -m "Refs #246"
```

---

### Task 4: Carry the link's fragment through the two steps

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginController.kt` (`submit`, `emailStep`, and the KDoc)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/login.html` (the e-mail form)
- Modify: `modules/ndopify-partner-central/src/main/assets/js/partner-central.js` (`initLoginEmail`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginTemplateTest.kt` (new)

**Interfaces:**
- Consumes: `SignInTarget.fragmentOf` and `SignInTarget.SESSION_ATTRIBUTE` from Task 1; the `LoginController` session lookup from Task 3.
- Produces: `LoginForm.fragment: String?`, `LoginController.emailStep(email: String?, model: Model, session: HttpSession)`, and a hidden `input[name=fragment]` on the e-mail step.

- [ ] **Step 1: Write the failing controller tests**

Add to `LoginControllerTest`:

```kotlin
@Test
fun submitStoresTheFragmentOnTheTarget() {
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42"))

    val result = mvc.perform(
        post("/login").param("email", "ray@gmail.com").param("fragment", "match-7").session(session),
    ).andReturn()

    assertEquals(
        SignInTarget("/mandates/42", fragment = "match-7"),
        session.getAttribute(SignInTarget.SESSION_ATTRIBUTE),
    )
    assertEquals("redirect:/login?step=code", result.modelAndView?.viewName)
}

@Test
fun submitIgnoresAFragmentWithoutATarget() {
    mvc.perform(post("/login").param("email", "ray@gmail.com").param("fragment", "match-7").session(session))

    assertNull(session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
}
```

Also assert that a rejected OTP request re-renders the e-mail step with the fragment in the model: in `submitRejectedByServer`, seed `SignInTarget("/mandates/42", fragment = "match-7")` and assert `model(result)["fragment"] == "match-7"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=LoginControllerTest`
Expected: `Unresolved reference 'fragment'` on `LoginForm`, or a failed assertion on `fragment` in the model.

- [ ] **Step 3: Write the failing template test**

Create `LoginTemplateTest` modelled on `JoinTemplateTest`'s real Thymeleaf engine (`SpringTemplateEngine` + `ClassLoaderTemplateResolver` on `templates/` + `ThymeleafViewResolver`), with `LoginController(otpClient, authClient, accessTokenService, Clock.fixed(...))` and a `MockHttpSession`:

```kotlin
@Test
fun emailStepRendersTheRememberedFragment() {
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42", fragment = "match-7"))

    val page = render(get("/login"))

    assertEquals("match-7", page.selectFirst("form[data-login-form] input[name=fragment]")?.`val`())
}

@Test
fun codeStepHasNoFragmentField() {
    session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
    session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42", fragment = "match-7"))

    val page = render(get("/login?step=code"))

    assertNull(page.selectFirst("input[name=fragment]"))
}
```

`render` follows `JoinTemplateTest.render`: perform the request, assert `200`, parse with `Jsoup.parse(response.getContentAsString(Charsets.UTF_8))`.

- [ ] **Step 4: Run the template test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=LoginTemplateTest`
Expected: failure because `templates/login.html` has no `input[name=fragment]`.

- [ ] **Step 5: Implement the form field, the controller and the script**

- `LoginForm` gains `val fragment: String? = null`.
- `LoginController.submit` reads `SignInTarget.fragmentOf(form.fragment)`; when the session holds a target and the fragment is valid, it replaces the target with `target.copy(fragment = fragment)` before requesting the OTP, so a rejected e-mail (which clears `PendingLogin`) does not lose it.
- `LoginController.emailStep` gains the `session` parameter and adds `model.addAttribute("fragment", signInTarget(session)?.fragment)`; update its two callers (`show`, `submit`'s failure path).
- `templates/login.html`: inside `form[data-login-form]`, add `<input type="hidden" name="fragment" data-sign-in-fragment th:value="${fragment}">`. Nothing visible changes.
- `partner-central.js`, at the start of `initLoginEmail(root)`: read `[data-sign-in-fragment]`; when `window.location.hash.length > 1`, set its value to `window.location.hash.substring(1)` (the length is capped server-side). Leave the server-rendered value alone when the hash is empty, so a re-rendered e-mail step keeps it. No other script changes.

- [ ] **Step 6: Run the focused tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='LoginControllerTest,LoginTemplateTest'`
Expected: `BUILD SUCCESS`, all controller and template tests green.

- [ ] **Step 7: Rebuild the bundle and commit**

`mvn verify` runs `build-assets.mjs`, so no manual bundle commit is needed (`target/classes` and the generated `app.min.js` are build output). Commit the source only:

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginForm.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/LoginController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/login.html \
        modules/ndopify-partner-central/src/main/assets/js/partner-central.js \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/login/LoginTemplateTest.kt
git commit -m "fix(npc): replay the sign-in link's fragment" -m "Refs #246"
```

## Manual verification

After Task 4, with a local stack (`ndopify-server` + MySQL + the local tenant) or the TEST environment:

1. Sign out, then open an authenticated URL with a fragment — for example `/mandates/{id}#match-7` (or `/listings/{id}#visits-title`). Expected: a brief interstitial, then `/login#match-7` in the address bar (the fragment carried over by the interstitial's script).
2. Request the OTP, enter it. Expected: `/mandates/{id}#match-7`, with the `match-7` card in view; the address bar keeps the query and the hash.
3. Repeat with JavaScript disabled. Expected: `/mandates/{id}` (no hash), at the top of the page.
4. Open `/login` directly and sign in. Expected: `/`.
5. Open `/mandates/{id}?match=7` while signed out, sign in. Expected: `/mandates/{id}?match=7`.
6. Sign out, open `/mandates/{id}`, sign in with a wrong code, then the right one. Expected: the wrong code stays on `/login?step=code`; the right one opens `/mandates/{id}`.

Final gate:

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: `BUILD SUCCESS`, ktlint clean, all tests green, JaCoCo gate met.
