# Telegram Visit Reminder Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remind the agent of a confirmed visit 24 h ahead over Telegram, with the agent e-mail as the fallback for an unlinked agent or a failed send, while the customer's e-mail stays exactly as it is.

**Architecture:** `VisitReminderEvent` still reaches `VisitEventListener` (async, after commit), which calls `VisitService.sendReminderNotifications` (renamed from `sendReminderEmails`). That method sends the customer copy through `VisitReminderMailet.sendCustomer` and the agent copy through a new `VisitReminderNotifier`: a linked chat picks `VisitReminderBotlet`, otherwise — or when the botlet throws — `VisitReminderMailet.sendAgent`. The co-brokerage reminder stays an e-mail.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/149 (the story is the spec; groomed on 2026-11-15 with the "WhatsApp only" and "split the mailet" decisions)

## Global Constraints

- **Precondition: #136 and #148 are merged** (they are, on `main`). This plan consumes their code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, url)`, `AgentTelegramService.chatId(agentId)`, the `visit/server/bot/` package and the notifier/botlet convention (`VisitRequestedBotlet` / `VisitRequestedNotifier`).
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` / `ndopify-web-core` change, no UI page, no new config.
- The bot's text is French and mirrors `mail/visit/reminder_agent.mjml`; code, comments, logs and docs are English.
- **The botlet takes the chat id:** `send(visit, customer, agent, listing, chatId)` — every `*Botlet` receives the link's chat id as its last parameter; the notifier resolves it from `AgentTelegramService.chatId`.
- The e-mail is the fallback, not a second channel: a linked agent gets no reminder e-mail; a failed Telegram send logs (never the token nor the URL) and calls `sendAgent`.
- **The mailet is split:** `sendCustomer(...)` and `sendAgent(...)`; `VisitService` calls `sendCustomer` and the notifier calls `sendAgent`. A fallback must never re-send the customer copy.
- **Message content:** WhatsApp number included; no check-in instruction; never the 4-digit code, never the listing's exact address (`landmark`, `accessDirections`, position).
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- KDoc on classes and public methods.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #149` in the body of each.

## Review Focus

- A linked agent whose bot is blocked or Telegram is down: the agent e-mail is sent exactly once, the failure is logged, no exception escapes the listener, and the customer gets exactly one e-mail.
- An unlinked agent: no Telegram call at all.
- A tenant without `partnerCentralUrl`: the message carries no button and no empty keyboard is sent.
- The reminder runs at any hour of the 24 h window: `aujourd'hui` / `demain` follows the tenant's calendar day, never UTC's.
- The text leaks neither the visit code nor the listing's landmark / access directions.
- A second reminder run sends nothing on either channel (`reminderSentAt` already set).

---

### Task 1: The reminder botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`; `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`; `TenantService.findById(id: Long): TenantEntity`; `LocationService.findByIdOrNull(id: Long): LocationEntity?`; `TenantEntity.timeZone: String`, `.partnerCentralUrl: String?`; `TenantFormatter(tenant)` (`phone(String?): String?`, `date(Date): String`, `time(Date): String`); `VisitEntity.scheduledAt: Date?`, `.type: VisitType`; `CustomerEntity.whatsappNumber: String?`, `.party.fullName()`; `AgentEntity.party.fullName()`; `ListingEntity.title: String?`, `.cityId: Long`, `.neighborhoodId: Long?`; `Clock`.
- Produces: `VisitReminderBotlet(tenantService, locationService, client, clock)` with `send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing botlet tests**

Model the fixtures and imports on `VisitRequestedBotletTest`. Fix the clock at **2026-12-01 10:00 in Douala** and build the visits with the same helpers:

```kotlin
class VisitReminderBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val clock = Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneId.of("UTC"))
    private val botlet = VisitReminderBotlet(tenantService, locationService, client, clock)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        timeZone = "Africa/Douala",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val listing = ListingEntity(id = 101L, title = "Nice apartment", cityId = 2370201L, neighborhoodId = 1001L)
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        code = "1234",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `an on-site reminder carries the slot, the place and the customer's WhatsApp`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Petit rappel : la visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Sur place
            Lieu: Bastos, Yaounde
            WhatsApp: +237 6 00 00 00 01
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a video reminder carries the WhatsApp call note`() {
        botlet.send(visit.copy(type = VisitType.VIDEO), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour Kim Possible,

            Petit rappel : la visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Visite vidéo
            Lieu: Bastos, Yaounde
            WhatsApp: +237 6 00 00 00 01

            Depuis le bien, appelez Ray Sponsible en vidéo sur WhatsApp. Le client vous lira son code de visite pendant l'appel.
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("Type: Sur place"))
    }

    @Test
    fun `a visit later today says aujourd'hui`() {
        botlet.send(visit.copy(scheduledAt = date(2026, 12, 1, 20, 0)), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("a lieu aujourd'hui, 01/12/2026 à 20:00"))
    }

    @Test
    fun `no WhatsApp number leaves no line`() {
        botlet.send(visit, customer.copy(whatsappNumber = null), agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("WhatsApp:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = " "), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("la visite du bien Bien n° 101 avec Ray Sponsible"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `no visit code and no exact address in the text`() {
        botlet.send(
            visit,
            customer,
            agent,
            listing.copy(landmark = "Rue 1.234", accessDirections = "Derrière la pharmacie"),
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("1234"))
        assertFalse(text.firstValue.contains("Rue 1.234"))
        assertFalse(text.firstValue.contains("Derrière la pharmacie"))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: FAIL — `VisitReminderBotlet` is unresolved.

- [ ] **Step 3: Implement the botlet**

`VisitReminderBotlet(tenantService, locationService, client, clock)`, `@Service`, one `send(visit, customer, agent, listing, chatId)`. KDoc: mirrors `VisitReminderMailet`'s agent copy, never the code nor the exact address; the recipient is the agent's chat from #135's link (`AgentTelegramService.chatId`); Telegram refusing the message throws, so the notifier falls back to the e-mail.

Build the text:

```text
Bonjour <agent.party.fullName()>,

Petit rappel : la visite du bien <listing.title, or "Bien n° <listing.id>"> avec <customer.party.fullName()> a lieu <aujourd'hui|demain>, <formatter.date(visit.scheduledAt)> à <formatter.time(visit.scheduledAt)>.

Type: <"Sur place" for ON_SITE, "Visite vidéo" for VIDEO>
[Lieu: <neighbourhood>, <city>]                    (the mailet's placeOf: omit without either)
[WhatsApp: <formatter.phone(customer.whatsappNumber)>]   (only when it formats to a value)

[Depuis le bien, appelez <customer.party.fullName()> en vidéo sur WhatsApp. Le client vous lira son code de visite pendant l'appel.]   (VIDEO only)
```

`aujourd'hui` when `visit.scheduledAt` falls on the current date in `ZoneId.of(tenant.timeZone)`, else `demain` (the job only reminds within 24 h, so no other value). `UNKNOWN` never reaches it: the schedule endpoint rejects it. Then:

```kotlin
client.sendMessage(
    chatId,
    text,
    tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
        listOf(TelegramButton("Voir la visite", url = "$url/visits/${visit.id}"))
    } ?: emptyList(),
)
```

Reuse `VisitReminderMailet`'s private helpers for the title (`listingTitle`) and the place (`placeOf` with `LocationService`); do not call `TenantFormatter` for the exact address.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt
git commit -m "feat(visit): remind the agent of a visit over Telegram" -m "Refs #149"
```

---

### Task 2: Split the reminder mailet

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailetTest.kt`

**Interfaces:**
- Produces: `VisitReminderMailet.sendCustomer(visit, customer, agent, listing)` and `VisitReminderMailet.sendAgent(visit, customer, agent, listing)`, replacing `send(...)`. Both keep the current templates, subjects and contexts; the customer copy keeps the code, exact address and public-portal link, the agent copy keeps the WhatsApp number and partner-central link.

- [ ] **Step 1: Update the mailet test to the split**

Replace `send()` with two tests, capturing exactly one e-mail each:

```kotlin
@Test
fun `sendCustomer sends only the customer copy`() {
    mailet.sendCustomer(visit, customer, agent, listing)

    val email = email()
    assertEquals("ray.sponsible@gmail.com", email.recipient.email)
    assertEquals("Ndopify: rappel de votre visite", email.subject)
    assertEquals("Kim Possible", email.sender?.displayName)

    val customerContext = context(VisitReminderMailet.TEMPLATE_CUSTOMER)
    assertEquals("02/12/2026", customerContext["visit_date"])
    assertEquals("10:00", customerContext["visit_time"])
    assertEquals("1234", customerContext["code"])
    assertEquals("Bastos, Yaounde", customerContext["place"])
    assertEquals("près de Rue 1.234", customerContext["landmark"])
    assertFalse(customerContext.containsKey("address"))
    assertEquals("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz", customerContext["visit_url"])
}

@Test
fun `sendAgent sends only the agent copy`() {
    mailet.sendAgent(visit, customer, agent, listing)

    val email = email()
    assertEquals("kim.possible@gmail.com", email.recipient.email)
    assertEquals("Ndopify: rappel de la visite avec Ray Sponsible", email.subject)
    assertNull(email.sender)

    val agentContext = context(VisitReminderMailet.TEMPLATE_AGENT)
    assertEquals("+237 6 00 00 00 01", agentContext["customer_whatsapp"])
    assertEquals("https://partner.ndopify.com/visits/1", agentContext["visit_url"])
}

private fun email(): Email {
    val captor = argumentCaptor<Email>()
    verify(mailService).send(captor.capture())
    return captor.firstValue
}
```

`a VIDEO reminder has no address in the e-mail` calls `mailet.sendCustomer(...)`; `blank title` calls `mailet.sendCustomer(...)`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderMailetTest`
Expected: FAIL — `sendCustomer` / `sendAgent` are unresolved.

- [ ] **Step 3: Split `VisitReminderMailet.send`**

Keep both e-mails' bodies exactly as they are, one per method. `sendCustomer(...)` builds the customer `Email` (customer token, public-portal URL, `place`, `landmark`, `code`) and `sendAgent(...)` the agent `Email` (agent URL, customer WhatsApp). Keep the `SUBJECT_*`, `TEMPLATE_*` constants and the `listingTitle` / `placeOf` / `landmarkOf` helpers. No other caller exists on `main` after #148; `VisitService` is updated in Task 4.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderMailetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailetTest.kt
git commit -m "refactor(visit): split the reminder into one copy per recipient" -m "Refs #149"
```

---

### Task 3: Route the reminder to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitReminderNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitReminderNotifierTest.kt`

**Interfaces:**
- Consumes: Task 1's `VisitReminderBotlet.send(visit, customer, agent, listing, chatId)`; Task 2's `VisitReminderMailet.sendAgent(visit, customer, agent, listing)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `VisitReminderNotifier(botlet, mailet, telegramService)` with `send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)`.

- [ ] **Step 1: Write the failing notifier test**

Mirror `VisitRequestedNotifierTest`:

```kotlin
class VisitReminderNotifierTest {
    private val botlet = mock<VisitReminderBotlet>()
    private val mailet = mock<VisitReminderMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = VisitReminderNotifier(botlet, mailet, telegramService)

    private val visit = VisitEntity(id = 1L, agentId = 20L, status = VisitStatus.CONFIRMED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendAgent(visit, customer, agent, listing)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(botlet).send(visit, customer, agent, listing, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the agent e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(visit, customer, agent, listing, 1000L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendAgent(visit, customer, agent, listing)
        verify(mailet, never()).sendCustomer(any(), any(), any(), any())
    }
}
```

(Add `com.nhaarman.mockitokotlin2.never` to the imports.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderNotifierTest`
Expected: FAIL — `VisitReminderNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

Same shape as `VisitRequestedNotifier`: `val chatId = telegramService.chatId(agent.id!!)`; null → `mailet.sendAgent(visit, customer, agent, listing)`; else `try { botlet.send(..., chatId) } catch (ex: Exception) { LOGGER.warn("Telegram visit reminder failed for Visit#${visit.id}, falling back to e-mail", ex); mailet.sendAgent(...) }`. Catch `Exception`, not only `TelegramException`. KDoc: the channel is #135's link; the agent e-mail is the fallback; the customer copy is never this notifier's job; failures are logged without the token nor the URL.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitReminderNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitReminderNotifierTest.kt
git commit -m "feat(visit): route the visit reminder to Telegram or e-mail" -m "Refs #149"
```

---

### Task 4: Wire the service and the listener

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt` (constructor, `sendReminderEmails`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt` (`onVisitReminder`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Consumes: Task 2's `sendCustomer(...)`; Task 3's `VisitReminderNotifier.send(...)`.
- Produces: `VisitService.sendReminderNotifications(visitId: Long): Unit`, replacing `sendReminderEmails`; `VisitService`'s constructor takes `reminderNotifier: VisitReminderNotifier` next to `reminderMailet: VisitReminderMailet`.

- [ ] **Step 1: Update the failing service tests**

Add `private val reminderNotifier = mock<VisitReminderNotifier>()` after `requestedNotifier`, pass it to the constructor right after `reminderMailet`, rename the existing `sendReminderEmails` call to `sendReminderNotifications`, and add:

```kotlin
@Test
fun `reminder - the customer is e-mailed and the agent notified`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))))
        .whenever(dao).findById(1L)

    service.sendReminderNotifications(1L)

    verify(reminderMailet).sendCustomer(any(), eq(customer), eq(agent20), eq(coBrokerageListing))
    verify(reminderNotifier).send(any(), eq(customer), eq(agent20), eq(coBrokerageListing))
}

@Test
fun `reminder - nothing once no longer CONFIRMED`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.CANCELLED, scheduledAt = date(2026, 12, 2, 10, 0))))
        .whenever(dao).findById(1L)

    service.sendReminderNotifications(1L)

    verifyNoInteractions(reminderMailet, reminderNotifier)
}

@Test
fun `reminder - nothing for an unknown visit`() {
    doReturn(Optional.empty<VisitEntity>()).whenever(dao).findById(999L)

    service.sendReminderNotifications(999L)

    verifyNoInteractions(reminderMailet, reminderNotifier)
}
```

The existing `reminder - the listing agent is mailed when the visit belongs to another agent` keeps its body, with `sendReminderNotifications`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: FAIL — `sendReminderNotifications` and `VisitReminderNotifier` are unresolved.

- [ ] **Step 3: Rewire the service and the listener**

`VisitService`: add `private val reminderNotifier: VisitReminderNotifier` after `reminderMailet`; rename `sendReminderEmails(id: Long)` to `sendReminderNotifications`, keep its `@Transactional` and its guard (`visit == null` or `status != CONFIRMED` or `scheduledAt == null` → return), then:

```kotlin
val agent = agentService.findById(visit.agentId)
val listing = listingService.findById(visit.listingId)
val customer = customerService.findById(visit.customerId)
reminderMailet.sendCustomer(visit, customer, agent, listing)
reminderNotifier.send(visit, customer, agent, listing)
if (visit.agentId != listing.agent.id) {
    coBrokerageVisitMailet.sendReminder(visit, listing, agent)
}
```

KDoc: "Notifies both sides of a confirmed visit, 24 h ahead: the customer by e-mail, the agent over Telegram when its chat is linked, the e-mail otherwise or as the fallback; nothing when the visit is no longer CONFIRMED."

`VisitEventListener.onVisitReminder`: call `service.sendReminderNotifications(event.visitId)`; keep the log keys.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): notify the agent of a reminder through the notifier" -m "Refs #149"
```

---

### Task 5: Full-context test of a reminder

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitReminderJob.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt`

**Interfaces:**
- Consumes: `VisitJobs.remind()`, `RemindVisitsJob.sql`'s visits, the beans from Tasks 1–4, `@MockitoBean TelegramClient`, `awaitAsyncTasks()`.
- Produces: no production code; proves the wiring, the link lookup, the message and the no-e-mail rule against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramVisitReminderJob.sql` only links agent 20 (visit 1 of `RemindVisitsJob.sql`) to chat 1000:

```sql
-- The agent of visit 1 (RemindVisitsJob.sql) is linked to the bot; tenant 2's agent 90 stays unlinked
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing job test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/RemindVisitsJob.sql",
        "/db/test/telegram/TelegramVisitReminderJob.sql",
    ],
)
class TelegramVisitReminderJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: VisitJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a linked agent gets the reminder over Telegram, the customer by e-mail`() {
        jobs.remind()
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("Bonjour Kim Possible,"))
        assertTrue(text.firstValue.contains("Petit rappel"))
        assertTrue(text.firstValue.contains("Nice apartment"))
        assertTrue(text.firstValue.contains("Ray Sponsible"))
        assertTrue(text.firstValue.contains("Type: Sur place"))
        assertTrue(text.firstValue.contains("Lieu: Bastos, Yaounde"))
        assertTrue(text.firstValue.contains("WhatsApp: +237 6 00 00 00 01"))
        assertFalse(text.firstValue.contains("1111"))
        assertFalse(text.firstValue.contains("Rue 1.234"))
        assertFalse(text.firstValue.contains("Derrière la pharmacie"))
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 3), "customer copy of visit 1 + both copies of tenant 2's visit")
        assertTrue(
            smtp.receivedMessages.none { message ->
                message.allRecipients.any { it.toString().contains("kim.possible") }
            },
            "no reminder e-mail for the linked agent",
        )
    }

    @Test
    fun `the reminder reaches the bot only once`() {
        jobs.remind()
        awaitAsyncTasks()

        jobs.remind()
        awaitAsyncTasks()

        verify(client, times(1)).sendMessage(any(), any(), any())
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitReminderJobTest`
Expected: PASS — Tasks 1–4 implemented the wiring; a failure points at the seed, the expected e-mail count, or a missed wiring (not at new production code). If the e-mail count is off, recount: visit 1 is linked (customer only), visit 5 is tenant 2 and unlinked (customer + agent) → 3 messages.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitReminderJob.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt
git commit -m "test(visit): cover the Telegram visit reminder end to end" -m "Refs #149"
```

---

### Task 6: Document the reminder channel

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **visit** bullet and the **Asynchronous e-mails** list)

- [ ] **Step 1: Update the module guide**

In the **visit** bullet, where `reminder` is described, say that the customer's copy stays an e-mail and the agent's goes over Telegram when #135's chat is linked, through `VisitReminderNotifier` / `VisitReminderBotlet`, with `VisitReminderMailet.sendAgent` as the fallback; the co-brokerage reminder stays an e-mail. In **Asynchronous e-mails**, rename `VisitService.sendReminderEmails` to `sendReminderNotifications`.

- [ ] **Step 2: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(visit): document the Telegram visit reminder" -m "Refs #149"
```

---

### Task 7: Full verification

- [ ] **Step 1: Run the module's full build**

Run: `cd modules/ndopify-server && mvn verify`
Expected: `BUILD SUCCESS`; ktlint, the tests and the JaCoCo gate pass. A local MySQL must be running.

- [ ] **Step 2: Check the tree**

Run: `git status`
Expected: clean (only the intended commits; no `-SNAPSHOT` edit — this story touches no library).

- [ ] **Step 3: Report**

Tell the user the story is ready for `push 149`; do not push.
