# The Mandate Page's Type and Status Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every mandate page, offer card and *Mes mandats* card shows its project — *Vente* for a SALE mandate, *Location*
for everything else — as a pill of the status tag's own class, painted as an ink keyline, beside the status on the page
(outside the heading, on its own line under 640 px) and hugging its word on a card.

**Architecture:** No Kotlin changes: `MandateView.sale`, `MandateCardView.sale` and `AssignmentView.sale` already say
whether the mandate is a sale, so the three Thymeleaf templates render the type badge server-side. A new
`.status-tag--type` variant of `.status-tag` (white fill, `--color-fg` keyline and text) replaces the info-blue
`.deal-tag` of the prototype; the page wraps the type and the status in `.mandate-intro__tags` beside the `h1`, and
`.mandate-title__text` left-aligns its lines so the card badge does not stretch to the client's name. The approved
prototype (`src/main/html/v2`) is the visual baseline; the styles port its rules verbatim.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, the esbuild `partner-central` bundle, JUnit 5 + `kotlin.test`,
MockMvc, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/276

**Approved prototype (already on the default branch with this plan — the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/mandate.html` — the page's `.mandate-intro__tags` pair
- `modules/ndopify-partner-central/src/main/html/v2/index.html` — the offer and *Mes mandats* cards
- `modules/ndopify-partner-central/src/main/html/v2/assets/css/home.css` — `.status-tag--type`, `.mandate-intro__tags`
- `modules/ndopify-partner-central/src/main/html/v2/assets/css/partner-central.css` — `.mandate-title__text`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/partner-central.js` — `setDealTag` (prototype only)
- Review it at `python3 -m http.server 8899` in `src/main/html/v2`: `index.html`, `mandate.html?id=301` (*Vente* + *Actif*),
  `mandate.html?id=201` (*Location* + *Assigné*), and both at 390 px.

## Global Constraints

- **Module:** `modules/ndopify-partner-central` only. No DTO, no `ndopify-web-core`, no migration, no server, no public
  site, no `pom.xml` or `application*.yml` change.
- **No Kotlin/model change:** the `sale` Boolean already exists on every view the three templates receive. Tasks 1–3
  touch two stylesheets, three templates and two template tests.
- **The type is not a state.** `.status-tag--type` resolves to `--color-fg` (border and text) and `--color-bg` (fill)
  only; no status colour, no new token, no `DESIGN.md` change. The four status variants keep their colours.
- **One pill, two labels.** *Vente* only when the view's `sale` is true; *Location* otherwise (`listingType` may be
  `UNKNOWN` on mandates created before sales existed — they were all rentals). The badge carries no icon.
- **No JavaScript.** The badges are server-rendered; do not touch `src/main/assets/js/` and do not require a script.
- **Prototype:** `src/main/html/` is the design record — never edit or delete anything in it from this plan.
- **French UI, English code.** The two labels are `Vente` / `Location`; classes, KDoc, comments and commits are English.
- **Metrics stay shared.** The type badge is a `.status-tag`; keep the shared `.status-tag` box (padding `0.125rem
  var(--space-3)`, 1 px border, `--radius-control`, `--step-hint`, weight 600). The mobile boundary is the module's
  existing `639.98px` / `min-width: 640px`.
- `mvn verify` green in `modules/ndopify-partner-central` (ktlint, tests, JaCoCo). `mvn test` needs no MySQL; a clean
  `mvn verify` builds the asset bundle and needs network for `npm ci`.
- Commits are conventional and scoped `feat(npc): …` / `test(npc): …`, each with `Refs #276` in the body.

## Review Focus

1. **A mandate with no sale type reads *Location*.** `listingType` may be `UNKNOWN` or absent; `sale` is false, so the
   badge must say *Location*, never an empty pill. Pinned by Task 1's `an unknown listing type reads Location`.
2. **A card now has two `.status-tag` nodes.** Every existing `.status-tag` assertion that means the status must not
   catch the type pill (DOM order puts the type pill in the title first). Pinned by Task 2's updated `mandates` test
   (`:not(.status-tag--type)`).
3. **No heading typography on the type badge.** The page badge must stay outside the `h1` (Lora 700, heading
   line-height), or it inherits both and the pair stops matching. Pinned by Task 1's `assertNull(page.selectFirst("h1
   [data-deal-tag]"))`.
4. **The page's two pills are one component.** Both are `.status-tag` inside `.mandate-intro__tags`; only the status
   adds its colour class and only the type adds `--type`. Pinned by Task 1's class assertions on the pair.
5. **The pair survives a long title and the card badge a long name.** The pair is one flex child (`flex-basis: 100%`
   under 640 px) and the card's title column is left-aligned; a wrapped pair must not split the two badges. Pinned by
   Task 1's `.mandate-intro__tags` node and Task 3's `align-items: flex-start` on `.mandate-title__text` (jsoup pins the
   structure, the prototype states pin the look).
6. **Statuses are unchanged.** The four status colour classes keep their paint; `.status-tag--type` overrides only
   its own element. Pinned by Task 2's `status-tag status-tag--info` / `--warning` assertions and Task 3's review of
   the diff against the prototype.

---

### Task 1: The mandate page's type and status pair

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html:52-58`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `MandateView.sale: Boolean`, `MandateView.tag.style: String`, `MandateView.tag.label: String` (existing).
- Produces: the markup `.mandate-intro__head > .mandate-intro__tags > span.status-tag.status-tag--type[data-deal-tag]`
  followed by `span.status-tag.status-tag--<style>`, which Task 3 styles and which later pages select.

- [ ] **Step 1: Write the failing tests**

In `MandateTemplateTest.kt`, update `pending approval` (rental, PENDING_APPROVAL):

```kotlin
    @Test
    fun `pending approval`() {
        val page = render(get("/mandates/201"))

        assertEquals("Locataire · Maison — Ndopify Partner Central", page.title())
        assertEquals("Locataire · Maison", page.selectFirst("h1")?.text())
        assertEquals("search", page.selectFirst("h1 [data-mandate-kind]")?.attr("data-mandate-kind"))
        // The type and the status are two pills of the same class, side by side outside the heading.
        val tags = page.selectFirst(".mandate-intro__tags")!!
        assertEquals(listOf("Location", "En approbation"), tags.select(".status-tag").map { it.text() })
        assertEquals("status-tag status-tag--type", tags.selectFirst("[data-deal-tag]")?.className())
        assertEquals(
            "status-tag status-tag--warning",
            tags.selectFirst(".status-tag:not(.status-tag--type)")?.className(),
        )
        assertNull(page.selectFirst("h1 [data-deal-tag]"))
        // ... the rest of the test is unchanged
    }
```

Update `a rental's next hint is unchanged` so a rental carries *Location*:

```kotlin
        assertEquals("Location", page.selectFirst(".mandate-intro__tags [data-deal-tag]")?.text())
```

Update `a sale search's page words the buyer`:

```kotlin
        assertEquals("Acheteur · Maison", page.selectFirst("[data-mandate-title]")?.text())
        val tags = page.selectFirst(".mandate-intro__tags")!!
        assertEquals(listOf("Vente", "Actif"), tags.select(".status-tag").map { it.text() })
        assertEquals("status-tag status-tag--type", tags.selectFirst("[data-deal-tag]")?.className())
        assertNull(page.selectFirst("h1 [data-deal-tag]"))
```

Update `a sale listing's page words the seller`:

```kotlin
        assertEquals("Vente", page.selectFirst(".mandate-intro__tags [data-deal-tag]")?.text())
```

Add the no-sale case:

```kotlin
    @Test
    fun `an unknown listing type reads Location`() {
        val page = render(APPROVED.copy(listingType = ListingType.UNKNOWN))

        assertEquals("Location", page.selectFirst(".mandate-intro__tags [data-deal-tag]")?.text())
        assertNull(page.selectFirst("h1 [data-deal-tag]"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=MandateTemplateTest` in `modules/ndopify-partner-central`
Expected: FAIL — `.mandate-intro__tags` is null in `pending approval`, and the sale assertions do not find
`[data-deal-tag]` outside the `h1`.

- [ ] **Step 3: Port the prototype's header into `templates/mandate.html`**

Replace the `h1` and the status paragraph with (keeping `th:with="m=${mandate}"` and the existing comment above them):

```html
                    <div class="mandate-intro__head">
                        <h1 class="mandate-title"><span th:replace="~{fragments/mandate-kind :: icon(${m.owner})}"></span><span
                                data-mandate-title th:text="${m.title}">Locataire · Maison</span></h1>
                        <!-- The type and the status are siblings of the title, outside the heading: two pills of the
                             same class. Below 640px they share a line of their own under the title. -->
                        <div class="mandate-intro__tags">
                            <span class="status-tag status-tag--type" data-deal-tag
                                  th:text="${m.sale} ? 'Vente' : 'Location'">Vente</span>
                            <span th:class="|status-tag status-tag--${m.tag.style}|" th:text="${m.tag.label}">Assigné</span>
                        </div>
                    </div>
```

Update the comment above the header to say the type and the status sit by the title.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=MandateTemplateTest` in `modules/ndopify-partner-central`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show a mandate's type beside its status" -m "Refs #276"
```

---

### Task 2: The type badge on every card

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-card.html:8-18`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html:144-152`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: `MandateCardView.sale: Boolean`, `AssignmentView.sale: Boolean` (existing).
- Produces: one `span.status-tag.status-tag--type[data-deal-tag]` per offer and *Mes mandats* card, inside the card's
  `h3` (the offer's row, the card's `.mandate-title__text` column), which Task 3 positions.

- [ ] **Step 1: Write the failing tests**

In `HomeTemplateTest.kt`, update the status-only assertions in `mandates` so the new type pill is not caught:

```kotlin
        assertEquals("status-tag status-tag--info", card.selectFirst(".status-tag:not(.status-tag--type)")?.className())
        assertEquals("Assigné", card.selectFirst(".status-tag:not(.status-tag--type)")?.text())
        // ...
        assertEquals(
            "status-tag status-tag--warning",
            cards[1].selectFirst(".status-tag:not(.status-tag--type)")?.className(),
        )
```

Update `sale cards carry the Vente tag` to assert the new class on both cards:

```kotlin
    @Test
    fun `sale cards carry the Vente type badge`() {
        // ... the setup is unchanged
        val page = render(get("/"))

        val offer = page.selectFirst("[data-assignment-id=104]")!!
        assertEquals("Acheteur", offer.selectFirst("[data-deal-tag]")?.previousElementSibling()?.text())
        assertEquals("Vente", offer.selectFirst("[data-deal-tag]")?.text())
        assertEquals("status-tag status-tag--type", offer.selectFirst("[data-deal-tag]")?.className())

        val card = page.selectFirst("[data-mandate-id=301]")!!
        assertEquals("Vendeur · Maison", card.selectFirst("a.mandate-card__link")?.text())
        assertEquals("Vente", card.selectFirst("[data-deal-tag]")?.text())
        assertEquals("status-tag status-tag--type", card.selectFirst("[data-deal-tag]")?.className())
    }
```

Replace `a rental card carries no Vente tag`:

```kotlin
    @Test
    fun `a rental card carries the Location type badge`() {
        val page = render(get("/"))

        val offer = page.selectFirst("[data-assignment] [data-deal-tag]")
        assertEquals("Location", offer?.text())
        assertEquals("status-tag status-tag--type", offer?.className())

        val card = page.selectFirst("[data-mandate-card] [data-deal-tag]")
        assertEquals("Location", card?.text())
        assertEquals("status-tag status-tag--type", card?.className())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn test -Dtest=HomeTemplateTest` in `modules/ndopify-partner-central`
Expected: FAIL — the rental card has no `[data-deal-tag]`, and the sale assertions expect the old
`status-tag status-tag--info` class (now `status-tag status-tag--type`).

- [ ] **Step 3: Port the prototype's badge into the two templates**

In `fragments/mandate-card.html`, replace the conditional info tag with the always-present type badge (inside
`.mandate-title__text`, after the `.mandate-card__kind` span):

```html
                        <span class="status-tag status-tag--type" data-deal-tag
                              th:text="${m.sale} ? 'Vente' : 'Location'">Vente</span>
```

In `index.html`, in the offer's `h3` (after the title span), replace the conditional info tag with:

```html
                                        th:text="${a.title}">Locataire</span><span class="status-tag status-tag--type"
                                        data-deal-tag th:text="${a.sale} ? 'Vente' : 'Location'">Vente</span></h3>
```

Neither template may keep a `th:if` on the badge: a rental reads *Location*.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn test -Dtest=HomeTemplateTest` in `modules/ndopify-partner-central`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-card.html \
        modules/ndopify-partner-central/src/main/resources/templates/index.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "feat(npc): give every mandate card its type badge" -m "Refs #276"
```

---

### Task 3: The ink keyline, the pair and the hugging card column

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/css/home.css` (the `.status-tag` block around line 448, the
  `.mandate-intro` block around line 686)
- Modify: `modules/ndopify-partner-central/src/main/assets/css/partner-central.css` (`.mandate-title__text` around
  line 1031)

**Interfaces:**
- Consumes: the markup of Tasks 1–2 (`.status-tag--type`, `.mandate-intro__tags`, `.mandate-title__text`).
- Produces: the visual look of the approved prototype; no new class beyond those three.

- [ ] **Step 1: Port the type variant and the shared pill metrics into `home.css`**

In the `.status-tag` rule add the body font and line-height the prototype uses, so the pill looks the same inside a
card title as beside the page title:

```css
    font-family: var(--font-body);
    font-size: var(--step-hint);
    font-weight: 600;
    line-height: 1.6;
```

After `.status-tag--success`, add:

```css
/* The mandate's type ("Vente" / "Location"), beside the status: the same pill, an ink keyline instead of a status
   colour. It says the kind, not the state, so it never borrows a status's green, amber or red; the black edge is what
   keeps it legible on white (a grey hairline disappeared next to the coloured status). */
.status-tag--type {
    border-color: var(--color-fg);
    background: var(--color-bg);
    color: var(--color-fg);
}

/* On an offer card the badge sits on the title's line; on a Mes mandats card it is the last line of the title block,
   a step below the party-and-type line. */
.mandate-title > .status-tag--type {
    align-self: center;
}

.mandate-title__text > .status-tag--type {
    margin-top: var(--space-1);
}
```

- [ ] **Step 2: Port the pair into `home.css`**

After `.mandate-intro__head`, add (mobile-first, as the prototype):

```css
/* The page's type and status pills: one row after the title, closer together than the gap that separates them from
   the title, so they read as a pair. On phones the pair takes its own line under the title, whatever the title's
   length (the head above wraps). */
.mandate-intro__tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
    flex-basis: 100%;
}
```

Inside the existing `@media (min-width: 640px)` block that holds `.mandate-intro h1`, add:

```css
    .mandate-intro__tags {
        flex-basis: auto;
    }
```

- [ ] **Step 3: Left-align the card's title column in `partner-central.css`**

```css
.mandate-title__text {
    display: flex;
    flex-direction: column;
    /* A column stretches its children across its width, which the client's name sets: the type badge would be as
       wide as the name instead of hugging its word. Every line hugs its own text. */
    align-items: flex-start;
}
```

- [ ] **Step 4: Run the module's gates**

Run: `mvn verify` in `modules/ndopify-partner-central`
Expected: PASS (ktlint, all tests, JaCoCo).

- [ ] **Step 5: Compare with the approved prototype**

Open the prototype for the reference look (`python3 -m http.server 8899` in
`modules/ndopify-partner-central/src/main/html/v2`, `index.html`, `mandate.html?id=301`, `mandate.html?id=201`, at 390 px)
and compare with a local run of the app at the same widths: the page shows the title, then the two pills of equal height
and body font, on their own line under 640 px; a *Mes mandats* card's *Vente* / *Location* pill is no wider than its
text. No status colour changed, and no JavaScript was added.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/assets/css/home.css \
        modules/ndopify-partner-central/src/main/assets/css/partner-central.css
git commit -m "style(npc): paint the mandate's type badge as an ink keyline" -m "Refs #276"
```
