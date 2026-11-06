# Match Property Type Score — Design

## Intent

Today `Matcher.isRuledOut` treats a different `propertyType` as a hard veto
(`Matcher.kt:66`). That is right for `LAND` (a terrain is not an office or a
warehouse) and matches the product stance for residential searches
(an apartment search should not return a house). It is too strict for
commercial searches, where `OFFICE`, `RETAIL` and `BUILDING` are often
substitutable for a business, and where the customer can already choose
"Indifférent" and see every commercial type.

This change turns the property type into a **graded, multiplicative score**:

- an incompatible type still rules the pair out (`0.0`);
- a compatible sibling type is allowed but scores lower (`0.8`–`0.9`);
- an exact type, or no type preference, leaves the score unchanged (`1.0`).

The goal is to let the matcher surface commercial siblings without changing
the residential and land behaviour. At equal base score an exact type outranks
a sibling; a strong sibling may still outrank a weak exact match, which is
intended (a great retail unit is a better proposal than a mediocre office).

## Goals

- Commercial non-land searches can match sibling commercial types.
- `LAND` stays incompatible with every non-land type.
- Residential cross-types stay incompatible (same behaviour as today).
- An exact type, or no preferred type, produces exactly today's scores
  (`type = 1.0`).
- `UNKNOWN` is treated as a missing type, like `null`, not as a preference.
- `typeScore` is stored and exposed, so the agent's explanation can say why a
  sibling was proposed and so a later change can rank or soften the rule.
- A `BUILDING` search can match a `BUILDING` listing despite the two wizards
  storing different `propertyCategory` values for it.

## Non-goals

- Allowing residential cross-types (house for an apartment search). The matrix
  has a single place to change if that decision is revisited.
- Changing how a search with **no** preferred type (`null`/`UNKNOWN`) treats
  `LAND`: the category check still decides, as today. The land veto below only
  applies when a type is specified.
- Fixing the `BUILDING` category mismatch between `FindPropertyRules` (search:
  `COMMERCIAL`) and `OfferPropertyRules`/`ListingRules` (listing:
  `RESIDENTIAL`), or migrating existing rows. The matcher works around it for
  typed pairs; the `ANY`-type case stays as it is today.
- Changing the six criterion weights or `minScore` (0.5).
- Showing the type on the customer-facing `ndopify-web` match page (agent
  explanation only).

## Constraints

- `Matcher.score` is called for every (search, listing) pair of an agent, so
  the new logic must stay a pure, allocation-light function.
- `MatchScore` is constructed positionally in tests
  (`MatchServiceTest.score()`); a new trailing parameter must have a default.
- `T_MATCH` already has rows in production: the migration must be add-only and
  neutral for existing rows.
- `DISMISSED`/`SHORTLISTED`/`PROPOSED` rows are never re-scored by design, so
  no backfill is required.

## Approaches considered

1. **A — narrow the rule-out.** Keep the veto for residential and `LAND`, drop
   it for commercial non-land. Smallest change; no schema, no DTO, no UI. But
   it cannot rank an exact commercial type above a sibling, and it leaves the
   `BUILDING` category bug in place unless fixed separately.
2. **B1 — additive partial score.** Add a seventh weighted criterion and
   rebalance the six. The math defeats the gate: with `minScore = 0.5`, a
   `type = 0` only excludes a pair if its weight exceeds `0.5`, because the
   other criteria can still total `1 − w`. At a sensible weight (0.05–0.15)
   a house still passes an apartment search. It also shifts every pinned
   score in `MatcherTest`.
3. **B2 — multiplicative factor + compatibility matrix (chosen).** Compute a
   `type` factor in `[0, 1]` and multiply the existing weighted sum by it.
   Exact/no-preference leaves today's scores untouched; `0.0` is a real veto;
   sibling factors of `0.8`–`0.9` demote without re-tuning the six weights.
   The demotion is proportional: exact wins at equal base score, but a strong
   sibling can still outrank a weak exact match. That is the intended
   preference, not a defect. Requires a schema column and a DTO field, which is
   the cost of making the explanation and future ranking possible.
4. **B3 — ranking tie-breaker only.** Keep `total` unchanged and sort by
   `score DESC, typeScore DESC`. Cheapest and gives the same "exact wins at
   equal base" order, but the type never affects the stored score, the
   `minScore` threshold or the displayed percentage, so a sibling is not
   demoted at all. Rejected: it does not model "close, but not what was asked".

## Algorithm

### Type factor

`typeScore(search: PropertyType?, listing: PropertyType?): Double`

| Case (evaluated in this order) | `type` |
|---|---|
| Search type `null` or `UNKNOWN` ("Indifférent") | 1.0 |
| Listing type `null` or `UNKNOWN` | 0.0 (veto) |
| Same type | 1.0 |
| `LAND` on either side and the other is non-land | 0.0 |
| `BUILDING` ↔ `OFFICE` / `RETAIL` | 0.85 |
| `OFFICE` ↔ `RETAIL` | 0.9 |
| `INDUSTRIAL` ↔ `OFFICE` / `RETAIL` / `BUILDING` | 0.8 |
| Two different housing types (`ROOM`, `STUDIO`, `APARTMENT`, `HOUSE`, `VILLA`, `DUPLEX`) | 0.0 |
| Anything else (including `BUILDING` ↔ housing, commercial ↔ housing) | 0.0 |

Notes:

- Evaluate the rows in order: exact, then `LAND`, then the explicit
  `BUILDING`/`OFFICE`/`RETAIL`/`INDUSTRIAL` pairs, then the residential and
  generic vetoes. The implementation must not derive `BUILDING`'s
  classification from `propertyCategory`: the two wizards disagree, so only
  the explicit pairs above are authoritative.
- The residential row is deliberately `0.0`: this preserves today's behaviour.
  Softening it later means changing `0.0` to a value around `0.85` in one
  place, with no other code change.
- `null` and `UNKNOWN` on the search side both mean "no preference" (`1.0`).
  A `null`/`UNKNOWN` listing type with a specified search type is `0.0`
  (cannot be verified), matching today's veto.
- `0.8`–`0.9` are chosen against `minScore = 0.5`: a factor `f` keeps a pair
  only when its base score is at least `0.5 / f`. At `0.9` the floor is
  `0.556`; at `0.85` it is `0.588`; at `0.8` it is `0.625`. Lower factors are
  effectively vetoes, which is why the matrix does not use them.

### Rule-outs

`isRuledOut` keeps, in order:

1. the listing is the search customer's own property;
2. other `listingType`;
3. other `cityId`;
4. other `propertyCategory`, except for a specified `BUILDING` pair (see below);
5. budget currency/periodicity and price over budget.

The type veto moves out of `isRuledOut` into `score()`:

```kotlin
val type = typeScore(search.propertyType, listing.propertyType)
if (isRuledOut(search, listing) || type == 0.0) {
    return null
}
```

`type == 0.0` returns `null`, not a zero score, so "ruled out" keeps its
meaning and the existing `assertNull` tests stay valid.

### Category handling

The `propertyCategory` check is skipped **only for a specified `BUILDING`
pair**:

```kotlin
private fun categoryMismatch(search: MandateEntity, listing: ListingEntity): Boolean =
    listing.propertyCategory != search.propertyCategory &&
        (search.propertyType == null || search.propertyType == PropertyType.UNKNOWN ||
            (search.propertyType != PropertyType.BUILDING && listing.propertyType != PropertyType.BUILDING))
```

Reason: the two wizards disagree on `BUILDING` (search: `COMMERCIAL`,
listing: `RESIDENTIAL`), so a specified `BUILDING` pair must let the type matrix
decide. Every other pair keeps today's category veto, including an `APARTMENT`
residential search against an `APARTMENT` listing stored as commercial — the
existing `MatcherTest` case stays valid. When the search has no preferred type
(`null`/`UNKNOWN`), the category still decides, exactly as before. With
`BUILDING`, the matrix's explicit pairs decide: `BUILDING` ↔ `BUILDING` is
`1.0` (the bug is fixed), `BUILDING` ↔ `OFFICE`/`RETAIL` is `0.85`, and
`BUILDING` ↔ residential is `0.0`.

### Total score

`MatchScore.total` becomes:

```kotlin
val total: Double
    get() = (
        Matcher.WEIGHT_NEIGHBORHOOD * neighborhood +
        Matcher.WEIGHT_PRICE * price +
        Matcher.WEIGHT_ROOMS * rooms +
        Matcher.WEIGHT_AMENITIES * amenities +
        Matcher.WEIGHT_AVAILABILITY * availability +
        Matcher.WEIGHT_FURNITURE * furniture
        ) * type
```

The six weights and `minScore` are unchanged. With `type == 1.0` every
existing score is bit-for-bit the same as today.

### Worked example

The existing residential example is unchanged: all its listings are
`APARTMENT`, so `type = 1.0` and A/C/B keep 0.95/0.745/0.58.

A commercial search `S2` = the same fields as the fixture search but
`propertyCategory = COMMERCIAL`, `propertyType = OFFICE`:

| Listing | Type pair | `type` | Base | Total | Stored |
|---|---|---|---|---|---|
| A2, `OFFICE` | exact | 1.0 | 0.95 | 0.95 | yes |
| A2, `RETAIL` | office ↔ retail | 0.9 | 0.95 | 0.855 | yes |
| A2, `BUILDING` | office ↔ building | 0.85 | 0.95 | 0.8075 | yes |
| A2, `LAND` | land | 0.0 | — | — | no (`null`) |
| A2, `APARTMENT` | commercial ↔ residential | 0.0 | — | — | no (`null`) |

A `BUILDING` search (stored `COMMERCIAL` by the search wizard) against a
`BUILDING` listing (stored `RESIDENTIAL` by the listing wizard): exact type,
category check skipped, `type = 1.0` → matches.

## Data and contract

### Migration

`modules/ndopify-server/src/main/resources/db/migration/common/V10_3__match-type-score.sql`:

```sql
ALTER TABLE T_MATCH ADD COLUMN type_score DOUBLE NOT NULL DEFAULT 1.0 AFTER score;
```

`DEFAULT 1.0` is neutral: pre-existing rows keep their stored `score` and are
not penalised before the next re-score. Test SQL inserts that omit the column
continue to work.

### Kotlin surface

- `MatchScore`: add `val type: Double = 1.0` after `furniture` (trailing with a
  default, so positional construction in `MatchServiceTest` keeps compiling).
- `Matcher`: add the private `typeScore` matrix and the `score()` veto; update
  the class KDoc (rule-outs, factor, pointer to this design).
- `MatchEntity`: add `val typeScore: Double = 1.0`.
- `Match` (DTO): add `val typeScore: Double = 1.0`.
- `MatchService.store`: persist `typeScore = score.type`.
- `MatchMapper`: map `typeScore`.
- No new configuration property.
- Server-side sorting (`Sort.desc("score")`) needs no change: the factor is
  already inside `score`.

## Display

Partner Central's "Pourquoi ce bien ?" (`MatchRules.fit`) gains a line after
the rooms line, only when the search specifies a type. The list is built with
`listOfNotNull(neighborhood, price, rooms, type, amenities, availability,
furniture)`, so when there is no rooms line (commercial searches are never
asked for rooms) the type line naturally falls right after price.

- exact (`typeScore >= 0.999`): `FitLine(true, "Appartement, comme demandé")`;
- sibling (`0 < typeScore < 0.999`): `FitLine(false, "Boutique au lieu de Bureau")`.

Label the listing type with `MandateLabels.shortPropertyType`, fall back to
`MandateLabels.propertyType`. The customer-facing `ndopify-web` page is
unchanged.

The existing `MatchRulesTest` expectations gain the new line, and both modules'
`MatchFixtures` set `typeScore = 1.0` on the existing exact-type matches.

## Tests

`MatcherTest` (keep the pinning tests; add):

- residential cross type (`APARTMENT` vs `HOUSE`) → `null`;
- `LAND` vs any non-land → `null`;
- commercial siblings: `OFFICE` vs `RETAIL` → `total = 0.9 × base`;
  `OFFICE` vs `BUILDING` → `0.85 × base`; `INDUSTRIAL` vs `OFFICE` → `0.8 × base`;
- `BUILDING` vs `BUILDING` with opposite `propertyCategory` → matches;
- commercial vs residential (non-building) → `null`;
- search type `null` or `UNKNOWN` → `type = 1.0`;
- listing type `null` with a searched type → `null`;
- keep the existing `ruled out - other property category` case
  (`APARTMENT`/`RESIDENTIAL` search vs `APARTMENT`/`COMMERCIAL` listing): the
  narrow `BUILDING` exception must not relax it;
- `OFFICE` search vs a `BUILDING` listing stored residential → `0.85 × base`:
  the `BUILDING` exception skips the category veto, the matrix decides.

`MatchServiceTest`: persisting a non-1.0 `typeScore` reaches `MatchEntity`
(and the existing positional `MatchScore` helper still compiles thanks to the
default).

`MatchRulesTest` / `MatchViewsTest`: exact and sibling explanations.

Endpoint tests that assert a full `Match` payload may add `typeScore`; the SQL
fixtures need no change because of the column default.

## Rollout

- Deploy: the migration adds the column; `matchAll`/triggers re-score `ACTIVE`
  rows on their next run. No backfill.
- Non-`ACTIVE` rows keep `typeScore = 1.0`, which is harmless: they are not
  shown as candidates and sorting uses `score`.
- Rolling back the code leaves an unused nullable-safe column; no down
  migration is required by the project's conventions.

## Open decisions left to the builder

None. Matrix values are initial defaults and may be tuned after observing real
matches; they are listed in one place in `Matcher`.
