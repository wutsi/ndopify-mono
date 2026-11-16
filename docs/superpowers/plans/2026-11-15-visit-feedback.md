# Visit Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the customer of a `COMPLETED` visit answer the feedback form once from the public visit page, and notify the visit's agent over Telegram (e-mail fallback) when the answer arrives.

**Architecture:** `ndopify-web` adds `POST /visits/{id}/feedback` (a `VisitFeedbackForm` → `NdVisitClient.feedback`), and renders the `COMPLETED` block of `visit.html` either as the prototype's form or as its two thank-you states (driven by `Visit.interest`). `ndopify-server` publishes `VisitFeedbackSentEvent` from `VisitService.feedback`; its after-commit listener runs `VisitFeedbackNotifier`, which messages the agent (`VisitFeedbackBotlet`) when its Telegram chat is linked and e-mails it (`VisitFeedbackMailet`) otherwise, with the e-mail as the failure fallback.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf + Jsoup template tests, MockMvc / MockRestServiceServer, mockito-kotlin, `kotlin.test`, MySQL for `ndopify-server` integration tests.

**Spec:** GitHub issue #168 — https://github.com/wutsi/ndopify-mono/issues/168. The approved visual source is the committed prototype `modules/ndopify-web/src/main/html/visite.html`, states `completed`, `feedback-sent`, `feedback-sent-no`; this plan changes no prototype file.

## Global Constraints

- Two modules: `ndopify-web` and `ndopify-server`. No `ndopify-dto`, `ndopify-web-core`, Flyway or root `pom.xml` change; no library release.
- UI and bot/e-mail text in French; code, comments, logs and docs in English.
- Dates and times go through `TenantFormatter` / `VisitSlots` (tenant locale and `timeZone`), never a hard-coded pattern.
- The page works without JavaScript; the reasons' `:has` visibility is CSS only, no new JS.
- The notified agent is `Visit.agentId`. The co-brokerage listing agent is not notified.
- Never include the visit's code, exact address or the customer's `public-portal` token in a notification; never log the bot token or the Telegram URL.
- The notification is a side effect: a failure is logged, never rethrown to the customer, and the feedback stays committed.
- One module per commit (a `ndopify-web` commit or a `ndopify-server` commit, never both); conventional messages with `Refs #168` in the body.
- `kotlin.test` assertions and `mockito-kotlin` mocks, as in the surrounding tests.
- `mvn verify` green in each touched module before the push; `ndopify-server` needs a local MySQL.

## Review Focus

1. **Blank interest never reaches the server** — `VisitFeedbackRequest.interest` defaults to `INTERESTED`; a post without a valid interest must be a redirect to `?feedback=invalid`, never a request (Task 3).
2. **No notification without a new answer** — a 409 `VISIT_FEEDBACK_ALREADY_SENT` / `VISIT_NOT_COMPLETED` must call no notifier (Tasks 11, 12).
3. **No secret in the message** — the bot text and the e-mail context never carry the code, the exact address or the customer's token (Tasks 8, 9); assert their absence.
4. **One channel, one fallback** — linked agent → Telegram and no e-mail; unlinked agent → e-mail; failed Telegram → e-mail (Tasks 10, 12).
5. **The thank-you promises nothing extra** — no WhatsApp link without an agent number, and the copy assumes the notification this story adds (Task 6).

---

## Part A — ndopify-web

### Task 1: Prototype gate (no code)

**Files:** none.

- [ ] **Step 1: Serve the prototype**

Run: `cd modules/ndopify-web && python3 -m http.server 4321 --directory src/main/html`

- [ ] **Step 2: Show the three states**

Open `http://localhost:4321/visite.html?state=completed`, `?state=feedback-sent`, `?state=feedback-sent-no` at 390 px and 1280 px. They are already approved; the "l'agent est prévenu" copy becomes true with Task 8. Stop the server (`pkill -f "http.server 4321"`).

- [ ] **Step 3: Stop for the human partner's OK**

Show the states and wait. Do not edit any DTO, server or web code before the OK. No file changes in this task.

---

### Task 2: Post the feedback to ndopify-server

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClient.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClientTest.kt`

**Interfaces:**
- Produces: `NdVisitClient.feedback(id: Long, request: VisitFeedbackRequest)` — `POST /v1/visits/{id}/feedback`; lets the server's exceptions propagate.

- [ ] **Step 1: Write the failing test**

Add to `NdVisitClientTest` (imports: `com.wutsi.ndopify.match.dto.MatchRejectionReason`, `com.wutsi.ndopify.visit.dto.VisitFeedbackRequest`, `com.wutsi.ndopify.visit.dto.VisitInterest`):

```kotlin
@Test
fun feedback() {
    server.expect(requestTo("http://localhost:8080/v1/visits/9/feedback"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.interest").value("NOT_INTERESTED"))
        .andExpect(jsonPath("$.reasonCode").value("PRICE"))
        .andExpect(jsonPath("$.comment").value("Trop cher"))
        .andRespond(withStatus(HttpStatus.OK))

    client.feedback(
        9,
        VisitFeedbackRequest(
            interest = VisitInterest.NOT_INTERESTED,
            reasonCode = MatchRejectionReason.PRICE,
            comment = "Trop cher",
        ),
    )
    server.verify()
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdVisitClientTest#feedback`
Expected: compilation error — `feedback` unresolved.

- [ ] **Step 3: Implement `feedback`**

In `NdVisitClient`, beside `cancel`:

```kotlin
fun feedback(id: Long, request: VisitFeedbackRequest) {
    rest.post()
        .uri("/v1/visits/{id}/feedback", id)
        .body(request)
        .retrieve()
        .toBodilessEntity()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=NdVisitClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClient.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/client/ndopify/NdVisitClientTest.kt
git commit -m "feat(web): post the visit feedback to ndopify-server" -m "Refs #168"
```

---

### Task 3: The feedback form and its POST endpoint

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitFeedbackForm.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt`

**Interfaces:**
- Produces:
  - `VisitFeedbackForm(interest: String? = null, reasonCode: String? = null, comment: String? = null)`
  - `VisitController.feedback(id: Long, form: VisitFeedbackForm, model: Model): Any` (`POST /visits/{id}/feedback`)
  - `SecurityConfiguration` permits `POST /visits/*/feedback`.

- [ ] **Step 1: Write the failing controller tests**

Add to `VisitControllerTest` (imports: `com.wutsi.ndopify.match.dto.MatchRejectionReason`, `com.wutsi.ndopify.visit.dto.VisitFeedbackRequest`, `com.wutsi.ndopify.visit.dto.VisitInterest`):

```kotlin
@Test
fun `feedback - interested`() {
    MatchFixtures.signIn()

    val result = perform(post("/visits/9/feedback").param("interest", "INTERESTED"))

    verify(visitClient).feedback(9, VisitFeedbackRequest(interest = VisitInterest.INTERESTED))
    assertEquals("/visits/9", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `feedback - not interested, with a reason and a trimmed comment`() {
    MatchFixtures.signIn()

    perform(
        post("/visits/9/feedback")
            .param("interest", "NOT_INTERESTED")
            .param("reasonCode", "PRICE")
            .param("comment", "  Trop cher  "),
    )

    verify(visitClient).feedback(
        9,
        VisitFeedbackRequest(
            interest = VisitInterest.NOT_INTERESTED,
            reasonCode = MatchRejectionReason.PRICE,
            comment = "Trop cher",
        ),
    )
}

@Test
fun `feedback - no reason is OTHER`() {
    MatchFixtures.signIn()

    perform(post("/visits/9/feedback").param("interest", "NOT_INTERESTED").param("comment", "  "))

    verify(visitClient).feedback(
        9,
        VisitFeedbackRequest(
            interest = VisitInterest.NOT_INTERESTED,
            reasonCode = MatchRejectionReason.OTHER,
            comment = null,
        ),
    )
}

@Test
fun `feedback - a reason is dropped when interested`() {
    MatchFixtures.signIn()

    perform(post("/visits/9/feedback").param("interest", "INTERESTED").param("reasonCode", "PRICE"))

    verify(visitClient).feedback(9, VisitFeedbackRequest(interest = VisitInterest.INTERESTED))
}

@Test
fun `feedback - a long comment is cut`() {
    MatchFixtures.signIn()

    perform(post("/visits/9/feedback").param("interest", "INTERESTED").param("comment", "x".repeat(1001)))

    verify(visitClient).feedback(
        9,
        VisitFeedbackRequest(interest = VisitInterest.INTERESTED, comment = "x".repeat(1000)),
    )
}

@Test
fun `feedback - no interest is not forwarded`() {
    MatchFixtures.signIn()

    val result = perform(post("/visits/9/feedback"))
    val invalid = perform(post("/visits/9/feedback").param("interest", "MAYBE"))

    assertEquals("/visits/9?feedback=invalid", result.response.getHeader(HttpHeaders.LOCATION))
    assertEquals("/visits/9?feedback=invalid", invalid.response.getHeader(HttpHeaders.LOCATION))
    verify(visitClient, never()).feedback(any(), any())
}

@Test
fun `feedback - conflict returns to the page`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.CONFLICT)).whenever(visitClient).feedback(any(), any())

    val result = perform(post("/visits/9/feedback").param("interest", "INTERESTED"))

    assertEquals("/visits/9", result.response.getHeader(HttpHeaders.LOCATION))
}

@Test
fun `feedback - server unauthorized`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(visitClient).feedback(any(), any())

    val result = perform(post("/visits/9/feedback").param("interest", "INTERESTED"))

    verify(accessTokenService).remove()
    assertEquals("expired", result.modelAndView?.model?.get("reason"))
}

@Test
fun `feedback - another customer`() {
    MatchFixtures.signIn()
    doThrow(failure(HttpStatus.FORBIDDEN)).whenever(visitClient).feedback(any(), any())

    val result = perform(post("/visits/9/feedback").param("interest", "INTERESTED"))

    assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
}

@Test
fun `feedback - signed out`() {
    val result = perform(post("/visits/9/feedback").param("interest", "INTERESTED"))

    assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
    verify(visitClient, never()).feedback(any(), any())
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitControllerTest`
Expected: compilation error — `feedback` on `NdVisitClient` is known but the route/handler is absent; the POSTs return 404 or 403.

- [ ] **Step 3: Add `VisitFeedbackForm`**

```kotlin
package com.wutsi.ndopify.web.ui.visit

/**
 * The feedback form of a COMPLETED visit: [interest] (`INTERESTED` / `NOT_INTERESTED`), the [reasonCode] when the
 * property doesn't interest the customer, and an optional [comment]. Read as strings so an unknown value is a redirect,
 * never a binding error.
 */
data class VisitFeedbackForm(
    val interest: String? = null,
    val reasonCode: String? = null,
    val comment: String? = null,
)
```

- [ ] **Step 4: Add the POST handler**

In `VisitController`, beside `cancel`:

```kotlin
/**
 * Posts the feedback; a missing or unknown interest goes back with `?feedback=invalid` and is never forwarded
 * (`VisitFeedbackRequest` defaults a missing interest to `INTERESTED`). A missing reason is `OTHER`, a blank comment
 * none, a longer one is cut to what ndopify-server takes; a 409 (already answered, or no longer COMPLETED) returns to
 * the page, which shows the answer it already has.
 */
@PostMapping("/visits/{id}/feedback")
fun feedback(@PathVariable id: Long, @ModelAttribute form: VisitFeedbackForm, model: Model): Any =
    signedIn("POST /visits/$id/feedback", model) {
        val interest = VisitInterest.entries.find { it.name == form.interest }
            ?: return@signedIn redirect("/visits/$id?feedback=invalid")
        val request = VisitFeedbackRequest(
            interest = interest,
            reasonCode = if (interest == VisitInterest.NOT_INTERESTED) {
                MatchRejectionReason.entries.find { it.name == form.reasonCode }
                    ?.takeIf { it != MatchRejectionReason.UNKNOWN }
                    ?: MatchRejectionReason.OTHER
            } else {
                null
            },
            comment = form.comment?.trim()?.ifEmpty { null }?.take(MAX_COMMENT),
        )
        try {
            visitClient.feedback(id, request)
            redirect("/visits/$id")
        } catch (ex: HttpClientErrorException.Conflict) {
            LOGGER.info("Visit #$id feedback already sent, or the visit is no longer COMPLETED")
            redirect("/visits/$id")
        }
    }
```

Add `private const val MAX_COMMENT = 1000` beside `MAX_REASON`, and the imports
`com.wutsi.ndopify.match.dto.MatchRejectionReason`, `com.wutsi.ndopify.visit.dto.VisitFeedbackRequest`,
`com.wutsi.ndopify.visit.dto.VisitInterest`.

- [ ] **Step 5: Permit the route**

In `SecurityConfiguration`, after the cancellation matcher:

```kotlin
.requestMatchers(HttpMethod.POST, "/visits/*/feedback").permitAll()
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitControllerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitFeedbackForm.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt
git commit -m "feat(web): accept the visit feedback form" -m "Refs #168"
```

---

### Task 4: The feedback fields of the visit page

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Produces:
  - `VisitPage.feedbackInterest: VisitInterest?` — the answer, null when none.
  - `VisitPage.feedbackIntro: String?` — "Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée." (COMPLETED only).
  - `VisitPage.feedbackError: String?` — "Répondez par oui ou par non." only for `?feedback=invalid` on an unanswered COMPLETED visit.
  - `VisitPages.of(..., cancel: String?, feedback: String?)` and `VisitPages.FEEDBACK_INVALID = "invalid"`.

- [ ] **Step 1: Write the failing page tests**

In `VisitPagesTest`, add a `feedback: String? = null` parameter to the private `of(...)` helper and pass it to `VisitPages.of`. Add (imports: `com.wutsi.ndopify.visit.dto.VisitInterest`):

```kotlin
@Test
fun `completed intro`() {
    assertEquals(
        "Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée.",
        of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)).feedbackIntro,
    )
}

@Test
fun `completed video intro`() {
    assertEquals(
        "Votre visite vidéo du samedi 7 novembre 2026 à 11:00 est terminée.",
        of(visit(VisitStatus.COMPLETED, VisitType.VIDEO)).feedbackIntro,
    )
}

@Test
fun `feedback interest is the answer`() {
    val page = of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.NOT_INTERESTED))

    assertEquals(VisitInterest.NOT_INTERESTED, page.feedbackInterest)
}

@Test
fun `feedback invalid asks for an answer`() {
    val page = of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID)

    assertEquals("Répondez par oui ou par non.", page.feedbackError)
}

@Test
fun `feedback invalid is ignored once answered`() {
    val page = of(
        visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED),
        feedback = VisitPages.FEEDBACK_INVALID,
    )

    assertNull(page.feedbackError)
}

@Test
fun `no feedback error outside COMPLETED`() {
    val page = of(visit(VisitStatus.CHECKED_IN, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID)

    assertNull(page.feedbackError)
    assertNull(page.feedbackIntro)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitPagesTest`
Expected: compilation error — `feedbackIntro`, `feedbackInterest`, `feedbackError`, `FEEDBACK_INVALID` unresolved.

- [ ] **Step 3: Add the fields to `VisitPage`**

After `flash`:

```kotlin
/** The answer given, null while the customer hasn't answered. */
val feedbackInterest: VisitInterest?,
/** "Votre visite sur place du … à … est terminée." for a COMPLETED visit, null otherwise. */
val feedbackIntro: String?,
/** "Répondez par oui ou par non." when `?feedback=invalid` found no answer; null otherwise. */
val feedbackError: String?,
```

Import `com.wutsi.ndopify.visit.dto.VisitInterest`.

- [ ] **Step 4: Compute them in `VisitPages.of`**

Add the parameter `feedback: String?` after `cancel: String?`, then before `VisitPage(`:

```kotlin
val intro = feedbackIntro(visit, tenant, formatter)
val error = if (
    feedback == FEEDBACK_INVALID && visit.status == VisitStatus.COMPLETED && visit.interest == null
) {
    "Répondez par oui ou par non."
} else {
    null
}
```

and pass `feedbackInterest = visit.interest`, `feedbackIntro = intro`, `feedbackError = error`. Add `const val FEEDBACK_INVALID = "invalid"` to `VisitPages` and:

```kotlin
/** "Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée." — COMPLETED only, tenant-local. */
private fun feedbackIntro(visit: Visit, tenant: Tenant, formatter: TenantFormatter): String? {
    if (visit.status != VisitStatus.COMPLETED) {
        return null
    }
    val at = visit.scheduledAt ?: visit.requestedAt
    val local = at.toInstant().atZone(ZoneId.of(tenant.timeZone)).toLocalDate()
    val type = if (visit.type == VisitType.VIDEO) "vidéo" else "sur place"
    return "Votre visite $type du ${formatter.date(local, "EEEE d MMMM yyyy")} à ${formatter.time(at)} est terminée."
}
```

- [ ] **Step 5: Read `feedback` in the GET handler**

In `VisitController.index`, add `@RequestParam(name = "feedback", required = false) feedback: String?` after `cancel`, and pass it as the last argument of `VisitPages.of(...)`.

- [ ] **Step 6: Point the test helpers at the new signature**

In `VisitTemplateTest`, add `feedback: String? = null` to the private `of(...)` helper (and the direct `VisitPages.of` call) and pass it through. Add to `VisitControllerTest`:

```kotlin
@Test
fun `feedback invalid reaches the page`() {
    MatchFixtures.signIn()
    doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.COMPLETED, type = VisitType.ON_SITE))
        .whenever(visitClient).get(9)

    val page = page(request("/visits/9?feedback=invalid"))

    assertEquals("Répondez par oui ou par non.", page.feedbackError)
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitPagesTest,VisitControllerTest,VisitTemplateTest`
Expected: PASS (`VisitTemplateTest` compiles with the new helper parameter; the template still renders).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitController.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitControllerTest.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt
git commit -m "feat(web): add the visit feedback view fields" -m "Refs #168"
```

---

### Task 5: Share the rejection-reason choices

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/reject-reasons.html`
- Modify: `modules/ndopify-web/src/main/resources/templates/match.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

**Interfaces:**
- Produces: the Thymeleaf fragment `fragments/reject-reasons :: fieldset(name, required)` — one `<fieldset class="field reject-reasons">` with the six `MatchRejectionReason` labels, radios named `${name}`, `required` set only when `required` is true.

- [ ] **Step 1: Extend the match test**

In `MatchTemplateTest.pending`, after the existing reason-value assertion:

```kotlin
assertTrue(reject.select("input[name=reason]").all { it.hasAttr("required") })
```

- [ ] **Step 2: Run the test to verify it passes before the move**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchTemplateTest`
Expected: PASS — it pins the current markup before the refactor.

- [ ] **Step 3: Create the fragment**

`templates/fragments/reject-reasons.html`:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/* The six reasons of a "Pas pour moi" answer, shared by the match popup and the visit feedback form. `required`
       is true only where the form needs one (the match page); a false value removes the attribute. */-->
<fieldset class="field reject-reasons" th:fragment="fieldset(name, required)">
    <legend>La raison principale</legend>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="PRICE">Trop cher</label>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="LOCATION">Le quartier ne me convient pas</label>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="SIZE">Trop petit ou trop grand</label>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="AMENITIES">Il manque des équipements</label>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="AVAILABILITY">Disponible trop tard</label>
    <label><input th:name="${name}" th:required="${required}" type="radio" value="OTHER">Autre raison</label>
</fieldset>
</body>
</html>
```

- [ ] **Step 4: Use it in the match popup**

In `match.html`, replace the whole `<fieldset class="field reject-reasons">…</fieldset>` of the `#reject` popup with:

```html
<fieldset th:replace="~{fragments/reject-reasons :: fieldset('reason', true)}"></fieldset>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchTemplateTest`
Expected: PASS — same six values, still required.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/fragments/reject-reasons.html \
  modules/ndopify-web/src/main/resources/templates/match.html \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt
git commit -m "refactor(web): share the rejection-reason choices" -m "Refs #168"
```

---

### Task 6: The COMPLETED block of the visit page

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `page.feedbackInterest`, `page.feedbackIntro`, `page.feedbackError`, `page.agent?.name`, `page.agent?.whatsappUrl`, the Task 5 fragment.

- [ ] **Step 1: Write the failing template tests**

In `VisitTemplateTest`, replace the old `no feedback form and no prototype links` test with these (imports: `com.wutsi.ndopify.visit.dto.VisitInterest`):

```kotlin
@Test
fun `completed without feedback shows the form`() {
    val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE)))
    val completed = doc.selectFirst("[data-visit-state=COMPLETED]")!!

    assertTrue(completed.selectFirst("h2")!!.text().startsWith("Le bien vous intéresse"))
    assertEquals("post", completed.selectFirst("form.feedback")?.attr("method"))
    assertEquals("/visits/9/feedback", completed.selectFirst("form.feedback")?.attr("action"))
    assertEquals(2, completed.select("input[name=interest]").size)
    assertTrue(completed.select("input[name=interest]").all { it.hasAttr("required") })
    assertEquals(
        listOf("PRICE", "LOCATION", "SIZE", "AMENITIES", "AVAILABILITY", "OTHER"),
        completed.select("input[name=reasonCode]").map { it.attr("value") },
    )
    assertTrue(completed.select("input[name=reasonCode]").none { it.hasAttr("required") })
    assertEquals("1000", completed.selectFirst("#feedback-comment")?.attr("maxlength"))
    assertTrue(completed.text().contains("Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée."))
    assertTrue(completed.text().contains("Votre réponse aide Serge Ngono pour la suite."))
    assertTrue(doc.select("[href$=.html], [src$=.html]").isEmpty())
}

@Test
fun `completed interested shows the thanks and the agent whatsapp`() {
    val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED)))

    assertNull(doc.selectFirst("form.feedback"))
    assertEquals("Merci pour votre réponse", doc.selectFirst("[data-visit-state=COMPLETED] h2")?.text())
    assertTrue(
        doc.text().contains("Le logement vous intéresse : Serge Ngono vous recontacte pour la suite (dossier, bail)."),
    )
    assertEquals("https://wa.me/237677000111", doc.selectFirst(".feedback-thanks a.btn")?.attr("href"))
    assertTrue(doc.selectFirst(".feedback-thanks a.btn")!!.text().contains("Écrire à Serge Ngono"))
}

@Test
fun `completed interested without a number has no whatsapp`() {
    val doc = render(
        of(
            visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.INTERESTED),
            agent = agent.copy(whatsappNumber = null),
        ),
    )

    assertNull(doc.selectFirst(".feedback-thanks a.btn"))
}

@Test
fun `completed not interested says the agent keeps searching`() {
    val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE).copy(interest = VisitInterest.NOT_INTERESTED)))

    assertNull(doc.selectFirst("form.feedback"))
    assertTrue(
        doc.text().contains("C'est noté : Serge Ngono est prévenu et continue de chercher pour vous. Il vous enverra d'autres logements."),
    )
}

@Test
fun `feedback invalid shows the error above the form`() {
    val doc = render(of(visit(VisitStatus.COMPLETED, VisitType.ON_SITE), feedback = VisitPages.FEEDBACK_INVALID))

    assertEquals("Répondez par oui ou par non.", doc.selectFirst(".field-error")?.text())
    assertNotNull(doc.selectFirst("form.feedback"))
}

@Test
fun `other states have no feedback form`() {
    listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN, VisitStatus.CANCELLED, VisitStatus.EXPIRED, VisitStatus.NO_SHOW)
        .forEach { status ->
            assertNull(render(of(visit(status, VisitType.ON_SITE))).selectFirst("form.feedback"), status.name)
        }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL — no `form.feedback`; the COMPLETED block still shows "Votre visite est terminée".

- [ ] **Step 3: Render the three COMPLETED variants**

Replace the whole COMPLETED `<section>` of `visit.html` with:

```html
<!-- ============================================================ COMPLETED -->
<section aria-labelledby="completed-title" class="visit-state" data-visit-state="COMPLETED"
         th:if="${page.status.name() == 'COMPLETED'}">
    <!--/* No answer yet: the feedback form (final, like a match answer) */-->
    <th:block th:if="${page.feedbackInterest == null}">
        <h2 id="completed-title">Le bien vous intéresse&nbsp;?</h2>
        <p><span th:text="${page.feedbackIntro}">Votre visite sur place du samedi 7 novembre 2026 à 11:00 est terminée.</span>
            <span th:text="|Votre réponse aide ${page.agent?.name ?: 'votre agent'} pour la suite.|">Votre réponse aide Serge pour la suite.</span>
        </p>
        <p class="field-error" role="alert" th:if="${page.feedbackError}" th:text="${page.feedbackError}">Répondez par oui ou par non.</p>
        <form class="feedback" method="post" th:action="@{/visits/{id}/feedback(id=${page.id})}">
            <fieldset class="field feedback__interest">
                <legend class="visually-hidden">Le bien vous intéresse&nbsp;?</legend>
                <div class="option-grid option-grid--2">
                    <label><input name="interest" required type="radio" value="INTERESTED">Oui, il m'intéresse</label>
                    <label><input name="interest" required type="radio" value="NOT_INTERESTED">Non</label>
                </div>
            </fieldset>
            <div class="feedback__no" th:insert="~{fragments/reject-reasons :: fieldset('reasonCode', false)}"></div>
            <div class="field">
                <label for="feedback-comment">Un commentaire <span class="field__optional">(facultatif)</span></label>
                <textarea id="feedback-comment" maxlength="1000" name="comment" rows="3"></textarea>
            </div>
            <button class="btn btn-primary visit-submit" type="submit">Envoyer ma réponse</button>
        </form>
    </th:block>

    <!--/* Answered: the prototype's thanks, never the form again */-->
    <th:block th:if="${page.feedbackInterest != null}">
        <h2 id="completed-title">Merci pour votre réponse</h2>
        <th:block th:if="${page.feedbackInterest.name() == 'INTERESTED'}">
            <p class="notice notice--success">
                <svg aria-hidden="true" class="icon"><use href="#i-check"/></svg>
                <span th:text="|Le logement vous intéresse : ${page.agent?.name ?: 'votre agent'} vous recontacte pour la suite (dossier, bail).|">Le logement vous intéresse : Serge vous recontacte pour la suite (dossier, bail).</span>
            </p>
            <div class="feedback-thanks">
                <p>Vous pouvez aussi lui écrire dès maintenant.</p>
                <a class="btn btn-secondary" th:if="${page.agent?.whatsappUrl}" rel="noopener"
                   target="_blank" th:href="${page.agent?.whatsappUrl}">
                    <svg aria-hidden="true" class="icon"><use href="#i-chat"/></svg><span
                        th:text="|Écrire à ${page.agent?.name ?: 'votre agent'}|">Écrire à Serge</span><span
                        class="visually-hidden"> sur WhatsApp</span>
                </a>
            </div>
        </th:block>
        <p th:if="${page.feedbackInterest.name() == 'NOT_INTERESTED'}"
           th:text="|C'est noté : ${page.agent?.name ?: 'votre agent'} est prévenu et continue de chercher pour vous. Il vous enverra d'autres logements.|">C'est noté : Serge est prévenu et continue de chercher pour vous. Il vous enverra d'autres logements.</p>
    </th:block>
</section>
```

Update the template's head comment: "Only the current state's block is rendered: the COMPLETED block shows the feedback form, or its thanks once answered; no check-in UI."

- [ ] **Step 4: Add the feedback styles**

At the end of `visit.css`, after the existing `.visit-state` rules:

```css
/* The feedback form of a COMPLETED visit: the match reply's reasons, shown when "Non" (all of them without :has) */
.feedback fieldset { border: 0; margin: 0 0 var(--space-xl); padding: 0; }
.feedback .reject-reasons { margin-bottom: var(--space-xl); }
@supports selector(:has(*)) {
  .feedback__no { display: none; }
  .feedback:has(input[value="NOT_INTERESTED"]:checked) .feedback__no { display: block; }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/visit.html \
  modules/ndopify-web/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt
git commit -m "feat(web): let the customer answer the visit feedback form" -m "Refs #168"
```

---

### Task 7: Document and verify ndopify-web

**Files:**
- Modify: `modules/ndopify-web/CLAUDE.md`

- [ ] **Step 1: Update the module documentation**

In the `ui/visit/` bullet, replace "No feedback form and no check-in UI: the page only reads." with the feedback behaviour: the COMPLETED block shows the form (Oui / Non, the match reply's reasons — CSS `:has`, no JS — an optional comment ≤ 1000) unless `interest` is already set, then the prototype's "Merci pour votre réponse" (the agent's WhatsApp when it has a number); `POST /visits/{id}/feedback` redirects to `/visits/{id}` (a 409 too), a missing/unknown interest to `/visits/{id}?feedback=invalid` without calling the server. No check-in UI.

In the client list, add `feedback` to `NdVisitClient`. In the `config/` bullet, add `POST /visits/*/feedback` to the permitted routes. In the static-assets paragraph, note that `visit.css` now carries the feedback styles (no JS change).

- [ ] **Step 2: Run the module verification**

Run: `cd modules/ndopify-web && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-web/CLAUDE.md
git commit -m "docs(web): document the visit feedback form" -m "Refs #168"
```

---

## Part B — ndopify-server

### Task 8: The feedback botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackLabels.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`, `TenantService.findById`, `TenantFormatter`, `AgentEntity`/`CustomerEntity.party.fullName()`.
- Produces:
  - `VisitFeedbackLabels.interest(interest: VisitInterest): String` → "Intéressé" / "Pas intéressé".
  - `VisitFeedbackLabels.reason(code: MatchRejectionReason?): String?` → "trop cher", "le quartier ne convient pas", "trop petit ou trop grand", "il manque des équipements", "disponible trop tard", "autre raison"; null for null/UNKNOWN.
  - `VisitFeedbackBotlet.send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing test**

`VisitFeedbackBotletTest` (mirror `VisitRequestedBotletTest`'s fixtures):

```kotlin
package com.wutsi.ndopify.visit.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitFeedbackBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = VisitFeedbackBotlet(tenantService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(id = 10L, party = PartyEntity(firstName = "Ray", lastName = "Sponsible"))
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.COMPLETED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 1, 10, 0),
        interest = VisitInterest.NOT_INTERESTED,
        feedbackReasonCode = MatchRejectionReason.PRICE,
        feedbackComment = "Trop cher pour moi",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `a not-interested answer carries the reason and the comment`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Ray Sponsible a donné son avis sur la visite.

            Bien: Nice apartment
            Date: 01/12/2026 à 10:00
            Client: Ray Sponsible
            Avis: Pas intéressé
            Raison: trop cher
            Commentaire: Trop cher pour moi
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `an interested answer carries no reason and no comment`() {
        botlet.send(
            visit.copy(interest = VisitInterest.INTERESTED, feedbackReasonCode = null, feedbackComment = null),
            customer, agent, listing, 1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Avis: Intéressé"))
        assertFalse(text.firstValue.contains("Raison:"))
        assertFalse(text.firstValue.contains("Commentaire:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Bien n° 101"))
    }

    @Test
    fun `an unknown reason leaves no Raison line`() {
        botlet.send(visit.copy(feedbackReasonCode = MatchRejectionReason.UNKNOWN), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Raison:"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Africa/Douala")).toInstant())
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest`
Expected: compilation error — `VisitFeedbackBotlet` / `VisitFeedbackLabels` unresolved.

- [ ] **Step 3: Implement the labels and the botlet**

`VisitFeedbackLabels` is an `object` in `visit/server/service/` holding the six `MatchRejectionReason` labels above (same wording as Partner Central's `VisitRules.REJECTION_LABELS`) plus `interest(...)`; KDoc says it is the wording of the feedback notification, in French, shared by the botlet and the mailet.

`VisitFeedbackBotlet` is a `@Service` with `(tenantService, client)`:
- `send` resolves the tenant and its `TenantFormatter`, then calls `client.sendMessage(chatId, text(...), buttons)`; the button is `TelegramButton("Voir la visite", url = "$base/visits/${visit.id}")` where `base = tenant.partnerCentralUrl?.removeSuffix("/")`, else `emptyList()`.
- `text` lines: `"Bonjour ${agent.party.fullName()},"`, blank, `"${customer.party.fullName()} a donné son avis sur la visite."`, blank, `"Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}")`, `"Date: ${formatter.date(at)} à ${formatter.time(at)}"` with `at = visit.scheduledAt ?: visit.requestedAt`, `"Client: ${customer.party.fullName()}"`, `"Avis: ${VisitFeedbackLabels.interest(it)}"`, then when `visit.interest == VisitInterest.NOT_INTERESTED` a `"Raison: ${label}"` line if `VisitFeedbackLabels.reason(visit.feedbackReasonCode)` is non-null, and a `"Commentaire: $it"` line if `visit.feedbackComment` is non-blank. Join with `"\n"`.
- KDoc: the mirror of `VisitFeedbackMailet`; never the visit's code, exact address or the customer's token; Telegram refusing throws and the notifier falls back to the e-mail.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackLabels.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt
git commit -m "feat(visit): message the agent a visit feedback over Telegram" -m "Refs #168"
```

---

### Task 9: The feedback e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/visit/feedback.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailetTest.kt`

**Interfaces:**
- Produces:
  - `VisitFeedbackMailet.send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)`
  - `VisitFeedbackMailet.TEMPLATE = "/mail/visit/feedback.mjml"`, `SUBJECT = "{{tenant_name}}: l'avis de {{customer_name}} sur la visite"`.
  - Context keys: `tenant_logo_url`, `tenant_name`, `recipient_name`, `customer_name`, `listing_title`, `image_url`, `visit_date`, `visit_time`, `interest`, `reason`, `comment`, `visit_url`.

- [ ] **Step 1: Write the failing test**

`VisitFeedbackMailetTest`, modelled on `VisitRequestedMailetTest`: same mocks (`tenantService`, `mailService`, `mailBodyResolver`, `imageService`), same tenant/customer/agent/listing, and a `visit` that is `COMPLETED`, `scheduledAt = date(2026, 12, 1, 10, 0)`, `interest = NOT_INTERESTED`, `feedbackReasonCode = PRICE`, `feedbackComment = "Trop cher pour moi"`.

```kotlin
@Test
fun send() {
    mailet.send(visit, customer, agent, listing)

    assertEquals(
        mapOf(
            "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
            "tenant_name" to "Ndopify",
            "recipient_name" to "Kim Possible",
            "customer_name" to "Ray Sponsible",
            "listing_title" to "Nice apartment",
            "image_url" to "https://cdn.ndopify.com/7.jpg",
            "visit_date" to "01/12/2026",
            "visit_time" to "10:00",
            "interest" to "Pas intéressé",
            "reason" to "trop cher",
            "comment" to "Trop cher pour moi",
            "visit_url" to "https://partner.ndopify.com/visits/1",
        ),
        context(),
    )

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
    assertEquals("Kim Possible", email.firstValue.recipient.displayName)
    assertEquals("Ndopify: l'avis de Ray Sponsible sur la visite", email.firstValue.subject)
    assertEquals("<html>feedback</html>", email.firstValue.body)
}

@Test
fun `an interested answer has no reason and no comment`() {
    mailet.send(visit.copy(interest = VisitInterest.INTERESTED, feedbackReasonCode = null, feedbackComment = null), customer, agent, listing)

    assertEquals("Intéressé", context()["interest"])
    assertNull(context()["reason"])
    assertNull(context()["comment"])
}

@Test
fun `no partner central - no link`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    mailet.send(visit, customer, agent, listing)

    assertNull(context()["visit_url"])
}
```

`context()` and `date()` copy `VisitRequestedMailetTest`; `setUp` returns `"<html>feedback</html>"` from `mailBodyResolver.resolve(any(), any())`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackMailetTest`
Expected: compilation error — `VisitFeedbackMailet` unresolved.

- [ ] **Step 3: Implement the mailet**

Copy `VisitRequestedMailet`'s shape: `(tenantService, mailService, mailBodyResolver, imageService)`. `send` resolves the tenant and formatter, the cover image, the names and `visitUrl` (`{partnerCentralUrl}/visits/{id}`, null without `partnerCentralUrl`), and sends an `Email` to the agent with the subject above and `mailBodyResolver.resolve(TEMPLATE, context)` where:
`visit_date`/`visit_time` are the formatter's for `visit.scheduledAt ?: visit.requestedAt`; `interest` is `VisitFeedbackLabels.interest(visit.interest)`;
`reason` is `VisitFeedbackLabels.reason(visit.feedbackReasonCode)` only when `interest == NOT_INTERESTED`, else null;
`comment` is `visit.feedbackComment?.ifBlank { null }`.

- [ ] **Step 4: Write the template**

`mail/visit/feedback.mjml`: copy `requested.mjml`'s wrapper, logo, divider, photo (linked to `visit_url`), greeting and sign-off. Replace the body with:
`{customer_name} a donné son avis sur la visite du bien {listing_title} du {visit_date} à {visit_time}.`
a bold `Avis : {interest}`, then `{{#reason}}Raison : {reason}{{/reason}}`, `{{#comment}}« {comment} »{{/comment}}`, and the `{{#visit_url}}` "Voir la visite" button. Never the code, address or token.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackMailetTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailet.kt \
  modules/ndopify-server/src/main/resources/mail/visit/feedback.mjml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailetTest.kt
git commit -m "feat(visit): e-mail the agent a visit feedback" -m "Refs #168"
```

---

### Task 10: The feedback notifier

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackNotifierTest.kt`

**Interfaces:**
- Consumes: Task 8's botlet, Task 9's mailet, `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `VisitFeedbackNotifier.send(visit, customer, agent, listing)` — Telegram when linked, e-mail otherwise, e-mail on any send failure.

- [ ] **Step 1: Write the failing test**

Copy `VisitRequestedNotifierTest` with `VisitFeedbackBotlet` / `VisitFeedbackMailet` / `VisitFeedbackNotifier` and the same three tests:

```kotlin
@Test
fun `an unlinked agent is e-mailed, Telegram is not called`() {
    doReturn(null).whenever(telegramService).chatId(20L)

    notifier.send(visit, customer, agent, listing)

    verify(mailet).send(visit, customer, agent, listing)
    verifyNoInteractions(botlet)
}

@Test
fun `a linked agent is messaged, not e-mailed`() {
    doReturn(1000L).whenever(telegramService).chatId(20L)

    notifier.send(visit, customer, agent, listing)

    verify(botlet).send(visit, customer, agent, listing, 1000L)
    verifyNoInteractions(mailet)
}

@Test
fun `a failed Telegram send falls back to the e-mail`() {
    doReturn(1000L).whenever(telegramService).chatId(20L)
    doThrow(TelegramException("Forbidden: bot was blocked by the user"))
        .whenever(botlet).send(visit, customer, agent, listing, 1000L)

    notifier.send(visit, customer, agent, listing)

    verify(mailet).send(visit, customer, agent, listing)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackNotifierTest`
Expected: compilation error — `VisitFeedbackNotifier` unresolved.

- [ ] **Step 3: Implement the notifier**

Copy `VisitRequestedNotifier`: `(botlet, mailet, telegramService)`, a private `LOGGER`. `send` resolves `telegramService.chatId(agent.id!!)`; null → `mailet.send(...)` and return; otherwise the botlet, and `catch (ex: Exception)` logs `"Telegram visit feedback failed for Visit#${visit.id}, falling back to e-mail"` then calls the mailet. KDoc: tells the visit's agent its customer answered the feedback form, over Telegram when #135's link gives it a chat, by e-mail otherwise; the e-mail is the failure fallback, so an answer is never lost; the failure is logged without the bot token nor the URL.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackNotifier.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitFeedbackNotifierTest.kt
git commit -m "feat(visit): route the visit feedback notice to Telegram or e-mail" -m "Refs #168"
```

---

### Task 11: Publish the event and run the notification

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitFeedbackSentEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Produces:
  - `VisitFeedbackSentEvent(visitId: Long, tenantId: Long)`.
  - `VisitService.sendFeedbackNotification(visitId: Long)`.
  - `VisitEventListener.onVisitFeedbackSent(event: VisitFeedbackSentEvent)`.
  - `VisitService.feedback` publishes `VisitFeedbackSentEvent` after the save.

- [ ] **Step 1: Write the failing service tests**

In `VisitServiceTest`: add `private val feedbackNotifier = mock<VisitFeedbackNotifier>()` after `requestedNotifier`, and pass it to the `VisitService` constructor right after `requestedNotifier`. Add (imports: `VisitFeedbackSentEvent`, `VisitFeedbackRequest`, `VisitInterest`):

```kotlin
@Test
fun `feedback publishes the notification event`() {
    doReturn(visit.copy(status = VisitStatus.COMPLETED)).whenever(dao).findByIdForUpdate(1L)

    service.feedback(1L, VisitFeedbackRequest(interest = VisitInterest.INTERESTED))

    verify(eventPublisher).publishEvent(VisitFeedbackSentEvent(visitId = 1L, tenantId = 1L))
}

@Test
fun `feedback - the agent is notified`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.COMPLETED, interest = VisitInterest.INTERESTED)))
        .whenever(dao).findById(1L)

    service.sendFeedbackNotification(1L)

    verify(feedbackNotifier).send(any(), eq(customer), eq(agent20), eq(coBrokerageListing))
}

@Test
fun `feedback - nothing without an answer`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.COMPLETED, interest = null))).whenever(dao).findById(1L)

    service.sendFeedbackNotification(1L)

    verifyNoInteractions(feedbackNotifier)
}

@Test
fun `feedback - nothing when not COMPLETED`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.CONFIRMED, interest = VisitInterest.INTERESTED)))
        .whenever(dao).findById(1L)

    service.sendFeedbackNotification(1L)

    verifyNoInteractions(feedbackNotifier)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: compilation error — `feedbackNotifier`, `VisitFeedbackSentEvent`, `sendFeedbackNotification` unresolved.

- [ ] **Step 3: Add the event**

```kotlin
package com.wutsi.ndopify.visit.server.event

/**
 * Published by `VisitService.feedback`, inside its transaction, when a customer answers the visit feedback form. Carries
 * ids rather than the entity, so the listener reloads what it needs ([tenantId] is set because it runs on a thread that
 * has none).
 */
data class VisitFeedbackSentEvent(
    val visitId: Long,
    val tenantId: Long,
)
```

- [ ] **Step 4: Publish it and add the service method**

In `VisitService`: add `private val feedbackNotifier: VisitFeedbackNotifier` after `requestedNotifier`. In `feedback`, replace the final `return dao.save(...)` with:

```kotlin
val saved = dao.save(visit.copy(/* … unchanged … */))
eventPublisher.publishEvent(VisitFeedbackSentEvent(visitId = saved.id!!, tenantId = saved.tenantId!!))
return saved
```

Add, beside `sendRequestedNotification`:

```kotlin
/** Notifies the visit's agent of the customer's answer. Called by `VisitEventListener`; nothing without one. */
@Transactional
fun sendFeedbackNotification(visitId: Long) {
    val visit = dao.findById(visitId).orElse(null) ?: return
    if (visit.status != VisitStatus.COMPLETED || visit.interest == null) {
        return
    }
    logger.add("visit_id", visitId)
    feedbackNotifier.send(
        visit = visit,
        customer = customerService.findById(visit.customerId),
        agent = agentService.findById(visit.agentId),
        listing = listingService.findById(visit.listingId),
    )
}
```

Update `feedback`'s KDoc: "…and notifies the visit's agent after the commit, once."

- [ ] **Step 5: Listen for it**

In `VisitEventListener`, add the method after `onVisitCompleted`, mirroring the others:

```kotlin
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun onVisitFeedbackSent(event: VisitFeedbackSentEvent) {
    val logger = DefaultKVLogger()
    KVLoggerThreadLocal.set(logger)
    TenantContext.set(event.tenantId)
    try {
        logger.add("event", VisitFeedbackSentEvent::class.java.simpleName)
        logger.add("tenant_id", event.tenantId)
        logger.add("visit_id", event.visitId)

        service.sendFeedbackNotification(event.visitId)
        logger.add("success", true)
    } catch (ex: Exception) {
        logger.add("success", false)
        logger.setException(ex)
    } finally {
        logger.log()
        TenantContext.remove()
        KVLoggerThreadLocal.remove()
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest,VisitFeedbackEndpointTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitFeedbackSentEvent.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): notify the agent when the visit feedback is sent" -m "Refs #168"
```

---

### Task 12: Endpoint coverage

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitFeedback.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitFeedbackEndpointTest.kt`

**Interfaces:**
- Consumes: `POST /v1/visits/{id}/feedback`, `VisitFixtures.sql`, `VisitFeedbackEndpoint.sql` (visit 1 COMPLETED, agent 20; visit 2 already answered; visit 3 CONFIRMED), `@MockitoBean TelegramClient`, `awaitAsyncTasks()`, `smtp`.

- [ ] **Step 1: Write the seed**

`TelegramVisitFeedback.sql`:

```sql
-- The agent of VisitFeedbackEndpoint.sql's visits (agent 20), linked to the bot
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.VisitFeedbackRequest
import com.wutsi.ndopify.visit.dto.VisitInterest
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/VisitFeedbackEndpoint.sql",
        "/db/test/telegram/TelegramVisitFeedback.sql",
    ],
)
class TelegramVisitFeedbackEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.NOT_INTERESTED, comment = "Trop cher"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Ray Sponsible a donné son avis sur la visite."))
        assertTrue(text.firstValue.contains("Bien: Nice apartment"))
        assertTrue(text.firstValue.contains("Date: 01/12/2026 à 10:00"))
        assertTrue(text.firstValue.contains("Avis: Pas intéressé"))
        assertTrue(text.firstValue.contains("Raison: autre raison"))
        assertTrue(text.firstValue.contains("Commentaire: Trop cher"))
        assertFalse(text.firstValue.contains("1234"), "never the visit code")
        assertFalse(text.firstValue.contains("Rue 1.234"), "never the exact address")
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no e-mail for a linked agent")
    }

    @Sql(
        value = [
            "/db/test/clean.sql",
            "/db/test/visit/VisitFixtures.sql",
            "/db/test/visit/VisitFeedbackEndpoint.sql",
        ],
    )
    @Test
    fun `an unlinked agent is e-mailed, never messaged`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail to the agent")
        assertEquals(
            "Kim Possible <kim.possible@gmail.com>",
            smtp.receivedMessages.single().allRecipients.single().toString(),
        )
    }

    @Test
    fun `an already answered visit notifies nobody`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/2/feedback",
            VisitFeedbackRequest(interest = VisitInterest.NOT_INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no notification for a 409")
    }

    @Test
    fun `a visit not completed notifies nobody`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/3/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no notification for a visit not COMPLETED")
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitFeedbackEndpointTest`
Expected: FAIL — the notifier is not wired / the message differs (or the class fails to compile on `TelegramClient` if the import is wrong; `TelegramClient` lives in `com.wutsi.ndopify.platform.telegram`).

- [ ] **Step 4: Fix the wiring until it is green**

Likely gaps: `VisitFeedbackNotifier` missing `@Service`, or Task 11's constructor parameter absent. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the visit endpoint tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitFeedbackEndpointTest,VisitFeedbackEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitFeedback.sql \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitFeedbackEndpointTest.kt
git commit -m "test(telegram): cover the visit feedback notification" -m "Refs #168"
```

---

### Task 13: Document and verify ndopify-server

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

- [ ] **Step 1: Document the notification**

In the **visit** bullet, after the **Request notification** paragraph, add **Feedback notification**: `VisitFeedbackNotifier` messages the visit's agent when the customer answers the feedback form — over Telegram when its chat is linked (`VisitFeedbackBotlet`), e-mailed otherwise (`VisitFeedbackMailet`, `mail/visit/feedback.mjml`), with the e-mail as the fallback of a failed send (the failure is logged, without the token nor the URL). The message carries the interest, the reason and comment when not interested, the listing (or `Bien n° <id>`), the date and time in the tenant's `timeZone`, the customer, and a `Voir la visite` button to `{partnerCentralUrl}/visits/{id}` (none without one); never the visit's code, exact address or the customer's `public-portal` token. Only a new answer notifies: a 409 (`VISIT_FEEDBACK_ALREADY_SENT` / `VISIT_NOT_COMPLETED`) does not.

In the **Asynchronous e-mails** list, add the visit feedback to the e-mailed notifications and `VisitFeedbackSentEvent` to the events.

- [ ] **Step 2: Run the module verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(visit): document the visit feedback notification" -m "Refs #168"
```

---

## Self-Review

- **Spec coverage:** every acceptance criterion maps to a task — the form (Tasks 3, 6), the answered states (Tasks 4, 6), the blank-interest redirect (Task 3), the 409s (Tasks 3, 12), the security route (Task 3), the notification (Tasks 8–12), the omission cases (Tasks 8–10) and the docs (Tasks 7, 13).
- **No DTO/server contract change:** `ndopify-web` uses the existing `VisitFeedbackRequest` / `Visit`; `ndopify-server`'s event is internal; no migration, no release.
- **Type consistency:** `feedbackInterest` / `feedbackIntro` / `feedbackError` are defined in Task 4 and consumed by Task 6; `VisitFeedbackLabels.interest` / `reason` are defined in Task 8 and consumed by Task 9; `VisitFeedbackNotifier.send` is defined in Task 10 and consumed by Task 11.
- **Review Focus:** each line has its test — blank interest (Task 3), no notification on 409 (Tasks 11, 12), no secret in the message (Task 12), one channel plus fallback (Tasks 10, 12), thank-you without a number (Task 6).
