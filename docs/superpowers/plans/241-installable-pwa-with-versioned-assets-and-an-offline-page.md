# ndopify-web — Installable PWA with Versioned Assets and an Offline Page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve one minified CSS and one minified JS with content-versioned URLs and long-lived caching, make the public site installable with a tenant-branded manifest, and add a service worker that shows a branded "Vous êtes hors ligne" page when a navigation fails.

**Architecture:** Sources move to `src/main/assets/` and are concatenated and minified by esbuild (via `frontend-maven-plugin`) into `target/classes/public/assets/dist/`. Spring's content-version strategy appends `-<hash>` to the `@{}` URLs at runtime; a filter sets the cache headers. A generated, versioned `sw.js` intercepts navigations only (fallback to a self-contained `/offline.html`) and serves the two precached URLs. `/manifest.json` is a controller that renders the tenant's name and icon. No HTML or API is ever cached.

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (MVC, Thymeleaf, Security), Maven, esbuild 0.28.2, Node v26.13.1, `frontend-maven-plugin` 1.15.1, kotlin.test + mockito-kotlin.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/241 (enriched story) and its approved prototype, `modules/ndopify-web/src/main/html/offline.html`.

## Global Constraints

- One CSS (`app.min.css`) and one JS (`app.min.js`), minified. CSS order, exactly: `styles`, `listing`, `proposal`, `visit`. JS order, exactly: `main`, `listing`, `proposal`, `visit`. The order preserves every "loaded after" pair.
- `head` loses its `styles` list parameter: every page loads the one bundle.
- No caching of HTML or API responses. Assets only.
- Cache headers: `/assets/dist/**` → `public, max-age=31536000, immutable`; other `/assets/**` → `public, max-age=86400`; everything else (HTML, `/sw.js`, `/offline.html`, `/manifest.json`) → `no-cache, must-revalidate`.
- The service worker intercepts `mode === "navigate"` and the precached URLs; nothing else.
- Interim icon only: the existing `/assets/images/icon.png` (500×500). Do not generate or copy icons.
- The offline page is Ndopify-branded and tenant-neutral: no tenant name or logo. `/manifest.json` is tenant-branded (name + `iconUrl`, fallback `Ndopify` + `/assets/images/icon.png`).
- UI copy is French, code/comments/docs English; every page works without JavaScript.
- Pinned tool versions: esbuild `0.28.2`, Node `v26.13.1`, `frontend-maven-plugin` `1.15.1`.
- Tests are plain unit tests (`kotlin.test`, `mockito-kotlin`); no Spring context, no MySQL, no running ndopify-server.

## Review Focus

- **An unversioned URL behind `immutable`.** If the content strategy does not rewrite `@{…}`, `/assets/dist/app.min.css` would be pinned for a year and a deploy would never reach a client. Verify the rendered page carries `-<hash>` before trusting the header (Task 2, manual curl).
- **A merged CSS bundle leaking page rules.** `listing.css`, `proposal.css` and `visit.css` are page-scoped today; merged, they become global. Check `/`, `/owner`, `/agent`, a listing page, `/match/{id}`, `/visits/{id}` at 375 px and 1280 px, and fix by scoping the leaking rules (Task 7).
- **The PWA files behind `denyAll`.** If `/sw.js`, `/offline.html` or `/manifest.json` are not permitted, registration, install and the offline fallback fail for every visitor. Verify signed out with `curl` (Tasks 3, 5).
- **A tenant-aware manifest that gets cached.** `/manifest.json` is answered per request; a cached one would show one tenant's branding on another. The filter must leave it `no-cache` (Tasks 2, 3).
- **An offline page that is not self-contained.** A webfont, the CSS bundle or a relative asset in `/offline.html` makes it blank offline, the one moment it exists for. `PwaAssetsTest` pins it (Task 4).

---

### Task 1: One minified CSS + one JS bundle, loaded by every page

**Files:**
- Create: `modules/ndopify-web/package.json`, `package-lock.json`, `build-assets.mjs`
- Create: `modules/ndopify-web/src/main/assets/css/` (4 files, moved), `src/main/assets/js/` (4 files, moved)
- Modify: `modules/ndopify-web/pom.xml`
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/head.html` and all 9 templates that call it
- Modify: `agent.html`, `index.html`, `owner.html`, `listing.html`, `match.html`, `visit.html` (drop the bottom `<script>`s)
- Test: `src/test/kotlin/com/wutsi/ndopify/web/BundledAssetsTest.kt` (new)
- Test: `IndexTemplateTest`, `Error404TemplateTest` (head assertions)

**Interfaces:**
- Consumes: nothing.
- Produces: classpath resources `public/assets/dist/app.min.css` and `public/assets/dist/app.min.js`; `fragments/head :: head(title, description, canonical, ogTitle, ogDescription, ogImage, robots, referrer, cdn, brand, structuredData)` (the `styles` parameter removed).

- [ ] **Step 1: Write the failing bundle test**

Create `BundledAssetsTest.kt` in `com.wutsi.ndopify.web`:

```kotlin
class BundledAssetsTest {
    @Test
    fun cssBundleIsBuiltInOrder() {
        val css = resource("public/assets/dist/app.min.css")
        assertTrue(css.length > 10_000, "css bundle is ${css.length} bytes")
        assertTrue(css.contains(".site-header"))        // styles.css, first
        assertTrue(css.contains(".audit-note"))         // listing.css
        assertTrue(css.contains(".agent-card"))         // proposal.css
        assertTrue(css.contains(".visit-address"))      // visit.css, last
        assertTrue(css.indexOf(".site-header") < css.indexOf(".audit-note"))
        assertTrue(css.indexOf(".audit-note") < css.indexOf(".agent-card"))
        assertTrue(css.indexOf(".agent-card") < css.indexOf(".visit-address"))
    }

    @Test
    fun jsBundleHasTheScriptsInOrder() {
        val js = resource("public/assets/dist/app.min.js")
        assertTrue(js.length > 10_000, "js bundle is ${js.length} bytes")
        assertTrue(js.contains("data-amount"))              // main.js, first
        assertTrue(js.contains("data-lightbox"))            // listing.js
        assertTrue(js.contains("data-visit-request-form"))  // proposal.js
        assertTrue(js.contains("#cancel"))                  // visit.js, last
        assertTrue(js.indexOf("data-amount") < js.indexOf("data-lightbox"))
        assertTrue(js.indexOf("data-lightbox") < js.indexOf("data-visit-request-form"))
        assertTrue(js.indexOf("data-visit-request-form") < js.indexOf("#cancel"))
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")
}
```

`fail(...)` is `kotlin.test.fail`.

In `IndexTemplateTest`, add a test using its `render(...)` helper:

```kotlin
@Test
fun `the head loads the one bundle`() {
    val page = render("tenant" to tenant)

    assertEquals("/assets/dist/app.min.css", page.selectFirst("link[rel=stylesheet]")?.attr("href"))
    assertEquals(1, page.select("link[rel=stylesheet]").size)
    val script = page.selectFirst("script[src=/assets/dist/app.min.js]")
    assertNotNull(script)
    assertTrue(script.hasAttr("defer"))
    assertEquals(1, page.select("script[src^=/assets/]").size)
}
```

In `Error404TemplateTest.Ndopify shell without a tenant`, add the same three assertions (the 404 has no tenant and
must still load the bundle).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest='BundledAssetsTest,IndexTemplateTest,Error404TemplateTest'`
Expected: `BundledAssetsTest` fails on the missing resource; the template tests fail on the missing `<link>`.

- [ ] **Step 3: Move the sources**

```bash
cd modules/ndopify-web
mkdir -p src/main/assets/css src/main/assets/js
git mv src/main/resources/public/assets/css/*.css src/main/assets/css/
git mv src/main/resources/public/assets/js/*.js src/main/assets/js/
```

The prototype's copies under `src/main/html/` stay untouched.

- [ ] **Step 4: Add the build script and package files**

`package.json`:

```json
{
  "name": "ndopify-web-assets",
  "private": true,
  "type": "module",
  "devDependencies": { "esbuild": "0.28.2" },
  "scripts": { "build": "node build-assets.mjs" }
}
```

`build-assets.mjs` (keep the two order arrays in sync with Global Constraints):

```js
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { transform } from "esbuild";

const CSS = ["styles", "listing", "proposal", "visit"];
const JS = ["main", "listing", "proposal", "visit"];
const out = process.argv[2] || "target/classes";

const read = (p) => readFile(p, "utf8");
const concat = async (dir, names) =>
  (await Promise.all(names.map((n) => read(`src/main/assets/${dir}/${n}.${dir}`))))
    .join("\n");

const min = async (code, loader) =>
  (await transform(code, { loader, minify: true, legalComments: "none" })).code;

await mkdir(`${out}/public/assets/dist`, { recursive: true });
await writeFile(`${out}/public/assets/dist/app.min.css`, await min(await concat("css", CSS), "css"));
await writeFile(`${out}/public/assets/dist/app.min.js`, await min(await concat("js", JS), "js"));
```

Run `npm install` once in the module (needs network) and commit the generated `package-lock.json`. `.gitignore`
already ignores `node_modules/`.

- [ ] **Step 5: Wire the build into Maven**

In `modules/ndopify-web/pom.xml`, add a module `<properties>` block after the existing one:

```xml
<properties>
    <jacoco.threshold.line>0.97</jacoco.threshold.line>
    <jacoco.threshold.class>0.80</jacoco.threshold.class>
    <frontend-maven-plugin.version>1.15.1</frontend-maven-plugin.version>
    <node.version>v26.13.1</node.version>
</properties>
```

and, inside `<build><plugins>`, after the Spring Boot plugin:

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>${frontend-maven-plugin.version}</version>
    <configuration>
        <nodeVersion>${node.version}</nodeVersion>
        <installDirectory>${project.build.directory}</installDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <phase>generate-resources</phase>
            <goals><goal>install-node-and-npm</goal></goals>
        </execution>
        <execution>
            <id>npm-ci</id>
            <phase>generate-resources</phase>
            <goals><goal>npm</goal></goals>
            <configuration><arguments>ci</arguments></configuration>
        </execution>
        <execution>
            <id>build-assets</id>
            <phase>generate-resources</phase>
            <goals><goal>npm</goal></goals>
            <configuration><arguments>run build</arguments></configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 6: Switch the head fragment to the bundle**

`templates/fragments/head.html`: change the fragment signature to
`head(title, description, canonical, ogTitle, ogDescription, ogImage, robots, referrer, cdn, brand, structuredData)`,
and replace the base stylesheet plus the `th:each` loop with:

```html
<link rel="stylesheet" th:href="@{/assets/dist/app.min.css}">
```

The `cdn` block (intl-tel-input CSS) and the structured-data block stay where they are. After them, render:

```html
<script th:src="@{/assets/dist/app.min.js}" defer></script>
```

- [ ] **Step 7: Update the 9 call sites**

Each `<head th:replace="~{fragments/head :: head(...)}">` loses its ninth argument, the `styles` list:
`null` on `index.html`, `owner.html`, `agent.html`, `match-link.html`, `visit-link.html`;
`${ {'listing'} }` on `listing.html`; `${ {'listing','proposal'} }` on `match.html`;
`${ {'listing','proposal','visit'} }` on `visit.html`; the extra `null` on `error/404.html`. Verify with:

```bash
grep -rn "\${ {'listing'}" modules/ndopify-web/src/main/resources/templates   # expected: no output
```

- [ ] **Step 8: Drop the bottom scripts**

Remove `<script th:src="@{/assets/js/main.js}"></script>` from `index.html`, `owner.html`, `agent.html`,
`listing.html`, `match.html` and `visit.html`, and the `listing.js` / `proposal.js` / `visit.js` lines beside it.
Keep: the inline `<script id="listing-photos">` blocks, and the `intl-tel-input` CDN `<script src="https://…">` on
`listing.html` (it is a body script, not the bundle).

- [ ] **Step 9: Run the module tests**

Run: `cd modules/ndopify-web && mvn test`
Expected: BUILD SUCCESS, including `BundledAssetsTest`, the two updated template tests and every existing template test.

- [ ] **Step 10: Commit**

```bash
git add .gitignore modules/ndopify-web
git commit -m "feat(web): serve one minified css and js bundle"
```

---

### Task 2: Versioned asset URLs and cache headers

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/application.yml`
- Create: `src/main/kotlin/com/wutsi/ndopify/web/config/AssetCacheFilter.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/web/config/AssetCacheConfiguration.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/config/AssetCacheFilterTest.kt`

**Interfaces:**
- Consumes: the bundles at `/assets/dist/app.min.{css,js}` (Task 1).
- Produces: `Cache-Control` headers on every response; `@{…}` asset URLs rendered as `…-<hash>.<ext>`.

- [ ] **Step 1: Write the failing filter test**

```kotlin
class AssetCacheFilterTest {
    private val filter = AssetCacheFilter()

    private fun cacheControl(path: String): String? {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", path), response, MockFilterChain())
        return response.getHeader("Cache-Control")
    }

    @Test
    fun bundleIsImmutable() =
        assertEquals("public, max-age=31536000, immutable", cacheControl("/assets/dist/app-abc123.min.css"))

    @Test
    fun otherAssetsAreCachedForADay() =
        assertEquals("public, max-age=86400", cacheControl("/assets/images/logo.png"))

    @Test
    fun pagesAreRevalidated() =
        assertEquals("no-cache, must-revalidate", cacheControl("/match/42"))

    @Test
    fun pwaFilesAreRevalidated() {
        assertEquals("no-cache, must-revalidate", cacheControl("/sw.js"))
        assertEquals("no-cache, must-revalidate", cacheControl("/offline.html"))
        assertEquals("no-cache, must-revalidate", cacheControl("/manifest.json"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=AssetCacheFilterTest`
Expected: FAIL — `AssetCacheFilter` does not exist.

- [ ] **Step 3: Implement the filter and its configuration**

`AssetCacheFilter` is a `OncePerRequestFilter` in `com.wutsi.ndopify.web.config`:

```kotlin
class AssetCacheFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.setHeader("Cache-Control", cacheControl(request.requestURI))
        filterChain.doFilter(request, response)
    }

    internal fun cacheControl(path: String): String = when {
        path.startsWith("/assets/dist/") -> "public, max-age=31536000, immutable"
        path.startsWith("/assets/") -> "public, max-age=86400"
        else -> "no-cache, must-revalidate"
    }
}
```

Set the header **before** the chain, so it is present when the resource handler writes the body (a plain `Filter`
bean runs after Spring Security's header writer, so this value wins).

`AssetCacheConfiguration.kt`:

```kotlin
@Configuration
class AssetCacheConfiguration {
    @Bean
    fun assetCacheFilter(): AssetCacheFilter = AssetCacheFilter()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=AssetCacheFilterTest`
Expected: PASS.

- [ ] **Step 5: Enable the content-version strategy**

Inside the existing top-level `spring:` block of `src/main/resources/application.yml`, after `jackson:`:

```yaml
    web:
        resources:
            # Renders the @{…} asset URLs as …-<hash>.<ext>, so the year-long immutable cache of /assets/dist/
            # never pins a stale bundle after a deploy.
            chain:
                enabled: true
                strategy:
                    content:
                        enabled: true
                        paths: /assets/**
```

Thymeleaf's `ResourceUrlEncodingFilter` is auto-registered by Spring Boot 4.1.2; no Kotlin bean is needed.

- [ ] **Step 6: Verify a rendered page against the running app**

With a local ndopify-server and MySQL (module README), run `mvn spring-boot:run`, then read the bundle URLs the page
renders:

```bash
curl -s http://localhost:8082/ | grep -o '/assets/dist/app[^"]*'
```

Expected: `/assets/dist/app-<hash>.min.css` **and** `/assets/dist/app-<hash>.min.js` — the hash is mandatory (see
Review Focus). Copy one of them and check its headers, then the page's:

```bash
curl -s -D - -o /dev/null 'http://localhost:8082/assets/dist/app-<hash>.min.css' | grep -i cache-control
curl -s -D - -o /dev/null http://localhost:8082/ | grep -i cache-control
```

Expected: `public, max-age=31536000, immutable` for the bundle and `no-cache, must-revalidate` for the page.

- [ ] **Step 7: Confirm the dead `generate-asset-version` step is gone**

```bash
grep -rn "generate-asset-version" --include=pom.xml modules/ndopify-web   # expected: no output
ls modules/ndopify-web/target/classes/wutsi.properties                    # expected: no such file
```

Nothing to change: #240 removed it. This step pins the story's last acceptance criterion.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): version static assets and cache the bundle"
```

---

### Task 3: Tenant-branded manifest

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/web/ui/pwa/ManifestController.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/ui/pwa/ManifestControllerTest.kt`
- Modify: `templates/fragments/head.html`
- Modify: `src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `IndexTemplateTest` (manifest link and apple meta)

**Interfaces:**
- Consumes: `TenantAdvice`'s `tenant` model attribute; `/assets/images/icon.png`.
- Produces: `GET /manifest.json` → `application/manifest+json`, body `{"name", "short_name", "lang", "start_url", "scope", "display", "theme_color", "icons": [{"src", "sizes", "type", "purpose"}]}`.

- [ ] **Step 1: Write the failing test**

```kotlin
class ManifestControllerTest {
    @ControllerAdvice
    class FakeTenantAdvice {
        @ModelAttribute("tenant")
        fun tenant(): Tenant = tenant
    }

    companion object {
        var tenant = Tenant(name = "Ndopify Cameroun", iconUrl = "https://cdn.ndopify.cm/icon.png")
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ManifestController())
        .setControllerAdvice(FakeTenantAdvice())
        .build()

    private fun manifest(): JsonNode =
        ObjectMapper().readTree(
            mvc.perform(get("/manifest.json")).andExpect(status().isOk).andReturn().response.contentAsString,
        )

    @Test
    fun `the manifest carries the tenant branding`() {
        val json = manifest()

        assertEquals("Ndopify Cameroun", json["name"].asText())
        assertEquals("Ndopify Cameroun", json["short_name"].asText())
        assertEquals("fr", json["lang"].asText())
        assertEquals("/", json["start_url"].asText())
        assertEquals("/", json["scope"].asText())
        assertEquals("standalone", json["display"].asText())
        assertEquals("#1d7edf", json["theme_color"].asText())
        assertEquals("https://cdn.ndopify.cm/icon.png", json["icons"][0]["src"].asText())
        assertEquals("any", json["icons"][0]["purpose"].asText())
    }

    @Test
    fun `a tenant without name or icon falls back to Ndopify`() {
        tenant = Tenant()
        val json = manifest()

        assertEquals("Ndopify", json["name"].asText())
        assertEquals("Ndopify", json["short_name"].asText())
        assertEquals("/assets/images/icon.png", json["icons"][0]["src"].asText())
        assertEquals("500x500", json["icons"][0]["sizes"].asText())
    }

    @Test
    fun `the response is a manifest`() {
        mvc.perform(get("/manifest.json"))
            .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
    }
}
```

Reset the tenant before each test:

```kotlin
@BeforeEach
fun reset() {
    tenant = Tenant(name = "Ndopify Cameroun", iconUrl = "https://cdn.ndopify.cm/icon.png")
}
```

In `IndexTemplateTest`, add:

```kotlin
assertEquals("/manifest.json", page.selectFirst("link[rel=manifest]")?.attr("href"))
assertNotNull(page.selectFirst("meta[name=apple-mobile-web-app-capable]"))
assertNotNull(page.selectFirst("meta[name=apple-mobile-web-app-title]"))
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest='ManifestControllerTest,IndexTemplateTest'`
Expected: FAIL — `ManifestController` does not exist; no `<link rel=manifest>`.

- [ ] **Step 3: Add the controller**

`ManifestController` in `com.wutsi.ndopify.web.ui.pwa`, annotated `@RestController`:

- `@GetMapping("/manifest.json", produces = ["application/manifest+json"])`
- signature: `fun manifest(model: Model): WebManifest` — read the tenant from the model with
  `model.getAttribute("tenant") as Tenant`, never as a `@ModelAttribute` parameter (module rule: request parameters
  could bind it).
- A nested data class `WebManifest` with the fields below, `@get:JsonProperty` for the snake_case ones; a nested
  `Icon` data class with `src`, `sizes`, `type`, `purpose`.
- Values: `name` and `shortName` = `tenant.name.ifBlank { "Ndopify" }`; `lang` `fr`; `startUrl` and `scope` `/`;
  `display` `standalone`; `themeColor` `#1d7edf`; one icon, `src` = `tenant.iconUrl` if not blank else
  `/assets/images/icon.png`, `sizes` `500x500`, `type` `image/png`, `purpose` `any`.

No `public/manifest.json` file: it would install Ndopify's branding on every tenant.

- [ ] **Step 4: Add the head metadata**

In `templates/fragments/head.html`, after the favicon links:

```html
<link rel="manifest" th:href="@{/manifest.json}">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Ndopify">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
```

- [ ] **Step 5: Make the three PWA paths public**

In `SecurityConfiguration.filterChain`, after the `/error` matcher (the three paths are asserted as 200 in Task 5):

```kotlin
.requestMatchers("/sw.js", "/offline.html", "/manifest.json").permitAll()
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn test -Dtest='ManifestControllerTest,IndexTemplateTest'`
Expected: PASS.

- [ ] **Step 7: Verify the content type and the headers**

With `mvn spring-boot:run`:

```bash
curl -s -D - -o /dev/null http://localhost:8082/manifest.json | grep -i "content-type\|cache-control"
```

Expected: HTTP 200, `application/manifest+json` and `no-cache, must-revalidate` (Task 2's filter).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): add the tenant web app manifest"
```

---

### Task 4: The offline page

**Files:**
- Create: `modules/ndopify-web/src/main/resources/public/offline.html`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/PwaAssetsTest.kt` (new)

**Interfaces:**
- Consumes: `/assets/images/logo.png`, `/assets/images/icon.png`; the approved prototype
  `modules/ndopify-web/src/main/html/offline.html`.
- Produces: `/offline.html`, served by the worker in Task 5.

- [ ] **Step 1: Write the failing test**

In `PwaAssetsTest` (same private `resource(path)` helper as `BundledAssetsTest`):

```kotlin
@Test
fun offlinePageIsSelfContained() {
    val html = resource("public/offline.html")
    assertTrue(html.contains("Vous êtes hors ligne"))
    assertTrue(html.contains("data-retry"))
    assertTrue(html.contains("data-offline-status"))
    assertTrue(html.contains("/assets/images/logo.png"))
    assertFalse(html.contains("fonts.googleapis.com"), "no webfont offline")
    assertFalse(html.contains("/assets/css/"), "no bundle dependency")
    assertFalse(html.contains("<script src="), "scripts must be inline")
    assertFalse(html.contains("rel=\"stylesheet\""), "styles must be inline")
    assertFalse(html.contains("status=online"), "the prototype's review-only branch is dropped")
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=PwaAssetsTest`
Expected: FAIL — missing resource.

- [ ] **Step 3: Build the page from the approved prototype**

Copy the structure, copy and inline script of `modules/ndopify-web/src/main/html/offline.html`, with these changes:

- Remove the prototype comment and the `<link rel="stylesheet" href="../resources/public/…">`.
- Replace it with one inline `<style>` containing, copied from `src/main/assets/css/styles.css`: the `:root` tokens
  the page reads (`--color-primary`, `--color-primary-hover`, `--color-bg-default`, `--color-bg-secondary`,
  `--color-text-default`, `--color-text-success`, `--color-text-disabled`, `--color-border-default`,
  `--font-heading`, `--font-body`, `--space-md`, `--space-lg`, `--space-xl`, `--space-section`, `--h1-size`,
  `--radius-full`, `--focus-ring`), plus `body`, `h1`, `.container`, `.section`, `.section--default`,
  `.skip-link`, `.skip-link:focus`, `.site-header`, `.site-header__logo img`, `.btn`, `.btn-primary`,
  `.btn-primary:hover`, `.btn:focus-visible`, and the prototype's `.offline*` block. Fonts fall back to
  `Georgia, "Times New Roman", serif` and the system sans stack: no `@import`, no webfont.
- Keep the absolute `/assets/images/logo.png` and `/assets/images/icon.png`.
- Keep the inline `<script>`, minus the review-only `?status=online` branch: the `online` handler writes
  *Connexion rétablie. Rechargement…* and reloads once; the retry link is upgraded to a reload.
- Keep the logo-only header, the title *Vous êtes hors ligne*, the text, the *Réessayer* link and the note.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=PwaAssetsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): add the offline fallback page"
```

---

### Task 5: Service worker and registration

**Files:**
- Create: `modules/ndopify-web/src/main/assets/sw.js`
- Modify: `modules/ndopify-web/build-assets.mjs`
- Modify: `src/main/assets/js/main.js` (registration, before the closing `})();`)
- Test: `PwaAssetsTest`, `BundledAssetsTest` (new cases)

**Interfaces:**
- Consumes: `/offline.html` (Task 4), `/assets/images/logo.png`, the bundle (Task 1).
- Produces: `/sw.js` (generated), with `VERSION` = first 12 hex chars of `sha256(offline.html bytes + logo bytes)`; precache `["/offline.html", "/assets/images/logo.png"]`.

- [ ] **Step 1: Write the failing tests**

In `PwaAssetsTest`:

```kotlin
@Test
fun serviceWorkerVersionMatchesTheOfflinePage() {
    val sw = resource("public/sw.js")
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(resourceBytes("public/offline.html"))
    digest.update(resourceBytes("public/assets/images/logo.png"))
    val version = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(12)
    assertTrue(sw.contains("const VERSION = \"$version\""), sw.take(200))
    assertTrue(sw.contains("/offline.html"))
    assertTrue(sw.contains("/assets/images/logo.png"))
    assertTrue(sw.contains("request.mode === \"navigate\""))
}
```

Add `resourceBytes(path): ByteArray` next to `resource(path)`.

In `BundledAssetsTest`:

```kotlin
@Test
fun jsBundleRegistersTheServiceWorker() {
    assertTrue(resource("public/assets/dist/app.min.js").contains("serviceWorker"))
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn test -Dtest='PwaAssetsTest,BundledAssetsTest'`
Expected: FAIL — no `sw.js`, no `serviceWorker` in the bundle.

- [ ] **Step 3: Write the worker template**

`src/main/assets/sw.js`:

```js
"use strict";
const VERSION = "__VERSION__";
const CACHE = "nweb-offline-" + VERSION;
const PRECACHE = ["/offline.html", "/assets/images/logo.png"];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(PRECACHE.map((url) => new Request(url, { cache: "reload" }))))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.mode === "navigate") {
        event.respondWith(
            fetch(request).catch(() =>
                caches.match("/offline.html").then((hit) => hit || Response.error())
            )
        );
        return;
    }
    const url = new URL(request.url);
    if (url.origin === self.location.origin && PRECACHE.includes(url.pathname)) {
        event.respondWith(caches.match(url.pathname).then((hit) => hit || fetch(request)));
    }
});
```

- [ ] **Step 4: Generate it in the build script**

Append to `build-assets.mjs`, after the bundles are written:

```js
const offline = await readFile("src/main/resources/public/offline.html");
const logo = await readFile("src/main/resources/public/assets/images/logo.png");
const version = createHash("sha256").update(offline).update(logo).digest("hex").slice(0, 12);
const sw = (await readFile("src/main/assets/sw.js", "utf8")).replace("__VERSION__", version);
await writeFile(`${out}/public/sw.js`, sw);
```

and add `import { createHash } from "node:crypto";` to the imports.

- [ ] **Step 5: Register the worker from the shared script**

At the end of `src/main/assets/js/main.js`, before the final `})();`:

```js
if ("serviceWorker" in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').then(function (registration) {
      registration.update();
    }).catch(function () { /* the app works without a worker */ });
  });
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Verify signed-out access and the worker**

Run `mvn spring-boot:run`, then, with no cookie:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/sw.js
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/offline.html
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/manifest.json
curl -s -D - -o /dev/null http://localhost:8082/sw.js | grep -i cache-control
```

Expected: `200`, `200`, `200`, and `no-cache, must-revalidate`. In DevTools → Application → Service Workers:
activated and controlling a page; Network → Offline → navigate → the French offline page; back online → it reloads
itself.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): add the offline service worker"
```

---

### Task 6: Update the module documentation

**Files:**
- Modify: `modules/ndopify-web/CLAUDE.md`

**Interfaces:**
- Consumes: the decisions above.
- Produces: accurate guidance for the next agent.

- [ ] **Step 1: Rewrite the affected paragraphs**

Update, keeping the file's tone:

- The *Static assets* paragraph: sources live in `src/main/assets/{css,js}` (a `styles`, `listing`, `proposal`, `visit`
  CSS order and a `main`, `listing`, `proposal`, `visit` JS order, merged because every page loads one bundle), built
  by `frontend-maven-plugin` + `build-assets.mjs` into `public/assets/dist/app.min.{css,js}`; images stay in
  `public/assets/images`.
- *`SecurityConfiguration`*: add `/sw.js`, `/offline.html` and `/manifest.json` to the permitted list.
- A new short paragraph on the PWA: `/manifest.json` is a controller rendering the tenant's name and `iconUrl`
  (fallback `Ndopify` and the interim `/assets/images/icon.png`); `sw.js` handles navigations and the two precached
  URLs only; `/offline.html` is self-contained; the rule that **HTML and API responses are never cached**.
- *Build*: `npm`/Node are installed by Maven; a clean run needs network; the bundles and `sw.js` are build output,
  never committed.

- [ ] **Step 2: Check for stale references**

```bash
grep -rn "public/assets/js\|public/assets/css\|manifest.webmanifest" modules/ndopify-web/CLAUDE.md
```

Expected: no match.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-web/CLAUDE.md
git commit -m "docs(web): document the asset bundle and pwa"
```

---

### Task 7: Visual pass and device verification

**Files:**
- Modify (only if the pass finds a regression): `src/main/assets/css/*.css`

**Interfaces:**
- Consumes: everything above.
- Produces: proof that the merge changed no layout, and that a deploy reaches an installed app.

- [ ] **Step 1: Run every page locally**

With a local ndopify-server and its seed data (module README), `mvn spring-boot:run`, then open at 375 px and 1280 px:
`/`, `/owner`, `/agent`, a published listing page, `/match/{id}?token=…`, `/visits/{id}?token=…`, an expired match
link (`/match/{id}` with no cookie), the matching visit link, and an unknown address (404).

Expected: each page looks as before. Pay attention to the listing page (its own CSS was page-scoped), the match page
(`proposal.css`) and the visit page (`visit.css`), which now cascade across the site.

- [ ] **Step 2: Fix any global-cascade regression at the source**

If a page-scoped rule leaks (a selector from `listing.css` / `proposal.css` / `visit.css` matching another page),
scope it to the page that owns it (under its `body[data-page]` or page class) instead of adding an undo. Re-run Step 1.

- [ ] **Step 3: Check the offline behaviour**

DevTools → Network → Offline: navigate to `/`, `/match/{id}`, `/visits/{id}` → the offline page each time. Turn the
network back on → the page reloads once by itself. Reload `/offline.html` with JS disabled → the retry link still
navigates to `/`.

- [ ] **Step 4: Verify a deploy reaches an installed app**

Note the bundle URL in the rendered HTML, deploy a CSS change (or change a rule and rebuild locally), reload → the
page carries a new `-<hash>` and the new styles are visible; unchanged files keep their hash.

- [ ] **Step 5: Verify on an iPhone and an Android phone**

- iPhone: Safari → Share → *Sur l'écran d'accueil*; it opens standalone with the tenant's name and icon (or Ndopify's),
  and re-opening after a deploy shows the new styles.
- Android: browser menu → Install; it opens standalone with the interim icon; the in-browser promotion is not expected
  yet.

- [ ] **Step 6: Commit any fix and report**

```bash
git add modules/ndopify-web/src/main/assets/css
git commit -m "fix(web): keep page-scoped rules scoped after the css merge"
```

If nothing changed, say so — no empty commit.
