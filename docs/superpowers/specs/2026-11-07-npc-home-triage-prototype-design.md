# NPC home triage — prototype alignment

## Intent

The BFF on `npc-home-triage` ships a triage-first Espace agent home. The static prototype must show the same page so
design review and the served page stay aligned. This work lives on `npc-home-triage` and touches only
`modules/ndopify-partner-central/src/main/html/`.

## Goals

- Visible “Espace agent” header: the day's date and count chips (offers, visits to confirm, visits today).
- Offers sorted by expiry; `data-urgent` under 15 minutes; only the first open offer's accept is primary.
- Requested visits confirm inline: one tap when the client's slot is in the allowed lists, otherwise
  “Choisir un créneau” with day/time selects; confirming simulates the server and moves the visit to upcoming.
- Visits: “En cours” first, then upcoming grouped `Aujourd'hui` / `Demain` / date; four cards, the rest folded behind
  “Voir les N autres visites”.
- Profile nudge moved below the queue.

## Non-goals

- No change to the mandate or listing pages, the BFF, or the design tokens.
- No network, build step or module loader: the prototype keeps working from `file://`.

## Constraints

- French copy, English code and comments; reuse the tokens `partner-central.css` already exposes.
- Existing accept/decline, timers, visit fixtures and their session persistence stay.
- Files: `index.html`, `assets/js/partner-central.js`, `assets/js/visit.js`, `assets/css/home.css`,
  `assets/css/visit.css`.

## Approach

Port the served behaviour into the prototype's own simulation, rather than static-only markup, because the prototype
already simulates accept/decline and visits, and the counts, order and folding must react to those actions.

- `partner-central.js` owns the triage header: one `updateHomeIntro()` reads the offers from the DOM and the visit
  counts from `window.NpcVisits.homeCounts()`; called after initialisation and after every accept, decline or
  confirmation.
- `initAssignments` sorts the cards by `data-expires-in`, marks `data-urgent` under 15 minutes, and gives only the
  first open card a primary accept; order and primary are re-evaluated after a card closes.
- `visit.js` builds the requested cards with the inline confirm/change block, reusing visit.html's 7-day,
  30-minute slot generation; confirming updates the fixture and re-renders.
- `visit.js` groups upcoming visits by day and splits them into a four-card preview and the folded rest; it refreshes
  the header after rendering.
- `index.html` gains the header, moves the profile section below “Mes mandats”, and updates the visit group
  containers; `home.css` / `visit.css` copy the triage and visit-confirmation rules from the served CSS.

## Left to the builder

- Which fixture demonstrates the fallback state (one requested visit gets a time off the 30-minute grid or outside the
  seven days) so both “Confirmer le créneau demandé” and “Choisir un créneau” are visible.
- The re-render strategy after an action (rebuild the lists or patch one card), as long as header, grouping and
  folding stay consistent.

## Verification

- Open `index.html` at 390 px and 1280 px; exercise accept/decline, inline confirm, slot change and folding; the
  counts update and the page still works from `file://`.
- `impeccable detect --scope layout index.html` returns `[]`.
