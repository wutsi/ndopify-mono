# ndopify-web

The public marketing site (port 8082, French UI): a Spring Boot + Thymeleaf app with no database, deployed to Heroku
(`Procfile`, `ndopify-web-main.yml`). It reads from ndopify-server, without a token: `GET /v1/tenants` to find the
tenant, and a PUBLISHED listing's `/v1/listings/{id}`, its `/images` and its `/v1/locations`; its calls to action link
to Partner Central's public wizards. Its infrastructure (`TenantContext`, `TenantFormatter`, the logging and
`X-Tenant-ID` interceptors, `NdTenantClient`) comes from `ndopify-web-core` (package `com.wutsi.ndopify.web.core`),
minus everything about sign-in, except one: a match link's `public-portal` token (`?token=`, see `MatchController`) is kept in the
`__nweb_access_token` cookie (`AccessTokenService`, `JwtAuthenticationFilter`) and sent to ndopify-server as the bearer
token (to read the match, its mandate, its agent and its listing, and post the answer); any other token is signed
out. No other page reads it.

## Code Organization

Package `com.wutsi.ndopify.web`:

- `ui/home/` (`/`, `templates/index.html`, people looking for a rental → `{partnerCentralUrl}/find-property`),
  `ui/owner/` (`/owner`, `owner.html`, property owners → `/offer-property`), `ui/agent/` (`/agent`, `agent.html`,
  agents → `/join`). Each controller only builds its link with `ui/layout/PartnerCentralUrls.of(tenant, path)`,
  null (the call to action is left out) when the tenant has no `partnerCentralUrl`.
- `ui/listing/` — a listing's public page at its `Listing.url` (`/<city>/<neighborhood>/listing/<id>/<slug>`,
  `listing.html` from `annonce.html`): `ListingController` (another path to the same id → 301 to the url),
  `ListingPages` builds the `ListingPage` view (every text formatted, never `agentRemarks`); its body (head, gallery,
  content sections) is `templates/fragments/listing-body.html`, shared with `match.html`. The "Demander une visite"
  and "Poser une question" buttons are left out for now (the bottom bar on mobile only shows the price); their popup
  and drawer are still in the page, the prototype's: `listing.js` fakes their answers, no handler posts them yet.
- `ui/match/` — `MatchController`, the link of the proposition e-mail (`/match/{id}?token=`): the token goes to the
  cookie and is stripped from the address (302); signed out or a 401 from ndopify-server (cookie cleared) → `match-link.html`
  with `reason = expired`, a 403 → `not-yours`, a 404 → the app's 404 — the same for the page and its two answers.
  Signed in → `match.html` (from `proposition.html`), the `MatchPage` that `MatchPages` builds from the match, its
  mandate (the search summary), its agent and its listing (the listing body is `ListingPages`'s, never
  `agentRemarks`); "Pourquoi ce logement ?" lists the criteria fully met that the mandate sets, and the missing
  amenities. One answer block per `AnswerState`: PENDING (match PROPOSED) → "Ça m'intéresse" (`POST /match/{id}/accept`)
  and "Pas pour moi" (`#reject` popup, `RejectForm`: `POST /match/{id}/reject`, missing reason → `OTHER`, blank comment
  → none); ACCEPTED / REJECTED → the answer, no form (answers are final: no "change my mind", unlike the prototype);
  UNAVAILABLE (listing 403/404 or not PUBLISHED, whatever the answer) → no listing, no form, the agent's WhatsApp.
  An answer redirects to `/match/{id}?answer=accepted|rejected` (the flash, shown only in that state; no text says the
  agent was told: it isn't notified yet); a second answer
  (409 `MATCH_NOT_PROPOSED`) to `/match/{id}`. "Demander une visite" is left out, as on the listing page.
- `ui/layout/HttpClientErrorHandler` — a 403/404 from ndopify-server (ex: a listing no longer published) is answered
  with the same status (`templates/error/404.html`).
- `ui/layout/TenantAdvice` — puts the tenant in every page's model as `tenant`. Controllers read it from the model
  (`model.getAttribute("tenant")`), never as a `@ModelAttribute` parameter, which request parameters could bind.
- `TenantContext` (web-core) — the tenant whose `publicPortalUrl` prefixes the request URL (from web-core's
  `NdTenantClient`, a plain `RestTemplate`); `TenantFormatter` (web-core) — dates and amounts.
- `client/ndopify/NdListingClient`, `NdLocationClient`, `NdMatchClient` (get/accept/reject), `NdMandateClient`,
  `NdAgentClient` — `RestClient`s that get `RestConfiguration`'s interceptors
  (web-core's logging and `X-Tenant-ID`).
- `config/` — `SecurityConfiguration` permits only `/`, `/agent`, `/owner`, GET of the listing paths
  (`/*/listing/*`, `/*/*/listing/*/*`…), `GET /match/*`, `POST /match/*/accept|reject`, `/assets/**` and `/error` (everything else is denied: a new page must
  be added there), `RestConfiguration`, `ClockConfiguration`, and `WebCoreConfiguration` (`TenantContext` on
  `TenantPortal.PUBLIC_PORTAL`, `@Import(NdTenantClient)`).

Static assets in `src/main/resources/public/assets/` (`main.js`, and the listing page's `listing.js` / `listing.css`,
copied from the prototype's `annonce.*`; the match page's `proposal.css` from `proposition.css`, and `proposal.js`, which
only moves the focus into its popups: `listing.js` opens them and shows the photos, `proposition.js`'s faked answers
are not ported. Served files have English names; scripts only enhance). `src/main/html/` is the
prototype (`index.html`, `louer.html`, `bailleur.html`, `agent.html`, `rechercher.html`, `joindre.html`, `proposition.html` (the match proposal), `annonce.html` — a listing's public page, with a photo viewer, a "Demander une visite" popup (day + time) and a "Poser une question" AI-assistant chat drawer — + assets),
generated by the `/init-website` command: never serve it, and never delete or "clean up" anything in it.

## Testing Patterns

As Partner Central's (see `modules/ndopify-partner-central/CLAUDE.md`): plain unit tests, no server. A
`<Page>ControllerTest` (`MockMvc` standalone) and a `<Page>TemplateTest` (real Thymeleaf engine) per page, with and
without a `partnerCentralUrl`; `ListingPagesTest` / `MatchPagesTest` for the listing and match views; `PartnerCentralUrlsTest`,
`Nd*ClientTest` (`MockRestServiceServer`).
