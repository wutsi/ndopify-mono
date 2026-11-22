# Partner Central — Installable PWA with Versioned Assets — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve one minified CSS and one minified JS with content-versioned URLs and long-lived caching, make the app installable, and add a service worker that shows a branded "Vous êtes hors ligne" page when a navigation fails.

**Architecture:** Sources move to `src/main/assets/` and are concatenated and minified by esbuild (via `frontend-maven-plugin`) into `target/classes/public/assets/dist/`. Spring's content-version strategy appends `-<hash>` to the `@{}` URLs at runtime; a filter sets the cache headers. A generated, versioned `sw.js` intercepts navigations only (fallback to a self-contained `/offline.html`) and serves the two precached URLs. No HTML or API is ever cached.

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (MVC, Thymeleaf, Security), Maven, esbuild 0.28.2, Node v26.13.1, `frontend-maven-plugin` 1.15.1, kotlin.test + mockito-kotlin.

**Story:** https://github.com/wutsi/ndopify-mono/issues/240

## Global Constraints

- One CSS (`app.min.css`) and one JS (`app.min.js`), minified. CSS order, exactly: `partner-central`, `join`, `find-property`, `offer-property`, `home`, `listing`, `visit`, `profile`, `agent`, `telegram`. JS order, exactly: `util`, `direct-upload`, `partner-central`, `home`, `join`, `kyc`, `listing`, `mandate`, `visit`, `find-property`, `offer-property`.
- No caching of HTML or API responses. Assets only.
- Cache headers: `/assets/dist/**` → `public, max-age=31536000, immutable`; other `/assets/**` → `public, max-age=86400`; everything else (HTML, `/sw.js`, `/offline.html`, `/manifest.json`) → `no-cache, must-revalidate`.
- The service worker intercepts `mode === "navigate"` and the precached URLs; nothing else.
- Interim icon only: the manifest declares the existing `/assets/images/icon.png` (500×500). Do not generate icons.
- UI copy is French, code/comments/docs English; every page works without JavaScript.
- Pinned tool versions: esbuild `0.28.2`, Node `v26.13.1`, `frontend-maven-plugin` `1.15.1`.
- Tests are plain unit tests (`kotlin.test`, `mockito-kotlin`); no Spring context, no MySQL.

## Review Focus

- **An unversioned URL behind `immutable`.** If the content strategy does not rewrite `@{…}`, `/assets/dist/app.min.css` would be pinned for a year and a deploy would never reach a client. Verify the rendered page carries `-<hash>` before trusting the header (Task 2, manual curl).
- **KYC's review row.** `join.css` owns the shared `.review__*` layout and becomes global; KYC never loaded it today and `home.css`'s undo is scoped to `[data-mandate]`. Check `/kyc` at 375 px, and fix by scoping the shared rules to the wizards that own them (Task 8).
- **A failed form POST while offline.** It lands on `/offline.html` and the typed data is gone; the page says to check the result. Verify by posting a form with DevTools offline (Task 8).
- **A stale offline page.** The worker's `VERSION` is the hash of `offline.html` + the logo; if it did not change, an edited offline page would never ship. `PwaAssetsTest` recomputes the hash and compares (Task 5).
- **Signed-out access to the PWA files.** If `/sw.js`, `/offline.html` or `/manifest.json` fall behind authentication, registration and install fail for every signed-out visitor. Verify with `curl` signed out (Task 5).

---

### Task 1: One minified CSS + one JS bundle, loaded by every page

**Files:**
- Create: `modules/ndopify-partner-central/package.json`, `package-lock.json`, `build-assets.mjs`
- Create: `modules/ndopify-partner-central/src/main/assets/css/` (10 files, moved), `src/main/assets/js/` (11 files, moved)
- Modify: `modules/ndopify-partner-central/pom.xml`
- Modify: `/Users/herve/Projects/ndopify-mono/.gitignore`
- Modify: `templates/fragments/head.html` and all 22 templates that call it
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/BundledAssetsTest.kt` (new)
- Test: the ten template tests listed in step 4

**Interfaces:**
- Consumes: nothing.
- Produces: classpath resources `public/assets/dist/app.min.css` and `public/assets/dist/app.min.js`; `fragments/head :: head(title, description, cdn)` (two parameters removed).

- [ ] **Step 1: Write the failing bundle test**

Create `BundledAssetsTest.kt` in `com.wutsi.ndopify.npc`:

```kotlin
class BundledAssetsTest {
    @Test
    fun cssBundleIsBuiltInOrder() {
        val css = resource("public/assets/dist/app.min.css")
        assertTrue(css.length > 10_000, "css bundle is ${css.length} bytes")
        assertTrue(css.contains(".site-header"))            // partner-central.css, first
        assertTrue(css.contains(".connect-steps"))          // telegram.css, last
        assertTrue(css.indexOf(".site-header") < css.indexOf(".connect-steps"))
    }

    @Test
    fun jsBundleHasTheSharedScripts() {
        val js = resource("public/assets/dist/app.min.js")
        assertTrue(js.length > 10_000, "js bundle is ${js.length} bytes")
        assertTrue(js.contains("NdDirectUpload"))
        assertTrue(js.contains("data-page"))
        assertTrue(js.indexOf("NdDirectUpload") < js.indexOf("data-page"))
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
            ?: fail("missing classpath resource $path")
}
```

`fail(...)` is `kotlin.test.fail`. Then change `AgentProfileTemplateTest` to expect the bundle instead of the three old files:

```kotlin
assertEquals("/assets/dist/app.min.css", page.selectFirst("link[href=/assets/dist/app.min.css]")?.attr("href"))
assertNotNull(page.selectFirst("script[src=/assets/dist/app.min.js]"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='BundledAssetsTest,AgentProfileTemplateTest'`
Expected: `BundledAssetsTest` fails on the missing resource; `AgentProfileTemplateTest` fails on the missing `<link>`.

- [ ] **Step 3: Move the sources**

```bash
cd modules/ndopify-partner-central
mkdir -p src/main/assets/css src/main/assets/js
git mv src/main/resources/public/assets/css/*.css src/main/assets/css/
git mv src/main/resources/public/assets/js/*.js src/main/assets/js/
```

- [ ] **Step 4: Add the build script and package files**

`package.json`:

```json
{
  "name": "ndopify-partner-central-assets",
  "private": true,
  "type": "module",
  "devDependencies": { "esbuild": "0.28.2" },
  "scripts": { "build": "node build-assets.mjs" }
}
```

Run `npm install` once in the module, then commit `package-lock.json`. Add `node_modules/` to the repo `.gitignore`.

`build-assets.mjs` (exact behavior; keep the two order arrays in sync with Global Constraints):

```js
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { transform } from "esbuild";

const CSS = ["partner-central", "join", "find-property", "offer-property", "home",
             "listing", "visit", "profile", "agent", "telegram"];
const JS = ["util", "direct-upload", "partner-central", "home", "join", "kyc",
            "listing", "mandate", "visit", "find-property", "offer-property"];
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

(The `sw.js` generation is added in Task 5.)

- [ ] **Step 5: Wire the build into Maven**

In `modules/ndopify-partner-central/pom.xml`, add a module `<properties>` block between `</repositories>` and `<dependencies>`:

```xml
<properties>
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

`templates/fragments/head.html`: change the fragment signature to `head(title, description, cdn)`, delete the `partner-central.css` line and both `th:each` loops, and in their place render:

```html
<link rel="stylesheet" th:href="@{/assets/dist/app.min.css}">
```

then the `cdn` block (unchanged), then:

```html
<script th:src="@{/assets/dist/app.min.js}" defer></script>
```

- [ ] **Step 7: Update the 22 call sites**

Each `<head th:replace="~{fragments/head :: head(...)}">` loses its fourth and fifth arguments (the `${ {'…'} }` lists). Keep `true`/`false` (the `cdn` flag). `templates/mandate/visit.html` drops the dead `'rejoindre'` list with them. Verify with a repo-wide grep that no `head(` call still passes a list.

- [ ] **Step 8: Update the remaining template tests**

In these tests, replace the per-file `<link>`/`<script>` assertions with the bundle assertion from Step 1, and delete assertions that order one page script before another:

`AgentProfileTemplateTest`, `KycTemplateTest`, `ProfileTemplateTest`, `MandateTemplateTest`, `MandateEditTemplateTest`, `MandateCloseTemplateTest`, `MandateContractTemplateTest`, `ListingTemplateTest`, `ListingSectionTemplateTest`, `ListingPhotoTemplateTest`.

- [ ] **Step 9: Run the module tests**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: BUILD SUCCESS, including `BundledAssetsTest` and every template test.

- [ ] **Step 10: Commit**

```bash
git add .gitignore modules/ndopify-partner-central
git commit -m "feat(npc): serve one minified css and js bundle"
```

---

### Task 2: Versioned asset URLs and cache headers

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/application.yml`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/config/AssetCacheFilter.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/config/AssetCacheConfiguration.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/config/AssetCacheFilterTest.kt`

**Interfaces:**
- Consumes: the bundle at `/assets/dist/app.min.{css,js}` (Task 1).
- Produces: `Cache-Control` headers on every response; `@{…}` asset URLs rendered as `…-<hash>.<ext>`.

- [ ] **Step 1: Write the failing filter test**

```kotlin
class AssetCacheFilterTest {
    private val filter = AssetCacheFilter()

    private fun cacheControl(path: String): String {
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", path), response, MockFilterChain())
        return response.getHeader("Cache-Control")
    }

    @Test
    fun bundleIsImmutable() =
        assertEquals("public, max-age=31536000, immutable", cacheControl("/assets/dist/app.min.css"))

    @Test
    fun otherAssetsAreCachedForADay() =
        assertEquals("public, max-age=86400", cacheControl("/assets/images/logo.png"))

    @Test
    fun pagesAreRevalidated() =
        assertEquals("no-cache, must-revalidate", cacheControl("/"))

    @Test
    fun pwaFilesAreRevalidated() {
        assertEquals("no-cache, must-revalidate", cacheControl("/sw.js"))
        assertEquals("no-cache, must-revalidate", cacheControl("/manifest.json"))
        assertEquals("no-cache, must-revalidate", cacheControl("/offline.html"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=AssetCacheFilterTest`
Expected: FAIL — `AssetCacheFilter` does not exist.

- [ ] **Step 3: Implement the filter**

`AssetCacheFilter` is a `OncePerRequestFilter` in `com.wutsi.ndopify.npc.config`:

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
}
```

`cacheControl(path: String): String` maps the three cases from Global Constraints (prefix `/assets/dist/`, prefix `/assets/`, otherwise). Set the header **before** the chain, so it is present when the resource handler writes the body.

`AssetCacheConfiguration.kt` declares it:

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

Under `spring:` in `application.yml`:

```yaml
spring:
    web:
        resources:
            chain:
                enabled: true
                strategy:
                    content:
                        enabled: true
                        paths: /assets/**
```

Thymeleaf's `ResourceUrlEncodingFilter` is auto-registered by Spring Boot 4.1.2; no Kotlin bean is needed.

- [ ] **Step 6: Verify a rendered page against the running app**

Run `mvn spring-boot:run`, then:

```bash
curl -s http://localhost:8081/login | grep -o '/assets/dist/app[^"]*'
```

Expected: a URL of the form `/assets/dist/app-<hash>.min.css` **and** `/assets/dist/app-<hash>.min.js` (the hash is mandatory — see Review Focus). Then:

```bash
curl -s -D - -o /dev/null http://localhost:8081/assets/dist/app-<hash>.min.css | grep -i cache-control
curl -s -D - -o /dev/null http://localhost:8081/login | grep -i cache-control
```

Expected: `public, max-age=31536000, immutable` and `no-cache, must-revalidate`.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): version static assets and cache the bundle"
```

---

### Task 3: PWA manifest and head metadata

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/public/manifest.json`
- Modify: `templates/fragments/head.html`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt` (new)
- Test: `AgentProfileTemplateTest` (one new assertion)

**Interfaces:**
- Consumes: `fragments/head` (Task 1), `/assets/images/icon.png`.
- Produces: `/manifest.json`; manifest/apple `<meta>` tags on every page.

- [ ] **Step 1: Write the failing test**

In `PwaAssetsTest` (give it the same private `resource(path)` helper as `BundledAssetsTest`, plus `resourceBytes(path): ByteArray`):

```kotlin
@Test
fun manifestIsValidJsonWithTheInterimIcon() {
    val json = ObjectMapper().readTree(resource("public/manifest.json"))
    assertEquals("Ndopify Partner Central", json["name"].asText())
    assertEquals("Ndopify", json["short_name"].asText())
    assertEquals("/", json["start_url"].asText())
    assertEquals("/", json["scope"].asText())
    assertEquals("standalone", json["display"].asText())
    assertEquals("fr", json["lang"].asText())
    assertEquals("/assets/images/icon.png", json["icons"][0]["src"].asText())
    assertEquals("500x500", json["icons"][0]["sizes"].asText())
}
```

In `AgentProfileTemplateTest`:

```kotlin
assertEquals("/manifest.json", page.selectFirst("link[rel=manifest]")?.attr("href"))
assertNotNull(page.selectFirst("meta[name=apple-mobile-web-app-title]"))
assertNotNull(page.selectFirst("meta[name=apple-mobile-web-app-capable]"))
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -Dtest='PwaAssetsTest,AgentProfileTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Add the manifest**

Named `manifest.json`, not `manifest.webmanifest`: embedded Tomcat 11 and Spring's `MediaTypeFactory` have no
`.webmanifest` mapping, so it would be served as `application/octet-stream`. `.json` is served as `application/json`,
which browsers accept. `public/manifest.json`:

```json
{
  "name": "Ndopify Partner Central",
  "short_name": "Ndopify",
  "description": "Espace agent Ndopify Partner Central.",
  "lang": "fr",
  "start_url": "/",
  "scope": "/",
  "display": "standalone",
  "theme_color": "#ffffff",
  "background_color": "#ffffff",
  "icons": [
    { "src": "/assets/images/icon.png", "sizes": "500x500", "type": "image/png", "purpose": "any" }
  ]
}
```

- [ ] **Step 4: Add the head metadata**

In `fragments/head.html`, after the favicon links:

```html
<link rel="manifest" th:href="@{/manifest.json}">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Ndopify">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest='PwaAssetsTest,AgentProfileTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Verify the content type**

With `mvn spring-boot:run`:

```bash
curl -s -D - -o /dev/null http://localhost:8081/manifest.json | grep -i content-type
```

Expected: `application/json` (if it is `application/octet-stream`, the file was named wrongly — fix it rather than adding a
custom mime mapping).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): add the web app manifest and apple metadata"
```

---

### Task 4: The offline page

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/public/offline.html`
- Test: `PwaAssetsTest` (new cases)

**Interfaces:**
- Consumes: `/assets/images/logo.png`; the approved prototype `src/main/html/offline.html`.
- Produces: `/offline.html`, served by the worker in Task 5.

- [ ] **Step 1: Write the failing test**

In `PwaAssetsTest`:

```kotlin
@Test
fun offlinePageIsSelfContained() {
    val html = resource("public/offline.html")
    assertTrue(html.contains("Vous êtes hors ligne"))
    assertTrue(html.contains("data-retry"))
    assertTrue(html.contains("/assets/images/logo.png"))
    assertFalse(html.contains("fonts.googleapis.com"), "no webfonts offline")
    assertFalse(html.contains("/assets/css/"), "no bundle dependency")
    assertFalse(html.contains("<script src="), "scripts must be inline")
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=PwaAssetsTest`
Expected: FAIL — missing resource.

- [ ] **Step 3: Build the page from the approved prototype**

Copy the structure and copy of `modules/ndopify-partner-central/src/main/html/offline.html`, with these changes:

- Remove the Google Fonts `<link>`s and the `../resources/public/assets/css/partner-central.css` link.
- Replace them with one inline `<style>` containing, copied from `partner-central.css`: the `:root` tokens the page reads (`--primary`… the tier-1 colours used, `--font-*`, `--space-*`, `--step-h1`, `--step-hint`, `--radius-control`, `--target`, `--header-height`, `--color-*` names used), plus `body`, `.skip-link`, `.site-header`, `.site-header__inner`, `.site-header__brand`, `.site-header__brand img`, `.page`, `.column`, `.btn`, `.btn--primary`, `.btn--primary:hover`, and the prototype's `.offline*` block. Fonts fall back to Georgia/system sans (no webfonts offline).
- Change the logo to `/assets/images/logo.png` (absolute).
- Keep the inline `<script>` (retry link + `online` status + one auto-reload) and the inline SVG icon.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=PwaAssetsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): add the offline fallback page"
```

---

### Task 5: Service worker, registration, and public paths

**Files:**
- Create: `modules/ndopify-partner-central/src/main/assets/sw.js`
- Modify: `modules/ndopify-partner-central/build-assets.mjs`
- Modify: `src/main/assets/js/partner-central.js` (registration, before the closing `})();`)
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt`
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
}
```

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
const CACHE = "npc-offline-" + VERSION;
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

- [ ] **Step 5: Register the worker from the shared script**

At the end of `src/main/assets/js/partner-central.js`, before the final `})();`:

```js
if ("serviceWorker" in navigator) {
    window.addEventListener("load", function () {
        navigator.serviceWorker.register("/sw.js").then(function (registration) {
            registration.update();
        }).catch(function () { /* the app works without a worker */ });
    });
}
```

- [ ] **Step 6: Make the PWA files public**

In `SecurityConfiguration.filterChain`, add:

```kotlin
.requestMatchers("/sw.js", "/offline.html", "/manifest.json").permitAll()
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Verify signed-out access and the worker**

Run `mvn spring-boot:run`, then, with no cookie:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/sw.js
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/offline.html
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/manifest.json
curl -s -D - -o /dev/null http://localhost:8081/sw.js | grep -i cache-control
```

Expected: `200`, `200`, `200`, and `no-cache, must-revalidate` (not `302` to `/login`). In DevTools → Application → Service Workers: activated and controlling; Network → Offline → reload → the French offline page; back online → it reloads itself.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): add the offline service worker"
```

---

### Task 6: Remove the dead `generate-asset-version` step

**Files:**
- Modify: `modules/ndopify-partner-central/pom.xml` (the `exec-maven-plugin` block, lines ~155–177)
- Modify: `modules/ndopify-web/pom.xml` (the same block, ~166–186)
- Modify: `modules/ndopify-esignature/pom.xml` (the same block, ~138–158)

**Interfaces:**
- Consumes: nothing.
- Produces: no `wutsi.properties` in any built jar.

- [ ] **Step 1: Delete the three blocks**

Remove the whole `<plugin>` whose execution id is `generate-asset-version` (`org.codehaus.mojo:exec-maven-plugin`) from each POM. Leave the root POM's `pluginManagement` entry and `maven-exec-plugin.version` property.

- [ ] **Step 2: Verify**

```bash
# from the repo root
grep -rn "generate-asset-version" --include=pom.xml .        # expected: no output
(cd modules/ndopify-web && mvn -q validate)
(cd modules/ndopify-esignature && mvn -q validate)
(cd modules/ndopify-partner-central && mvn -q -DskipTests package)
test ! -f modules/ndopify-partner-central/target/classes/wutsi.properties && echo "wutsi.properties gone"
```

Expected: no grep match, both builds succeed, `wutsi.properties gone`.

- [ ] **Step 3: Commit**

```bash
git add modules/*/pom.xml
git commit -m "chore: remove the unused generate-asset-version step"
```

---

### Task 7: Update the module documentation

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

**Interfaces:**
- Consumes: the decisions above.
- Produces: accurate guidance for the next agent.

- [ ] **Step 1: Rewrite the affected paragraphs**

Update, keeping the file's tone:

- *Code Organization › Resources*: sources live in `src/main/assets/{css,js}` (not served), built by `frontend-maven-plugin` + `build-assets.mjs` into `public/assets/dist/app.min.{css,js}`; the CSS/JS bundle order and why; page scripts are still IIFEs gated on `data-page`, but all ship in one bundle. Images stay in `public/assets/images`.
- *Pages ›* the authentication list: add `/sw.js`, `/offline.html`, `/manifest.json`.
- A new short paragraph on the PWA: the manifest, the interim icon, `sw.js` (navigations + the two precached URLs only), `/offline.html`, and the rule that **HTML and API responses are never cached**.
- *Build*: `npm`/Node are installed by Maven; the build needs network on a clean run; the bundles are build output, never committed.

- [ ] **Step 2: Check for stale references**

```bash
grep -rn "public/assets/js\|public/assets/css\|manifest.webmanifest" modules/ndopify-partner-central/CLAUDE.md
```

Expected: no match.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the asset bundle and pwa"
```

---

### Task 8: Visual pass and device verification

**Files:**
- Modify (only if the pass finds a regression): `src/main/assets/css/*.css`

**Interfaces:**
- Consumes: everything above.
- Produces: proof that the merge changed no layout.

- [ ] **Step 1: Run every page locally**

`mvn spring-boot:run` against a local ndopify-server (seed data per the module README), then open, at 375 px and 1280 px: `/login`, `/join`, `/find-property`, `/offer-property`, `/` (agent home), `/kyc`, `/profile`, `/profile/photo`, `/agent`, `/mandates/{id}`, `/mandates/{id}/edit`, `/mandates/{id}/close`, `/mandates/{id}/contract`, `/mandates/{id}/customer/edit`, `/mandates/{id}/visits`, `/listings/{id}`, `/listings/{id}/location`, `/listings/{id}/photos`, `/visits/{id}`, `/telegram`, and the 403/404 pages.

Expected: each page looks as before. Pay attention to **`/kyc`'s review row** (Review Focus) and to alerts (`.alert`) on home and mandate pages.

- [ ] **Step 2: Fix any global-cascade regression at the source**

If KYC's review row moved, scope `join.css`'s `.review__*` rules to the wizards that own them (e.g. under their page classes) or add a KYC-specific undo next to the other `home.css` undo — prefer scoping. Re-run Step 1.

- [ ] **Step 3: Check the offline behaviour**

DevTools → Network → Offline: submit a form with typed data → the offline page appears; the note about checking the result is visible; turning the network back on reloads once. Confirm that an offline navigation to any page shows the same page.

- [ ] **Step 4: Verify on an iPhone and an Android phone**

- iPhone: Safari → Share → *Sur l'écran d'accueil*; deploy a CSS change; reopen from the home screen → the new styles are visible (the rendered page carries a new `-<hash>`).
- Android: browser menu → Install; it opens standalone with the interim icon (the in-browser promotion is not expected yet).

- [ ] **Step 5: Commit any fix and report**

```bash
git add modules/ndopify-partner-central/src/main/assets/css
git commit -m "fix(npc): keep the review layout scoped after the css merge"
```

If nothing changed, say so — no empty commit.
