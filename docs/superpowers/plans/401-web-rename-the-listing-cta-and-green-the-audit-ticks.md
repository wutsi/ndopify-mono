# Listing CTA copy and audit-note ticks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The listing page's three contact CTAs and the `#visit` popup's heading read **« Contacter l'agent »**, and the audit note's check marks stay success-green in both the verified and the amber unverified state, on the listing page and on the match page.

**Architecture:** French copy changes in `templates/listing.html` and the `listing.js` drawer copy (the canned *visite* answer's sentence and the button it appends); an `audit-note__check` hook added to the four checked rows of the shared `templates/fragments/listing-body.html`, coloured `--color-text-success` by one rule in `assets/css/listing.css`. No Kotlin, DTO, ndopify-server nor ndopify-web-core change. The approved v2 prototype (`src/main/html/v2`) is the visual baseline: the production markup has no JS-switched icon wrappers, so the class hook carries the colour the prototype gets from `.audit-note__list .icon`.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, Jsoup template tests, `kotlin.test`, the esbuild CSS/JS bundle.

**Spec:** story #401 — https://github.com/wutsi/ndopify-mono/issues/401 — with its approved prototype under `modules/ndopify-web/src/main/html/v2` (`annonce.html`, `assets/css/annonce.css`, `assets/js/annonce.js`, `README.md`).

## Global Constraints

- Only `modules/ndopify-web` changes: no `ndopify-dto`, no `ndopify-server`, no `ndopify-web-core`, no release.
- The label is exactly **`Contacter l'agent`** (straight apostrophe, no capital on *agent*), on all four user-visible occurrences: the price card's CTA, the fixed bottom bar's CTA, the *Poser une question* drawer's inline link, and the `#visit` popup's heading — plus the two copies inside `listing.js` (its canned answer's sentence and the CTA it appends).
- UI copy in French; code, comments and docs in English.
- The page works without JavaScript: the JS change is copy in the canned answer only, never a requirement.
- The ticks use the existing `--color-text-success` (`#155724`); no new colour, no new token. Only the glyphs change
  colour, never the note's text: green on `#f0fdf4` is 8.3:1 and on `#fef3c7` 7.8:1, AA on both.
- The approved prototype in `src/main/html/v2` is not served and must not be edited by the implementation.
- Assertions use `kotlin.test` (`assertEquals`, `assertTrue`, `assertFalse`, `assertNull`); `mvn verify` in `modules/ndopify-web` (ktlint, tests, coverage gate, bundle) passes.

## Files

- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html` (three CTAs, the `#visit` heading, two comments)
- Modify: `modules/ndopify-web/src/main/assets/js/listing.js` (two comments, the canned sentence, the appended CTA)
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html` (the four checked icons of the audit note)
- Modify: `modules/ndopify-web/src/main/assets/css/listing.css` (one rule, after `.audit-note__limit`)
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRules.kt`, `ListingInterestForm.kt`, `ListingController.kt` and `config/SecurityConfiguration.kt` (KDoc/comments only)
- Modify: `modules/ndopify-web/CLAUDE.md` (the listing paragraph's CTA wording)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

## Review Focus

- The drawer's JavaScript-appended CTA and its canned sentence have no template test: a leftover « Être contacté » would only show after a chat answer — `grep -rn "Être contacté" modules/ndopify-web/src` must print nothing by the end.
- The warning note: the visit and GPS ticks must stay green while the owner-identity and title alerts and the registry footnote stay amber — the hook is conditional on `ownershipVerified`, never blanket on every icon.
- A closed listing (SOLD / RENTED): still no price-card CTA, no bottom-bar action, unchanged.
- The match page shares `fragments/listing-body.html` and `proposal.css` has no `.audit-note` override: the same hook and colour must reach it.
- The rest of the `#visit` popup is untouched by the copy change — subtitle, fields, moving-period options, honeypot, e-mail-invalid re-render and confirmation; the existing popup tests in `ListingTemplateTest` must stay green, unchanged.

---

### Task 1: « Contacter l'agent » on the CTAs and the `#visit` heading

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html:18,51,65,71,88,215`
- Modify: `modules/ndopify-web/src/main/assets/js/listing.js:1,21,194,270`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRules.kt:4`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestForm.kt:8`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt:30,80`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt:40`
- Modify: `modules/ndopify-web/CLAUDE.md:40`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing (copy only; the routes, the form and the popup behaviour are unchanged).

- [ ] **Step 1: Write the failing test**

In `ListingTemplateTest`, extend `a published listing shows the contact call to action` (currently lines 373-387) so it reads:

```kotlin
    @Test
    fun `a published listing shows the contact call to action`() {
        val doc = render()

        val card = doc.selectFirst(".price-card__cta")!!
        assertEquals("Contacter l'agent", card.text())
        assertEquals("#visit", card.attr("href"))
        assertTrue(card.hasAttr("data-modal-open"))
        assertEquals("Contacter l'agent", doc.selectFirst(".cta-bar__actions a")?.text())
        assertEquals(
            "Un agent vous contacte sous 24 h pour organiser la visite.",
            doc.selectFirst(".price-card__hint")?.text(),
        )
        assertEquals("Contacter l'agent", doc.selectFirst("#visit-title")?.text())
        val drawerLink = doc.selectFirst("#ask .chat__disclaimer a")!!
        assertEquals("Contacter l'agent", drawerLink.text())
        assertEquals("#visit", drawerLink.attr("href"))
        assertTrue(drawerLink.hasAttr("data-modal-open"))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest`
Expected: FAIL in `a published listing shows the contact call to action` — `expected:<Contacter l'agent> but was:<Être contacté par un agent>`.

- [ ] **Step 3: Change the three CTAs and the popup heading in `templates/listing.html`**

- the price card's button (line 51) keeps its calendar icon and becomes
  `…</svg>Contacter l'agent`;
- the bottom bar's link (line 65) becomes
  `<a class="btn btn-primary" data-modal-open th:href="'#visit'">Contacter l'agent</a>`;
- the drawer's inline link (line 215) becomes
  `<a data-modal-open href="#visit">Contacter l'agent</a>`;
- the popup's heading (line 88) becomes
  `<h2 class="modal__title" id="visit-title">Contacter l'agent</h2>`;
- the two comments that name the old wording (lines 18 and 71) read `"Contacter l'agent"` too. Nothing else on the
  popup (subtitle, fields, confirmation, buttons) changes — including the calendar icon and the hint line.

- [ ] **Step 4: Change the drawer's JavaScript copy in `assets/js/listing.js`**

- line 194, the canned *visite* answer: `'Dans « Contacter l\'agent », indiquez vos coordonnées et votre date d\'emménagement : '`
  (the apostrophe is escaped inside the single-quoted string);
- line 270, the button that answer appends: `'<svg aria-hidden="true" class="icon"><use href="#i-calendar"/></svg>Contacter l\'agent'`;
- lines 1 and 21, the comments that name the popup: `"Contacter l'agent"`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest`
Expected: PASS, every test of the class.

- [ ] **Step 6: Bring the docs and KDoc in line with the new wording**

Replace the five KDoc occurrences of `"Être contacté par un agent"` with `"Contacter l'agent"` in
`ListingInterestRules.kt` (line 4), `ListingInterestForm.kt` (line 8), `ListingController.kt` (lines 30 and 80) and
`SecurityConfiguration.kt` (line 40), and the `CLAUDE.md` line 40 phrase `"Être contacté par un agent" (price card, and
bottom bar below 1024px)` with `"Contacter l'agent" (price card, bottom bar and the drawer's link)`. Keep the sentence
around it: the heading is now the same words.

- [ ] **Step 7: Verify no old wording is left**

Run: `grep -rn "Être contacté\|Contactez l'agent" modules/ndopify-web/src modules/ndopify-web/CLAUDE.md`
Expected: no output — the approved prototype under `src/main/html/v2` was already renamed.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/listing.html \
  modules/ndopify-web/src/main/assets/js/listing.js \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRules.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestForm.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt \
  modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt \
  modules/ndopify-web/CLAUDE.md \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt
git commit -m "feat(web): the listing's contact CTA and popup read Contacter l'agent"
```

---

### Task 2: The audit note's checked ticks are green in both states

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html:66-86`
- Modify: `modules/ndopify-web/src/main/assets/css/listing.css` (after `.audit-note__limit`, line 150)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

**Interfaces:**
- Consumes: `ListingPage.ownershipVerified` (the fragment already branches on it for the glyph and the words).
- Produces: the `audit-note__check` class on the four checked icons; `.audit-note__check { color: var(--color-text-success) }` in the bundle.

- [ ] **Step 1: Write the failing tests**

In `ListingTemplateTest`, add this helper next to `icons` (line 164):

```kotlin
    private fun checkTicks(doc: Document) =
        doc.select(".audit-note__list li").map { it.selectFirst("svg")!!.hasClass("audit-note__check") }
```

In `the audit note lists what was checked`, after the `assertEquals(listOf("i-check", "i-check", "i-check", "i-check", "i-info"), icons(doc))` line, add:

```kotlin
        assertEquals(listOf(true, true, true, true, false), checkTicks(doc))
```

In `an owner not verified is the warning state, in words`, after `assertEquals(listOf("i-check", "i-check", "i-alert", "i-alert", "i-info"), icons(doc))`, add:

```kotlin
        assertEquals(listOf(true, true, false, false, false), checkTicks(doc))
```

In `MatchTemplateTest`'s `the match page keeps its audited badge and note, without the ownership suffix` (around line 646), after the existing assertions, add:

```kotlin
        assertEquals(
            listOf(true, true, true, true, false),
            doc.selectFirst(".audit-note")!!.select(".audit-note__list li")
                .map { it.selectFirst("svg")!!.hasClass("audit-note__check") },
        )
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest,MatchTemplateTest`
Expected: FAIL with `expected:<[true, true, true, true, false]> but was:<[false, false, false, false, false]>` (and the
warning list likewise).

- [ ] **Step 3: Add the hook in `templates/fragments/listing-body.html`**

The visit and GPS icons (currently `<svg aria-hidden="true" class="icon"><use href="#i-check"/></svg>`) become
`<svg aria-hidden="true" class="icon audit-note__check"><use href="#i-check"/></svg>`.

The owner-identity and title icons keep their conditional glyph and take the hook only when checked:

```html
<svg aria-hidden="true" class="icon" th:classappend="${listing.ownershipVerified} ? 'audit-note__check' : null"><use th:href="${listing.ownershipVerified} ? '#i-check' : '#i-alert'"/></svg>
```

The registry footnote (`audit-note__limit`) keeps `class="icon"`, with no hook.

- [ ] **Step 4: Add the colour rule in `assets/css/listing.css`**

After the `.audit-note__limit` block, add:

```css
/* #401: a checked fact's tick is success-green on the verified note and still green on the warning one — a check never
   reads as a warning. Only the glyphs change, not the text: the unverified rows' alerts and the registry footnote keep
   the note's own colour. Green on #f0fdf4 (8.3:1) and on #fef3c7 (7.8:1): AA. */
.audit-note__check { color: var(--color-text-success); }
```

The prototype reaches the same result by colouring `.audit-note__list .icon` and re-inheriting on its JS-switched
wrappers; production has no such wrapper, so the hook decides — do not copy the prototype's blanket selector.

- [ ] **Step 5: Run the two test classes to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest,MatchTemplateTest`
Expected: PASS.

- [ ] **Step 6: Run the module gate**

Run: `cd modules/ndopify-web && mvn verify`
Expected: BUILD SUCCESS — ktlint, every test, the coverage gate and the bundle build pass.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html \
  modules/ndopify-web/src/main/assets/css/listing.css \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt
git commit -m "feat(web): keep the audit note's checked ticks green on both variants"
```
