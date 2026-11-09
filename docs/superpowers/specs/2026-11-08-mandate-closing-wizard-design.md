# Closing a Mandate — Design

## Intent

Today a mandate is closed with one number: `POST /v1/mandates/{id}/close` records
the agreed rent and nothing else. But a successful deal is not a mandate — it is
a **match**: one customer's search, one property. The agent knows the deal as
“Sandrine took the apartment I sent her”, not “mandate 204 ended at 240 000”.

This design gives closing its own page and makes the deal explicit:

- a **SEARCH** mandate closes on one of the listings that interested the client
  (or on a property outside the agent's listings);
- a **LISTING** mandate closes with one of the clients that interested the
  listing (or on a client outside the agent's searches);
- when the listing belongs to the agent, closing also closes its **LISTING**
  mandate and takes the listing off the market — both sides of one deal end
  together. Another agent's listing is left to that agent.

The prototype is `modules/ndopify-partner-central/src/main/html/mandate/close.html`
(committed in `81d43b9c`); it is the visual reference, not served.

## Current state

- `CloseMandateRequest(finalPrice)`; `MandateService.close` accepts an `APPROVED`
  mandate only (else 409 `MANDATE_NOT_APPROVED`), sets `CLOSED`, `finalPrice`,
  `closedAt`, `age`, and publishes `MandateClosedEvent`.
  `ListingMandateEventListener` (synchronous, in the same transaction) then takes
  the mandate's `PUBLISHED` listing to `RENTED` / `SOLD`
  (`ListingService.onMandateClosed`).
- Partner Central's mandate page has an inline close form at the bottom
  (`MandateEndForm`, `POST /mandates/{id}/close`, opened without JS with
  `GET /mandates/{id}?end=close`), next to the cancel form.
- Matches (`T_MATCH`) are one row per (SEARCH mandate, listing), always within one
  agent's inventory: `MatchService` matches a search against its agent's
  published listings, and a listing against its agent's approved searches.
- A match the customer answered is `ACCEPTED` (a visit request implies
  `ACCEPTED`); the mandate page shows these as *Intéressés* (search side:
  *Biens correspondants*) or as interested clients (listing side: *Propositions
  et visites*).
- Nothing links a closed mandate to the listing or to the deal's other mandate.

## Goals

- Closing a **SEARCH** mandate records the listing it ended on, selected among
  the client's `ACCEPTED` matches, or records that the property is outside the
  agent's listings.
- Closing a **LISTING** mandate records the client it was rented to, selected
  among the listing's `ACCEPTED` matches, or records that the client is outside
  the agent's searches.
- When the selected match's listing belongs to the signed-in agent, one action
  closes both mandates at the same final price, and the listing goes
  `RENTED` / `SOLD`.
- The agreed rent is pre-filled from the selected listing's price (the mandate's
  price for an outside property) and remains editable.
- Both closed pages show the deal: the listing, the client, the counterpart
  mandate, or that the deal was outside the agent's parties.
- The page works without JavaScript: each step is a server-rendered form and a
  redirect; the session holds only validated answers.
- Cancel is unchanged, and so is the rest of the mandate lifecycle.

## Non-goals

- Emails/notifications on closing (none are sent today).
- A new match status (e.g. `WON`) or deal reporting/statistics.
- Marking a waiting client or a proposed match interested before closing.
- A free-text reference for an outside property, or picking another agent's
  listing from the public portal. A closure without a match records only that it
  was outside (no listing link, no counterpart).
- Sale mandates: the closing price remains a monthly rent; `SALE` listings keep
  their existing `SOLD` behaviour but are not prompted differently.
- Changing dispatch, approvals, contracts, visits or cancel.

## Approaches considered

1. **Keep the inline form, add a counterpart select and a JS confirmation.**
   Cheapest, but the mandate page is already long; the form cannot express two
   different counterpart lists cleanly; without JS the agent can reach the final
   POST without seeing what changes; and every validation would live in one
   controller. Rejected.
2. **A dedicated page with every field in one form, revealed by JavaScript.**
   Better separation, but violates the “every page works without JS” rule unless
   the server also gates the steps — at which point it is option 3 with extra
   work. Rejected.
3. **A dedicated page, one server-rendered step at a time (`?step=`), answers in
   the session, one final POST.** Chosen: it follows the existing
   *Trouver un bien* / *Rejoindre* wizard pattern, works without JS, validates
   the URL rather than trusting it, and the same page can serve both mandate
   types with different labels.

Within option 3:

- **Selection unit: match, not listing.** Chosen. A match carries the client's
  answer, the listing and their relationship; it is the deal. It also lets the
  server validate the selection (`ACCEPTED`, belongs to this mandate) rather
  than trusting a raw listing id. The list is a compact variant of the existing
  cards, not a new listing picker.
- **Interested (`ACCEPTED`) only, both sides.** Chosen. A waiting client or an
  unanswered match is no evidence that the deal happened; offering them would
  let an agent attribute a deal to a party that never wanted it. A known
  limitation follows: a deal closed offline without the client answering cannot
  be linked.
- **Close both sides.** Chosen when the selected match's listing is the agent's
  own: one deal is one match, so the customer's search and the owner's listing
  mandate end together. Another agent's listing is never touched.

## The flow

Entry: the mandate page's **Fermer le mandat** becomes a link to
`GET /mandates/{id}/close` (the page is only reachable for `APPROVED`, the
agent's own mandate). **Annuler** stays on the mandate page.

```
1 · Contrepartie        2 · Loyer                 3 · Confirmation
┌────────────────────┐  ┌───────────────────┐    ┌──────────────────────────────┐
│ SEARCH: quel bien ?│  │ Loyer convenu     │    │ Bien   Appartement · 3 ch.   │
│  ○ annonce 3012    │  │ [240 000] FCFA    │    │        Bonapriso · n° 3012   │
│    Intéressé le …  │  │      / mois       │    │ Client Sandrine Eboa         │
│  ○ annonce 2987    │  │                   │    │ Loyer  240 000 FCFA / mois   │
│    Intéressé le …  │  │ pré-rempli avec   │    │ Le mandat est fermé,         │
│  ○ un bien hors    │  │ le loyer du bien  │    │ l'annonce retirée, le mandat │
│    de vos annonces │  │ choisi            │    │ du bailleur fermé.           │
│ LISTING: quel      │  └───────────────────┘    │ [Confirmer la fermeture]     │
│ client ? ○ …       │                           └──────────────────────────────┘
└────────────────────┘
```

- Step 1 is skipped when there is no interested match; the closure then records
  an outside property/client implicitly.
- Step 2 pre-fills from the selected match's listing price; an outside property
  keeps the mandate's price. It must be a positive whole amount.
- Step 3 recaps the property, the client, the rent and exactly what will be
  closed; its button is the irreversible action.
- Before a step is shown, every earlier one must be answered; a URL beyond them
  redirects to the first incomplete step. Opening the page with no `?step` clears
  a stale draft.
- **Précédent** keeps the answers. On step 1, **Retour au mandat** clears the
  draft and returns to the mandate page.
- Success redirects to `/mandates/{id}` with the mandate page's `closed`
  outcome. A 403 / 404 / 409 is `changed`; a 5xx is `error`.

## Data and API

### ndopify-dto

Two requests, because there are two operations:

```kotlin
// mandate/dto — closing a mandate with no match (the property/client is outside the agent's parties)
data class CloseMandateRequest(
    val finalPrice: Money = Money(),
)

// match/dto — closing the deal of an ACCEPTED match (both mandates, when both are the agent's)
data class CloseMatchRequest(
    val finalPrice: Money = Money(),
)
```

**Why the match endpoint.** The `mandate` domain must not depend on `match`:
`match` depends on `mandate` and `listing`, never the reverse (root/server
`CLAUDE.md`). Closing a deal needs the match's status and the listing's status,
so the operation belongs to the match domain; `MandateService` only closes
mandates it is given.

- `Mandate` gains two deal fields, mirroring the existing scalar `contractId`
  (the mandate stores the id without depending on the match domain):
  - `dealMatchId: Long?` — the `ACCEPTED` match the mandate was closed on. Both
    mandates of a deal store the same id; the page derives the listing, the
    client and the counterpart mandate from it. An outside closure stores none.
  - `dealOutside: Boolean` — the closure had no match. It lets a closed page
    say “hors de vos annonces” rather than showing nothing, and tells an
    outside closure from a pre-feature one.
- No new error code is needed: `MATCH_NOT_ACCEPTED`, `MATCH_NOT_FOUND`,
  `LISTING_NOT_PUBLISHED`, `MANDATE_ALREADY_CLOSED`, `MANDATE_NOT_APPROVED` and
  `MANDATE_ACCESS_DENIED` already exist.
- This is a `ndopify-dto` change: release it and bump `ndopify-dto.version`
  before the server and BFF builds.

### ndopify-server

- Migration `db/migration/common/V10_4__mandate-close-deal.sql`:
  `deal_match_id BIGINT NULL` (no foreign key: the match domain owns
  `T_MATCH`, as `contract_id` is a scalar) and
  `deal_outside BOOLEAN NOT NULL DEFAULT FALSE` on `T_MANDATE`. No `@TenantId`
  change. Pre-feature closings keep `NULL` / `FALSE` and show no deal line.
- `MandateEntity` / `MandateMapper` / `Mandate` carry the two columns.

Two operations:

**A. Outside deal — `POST /v1/mandates/{id}/close`**
(`CloseMandateRequest(finalPrice)`), `MandateService.close`:

1. 404 `MANDATE_NOT_FOUND`; `ensureCanUpdate`: 403 `MANDATE_ACCESS_DENIED`;
   409 `MANDATE_ALREADY_CLOSED` when `CLOSED`/`CANCELLED`; 409
   `MANDATE_NOT_APPROVED` unless `APPROVED`; 400 `HTTP_INVALID_PARAMETER` when
   `finalPrice.amount < 1`.
2. Closure: `CLOSED`, `finalPrice`, `closedAt`, `age`, `dealOutside = true`,
   `dealMatchId = null`; `MandateClosedEvent`. The BFF finds a landlord's
   listing by mandate id for the closed panel, as it already does for the
   approved page.

**B. The deal — `POST /v1/matches/{id}/close`**
(`CloseMatchRequest(finalPrice)`), `MatchEndpoints.close` →
`MatchService.close(matchId, request)`:

1. `findById(matchId)`: 404 `MATCH_NOT_FOUND`, then
   `MandateAccessService.ensureCanUpdate(match.mandate)`: 401 / 403, exactly as
   dismiss/propose. The match's mandate is the search side.
2. 409 `MATCH_NOT_ACCEPTED` unless the match is `ACCEPTED`.
3. Resolve the sides: the search mandate (`match.mandate`) and the listing
   mandate (`match.listing.mandate`). The listing side is part of the deal when
   its agent is the search mandate's agent (always true with today's matcher);
   another agent's listing is recorded but never closed.
4. 409 `LISTING_NOT_PUBLISHED` **only when this action will itself close the
   listing side** (its mandate is not already `CLOSED`/`CANCELLED`). After the
   listing side closed first, the property is legitimately no longer
   `PUBLISHED`: a search closed afterwards must still record the same deal.
5. 400 `HTTP_INVALID_PARAMETER` when `finalPrice.amount < 1`.
6. `MandateService.closeDeal(searchMandateId, counterpartMandateId?, matchId,
   finalPrice)` in the same transaction (see below).

`MandateService.closeDeal` (internal; the match domain calls it, so `mandate`
stays unaware of `match`):

- Lock the mandates it will close in ascending id order, then re-read them.
- 409 `MANDATE_ALREADY_CLOSED` when every side to close is already
  `CLOSED`/`CANCELLED`; a side already closed is left as is and the other is
  closed (the normal case when the landlord closed first).
- 409 `MANDATE_NOT_APPROVED` when a side to close isn't `APPROVED`.
- Save each closed side: `CLOSED`, the same `finalPrice` and `closedAt`, its own
  `age`, `dealMatchId = matchId`, `dealOutside = false`; publish
  `MandateClosedEvent` for each. The existing listener takes the `PUBLISHED`
  listing to `RENTED`/`SOLD`.
- The match row stays `ACCEPTED`; no email, no visit, no contract work. The
  closed pages derive the listing, the client and the counterpart mandate from
  `dealMatchId` (see *Template and mandate page*).

### Locks

The deal close (`POST /v1/matches/{id}/close`) is the only path that locks two
mandates. `MandateService.closeDeal` locks them in **ascending id order**, then
re-reads their statuses. A concurrent close from the search page and from the
listing page therefore waits or loses cleanly instead of deadlocking. The
outside close, and the rest of the mandate lifecycle, lock one mandate (or its
contract then the mandate) and are unaffected.

### ndopify-server edge cases

- The same deal is submitted twice (double click, two tabs): the second request
  finds both mandates closed and gets 409 `MANDATE_ALREADY_CLOSED`.
- One side was closed separately first: the other still closes, the closed one
  is a no-op, and the recorded link still says what the deal was. This is the
  normal order when the landlord's side closed first and the search follows.
- The match's listing was unpublished between the step and the confirm, and
  this action would close the listing side: 409 `LISTING_NOT_PUBLISHED`; the
  confirm step shows the conflict. If the listing side is already closed, the
  closure is recorded instead (see above).
- Another agent's listing (only possible if matches ever cross agents): recorded
  as the listing id, never closed, and never `PUBLISHED`-checked — its closing
  is the other agent's business. The other agent closes it through its own
  mandate close.
- A match row deleted meanwhile (only `ACTIVE` rows can be deleted by a
  re-score; `ACCEPTED` rows are stable): 404 `MATCH_NOT_FOUND`, the agent picks
  again.
- A LISTING mandate whose listing doesn't exist or isn't `PUBLISHED` is still
  closable: there are no candidates, step 1 is skipped, and the closure is
  recorded as outside (`dealOutside = true`; the closed page finds the listing
  by mandate id when it exists). `PUBLISHED` gates the *candidates*, not the
  page.

## Partner Central (BFF)

### Files

In `ui/mandate/`, next to `MandateController`:

- `MandateCloseController.kt` — the wizard.
- `PendingMandateClose.kt` — the session draft
  (`SESSION_ATTRIBUTE = "npc.pending-mandate-close"`, `Serializable`) with
  `matchId: Long?` / `outside: Boolean` / `priceAmount: Long?`, plus
  `isComplete(step, counterpartApplicable)` and
  `firstIncompleteStep(counterpartApplicable)`.
- `MandateCloseForm.kt` — the `POST`-bound forms (counterpart id or `outside`,
  price as typed).
- `MandateCloseRules.kt` — price parsing (reuse `FindPropertyRules.parseAmount`),
  price wording, candidate labels.
- `MandateCloseViews.kt` — `CloseCandidateView` (matchId, title, reference,
  neighbourhood, rent, interest label) and the page view.
- `templates/mandate/close.html` — the three steps.
- `public/assets/js/mandate.js` may only enhance focus; every step posts.

### Routes

```
GET  /mandates/{id}/close               first step, clears a stale draft
GET  /mandates/{id}/close?step=counterpart|price|confirm
POST /mandates/{id}/close/counterpart   validate + store, 302 → ?step=price
POST /mandates/{id}/close/price         validate + store, 302 → ?step=confirm
POST /mandates/{id}/close/confirm       match chosen: NdMatchClient.close(matchId,
                                        CloseMatchRequest(price)) — else
                                        NdMandateClient.close(id,
                                        CloseMandateRequest(price)); clear session,
                                        302 → /mandates/{id} with outcome=closed
```

`counterpart` is one step name for both types; the template labels it “Client”
or “Bien” from the mandate type.

Whether step 1 applies depends on the live candidate read, so the *controller*
builds the step list per request: `counterpart` when there is at least one
`ACCEPTED` candidate, then `price`, `confirm`. A step is shown only once the
earlier ones are complete; a URL past them (including `?step=counterpart` when
there are no candidates) redirects to the first applicable one. With no
candidates, the confirm calls the outside close (`NdMandateClient.close`);
nothing is written to the session for the skipped step. Back from the price
step, when it is the first applicable step, acts as *Retour au mandat*.

### Session draft

```kotlin
data class PendingMandateClose(
    val matchId: Long? = null,    // null: not answered, or the "outside" answer (see outside)
    val outside: Boolean = false, // the "none" answer; matchId is null then
    val priceAmount: Long? = null,
) : Serializable

// isComplete(step, counterpartApplicable) / firstIncompleteStep(counterpartApplicable):
// the counterpart step is complete when matchId != null || outside; it is not
// part of the list when counterpartApplicable is false.
```

The draft holds ids and numbers only, never rendered labels: the confirm step
re-reads the candidates and the mandate, so a stale session cannot close
something the page no longer lists. The final POST sends the session's answers,
never the form's.

**Stale answers.** `POST /counterpart` validates the submitted id against the
live `ACCEPTED` candidates; an id that isn't one is a field error (re-rendered
with the list) and never reaches the session. On `GET ?step=confirm`, the
candidates are read again; if the stored match is no longer among them, the
draft's match is cleared and the response redirects to step 1 with a flash
(“Ce bien n'est plus disponible : choisissez-en un autre.”), or to the price
step when there are no candidates left. The final 409 (`MATCH_NOT_ACCEPTED`,
`LISTING_NOT_PUBLISHED`, `MANDATE_ALREADY_CLOSED`) goes back to the mandate page
as `changed`, as today.

### Reads

The mandate itself: `NdMandateClient.get(id)`; require `agentId ==
signedInAgent.id` and `APPROVED`; else the locked view. A 404 / 403 is the
locked view too.

- **LISTING:** `NdListingClient.findIdByMandate(id, agentId)` → listing. A
  missing or non-`PUBLISHED` listing isn't an error: there are simply no
  candidates, so step 1 is skipped and the closure is recorded outside (the
  listing id, when it exists, is stored anyway). Candidates only for a
  `PUBLISHED` listing: `NdMatchClient.searchByListings(listOf(listingId),
  listOf(ACCEPTED))`; their search mandates in one
  `NdMandateClient.searchByIds(…, agentId)` (another agent's is never named);
  their customers in one `NdCustomerClient.search`. This is
  `MandateController.listingClients`, restricted to `ACCEPTED`; extract the
  common read (`MandateController` keeps its visits/proposals view).
- **SEARCH:** candidates: `NdMatchClient.search(listOf(id),
  listOf(ACCEPTED))`; their listings in one `NdListingClient.searchByIds`.
- Each candidate row shows the listing title/reference and rent, the client's
  name and “Intéressé le …”; a visit date is optional (one `NdVisitClient` read,
  left out when it fails).
- A candidates read failing shows the step's “ne peuvent pas être affichés”
  message and the outside option, so the agent can retry or close without a
  link; it never silently invents an empty list.

### Template and mandate page

- `templates/mandate/close.html`: a greeting/title with the mandate number, the
  progress, the current step's form, `errors` per field, the `currency` in the
  price suffix, and the recap. Reuse the prototype's classes (`close-steps`,
  `close-hint`, `choice`, `review`).
- `MandateController`: the inline close form and `END_CLOSE` go; the cancel form
  stays. `GET /mandates/{id}?end=close` redirects to
  `/mandates/{id}/close` so old links work. The closed panel keeps showing the
  final price/age and adds the deal line from the new mandate fields:
  the listing (“Annonce n° 501”), the counterpart (“Fermé avec le mandat n°
  203”), and the client (`closed` for a listing closure, with its name from the
  counterpart mandate/customer) — or “Bien/Client hors de vos annonces/recherches”
  when `dealOutside`. On a deal closure the page reads the match by
  `dealMatchId` (`NdMatchClient.get`, allowed for a closed mandate): its
  `listingId` names the property, and its `mandateId` / the listing's
  `mandateId` give the counterpart — for a SEARCH the listing's mandate, for a
  LISTING the client's search — whose customer gives the client's name. On an
  outside LISTING closure the property is found by mandate id with the agent's
  id, as the approved page does. All of these are secondary reads: a failure
  leaves the number only; another agent's listing/mandate is never read beyond
  its public reference.
- `GET /mandates/{id}/close` with no `step` clears the session draft; a 5xx on
  the final POST is `error`, a 403/404/409 is `changed`, both redirect to the
  mandate page (the deal is over or the page is stale, so keeping the wizard
  would be misleading).

### Tests

BFF, mirroring the module's patterns:

- `MandateCloseControllerTest`: locked/unavailable mandate, step gating with and
  without candidates (a `?step=counterpart` with none goes to price), each step's
  validation, a hand-edited/removed candidate never reaching the session, the
  confirm step clearing a match that is no longer a candidate, session contents,
  the `CloseMandateRequest` body (`argumentCaptor`), the redirect/flash for
  success, 403/404/409 (`changed`) and 5xx (`error`), stale draft cleared.
- `PendingMandateCloseTest`, `MandateCloseRulesTest`, `MandateCloseTemplateTest`
  (real Thymeleaf: both mandate types, the no-candidate case, errors, the
  outside option).
- `NdMandateClientTest` / `NdMatchClientTest`: the outside and deal request
  bodies.
- `MandateControllerTest`: the link, the `?end=close` redirect, the deal line.

ndopify-server:

- `CloseMatchEndpointTest` (new): SEARCH on an own listing (both mandates
  `CLOSED`, same price, the same `dealMatchId` on both, listing `RENTED`, match
  untouched); LISTING with a client (search closed too); another agent's
  listing (not closed, `dealMatchId` recorded, no `PUBLISHED` check);
  `MATCH_NOT_FOUND`, `MATCH_NOT_ACCEPTED`, `LISTING_NOT_PUBLISHED` when this
  action closes the listing side, `MANDATE_ALREADY_CLOSED` when both are closed,
  `HTTP_INVALID_PARAMETER` for a zero price; access 403; one side already
  closed (the other still closes, `dealMatchId` recorded, no
  `LISTING_NOT_PUBLISHED`); a second submission of the same deal (409).
- `CloseMandateEndpointTest` (existing): the outside closure records
  `dealOutside`, no link; the existing listing `RENTED`/`SOLD` cases still pass.
- A lock-order test is not practical in a single-threaded endpoint test; the
  service unit test asserts the lock order helper, and the concurrent close
  case is covered by the endpoint's 409 already-closed path.

## Known limitation

Because both wizards offer `ACCEPTED` matches only, a deal made offline before
the client answered cannot be linked: the agent can close “hors annonces” /
“loué à un autre client” and the counterpart mandate stays open. A follow-up
(not in this design) is to let the closing page mark a `PROPOSED` match
interested first, or to reopen the waiting-client branch. This is deliberate:
closing must not claim an interest the customer never expressed.

## Order of work

1. `ndopify-dto`: `CloseMatchRequest`, `Mandate` deal fields; release and bump
   the version.
2. `ndopify-server`: migration, entity/mapper, `MandateService.close` (outside),
   `MandateService.closeDeal` + `MatchService.close` + `POST
   /v1/matches/{id}/close`, tests.
3. `ndopify-partner-central`: `NdMatchClient.close`, controller/session/rules/
   views, template, mandate page link + `?end=close` redirect, tests.
4. Manual walkthrough of both directions on the dev stack.
