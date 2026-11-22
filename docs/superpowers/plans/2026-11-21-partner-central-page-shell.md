# Partner Central Page Shell Fragments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Partner Central's duplicated page shell — the fixed `<head>`, the site header and the 173 repeated icons — into three Thymeleaf fragments, with the rendered pages unchanged apart from an invisible 0.25-unit check-icon normalisation.

**Architecture:** Three new fragments under `src/main/resources/templates/fragments/` (`head.html`, `site-header.html`, `icon.html`). Every page replaces its `<head>` with a `head(...)` call, its skip link + `<header>` with a `header(...)` call, and its repeated icons with `icon.html` fragments. No Kotlin, CSS, JS or prototype change; partner-central's 19 template tests render every page through the new fragments and are left unmodified.

**Tech Stack:** Thymeleaf 3, Spring Boot, Kotlin, JUnit 5 + jsoup template tests, JaCoCo.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/236

## Global Constraints

- `ndopify-partner-central` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, schema, API, CSS or JavaScript change.
- Nothing under `src/main/html/` changes. It is the prototype: never served, never edited here.
- UI text stays French and byte-identical; code, comments and KDoc stay English.
- `mvn verify` in `modules/ndopify-partner-central` must be green (ktlint, 19 template tests, JaCoCo gate). It needs neither MySQL nor a running server.
- The 19 existing template tests are **not** modified. They are the regression net; a fragment call that does not parse fails the render.
- Conventional commits, three of them: `refactor(npc): extract the page head into a fragment`, `refactor(npc): extract the site header into a fragment`, `refactor(npc): extract the repeated icons into a fragment`. Body ends with `Refs #236`.
- Fragment-call syntax (verified against Thymeleaf 3 / SpringEL in this module):
  - the number of arguments must equal the fragment signature — pass `null` for an absent description, list or scripts;
  - a list argument is a SpEL inline list: `${ {'home','listing'} }`; `{...}` alone does not parse;
  - a plain string with commas is fine in single quotes; a string with an apostrophe must be a literal substitution: `|Vérification d'identité|`;
  - fragment arguments may be full expressions (`${...}`, `@{...}`, conditionals, `|...|`).
- First-party assets stay at `/assets/css/<name>.css` and `/assets/js/<name>.js`, built with link expressions so the context path still applies.
- `styles` and `scripts` never include `partner-central.css` / `partner-central.js` as special cases: the head fragment owns `partner-central.css`, and `partner-central.js` is an ordinary entry of `scripts` where the page loads it.

## Review Focus

- **A page loses its title or description:** 13 pages have dynamic titles (agent, visit, mandate, mandates close/edit/customer-edit, listing, listing photos/section, find-property, offer-property, join, login). A wrong argument renders a plausible wrong title. Task 1's page list pins every argument, and the template tests assert `page.title()` on several pages.
- **A page loses a stylesheet or script, or their order changes:** the listing page (5 CSS, 2 JS), profile/edit and kyc are the fragile ones. Task 1's page list is the source of truth; Task 1 step 4 renders all 22 heads.
- **The account menu appears where it must not, or disappears:** `/login`, `/error/403`, `/error/404` and `/join` must render no `.user-menu`; the 16 signed-in pages must. The Home/Agent/Mandate/Profile/Listing/Kyc/Error template tests assert this; Task 2 runs them and checks `/login` by hand, the one page with no template test.
- **A guarded icon is always or never shown:** the success/warning/error flash icons in `listing.html` and `mandate.html` (including `matchOutcomeStyle`) and the `line.full` check rely on the `th:block` wrapper, since `th:if` on a `th:replace` host is discarded. Task 3 step 7 checks them visually.
- **An icon loses its class:** the four confirmation checks use `confirmation__icon`, not `icon`; passing the wrong class argument silently shrinks or restyles them. Task 3 step 4 names the four call sites.

---

### Task 1: The head fragment

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/head.html`
- Modify: the 22 page templates under `modules/ndopify-partner-central/src/main/resources/templates/` (every `.html` outside `fragments/`)

**Interfaces:**
- Produces: fragment signature `head(title, description, styles, scripts, cdn)` in `templates/fragments/head.html`.
  - `title`: the page's full `<title>` text, an expression.
  - `description`: the `<meta name="description">` content, or `null`.
  - `styles`: a SpEL list of bare CSS names, or `null`; each becomes `/assets/css/<name>.css` after `partner-central.css`.
  - `scripts`: a SpEL list of bare JS names, or `null`; each becomes `/assets/js/<name>.js` with `defer`.
  - `cdn`: `true` to load intl-tel-input and slim-select (CSS before `partner-central.css`, JS before the page scripts).

- [ ] **Step 1: Baseline**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: `BUILD SUCCESS`; 19 template tests green before any change.

- [ ] **Step 2: Create `fragments/head.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title, description, styles, scripts, cdn)">
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="${title}">Ndopify Partner Central</title>
    <meta th:if="${description != null}" name="description" th:content="${description}">
    <meta name="robots" content="noindex, nofollow">
    <meta name="theme-color" content="#ffffff">
    <link rel="icon" type="image/png" th:href="@{/assets/images/icon.png}">
    <link rel="apple-touch-icon" th:href="@{/assets/images/icon.png}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Lora:wght@700&amp;family=Montserrat:wght@400;600&amp;display=swap">
    <th:block th:if="${cdn}">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/css/intlTelInput.min.css">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/slim-select@4.3.0/dist/slimselect.css">
    </th:block>
    <link rel="stylesheet" th:href="@{/assets/css/partner-central.css}">
    <link th:each="name : ${styles}" rel="stylesheet" th:href="@{/assets/css/{name}.css(name=${name})}">
    <th:block th:if="${cdn}">
        <script src="https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/js/intlTelInput.min.js" defer></script>
        <script src="https://cdn.jsdelivr.net/npm/slim-select@4.3.0/dist/slimselect.iife.js" defer></script>
    </th:block>
    <script th:each="name : ${scripts}" th:src="@{/assets/js/{name}.js(name=${name})}" defer></script>
</head>
</html>
```

- [ ] **Step 3: Replace each page's `<head>` with its fragment call**

Every page's `<head>...</head>` becomes an empty `<head>` carrying `th:replace`. The call shape is:

```html
<head th:replace="~{fragments/head :: head(<title>, <description>, <styles>, <scripts>, <cdn>)}"></head>
```

`<description>` is the page's current `<meta name="description">` content verbatim, or `null` for `error/403.html` and `error/404.html`. When that text contains an apostrophe, write it as a literal substitution (`|…|`); otherwise plain single quotes.

Per page, `<title>` — keep the page's current `<title>` expression — and the two lists (bare names, in the current order, without `partner-central.css`):

- `agent.html` — title: `${missing} ? 'Agent introuvable — Ndopify Partner Central' : |${profile.name} — Ndopify Partner Central|`; styles: `{'agent'}`; scripts: `{'partner-central'}`; cdn: `false`
- `error/403.html` — title: `'Accès refusé — Ndopify Partner Central'`; styles: `null`; scripts: `null`; cdn: `false`; description: `null`
- `error/404.html` — title: `'Page introuvable — Ndopify Partner Central'`; styles: `null`; scripts: `null`; cdn: `false`; description: `null`
- `find-property.html` — title: `${step == 'confirmation'} ? 'Demande envoyée — Ndopify' : 'Trouver un bien — Ndopify'`; styles: `{'join','find-property'}`; scripts: `{'util','find-property'}`; cdn: `true`
- `index.html` — title: `'Espace agent — Ndopify Partner Central'`; styles: `{'home','listing','visit'}`; scripts: `{'partner-central','home'}`; cdn: `false`
- `join.html` — title: `${step == 'confirmation'} ? 'Candidature envoyée — Ndopify Partner Central' : 'Rejoindre Ndopify — Ndopify Partner Central'`; styles: `{'join'}`; scripts: `{'util','join'}`; cdn: `true`
- `kyc.html` — title: `|Vérification d'identité — Ndopify Partner Central|`; styles: `null`; scripts: `{'partner-central','direct-upload','kyc'}`; cdn: `false`
- `listing.html` — title: `${listing} ? |${listing.title} — Ndopify Partner Central| : 'Annonce — Ndopify Partner Central'`; styles: `{'join','find-property','home','listing'}`; scripts: `{'partner-central','listing'}`; cdn: `false`
- `listing/photos.html` — title: `${section} ? ${section.pageTitle} : 'Annonce — Ndopify Partner Central'`; styles: `{'join','find-property','home','listing'}`; scripts: `{'partner-central','direct-upload','listing'}`; cdn: `false`
- `listing/section.html` — title: `${section} ? ${section.pageTitle} : 'Annonce — Ndopify Partner Central'`; styles: `{'join','find-property','home','listing'}`; scripts: `{'partner-central','listing'}`; cdn: `false`
- `login.html` — title: `${step == 'code'} ? 'Code de connexion — Ndopify Partner Central' : 'Connexion — Ndopify Partner Central'`; styles: `null`; scripts: `{'partner-central'}`; cdn: `false`
- `mandate.html` — title: `${mandate} ? |${mandate.title} — Ndopify Partner Central| : 'Mandat — Ndopify Partner Central'`; styles: `{'join','home','listing','visit'}`; scripts: `{'partner-central','mandate'}`; cdn: `false`
- `mandate/close.html` — title: `${close} ? |Fermer le mandat ${close.mandateId} — Ndopify Partner Central| : 'Fermer le mandat — Ndopify Partner Central'`; styles: `{'join','find-property','home'}`; scripts: `{'partner-central','mandate'}`; cdn: `false`
- `mandate/contract.html` — title: `'Envoyer le mandat — Ndopify Partner Central'`; styles: `{'find-property','home'}`; scripts: `{'partner-central','mandate'}`; cdn: `false`
- `mandate/customer-edit.html` — title: `|Modifier les coordonnées ${mandate?.words?.of ?: 'du client'} — Mandat n° ${mandateId} — Ndopify Partner Central|`; styles: `{'join','home'}`; scripts: `{'util','partner-central','mandate'}`; cdn: `true`
- `mandate/edit.html` — title: `${answers} ? |Modifier le mandat ${mandate.id} — Ndopify Partner Central| : 'Modifier le mandat — Ndopify Partner Central'`; styles: `{'join','home'}`; scripts: `{'partner-central','mandate'}`; cdn: `true`
- `mandate/visit.html` — title: `'Planifier la visite — Ndopify Partner Central'`; styles: `{'rejoindre','home','visit'}`; scripts: `{'partner-central'}`; cdn: `false`
- `offer-property.html` — title: `${step == 'confirmation'} ? 'Bien enregistré — Ndopify' : 'Proposer un bien — Ndopify'`; styles: `{'join','find-property','offer-property'}`; scripts: `{'util','offer-property'}`; cdn: `true`
- `profile/edit.html` — title: `'Modifier mon profil — Ndopify Partner Central'`; styles: `{'join','profile'}`; scripts: `{'util','partner-central','join'}`; cdn: `true`
- `profile/photo.html` — title: `'Photo de profil — Ndopify Partner Central'`; styles: `{'profile'}`; scripts: `{'partner-central','direct-upload','kyc'}`; cdn: `false`
- `telegram.html` — title: `'Connecter Telegram — Ndopify Partner Central'`; styles: `{'telegram'}`; scripts: `{'partner-central'}`; cdn: `false`
- `visit.html` — title: `${page} ? |Visite de ${page.customerName} — Ndopify Partner Central| : 'Visite — Ndopify Partner Central'`; styles: `{'join','home','listing','visit'}`; scripts: `{'partner-central','visit'}`; cdn: `false`

Write each list literally as a SpEL inline list, e.g. `index.html`:

```html
<head th:replace="~{fragments/head :: head('Espace agent — Ndopify Partner Central', 'Espace agent Ndopify Partner Central.', ${ {'home','listing','visit'} }, ${ {'partner-central','home'} }, false)}"></head>
```

and `error/404.html`:

```html
<head th:replace="~{fragments/head :: head('Page introuvable — Ndopify Partner Central', null, null, null, false)}"></head>
```

- [ ] **Step 4: Verify no page keeps the fixed block**

Run:

```bash
grep -rln '<meta charset' modules/ndopify-partner-central/src/main/resources/templates
grep -rln 'partner-central.css' modules/ndopify-partner-central/src/main/resources/templates
```

Expected: only `templates/fragments/head.html` in both. Any page still listed has a leftover `<head>` block.

- [ ] **Step 5: Render every page**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: `BUILD SUCCESS`, 19 template tests green. A malformed call, a wrong argument count or an unresolvable expression fails the page's render here. `login.html` is the one page with no template test: start the app and open `/login` to check its head renders with its stylesheet.

- [ ] **Step 6: Full gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: `BUILD SUCCESS`, ktlint clean, coverage gate passes.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates
git commit -m "refactor(npc): extract the page head into a fragment" -m "Refs #236"
```

### Task 2: The site-header fragment

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/site-header.html`
- Modify: the 19 page templates that share the Ndopify header — the 16 signed-in pages plus `login.html`, `error/403.html`, `error/404.html`
- Keep inline: `join.html`, `find-property.html`, `offer-property.html` (tenant-branded headers; join also has its *Quitter* button)

**Interfaces:**
- Consumes: `fragments/user-menu.html :: menu` (unchanged).
- Produces: fragment signature `header(home, menu)` in `templates/fragments/site-header.html`, rendering the skip link, the `<header class="site-header">` with the Ndopify brand linking to `home`, and the account menu when `menu` is true.

- [ ] **Step 1: Create `fragments/site-header.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/*
  The page shell's skip link and header, on every page that uses the Ndopify brand: "home" is the brand's link
  (an @{} expression), "menu" renders the account menu — explicit, not inferred from "signedIn", because /login shows
  no account menu even to a signed-in visitor. Pages with a tenant-branded header (join, find/offer property) keep
  their own.
*/-->
<th:block th:fragment="header(home, menu)">
    <a class="skip-link" href="#main">Aller au contenu</a>
    <header class="site-header">
        <div class="site-header__inner">
            <a class="site-header__brand" th:href="${home}">
                <img th:src="@{/assets/images/logo.png}" alt="Ndopify, accueil" width="84" height="40">
            </a>
            <th:block th:if="${menu}">
                <nav th:replace="~{fragments/user-menu :: menu}"></nav>
            </th:block>
        </div>
    </header>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Replace the skip link and header in the 19 pages**

Each page's `<a class="skip-link" …>` plus `<header class="site-header">…</header>` becomes one call. The 16 signed-in pages (`index`, `agent`, `kyc`, `listing`, `listing/photos`, `listing/section`, `mandate`, `mandate/close`, `mandate/contract`, `mandate/edit`, `mandate/customer-edit`, `mandate/visit`, `profile/edit`, `profile/photo`, `telegram`, `visit`):

```html
<th:block th:replace="~{fragments/site-header :: header(@{/}, true)}"></th:block>
```

`login.html`:

```html
<th:block th:replace="~{fragments/site-header :: header(@{/login}, false)}"></th:block>
```

`error/403.html` and `error/404.html`:

```html
<th:block th:replace="~{fragments/site-header :: header(@{/}, false)}"></th:block>
```

Leave `join.html`, `find-property.html` and `offer-property.html` untouched.

- [ ] **Step 3: Verify the remaining headers are the three tenant ones**

Run:

```bash
grep -rln 'class="site-header"' modules/ndopify-partner-central/src/main/resources/templates
grep -rln 'skip-link' modules/ndopify-partner-central/src/main/resources/templates
```

Expected: `fragments/site-header.html`, `join.html`, `find-property.html`, `offer-property.html` — exactly those four.

- [ ] **Step 4: Render and gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: `BUILD SUCCESS`. The Home/Agent/Mandate/Profile/Listing/Kyc/Error template tests assert the account menu's presence and absence; `assertNull(page.selectFirst(".user-menu"))` on the error pages must still hold. Check `/login` by hand: no account menu, brand to `/login`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates
git commit -m "refactor(npc): extract the site header into a fragment" -m "Refs #236"
```

### Task 3: The icon fragment

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/icon.html`
- Modify: every template that uses one of the eight drawings below, including `fragments/agent-card.html`, `fragments/mandate-card.html`, `fragments/visit-card.html` and `fragments/visit-detail.html`

**Interfaces:**
- Produces: eight fragments in `templates/fragments/icon.html` — `close`, `warning`, `info`, `check(clazz)`, `clock`, `whatsapp`, `pin`, `chart`.
- Consumed by pages as `<svg th:replace="~{fragments/icon :: <name>}"></svg>`, and for `check` as `~{fragments/icon :: check('icon')}` or `~{fragments/icon :: check('confirmation__icon')}`. The argument count must match, so `check` is never called without parentheses.

- [ ] **Step 1: Create `fragments/icon.html`**

Copy each drawing verbatim from these representative occurrences (same `class`, `aria-hidden`, `focusable`, `viewBox`, paths and attribute order):

| Fragment | Copy from |
| --- | --- |
| `close` | `index.html`, the `<svg>` whose paths are `m7 7 6 6M13 7l-6 6` |
| `warning` | `index.html`, the triangle `M10 2.5 18.5 17h-17z` |
| `info` | `index.html`, the circle with `M10 9v5` and `circle cx="10" cy="6.25"` |
| `check(clazz)` | `index.html`, the circle with `m6.5 10.5 2.5 2.5 4.5-5` |
| `clock` | `fragments/visit-card.html`, the 24×24 circle with `M12 6v6l4 2` |
| `whatsapp` | `fragments/agent-card.html`, the brand path starting `M17.472 14.382` |
| `pin` | `visit.html`, the 24×24 path `M12 21s-7-6.2-7-11.5…` |
| `chart` | `fragments/mandate-card.html`, the 24×24 bars `M3 20h18` |

The file is a normal fragment file:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<svg th:fragment="close" class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…paths…</svg>
…
<svg th:fragment="check(clazz)" th:class="${clazz}" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…paths…</svg>
…
</body>
</html>
```

For `check`, use the `m6.5 10.5` path (the majority); the `m6.5 10.25` variant in `mandate.html` is normalised to it — the story's one intentional, invisible difference.

- [ ] **Step 2: Replace the unguarded occurrences**

Replace each occurrence with `<svg th:replace="~{fragments/icon :: <name>}"></svg>` (and `check('icon')` for the check). Per file, the number of `<svg` elements to remove:

- `agent.html`: warning 1, check 1, whatsapp 1
- `find-property.html`: close 2, warning 1, info 4, check 1
- `index.html`: close 3, warning 9, check 3, clock 1
- `join.html`: close 10, warning 2, info 5, check 1
- `kyc.html`: close 5, warning 2, info 4, check 2
- `listing.html`: close 2, warning 3, info 2, check 1, chart 1
- `listing/photos.html`: close 2, warning 1, info 1
- `listing/section.html`: close 3, warning 1, info 1
- `login.html`: close 2, info 2
- `mandate.html`: close 4, warning 6, info 1, check 4, clock 1, whatsapp 1, chart 2
- `mandate/close.html`: close 2, warning 3
- `mandate/contract.html`: info 1
- `mandate/customer-edit.html`: close 5, warning 1, info 1
- `mandate/edit.html`: close 11, warning 1, info 2
- `mandate/visit.html`: info 1
- `offer-property.html`: close 2, warning 1, info 3, check 1
- `profile/edit.html`: close 8, info 1
- `profile/photo.html`: close 1, info 1
- `telegram.html`: close 1, check 1
- `visit.html`: close 1, warning 3, info 3, check 2, clock 3, whatsapp 3, pin 5
- `fragments/agent-card.html`: whatsapp 1
- `fragments/mandate-card.html`: chart 2
- `fragments/visit-card.html`: clock 2
- `fragments/visit-detail.html`: clock 1

That is 173 replacements: close 64, warning 35, info 33, check 17, clock 8, whatsapp 6, pin 5, chart 5.

- [ ] **Step 3: Keep the `th:if`/`th:unless` guards with a `th:block`**

`th:if` on a `th:replace` host is discarded, so wrap each guarded icon instead of putting the condition on the replacing `<svg>`. In `listing.html` and `mandate.html`, the success/warning/error flash icons become:

```html
<th:block th:if="${outcomeStyle == 'success'}"><svg th:replace="~{fragments/icon :: check('icon')}"></svg></th:block>
<th:block th:if="${outcomeStyle == 'warning'}"><svg th:replace="~{fragments/icon :: warning}"></svg></th:block>
<th:block th:if="${outcomeStyle == 'error'}"><svg th:replace="~{fragments/icon :: close}"></svg></th:block>
```

The same three in `mandate.html` for `matchOutcomeStyle`, and the `line.full` check:

```html
<th:block th:if="${line.full}"><svg th:replace="~{fragments/icon :: check('icon')}"></svg></th:block>
```

Leave the `th:unless="${line.full}"` dash inline: it is a one-off.

- [ ] **Step 4: Pass `confirmation__icon` to the four confirmation checks**

In `find-property.html`, `join.html`, `kyc.html` and `offer-property.html`, the check inside `<span class="confirmation__mark">` uses the class `confirmation__icon`:

```html
<span class="confirmation__mark"><svg th:replace="~{fragments/icon :: check('confirmation__icon')}"></svg></span>
```

- [ ] **Step 5: Verify the counts**

Run:

```bash
grep -ro '<svg' modules/ndopify-partner-central/src/main/resources/templates | wc -l
```

Expected: `63` — the 55 SVGs that stay inline plus the 8 fragment definitions. Before this task the count was 228.

- [ ] **Step 6: Render and gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: `BUILD SUCCESS`, 19 template tests green.

- [ ] **Step 7: Check the guarded and confirmation icons by eye**

On the running app (server up, an approved mandate and a listing): the listing's *Annonce envoyée* success flash shows the check, a warning flash the triangle, an error flash the circle-X; the mandate page's match flashes show the same three; the match fit list shows the check only on full matches. The join/find/offer confirmation screens keep the large `confirmation__icon` check.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates
git commit -m "refactor(npc): extract the repeated icons into a fragment" -m "Refs #236"
```
