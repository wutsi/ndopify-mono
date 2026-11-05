# Ndopify Website

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-jacoco.svg)

The public Ndopify site — the marketing pages, a published listing's public page, and the match-proposal and visit
pages a customer opens from their e-mail. Server-side rendered with Kotlin + Spring Boot + Thymeleaf, UI in French.

It has no database: every read/write goes to [ndopify-server](../ndopify-server) over REST, using the shared
contracts from [ndopify-dto](../ndopify-dto) and the infrastructure from
[ndopify-web-core](../ndopify-web-core).

## Features

- **Home page** (`ui/home/HomeController`, `templates/index.html`, `/`) — for people looking for a rental; its calls
  to action open Partner Central's *Trouver un bien* wizard (`{partnerCentralUrl}/find-property`).
- **Owner page** (`ui/owner/OwnerController`, `templates/owner.html`, `/owner`) — for property owners; the calls to
  action open Partner Central's *Proposer un bien* wizard (`{partnerCentralUrl}/offer-property`).
- **Agent page** (`ui/agent/AgentController`, `templates/agent.html`, `/agent`) — for mobile-money agents; the calls to
  action open Partner Central's *Rejoindre Ndopify* wizard (`{partnerCentralUrl}/join`).
- **Listing page** (`ui/listing/ListingController`, `templates/listing.html`) — a PUBLISHED listing at its
  `Listing.url`, `/<city>/<neighborhood>/listing/<id>/<slug>`: photos, key facts, description, amenities, price,
  terms, map data and structured data. Any other path to the same id is redirected (301) to the canonical URL. Only
  public fields are shown — never the listing's `agentRemarks`. The listing page posts no lead: its "Demander une
  visite" and "Poser une question" calls to action are still the prototype's popup and drawer, not submitted.
- **Match proposal** (`ui/match/MatchController`, `templates/match.html`, `/match/{id}?token=…`) — the link e-mailed
  with a proposed listing. The `public-portal` token is kept in the `__nweb_access_token` cookie and stripped from the
  address (302), then signs the customer in. The page shows the listing, its mandate (the search), the agent, why the
  property matches and one answer block: "Ça m'intéresse" (`POST /match/{id}/accept`) or "Pas pour moi"
  (`POST /match/{id}/reject`, with an optional reason). Answers are final. Once accepted, the customer can request a
  visit: "Demander une visite" (on-site or WhatsApp video, one of the next 7 days, a time, an optional note) posts
  `POST /match/{id}/visit`; the page then shows the open visit's slot and "Voir ma visite" (`/visits/{id}`). Expired
  proposals, unavailable listings, a visitor who isn't the customer and an invalid link each have their own view.
- **Visit page** (`ui/visit/VisitController`, `templates/visit.html`, `/visits/{id}?token=…`) — the link e-mailed with
  a visit. The token works as on the match page. The page shows the listing, the agent, the customer's contact and one
  block per status: requested, confirmed, in progress, completed, cancelled, expired or no-show, with the visit code
  and — for an on-site visit — the exact address once confirmed. A requested or confirmed visit can be cancelled
  before the check-in (`POST /visits/{id}/cancel`, with an optional reason); there is no feedback form and no check-in
  UI. An expired or another customer's link has its own view (`visit-link.html`).
- **Errors** — a 403 / 404 from ndopify-server is answered with the same status
  (`ui/layout/HttpClientErrorHandler`), rendered by `templates/error/404.html`; a 401 clears the cookie and reloads
  the page.

## How it works

- **Shared infrastructure** — `TenantContext`, `TenantFormatter`, `NdTenantClient`, `AccessTokenService`,
  `JwtDecoder`, `JwtAuthenticationFilter` and the REST interceptors come from
  [`ndopify-web-core`](../ndopify-web-core), declared by `config/WebCoreConfiguration`.
- **Tenant resolution** — `TenantContext` loads the active tenants from ndopify-server and picks the one whose
  `publicPortalUrl` prefixes the incoming request URL. Dates and amounts go through `TenantFormatter` (the tenant's
  formats, currency, locale and time zone).
- **Authentication** — the only token accepted is the `public-portal` token of a match or visit link, kept in the
  HTTP-only `__nweb_access_token` cookie and sent to ndopify-server as the bearer token while it is present. Every
  other token is signed out. A 401 from ndopify-server clears the cookie.
- **REST clients** (`client/ndopify/`: `NdAgentClient`, `NdCustomerClient`, `NdListingClient`, `NdLocationClient`,
  `NdMandateClient`, `NdMatchClient`, `NdVisitClient`) — Spring-built `RestClient`s with the logging and `X-Tenant-ID`
  interceptors (and the `Authorization` one from web-core), except `NdTenantClient`, which is web-core's plain
  `RestTemplate` since tenants are global.
- **Links** — `ui/layout/PartnerCentralUrls.of(tenant, path)` builds the calls to action, or returns null (the link is
  left out) when the tenant has no `partnerCentralUrl`.

## Directory structure

```
src/main/kotlin/com/wutsi/ndopify/web/
    client/ndopify/   REST clients for ndopify-server
    config/           Spring configuration (security, REST, clock, ndopify-web-core beans)
    ui/               MVC controllers: home, owner, agent, listing, match, visit, layout (tenant advice, error handler,
                      Partner Central links)
src/main/resources/
    templates/        Thymeleaf pages served by the app
    public/           Static assets (CSS, JS, images) served under /assets
src/main/html/        The prototype generated by /init-website, which the templates are derived from. Not served —
                      and kept as is: never delete or "clean up" anything in it
```

## Environment Setup

- JDK 25, Maven 3.9+
- A running [ndopify-server](../ndopify-server) — by default on `http://localhost:8080`
  (`ndopify.server.api.base-url` in `application.yml`)
- A tenant whose `publicPortalUrl` matches the URL you browse to (e.g. `http://localhost:8082`), otherwise tenant
  resolution fails

## Running

```bash
mvn spring-boot:run          # starts on port 8082
```

In deployment, the `Procfile` runs the jar with `--spring.profiles.active=$APP_PROFILE`.

## Build & Test

```bash
mvn package
mvn test
mvn test -Dtest=ClassName#methodName
```

Tests are unit / standalone-MockMvc level and need neither ndopify-server nor a database. The `*TemplateTest`s (ex:
`ListingTemplateTest`, `MatchTemplateTest`, `VisitTemplateTest`) render the templates with a real Thymeleaf engine
(checked with jsoup), so template mistakes fail the build.

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
mvn verify                            # JaCoCo coverage gate (build-breaking)
```

Coverage thresholds are the parent POM's defaults (`jacoco.threshold.line` / `jacoco.threshold.class` in the root
`pom.xml`).
