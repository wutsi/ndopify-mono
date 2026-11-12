# Visit Closure Actions in the BFF — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the visit page's refuse / cancel / no-show actions the same light-blue closure box and in-place form as the mandate page, without breaking the no-JS flow.

**Architecture:** `GET /visits/{id}?close=refuse|cancel|no-show` tells the controller which closure form to render open; the template groups the triggers in `.visit-close__buttons` and hides the forms; `visit.js` intercepts the triggers and *Retour* to toggle the same nodes in place.

**Tech Stack:** Kotlin / Spring MVC, Thymeleaf, vanilla JS, existing partner-central CSS tokens.

**Spec:** `docs/superpowers/specs/2026-11-12-visit-closure-actions-design.md`

## Global Constraints

- Every page works without JavaScript; `visit.js` only enhances. The `?close=` flow is the no-JS path.
- French UI; code, comments, logs and docs in English.
- Reuse existing tokens and `.mandate-panel__actions`; add no colours and no new components.
- POST endpoints, status rules and wording do not change.
- Tests use `kotlin.test` assertions and `mockito-kotlin` mocks.

## Review Focus

- A hand-edited `?close=` value on the wrong panel must be ignored, never rendered or 500.
- `?close=no-show` before `scheduledAt + 15 min` must be ignored even though the panel is `WINDOW`.
- The no-show trigger must stay absent (and `?close=no-show` ignored) before that time.
- With JS, *Retour* must restore the button box and move focus back to the trigger; it must not clear the captured position inputs.
- The locked-check-in link must open the no-show declaration without JS.

---

### Task 1: Commit the approved prototype

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/visit.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`

**Interfaces:**
- Consumes: the approved prototype in the main checkout.
- Produces: the same markup and classes the BFF task ports (`data-visit-close`, `data-close-buttons`,
  `data-close-open`, `data-close-form`, `data-close-back`, `.visit-close__buttons`, `.visit-close__form`).

- [ ] **Step 1: Copy the approved prototype files**

The approved prototype lives in the main checkout (uncommitted). Copy only these three files:

```bash
cp /Users/herve/Projects/ndopify-mono/modules/ndopify-partner-central/src/main/html/visit.html \
   modules/ndopify-partner-central/src/main/html/visit.html
cp /Users/herve/Projects/ndopify-mono/modules/ndopify-partner-central/src/main/html/assets/css/visit.css \
   modules/ndopify-partner-central/src/main/html/assets/css/visit.css
cp /Users/herve/Projects/ndopify-mono/modules/ndopify-partner-central/src/main/html/assets/js/visit.js \
   modules/ndopify-partner-central/src/main/html/assets/js/visit.js
```

- [ ] **Step 2: Check the diff**

Run: `git diff --check -- modules/ndopify-partner-central/src/main/html`
Expected: no output (no whitespace errors).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/visit.html \
        modules/ndopify-partner-central/src/main/html/assets/css/visit.css \
        modules/ndopify-partner-central/src/main/html/assets/js/visit.js
git commit -m "feat(npc): prototype visit closure actions"
```

---

### Task 2: Controller accepts `?close=` and validates it against the panel

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt`

**Interfaces:**
- Produces: model attribute `close` (`"refuse" | "cancel" | "no-show" | null`), consumed by the
  template in Task 3.
- Constants on `VisitController`: `CLOSE`, `CLOSE_REFUSE`, `CLOSE_CANCEL`, `CLOSE_NO_SHOW`.

- [ ] **Step 1: Write the failing controller tests**

Add to `VisitControllerTest` (import `com.wutsi.ndopify.npc.ui.visit.VisitFixtures.date` if needed):

```kotlin
@Test
fun `close refuse`() {
    val result = mvc.perform(get("/visits/7001").param("close", "refuse")).andReturn()

    assertEquals(VisitController.CLOSE_REFUSE, model(result)[VisitController.CLOSE])
}

@Test
fun `close ignored on the wrong panel`() {
    val result = mvc.perform(get("/visits/7001").param("close", "cancel")).andReturn()

    assertNull(model(result)[VisitController.CLOSE])
}

@Test
fun `close cancel on a window`() {
    doReturn(window()).whenever(visitClient).get(7001)

    val result = mvc.perform(get("/visits/7001").param("close", "cancel")).andReturn()

    assertEquals(VisitController.CLOSE_CANCEL, model(result)[VisitController.CLOSE])
}

@Test
fun `close no show`() {
    doReturn(window()).whenever(visitClient).get(7001)

    val result = mvc.perform(get("/visits/7001").param("close", "no-show")).andReturn()

    assertEquals(VisitController.CLOSE_NO_SHOW, model(result)[VisitController.CLOSE])
}

@Test
fun `close no show before the time is ignored`() {
    doReturn(window().copy(scheduledAt = date("2026-11-05T09:50:00Z"))).whenever(visitClient).get(7001)

    val result = mvc.perform(get("/visits/7001").param("close", "no-show")).andReturn()

    assertNull(model(result)[VisitController.CLOSE])
}

@Test
fun `unknown close is ignored`() {
    val result = mvc.perform(get("/visits/7001").param("close", "hack")).andReturn()

    assertNull(model(result)[VisitController.CLOSE])
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest`
Expected: FAIL — `Unresolved reference: CLOSE` / the model has no `close` key.

- [ ] **Step 3: Implement the controller**

In `VisitController.companion object`, add:

```kotlin
const val CLOSE = "close"
const val CLOSE_REFUSE = "refuse"
const val CLOSE_CANCEL = "cancel"
const val CLOSE_NO_SHOW = "no-show"
```

Change `show` to:

```kotlin
@GetMapping("/{id}")
fun show(
    @PathVariable id: String,
    @RequestParam(required = false) close: String?,
    model: Model,
    session: HttpSession,
): String {
    // ... unchanged body up to the view model

    val page = VisitViews.of(visit, customer, listing, locations, clock.millis(), tenant, formatter)
    model.addAttribute("page", page)
    model.addAttribute(CLOSE, closeFor(page, close))
    // ... unchanged
}
```

Add the private rule:

```kotlin
/** The closure form the page opens, only when the current panel offers it; a hand-edited value is ignored. */
private fun closeFor(page: VisitPage, close: String?): String? =
    when (close) {
        CLOSE_REFUSE -> close.takeIf {
            page.panel == VisitRules.Panel.REQUESTED && page.canCancel
        }
        CLOSE_CANCEL -> close.takeIf {
            (page.panel == VisitRules.Panel.CONFIRMED || page.panel == VisitRules.Panel.WINDOW) && page.canCancel
        }
        CLOSE_NO_SHOW -> close.takeIf {
            page.panel == VisitRules.Panel.WINDOW && page.canNoShow
        }
        else -> null
    }
```

Add the `@RequestParam` import if it is missing.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitControllerTest.kt
git commit -m "feat(npc): accept a visit closure action from the URL"
```

---

### Task 3: Template and CSS — the mandate-style closure box

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/visit.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `close` from Task 2.
- Produces: `[data-visit-close]` wrappers, `[data-close-buttons]`, `[data-close-open]`,
  `[data-close-form]`, `[data-close-back]`, `.visit-close__buttons`, `.visit-close__form`.

- [ ] **Step 1: Write the failing template tests**

Keep every existing assertion. Add the following assertions to the existing tests, then add the new
tests below them.

Add to `requested panel` (after its current assertions):

```kotlin
val close = panel.selectFirst("[data-visit-close]")!!
assertEquals("/visits/7001?close=refuse#close-title-refuse",
    close.selectFirst("[data-close-open=refuse]")?.attr("href"))
assertEquals("false", close.selectFirst("[data-close-open=refuse]")?.attr("aria-expanded"))
assertFalse(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
assertTrue(close.selectFirst("[data-close-form=refuse]")!!.hasAttr("hidden"))
assertEquals("/visits/7001/cancel", close.selectFirst("[data-close-form=refuse]")?.attr("action"))
assertEquals("/visits/7001", close.selectFirst("[data-close-form=refuse] [data-close-back]")?.attr("href"))
```

Add to `confirmed panel`:

```kotlin
val close = panel.selectFirst("[data-visit-close]")!!
assertEquals("/visits/7001?close=cancel#close-title-cancel",
    close.selectFirst("[data-close-open=cancel]")?.attr("href"))
assertTrue(close.selectFirst("[data-close-form=cancel]")!!.hasAttr("hidden"))
```

Add to `window panel`:

```kotlin
val close = panel.selectFirst("[data-visit-close]")!!
assertEquals("/visits/7001?close=no-show#close-title-noshow",
    close.selectFirst("[data-close-open=no-show]")?.attr("href"))
assertEquals("/visits/7001?close=cancel#close-title-cancel",
    close.selectFirst("[data-close-open=cancel]")?.attr("href"))
assertTrue(close.selectFirst("[data-close-form=no-show]")!!.hasAttr("hidden"))
assertTrue(close.selectFirst("[data-close-form=cancel]")!!.hasAttr("hidden"))
```

Add the new tests:

```kotlin
@Test
fun `requested close refuse`() {
    val page = render(get("/visits/7001?close=refuse"))
    val panel = panel(page, "REQUESTED")!!
    val close = panel.selectFirst("[data-visit-close]")!!

    assertTrue(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
    val form = close.selectFirst("[data-close-form=refuse]")!!
    assertFalse(form.hasAttr("hidden"))
    assertEquals("close-title-refuse", form.selectFirst("h2")?.id())
    assertEquals("true", close.selectFirst("[data-close-open=refuse]")?.attr("aria-expanded"))
}

@Test
fun `requested ignores another close`() {
    val page = render(get("/visits/7001?close=cancel"))
    val close = panel(page, "REQUESTED")!!.selectFirst("[data-visit-close]")!!

    assertFalse(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
    assertTrue(close.selectFirst("[data-close-form=refuse]")!!.hasAttr("hidden"))
}

@Test
fun `confirmed close cancel`() {
    val page = render(get("/visits/7001?close=cancel"))
    val close = panel(page, "CONFIRMED")!!.selectFirst("[data-visit-close]")!!

    assertTrue(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
    assertFalse(close.selectFirst("[data-close-form=cancel]")!!.hasAttr("hidden"))
    assertEquals("close-title-cancel", close.selectFirst("[data-close-form=cancel] h2")?.id())
}

@Test
fun `window close no show`() {
    val page = render(get("/visits/7001?close=no-show"))
    val close = panel(page, "WINDOW")!!.selectFirst("[data-visit-close]")!!

    assertTrue(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
    assertFalse(close.selectFirst("[data-close-form=no-show]")!!.hasAttr("hidden"))
    assertTrue(close.selectFirst("[data-close-form=cancel]")!!.hasAttr("hidden"))
}

@Test
fun `window close cancel`() {
    val page = render(get("/visits/7001?close=cancel"))
    val close = panel(page, "WINDOW")!!.selectFirst("[data-visit-close]")!!

    assertFalse(close.selectFirst("[data-close-form=cancel]")!!.hasAttr("hidden"))
    assertTrue(close.selectFirst("[data-close-form=no-show]")!!.hasAttr("hidden"))
}

@Test
fun `window - before the no-show ignores close no-show`() {
    doReturn(window().copy(scheduledAt = Date(VisitFixtures.NOW - 5 * 60_000)))
        .whenever(visitClient).get(7001)

    val page = render(get("/visits/7001?close=no-show"))
    val close = panel(page, "WINDOW")!!.selectFirst("[data-visit-close]")!!

    assertFalse(close.selectFirst("[data-close-buttons]")!!.hasAttr("hidden"))
    assertNull(close.selectFirst("[data-close-form=no-show]"))
}
```

Extend the existing `window - before the no-show` test with:

```kotlin
assertNull(panel.selectFirst("[data-close-open=no-show]"))
assertNull(panel.selectFirst("[data-close-form=no-show]"))
```

In `window - code locked`, add:

```kotlin
assertEquals("/visits/7001?close=no-show#close-title-noshow",
    panel.selectFirst("[data-locked-noshow]")?.attr("href"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL — `[data-visit-close]` is null (the template still uses `<details>`).

- [ ] **Step 3: Replace each closure block in `templates/visit.html`**

Each panel gets one wrapper. The form bodies are the existing ones, moved unchanged, with a new
`<h2 class="visit-form__title">` and a `.mandate-panel__actions` footer.

| Panel | Trigger label | `data-close-open` | href | Form `data-close-form` | Form id | Title id |
|---|---|---|---|---|---|---|
| `REQUESTED` | Refuser la demande | `refuse` | `/visits/{id}?close=refuse#close-title-refuse` | `refuse` | `close-form-refuse` | `close-title-refuse` |
| `CONFIRMED` | Annuler la visite | `cancel` | `/visits/{id}?close=cancel#close-title-cancel` | `cancel` | `close-form-cancel` | `close-title-cancel` |
| `WINDOW` | Le client est absent… | `no-show` | `/visits/{id}?close=no-show#close-title-noshow` | `no-show` | `close-form-no-show` | `close-title-noshow` |
| `WINDOW` | Vous ne pouvez pas venir ? | `cancel` | `/visits/{id}?close=cancel#close-title-cancel` | `cancel` | `close-form-cancel` | `close-title-cancel` |

Use this shape (REQUESTED shown; repeat for the table rows):

```html
<div class="visit-close" data-visit-close>
    <div class="mandate-panel__actions visit-close__buttons" data-close-buttons
         th:attr="hidden=${close} ? 'hidden' : null">
        <a class="btn btn--danger"
           th:href="@{|/visits/${p.id}?close=refuse#close-title-refuse|}"
           aria-controls="close-form-refuse" data-close-open="refuse"
           th:attr="aria-expanded=${close == 'refuse'} ? 'true' : 'false'">Refuser la demande</a>
    </div>
    <form id="close-form-refuse" class="form visit-close__form" method="post" novalidate
          th:action="@{/visits/{id}/cancel(id=${p.id})}"
          th:attr="hidden=${close == 'refuse'} ? null : 'hidden'"
          data-close-form="refuse" data-cancel-form>
        <h2 class="visit-form__title" th:id="${close == 'refuse'} ? 'close-title-refuse' : null">Refuser la demande</h2>
        <!-- the existing paragraph and reason field, unchanged -->
        <div class="mandate-panel__actions">
            <button type="submit" class="btn btn--danger">Refuser la demande</button>
            <a class="btn btn--outline" th:href="@{/visits/{id}(id=${p.id})}" data-close-back>Retour</a>
        </div>
    </form>
</div>
```

Panel-specific hidden conditions:

- Buttons box: `th:attr="hidden=${close} ? 'hidden' : null"`.
- REQUESTED form: `th:attr="hidden=${close == 'refuse'} ? null : 'hidden'"`.
- CONFIRMED form: `th:attr="hidden=${close == 'cancel'} ? null : 'hidden'"`.
- WINDOW no-show trigger and form: keep `th:if="${p.canNoShow}"`; form hidden unless
  `${close == 'no-show'}`. The no-show title is "Déclarer l'absence".
- WINDOW cancel form: hidden unless `${close == 'cancel'}`.
- Every trigger gets `aria-controls` pointing at its form id and the `aria-expanded` expression
  from the table.

Change the locked-check-in link to the no-JS URL:

```html
<a th:if="${p.canNoShow}" th:href="@{|/visits/${p.id}?close=no-show#close-title-noshow|}"
   data-locked-noshow>Déclarer le client absent</a>
```

- [ ] **Step 4: Replace the `.visit-secondary` CSS**

In `public/assets/css/visit.css`, replace the `.visit-secondary summary`,
`.visit-secondary__body`, and its media block with:

```css
/* The closure actions (refuse, cancel, no-show): the last, rare and final actions. Same as the mandate's
   `.mandate-close`: grouped in a light blue box, the buttons in the danger style; a form opens below on a white
   card, the box hidden until Retour brings the buttons back. */
.visit-close {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
}

.visit-close__buttons {
    justify-content: center;
    padding: var(--space-4);
    border: 1px solid var(--color-info-border);
    background: var(--color-info-bg);
}

.visit-close__form {
    padding: var(--space-4);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
}
```

`.visit-close__buttons` also carries `mandate-panel__actions` in the markup, so it keeps the
column-on-phones / row-from-640px layout defined in `home.css`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/visit.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt
git commit -m "feat(npc): style visit closure actions like the mandate close"
```

---

### Task 4: JavaScript enhancement

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js`

**Interfaces:**
- Consumes: the classes and data attributes from Task 3.

- [ ] **Step 1: Replace the `#no-show` block**

Delete the final block:

```js
/* ---------- The locked check-in's link opens the no-show ---------- */
var noShow = $("#no-show");
if (noShow && window.location.hash === "#no-show") noShow.open = true;
```

Add:

```js
/* ---------- The closure actions: the trigger opens its form in place, as ?close= does without JS ---------- */
$all("[data-visit-close]").forEach(function (box) {
    var buttons = $("[data-close-buttons]", box);
    var openers = $all("[data-close-open]", box);
    var opener = null;

    function formOf(mode) {
        return $('[data-close-form="' + mode + '"]', box);
    }

    function open(link) {
        opener = link;
        var mode = link.getAttribute("data-close-open");
        $all("[data-close-form]", box).forEach(function (form) {
            form.hidden = form.getAttribute("data-close-form") !== mode;
        });
        buttons.hidden = true;
        link.setAttribute("aria-expanded", "true");
        var first = $("input, textarea", formOf(mode));
        if (first) first.focus();
    }

    function close() {
        $all("[data-close-form]", box).forEach(function (form) {
            form.hidden = true;
        });
        buttons.hidden = false;
        openers.forEach(function (link) {
            link.setAttribute("aria-expanded", "false");
        });
        if (opener) opener.focus();
    }

    openers.forEach(function (link) {
        var form = formOf(link.getAttribute("data-close-open"));
        link.setAttribute("aria-expanded", form && !form.hidden ? "true" : "false");
        link.addEventListener("click", function (event) {
            event.preventDefault();
            open(link);
        });
    });

    $all("[data-close-back]", box).forEach(function (back) {
        back.addEventListener("click", function (event) {
            event.preventDefault();
            close();
        });
    });

    // Opened by the server (?close=...): focus its first field.
    $all("[data-close-form]", box).forEach(function (form) {
        if (form.hidden) return;
        opener = $('[data-close-open="' + form.getAttribute("data-close-form") + '"]', box);
        var first = $("input, textarea", form);
        if (first) first.focus();
    });
});
```

Do **not** call `form.reset()` in `close()`: the no-show form's hidden position inputs are captured
once on page load and must survive a *Retour*.

- [ ] **Step 2: Verify in a browser**

Run the BFF against its test data (or use the prototype markup with this script) and check:

- `/visits/7001?close=refuse`: the form is open on load, its textarea focused, the buttons box hidden; *Retour* restores the box and focuses *Refuser la demande*.
- Clicking *Refuser la demande* opens the form without a page load.
- `/visits/7003?close=cancel`: the cancel form is open on load.
- On a window visit, the no-show and cancel buttons share one box; opening one hides the other.
- Type a reason, click *Retour*, reopen: the text is still there. On the no-show form, the captured
  position inputs still have their values after *Retour*.
- The locked-check-in link reaches the no-show form.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/visit.js
git commit -m "feat(npc): toggle visit closure forms in place"
```

---

### Task 5: Docs, full verification and push

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`
- Already created: `docs/superpowers/specs/2026-11-12-visit-closure-actions-design.md`
- Already created: `docs/superpowers/plans/2026-11-12-visit-closure-actions-bff.md`

- [ ] **Step 1: Update the visit page note in `CLAUDE.md`**

Change the sentence that ends with "position, `#no-show`" to say the script also toggles the
closure box, and that `GET /visits/{id}?close=refuse|cancel|no-show` opens the chosen form without
JS (ignored when the panel doesn't offer it).

- [ ] **Step 2: Run the module's full test suite and ktlint**

Run:

```bash
cd modules/ndopify-partner-central && mvn test
```

Expected: BUILD SUCCESS, all tests pass. Ktlint runs in `validate`; if it fails, run
`mvn antrun:run@ktlint-format` and re-run.

- [ ] **Step 3: Check the branch diff**

Run:

```bash
git diff --check origin/main...HEAD
git status --short
git log --oneline origin/main..HEAD
```

Expected: no whitespace errors; only the planned files changed; the commits are the design/plan,
prototype, controller, template/CSS and JS commits.

- [ ] **Step 4: Commit the docs**

```bash
git add docs/superpowers/specs/2026-11-12-visit-closure-actions-design.md \
        docs/superpowers/plans/2026-11-12-visit-closure-actions-bff.md \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): record visit closure actions"
```

- [ ] **Step 5: Push the branch**

```bash
git push -u origin feat/npc-visit-closure-actions
```

Expected: the branch is on `origin`. Report the GitHub compare URL
`https://github.com/wutsi/ndopify-mono/compare/main...feat/npc-visit-closure-actions`.
