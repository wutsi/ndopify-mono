# Search Mandate Stats — Design

## Intent

Show a one-line activity summary for every approved `SEARCH` mandate, on the Espace agent's
card and on the mandate page:

```
12 biens · 1 intéressé · 3 visites
```

The line mirrors the listing activity line (`2 personnes intéressées · 3 visites`) on the other
side of the marketplace: the agent sees the search's pool, the client's response and the visits
without opening the mandate.

The numbers now come from one server-side batch read, `GET /v1/mandates/stats`, mirroring
`GET /v1/listings/stats`. The home page no longer loads the matches or the contracts just to draw
its cards, keeps its visit read to open visits only, and the card no longer shows a next step.

## What the numbers mean

`12 biens` is the search's **current matching pool**, not a lifetime total. It counts one row
per (mandate, listing) pair in the statuses shown in *Biens correspondants*:

| Match status | Counted? | Meaning |
|---|---|---|
| `ACTIVE` | yes | matched, not sent yet |
| `PROPOSED` | yes | sent, waiting for the client |
| `ACCEPTED` | yes | client said yes |
| `EXPIRED` | yes | sent, no answer in time, still re-sendable |
| `REJECTED` | no | client refused |
| `DISMISSED` | no | agent set it aside ("Écarter") |
| `SHORTLISTED` | no | kept for later; partner-central never creates or shows it today |
| `UNKNOWN` | no | no status |

`1 intéressé` counts `ACCEPTED` matches. `3 visites` is `VisitStats.total` (upcoming +
completed) for the mandate, with the same windows as the listing stats: fresh requests, future
confirmations and checked-in visits are upcoming; completed ones are completed.

The number is a snapshot: the match search only returns rows while the mandate is `APPROVED`
and the listing is `PUBLISHED`; the matching job re-scores `ACTIVE` rows and deletes those that
no longer qualify. A stats endpoint counts the rows directly, so it is no longer capped by the
100-row page limit of `GET /v1/matches`.

## Contract

`GET /v1/mandates/stats?mandate-id=…`, one row per distinct id in request order, zeros included:

```kotlin
data class MandateStats(
    val mandateId: Long = -1,
    val properties: Int = 0,
    val interested: Int = 0,
    val upcomingVisits: Int = 0,
    val completedVisits: Int = 0,
)

data class SearchMandateStatsRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
)

data class SearchMandateStatsResponse(
    val stats: List<MandateStats> = emptyList(),
)
```

- No `mandate-id` → 400 `HTTP_MISSING_PARAMETER` (`mandate-id`).
- Anonymous → 401.
- A mandate that is gone or in another tenant → 404 `MANDATE_NOT_FOUND`.
- Another agent's mandate, and not offered to the caller → 403 `MANDATE_ACCESS_DENIED`
  (`MandateAccessService.ensureCanRead`; admins pass).
- The rows are aggregated in SQL with `GROUP BY`, not loaded per mandate.

## Client loading

**Home card (`HomeController`).**

- One `GET /v1/mandates?agent-id=&status=…` for the mandates, as today.
- One `GET /v1/mandates/stats?mandate-id=…` for the approved searches (skipped without one).
- One `GET /v1/listings/stats?listing-id=…` for the published listings, as today.
- The *Visites* section still reads the agent's **open** visits only, with their listings and
  customers.
- No `GET /v1/matches` and no `GET /v1/contracts`: the card has no next step and no per-match
  lines left to draw. A failure of the stats call leaves the line out; the card still renders.

**Mandate page (`MandateController`).**

- It keeps its match and visit reads: *Biens correspondants*, *Propositions* and *Visites* need
  the actual rows.
- It derives the same line from those rows (`SearchStatsView.of(mandate, matches, visitStats)`),
  so it makes no extra stats call.

## Display

- **Home card:** the line replaces the current `m.matches` line (`3 nouveaux biens correspondants`)
  and `m.proposals` line (`1 intéressé · 1 refusé · 1 en attente`). It links to
  `/mandates/{id}#matches-title`.
- **Next step removed:** `MandateCardView.nextStep`, `MatchRules.nextStep`,
  `MandateRules.nextStep` and `MandateRules.listingStep` go away. The card keeps its title,
  tag, facts, listing line and listing activity.
- **Mandate page:** the same line in the *Biens correspondants* header, only when the pool is
  non-empty. The detailed proposal line (`1 intéressé · 1 refusé · 1 en attente`), the visit
  breakdown in the approved panel and the per-match cards stay below.
- Only an approved search shows the line. Before signature, for a listing mandate, or when the
  stats can't be read, there is no line.

## States

- Drop zero segments, never wrap:
  - `12 biens · 1 intéressé · 3 visites`
  - `12 biens · 3 visites`
  - `12 biens · 1 intéressé`
  - `12 biens`
- No matches: `Aucun bien correspondant pour l'instant` on the home card. The mandate page
  keeps its existing, fuller empty state and hides the line.
- Stats unreadable: no line on the home card, the rest of the card still renders.
- Plural: `1 bien` / `12 biens`, `1 intéressé` / `2 intéressés`, `1 visite` / `3 visites`.

## Mobile

One line. The canonical label is 34 characters, which fits a 320px card at the existing 14px
hint size. Use `white-space: nowrap`; drop to 13px below 360px. The home link keeps the 44px
tap target. Segments use ` · ` (space, middle dot, space).

## Prototype

`modules/ndopify-partner-central/src/main/html/` is updated with the same card: the two match
lines and the next step are gone, the activity line is shown for an approved search (204), and
`mandate.html?id=204` shows the line above *Biens correspondants*. The prototype remains a
mockup: it is not wired into the application.

## Out of scope

- Page views, average score, budget coverage, and refused/pending counts on the home card.
- `SHORTLISTED` and sending several matches at once.
- Any change to the mandate page's detailed proposal line, visit breakdown or match cards.
