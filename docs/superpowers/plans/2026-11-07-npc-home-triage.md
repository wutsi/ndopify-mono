# NPC home triage

## Goal

Make the Espace agent answer "what do I do now?" above the fold. The page stays a single work queue, but it
leads with what expires first, lets the agent confirm a visit without leaving the queue, and stops treating every
object as equally urgent.

## Decisions

- Accept/decline behaviour is unchanged: accepting stays one tap, declining stays confirmed (explicit user choice).
- No new colours, fonts, or spacing values; reuse the DESIGN.md tokens already exposed by `partner-central.css`.
- Every action still posts without JavaScript. Scripts only enhance.
- The contrast of white on `#1d7edf` (4.12:1) remains as documented in DESIGN.md; this pass does not silently change
  the brand colour.
- French UI copy, English code and comments.

## Work

1. **Triage header and offer order**
   - `HomeController`: compute the visible date, offer count, requested-visit count and today's visit count; sort
     the open offers by `expiresAt` ascending.
   - `AssignmentView`: carry `urgent` (< 15 min left).
   - `templates/index.html`: replace the hidden H1 with a visible "Espace agent" header + date + count links; move
     the profile nudge below the queue.
   - `home.css`: header, counts, timer emphasis, compact facts.
   - Tests: `HomeControllerTest`, `HomeTemplateTest`.

2. **Offer hierarchy**
   - Only the first (most urgent) accept is a primary button; the others are secondary.
   - `data-urgent` on the card from the server so the warning treatment appears before JS runs.
   - Tests: `HomeTemplateTest`.

3. **Inline visit confirmation**
   - `VisitCardView`: add the slot lists, selected day/time, and whether the requested slot is valid.
   - `VisitCards`: compute the slots once per page; group the upcoming visits by day (ongoing, today, tomorrow, later).
   - `VisitScheduleForm`: accept a `from` field.
   - `VisitController.schedule`: when `from=home`, redirect to `/#visits-title` with a `visit=confirmed` flash.
   - `templates/index.html`: one-tap "Confirmer" for a valid requested slot; a "Changer le créneau" details with the
     day/time selects; a success alert for the flash.
   - `home.css` / `visit.css`: confirmation block, z-index above the card link, group headings, cap.
   - Tests: `VisitCardsTest`, `VisitControllerTest`, `HomeTemplateTest`.

4. **Bounded visit feed**
   - `VisitCardsView`: `ongoing` and `upcomingGroups`.
   - `templates/index.html`: "En cours" first, then day groups; show four upcoming visits and fold the rest behind a
     "Voir les N autres visites" details.
   - `home.css`: group spacing and summary.
   - Tests: `VisitCardsTest`, `HomeTemplateTest`.

5. **Polish**
   - Shorten the repeated offer intro; link the accepted-mandate alert to `#mandates-title`.
   - Make the profile "Ajouter une photo" link a full 44px target.
   - Leave the static prototype alignment as a separate follow-up (see Deviations).
   - Run ktlint, the module tests, the Impeccable detector, and render screenshots at 390px and 1280px.

## Deviations

- Accept/decline behaviour stayed as it is, per the explicit user decision.
- The static prototype under `src/main/html/` is left untouched in this worktree: the in-flight `npc-merged-cards`
  branch already has uncommitted prototype changes, and a second prototype edit would conflict. The production
  template is the source of truth; the prototype can be regenerated separately.
- The 4.12:1 primary-button contrast is a DESIGN.md decision and remains unchanged; the finding stays documented.

