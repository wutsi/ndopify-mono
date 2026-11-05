# Web "Demander une visite" and visit page — Design

**Date:** 2026-11-04
**Module:** `ndopify-web`
**Status:** proposed

## Intent

A customer who accepted a proposed listing can ask to visit it from the match page (`/match/{id}`), then open
"Voir ma visite" to see where the request stands and cancel it. ndopify-server supports all of this already
(`/v1/visits`: request, get, cancel; the visit e-mails link to `{publicPortalUrl}/visits/{id}?token=`), but
ndopify-web has never wired it.

The customer's visit page renders every status. It does not include the visit code flow beyond showing the code,
the feedback form, or any check-in/check-out UI (those are the agent's, or out of scope).

## Goals

- On a match `ACCEPTED` with an `APPROVED` mandate, a `PUBLISHED` listing and no open visit, the customer can choose
  `ON_SITE` or `VIDEO`, a day, a time and an optional note, and post the request.
- No second request while a visit is open (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`): the match page shows the open
  visit and a "Voir ma visite" link instead.
- `/visits/{id}` (with `?token=` or the existing cookie) shows the visit for its customer in every status, and lets
  them cancel while it is `REQUESTED` or `CONFIRMED`.
- Success and known failures are visible, never a lost POST or a dead end.
- Works without JavaScript: popups open with `:target`, forms post, the server answers with a redirect.

## Non-goals

- No feedback form on `COMPLETED` (the visit e-mail's question is answered later), no code entry, no check-in,
  check-out or no-show UI.
- No anonymous listing-page lead; ndopify-server has no lead endpoint.
- No change to ndopify-server or ndopify-dto (visit DTOs are in `ndopify-dto` 0.0.80). The schedule overwrites the
  visit's `type`, so the page can flag a changed **slot** only, never a changed type.
- No agent page in ndopify-web (Partner Central has its own).

## Approach

**A. Two server-rendered controllers in ndopify-web** (chosen):
`MatchController` gains the request; a new `VisitController` owns `/visits/{id}` and its cancel. Both use the
existing `public-portal` cookie pattern and redirect after POST (PRG). One view per page, no AJAX, consistent with
the rest of the module.

**B. A separate request controller plus a JS submit** was rejected: more routes, a fragment refresh, two sources of
truth, and it breaks the no-JavaScript promise for no benefit.

## Server interactions

| Call | When | Token |
|---|---|---|
| `GET /v1/visits?match-id={id}&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1` | match GET when the match is `ACCEPTED` (even if the listing is no longer PUBLISHED) | customer cookie |
| `GET /v1/customers/{mandate.customerId}` | match GET, only when the request form is shown | same |
| `POST /v1/visits` `{ matchId, type, requestedAt, note? }` | match form POST | same |
| `GET /v1/visits/{id}` | visit page GET | same |
| `GET /v1/customers/{visit.customerId}` | visit page GET (for the VIDEO call number) | same |
| `POST /v1/visits/{id}/cancel` `{ reason? }` | visit cancel POST | same |

- `requestedAt` is built in the tenant's time zone (`Tenant.timeZone`) from `visitDate` (`yyyy-MM-dd`) and
  `visitTime` (`HH:mm`), as `java.util.Date`.
- A customer read that fails (403/404) is tolerated: the match page hides the contact line and does not offer
  `VIDEO` (the server needs `customer.whatsappNumber`); the visit page still shows the visit, and its VIDEO block
  simply omits the call number.
- The visit page also reads its listing (`GET /v1/listings/{id}`, images, locations) and its agent
  (`GET /v1/agents/{id}`). A listing or agent that is gone is not an error: the card is omitted / "votre agent".
- The visits search is restricted to the customer's own mandates by ndopify-server.

## Match page

- **New clients:** `NdVisitClient` (`create`, `openForMatch`), `NdCustomerClient` (`get`), beside the `Nd*Client`s.
- **New web `VisitSlots`:** exactly the server's slots — days `today + 1` … `today + 7`, times 08:00 … 20:00 every
  30 minutes (25). It does not reuse `ListingPages`' prototype lists (8 days, 10:00–21:00), which belong to the
  anonymous listing popup.
- **View model:** `MatchPage` gains `visit: VisitPanel?` and `visitFlash: String?`.
  - `VisitPanel(open: OpenVisit?, form: VisitForm?)`.
  - `OpenVisit(id: Long, type: String, slot: String, status: VisitStatus)`; slot = `Sur place · samedi 7 novembre
    2026 à 10:30` (long date in the tenant's locale, its time format) from `requestedAt`, or `scheduledAt` when
    confirmed.
  - `VisitForm(days: List<VisitDay>, times: List<Pair<String, String>>, contact: String?, video: Boolean)`;
    `contact` = `"Sandrine Eboa · WhatsApp +237 699 88 77 66"`, null without a name/number; `video` only with a
    WhatsApp number.
- **When the form is offered:** state `ACCEPTED`, mandate `APPROVED`, listing `PUBLISHED`, no open visit.
- **Open visit block:** loaded whenever the match is `ACCEPTED`, even if the listing is not `PUBLISHED` anymore. It
  replaces the button in the normal state, and is also shown in the `UNAVAILABLE` block (so the customer can still
  reach a visit open while the listing went away). It shows type + slot + a "Voir ma visite" link to `/visits/{id}`,
  and a sentence by status: `REQUESTED` → "L'agent vous confirme la date par e-mail sous 48 h."; `CONFIRMED` →
  "Votre code de visite vous a été envoyé par e-mail."; `CHECKED_IN` → "Visite en cours."
- **POST outcomes** (PRG): success → `?visit=requested`; `BadRequest` (stale slot) → `?visit=invalid-slot`;
  `Conflict` (already exists, match/mandate/listing changed, WhatsApp missing) → `?visit=conflict`.
  401/403 keep the existing `signedIn` behavior; 404 stays the app's 404.
- **Flashes:** `requested` only when an open visit was loaded (a hand-typed query says nothing false);
  `invalid-slot` and `conflict` always show their message. No error-body parsing.
- **Template:** the prototype's `#visit` popup with `th:each` days/times, contact, note and form action
  `/match/{id}/visit`; bottom bar shows "Demander une visite" when the form applies, otherwise "Voir ma visite" when a
  visit is open. Extend `proposal.js` to focus the type choice and disable submit until a day and time are chosen
  (no `preventDefault`: the form still posts).
- **Safety with the listing page:** `listing.js`'s `initVisitForm` only runs on the listing lead form, which gets a
  `data-lead-form` marker; the match popup's `#visit` also exists, so without the guard its submit would be hijacked.
  This is a one-attribute change in `listing.html` plus the guard.
- **Security:** permit `POST /match/*/visit`.

## Visit page (`/visits/{id}`)

- **Route and token:** `GET /visits/{id}?token=` behaves like the match link: a JWT-shaped token is kept in the
  `__nweb_access_token` cookie and the visitor is redirected (302) to the same path without `token`; an invalid shape
  → `visit-link` with `expired`, never set as a cookie. Without a token, no cookie or a rejected token → `expired`;
  another customer's visit (403) → `not-yours`; a missing visit (404) → the app's 404.
- **Shared helper:** extract the token-shape check and the token-stripped URL builder from `MatchController` into a
  small `ui/layout/PortalLink` object, used by both controllers (one copy of a security rule, tested once).
- **Data:** `NdVisitClient.get(id)`, then listing/images/locations through the existing clients and `ListingPages`
  (the compact card: photo, title, place, price), the agent through `NdAgentClient`, and the customer through
  `NdCustomerClient` for the VIDEO call number. All four are optional.
- **View model:** `VisitPage` carries every already-formatted text:
  - id, status, status tone/label ("Demandée", "Confirmée", "En cours", "Terminée", "Annulée", "Expirée",
    "Non effectuée"), type (`ON_SITE`/`VIDEO`), requested slot, scheduled slot, "you had asked for …" line when the
    agent changed the slot, note, code, ON_SITE address + map URL, VIDEO neighbourhood and the customer's WhatsApp
    number when available, checked-in time, cancellation (`cancelledBy`, reason, at), no-show party/reason,
    `canCancel`, `flash`, `listing: ListingPage?`, `agent: VisitAgent?`, `matchUrl = /match/{matchId}`.
- **Status blocks**, one rendered at a time:
  - `REQUESTED`: requested slot/type, note, "the agent confirms within 48 h", cancel.
  - `CONFIRMED`: scheduled slot/type (+ the requested one when changed), code (customer only), ON_SITE address and map
    link, VIDEO neighbourhood and call instructions, agent WhatsApp, cancel.
  - `CHECKED_IN`: "Visite en cours" / "Appel en cours", start time, slot, no cancel.
  - `COMPLETED`: "Votre visite est terminée", slot, no feedback form.
  - `CANCELLED`: who cancelled and when, the agent's reason when it did, "Demander une autre visite" →
    `/match/{matchId}`.
  - `EXPIRED`: slot, "the agent did not confirm within 48 h", another-visit link.
  - `NO_SHOW`: party and reason, another-visit link.
  - `UNKNOWN`: a read-only fallback; the server does not return it.
- **Cancellation:** only `REQUESTED`/`CONFIRMED`. A `#cancel` popup with an optional reason (≤ 500) posts to
  `/visits/{id}/cancel`; success → `?cancel=done`; `409` → `?cancel=conflict`; 401/403/404 as above. Flash:
  "Votre visite est annulée. L'agent est prévenu." (only when the loaded visit is `CANCELLED` by the customer) or
  "Cette visite ne peut plus être annulée."
- **Template/assets:** `visit.html` from `visite.html`, `visit-link.html` for expired/not-yours, `visit.css` from
  `visite.css`, and a small `visit.js` (focus the reason in `#cancel`; the generic popups come from `listing.js`,
  whose listing-only parts are guarded). No prototype state faking is ported.
- **Security:** permit `GET /visits/*` and `POST /visits/*/cancel`.

## Review focus

1. **Tenant time zone:** the posted instant is the one the server expects; a UTC "today" at 23:30 in Douala must not
   drop the valid first day (`VisitSlotsTest`, `MatchControllerTest`).
2. **No duplicate request:** an open visit hides the form; a double tap lands on `VISIT_ALREADY_EXISTS` and the
   reloaded page still shows exactly one visit.
3. **Video without WhatsApp:** the option is not offered, and a raced request gets the conflict flash.
4. **Stale form:** a slot that expired between render and post is `400 VISIT_INVALID_SLOT`; the page reloads with
   fresh slots and an explanatory flash — never a 500 or a lost form.
5. **Token and access:** a visit link from an e-mail works signed out; a token of another customer shows
   `not-yours`, never the visit; no token in the address bar after the redirect, never logged.
6. **Cancellation:** only before the check-in; a stale page that posts after the agent started gets
   `VISIT_CANNOT_BE_CANCELLED` and the reload shows the current status.
7. **No JavaScript:** popups via `:target`, forms post, redirects and flashes intact; `agentRemarks` still never
   shown.

## Documentation

Update `modules/ndopify-web/CLAUDE.md` (match request + new visit page, clients, security matchers, token pattern) and
`README.md` (features and clients).
