# ndopify-partner-central

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-partner-central-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-partner-central-jacoco.svg)

Partner Central — the web app where Ndopify agents (démarcheurs) sign up, sign in and verify their identity (KYC). Server-side
rendered with Kotlin + Spring Boot + Thymeleaf, UI in French.

It has no database: every read/write goes to [ndopify-server](../ndopify-server) over REST, using the shared
contracts from [ndopify-dto](../ndopify-dto).

## Features

- **Sign-up wizard, *Rejoindre Ndopify*** (`ui/join/JoinController`) — public, linked from the login page. One page,
  `templates/join.html`, built like the sign-in: five steps, `identity` → `experience` → `area` →
  `payment` → `review`, then a confirmation.
    - `GET /join?step=…` shows a step; `POST /join/{step}` validates it (`JoinRules`, French messages)
      and stores its answers in a `PendingJoin` in the HTTP session, then redirects to the next step — or back to
      the review with `returnTo=review`. An invalid step is shown again with its errors, and the session is left
      untouched.
    - A step only opens once every step before it is complete, whatever the URL says. The sector step also checks,
      through `NdLocationClient`, that the city is one of the tenant's cities and that the neighbourhoods (1 to 3)
      belong to it.
    - `POST /join/review` creates the agent on ndopify-server from the session's answers, never from the page,
      then shows the confirmation (`?step=confirmation`, while a `JoinedAgent` is in the session). An e-mail that
      already has an agent account (409 `AGENT_ALREADY_EXISTS`) is reported on the review, with a link to sign in.
    - Every step posts without JavaScript. `public/assets/js/join.js` enhances the fields: a slim-select city
      type-ahead and the city's neighbourhoods (over `/locations`), intl-tel-input for phone numbers (posted as
      E.164 through a hidden input), and "Utiliser mon numéro WhatsApp". Without it, the sector step only offers
      the city and neighbourhoods already chosen.
- **Location search for dropdowns** (`ui/location/LocationController`) — `GET /locations`, a public JSON proxy to
  ndopify-server's location search, used by the wizard's city and neighbourhood dropdowns. Because it's public, it
  only searches cities and neighbourhoods, always in the tenant's country, with a capped page size; responses are
  cacheable for an hour.
- **E-mail OTP sign-in** (`ui/login/LoginController`) — one page, `templates/login.html`, with two steps:
    1. `GET /login` asks for the e-mail; `POST /login` has ndopify-server e-mail a 6-digit code (5-minute TTL) and
       keeps a `PendingLogin` (e-mail + expiry) in the HTTP session. `POST /login` with `resend=true` sends a new
       code to the pending address.
    2. `GET /login?step=code` asks for the code; `POST /login/verify` exchanges it for an access token
       (`AuthType.OTP`, application `partner-central`), clears the pending sign-in and redirects to `/kyc`.

  The code step is only shown while a sign-in is pending in the session; otherwise `/login?step=code` shows the
  e-mail step. The e-mail is read from the session at verify time, never from the page. `/login/connection` (the
  former code page) redirects to `/login?step=code`.
- **Sign-out** — `GET /logout` drops the access-token cookie and the HTTP session. ndopify-server has no revocation
  endpoint, so the token stays valid until it expires.
- **KYC wizard** — Thymeleaf templates under `templates/kyc/` (`index`, `verification`, `revue`,
  `confirmation`). No controller serves them yet.

## How it works

- **Authentication** — the access token is stored in the `__npc_access_token` HTTP-only cookie
  (`AccessTokenService`). `JwtAuthenticationFilter` decodes it on each request; everything except `/assets/**`,
  `/login/**`, `/error`, `/join/**` and `/locations/**` requires authentication, and unauthenticated requests
  are redirected to `/login`.
  Note: tokens are decoded with `Algorithm.none()` — the same unsigned-JWT gap as ndopify-server.
- **Tenant resolution** — `TenantContext` loads the active tenants from ndopify-server and picks
  the one whose `partnerCentralUrl` is a prefix of the incoming request URL (`getTenant()` / `getTenantId()`).
- **REST clients** (`client/ndopify/`: `NdAgentClient`, `NdAuthClient`, `NdLocationClient`, `NdOtpClient`,
  `NdTenantClient`) — every `RestClient` built by
  Spring gets three interceptors (`config/RestConfiguration`): request logging, `Authorization` (from the cookie)
  and `X-Tenant-ID` (from `TenantContext`). `NdTenantClient` opts out, since tenants are global reference data.
  For the endpoints these clients call, see ndopify-server's Swagger UI
  ([API Documentation](../ndopify-server/README.md#api-documentation)).

## Directory structure

```
src/main/kotlin/com/wutsi/ndopify/npc/
    client/      REST clients for ndopify-server + request interceptors
    config/      Spring configuration (security, REST, clock)
    service/     TenantContext, security/ (access-token cookie, JWT decoding, auth filter)
    ui/          MVC controllers: join (sign-up wizard), location (JSON proxy), login, logout
src/main/resources/
    templates/   Thymeleaf pages served by the app
    public/      Static assets (CSS, JS, images) served under /assets
src/main/html/   The prototype: static HTML pages + assets generated by /init-partner-central, which the templates
                 are derived from. Not served — and kept as is: never delete or "clean up" anything in it
ux/journeys/     User-journey maps produced by Stage 1 of /init-partner-central
```

## Environment Setup

- JDK 17, Maven 3.9+
- A running [ndopify-server](../ndopify-server) — by default on `http://localhost:8080`
  (`ndopify.server.api.base-url` in `application.yml`)
- A tenant whose `partnerCentralUrl` matches the URL you browse to (e.g. `http://localhost:8081`), otherwise tenant
  resolution fails
- To receive OTP codes locally, an SMTP catcher (e.g. Mailpit) for ndopify-server — see its README

## Running

```bash
mvn spring-boot:run          # starts on port 8081
```

In deployment, the `Procfile` runs the jar with `--spring.profiles.active=$APP_PROFILE`.

## Build & Test

```bash
mvn package
mvn test
mvn test -Dtest=ClassName#methodName
```

Tests are unit/MockMvc-level and don't need ndopify-server or a database. `JoinTemplateTest` renders
`templates/join.html` through `JoinController` with a real Thymeleaf engine (checked with jsoup), so template
mistakes fail the build; the other controller tests only check view names and models.

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
mvn verify                            # JaCoCo coverage gate (build-breaking)
```

This module overrides the parent POM's class-coverage threshold (`jacoco.threshold.class` in this module's
`pom.xml`); line coverage uses the parent default.
