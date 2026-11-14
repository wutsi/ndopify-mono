# Co-Brokerage Line in *Biens correspondants* — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mark another agent's listing in a search mandate's *Biens correspondants* with one compact info-navy line under the title, in the prototype and in the BFF.

**Architecture:** The BFF carries `Match.coBrokerage` into `MatchView`; the Thymeleaf match card renders a static line when it is true. The prototype mirrors the same markup, toggled by `renderMatches`. One CSS rule is added to each `home.css` copy. No controller, API, DTO or ordering change.

**Tech Stack:** Kotlin, Spring Boot + Thymeleaf, Jsoup template tests, JUnit 5 / `kotlin.test`, plain HTML/CSS/JS prototype.

**Spec:** `docs/superpowers/specs/2026-11-13-npc-match-co-brokerage-badge-design.md`

## Global Constraints

- French UI copy, consistent with the existing wording.
- Reuse `--color-info-fg`, `--step-hint` and `--space-1`; no new colour token.
- The marker is text plus an `aria-hidden` icon; colour is never the only signal.
- Own-inventory cards render no line and keep their current height.
- The line is independent of the status tag: it must stay when a visit block hides the tag.
- Update **both** `home.css` copies: `src/main/html/assets/css/home.css` and `src/main/resources/public/assets/css/home.css`.
- `src/main/html` is the prototype: never served, never delete or clean up unrelated files.
- The working tree has unrelated WIP. Stage only the paths each task names (`git add <paths>`), never `git add -A`.
- Run module tests from `modules/ndopify-partner-central` with `mvn test -Dtest=<Class>`.

## Review Focus

1. **Co-brokerage match with a requested or confirmed visit** — the status tag is hidden; the origin line must remain.
2. **Own-inventory match** — no origin line, and no extra spacing or height.
3. **Visits unavailable** — the line still renders; it does not depend on the visit read.
4. **Prototype match 9009** — the line appears, and the old `Origine` fact is gone (no duplicate marker).
5. **Screen readers** — the icon is `aria-hidden`, the text is present, and the line is not colour-only.

---

### Task 1: Carry `coBrokerage` into `MatchView`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`

**Interfaces:**
- Consumes: `Match.coBrokerage: Boolean` (already in `ndopify-dto`).
- Produces: `MatchView.coBrokerage: Boolean = false`, set by `MatchViews.section`.

- [ ] **Step 1: Write the failing test**

Add to `MatchViewsTest`:

```kotlin
@Test
fun `co-brokerage card is flagged for the template`() {
    val section = section(listOf(MATCH_A.copy(coBrokerage = true), MATCH_B))

    assertTrue(section.matches.first { it.id == 9001L }.coBrokerage)
    assertFalse(section.matches.first { it.id == 9003L }.coBrokerage)
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `mvn test -Dtest=MatchViewsTest`
Expected: compilation fails, `Unresolved reference: coBrokerage`.

- [ ] **Step 3: Add the field and pass it**

In `MatchView`, after `listingId`:

```kotlin
val coBrokerage: Boolean = false,
```

In `MatchViews.view`, in the `MatchView(...)` construction, add:

```kotlin
coBrokerage = match.coBrokerage,
```

- [ ] **Step 4: Run it and verify it passes**

Run: `mvn test -Dtest=MatchViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt
git commit -m "feat(npc): flag co-brokerage match cards in the view model"
```

---

### Task 2: Render the line in the BFF card

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchView.coBrokerage` (Task 1).
- Produces: the rendered markers `[data-match-origin]` and `[data-matches-intro]`.

- [ ] **Step 1: Write the failing tests**

Add these tests next to `co-brokerage match links to the listing with its match id`:

```kotlin
@Test
fun `co-brokerage match - line under the title`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, coBrokerage = true))

    val page = render(APPROVED)
    val card = page.selectFirst("[data-match-id=9001]")!!

    assertEquals("Bien d'un autre agent", card.selectFirst("[data-match-origin]")?.text())
    assertTrue(card.selectFirst("[data-match-origin] svg")!!.hasAttr("aria-hidden"))
}

@Test
fun `own-inventory match - no origin line`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

    val page = render(APPROVED)

    assertNull(page.selectFirst("[data-match-id=9001] [data-match-origin]"))
}

@Test
fun `co-brokerage match with a visit keeps its origin line`() {
    doReturn(VisitFixtures.NOW).whenever(clock).millis()
    withMatches(
        MatchFixtures.MATCH_A.copy(
            mandateId = 201,
            status = MatchStatus.ACCEPTED,
            coBrokerage = true,
            proposedAt = proposedAt,
        ),
    )
    doReturn(listOf(VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001))))
        .whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val page = render(APPROVED)
    val card = page.selectFirst("[data-match-id=9001]")!!

    assertNull(card.selectFirst("[data-match-tag]"))
    assertEquals("Bien d'un autre agent", card.selectFirst("[data-match-origin]")?.text())
}

@Test
fun `co-brokerage line stays when visits are unavailable`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, coBrokerage = true))
    doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val page = render(APPROVED)

    assertEquals(
        "Bien d'un autre agent",
        page.selectFirst("[data-match-id=9001] [data-match-origin]")?.text(),
    )
}

@Test
fun `matches intro covers other agents' listings`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

    val page = render(APPROVED)

    assertEquals(
        "Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par d'autres " +
            "agents. Envoyez-les-lui ou écartez-les.",
        page.selectFirst("[data-matches-intro]")?.text(),
    )
}
```

- [ ] **Step 2: Run them and verify they fail**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: failures on `[data-match-origin]` / `[data-matches-intro]` (missing elements).

- [ ] **Step 3: Add the line to `templates/mandate.html`**

In the match card, after the closing `</div>` of `assignment__head` and before `<dl class="match__facts" data-match-facts>`:

```html
<p class="match__origin" th:if="${match.coBrokerage}" data-match-origin>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><path d="M3.5 8.5 10 3l6.5 5.5V16a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M8 17v-4h4v4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>
    <span>Bien d'un autre agent</span>
</p>
```

- [ ] **Step 4: Correct the section intro**

Replace the current intro paragraph with:

```html
<p data-matches-intro>Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par
    d'autres agents. Envoyez-les-lui ou écartez-les.</p>
```

- [ ] **Step 5: Add the CSS rule**

In `resources/public/assets/css/home.css`, immediately before the `.match__facts` rule:

```css
/* The source of a co-brokerage card: one compact line under the title, before the facts. */
.match__origin {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    color: var(--color-info-fg);
    font-size: var(--step-hint);
    font-weight: 600;
}

.match__origin .icon {
    width: 1rem;
    height: 1rem;
    flex: none;
}
```

The existing `.match article > * + *` rule supplies the small top margin; do not add one.

- [ ] **Step 6: Run the BFF tests**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): mark co-brokerage listings in Biens correspondants"
```

---

### Task 3: Mirror it in the prototype

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Consumes: `match.coBrokerage` already present on prototype matches (`9009`).
- Produces: the same `[data-match-origin]` and intro copy as the BFF.

- [ ] **Step 1: Add the template node and intro copy**

In `src/main/html/mandate.html`:

- replace the intro line « Les biens de vos annonces publiées qui correspondent à la recherche du client. » with the
  same paragraph as Task 2;
- in `<template data-match-template>`, after `</div>` of `assignment__head` and before
  `<dl class="match__facts" data-match-facts>`, add:

```html
<p class="match__origin" data-match-origin hidden>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><path d="M3.5 8.5 10 3l6.5 5.5V16a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M8 17v-4h4v4" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>
    <span>Bien d'un autre agent</span>
</p>
```

- [ ] **Step 2: Toggle the line and drop the old fact**

In `renderMatches`'s `matchItem`, next to the `data-match-tag` handling, add:

```js
$("[data-match-origin]", item).hidden = !match.coBrokerage;
```

Then delete the old fact line:

```js
if (match.coBrokerage) fact(facts, "Origine", "Bien d'un autre agent");
```

- [ ] **Step 3: Add the same CSS rule**

In `src/main/html/assets/css/home.css`, immediately before `.match__facts`, add the `.match__origin` and
`.match__origin .icon` rules from Task 2, Step 5.

- [ ] **Step 4: Check the prototype at both widths**

Open `modules/ndopify-partner-central/src/main/html/mandate.html?id=204` from the file system (or via the static
prototype's usual local server), then:

- the co-brokerage card (listing `5001`, match `9009`) shows the info-navy **Bien d'un autre agent** line under the
  title, and no `Origine` fact;
- the agent's own cards show no line and the same height as before;
- narrow the window to 360px: no horizontal scroll, and the line stays on one row;
- a co-brokerage card with a visit would show the line with the status tag absent (the prototype's data has visits on
  own cards only, so this is covered by the BFF test rather than the prototype).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/mandate.html \
        modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "feat(npc): mirror the co-brokerage line in the prototype"
```

---

## Final verification

- [ ] Run the full module test suite: `mvn test` in `modules/ndopify-partner-central` — all pass.
- [ ] Run `mvn validate` in the same module — ktlint passes.
- [ ] Confirm `git status` contains no unintended files from this work.
