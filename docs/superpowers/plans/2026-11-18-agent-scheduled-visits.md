# Agent-scheduled visits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an agent create a `CONFIRMED` visit for an accepted match from the mandate page, and nudge the agent (Telegram, e-mail fallback) once when an accepted match still has no visit after 48 h.

**Architecture:** A new match-scoped endpoint (`POST /v1/matches/{id}/visits`) writes the visit straight to `CONFIRMED` through `VisitService.createForAgent`, reusing the customer-request rules and the scheduling code. The visit keeps a `createdBy` marker that only the customer's confirmation e-mail reads. A sibling of `MatchVisitReminderService` selects idle accepted matches and hands them to a notifier that prefers Telegram. Partner Central adds one action to the match card, reusing its existing slot list and WhatsApp contact button.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway, Thymeleaf, MJML, Maven, ktlint, JaCoCo, JUnit 5 + `kotlin.test` + `mockito-kotlin`.

**Spec:** story [#196](https://github.com/wutsi/ndopify-mono/issues/196) — the acceptance criteria below are its groomed version.

## Global Constraints

- UI text, e-mails and contracts are French; code, comments, logs and docs are English.
- Tests use `kotlin.test` assertions and `mockito-kotlin`; tests mirror the main package.
- Dates and amounts shown to users go through `TenantFormatter`; never a hard-coded pattern or "FCFA".
- Schema changes go in a new Flyway script under `src/main/resources/db/migration/common/` (`V14_0`, `V14_1`).
- A commit touches at most one library module (`modules/ndopify-dto/` here) or none. Library commits: `feat(dto): …` with `Refs #196` in the body.
- Never commit a `-SNAPSHOT` in the root `pom.xml`; the local version bump is uncommitted.
- Every page works without JavaScript: scripts only enhance, and the server re-checks every rule.
- `src/main/html/` is the prototype: never served, never deleted.
- `mvn verify` (ktlint, tests, JaCoCo gate) must be green in every touched module.
- Access order for every endpoint: 404, then 403, before any 409.

## Review Focus

1. **Two requests at once for one match** — the second must answer 409 `VISIT_ALREADY_EXISTS` and send no e-mail: the match is locked before the open-visit check (pinned in Task 3).
2. **Booked less than 24 h ahead** — `reminderSentAt` must be set like `schedule` does, or the customer gets two reminders (pinned in Task 3).
3. **Co-brokerage** — the listing's agent is not `match.agentId`: its call must be 403, and the nudge must go only to the search agent, never carrying the listing agent's customer (pinned in Tasks 3 and 6).
4. **Mandate or listing ended after acceptance** — booking must answer 409 `MANDATE_NOT_APPROVED` / `LISTING_NOT_PUBLISHED` with nothing written, and the nudge must skip it (pinned in Tasks 3 and 6).
5. **Tenant time zone** — a slot posted as `2026-11-21` `10:30` is that wall-clock time in the tenant's zone, never the server's; the BFF lists and the server's `VisitSlots` must agree (pinned in Task 8).

---

### Task 1: The prototype (gate)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify (if needed): `modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css`

**Interfaces:**
- Produces: the prototype the user approves before any DTO/server/BFF code. No code interface.

- [ ] **Step 1: Add an accepted match without a visit to the prototype data**

In `MATCHES[204]` (around line 1783 of `partner-central.js`), add one fixture after `9006`:

```javascript
// Accepted 1 day ago, no visit yet: the agent must phone the client and schedule it.
{
    id: "9007", listingId: "3005", propertyType: "APARTMENT", rooms: 2, neighbourhood: "Bonapriso",
    price: 230000, availableIn: 10, score: 0.9,
    initial: { status: "PROPOSED", sentAt: -2, answer: "ACCEPTED", answeredAt: -1 },
    fit: [[true, "Dans le secteur recherché"], [true, "Dans le budget"], [true, "2 chambres, comme demandé"],
        [true, "Les 3 équipements demandés"], [true, "Disponible avant la date d'emménagement"],
        [true, "Non meublé, comme demandé"]]
},
```

Use an existing listing id of the prototype (pick one not already matched to 204; `3005` if unused, else the next free one) so its card renders a title and a price.

- [ ] **Step 2: Add the action and the slot form to the match template**

In `[data-match-template]` (after the `[data-match-visit]` block), add a block rendered only for an accepted match without a visit:

- a button/link `data-match-schedule` labelled *Planifier une visite*;
- a form `data-match-schedule-form`, hidden by default, with:
  - a `<select data-match-schedule-day>` listing the 7 days from tomorrow,
  - a `<select data-match-schedule-time>` listing 08:00 → 20:00 every 30 min,
  - a type `<select data-match-schedule-type>` with *Sur place* (`ON_SITE`) and *Visite vidéo* (`VIDEO`),
  - a submit *Confirmer la visite* and a link *Annuler*.

The markup and the wording mirror `src/main/html/visit.html`'s slot picker; ids are suffixed with the match id.

- [ ] **Step 3: Wire it in `matchItem`**

In `partner-central.js`'s `matchItem`, after the visit block:

- show the schedule block when `match.status === "ACCEPTED" && !match.visit`;
- fill the day/time selects once (the prototype's `visit.js` has the same lists — reuse them if exported there, else duplicate the two arrays into `partner-central.js` as `SCHEDULE_DAYS` / `SCHEDULE_TIMES`);
- clicking `data-match-schedule` reveals the form;
- submitting simulates `POST /v1/matches/{id}/visits`: `setMatch(match.id, { visit: { at: Date.now() + n * MS, time, type } })` with the chosen slot, then `showMatchFlash("Visite planifiée pour le … : le client a reçu la date, l'adresse et son code.")`.

- [ ] **Step 4: Verify in a browser**

Open `modules/ndopify-partner-central/src/main/html/mandate.html?id=204` (the prototype renders its cards from JavaScript; the no-JavaScript path is the served template's, in Task 8). Check:
- match 9007 shows *Planifier une visite*; 9001/9004/9006 (visits) do not;
- clicking opens the form, and submitting puts a confirmed visit in the card and removes the button.

- [ ] **Step 5: Stop for the user's OK**

Show the files and the URL above. Do not touch any DTO, server or BFF file before the user approves the prototype and this plan. If the feedback changes the design, update this plan, commit it to `main` (`docs(visit): update the agent-scheduled visits plan`) and rebase.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(partner-central): prototype the agent scheduling a visit"
```

---

### Task 2: The agent's visit request DTO

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/CreateMatchVisitRequest.kt`
- Modify (uncommitted): root `pom.xml` — `<ndopify-dto.version>` to the module's `-SNAPSHOT`

**Interfaces:**
- Produces: `CreateMatchVisitRequest(type: VisitType, scheduledAt: Date)`, used by the server endpoint (Task 3) and `NdVisitClient` (Task 7).

- [ ] **Step 1: Write the DTO**

```kotlin
package com.wutsi.ndopify.visit.dto

import java.util.Date

/**
 * The agent books a visit for an accepted match: the type and the slot, on the 7 days starting tomorrow,
 * 08:00-20:00 in the tenant's time zone, on a 30-minute boundary (ndopify-server's `VisitSlots`).
 */
data class CreateMatchVisitRequest(
    val type: VisitType = VisitType.UNKNOWN,
    val scheduledAt: Date = Date(),
)
```

- [ ] **Step 2: Build it locally**

```bash
mvn -pl modules/ndopify-dto install
```

Then set the root `pom.xml`'s `<ndopify-dto.version>` to the DTO's `-SNAPSHOT` (its `pom.xml` version). This edit stays uncommitted.

- [ ] **Step 3: Commit (the module only)**

```bash
git add modules/ndopify-dto
git commit -m "feat(dto): add the agent-scheduled visit request" -m "Refs #196"
```

---

### Task 3: The agent creates a confirmed visit

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/endpoint/MatchVisitEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/domain/VisitEntity.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V14_0__visit-created-by.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CreateMatchVisitEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/visit/CreateMatchVisitEndpoint.sql`

**Interfaces:**
- Consumes: `CreateMatchVisitRequest` (Task 2), `VisitSlots.isValid`, `VisitCodeGenerator`, `VisitPolicyProperties.remindBefore`, `MandateAccessService.ensureCanUpdate`, `MatchService.findById`.
- Produces: `VisitService.createForAgent(matchId: Long, request: CreateMatchVisitRequest): VisitEntity`; `VisitEntity.createdBy: VisitParty`; `POST /v1/matches/{matchId}/visits` returning `CreateVisitResponse(visitId)`; `VisitConfirmedEvent` with the visit in `CONFIRMED`.

- [ ] **Step 1: Write the failing endpoint test**

`CreateMatchVisitEndpointTest` extends `AuthorizationAwareEndpointIntegrationTest`, with:

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CreateMatchVisitEndpoint.sql",
    ],
)
class CreateMatchVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired private lateinit var dao: VisitRepository

    @Test
    fun create() {
        val response = post(1L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(response.body!!.visitId).get()
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(VisitParty.AGENT, visit.createdBy)
        assertEquals(VisitType.ON_SITE, visit.type)
        assertEquals(slot(2, 10, 0), visit.scheduledAt)
        assertTrue(Regex("^\\d{4}$").matches(visit.code!!))
        assertNull(visit.reminderSentAt) // more than 24 h ahead

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "customer + agent e-mails")
        val customer = smtp.receivedMessages.single { "ray.sponsible" in it.allRecipients.single().toString() }
        assertEquals("Ndopify: votre visite est confirmée", customer.subject)
        assertTrue(customer.content.toString().contains(visit.code!!))
        assertTrue(customer.content.toString().contains("a planifié"))
    }
}
```

`post(matchId, request)` posts to `/v1/matches/{matchId}/visits`; `postError` returns `ErrorResponse`. The default caller (`userId = 11L`) is the fixture's agent (it is the default of `ScheduleVisitEndpointTest`). `slot(day, hour, minute)` is the same helper as in `CreateVisitEndpointTest`.

- [ ] **Step 2: Run it and watch it fail**

```bash
cd modules/ndopify-server
mvn test -Dtest=CreateMatchVisitEndpointTest
```

Expected: FAIL — 404, the route doesn't exist.

- [ ] **Step 3: Add the migration and the entity field**

`V14_0__visit-created-by.sql`:

```sql
ALTER TABLE T_VISIT ADD COLUMN created_by VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';
```

In `VisitEntity`, after `type`:

```kotlin
// Who created the visit: the customer's request, or the agent booking it for an accepted match
val createdBy: VisitParty = VisitParty.CUSTOMER,
```

- [ ] **Step 4: Add `createForAgent` to `VisitService`**

Signature and rules (KDoc lists the error codes in check order, like `create`):

```kotlin
/**
 * The agent books a visit for an accepted match, straight to CONFIRMED: locks the match (one booking at a time), then
 * 400 HTTP_INVALID_PARAMETER for an UNKNOWN type, 400 VISIT_INVALID_SLOT for a date outside [VisitSlots], then
 * 409 MATCH_NOT_ACCEPTED, MANDATE_NOT_APPROVED, LISTING_NOT_PUBLISHED, VISIT_WHATSAPP_MISSING (a VIDEO visit needs
 * the customer's WhatsApp number) and VISIT_ALREADY_EXISTS. The code is generated here; a visit booked less than
 * [VisitPolicyProperties.remindBefore] ahead is its own reminder. Publishes [VisitConfirmedEvent]: the customer's
 * e-mail, the co-brokerage notice, never a request notification.
 */
@Transactional
fun createForAgent(matchId: Long, request: CreateMatchVisitRequest): VisitEntity
```

Body reuses `create`'s checks in its order, then saves one entity:

```kotlin
VisitEntity(
    matchId = match.id!!,
    mandateId = match.mandate.id!!,
    listingId = match.listing.id!!,
    agentId = match.agentId,
    customerId = match.mandate.customerId,
    status = VisitStatus.CONFIRMED,
    type = request.type,
    scheduledAt = request.scheduledAt,
    code = codeGenerator.generate(),
    codeAttempts = 0,
    reminderSentAt = now.takeIf { request.scheduledAt.time - now.time < policy.remindBefore.toMillis() },
    createdBy = VisitParty.AGENT,
    createdAt = now,
)
```

and publishes `VisitConfirmedEvent(visitId = saved.id!!, tenantId = match.tenantId!!)`.

- [ ] **Step 5: Add the endpoint**

```kotlin
@RestController
@RequestMapping("/v1/matches")
class MatchVisitEndpoints(
    private val service: VisitService,
    private val matchService: MatchService,
    private val mandateService: MandateService,
    private val mandateAccess: MandateAccessService,
) {
    // The match's agent (or an admin) books the visit: nobody books it for another agent's customer
    @PostMapping("/{id:\\d+}/visits")
    fun create(@PathVariable id: Long, @RequestBody @Valid request: CreateMatchVisitRequest): CreateVisitResponse {
        val match = matchService.findById(id)
        mandateAccess.ensureCanUpdate(mandateService.findById(match.mandate.id!!))
        return CreateVisitResponse(visitId = service.createForAgent(id, request).id!!)
    }
}
```

`matchService.findById` throws 404 `MATCH_NOT_FOUND`; `ensureCanUpdate` throws 403 `MANDATE_ACCESS_DENIED` (admin allowed, like `schedule`).

- [ ] **Step 6: Run the endpoint test**

```bash
mvn test -Dtest=CreateMatchVisitEndpointTest
```

Expected: PASS.

- [ ] **Step 7: Add the error and access tests**

In the same class, one test each, asserting the code and that `dao.findAll()` gained nothing:

- `admin` — override `roles = arrayOf(RoleCode.ADMIN)` → 200.
- `another agent` — `userId` of the listing's agent / another member → 403 `MANDATE_ACCESS_DENIED`.
- `no token` — `anonymousUser = true` → 401 `AUTH_UNAUTHORIZED`.
- `match not found` (999) → 404 `MATCH_NOT_FOUND`.
- `match not accepted` (fixture match 4) → 409 `MATCH_NOT_ACCEPTED`.
- `mandate not approved`, `listing not published` → 409 with their codes (fixtures in `CreateMatchVisitEndpoint.sql`).
- `video without whatsapp` (fixture match 7) → 409 `VISIT_WHATSAPP_MISSING`.
- `already an open visit` (fixture match 2) → 409 `VISIT_ALREADY_EXISTS`, and exactly the e-mails of the first test's count (no second one).
- `unknown type` → 400 `HTTP_INVALID_PARAMETER`.
- `invalid slots` — the same five slots as `CreateVisitEndpointTest` → 400 `VISIT_INVALID_SLOT` each.
- `less than 24 h ahead` — covered by the service unit test in Step 8 with a fixed clock; the endpoint test keeps `assertNull(visit.reminderSentAt)` for a slot more than 24 h ahead.

- [ ] **Step 8: Add the service unit tests**

In `VisitServiceTest`, with a fixed `Clock`:

- `createForAgent` writes `CONFIRMED`, `createdBy = AGENT`, `agentId`/`customerId` from the match, and publishes `VisitConfirmedEvent` only.
- the error order: UNKNOWN type before an invalid slot before `MATCH_NOT_ACCEPTED`.
- a slot within `policy.remindBefore` sets `reminderSentAt`; a later one leaves it null.

- [ ] **Step 9: Verify and commit**

```bash
mvn verify
git add modules/ndopify-server
git commit -m "feat(visit): let the agent schedule a visit for an accepted match" -m "Refs #196"
```

---

### Task 4: The customer's e-mail when the agent booked

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/confirmed.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedTemplateTest.kt`

**Interfaces:**
- Consumes: `VisitEntity.createdBy` (Task 3).
- Produces: the template context flag `by_agent: Boolean` on `/mail/visit/confirmed.mjml`.

- [ ] **Step 1: Write the failing template test**

In `VisitConfirmedTemplateTest`, a visit with `createdBy = VisitParty.AGENT` renders:

```kotlin
assertTrue(body.contains("a planifié votre visite"))
assertTrue(body.contains("Vous pouvez l'annuler"))
```

and the existing `CUSTOMER` case keeps "Votre visite du bien … est confirmée".

- [ ] **Step 2: Run it and watch it fail**

```bash
mvn test -Dtest=VisitConfirmedTemplateTest
```

Expected: FAIL — the wording is absent.

- [ ] **Step 3: Add the context flag and the conditional wording**

In `VisitConfirmedMailet`'s customer context, add `"by_agent" to (visit.createdBy == VisitParty.AGENT)`.

In `confirmed.mjml`, replace the single confirmation paragraph with two variants:

```html
{{#by_agent}}
<mj-text>
    Votre agent <strong>{{agent_full_name}}</strong> a planifié votre visite du bien
    <strong>{{listing_title}}</strong> le <strong>{{visit_date}} à {{visit_time}}</strong>.
</mj-text>
{{/by_agent}}
{{^by_agent}}
<mj-text>
    Votre visite du bien <strong>{{listing_title}}</strong> est confirmée le
    <strong>{{visit_date}} à {{visit_time}}</strong>.
</mj-text>
{{/by_agent}}
```

After the code paragraphs, add (only `by_agent`):

```html
{{#by_agent}}
<mj-text>
    Cette visite a été planifiée par votre agent. Vous pouvez l'annuler à tout moment depuis votre page de visite.
</mj-text>
{{/by_agent}}
```

- [ ] **Step 4: Run the template and mailet tests**

```bash
mvn test -Dtest='VisitConfirmed*'
```

Expected: PASS, both variants.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(visit): tell the customer its agent scheduled the visit" -m "Refs #196"
```

---

### Task 5: The agent nudge message

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/agent_visit_nudge.mjml`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitAgentNudgeNotifier.kt`
- Test: `.../match/server/mail/MatchVisitAgentNudgeMailetTest.kt`
- Test: `.../match/server/mail/MatchVisitAgentNudgeTemplateTest.kt`
- Test: `.../match/server/bot/MatchVisitAgentNudgeBotletTest.kt`
- Test: `.../match/server/service/MatchVisitAgentNudgeNotifierTest.kt`

**Interfaces:**
- Consumes: `AgentTelegramService.chatId`, `TelegramClient`, `MailService`, `TenantFormatter`, `MandateMailLabels`, `TenantService`.
- Produces: `MatchVisitAgentNudgeNotifier.send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity): Boolean` — true when it reached the agent (Telegram or e-mail), false when there is no channel (no chat and a blank e-mail), throws only when the fallback send fails.

- [ ] **Step 1: Write the failing botlet test**

`MatchVisitAgentNudgeBotletTest` (mirror `MatchAnsweredBotletTest`): with a tenant whose `partnerCentralUrl` is `https://partner.ndopify.com` and a customer with WhatsApp `+237 6 99 00 00 01`, `send` sends one Telegram message that:

- names the agent and the customer;
- contains the listing title;
- has the keyboard `["Contacter sur WhatsApp" → "https://wa.me/237699000001", "Planifier la visite" → "https://partner.ndopify.com/mandates/204#match-9001"]`.

A tenant without `partnerCentralUrl` has only the WhatsApp button; a customer without a number has only *Planifier la visite*; neither leaves an empty keyboard.

- [ ] **Step 2: Run it and watch it fail**

```bash
mvn test -Dtest=MatchVisitAgentNudgeBotletTest
```

Expected: FAIL — class not found.

- [ ] **Step 3: Write the botlet**

```kotlin
@Service
class MatchVisitAgentNudgeBotlet(
    private val tenantService: TenantService,
    private val client: TelegramClient,
) {
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long)
}
```

Text (French, plain, no markdown): greeting with the agent's full name, "<customer> a accepté votre proposition pour <listing title> il y a plus de 48 h, mais aucune visite n'est planifiée.", "Appelez-le sur WhatsApp pour convenir d'un créneau, puis planifiez la visite.", closing "— Ndopify". Buttons as in Step 1; the WhatsApp digits are `customer.whatsappNumber.orEmpty().filter(Char::isDigit)`.

- [ ] **Step 4: Write the failing mailet/template test**

`MatchVisitAgentNudgeTemplateTest` renders the template with the same context and asserts the customer name, the listing title, the price, and both links (the `wa.me` one and the partner-central one). `MatchVisitAgentNudgeMailetTest` asserts the recipient is the agent's party e-mail, the subject, and that the sender context is built (`AgentMailContext`).

- [ ] **Step 5: Write the mailet and the template**

`MatchVisitAgentNudgeMailet` mirrors `MatchVisitReminderMailet`: subject `"{{tenant_name}}: {{customer_name}} attend une visite"`, template `/mail/match/agent_visit_nudge.mjml`, context `recipient_name`, `customer_name`, `listing_title`, `price_label`, `price`, `image_url`, `whatsapp_url` (null without a number), `visit_url` (`{partnerCentralUrl}/mandates/{mandateId}#match-{matchId}`, null without a URL) + `AgentMailContext.of(agent, formatter)`.

The MJML mirrors `visit_reminder.mjml`: the intro, the listing's cover and its title/price, then two `mj-button`s (`Contacter sur WhatsApp` when `whatsapp_url`, `Planifier la visite` when `visit_url`), guarded by `{{#…}}`, then the agent's signature footer.

- [ ] **Step 6: Write the failing notifier test and the notifier**

Test cases (mirror `MatchAnsweredNotifierTest`):

- a linked chat → the botlet only;
- no chat → the mailet;
- the botlet throws → the mailet, and `send` returns true;
- the mailet throws after a Telegram failure → `send` throws (the caller leaves it for the next run);
- no chat and a blank agent e-mail → no send, returns false.

```kotlin
@Service
class MatchVisitAgentNudgeNotifier(
    private val botlet: MatchVisitAgentNudgeBotlet,
    private val mailet: MatchVisitAgentNudgeMailet,
    private val telegramService: AgentTelegramService,
) {
    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity): Boolean
}
```

Log a Telegram failure without the bot token nor the URL, like `MatchAnsweredNotifier`.

- [ ] **Step 7: Verify and commit**

```bash
mvn verify
git add modules/ndopify-server
git commit -m "feat(match): nudge an agent about an accepted match without a visit" -m "Refs #196"
```

---

### Task 6: The nudge job

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitAgentNudgeService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V14_1__match-agent-visit-nudge.sql`
- Test: `.../match/server/service/MatchVisitAgentNudgeServiceTest.kt`
- Test: `.../match/server/job/NudgeAcceptedMatchesJobTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/match/NudgeAcceptedMatchesJob.sql`

**Interfaces:**
- Consumes: `MatchVisitAgentNudgeNotifier.send` (Task 5), `MatchVisitReminderService.OPEN_VISIT_STATUSES`.
- Produces: `MatchVisitAgentNudgeService.nudgeDue(): Int`; `MatchJobs.nudgeAcceptedWithoutVisitAgent()`; `MatchEntity.agentVisitNudgedAt: Date?`.

- [ ] **Step 1: Write the failing service test**

`MatchVisitAgentNudgeServiceTest`, mirroring `MatchVisitReminderServiceTest` with a fixed clock and `delayHours = 48`:

- a due accepted match → `notifier.send`, then `dao.save(match.copy(agentVisitNudgedAt = now, modifiedAt = now))`, returns 1;
- an open visit → no send, no save;
- already nudged → no send;
- changed while selecting (REJECTED) → no send;
- a notifier returning false → the match is still marked (no channel: nothing to retry);
- a notifier throwing → nothing saved, returns 0 (retry next run).

- [ ] **Step 2: Run it and watch it fail**

```bash
mvn test -Dtest=MatchVisitAgentNudgeServiceTest
```

Expected: FAIL — class not found.

- [ ] **Step 3: Add the migration, the entity field and the query**

`V14_1__match-agent-visit-nudge.sql`:

```sql
ALTER TABLE T_MATCH ADD COLUMN agent_visit_nudged_at DATETIME DEFAULT NULL;
ALTER TABLE T_MATCH ADD INDEX IDX_MATCH_agent_visit_nudge (status, agent_visit_nudged_at);
```

`MatchEntity`, after `visitRemindedAt`:

```kotlin
// When the agent was nudged that its accepted match still has no visit (MatchVisitAgentNudgeService)
val agentVisitNudgedAt: Date? = null,
```

`MatchRepository`, after `findVisitReminderDue` — the same query with the agent's marker:

```kotlin
@Query(
    "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.agentVisitNudgedAt IS NULL " +
        "AND m.answeredAt <= :before AND m.mandate.status = :mandateStatus " +
        "AND m.listing.status = :listingStatus " +
        "AND NOT EXISTS (SELECT v.id FROM VisitEntity v WHERE v.matchId = m.id AND v.status IN :visitStatuses) " +
        "ORDER BY m.answeredAt ASC, m.id ASC"
)
fun findAgentVisitNudgeDue(
    @Param("status") status: MatchStatus,
    @Param("before") before: Date,
    @Param("mandateStatus") mandateStatus: MandateStatus,
    @Param("listingStatus") listingStatus: ListingStatus,
    @Param("visitStatuses") visitStatuses: Collection<VisitStatus>,
    page: Pageable,
): List<MatchEntity>
```

- [ ] **Step 4: Write the service**

```kotlin
@Service
class MatchVisitAgentNudgeService(
    private val dao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val notifier: MatchVisitAgentNudgeNotifier,
    private val tx: TransactionTemplate,
    private val clock: Clock,
    @param:Value("\${ndopify.match.agent-visit-nudge.delay-hours}") private val delayHours: Long,
    @param:Value("\${ndopify.match.agent-visit-nudge.batch-size}") private val batchSize: Int,
) {
    /** Returns the number of agents reached. */
    fun nudgeDue(): Int
}
```

Select with `MatchVisitReminderService.OPEN_VISIT_STATUSES` and `before = now - delayHours hours`, then, per match, in its own transaction: reload, re-check (ACCEPTED, `agentVisitNudgedAt == null`, APPROVED, PUBLISHED, no open visit), `notifier.send`, mark `agentVisitNudgedAt = now` / `modifiedAt = now`. An `ObjectOptimisticLockingFailureException` skips the match; any other exception logs and leaves it unmarked.

- [ ] **Step 5: Run the service test**

```bash
mvn test -Dtest=MatchVisitAgentNudgeServiceTest
```

Expected: PASS.

- [ ] **Step 6: Add the job and the config**

`MatchJobs`:

```kotlin
private val agentNudgeService: MatchVisitAgentNudgeService

/** Nudges the agents whose accepted match still has no visit after 48 h. */
@Scheduled(cron = "\${ndopify.match.agent-visit-nudge.cron}")
fun nudgeAcceptedWithoutVisitAgent() {
    forEachTenant("nudgeAcceptedWithoutVisitAgent", "match(es)") { agentNudgeService.nudgeDue() }
}
```

`application.yml`, under `ndopify.match`:

```yaml
agent-visit-nudge:
    delay-hours: 48               # An ACCEPTED match with no visit nudges its agent after this delay
    batch-size: 100               # Matches examined per run
    cron: "0 30 9 * * ?"          # Every day at 09:30, after the customer reminder (09:00)
```

- [ ] **Step 7: Write the failing job test**

`NudgeAcceptedMatchesJobTest` extends `BaseEndpointIntegrationTest`, `@Sql` on `NudgeAcceptedMatchesJob.sql`, then:

```kotlin
jobs.nudgeAcceptedWithoutVisitAgent()

assertTrue(smtp.waitForIncomingEmail(5000, 2), "one e-mail per idle accepted match")
assertNotNull(agentVisitNudgedAt(1L))  // nudged
assertNull(agentVisitNudgedAt(2L))     // accepted 2 days ago
assertNull(agentVisitNudgedAt(3L))     // open visit
assertNull(agentVisitNudgedAt(5L))     // listing rented
jobs.nudgeAcceptedWithoutVisitAgent()
assertFalse(smtp.waitForIncomingEmail(1000, 3), "no second nudge")
```

The Telegram path is covered by `MatchVisitAgentNudgeNotifierTest` (Task 5), not a second endpoint test.

- [ ] **Step 8: Verify and commit**

```bash
mvn verify
git add modules/ndopify-server
git commit -m "feat(match): nudge the agent when an accepted match has no visit" -m "Refs #196"
```

---

### Task 7: Partner Central reads the scheduling state

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`

**Interfaces:**
- Consumes: `CreateMatchVisitRequest` (Task 2).
- Produces: `NdVisitClient.createForMatch(matchId: Long, request: CreateMatchVisitRequest)`; `MatchView.schedulable: Boolean`.

- [ ] **Step 1: Write the failing client test**

In `NdVisitClientTest`, one case: `createForMatch(9001, CreateMatchVisitRequest(ON_SITE, at))` sends `POST /v1/matches/9001/visits` with `{"type":"ON_SITE","scheduledAt":"…"}` against `MockRestServiceServer`, and a 409 surfaces as `HttpClientErrorException`.

- [ ] **Step 2: Add the client method**

```kotlin
/** Books a visit for an accepted match; the agent picks the type and the slot. */
fun createForMatch(matchId: Long, request: CreateMatchVisitRequest) {
    rest.post().uri("/v1/matches/{id}/visits", matchId).body(request).retrieve().toBodilessEntity()
}
```

- [ ] **Step 3: Write the failing views test**

In `MatchViewsTest`:

- an ACCEPTED match with no visit → `schedulable == true` (and `resendable == true`);
- an ACCEPTED match with a visited card → `schedulable == false`;
- a PROPOSED and an ACTIVE match → `false`.

- [ ] **Step 4: Add the flag**

`MatchView`, after `resendable`:

```kotlin
/** ACCEPTED and no open visit: the agent may book it (the card's *Planifier une visite*). */
val schedulable: Boolean = false,
```

`MatchViews.view`: `schedulable = match.status == MatchStatus.ACCEPTED && visit == null` (`visit` is the open visit resolved by `MandateController`).

- [ ] **Step 5: Verify and commit**

```bash
mvn verify
git add modules/ndopify-partner-central
git commit -m "feat(partner-central): flag the accepted matches the agent may book" -m "Refs #196"
```

---

### Task 8: Partner Central schedules the visit

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchVisitForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `.../ui/match/MatchVisitFormTest.kt`
- Test: `.../ui/match/MatchControllerTest.kt`
- Test: `.../ui/mandate/MandateControllerTest.kt`
- Test: `.../ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchView.schedulable` (Task 7), `NdVisitClient.createForMatch`, `VisitSlots.days/times/at`.
- Produces: `MatchVisitForm(date, time, type).toRequest(tenant): CreateMatchVisitRequest?`; `POST /mandates/{mandateId}/matches/{matchId}/visit` with the flash `match` = `scheduled` | `invalid-slot` | `whatsapp-missing` | `changed` | `error`; `GET /mandates/{id}?schedule={matchId}` opens the card's form.

- [ ] **Step 1: Write the failing form test**

`MatchVisitFormTest`:

- a valid `date=2026-11-21`, `time=10:30`, `type=VIDEO` → `CreateMatchVisitRequest(VIDEO, <that instant in Africa/Douala>)`;
- a missing/blank date, time or type → null;
- `type=UNKNOWN` → null;
- a malformed date or time → null.

- [ ] **Step 2: Write the form**

```kotlin
/** The match card's schedule form: a day and a time from the lists, and the visit's type. A hand-edited value that can't be parsed is null. */
data class MatchVisitForm(
    val date: String? = null,
    val time: String? = null,
    val type: String? = null,
) {
    fun toRequest(tenant: Tenant): CreateMatchVisitRequest?
}
```

Mirror `VisitScheduleForm.toRequest`, except that the type is required (`ON_SITE` or `VIDEO`, never a fallback).

- [ ] **Step 3: Write the failing controller tests**

In `MatchControllerTest` (the controller now takes `NdVisitClient` and `TenantContext`; `setUp` mocks them):

- `visit` — `post("/mandates/204/matches/9001/visit").param("date", "2026-11-21")…` with `MATCH_A` ACCEPTED → `verify(visitClient).createForMatch(9001, request)`, redirect `/mandates/204#matches-title`, flash `scheduled`;
- a 400 `VISIT_INVALID_SLOT` → `invalid-slot`;
- a 409 `VISIT_WHATSAPP_MISSING` → `whatsapp-missing`;
- a 409 `VISIT_ALREADY_EXISTS`, a 403 and a 404 → `changed`;
- a non-numeric id or a match of another mandate / not ACCEPTED → `changed` without a call;
- a 5xx → `error`.

In `MandateControllerTest` (or `MandateTemplateTest`): `?schedule=9001` is passed to the view for a schedulable match only; a hand-edited id of a match with a visit or another status is ignored.

- [ ] **Step 4: Add the controller action**

In `MatchController`, add `private val visitClient: NdVisitClient` and `private val tenantContext: TenantContext`, then:

```kotlin
@PostMapping("/visit")
fun visit(
    @PathVariable mandateId: String,
    @PathVariable matchId: String,
    @ModelAttribute form: MatchVisitForm,
    redirect: RedirectAttributes,
): String
```

Read the match first (like `act`): a bad id, another mandate, or a status other than `ACCEPTED` is `CHANGED` with no call; a request that doesn't parse is `INVALID_SLOT` (the form's own error); otherwise call `visitClient.createForMatch`. Map the server's errors as in Step 3. Add the outcome constants `SCHEDULED = "scheduled"`, `INVALID_SLOT = "invalid-slot"`, `WHATSAPP_MISSING = "whatsapp-missing"`. Redirect to `/mandates/$mid#matches-title` and flash `LISTING` as today.

- [ ] **Step 5: Add the mandate page's model**

In `MandateController.show`, accept `@RequestParam(required = false) schedule: String?`, then:

```kotlin
model.addAttribute("scheduling", scheduling(section, schedule))
if (section.matches.any { it.schedulable }) {
    model.addAttribute("scheduleDays", VisitSlots.days(today(), formatter()))
    model.addAttribute("scheduleTimes", VisitSlots.times())
}
```

`scheduling(section, schedule)` returns the id only when that match is `schedulable`, like `dismissing`.

Extend `matchOutcome`:

```kotlin
MatchController.SCHEDULED ->
    "Visite planifiée : le client a reçu par e-mail la date, l'adresse et son code."
MatchController.INVALID_SLOT -> "Ce créneau n'est plus disponible. Choisissez-en un autre."
MatchController.WHATSAPP_MISSING -> "Le client n'a pas de numéro WhatsApp : choisissez une visite sur place."
```

with `warning` for the last two and `success` for `scheduled`.

- [ ] **Step 6: Add the template**

In `templates/mandate.html`'s match card, after the visit block:

```html
<!-- ACCEPTED and no open visit: the agent phones the client, then books the slot. No JavaScript needed:
     the button reloads the page with ?schedule={matchId}, which opens this form. -->
<div class="match__schedule" th:if="${match.schedulable}" data-match-schedule-block>
    <a class="btn btn--primary" data-match-schedule
       th:href="@{|/mandates/${m.id}?schedule=${match.id}#match-${match.id}-title|}">Planifier une visite</a>
    <form method="post" data-match-schedule-form
          th:action="@{/mandates/{mid}/matches/{id}/visit(mid=${m.id},id=${match.id})}"
          th:attr="hidden=${scheduling == match.id} ? null : 'hidden'">
        <select name="date" data-match-schedule-day aria-label="Jour">
            <option th:each="day : ${scheduleDays}" th:value="${day.value}" th:text="${day.label}">sam. 21 novembre 2026</option>
        </select>
        <select name="time" data-match-schedule-time aria-label="Heure">
            <option th:each="time : ${scheduleTimes}" th:value="${time.first}" th:text="${time.second}">10h30</option>
        </select>
        <select name="type" data-match-schedule-type aria-label="Type de visite">
            <option value="ON_SITE">Sur place</option>
            <option value="VIDEO">Visite vidéo</option>
        </select>
        <button type="submit" class="btn btn--primary">Confirmer la visite</button>
        <a class="btn btn--outline" th:href="@{|/mandates/${m.id}#match-${match.id}-title|}">Annuler</a>
    </form>
</div>
```

Keep the existing `match__actions` block for ACTIVE / EXPIRED untouched.

- [ ] **Step 7: Write the failing template test**

In `MandateTemplateTest`: with match 9001 ACCEPTED and no visit, the card has one `[data-match-schedule]` linking to `/mandates/204?schedule=9001#match-9001-title`, a hidden form posting to `/mandates/204/matches/9001/visit`, 7 day options and 25 time options. With a visit on 9001 the block is absent. With `?schedule=9001` the form is not hidden. A landlord's mandate has none.

- [ ] **Step 8: Run the BFF tests**

```bash
mvn test -Dtest='Match*Test,MandateTemplateTest,MandateControllerTest'
```

Expected: PASS.

- [ ] **Step 9: Verify and commit**

```bash
mvn verify
git add modules/ndopify-partner-central
git commit -m "feat(partner-central): schedule a visit from an accepted match" -m "Refs #196"
```

---

### Task 9: The JavaScript enhancement, the docs and the final check

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js`
- Modify: `modules/ndopify-server/CLAUDE.md`
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

**Interfaces:**
- Consumes: Task 8's `data-match-schedule` / `data-match-schedule-form` hooks.

- [ ] **Step 1: Enhance the page**

In `mandate.js`'s match-card setup (the same place as the dismiss confirmation): a click on `[data-match-schedule]` prevents the default, clears `hidden` on that card's `[data-match-schedule-form]`, hides the button, and focuses the first select; *Annuler* hides the form and shows the button again. No rule is re-implemented: the server checks the slot and the type.

- [ ] **Step 2: Document the server**

In `modules/ndopify-server/CLAUDE.md`'s **visit** section:

- the agent (or an admin) books a visit for an ACCEPTED match with no open visit, `POST /v1/matches/{id}/visits` (`CreateMatchVisitRequest`): same checks as the customer's request plus `schedule`'s slot and code, written straight to `CONFIRMED` with `createdBy = AGENT`; no *visite demandée* message;
- the customer's e-mail says the agent planned it and can be cancelled;
- `MatchJobs.nudgeAcceptedWithoutVisitAgent` (daily, `ndopify.match.agent-visit-nudge.*`): an ACCEPTED match with no open visit, answered at least `delay-hours` (48) ago, not nudged yet → the agent over Telegram (`MatchVisitAgentNudgeBotlet`) or by e-mail (`MatchVisitAgentNudgeMailet`), once, marked only after the send.

- [ ] **Step 3: Document Partner Central**

In `modules/ndopify-partner-central/CLAUDE.md`'s *Biens correspondants* paragraph: an ACCEPTED card without an open visit shows *Planifier une visite* → the slot form (no JS: `GET /mandates/{id}?schedule={matchId}`), `POST /mandates/{mid}/matches/{id}/visit` → `NdVisitClient.createForMatch`, outcomes `scheduled` / `invalid-slot` / `whatsapp-missing` / `changed` / `error`; the mandate page's existing WhatsApp button is unchanged.

- [ ] **Step 4: Final verification**

```bash
cd modules/ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
cd ../..
git status   # only the root pom.xml's -SNAPSHOT property may differ
```

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(visit): record the agent-scheduled visit and its nudge" -m "Refs #196"
```

---

## Self-review notes

- **Spec coverage:** agent create (T3), CONFIRMED + code + e-mails (T3/T4), 48 h once nudge over Telegram with e-mail fallback (T5/T6), card action with the existing WhatsApp button untouched (T8), prototype first (T1), no DTO `createdBy` (T3), docs (T9). Out of scope stays out: the listing-page flow, any WhatsApp API, the customer's own booking.
- **Library release:** only `ndopify-dto` changes (T2); `push` releases it, then bumps the root `pom.xml`.
- **Coverage:** every behavior change has a test; the prototype is static and is the one deliverable without one.
