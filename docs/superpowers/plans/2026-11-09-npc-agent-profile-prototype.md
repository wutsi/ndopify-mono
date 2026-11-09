# NPC Agent Profile Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the static prototype of another agent's profile page at `src/main/html/agent.html`, reachable from that agent's listing once co-brokerage ships.

**Architecture:** A `file://`-safe static page: the complete fixture lives in `agent.html`, a small vanilla script (`agent.js`) switches fixtures for `?id=` states, and page-specific styles go to `agent.css` on top of `partner-central.css` tokens. No build step, no server, no BFF changes.

**Tech Stack:** Static HTML, vanilla ES5 JavaScript (IIFE, no modules), CSS custom properties from `partner-central.css`.

**Spec:** `docs/superpowers/specs/2026-11-09-npc-agent-profile-prototype-design.md`

## Global Constraints

- Work in `/Users/herve/Projects/ndopify-mono`; touch only `modules/ndopify-partner-central/src/main/html/` and this plan/spec.
- Do not change the BFF (`src/main/resources`, `src/main/kotlin`), the served tests, or any other prototype page. Never delete prototype files.
- French UI copy; English code and comments.
- Reuse `partner-central.css` tier-2 tokens; do not add colours, fonts, or spacing values, and do not put hex values in `agent.css`.
- No experience/tenure, no rating, no e-mail, no listings section, no agent directory.
- Do not add a navigation entry or a listing link to this page; the listing → agent link comes with co-brokerage.
- Page must work from `file://` with no build step and no new network dependency (no external images).
- Page must work without JavaScript: the default profile is complete in the markup.
- Target sizes: 320 px, 390 px and 1280 px wide; no horizontal scroll at any of them.
- No JS test harness exists for `src/main/html`; this is a throwaway prototype. Verify in a browser against the exact expected values below, and run the Impeccable layout detector at the end of each task:
  `/Users/herve/.config/opencode/skills/impeccable/scripts/impeccable detect --json --scope layout modules/ndopify-partner-central/src/main/html/agent.html`
  Expected output: `[]`.

## Review Focus

1. **Unknown or absent id:** `agent.html?id=9999` must show the "Agent introuvable" panel and hide the profile, never a blank page or a half-rendered profile. Verify in Task 2.
2. **Missing data:** no biography hides "À propos"; no WhatsApp hides the button (no dead link); no neighbourhoods hides "Ses secteurs"; no city hides the "· city" separator. Verify in Task 2.
3. **Long content:** a 500-character biography, a long name and long neighbourhood names must wrap at 320 px without horizontal scroll. Verify in Tasks 1 and 2.
4. **No JS:** with JavaScript disabled, `agent.html` still shows the complete default profile and works. Verify in Task 2.
5. **Back-link safety:** `?back=` only accepts a scheme-less URL; `javascript:` or `https://…` is ignored and the link falls back to `index.html`. Verify in Task 2.

---

### Task 1: Profile page markup and styles

**Files:**
- Create: `modules/ndopify-partner-central/src/main/html/agent.html`
- Create: `modules/ndopify-partner-central/src/main/html/assets/css/agent.css`

**Interfaces:**
- Produces DOM hooks consumed by Task 2: `[data-agent]`, `[data-agent-missing]`, `[data-back-link]`, `[data-agent-photo]`, `[data-agent-photo-image]`, `[data-agent-initials]`, `[data-agent-name]`, `[data-agent-verified]`, `[data-agent-type]`, `[data-agent-place]`, `[data-agent-city]`, `[data-agent-since]`, `[data-agent-whatsapp]`, `[data-agent-sectors]`, `[data-agent-sectors-city]`, `[data-agent-neighborhoods]`, `[data-agent-about]`, `[data-agent-bio]`.
- Consumes: `.page`, `.column`, `.section`, `.back-link`, `.alert`, `.btn`, `.icon`, `.visually-hidden`, `.skip-link`, `.site-header` from `partner-central.css`.

- [ ] **Step 1: Create `agent.html` with the complete default fixture**

Use the same document head, skip link, header and account menu as `mandate.html` (title: `Profil de l'agent — Ndopify Partner Central`; description: `Profil d'un agent du réseau Ndopify.`; `data-page="agent"`). Load `assets/css/partner-central.css` and `assets/css/agent.css?v=1`.

Inside `<main class="page">`, use `<div class="column">` and this content:

```html
<p class="back-link"><a href="index.html" data-back-link>← Retour à l'espace agent</a></p>

<!-- Shown by agent.js for an unknown id. -->
<div class="alert alert--warning" hidden data-agent-missing>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…warning icon as in mandate.html…</svg>
    <div>
        <h1 class="alert__title">Agent introuvable</h1>
        <p>Ce profil n'existe pas ou n'est plus disponible.</p>
        <p><a href="index.html">Retour à l'espace agent</a></p>
    </div>
</div>

<article data-agent>
    <header class="agent-hero">
        <div class="agent-hero__avatar" aria-hidden="true" data-agent-photo>
            <img hidden data-agent-photo-image alt="">
            <span data-agent-initials>CM</span>
        </div>
        <div class="agent-hero__heading">
            <h1 class="agent-hero__name" data-agent-name>Clarisse Mbarga</h1>
            <span class="agent-badge" data-agent-verified>
                <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…check-circle icon…</svg>
                Vérifié
            </span>
        </div>
        <p class="agent-hero__meta">
            <span data-agent-type>Indépendant</span>
            <span data-agent-place> · <span data-agent-city>Douala</span></span>
        </p>
        <p class="agent-hero__since" data-agent-since>Membre depuis mars 2023</p>
        <a class="btn btn--primary agent-hero__contact" target="_blank" rel="noopener"
           href="https://wa.me/237699123456" data-agent-whatsapp>
            <svg class="icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">…WhatsApp icon as in mandate.html…</svg>
            Contacter sur WhatsApp
        </a>
    </header>

    <section class="section" aria-labelledby="sectors-title" data-agent-sectors>
        <h2 id="sectors-title">Ses secteurs</h2>
        <p class="agent-sectors">
            <span class="agent-sectors__city" data-agent-sectors-city>Douala</span>
            <span class="agent-sectors__chips" data-agent-neighborhoods>
                <span class="agent-sectors__chip">Bonapriso</span>
                <span class="agent-sectors__chip">Akwa</span>
                <span class="agent-sectors__chip">Bonanjo</span>
            </span>
        </p>
    </section>

    <section class="section" aria-labelledby="about-title" data-agent-about>
        <h2 id="about-title">À propos</h2>
        <p data-agent-bio>Démarcheur à Douala, je connais Bonapriso, Akwa et Bonanjo rue par rue.
            J'accompagne les familles et les jeunes actifs de la recherche à la signature du bail,
            avec des biens vérifiés et des visites organisées.</p>
    </section>
</article>
```

Every icon is inline SVG with `aria-hidden="true"` and `focusable="false"`, copied from the existing prototype (warning from `mandate.html`, WhatsApp from `mandate.html`, check-circle from `index.html`). Keep the account menu guarded as on the other pages.

- [ ] **Step 2: Create `agent.css`**

Load after `partner-central.css`. Rules, tokens only:

- `.agent-hero`: centered column (`display: flex`, `flex-direction: column`, `align-items: center`, `text-align: center`), gaps `var(--space-2)`/`var(--space-3)`.
- `.agent-hero__avatar`: circle `7rem` square, `border-radius: 50%`, `overflow: hidden`, `background: var(--color-bg-subtle)`, grid centered; `img` fills it with `object-fit: cover`; initials use `font: 700 var(--step-h2) / 1 var(--font-heading)`.
- `.agent-hero__heading`: `display: flex`, `flex-wrap: wrap`, centered, `gap: var(--space-2)`, `align-items: center`, `justify-content: center`.
- `.agent-hero__name`: heading font inherits from `h1`; no extra size.
- `.agent-badge`: inline-flex, `gap: var(--space-1)`, `align-items: center`, `color: var(--color-success-fg)`, `font-size: var(--step-hint)`, `font-weight: 600`; its `.icon` is `1rem` square.
- `.agent-hero__meta`, `.agent-hero__since`: `font-size: var(--step-hint)` in the default foreground (no muted gray: it would fail small-text contrast).
- `.agent-hero__contact`: `display: inline-flex`, `justify-content: center`, `gap: var(--space-2)`, `width: 100%`, `max-width: 22rem`, `margin-top: var(--space-3)`; `.icon` is `1.25rem`.
- `.agent-sectors`: flex wrap, left-aligned (sections are left-aligned), `gap: var(--space-2)`, `align-items: center`, `flex-wrap: wrap`.
- `.agent-sectors__city`: `font-weight: 600`.
- `.agent-sectors__chips`: inline-flex, wrap, `gap: var(--space-2)`.
- `.agent-sectors__chip`: inline-flex, `padding: 0.125rem var(--space-3)`, `border-radius: var(--radius-control)`, `background: var(--color-bg-subtle)`, `font-size: var(--step-hint)`.
- Add only what these classes need; no page-level overrides of shared components.

- [ ] **Step 3: Verify the page in a browser at 320 px, 390 px and 1280 px**

Open `modules/ndopify-partner-central/src/main/html/agent.html` from `file://`. Expected:

- One `<h1>`: "Clarisse Mbarga", followed by the green "Vérifié" badge.
- "Indépendant · Douala", "Membre depuis mars 2023", a full-width (max 22rem) WhatsApp button with the icon.
- "Ses secteurs" with the city and three pill chips; "À propos" with the biography.
- Centered hero, left-aligned sections, no horizontal scroll at 320 px. Long bio text wraps.
- Keyboard: skip link, back link, WhatsApp button and account menu show a visible focus ring; the button is at least 44 px tall.

- [ ] **Step 4: Run the layout detector**

Run the command from Global Constraints. Expected: `[]`. Fix any reported layout finding without adding new tokens.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/agent.html \
        modules/ndopify-partner-central/src/main/html/assets/css/agent.css
git commit -m "feat(npc): prototype of another agent's profile page"
```

---

### Task 2: `?id=` fixtures and fallbacks

**Files:**
- Create: `modules/ndopify-partner-central/src/main/html/assets/js/agent.js`
- Modify: `modules/ndopify-partner-central/src/main/html/agent.html` (script tag at the end of `<body>`)

**Interfaces:**
- Consumes every DOM hook listed in Task 1.
- Produces: fixture ids `1001` (Clarisse Mbarga) and `1002` (Paul Etame); unknown ids show `[data-agent-missing]`; `?back=` sets `[data-back-link]`.
- Adds `<script src="assets/js/agent.js?v=1" defer></script>` after `partner-central.js` in `agent.html`.

- [ ] **Step 1: Confirm the gap**

Open `agent.html?id=1002`. Expected: it still shows Clarisse Mbarga — fixture switching does not exist yet. This is the failing check for this task.

- [ ] **Step 2: Implement `agent.js`**

Vanilla ES5 IIFE, using the `$` / `$all` helpers style of the other prototype scripts. Fixtures:

```js
var AGENTS = {
    "1001": {
        firstName: "Clarisse", lastName: "Mbarga", verified: true,
        agentType: "INDEPENDANT", city: "Douala",
        neighborhoods: ["Bonapriso", "Akwa", "Bonanjo"],
        joinedAt: "2023-03-14", whatsappNumber: "+237699123456",
        biography: "Démarcheur à Douala, je connais Bonapriso, Akwa et Bonanjo rue par rue. " +
            "J'accompagne les familles et les jeunes actifs de la recherche à la signature du bail, " +
            "avec des biens vérifiés et des visites organisées."
    },
    "1002": {
        firstName: "Paul", lastName: "Etame", verified: true,
        agentType: "REAL_ESTATE_AGENT", city: "Yaoundé",
        neighborhoods: ["Bastos", "Biyem-Assi"],
        joinedAt: "2024-09-02", whatsappNumber: null, biography: ""
    }
};

var AGENT_TYPE_LABELS = {
    INDEPENDANT: "Indépendant",
    REAL_ESTATE_AGENT: "Rattaché à une agence immobilière"
};
```

Render rules, all guarded (`hidden = true` when the data is missing):

- `?id=` absent → `1001`. Unknown id → hide `[data-agent]`, show `[data-agent-missing]`; stop.
- Name, initials (first letter of each name, uppercase), type label, city, "Membre depuis " + `Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric" }).format(new Date(joinedAt))`, biography. Hide `[data-agent-verified]` when not verified; hide `[data-agent-type]` when there is no label; hide `[data-agent-place]` when there is no city; hide `[data-agent-since]` when there is no date.
- Rebuild `[data-agent-neighborhoods]` as `.agent-sectors__chip` spans; hide `[data-agent-sectors]` when both city and neighbourhoods are empty; hide `[data-agent-sectors-city]` when there is no city (the chips stay).
- `[data-agent-about]` hidden when the biography is empty; `[data-agent-whatsapp]` hidden when there is no number, otherwise `href = "https://wa.me/" + whatsappNumber.replace(/\D/g, "")`.
- If `photoUrl` is set (no fixture uses it), unhide `[data-agent-photo-image]`, set `src` and `alt = ""`, hide the initials.
- `?back=`: read the raw value; accept it only when it is non-empty, does not contain `:`, and does not start with `//` (rejects `javascript:`, `http:` and protocol-relative URLs); set `[data-back-link]` href and text "← Retour". Otherwise leave `index.html` and "← Retour à l'espace agent".
- Run on `DOMContentLoaded`; guard every element so the script is inert on other pages.

- [ ] **Step 3: Verify every state**

- `agent.html` → Clarisse Mbarga, "Indépendant · Douala", "Membre depuis mars 2023", bio, WhatsApp href `https://wa.me/237699123456`.
- `agent.html?id=1002` → Paul Etame, "Rattaché à une agence immobilière · Yaoundé", "Membre depuis septembre 2024", chips Bastos / Biyem-Assi, no "À propos" section, no WhatsApp button.
- `agent.html?id=9999` → "Agent introuvable" panel, profile hidden, exactly one visible `<h1>`.
- `agent.html?id=1001&back=index.html%23mandates-title` → back link points there and reads "← Retour".
- `agent.html?back=javascript:alert(1)` → ignored, back link still `index.html` with the full label.
- Disable JavaScript and reload `agent.html` → the complete Clarisse profile is still readable and the back link works.
- At 320 px, paste a 500-character biography into devtools; it wraps with no horizontal scroll.

- [ ] **Step 4: Run the layout detector again**

Run the command from Global Constraints. Expected: `[]`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/agent.html \
        modules/ndopify-partner-central/src/main/html/assets/js/agent.js
git commit -m "feat(npc): agent profile fixtures and fallbacks"
```
