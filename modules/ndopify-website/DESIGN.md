---
version: alpha
name: Ndopify Design System
description: Design tokens and UI guidance for the Ndopify website, sourced from the canonical Ndopify design doc.
colors:
  primary: "#1d7edf"
  background-default: "#ffffff"
  background-secondary: "#e4edf7"
  background-success: "#f0fdf4"
  background-info: "#dbeafe"
  background-warning: "#fef3c7"
  background-error: "#fef2f2"
  text-default: "#040404"
  text-disabled: "#808080"
  text-success: "#155724"
  text-info: "#1e3a8a"
  text-warning: "#78350f"
  text-error: "#8b0000"
  border-default: "#dfdfdf"
  border-success: "#dcfce7"
  border-info: "#bfdbfe"
  border-warning: "#fef08a"
  border-error: "#fee2e2"
  surface-lowest: "#ffffff"
  surface-low: "#f8f8f8"
  surface-default: "#dbeafe"
  surface-high: "#1d7edf"
typography:
  h1:
    fontFamily: Lora
    fontWeight: 700
    fontSize: "3.2rem"
  h2:
    fontFamily: Lora
    fontWeight: 700
    fontSize: "2.4rem"
  h3:
    fontFamily: Lora
    fontWeight: 700
    fontSize: "1.6rem"
  h4:
    fontFamily: Lora
    fontWeight: 700
    fontSize: "1.2rem"
  body-md:
    fontFamily: Montserrat
    fontSize: "1rem"
    fontWeight: 400
  label-success:
    fontFamily: "{typography.body-md.fontFamily}"
    fontSize: "{typography.body-md.fontSize}"
  label-info:
    fontFamily: "{typography.body-md.fontFamily}"
    fontSize: "{typography.body-md.fontSize}"
  label-warning:
    fontFamily: "{typography.body-md.fontFamily}"
    fontSize: "{typography.body-md.fontSize}"
  label-error:
    fontFamily: "{typography.body-md.fontFamily}"
    fontSize: "{typography.body-md.fontSize}"
  button:
    fontFamily: "{typography.body-md.fontFamily}"
    fontSize: "1rem"
    fontWeight: 600
spacing:
  sm: "0.25rem"
  md: "0.50rem"
  lg: "0.75rem"
  xl: "1.5rem"
rounded:
  full: "30px"
assets:
  logo-light: src/main/html/assets/images/logo.png
  logo-dark: src/main/html/assets/images/logo-dark.png
  icon-light: src/main/html/assets/images/icon.png
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.lg} {spacing.xl}"
  button-primary-hover:
    backgroundColor: "#111184"
    textColor: "#ffffff"
  button-secondary:
    backgroundColor: "{colors.background-secondary}"
    textColor: "{colors.text-default}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.lg} {spacing.xl}"
  button-outline:
    backgroundColor: "{colors.background-default}"
    textColor: "{colors.text-default}"
    borderColor: "{colors.border-default}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    padding: "{spacing.lg} {spacing.xl}"
  alert-success:
    backgroundColor: "{colors.background-success}"
    textColor: "{colors.text-success}"
    borderColor: "{colors.border-success}"
    typography: "{typography.label-success}"
  alert-info:
    backgroundColor: "{colors.background-info}"
    textColor: "{colors.text-info}"
    borderColor: "{colors.border-info}"
    typography: "{typography.label-info}"
  alert-warning:
    backgroundColor: "{colors.background-warning}"
    textColor: "{colors.text-warning}"
    borderColor: "{colors.border-warning}"
    typography: "{typography.label-warning}"
  alert-error:
    backgroundColor: "{colors.background-error}"
    textColor: "{colors.text-error}"
    borderColor: "{colors.border-error}"
    typography: "{typography.label-error}"
  table-header:
    backgroundColor: "{colors.surface-high}"
    textColor: "#ffffff"
    typography: "{typography.body-md}"
  table-row-default:
    backgroundColor: "{colors.surface-lowest}"
    textColor: "{colors.text-default}"
    typography: "{typography.body-md}"
  table-row-alt:
    backgroundColor: "{colors.surface-low}"
    textColor: "{colors.text-default}"
    typography: "{typography.body-md}"
  card-title:
    typography: "{typography.body-md}"
    textAlign: center
---

## Overview

Ndopify is a rental platform whose brand promise is trust and verification — tagline "Logements vérifiés.
Locations fiables" (French) / "Verified homes. Trusted rentals" (English). The UI should read as clean,
trustworthy, and mobile-money-oriented: a bright white surface, a single confident blue accent (`primary`) for
calls to action, soft pill-shaped buttons, and a serif/sans pairing (Lora headings over Montserrat body copy) that
feels approachable but credible. Favor generous whitespace and one clear primary action per screen over dense,
busy layouts.

Logo and icon assets checked into this repo, under `src/main/html/assets/images/`:

- `logo.png` — full wordmark, dark/black-on-transparent, for use on light surfaces (Background/Default,
  Background/Secondary). Matches the source doc's "Logo (Light variant)" Canva design.
- `logo-dark.png` — full wordmark, light/white-on-transparent, for use on dark surfaces (the primary blue or other
  dark backgrounds). Renders as just the blue-and-black mark with no visible text when previewed on a white
  background — that's expected, the wordmark is white. Matches the source doc's "Logo (Dark variant)" Canva design.
- `icon.png` — mark only (no wordmark), dark/black-on-transparent, for use on light surfaces. Matches the source
  doc's "Icon (Light variant)".

The source design doc also references an "Icon (Dark variant)" — a light-on-dark rendering of the icon-only mark,
for use on dark surfaces — but that file isn't checked into this repo yet, and the source doc points to the same
Canva/Drive link for both icon variants, so its actual appearance can't be confirmed from the doc alone:
https://www.canva.com/design/DAHWIgFphOI/tjpLLzm4BpYLfYebj-x4gQ/edit /
https://drive.google.com/file/d/1oobn7omvASlwKarTBfhCi5A8Xq7wqyqU/view?usp=drive_link. Don't invent a light-colored
icon asset; fall back to `icon.png` or flag the missing asset instead. No sizing or clear-space rules are
documented yet — confirm with design before scaling or cropping any mark.

## Colors

- **Primary (`#1d7edf`):** the sole accent color — used for the primary button and other core interactive elements.
- **Background (`#ffffff` default, `#e4edf7` secondary):** white is the base surface; the soft blue-tinted
  secondary is used for secondary buttons.
- **Surface (`#ffffff` lowest, `#f8f8f8` low, `#dbeafe` default, `#1d7edf` high):** a separate palette the source
  doc defines specifically for layered/elevated content — currently only consumed by the Tables component
  (`surface-lowest`/`surface-low` alternating rows, `surface-high` header). `surface-high` and `surface-low`
  happen to share hex values with `primary` and the table's old literal gray respectively, but they're distinct
  named tokens in the source doc — don't collapse them into `primary`/`background-*`. `surface-default`
  (`#dbeafe`, incidentally identical to `background-info`) isn't consumed by any component yet.
- **Text (`#040404` default, `#808080` disabled):** near-black for readable body/heading text; gray for disabled
  states. `text-disabled` isn't wired into any component below — the source doc defines the color but no
  disabled-state component variant (e.g. a disabled button) yet, so the linter flags it as orphaned; don't invent
  a disabled button/field spec to silence that, wait for the source doc.
- **Status colors (success / info / warning / error):** each status has a matching background, text, and border
  triad (e.g. success = `#f0fdf4` background / `#155724` text / `#dcfce7` border) used together for alerts, badges,
  and form validation — never mixed across statuses. The source doc lists a single value per status/text/border
  row (no distinct dark-theme values), i.e. there is no separate dark-theme palette defined yet.

## Typography

- **Headings:** `Lora`, weight 700 (bold) — sizes H1 `3.2rem`, H2 `2.4rem`, H3 `1.6rem`, H4 `1.2rem`.
- **Body:** `Montserrat`, weight 400, base size `1rem`.
- **Buttons:** `Montserrat`, weight 600 (semi-bold), size `1rem`, normal case (no uppercase transform).
- **Font source:** the source doc now explicitly tags both `Lora` and `Montserrat` as Google Fonts. Load them via
  the Google Fonts stylesheet/`@import` (or self-host the same families) rather than assuming a system font
  fallback is acceptable — this isn't a `typography.*` sub-token the DESIGN.md spec models (there's no
  `fontProvider`/`fontUrl` field), so it's documented here as implementation guidance instead.
- The source doc doesn't specify a distinct typeface/size for table or card text — both default to body typography
  (`Montserrat`, `1rem`, weight 400) until the source doc says otherwise.

## Layout

Spacing follows a small, fixed scale rather than a grid system: `sm` (`0.25rem`), `md` (`0.50rem`), `lg`
(`0.75rem`), and `xl` (`1.5rem`). Buttons use `lg` for vertical padding and `xl` for horizontal padding; apply the
same scale for consistent rhythm between other UI elements until a grid/breakpoint system is defined.

## Elevation & Depth

The design is otherwise flat — the only elevation cue is a soft blue glow under the primary button
(`0px 4px 12px rgba(29, 126, 223, 0.25)`), signaling it as the page's main call to action. Secondary and outline
buttons, tables, cards, and all other surfaces stay shadow-free — the source doc defines no elevation beyond the
primary button glow.

## Shapes

Every interactive control is fully rounded ("pill", `30px` radius) — there are no sharp-cornered buttons. The
outline button variant additionally uses a `1px solid` border in `border-default` (`#dfdfdf`) since it has no
fill to define its edge. The source doc defines no radius for tables or cards — leave their corners unstyled
(square) until specified.

## Components

### Buttons

| Variant   | Background            | Text         | Border                          | Elevation        |
|-----------|------------------------|--------------|----------------------------------|------------------|
| Primary   | Primary                | White        | none                             | Primary glow     |
| Secondary | Background/Secondary   | Text/Default | none                             | none             |
| Outline   | transparent            | Text/Default | `1px solid` Border/Default       | none             |

All variants share button typography and `lg`/`xl` padding, and are pill-shaped. On hover, the primary variant
darkens to `#111184` background with white text (`button-primary-hover`). The source doc does not define hover
states for the Secondary or Outline variants — don't invent one; leave them unstyled on hover until specified.

### Alerts / status badges

Each status (success, info, warning, error) pairs its background and text tokens; add the matching border token
(e.g. `border-success` with `alert-success`) when a bordered treatment is needed. `borderColor` isn't one of the
DESIGN.md spec's recognized component sub-tokens (only `backgroundColor`, `textColor`, `typography`, `rounded`,
`padding`, `size`, `height`, `width` are) — the linter will warn on it, but it's kept here anyway since it's the
most direct way to express a real, source-doc-defined border color per component; don't drop it just to silence
the warning.

### Tables

- **Header (`table-header`):** `surface-high` (`#1d7edf`) background, white text.
- **Content rows:** alternate between `surface-lowest` (`table-row-default`, `#ffffff`) and `surface-low`
  (`table-row-alt`, `#f8f8f8`) for readability on long rows; both use `text-default` for text. Use the `surface-*`
  tokens here, not `primary`/`background-default`/`background-secondary` — the source doc ties Tables
  specifically to the Surface palette, even though some values coincide numerically. The source doc specifies
  the alternation itself but not a starting row (first row default vs. alt) — pick either, consistently, per
  table.

### Cards

- **Title (`card-title`):** center-aligned. This is the only card styling the source doc defines — it does not
  specify card background, border, padding, or radius. `textAlign` isn't one of the spec's recognized component
  sub-tokens, same caveat as `borderColor` above: kept anyway since it's the most direct expression of a real,
  source-doc-defined rule. Don't invent the rest of the card's styling (background/border/padding/radius); fall
  back to plain text on the page background until the source doc is completed.

### Form fields

- **Field identifiers are always English, regardless of page language:** every `name`, `id`, `for`, and
  `aria-labelledby` on a form control (and the matching group-label `id`, e.g. `*-label`) must be an English
  token — `city`, `rent`, `firstname`, `property_type`, `category`, etc. — even on fully French-language pages.
  Only the visible label text, placeholder, hint, and option text stay in the page's language; the DOM-facing
  identifier never does. When the same concept appears on more than one form under a different French name (e.g.
  the "property type" concept was `type_propriete` on one wizard and `type_bien` on another), standardize on one
  English name across all forms rather than translating each literally — this is what makes the field names
  reusable/greppable across pages. This isn't a source-doc rule (the source doc only specifies visible copy) but
  is now the established implementation pattern — don't reintroduce a French `name`/`id`/`value` when adding or
  regenerating a field.
- **Radio/checkbox `value`s are English tokens too** (e.g. `value="yes"`/`"no"`/`"morning"`, not
  `"oui"`/`"non"`/`"matin"`), for the same reason — they're data, not copy.
- **Phone number:** use the `int-tel-input` library (https://github.com/jackocnr/intl-tel-input) — don't
  hand-roll phone input masking/validation.
- **Country:** use a dropdown populated with the full list of countries, not a free-text field.
- **Amount / currency (FCFA):** use the `AutoNumeric.js` library (https://github.com/autoNumeric/autoNumeric) —
  don't hand-roll currency masking/validation. Per the source doc's exact spec:
  - Element: `<input type="text" inputmode="decimal">`.
  - Suffix: `FCFA`, placed after the amount with a space (e.g. `10 000 FCFA`).
  - Thousands separator: a space (e.g. `1 000 000`).
  - Decimal precision: `0` (`decimalPlaces: 0`) — XAF/FCFA has no subunit in practice.
  - Minimum value: `0` (`minimumValue: '0'`).
- **Duration-in-months fields (e.g. `advance`/`deposit` on `louer.html`):** when a field expresses a number of
  months rather than a sum, use a plain `<select>` with options `0` (visible text "pas d'avance"/"pas de caution")
  through `24` ("24 mois") — not an AutoNumeric/FCFA-masked text input. Don't conflate "duration" fields with
  "amount" fields just because both relate to rent terms.
- **Checkbox / radio option groups:** stack options in a single column, one option per row — not a horizontal/wrapping
  grid. Each option is its own row: `1px solid` `border-default`, `md` radius, `lg` padding, 44px minimum height
  (touch-target size), `md` gap between rows, the input itself fixed at 20×20px. This isn't a source-doc rule (the
  source doc doesn't specify option-group layout) but is now the established implementation pattern — follow it for
  any new checkbox/radio group rather than reintroducing a horizontal/auto-fit layout.

### Multi-step forms (wizards)

Not a source-doc rule either, but the established implementation pattern for `rechercher.html` / `louer.html` /
`joindre.html` — follow it for any new multi-step form:

- **Progress indicator:** one `role="progressbar"` element per form, with real `aria-valuemin`/`aria-valuemax`/
  `aria-valuenow`/`aria-valuetext` (e.g. `"Étape 2 sur 6""`), paired with a visible text label — not a bar-only
  indicator.
- **One `<fieldset class="step">` per step**, toggled via the `hidden` attribute so only one step is in the layout
  at a time.
- **Step titles:** each step's `<legend>` is kept (it's the fieldset's accessible name) but visually hidden via the
  `.visually-hidden` utility class, paired with a visible `<h3 aria-hidden="true">` carrying the same text — this
  avoids double-announcing the title to screen readers while still showing it to sighted users. Precede the `<form>`
  with one `<h2 class="visually-hidden">Étapes du formulaire</h2>` so the heading order (`h1` → `h2` → `h3`×N) never
  skips a level. The confirmation step is the exception: it uses a plain visible `<h2>` inside `.confirmation`, not
  the legend/h3 pattern, since it's the form's terminal state rather than a step.
- **Navigation:** `btn-outline` "Précédent" / `btn-primary` "Suivant" per step; the real submit button
  (`btn-primary`, type `submit`) appears only on the final data step, never earlier.
- **Validation:** required fields validate inline on `next`, blocking advance until valid; focus moves to the first
  invalid field.

### Not yet specified

The source doc does not define card background/border/padding/radius (beyond title alignment, above), Navigation,
or Tabs — don't invent styling for any of these; flag them as missing if needed before the source doc is updated.

## Do's and Don'ts

- Do use `primary` for exactly one call to action per screen — it's the only accent color in the system.
- Do keep every button pill-shaped; don't introduce sharp-cornered or differently-rounded buttons.
- Do alternate table row backgrounds between `surface-lowest` (`#ffffff`) and `surface-low` (`#f8f8f8`) for
  readability; don't skip the alternation, don't substitute `background-default`/`background-secondary`, and
  don't invent a third row color.
- Don't give secondary or outline buttons a shadow — only the primary button has elevation.
- Don't mix a status's background with a different status's text or border color.
- Don't invent new hex values, font sizes, or spacing values — extend the token tables above instead.
- Do stack checkbox/radio option groups vertically (one option per row); don't reintroduce a horizontal/auto-fit
  grid for them.
- Do pair a visually-hidden `<legend>` with a visible `<h3 aria-hidden="true">` for wizard step titles, preceded by
  one visually-hidden `<h2>` per form — don't use a bare `<h3>`/`<legend>` alone (loses either the accessible name
  or the heading hierarchy).
- When wiring a CDN asset (e.g. intl-tel-input), verify the exact file path actually exists for the pinned
  version — some versions only publish an unminified CSS build (no `.min.css`); a guessed filename 404s silently
  and the component renders unstyled.
- Do keep every form field's `name`/`id`/`for`/`aria-labelledby`/`value` in English even on French-language pages
  (e.g. `name="city"` with label text "Ville"); don't translate the visible label and then mirror that French
  word into the identifier too — see "Form fields" above.
- Note: white text on `primary` (`#1d7edf`) measures 4.12:1 contrast, below WCAG AA's 4.5:1 minimum for normal
  text. This comes directly from the source doc's brand color and hover state — don't silently "fix" it by
  darkening `primary` or lightening the button text; flag it to design instead if AA compliance is required.
