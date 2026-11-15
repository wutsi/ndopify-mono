# Visit Page Contacts and Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the approved Partner Central visit-page prototype to the served page: name every phone number with its owner's role, put the date first, quiet the destructive actions behind one chooser, make the video WhatsApp flow explicit, and give the code lock a warning and a support route.

**Architecture:** Server-rendered, no JavaScript required: `VisitViews` adds five view fields (`leadWhen`, `slotChanged`, `agentPhone`, `agentPhoneDigits`, `noShowFrom`) to `VisitPage`; `templates/visit.html` renders the two labelled contacts, the date band and the problem chooser; `visit.js` only toggles the chooser; `visit.css` carries the prototype's styling. `?close=problem` is a new server state alongside `?close=refuse|cancel|no-show`, so the chooser works without JS.

**Tech Stack:** Kotlin, Spring Boot + Thymeleaf, `mockito-kotlin`, `kotlin.test`, Jsoup template tests, Maven. No DTO, web-core, server or migration change.

**Spec:** GitHub issue #129 — https://github.com/wutsi/ndopify-mono/issues/129. The approved prototype is in `src/main/html/` (committed with this plan) and is the visual source.

## Global Constraints

- Work on branch `feat/npc-visit-contacts-and-actions`.
- Module `ndopify-partner-central` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server` or migration change; no library release.
- French UI text; dates through `TenantFormatter` / `VisitSlots`, never a hard-coded pattern.
- The page must work without JavaScript: the problem chooser is a `?close=problem` server state; `?close=no-show` and `?close=cancel` keep opening their forms; a hand-edited `close` value is ignored.
- Support channel: `mailto:contact@ndopify.com`, visit id in the subject; no new support page or number.
- `src/main/html/` is the prototype: never serve it, never delete it. It is already committed; do not redesign it.
- Tests use `kotlin.test` assertions and `mockito-kotlin`; tests mirror the main package.
- `mvn verify` green in `modules/ndopify-partner-central` before the push.

## Review Focus

1. **No-JS chooser** — `?close=problem` must render the two choices; each choice must link to its existing form; a non-WINDOW panel must ignore it.
2. **Phone ownership** — both numbers always carry their role and person; the accessible names must include the person, not just "WhatsApp".
3. **Moved slot** — only CONFIRMED/WINDOW, only when the instants differ; the request's original time must stay in the note, and equal times must leave no note.
4. **Video WINDOW** — the header WhatsApp must be absent and the panel's "Ouvrir WhatsApp" must be the only chat entry, with the three steps.
5. **Code lock** — the support link and the no-show route must be reachable, and the `0000` placeholder must be gone.

---

### Task 1: Expiry wording in days

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRules.kt:136-143`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRulesTest.kt:85-93`

**Interfaces:**
- Consumes: nothing new.
- Produces: `VisitRules.expiresIn(left: Long): String` unchanged signature; days from 48 h, hours below.

- [ ] **Step 1: Write the failing test**

Add to `VisitRulesTest.expires`:

```kotlin
assertEquals("5 j", VisitRules.expiresIn(123 * hour))
assertEquals("1 j", VisitRules.expiresIn(48 * hour))
assertEquals("47 h", VisitRules.expiresIn(47 * hour))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitRulesTest`
Expected: FAIL — `expected:<5 j> but was:<123 h>`.

- [ ] **Step 3: Implement the day threshold**

In `VisitRules`, add `private const val DAY = 24 * HOUR` beside `HOUR`/`MINUTE`, then:

```kotlin
fun expiresIn(left: Long): String {
    val days = Math.floorDiv(left, 2 * DAY)
    val hours = Math.floorDiv(left, HOUR)
    return when {
        days >= 1 -> "$days j"
        hours >= 1 -> "$hours h"
        else -> "${maxOf(1L, ceil(left / MINUTE.toDouble()).toLong())} min"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitRulesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRules.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitRulesTest.kt
git commit -m "feat(npc): show visit expiry in days beyond two days"
```

---

### Task 2: The visit view's header, moved-slot and contact fields

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt:14-68`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt:64-155`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt`

**Interfaces:**
- Produces: `VisitPage.leadWhen: String?`, `VisitPage.slotChanged: String?`, `VisitPage.agentPhone: String?`, `VisitPage.agentPhoneDigits: String?`, `VisitPage.noShowFrom: String?`. Each is already formatted in the tenant's zone; `null` when the state has no such fact.
- Consumes: `VisitRules.whenLabel(at, today, tenant, formatter)`, `VisitSlots.dateTimeLabel(...)`, `VisitRules.noShowFrom(visit)`, the existing private `displayPhone(e164)`.

- [ ] **Step 1: Write the failing tests**

Add to `VisitViewsTest`:

```kotlin
@Test
fun `lead when for a request`() {
    val page = of(VisitFixtures.requested())
    assertEquals("sam. 7 novembre 2026 à 10:30", page.leadWhen)
    assertNull(page.slotChanged)
    assertNull(page.agentPhone)
}

@Test
fun `lead when and moved slot for a confirmed visit`() {
    val page = of(VisitFixtures.confirmed().copy(scheduledAt = VisitFixtures.date("2026-11-07T10:00:00Z")))
    assertEquals("sam. 7 novembre 2026 à 11:00", page.leadWhen)
    assertEquals(
        "Créneau modifié : la demande portait sur le sam. 7 novembre 2026 à 10:30. " +
            "Le client a été prévenu par e-mail.",
        page.slotChanged,
    )
}

@Test
fun `no moved slot when the times are equal`() {
    assertNull(of(VisitFixtures.confirmed()).slotChanged)
}

@Test
fun `lead when for a window is today`() {
    assertEquals("Aujourd'hui · 10:30", of(VisitFixtures.window()).leadWhen)
}

@Test
fun `a finished visit has no lead when`() {
    assertNull(of(VisitFixtures.checkedIn()).leadWhen)
}

@Test
fun `the listing agent's phone is formatted`() {
    val page = of(agent = VisitFixtures.OTHER_AGENT)
    assertEquals("+237 6 99 00 00 02", page.agentPhone)
    assertEquals("+237699000002", page.agentPhoneDigits)
}

@Test
fun `the no-show time is formatted`() {
    assertEquals("jeu. 5 novembre 2026 à 10:45", of(VisitFixtures.window()).noShowFrom)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitViewsTest`
Expected: FAIL — unresolved reference `leadWhen` / `slotChanged` / `agentPhone` / `agentPhoneDigits` / `noShowFrom`.

- [ ] **Step 3: Add the fields to `VisitPage`**

After `expiresIn` add:

```kotlin
/** The date band under the status: the request's slot, the scheduled one, or "Aujourd'hui · HH:MM" in the window. */
val leadWhen: String?,
/** "Créneau modifié : …" when the agent moved the slot; null when it equals the request. */
val slotChanged: String?,
/** The listing agent's number, formatted for display; null on own inventory. */
val agentPhone: String?,
/** The listing agent's raw E.164 number for `tel:`; null on own inventory. */
val agentPhoneDigits: String?,
/** When the no-show becomes available, formatted; null without a scheduled slot. */
val noShowFrom: String?,
```

- [ ] **Step 4: Compute them in `VisitViews.of`**

Before the `VisitPage(` constructor:

```kotlin
val today = VisitSlots.localDate(Date(now), tenant)
val leadWhen = when (panel) {
    VisitRules.Panel.REQUESTED -> VisitSlots.dateTimeLabel(visit.requestedAt, tenant, formatter)
    VisitRules.Panel.CONFIRMED -> visit.scheduledAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) }
    VisitRules.Panel.WINDOW -> visit.scheduledAt?.let { VisitRules.whenLabel(it, today, tenant, formatter) }
    else -> null
}
val slotChanged = if (
    (panel == VisitRules.Panel.CONFIRMED || panel == VisitRules.Panel.WINDOW) &&
    visit.scheduledAt != null && visit.scheduledAt.time != visit.requestedAt.time
) {
    "Créneau modifié : la demande portait sur le " +
        VisitSlots.dateTimeLabel(visit.requestedAt, tenant, formatter) +
        ". Le client a été prévenu par e-mail."
} else {
    null
}
val agentNumber = agent?.whatsappNumber?.trim()?.ifEmpty { null }
val agentPhone = agentNumber?.let { displayPhone(it) }
val noShowFrom = VisitRules.noShowFrom(visit)?.let { VisitSlots.dateTimeLabel(Date(it), tenant, formatter) }
```

Pass `leadWhen = leadWhen, slotChanged = slotChanged, agentPhone = agentPhone, agentPhoneDigits = agentNumber, noShowFrom = noShowFrom` in the constructor.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitViewsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt
git commit -m "feat(npc): add the visit page's header and contact view fields"
```

---

### Task 3: The header date and the two labelled contacts

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `p.leadWhen`, `p.slotChanged`, `p.customerName`, `p.phone`, `p.phoneDigits`, `p.agent`, `p.agentPhone`, `p.agentPhoneDigits`.
- Produces: the DOM the later tasks build on: `.visit-when--lead`, `.visit-when__changed`, `.visit-contact`, `.visit-contact__person`, `.visit-contact__role`, `.visit-contact__name`, `.visit-contact__phone`, `.visit-contact__whatsapp`.

- [ ] **Step 1: Write the failing tests**

Add to `VisitTemplateTest`:

```kotlin
@Test
fun `header names the client and the date`() {
    val page = render(requested())

    val lead = page.selectFirst(".visit-when--lead")!!
    assertEquals("sam. 7 novembre 2026 à 10:30", lead.text())
    val contacts = page.select(".visit-contact__person")
    assertEquals(1, contacts.size)
    assertEquals("Client", contacts[0].selectFirst(".visit-contact__role")?.text())
    assertEquals("Sandrine Eboa", contacts[0].selectFirst(".visit-contact__name")?.text())
    assertEquals("tel:+237699887766", contacts[0].selectFirst("[data-phone-link]")?.attr("href"))
    assertEquals(
        "https://wa.me/237699887766",
        contacts[0].selectFirst(".visit-contact__whatsapp")?.attr("href"),
    )
}

@Test
fun `header names both people on a co-brokerage visit`() {
    doReturn(LISTING.copy(agentId = 30)).whenever(listingClient).get(3012)
    doReturn(VisitFixtures.OTHER_AGENT).whenever(agentClient).get(30L)

    val page = render(get("/visits/7001"))
    val contacts = page.select(".visit-contact__person")

    assertEquals(2, contacts.size)
    assertEquals("Client", contacts[0].selectFirst(".visit-contact__role")?.text())
    assertEquals("Agent du bien", contacts[1].selectFirst(".visit-contact__role")?.text())
    assertEquals("Clarisse Mbarga", contacts[1].selectFirst(".visit-contact__name")?.text())
    assertEquals(
        "/agent?id=30&back=/visits/7001",
        contacts[1].selectFirst(".visit-contact__name a")?.attr("href"),
    )
    assertEquals("tel:+237699000002", contacts[1].selectFirst("[data-phone-link]")?.attr("href"))
    assertEquals(
        "https://wa.me/237699000002",
        contacts[1].selectFirst(".visit-contact__whatsapp")?.attr("href"),
    )
    assertEquals("+237 6 99 00 00 02", contacts[1].selectFirst(".visit-contact__phone a")?.text())
    assertNull(page.selectFirst("[data-visit-agent]"))
    assertFalse(page.text().contains("Voir sa fiche"))
}

@Test
fun `own-inventory visit has no agent contact`() {
    assertEquals(1, render(get("/visits/7001")).select(".visit-contact__person").size)
}

@Test
fun `a moved slot is stated in the date band`() {
    doReturn(confirmed().copy(scheduledAt = date("2026-11-07T10:00:00Z")))
        .whenever(visitClient).get(7001)

    val page = render(get("/visits/7001"))

    assertTrue(page.selectFirst(".visit-when__changed")!!.text()
        .startsWith("Créneau modifié : la demande portait sur le sam. 7 novembre 2026 à 10:30."))
}
```

Update the existing `requested panel` test: keep the close assertions; add nothing there. Update `confirmed panel`: replace `assertTrue(panel.text().contains("sam. 7 novembre 2026 à 09:30"))` with `assertEquals("sam. 7 novembre 2026 à 10:30", page.selectFirst(".visit-when--lead")?.text())`. Replace the old co-brokerage test's `[data-visit-agent]` assertions with the new contact test above; delete the old one.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL — no `.visit-contact` / `.visit-when--lead`.

- [ ] **Step 3: Port the header markup**

In `templates/visit.html`, copy the prototype's `.visit-when--lead` block and `.visit-contact` block from `src/main/html/visit.html`, replacing the `data-v` hooks with Thymeleaf:

- Date band: `<p class="visit-when visit-when--lead" th:if="${p.leadWhen != null}">`, `th:text="${p.leadWhen}"`, and `<span class="visit-when__changed" th:if="${p.slotChanged != null}" th:text="${p.slotChanged}"></span>`.
- Client: `th:text="${p.customerName}"`, `th:text="${p.phone}"`, `th:href="|tel:+${p.phoneDigits}|"`, `th:href="|https://wa.me/${p.phoneDigits}|"`; the hidden prefix `Écrire à ${p.customerName} sur ` and visible `WhatsApp`.
- Agent: `th:if="${p.agent != null}"`, `th:text="${p.agent.name}"`, `th:href="${p.agent.profileUrl}"` on the name link, `th:text="${p.agentPhone}"`, `th:href="|tel:${p.agentPhoneDigits}|"`, `th:href="${p.agent.whatsappUrl}"`; hidden prefix `Écrire à ${p.agent.name} sur ` and visible `WhatsApp`.
- Add `data-phone-link` to both number links and `data-header-whatsapp` to the client's WhatsApp button (the video task will use it).
- Remove the old `.contact-card__line` phone row and the whole `section` that renders the listing agent ("Visite organisée avec").
- Remove the REQUESTED panel's `visit-when` date line and its "…, le" wording; leave "… souhaite visiter le bien **sur place**."
- Remove the CONFIRMED panel's visible `h2`/date block; keep the `h2` with `class="visually-hidden"` for its `aria-labelledby`.
- Remove the WINDOW panel's `visit-when` date line.
- Add `Vous n'avez rien à faire d'ici là.` with the clock icon after the CONFIRMED hint.

- [ ] **Step 4: Port the styles**

Add to `public/assets/css/visit.css` the prototype's `.visit-when--lead`, `.visit-when__changed`, `.visit-contact*`, `.visit-contact__whatsapp` and `.visit-close__trigger` rules from `src/main/html/assets/css/visit.css`, with its 480px padding breakpoint. Keep the production file's token names.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/visit.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt
git commit -m "feat(npc): name both contacts and lead with the visit date"
```

---

### Task 4: Quiet closures and the no-JS problem chooser

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt:59-79,160-180`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js:110-172`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt:611-660`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt:190-340`

**Interfaces:**
- Produces: `VisitController.CLOSE_PROBLEM = "problem"`; `closeFor` accepts it only on WINDOW. DOM: `[data-problem-toggle]`, `[data-problem-options]`, `[data-problem-noshow]`.
- Consumes: the existing `CLOSE`, `CLOSE_NO_SHOW`, `CLOSE_CANCEL` model values and the `visit.js` `open`/`close` helpers.

- [ ] **Step 1: Write the failing controller tests**

Add to `VisitControllerTest`:

```kotlin
@Test
fun `close problem on a window`() {
    doReturn(window()).whenever(visitClient).get(7001)
    val result = mvc.perform(get("/visits/7001").param("close", "problem")).andReturn()
    assertEquals(VisitController.CLOSE_PROBLEM, model(result)[VisitController.CLOSE])
}

@Test
fun `close problem is ignored outside a window`() {
    val result = mvc.perform(get("/visits/7001").param("close", "problem")).andReturn()
    assertNull(model(result)[VisitController.CLOSE])
}
```

- [ ] **Step 2: Write the failing template tests**

Add to `VisitTemplateTest`:

```kotlin
@Test
fun `requested close is a quiet link`() {
    val close = panel(render(requested()), "REQUESTED")!!.selectFirst("[data-visit-close]")!!
    val opener = close.selectFirst("[data-close-open=refuse]")!!
    assertEquals("A", opener.tagName().uppercase())
    assertEquals("/visits/7001?close=refuse#close-title-refuse", opener.attr("href"))
    assertEquals("link-button", opener.className())
}

@Test
fun `window problem chooser opens with close=problem`() {
    doReturn(window()).whenever(visitClient).get(7001)
    val page = render(get("/visits/7001?close=problem"))
    val close = panel(page, "WINDOW")!!.selectFirst("[data-visit-close]")!!
    val trigger = close.selectFirst("[data-problem-toggle]")!!

    assertEquals("true", trigger.attr("aria-expanded"))
    assertFalse(close.selectFirst("[data-problem-options]")!!.hasAttr("hidden"))
    assertEquals(
        "/visits/7001?close=no-show#close-title-noshow",
        close.selectFirst("[data-close-open=no-show]")?.attr("href"),
    )
    assertEquals(
        "/visits/7001?close=cancel#close-title-cancel",
        close.selectFirst("[data-close-open=cancel]")?.attr("href"),
    )
}

@Test
fun `the problem chooser has no absent choice before the no-show time`() {
    doReturn(window().copy(scheduledAt = date("2026-11-05T09:50:00Z"))).whenever(visitClient).get(7001)
    val panel = panel(render(get("/visits/7001?close=problem")), "WINDOW")!!
    val close = panel.selectFirst("[data-visit-close]")!!

    assertNull(close.selectFirst("[data-close-open=no-show]"))
    assertNotNull(close.selectFirst("[data-close-open=cancel]"))
    assertTrue(panel.text().contains("15 min après l'heure prévue"))
}
```

Update the existing `window - before the no-show` test: it currently asserts `[data-close-form=no-show]` is null; keep. It will find the new cancel choice. Replace its `assertNull(panel.selectFirst("[data-close-open=no-show]"))` with the new test's assertion (already null).

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest,VisitTemplateTest`
Expected: FAIL — `CLOSE_PROBLEM` unresolved; no `[data-problem-toggle]`.

- [ ] **Step 4: Implement `close=problem`**

In `VisitController`: add `const val CLOSE_PROBLEM = "problem"` beside the other close constants, and in `closeFor`:

```kotlin
CLOSE_PROBLEM -> close.takeIf { page.panel == VisitRules.Panel.WINDOW }
```

Keep the other branches unchanged.

- [ ] **Step 5: Render the quiet links and the chooser**

In `templates/visit.html`:

- REQUESTED: replace the danger button with `<a class="link-button" th:href="@{|/visits/${p.id}?close=refuse#close-title-refuse|}" data-close-open="refuse" aria-controls="close-form-refuse" th:attr="aria-expanded=${close == 'refuse'} ? 'true' : 'false'">Refuser la demande…</a>` inside the `data-close-buttons` container.
- CONFIRMED: the same with `close=cancel`, `Annuler la visite…`, `aria-controls="close-form-cancel"`.
- WINDOW: replace the two danger links with the chooser:
  - `<div class="visit-problem" data-close-buttons th:attr="hidden=${close == 'cancel' or close == 'no-show'} ? 'hidden' : null">`
  - trigger `<a class="btn btn--outline visit-problem__toggle" th:href="@{|/visits/${p.id}?close=problem#visit-problem-options|}" data-problem-toggle aria-controls="visit-problem-options" th:attr="aria-expanded=${close == 'problem'} ? 'true' : 'false'">Un problème avec cette visite&nbsp;?</a>`
  - `<div class="visit-problem__options" id="visit-problem-options" th:attr="hidden=${close == 'problem'} ? null : 'hidden'">`
  - no-show choice `th:if="${p.canNoShow}"` linking to `?close=no-show#close-title-noshow`, with the consequence "L'absence ou le refus du code sera enregistré avec votre position."
  - cancel choice linking to `?close=cancel#close-title-cancel`, with "La visite sera annulée ; le client est prévenu par e-mail."
- Keep the existing `data-close-form=no-show|cancel` forms below; they are still hidden by the `close` value.
- Change the no-show form's warning title to "L'absence du client sera enregistrée avec votre position".
- Add the pre-gate line above the chooser: `<p class="visit-later" th:if="${!p.canNoShow and p.noShowFrom != null}">… À partir de <strong th:text="${p.noShowFrom}"></strong>, 15 min après l'heure prévue, vous pourrez le déclarer absent.</p>`
- Remove the now-unused `.visit-close__buttons` wrapper from the three panels (keep the class only if another template uses it; it does not). Remove its CSS rule.

- [ ] **Step 6: Enhance the chooser in `visit.js`**

Inside the `[data-visit-close]` loop, after `var opener = null;`, add:

```javascript
var problemToggle = $("[data-problem-toggle]", box);
var problemOptions = $("[data-problem-options]", box);
if (problemToggle && problemOptions) {
    problemToggle.addEventListener("click", function (event) {
        event.preventDefault();
        var open = problemToggle.getAttribute("aria-expanded") === "true";
        problemToggle.setAttribute("aria-expanded", open ? "false" : "true");
        problemOptions.hidden = open;
    });
}
```

In `close()`, before restoring `buttons.hidden = false`, add:

```javascript
if (problemToggle && problemOptions) {
    problemToggle.setAttribute("aria-expanded", "false");
    problemOptions.hidden = true;
}
```

- [ ] **Step 7: Port the problem styles**

Copy `.visit-close__trigger`, `.visit-problem`, `.visit-problem__toggle`, `.visit-problem__options`, `.visit-problem__choice`, `.visit-problem__option`, `.visit-problem__consequence` and the 640px toggle rule from the prototype's `visit.css`.

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest,VisitTemplateTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt \
  modules/ndopify-partner-central/src/main/resources/templates/visit.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt
git commit -m "feat(npc): quiet the visit closures behind a no-JS problem chooser"
```

---

### Task 5: The video flow, the code lock and the copy

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `p.panel`, `p.type`, `p.customerFirstName`, `p.noShowFrom`, `p.canNoShow`, `checkInLocked`.
- Produces: `[data-header-whatsapp]`, `#code-attempts`, the video steps list.

- [ ] **Step 1: Write the failing tests**

Add to `VisitTemplateTest`:

```kotlin
@Test
fun `a video window hides the header whatsapp`() {
    doReturn(window().copy(type = VisitType.VIDEO)).whenever(visitClient).get(7001)
    val page = render(get("/visits/7001"))

    assertNull(page.selectFirst("[data-header-whatsapp]"))
    assertTrue(page.selectFirst("[data-panel=WINDOW]")!!.text().contains("Ouvrir WhatsApp"))
    assertEquals(3, page.select("[data-panel=WINDOW] .visit-call__steps li").size)
}

@Test
fun `an on-site window keeps the header whatsapp`() {
    doReturn(window()).whenever(visitClient).get(7001)
    assertNotNull(render(get("/visits/7001")).selectFirst("[data-header-whatsapp]"))
}

@Test
fun `the code field states the attempt limit`() {
    doReturn(window()).whenever(visitClient).get(7001)
    val panel = panel(render(get("/visits/7001")), "WINDOW")!!
    val code = panel.selectFirst("input[name=code]")!!

    assertEquals("", code.attr("placeholder"))
    assertTrue(code.attr("aria-describedby").contains("code-attempts"))
    assertTrue(panel.text().contains("5 essais maximum"))
}

@Test
fun `the locked code offers the support and a conditional no-show`() {
    doReturn(window()).whenever(visitClient).get(7001)
    val session = MockHttpSession().apply { setAttribute("npc.visit.code-locked.7001", true) }

    val panel = panel(render(get("/visits/7001").session(session)), "WINDOW")!!

    assertTrue(panel.text().contains("Code bloqué après 5 essais"))
    assertTrue(
        panel.selectFirst("a[href^=mailto:contact@ndopify.com]")!!.attr("href")
            .startsWith("mailto:contact@ndopify.com"),
    )
    assertEquals("/visits/7001?close=no-show#close-title-noshow",
        panel.selectFirst("[data-locked-noshow]")?.attr("href"))
}

@Test
fun `the no-show warning is future tense`() {
    doReturn(window()).whenever(visitClient).get(7001)
    val panel = panel(render(get("/visits/7001?close=no-show")), "WINDOW")!!
    assertTrue(panel.text().contains("L'absence du client sera enregistrée avec votre position"))
}
```

Also assert the schedule hint in `requested panel`:

```kotlin
assertTrue(panel.text().contains("le client sera prévenu par e-mail"))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL — header WhatsApp still present on video; no `code-attempts`; no mailto.

- [ ] **Step 3: Implement the template changes**

- Header client WhatsApp: add `data-header-whatsapp` and `th:if="${!(p.panel.name() == 'WINDOW' and p.type.name() == 'VIDEO')}"`.
- Video call: button text `Ouvrir WhatsApp` + visually hidden ` avec ${p.customerFirstName}`; replace the two paragraphs with `<ol class="visit-call__steps"><li>Ouvrez la discussion WhatsApp ci-dessus.</li><li>Touchez l'icône vidéo en haut de la discussion.</li><li>Demandez-lui son code et montrez-lui le bien.</li></ol>`.
- Code hint: add `<p class="field__optional" id="code-attempts">5 essais maximum&nbsp;: au-delà, le code est bloqué et la visite ne peut plus démarrer.</p>`; remove `placeholder="0000"`; set `aria-describedby="code-hint code-attempts code-error"`.
- Locked alert: title "Code bloqué après 5 essais."; `<p>Le code ne peut plus être saisi pour cette visite. <a th:href="@{mailto:contact@ndopify.com(subject=${'Visite ' + p.id + ' - code bloqué'})}">Contacter le support Ndopify</a> pour la débloquer.</p>`; the no-show link `th:if="${p.canNoShow}"`; otherwise "Si le client est absent, vous pourrez le signaler à partir de `${p.noShowFrom}`."
- No-show warning: "L'absence du client sera enregistrée avec votre position".
- Schedule hint: "Vous pouvez décaler le créneau ou changer le type : le client sera prévenu par e-mail."

- [ ] **Step 4: Port the video styles**

Add `.visit-call__steps` and update `.visit-call__button` to `display: flex; width: 100%; margin-inline: auto; margin-bottom: var(--space-3)`, with `width: fit-content` from 640px, from the prototype's `visit.css`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/visit.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt
git commit -m "feat(npc): clarify the video flow and the locked code"
```

---

### Task 6: Full verification

**Files:** none.

- [ ] **Step 1: Run the module build**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS; ktlint, tests and JaCoCo pass.

- [ ] **Step 2: Check the prototype still matches the production decisions**

Run:

```bash
cd modules/ndopify-partner-central
python3 -m http.server 4321 --directory src/main/html
```

Open `http://localhost:4321/visit.html?id=7025`, `?id=7006`, `?id=7021`, `?id=7001` at 390px and 1280px. Confirm the date band, the two labelled contacts, the quiet links and the centered "Ouvrir WhatsApp". Stop the server (`pkill -f "http.server 4321"`). No production server is needed: the template tests cover the served page.

- [ ] **Step 3: Commit any test fix**

If the build needed a fix, commit it with `test(npc): …` or `fix(npc): …`. A clean tree needs no commit.

---

## Self-Review

- **Spec coverage:** all nine acceptance criteria map to Tasks 1-5; `noShowFrom` is covered in Task 2 and the pre-gate line in Task 4; the support link is Task 5; the moved-slot note is Task 2 and Task 3.
- **No-JS:** the problem chooser's server state is Task 4; the form links are the existing `?close=no-show|cancel`; the JS only toggles.
- **Type consistency:** `leadWhen`, `slotChanged`, `agentPhone`, `agentPhoneDigits`, `noShowFrom` are defined in Task 2 and consumed by those names in Tasks 3-5.
- **Review Focus:** each focus is pinned by a test in the task that owns the code.
