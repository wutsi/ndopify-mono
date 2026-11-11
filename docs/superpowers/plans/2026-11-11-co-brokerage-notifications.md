# Co-Brokerage Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notify the search agent when another agent's listing matches their search, and notify the listing agent when a search agent shows interest, while keeping customers and counterpart agents unable to reach each other.

**Architecture:** A new `CoBrokerageNotificationService` is called from the existing `MatchJobs.notifyAgents()` cron. It sends two separate emails with their own templates: a forward email to the search agent (listing + co-agent contact) and a reverse email to the listing agent after a shortlist/proposal (interested search agent contact + anonymized criteria, never the customer). A new `T_MATCH.listing_agent_notified_at` column makes the reverse direction one-shot; `notifiedAt` stays the forward one-shot.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), Flyway/MySQL, MJML + Mustache mail templates, mockito-kotlin, `kotlin.test`, `TestRestTemplate`/job integration tests.

**Spec:** `docs/superpowers/specs/2026-11-11-co-brokerage-notifications-design.md`

## Global Constraints

- Server-only: no new endpoint, no `ndopify-dto` release, no new `ndopify.match.*` cron property.
- Reuse `ndopify.match.notify.cron` (prod hourly, dev every 15 min); both directions ride `MatchJobs.notifyAgents()`.
- Forward one-shot: `MatchEntity.notifiedAt`. Reverse one-shot: `MatchEntity.listingAgentNotifiedAt` (new, `V10_6`).
- Reverse is sent only after interest: `SHORTLISTED`, `PROPOSED`, `ACCEPTED`, `EXPIRED`; never `ACTIVE`, `REJECTED`, `DISMISSED`.
- Anti-sautage is a hard invariant: a customer never learns the counterpart agent's identity/contact, and a counterpart agent never learns the customer's identity/contact/mandate. No email or link crosses that boundary.
- The own-inventory pipeline and `new_matches.mjml` stay unchanged; co-brokerage rows are still excluded from it.
- French copy in emails, matching the existing tone.
- Tests with `kotlin.test`, mocks with mockito-kotlin; job/integration tests need local MySQL (root, no password).
- Coverage gate of `ndopify-server`: `jacoco.threshold.line` 0.95, `jacoco.threshold.class` 0.90.
- Work on branch `feat/match-co-brokerage-notifications`.

## Review Focus

1. **A counterpart agent with neither WhatsApp nor e-mail** → the email still renders their name/photo, shows no empty contact link, and does not crash.
2. **A co-brokerage match whose counterpart agent row is missing** → the job skips that recipient without failing the tenant and without marking the row.
3. **A search closed or a listing rented between the interest and the next job run** → no reverse e-mail and no `listing_agent_notified_at` mark.
4. **`partnerCentralUrl` unset** → neither email renders a CTA link.
5. **An own-inventory match pending in the same run** → it reaches only the own-inventory mailet, never either co-brokerage mailet.

---

### Task 1: Forward co-brokerage email

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/co_brokerage_matches.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailetTest.kt`

**Interfaces:**
- Consumes: `MatchEntity`, `ListingEntity.agent`, `TenantService`, `CustomerService`, `LocationService`, `MailService`, `MailBodyResolver`, `ListingImageService`, `AgentMailContext`, `MandateMailLabels`, `TenantFormatter`.
- Produces:
  - `CoBrokerageMatchMailet.send(agent: AgentEntity, matches: List<MatchEntity>)`
  - constants `TEMPLATE = "/mail/match/co_brokerage_matches.mjml"`, `SUBJECT_ONE`, `SUBJECT_MANY`, `MAX_LISTINGS = 5`
  - template context keys `tenant_logo_url`, `tenant_name`, `recipient_name`, `mandates`; each mandate has `customer_name`, `listing_type`, `property_type`, `place`, `mandate_url`, `listings`, `more`; each listing has `image_url`, `title`, `price`, `score`, `agent_full_name`, `agent_photo_url`, `agent_whatsapp`, `agent_whatsapp_url`, `agent_email`, `agent_email_url`.

- [ ] **Step 1: Write the failing mailet tests**

Create `CoBrokerageMatchMailetTest` with the fixture from `MatchNotificationMailetTest` (mock services, tenant, agent, mandate, listing, match) and these values: tenant 1 `Ndopify`, `partnerCentralUrl = https://pc.ndopify.com`, XAF; search agent 20 `Kim Possible` (`kim.possible@gmail.com`); listing agent 30 `John Doe` (`john.doe@gmail.com`, WhatsApp `+237677889900`); search customer 10 `Ray Sponsible`; listing customer 11 `Secret Owner` (`owner@gmail.com`); search mandate 1 APARTMENT, 2 rooms, budget 100 000, Bastos (Yaounde); listing 101 `Villa Bastos`, 90 000 XAF, agent 30; match score 0.8. Tests:

```kotlin
@Test
fun send() {
    mailet.send(searchAgent, listOf(match))

    val context = context()
    val mandate = (context["mandates"] as List<*>).single() as Map<*, *>
    assertEquals("Ray Sponsible", mandate["customer_name"])
    assertEquals("Bastos (Yaounde)", mandate["place"])
    assertEquals("https://pc.ndopify.com/mandates/1", mandate["mandate_url"])

    val listing = (mandate["listings"] as List<*>).single() as Map<*, *>
    assertEquals("Villa Bastos", listing["title"])
    assertEquals("90 000 FCFA / mois", listing["price"])
    assertEquals(80, listing["score"])
    assertEquals("John Doe", listing["agent_full_name"])
    assertEquals("https://wa.me/237677889900", listing["agent_whatsapp_url"])
    assertNull(listing["agent_email"])

    // Anti-sautage: the search agent's own customer is fine, the listing customer is not
    val rendered = context.toString()
    assertFalse(rendered.contains("Secret Owner"))
    assertFalse(rendered.contains("owner@gmail.com"))
    verify(customerService, never()).findByIdOrNull(11L)

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
    assertEquals("Ndopify: 1 bien d'un autre agent correspond à vos recherches", email.firstValue.subject)
}

@Test
fun `no WhatsApp - the email is the contact`() {
    val noWhatsApp = coAgent.copy(whatsappNumber = null)
    mailet.send(searchAgent, listOf(match.copy(listing = listing.copy(agent = noWhatsApp))))

    val card = listingCard()
    assertNull(card["agent_whatsapp_url"])
    assertEquals("john.doe@gmail.com", card["agent_email"])
    assertEquals("mailto:john.doe@gmail.com", card["agent_email_url"])
}

@Test
fun `no WhatsApp and no email - no contact link`() {
    val noContact = coAgent.copy(
        whatsappNumber = null,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = ""),
    )
    mailet.send(searchAgent, listOf(match.copy(listing = listing.copy(agent = noContact))))

    val card = listingCard()
    assertNull(card["agent_whatsapp_url"])
    assertNull(card["agent_email"])
    assertNull(card["agent_email_url"])
    assertEquals("John Doe", card["agent_full_name"])
}

@Test
fun `no partner portal - no mandate link`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    mailet.send(searchAgent, listOf(match))

    val mandate = (context()["mandates"] as List<*>).single() as Map<*, *>
    assertNull(mandate["mandate_url"])
}

@Test
fun `more than 5 listings`() {
    val matches = (1..6).map { i -> match.copy(id = i.toLong(), mandate = search) }
    mailet.send(searchAgent, matches)

    val mandate = (context()["mandates"] as List<*>).single() as Map<*, *>
    assertEquals(5, (mandate["listings"] as List<*>).size)
    assertEquals(1, mandate["more"])
}

@Test
fun template() {
    val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )
    val html = resolver.resolve(
        CoBrokerageMatchMailet.TEMPLATE,
        mapOf(
            "tenant_name" to "Ndopify",
            "recipient_name" to "Kim Possible",
            "mandates" to listOf(
                mapOf(
                    "customer_name" to "Ray Sponsible",
                    "listing_type" to "Location",
                    "property_type" to "Appartement",
                    "place" to "Bastos (Yaounde)",
                    "mandate_url" to "https://pc.ndopify.com/mandates/1",
                    "listings" to listOf(
                        mapOf(
                            "title" to "Villa Bastos",
                            "price" to "90 000 FCFA / mois",
                            "score" to 80,
                            "agent_full_name" to "John Doe",
                            "agent_whatsapp_url" to "https://wa.me/237677889900",
                        ),
                    ),
                    "more" to null,
                ),
            ),
        ),
    )
    assertTrue(html.contains("Villa Bastos"))
    assertTrue(html.contains("John Doe"))
    assertTrue(html.contains("https://wa.me/237677889900"))
    assertFalse(html.contains("Secret Owner"))
    assertFalse(html.contains("owner@gmail.com"))
}
```

Add these helpers to the test class:

```kotlin
private fun context(): Map<String, Any?> {
    val context = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver).resolve(eq(CoBrokerageMatchMailet.TEMPLATE), context.capture())
    return context.firstValue
}

private fun listingCard(): Map<*, *> =
    (((context()["mandates"] as List<*>).single() as Map<*, *>)["listings"] as List<*>).single() as Map<*, *>
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageMatchMailetTest`
Expected: compilation fails with unresolved reference `CoBrokerageMatchMailet`.

- [ ] **Step 3: Implement the mailet**

`CoBrokerageMatchMailet.send` mirrors `MatchNotificationMailet.send` (same tenant lookup, formatter, group-by-mandate, best score first, `MAX_LISTINGS` cap, `more` count, `MailBodyResolver`, `Email`). Differences:
- subject constants are `SUBJECT_ONE = "{{tenant_name}}: 1 bien d'un autre agent correspond à vos recherches"` and `SUBJECT_MANY = "{{tenant_name}}: {{count}} biens d'autres agents correspondent à vos recherches"`;
- each listing card adds the listing agent's contact via `AgentMailContext.of(listing.agent, formatter)`, plus `agent_email`/`agent_email_url` only when `listing.agent.whatsappNumber` is blank and `listing.agent.party.email` is not;
- `customerService.findByIdOrNull(mandate.customerId)` is used only for the recipient's own search customer; never for `listing.mandate.customerId`.

- [ ] **Step 4: Write the template**

Create `co_brokerage_matches.mjml` modeled on `new_matches.mjml`: the same logo/divider/sign-off, body text "Des biens publiés par d'autres agents correspondent aux recherches de vos clients :", each mandate header with customer/criteria/place, listing rows with image, title, price, score, and the co-agent's name + WhatsApp link or e-mail link, the "more" line, and the "Voir le mandat" button when `mandate_url` is present. Do not render the listing's customer.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageMatchMailetTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailet.kt \
    modules/ndopify-server/src/main/resources/mail/match/co_brokerage_matches.mjml \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailetTest.kt
git commit -m "feat(match): email co-brokerage matches to the search agent"
```

---

### Task 2: Forward delivery on the job

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V10_6__match-listing-agent-notified.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/match/NotifyCoBrokerageJob.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt`

**Interfaces:**
- Consumes: `CoBrokerageMatchMailet` (Task 1).
- Produces:
  - `MatchEntity.listingAgentNotifiedAt: Date? = null`
  - `MatchRepository.findCoBrokerageToNotify(status: MatchStatus, mandateStatus: MandateStatus, listingStatus: ListingStatus): List<MatchEntity>`
  - `CoBrokerageNotificationService.notifySearchAgents(): Int`
  - `MatchJobs` constructor gains `coBrokerageNotificationService: CoBrokerageNotificationService`.

- [ ] **Step 1: Write the failing service tests**

Create `CoBrokerageNotificationServiceTest` with mocks for `MatchRepository`, `AgentService`, `CoBrokerageMatchMailet`, `Clock` (and `agent20 = AgentEntity(id = 20L)`, `agent21 = AgentEntity(id = 21L)` in the fixture) and these cases:

```kotlin
@Test
fun `sends one email per search agent and marks the matches`() {
    val first = MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)
    val second = first.copy(id = 2L)
    val third = first.copy(id = 3L, agentId = 21L)
    doReturn(listOf(first, second, third)).whenever(dao)
        .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
    doReturn(agent20).whenever(agentService).findByIdOrNull(20L)
    doReturn(agent21).whenever(agentService).findByIdOrNull(21L)

    assertEquals(2, service.notifySearchAgents())

    verify(mailet).send(agent20, listOf(first, second))
    verify(mailet).send(agent21, listOf(third))
    verify(dao).markNotified(listOf(1L, 2L), any())
    verify(dao).markNotified(listOf(3L), any())
}

@Test
fun `a failed email leaves the matches for the next run`() {
    val first = MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)
    val second = first.copy(id = 2L)
    val third = first.copy(id = 3L, agentId = 21L)
    doReturn(listOf(first, second, third)).whenever(dao)
        .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
    doReturn(agent20).whenever(agentService).findByIdOrNull(20L)
    doReturn(agent21).whenever(agentService).findByIdOrNull(21L)
    doThrow(RuntimeException("SMTP down")).whenever(mailet).send(eq(agent20), any())

    assertEquals(1, service.notifySearchAgents())

    verify(dao, never()).markNotified(listOf(1L, 2L), any())
    verify(dao).markNotified(listOf(3L), any())
}

@Test
fun `an unknown agent is skipped without marking`() {
    doReturn(listOf(MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)))
        .whenever(dao).findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
    doReturn(null).whenever(agentService).findByIdOrNull(20L)

    assertEquals(0, service.notifySearchAgents())

    verify(mailet, never()).send(any(), any())
    verify(dao, never()).markNotified(any(), any())
}

@Test
fun `nothing pending`() {
    doReturn(emptyList<MatchEntity>()).whenever(dao)
        .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)

    assertEquals(0, service.notifySearchAgents())

    verify(mailet, never()).send(any(), any())
    verify(dao, never()).markNotified(any(), any())
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageNotificationServiceTest`
Expected: compilation fails with unresolved reference `CoBrokerageNotificationService`.

- [ ] **Step 3: Write the failing job test and fixture**

Create `NotifyCoBrokerageJob.sql` with tenant 1, city `2370201`, neighbourhood `1001`, parties `100` (search customer), `101` (listing customer), `200` (agent 20), `300` (agent 30), customers `10`/`11`, agents `20`/`30`, and:

- searches `1`, `2`, `3` of agent 20, customer 10, APPROVED, RENTAL/APARTMENT;
- listing mandates `11` (agent 30, customer 11) and `12` (agent 20, customer 11);
- listings `101` (agent 30, PUBLISHED, 90 000 XAF) and `102` (agent 20, PUBLISHED, 100 000 XAF);
- matches:

```sql
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, notified_at, co_brokerage) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.8, 90000, NULL, TRUE),                     -- forward
    (2, 1, 1, 102, 20, 'ACTIVE', 0.9, 100000, NULL, FALSE),                   -- own inventory
    (3, 1, 2, 101, 20, 'ACTIVE', 0.8, 90000, '2026-10-01 10:00:00', TRUE),    -- already notified
    (4, 1, 3, 101, 20, 'SHORTLISTED', 0.8, 90000, NULL, TRUE);                -- interest, not a forward match
```

Create `NotifyCoBrokerageJobTest` extending `BaseEndpointIntegrationTest`, with `@Sql` for `clean.sql` + the fixture, `@MockitoBean` for `MatchNotificationMailet`, `CoBrokerageMatchMailet` and (in Task 4) `CoBrokerageInterestMailet`, and:

```kotlin
@Test
fun `notifies the search agent of a co-brokerage match`() {
    jobs.notifyAgents()

    val coBrokerage = argumentCaptor<List<MatchEntity>>()
    verify(coBrokerageMailet).send(argThat { id == 20L }, coBrokerage.capture())
    assertEquals(listOf(1L), coBrokerage.firstValue.map { it.id })

    val own = argumentCaptor<List<MatchEntity>>()
    verify(ownMailet).send(argThat { id == 20L }, own.capture())
    assertEquals(listOf(2L), own.firstValue.map { it.id })

    assertTrue(notifiedAt(1L))
    assertFalse(notifiedAt(3L))
    assertFalse(notifiedAt(4L))
}

private fun notifiedAt(id: Long): Boolean =
    jdbc.queryForList("SELECT notified_at FROM T_MATCH WHERE id=?", id).single()["notified_at"] != null
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=NotifyCoBrokerageJobTest`
Expected: FAIL — the forward mailet is never called / the job has no co-brokerage service.

- [ ] **Step 5: Add the migration and entity field**

`V10_6__match-listing-agent-notified.sql`:

```sql
ALTER TABLE T_MATCH
    ADD COLUMN listing_agent_notified_at DATETIME DEFAULT NULL;

-- Rows that predate this slice: keep them visible, but do not e-mail a one-time backlog
UPDATE T_MATCH
    SET notified_at = COALESCE(created_at, NOW())
    WHERE co_brokerage = TRUE AND notified_at IS NULL;

UPDATE T_MATCH
    SET listing_agent_notified_at = modified_at
    WHERE co_brokerage = TRUE AND listing_agent_notified_at IS NULL
      AND status IN ('SHORTLISTED', 'PROPOSED', 'ACCEPTED', 'EXPIRED');
```

Add after `notifiedAt` in `MatchEntity`:

```kotlin
// When the listing's agent was told another agent's search matched their listing (co-brokerage interest)
val listingAgentNotifiedAt: Date? = null,
```

- [ ] **Step 6: Add the forward query**

In `MatchRepository`:

```kotlin
// The co-brokerage matches the search agent has not been told about yet
@Query(
    "SELECT m FROM MatchEntity m WHERE m.coBrokerage = TRUE AND m.status = :status " +
        "AND m.notifiedAt IS NULL AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus"
)
fun findCoBrokerageToNotify(
    status: MatchStatus,
    mandateStatus: MandateStatus,
    listingStatus: ListingStatus,
): List<MatchEntity>
```

- [ ] **Step 7: Implement the forward service**

Create `CoBrokerageNotificationService` (constructor: `dao`, `agentService`, `mailet`, `clock`). `notifySearchAgents()` loads `findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)`, groups by `agentId`, and per group resolves `agentService.findByIdOrNull`, calls `mailet.send(agent, matches)`, then `dao.markNotified(ids, Date(clock.millis()))`; wraps each group in try/catch, logs and leaves the rows on failure. Returns the number of emails sent.

- [ ] **Step 8: Wire the job**

`MatchJobs` gains `coBrokerageNotificationService`; `notifyAgents()` becomes:

```kotlin
forEachTenant("notifyAgents", "agent(s)") {
    notificationService.notifyAgents() + coBrokerageNotificationService.notifySearchAgents()
}
```

- [ ] **Step 9: Run both tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageNotificationServiceTest,NotifyCoBrokerageJobTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V10_6__match-listing-agent-notified.sql \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt \
    modules/ndopify-server/src/test/resources/db/test/match/NotifyCoBrokerageJob.sql \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt
git commit -m "feat(match): notify the search agent of co-brokerage matches"
```

---

### Task 3: Reverse co-brokerage interest email

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/match/co_brokerage_interest.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt`

**Interfaces:**
- Consumes: `MatchEntity`, `ListingEntity`, `AgentService` (interested search agent), `TenantService`, `LocationService`, `MailService`, `MailBodyResolver`, `ListingImageService`, `AgentMailContext`, `MandateMailLabels`.
- Produces:
  - `CoBrokerageInterestMailet.send(agent: AgentEntity, matches: List<MatchEntity>)` where `agent` is the listing agent
  - constants `TEMPLATE = "/mail/match/co_brokerage_interest.mjml"`, `SUBJECT_ONE`, `SUBJECT_MANY`, `MAX_AGENTS = 5`
  - **no `CustomerService` dependency** — the reverse email cannot read a customer by construction.
  - template context keys `tenant_logo_url`, `tenant_name`, `recipient_name`, `listings`; each listing has `image_url`, `title`, `price`, `mandate_url`, `agents`, `more`; each interested agent has `agent_full_name`, `agent_photo_url`, `agent_whatsapp`, `agent_whatsapp_url`, `agent_email`, `agent_email_url`, `search_count`, `listing_type`, `property_type`, `place`, `rooms`, `budget`.

- [ ] **Step 1: Write the failing mailet tests**

Create `CoBrokerageInterestMailetTest` with a listing agent (recipient), a search agent (interested), a listing, a search, and a match (status `SHORTLISTED`, `coBrokerage = true`). Values: tenant 1 `Ndopify`, `partnerCentralUrl = https://pc.ndopify.com`, XAF; listing agent 30 `John Doe` (`john.doe@gmail.com`, WhatsApp `+237677889900`); search agent 20 `Kim Possible` (`kim.possible@gmail.com`, WhatsApp `+237677889900`); search customer 10 `Secret Client` (`secret@example.com`); listing 101 `Villa Bastos`, 90 000 XAF, mandate 11; search mandate 1 APARTMENT, 2 rooms, budget 100 000, Bastos (Yaounde); match score 0.8. Tests:

```kotlin
@Test
fun send() {
    mailet.send(listingAgent, listOf(match))

    val context = context()
    val listing = (context["listings"] as List<*>).single() as Map<*, *>
    assertEquals("Villa Bastos", listing["title"])
    val agent = (listing["agents"] as List<*>).single() as Map<*, *>
    assertEquals("Kim Possible", agent["agent_full_name"])
    assertEquals("Appartement", agent["property_type"])
    assertEquals("Bastos (Yaounde)", agent["place"])
    assertNull(agent["search_count"])

    // Anti-sautage: the search customer never appears
    val rendered = context.toString()
    assertFalse(rendered.contains("Secret Client"))
    assertFalse(rendered.contains("secret@example.com"))

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("john.doe@gmail.com", email.firstValue.recipient.email)
    assertEquals("Ndopify: 1 agent s'intéresse à votre bien", email.firstValue.subject)
}

@Test
fun `several searches of the same agent - one row`() {
    val other = match.copy(id = 2L, mandate = search.copy(id = 2L, price = 200000), score = 0.9)
    mailet.send(listingAgent, listOf(match, other))

    val rows = agents()
    assertEquals(1, rows.size)
    val row = rows.single() as Map<*, *>
    assertEquals(2, row["search_count"])
    assertEquals("200 000 FCFA / mois", row["budget"])
}

@Test
fun `several agents - subject counts agents`() {
    val second = searchAgent.copy(
        id = 21L,
        party = PartyEntity(id = 210L, firstName = "Sam", lastName = "Sung", email = "sam@example.com"),
    )
    doReturn(second).whenever(agentService).findByIdOrNull(21L)
    val other = match.copy(id = 2L, mandate = search.copy(id = 2L, agentId = 21L))

    mailet.send(listingAgent, listOf(match, other))

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("Ndopify: 2 agents s'intéressent à vos biens", email.firstValue.subject)
}

@Test
fun `no WhatsApp - the email is the contact`() {
    doReturn(searchAgent.copy(whatsappNumber = null)).whenever(agentService).findByIdOrNull(20L)

    mailet.send(listingAgent, listOf(match))

    val row = agents().single() as Map<*, *>
    assertNull(row["agent_whatsapp_url"])
    assertEquals("kim.possible@gmail.com", row["agent_email"])
    assertEquals("mailto:kim.possible@gmail.com", row["agent_email_url"])
}

@Test
fun `no WhatsApp and no email - no contact link`() {
    doReturn(
        searchAgent.copy(
            whatsappNumber = null,
            party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = ""),
        )
    ).whenever(agentService).findByIdOrNull(20L)

    mailet.send(listingAgent, listOf(match))

    val row = agents().single() as Map<*, *>
    assertNull(row["agent_whatsapp_url"])
    assertNull(row["agent_email"])
    assertNull(row["agent_email_url"])
    assertEquals("Kim Possible", row["agent_full_name"])
}

@Test
fun `no partner portal - no mandate link`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    mailet.send(listingAgent, listOf(match))

    assertNull(listing()["mandate_url"])
}

@Test
fun template() {
    val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )
    val html = resolver.resolve(
        CoBrokerageInterestMailet.TEMPLATE,
        mapOf(
            "tenant_name" to "Ndopify",
            "recipient_name" to "John Doe",
            "listings" to listOf(
                mapOf(
                    "title" to "Villa Bastos",
                    "price" to "90 000 FCFA / mois",
                    "mandate_url" to "https://pc.ndopify.com/mandates/11",
                    "agents" to listOf(
                        mapOf(
                            "agent_full_name" to "Kim Possible",
                            "property_type" to "Appartement",
                            "place" to "Bastos (Yaounde)",
                            "budget" to "100 000 FCFA / mois",
                            "agent_whatsapp_url" to "https://wa.me/237677889900",
                        ),
                    ),
                    "more" to null,
                ),
            ),
        ),
    )
    assertTrue(html.contains("Villa Bastos"))
    assertTrue(html.contains("Kim Possible"))
    assertTrue(html.contains("https://wa.me/237677889900"))
    assertFalse(html.contains("Secret Client"))
    assertFalse(html.contains("secret@example.com"))
}
```

Add these helpers to the test class:

```kotlin
private fun context(): Map<String, Any?> {
    val context = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver).resolve(eq(CoBrokerageInterestMailet.TEMPLATE), context.capture())
    return context.firstValue
}

private fun listing(): Map<*, *> = (context()["listings"] as List<*>).single() as Map<*, *>

private fun agents(): List<*> = listing()["agents"] as List<*>
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestMailetTest`
Expected: compilation fails with unresolved reference `CoBrokerageInterestMailet`.

- [ ] **Step 3: Implement the mailet**

`CoBrokerageInterestMailet.send` resolves the tenant from `matches.first().listing.tenantId`, builds `TenantFormatter`, groups the matches by `listing.id` (best score first), then groups each listing's matches by `mandate.agentId` and keeps one row per agent:
- resolve the interested agent with `agentService.findByIdOrNull(agentId)`, skip if null;
- `search_count = perAgent.size` only when > 1;
- use the best-scoring match's mandate for `listing_type`, `property_type`, `place` (with `MandateMailLabels`), `rooms`, `budget` (`MandateMailLabels.budget`);
- add `AgentMailContext.of(agent, formatter)` plus `agent_email`/`agent_email_url` only when the agent has no WhatsApp and a non-blank e-mail;
- cap at `MAX_AGENTS` per listing, with `more`;
- subject count is the number of distinct `mandate.agentId` values across all matches;
- recipient is the `listingAgent` argument; the email body never calls `CustomerService` and never renders `match.mandate.customerId`.

- [ ] **Step 4: Write the template**

Create `co_brokerage_interest.mjml` modeled on `new_matches.mjml`: body "Votre bien intéresse d'autres agents.", each listing with image/title/price, then each interested agent with name/photo, WhatsApp or e-mail link, and the anonymized criteria (type, property type, place, rooms, budget, `N recherches` when present), the "more" line, and the "Voir le mandat" button when `mandate_url` is present. It must never show a customer name or e-mail.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestMailetTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt \
    modules/ndopify-server/src/main/resources/mail/match/co_brokerage_interest.mjml \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt
git commit -m "feat(match): email co-brokerage interest to the listing agent"
```

---

### Task 4: Reverse delivery on the job

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/match/NotifyCoBrokerageJob.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt`

**Interfaces:**
- Consumes: `CoBrokerageInterestMailet` (Task 3), `MatchEntity.listingAgentNotifiedAt` (Task 2).
- Produces:
  - `MatchRepository.findCoBrokerageInterestToNotify(statuses: Collection<MatchStatus>, mandateStatus: MandateStatus, listingStatus: ListingStatus): List<MatchEntity>`
  - `MatchRepository.markListingAgentNotified(ids: Collection<Long>, now: Date): Int`
  - `CoBrokerageNotificationService.notifyListingAgents(): Int`; its constructor gains `interestMailet`.
  - `MatchJobs.notifyAgents()` also calls `notifyListingAgents()`.

- [ ] **Step 1: Write the failing service tests**

Extend `CoBrokerageNotificationServiceTest` with `interestMailet` mocked and passed to the service constructor, plus:

```kotlin
@Test
fun `sends one email per listing agent and marks the interest`() {
    val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
        listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
    val second = first.copy(id = 5L, status = MatchStatus.PROPOSED)
    doReturn(listOf(first, second)).whenever(dao)
        .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
    doReturn(listingAgent).whenever(agentService).findByIdOrNull(30L)

    assertEquals(1, service.notifyListingAgents())

    verify(interestMailet).send(listingAgent, listOf(first, second))
    verify(dao).markListingAgentNotified(listOf(4L, 5L), any())
}

@Test
fun `a failed reverse email leaves the interest for the next run`() {
    val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
        listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
    doReturn(listOf(first)).whenever(dao)
        .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
    doReturn(listingAgent).whenever(agentService).findByIdOrNull(30L)
    doThrow(RuntimeException("SMTP down")).whenever(interestMailet).send(eq(listingAgent), any())

    assertEquals(0, service.notifyListingAgents())

    verify(dao, never()).markListingAgentNotified(any(), any())
}

@Test
fun `an unknown listing agent is skipped without marking`() {
    val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
        listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
    doReturn(listOf(first)).whenever(dao)
        .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
    doReturn(null).whenever(agentService).findByIdOrNull(30L)

    assertEquals(0, service.notifyListingAgents())

    verify(interestMailet, never()).send(any(), any())
    verify(dao, never()).markListingAgentNotified(any(), any())
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageNotificationServiceTest`
Expected: compilation fails because `notifyListingAgents`/`CoBrokerageInterestMailet` do not exist.

- [ ] **Step 3: Extend the fixture and job test**

Add searches `4`, `5`, `6` (agent 20, customer 10), a `CLOSED` search `7`, an `APPROVED` search `8`, and a `RENTED` listing `103` (agent 30, mandate 13). Then add:

```sql
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, notified_at, co_brokerage, listing_agent_notified_at) VALUES
    (5, 1, 4, 101, 20, 'PROPOSED', 0.8, 90000, '2026-10-01 10:00:00', TRUE, NULL),  -- reverse
    (6, 1, 5, 101, 20, 'REJECTED', 0.8, 90000, '2026-10-01 10:00:00', TRUE, NULL),  -- not reverse
    (7, 1, 6, 101, 20, 'DISMISSED', 0.8, 90000, '2026-10-01 10:00:00', TRUE, NULL), -- not reverse
    (8, 1, 7, 101, 20, 'SHORTLISTED', 0.8, 90000, '2026-10-01 10:00:00', TRUE, NULL), -- CLOSED search
    (9, 1, 8, 103, 20, 'SHORTLISTED', 0.8, 90000, '2026-10-01 10:00:00', TRUE, NULL); -- RENTED listing
```

Match `4` from Task 2 (`SHORTLISTED`, `notified_at` null) is also a reverse candidate. Add the job tests:

```kotlin
@Test
fun `notifies the listing agent when a search agent is interested`() {
    jobs.notifyAgents()

    val interest = argumentCaptor<List<MatchEntity>>()
    verify(interestMailet).send(argThat { id == 30L }, interest.capture())
    assertEquals(listOf(4L, 5L), interest.firstValue.map { it.id }.sorted())

    assertTrue(listingAgentNotifiedAt(4L))
    assertTrue(listingAgentNotifiedAt(5L))
    assertFalse(listingAgentNotifiedAt(6L)) // REJECTED
    assertFalse(listingAgentNotifiedAt(7L)) // DISMISSED
}

@Test
fun `a closed search or a rented listing is not notified`() {
    jobs.notifyAgents()

    assertFalse(listingAgentNotifiedAt(8L)) // search CLOSED
    assertFalse(listingAgentNotifiedAt(9L)) // listing RENTED
}

@Test
fun `nothing is sent on the next run`() {
    jobs.notifyAgents()
    reset(ownMailet, coBrokerageMailet, interestMailet)

    jobs.notifyAgents()

    verify(ownMailet, never()).send(any(), any())
    verify(coBrokerageMailet, never()).send(any(), any())
    verify(interestMailet, never()).send(any(), any())
}
```

Add the helper:

```kotlin
private fun listingAgentNotifiedAt(id: Long): Boolean =
    jdbc.queryForList("SELECT listing_agent_notified_at FROM T_MATCH WHERE id=?", id)
        .single()["listing_agent_notified_at"] != null
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=NotifyCoBrokerageJobTest`
Expected: FAIL — the interest mailet is never called and `listing_agent_notified_at` stays null.

- [ ] **Step 5: Add the reverse query and mark update**

In `MatchRepository`:

```kotlin
// Co-brokerage rows where the search agent showed interest, the listing agent not told yet
@Query(
    "SELECT m FROM MatchEntity m WHERE m.coBrokerage = TRUE AND m.listingAgentNotifiedAt IS NULL " +
        "AND m.status IN :statuses AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus"
)
fun findCoBrokerageInterestToNotify(
    statuses: Collection<MatchStatus>,
    mandateStatus: MandateStatus,
    listingStatus: ListingStatus,
): List<MatchEntity>

@Transactional
@Modifying
@Query("UPDATE MatchEntity m SET m.listingAgentNotifiedAt = :now WHERE m.id IN :ids")
fun markListingAgentNotified(@Param("ids") ids: Collection<Long>, @Param("now") now: Date): Int
```

- [ ] **Step 6: Implement `notifyListingAgents`**

Add `interestMailet` to the service constructor. `notifyListingAgents()` loads `findCoBrokerageInterestToNotify(INTEREST_STATUSES, MandateStatus.APPROVED, ListingStatus.PUBLISHED)` with `INTEREST_STATUSES = setOf(SHORTLISTED, PROPOSED, ACCEPTED, EXPIRED)`, groups by `listing.agent.id`, resolves the recipient via `agentService.findByIdOrNull`, calls `interestMailet.send(agent, matches)`, then `markListingAgentNotified(ids, now)`; per-agent try/catch, leaves rows on failure. Returns the number of emails sent.

- [ ] **Step 7: Wire the job**

`notifyAgents()`:

```kotlin
forEachTenant("notifyAgents", "agent(s)") {
    notificationService.notifyAgents() +
        coBrokerageNotificationService.notifySearchAgents() +
        coBrokerageNotificationService.notifyListingAgents()
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageNotificationServiceTest,NotifyCoBrokerageJobTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt \
    modules/ndopify-server/src/test/resources/db/test/match/NotifyCoBrokerageJob.sql \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt
git commit -m "feat(match): notify the listing agent of co-brokerage interest"
```

---

### Task 5: Anti-sautage regression and documentation

**Files:**
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` — the **match** bullet of *Domains in `ndopify-server`*

**Interfaces:**
- Consumes: nothing.
- Produces: the written co-brokerage notification rule and the regression guard the later slices inherit.

- [ ] **Step 1: Add the customer-proposal leak guard**

In `MatchProposedMailetTest` (add `import kotlin.test.assertFalse`):

```kotlin
@Test
fun `never exposes the listing agent or the listing customer`() {
    val coBroker = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "Co", lastName = "Broker", email = "co.broker@example.com"),
    )
    val coListing = listing.copy(
        mandate = mandate.copy(id = 11L, customerId = 11L, agentId = 30L, mandateType = MandateType.LISTING),
        agent = coBroker,
    )

    mailet.send(listOf(match.copy(listing = coListing)), customer, agent)

    val rendered = context().toString()
    assertFalse(rendered.contains("Co Broker"))
    assertFalse(rendered.contains("co.broker@example.com"))
    assertFalse(rendered.contains("Secret Owner"))
    assertFalse(rendered.contains("owner@example.com"))
}
```

- [ ] **Step 2: Run it**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposedMailetTest`
Expected: PASS. This is a regression guard for existing behavior (the proposal mailet already names only the search agent); it must stay green.

- [ ] **Step 3: Update the match documentation**

In the `match` bullet of `modules/ndopify-server/CLAUDE.md`, after the co-brokerage eligibility sentence added by the previous slice, append:

> Co-brokerage notifications (`V10_6`): the search agent is e-mailed the other agent's listing and contact when the match is created; the listing agent is e-mailed the interested agent's contact and anonymized search criteria only after a shortlist/proposal (`SHORTLISTED`, `PROPOSED`, `ACCEPTED`, `EXPIRED`). Both ride `MatchJobs.notifyAgents`; `notifiedAt` is the forward one-shot and `listingAgentNotifiedAt` the reverse one. Anti-sautage: a customer never learns the counterpart agent's identity/contact, and a counterpart agent never learns the customer's identity/contact/mandate; the customer-facing proposal still names only the search agent.

- [ ] **Step 4: Run the match package and the coverage gate**

Run: `cd modules/ndopify-server && mvn test -Dtest='com.wutsi.ndopify.match.**' && mvn verify`
Expected: PASS; JaCoCo line coverage ≥ 0.95 and class coverage ≥ 0.90.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt \
    modules/ndopify-server/CLAUDE.md
git commit -m "docs(match): document co-brokerage notifications and sautage guard"
```
