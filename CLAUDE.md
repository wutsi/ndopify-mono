# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

`ndopify-mono` is an early-stage monorepo. The group/package is `com.wutsi.ndopify`. Six modules currently exist:

- `modules/ndopify-dto` — shared DTOs/contracts (no business logic)
- `modules/ndopify-server` — the REST API backend (Spring Boot)
- `modules/ndopify-partner-central` — agent-facing web app (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.npc`. No database; talks to `ndopify-server` over REST.
- `modules/ndopify-esignature` — signer-facing contract sign page (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.esignature`. No database; talks to `ndopify-server` over REST.
- `modules/ndopify-web` — public marketing site (Spring Boot + Thymeleaf, French UI), package
  `com.wutsi.ndopify.web`. No database; only reads the tenants from `ndopify-server`, and links to Partner Central's
  public wizards.
- `modules/ndopify-web-core` — library of the infrastructure shared by the web apps (`TenantContext`,
  `TenantFormatter`, REST interceptors, `NdTenantClient`, the access-token and JWT classes), package
  `com.wutsi.ndopify.web.core`. Used by `ndopify-esignature`; Partner Central and the website still have their own
  copies.

All six are in the root `pom.xml` reactor. The Spring Boot apps are deployed to Heroku by their own GitHub
workflows; `ndopify-dto` is released to GitHub Packages. There is no SDK, admin console or tracking-server module
yet (the tenant has an `adminConsoleUrl`, and `admin-console` tokens exist, but no code here). Do not assume they
exist or scaffold code that depends on them.

Each module has its own `CLAUDE.md`, loaded when working on its files: code organization, testing patterns, and
the module's rules. `modules/ndopify-server/CLAUDE.md` (domains, platform, multi-tenancy, tests, migrations, JPA
utilities), `modules/ndopify-partner-central/CLAUDE.md`, `modules/ndopify-esignature/CLAUDE.md`,
`modules/ndopify-web/CLAUDE.md` and `modules/ndopify-dto/CLAUDE.md` (DTO conventions and the release flow). Keep
module-specific guidance there, and in this file only what applies to several modules.

## Repository Layout

- `modules/<module>/` — the six modules above.
- `pom.xml` — parent POM and reactor; `settings.xml` — the Maven settings CI uses (`-s settings.xml`) to reach
  GitHub Packages, where `ndopify-dto` is published.
- `.github/workflows/` — per module, `<module>-pr.yml` (build + tests on a pull request) and `<module>-main.yml`
  (build, coverage badge, deploy on `main`), triggered by changes under the module's path; `_pr.yml` / `_main.yml`
  build everything when the root POM or settings change. `.github/badges/` holds the generated JaCoCo badges.
- `docs/superpowers/plans/YYYY-MM-DD-<topic>.md` — implementation plans, kept after the work as the design record
  (ex: `2026-10-14-mandate-dispatch.md`); `docs/reviews/YYYY-MM-DD-code-review.md` — code reviews.
- `.claude/commands/` — `/init-partner-central`, `/init-esignature`, `/init-website`, `/init-website-design`:
  generate a web app's HTML prototype (`src/main/html/`) with the skills in `.claude/skills/`.

## Build & Test Commands

### Full Build

```bash
# Build entire monorepo
mvn install -Dheadless=true

# Build a single module
cd modules/<module-name>
mvn package
```

### Testing

```bash
# Run all tests for a module
cd modules/ndopify-server
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

`ndopify-server` endpoint tests use `TestRestTemplate` against a real MySQL database (Flyway-migrated), so a local
MySQL instance must be running (see Environment Setup below). `ndopify-partner-central`, `ndopify-esignature` and
`ndopify-web` tests are plain unit tests / standalone `MockMvc` and need neither MySQL nor a running server.
`ndopify-dto` has no tests.

### Code Quality

```bash
# ktlint runs automatically during the validate phase
mvn validate

# Auto-format code with ktlint
mvn antrun:run@ktlint-format

# Check test coverage (JaCoCo), enforced as a build-breaking gate
mvn verify
```

Coverage thresholds are set per-module and can override the parent POM's defaults — see `jacoco.threshold.line` /
`jacoco.threshold.class` in the root `pom.xml` and any per-module override (e.g. `modules/ndopify-server/pom.xml`)
for the current values; they change over time so are not duplicated here.

### Running the Server

```bash
cd modules/ndopify-server
mvn spring-boot:run          # starts on port 8080, Swagger UI at /api.html

cd modules/ndopify-partner-central
mvn spring-boot:run          # starts on port 8081, calls ndopify-server at ndopify.server.api.base-url

cd modules/ndopify-web
mvn spring-boot:run          # starts on port 8082, reads the tenants from ndopify-server

cd modules/ndopify-esignature
mvn spring-boot:run          # starts on port 8083, calls ndopify-server like partner-central
```

The web apps find their tenant by matching the request URL against the tenant's `partnerCentralUrl` /
`publicPortalUrl` / `eSignaturePortalUrl` (`T_TENANT`), so locally these must be set to the `http://localhost:<port>`
URLs, as the local seed data (`db/migration/local/`) does.

First-time local setup requires importing reference data (see `LocationEndpoints` / `ApplicationEndpoints`):

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

## Environment Setup

Required for local development (there is no `CONTRIBUTING.md` in this repo — this is derived from `application.yml`
and the module POMs):

- JDK 17, Maven 3.9+
- MySQL 8+, with the `root` user having **no password** (matches the default `application.yml` datasource)

- An SMTP server on `localhost:1025` (e.g. Mailpit) to receive e-mails sent by `ndopify-server` (agent welcome,
  OTP codes) — see `spring.mail` in `application.yml`.
- No JWT secret to set: `application.yml` has a dummy `ndopify.security.jwt.secret` (deployed servers use
  `JWT_SECRET`, see *Token signing* in `modules/ndopify-server/CLAUDE.md`).
- `KIMI_API_KEY` and `DEEPSEEK_API_KEY` for the LLMs of `ndopify-server` (Spring AI): the vision model, Kimi
  (`spring.ai.openai`, OpenAI-compatible), reads the KYC documents and listing pictures; the text model, DeepSeek
  (`spring.ai.deepseek`), is for text-only prompts. See `AIConfiguration` / `ChatClients`.

No RabbitMQ or messaging broker is wired up in `ndopify-server` today — don't assume one exists. MTN Mobile Money credentials (`ndopify.mobile-money.mtn.*` in `application.yml`) default to
sandbox values and don't need to be set for local dev unless you're exercising real MTN calls.

## Architecture Overview

### Module Dependency

```
ndopify-dto  →  ndopify-server
ndopify-dto  →  ndopify-partner-central  ──(REST, runtime)──▶  ndopify-server
ndopify-dto  →  ndopify-web-core
ndopify-dto, ndopify-web-core  →  ndopify-esignature  ──(REST, runtime)──▶  ndopify-server
ndopify-dto  →  ndopify-web              ──(REST, runtime)──▶  ndopify-server
```

DTOs live in `ndopify-dto` and are the request/response contracts shared by the server and its clients. `ndopify-dto`
must never depend on another module of the repo, and the web apps (`ndopify-partner-central`, `ndopify-esignature`,
`ndopify-web`) must not depend on `ndopify-server` nor on each other at build time — only call the server over HTTP.
They may depend on `ndopify-web-core` (a released version, like `ndopify-dto`: `ndopify-web-core.version` in the root
`pom.xml`).
A web app's infrastructure copied from another (`TenantContext`, `TenantFormatter`, the REST interceptors, the JWT
classes) keeps the same class names and signatures, so that a fix in one can be carried to the others.

### Cross-module rules

The detail is in each module's `CLAUDE.md`; these hold across modules:

- **Tenancy**: ndopify-server enforces it with Hibernate multi-tenancy (`@TenantId` entities, `TenantContext`), never
  hand-written predicates. The tenant comes from the JWT; the `X-Tenant-ID` header only counts on a public endpoint
  without a token. The web apps resolve the tenant from the request URL (`partnerCentralUrl` / `eSignaturePortalUrl`
  / `publicPortalUrl`); Partner Central and the e-signature portal send it as `X-Tenant-ID`.
- **Token trust**: only ndopify-server has the JWT secret and checks signatures. Partner Central's and the e-signature
  portal's `JwtDecoder` read the claims **without checking the signature**: a web app never acts on a claim alone,
  only uses it to pick what to ask ndopify-server, which checks the token on each call (a 401 from it clears the
  web app's cookie).
- **Contract signing**: only the contract party itself signs, from the e-signature portal (`e-signature` JWT whose
  party is the contract party's). No admin bypass, nobody signs on another's behalf — never add one.
- **Formatting**: dates and amounts shown to users (pages, e-mails, contracts) go through the module's
  `TenantFormatter` (tenant's formats, locale, time zone, currency symbol), never a hard-coded pattern or "FCFA".
- **Prototypes**: `src/main/html/` in partner-central, esignature and web is the prototype: never serve it, and
  never delete or "clean up" anything in it. Every page works without JavaScript; scripts only enhance.
- **DTO changes**: a change to `ndopify-dto` needs a DTO release before the modules using it can be deployed (see
  *Versioning and Release* in `modules/ndopify-dto/CLAUDE.md`).

## Testing Conventions

**Assertions:** use `kotlin.test` (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`, etc.), never
`org.junit.jupiter.api.Assertions.*` or `org.junit.jupiter.api.assertThrows`. `@Test` from either JUnit or
`kotlin.test` is fine — this convention is only about the assertion calls. Not currently enforced by a build gate
(ktlint doesn't check semantics); enforce via code review.

**Mocks:** `mockito-kotlin` (`com.nhaarman.mockitokotlin2`: `mock`, `doReturn(...).whenever(...)`, `argumentCaptor`,
`verify`) in every module. Tests mirror the main package (`<package>/<Class>Test.kt`). Each module's `CLAUDE.md` has
its own patterns: endpoint integration tests against MySQL in `ndopify-server`, `MockMvc` controller tests and
real-Thymeleaf template tests in the web apps.

## Code Style

- Kotlin, formatted by ktlint (`mvn validate` fails on a violation; `mvn antrun:run@ktlint-format` fixes most).
- KDoc on a class or public method says what it's for and the non-obvious rules (error codes in the order they're
  checked, why something is or isn't checked); no comment that restates the code.
- UI text, e-mails and contracts are in French; code, comments, logs and docs in English.

## Key Configuration Files

- `pom.xml` (root) — parent POM: shared dependencies, JaCoCo/ktlint plugin config, coverage thresholds
- `modules/*/pom.xml` — module-specific dependencies and overrides (e.g. `ndopify-server`'s coverage thresholds)
- `modules/ndopify-server/src/main/resources/application.yml` — default config (MySQL, Flyway locations, Jackson,
  mail, Spring AI, storage, SpringDoc/Swagger groups)
- `modules/ndopify-partner-central/src/main/resources/application.yml` — port 8081, `ndopify.server.api.base-url`
  (the same in `ndopify-web`, port 8082, and `ndopify-esignature`, port 8083)
- `application-test.yml` / `application-prod.yml` — profile overrides (`test` is the deployed test environment, set
  by the `*-main.yml` workflows; `qa` is ndopify-server's profile for its own tests)

## README.md Files

Every module README (`modules/<module>/README.md`) has CI-managed badges as the first lines, right after the
`# <module-name>` heading:

- `main` and `pull_request` GitHub Actions workflow-status badges (all modules), pointing at
  `.github/workflows/<module>-main.yml` / `<module>-pr.yml`.
- A JaCoCo coverage badge (`ndopify-server`, `ndopify-partner-central`, `ndopify-esignature` and `ndopify-web`),
  pointing at the auto-generated SVG in `.github/badges/<module>-jacoco.svg`.

These badges must be preserved whenever a README is edited. Edit module READMEs with a targeted edit (not a
full-file rewrite) so the badge block isn't clobbered — and if a full rewrite is unavoidable, read the existing
file first and carry the badge lines over verbatim.
