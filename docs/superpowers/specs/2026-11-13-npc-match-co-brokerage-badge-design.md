# Mark co-brokerage listings in *Biens correspondants* — Design

**Date:** 2026-11-13
**Status:** approved (prototype: `mockup-c.html`, treatment B)
**Size:** small change

## Intent

On a search mandate's *Biens correspondants*, a card may be one of the agent's own published listings or another
agent's published listing found by the co-brokerage fallback (`Match.coBrokerage`). Today the two are
indistinguishable on the page: only the link target differs (`?match=` for co-brokerage). An agent reading the list
can mistake another agent's listing for their own.

The job of the distinction is informational only: **do not mistake it for mine**. No change of priority, no
counterpart-agent contact, no new action.

## Goals

- Mark every co-brokerage card with one compact line under the title: **Bien d'un autre agent**, in
  `--color-info-fg` with a small house icon.
- Leave own-inventory cards exactly as they are: same title, status tag, facts, actions and height.
- Keep the line visible when a visit block hides the status tag.
- Work from 320px up, without horizontal scroll, and keep the 44px title target.
- Correct the section intro, which currently promises only the agent's own listings.

## Non-goals

- Sorting or prioritising own inventory above co-brokerage on the page.
- Showing the counterpart agent's identity, contact or profile, or any "Contacter l'agent" action.
- Changing the *Propositions et visites* section, the server API, the `Match` DTO or the matcher.
- Changing the card's actions (`Envoyer`, `Écarter`) or the co-brokerage listing page.

## Anti-goals

- A distinction conveyed by colour alone.
- Revealing another agent's customer (the anti-sautage rule), or adding the counterpart agent's customer anywhere.
- A badge, pill or extra height on own-inventory cards.

## Approaches considered

1. **Fact in the existing facts line** (`Origine : bien d'un autre agent`) — the prototype's former treatment.
   Smallest diff, but hint-sized, buried after score and neighbourhood, and it lengthens a line that already wraps.
2. **Two badges in the card head** — a neutral provenance pill next to the status pill. Tested, scannable, but
   crowds the head on phones and mixes provenance with state.
3. **One compact line under the title** — chosen. Reads before the facts, leaves the status pill alone, and does not
   turn provenance into a second status.

Three colour treatments were shown for (3): neutral text, info-navy text, and an info-tinted line. **Info-navy text**
was chosen: it stands out without reading as a status chip, and keeps 10.4:1 contrast on white.

## Design

### View model

`MatchView` gains `coBrokerage: Boolean = false`, set from `Match.coBrokerage` in `MatchViews.view`. Nothing else in
the section's view model changes.

### Card

Between the head and the facts:

```html
<p class="match__origin" th:if="${match.coBrokerage}" data-match-origin>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">…</svg>
    <span>Bien d'un autre agent</span>
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

- A house icon, `aria-hidden`, decorative; the text carries the meaning.
- No background, border or pill: the line is not a status.
- Rendered independently of the status pill, so it remains when a visit block hides the status.
- Own-inventory cards render no line and keep their current height.

### Section intro

Replace « Les biens de vos annonces publiées qui correspondent à la recherche du client. » with wording that
covers both sources, e.g. « Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par
d'autres agents. Envoyez-les-lui ou écartez-les. » (exact wording is a builder call).

### Prototype parity

`src/main/html/mandate.html`, `partner-central.js` and `home.css` are the design source and are never served. Replace
the `Origine : bien d'un autre agent` fact in `renderMatches` with the same line and icon, add the `.match__origin`
rule, and mirror the intro wording.

## Mobile behaviour (verified with the real CSS)

| Width | Result |
|-------|--------|
| 700px | title and status on line 1; provenance line under the title |
| 360–375px | same; the line fits on one row, facts wrap as today |
| 320px | same; the line still fits on one row; no horizontal scroll |

Own-inventory cards are unchanged at every width. The line adds roughly 20px of height to co-brokerage cards only.

## Accessibility

- The marker is real text, part of the card's content, not a colour or an icon-only cue.
- It follows the title in DOM order, so it is read with the card.
- `--color-info-fg` on white is 10.4:1.
- It is not interactive; no target-size change. The title link keeps its 44px area.

## Tests

- `MatchViewsTest`: `coBrokerage` is carried into `MatchView`.
- `MatchTemplateTest`: the provenance line and its text appear for a co-brokerage match only; an own match has no
  line; the intro no longer says only « vos annonces publiées ».
- Prototype check: the rendered widths above are manual/visual, not a test.

## Left to the builder

- Exact French wording of the section intro.
- The house icon path, reusing the inline `icon` conventions of the templates.
