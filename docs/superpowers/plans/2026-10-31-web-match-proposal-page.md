# Web Match Proposal Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The page of `ndopify-web` that the match proposal e-mail links to, `{publicPortalUrl}/match/{matchId}?token={jwt}`,
where the customer sees the proposed listing and accepts or rejects it.

**Architecture:** `MatchController` (`ui/match/`) takes the `?token=` once, keeps it in an HTTP-only cookie and
redirects to the same URL without it; the page then reads the match, its mandate, its agent and its listing from
ndopify-server with that token, and posts the answer to `POST /v1/matches/{id}/accept` / `reject`. ndopify-server first
lets the mandate's customer read the match and the mandate (today only the agent or an admin can).

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, Spring Security, `ndopify-web-core` (`AccessTokenService`,
`JwtDecoder`, `JwtAuthenticationFilter`, `AuthorizationClientHttpRequestInterceptor`), `RestClient`.

**Spec:** `docs/superpowers/plans/2026-10-31-match-proposal.md` (the server side, and the link in
`MatchProposedMailet`), the prototype `modules/ndopify-web/src/main/html/proposition.html` (+ `assets/js/proposition.js`),
and `modules/ndopify-server/CLAUDE.md` (*match → Proposal*).

## Global Constraints

- **Who answers:** only the mandate's customer, as ndopify-server enforces (`ensureIsCustomer`). The web app never
  decides on a JWT claim alone: a 401 from ndopify-server clears the cookie, a 403 is the "lien invalide" page.
- **Token:** `public-portal` JWT only (`JwtDecoder` accepting `ApplicationCode.PUBLIC_PORTAL`), cookie
  `__nweb_access_token` (persistent: lives until the token's `exp`, 2 days). `?token=` is stripped by a 302 so it
  never stays in the address bar, the history or a `Referer`.
- **Answer is final** (server: 409 `MATCH_NOT_PROPOSED`): the prototype's "Finalement, pas pour moi" and
  "Ce logement m'intéresse finalement" are left out of the template (kept in the prototype).
- **Reject:** `reasonCode` one of `PRICE`, `LOCATION`, `SIZE`, `AMENITIES`, `AVAILABILITY`, `OTHER` (radio `reason`,
  required in the form; missing → `OTHER`), `comment` → `RejectMatchRequest.reason`, `maxlength="500"` as the
  prototype (the server allows 1000).
- **Out of scope:** "Demander une visite" (left out as on the listing page: the popup stays in the template, no
  handler), signing in without the link (no public-portal OTP yet), telling the agent of the answer.
- Never show `Listing.agentRemarks`; prices and dates through `TenantFormatter`; UI text in French; every page works
  without JavaScript (`proposal.js` only opens the popups).
- No DTO change, so no DTO release. The server change (Task 1) must deploy before the web app.

## Review Focus

1. **Expired or forwarded-and-expired link** (`?token=` that doesn't decode, or no cookie) → "Ce lien a expiré" page
   (200, no match data), not a 500 or a redirect loop (Task 3).
2. **Another customer's token on this match** (ndopify-server 403) → "Ce lien n'est pas le vôtre" page, no match
   data (Task 3).
3. **Listing rented / unpublished since the proposal** (listing GET 403/404) → `UNAVAILABLE` block, no answer form,
   no photos (Task 4).
4. **Double submit / answering in two tabs** (409 `MATCH_NOT_PROPOSED`) → redirect to the page, showing the current
   answer, no error page (Task 4).
5. **A match the agent never proposed** (ACTIVE, SHORTLISTED, DISMISSED) read by the customer → 403 from
   ndopify-server, so its existence and score don't leak (Task 1).

---

### Task 1: ndopify-server — the customer reads its proposal

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateAccessService.kt`
- Modify: `.../match/server/endpoint/MatchEndpoints.kt` (`get`), `.../mandate/server/endpoint/MandateEndpoints.kt` (`get`)
- Test: `.../match/server/endpoint/GetMatchEndpointTest.kt`, `.../mandate/server/endpoint/GetMandateEndpointTest.kt`
  (+ their `.sql`; reuse `ProposeMatchEndpoint.sql`'s customer if it fits)
- Docs: `modules/ndopify-server/CLAUDE.md` (*match*, *mandate* access)

**Interfaces:**
- Produces: `MandateAccessService.ensureCanReadAsCustomerOrAgent(mandate: MandateEntity)` — passes for whoever
  `ensureCanRead` passes, or the mandate's customer (`isCustomer`, the private check `ensureIsCustomer` already does).
  `GET /v1/matches/{id}` for the customer only when `status in (PROPOSED, ACCEPTED, REJECTED)`, else 403
  `MANDATE_ACCESS_DENIED`. `GET /v1/mandates/{id}` for the customer, any status.

- [ ] **Step 1: Failing tests** — `GetMatchEndpointTest`: `customer` (PROPOSED → 200), `customerAnswered` (REJECTED →
  200), `customerNotProposed` (ACTIVE → 403 `MANDATE_ACCESS_DENIED`), `otherCustomer` → 403; existing agent / admin
  cases unchanged. `GetMandateEndpointTest`: `customer` → 200, `otherCustomer` → 403.
- [ ] **Step 2:** `mvn test -Dtest='Get*EndpointTest'` → the customer cases FAIL with 403.
- [ ] **Step 3:** Implement; extract the party check of `ensureIsCustomer` into a private `isCustomer(mandate)`. The
  status rule lives in `MatchEndpoints.get` (it's about matches, not mandates).
- [ ] **Step 4:** `mvn test -Dtest='*Match*,*Mandate*'` → PASS; update `CLAUDE.md`; `mvn verify` → BUILD SUCCESS.
- [ ] **Step 5:** Commit `feat(server): the customer reads its match proposal and its mandate`.

### Task 2: ndopify-web — sign-in by link token

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/WebCoreConfiguration.kt`,
  `SecurityConfiguration.kt`, `RestConfiguration.kt`; `modules/ndopify-web/pom.xml` if `auth0 java-jwt` /
  security deps aren't transitive from web-core
- Test: `.../web/config/SecurityConfigurationTest.kt` (or extend the existing controller tests)

**Interfaces:**
- Produces: beans `AccessTokenService(cookieName = "__nweb_access_token", persistent = true)`,
  `JwtDecoder` accepting only `ApplicationCode.PUBLIC_PORTAL`, `JwtAuthenticationFilter` before
  `UsernamePasswordAuthenticationFilter`, `AuthorizationClientHttpRequestInterceptor` on the `RestClient`s (after the
  `X-Tenant-ID` one), as in `ndopify-esignature`'s `WebCoreConfiguration`. `SecurityConfiguration` also permits
  `GET /match/*`, `POST /match/*/accept`, `POST /match/*/reject` (controller decides signed in or not; CSRF on).

- [ ] **Step 1: Failing test** — a request with a valid `public-portal` cookie gets a `JwtAuthentication`; an
  `e-signature` token or a malformed one is signed out and the cookie is cleared (`Max-Age=0`).
- [ ] **Step 2:** `mvn test` → FAIL.
- [ ] **Step 3:** Copy the esignature wiring; no other page changes (the listing / home calls send no token when
  there's no cookie).
- [ ] **Step 4:** `mvn test` → PASS.
- [ ] **Step 5:** Commit `feat(web): sign in with the match link's token`.

### Task 3: ndopify-web — clients and the controller's entry / error states

**Files:**
- Create: `.../web/client/ndopify/NdMatchClient.kt`, `NdMandateClient.kt`, `NdAgentClient.kt`
- Create: `.../web/ui/match/MatchController.kt`, `templates/match.html` (from `proposition.html`, minus the parts
  above), `templates/match-link.html` (expired / not-yours: logo + one message, no match data)
- Test: `Nd{Match,Mandate,Agent}ClientTest` (`MockRestServiceServer`), `MatchControllerTest` (MockMvc standalone),
  fixtures `MatchFixtures`

**Interfaces:**
- Produces: `NdMatchClient.get(id: Long): Match`, `accept(id: Long)`, `reject(id: Long, request: RejectMatchRequest)`;
  `NdMandateClient.get(id: Long): Mandate`; `NdAgentClient.get(id: Long): Agent`.
- `MatchController`:
  - `GET /match/{id}?token=` → `accessTokenService.set(token)`, 302 `/match/{id}` (only `token` removed).
  - `GET /match/{id}` signed out → `match-link` with `reason = "expired"`; ndopify-server 401 → clear cookie,
    `expired`; 403 → `reason = "not-yours"`; 404 → the app's 404 (`HttpClientErrorHandler`).

- [ ] **Step 1: Failing tests** — `tokenIsStoredAndStripped` (Set-Cookie `__nweb_access_token`, `Location: /match/12`),
  `signedOut` (view `match-link`, `reason=expired`, no `view` attribute), `serverUnauthorized` (cookie cleared,
  `expired`), `notYours` (403 → `not-yours`), `notFound` (404).
- [ ] **Step 2:** `mvn test -Dtest='*Match*'` → FAIL.
- [ ] **Step 3:** Implement the clients and these branches; texts: expired → "Ce lien a expiré. Demandez à votre
  agent de vous renvoyer la proposition."; not-yours → "Ce lien a été envoyé à une autre personne."
- [ ] **Step 4:** `mvn test -Dtest='*Match*'` → PASS.
- [ ] **Step 5:** Commit `feat(web): match proposal link`.

### Task 4: ndopify-web — the proposal page and the answer

**Files:**
- Create: `.../web/ui/match/MatchPage.kt` (view model), `MatchPages.kt` (builds it), `RejectForm.kt`
- Modify: `MatchController.kt`, `templates/match.html`; reuse `ui/listing/ListingPages` for the listing body
- Create: `public/assets/js/proposal.js`, `public/assets/css/proposal.css` (from the prototype's `proposition.*`,
  without its faked answers)
- Test: `MatchPagesTest`, `MatchControllerTest` (extended), `MatchTemplateTest` (real Thymeleaf)
- Docs: `modules/ndopify-web/CLAUDE.md` (the page, the cookie: it's no longer "no JWT, no cookie")

**Interfaces:**
- Consumes: Task 3's clients; `ListingPages.build(...)` → `ListingPage`.
- Produces: `MatchPage(state: AnswerState, title: String /* "Serge vous propose ce logement" */,
  searchSummary: String, agentName: String, agentInitials: String, agentWhatsappUrl: String?, listing: ListingPage?,
  flash: String?)`; `enum AnswerState { PENDING, ACCEPTED, REJECTED, UNAVAILABLE }` — PROPOSED → PENDING; listing GET
  403/404 or listing not PUBLISHED → UNAVAILABLE (`listing = null`), whatever the match's status.
  `POST /match/{id}/accept` → 302 `/match/{id}?answer=accepted`; `POST /match/{id}/reject` (`RejectForm(reason:
  MatchRejectionReason?, comment: String?)`) → 302 `/match/{id}?answer=rejected`; 409 `MATCH_NOT_PROPOSED` → 302
  `/match/{id}` (no flash); 401 / 403 as Task 3.
- Flash (`?answer=`): accepted → "C'est noté : {agent} est prévenu de votre intérêt."; rejected → "C'est noté :
  {agent} va continuer à chercher pour vous."

- [ ] **Step 1: Failing tests** — `MatchPagesTest`: each `AnswerState`, title with the agent's first name (no customer name: the customer isn't readable from here, and the prototype's "Sandrine, " is dropped), price through `TenantFormatter`.
  `MatchControllerTest`: `accept` / `reject` call the client with `RejectMatchRequest(reasonCode, reason)`, blank
  comment → null, missing reason → `OTHER`; `answerTwice` (409 → 302 `/match/12`). `MatchTemplateTest`: only the
  current state's `data-answer` block is rendered; PENDING has the two forms with the CSRF field; ACCEPTED / REJECTED
  have no form; UNAVAILABLE has no photos and no form; `agentRemarks` never in the HTML.
- [ ] **Step 2:** `mvn test -Dtest='*Match*'` → FAIL.
- [ ] **Step 3:** Implement. The "Pourquoi ce logement" list: one line per score of `Match` that is 1.0 (neighbourhood
  when `distanceKm == 0.0`, price, rooms, furniture, availability), plus the partial amenities line from
  `missingAmenities` (French labels as in the listing page); left out when empty.
- [ ] **Step 4:** `mvn test` → PASS; `mvn verify` (coverage gate) → BUILD SUCCESS; update `CLAUDE.md`.
- [ ] **Step 5:** Commit `feat(web): the match proposal page`.

### Task 5: Manual check

- [ ] Local stack (server 8080, web 8082, Mailpit): propose a match from Partner Central, open the e-mail's link →
  redirected without `token`, page PENDING; reject with a reason → REJECTED + flash; reload → no form; open the link
  in a private window after deleting the cookie → "Ce lien a expiré" only if the token is past `exp`, else signed in
  again (expected: the token isn't single-use).
