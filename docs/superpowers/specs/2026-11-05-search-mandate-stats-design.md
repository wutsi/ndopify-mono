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
completed) for the mandate.

The number is a snapshot: the match search only returns rows while the mandate is `APPROVED`
and the listing is `PUBLISHED`; the matching job re-scores `ACTIVE` rows and deletes those that
no longer qualify. It is limited to the agent's own published listings that passed the matcher
(same city, listing type, property category, property type when asked, currency/periodicity,
price within budget + tolerance, not the client's own property, above the minimum score), one
row per property.

The read is capped at 100 matches, so the line can show at most 100 today.

## Display

- **Home card:** replaces the current `m.matches` line (`3 nouveaux biens correspondants`) and
  `m.proposals` line (`1 intéressé · 1 refusé · 1 en attente`). Keep *Prochaine étape*. The
  line links to `/mandates/{id}#matches-title`.
- **Mandate page:** the same line in the *Biens correspondants* header, only when the pool is
  non-empty. The detailed proposal line (`1 intéressé · 1 refusé · 1 en attente`), the visit
  breakdown in the approved panel and the per-match cards stay below.
- Only an approved search shows the line. Before signature, for a listing mandate, or when the
  matches can't be read, there is no line.

## States

- Drop zero segments, never wrap:
  - `12 biens · 1 intéressé · 3 visites`
  - `12 biens · 3 visites`
  - `12 biens · 1 intéressé`
  - `12 biens`
- No matches: `Aucun bien correspondant pour l'instant` on the home card. The mandate page
  keeps its existing, fuller empty state and hides the line.
- Visits unreadable: omit the visits segment; the pool and interest still show.
- Plural: `1 bien` / `12 biens`, `1 intéressé` / `2 intéressés`, `1 visite` / `3 visites`.

## Mobile

One line. The canonical label is 34 characters, which fits a 320px card at the existing 14px
hint size. Use `white-space: nowrap`; drop to 13px below 360px. The home link keeps the 44px
tap target. Segments use ` · ` (space, middle dot, space).

## Out of scope

- A server-side `MandateStats` endpoint; the existing match read and the agent's visit read
  supply the numbers.
- `SHORTLISTED`, page views, average score, budget coverage, and refused/pending counts on the
  home card.
- Any server or DTO change; this is a partner-central view change.
