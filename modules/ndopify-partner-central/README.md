# ndopify-partner-central

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-partner-central-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-partner-central-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-partner-central-jacoco.svg)

Partner Central — the web app where Ndopify agents (démarcheurs) sign up, sign in, verify their identity (KYC), and
handle the mandates of people looking for a rental: accept the ones offered to them, follow them up to the contract's
signature, then close them. It also hosts the public *Trouver un bien* wizard, where those people submit their search.
Server-side rendered with Kotlin + Spring Boot + Thymeleaf, UI in French.

It has no database: every read/write goes to [ndopify-server](../ndopify-server) over REST, using the shared
contracts from [ndopify-dto](../ndopify-dto).

## Features

Public pages:

- **Sign-up wizard, *Rejoindre Ndopify*** (`ui/join/JoinController`) — linked from the login page. One page,
  `templates/join.html`, five steps, `identity` → `experience` → `area` → `payment` → `review`, then a confirmation.
    - `GET /join?step=…` shows a step; `POST /join/{step}` validates it (`JoinRules`, French messages) into a
      `PendingJoin` in the HTTP session, then redirects to the next step — or back to the review with
      `returnTo=review`. An invalid step is shown again with its errors.
    - A step only opens once every step before it is complete, whatever the URL says. The area step checks that the
      city is one of the tenant's and that the neighbourhoods (1 to 3) belong to it (`SectorValidator`).
    - `POST /join/review` creates the agent (`POST /v1/agents`) from the session's answers, never from the page. An
      e-mail that already has an agent account (409 `AGENT_ALREADY_EXISTS`) is reported on the review.
- **Property search wizard, *Trouver un bien*** (`ui/findproperty/FindPropertyController`) — for people looking for
  a rental. One page, `templates/find-property.html`, built like *Rejoindre Ndopify*: seven steps, `need` → `area` →
  `property` → `amenities` → `budget` → `contact` → `review`, then a confirmation. The review creates a TENANT
  rental mandate (`POST /v1/mandates`), which ndopify-server then offers to an agent. These rules
  (`FindPropertyRules`) are the only validation a mandate gets, since ndopify-server doesn't validate mandates yet.
- **Location search for dropdowns** (`ui/location/LocationController`) — `GET /locations`, a JSON proxy to
  ndopify-server's location search for the wizards' city and neighbourhood dropdowns: only cities and
  neighbourhoods, always in the tenant's country, with a capped page size.
- **E-mail OTP sign-in** (`ui/login/LoginController`) — one page, `templates/login.html`: `POST /login` has
  ndopify-server e-mail a 6-digit code (5-minute TTL) and keeps a `PendingLogin` in the session (`resend=true` sends
  a new one); `POST /login/verify` exchanges the code for an access token (`AuthType.OTP`, application
  `partner-central`), reading the e-mail from the session, never from the page, and redirects to `/`.
- **Sign-out** — `GET /logout` drops the access-token cookie and the HTTP session. ndopify-server has no revocation
  endpoint, so the token stays valid until it expires.

Signed-in pages:

- ***Espace agent*** (`ui/home/HomeController`, `templates/index.html`) — the home page, whatever the KYC status: a
  banner linking to `/kyc` for an agent who isn't verified yet, the agent's profile summary and, once verified:
    - ***Mandats proposés*** — the mandates offered to the agent and their deadline. `POST /assignments/{id}/accept`
      or `/decline` (`ui/assignment/AssignmentController`); no customer data is shown before acceptance.
    - ***Mes mandats*** — the agent's mandates in progress (assigned, waiting for the customer's signature, approved),
      each with its next step.
- **KYC wizard** (`ui/kyc/KycController`, `templates/kyc.html`) — `front` → `back` → `selfie` → `review`: each step
  uploads its photo straight to the agent's pending KYC case (sniffed with Tika, 5 Mo max), and the review submits
  it. Afterwards the page shows the case's status; a rejected case can be restarted.
- **Profile** (`ui/profile/ProfileController`) — `GET|POST /profile` edits the agent's profile (not the name) and
  `GET|POST /profile/photo` its photo.
- **Mandate page** (`ui/mandate/MandateController`, `templates/mandate.html`) — `GET /mandates/{id}`: the mandate's
  steps, its customer's contact, its criteria and the actions of its status, all post-redirect-get:
  `POST /mandates/{id}/request-approval` (generates the contract and e-mails the customer a link to sign it on
  [ndopify-esignature](../ndopify-esignature)), `/close` and `/cancel`. `GET|POST /mandates/{id}/edit` edits an
  assigned or pending-approval mandate, with optimistic locking (the version it was loaded with).
  Another agent's mandate is shown as locked.

Every form posts without JavaScript; the scripts in `public/assets/js/` (`join.js`, `find-property.js`, `kyc.js`,
`home.js`, `mandate.js`) only enhance (dropdowns, intl-tel-input, photo resizing, in-page camera, countdowns, in-place
confirmations).

## How it works

- **Authentication** — the access token is stored in the `__npc_access_token` HTTP-only cookie
  (`AccessTokenService`). `JwtAuthenticationFilter` decodes it on each request and clears it once it no longer
  decodes (expired); everything except `/assets/**`, `/login/**`, `/error`, `/join/**`, `/find-property/**` and
  `/locations/**` requires authentication, and unauthenticated requests are redirected to `/login`.
  Note: tokens are decoded with `Algorithm.none()` — the same unsigned-JWT gap as ndopify-server.
- **The signed-in agent** — agent and party ids always come from the token (`CurrentParty`, `CurrentAgent`), never
  from a form. ndopify-server checks that an agent only reads and changes its own mandates and offers.
- **Tenant resolution** — `TenantContext` loads the active tenants from ndopify-server and picks the one whose
  `partnerCentralUrl` is a prefix of the incoming request URL. Dates and amounts go through `TenantFormatter` (the
  tenant's formats, currency, locale and time zone).
- **REST clients** (`client/ndopify/`: `NdAgentClient`, `NdAssignmentClient`, `NdAuthClient`, `NdContractClient`,
  `NdCustomerClient`, `NdKycClient`, `NdLocationClient`, `NdMandateClient`, `NdOtpClient`, `NdPartyClient`,
  `NdTenantClient`, `NdUserClient`) — every `RestClient` built by Spring gets three interceptors
  (`config/RestConfiguration`): request logging, `Authorization` (from the cookie) and `X-Tenant-ID` (from
  `TenantContext`). `NdTenantClient` opts out, since tenants are global reference data. For the endpoints these
  clients call, see ndopify-server's Swagger UI ([API Documentation](../ndopify-server/README.md#api-documentation)).
- **Errors** — a 403 / 404 from ndopify-server that no page catches is answered with the same status
  (`ui/layout/HttpClientErrorHandler`), rendered by `templates/error/403.html` / `404.html`.

## Directory structure

```
src/main/kotlin/com/wutsi/ndopify/npc/
    client/      REST clients for ndopify-server + request interceptors
    config/      Spring configuration (security, REST, clock)
    service/     TenantContext, TenantFormatter, security/ (access-token cookie, JWT decoding, auth filter)
    ui/          MVC controllers: home, assignment, mandate, kyc, profile, join, findproperty, location, login,
                 logout, layout (advices, error handler)
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

Tests are unit/MockMvc-level and don't need ndopify-server or a database. The `*TemplateTest`s (ex: `JoinTemplateTest`)
render the templates through their controller with a real Thymeleaf engine (checked with jsoup), so template mistakes
fail the build.

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
mvn verify                            # JaCoCo coverage gate (build-breaking)
```

Coverage thresholds are the parent POM's defaults (`jacoco.threshold.line` / `jacoco.threshold.class` in the root
`pom.xml`).
