# NPC mandate cards — identity-first, triage order, folded list

## Intent

The Espace agent's **Mes mandats** section repeats the same title (`Locataire · Appartement`) on every search card, so
an agent with several mandates must open cards one by one to find the one they mean. The list also renders in the
server's id order (oldest first) and silently stops at the API's default 20, so the mandates that need action and the
most recent ones are the hardest to find.

## Goals

- Title each card with the client's name; the party and property type move to a quiet second line.
- Order the list for triage: `ASSIGNED` (the agent must send it), then `PENDING_APPROVAL` (waiting for the client),
  then `APPROVED` (active); newest `createdAt` first inside each group.
- Show five cards; fold the rest behind `Voir les N autres mandats` (`Voir l'autre mandat` when one).
- Load the agent's full active list (the API's maximum of 100) so the fold count is knowable.
- No ndopify-server or ndopify-dto changes.

## Non-goals

- Server-side sorting, pagination, or a `total` on `SearchMandateResponse`.
- Changes to the mandate page, its actions, or the offer list.
- Filters or search on the home page.

## Decisions

- **Identity first.** The client name is loaded in one batch (`NdCustomerClient.search`), in parallel with the other
  home reads. When the customer can't be loaded, the card falls back to the old `Propriétaire/Locataire · Type` title
  and hides the second line.
- **Triage order**, in `MandateCards.order`: status rank then `createdAt` descending, `id` descending as tiebreaker.
- **Five cards**, `MandateCards.PAGE_SIZE = 5`; the remainder is rendered in a `<details>` after the main list.
- **Load 100.** `NdMandateClient.active` asks for `limit=100`, the server's maximum. Known limit of the no-server-change
  approach: with more than 100 active mandates, the server returns the oldest 100 (id order), so the newest beyond that
  are not shown.
- **Shared markup.** The card is extracted to `templates/fragments/mandate-card.html`, used by both the visible and
  folded lists.
- The static prototype in `src/main/html/` mirrors the served page: same titles, order, five-card preview and fold.

## Constraints

- French copy, English code and comments.
- Reuse the existing CSS tokens; no new colours.
- Every failure is local: a failed customer, stats or listing read only drops that line.
- Tests are plain unit/template tests (Mockito-Kotlin, `kotlin.test`); ktlint runs on build.

## Verification

- `mvn test` in `modules/ndopify-partner-central` (full suite).
- Prototype rendered at 390 px and 1280 px, with the fold opened and an offer accepted.
