# Visit closure actions — design

**Date:** 2026-11-12
**Status:** approved through the prototype
**Module:** `ndopify-partner-central` (the BFF)

## Intent

The visit page's rare, final actions — *Refuser la demande*, *Annuler la visite*, *Déclarer
l'absence* — currently unfold as underlined `<details>` links below the primary form. The mandate
page's `.mandate-close` section communicates "this ends the matter" much better: a light-blue box
with danger buttons, and the action's form in a white card once opened.

Apply that same visual and interaction to the visit page, keeping the BFF's rule that every page
works without JavaScript.

## Goals

- Group each panel's closure actions in a light-blue `.visit-close__buttons` box, matching
  `.mandate-close`.
- Opening a closure action hides the box and shows its form in a white `.visit-close__form` card,
  with the form's submit and a *Retour* button.
- On `REQUESTED`: *Refuser la demande*.
- On `CONFIRMED`: *Annuler la visite*.
- On `WINDOW`: the no-show declaration (when available) and *Annuler la visite*, in one box.
- Without JavaScript, `GET /visits/{id}?close=refuse|cancel|no-show` renders the chosen form open.
- With JavaScript, the same links open and close the forms in place.

## Non-goals

- No change to the POST endpoints, the visit state machine, the validation rules, or the wording of
  the primary actions (confirm, start, finish).
- No change to the public `ndopify-web` visit page.
- No new colours or components: the existing `.mandate-panel__actions`, info tokens and surface
  tokens are reused.

## Approach chosen

The server decides whether the requested closure action is valid for the current panel, renders
that form open, and leaves every other form hidden. The controller ignores unknown or unavailable
values, exactly like the mandate page's `?end=` parameter. `visit.js` intercepts the trigger and
*Retour* links and toggles the same nodes in place.

Alternatives considered:

- **Pure `<details>` restyling.** Kept a duplicate trigger visible while the form was open, and the
  form could not sit in a separate white card without nesting it inside the info box. It did not
  match the approved prototype.
- **JavaScript only.** Would break the module rule that every page works without JavaScript.

## Constraints

- French UI; code, comments and docs in English.
- Tier-2 semantic tokens only (`--color-info-bg`, `--color-info-border`, `--color-surface`, spacing
  tokens), as `home.css` does for `.mandate-close`.
- The page's `POST` forms remain the source of truth; JS is enhancement only.
- The prototype under `src/main/html/` is the approved visual reference and is committed with the
  implementation.
