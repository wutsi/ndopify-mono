# Tracker Done Dot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A completed tracker step reads as a filled success-green dot with no icon, on the mandate and KYC pages.

**Architecture:** Remove the check `svg` from the two trackers' markup and replace it with a visually hidden "Terminé"; fill a done `.tracker__mark` with `--color-success-fg` in the shared stylesheet. The prototype (`src/main/html/`) is built and approved first, then the Thymeleaf templates, stylesheet and template tests follow.

**Tech Stack:** Thymeleaf + one shared CSS file, vanilla-JS static prototype, `kotlin.test` + jsoup template tests, Maven (ktlint / JaCoCo).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/210

## Global Constraints

- Module: `ndopify-partner-central` only. No `ndopify-dto` / `ndopify-web-core` change.
- Colour comes from the tier-2 token `var(--color-success-fg)` (`#155724`); no new hex value.
- The screen-reader text is exactly `Terminé`, in an element with `class="visually-hidden"`.
- `src/main/html/assets/css/partner-central.css` and `src/main/resources/public/assets/css/partner-central.css` stay in sync.
- `CHECK_MARK` in `src/main/html/assets/js/partner-central.js` stays: the match "fit" rows still use it.
- `src/main/html/` is the prototype: never served, never deleted or "cleaned up".
- UI text is French; code, comments and docs are English.
- Tests use `kotlin.test` assertions (`assertNull`, `assertEquals`), never JUnit's.
- `mvn verify` in `modules/ndopify-partner-central` is green (ktlint + tests + coverage).

## Review Focus

- A mandate cancelled before approval skips a step: only steps with a real `done` state get the fill and "Terminé"; the skipped step stays a hollow todo mark (Task 2's `cancelled` test).
- The KYC tracker's current and todo marks are untouched; only the done mark changes (Task 3's `pending status` test).
- The pages work without JavaScript: the markup and style are server-rendered (both template tests render with no script).
- The match "fit" rows keep their check icon: `CHECK_MARK` is still defined and used (Task 1 verification).
- Stale markup with an `svg` inside a done mark shows no icon (the defensive CSS rule, Task 2).

---

### Task 1: Prototype — the filled done dot on both trackers

**Files:**
- Modify: `src/main/html/assets/css/partner-central.css` (the `.tracker [data-state="done"] .tracker__mark` block)
- Modify: `src/main/html/assets/js/partner-central.js` (the `CHECK_MARK`/`SHORT_DATE_FORMAT` constants and the `render` function's step loop)
- Modify: `src/main/html/mandate.html:15,21` (cache-busting `?v=` on the stylesheet and script)
- Modify: `src/main/html/kyc.html:290` (the done tracker mark)
- Modify: `src/main/html/kyc/index.html:84` (the done tracker mark)

**Interfaces:**
- Consumes: nothing.
- Produces: the approved visual reference both served pages follow. `DONE_MARK` is a JS string constant used only by `initMandate`'s `render`.

- [ ] **Step 1: Fill a done mark in the prototype stylesheet**

In `src/main/html/assets/css/partner-central.css`, replace:

```css
.tracker [data-state="done"] .tracker__mark {
    border-color: var(--color-success-fg);
    color: var(--color-success-fg);
}
```

with:

```css
.tracker [data-state="done"] .tracker__mark {
    border-color: var(--color-success-fg);
    background: var(--color-success-fg);
}

/* A done step is a filled green dot: no check drawn inside. */
.tracker [data-state="done"] .tracker__mark svg {
    display: none;
}
```

- [ ] **Step 2: Replace the injected check with the hidden state text in the prototype JS**

In `src/main/html/assets/js/partner-central.js`, next to `CHECK_MARK`, add:

```javascript
    // A done tracker step: filled by CSS, the state is carried for screen readers only.
    var DONE_MARK = '<span class="visually-hidden">Terminé</span>';
```

In `initMandate`'s `render`, replace:

```javascript
                $(".tracker__mark", li).innerHTML = done ? CHECK_MARK : "";
```

with:

```javascript
                $(".tracker__mark", li).innerHTML = done ? DONE_MARK : "";
```

- [ ] **Step 3: Bump the prototype mandate page's cache-busters**

In `src/main/html/mandate.html`, set the `partner-central.css` and `partner-central.js` links to `?v=20261119a` (the stylesheet is `20261116a` and the script `20261118c` today).

- [ ] **Step 4: Update the KYC prototype trackers**

In `src/main/html/kyc.html` and `src/main/html/kyc/index.html`, replace the done mark's check `svg` with the hidden text, so the line reads:

```html
<li data-state="done"><span class="tracker__mark"><span class="visually-hidden">Terminé</span></span>Documents envoyés</li>
```

- [ ] **Step 5: Check the prototype renders the state correctly**

Run (with the Playwright headless shell if present; otherwise open the pages in a browser):

```bash
cd modules/ndopify-partner-central/src/main/html
~/Library/Caches/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-mac-arm64/chrome-headless-shell \
  --headless --disable-gpu --virtual-time-budget=4000 --dump-dom "file://$PWD/mandate.html?id=204" \
  | grep -A1 'data-state="done"'
```

Expected: each done `tracker__mark` contains only `<span class="visually-hidden">Terminé</span>` — no `<svg>`. Also confirm `grep -c "CHECK_MARK" src/main/html/assets/js/partner-central.js` is still at least 2 (constant defined + match fit rows), and open `src/main/html/kyc/index.html` to see the filled done dot there.

- [ ] **Step 6: Commit**

```bash
git add src/main/html/assets/css/partner-central.css src/main/html/assets/js/partner-central.js \
  src/main/html/mandate.html src/main/html/kyc.html src/main/html/kyc/index.html
git commit -m "feat(npc): prototype a done tracker step as a filled dot"
```

- [ ] **Step 7: Prototype gate**

STOP: show the prototype (`src/main/html/mandate.html?id=204`, `src/main/html/kyc/index.html`) to the user and wait for their OK before Task 2.

---

### Task 2: Served mandate page — filled done step, no icon

**Files:**
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt:188-189` (the `pending approval` test)
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt:634-649` (the `cancelled` test)
- Modify: `src/main/resources/templates/mandate.html:155` (the step's tracker mark)
- Modify: `src/main/resources/public/assets/css/partner-central.css` (the `.tracker [data-state="done"] .tracker__mark` block)

**Interfaces:**
- Consumes: the prototype's approved look (Task 1).
- Produces: a done `.tracker__mark` with no `svg` and a `.visually-hidden` "Terminé"; the shared CSS rule Task 3 relies on.

- [ ] **Step 1: Write the failing mandate assertions**

In `pending approval`, replace:

```kotlin
        assertNotNull(steps[0].selectFirst(".tracker__mark svg"))
        assertNull(steps[1].selectFirst(".tracker__mark svg"))
```

with:

```kotlin
        assertNull(steps[0].selectFirst(".tracker__mark svg"))
        assertEquals("Terminé", steps[0].selectFirst(".tracker__mark .visually-hidden")?.text())
        assertNull(steps[1].selectFirst(".tracker__mark svg"))
        assertNull(steps[1].selectFirst(".tracker__mark .visually-hidden"))
```

In `cancelled`, after the existing `assertNull(page.selectFirst("[data-end]"))`, add:

```kotlin
        val steps = page.select("[data-mandate-steps] li")
        assertEquals(listOf("done", "done", "todo", "done"), steps.map { it.attr("data-state") })
        assertNull(steps[2].selectFirst(".tracker__mark svg"))
        assertNull(steps[2].selectFirst(".tracker__mark .visually-hidden"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: FAIL — the done mark still renders the `svg` and no hidden text.

- [ ] **Step 3: Remove the icon from the mandate template**

In `src/main/resources/templates/mandate.html`, replace the mark line with:

```html
                            <span class="tracker__mark"><span class="visually-hidden" th:if="${step.done}">Terminé</span></span>
```

- [ ] **Step 4: Fill the done mark in the served stylesheet**

Apply the same block as Task 1 Step 1 to `src/main/resources/public/assets/css/partner-central.css`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/mandate.html src/main/resources/public/assets/css/partner-central.css \
  src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show a done mandate step as a filled dot"
```

---

### Task 3: Served KYC page — same done mark

**Files:**
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt:215-224` (the `pending status` test)
- Modify: `src/main/resources/templates/kyc.html:281` (the done tracker mark)

**Interfaces:**
- Consumes: the shared CSS rule from Task 2 (no new CSS).
- Produces: the KYC tracker's done mark matches the mandate's.

- [ ] **Step 1: Write the failing KYC assertions**

In `pending status`, after `assertNull(page.selectFirst(".progress"))`, add:

```kotlin
        val steps = page.select("[data-status=pending] .tracker li")
        assertEquals(listOf("done", "current", "todo"), steps.map { it.attr("data-state") })
        assertNull(steps[0].selectFirst(".tracker__mark svg"))
        assertEquals("Terminé", steps[0].selectFirst(".tracker__mark .visually-hidden")?.text())
        assertNull(steps[1].selectFirst(".tracker__mark .visually-hidden"))
        assertNull(steps[2].selectFirst(".tracker__mark .visually-hidden"))
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=KycTemplateTest`
Expected: FAIL — the done mark still renders the `svg`.

- [ ] **Step 3: Remove the icon from the KYC template**

In `src/main/resources/templates/kyc.html`, replace the done mark with:

```html
                        <li data-state="done"><span class="tracker__mark"><span class="visually-hidden">Terminé</span></span>Documents envoyés</li>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=KycTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/kyc.html \
  src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt
git commit -m "feat(npc): follow the filled done dot on the KYC tracker"
```

---

### Task 4: Full module verification

**Files:**
- No file change.

- [ ] **Step 1: Run the module's full build**

Run: `mvn verify` in `modules/ndopify-partner-central`.
Expected: BUILD SUCCESS — ktlint clean, every test green, coverage gate met.

- [ ] **Step 2: Confirm nothing else changed**

Run: `git status --short` and `git diff --stat origin/main`.
Expected: only the prototype, the two templates, the served stylesheet and the two template tests.
