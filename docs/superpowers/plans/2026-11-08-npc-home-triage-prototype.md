# NPC Home Triage Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the static prototype home page (`src/main/html/index.html` and its assets) in line with the triage-first Espace agent home the BFF now serves on `npc-home-triage`.

**Architecture:** The prototype stays a `file://`-safe static page: fixture markup in `index.html`, simulation in `partner-central.js` (header, offers) and `visit.js` (visits), styles in `home.css` / `visit.css`. The BFF's server-computed values become fixture/JS-computed values, recomputed after every simulated action.

**Tech Stack:** Static HTML, vanilla ES5 JavaScript (IIFE, no modules), CSS custom properties from `partner-central.css`.

**Spec:** `docs/superpowers/specs/2026-11-07-npc-home-triage-prototype-design.md`

## Global Constraints

- Work on `main` in `/Users/herve/Projects/ndopify-mono`; touch only `modules/ndopify-partner-central/src/main/html/` and this plan/spec.
- Do not change the BFF (`src/main/resources`, `src/main/kotlin`) or the served tests.
- French UI copy, English code and comments.
- Reuse the existing design tokens; do not add colours, fonts or spacing values.
- The page must keep working from `file://` with no build step, and without a network.
- Existing prototype switches (`?assignments=empty`, `?visits=empty`) and session persistence must keep working.
- No JS test harness exists for `src/main/html`; this is a throwaway prototype. Verify each task in a browser against the exact expected values below, and run the Impeccable layout detector on `index.html` at the end.

## Review Focus

1. **Counts after an action:** accepting, declining or confirming must update the header immediately, and a reload must show the same counts (session persistence). Verify at the end of tasks 2 and 3.
2. **Re-render safety:** the visit lists are rebuilt after a confirmation; listeners must bind only to the fresh nodes (no double submit, no duplicate cards). Verify in task 3/4.
3. **Invalid requested slot:** a request outside the 7-day/30-minute lists must open “Choisir un créneau” with a valid selection, never a dead confirm button. Verify in task 3.
4. **Folding boundary:** exactly four upcoming cards stay visible; the rest are inside “Voir les N autres visites”, and no day group is duplicated or lost. Verify in task 4.
5. **No-JS and empty states:** the static requested list remains a readable fallback, `?visits=empty` shows the empty line, and `?assignments=empty` hides the offers section. Verify in task 5.

---

### Task 1: Triage header and profile move

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Produces: `window.NpcHome = { updateIntro: updateHomeIntro }`, where `updateHomeIntro()` has no arguments and is safe on any page (returns when `[data-home-date]` is absent).
- Produces DOM hooks: `[data-home-date]`, `[data-home-counts]`, `[data-home-count="offers"|"requested"|"today"]`, `[data-home-count-value]`, `[data-home-count-label]`, `[data-home-urgent]`.
- Consumes (guarded, may be absent until Task 3): `window.NpcVisits.homeCounts()` returning `{ requested: Number, today: Number }`.

- [ ] **Step 1: Replace the hidden title with the triage header**

In `index.html`, replace `<h1 class="visually-hidden">Espace agent</h1>` with:

```html
<header class="home-intro">
    <h1 class="home-intro__title">Espace agent</h1>
    <p class="home-intro__date" data-home-date></p>
    <ul class="home-intro__counts" data-home-counts hidden>
        <li data-home-count="offers" hidden>
            <a href="#assignments-title"><strong data-home-count-value></strong>
                <span data-home-count-label></span>
                <span class="home-intro__urgent" data-home-urgent hidden></span></a>
        </li>
        <li data-home-count="requested" hidden>
            <a href="#visits-title"><strong data-home-count-value></strong>
                <span data-home-count-label></span></a>
        </li>
        <li data-home-count="today" hidden>
            <a href="#visits-title"><strong data-home-count-value></strong>
                <span data-home-count-label></span></a>
        </li>
    </ul>
</header>
```

- [ ] **Step 2: Move the profile nudge below the queue**

Cut the whole `<section class="home-section" aria-labelledby="profile-title" data-profile>…</section>` block from the top of the column and paste it after the “Mes mandats” section, just before the closing `</div>` of `.column`.

- [ ] **Step 3: Implement `updateHomeIntro()` in `partner-central.js`**

Next to `initAgentHome()`, add:

```js
function updateHomeIntro() {
    var date = $("[data-home-date]");
    if (!date) return;
    date.textContent = new Intl.DateTimeFormat("fr-FR", {
        weekday: "long", day: "numeric", month: "long"
    }).format(new Date());
    var list = $("[data-assignments]");
    var offers = list ? $all("[data-assignment]:not([data-closed])", list) : [];
    var urgent = offers.filter(function (item) {
        return item.hasAttribute("data-urgent");
    }).length;
    var visits = window.NpcVisits && window.NpcVisits.homeCounts
        ? window.NpcVisits.homeCounts() : { requested: 0, today: 0 };
    setHomeCount("offers", offers.length, "mandat à traiter", "mandats à traiter", urgent);
    setHomeCount("requested", visits.requested, "visite à confirmer", "visites à confirmer", 0);
    setHomeCount("today", visits.today, "visite aujourd'hui", "visites aujourd'hui", 0);
    $("[data-home-counts]").hidden = offers.length + visits.requested + visits.today === 0;
}

function setHomeCount(name, count, one, many, urgent) {
    var item = $('[data-home-count="' + name + '"]');
    if (!item) return;
    item.hidden = count === 0;
    $("[data-home-count-value]", item).textContent = count;
    $("[data-home-count-label]", item).textContent = count > 1 ? many : one;
    var tag = $("[data-home-urgent]", item);
    tag.hidden = urgent === 0;
    tag.textContent = urgent > 0 ? "· " + urgent + " expire bientôt" : "";
}
```

- [ ] **Step 4: Expose it and call it on load**

After the two functions, add `window.NpcHome = { updateIntro: updateHomeIntro };`. In `initAgentHome()`, add `updateHomeIntro();` after `initAssignments(initMandates());`.

- [ ] **Step 5: Add the triage styles**

In `home.css`, add the rules from the served diff (same tokens): `.home-intro`, `.home-intro__title`, `.home-intro__date`, `.home-intro__counts`, `.home-intro__counts a`, `:hover`, `strong`, `.home-intro__urgent`, `[data-page="agent-home"] .home-section > h2`, `[data-page="agent-home"] .assignment__timer`, and `[data-page="agent-home"] .assignment[data-urgent] .assignment__timer`.

- [ ] **Step 6: Verify in the browser**

Open `index.html` and a subpage (e.g. `mandate.html?id=204`). Expected:
- Home: visible “Espace agent”, today's date in French (e.g. “dimanche 8 novembre”), one offers chip “3 mandats à traiter · 1 expire bientôt”, no crashes on the other pages.
- The profile nudge sits after “Mes mandats”.
- Console has no errors.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/index.html \
        modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "feat(prototype): add the agent home triage header"
```

---

### Task 2: Offer order, urgency and one primary accept

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Consumes: `window.NpcHome.updateIntro()` from Task 1.
- Produces: offers are DOM-ordered by `data-expires-in` ascending; `data-urgent` is set while under 15 minutes; only the first open offer's `[data-accept]` carries `btn--primary`.

- [ ] **Step 1: Shorten the section intro**

In `index.html`, replace the long assignments intro paragraph with: `Mandat exclusif : acceptez ou refusez avant la fin du délai.`

- [ ] **Step 2: Sort the offer cards before wiring them**

At the start of `initAssignments`, before the `$all("[data-assignment]", list).forEach(...)` block:

```js
$all("[data-assignment]", list).slice().sort(function (a, b) {
    return (parseFloat(a.getAttribute("data-expires-in")) || 0) -
        (parseFloat(b.getAttribute("data-expires-in")) || 0);
}).forEach(function (item) {
    list.appendChild(item);
});
```

- [ ] **Step 3: Give only the first open offer a primary accept**

Add above `syncEmpty()`:

```js
function syncPrimary() {
    pending().forEach(function (item, index) {
        var accept = $("[data-accept]", item);
        if (accept) accept.className = "btn " + (index === 0 ? "btn--primary" : "btn--secondary");
    });
}
```

Call `syncPrimary();` at the end of `syncEmpty()`, and after `remove(item)` / `close(...)` in the accept, decline and timeout paths.

- [ ] **Step 4: Refresh the header from the timer loop**

At the end of `tick()`, call `updateHomeIntro();` (urgency and expiry change the counts). Call it once after the initial `tick();` too.

- [ ] **Step 5: Style the urgent deadline**

Confirm `home.css` has `[data-page="agent-home"] .assignment[data-urgent] .assignment__timer { font-weight: 700; }` from Task 1; if the prototype already styles `[data-urgent]`, keep the served rule on top.

- [ ] **Step 6: Verify in the browser**

Expected on `index.html`:
- Offer order is “Propriétaire” (9 min), then 38 min, then 52 min.
- “Propriétaire” has the urgent treatment and the only primary “Accepter”; the other two accepts are secondary.
- Accepting it closes that card, makes the next accept primary, and the header offers chip drops to “2 mandats à traiter” (and loses the urgent mention).
- Declining an offer does the same; `?assignments=empty` still hides the section.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/index.html \
        modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "feat(prototype): order offers by expiry and keep one primary accept"
```

---

### Task 3: Visit counts and inline confirmation

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Consumes: `window.NpcHome.updateIntro()` from Task 1.
- Produces: `window.NpcVisits.homeCounts()` returning `{ requested, today }`; `renderHomeVisits(section)` rebuilds the visit section; `fillHomeSlots(form, selectedAt)` fills a card's day/time selects and returns nothing; `showVisitOutcome(title, text)` fills and shows `[data-visit-outcome]`; a confirmation persists through `saveVisit(id, { status: "CONFIRMED", scheduledAt, scheduledOn })`.

- [ ] **Step 1: Expose the home counts**

Add to `window.NpcVisits`:

```js
homeCounts: function () {
    var now = Date.now();
    var open = allVisits().filter(function (v) {
        return OPEN.indexOf(v.status) !== -1;
    });
    var requested = open.filter(function (v) {
        return v.status === "REQUESTED" && expiresAt(v) > now;
    }).length;
    var today = open.filter(function (v) {
        return v.status === "CHECKED_IN" ||
            (v.status === "CONFIRMED" && sameDay(v.scheduledAt, now));
    }).length;
    return { requested: requested, today: today };
}
```

- [ ] **Step 2: Add the requested-card actions to the template**

In the `[data-visit-card-template]` article, after the timer paragraph, append a hidden block:

```html
<div class="visit-card__actions" data-visit-actions hidden>
    <form class="visit-card__confirm" data-home-confirm>
        <button type="submit" class="btn btn--primary btn--block" data-confirm-visit>Confirmer le créneau demandé</button>
        <p class="visit-card__confirm-hint">Le client reçoit la date, l'adresse et son code.</p>
    </form>
    <details class="visit-card__change">
        <summary data-visit-change-label>Changer le créneau</summary>
        <form class="visit-card__change-form" data-home-change>
            <div class="visit-slot">
                <div class="field">
                    <label class="field__label">Jour</label>
                    <select class="input select" name="date"></select>
                </div>
                <div class="field">
                    <label class="field__label">Heure</label>
                    <select class="input select" name="time"></select>
                </div>
            </div>
            <button type="submit" class="btn btn--secondary">Confirmer ce créneau</button>
        </form>
    </details>
</div>
```

- [ ] **Step 3: Add the outcome alert to the visits section**

After the `<h2>Visites</h2>`, add:

```html
<div class="alert alert--success" role="status" hidden data-visit-outcome>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="m6.5 10.5 2.5 2.5 4.5-5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
    <div>
        <p class="alert__title" data-visit-outcome-title></p>
        <p data-visit-outcome-text></p>
    </div>
</div>
```

- [ ] **Step 4: Refactor `initHomeVisits` into a re-runnable renderer**

Keep `initHomeVisits()` as the entry point, but move its body into `renderHomeVisits(section)` so a confirmation can call it again. In `renderHomeVisits`, for each requested card:
- Show `[data-visit-actions]`.
- If the client's requested day/time is in the 7-day/30-minute lists, show `[data-home-confirm]`, hide the details (`open` false), and keep “Changer le créneau”.
- Otherwise hide `[data-home-confirm]`, set the summary to “Choisir un créneau”, and set `details.open = true`.
- Fill both selects with the same slot lists as `visit.html` (`fillHomeSlots(form, selected)`), preselecting the requested slot when valid, else the first day and time.
- Bind submit on the fresh forms: compute the slot, call `saveVisit(v.id, { status: "CONFIRMED", scheduledAt: slot, scheduledOn: Date.now() })`, show the outcome alert (“Visite confirmée” / “Le client a reçu la date, l'adresse et son code.”), then `renderHomeVisits(section)` and `window.NpcHome.updateIntro()`.

- [ ] **Step 5: Demonstrate both requested states**

In `visit.js`'s `VISITS`, change visit `7002`'s `requestedAt` to `at(3, "16:10")` (off the 30-minute grid) so it shows “Choisir un créneau”, while `7001` keeps `at(3, "09:00")` and shows “Confirmer le créneau demandé”.

- [ ] **Step 6: Add the confirmation styles**

Copy the served rules into `home.css`: `.visit-card__actions`, `.visit-card__confirm`, `.visit-card__change-form`, their `.btn` width, `.visit-card__confirm-hint`, `.visit-card__change summary`, `.visit-card__change[open] summary`, `.visit-card__change .visit-slot`, and `[data-visit-outcome]`.

- [ ] **Step 7: Verify in the browser**

Expected:
- Requested cards: 7002 shows an open “Choisir un créneau” with a valid day/time preselected; 7001 shows “Confirmer le créneau demandé” and the closed “Changer le créneau”.
- Confirming 7001 moves it out of “À confirmer” into the upcoming list, shows the “Visite confirmée” alert, and the header requested chip decrements.
- Reloading keeps the confirmed state and the counts.
- Two rapid submits create one confirmed visit (no duplicate cards).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/index.html \
        modules/ndopify-partner-central/src/main/html/assets/js/visit.js \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "feat(prototype): confirm a requested visit inline from the home queue"
```

---

### Task 4: Ongoing visits, day groups and folding

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Consumes: `renderHomeVisits(section)` from Task 3.
- Produces: `groupByDay(visits)` returning `[{ label, visits }]`; `splitPreview(groups, 4)` returning `{ preview, more, moreCount }`.

- [ ] **Step 1: Replace the single upcoming group with the new containers**

In `index.html`, replace `<div class="visit-group" data-visit-group="upcoming">…</div>` with:

```html
<div class="visit-group" data-visit-group="ongoing" hidden>
    <h3 class="visit-group__title">En cours</h3>
    <ul class="assignments visit-cards" data-visit-list></ul>
</div>
<div data-visit-groups></div>
<details class="visit-more" data-visit-more hidden>
    <summary data-visit-more-label></summary>
    <div data-visit-more-groups></div>
</details>
```

- [ ] **Step 2: Render ongoing and grouped upcoming visits**

In `renderHomeVisits`, after the requested list:
- `ongoing` = upcoming visits whose status is `CHECKED_IN`.
- `upcoming` = the others, sorted by `scheduledAt`.
- `groupByDay(upcoming)` labels each consecutive day: `Aujourd'hui`, `Demain`, else `new Intl.DateTimeFormat("fr-FR", { weekday: "short", day: "numeric", month: "long" }).format(date)`.
- Render ongoing into `[data-visit-group="ongoing"]`.
- `splitPreview(groups, 4)`: fill `[data-visit-groups]` with the preview groups and `[data-visit-more-groups]` with the rest, splitting a group without losing cards.
- Show `[data-visit-more]` only when `moreCount > 0`; its summary reads `Voir les ${moreCount} autres visites`.
- Hide each empty group; the empty line shows when requested + upcoming are both empty and the section isn't unavailable.

- [ ] **Step 3: Add group and folding styles**

Copy from the served CSS: `.visit-more`, `.visit-more > summary`, `.visit-more > .visit-group`, and `[data-page="agent-home"] .visit-group__title`; keep `.visit-group` spacing already present in `visit.css`.

- [ ] **Step 4: Verify in the browser**

Expected:
- “En cours” shows first (Grace Nkeng), then upcoming groups by day.
- At most four upcoming cards are visible; the rest are behind “Voir les N autres visites”, which reveals them without duplicates.
- A confirmation from Task 3 lands in the correct day group and can push the fifth card into the fold.
- `?visits=empty` shows the empty line and no heading.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/index.html \
        modules/ndopify-partner-central/src/main/html/assets/js/visit.js \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "feat(prototype): group and fold the home upcoming visits"
```

---

### Task 5: Final verification and polish

**Files:**
- Modify (only if a check fails): the prototype files from Tasks 1–4.

- [ ] **Step 1: Run the layout detector**

Run: `/Users/herve/.config/opencode/skills/impeccable/scripts/impeccable detect --json --scope layout modules/ndopify-partner-central/src/main/html/index.html`
Expected: `[]`, exit 0.

- [ ] **Step 2: Walk the page at 390 px and 1280 px**

Check: header and chips wrap without overflow; the queue order reads offers → visits → mandates → profile; offers, visits and the folded list are all reachable; every action posts nothing to the network and works from `file://`.

- [ ] **Step 3: Commit any fixes**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "fix(prototype): polish the home triage queue"
```

---

## Execution Handoff

Native execution in this session: implement the tasks on `main`, then run the layout detector and walk the page.
