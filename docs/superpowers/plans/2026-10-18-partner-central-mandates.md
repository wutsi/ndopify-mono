# Espace agent — Mes mandats, page mandat, modification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the prototype's three mandate screens to the served Partner Central: the *Mes mandats* section of the
Espace agent (`/`), the mandate page (`/mandates/{id}`: status tracker, client contact, criteria, send for approval,
close, cancel) and the edit page (`/mandates/{id}/edit`). Every screen works without JS; JS only enhances.

**Architecture:** BFF only, no ndopify-server change: every endpoint it needs already exists (`GET /v1/mandates`,
`GET|POST /v1/mandates/{id}`, `POST .../request-approval|close|cancel`, `GET /v1/contracts/{id}`,
`GET /v1/customers/{id}`, `GET /v1/assignments`). A new `ui/mandate/` package holds `MandateController` (page,
actions, edit), its view builders (`MandateViews`, `MandateCards`) and its rules (`MandateRules`, `MandateEditRules`);
`HomeController` gains the *Mes mandats* list. Actions are post-redirect-get with a flash outcome, like
`AssignmentController`. The close / cancel form opens without JS through `GET /mandates/{id}?end=close|cancel`, like
the offers' `?decline=`.

**Tech Stack:** Kotlin, Spring Boot MVC + Thymeleaf, Spring `RestClient`, JUnit 5 + mockito-kotlin + `kotlin.test`,
standalone `MockMvc`, `MockRestServiceServer`, Jsoup template tests, plain ES5 JS, slim-select (already loaded by the
profile page).

**Spec:** the prototype, which is the design (the user opted out of further questions; security is out of scope):
- `modules/ndopify-partner-central/src/main/html/index.html` — section *Mes mandats* (`#mandates-title`) and its
  header comment.
- `modules/ndopify-partner-central/src/main/html/mandate.html` — the mandate page; its header comment lists the
  actions per status.
- `modules/ndopify-partner-central/src/main/html/mandate/edit.html` — the edit page; its header comment lists what
  is and isn't editable.
- `src/main/html/assets/js/partner-central.js` — `titleOf`, `criteriaOf`, `STATUS_TAGS`, `CANCELLATION_LABELS`,
  `partyWords`, `nextStep` (≈ lines 1300–1500), `initMandate` (≈ 1581–1900), `initMandateEdit` (≈ 1903–end): **the
  source of every French string below that the plan doesn't quote**.
- `src/main/html/assets/css/home.css` (662 lines vs 248 served) and `partner-central.css` (917 vs 907): the mandate
  styles to carry over.

## Global Constraints

- Module `modules/ndopify-partner-central`, package `com.wutsi.ndopify.npc`. REST only to ndopify-server, DTOs from
  `ndopify-dto`. No change to `ndopify-server` or `ndopify-dto`.
- **Never modify or delete anything under `src/main/html/`.** Copy from it.
- UI copy is French, copied verbatim from the prototype (HTML or JS) wherever it exists.
- **Security is out of scope (user's call, resolved later):** no check that the mandate belongs to the signed-in
  agent, in the BFF or the server. Don't add one; don't remove the agent id from `CurrentAgent` either (the list is
  still the agent's own: `GET /v1/mandates?agent-id={currentAgent.id}`).
- Agent id comes from `CurrentAgent`; the mandate id from the URL path. `currentAgent.get() == null` →
  `redirect:/logout` on every route, as `HomeController.SIGN_OUT`.
- Statuses and their tags, exactly the prototype's `STATUS_TAGS`: `ASSIGNED` "Assigné" `info`,
  `PENDING_APPROVAL` "En approbation" `warning`, `APPROVED` "Actif" `success`, `CLOSED` "Fermé" `neutral`,
  `CANCELLED` "Annulé" `neutral`. Active (listed on `/`): ASSIGNED, PENDING_APPROVAL, APPROVED. Editable: ASSIGNED,
  PENDING_APPROVAL. Shown on the mandate page: the five above; any other status (PENDING_ASSIGNMENT,
  MANUAL_ASSIGNMENT, UNKNOWN) or a 404 → the locked view ("Ce mandat n'est pas disponible").
- Party wording follows `mandateType`: `LISTING` = landlord ("Bailleur", "le bailleur", "Son bien", "Loyer",
  "Disponibilité", no "Indifférent" choices); anything else = client ("Locataire", "le client", "Sa recherche",
  "Budget", "Emménagement").
- Dates: `d MMMM yyyy` in French (`"22 octobre 2026"`), in the tenant's `timeZone` (`TenantContext.getTenant()`).
- Signature TTL: new property `ndopify.contract.signature.ttl-days: 7` in partner-central's `application.yml` (same
  value as ndopify-server's `ndopify.contract.signature.ttl-days`), injected where needed.
- Assertions: `kotlin.test` only. Mock with mockito-kotlin (`com.nhaarman.mockitokotlin2`), as the existing tests.
- Gate at the end of every task: `cd modules/ndopify-partner-central && mvn verify` (ktlint, tests, JaCoCo with
  `jacoco.threshold.class` 0.98). Format with `mvn antrun:run@ktlint-format`.

## Calls made without the user (check these while reading)

1. **"Envoyé le" and "Modifié depuis l'envoi" are derived**, since no DTO carries the request time:
   `requestedAt = contract.signatureDeadline − ttl-days`; edited since = `mandate.modifiedAt > requestedAt`. Correct
   because `requestApproval` saves the mandate before `requestSignature` sets the deadline. Breaks silently if the
   two TTL settings drift: the comment on the property says so.
2. **"accepté le"** = `modifiedAt` of the mandate's `ACCEPTED` assignment (`GET /v1/assignments?mandate-id=&status=ACCEPTED`);
   left out when there isn't one (ex: assigned by hand).
3. **"Mandat n°"** is the mandate id, as in the prototype (not the contract number).
4. **"valable jusqu'au"** is the contract's `validUntil`; the card on `/` and the APPROVED panel leave the clause out
   when the contract can't be loaded.
5. **Neighbourhoods on the edit page** use the profile page's pattern: a `<select multiple>` holding the chosen ones,
   which `join.js`-style slim-select enhances with a search over `/locations` restricted to the mandate's city.
   Without JS, neighbourhoods can only be kept or removed, not added (same limit as the profile page).
6. **"Simuler la signature (prototype)"** is not ported: signing happens on the contract page.
7. Each card on `/` costs one `GET /v1/mandates/{id}` (the summary has no `movingPeriod`) plus one
   `GET /v1/contracts/{id}` for PENDING_APPROVAL / APPROVED; the list is capped at 20 (the search's default limit).

## Review Focus

1. **A PENDING_APPROVAL mandate past its `signatureDeadline`** (the contract expiry job runs daily): the card says
   "Délai de signature dépassé : renvoyez le mandat." and the panel's deadline reads "… (dépassé)"; "Renvoyer"
   still works (the server re-creates an expired contract). → `MandateRulesTest.next step - deadline passed`,
   `MandateViewsTest.pending approval - deadline passed`.
2. **The mandate was approved, closed or cancelled since the page loaded** (customer signed meanwhile, other tab):
   request-approval → 409 `MANDATE_CANNOT_BE_APPROVED`, close → 409 `MANDATE_NOT_APPROVED` / `MANDATE_ALREADY_CLOSED`,
   cancel → 409 `MANDATE_ALREADY_CLOSED`, a stale edit form POST on an APPROVED mandate (the server's update doesn't
   check status): each redirects to the page with the "changed" outcome, never an error page, and the edit is
   **not** sent. → `MandateControllerTest.request approval - conflict`, `close - conflict`,
   `save - approved since`.
3. **Contract, customer or assignment lookup fails** on the mandate page: the page still renders without the
   affected lines (no dates, contact "indisponible"); only the mandate itself failing gives the locked view. →
   `MandateControllerTest.show - contract unavailable`, `show - customer unavailable`.
4. **Close with a malformed amount** ("150.000", "abc", "0", empty) or **cancel without a reason code**: the page
   re-renders with the form open (`end` = close / cancel), the typed value kept, and the field error from the
   prototype; nothing is posted. → `MandateControllerTest.close - invalid amount`, `cancel - no code`.
5. **Edit with follow-ups that no longer apply** (type changed from APARTMENT to LAND with rooms still posted, a
   landlord posting `ANY`): rooms / furniture / parkings that don't apply are sent as `null`; `ANY` is an error for a
   landlord and `null` for a client. → `MandateEditRulesTest.request - drops follow-ups that don't apply`,
   `errors - landlord can't answer any`.

Known, out of scope: no ownership check (above); CSRF is off app-wide, as for the other forms.

---

## File Structure

| File | Responsibility |
|---|---|
| `client/ndopify/NdMandateClient.kt` (modify) | + `active(agentId)`, `update`, `requestApproval`, `close`, `cancel` |
| `client/ndopify/NdContractClient.kt` (create) | `get(id)` |
| `client/ndopify/NdCustomerClient.kt` (create) | `get(id)` |
| `client/ndopify/NdAssignmentClient.kt` (modify) | + `accepted(mandateId)` |
| `ui/mandate/MandateLabels.kt` (modify) | + party / short type labels, full criteria rows |
| `ui/mandate/MandateRules.kt` (create) | statuses, tags, party words, dates, next step, requested-at / edited-since |
| `ui/mandate/MandateCardView.kt`, `MandateCards.kt` (create) | a *Mes mandats* card and its builder |
| `ui/mandate/MandateView.kt`, `MandateViews.kt` (create) | the mandate page's model and its builder |
| `ui/mandate/MandateEndForm.kt` (create) | close / cancel form |
| `ui/mandate/MandateEditForm.kt`, `MandateEditRules.kt` (create) | edit form, its validation and `UpdateMandateRequest` |
| `ui/mandate/MandateController.kt` (create) | `/mandates/{id}` routes |
| `ui/home/HomeController.kt` (modify) | + `mandates` model attribute |
| `templates/index.html` (modify) | + *Mes mandats* section |
| `templates/mandate.html`, `templates/mandate/edit.html` (create) | the two pages |
| `public/assets/css/home.css`, `partner-central.css` (modify) | mandate styles from the prototype |
| `public/assets/js/mandate.js` (create) | in-place end form, edit page enhancements |
| `resources/application.yml` (modify) | `ndopify.contract.signature.ttl-days` |
| `CLAUDE.md` (modify) | the `ndopify-partner-central` mandate paragraph |

Paths under `ui/`, `client/`: `src/main/kotlin/com/wutsi/ndopify/npc/`; tests mirror them under `src/test/kotlin`.

---

### Task 1: REST clients

**Files:**
- Modify: `client/ndopify/NdMandateClient.kt`, `client/ndopify/NdAssignmentClient.kt`
- Create: `client/ndopify/NdContractClient.kt`, `client/ndopify/NdCustomerClient.kt`
- Test: `NdMandateClientTest.kt`, `NdAssignmentClientTest.kt` (modify), `NdContractClientTest.kt`,
  `NdCustomerClientTest.kt` (create)

**Interfaces:**
- Produces:
  - `NdMandateClient.active(agentId: Long): List<MandateSummary>` → `GET /v1/mandates?agent-id={agentId}&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED`
  - `NdMandateClient.update(id: Long, request: UpdateMandateRequest)` → `POST /v1/mandates/{id}`
  - `NdMandateClient.requestApproval(id: Long)` → `POST /v1/mandates/{id}/request-approval`
  - `NdMandateClient.close(id: Long, request: CloseMandateRequest)` → `POST /v1/mandates/{id}/close`
  - `NdMandateClient.cancel(id: Long, request: CancelMandateRequest)` → `POST /v1/mandates/{id}/cancel`
  - `NdContractClient.get(id: String): Contract` → `GET /v1/contracts/{id}`
  - `NdCustomerClient.get(id: Long): Customer` → `GET /v1/customers/{id}`
  - `NdAssignmentClient.accepted(mandateId: Long): AssignmentSummary?` → `GET /v1/assignments?mandate-id={mandateId}&status=ACCEPTED`, first or null
  - All throw Spring's `RestClientException` / `HttpClientErrorException` unchanged, as the existing clients.

- [ ] **Step 1: Write the failing tests**, in the style of `NdMandateClientTest` (`MockRestServiceServer` bound to a
  `RestClient.Builder`): one test per method asserting method + exact URI (the three `status` params in that order
  for `active`), the JSON body for `update` / `close` / `cancel`, the parsed result, and a 409 propagated as
  `HttpClientErrorException` for `requestApproval`. `accepted` returns `null` on an empty list.
- [ ] **Step 2: Run** `mvn test -Dtest='Nd*ClientTest'` — expect compilation failure.
- [ ] **Step 3: Implement** the methods, each new client a `@Service` built like `NdMandateClient` (base URL
  `ndopify.server.api.base-url`, Spring-built `RestClient.Builder` so the tenant and auth interceptors apply).
- [ ] **Step 4: Run** `mvn test -Dtest='Nd*ClientTest'` — PASS.
- [ ] **Step 5: Commit** `feat(npc): mandate, contract and customer clients for the mandate pages`

---

### Task 2: Mandate rules and labels

**Files:**
- Modify: `ui/mandate/MandateLabels.kt`, `src/main/resources/application.yml`
- Create: `ui/mandate/MandateRules.kt`
- Test: `MandateLabelsTest.kt` (modify), `MandateRulesTest.kt` (create)

**Interfaces:**
- Produces (all pure; no Spring):
  - `MandateLabels.party(type: MandateType): String` — "Bailleur" / "Locataire"
  - `MandateLabels.shortPropertyType(type: PropertyType?): String` — `PROPERTY_TYPE_SHORT` of the JS: RETAIL "Boutique",
    INDUSTRIAL "Entrepôt", null "Tout type", else `PROPERTY_TYPE_LABELS`
  - `MandateLabels.title(mandate: Mandate): String` — `"Locataire · Maison"`
  - `MandateLabels.criteria(mandate: Mandate, locations: Map<Long, Location>): List<Pair<String, String>>` — the JS
    `criteriaOf` rows in its order: sector (`"Kotto, Logpom — Douala"`, `"Toute la ville — Douala"`), amount, moving,
    category, type, furniture / rooms / parkings / area when they apply (reuse `FindPropertyRules.needs*`), amenity
    groups (existing `amenityGroups`), then `"Autres précisions"` = comments. The first three are the card's facts;
    the page shows rows 0–4 (+ comments) as key criteria, the rest under "Voir tous les critères".
  - `object MandateRules` with `ACTIVE: Set<MandateStatus>`, `EDITABLE: Set<MandateStatus>`, `SHOWN: Set<MandateStatus>`,
    `data class Tag(val label: String, val style: String)`, `fun tag(status: MandateStatus): Tag`,
    `data class PartyWords(val the: String, val theUpper: String, val of: String, val search: String, val searchUpper: String)`,
    `fun words(type: MandateType): PartyWords`, `fun cancellation(code: MandateCancellationCode?): String`
    (`CANCELLATION_LABELS` of the JS; null/UNKNOWN → "autre raison"),
    `fun formatDate(date: Date, zone: ZoneId): String`,
    `fun requestedAt(contract: Contract?, ttlDays: Int): Date?`,
    `fun editedSince(mandate: Mandate, requestedAt: Date?): Boolean`,
    `fun nextStep(mandate: Mandate, contract: Contract?, now: Long, zone: ZoneId): String?` — the JS `nextStep` copy,
    APPROVED with the contract's `validUntil` ("Mandat signé." alone without it), null for other statuses.
  - `application.yml`: `ndopify.contract.signature.ttl-days: 7` with the comment "Same as ndopify-server's: the
    mandate page derives when the contract was sent from its deadline".

- [ ] **Step 1: Write the failing tests.** `MandateRulesTest`: `tag` for each of the five statuses (label + style
  above); `words(LISTING).theUpper == "Le bailleur"`; `formatDate` of `2026-10-22T23:30Z` in `Africa/Douala` is
  `"23 octobre 2026"`; `requestedAt` = deadline − 7 days, null without contract or deadline; `editedSince` true only
  when `modifiedAt` is after it; `nextStep` for ASSIGNED ("Contactez le client, puis envoyez-lui le mandat à
  approuver."), PENDING_APPROVAL ("Le client a jusqu'au 22 octobre 2026 pour signer le mandat."), **deadline passed**
  ("Délai de signature dépassé : renvoyez le mandat."), APPROVED ("Mandat signé, valable jusqu'au 28 mars 2027."),
  CLOSED → null. `MandateLabelsTest`: `title` of a LISTING APARTMENT is `"Bailleur · Appartement"`, of a SEARCH with
  no type `"Locataire · Tout type"`; `criteria` order and the "Toute la ville — Douala" sector; comments last.
- [ ] **Step 2: Run** `mvn test -Dtest='Mandate*Test'` — FAIL (unresolved references).
- [ ] **Step 3: Implement.** `formatDate` with `DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)`.
- [ ] **Step 4: Run** — PASS. **Step 5: Commit** `feat(npc): mandate statuses, wording and criteria`

---

### Task 3: *Mes mandats* on the Espace agent

**Files:**
- Create: `ui/mandate/MandateCardView.kt`, `ui/mandate/MandateCards.kt`
- Modify: `ui/home/HomeController.kt`, `templates/index.html`, `public/assets/css/home.css`
- Test: `MandateCardsTest.kt` (create), `HomeControllerTest.kt`, `HomeTemplateTest.kt` (modify)

**Interfaces:**
- Consumes: Task 1 `NdMandateClient.active/get`, `NdContractClient.get`; Task 2 `MandateLabels.title/criteria`,
  `MandateRules.tag/nextStep`.
- Produces:
  - `data class MandateCardView(val id: Long, val title: String, val tag: MandateRules.Tag, val facts: List<Pair<String, String>>, val nextStep: String?)`
    — `facts` = the first three `criteria` rows, the sector without its `" — city"` suffix (the card's "Secteurs:
    Kotto, Logpom").
  - `MandateCards.of(mandate: Mandate, contract: Contract?, locations: Map<Long, Location>, now: Long, zone: ZoneId): MandateCardView`
  - `HomeController` constructor gains `contractClient: NdContractClient`; model attributes `mandates:
    List<MandateCardView>` and `mandatesUnavailable: Boolean`, **only for a VERIFIED agent** (like `assignments`).

- [ ] **Step 1: Write the failing tests.** `MandateCardsTest`: facts and next step for each active status.
  `HomeControllerTest`: the agent's active mandates become cards in the search's order; one mandate whose `get`
  fails is left out, the others shown; `active` failing → `mandatesUnavailable = true`, empty list; a contract
  failing → card kept, `nextStep` without the date; contract only fetched for PENDING_APPROVAL / APPROVED; location
  names fetched once for offers and mandates together; not VERIFIED → no `mandates` attribute.
  `HomeTemplateTest`: the section `#mandates-title` "Mes mandats" renders each card's title as a link to
  `/mandates/{id}`, its `status-tag status-tag--{style}`, facts and next step; the empty state text
  ("Vous n'avez aucun mandat en cours. Acceptez un mandat proposé pour le retrouver ici.") with no mandates; the
  unavailable alert when `mandatesUnavailable`.
- [ ] **Step 2: Run** `mvn test -Dtest='MandateCardsTest,Home*Test'` — FAIL.
- [ ] **Step 3: Implement** `MandateCards`, then `HomeController.mandates(agent)` beside `assignments(agent)`
  (same try/catch and logging pattern); merge the location ids of both before the single `names(...)` call.
- [ ] **Step 4: Template.** Copy the prototype's *Mes mandats* section after *Mandats proposés*, minus the
  `<template data-mandate-template>` and `data-*` hooks only the prototype JS used; `th:each` over `mandates`.
  Copy the `.mandate-card*` / `.status-tag*` rules from the prototype `home.css` / `partner-central.css` that the
  served CSS lacks (diff the files; copy, don't restyle).
- [ ] **Step 5: Run** `mvn verify` — PASS. **Step 6: Commit** `feat(npc): Mes mandats on the Espace agent`

---

### Task 4: The mandate page (read-only)

**Files:**
- Create: `ui/mandate/MandateView.kt`, `ui/mandate/MandateViews.kt`, `ui/mandate/MandateController.kt`,
  `templates/mandate.html`
- Modify: `public/assets/css/home.css`
- Test: `MandateViewsTest.kt`, `MandateControllerTest.kt`, `MandateTemplateTest.kt` (create)

**Interfaces:**
- Consumes: Task 1 clients, Task 2 rules / labels.
- Produces:
  - `data class MandateView(val id: Long, val status: MandateStatus, val title: String, val tag: MandateRules.Tag, val words: MandateRules.PartyWords, val acceptedAt: String?, val steps: List<StepView>, val currentStep: Int, val requestedAt: String?, val signatureDeadline: String?, val deadlinePassed: Boolean, val editedSince: Boolean, val approvedAt: String?, val validUntil: String?, val closedAt: String?, val finalPrice: String?, val age: String?, val cancellation: String?, val cancellationReason: String?, val contact: ContactView?, val keyCriteria: List<Pair<String, String>>, val moreCriteria: List<Pair<String, String>>, val editable: Boolean, val closable: Boolean, val cancellable: Boolean, val signatureTtlDays: Int)`
  - `data class StepView(val status: String, val label: String, val date: String?, val done: Boolean)` — the four
    steps ASSIGNED / PENDING_APPROVAL / APPROVED / CLOSED, the last labelled "Annulé" for a CANCELLED mandate; dates
    acceptedAt / requestedAt / approvedAt / closedAt.
  - `data class ContactView(val name: String, val email: String, val whatsapp: String?, val whatsappDisplay: String?, val period: String?)`
    — `whatsappDisplay` = the JS `displayPhone` ("+237 6 99 12 34 56"); the WhatsApp link is `https://wa.me/{digits}`;
    `period` from the find-property contact labels ("le matin", …), moved from `FindPropertyController` to
    `MandateLabels.CONTACT_PERIOD_LABELS`.
  - `MandateViews.of(mandate, contract: Contract?, customer: Customer?, accepted: AssignmentSummary?, locations, now: Long, zone: ZoneId, ttlDays: Int): MandateView`
  - `MandateController` (`@Controller @RequestMapping("/mandates")`) constructor: `currentAgent, mandateClient,
    contractClient, customerClient, assignmentClient, locationClient, tenantContext, clock,
    @Value("\${ndopify.contract.signature.ttl-days}") ttlDays: Int`.
  - `GET /mandates/{id}` (`@RequestParam(required = false) end: String?`) → view `"mandate"`, model `mandate`
    (`MandateView`) or `locked = true`; `end` ∈ {"close","cancel"} accepted only when the button would show, else
    ignored; flash `outcome` (Task 5) rendered when present.
  - Final price shown as `MandateLabels.formatNumber(finalPrice) + " FCFA / mois"`; age `"12 jours"` / `"1 jour"`.

- [ ] **Step 1: Write the failing tests.** `MandateViewsTest`: one test per status checking the current step, its
  dates, which of edit / close / cancel are offered (edit: ASSIGNED, PENDING_APPROVAL; close: APPROVED; cancel: the
  three active ones); **pending approval - deadline passed** (`deadlinePassed`, "(dépassé)" appended by the
  template); edited since; CANCELLED's last step "Annulé" + cancellation label; LISTING wording; criteria split.
  `MandateControllerTest`: show renders the view; 404 on the mandate → `locked`; PENDING_ASSIGNMENT → `locked`;
  **contract unavailable** / **customer unavailable** / no accepted assignment → page still rendered, the field
  null; `end=close` on an ASSIGNED mandate ignored; no agent → `redirect:/logout`.
  `MandateTemplateTest` (real Thymeleaf, as `HomeTemplateTest`): title + tag, the four steps with `aria-current`
  on the current, only the current status's panel, the contact's `tel:` / WhatsApp links, the end buttons as links
  to `?end=close|cancel`, the edit link to `/mandates/{id}/edit`, back link to `/#mandates-title`, the user menu
  inside `th:if="${signedIn}"`, the locked alert.
- [ ] **Step 2: Run** `mvn test -Dtest='Mandate*Test'` — FAIL.
- [ ] **Step 3: Implement** `MandateViews`, then the controller's `show`. Each secondary lookup (contract when
  `contractId != null`, customer, accepted assignment, locations) in its own try/catch logging a warning, as
  `HomeController`.
- [ ] **Step 4: Template** `templates/mandate.html` from the prototype's `mandate.html`: head and header like
  `templates/index.html`; panels rendered with `th:if` on `mandate.status` instead of `hidden`; the "Envoyer au
  client" / "Renvoyer" buttons are `<form method="post" th:action="@{/mandates/{id}/request-approval(id=...)}">`
  (Renvoyer `btn--primary` when `editedSince`, else `btn--outline`, as the JS); the end section's buttons are links,
  the form (Task 5) shown when `end` is set; drop "Simuler la signature". Copy the missing `.mandate-*`, `.tracker`,
  `.contact-card*` rules from the prototype CSS.
- [ ] **Step 5: Run** `mvn verify` — PASS. **Step 6: Commit** `feat(npc): the mandate page`

---

### Task 5: Send for approval, close, cancel

**Files:**
- Create: `ui/mandate/MandateEndForm.kt`
- Modify: `ui/mandate/MandateController.kt`, `templates/mandate.html`
- Test: `MandateControllerTest.kt`, `MandateTemplateTest.kt` (modify)

**Interfaces:**
- Consumes: Task 1 `requestApproval/close/cancel`, Task 4 controller and view.
- Produces:
  - `data class MandateEndForm(val finalPrice: String? = null, val cancellationCode: MandateCancellationCode? = null, val cancellationReason: String? = null)`
  - `POST /mandates/{id}/request-approval`, `POST /mandates/{id}/close`, `POST /mandates/{id}/cancel`: on success
    `redirect:/mandates/{id}` with flash `outcome` = `sent` | `resent` (status was PENDING_APPROVAL) | `closed` |
    `cancelled`; 409 / 404 → `changed`; other `RestClientException` → `error`, each still redirecting.
  - Flash texts (from the JS): sent "Mandat envoyé à {email} pour signature.", resent "Mandat renvoyé à {email}
    pour signature.", closed "Mandat fermé : félicitations pour ce bail !", cancelled "Mandat annulé. Il ne figure
    plus dans vos mandats en cours.", saved (Task 6) "Mandat modifié." / "Mandat modifié. Renvoyez-le pour que
    {the} signe la nouvelle version." (PENDING_APPROVAL); my wording for the two the prototype lacks:
    changed "Ce mandat a changé entre-temps : vérifiez son état avant de recommencer." (warning alert), error
    "Action impossible pour le moment. Réessayez dans quelques instants." (error alert). `{email}` is the contact's;
    resolve texts in the controller's `show` from the outcome + the freshly loaded view.
  - Validation, re-rendering `show` with `end` set, the form values kept and `errors` per field: close —
    `FindPropertyRules.parseAmount(finalPrice)` must be ≥ 1, else "Indiquez le loyer convenu, en FCFA par mois.";
    cancel — code required and not `UNKNOWN`/`EXPIRED`, else "Choisissez la raison de l'annulation."; reason trimmed,
    ≤ `FindPropertyRules.MAX_COMMENTS`. `CloseMandateRequest.finalPrice` = `Money(amount, mandate's currency, MONTHLY)`.

- [ ] **Step 1: Write the failing tests** (`MandateControllerTest`): each action's success redirect + flash +
  client call with the exact request; **request approval - conflict** (409) → `changed`; **close - conflict** →
  `changed`; 5xx → `error`; **close - invalid amount** for `""`, `"abc"`, `"0"` → view `mandate`, `end = close`,
  `errors.finalPrice`, `close` never called; `"150 000"` → amount 150000; **cancel - no code** → error, no call;
  reason trimmed, blank sent as null. `MandateTemplateTest`: each outcome's alert text; the open form shows only its
  mode's fields, with the error and the kept value.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement.** **Step 4: Template**: the form from the prototype, one
  `<form method="post">` per mode (`th:action` close / cancel), titles / submit labels / styles from the JS
  `CLOSE_MODES`, "Retour" a link back to `/mandates/{id}`.
- [ ] **Step 5: Run** `mvn verify` — PASS. **Step 6: Commit** `feat(npc): send, close and cancel a mandate`

---

### Task 6: The edit page

**Files:**
- Create: `ui/mandate/MandateEditForm.kt`, `ui/mandate/MandateEditRules.kt`, `templates/mandate/edit.html`
- Modify: `ui/mandate/MandateController.kt`, `public/assets/css/home.css`
- Test: `MandateEditRulesTest.kt`, `MandateEditTemplateTest.kt` (create), `MandateControllerTest.kt` (modify)

**Interfaces:**
- Consumes: Task 1 `get/update`, Task 2, `FindPropertyRules`, `SectorValidator`.
- Produces:
  - `data class MandateEditForm(val neighborhoodIds: List<Long> = emptyList(), val propertyCategory: PropertyCategory? = null, val propertyType: String? = null, val rooms: String? = null, val furnitureType: String? = null, val parkings: String? = null, val area: String? = null, val amenities: List<String> = emptyList(), val priceAmount: String? = null, val movingPeriod: MovingPeriod? = null, val comments: String? = null)`
    with `companion fun of(mandate: Mandate): MandateEditForm` (null type/furniture/parkings → `ANY` for a client).
    Amenity names are `PendingMandate.AMENITIES`.
  - `object MandateEditRules`: `fun errors(form: MandateEditForm, owner: Boolean): Map<String, String>` — reuses
    `FindPropertyRules.propertyCategory / propertyType / rooms / furnitureType / parkings / area / priceAmount /
    movingPeriod / comments / neighborhoodIds`, applying rooms / furniture / parkings only when
    `needsRooms` / `needsFurniture` / `asksParkings`; for `owner`, `ANY` (or no type) is an error with the JS
    `initMandateEdit` messages; price message "Indiquez le loyer, en FCFA par mois." vs "… le budget …".
    `fun request(form: MandateEditForm, mandate: Mandate): UpdateMandateRequest` — keeps `listingType`, `cityId`,
    price currency + periodicity from `mandate`; `ANY` and non-applicable follow-ups → null.
  - `GET /mandates/{id}/edit` → view `"mandate/edit"`, model `answers`, `errors`, `mandate` (`MandateView`, for the
    title, number, wording and the city name), `neighborhoods` (chosen `Location`s), or `locked = true` when the
    mandate isn't found or not `EDITABLE` (locked text from the prototype's `data-edit-locked-*`).
  - `POST /mandates/{id}/edit` → re-fetches the mandate first: not EDITABLE → `redirect:/mandates/{id}` with
    `changed`, no update; errors (incl. `SectorValidator.errors(mandate.cityId, ids)`) → the form again; success →
    `update`, `redirect:/mandates/{id}` with `saved`; `RestClientException` → the form with "Enregistrement
    impossible pour le moment. Réessayez dans quelques instants." (as `ProfileController`).

- [ ] **Step 1: Write the failing tests.** `MandateEditRulesTest`: valid client form → no errors; **errors -
  landlord can't answer any** (type, furniture, parkings); rooms required for APARTMENT only; **request - drops
  follow-ups that don't apply** (LAND with rooms "3" → rooms null); amount "150 000" → 150000 with the mandate's
  currency; city never taken from the form. `MandateControllerTest`: edit GET pre-fills from the mandate; APPROVED
  → locked; **save - approved since** → redirect + `changed`, `update` never called; invalid → form with errors and
  the typed values; neighbourhood outside the city → error; success → `update` with the expected request + `saved`;
  5xx → the form with the save error. `MandateEditTemplateTest`: the five `<details class="edit-group">` (sector,
  property, amenities, budget, comments), the city read-only, the PENDING_APPROVAL notice ("Ce mandat attend la
  signature du client : après vos modifications, …" from the prototype), landlord labels and no `ANY` choices, all
  follow-up questions present without JS, errors shown, groups with an error rendered `open`.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implement** rules, form, then the two routes.
- [ ] **Step 4: Template** from the prototype's `mandate/edit.html`; neighbourhoods as profile/edit.html's
  `<select multiple name="neighborhoodIds">` (call 5) with `data-city-id`; the "Indifférent" choices under
  `th:unless="${owner}"`; back link to `/mandates/{id}`. Copy the `.edit-group*` / `.edit-readonly` CSS.
- [ ] **Step 5: Run** `mvn verify` — PASS. **Step 6: Commit** `feat(npc): edit a mandate`

---

### Task 7: `mandate.js`

**Files:**
- Create: `public/assets/js/mandate.js`
- Modify: `templates/mandate.html`, `templates/mandate/edit.html` (script tags, `data-page="mandate"` /
  `"mandate-edit"`)
- Test: `MandateTemplateTest.kt`, `MandateEditTemplateTest.kt` (the script tags and `data-*` hooks are rendered)

**Interfaces:**
- Consumes: the `data-*` hooks of Tasks 4–6; `util.js` / `partner-central.js` helpers already served (user menu,
  `setFieldError`); slim-select 4.3.0 from the same jsdelivr URL as `profile/edit.html`.
- Produces, ES5, ported from the prototype JS:
  - mandate page: the end buttons open the matching form in place (`preventDefault` on the `?end=` link,
    `aria-expanded`, focus the first field, "Retour" closes it); client-side checks with the same messages before
    posting; the submit button disabled with "Fermeture…" / "Annulation…" / "Envoi…" while posting; the flash alert
    focused on load (`tabindex="-1"`).
  - edit page: types filtered by category (`data-categories`), follow-up questions hidden when they don't apply,
    each group's summary showing its current value, the neighbourhoods select enhanced with slim-select over
    `/locations?type=NEIGHBORHOOD&parent-id={city}` (3 at most, counter "n sur 3 sélectionné"), amounts formatted as
    typed.

- [ ] **Step 1: Extend the template tests** to assert `mandate.js` is loaded with `defer` and the hooks it uses are
  present (`data-end-open`, `data-end-form`, `data-categories`, `data-edit-group`, `data-city-id`). Run — FAIL.
- [ ] **Step 2: Implement** `mandate.js` and add the tags. Run `mvn verify` — PASS.
- [ ] **Step 3: Manual check** with the `run` skill (server on 8080 + partner-central on 8081): with JS on and off,
  open a card from `/`, send for approval, edit while PENDING_APPROVAL (notice + "Renvoyer" primary), cancel with a
  reason; at 375px wide nothing scrolls sideways.
- [ ] **Step 4: Commit** `feat(npc): mandate page enhancements`

---

### Task 8: Docs and final gate

**Files:** Modify: `CLAUDE.md` (the `ndopify-partner-central` section).

- [ ] **Step 1:** Add a bullet after the *Mandats proposés* paragraph: *Mes mandats* (`HomeController` →
  `MandateCards`, active statuses, one `GET` per card + its contract), the mandate page and its routes
  (`MandateController`, `?end=close|cancel`, PRG with `outcome`), the edit page (EDITABLE statuses, re-checked on
  POST since ndopify-server's update doesn't check the status), the derived "Envoyé le" and its
  `ndopify.contract.signature.ttl-days` coupling, and that ownership isn't checked yet (BFF nor server). Remove
  "that ndopify-partner-central page doesn't exist yet" only if it refers to a page this plan added (it doesn't:
  it's the contract sign page — leave it).
- [ ] **Step 2: Run** `mvn install -Dheadless=true` from the repo root — BUILD SUCCESS (ktlint, tests, JaCoCo).
- [ ] **Step 3: Commit** `docs: partner central mandate pages`
