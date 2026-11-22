# ndopify-web Page Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move ndopify-web's duplicated page shell — the fixed head, header, footer and three icon sprites — into Thymeleaf fragments, with the rendered pages unchanged.

**Architecture:** Five new files under `src/main/resources/templates/fragments/` (`head.html`, `structured-data.html`, `site-header.html`, `site-footer.html`, `icon-sprite.html`). Every page replaces its `<head>` with one `head(...)` call, its skip link + header with `header(...)`, its footer with `footer(...)`, and its inline sprite with `sprite`. Two new template tests render the two pages that currently have no render test. No Kotlin, CSS, JavaScript, controller or prototype change.

**Tech Stack:** Thymeleaf 3, Spring Boot, Kotlin, JUnit 5 + jsoup template tests, JaCoCo.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/237

## Global Constraints

- `ndopify-web` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, schema, API, CSS or JavaScript change.
- Nothing under `src/main/html/` changes. It is the prototype: never served, never edited here.
- UI text stays French and byte-identical; code, comments and KDoc stay English.
- `mvn verify` in `modules/ndopify-web` must be green (ktlint, template tests, JaCoCo gate). It needs neither MySQL nor a running server.
- The 13 existing test classes are **not** modified. Two new tests are added: `VisitLinkTemplateTest` and `Error404TemplateTest`.
- Conventional commits, five of them: `test(web): render the visit-link and 404 pages`, `refactor(web): extract the page head into a fragment`, `refactor(web): extract the site header into a fragment`, `refactor(web): extract the site footer into a fragment`, `refactor(web): extract the icon sprite into a fragment`. Each body ends with `Refs #237`.
- Fragment-call syntax (as used by #236 in this repo):
  - the number of arguments must equal the fragment signature — pass `null` for an absent value;
  - a list argument is a SpEL inline list: `${ {'listing','proposal'} }`; `{...}` alone does not parse;
  - a string containing an apostrophe must be a literal substitution: `|Décrivez votre recherche ... jusqu'à la signature.|`;
  - `@{}` link expressions and `${...}` conditionals are valid arguments; a fragment expression (`~{...}`) is a valid argument for `structuredData`.
- First-party assets stay at `/assets/css/<name>.css` and `/assets/js/<name>.js`, built with link expressions so the context path still applies.
- Tenant-safe expressions in fragments: use `tenant?.name`, `tenant?.logoUrl`, `tenant?.iconUrl` (safe navigation is already used in `listing.html`). The 404 has no `tenant` in the model.
- The fragment call for the head **replaces** the whole `<head>`; the page keeps no head markup.

## Review Focus

- **A page gets a plausible but wrong head:** a wrong title, description, canonical, OG value, `robots`/`referrer` flag or stylesheet order renders a page that looks fine. Task 2's per-page list is the source of truth; the existing Index/Agent/Owner/Listing/Match/MatchLink tests assert the titles, canonical, `og:image`, robots and referrer.
- **The 404 throws on a missing tenant:** the 404 renders outside the tenant. Every fragment expression must be null-safe when `brand` is false, and `Error404TemplateTest` renders it with no tenant at all.
- **The header variant drifts:** `aria-current` on the wrong link, a nav on `match-link` / `visit-link` / `error/404`, or the wrong toggle label (`Locataires` vs `Menu`). Task 3's call list pins all nine; `VisitLinkTemplateTest` and `Error404TemplateTest` assert no `.site-nav`.
- **The footer variant drifts:** the wrong tagline, a nav on the 404, or a footer on `match-link` / `visit-link`. Task 4's call list pins all seven; `Error404TemplateTest` asserts no footer nav and no footer on `visit-link`.
- **The shared sprite misses a symbol:** `listing-body.html` uses `#i-bed`, `#i-bath`, `#i-area`, `#i-sofa`, `#i-car`, `#i-info`, `#i-check`, `#i-shield`, `#i-pin`, `#i-close` and is rendered from both `listing` and `match`; a missing symbol renders an empty box. Task 5 copies the 18 listing symbols plus the 6 visit-only drawings, and `MatchTemplateTest` already asserts `#i-check` / `#i-close`.

---

### Task 1: Characterization tests for the two uncovered pages

**Files:**
- Create: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitLinkTemplateTest.kt`
- Create: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/layout/Error404TemplateTest.kt`

**Interfaces:**
- Consumes: `VisitController.VIEW_LINK` (`"visit-link"`).
- Produces: a render-level regression net for `visit-link.html` and `error/404.html`; the existing 13 test classes stay untouched.

These are characterization tests: they must pass against the current templates. They are written first so every later task is checked against them.

- [ ] **Step 1: Write `VisitLinkTemplateTest`**

```kotlin
package com.wutsi.ndopify.web.ui.visit

import com.wutsi.ndopify.refdata.dto.Tenant
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Renders `templates/visit-link.html` with a real Thymeleaf engine. */
class VisitLinkTemplateTest {
    @Controller
    class FakeController {
        var reason: String = "expired"
        var tenant: Tenant = Tenant()

        @GetMapping("/")
        fun page(model: Model): String {
            model.addAttribute("tenant", tenant)
            model.addAttribute("reason", reason)
            return VisitController.VIEW_LINK
        }
    }

    private val controller = FakeController()
    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setViewResolvers(
            ThymeleafViewResolver().apply {
                templateEngine = SpringTemplateEngine().apply {
                    setTemplateResolver(
                        ClassLoaderTemplateResolver().apply {
                            prefix = "templates/"
                            suffix = ".html"
                            characterEncoding = "UTF-8"
                        },
                    )
                }
                characterEncoding = "UTF-8"
            },
        )
        .build()

    private fun render(
        reason: String,
        tenant: Tenant = Tenant(
            name = "Ndopify Cameroun",
            logoUrl = "https://cdn.ndopify.cm/logo.png",
            iconUrl = "https://cdn.ndopify.cm/icon.png",
        ),
    ): Document {
        controller.reason = reason
        controller.tenant = tenant
        return Jsoup.parse(mvc.perform(get("/")).andReturn().response.contentAsString)
    }

    @Test
    fun expired() {
        val page = render("expired")

        assertEquals(
            "Ce lien a expiré. Demandez à votre agent de vous renvoyer le lien de la visite.",
            page.selectFirst("main p")?.text(),
        )
        assertEquals("Votre visite — Ndopify", page.title())
        assertEquals("https://cdn.ndopify.cm/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify Cameroun", page.selectFirst("header img")?.attr("alt"))
        assertEquals("https://cdn.ndopify.cm/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
        assertTrue(page.select(".site-nav").isEmpty())
        assertTrue(page.select(".site-footer").isEmpty())
    }

    @Test
    fun notYours() {
        val page = render("not-yours")

        assertEquals("Ce lien a été envoyé à une autre personne.", page.selectFirst("main p")?.text())
    }

    @Test
    fun `Ndopify's logo when the tenant has none`() {
        val page = render("expired", Tenant(logoUrl = null, iconUrl = ""))

        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("/assets/images/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
    }
}
```

- [ ] **Step 2: Write `Error404TemplateTest`**

```kotlin
package com.wutsi.ndopify.web.ui.layout

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Renders `templates/error/404.html` with a real Thymeleaf engine, without a tenant. */
class Error404TemplateTest {
    @Controller
    class FakeController {
        @GetMapping("/")
        fun page(): String = "error/404"
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FakeController())
        .setViewResolvers(
            ThymeleafViewResolver().apply {
                templateEngine = SpringTemplateEngine().apply {
                    setTemplateResolver(
                        ClassLoaderTemplateResolver().apply {
                            prefix = "templates/"
                            suffix = ".html"
                            characterEncoding = "UTF-8"
                        },
                    )
                }
                characterEncoding = "UTF-8"
            },
        )
        .build()

    private fun render(): Document =
        Jsoup.parse(mvc.perform(get("/")).andReturn().response.contentAsString)

    @Test
    fun `Ndopify shell without a tenant`() {
        val page = render()

        assertEquals("Page introuvable — Ndopify", page.title())
        assertEquals("noindex, nofollow", page.selectFirst("meta[name=robots]")?.attr("content"))
        assertEquals("/assets/images/icon.png", page.selectFirst("link[rel=icon]")?.attr("href"))
        assertEquals("/assets/images/logo.png", page.selectFirst("header img")?.attr("src"))
        assertEquals("Ndopify", page.selectFirst("header img")?.attr("alt"))
        assertEquals("/assets/images/icon.png", page.selectFirst("footer img")?.attr("src"))
        assertTrue(page.select(".site-nav").isEmpty())
        assertTrue(page.select(".site-footer nav").isEmpty())
        assertEquals(
            "© 2026 Ndopify. Logements vérifiés. Locations fiables.",
            page.selectFirst(".site-footer small")?.text(),
        )
    }
}
```

- [ ] **Step 3: Run the two new tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='VisitLinkTemplateTest,Error404TemplateTest'`

Expected: `BUILD SUCCESS`, both classes green against the **current** templates. If a test fails, fix the test, not the template — this step records today's behaviour.

- [ ] **Step 4: Baseline gate**

Run: `cd modules/ndopify-web && mvn verify`

Expected: `BUILD SUCCESS`; ktlint clean; the 13 existing classes plus the 2 new ones green; coverage gate passes.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitLinkTemplateTest.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/layout/Error404TemplateTest.kt
git commit -m "test(web): render the visit-link and 404 pages" -m "Refs #237"
```

### Task 2: The head fragment and structured data

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/head.html`
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/structured-data.html`
- Modify: all nine page templates under `modules/ndopify-web/src/main/resources/templates/` (every `.html` outside `fragments/`)

**Interfaces:**
- Produces: fragment signature
  `head(title, description, canonical, ogTitle, ogDescription, ogImage, robots, referrer, styles, cdn, brand, structuredData)`
  in `templates/fragments/head.html`:
  - `title` — the full `<title>` text.
  - `description` — the description meta content, or `null`.
  - `canonical` — the canonical URL, or `null`; also used as `og:url`.
  - `ogTitle` / `ogDescription` / `ogImage` — Open Graph values, or `null`. The OG/Twitter block renders only when `ogTitle` is set; `ogImage` falls back to the tenant logo (Ndopify's when it has none) when `null`.
  - `robots` / `referrer` — the `robots` / `referrer` meta contents, or `null`.
  - `styles` — a SpEL list of bare CSS names rendered after `styles.css`, or `null`.
  - `cdn` — `true` to load intl-tel-input's CSS before `styles.css`.
  - `brand` — `true` for the tenant icon with Ndopify fallback; `false` for the 404's static icon.
  - `structuredData` — a fragment expression rendered at the end of the head, or `null`.
- Produces: fragments `organization` and `listing(page)` in `templates/fragments/structured-data.html`.

- [ ] **Step 1: Create `fragments/structured-data.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/* The Organization block shared by the home, agent and owner pages. */-->
<th:block th:fragment="organization"
          th:with="logoUrl=${#strings.isEmpty(tenant.logoUrl)} ? @{/assets/images/logo.png} : ${tenant.logoUrl}">
    <script th:inline="javascript" type="application/ld+json">
        {
            "@context": "https://schema.org",
            "@type": "Organization",
            "name": /*[[${#strings.isEmpty(tenant.name)} ? 'Ndopify' : ${tenant.name}]]*/ "Ndopify",
            "url": /*[[${tenant.publicPortalUrl}]]*/ null,
            "logo": /*[[${logoUrl}]]*/ null
        }
    </script>
</th:block>
<!--/* A listing's structured data. */-->
<th:block th:fragment="listing(page)">
    <script type="application/ld+json" th:inline="javascript">
        {
            "@context": "https://schema.org",
            "@type": /*[[${page.schemaType}]]*/ "Apartment",
            "name": /*[[${page.title}]]*/ "Appartement",
            "url": /*[[${page.canonicalUrl}]]*/ null,
            "numberOfRooms": /*[[${page.rooms}]]*/ null,
            "numberOfBathroomsTotal": /*[[${page.bathrooms}]]*/ null,
            "floorSize": /*[[${page.area} != null ? ${ {'@type': 'QuantitativeValue', 'value': page.area, 'unitCode': 'MTK'} } : null]]*/ null,
            "address": {"@type": "PostalAddress", "addressLocality": /*[[${page.city}]]*/ null, "addressCountry": /*[[${page.countryCode}]]*/ "CM"},
            "geo": /*[[${page.latitude} != null and ${page.longitude} != null ? ${ {'@type': 'GeoCoordinates', 'latitude': page.latitude, 'longitude': page.longitude} } : null]]*/ null
        }
    </script>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Create `fragments/head.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(title, description, canonical, ogTitle, ogDescription, ogImage, robots, referrer, styles, cdn, brand, structuredData)">
    <meta charset="utf-8">
    <meta content="width=device-width, initial-scale=1" name="viewport">
    <title th:text="${title}">Ndopify</title>
    <meta th:if="${description != null}" name="description" th:content="${description}">
    <th:block th:if="${canonical != null}">
        <link rel="canonical" th:href="${canonical}">
    </th:block>
    <th:block th:if="${ogTitle != null}">
        <meta content="website" property="og:type">
        <meta property="og:title" th:content="${ogTitle}">
        <meta property="og:description" th:content="${ogDescription}">
        <th:block th:with="logoUrl=${#strings.isEmpty(tenant?.logoUrl)} ? @{/assets/images/logo.png} : ${tenant.logoUrl}">
            <meta property="og:image" th:content="${ogImage != null} ? ${ogImage} : ${logoUrl}">
        </th:block>
        <meta th:if="${canonical != null}" property="og:url" th:content="${canonical}">
        <meta content="summary_large_image" name="twitter:card">
    </th:block>
    <meta th:if="${robots != null}" name="robots" th:content="${robots}">
    <meta th:if="${referrer != null}" name="referrer" th:content="${referrer}">
    <meta content="#1d7edf" name="theme-color">
    <link rel="icon"
          th:href="${!brand or #strings.isEmpty(tenant?.iconUrl)} ? @{/assets/images/icon.png} : ${tenant.iconUrl}">
    <link rel="apple-touch-icon"
          th:href="${!brand or #strings.isEmpty(tenant?.iconUrl)} ? @{/assets/images/icon.png} : ${tenant.iconUrl}">
    <link th:if="${cdn}" rel="stylesheet"
          href="https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/css/intlTelInput.min.css">
    <link rel="stylesheet" th:href="@{/assets/css/styles.css}">
    <link th:each="name : ${styles}" rel="stylesheet" th:href="@{/assets/css/{name}.css(name=${name})}">
    <th:block th:if="${structuredData != null}">
        <th:block th:replace="${structuredData}"></th:block>
    </th:block>
</head>
</html>
```

- [ ] **Step 3: Convert `index.html` first and verify the structured-data argument**

Replace its whole `<head>...</head>` with:

```html
<head th:replace="~{fragments/head :: head(
    'Ndopify — Confiez votre recherche de logement à un agent vérifié',
    |Décrivez votre recherche : un agent vérifié de votre secteur la prend en charge, vous propose des biens qui vous correspondent et organise vos visites, sur place ou en vidéo. Gratuit jusqu'à la signature du bail.|,
    ${#strings.isEmpty(tenant.publicPortalUrl) ? null : tenant.publicPortalUrl},
    'Ndopify — Un agent vérifié pour votre recherche de logement',
    |Un agent vérifié de votre secteur, des biens choisis pour vous, des visites organisées. Gratuit jusqu'à la signature du bail.|,
    null,
    null,
    null,
    null,
    false,
    true,
    ~{fragments/structured-data :: organization})}"></head>
```

Run: `cd modules/ndopify-web && mvn test -Dtest=IndexTemplateTest`

Expected: `BUILD SUCCESS`. This step proves that a fragment expression (`~{...}`) passed as a fragment argument renders the JSON-LD in the standalone engine. If it fails, stop and report it before converting the other eight pages.

- [ ] **Step 4: Convert the other eight pages**

Replace each page's whole `<head>...</head>` with a `head(...)` call. The argument list is, in order:
`title, description, canonical, ogTitle, ogDescription, ogImage, robots, referrer, styles, cdn, brand, structuredData`.

- `agent.html` — title `'Ndopify — Recevez des mandats de clients dans votre secteur'`; description `|Rejoignez le réseau d'agents vérifiés Ndopify : des mandats de recherche et de location dans vos quartiers, les biens qui correspondent à vos clients, vos visites organisées. Inscription gratuite.|`; canonical `${#strings.isEmpty(tenant.publicPortalUrl) ? null : tenant.publicPortalUrl + '/agent'}`; ogTitle `'Ndopify — Des mandats de clients dans votre secteur'`; ogDescription `'Mandats signés dans vos quartiers, biens correspondants trouvés pour vous, visites organisées. Inscription gratuite.'`; ogImage `null`; robots `null`; referrer `null`; styles `null`; cdn `false`; brand `true`; structuredData `~{fragments/structured-data :: organization}`
- `owner.html` — title `'Ndopify — Confiez la location de votre bien à un agent vérifié'`; description `|Proposez votre bien : un agent vérifié de votre quartier l'audite, le publie et le présente aux locataires dont la recherche lui correspond. Gratuit jusqu'à la signature du bail.|`; canonical `${#strings.isEmpty(tenant.publicPortalUrl) ? null : tenant.publicPortalUrl + '/owner'}`; ogTitle `'Ndopify — Un agent vérifié pour louer votre bien'`; ogDescription `'Audit et photos par un agent vérifié, présentation aux locataires qui cherchent un bien comme le vôtre, visites organisées.'`; ogImage `null`; robots `null`; referrer `null`; styles `null`; cdn `false`; brand `true`; structuredData `~{fragments/structured-data :: organization}`
- `listing.html` — title `${page.title + ' | ' + (#strings.isEmpty(tenant.name) ? 'Ndopify' : tenant.name)}`; description `${page.metaDescription}`; canonical `${page.canonicalUrl}`; ogTitle `${page.title}`; ogDescription `${page.metaDescription}`; ogImage `${#lists.isEmpty(page.photos) ? null : page.photos[0].socialUrl}`; robots `null`; referrer `null`; styles `${ {'listing'} }`; cdn `true`; brand `true`; structuredData `~{fragments/structured-data :: listing(${page})}`
- `match.html` — title `${page.title + ' | ' + (#strings.isEmpty(tenant.name) ? 'Ndopify' : tenant.name)}`; description `null`; canonical `null`; ogTitle `null`; ogDescription `null`; ogImage `null`; robots `'noindex, nofollow'`; referrer `'no-referrer'`; styles `${ {'listing','proposal'} }`; cdn `false`; brand `true`; structuredData `null`
- `match-link.html` — title `'Votre proposition — Ndopify'`; description `null`; canonical `null`; ogTitle `null`; ogDescription `null`; ogImage `null`; robots `'noindex, nofollow'`; referrer `null`; styles `null`; cdn `false`; brand `true`; structuredData `null`
- `visit-link.html` — title `'Votre visite — Ndopify'`; description `null`; canonical `null`; ogTitle `null`; ogDescription `null`; ogImage `null`; robots `'noindex, nofollow'`; referrer `null`; styles `null`; cdn `false`; brand `true`; structuredData `null`
- `visit.html` — title `${'Votre visite | ' + (#strings.isEmpty(tenant.name) ? 'Ndopify' : tenant.name)}`; description `null`; canonical `null`; ogTitle `null`; ogDescription `null`; ogImage `null`; robots `'noindex, nofollow'`; referrer `'no-referrer'`; styles `${ {'listing','proposal','visit'} }`; cdn `false`; brand `true`; structuredData `null`
- `error/404.html` — title `'Page introuvable — Ndopify'`; description `null`; canonical `null`; ogTitle `null`; ogDescription `null`; ogImage `null`; robots `'noindex, nofollow'`; referrer `null`; styles `null`; cdn `false`; brand `false`; structuredData `null`

For the 404, the call is:

```html
<head th:replace="~{fragments/head :: head('Page introuvable — Ndopify', null, null, null, null, null, 'noindex, nofollow', null, null, false, false, null)}"></head>
```

- [ ] **Step 5: Verify no page keeps its head**

Run:

```bash
grep -rln '<meta charset' modules/ndopify-web/src/main/resources/templates
grep -rln 'styles.css' modules/ndopify-web/src/main/resources/templates
grep -rln 'application/ld+json' modules/ndopify-web/src/main/resources/templates
```

Expected: only `fragments/head.html` for the first two; only `fragments/structured-data.html` for the third. Any page still listed has a leftover head block.

- [ ] **Step 6: Run and gate**

Run: `cd modules/ndopify-web && mvn verify`

Expected: `BUILD SUCCESS`; every template test green; the two new tests still green. A malformed call, a wrong argument count or an unresolvable expression fails the page's render here.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates
git commit -m "refactor(web): extract the page head into a fragment" -m "Refs #237"
```

### Task 3: The site-header fragment

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/site-header.html`
- Modify: the nine page templates, replacing the skip link and `<header class="site-header">…</header>`

**Interfaces:**
- Produces: fragment signature `header(active, menuLabel, brand)` in `templates/fragments/site-header.html`:
  - `active` — `'home'`, `'owner'`, `'agent'`, or `null` for no nav.
  - `menuLabel` — the nav toggle's label, or `null` when there is no nav.
  - `brand` — `true` for the tenant logo/alt with Ndopify fallback; `false` for the 404's static logo.

- [ ] **Step 1: Create `fragments/site-header.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/*
  The page shell's skip link and header. "active" marks the current section; "menuLabel" is the nav toggle's label
  (it is "Menu" on the listing, match and visit pages, not the section's name); "brand" replaces the tenant logo with
  Ndopify's own on the 404, which renders without a tenant.
*/-->
<th:block th:fragment="header(active, menuLabel, brand)">
    <a class="skip-link" href="#main">Aller au contenu principal</a>
    <header class="site-header">
        <a aria-label="Ndopify — accueil" class="site-header__logo" th:href="@{/}">
            <img alt="Ndopify" height="36" width="140"
                 th:alt="${!brand or #strings.isEmpty(tenant?.name)} ? 'Ndopify' : ${tenant.name}"
                 th:src="${!brand or #strings.isEmpty(tenant?.logoUrl)} ? @{/assets/images/logo.png} : ${tenant.logoUrl}">
        </a>
        <th:block th:if="${active != null}">
            <nav aria-label="Navigation principale" class="site-nav">
                <button aria-controls="primary-nav-list" aria-expanded="false" class="nav-toggle"><span aria-hidden="true"
                                                                                                        class="nav-toggle__icon">☰</span>
                    <th:block th:text="${menuLabel}">Menu</th:block>
                </button>
                <ul class="site-nav__list" id="primary-nav-list">
                    <li><a th:attr="aria-current=${active == 'home' ? 'page' : null}" th:href="@{/}">Locataires</a></li>
                    <li><a th:attr="aria-current=${active == 'owner' ? 'page' : null}" th:href="@{/owner}">Propriétaires</a></li>
                    <li><a th:attr="aria-current=${active == 'agent' ? 'page' : null}" th:href="@{/agent}">Agents</a></li>
                </ul>
            </nav>
        </th:block>
    </header>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Replace the skip link and header in the nine pages**

Each page's `<a class="skip-link" …>` plus `<header class="site-header">…</header>` becomes one call. The brand `<img>`'s attribute order is normalised to `alt height width th:alt th:src`; the rendered src/alt are unchanged.

- `index.html` — `<th:block th:replace="~{fragments/site-header :: header('home', 'Locataires', true)}"></th:block>`
- `agent.html` — `header('agent', 'Agents', true)`
- `owner.html` — `header('owner', 'Propriétaires', true)`
- `listing.html` — `header('home', 'Menu', true)`
- `match.html` — `header('home', 'Menu', true)`
- `visit.html` — `header('home', 'Menu', true)`
- `match-link.html` — `header(null, null, true)`
- `visit-link.html` — `header(null, null, true)`
- `error/404.html` — `header(null, null, false)`

- [ ] **Step 3: Verify the remaining headers are the fragment**

Run:

```bash
grep -rln 'class="site-header"' modules/ndopify-web/src/main/resources/templates
grep -rln 'skip-link' modules/ndopify-web/src/main/resources/templates
```

Expected: only `fragments/site-header.html` in both.

- [ ] **Step 4: Run and gate**

Run: `cd modules/ndopify-web && mvn verify`

Expected: `BUILD SUCCESS`. The Index/Agent/Owner/Listing/Match/MatchLink tests assert the header brand; `VisitLinkTemplateTest` and `Error404TemplateTest` assert no `.site-nav` on the three header-less pages.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates
git commit -m "refactor(web): extract the site header into a fragment" -m "Refs #237"
```

### Task 4: The site-footer fragment

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/site-footer.html`
- Modify: `index.html`, `agent.html`, `owner.html`, `listing.html`, `match.html`, `visit.html`, `error/404.html`
- Keep none: `match-link.html`, `visit-link.html`

**Interfaces:**
- Produces: fragment signature `footer(tagline, brand)` in `templates/fragments/site-footer.html`:
  - `tagline` — the full `<small>` text.
  - `brand` — `true` for the tenant icon and the nav; `false` for the 404's static icon without a nav.

- [ ] **Step 1: Create `fragments/site-footer.html`**

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/*
  The site footer. "brand" false is the 404's static, nav-less footer; the two taglines are the tenant pages' own copy.
*/-->
<footer class="site-footer" th:fragment="footer(tagline, brand)">
    <div class="container site-footer__inner">
        <img alt="Ndopify" height="32" width="32"
             th:alt="${!brand or #strings.isEmpty(tenant?.name)} ? 'Ndopify' : ${tenant.name}"
             th:src="${!brand or #strings.isEmpty(tenant?.iconUrl)} ? @{/assets/images/icon.png} : ${tenant.iconUrl}">
        <th:block th:if="${brand}">
            <nav aria-label="Navigation du pied de page">
                <ul>
                    <li><a th:href="@{/}">Locataires</a></li>
                    <li><a th:href="@{/owner}">Propriétaires</a></li>
                    <li><a th:href="@{/agent}">Agents</a></li>
                </ul>
            </nav>
        </th:block>
        <small th:text="${tagline}">© 2026 Ndopify. Logements vérifiés. Locations fiables.</small>
    </div>
</footer>
</body>
</html>
```

- [ ] **Step 2: Replace the footer in the seven pages**

The footer `<img>`'s attribute order is normalised to `alt height width th:alt th:src`; the rendered src/alt are unchanged.

- `index.html`, `agent.html`, `owner.html` — `<footer th:replace="~{fragments/site-footer :: footer('Des agents vérifiés, à votre service.', true)}"></footer>`
- `listing.html`, `match.html`, `visit.html` — `<footer th:replace="~{fragments/site-footer :: footer('Logements vérifiés. Locations fiables.', true)}"></footer>`
- `error/404.html` — `<footer th:replace="~{fragments/site-footer :: footer('Logements vérifiés. Locations fiables.', false)}"></footer>`

Leave `match-link.html` and `visit-link.html` untouched: they have no footer.

- [ ] **Step 3: Verify the remaining footers are the fragment**

Run:

```bash
grep -rln 'class="site-footer"' modules/ndopify-web/src/main/resources/templates
```

Expected: only `fragments/site-footer.html`.

- [ ] **Step 4: Run and gate**

Run: `cd modules/ndopify-web && mvn verify`

Expected: `BUILD SUCCESS`. Index/Agent/Owner assert the footer icon; `Error404TemplateTest` asserts the static icon, no footer nav and the tagline; `VisitLinkTemplateTest` asserts no footer.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates
git commit -m "refactor(web): extract the site footer into a fragment" -m "Refs #237"
```

### Task 5: The shared icon sprite

**Files:**
- Create: `modules/ndopify-web/src/main/resources/templates/fragments/icon-sprite.html`
- Modify: `listing.html`, `match.html`, `visit.html`
- Keep unchanged: `fragments/listing-body.html`, which references the symbols by id

**Interfaces:**
- Produces: fragment `sprite` in `templates/fragments/icon-sprite.html`, an `<svg class="visually-hidden">` holding the 24 unique symbols.
- Consumed by pages as `<th:block th:replace="~{fragments/icon-sprite :: sprite}"></th:block>`.

- [ ] **Step 1: Create `fragments/icon-sprite.html`**

Copy the 18 symbols from `listing.html`'s current inline sprite, in their current order:
`i-bed`, `i-bath`, `i-area`, `i-sofa`, `i-car`, `i-info`, `i-check`, `i-shield`, `i-pin`, `i-close`, `i-share`,
`i-chevron-left`, `i-chevron-right`, `i-calendar`, `i-chat`, `i-video`, `i-send`, `i-sparkle`.

Then add, from `visit.html`'s sprite, the six drawings it alone defines: `i-alert`, `i-clock`, `i-phone`, `i-route`,
`i-key`, `i-id`.

Every shared symbol is byte-identical across the three current sprites; copy each verbatim (`id`, `viewBox`, `<path d>`). The file is:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!-- Icon sprite (decorative: every icon sits next to its text) -->
<svg th:fragment="sprite" aria-hidden="true" class="visually-hidden" focusable="false" xmlns="http://www.w3.org/2000/svg">
    <symbol id="i-bed" viewBox="0 0 24 24"><path d="…"/></symbol>
    …
    <symbol id="i-id" viewBox="0 0 24 24"><path d="…"/></symbol>
</svg>
</body>
</html>
```

- [ ] **Step 2: Replace the three inline sprites**

In `listing.html`, `match.html` and `visit.html`, replace the whole `<svg aria-hidden="true" class="visually-hidden" …>…</svg>` sprite block (the one between `</header>` and `<main>`) with:

```html
<th:block th:replace="~{fragments/icon-sprite :: sprite}"></th:block>
```

Do not touch the `<svg class="icon"><use href="#i-…"/></svg>` call sites.

- [ ] **Step 3: Verify the counts**

Run:

```bash
grep -rl '<symbol' modules/ndopify-web/src/main/resources/templates
grep -ro '<symbol' modules/ndopify-web/src/main/resources/templates | wc -l
grep -rln 'icon-sprite' modules/ndopify-web/src/main/resources/templates
grep -ro '<use ' modules/ndopify-web/src/main/resources/templates | wc -l
```

Expected: only `fragments/icon-sprite.html` lists symbols; the symbol count is `24`; `icon-sprite` is called by `listing.html`, `match.html` and `visit.html`; the `<use>` count stays `52`.

- [ ] **Step 4: Run and gate**

Run: `cd modules/ndopify-web && mvn verify`

Expected: `BUILD SUCCESS`. `MatchTemplateTest` still finds `#i-check` and `#i-close` through the shared sprite; every other template test renders its page.

- [ ] **Step 5: Check the icons by eye**

On the running app (ndopify-server up, one published listing and one proposed match): the listing's bedroom, bathroom, area, sofa and car facts, the price-card calendar, the photo chevrons and the share icon render; the match page's check/close criteria and the visit page's clock/pin/video/route/key/id icons render. None is an empty box.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates
git commit -m "refactor(web): extract the icon sprite into a fragment" -m "Refs #237"
```
