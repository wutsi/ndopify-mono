# Wrapped Match Title and Facts Line Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop a wrapped match or proposal card title from colliding with the facts line on phones, without changing the one-line layout or the 44px tap area.

**Architecture:** The approved prototype replaces the title link's `min-height: var(--target)` with equal `padding-block` and keeps the cancelling negative `margin-block`. A one-line title still occupies exactly one 1.3em line in the flow with a 44px box; a wrapped title keeps its own height and pushes the `.match__facts` line down. The only production change is the same rule in `src/main/assets/css/home.css`, which esbuild bundles into `public/assets/dist/app.min.css`; no template, script, DTO or server change.

**Tech Stack:** CSS (mobile-first), esbuild asset bundle, Maven, Spring Boot + Thymeleaf (assets only).

**Spec:** GitHub issue [#345](https://github.com/wutsi/ndopify-mono/issues/345) — the enriched story and the approved option-1 prototype.

## Global Constraints

- Only `modules/ndopify-partner-central` changes: no `ndopify-dto`, `ndopify-server`, `ndopify-web-core` or database change.
- The CSS source is `src/main/assets/css/home.css`; `public/assets/dist/app.min.css` is built into `target/classes`, never committed.
- The static prototype (`src/main/html/`) is never served; its stylesheet copy is the approved visual reference, already committed on `main`.
- No template, markup, JavaScript or DTO change; the page keeps working without JavaScript.
- One-line cards must look exactly as today, and the title link keeps a tap area of at least 44px.
- Target widths: 320, 360 and 390px; 1280px stays unchanged.
- UI copy is French; code, comments and docs are English.
- Conventional commits with `Refs #345` in the body.
- `mvn verify` in `modules/ndopify-partner-central` is the final gate; it rebuilds the asset bundle.

## Review Focus

1. **A one-line title keeps the card's height and the facts line's position at 390px.** The padding must cancel through the negative margin; a stray extra margin would move every card.
2. **A two-line title at 320px pushes the facts line down instead of bleeding over it.** "Appartement · 3 chambres" beside the "Intéressé" tag is the reference; no glyph of the title may touch the facts line.
3. **The proposal cards get the same fix.** A long "Client d'un autre agent" name uses `.proposal-card .assignment__type a`, the second selector of the rule.
4. **The tap area stays at least 44px on one line and grows when the title wraps.** The box comes from padding now, not `min-height`; the focus ring must still surround the padded box.
5. **The served page reads the new rule from the fresh bundle.** A stale `app.min.css` would keep the old `min-height` and reproduce the bug.

---

### Task 1: Port the approved rule to the served stylesheet

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/css/home.css:1227-1236`
- Test: none — no automated test covers CSS in this module; the prototype is the visual harness, and the steps below verify the rule, the built bundle and the rendered cards.

**Interfaces:**
- Consumes: the approved prototype block in `modules/ndopify-partner-central/src/main/html/assets/css/home.css` (the same block in `src/main/html/v2/assets/css/home.css`), committed on `main`.
- Produces: the bundled `.match__title a, .proposal-card .assignment__type a` rule in `public/assets/dist/app.min.css`.

- [ ] **Step 1: Replace the title link's rule**

In `modules/ndopify-partner-central/src/main/assets/css/home.css`, replace:

```css
/* The card title is the one link on the row: a 44px hit area without changing its line rhythm. */
.match__title a,
.proposal-card .assignment__type a {
    display: inline-flex;
    align-items: center;
    min-height: var(--target);
    margin-block: calc((1.3em - var(--target)) / 2);
    color: inherit;
    text-underline-offset: 0.2em;
}
```

with the approved prototype's block, verbatim:

```css
/* The card title is the one link on the row: a 44px tap area on one line, and a wrapped title keeps its own
   height instead of bleeding over the facts line. */
.match__title a,
.proposal-card .assignment__type a {
    display: inline-flex;
    align-items: center;
    padding-block: calc((var(--target) - 1.3em) / 2);
    margin-block: calc((1.3em - var(--target)) / 2);
    color: inherit;
    text-underline-offset: 0.2em;
}
```

Do not change `display`, `align-items`, the negative margin, the colour, the line-height or any neighbouring rule.

- [ ] **Step 2: Check the served rule matches the approved prototype**

```bash
cd /Users/herve/Projects/ndopify-mono
diff <(sed -n '/The card title is the one link/,/^}/p' \
  modules/ndopify-partner-central/src/main/assets/css/home.css) \
     <(sed -n '/The card title is the one link/,/^}/p' \
  modules/ndopify-partner-central/src/main/html/assets/css/home.css)
```

Expected: no output (the two blocks are identical). A difference means Task 1 has drifted from the approved design.

- [ ] **Step 3: Build the module and run its checks**

```bash
cd /Users/herve/Projects/ndopify-partner-central
mvn verify
```

Expected: BUILD SUCCESS. This runs ktlint, the tests, the coverage gate and the esbuild bundle (`npm ci` needs network on a clean run). No test asserts CSS; this proves nothing else broke and produces the bundle.

- [ ] **Step 4: Check the built bundle carries the rule**

```bash
cd /Users/herve/Projects/ndopify-partner-central
grep -o '\.match__title a[^}]*}' target/classes/public/assets/dist/app.min.css
```

Expected: one match containing `padding-block` and no `min-height:var(--target)`. A stale bundle still showing `min-height` means the Maven build did not rerun `build-assets.mjs`.

- [ ] **Step 5: Check the cards at the three phone widths**

The prototype carries the same rule; serve it (it is never served by the app):

```bash
cd /Users/herve/Projects/ndopify-mono/modules/ndopify-partner-central/src/main/html
python3 -m http.server 8099
```

Open `http://localhost:8099/mandate.html?id=204` and check, at 320, 360 and 390px:

- *Intéressés*: "Appartement · 3 chambres" wraps at 320–360px, the facts line (score, neighbourhood, rent, availability) starts about 6px under the title's last line, and no glyphs overlap; the card's list gap is unchanged.
- *Propositions et visites*: the "Client d'un autre agent" card shows the same gap under a wrapped name.
- One-line cards: the title, tag and facts line sit exactly where they did before; the tap area still surrounds the title at 44px.
- At 1280px: every card is on one line and unchanged.

If Partner Central runs against a local `ndopify-server`, repeat on a signed search's `/mandates/{id}` page (*Biens correspondants*) and on a landlord's *Propositions*: the served page must match the prototype. The identical diff and the bundle check above are the evidence when the app cannot run locally.

- [ ] **Step 6: Commit**

```bash
cd /Users/herve/Projects/ndopify-mono
git add modules/ndopify-partner-central/src/main/assets/css/home.css
git commit -m "fix(npc): keep a wrapped match title off the facts line" -m "Refs #345"
```

## Manual verification (after Task 1)

With Partner Central built, open a signed search's mandate page at 320, 360 and 390px:

- a wrapped title (two lines) has a clear gap to the facts line, and the card is taller than a one-line card by the wrapped line's height;
- a one-line title's card is unchanged, including its 44px tap area;
- the *Propositions et visites* cards with a long client name behave the same;
- at 1280px nothing moved.
