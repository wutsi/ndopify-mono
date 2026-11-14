# Mark co-brokerage listings in *Biens correspondants* — Design

**Date:** 2026-11-13
**Status:** approved (prototype: `mockup-d.html`, mixed-list top card)
**Size:** small change

## Intent

On a search mandate's *Biens correspondants*, a card may be one of the agent's own published listings or another
agent's published listing found by the co-brokerage fallback (`Match.coBrokerage`). Today the two are
indistinguishable on the page: only the link target differs (`?match=` for co-brokerage). An agent reading the list
can mistake another agent's listing for their own.

The job of the distinction is informational only: **do not mistake it for mine**. No change of priority, no contact
action, no change to own-inventory cards.

## Goals

- On a co-brokerage card, show one compact line under the title: a person icon and the **listing agent's full name**
  (« Clarisse Mbarga »), in `--color-info-fg`.
- When the name can't be read, show the existing generic wording « Bien d'un autre agent » instead.
- Leave own-inventory cards exactly as they are: no line, same title, status tag, facts, actions and height.
- Keep the line visible when a visit block hides the status tag.
- Work from 320px up, without horizontal scroll, and keep the 44px title target.
- Correct the section intro, which currently promises only the agent's own listings.

## Non-goals

- « Votre annonce » or any label on own-inventory cards.
- Sorting or prioritising own inventory above co-brokerage on the page.
- Showing the counterpart agent's contact, profile link or any "Contacter l'agent" action.
- Changing the *Propositions et visites* section, the server API, the `Match` DTO or the matcher.
- Changing the card's actions (`Envoyer`, `Écarter`) or the co-brokerage listing page.
- Updating the static prototype in this pass: the BFF ships first, the prototype follows.

## Anti-goals

- A distinction conveyed by colour alone.
- Revealing another agent's customer (the anti-sautage rule), or adding the counterpart agent's customer anywhere.
- A line, pill or extra height on own-inventory cards.

## Approaches considered

1. **Fact in the existing facts line** (`Origine : bien d'un autre agent`) — the prototype's current treatment.
   Hint-sized, buried after score and neighbourhood, and it lengthens a line that already wraps.
2. **Two badges in the card head** — a provenance pill next to the status pill. Scannable, but crowds the head on
   phones and mixes provenance with state.
3. **One compact line under the title** — chosen. Reads before the facts, leaves the status pill alone, and does not
   turn provenance into a second status.

For (3), the wording was iterated: generic label, « Bien de Clarisse Mbarga », name + generic mention, then both
sides labelled (« Votre annonce »). The retained form is the **bare agent name**: it labels only the exception,
keeps own cards untouched, and the person icon supplies the “whose listing” reading that « Bien de… » would.

## Design

### View model

`MatchView` gains `origin: String? = null`: the publisher line under the title, null for own inventory. `MatchViews.section`
gains `agentNames: Map<Long, String> = emptyMap()` (listing agent id → full name) and sets:

```kotlin
origin = if (match.coBrokerage) agentNames[listing.agentId] ?: OTHER_AGENT else null
```

with `OTHER_AGENT = "Bien d'un autre agent"`. `coBrokerage` stays for the listing URL.

### Reading the agent names

`MandateController` injects `NdAgentClient`. For the matches it already loads as `ListingSummary`:

1. keep the shown matches with `coBrokerage = true`;
2. take their listing's `agentId`, excluding the signed-in agent and `-1`;
3. fetch each distinct id with `agentClient.get(id)` through the existing `lookup(...)` helper, and keep
   `ProfileRules.fullName(agent).takeIf { it.isNotBlank() }`;
4. pass the map to `MatchViews.section`.

Every read is secondary: a failed or missing agent leaves the generic line, never the section unavailable. No call is
made when the page has no co-brokerage match.

### Card

Between the head and the facts:

```html
<p class="match__origin" th:if="${match.origin}" data-match-origin>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…person…</svg>
    <span th:text="${match.origin}">Clarisse Mbarga</span>
</p>
```

```css
.match__origin {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    color: var(--color-info-fg);
    font-size: var(--step-hint);
    font-weight: 600;
}

.match__origin .icon {
    width: 1rem;
    height: 1rem;
    flex: none;
}
```

- A person icon, `aria-hidden`, decorative; the text carries the meaning.
- No background, border or pill: the line is not a status.
- Rendered independently of the status pill, so it remains when a visit block hides the status.
- Own-inventory cards render no line and keep their current height.

### Section intro

Replace « Les biens de vos annonces publiées qui correspondent à la recherche du client. » with wording that
covers both sources, e.g. « Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par
d'autres agents. Envoyez-les-lui ou écartez-les. » (exact wording is a builder call).

## Mobile behaviour (verified with the real CSS)

| Width | Result |
|-------|--------|
| 700px | title and status on line 1; the name line under the title |
| 360–375px | same; the name fits on one row, facts wrap as today |
| 320px | same; the name still fits; no horizontal scroll |

Own-inventory cards are unchanged at every width. The line adds roughly 20px of height to co-brokerage cards only.

## Privacy

The listing agent's identity is allowed by the co-brokerage anti-sautage matrix: agents may know each other; the
counterpart **customer** may never be named or linked. This line reads only the listing's `agentId` and the public
agent profile, never the listing's mandate or customer.

## Tests

- `MatchViewsTest`: `origin` is the agent name when provided, « Bien d'un autre agent » without one, and null for an
  own-inventory match.
- `MandateControllerTest` / `MandateTemplateTest` / `MandateEditTemplateTest`: constructor wiring for `NdAgentClient`.
- `MandateTemplateTest`: the name renders for a co-brokerage match; the generic line renders when the agent lookup
  returns nothing; an own match has no line; a co-brokerage match with a visit keeps the line; visits unavailable
  keeps the line; the intro no longer says only « vos annonces publiées ».

## Left to the builder

- Exact French wording of the section intro.
- The person icon path, reusing the inline `icon` conventions of the templates.
