# Match Visit Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** E-mail the customer of an `ACCEPTED` match that still has no visit, 3 days after it accepted it, with a fresh link to the match page where the visit form waits.

**Architecture:** A new `MatchJobs.remindAcceptedWithoutVisit` job calls `MatchVisitReminderService.remindDue()`, which selects the due matches (`MatchRepository`), then for each one sends `MatchVisitReminderMailet` and sets the new `visitRemindedAt` column. It follows `MatchNotificationService`: send synchronously, mark only after a successful send, log a failure and leave that match for the next run. The e-mail reuses the proposal e-mail's French layout and agent signature, with a fresh `MatchLinkTokenService` token.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway, MJML + Mustache mail templates, mockito-kotlin, `kotlin.test`, MySQL for `ndopify-server` integration tests.

**Spec:** GitHub issue #178 — https://github.com/wutsi/ndopify-mono/issues/178

## Global Constraints

- `ndopify-server` only. No `ndopify-dto`, `ndopify-web-core`, UI or root `pom.xml` change; no library release.
- One reminder per match, 3 days after `answeredAt`. No second reminder in v1.
- One e-mail per match; never group a customer's matches.
- The agent is not told about the reminder.
- Send first, then mark `visitRemindedAt`: a failed send leaves it null for the next run.
- A customer whose party has a blank e-mail is marked without sending, so it can't clog the `batch-size` window.
- No open visit (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`), mandate `APPROVED` and listing `PUBLISHED` are required.
- French e-mail copy; code, comments, logs and docs in English. Dates and amounts go through `TenantFormatter` / `MandateMailLabels`, never a hard-coded pattern or "FCFA".
- Flyway: a new script after `V12_0__mandate-origin-listing.sql`; never edit an applied migration.
- `kotlin.test` assertions and mockito-kotlin mocks, as in the surrounding tests.
- Conventional commits with `Refs #178` in the body.
- `mvn verify` green in `modules/ndopify-server` before the push — it needs a local MySQL.

## Review Focus

1. **An open visit never gets a reminder** — the selection excludes it and the service re-checks each match before sending (Tasks 3, 4).
2. **A failed send retries** — the row is marked only after `mailet.send` returns; SMTP down leaves it null (Tasks 3, 4).
3. **No e-mail doesn't starve the batch** — such a match is marked as handled without an e-mail; otherwise it stays at the front of every run (Tasks 3, 4).
4. **One reminder only** — `visitRemindedAt` is set once; a second run sends nothing (Task 4).
5. **The agent learns nothing and no token leaks** — no Telegram / e-mail to the agent, the job logs counts only, never the token or the URL (Tasks 2, 3, 4).
6. **No public portal, no link** — the e-mail still goes out without a `token` and without a match link (Task 2).

---

### Task 1: Add `visit_reminded_at` to `T_MATCH`

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V12_1__match-visit-reminder.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt:76-77`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/ExpireMatchesJobTest.kt` (existing; boots the context and applies Flyway)

**Interfaces:**
- Produces: `MatchEntity.visitRemindedAt: Date?` (default `null`), column `visit_reminded_at`, so later tasks can select and save it.

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE T_MATCH ADD COLUMN visit_reminded_at DATETIME DEFAULT NULL;
ALTER TABLE T_MATCH ADD INDEX IDX_MATCH_visit_reminder (status, visit_reminded_at);
```

- [ ] **Step 2: Add the field to `MatchEntity`**

After `answeredAt`, before `createdAt`:

```kotlin
    // When the customer was reminded that its accepted match still has no visit (MatchVisitReminderService)
    val visitRemindedAt: Date? = null,
```

- [ ] **Step 3: Verify the migration applies**

Run: `cd modules/ndopify-server && mvn -q -Dtest=ExpireMatchesJobTest test`
Expected: BUILD SUCCESS. Flyway applies `V12_1`; the existing suite still passes.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V12_1__match-visit-reminder.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt
git commit -m "feat(match): add visit_reminded_at to T_MATCH

Refs #178"
```

---

### Task 2: Add the reminder e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/visit_reminder.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchEntity`, `CustomerEntity`, `AgentEntity`, `MandateMailLabels`, `MatchLinkTokenService.create(customer: CustomerEntity): String?`, `AgentMailContext`.
- Produces: `MatchVisitReminderMailet.send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity)`, `MatchVisitReminderMailet.SUBJECT`, `MatchVisitReminderMailet.TEMPLATE`.

- [ ] **Step 1: Write the failing mailet test**

Mirror `MatchProposedMailetTest` (same mocks and fixtures: tenant 1 `Ndopify` with `publicPortalUrl = https://www.ndopify.com/`, listing `101` `Nice apartment` at `150000` `XAF` `MONTHLY`, customer Ray, agent Kim). Test class `MatchVisitReminderMailetTest`, with:

```kotlin
@Test
fun send() {
    mailet.send(match, customer, agent)

    assertEquals(
        mapOf(
            "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
            "tenant_name" to "Ndopify",
            "recipient_name" to "Ray Sponsible",
            "agent_full_name" to "Kim Possible",
            "agent_photo_url" to null,
            "agent_whatsapp" to null,
            "agent_whatsapp_url" to null,
            "listing_title" to "Nice apartment",
            "image_url" to "https://cdn.ndopify.com/7.jpg",
            "price_label" to "Loyer",
            "price" to "150 000 FCFA / mois",
            "online" to true,
            "listing_url" to "https://www.ndopify.com/match/1?token=xxx.yyy.zzz",
        ),
        context(),
    )

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
    assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
    assertEquals("Ndopify: n'oubliez pas de demander votre visite", email.firstValue.subject)
    assertEquals("<html>reminder</html>", email.firstValue.body)
    assertEquals("Kim Possible", email.firstValue.sender?.displayName)
    assertEquals("", email.firstValue.sender?.email)
}
```

Plus these tests, mirroring `MatchProposedMailetTest`:

```kotlin
@Test
fun `no public portal - no link`()          // tenant.copy(publicPortalUrl = null): listing_url and online null; verify(linkTokenService, never()).create(any())

@Test
fun `no token - the link without it`()      // linkTokenService.create returns null: "https://www.ndopify.com/match/1"

@Test
fun `no photo - no image`()                 // imageService.cover returns null: image_url null

@Test
fun `blank title`()                         // listing.title = "": listing_title "Bien n° 101"
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchVisitReminderMailetTest test`
Expected: FAIL — `MatchVisitReminderMailet` doesn't exist.

- [ ] **Step 3: Write the failing template test**

Mirror `MatchProposedTemplateTest` with a real `MailBodyResolver` (`MjmlRenderer.create()`, `DefaultMustacheFactory(ClasspathResolver())`, `listOf(MailFilterNull())`):

```kotlin
@Test
fun `asks for a visit`() {
    val html = resolver.resolve(MatchVisitReminderMailet.TEMPLATE, context)

    assertTrue(html.contains("Ray Sponsible"))
    assertTrue(html.contains("Vous avez indiqué que ce bien vous intéresse"))
    assertTrue(html.contains("Nice apartment"))
    assertTrue(html.contains("150 000 FCFA / mois"))
    assertTrue(html.contains("https://www.ndopify.com/match/1"))
    assertTrue(html.contains("Demander une visite"))
    assertFalse(html.contains("Répondez-moi"))
    assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""))
    // The shared agent footer: photo, name, WhatsApp
    assertTrue(html.contains("https://wa.me/237699000001"))
    assertTrue(html.contains("src=\"https://cdn.ndopify.com/agent.jpg\""))
}

@Test
fun `no photo - no image`()                 // image_url removed: assertFalse(html.contains("cdn.ndopify.com/7.jpg"))

@Test
fun `no link - reply to the agent`()        // listing_url and online removed: no "Demander une visite", "Répondez-moi" present, assertFalse(html.contains("href=\"\""))
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchVisitReminderTemplateTest test`
Expected: FAIL — template not found.

- [ ] **Step 5: Implement `MatchVisitReminderMailet`**

Same dependencies and URL logic as `MatchProposedMailet`:

```kotlin
@Service
class MatchVisitReminderMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: n'oubliez pas de demander votre visite"
        const val TEMPLATE = "/mail/match/visit_reminder.mjml"
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) { … }
}
```

`send` builds the tenant (`tenantService.findById(match.mandate.tenantId!!)`), a `TenantFormatter`, the customer/agent names, and the context map asserted in Step 1. The link is `match.id`: `baseUrl.removeSuffix("/") + "/match/" + match.id`, plus `?token=$token` when a token exists; the token comes from `tenant.publicPortalUrl?.let { linkTokenService.create(customer) }`. `price` uses `MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter)`; `price_label` uses `MandateMailLabels.priceLabel(mandate)`. Sender is `AgentMailContext.from(agent)`; the context adds `AgentMailContext.of(agent, formatter)`. `online` is `tenant.publicPortalUrl != null`.

KDoc: one e-mail per accepted match without a visit, the listing, its price, the agent's signature, and a fresh `public-portal` link to the match page where the visit form waits; no token without a `publicPortalUrl`.

- [ ] **Step 6: Implement the template**

`mail/match/visit_reminder.mjml`, same wrapper, logo, divider and `{{> mail/fragments/agent-footer.mjml}}` as `proposed.mjml`. French body:

- greeting `Bonjour <strong>{{recipient_name}}</strong>,`
- `Vous avez indiqué que ce bien vous intéresse. Si vous souhaitez le visiter, choisissez un créneau qui vous convient : votre demande ne prend que quelques secondes.`
- the listing: `{{#image_url}}` an `mj-image` linked to `listing_url` when present; an `mj-text` with `<b>{{listing_title}}</b>` and `<b>{{price_label}}</b>&#160;: {{price}}`
- `{{#listing_url}}` an `mj-button` "Demander une visite" + `mj-spacer` `{{/listing_url}}`
- `{{#online}}` `Choisissez votre créneau sur la page du bien.` `{{/online}}`
- `{{^online}}` `Répondez-moi et je vous proposerai un créneau.` `{{/online}}`
- sign-off `Cordialement,` as `proposed.mjml` does, then the shared agent footer partial.

- [ ] **Step 7: Run the two test classes**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchVisitReminderMailetTest,MatchVisitReminderTemplateTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt \
        modules/ndopify-server/src/main/resources/mail/match/visit_reminder.mjml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderTemplateTest.kt
git commit -m "feat(match): add the visit reminder e-mail

Refs #178"
```

---

### Task 3: Select the due matches and send the reminders

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitReminderService.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (under `ndopify.match`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitReminderServiceTest.kt`

**Interfaces:**
- Consumes: `MatchEntity.visitRemindedAt`, `MatchVisitReminderMailet.send`, `CustomerService.findById(id: Long): CustomerEntity`, `AgentService.findById(id: Long): AgentEntity`.
- Produces: `MatchVisitReminderService.remindDue(): Int` (number e-mailed); repository methods `findVisitReminderDue(...)` and `existsOpenVisit(...)`.

- [ ] **Step 1: Write the failing service test**

`MatchVisitReminderServiceTest`, mirroring `CoBrokerageNotificationServiceTest` (mocks: `MatchRepository`, `CustomerService`, `AgentService`, `MatchVisitReminderMailet`; `Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)`; service built with `delayDays = 3`, `batchSize = 100`). Fixtures: `approvedSearch` (`MandateStatus.APPROVED`, customer 10, tenant 1), `publishedListing` (`ListingStatus.PUBLISHED`, id 101, title "Nice apartment"), `accepted = MatchEntity(id = 1L, status = ACCEPTED, answeredAt = now - 3 days, mandate = approvedSearch, listing = publishedListing, agentId = 20L)`, `customer` (party e-mail `ray.sponsible@gmail.com`), `agent`.

Tests and assertions:

```kotlin
@Test
fun `reminds each due match, then marks it`() {
    doReturn(listOf(accepted)).whenever(dao).findVisitReminderDue(
        eq(MatchStatus.ACCEPTED),
        eq(Date(now.time - 3 * DAY_MILLIS)),
        eq(MandateStatus.APPROVED),
        eq(ListingStatus.PUBLISHED),
        eq(MatchVisitReminderService.OPEN_VISIT_STATUSES),
        eq(PageRequest.of(0, 100)),
    )
    doReturn(Optional.of(accepted)).whenever(dao).findById(1L)
    doReturn(false).whenever(dao).existsOpenVisit(1L, MatchVisitReminderService.OPEN_VISIT_STATUSES)
    doReturn(customer).whenever(customerService).findById(10L)
    doReturn(agent).whenever(agentService).findById(20L)

    assertEquals(1, service.remindDue())

    verify(mailet).send(accepted, customer, agent)
    verify(dao).save(accepted.copy(visitRemindedAt = now, modifiedAt = now))
}

@Test
fun `an open visit - no reminder`()          // existsOpenVisit true: verify(mailet, never()).send(any(), any(), any()); verify(dao, never()).save(any()); assertEquals(0, …)

@Test
fun `already reminded - no reminder`()       // accepted.copy(visitRemindedAt = now - 1 day): no send, no save

@Test
fun `changed while selecting - skipped`()    // dao.findById returns Optional.of(accepted.copy(status = REJECTED)): no send, no save

@Test
fun `a failed send is left for the next run`() // doThrow(RuntimeException("SMTP down")).whenever(mailet).send(…): assertEquals(0, …); verify(dao, never()).save(any())

@Test
fun `no e-mail - marked without sending`()   // customer.party.email = "": verify(mailet, never()).send(any(), any(), any()); verify(dao).save(accepted.copy(visitRemindedAt = now, modifiedAt = now)); assertEquals(0, …)

@Test
fun `the other matches still go out after one failure`() // first throws, second sends: assertEquals(1, …); verify(dao).save(second…)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchVisitReminderServiceTest test`
Expected: FAIL — class and repository methods missing.

- [ ] **Step 3: Add the repository queries**

In `MatchRepository`, import `com.wutsi.ndopify.visit.dto.VisitStatus` (a DTO, not the visit domain), then:

```kotlin
    // The accepted matches with no visit yet that are due for a reminder, oldest answer first (MatchVisitReminderService).
    // VisitEntity is named in the query rather than imported: the match domain doesn't depend on the visit one.
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.visitRemindedAt IS NULL " +
            "AND m.answeredAt <= :before AND m.mandate.status = :mandateStatus " +
            "AND m.listing.status = :listingStatus " +
            "AND NOT EXISTS (SELECT v.id FROM VisitEntity v WHERE v.matchId = m.id AND v.status IN :visitStatuses) " +
            "ORDER BY m.answeredAt ASC, m.id ASC"
    )
    fun findVisitReminderDue(
        @Param("status") status: MatchStatus,
        @Param("before") before: Date,
        @Param("mandateStatus") mandateStatus: MandateStatus,
        @Param("listingStatus") listingStatus: ListingStatus,
        @Param("visitStatuses") visitStatuses: Collection<VisitStatus>,
        page: Pageable,
    ): List<MatchEntity>

    // Whether the match has an open visit: the reminder doesn't go out then. VisitEntity is named in the query rather
    // than imported, as above.
    @Query(
        "SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END FROM VisitEntity v " +
            "WHERE v.matchId = :matchId AND v.status IN :statuses"
    )
    fun existsOpenVisit(matchId: Long, statuses: Collection<VisitStatus>): Boolean
```

- [ ] **Step 4: Implement `MatchVisitReminderService`**

```kotlin
@Service
class MatchVisitReminderService(
    private val dao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mailet: MatchVisitReminderMailet,
    private val clock: Clock,
    @param:Value("\${ndopify.match.visit-reminder.delay-days}") private val delayDays: Long,
    @param:Value("\${ndopify.match.visit-reminder.batch-size}") private val batchSize: Int,
) {
    companion object {
        val OPEN_VISIT_STATUSES = setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)
        private val LOGGER = LoggerFactory.getLogger(MatchVisitReminderService::class.java)
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    fun remindDue(): Int { … }
}
```

`remindDue()` selects with `findVisitReminderDue(status = ACCEPTED, before = Date(now - delayDays * DAY_MILLIS), mandateStatus = APPROVED, listingStatus = PUBLISHED, visitStatuses = OPEN_VISIT_STATUSES, page = PageRequest.of(0, batchSize))`, then counts the matches it e-mailed. Each match is handled in its own `try`: reload with `dao.findById(id).orElse(null)` (skip when null); skip unless `ACCEPTED`, `visitRemindedAt == null`, mandate `APPROVED`, listing `PUBLISHED`, and `!dao.existsOpenVisit(id, OPEN_VISIT_STATUSES)`. Then load the customer and agent; if the customer's party e-mail is blank, save `match.copy(visitRemindedAt = now, modifiedAt = now)` and count nothing; otherwise `mailet.send(match, customer, agent)`, save that copy and count it. Catch `ObjectOptimisticLockingFailureException` (log `Match#<id> - Changed while reminding, left as is`, count nothing) and `Exception` (log `Match#<id> - Unable to remind`, count nothing). No transaction around the send: each `save` is its own, as `MatchProposalService.expireDue`.

KDoc: one e-mail per `ACCEPTED` match with no open visit whose customer accepted it at least `delay-days` ago; marked after a successful send so a failure retries; a customer without e-mail is marked without one; the agent isn't told.

- [ ] **Step 5: Add the config**

Under `ndopify.match` in `modules/ndopify-server/src/main/resources/application.yml`:

```yaml
        visit-reminder:
            delay-days: 3                   # An ACCEPTED match with no visit is reminded after this delay
            batch-size: 100                 # Matches reminded per run
            cron: "0 0 9 * * ?"             # Every day at 09:00: reminds the customers who accepted a match but booked no visit
```

- [ ] **Step 6: Run the unit test, then validate the queries at startup**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchVisitReminderServiceTest test`
Expected: PASS.
Run: `cd modules/ndopify-server && mvn -q -Dtest=ExpireMatchesJobTest test`
Expected: PASS — Spring Data parses the new `@Query` at context startup, so invalid JPQL fails here.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitReminderService.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchVisitReminderServiceTest.kt
git commit -m "feat(match): remind accepted matches without a visit

Refs #178"
```

---

### Task 4: Schedule the job, prove it end to end, document it

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `modules/ndopify-server/src/main/resources/application-prod.yml` (under `ndopify.match`)
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml` (under `ndopify.match`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/MatchJobsTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/RemindAcceptedMatchesJobTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/match/RemindAcceptedMatchesJob.sql`
- Modify: `modules/ndopify-server/CLAUDE.md` (Code Organization jobs list, and the *match* section)

**Interfaces:**
- Consumes: `MatchVisitReminderService.remindDue(): Int`.
- Produces: `MatchJobs.remindAcceptedWithoutVisit()`.

- [ ] **Step 1: Write the failing job test**

`MatchJobsTest` (plain unit test): add `private val reminderService = mock<MatchVisitReminderService>()` and pass it to the constructor. Add:

```kotlin
@Test
fun `remindAcceptedWithoutVisit - each active tenant, with its tenant set`() {
    doAnswer {
        tenants.add(TenantContext.get())
        3
    }.whenever(reminderService).remindDue()

    jobs.remindAcceptedWithoutVisit()

    assertEquals(listOf<Long?>(1L, 2L), tenants)
    assertNull(TenantContext.get())
}
```

- [ ] **Step 2: Write the failing integration test and its fixture**

`RemindAcceptedMatchesJobTest`, like `RemindVisitsJobTest` and `ExpireMatchesJobTest`:

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RemindAcceptedMatchesJob.sql"])
class RemindAcceptedMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired private lateinit var jobs: MatchJobs
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun remind() {
        jobs.remindAcceptedWithoutVisit()

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "one e-mail per idle accepted match")
        assertEquals(
            "Ndopify: n'oubliez pas de demander votre visite",
            smtp.receivedMessages.first { it.allRecipients.single().toString().contains("ray.sponsible") }.subject,
        )
        val body = smtp.receivedMessages.first { it.allRecipients.single().toString().contains("ray.sponsible") }
            .content.toString()
        assertTrue(body.contains("A")) // The listing
        assertTrue(body.contains("/match/1?token="))

        assertNotNull(visitRemindedAt(1L))  // Reminded
        assertNotNull(visitRemindedAt(21L)) // Tenant 2
        assertNotNull(visitRemindedAt(7L))  // No e-mail: marked as handled
        assertNull(visitRemindedAt(2L))     // Accepted 2 days ago
        assertNull(visitRemindedAt(3L))     // Open visit
        assertNull(visitRemindedAt(4L))     // Already reminded
        assertNull(visitRemindedAt(5L))     // Listing rented
        assertNull(visitRemindedAt(6L))     // Mandate closed
        assertNull(visitRemindedAt(31L))    // Inactive tenant
    }

    @Test
    fun `reminded once`() {
        jobs.remindAcceptedWithoutVisit()
        assertTrue(smtp.waitForIncomingEmail(5000, 2))

        jobs.remindAcceptedWithoutVisit()

        assertFalse(smtp.waitForIncomingEmail(1000, 3), "no second reminder")
        assertEquals(2, smtp.receivedMessages.size)
    }

    private fun visitRemindedAt(id: Long): Any? =
        jdbc.queryForList("SELECT visit_reminded_at FROM T_MATCH WHERE id = ?", id).single()["visit_reminded_at"]
}
```

The fixture, with the shape of `ProposeMatchEndpoint.sql` / `RemindVisitsJob.sql`:

```sql
-- Remind: 1 (tenant 1, accepted 3 days ago, no visit), 21 (tenant 2)
-- Keep: 2 (accepted 2 days ago), 3 (open visit), 4 (already reminded), 5 (listing rented), 6 (mandate closed),
--       7 (no customer e-mail: marked, no e-mail), 31 (inactive tenant)
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, public_portal_url, currency_code, monetary_format, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://www.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', 'https://other.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (3, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', NULL, 'XAF', '#,###,##0 FCFA', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde');

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, '', 'No', 'Mail'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent'),
    (130, 3, 'inactive.customer@gmail.com', 'In', 'Active'),
    (230, 3, 'inactive.agent@gmail.com', 'Ina', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100), (11, 1, 101), (12, 2, 120), (13, 3, 130);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201), (22, 2, 220, 2370201), (23, 3, 230, 2370201);

INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, price, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (2, 1, 11, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (3, 1, 10, 20, 'CLOSED',   'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (4, 2, 12, 22, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (5, 3, 13, 23, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 250000, 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (18, 2, 12, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF'),
    (19, 3, 13, 23, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 240000, 'XAF');

INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, price, currency_code, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED',    2370201, 240000, 'XAF', 'E'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'F'),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'G'),
    (201, 2, 18, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Other'),
    (301, 3, 19, 23, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 240000, 'XAF', 'Inactive');

INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, proposed_at, answered_at, visit_reminded_at) VALUES
    (1,  1, 1, 101, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (2,  1, 1, 102, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 DAY), NULL),
    (3,  1, 1, 103, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (4,  1, 1, 104, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)),
    (5,  1, 1, 105, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (6,  1, 3, 106, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (7,  1, 2, 107, 20, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (21, 2, 4, 201, 22, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL),
    (31, 3, 5, 301, 23, 'ACCEPTED', 0.9, 240000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 4 DAY), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 3 DAY), NULL);

-- Match 3 has an open visit: no reminder
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, code, reminder_sent_at, created_at
) VALUES
    (1, 1, 3, 1, 103, 20, 10, 'REQUESTED', 'ON_SITE',
     DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 DAY), NULL, NULL,
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY));
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchJobsTest,RemindAcceptedMatchesJobTest test`
Expected: FAIL — `MatchJobs` has no `remindAcceptedWithoutVisit`.

- [ ] **Step 4: Implement the job**

Add `private val reminderService: MatchVisitReminderService` to the `MatchJobs` constructor, and:

```kotlin
    /** Reminds the customers who accepted a match but didn't book a visit. */
    @Scheduled(cron = "\${ndopify.match.visit-reminder.cron}")
    fun remindAcceptedWithoutVisit() {
        forEachTenant("remindAcceptedWithoutVisit", "match(es)") { reminderService.remindDue() }
    }
```

- [ ] **Step 5: Add the cron overrides**

`application-prod.yml`, under `ndopify.match`:

```yaml
        visit-reminder:
            cron: "0 0 9 * * ?"             # Every day at 09:00
```

`modules/ndopify-server/src/test/resources/application-qa.yml`, under `ndopify.match`:

```yaml
        visit-reminder:
            cron: "-" # Never on its own: tests call MatchJobs
```

- [ ] **Step 6: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn -q -Dtest=MatchJobsTest,RemindAcceptedMatchesJobTest test`
Expected: PASS.

- [ ] **Step 7: Document it in `modules/ndopify-server/CLAUDE.md`**

In the *Code Organization* jobs paragraph, extend the `MatchJobs` list with `remindAcceptedWithoutVisit` (daily). In the *match* domain section, after **Auto-proposal**, add **Visit reminder**:

> **Visit reminder** (`docs/superpowers/plans/2026-11-16-match-visit-reminder.md`): `visitRemindedAt` (`V12_1`) is set once the customer of an `ACCEPTED` match was reminded that it can book a visit. `MatchJobs.remindAcceptedWithoutVisit` (daily, `ndopify.match.visit-reminder.cron`) has `MatchVisitReminderService.remindDue` select the `ACCEPTED` matches with no open visit (`REQUESTED`, `CONFIRMED`, `CHECKED_IN`), answered at least `delay-days` (3) ago, not reminded yet, whose mandate is `APPROVED` and listing `PUBLISHED`; oldest first, `batch-size` (100) per run. One French e-mail per match (`MatchVisitReminderMailet`, `mail/match/visit_reminder.mjml`): the listing's photo, title and price, the agent's signature, and a fresh `MatchLinkTokenService` link to `{publicPortalUrl}/match/{id}` (none without a `publicPortalUrl`). The row is marked only after a successful send, so a failure retries on the next run; a customer without e-mail is marked without an e-mail; a match answered, closed or visited meanwhile is skipped (`@Version` / re-check). One reminder per match in v1, and the agent isn't told.

- [ ] **Step 8: Verify the module**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, tests and the JaCoCo gate (needs local MySQL).

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt \
        modules/ndopify-server/src/main/resources/application-prod.yml \
        modules/ndopify-server/src/test/resources/application-qa.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/MatchJobsTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/RemindAcceptedMatchesJobTest.kt \
        modules/ndopify-server/src/test/resources/db/test/match/RemindAcceptedMatchesJob.sql \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(match): schedule the visit reminder job

Refs #178"
```

---

## Self-Review

- **Spec coverage:** schema (Task 1), e-mail content and agent footer (Task 2), selection / 3-day delay / no open visit / mandate / listing / send-then-mark / no e-mail / batch (Tasks 3–4), job and daily cron, `qa` disabled, prod daily (Task 4), docs (Task 4). The agent is never notified; one reminder per match.
- **Type consistency:** `visitRemindedAt`, `findVisitReminderDue`, `existsOpenVisit`, `OPEN_VISIT_STATUSES`, `remindDue`, `remindAcceptedWithoutVisit` and `SUBJECT`/`TEMPLATE` are used with the same names and signatures in every task.
- **Review focus:** each of the six listed failure modes has a test in the owning task (selection/re-check: Tasks 3–4; send-then-mark: Task 3; no-e-mail marking: Tasks 3–4; one reminder: Task 4; no agent / no token log: Tasks 2–4; no public portal: Task 2).
