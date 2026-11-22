# ndopify-web — Installable PWA with Versioned Assets, Tenant Icon Variants and an Offline Page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve one minified CSS and one minified JS with content-versioned URLs and long-lived caching, make the public site installable with each tenant's own PWA icon set, and add a service worker that shows a branded "Vous êtes hors ligne" page when a navigation fails.

**Architecture:** Four nullable icon-variant URLs move from `ndopify-dto`'s `Tenant` into four new `T_TENANT` columns in `ndopify-server`; `ndopify-web` renders them in a tenant-branded `/manifest.json`, falling back to Ndopify's polished icon set. Sources move to `src/main/assets/` and are concatenated and minified by esbuild (via `frontend-maven-plugin`) into `target/classes/public/assets/dist/`. Spring's content-version strategy appends `-<hash>` to the `@{}` URLs at runtime; a filter sets the cache headers. A generated, versioned `sw.js` intercepts navigations only (fallback to a self-contained `/offline.html`) and serves the two precached URLs. No HTML or API is ever cached.

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (MVC, Thymeleaf, Security), JPA/Hibernate, Flyway, Maven, esbuild 0.28.2, Node v26.13.1, `frontend-maven-plugin` 1.15.1, kotlin.test + mockito-kotlin.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/241 (enriched story) and its approved prototype, `modules/ndopify-web/src/main/html/offline.html`.

## Global Constraints

### Release order (dto → server → web)

- `ndopify-dto` gains four nullable `Tenant` fields next to `iconUrl`: `icon192Url`, `icon512Url`, `maskableIconUrl`, `appleTouchIconUrl`. Its current version is `0.0.101-SNAPSHOT`; it is released as **0.0.101**, and the root `ndopify-dto.version` moves from `0.0.100` to `0.0.101` after the tag exists.
- `ndopify-server` adds the four columns and maps them; `ndopify-web` consumes them. The web app does not compile against 0.0.100, so the order is mandatory: dto release, then server, then web.
- `ndopify-web-core` does not change.

### Icons — all or nothing, never a missing URL

- `/manifest.json` declares each variant the tenant sets — `icon192Url` → `192x192`/`any`, `icon512Url` → `512x512`/`any`, `maskableIconUrl` → `512x512`/`maskable` — and nothing else.
- A tenant with no variant but an `iconUrl` gets `iconUrl` alone, `sizes` omitted (the browser reads the intrinsic size).
- A tenant with no icon at all gets Ndopify's `icon-192.png` (192x192, any), `icon-512.png` (512x512, any) and `icon-maskable-512.png` (512x512, maskable) — never mixed with a tenant icon.
- `head.html`'s `apple-touch-icon` is `appleTouchIconUrl`, else `iconUrl`, else the copied `/assets/images/apple-touch-icon.png` (180x180). `apple-mobile-web-app-title` is the tenant's name, else `Ndopify`.
- Do not generate or resize icons. Copy Partner Central's four files; declare only URLs that exist.
- Local and test seed tenant 1 with the four variants through Flyway (`db/migration/local` and `db/migration/test`), pointing at Partner Central's four PNGs copied to the test bucket's `www/assets/images/`. Prod keeps the ops `UPDATE`.

### Bundle, caching, worker

- One CSS (`app.min.css`) and one JS (`app.min.js`), minified. CSS order, exactly: `styles`, `listing`, `proposal`, `visit`. JS order, exactly: `main`, `listing`, `proposal`, `visit`. The order preserves every "loaded after" pair.
- `head` loses its `styles` list parameter: every page loads the one bundle.
- No caching of HTML or API responses. Assets only.
- Cache headers: `/assets/dist/**` → `public, max-age=31536000, immutable`; other `/assets/**` → `public, max-age=86400`; everything else (HTML, `/sw.js`, `/offline.html`, `/manifest.json`) → `no-cache, must-revalidate`.
- The service worker intercepts `mode === "navigate"` and the precached URLs; nothing else. On activate it deletes only caches prefixed `nweb-offline-`.
- The offline page is Ndopify-branded and tenant-neutral: no tenant name or logo.

### Tenant origin and copy

- Each tenant's `publicPortalUrl` is a **distinct origin**, so `start_url`/`scope` `/` and the root-scoped `/sw.js` are per-tenant. A path-prefixed tenant is out of scope.
- UI copy is French, code/comments/docs English; every page works without JavaScript.
- Pinned tool versions: esbuild `0.28.2`, Node `v26.13.1`, `frontend-maven-plugin` `1.15.1`.
- `ndopify-web` tests are plain unit tests (`kotlin.test`, `mockito-kotlin`); no Spring context, no MySQL. `ndopify-server`'s endpoint test needs the local MySQL.

## Review Focus

- **A mixed icon set.** A tenant with one variant and a Ndopify fallback would let Chrome pick Ndopify's 512 and mis-brand the tenant's install. The rule is all-or-nothing; `ManifestControllerTest` pins the three branches and the "never mixed" case (Task 6).
- **A manifest icon that 404s.** Declaring a guessed size, or a bundled file that was not copied, makes install fail. Tests assert every declared `src` is either a tenant URL or a shipped file (Task 6), and the files are checked on the classpath.
- **The release order.** Building `ndopify-web` against 0.0.100 fails at compile time; deploying `ndopify-server` before the DTO is released fails too. Verify the tag and resolve 0.0.101 from GitHub Packages before the server task (Tasks 1–3).
- **An unversioned URL behind `immutable`.** If the content strategy does not rewrite `@{…}`, `/assets/dist/app.min.css` would be pinned for a year. Verify the rendered page carries `-<hash>` (Task 5, manual curl).
- **A merged CSS bundle leaking page rules.** `listing.css`, `proposal.css` and `visit.css` are page-scoped today; merged, they become global. Check `/`, `/owner`, `/agent`, a listing page, `/match/{id}`, `/visits/{id}` at 375 px and 1280 px (Task 10).

---

### Task 1: ndopify-dto — the four tenant icon-variant fields (release 0.0.101)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/refdata/dto/Tenant.kt`
- Test: none — the DTO has no tests; `ndopify-server`'s endpoint test covers it (Task 3).

**Interfaces:**
- Consumes: nothing.
- Produces: `Tenant(…, iconUrl: String? = null, icon192Url: String? = null, icon512Url: String? = null, maskableIconUrl: String? = null, appleTouchIconUrl: String? = null, …)`, released as `com.wutsi.ndopify:ndopify-dto:0.0.101`.

- [ ] **Step 1: Add the fields**

In `Tenant.kt`, right after `iconUrl`, add four nullable strings with defaults:

```kotlin
    val iconUrl: String? = null,
    val icon192Url: String? = null,
    val icon512Url: String? = null,
    val maskableIconUrl: String? = null,
    val appleTouchIconUrl: String? = null,
```

No `@JsonProperty` is needed: Jackson's bean naming yields `icon192Url`, `icon512Url`, `maskableIconUrl`,
`appleTouchIconUrl` from the Kotlin getters. Every caller uses named arguments (`grep -rn "Tenant(" modules` to confirm
none is positional).

- [ ] **Step 2: Build the module**

Run: `mvn -q -pl modules/ndopify-dto install`
Expected: BUILD SUCCESS; the local `~/.m2` now has `0.0.101-SNAPSHOT`.

- [ ] **Step 3: Commit and push**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/refdata/dto/Tenant.kt
git commit -m "feat(dto): add the tenant pwa icon variants"
git push origin main
```

Pushing runs `ndopify-dto-main.yml`, which releases `0.0.101` and tags it.

- [ ] **Step 4: Wait for the release tag**

```bash
git fetch --tags
git tag --list ndopify-dto-0.0.101
```

Expected: `ndopify-dto-0.0.101`. Do not start Task 3 before it exists.

---

### Task 2: Use ndopify-dto 0.0.101

**Files:**
- Modify: `pom.xml` (root, `<ndopify-dto.version>`)

**Interfaces:**
- Consumes: the `0.0.101` tag and artifact (Task 1).
- Produces: every module resolves `com.wutsi.ndopify:ndopify-dto:0.0.101`.

- [ ] **Step 1: Bump the root property**

In the root `pom.xml`:

```xml
<ndopify-dto.version>0.0.101</ndopify-dto.version>
```

- [ ] **Step 2: Verify the released artifact resolves**

Run: `mvn -q -s settings.xml -pl modules/ndopify-web -DskipTests compile`
Expected: BUILD SUCCESS (GitHub Packages serves 0.0.101). A `Could not find artifact …:0.0.101` means the release
workflow has not finished or failed.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: use ndopify-dto 0.0.101"
```

---

### Task 3: ndopify-server — store and expose the icon variants

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V15_0__tenant-pwa-icons.sql`
- Create: `modules/ndopify-server/src/main/resources/db/migration/local/V15_0_1__tenant-pwa-icons.sql`
- Create: `modules/ndopify-server/src/main/resources/db/migration/test/V15_0_1__tenant-pwa-icons.sql`
- Modify: `src/main/kotlin/com/wutsi/ndopify/refdata/server/domain/TenantEntity.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/refdata/server/mapper/TenantMapper.kt`
- Modify: `src/test/resources/db/test/refdata/SearchTenantEndpoint.sql`
- Test: `src/test/kotlin/com/wutsi/ndopify/refdata/server/endpoint/SearchTenantEndpointTest.kt`

**Interfaces:**
- Consumes: `Tenant.icon192Url` / `icon512Url` / `maskableIconUrl` / `appleTouchIconUrl` (Task 1, resolved by Task 2).
- Produces: `GET /v1/tenants` returns the four fields; `T_TENANT` has `icon_192_url`, `icon_512_url`, `maskable_icon_url`, `apple_touch_icon_url`; in local and test, tenant 1 carries S3 URLs that resolve to 200.

There is no admin console or settings API: filling a tenant's variants in any environment is an **SQL/ops step**
(`UPDATE T_TENANT SET icon_192_url = … WHERE id = …`), never a screen or an endpoint in this story. The exception is
local and test, which get the `UPDATE` as an environment seed migration (Step 7) so every environment exercises the
tenant-branded manifest without a manual step; prod still needs the ops `UPDATE`.

- [ ] **Step 1: Write the failing test**

In `SearchTenantEndpointTest`, add (and import `kotlin.test.assertNull`):

```kotlin
@Test
fun `pwa icon variants`() {
    val tenants = rest.getForEntity("/v1/tenants", SearchTenantResponse::class.java).body!!.tenants

    val tenant = tenants.first { it.id == 1L }
    assertEquals("https://cdn.ndopify.cm/icon-192.png", tenant.icon192Url)
    assertEquals("https://cdn.ndopify.cm/icon-512.png", tenant.icon512Url)
    assertEquals("https://cdn.ndopify.cm/icon-maskable-512.png", tenant.maskableIconUrl)
    assertEquals("https://cdn.ndopify.cm/apple-touch-icon.png", tenant.appleTouchIconUrl)

    val plain = tenants.first { it.id == 2L }
    assertNull(plain.icon192Url)
    assertNull(plain.icon512Url)
    assertNull(plain.maskableIconUrl)
    assertNull(plain.appleTouchIconUrl)
}
```

and seed tenant 1 in `SearchTenantEndpoint.sql`, after the `time_zone` UPDATE:

```sql
-- Tenant 1 also exercises the PWA icon variants
UPDATE T_TENANT SET
    icon_192_url = 'https://cdn.ndopify.cm/icon-192.png',
    icon_512_url = 'https://cdn.ndopify.cm/icon-512.png',
    maskable_icon_url = 'https://cdn.ndopify.cm/icon-maskable-512.png',
    apple_touch_icon_url = 'https://cdn.ndopify.cm/apple-touch-icon.png'
WHERE id = 1;
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchTenantEndpointTest`
Expected: FAIL — the fixture's `UPDATE` names columns that do not exist yet, and `Tenant` has no such properties
until Task 2's version is in use.

- [ ] **Step 3: Add the migration**

`modules/ndopify-server/src/main/resources/db/migration/common/V15_0__tenant-pwa-icons.sql` — the next version after
`V14_1`, never edit an existing one:

```sql
ALTER TABLE T_TENANT
    ADD COLUMN icon_192_url TEXT,
    ADD COLUMN icon_512_url TEXT,
    ADD COLUMN maskable_icon_url TEXT,
    ADD COLUMN apple_touch_icon_url TEXT;
```

- [ ] **Step 4: Map the entity**

In `TenantEntity`, after `iconUrl`, add the four nullable fields. Use explicit `@Column` names (the camelCase-to-snake
strategy is not trusted with the digit in `icon192Url`):

```kotlin
    val iconUrl: String? = null,

    @Column(name = "icon_192_url")
    val icon192Url: String? = null,

    @Column(name = "icon_512_url")
    val icon512Url: String? = null,

    @Column(name = "maskable_icon_url")
    val maskableIconUrl: String? = null,

    @Column(name = "apple_touch_icon_url")
    val appleTouchIconUrl: String? = null,
```

Add `import jakarta.persistence.Column`.

- [ ] **Step 5: Map the DTO**

In `TenantMapper.toTenant`, after `iconUrl = entity.iconUrl,`:

```kotlin
            icon192Url = entity.icon192Url,
            icon512Url = entity.icon512Url,
            maskableIconUrl = entity.maskableIconUrl,
            appleTouchIconUrl = entity.appleTouchIconUrl,
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchTenantEndpointTest`
Expected: PASS (both tests), against the local MySQL with Flyway applying `V15_0`.

- [ ] **Step 7: Seed the local and test tenants**

Add the same `UPDATE` to both environment seed directories, versioned after the common migration so it applies once
the four columns exist. `V15_0_1` sorts after `V15_0` in both environments (each loads `common` + one of
`local` / `test`; their existing seed is the older `V1_2_1`). The URLs are already live: Partner Central's four PNGs,
copied to the public site's folder of the test bucket (`www/assets/images/`), public and answering 200:

`modules/ndopify-server/src/main/resources/db/migration/local/V15_0_1__tenant-pwa-icons.sql` and
`modules/ndopify-server/src/main/resources/db/migration/test/V15_0_1__tenant-pwa-icons.sql`:

```sql
-- Tenant 1 installs with the PWA icon set copied from Partner Central to the public site's assets.
UPDATE T_TENANT SET
    icon_192_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-192.png',
    icon_512_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-512.png',
    maskable_icon_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/icon-maskable-512.png',
    apple_touch_icon_url = 'https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/apple-touch-icon.png'
WHERE id = 1;
```

Do not edit the tenants' existing `V1_2_1__refdata-tenant.sql`: the test database is deployed and Flyway checks
applied migrations by checksum. The test environment applies `V15_0_1` on its next deploy; locally, the next
`mvn spring-boot:run` (or `mvn test`) applies it, and Task 6 Step 8 verifies the manifest it produces.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): store the tenant pwa icon variants"
```

---

### Task 4: ndopify-web — one minified CSS + one JS bundle, loaded by every page

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

In `modules/ndopify-web/pom.xml`, add to the existing module `<properties>` block:

```xml
    <frontend-maven-plugin.version>1.15.1</frontend-maven-plugin.version>
    <node.version>v26.13.1</node.version>
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

### Task 5: ndopify-web — versioned asset URLs and cache headers

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/application.yml`
- Create: `src/main/kotlin/com/wutsi/ndopify/web/config/AssetCacheFilter.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/web/config/AssetCacheConfiguration.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/config/AssetCacheFilterTest.kt`

**Interfaces:**
- Consumes: the bundles at `/assets/dist/app.min.{css,js}` (Task 4).
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

Nothing to change: #240 removed it.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): version static assets and cache the bundle"
```

---

### Task 6: ndopify-web — tenant icon variants in the manifest and the head

**Files:**
- Copy: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon-192.png`, `icon-512.png`,
  `icon-maskable-512.png`, `apple-touch-icon.png` → `modules/ndopify-web/src/main/resources/public/assets/images/`
- Create: `src/main/kotlin/com/wutsi/ndopify/web/ui/pwa/ManifestController.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/ui/pwa/ManifestControllerTest.kt`
- Modify: `templates/fragments/head.html`
- Modify: `src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Create: `src/test/kotlin/com/wutsi/ndopify/web/PwaAssetsTest.kt` (with the `resource` and `resourceBytes` helpers)
- Test: `IndexTemplateTest`, `Error404TemplateTest`, `PwaAssetsTest` (icon files)

**Interfaces:**
- Consumes: `Tenant.icon192Url` / `icon512Url` / `maskableIconUrl` / `appleTouchIconUrl` (Task 1), the `tenant` model attribute, the copied icon files.
- Produces: `GET /manifest.json` → `application/manifest+json`, body `{"name", "short_name", "lang", "start_url", "scope", "display", "theme_color", "background_color", "icons": [{"src", "sizes"?, "type", "purpose"}]}`.

- [ ] **Step 1: Write the failing test**

```kotlin
class ManifestControllerTest {
    @ControllerAdvice
    class FakeTenantAdvice {
        @ModelAttribute("tenant")
        fun tenant(): Tenant = tenant
    }

    companion object {
        val FULL = Tenant(
            name = "Ndopify Cameroun",
            iconUrl = "https://cdn.ndopify.cm/icon.png",
            icon192Url = "https://cdn.ndopify.cm/icon-192.png",
            icon512Url = "https://cdn.ndopify.cm/icon-512.png",
            maskableIconUrl = "https://cdn.ndopify.cm/icon-maskable-512.png",
            appleTouchIconUrl = "https://cdn.ndopify.cm/apple-touch-icon.png",
        )
        var tenant = FULL
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ManifestController())
        .setControllerAdvice(FakeTenantAdvice())
        .build()

    private fun manifest(): JsonNode =
        ObjectMapper().readTree(
            mvc.perform(get("/manifest.json")).andExpect(status().isOk).andReturn().response.contentAsString,
        )

    @BeforeEach
    fun reset() {
        tenant = FULL
    }

    @Test
    fun `the branding is the tenant's`() {
        val json = manifest()

        assertEquals("Ndopify Cameroun", json["name"].asText())
        assertEquals("Ndopify Cameroun", json["short_name"].asText())
        assertEquals("fr", json["lang"].asText())
        assertEquals("/", json["start_url"].asText())
        assertEquals("/", json["scope"].asText())
        assertEquals("standalone", json["display"].asText())
        assertEquals("#1d7edf", json["theme_color"].asText())
        assertEquals("#ffffff", json["background_color"].asText())
    }

    @Test
    fun `the tenant's variants are declared as-is`() {
        val icons = manifest()["icons"]

        assertEquals(3, icons.size())
        assertEquals("https://cdn.ndopify.cm/icon-192.png", icons[0]["src"].asText())
        assertEquals("192x192", icons[0]["sizes"].asText())
        assertEquals("any", icons[0]["purpose"].asText())
        assertEquals("https://cdn.ndopify.cm/icon-512.png", icons[1]["src"].asText())
        assertEquals("512x512", icons[1]["sizes"].asText())
        assertEquals("any", icons[1]["purpose"].asText())
        assertEquals("https://cdn.ndopify.cm/icon-maskable-512.png", icons[2]["src"].asText())
        assertEquals("512x512", icons[2]["sizes"].asText())
        assertEquals("maskable", icons[2]["purpose"].asText())
    }

    @Test
    fun `a partial variant set is never mixed with Ndopify's`() {
        tenant = Tenant(name = "A", iconUrl = "https://cdn/a.png", icon512Url = "https://cdn/512.png")
        val icons = manifest()["icons"]

        assertEquals(1, icons.size())
        assertEquals("https://cdn/512.png", icons[0]["src"].asText())
        assertTrue(icons.all { it["src"].asText().startsWith("https://cdn/") })
    }

    @Test
    fun `iconUrl alone keeps its intrinsic size`() {
        tenant = Tenant(name = "A", iconUrl = "https://cdn/a.png")
        val icons = manifest()["icons"]

        assertEquals(1, icons.size())
        assertEquals("https://cdn/a.png", icons[0]["src"].asText())
        assertNull(icons[0]["sizes"])
    }

    @Test
    fun `no icon uses Ndopify's set`() {
        tenant = Tenant(name = "A")
        val icons = manifest()["icons"]

        assertEquals(3, icons.size())
        assertEquals("/assets/images/icon-192.png", icons[0]["src"].asText())
        assertEquals("192x192", icons[0]["sizes"].asText())
        assertEquals("/assets/images/icon-512.png", icons[1]["src"].asText())
        assertEquals("512x512", icons[1]["sizes"].asText())
        assertEquals("/assets/images/icon-maskable-512.png", icons[2]["src"].asText())
        assertEquals("maskable", icons[2]["purpose"].asText())
    }

    @Test
    fun `a tenant without a name falls back to Ndopify`() {
        tenant = Tenant()
        assertEquals("Ndopify", manifest()["name"].asText())
    }

    @Test
    fun `the response is a manifest`() {
        mvc.perform(get("/manifest.json"))
            .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
    }
}
```

In `PwaAssetsTest`:

```kotlin
@Test
fun `Ndopify's icon set is shipped`() {
    listOf("icon-192.png", "icon-512.png", "icon-maskable-512.png", "apple-touch-icon.png").forEach {
        assertTrue(resourceBytes("public/assets/images/$it").size > 1_000, it)
    }
}
```

Create `PwaAssetsTest` in `com.wutsi.ndopify.web` with that case and the two helpers:

```kotlin
private fun resource(path: String): String =
    javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
        ?: fail("missing classpath resource $path")

private fun resourceBytes(path: String): ByteArray =
    javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
        ?: fail("missing classpath resource $path")
```

In `IndexTemplateTest`, add:

```kotlin
@Test
fun `the apple icon follows the tenant's icons`() {
    val withTouch = render("tenant" to tenant.copy(appleTouchIconUrl = "https://cdn.ndopify.cm/apple-touch-icon.png"))
    assertEquals(
        "https://cdn.ndopify.cm/apple-touch-icon.png",
        withTouch.selectFirst("link[rel=apple-touch-icon]")?.attr("href"),
    )
    assertEquals("Ndopify Cameroun", withTouch.selectFirst("meta[name=apple-mobile-web-app-title]")?.attr("content"))
    assertEquals("/manifest.json", withTouch.selectFirst("link[rel=manifest]")?.attr("href"))

    val iconOnly = render("tenant" to tenant.copy(appleTouchIconUrl = null))
    assertEquals("https://cdn.ndopify.cm/icon.png", iconOnly.selectFirst("link[rel=apple-touch-icon]")?.attr("href"))

    val none = render("tenant" to tenant.copy(name = "", iconUrl = null, appleTouchIconUrl = null))
    assertEquals("/assets/images/apple-touch-icon.png", none.selectFirst("link[rel=apple-touch-icon]")?.attr("href"))
    assertEquals("Ndopify", none.selectFirst("meta[name=apple-mobile-web-app-title]")?.attr("content"))
}
```

In `Error404TemplateTest` (no tenant), replace the apple-icon assertion and add the title:

```kotlin
assertEquals("/assets/images/apple-touch-icon.png", page.selectFirst("link[rel=apple-touch-icon]")?.attr("href"))
assertEquals("Ndopify", page.selectFirst("meta[name=apple-mobile-web-app-title]")?.attr("content"))
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest='ManifestControllerTest,IndexTemplateTest,Error404TemplateTest,PwaAssetsTest'`
Expected: FAIL — `ManifestController` does not exist, the icon files are missing, no `<link rel=manifest>`.

- [ ] **Step 3: Copy Ndopify's polished icon set**

```bash
cd modules/ndopify-web
cp ../ndopify-partner-central/src/main/resources/public/assets/images/icon-192.png \
   ../ndopify-partner-central/src/main/resources/public/assets/images/icon-512.png \
   ../ndopify-partner-central/src/main/resources/public/assets/images/icon-maskable-512.png \
   ../ndopify-partner-central/src/main/resources/public/assets/images/apple-touch-icon.png \
   src/main/resources/public/assets/images/
```

Do not copy the favicons: `head.html` keeps `/assets/images/icon.png` as the favicon.

- [ ] **Step 4: Add the controller**

`ManifestController` in `com.wutsi.ndopify.web.ui.pwa`, annotated `@RestController`:

- `@GetMapping("/manifest.json", produces = ["application/manifest+json"])`
- signature: `fun manifest(model: Model): WebManifest` — read the tenant with `model.getAttribute("tenant") as Tenant`,
  never as a `@ModelAttribute` parameter (module rule: request parameters could bind it).
- `@JsonInclude(JsonInclude.Include.NON_NULL)` on the `Icon` class so an omitted `sizes` is absent from the JSON.
- Values: `name` and `shortName` = `tenant.name.ifBlank { "Ndopify" }`; `lang` `fr`; `startUrl` and `scope` `/`;
  `display` `standalone`; `themeColor` `#1d7edf`; `backgroundColor` `#ffffff`.
- Icons, exactly one of the three branches:
  1. any of `icon192Url` / `icon512Url` / `maskableIconUrl` set → one `Icon` per set URL and **no** fallback:
     `192x192`/`any`, `512x512`/`any`, `512x512`/`maskable`; `type` `image/png`;
  2. else `iconUrl` set → one `Icon` with `src = iconUrl`, `sizes = null` (omitted), `purpose` `any`, `type`
     `image/png`;
  3. else → `/assets/images/icon-192.png` (192x192, any), `/assets/images/icon-512.png` (512x512, any),
     `/assets/images/icon-maskable-512.png` (512x512, maskable).
- Declare only URLs from branch 1 or 2, or files copied in Step 3: never a guessed size, never a file that may not
  exist.

No `public/manifest.json` file: it would install Ndopify's branding on every tenant.

- [ ] **Step 5: Add the head metadata**

In `templates/fragments/head.html`, replace the existing `apple-touch-icon` link and add the manifest and title tags
after the favicon links:

```html
<th:block th:with="tenantTouchIcon=${!brand ? null : (tenant?.appleTouchIconUrl ?: tenant?.iconUrl)}">
    <link rel="apple-touch-icon"
          th:href="${#strings.isEmpty(tenantTouchIcon)} ? @{/assets/images/apple-touch-icon.png} : ${tenantTouchIcon}">
</th:block>
<link rel="manifest" th:href="@{/manifest.json}">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title"
      th:content="${!brand or #strings.isEmpty(tenant?.name)} ? 'Ndopify' : ${tenant.name}">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
```

`#strings.isEmpty(null)` is true, so a tenant with no icon at all and the 404 (no tenant) both fall back to the copied
`apple-touch-icon.png`; `brand` stays the 404's guard.

- [ ] **Step 6: Make the three PWA paths public**

In `SecurityConfiguration.filterChain`, after the `/error` matcher (asserted as 200 in Task 8):

```kotlin
.requestMatchers("/sw.js", "/offline.html", "/manifest.json").permitAll()
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `mvn test -Dtest='ManifestControllerTest,IndexTemplateTest,Error404TemplateTest,PwaAssetsTest'`
Expected: PASS.

- [ ] **Step 8: Verify the manifest against a tenant**

With `mvn spring-boot:run` — the local tenant already carries the four S3 variants from Task 3's `local` seed:

```bash
curl -s -D - -o /dev/null http://localhost:8082/manifest.json | grep -i "content-type\|cache-control"
curl -s http://localhost:8082/manifest.json | python3 -m json.tool | head -40
```

Expected: HTTP 200, `application/manifest+json`, `no-cache, must-revalidate`, and the tenant's three declared icons
(192, 512, maskable) at the S3 URLs. Then clear the variants to check the fallbacks:

```sql
UPDATE T_TENANT SET icon_192_url = NULL, icon_512_url = NULL, maskable_icon_url = NULL,
    apple_touch_icon_url = NULL WHERE id = 1;
```

Reload: the manifest declares `iconUrl` alone, with no `sizes`. Null `icon_url` too: it declares Ndopify's three
bundled icons. Put the rows back with the `UPDATE` from Task 3 Step 7.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): brand the manifest with the tenant's pwa icons"
```

---

### Task 7: ndopify-web — the offline page

**Files:**
- Create: `modules/ndopify-web/src/main/resources/public/offline.html`
- Test: `src/test/kotlin/com/wutsi/ndopify/web/PwaAssetsTest.kt` (new case; created in Task 6)

**Interfaces:**
- Consumes: `/assets/images/logo.png`, `/assets/images/icon.png`; the approved prototype
  `modules/ndopify-web/src/main/html/offline.html`.
- Produces: `/offline.html`, served by the worker in Task 8.

- [ ] **Step 1: Write the failing test**

In `PwaAssetsTest`:

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

### Task 8: ndopify-web — service worker, registration and scoped cache purge

**Files:**
- Create: `modules/ndopify-web/src/main/assets/sw.js`
- Modify: `modules/ndopify-web/build-assets.mjs`
- Modify: `src/main/assets/js/main.js` (registration, before the closing `})();`)
- Test: `PwaAssetsTest`, `BundledAssetsTest` (new cases)

**Interfaces:**
- Consumes: `/offline.html` (Task 7), `/assets/images/logo.png`, the bundle (Task 4).
- Produces: `/sw.js` (generated), with `VERSION` = first 12 hex chars of `sha256(offline.html bytes + logo bytes)`; precache `["/offline.html", "/assets/images/logo.png"]`; caches prefixed `nweb-offline-`.

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

@Test
fun serviceWorkerPurgesOnlyItsOwnCaches() {
    val sw = resource("public/sw.js")
    assertTrue(sw.contains("const PREFIX = \"nweb-offline-\";"))
    assertTrue(sw.contains("k.startsWith(PREFIX)"))
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
const PREFIX = "nweb-offline-";
const CACHE = PREFIX + VERSION;
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
            // Only this app's own caches: another app on the same origin keeps its caches.
            .then((keys) => Promise.all(
                keys.filter((k) => k.startsWith(PREFIX) && k !== CACHE).map((k) => caches.delete(k))
            ))
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
itself. In Application → Cache Storage, a `nweb-offline-<version>` cache; create a dummy cache and activate a new
version → the dummy survives, the old `nweb-offline-*` is gone.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): add the offline service worker"
```

---

### Task 9: Update the module documentation

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
- A new short paragraph on the PWA: `/manifest.json` is a controller rendering the tenant's name and its icon variants
  (`icon192Url`, `icon512Url`, `maskableIconUrl`), or `iconUrl` alone, or Ndopify's `icon-192/512/maskable-512` —
  never mixed; the tenant's `appleTouchIconUrl` (or `iconUrl`) feeds `apple-touch-icon`; `sw.js` handles navigations
  and the two precached URLs only and purges only `nweb-offline-*`; `/offline.html` is self-contained; the rule that
  **HTML and API responses are never cached**.
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

### Task 10: Visual pass, install verification and the release order

**Files:**
- Modify (only if the pass finds a regression): `src/main/assets/css/*.css`

**Interfaces:**
- Consumes: everything above.
- Produces: proof that the merge changed no layout, that each tenant installs with its own icons, and that the three modules deploy in order.

- [ ] **Step 1: Run every page locally**

With a local ndopify-server and its seed data (module README), `mvn spring-boot:run`, then open at 375 px and 1280 px:
`/`, `/owner`, `/agent`, a published listing page, `/match/{id}?token=…`, `/visits/{id}?token=…`, an expired match
link (`/match/{id}` with no cookie), the matching visit link, and an unknown address (404).

Expected: each page looks as before. Pay attention to the listing page (its own CSS was page-scoped), the match page
(`proposal.css`) and the visit page (`visit.css`), which now cascade across the site.

- [ ] **Step 2: Fix any global-cascade regression at the source**

If a page-scoped rule leaks (a selector from `listing.css` / `proposal.css` / `visit.css` matching another page),
scope it to the page that owns it (under its `body[data-page]` or page class) instead of adding an undo. Re-run Step 1.

- [ ] **Step 3: Check every manifest branch**

```bash
curl -s http://localhost:8082/manifest.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print([(i["src"], i.get("sizes"), i["purpose"]) for i in d["icons"]])'
```

The local tenant starts with the four S3 variants (Task 3's seed), so the first run is branch 1. Then, to reach the
other branches, run `UPDATE T_TENANT SET icon_192_url = NULL, icon_512_url = NULL, maskable_icon_url = NULL,
apple_touch_icon_url = NULL WHERE id = 1;` and repeat (branch 2, `iconUrl` alone with no `sizes`), then also null
`icon_url` and repeat (branch 3, Ndopify's three files). Expected: the tenant's three variants / `iconUrl` alone /
Ndopify's three files. Restore the seed's `UPDATE` afterwards. Every `src` must answer 200:

```bash
curl -s http://localhost:8082/manifest.json | python3 -c 'import json,sys; [print(i["src"]) for i in json.load(sys.stdin)["icons"]]' \
  | while read -r src; do case "$src" in http*) u="$src";; *) u="http://localhost:8082$src";; esac; curl -s -o /dev/null -w "%{http_code} $src\n" "$u"; done
```

- [ ] **Step 4: Check the offline behaviour and the cache purge**

DevTools → Network → Offline: navigate to `/`, `/match/{id}`, `/visits/{id}` → the offline page each time. Turn the
network back on → the page reloads once by itself. Reload `/offline.html` with JS disabled → the retry link still
navigates to `/`. Create a cache that is not `nweb-offline-*` and activate a new worker version → it survives.

- [ ] **Step 5: Verify a deploy reaches an installed app**

Note the bundle URL in the rendered HTML, change a CSS rule and rebuild, reload → the page carries a new `-<hash>` and
the new styles are visible; unchanged files keep their hash.

- [ ] **Step 6: Verify the icons on an iPhone and an Android phone**

- Android: browser menu → Install; the install sheet shows the tenant's 512 icon (or Ndopify's), and the app opens
  standalone from `start_url` `/`. With the local variants cleared (Step 3), it shows Ndopify's icon.
- iPhone: Safari → Share → *Sur l'écran d'accueil*; the home-screen icon is the tenant's `appleTouchIconUrl` (or its
  `iconUrl`), and the label is the tenant's name.

- [ ] **Step 7: Verify the release order**

```bash
git fetch --tags && git tag --list ndopify-dto-0.0.101     # released
grep -n "ndopify-dto.version" pom.xml                      # 0.0.101
cd modules/ndopify-web && mvn -q -s ../../settings.xml -DskipTests package   # resolves the released dto
```

Expected: the tag, `0.0.101`, and BUILD SUCCESS. If the tag is missing, stop: the server and web changes must not be
deployed ahead of the DTO.

- [ ] **Step 8: Commit any fix and report**

```bash
git add modules/ndopify-web/src/main/assets/css
git commit -m "fix(web): keep page-scoped rules scoped after the css merge"
```

If nothing changed, say so — no empty commit.
