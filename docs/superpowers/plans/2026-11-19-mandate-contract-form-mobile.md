# Mandate Contract Form on Phones Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the contract send form comfortable on phones: stack *Durée* and *Frais de visite* below 640px, and format the fee by thousands while typing, like the app's other amount fields.

**Architecture:** The layout is a mobile-first CSS change in `.mandate-terms__fields` (one column by default, two from 640px). The fee reuses the caret-preserving `bindAmount` already in `mandate.js`, loaded on the contract page and dispatched from its `data-page="mandate-contract"`. No DTO, server, web-core or database change; the form still works without JavaScript.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, plain HTML/CSS/JS, Thymeleaf template tests (`kotlin.test`, Jsoup), JaCoCo.

**Spec:** GitHub issue [#201](https://github.com/wutsi/ndopify-mono/issues/201).

## Global Constraints

- UI copy is French; code, comments and docs are English.
- Only `modules/ndopify-partner-central` changes: no `ndopify-dto`, `ndopify-server`, `ndopify-web-core` or database change.
- No prototype page is served; `src/main/html/` is the static prototype.
- Breakpoint: one field per line below 640px, side by side from 640px up.
- Reuse `mandate.js`'s amount helpers; do not add a second formatter.
- The page still works without JavaScript, and `MandateContractRules` keeps accepting `5000` and `5 000`.
- Assertions use `kotlin.test`; mocks use `mockito-kotlin`.
- `mvn verify` in `modules/ndopify-partner-central` is the final gate.
- Conventional commits, `Refs #201` in the body.

## Review Focus

1. **A fee the server sends back as invalid (`abc`, `5.5`) is not reformatted or cleared on load.** It stays as typed, next to its error; only digits-and-whitespace values are formatted.
2. **At 320px, nothing clips or overlaps:** the duration select, the fee input and its currency suffix, both hints, and both error messages.
3. **From 640px up the fields stay side by side.** The phone rule must not leak into the tablet/desktop layout.
4. **Typing in the middle of a formatted amount keeps the caret after the edited digit.** `bindAmount`'s caret logic is kept, not replaced by a jump to the end.
5. **Without JavaScript the form is unchanged:** it posts its raw digits and shows the server's errors; the new script is purely an enhancement.

---

### Task 1: Prototype (UI gate)

**Files:**
- Create: `modules/ndopify-partner-central/src/main/html/mandate/contract.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`

**Interfaces:**
- Consumes: the served page `modules/ndopify-partner-central/src/main/resources/templates/mandate/contract.html` (copy, wording and field markup) and the prototype header/user menu in `src/main/html/mandate/close.html`.
- Produces: the approved prototype the served page mirrors — `.mandate-terms__fields` one column below 640px, and a `#visitFee` that formats as typed.

- [ ] **Step 1: Create the prototype page**

Create `modules/ndopify-partner-central/src/main/html/mandate/contract.html` from the served template
`src/main/resources/templates/mandate/contract.html`, with these substitutions:

- `<html lang="fr">` without the `xmlns:th` namespace; remove every `th:*` attribute.
- Asset paths relative to the page: `../assets/...` for the icon, apple-touch-icon, CSS and script.
- Link `../assets/css/partner-central.css`, `../assets/css/find-property.css` and `../assets/css/home.css`, in that
  order; load `../assets/js/partner-central.js` (deferred) only.
- Copy the `<header class="site-header">` block from `src/main/html/mandate/close.html` (the user menu included).
- The back link goes to `../mandate.html?id=201` and reads `← Retour au mandat`.
- Static values: `data-page="mandate-contract"`; the form has no `action`; `months` 1–12 with `6` selected;
  `visitFee` value `0`; the currency suffix `FCFA`; the e-mail `ray@gmail.com`; the signature deadline `7` (the
  hint's "Passé le délai de 7 jours…"); the button reads `Envoyer au client`.
- Keep the page comment from the served template, prefixed by two lines:

```html
<!--
    PROTOTYPE: the contract send page (story #201), reached from the mandate page's "Envoyer au client".
    Static copy of templates/mandate/contract.html: ?id= is ignored; the fee formats as typed.
-->
```

- [ ] **Step 2: Add the phone layout to the prototype CSS**

In `src/main/html/assets/css/home.css`, add after `.mandate-panel__actions .btn` (around line 770):

```css
/* The contract's terms: one field per line on phones, the duration then the visit fee. */
.mandate-terms__fields {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-3);
}

@media (min-width: 640px) {
    /* The two terms on one line, the fee a little wider for its amount and suffix. */
    .mandate-terms__fields {
        grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
    }
}

/* The label already says "Frais de visite": the suffix is the currency alone, leaving the amount its room. */
.mandate-terms__fields .input-unit .input {
    padding-right: 4.5rem;
}
```

- [ ] **Step 3: Add the fee formatter to the prototype script**

In `src/main/html/assets/js/partner-central.js`, next to `parseAmount` (around line 3576), add:

```js
var MAX_AMOUNT_DIGITS = 12;

function digitsOf(value) {
    return String(value || "").replace(/\D/g, "").replace(/^0+(?=\d)/, "");
}

// "5000" → "5 000": the digits grouped by thousands, capped like the server's amount.
function formatAmount(value) {
    var digits = digitsOf(value).slice(0, MAX_AMOUNT_DIGITS);
    return digits ? Number(digits).toLocaleString("fr-FR") : "";
}

// Regroups an amount's digits by thousands while typing, keeping the caret after the same digit.
// A value made of anything else ("abc", "5.5") is left for the server to refuse, as typed.
function bindAmount(input) {
    if (!input) return;
    if (/^[\d\s\u00a0\u202f]*$/.test(input.value)) input.value = formatAmount(input.value);
    input.addEventListener("input", function () {
        var caretDigits = digitsOf(input.value.slice(0, input.selectionStart)).length;
        var formatted = formatAmount(input.value);
        var pos = 0;
        for (var seen = 0; pos < formatted.length && seen < caretDigits; pos++) {
            if (/\d/.test(formatted.charAt(pos))) seen++;
        }
        input.value = formatted;
        input.setSelectionRange(pos, pos);
    });
}
```

Before the boot section (around line 5250), add:

```js
/* ---------- mandate/contract.html ---------- */

// The visit fee: "5000" becomes "5 000" as it is typed, like the closing page's rent.
function initMandateContract() {
    bindAmount($("#visitFee"));
}
```

Register it in the `pages` map (around line 5271), after `"mandate-close": initMandateClose,`:

```js
"mandate-contract": initMandateContract,
```

- [ ] **Step 4: Verify the prototype at three widths**

From `modules/ndopify-partner-central/src/main/html`:

```bash
python3 -m http.server 8099
```

Open `http://localhost:8099/mandate/contract.html` and check:

- 360px and 320px: *Durée* on its own line, *Frais de visite* on the next, full width; no label, hint, suffix or
  error clipped; the two fields have the same width.
- 1280px: the two fields side by side, the fee column wider, as today.
- Type `5000`: it becomes `5 000`; edit a middle digit and confirm the caret stays after that digit.

- [ ] **Step 5: Stop for the prototype gate**

Show the user `http://localhost:8099/mandate/contract.html` (or screenshots at 360px and 1280px) and the three
prototype files. Do not edit the served template, script or CSS before the user's OK on the prototype and this plan.
If their feedback changes the design, update this plan, commit and push it to `main`
(`docs(mandate): update the contract form mobile plan`), then show the prototype again.

- [ ] **Step 6: Commit the approved prototype**

```bash
git add modules/ndopify-partner-central/src/main/html/mandate/contract.html \
  modules/ndopify-partner-central/src/main/html/assets/css/home.css \
  modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js
git commit -m "feat(npc): prototype the phone-friendly contract form" -m "Refs #201"
```

---

### Task 2: Stack the fields on phones (served page)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css:847-857`

**Interfaces:**
- Consumes: Task 1's approved `.mandate-terms__fields` rules.
- Produces: the served page's one-column phone layout; no markup or class change.

- [ ] **Step 1: Replace the two-column base rule**

In `src/main/resources/public/assets/css/home.css`, replace the `.mandate-terms__fields` block and its comment with
the same block as the prototype (Task 1, Step 2). The base rule is one column; the `@media (min-width: 640px)` rule
restores the two columns. Keep the `.input-unit .input` padding rule unchanged.

- [ ] **Step 2: Check the served and prototype rules match**

```bash
cd /Users/herve/Projects/ndopify-mono
diff <(sed -n '/The contract.s terms/,/padding-right: 4.5rem/p' \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css) \
  <(sed -n '/The contract.s terms/,/padding-right: 4.5rem/p' \
  modules/ndopify-partner-central/src/main/html/assets/css/home.css)
```

Expected: no output. The comment wording may differ only if the prototype was changed at the gate.

- [ ] **Step 3: Run the module checks and commit**

```bash
cd modules/ndopify-partner-central
mvn verify
```

Expected: BUILD SUCCESS (no test covers CSS; this proves nothing else broke).

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css
git commit -m "feat(npc): stack the contract terms on phones" -m "Refs #201"
```

---

### Task 3: Format the visit fee as typed (served page)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/contract.html:18`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js:1-18,85-99,483-488,553-559`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractTemplateTest.kt:100-108`

**Interfaces:**
- Consumes: Task 1's `bindAmount` behaviour (caret-preserving, initial-value guard).
- Produces: `initMandateContract()` in `mandate.js`, dispatched on `data-page="mandate-contract"`; the page loads
  `/assets/js/mandate.js` after `/assets/js/partner-central.js`.

- [ ] **Step 1: Update the template test first**

In `MandateContractTemplateTest`, rename `the page loads no page script` to
`` `the page loads the mandate script` `` and change its assertion to:

```kotlin
assertEquals(
    listOf("/assets/js/partner-central.js", "/assets/js/mandate.js"),
    page.select("script[src]").map { it.attr("src") },
)
assertEquals("mandate-contract", page.body().attr("data-page"))
```

Keep the existing `abc` retention assertion in `the errors show under their fields` unchanged: it is the
server-side half of Review Focus 1.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=MandateContractTemplateTest
```

Expected: FAIL — the rendered page still lists only `/assets/js/partner-central.js`.

- [ ] **Step 3: Load the script on the page**

In `src/main/resources/templates/mandate/contract.html`, after the `partner-central.js` line (line 18), add:

```html
<script th:src="@{/assets/js/mandate.js}" defer></script>
```

- [ ] **Step 4: Wire the initializer and guard the initial formatting**

In `src/main/resources/public/assets/js/mandate.js`:

- In the header comment (lines 1–18), add the contract page to the pages it covers and a bullet:
  `` - contract page: the visit fee formatted as typed, like the closing page's rent.``
- In `bindAmount` (lines 85–99), format the initial value only when it is made of digits and whitespace:

```js
// A value the server sent back as invalid ("abc", "5.5") stays as typed: its error is about that value.
if (/^[\d\s\u00a0\u202f]*$/.test(input.value)) input.value = formatAmount(input.value);
```

replacing the current unconditional `input.value = formatAmount(input.value);` line.

- After `initMandateClose` (around line 488), add:

```js
/* ---------- mandate/contract.html ---------- */

// The visit fee: "5000" becomes "5 000" as it is typed, like the closing page's rent.
function initMandateContract() {
    bindAmount($("#visitFee"));
}
```

- In the `DOMContentLoaded` dispatch (around line 553), add:

```js
if (page === "mandate-contract") initMandateContract();
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=MandateContractTemplateTest
```

Expected: PASS.

- [ ] **Step 6: Check the formatter in a browser**

Serve the prototype (`python3 -m http.server 8099` from `src/main/html`) and, on `mandate/contract.html`, type
`5000`, then edit a middle digit: the grouping follows and the caret stays after the edited digit. If Partner
Central is runnable against a local ndopify-server, repeat on `/mandates/{id}/contract` with a 5000 fee and confirm
the pre-filled field reads `5 000`. On the served page, post `visitFee=abc` and confirm the re-rendered field still
reads `abc`.

- [ ] **Step 7: Run the module checks and commit**

```bash
cd modules/ndopify-partner-central
mvn verify
```

Expected: BUILD SUCCESS.

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/contract.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateContractTemplateTest.kt
git commit -m "feat(npc): format the contract's visit fee as typed" -m "Refs #201"
```

---

## Manual verification (after Task 3)

With Partner Central against a local ndopify-server, open an ASSIGNED or PENDING_APPROVAL mandate's
*Envoyer au client* page:

- at 360px and 320px, the two fields are on separate lines, full width, and nothing clips;
- at 1280px, they are side by side as before;
- the pre-filled fee reads `5 000`, typing regroups digits, and posting stores the same amount;
- a `visitFee=abc` error keeps `abc` in the field with its error under it;
- with JavaScript disabled, the form still posts and shows the same errors.
