# Match Agent Alert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** E-mail an agent, once, about the new matches between their listings and their search mandates.

**Architecture:** `T_MATCH` gets a `notified_at` column, null until the agent is told. An hourly job
(`MatchJobs.notifyAgents`) goes over the active tenants; for each agent with ACTIVE, not-yet-notified matches (mandate
APPROVED, listing PUBLISHED) it sends one digest e-mail grouped by mandate (`MatchNotificationMailet`), then sets
`notified_at` on them. The matcher never touches `notified_at`, so a re-score or a re-run never alerts twice.

**Tech Stack:** Kotlin, Spring Boot (`@Scheduled`), Spring Data JPA, Flyway (MySQL), MJML + Mustache e-mails.

**Spec:** the *Out* list of `docs/superpowers/plans/2026-10-31-search-listing-matching.md` ("the agent alert (e-mail,
`notifiedAt`)"), and option B as agreed on 2026-10-31: a scheduled digest rather than an e-mail on each match run.

## Global Constraints

- Module: `modules/ndopify-server` only. No DTO change (`notifiedAt` is not exposed by the API in this plan).
- Cron: `ndopify.match.notify.cron: "0 0 * * * ?"` (every hour), in `application.yml` under `ndopify.match`.
- One e-mail per agent per run, whatever the number of mandates and matches.
- Only ACTIVE matches, mandate `APPROVED`, listing `PUBLISHED`, `notified_at IS NULL`. DISMISSED ones are never sent.
- Send first, then mark: a failed send leaves `notified_at` null, so the next run retries. A failing agent must not
  stop the others, nor a failing tenant the other tenants (the `ContractJobs.forEachTenant` pattern).
- E-mail text in French; amounts through `TenantFormatter(tenant).money(amount, currencyCode)`, never "FCFA".
- Mailet in `match/server/mail/`, template in `src/main/resources/mail/match/`, job in `match/server/job/`.
- Assertions with `kotlin.test`; mocks with `mockito-kotlin`; integration tests extend `BaseEndpointIntegrationTest`
  with `@Sql(["/db/test/clean.sql", ...])`.
- Out of scope: an opt-out setting for the agent, a Partner Central match page (the e-mail links to the mandate page,
  `"${tenant.partnerCentralUrl}/mandates/{id}"`), re-alerting after a price drop (impossible today, see the matching
  plan; when it's added, the re-activation must set `notifiedAt = null`).

## Review Focus

1. **Re-run of the matcher** (`POST /v1/matches/run`, or a listing re-published): a re-scored match keeps its
   `notified_at`, so the agent isn't told twice. → Task 1, `re-score keeps notifiedAt`.
2. **Agent dismisses a match between the query and the mark**: the bulk update must not fail on the `@Version`, and
   the dismiss must stay. → Task 1 uses a JPQL bulk update (no version check); Task 3, `dismissed match not sent`.
3. **An agent with many matches** (ex: 40 listings for one search): the e-mail stays readable. → Task 2 caps at 5
   listings per mandate, best score first, with "et N autre(s) bien(s)"; test `more than 5 listings`.
4. **The send fails for one agent**: nothing is marked for that agent, the others are sent and marked. → Task 3,
   `failed agent is retried next run`.
5. **Tenant without `partnerCentralUrl`**: the e-mail is still sent, without the button. → Task 2, `no mandate_url`.

---

### Task 1: `notified_at` on the match, and its queries

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V9_1__match-notified-at.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/RunMatchEndpointTest.kt`
  (and its `db/test/match/RunMatchEndpoint.sql`)

**Interfaces:**
- Produces:
  - `MatchEntity.notifiedAt: Date? = null` (after `listingPrice`), with a comment: when the agent was e-mailed about
    it; null until then, untouched by a re-score.
  - `MatchRepository.findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatus(status: MatchStatus,
    mandateStatus: MandateStatus, listingStatus: ListingStatus): List<MatchEntity>`
  - `MatchRepository.markNotified(ids: Collection<Long>, now: Date): Int` — `@Modifying` +
    `@Query("UPDATE MatchEntity m SET m.notifiedAt = :now WHERE m.id IN :ids")`. A bulk update on purpose: it skips the
    `@Version` check, so a dismiss made meanwhile neither fails it nor is overwritten (it only sets `notified_at`).

- [ ] **Step 1: Write the failing test** in `RunMatchEndpointTest`: `re-score keeps notifiedAt`. Fixture: an existing
  ACTIVE match with `notified_at = '2026-10-01 10:00:00'`. Run the mandate; assert with `JdbcTemplate` that its
  `notified_at` is unchanged and that a newly created match has `notified_at` null.
- [ ] **Step 2: Run it, verify it fails** — `mvn test -Dtest=RunMatchEndpointTest` → fails on the unknown column.
- [ ] **Step 3: Implement.** Migration:
  ```sql
  ALTER TABLE T_MATCH ADD COLUMN notified_at DATETIME DEFAULT NULL;
  CREATE INDEX IDX_MATCH_notify ON T_MATCH(status, notified_at);
  ```
  Add the field and the two repository methods. `MatchService.store()` needs no change: `copy()` keeps `notifiedAt`.
- [ ] **Step 4: Run it, verify it passes** — `mvn test -Dtest='*Match*'` → all PASS.
- [ ] **Step 5: Commit** — `feat(match): keep when the agent was told about a match`

### Task 2: The digest e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchNotificationMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/new_matches.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchNotificationMailetTest.kt`

**Interfaces:**
- Consumes: `MatchEntity` (with `mandate`, `listing`, `score`), `MandateMailLabels.place/listingType/propertyType`
  (`internal`, same Maven module), `CustomerService.findByIdOrNull`, `TenantService.findById`, `MailService`,
  `MailBodyResolver`.
- Produces: `MatchNotificationMailet.send(agent: AgentEntity, matches: List<MatchEntity>)`; all `matches` are the
  agent's, of one tenant. Constants `SUBJECT_ONE`, `SUBJECT_MANY`, `TEMPLATE = "/mail/match/new_matches.mjml"`,
  `MAX_LISTINGS = 5`.

Copy fixed here:
- Subject: `SUBJECT_ONE = "{{tenant_name}}: 1 nouveau bien correspond à vos recherches"`,
  `SUBJECT_MANY = "{{tenant_name}}: {{count}} nouveaux biens correspondent à vos recherches"`; count = number of
  matches.
- Template, after the logo/divider of `mail/mandate/approved.mjml`: "Bonjour **{{recipient_name}}**,", "Des biens de
  votre portefeuille correspondent aux recherches de vos clients :". Then, per mandate (`{{#mandates}}`): a heading
  "**{{customer_name}}** — {{listing_type}}{{#property_type}} - {{property_type}}{{/property_type}} à {{place}}", a
  `<ul>` with each listing "{{title}} — {{price}} ({{score}} %)", "et {{more}} autre(s) bien(s)" when `more > 0`, and
  a "Voir le mandat" button on `{{mandate_url}}` when set.

Context: `tenant_logo_url`, `tenant_name`, `recipient_name` (agent full name), `mandates`: a list of maps
`customer_name`, `listing_type`, `property_type`, `place`, `mandate_url`, `listings` (maps `title`, `price`, `score`),
`more` (null when 0). Mandates in order of their best score; listings best score first, at most `MAX_LISTINGS`.
`title` falls back to the listing's id (`"Bien n° {id}"`) when blank; `score` = `(score * 100).roundToInt()`.

- [ ] **Step 1: Write the failing tests** in `MatchNotificationMailetTest`, structured like
  `MandateAssignedMailetTest` (mocks, `argumentCaptor<Email>()`, captured `context`):
  - `send` — 2 mandates, 3 matches: recipient is the agent's e-mail and full name; subject
    `"Ndopify: 3 nouveaux biens correspondent à vos recherches"`; `mandates` ordered by best score; prices formatted
    by `TenantFormatter`; `mandate_url == "https://pc.ndopify.com/mandates/1"`.
  - `one match` — subject `"Ndopify: 1 nouveau bien correspond à vos recherches"`.
  - `more than 5 listings` — 7 matches on one mandate: 5 `listings`, highest score first, `more == 2`.
  - `no mandate_url` — tenant without `partnerCentralUrl`: `assertNull(mandate["mandate_url"])`, still sent.
  - `blank title` — `title == "Bien n° 300"`.
- [ ] **Step 2: Run, verify they fail** — `mvn test -Dtest=MatchNotificationMailetTest` → compilation error.
- [ ] **Step 3: Implement** `MatchNotificationMailet` and `new_matches.mjml`. KDoc: what it sends, the cap, and that
  the caller marks the matches only after it returns.
- [ ] **Step 4: Run, verify they pass** — same command → PASS.
- [ ] **Step 5: Commit** — `feat(match): e-mail an agent about their new matches`

### Task 3: The hourly job

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchNotificationService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (`ndopify.match.notify.cron`)
- Modify: `modules/ndopify-server/CLAUDE.md` (jobs list: add `MatchJobs`; match domain: the alert)
- Modify: `docs/superpowers/plans/2026-10-31-search-listing-matching.md` (*Out*: point the agent alert to this plan)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyMatchesJobTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/match/NotifyMatchesJob.sql`

**Interfaces:**
- Consumes: Task 1's repository methods, Task 2's `MatchNotificationMailet.send`, `AgentService.findByIdOrNull`,
  `Clock`.
- Produces:
  - `MatchNotificationService.notifyAgents(): Int` — for the current tenant, the number of agents e-mailed. Groups
    the pending matches by `agentId`; for each, inside a try/catch that logs (warn) and continues: find the agent
    (skip if null), `mailet.send(agent, matches)`, then `dao.markNotified(ids, Date(clock.millis()))`. The method is
    not `@Transactional`; `markNotified` runs in its own transaction (annotate the repository method
    `@Transactional`), so each agent's mark commits on its own.
  - `MatchJobs.notifyAgents()` — `@Scheduled(cron = "\${ndopify.match.notify.cron}")`, same `forEachTenant` as
    `ContractJobs` (log line `"Tenant#$tenantId - MatchJobs.notifyAgents() completed: $count agent(s)"`).

- [ ] **Step 1: Write the fixture and the failing tests.** Fixture (two tenants, the second inactive): agent A with 2
  pending matches on 2 mandates; agent B with 1 pending match; one already notified; one DISMISSED; one whose mandate
  is not APPROVED; one whose listing is not PUBLISHED; one pending in the inactive tenant. `MatchNotificationMailet` is
  a `@MockitoBean` (as in `PublishListingsJobTest`). Tests:
  - `notifyAgents` — `send` called once for A (2 matches) and once for B (1 match); `notified_at` set on exactly those
    3 rows (checked with `JdbcTemplate`); the others untouched.
  - `nothing left on the next run` — second call: `send` not called again.
  - `dismissed match not sent` — the DISMISSED row is in no `send` call and keeps `notified_at` null.
  - `failed agent is retried next run` — `send` throws for A: B is still sent and marked, A's rows stay null; next run
    with no error sends A.
- [ ] **Step 2: Run, verify they fail** — `mvn test -Dtest=NotifyMatchesJobTest` → compilation error.
- [ ] **Step 3: Implement** the service, the job and the config line
  (`notify: cron: "0 0 * * * ?"   # Every hour: e-mails the agents about their new matches`).
- [ ] **Step 4: Run, verify they pass** — `mvn test -Dtest=NotifyMatchesJobTest` → PASS.
- [ ] **Step 5: Update the docs** (CLAUDE.md jobs list and match domain, the matching plan's *Out*).
- [ ] **Step 6: Full module check** — `mvn verify` in `modules/ndopify-server` → BUILD SUCCESS (ktlint, tests,
  JaCoCo gate).
- [ ] **Step 7: Commit** — `feat(match): hourly job e-mailing the agents about their new matches`
