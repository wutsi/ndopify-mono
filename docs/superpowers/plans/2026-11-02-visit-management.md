# Visit Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A customer who accepted a match asks to visit the listing, on site or by a WhatsApp video tour. The agent
confirms a date, then checks the visit in with the customer's code and checks it out afterwards.

**Architecture:** A new `visit` domain in ndopify-server (`VisitEntity`, `T_VISIT`) that depends on `match`,
`listing` and `mandate`, never the reverse. E-mails only, no SMS. The visit is free: there is no fee and no payment.
The customer uses the public portal (ndopify-web, `/visits/{id}?token=`), and the agent uses Partner Central
(`/visits/{id}`). Each web app gets its own plan once its prototype is approved (see *Out*).

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway (MySQL), MJML + Mustache e-mails.

**Spec:** this document's *Design* section (decisions of 2026-11-02; payments dropped the same day). The prototypes:
`modules/ndopify-web/src/main/html/visite.html` (customer), the visit popup of `proposition.html`, and
`modules/ndopify-partner-central/src/main/html/visit.html` (agent), plus the *Visites* section of Partner Central's
`index.html`.

## Design

### Lifecycle

```
REQUESTED ─schedule─▶ CONFIRMED ─check-in (code)─▶ CHECKED_IN ─check-out─▶ COMPLETED
    │                    │
    ├▶ CANCELLED         ├▶ CANCELLED
    └▶ EXPIRED (not      ├▶ NO_SHOW (customer: absent / refused the code)
       scheduled in 48 h)└▶ NO_SHOW (agent: no check-in 4 h after the time)
```

### Rules

| Rule | Value |
|---|---|
| Who requests | The mandate's customer (JWT party = customer's party), for a match `ACCEPTED`, with the mandate `APPROVED` and the listing `PUBLISHED`. At most one open visit per match (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`). |
| Type | `ON_SITE` (the customer comes to the property) or `VIDEO` (the agent, at the property, calls the customer by WhatsApp video at the time set, on the customer's `whatsappNumber`). Chosen by the customer; a `VIDEO` request from a customer without `whatsappNumber` is 409 `VISIT_WHATSAPP_MISSING`. Everything else (code, check-in with position, no-show, check-out, feedback) is the same for both. |
| Requested slot | `requestedAt`: a date and time on one of the 7 days starting tomorrow, 08:00–20:00 in the tenant's `timeZone`, on a 30-minute boundary. An optional `note` (≤ 500). |
| Who schedules / checks in / checks out | The mandate's agent (`match.agentId`). Schedule is also open to an admin; check-in, no-show and check-out never are. |
| Schedule | `scheduledAt` and `type` (each may differ from the request; `VIDEO` needs the customer's `whatsappNumber`), same slot rules, checked against the time of the schedule → `CONFIRMED`, the code generated. |
| Not scheduled | `REQUESTED` for 48 h → `EXPIRED` (job). |
| Visit code | 4 digits (`SecureRandom`), generated on `CONFIRMED`, stored, returned **only** to the visit's customer (`Visit.code` is null for anyone else, incl. the agent and admins). |
| Check-in | `CONFIRMED`, from `scheduledAt` − 1 h to `scheduledAt` + 4 h. The agent enters the code (`VIDEO`: the customer reads it out during the call). A wrong code is 409 `VISIT_CODE_INVALID`, `codeAttempts` + 1; at 5 attempts, 409 `VISIT_CODE_LOCKED` for good (the agent marks a no-show or contacts support). The position is optional (no JS, no GPS): its distance to the listing's position is stored (`checkInDistanceMeters`), never refused. Over 300 m it's flagged (`checkInFlagged`). |
| Customer no-show | `POST /no-show` by the agent, `CONFIRMED`, from `scheduledAt` + 15 min to + 4 h, reason `CUSTOMER_ABSENT` (`VIDEO`: doesn't answer the call) or `CODE_REFUSED`, position as check-in. |
| Agent no-show | `CONFIRMED` 4 h after `scheduledAt` without check-in → `NO_SHOW`, `noShowParty = AGENT` (job). |
| Check-out | `CHECKED_IN`, at least 10 minutes after check-in, a `comment` (required, ≤ 2000, private to the agent and admins), position optional as check-in → `COMPLETED`. |
| Feedback | The customer, `COMPLETED`, once: `interest` `INTERESTED` / `NOT_INTERESTED`, `reasonCode` (`MatchRejectionReason`, when not interested), `comment` (≤ 1000). |
| Cancel | The customer or the agent (or an admin for the agent), `REQUESTED` or `CONFIRMED`, any time before check-in. `reason` ≤ 500. |
| Address | The exact address (the listing's `landmark`, `accessDirections` and `latitude` / `longitude`) only in the confirmed e-mail and in `Visit` for its customer once an `ON_SITE` visit is `CONFIRMED`; never for a `VIDEO` one. |

### E-mails (French, dates through `TenantFormatter`, `mail/visit/`)

| Event | To | Template | Content |
|---|---|---|---|
| Requested | agent | `requested.mjml` | Customer, listing, requested slot, note, link `{partnerCentralUrl}/visits/{id}` |
| Confirmed | customer | `confirmed.mjml` | Type, date and time, address (`ON_SITE`) or "Serge vous appelle en vidéo sur WhatsApp au …" (`VIDEO`), agent's name and WhatsApp, **the code**, link `{publicPortalUrl}/visits/{id}?token=` |
| Confirmed | agent | `confirmed_agent.mjml` | Type, date and time, customer's name and WhatsApp, "ask the customer for the code" (`VIDEO`: "call the customer by WhatsApp video from the property"), link |
| Reminder (24 h before) | customer, agent | `reminder.mjml`, `reminder_agent.mjml` | As confirmed. Not sent when confirmed less than 24 h before: the confirmed e-mail is the reminder (`reminderSentAt` set then) |
| Completed | customer | `completed.mjml` | "Le bien vous intéresse ?", link |
| Cancelled / expired / no-show | the other side, or both for a job | `cancelled.mjml`, `expired.mjml`, `no_show.mjml` | What happened, a link to ask for another visit |

The customer's links carry a `public-portal` token from `MatchLinkTokenService.create(customer)` (no link without one).

## Global Constraints

- **Who acts:** request, feedback and cancel-as-customer are for the mandate's customer only
  (`MandateAccessService.ensureIsCustomer`); no admin bypass, the agent can't act for the customer. Check-in,
  check-out and no-show are for the visit's agent only (JWT party = agent's party); no admin bypass. Schedule and
  cancel-as-agent: the agent or an admin (`MandateAccessService.ensureCanUpdate`).
- Error order: 404 `VISIT_NOT_FOUND`, 401 `AUTH_UNAUTHORIZED` / 403 `VISIT_ACCESS_DENIED`, 400, then the 409s.
- New error codes: `VISIT_NOT_FOUND`, `VISIT_ACCESS_DENIED`, `VISIT_ALREADY_EXISTS`, `VISIT_INVALID_SLOT`,
  `VISIT_NOT_REQUESTED`, `VISIT_NOT_CONFIRMED`, `VISIT_NOT_CHECKED_IN`, `VISIT_NOT_COMPLETED`,
  `VISIT_OUTSIDE_WINDOW`, `VISIT_CODE_INVALID`, `VISIT_CODE_LOCKED`, `VISIT_FEEDBACK_ALREADY_SENT`,
  `VISIT_CANNOT_BE_CANCELLED`, `VISIT_VERSION_CONFLICT`, `VISIT_WHATSAPP_MISSING`, `MATCH_NOT_ACCEPTED`.
- `VisitEntity`: `@TenantId`, `@Version`. Enums through `EnumConverters`. `T_VISIT` goes in `db/test/clean.sql`
  before `T_MATCH`.
- One new migration, `V10_0__visit.sql`; never edit an applied one.
- Times are stored in UTC and compared with the injected `Clock`. The slot rules use the tenant's `timeZone`.
- A job loops over the active tenants with `TenantContext`, each visit in its own transaction, a `@Version`
  conflict skipped (like `MatchProposalService.expireDue`).
- E-mails follow *Asynchronous e-mails* (event with ids + `tenantId`, `@Async @TransactionalEventListener(AFTER_COMMIT)`).
- DTO changes need a DTO release before the server deploys (`ndopify-dto.version` = next `-SNAPSHOT` while developing).
- Never log the visit code or a token.

## Review Focus

1. **The agent reads the code** (`GET /v1/visits/{id}` or a search as the agent or an admin) → `code` is null. Only
   the customer's `GET` returns it, and never the address of a VIDEO visit (Task 3 get test).
2. **Brute-forcing the code**: the 5th wrong code locks check-in, a right code after that is still 409
   `VISIT_CODE_LOCKED` (Task 5 check-in test).
3. **Two requests for the same match** (double tap): the second is 409 `VISIT_ALREADY_EXISTS`, one visit, one
   e-mail (Task 3 create test, the match locked while checking).
4. **A visit scheduled less than 24 h ahead** gets one e-mail with the code, not a second "reminder"
   (Task 7 reminder test).
5. **Check-in after a cancel by the customer** (the agent's page is stale) → 409 `VISIT_NOT_CONFIRMED`, the visit
   stays CANCELLED (Task 5 check-in test).

---

### Task 1: DTO

**Files:** `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/`: `VisitStatus` (`UNKNOWN`,
`REQUESTED`, `CONFIRMED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `NO_SHOW`), `VisitParty` (`CUSTOMER`,
`AGENT`, `SYSTEM`), `VisitType` (`UNKNOWN`, `ON_SITE`, `VIDEO`), `VisitNoShowReason` (`CUSTOMER_ABSENT`, `CODE_REFUSED`, `AGENT_ABSENT`), `VisitInterest`
(`INTERESTED`, `NOT_INTERESTED`), `Visit`, `VisitSummary`, `CreateVisitRequest`, `ScheduleVisitRequest`,
`CheckInVisitRequest`, `CheckOutVisitRequest`, `NoShowVisitRequest`, `CancelVisitRequest`, `VisitFeedbackRequest`,
`SearchVisitResponse`, `CreateVisitResponse`, `GetVisitResponse`; `ErrorCode.kt`; root `pom.xml`
(`ndopify-dto.version`).

**Interfaces (produced):**
- `Visit(id, matchId, mandateId, listingId, agentId, customerId, status, type: VisitType, requestedAt: Date, note, scheduledAt: Date?,
  code: String?, address: String?, latitude: Double?, longitude: Double?, checkedInAt, checkInDistanceMeters: Int?,
  checkInFlagged, checkedOutAt, agentComment: String?, interest, feedbackReasonCode, feedbackComment, noShowParty,
  noShowReason, cancelledBy, cancellationReason, cancelledAt, createdAt, version)`. `VisitSummary` = the same without
  `code`, `address`, `latitude`, `longitude`, `agentComment` and the feedback.
- Position fields on check-in / check-out / no-show requests: `latitude: Double?`, `longitude: Double?`,
  `accuracyMeters: Double?`.
- `CreateVisitRequest(matchId: Long, type: VisitType, requestedAt: Date, @Size(max=500) note: String?)`;
  `ScheduleVisitRequest(scheduledAt: Date, type: VisitType?)` (null: keep the requested type); `CheckInVisitRequest(@NotEmpty code: String, ...position)`;
  `CheckOutVisitRequest(@NotEmpty @Size(max=2000) comment: String, ...position)`;
  `NoShowVisitRequest(reason: VisitNoShowReason, ...position)`; `CancelVisitRequest(@Size(max=500) reason: String?)`;
  `VisitFeedbackRequest(interest: VisitInterest, reasonCode: MatchRejectionReason?, @Size(max=1000) comment: String?)`.

- [ ] Add the types and error codes; `mvn install` in `modules/ndopify-dto` → BUILD SUCCESS; commit.

### Task 2: Schema and entity

**Files:** `db/migration/common/V10_0__visit.sql`; `visit/server/domain/VisitEntity.kt`;
`visit/server/dao/VisitRepository.kt`; `util/jpa/EnumConverters.kt` (the new enums); `db/test/clean.sql`.

`T_VISIT`: the `Visit` fields as columns, plus `code VARCHAR(4)`, `code_attempts INT`, `check_in_latitude`,
`check_in_longitude`, `check_in_accuracy`, `check_out_*` (same three), `no_show_latitude`, `no_show_longitude`,
`reminder_sent_at`, `fk_match`, `fk_mandate`, `fk_listing`, `version`; index `(tenant_id, status, scheduled_at)` and
`(fk_match, status)`.

- [ ] Add the migration, entity and repository (`findByIdForUpdate`, `existsByMatchIdAndStatusIn`,
  `findByStatusAndScheduledAtBefore`, `findByStatusAndCreatedAtBefore`,
  `findByStatusAndScheduledAtBetweenAndReminderSentAtIsNull`).
- [ ] `mvn test -Dtest=ResetDBTest` → PASS (the migration applies on a clean schema); `mvn validate` → no ktlint
  error; commit.

### Task 3: Request, get and search a visit

**Files:** `visit/server/service/VisitService.kt` (`create`, `findById`), `VisitAccessService.kt`, `VisitSlots.kt`
(pure: `isValid(at: Date, tenant: TenantEntity, now: Date): Boolean`), `visit/server/mapper/VisitMapper.kt`,
`visit/server/endpoint/VisitEndpoints.kt` (`POST /v1/visits`, `GET /v1/visits/{id}`, `GET /v1/visits`:
`mandate-id`, `match-id`, `agent-id`, `status` repeatable, `scheduled-from`, `scheduled-to`, soonest first),
`event/VisitRequestedEvent.kt`, `VisitEventListener.kt`, `mail/VisitRequestedMailet.kt` + `mail/visit/requested.mjml`.
Tests: `VisitSlotsTest`, `CreateVisitEndpointTest`, `GetVisitEndpointTest`, `SearchVisitEndpointTest`,
`VisitRequestedMailetTest` (+ SQL fixtures `db/test/visit/*.sql`).

**Interfaces (produced):** `VisitService.findById(id): VisitEntity` (404); `VisitAccessService.ensureCanRead(visit)`,
`ensureIsCustomer(visit)`, `ensureIsAgent(visit)`, `isCustomer(visit): Boolean`; `VisitMapper.toVisit(visit,
listing, showCode: Boolean)`.

- [ ] Tests: create as the customer (REQUESTED, the agent's e-mail with the slot and link); a match not ACCEPTED →
  409 `MATCH_NOT_ACCEPTED`; an open visit already → 409 `VISIT_ALREADY_EXISTS`, a CANCELLED one → a new one is fine;
  `VIDEO` → `type` saved; `VIDEO` for a customer without `whatsappNumber` → 409 `VISIT_WHATSAPP_MISSING`; `UNKNOWN`
  type → 400; slot today, 8 days ahead, 21:00, 10:15 → 400 `VISIT_INVALID_SLOT`; as the agent or an admin → 403; anonymous → 401.
  Get: customer sees `code` and `address` once an ON_SITE visit is CONFIRMED (a VIDEO one: `code`, never `address`); agent and admin see neither (`code` null); another party →
  403. Search: an agent's is forced to its own `agent-id`, a customer's to its own mandates. `VisitSlotsTest`: each
  boundary in `Africa/Douala` (tomorrow 08:00 ok, today 20:00 no, 7th day 20:00 ok, 8th day no, 20:30 no, 10:15 no).
- [ ] Implement (create locks the match, `MatchRepository.findByIdForUpdate`, before the open-visit check);
  `mvn test -Dtest='*Visit*'` → PASS; commit.

### Task 4: Schedule (agent)

**Files:** `VisitService.schedule(id, request)`; `VisitEndpoints` `POST /v1/visits/{id}/schedule`;
`event/VisitConfirmedEvent.kt`; `mail/VisitConfirmedMailet.kt` (customer + agent) + `confirmed.mjml`,
`confirmed_agent.mjml`; `VisitCodeGenerator.kt` (`fun generate(): String`, 4 digits, `SecureRandom`). Tests:
`ScheduleVisitEndpointTest`, `VisitCodeGeneratorTest`, `VisitConfirmedMailetTest`.

- [ ] Tests: schedule → CONFIRMED, `scheduledAt`, a 4-digit code, `reminderSentAt` set when `scheduledAt` − now
  < 24 h (null otherwise), both e-mails; a changed date is fine; ON_SITE → VIDEO is saved, and 409
  `VISIT_WHATSAPP_MISSING` without the customer's number; invalid slot → 400 `VISIT_INVALID_SLOT`; not
  REQUESTED → 409 `VISIT_NOT_REQUESTED`; another agent → 403; the customer → 403. The customer's e-mail has the code
  and the address (ON_SITE) or the WhatsApp call wording without the address (VIDEO); the agent's has no code. `VisitCodeGeneratorTest`: 1000 codes are all `^\d{4}$`.
- [ ] Implement; tests PASS; commit.

### Task 5: Check-in, no-show, check-out (agent)

**Files:** `VisitService.checkIn`, `noShow`, `checkOut`; `VisitEndpoints` `POST /v1/visits/{id}/check-in`,
`/no-show`, `/check-out`; `visit/server/service/GeoDistance.kt` (pure: `meters(lat1, lon1, lat2, lon2): Int`,
haversine); `event/VisitCompletedEvent.kt`, `VisitNoShowEvent.kt`; `mail/VisitCompletedMailet.kt`,
`VisitNoShowMailet.kt` + templates. Tests: `GeoDistanceTest`, `CheckInVisitEndpointTest`, `NoShowVisitEndpointTest`,
`CheckOutVisitEndpointTest`.

- [ ] Tests: check-in with the right code in the window → CHECKED_IN, `checkedInAt`, distance and flag (fixture
  listing at a known position; 50 m → not flagged, 800 m → flagged; no position → distance null, not flagged); wrong
  code → 409 `VISIT_CODE_INVALID`, attempts 1; 5th wrong → `VISIT_CODE_LOCKED`, then the right one → still
  `VISIT_CODE_LOCKED`; before `scheduledAt` − 1 h or after + 4 h → 409 `VISIT_OUTSIDE_WINDOW`; a CANCELLED visit →
  409 `VISIT_NOT_CONFIRMED`; an admin → 403; the customer → 403. No-show: before + 15 min → 409
  `VISIT_OUTSIDE_WINDOW`; then NO_SHOW, `noShowParty = CUSTOMER`, the reason and position, the customer's e-mail.
  Check-out: under 10 min after check-in → 409 `VISIT_OUTSIDE_WINDOW`; empty comment → 400; then COMPLETED, the
  customer's "Le bien vous intéresse ?" e-mail; not CHECKED_IN → 409 `VISIT_NOT_CHECKED_IN`. `GeoDistanceTest`: two
  Douala points with a known distance (± 1 m).
- [ ] Implement; tests PASS; commit.

### Task 6: Cancel and feedback

**Files:** `VisitService.cancel(id, request)`, `feedback(id, request)`; `VisitEndpoints` `POST /v1/visits/{id}/cancel`,
`/feedback`; `event/VisitCancelledEvent.kt`; `mail/VisitCancelledMailet.kt` + template. Tests:
`CancelVisitEndpointTest`, `VisitFeedbackEndpointTest`.

- [ ] Tests: the customer cancels REQUESTED and CONFIRMED → CANCELLED, `cancelledBy = CUSTOMER`, the agent's
  e-mail; the agent cancels → `cancelledBy = AGENT`, the customer's e-mail; CHECKED_IN / COMPLETED / CANCELLED → 409
  `VISIT_CANNOT_BE_CANCELLED`; another party → 403. Feedback: COMPLETED → saved; twice → 409
  `VISIT_FEEDBACK_ALREADY_SENT`; not COMPLETED → 409 `VISIT_NOT_COMPLETED`; the agent → 403.
- [ ] Implement; tests PASS; commit.

### Task 7: Jobs: expiry, reminder, agent no-show

**Files:** `visit/server/job/VisitJobs.kt` (`expire`, hourly: REQUESTED older than 48 h → EXPIRED; `remind`, every
15 min: CONFIRMED with `scheduledAt` within 24 h and `reminderSentAt` null; `noShow`, hourly: CONFIRMED 4 h past
`scheduledAt` → NO_SHOW AGENT); `VisitService.expireDue`, `remindDue`, `markAgentNoShows`; `VisitReminderMailet.kt`,
`VisitExpiredMailet.kt` + templates; `ndopify.visit.*` in `application.yml` (`-` cron in `application-qa.yml`).
Tests: `VisitJobsTest`, `VisitServiceTest` (the three batch methods), `RemindVisitsJobTest` (endpoint-style, end to
end).

- [ ] Tests: each job moves only the due visits; a visit changed meanwhile (`@Version`) is skipped; the reminder is
  sent once (`reminderSentAt`), not for a visit confirmed < 24 h before; the agent no-show e-mails both sides.
- [ ] Implement; `mvn verify` in `modules/ndopify-server` → BUILD SUCCESS (coverage gate); update
  `modules/ndopify-server/CLAUDE.md` (domain `visit`, the e-mails list, `@TenantId` / `@Version` lists, the jobs);
  commit.

## Out

- The web apps: ndopify-web's `/visits/{id}` page and the visit request from `match.html`, and Partner Central's
  `/visits/{id}` page and *Visites* home section. Each gets its own plan once its prototype is approved
  (`visite.html`, `proposition.html`'s popup, `visit.html`, `index.html`).
- A visit fee and its payment (MTN request to pay, refunds, paying the agent): dropped on 2026-11-02, to come back
  later as its own plan. SMS, a video room other than WhatsApp, the public listing page's "Demander une visite" (anonymous visitor → a
  lead later), the admin console's view of flagged check-ins.
