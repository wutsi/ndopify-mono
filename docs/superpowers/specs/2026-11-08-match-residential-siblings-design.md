# Match Residential Siblings — Design

Revisits the non-goal "Allowing residential cross-types" of
`2026-11-06-match-property-type-score-design.md`: for a residential search the number of rooms matters more than the
exact housing type, so a 3-room house is a fair proposal for a 3-room apartment search.

## Rules

Housing types: `ROOM`, `STUDIO`, `APARTMENT`, `HOUSE`, `VILLA`, `DUPLEX`. The type factor (`Matcher.typeScore`)
gains these symmetric pairs; every other pair of housing types stays `0.0`:

| Pair | `type` |
|---|---|
| `STUDIO` ↔ `APARTMENT` | 0.9 |
| `HOUSE` ↔ `VILLA` | 0.9 |
| `DUPLEX` ↔ `APARTMENT` / `HOUSE` / `VILLA` | 0.85 |
| `APARTMENT` ↔ `HOUSE` / `VILLA` | 0.8 |
| `ROOM` ↔ `STUDIO` | 0.8 |

`ROOM` against any other housing type stays `0.0`: a room, often in a shared place, is a different product.

**Rooms gate.** A housing sibling (`type < 1.0`, housing listing) is ruled out when its `rooms` partial is under
`Matcher.MIN_SIBLING_ROOMS` (0.7), i.e. it must have the searched count or one more (`"5+"`: 5 or more). A search
without a rooms count (or an unparseable one) keeps every sibling. An exact type is never gated: its rooms only
weigh in the score, as before.

**Studio and room listings.** The wizards don't ask a `STUDIO` or `ROOM` for its rooms, so such a listing without
`rooms` counts as 1 room (otherwise a 1-room apartment search could never be offered a studio).

## Why 0.8–0.9 and not lower

The factor multiplies the base score and pairs below `minScore = 0.5` are dropped, so a factor `f` needs a base of
`0.5 / f`: 0.5 would require a perfect listing, 0.6 a base of 0.83. 0.8–0.9 demotes a sibling below the exact type
at equal base without turning it into a veto; the rooms gate is what makes rooms decisive.

## Impact

- No schema, DTO or UI change: `type_score` is already stored and Partner Central's "Pourquoi ce bien ?" already
  shows a sibling (ex: "Maison au lieu d'appartement").
- `ACTIVE` matches pick the siblings up on the next daily `rematch`.
- Tests: `MatcherTest` (siblings, the rooms gate, `ROOM` vetoes, studio as 1 room); `RunMatchEndpoint.sql`'s house E
  now has 5 rooms, so it stays ruled out, by the rooms gate.
