# Partner Central Matches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the agent, in Partner Central, the listings ndopify-server matched to its signed searches, and let it
send one to the client (propose) or set it aside (dismiss); summarize them on the *Mes mandats* cards.

**Architecture:** A new `ui/match/` feature package in partner-central: `NdMatchClient` (`/v1/matches`),
`MatchRules` (pure wording: tags, "Pourquoi ce bien ?", order, card summaries), `MatchViews` → `MatchView`, and
`MatchController` for the two actions (post-redirect-get back to the mandate page). `MandateController` renders a
*Biens correspondants* section for an APPROVED SEARCH mandate; `HomeController` adds the matches' lines to the cards.
The matched listings come in one `GET /v1/listings?id=…` (a new filter of ndopify-server's listing search).

**Tech Stack:** Kotlin, Spring Boot MVC + Thymeleaf, Spring `RestClient`, mockito-kotlin, kotlin.test, `MockMvc`
standalone, real-Thymeleaf template tests; ndopify-server endpoint tests (MySQL) for the listing filter.

**Spec:** the prototype, `modules/ndopify-partner-central/src/main/html/mandate.html` (comment block, section
`data-matches-section`) and `index.html` (*Mes mandats*, `data-matches` / `data-proposals`), with their logic in
`src/main/html/assets/js/partner-central.js` (`MATCHES` … `renderMatches`); the server's match API as built by
`2026-10-31-search-listing-matching.md` and `2026-10-31-match-proposal.md`.

## Global Constraints

- Partner-central talks to ndopify-server over REST only; no dependency on `ndopify-server`.
- ndopify-server checks match access (`MandateAccessService`); the BFF still checks that a posted match belongs to
  the mandate in the URL (`Match.mandateId`), else `changed`.
- Every page works without JS: the dismiss confirmation opens with `GET /mandates/{id}?dismiss={matchId}`; the JS
  only does it in place.
- UI text in French; dates and amounts through `TenantFormatter` (never "FCFA"); `MandateLabels` for types and prices.
- Never edit or delete anything under `src/main/html/` (the prototype).
- A secondary lookup that fails leaves its lines out (matches, listings): only the mandate itself is required.
- Tests: `kotlin.test` assertions, mockito-kotlin mocks, mirror the main package.
- A `ndopify-dto` change: root `pom.xml` `<ndopify-dto.version>0.0.77-SNAPSHOT</ndopify-dto.version>` while
  developing; released before partner-central / server deploy.
- Branch: `git checkout -b feat/partner-central-matches`.

### Out of scope (no server support; the prototype has them)

> **Update 2026-10-31:** the server now has these, except visits: `SHORTLISTED` + `POST /v1/matches/{id}/shortlist`,
> `POST /v1/matches/propose` (several in one e-mail), `Match.distanceKm`, `Match.missingAmenities` and
> `Match.rejectionReasonCode` (see `modules/ndopify-server/CLAUDE.md`, *match*). Bringing them into this plan's UI is
> still to decide.

- *Retenir* (SHORTLISTED) and "Envoyer les N biens retenus": no such status on the server. Not rendered.
- The client's visit request (day, time, on site / video) and the coded rejection reasons ("trop cher"…): the
  server has ACCEPTED / REJECTED with a free-text `rejectionReason` only, shown as typed.
- The distance of a nearby neighbourhood ("à 1,5 km") and the names of the missing amenities: not on `Match` /
  `ListingSummary`. The fit says "Près du secteur recherché" and "2 équipements sur 3".

## Design

### Which matches

Only for a mandate with `mandateType = SEARCH` and `status = APPROVED` (`MatchRules.matched(mandate)`). One
`GET /v1/matches?mandate-id={id}&status=ACTIVE&status=PROPOSED&status=ACCEPTED&status=REJECTED&status=DISMISSED&limit=100`;
DISMISSED ones are only counted. Their listings in one `GET /v1/listings?id=…&limit=n` (PUBLISHED only, which is all
the server returns matches for); a match whose listing isn't returned is left out.

Order (`MatchRules.ORDER`): ACCEPTED, ACTIVE, PROPOSED, REJECTED; best `score` first within each.

### A match card (`MatchView`)

| Part        | Rule                                                                                                      |
|-------------|-----------------------------------------------------------------------------------------------------------|
| title       | `MandateLabels.shortPropertyType(listing.propertyType)` + " · 3 chambres" ("1 chambre"; no rooms: type only) |
| tag         | ACTIVE "Nouveau" `info`; PROPOSED "Envoyé" `neutral`; ACCEPTED "Intéressé" `success`; REJECTED "Refusé" `neutral` |
| score       | `"${(score * 100).roundToInt()} %"`, then "de correspondance · annonce n° {listingId}" linking `/listings/{listingId}` (new tab) |
| facts       | Quartier: neighbourhood name (else the city's); Loyer: `MandateLabels.price(Money(listing.price, listing.currencyCode, listing.pricePeriodicityType), formatter)`; Disponible: "Maintenant" if `availableFrom` null or ≤ today (tenant zone), else `formatter.date` |
| answer      | PROPOSED: "Envoyé le {date} · le client n'a pas encore répondu."; ACCEPTED: "Envoyé le {date} · le client est intéressé."; REJECTED: "Envoyé le {date} · refusé le {answeredAt}" + " : {rejectionReason}" when set + "."; ACTIVE: none |
| fit         | `MatchRules.fit`, below                                                                                   |
| actions     | ACTIVE only: "Envoyer au client" (`POST /mandates/{mid}/matches/{id}/propose`), "Écarter" (confirmation, then `POST …/dismiss`) |

`MatchRules.fit(match, mandate, listing): List<FitLine>`, `FitLine(full: Boolean, text: String)`, `full` = partial
≥ 0.999, in this order:

1. Neighbourhood: 1 → "Dans le secteur recherché"; > 0 → "Près du secteur recherché"; 0 → "Hors du secteur recherché".
2. Price: 1 → "Dans le budget"; else "{p} % au-dessus du budget", `p = ((listing.price − mandate.price) × 100.0 / mandate.price).roundToInt()`.
3. Rooms: `listing.rooms` null → "Nombre de chambres non précisé"; 1 → "{n} chambre(s), comme demandé" (no rooms
   searched: "{n} chambre(s)"); 0.7 → "{n} chambres, une de plus que demandé"; 0.3 → "{n} chambre(s), une de moins
   que demandé"; 0 → "{n} chambre(s) au lieu de {mandate.rooms}".
4. Amenities, only when `r = Amenity.of(mandate).size > 0`: `have = (amenitiesScore × r).roundToInt()`; `have == r`
   → "Les {r} équipements demandés" ("L'équipement demandé" for 1); else "{have} équipement(s) sur {r}".
5. Availability: 1 → "Disponible avant la date d'emménagement"; 0.5 → "Disponible moins d'un mois après la date
   d'emménagement"; 0 → "Disponible plus d'un mois après la date d'emménagement".
6. Furniture, only when `mandate.furnitureType` is set and not `UNKNOWN` (`MatchRules.FURNITURE_LABELS`:
   UNFURNISHED "Non meublé", FULLY_FURNISHED "Meublé"; `MandateLabels.furniture` is "Oui" / "Non", not usable here):
   1 → "Non meublé, comme demandé"; 0 → "Meublé : le client le veut non meublé" / "Non meublé : le client le veut
   meublé".

### The section (`MatchSectionView`)

`MatchSectionView(matches: List<MatchView>, dismissed: Int, unavailable: Boolean)`. Empty: the prototype's "Aucun de
vos biens ne correspond…". Dismissed > 0: "{n} bien(s) écarté(s) : ils ne vous seront plus proposés pour ce
mandat." Search or listing lookup failing: `unavailable`, "Les biens correspondants ne peuvent pas être affichés pour
le moment."

### Actions (`MatchController`, `/mandates/{mandateId}/matches/{matchId}/propose|dismiss`)

`GET /v1/matches/{id}` first: its `mandateId` must be the path's and its status ACTIVE, else `changed` (no call).
Back to `redirect:/mandates/{mandateId}#matches-title` with flash `match` = outcome and `matchListing` = listing id:

| outcome     | when                               | text (style)                                                                          |
|-------------|------------------------------------|---------------------------------------------------------------------------------------|
| `proposed`  | propose OK                         | "Annonce n° {listingId} envoyée à {contact.email}." ("au client" without the contact) (success) |
| `dismissed` | dismiss OK                         | "Annonce n° {listingId} écartée : elle ne vous sera plus proposée pour ce mandat." (success) |
| `changed`   | 403/404/409, other mandate, not ACTIVE | "Ce bien a changé entre-temps : vérifiez son état avant de recommencer." (warning)   |
| `error`     | other error                        | "Action impossible pour le moment. Réessayez dans quelques instants." (error)          |

A non-numeric `matchId` / `mandateId` → `redirect:/`. `GET /mandates/{id}?dismiss={matchId}` opens that card's
confirmation (only for an ACTIVE match shown on the page; else ignored).

### Home cards

For each APPROVED SEARCH card, from one `GET /v1/matches?mandate-id=…&status=ACTIVE&status=PROPOSED&status=ACCEPTED&status=REJECTED&limit=100`
(skipped without such a card; a failure leaves the lines out):

- `matches`: "3 nouveaux biens correspondants" / "1 nouveau bien correspondant" (ACTIVE count), linking
  `/mandates/{id}#matches-title`; null at 0.
- `proposals`: "3 envoyés · 1 intéresse le client · 1 refusé · 1 sans réponse" (PROPOSED + ACCEPTED + REJECTED sent;
  "2 intéressent le client", "2 refusés"); null when none sent.
- next step, before `MandateRules.nextStep`: an ACCEPTED one → "L'annonce n° {listingId} intéresse le client :
  proposez-lui une visite."; else an ACTIVE one → "Envoyez au client les biens qui lui correspondent."

## Review Focus

1. **A hand-edited match id of another mandate** (or another agent's) posted on this mandate's URL → `changed`, no
   propose / dismiss sent. Test in Task 4 (`propose a match of another mandate`).
2. **Double post / two tabs**: proposing an already PROPOSED match → `changed`, not `error`, no second e-mail. Test in
   Task 4 (`propose a match no longer active`).
3. **Listing of a match unpublished meanwhile** (not returned by the listing search) → card left out, the rest shown.
   Test in Task 3 (`match without its listing`).
4. **Sparse listing / search** (no rooms on a STUDIO, no budget, no furniture asked, no amenity asked, no
   neighbourhood) → a card with fewer fit lines, no exception. Tests in Task 3.
5. **ndopify-server down** for matches or listings → the mandate page still renders, the section says
   "unavailable"; the home cards render without match lines. Tests in Tasks 4 and 5.

---

### Task 1: Listing search by id, and `availableFrom` on the summary (dto + server)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/SearchListingRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ListingSummary.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt` (`search`)
- Modify: the server's listing mapper (`toListingSummary`)
- Modify: root `pom.xml` (`ndopify-dto.version` 0.0.77-SNAPSHOT)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt` (+ its SQL)

**Interfaces:**
- Produces: `SearchListingRequest.ids: List<Long>` (`@param:BindParam("id")`, first field); `ListingSummary.availableFrom: LocalDate? = null` (after `pricePeriodicityType`).

- [x] **Step 1: Write the failing tests** in `SearchListingEndpointTest`: `search by id` — `?id=A&id=B` returns
  exactly those two (PUBLISHED); an id of a DRAFT without `agent-id` is not returned; `availableFrom` is mapped.
- [x] **Step 2: Run** `cd modules/ndopify-dto && mvn install`, then `cd modules/ndopify-server && mvn test -Dtest=SearchListingEndpointTest` — Expected: FAIL.
- [x] **Step 3: Implement** the field, the `id IN` predicate (when not empty) and the mapping.
- [x] **Step 4: Run** the same test — Expected: PASS.
- [x] **Step 5: Commit** `feat(listing): search listings by id, availableFrom on the summary`

---

### Task 2: `NdMatchClient`, `NdListingClient.searchByIds`

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMatchClient.kt`
- Modify: `.../npc/client/ndopify/NdListingClient.kt`
- Test: `.../npc/client/ndopify/NdMatchClientTest.kt`, `NdListingClientTest.kt`

(`...` = `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify` and its `src/test/kotlin` mirror.)

**Interfaces:**
- Produces:
  - `NdMatchClient.search(mandateIds: List<Long>, statuses: List<MatchStatus>, limit: Int = 100): List<Match>` —
    `GET /v1/matches?mandate-id=…&status=…&limit=`
  - `NdMatchClient.get(id: Long): Match`, `propose(id: Long)`, `dismiss(id: Long)` — `POST /v1/matches/{id}/propose|dismiss`, no body
  - `NdListingClient.searchByIds(ids: List<Long>): List<ListingSummary>` — `GET /v1/listings?id=…&limit={ids.size}`; empty list without a call when `ids` is empty

- [ ] **Step 1: Write the failing tests** (style of `NdContractClientTest`, `MockRestServiceServer`): each URL /
  method, the repeated query params, the parsed response; `searchByIds(emptyList())` makes no request.
- [ ] **Step 2: Run** `cd modules/ndopify-partner-central && mvn test -Dtest='NdMatchClientTest,NdListingClientTest'` — Expected: compile FAIL.
- [ ] **Step 3: Implement** like `NdContractClient.search` (KDoc: who may call, the server's access rule).
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(npc): match client`

---

### Task 3: `MatchRules`, `MatchViews`

**Files:**
- Create in `.../npc/ui/match/`: `MatchRules.kt`, `MatchView.kt` (`MatchView`, `FitLine`, `MatchSectionView`), `MatchViews.kt`
- Test: `.../npc/ui/match/MatchRulesTest.kt`, `MatchViewsTest.kt`, fixtures `MatchFixtures.kt`

**Interfaces:**
- Consumes: `Match`, `MatchStatus` (dto); `ListingSummary.availableFrom` (Task 1); `MandateLabels`, `Amenity`,
  `MandateRules.Tag`.
- Produces:
  - `MatchRules.matched(mandate: Mandate): Boolean`; `ORDER: List<MatchStatus>`; `sort(matches: List<Match>): List<Match>`;
    `tag(match: Match, formatter: TenantFormatter): MandateRules.Tag`; `answer(match, formatter): String?`;
    `fit(match: Match, mandate: Mandate, listing: ListingSummary): List<FitLine>`;
    `summary(matches: List<Match>): String?`; `proposals(matches: List<Match>): String?`; `nextStep(matches: List<Match>): String?`
  - `data class MatchView(id: Long, listingId: Long, title: String, tag: MandateRules.Tag, score: String, listingUrl: String, facts: List<Pair<String, String>>, answer: String?, fit: List<FitLine>, actionable: Boolean)`
  - `MatchViews.section(mandate: Mandate, matches: List<Match>?, listings: Map<Long, ListingSummary>?, locations: Map<Long, Location>, today: LocalDate, formatter: TenantFormatter): MatchSectionView` — `null` matches or listings → `unavailable`

Fixtures: the matching plan's worked example (mandate RENTAL APARTMENT, rooms "3", 250 000 MONTHLY, wants
`generatorSet`, `securedFenced`, `waterTank`, `WITHIN_3_MONTHS`, `UNFURNISHED`; neighbourhoods 1001 Bonapriso,
1002 Bonanjo) with matches A (0.95), C (0.745), B (0.58) and their partials.

- [ ] **Step 1: Write the failing tests**
  - `MatchRulesTest.fit`: A → 6 lines, texts "Dans le secteur recherché", "Dans le budget", "3 chambres, comme
    demandé", "2 équipements sur 3" (not full), "Disponible avant la date d'emménagement", "Non meublé, comme
    demandé"; B → "Près du secteur recherché", "6 % au-dessus du budget", "4 chambres, une de plus que demandé",
    "Les 3 équipements demandés"; C → "2 chambres, une de moins que demandé", "1 équipement sur 3", "Disponible moins
    d'un mois après la date d'emménagement"
  - sparse: STUDIO without rooms → "Nombre de chambres non précisé"; no amenity asked → no amenities line; no
    furniture asked → no furniture line; `rooms = null` searched → "3 chambres"
  - `tag` / `answer` for each status, REJECTED with and without a reason; `sort`: ACCEPTED, ACTIVE (0.95 before 0.58),
    PROPOSED, REJECTED
  - `summary` ("3 nouveaux biens correspondants", "1 nouveau bien correspondant", null), `proposals`
    ("3 envoyés · 1 intéresse le client · 1 refusé · 1 sans réponse", null), `nextStep` (ACCEPTED wins over ACTIVE; null)
  - `MatchViewsTest`: title "Appartement · 3 chambres", score "95 %", `listingUrl` "/listings/3012", facts Quartier /
    Loyer (through `TenantFormatter`) / Disponible ("Maintenant" for a past date); `match without its listing` → left
    out; `dismissed` counted, not listed; `null` listings → `unavailable`; `actionable` only for ACTIVE
- [ ] **Step 2: Run** `mvn test -Dtest='MatchRulesTest,MatchViewsTest'` — Expected: compile FAIL.
- [ ] **Step 3: Implement** per *Design*. KDoc on `MatchRules`: what is out of scope (shortlist, visits) and why.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(npc): match rules and views`

---

### Task 4: *Biens correspondants* on the mandate page, propose and dismiss

**Files:**
- Create: `.../npc/ui/match/MatchController.kt`
- Modify: `.../npc/ui/mandate/MandateController.kt` (inject `NdMatchClient`; `show` takes `dismiss: String?`;
  `render` adds `matchSection`, `dismissing`, and the `match` flash's `matchOutcomeText` / `matchOutcomeStyle`)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html` — the section after the status
  panels, before `contact-title`, markup of the prototype's `data-matches-section` and `data-match-template`
  rendered server-side (`th:each` over `matchSection.matches`); the dismiss confirmation is a `<form>` shown when
  `dismissing == match.id`, "Écarter" a link to `?dismiss={id}#match-{id}-title`
- Test: `.../npc/ui/match/MatchControllerTest.kt`, `.../npc/ui/mandate/MandateControllerTest.kt`, `MandateTemplateTest.kt`

**Interfaces:**
- Consumes: Tasks 2 and 3.
- Produces: `MatchController.propose(mandateId: String, matchId: String, redirect): String`,
  `dismiss(...)`; constants `OUTCOME = "match"`, `LISTING = "matchListing"`, `PROPOSED = "proposed"`,
  `DISMISSED = "dismissed"`, `CHANGED = "changed"`, `FAILED = "error"`. The outcome texts are worded in
  `MandateController` (it has the contact's e-mail), per *Design / Actions*.

- [ ] **Step 1: Write the failing tests**
  - `MatchControllerTest`: `propose` → `matchClient.propose(9001)`, redirect `/mandates/204#matches-title`, flash
    `proposed` + listing 3012; `dismiss` likewise; `propose a match of another mandate` (`Match.mandateId = 205`) →
    `changed`, `propose` never called; `propose a match no longer active` (PROPOSED) → `changed`, never called;
    server 409 / 403 / 404 → `changed`; 500 → `error`; `abc` as id → `redirect:/`
  - `MandateControllerTest`: an APPROVED SEARCH mandate → `matchSection` with the views, `search` called with the 5
    statuses; a LISTING or ASSIGNED mandate → no `matchSection`, `matchClient` never called; match search throws →
    `matchSection.unavailable`, the page still renders; `?dismiss=9001` → `dismissing = 9001`, `?dismiss=9999` → null;
    flash `proposed` → "Annonce n° 3012 envoyée à jean@example.com."
  - `MandateTemplateTest`: the cards in order with tag, score, link `/listings/3012`, fit; "Envoyer au client" form
    posts to `/mandates/204/matches/9001/propose`; no actions on a PROPOSED card; `?dismiss=` shows the confirmation
    form posting `…/dismiss`; empty and unavailable texts; dismissed note
- [ ] **Step 2: Run** `mvn test -Dtest='MatchControllerTest,MandateControllerTest,MandateTemplateTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** the controller (same `act` / `errorCode` pattern as `MandateController`), the model
  attributes and the template section.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(npc): matches on the mandate page`

---

### Task 5: Matches on the *Mes mandats* cards

**Files:**
- Modify: `.../npc/ui/home/HomeController.kt` (one match search for the APPROVED SEARCH cards)
- Modify: `.../npc/ui/mandate/MandateCards.kt`, `MandateCardView.kt` (`matches: String?`, `matchesUrl: String?`,
  `proposals: String?`; next step from `MatchRules.nextStep` first)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html` (the prototype's
  `mandate-card__matches` and `mandate-card__proposals` lines)
- Test: `HomeControllerTest.kt`, `MandateCardsTest.kt`, `HomeTemplateTest.kt`

**Interfaces:**
- Consumes: `NdMatchClient.search`, `MatchRules.summary/proposals/nextStep`.
- Produces: `MandateCards.of(..., matches: Map<Long, List<Match>>?)` — the mandate's matches, null when unavailable.

- [ ] **Step 1: Write the failing tests**
  - `HomeControllerTest`: two APPROVED SEARCH cards and one ASSIGNED → one `search` with the two ids and the 4
    statuses; no APPROVED SEARCH card → never called; `search` throws → cards still there, no match lines
  - `MandateCardsTest`: 3 ACTIVE → `matches` "3 nouveaux biens correspondants", `matchesUrl`
    "/mandates/204#matches-title"; an ACCEPTED → next step "L'annonce n° 2987 intéresse le client : proposez-lui
    une visite."; no match → the old next step
  - `HomeTemplateTest`: the two lines render, absent when null
- [ ] **Step 2: Run** `mvn test -Dtest='HomeControllerTest,MandateCardsTest,HomeTemplateTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(npc): matches on the mandate cards`

---

### Task 6: Script, styles, docs

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js` — "Écarter" opens the
  confirmation in place (`preventDefault` on the link), "Annuler" closes it, focus as the prototype; no double post
  on "Envoyer au client" ("Envoi…")
- Modify: `.../public/assets/css/home.css` — port `.matches__head`, `.match__score`, `.match__answer`, `.match__fit`,
  `.match__fit-item(--full)`, `.matches__dismissed`, `.mandate-card__matches`, `.mandate-card__proposals` from
  `src/main/html/assets/css/home.css`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` — `match` in the feature list, and a *Pages* entry: the
  section, the actions and outcomes, the cards' lines, what's out of scope; point to this plan
- Modify: `modules/ndopify-partner-central/src/main/html/` — **nothing**

- [ ] **Step 1: Implement** the script, styles and docs.
- [ ] **Step 2: Check by hand** (`mvn spring-boot:run` on server and partner-central, an APPROVED SEARCH with a
  match from `POST /v1/matches/run?mandate-id=`): with and without JS, propose and dismiss come back with their
  message.
- [ ] **Step 3: Run** `cd modules/ndopify-partner-central && mvn verify` — Expected: BUILD SUCCESS (ktlint, tests, JaCoCo).
- [ ] **Step 4: Commit** `feat(npc): match script, styles and docs`

---

### After merge

Push to `main` releases `ndopify-dto` 0.0.77; set `<ndopify-dto.version>` to it before ndopify-server and
partner-central deploy.
