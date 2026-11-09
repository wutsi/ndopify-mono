# ndopify-web

The public marketing site (port 8082, French UI): a Spring Boot + Thymeleaf app with no database, deployed to Heroku
(`Procfile`, `ndopify-web-main.yml`). It reads from ndopify-server, without a token: `GET /v1/tenants` to find the
tenant, and a PUBLISHED, SOLD or RENTED listing's `/v1/listings/{id}`, its `/images` and its `/v1/locations`; its calls to action link
to Partner Central's public wizards. Its infrastructure (`TenantContext`, `TenantFormatter`, the logging and
`X-Tenant-ID` interceptors, `NdTenantClient`) comes from `ndopify-web-core` (package `com.wutsi.ndopify.web.core`),
minus everything about sign-in, except one: a `public-portal` token (`?token=`, see `MatchController` and
`VisitController`) is kept in the `__nweb_access_token` cookie (`AccessTokenService`, `JwtAuthenticationFilter`) and sent
to ndopify-server as the bearer token (to read the match, its mandate, its agent and its listing, the customer and the
visit, and post the answer, the visit request or the cancellation); any other token is signed out. The token is sent on
every ndopify-server call while the cookie is present (a 401 clears it: `HttpClientErrorHandler`).

## Code Organization

Package `com.wutsi.ndopify.web`:

- `ui/home/` (`/`, `templates/index.html`, people looking for a rental → `{partnerCentralUrl}/find-property`),
  `ui/owner/` (`/owner`, `owner.html`, property owners → `/offer-property`), `ui/agent/` (`/agent`, `agent.html`,
  agents → `/join`). Each controller only builds its link with `ui/layout/PartnerCentralUrls.of(tenant, path)`,
  null (the call to action is left out) when the tenant has no `partnerCentralUrl`.
- `ui/listing/` — a listing's public page at its `Listing.url` (`/<city>/<neighborhood>/listing/<id>/<slug>`,
  `listing.html` from `annonce.html`): `ListingController` (another path to the same id → 301 to the url),
  `ListingPages` builds the `ListingPage` view (every text formatted, never `agentRemarks`); its body (head, gallery,
  content sections) is `templates/fragments/listing-body.html`, shared with `match.html`. A SOLD or RENTED listing keeps
  its public page, marked "Vendu"/"Loué": its price card and bottom bar show its last published listing price, never a
  closing price, and its conditions are left out. The "Demander une visite"
  and "Poser une question" buttons are left out for now (the bottom bar on mobile only shows the price); their popup
  and drawer are still in the page, the prototype's: `listing.js` fakes their answers, no handler posts them yet.
  ndopify-server supports visits (request, schedule, check-in/out, feedback), but this page doesn't post one yet.
- `ui/match/` — `MatchController`, the link of the proposition e-mail (`/match/{id}?token=`): the token goes to the
  cookie and is stripped from the address (302); signed out or a 401 from ndopify-server (cookie cleared) → `match-link.html`
  with `reason = expired`, a 403 → `not-yours`, a 404 → the app's 404 (an agent that's gone → "votre agent"), a `token` that isn't JWT-shaped → `expired`, never set as a cookie — the same for the page and its three answers.
  Signed in → `match.html` (from `proposition.html`), the `MatchPage` that `MatchPages` builds from the match, its
  mandate (the search summary), its agent and its listing (the listing body is `ListingPages`'s, never
  `agentRemarks`); "Pourquoi ce logement ?" lists the criteria fully met that the mandate sets, and the missing
  amenities. One answer block per `AnswerState`: PENDING (match PROPOSED) → "Ça m'intéresse" (`POST /match/{id}/accept`)
  and "Pas pour moi" (`#reject` popup, `RejectForm`: `POST /match/{id}/reject`, missing reason → `OTHER`, blank comment
  → none); ACCEPTED / REJECTED → the answer, no form (answers are final: no "change my mind", unlike the prototype);
  EXPIRED (not answered within ndopify-server's `ndopify.match.expire.days`) → the listing, "Cette proposition a
  expiré", no form, contact the agent;
  UNAVAILABLE (listing 403/404 or not PUBLISHED, whatever the answer) → no listing, no form, the agent's WhatsApp.
  An answer redirects to `/match/{id}?answer=accepted|rejected` (the flash, shown only in that state; no text says the
  agent was told: it isn't notified yet); a second answer
  (409 `MATCH_NOT_PROPOSED`) to `/match/{id}`.
  Once ACCEPTED, the visit block: the match's open visit (REQUESTED / CONFIRMED / CHECKED_IN, `NdVisitClient.openForMatch`)
  with its slot and "Voir ma visite" (`/visits/{id}`); without one, and when the mandate is APPROVED, "Demander une
  visite" (`#visit` popup, `VisitRequestForm`): ON_SITE or VIDEO, one of `VisitSlots`'s next 7 days and times, an
  optional note (≤ 500), and a contact line from `NdCustomerClient` (video only with a WhatsApp number) →
  `POST /match/{id}/visit`, then `?visit=requested|invalid-slot|conflict` for the flash (a 400: the slot is gone; a
  409: already requested). An ACCEPTED match whose listing went away still shows its open visit; with no form and no
  open visit, the page only gives the agent's contact.
- `ui/visit/` — `VisitController`, the link of the "visite" e-mail (`/visits/{id}?token=`): the same token handling as
  the match link (cookie, 302, `visit-link.html` with `reason = expired` / `not-yours`, a bad-shaped token never set),
  then `visit.html`, the `VisitPage` that `VisitPages` builds from the visit: the listing (linked to `/match/{id}`),
  the agent, the customer's contact when readable, and one block for the visit's status — REQUESTED, CONFIRMED,
  CHECKED_IN, COMPLETED, CANCELLED, EXPIRED, NO_SHOW. The customer's code is shown on a CONFIRMED visit; the exact
  address and map never are, only the city, neighbourhood and landmark; a changed slot is called out on a CONFIRMED
  visit. "Annuler
  ma demande" / "Annuler la visite" (`#cancel` popup, `CancelVisitForm`, optional reason ≤ 500) posts
  `POST /visits/{id}/cancel` while REQUESTED or CONFIRMED, before the check-in; `?cancel=done|conflict` drives the
  flash. No feedback form and no check-in UI: the page only reads. A listing, agent or customer that ndopify-server no
  longer serves is not an error: the listing card is omitted, a gone agent's card shows "votre agent", and a gone
  customer just hides their contact.
- `ui/layout/HttpClientErrorHandler` — a 403/404 from ndopify-server (ex: a listing no longer published) is answered
  with the same status (`templates/error/404.html`); a 401 (it rejected the cookie's token) clears the cookie and
  redirects to the same page (a 404 when the request had no cookie, so it can't loop).
- `ui/layout/TenantAdvice` — puts the tenant in every page's model as `tenant`. Controllers read it from the model
  (`model.getAttribute("tenant")`), never as a `@ModelAttribute` parameter, which request parameters could bind.
- `TenantContext` (web-core) — the tenant whose `publicPortalUrl` prefixes the request URL (from web-core's
  `NdTenantClient`, a plain `RestTemplate`); `TenantFormatter` (web-core) — dates and amounts.
- `client/ndopify/NdListingClient`, `NdLocationClient`, `NdMatchClient` (get/accept/reject), `NdMandateClient`,
  `NdAgentClient`, `NdVisitClient` (create/get/cancel, and `openForMatch`), `NdCustomerClient` (get) — `RestClient`s
  that get `RestConfiguration`'s interceptors (web-core's logging and `X-Tenant-ID`). `ui/visit/VisitSlots` holds the
  offered slots (the next 7 days, 08:00–20:00 every 30 min), the tenant-local instant and the slot label, shared by the
  match popup and the visit page.
- `config/` — `SecurityConfiguration` permits only `/`, `/agent`, `/owner`, GET of the listing paths
  (`/*/listing/*`, `/*/*/listing/*/*`…), `GET /match/*`, `POST /match/*/accept|reject|visit`, `GET /visits/*`,
  `POST /visits/*/cancel`, `/assets/**` and `/error` (everything else is denied: a new page must
  be added there), `RestConfiguration`, `ClockConfiguration`, and `WebCoreConfiguration` (`TenantContext` on
  `TenantPortal.PUBLIC_PORTAL`, `@Import(NdTenantClient)`).

Static assets in `src/main/resources/public/assets/` (`main.js`, and the listing page's `listing.js` / `listing.css`,
copied from the prototype's `annonce.*`; the match page's `proposal.css` from `proposition.css`, now with the visit
request and open-visit rules, and `proposal.js`, which only moves the focus into its popups and visually disables the
"Demander une visite" submit (`aria-disabled`) until a slot is chosen (the form still posts): `listing.js` opens and
closes them and shows the photos,
`proposition.js`'s faked answers are not ported; the visit page's `visit.css` / `visit.js`, which only moves the focus
into `#cancel` — `listing.js` opens and closes it. `listing.js`'s lead form is guarded on the prototype's markers
(`data-interest-form`, `data-interest-done`, `data-phone-name`): the match page reuses `#visit` for its own request
popup, which is not the listing's lead popup and is left alone. Served files have English names; scripts only
enhance). `src/main/html/` is the
prototype (`index.html`, `louer.html`, `bailleur.html`, `agent.html`, `rechercher.html`, `joindre.html`, `proposition.html` (the match proposal), `annonce.html` — a listing's public page, with a photo viewer, a "Demander une visite" popup (day + time) and a "Poser une question" AI-assistant chat drawer — and `visite.html` for the visit flow + assets),
generated by the `/init-website` command: never serve it, and never delete or "clean up" anything in it.

## Testing Patterns

As Partner Central's (see `modules/ndopify-partner-central/CLAUDE.md`): plain unit tests, no server. A
`<Page>ControllerTest` (`MockMvc` standalone) and a `<Page>TemplateTest` (real Thymeleaf engine) per page, with and
without a `partnerCentralUrl`; `ListingPagesTest` / `MatchPagesTest` / `VisitPagesTest` for the listing, match and
visit views; `VisitSlotsTest`; `PartnerCentralUrlsTest`, `Nd*ClientTest` (`MockRestServiceServer`).
