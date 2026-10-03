# Ndopify Website

Static, self-contained marketing + lead-capture website for Ndopify (Cameroon PropTech, Mobile-Money-based rental
platform) — three audiences (locataires, bailleurs, agents), each with a marketing page and a multi-step wizard
form. No build step, no framework: plain HTML/CSS/JS, content in French.

## Directory structure

```
DESIGN.md                      Design-token source of truth (colors, typography, spacing, components)
src/main/html/
  index.html                   Marketing page — locataires (tenants), CTA → rechercher.html
  bailleur.html                Marketing page — bailleurs (landlords), CTA → louer.html
  agent.html                   Marketing page — agents, CTA → joindre.html
  rechercher.html             Wizard — locataire: search criteria → agent matching
  louer.html                  Wizard — bailleur: list a property
  joindre.html                Wizard — agent: join the network
  assets/css/styles.css        Shared stylesheet (tokens from DESIGN.md)
  assets/js/main.js            Shared behavior: wizard navigation/validation, FAQ accordion, nav, field masking
  assets/images/               Logo/icon assets
.claude/
  commands/                    Project slash commands (below)
  skills/                      Vendored skills used by those commands (below)
```

## Running locally

No build step — serve the directory and open it in a browser:

```bash
cd src/main/html
python3 -m http.server 8080
# then open http://localhost:8080/index.html
```

## Design system

`DESIGN.md` is the single source of truth for colors, typography, spacing, and component styling, generated from
the canonical Ndopify design doc. Any new UI work should extend it rather than inventing new tokens — see its
"Do's and Don'ts" section.

## AI skills / commands

This repo uses Claude Code slash commands and skills to (re)generate the design system and the website from the
canonical Ndopify source docs, and to keep the UI design-system-compliant.

### Slash commands (`.claude/commands/`)

| Command | File | Purpose |
|---|---|---|
| `/init-design-system` | `init-design-system.md` | (Re)generates `DESIGN.md` — colors, typography, spacing, components — from the canonical Ndopify design doc. |
| `/init-website` | `init-website.md` | (Re)generates the full website under `src/main/html/` in three stages — journey mapping, brand-native generation, then critique/fix — using the three skills below in sequence. |

### Skills (`.claude/skills/`)

Vendored skills invoked by `/init-website` (and usable standalone for ad-hoc UX/design work):

| Skill | Purpose |
|---|---|
| `neo-user-journey` | UX journey mapping, synthetic persona walkthroughs, flow/step design, microcopy — used to plan wizard step order and KYC-disclosure copy before any HTML exists. |
| `power-design` | Generates brand-native HTML (pages or decks) from design tokens and a codified rulebook (20 web-design rules) — used for the actual page/CSS generation, with `DESIGN.md` as the brand source instead of a live site crawl. |
| `impeccable` | Frontend design critique + mechanical defect detection (contrast, heading structure, spacing, color/radius drift) — used to audit and fix the generated pages against `DESIGN.md` and accessibility rules. |

### Typical workflow

1. Update the canonical Ndopify source docs (website content / pitch / vision) if the product content changed.
2. Run `/init-design-system` if the brand/design tokens changed — updates `DESIGN.md`.
3. Run `/init-website` to regenerate the site from scratch, or hand-edit `src/main/html/*.html` /
   `assets/css/styles.css` for small changes, keeping `DESIGN.md`'s tokens and component rules in mind.
4. For any manual UI change, double-check it against `DESIGN.md`'s "Do's and Don'ts" — it also tracks a few
   deliberate exceptions (e.g. a known AA-contrast gap inherent to the brand's primary color) that shouldn't be
   "fixed" by changing brand colors.
