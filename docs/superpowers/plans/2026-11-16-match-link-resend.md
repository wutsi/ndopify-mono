# Match Link Resend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the agent re-send the customer's match link for a `PROPOSED` or `ACCEPTED` match without changing its status, with a 15-minute throttle and a visit invitation when the customer already said it was interested.

**Architecture:** `ndopify-dto` gains `MATCH_RATE_LIMITED`; `ndopify-server` adds `POST /v1/matches/{id}/resend`, a `resent_at` column on `T_MATCH`, and a `MatchResentEvent` whose after-commit listener sends the existing proposal e-mail (which gains one line for an `ACCEPTED` match). `ndopify-partner-central` adds the "Renvoyer le lien" action on the mandate page, hidden when a visit is already open, and a dedicated message for the throttle.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway (MySQL), MJML + Mustache, Thymeleaf, MockMvc, MockRestServiceServer, mockito-kotlin, `kotlin.test`, auth0 JWT.

**Spec:** GitHub issue #177 — https://github.com/wutsi/ndopify-mono/issues/177

## Global Constraints

- Three modules: `ndopify-dto`, `ndopify-server`, `ndopify-partner-central`. No `ndopify-web-core`, no root `pom.xml` commit.
- **Prototype gate first:** Task 1 edits only `src/main/html/`; no DTO, server or BFF edit before the human partner's OK on the prototype.
- One library module per commit: the `ndopify-dto` commit is separate; the server and Partner Central commits never touch it. Conventional messages with `Refs #177` in the body.
- To build locally against the new DTO, set `<ndopify-dto.version>` in the root `pom.xml` to `0.0.95-SNAPSHOT` and `mvn install` it; this edit is **never committed** (the release in `push` sets the released version).
- `ndopify.match.expire.days` stays `7`; a resend of a `PROPOSED` match restarts that window, an `ACCEPTED` one doesn't touch it.
- The resend never changes a match's status, `answeredAt`, nor, for an `ACCEPTED` match, `proposedAt`. `EXPIRED` keeps the existing "Envoyer" (re-propose).
- The agent never receives a copyable link or token: only ndopify-server e-mails it.
- UI text and e-mails in French; code, comments, logs and docs in English. Dates and amounts through `TenantFormatter`.
- The token is never logged; the rate-limited response carries no token.
- `kotlin.test` assertions and `mockito-kotlin` mocks, as in the surrounding tests.
- `mvn verify` green in each touched module before its commit; `ndopify-server` needs a local MySQL.

## Review Focus

1. **An `ACCEPTED` match is actually e-mailed** — `sendProposedEmail` filters `PROPOSED`; a resent `ACCEPTED` match needs its own listener path (`sendResentEmail`), or the endpoint returns 200 and nothing is sent (Task 3).
2. **`ACCEPTED` stays accepted** — status, `answeredAt` and `proposedAt` unchanged; only `resentAt` and `modifiedAt` move (Task 3).
3. **`PROPOSED` gets a fresh 7-day window** — `proposedAt = now`, so `MatchJobs.expire` doesn't expire it the next day (Task 3).
4. **One e-mail per double post** — the second resend within 15 minutes is a 409 `MATCH_RATE_LIMITED` and sends nothing (Task 3).
5. **A throttled resend doesn't say "Ce bien a changé"** — the Partner Central controller must read `MATCH_RATE_LIMITED` from the 409 body and show its own message (Task 5).
6. **No resend button once a visit is open** — `MatchView.resendable` is false when an `ACCEPTED` match already has a `REQUESTED`/`CONFIRMED`/`CHECKED_IN` visit (Task 5).
7. **Closed search or rented listing** — 409 `MANDATE_NOT_APPROVED` / `LISTING_NOT_PUBLISHED`, no e-mail (Task 3).

---

## Part A — Prototype gate

### Task 1: Show the resend action on the prototype match card

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`

**Interfaces:**
- Consumes: nothing (static prototype).
- Produces: the approved visual for Task 5: a "Renvoyer le lien" button on a `PROPOSED` card and on an `ACCEPTED` card without a visit, with a success flash.

- [ ] **Step 1: Add the button to the card template**

In `src/main/html/mandate.html`, after the `data-match-answer` paragraph (line 465), add:

```html
<div class="assignment__actions match__actions" data-match-resend-actions hidden>
    <button type="button" class="btn btn--outline" data-match-resend>Renvoyer le lien<span
            class="visually-hidden"> au client</span></button>
</div>
```

- [ ] **Step 2: Render and simulate it**

In `matchItem` in `assets/js/partner-central.js` (around line 2936), keep the other actions for `ACTIVE` / `EXPIRED` only:

```js
actions.hidden = match.status !== "ACTIVE" && match.status !== "EXPIRED";
```

Then add, before `return item;`:

```js
var resendActions = $("[data-match-resend-actions]", item);
var resend = match.status === "PROPOSED" || (match.status === "ACCEPTED" && !match.visit);
resendActions.hidden = !resend;
if (resend) {
    $("[data-match-resend]", item).addEventListener("click", function () {
        // PROTOTYPE: simulates POST /v1/matches/{id}/resend: a fresh link, the status is unchanged.
        showMatchFlash("Lien renvoyé à " + c.email + ".");
    });
}
```

- [ ] **Step 3: Show it and stop**

Run: `cd modules/ndopify-partner-central && python3 -m http.server 4321 --directory src/main/html`

Open `http://localhost:4321/mandate.html` at 390 px and 1280 px: a `PROPOSED` card and an `ACCEPTED` card without a visit show "Renvoyer le lien"; an `ACCEPTED` card with a visit and an `ACTIVE` card don't. Stop the server (`pkill -f "http.server 4321"`).

- [ ] **Step 4: Stop for the human partner's OK**

Do not edit any DTO, server or BFF file before the OK. After the OK, commit the prototype:

```bash
git add modules/ndopify-partner-central/src/main/html/mandate.html \
  modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js
git commit -m "feat(npc): prototype the match link resend" -m "Refs #177"
```

---

## Part B — ndopify-dto

### Task 2: Add `MATCH_RATE_LIMITED`

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt:93-96`

**Interfaces:**
- Consumes: nothing.
- Produces: `ErrorCode.MATCH_RATE_LIMITED = "urn:wutsi:ndopify:error:match:rate-limited"`, used by Task 3 (server) and Task 5 (Partner Central).

- [ ] **Step 1: Add the constant**

After `MATCH_NOT_PROPOSED`:

```kotlin
    val MATCH_RATE_LIMITED = "$PREFIX:match:rate-limited"
```

- [ ] **Step 2: Verify the module**

Run: `cd modules/ndopify-dto && mvn verify`

Expected: BUILD SUCCESS (no tests; ktlint passes).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add the match rate-limited error code" -m "Refs #177"
```

---

## Part C — ndopify-server

### Task 3: `POST /v1/matches/{id}/resend`

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (under `ndopify.match`)
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V12_1__match-resent-at.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchResentEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListener.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/endpoint/MatchEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ResendMatchEndpointTest.kt` (new)
- Test: `modules/ndopify-server/src/test/resources/db/test/match/ResendMatchEndpoint.sql` (new)
- Modify: `modules/ndopify-server/CLAUDE.md` (the **match** section, **Proposal** / **Expiry** paragraphs)

**Interfaces:**
- Consumes: `ErrorCode.MATCH_RATE_LIMITED` (Task 2), `MatchLinkTokenService`, `MatchProposedMailet`.
- Produces: `MatchProposalService.resend(id: Long): MatchEntity`, `MatchProposalService.sendResentEmail(matchId: Long)`, `MatchResentEvent(matchId, tenantId)`, `POST /v1/matches/{id}/resend` (200, no body).

- [ ] **Step 1: Build against the local DTO**

Run: `cd modules/ndopify-dto && mvn install -DskipTests`

Set `<ndopify-dto.version>` in the root `pom.xml` to `0.0.95-SNAPSHOT`. **Do not commit it.**

- [ ] **Step 2: Write the failing unit tests**

In `MatchProposalServiceTest`, add `Duration.ofMinutes(15)` as the last constructor argument (after `7L`), then add:

```kotlin
@Test
fun `resend a proposed match restarts its window`() {
    service.resend(2L)

    val saved = saved()
    assertEquals(MatchStatus.PROPOSED, saved.status)
    assertEquals(Date.from(clock.instant()), saved.proposedAt)
    assertEquals(Date.from(clock.instant()), saved.resentAt)
    val event = argumentCaptor<MatchResentEvent>()
    verify(eventPublisher).publishEvent(event.capture())
    assertEquals(MatchResentEvent(matchId = 2L, tenantId = 1L), event.firstValue)
}

@Test
fun `resend an accepted match keeps its answer`() {
    val answeredAt = Date.from(clock.instant().minusSeconds(3600))
    doReturn(match.copy(status = MatchStatus.ACCEPTED, answeredAt = answeredAt)).whenever(matchService).findById(2L)

    service.resend(2L)

    val saved = saved()
    assertEquals(MatchStatus.ACCEPTED, saved.status)
    assertEquals(answeredAt, saved.answeredAt)
    assertNull(saved.proposedAt)
    assertEquals(Date.from(clock.instant()), saved.resentAt)
}

@Test
fun `resend too soon`() {
    doReturn(match.copy(resentAt = Date.from(clock.instant().minusSeconds(60)))).whenever(matchService).findById(2L)

    assertFailsWith<ConflictException> { service.resend(2L) }
    verify(dao, never()).save(any())
    verify(eventPublisher, never()).publishEvent(any())
}

@Test
fun `resend a match not proposed nor accepted`() {
    doReturn(match.copy(status = MatchStatus.ACTIVE)).whenever(matchService).findById(2L)

    assertFailsWith<ConflictException> { service.resend(2L) }
    verify(eventPublisher, never()).publishEvent(any())
}

@Test
fun `resend a proposed match of a closed search`() {
    doReturn(match.copy(mandate = mandate.copy(status = MandateStatus.CLOSED))).whenever(matchService).findById(2L)

    assertFailsWith<ConflictException> { service.resend(2L) }
}

@Test
fun `resend a proposed match whose listing is gone`() {
    doReturn(match.copy(listing = listing.copy(status = ListingStatus.RENTED))).whenever(matchService).findById(2L)

    assertFailsWith<ConflictException> { service.resend(2L) }
}

@Test
fun `resent - the customer is e-mailed for proposed and accepted`() {
    doReturn(Optional.of(match)).whenever(dao).findById(2L)
    doReturn(customer).whenever(customerService).findById(10L)
    doReturn(agent).whenever(agentService).findById(20L)
    service.sendResentEmail(2L)
    verify(mailet).send(listOf(match), customer, agent)

    val accepted = match.copy(status = MatchStatus.ACCEPTED)
    doReturn(Optional.of(accepted)).whenever(dao).findById(2L)
    service.sendResentEmail(2L)
    verify(mailet).send(listOf(accepted), customer, agent)
}

@Test
fun `resent - nothing for another status`() {
    doReturn(Optional.of(match.copy(status = MatchStatus.REJECTED))).whenever(dao).findById(2L)

    service.sendResentEmail(2L)

    verifyNoInteractions(mailet)
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposalServiceTest`

Expected: compilation fails on `resend`, `sendResentEmail`, `MatchResentEvent`, `resentAt`.

- [ ] **Step 4: Implement the migration, entity and config**

`V12_1__match-resent-at.sql`:

```sql
ALTER TABLE T_MATCH
    ADD COLUMN resent_at DATETIME DEFAULT NULL;
```

In `MatchEntity`, after `answeredAt`:

```kotlin
    // When the agent last resent the link; throttles a repeated resend (MatchProposalService.resend)
    val resentAt: Date? = null,
```

Under `ndopify.match` in `application.yml`:

```yaml
        resend:
            min-interval: 15m           # A match can't be resent to its customer before this delay
```

- [ ] **Step 5: Implement the service, event and endpoint**

`MatchResentEvent`:

```kotlin
/** Published by `MatchProposalService.resend`, inside its transaction: ids only, the listener reloads the match. */
data class MatchResentEvent(val matchId: Long, val tenantId: Long)
```

`MatchProposalService`: add the constructor parameter after `expireDays`:

```kotlin
    @param:Value("\${ndopify.match.resend.min-interval}") private val resendMinInterval: Duration,
```

Add:

```kotlin
/**
 * Sends the match's link to its customer again, without changing its status: PROPOSED or ACCEPTED only, the search
 * APPROVED and the listing PUBLISHED. A PROPOSED match gets a fresh `proposedAt` (its 7-day window restarts); an
 * ACCEPTED one keeps its `answeredAt` and `proposedAt`. 409 MATCH_NOT_PROPOSED, MANDATE_NOT_APPROVED,
 * LISTING_NOT_PUBLISHED, then MATCH_RATE_LIMITED when the last resend is younger than `ndopify.match.resend.min-interval`.
 * E-mails the customer after commit ([MatchResentEvent]).
 */
@Transactional
fun resend(id: Long): MatchEntity

/** E-mails the customer the match's link again, PROPOSED or ACCEPTED only; called by `MatchEventListener`. */
@Transactional
fun sendResentEmail(matchId: Long)
```

`resend` loads `matchService.findById(id)`, checks the four conditions in the KDoc's order, saves
`match.copy(proposedAt = if (status == PROPOSED) now else match.proposedAt, resentAt = now, modifiedAt = now)`, and
publishes `MatchResentEvent(saved.id!!, saved.tenantId!!)`. The throttle compares `match.resentAt` to
`Date(now.time - resendMinInterval.toMillis())`.

`sendResentEmail` returns unless the loaded match is `PROPOSED` or `ACCEPTED`; otherwise
`mailet.send(listOf(match), customerService.findById(match.mandate.customerId), agentService.findById(match.agentId))`.

`MatchEventListener`: copy `onMatchProposed`'s logger / `TenantContext` pattern into `onMatchResent(event: MatchResentEvent)`, calling `proposalService.sendResentEmail(event.matchId)`.

`MatchEndpoints`:

```kotlin
// A fresh link to an already sent match; the status, and an answer, are unchanged
@PostMapping("/{id}/resend")
fun resend(@PathVariable id: Long) {
    access.ensureCanUpdate(service.findById(id).mandate)
    proposalService.resend(id)
}
```

- [ ] **Step 6: Run the unit tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposalServiceTest`

Expected: PASS.

- [ ] **Step 7: Add the integration test and its fixture**

Create `ResendMatchEndpoint.sql` (it runs **after** the shared `ProposeMatchEndpoint.sql`), adding a listing mandate
`17`, a published listing `107` (same shape as `16` / `106`, title `"Accepted apartment"`) and an accepted match on an
approved search:

```sql
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category,
                      city_id, property_type, rooms, price, price_periodicity_type, currency_code)
VALUES (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id,
                      neighborhood_id, property_type, rooms, price, currency_code, price_periodicity_type, title, url)
VALUES (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY',
        'Accepted apartment', null);

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, proposed_at, answered_at)
VALUES (12, 1, 1, 107, 20, 'ACCEPTED', 0.85, 145000, '2026-10-20 10:00:00', '2026-10-21 10:00:00');
```

Create `ResendMatchEndpointTest` with
`@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql", "/db/test/match/ResendMatchEndpoint.sql"])`
(`AuthorizationAwareEndpointIntegrationTest`):

```kotlin
@Test fun `resend a proposed match`()          // 200; status PROPOSED; proposedAt/resentAt set; one e-mail with the match link
@Test fun `resend an accepted match`()         // 200; status ACCEPTED; answeredAt and proposedAt unchanged; one e-mail
@Test fun `resend again too soon`()            // first 200, second 409 MATCH_RATE_LIMITED, still one e-mail
@Test fun `resend an active match`()           // 409 MATCH_NOT_PROPOSED, no e-mail
@Test fun `resend an expired match`()          // 409 MATCH_NOT_PROPOSED: EXPIRED keeps POST /propose
@Test fun `resend a dismissed match`()         // 409 MATCH_NOT_PROPOSED
@Test fun `resend a match of a closed search`() // match 7, 409 MANDATE_NOT_APPROVED
@Test fun `resend a match of another agent`()  // match 5, 403 MANDATE_ACCESS_DENIED
@Test fun `resend an unknown match`()          // 999, 404 MATCH_NOT_FOUND
```

Assert the e-mail with `smtp.waitForIncomingEmail(5000, 1)`, `smtp.receivedMessages.single()`, and its body containing
`https://www.ndopify.com/match/{id}?token=`.

- [ ] **Step 8: Run the endpoint test**

Run: `cd modules/ndopify-server && mvn test -Dtest=ResendMatchEndpointTest`

Expected: PASS.

- [ ] **Step 9: Document it**

In `modules/ndopify-server/CLAUDE.md`, in the **match** section's **Proposal** paragraph, add the resend endpoint, its
checks and the `MATCH_RATE_LIMITED` code; in **Expiry**, note that a resend of a `PROPOSED` match restarts
`proposedAt`; add `resent_at` (`V12_1`) and `ndopify.match.resend.*`.

- [ ] **Step 10: Verify and commit**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS.

```bash
git add modules/ndopify-server
git commit -m "feat(match): let the agent resend a match link" -m "Refs #177"
```

---

### Task 4: Invite an interested customer to book a visit in the resent e-mail

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/match/proposed.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchProposedMailet.send(matches, customer, agent)` (Task 3 calls it with a single `ACCEPTED` match).
- Produces: the mailet context key `accepted`; the template block `{{#accepted}}`.

- [ ] **Step 1: Write the failing tests**

- `MatchProposedMailetTest.send()`'s expected context map: add `"accepted" to false`.
- New test: `mailet.send(listOf(match.copy(status = MatchStatus.ACCEPTED)), customer, agent)` then assert
  `context()["accepted"] == true` (keep the exact-map style of `send()` if the whole map is asserted).
- `MatchProposedTemplateTest`: a new test renders `context + mapOf("accepted" to true)` and asserts the HTML contains
  `"choisissez un créneau de visite"`; add `assertFalse(html.contains("choisissez un créneau de visite"))` to the
  existing `answered on the match's page` test.

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposedMailetTest,MatchProposedTemplateTest`

Expected: FAIL on the missing key and line.

- [ ] **Step 2: Implement**

In `MatchProposedMailet`, add to the template context:

```kotlin
"accepted" to matches.any { it.status == MatchStatus.ACCEPTED },
```

In `mail/match/proposed.mjml`, after the "J'ai sélectionné pour vous…" text (line 25) and before `{{#listings}}`:

```html
{{#accepted}}
<mj-text>
    Vous avez indiqué que ce bien vous intéresse : choisissez un créneau de visite sur sa page.
</mj-text>
{{/accepted}}
```

- [ ] **Step 3: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposedMailetTest,MatchProposedTemplateTest`

Expected: PASS.

- [ ] **Step 4: Verify and commit**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS.

```bash
git add modules/ndopify-server
git commit -m "feat(match): invite an interested customer to book a visit" -m "Refs #177"
```

---

## Part D — ndopify-partner-central

### Task 5: The "Renvoyer le lien" action

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMatchClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `NdMatchClientTest.kt`, `MatchRulesTest.kt`, `MatchViewsTest.kt`, `MatchControllerTest.kt`,
  `MandateControllerTest.kt`, `MandateTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

**Interfaces:**
- Consumes: `POST /v1/matches/{id}/resend` (Task 3), `ErrorCode.MATCH_RATE_LIMITED` (Task 2), the prototype from Task 1.
- Produces: `NdMatchClient.resend(id: Long)`, `MatchRules.RESENDABLE`, `MatchView.resendable`, `MatchController.RESENT` /
  `RATE_LIMITED`, `POST /mandates/{mandateId}/matches/{matchId}/resend`.

- [ ] **Step 1: Client — failing test, then `resend`**

Add to `NdMatchClientTest`:

```kotlin
@Test
fun resend() {
    server.expect(requestTo("http://localhost:8080/v1/matches/9/resend"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK))

    client.resend(9)
}
```

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdMatchClientTest` — FAIL (no `resend`).
Then add:

```kotlin
/** Sends the match's link again (PROPOSED or ACCEPTED); 409 MATCH_RATE_LIMITED inside the throttle. */
fun resend(id: Long) {
    rest.post().uri("/v1/matches/{id}/resend", id).retrieve().toBodilessEntity()
}
```

Run again — PASS.

- [ ] **Step 2: Rules and view — failing tests, then the flag**

`MatchRules`:

```kotlin
/** A sent match the customer may still be brought back to: pending, or interested without a visit yet. */
val RESENDABLE: Set<MatchStatus> = setOf(MatchStatus.PROPOSED, MatchStatus.ACCEPTED)
```

`MatchView`: add `val resendable: Boolean` after `actionable`.
`MatchViews.view`: `resendable = match.status in MatchRules.RESENDABLE && visit == null`.

Tests:
- `MatchRulesTest`: `RESENDABLE` contains `PROPOSED` and `ACCEPTED`, not `ACTIVE`, `EXPIRED`, `REJECTED`, `DISMISSED`.
- `MatchViewsTest`:
  - a `PROPOSED` match is `resendable`;
  - an `ACCEPTED` match without a visit is `resendable`;
  - an `ACCEPTED` match with a `REQUESTED` visit is not;
  - an `ACTIVE` match is not.

Run: `mvn test -Dtest=MatchRulesTest,MatchViewsTest` — FAIL, then PASS after the implementation.

- [ ] **Step 3: Controller — failing tests, then the action**

`MatchController`:
- constants `RESENT = "resent"` and `RATE_LIMITED = "rate-limited"`;
- `act` gains a `statuses: Set<MatchStatus> = MatchRules.ACTIONABLE` parameter (before the trailing lambda) and uses it
  instead of `MatchRules.ACTIONABLE`;
- `resend` route: `act(mandateId, matchId, RESENT, redirect, MatchRules.RESENDABLE) { client.resend(it) }`;
- the `HttpClientErrorException` catch reads the body:

```kotlin
val code = try { ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code } catch (ignored: Exception) { null }
when {
    code == ErrorCode.MATCH_RATE_LIMITED -> RATE_LIMITED
    ex.statusCode.value() in CHANGED_STATUSES -> CHANGED
    else -> FAILED
}
```

Tests in `MatchControllerTest`:
- `resend a proposed match` → `verify(client).resend(9001)`, outcome `resent`, listing 3012;
- `resend an accepted match` → same, with `MATCH_A.copy(status = ACCEPTED)`;
- `resend an active match` → no call, `changed`;
- `resend - rate limited` → a 409 whose `setBodyConvertFunction` returns `ErrorResponse(Error(code = ErrorCode.MATCH_RATE_LIMITED))`, outcome `rate-limited`;
- `resend - another conflict` → `changed`;
- `resend - server error` → `error`.

Run: `mvn test -Dtest=MatchControllerTest` — FAIL, then PASS.

- [ ] **Step 4: Outcomes and template**

`MandateController.matchOutcome`:
- `RESENT -> "$listing renvoyée ${view.contact?.let { "à ${it.email}" } ?: view.words.to}."` (success);
- `RATE_LIMITED -> "Un lien vient d'être envoyé au client. Réessayez dans quelques minutes."` (warning).

`templates/mandate.html`, after the `data-match-answer` paragraph:

```html
<th:block th:if="${match.resendable}">
    <div class="assignment__actions match__actions" data-match-resend-actions>
        <form method="post"
              th:action="@{/mandates/{mid}/matches/{id}/resend(mid=${m.id},id=${match.id})}">
            <button type="submit" class="btn btn--outline" data-match-resend>Renvoyer le lien<span
                    class="visually-hidden"> au client</span></button>
        </form>
    </div>
</th:block>
```

Tests:
- `MandateControllerTest`: a `resent` flash renders "Annonce n° 3012 renvoyée au client." with `success`; a
  `rate-limited` one renders the throttle message with `warning`.
- `MandateTemplateTest`: `data-match-resend` is present for a `PROPOSED` card and an `ACCEPTED` card without a visit,
  absent for an `ACCEPTED` card with a `REQUESTED` visit and for an `ACTIVE` card.

- [ ] **Step 5: Document, verify and commit**

Update the match page paragraph of `modules/ndopify-partner-central/CLAUDE.md` (the resend action, `RESENDABLE`, the
throttle message).

Run: `cd modules/ndopify-partner-central && mvn verify`

Expected: BUILD SUCCESS.

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): let the agent resend the match link" -m "Refs #177"
```
