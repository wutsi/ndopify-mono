# Telegram Co-Brokerage Visit Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the listing agent (B) over Telegram when another agent (A) confirms, reminds, cancels or reports a customer no-show on a co-brokerage visit, with the e-mail as the fallback for an unlinked B or a failed send.

**Architecture:** `VisitService` routes the four co-brokerage sends through a new `CoBrokerageVisitNotifier`. The notifier resolves B's chat with `AgentTelegramService.chatId(listing.agent.id)` — linked → `CoBrokerageVisitBotlet`, otherwise `CoBrokerageVisitMailet`, and any botlet failure logs (never the token nor the URL) then sends the mailet. The botlet mirrors the mailet's four messages in French plain text, shares the e-mail's labels through `CoBrokerageVisitLabels`, and closes with A's name because the bot is the sender.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/154

## Global Constraints

- **Precondition: #136 is merged** (it is, on `main`). This plan consumes its code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier/botlet convention (`docs/superpowers/plans/2026-11-15-telegram-assignment-notification.md`).
- **#148's plan is on `main` but may or may not be implemented yet.** It touches the same `VisitService` constructor, but only the requested-visit wiring: this plan replaces the co-brokerage mailet field and its four calls. If `VisitService` still takes `requestedMailet`, leave it alone; never rename or rewire the requested-visit path here.
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` / `ndopify-web-core` change, no UI page, no new config.
- The bot's text is French and mirrors `mail/visit/co_brokerage*.mjml`; code, comments, logs and docs are English.
- **A's identity:** the bot is the sender, so the text closes with `Visite organisée par <A's full name>.`; no profile link and no second button. B is `listing.agent`, A is the `agent` parameter (the visit's organizer).
- **One URL button:** `Voir l'annonce` → `{partnerCentralUrl}/listings/{listingId}#visits-title`, omitted (`emptyList()`) without a `partnerCentralUrl`. The keyboard is never sent empty in a way that breaks the payload.
- **Privacy by construction:** the botlet never reads the customer; it only receives the visit, the listing and A. Its dependencies are `TenantService` and `TelegramClient` — no `CustomerService`, no `ListingImageService`. The customer appears only as `mon client` (confirmed / reminder) or `le client` (the cancellation / no-show labels the e-mail already uses), never by name or data.
- **The botlet takes the chat id as its last parameter:** `sendX(visit, listing, agent, chatId)`, as every `*Botlet` does; the notifier resolves it from `AgentTelegramService.chatId`.
- The e-mail is the fallback, not a second channel: a linked B gets no co-brokerage e-mail; a failed Telegram send logs (never the token nor the URL) and sends the mailet.
- Never log, throw or report the bot token or the Telegram URL.
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- KDoc on classes and public methods.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #154` in the body of each.

## Review Focus

- A linked B whose bot is blocked or Telegram is down: the co-brokerage e-mail is sent and no exception escapes the listener — a backstage notice is never lost (Task 2).
- An unlinked B: no Telegram call at all, only the e-mail (Task 2, Task 4).
- A tenant without `partnerCentralUrl`: the message carries no button and no malformed keyboard (Task 1).
- An own-inventory visit, `REQUESTED`, `EXPIRED`, `COMPLETED` or an agent no-show: neither channel is called (Task 3).
- A cancellation caused by the listing's mandate ending: B is neither re-mailed nor re-messaged (Task 3).
- The text never carries A's customer's name, e-mail, code, note, address or mandate: only the listing and "mon client" (Task 1, Task 4).

---

### Task 1: The message and its shared labels

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitLabels.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt` (companion constants and the three label expressions)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailetTest.kt` (unchanged, must stay green)

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`; `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`; `TenantService.findById(id: Long): TenantEntity`; `TenantFormatter(tenant)` (`date`, `time`); `VisitEntity.type: VisitType`, `.scheduledAt: Date?`, `.cancelledBy: VisitParty?`, `.noShowReason: VisitNoShowReason?`; `ListingEntity.id: Long?`, `.title: String?`, `.agent: AgentEntity`; `AgentEntity.party.fullName()`.
- Produces:
  - `internal object CoBrokerageVisitLabels` with `fun cancelledBy(party: VisitParty?): String`, `fun noShowReason(reason: VisitNoShowReason?): String`, `fun typeLabel(type: VisitType): String`.
  - `CoBrokerageVisitBotlet(tenantService, client)` with `sendConfirmed`, `sendReminder`, `sendCancelled`, `sendNoShow`, each `(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity, chatId: Long)`; each throws when Telegram refuses.

- [ ] **Step 1: Write the failing botlet tests**

Model the fixtures on `CoBrokerageVisitMailetTest` (`tenant`, `listingAgent` = B, `searchAgent` = A, `listing`, `visit`, its `date(...)` helper). The test is the wording spec:

```kotlin
class CoBrokerageVisitBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = CoBrokerageVisitBotlet(tenantService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
        timeZone = "Africa/Douala",
    )

    // B, the listing agent: the recipient
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
    )

    // A, the search agent: the organizer and the voice of the message
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    private val listing = ListingEntity(id = 101L, title = "Villa Bastos", agent = listingAgent)
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        code = "1234",
        note = "Client pressé",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `confirmed - B is greeted, A signs, one listing button`() {
        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour John Doe,

            J'ai organisé une visite de votre bien Villa Bastos le 02/12/2026 à 10:00 (Sur place).

            Je vous y retrouve avec mon client.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir l'annonce", url = "https://partner.ndopify.com/listings/101#visits-title")),
            buttons.firstValue,
        )
    }

    @Test
    fun `confirmed - a video visit says the customer is remote`() {
        botlet.sendConfirmed(visit.copy(type = VisitType.VIDEO), listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("(Visite vidéo)"))
        assertTrue(text.firstValue.contains("Je serai sur place pour vous ouvrir ; le client suivra en visio."))
        assertFalse(text.firstValue.contains("Je vous y retrouve avec mon client."))
    }

    @Test
    fun `reminder - the reminder wording`() {
        botlet.sendReminder(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour John Doe,

            Rappel : la visite de votre bien Villa Bastos a lieu le 02/12/2026 à 10:00 (Sur place).

            Je vous y retrouve avec mon client.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `cancelled - the actor's label, never the mandate reason`() {
        botlet.sendCancelled(
            visit.copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Mandat clôturé"),
            listing,
            searchAgent,
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour John Doe,

            La visite de votre bien Villa Bastos (Sur place) prévue le 02/12/2026 à 10:00 a été annulée.

            J'ai annulé la visite.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("Mandat clôturé"))
    }

    @Test
    fun `cancelled - the customer and the system labels`() {
        val text = argumentCaptor<String>()
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.CUSTOMER), listing, searchAgent, 1000L)
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.SYSTEM), listing, searchAgent, 1000L)

        verify(client, times(2)).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.allValues[0].contains("Le client a annulé la visite."))
        assertTrue(text.allValues[1].contains("La visite est annulée."))
    }

    @Test
    fun `no-show - both customer-caused labels`() {
        val text = argumentCaptor<String>()
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CUSTOMER_ABSENT), listing, searchAgent, 1000L)
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CODE_REFUSED), listing, searchAgent, 1000L)

        verify(client, times(2)).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.allValues[0].contains("Le client ne s'est pas présenté à la visite."))
        assertTrue(text.allValues[1].contains("Le client a refusé de donner le code de visite."))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.sendConfirmed(visit, listing.copy(title = null), searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("votre bien Bien n° 101 le"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `the text never carries the customer nor its data`() {
        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Secret Client"))
        assertFalse(text.firstValue.contains("secret@example.com"))
        assertFalse(text.firstValue.contains("1234"))
        assertFalse(text.firstValue.contains("Client pressé"))
        assertTrue(text.firstValue.contains("mon client"))
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

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest`
Expected: FAIL — `CoBrokerageVisitBotlet` is unresolved.

- [ ] **Step 3: Create the shared labels and switch the mailet to them**

`CoBrokerageVisitLabels` (`internal object`, `visit/server/mail/`, KDoc: the French wording the e-mail and the message share, so the two channels can't drift):

```kotlin
const val CANCELLED_BY_AGENT = "J'ai annulé la visite."
const val CANCELLED_BY_CUSTOMER = "Le client a annulé la visite."
const val CANCELLED_BY_SYSTEM = "La visite est annulée."
const val CUSTOMER_ABSENT = "Le client ne s'est pas présenté à la visite."
const val CODE_REFUSED = "Le client a refusé de donner le code de visite."

fun cancelledBy(party: VisitParty?): String = when (party) {
    VisitParty.AGENT -> CANCELLED_BY_AGENT
    VisitParty.CUSTOMER -> CANCELLED_BY_CUSTOMER
    else -> CANCELLED_BY_SYSTEM
}

fun noShowReason(reason: VisitNoShowReason?): String =
    if (reason == VisitNoShowReason.CODE_REFUSED) CODE_REFUSED else CUSTOMER_ABSENT

fun typeLabel(type: VisitType): String = if (type == VisitType.ON_SITE) "Sur place" else "Visite vidéo"
```

In `CoBrokerageVisitMailet`, remove the five `LABEL_*` constants from the companion and call the object instead, with the same output: `"cancelled_by_label" to CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy)` (keep `"cancellation_reason" to null`), `"reason_label" to CoBrokerageVisitLabels.noShowReason(visit.noShowReason)`, `"type_label" to CoBrokerageVisitLabels.typeLabel(visit.type)`. No other change to the mailet.

- [ ] **Step 4: Run the mailet test to verify the refactor is behaviour-neutral**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitMailetTest`
Expected: PASS — every label and the type wording are unchanged.

- [ ] **Step 5: Implement the botlet**

`CoBrokerageVisitBotlet(tenantService, client)`, `@Service`, four public methods (KDoc on the class: mirrors `CoBrokerageVisitMailet`; `listing.agent` is the recipient B, `agent` is A; the chat comes from the notifier, never the e-mail; it never reads the customer; Telegram refusing a message throws, so the notifier falls back to the e-mail). Each method delegates to one private `send(visit, listing, agent, chatId, event)` that loads the tenant from `visit.tenantId!!`, builds the text with `TenantFormatter(tenant)`, and calls:

```kotlin
client.sendMessage(
    chatId,
    text,
    tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { url ->
        listOf(TelegramButton("Voir l'annonce", url = "$url/listings/${listing.id}#visits-title"))
    } ?: emptyList(),
)
```

The text is the event's block, with `type = CoBrokerageVisitLabels.typeLabel(visit.type)`, `title = listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"`, and `slot = "${formatter.date(visit.scheduledAt!!)} à ${formatter.time(visit.scheduledAt!!)}"` (the four triggers always have a scheduled slot — their `VisitService` guards require it):

```text
CONFIRMED
Bonjour <listing.agent.party.fullName()>,

J'ai organisé une visite de votre bien <title> le <slot> (<type>).

<Je vous y retrouve avec mon client. | Je serai sur place pour vous ouvrir ; le client suivra en visio.>

Visite organisée par <agent.party.fullName()>.

REMINDER
Bonjour <B>,

Rappel : la visite de votre bien <title> a lieu le <slot> (<type>).

<on-site line, as above>

Visite organisée par <A>.

CANCELLED
Bonjour <B>,

La visite de votre bien <title> (<type>) prévue le <slot> a été annulée.

<CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy)>

Visite organisée par <A>.

NO_SHOW
Bonjour <B>,

La visite de votre bien <title> (<type>) prévue le <slot> n'a pas eu lieu.

<CoBrokerageVisitLabels.noShowReason(visit.noShowReason)>

Visite organisée par <A>.
```

One item per line, one blank line between blocks, no trailing blank line.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='CoBrokerageVisitBotletTest,CoBrokerageVisitMailetTest'`
Expected: PASS — the botlet's wording, and the mailet unchanged by the shared labels.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitLabels.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt
git commit -m "feat(visit): message the listing agent about a co-brokerage visit" -m "Refs #154"
```

---

### Task 2: Route the message to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/CoBrokerageVisitNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/CoBrokerageVisitNotifierTest.kt`

**Interfaces:**
- Consumes: Task 1's `CoBrokerageVisitBotlet.sendConfirmed / sendReminder / sendCancelled / sendNoShow(visit, listing, agent, chatId)`; the existing `CoBrokerageVisitMailet.sendConfirmed / sendReminder / sendCancelled / sendNoShow(visit, listing, agent)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `CoBrokerageVisitNotifier(botlet, mailet, telegramService)` with `sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity)`, and the same for `sendReminder`, `sendCancelled`, `sendNoShow`.

- [ ] **Step 1: Write the failing notifier tests**

Mirror `AssignmentOfferNotifierTest`; B is `listing.agent` (agent 30), A is `agent` (agent 20):

```kotlin
class CoBrokerageVisitNotifierTest {
    private val botlet = mock<CoBrokerageVisitBotlet>()
    private val mailet = mock<CoBrokerageVisitMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = CoBrokerageVisitNotifier(botlet, mailet, telegramService)

    private val listingAgent = AgentEntity(id = 30L, party = PartyEntity(firstName = "John", lastName = "Doe"))
    private val searchAgent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L, agent = listingAgent)
    private val visit = VisitEntity(id = 1L, tenantId = 1L, listingId = 101L, agentId = 20L, status = VisitStatus.CONFIRMED)

    @Test
    fun `an unlinked listing agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(mailet).sendConfirmed(visit, listing, searchAgent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked listing agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)

        notifier.sendConfirmed(visit, listing, searchAgent)

        verify(mailet).sendConfirmed(visit, listing, searchAgent)
    }

    @Test
    fun `each trigger reaches its own botlet method`() {
        doReturn(1000L).whenever(telegramService).chatId(30L)

        notifier.sendConfirmed(visit, listing, searchAgent)
        notifier.sendReminder(visit, listing, searchAgent)
        notifier.sendCancelled(visit, listing, searchAgent)
        notifier.sendNoShow(visit, listing, searchAgent)

        verify(botlet).sendConfirmed(visit, listing, searchAgent, 1000L)
        verify(botlet).sendReminder(visit, listing, searchAgent, 1000L)
        verify(botlet).sendCancelled(visit, listing, searchAgent, 1000L)
        verify(botlet).sendNoShow(visit, listing, searchAgent, 1000L)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitNotifierTest`
Expected: FAIL — `CoBrokerageVisitNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

`@Service`, KDoc: B is the listing agent; the Telegram channel is #135's link, the e-mail is the fallback (unlinked B or a failed send), and a failure is logged without the token nor the URL. Each public method delegates to one private helper:

```kotlin
fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) = send(
    visit,
    listing,
    agent,
    bot = { chatId -> botlet.sendConfirmed(visit, listing, agent, chatId) },
    mail = { mailet.sendConfirmed(visit, listing, agent) },
)

private fun send(
    visit: VisitEntity,
    listing: ListingEntity,
    agent: AgentEntity,
    bot: (Long) -> Unit,
    mail: () -> Unit,
) {
    val chatId = telegramService.chatId(listing.agent.id!!)
    if (chatId == null) {
        mail()
        return
    }
    try {
        bot(chatId)
    } catch (ex: Exception) {
        LOGGER.warn("Telegram co-brokerage visit failed for Visit#${visit.id}, falling back to e-mail", ex)
        mail()
    }
}
```

Catch `Exception`, not only `TelegramException`: any transport failure must fall back.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/CoBrokerageVisitNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/CoBrokerageVisitNotifierTest.kt
git commit -m "feat(visit): route a co-brokerage visit to Telegram or e-mail" -m "Refs #154"
```

---

### Task 3: Wire the service

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt` (constructor and the four co-brokerage calls)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Consumes: Task 2's `CoBrokerageVisitNotifier.sendConfirmed / sendReminder / sendCancelled / sendNoShow(visit, listing, agent)`.
- Produces: `VisitService`'s constructor takes `coBrokerageVisitNotifier: CoBrokerageVisitNotifier` instead of `coBrokerageVisitMailet: CoBrokerageVisitMailet`; the four public methods keep their names and guards.

- [ ] **Step 1: Update the service test**

In `VisitServiceTest`: replace `private val coBrokerageVisitMailet = mock<CoBrokerageVisitMailet>()` with `private val coBrokerageVisitNotifier = mock<CoBrokerageVisitNotifier>()`, pass it where the constructor takes `coBrokerageVisitMailet`, and replace every `coBrokerageVisitMailet` in the eight co-brokerage verifies with `coBrokerageVisitNotifier` (same method names and arguments: `sendConfirmed` / `sendReminder` / `sendCancelled` / `sendNoShow(any(), eq(coBrokerageListing), eq(agent20))`, and the `never()` cases). Keep the existing guard tests exactly as they are; they are the behaviour spec:

- confirmed, with another agent's visit → `sendConfirmed`
- confirmed, own-inventory (`agent20`'s listing) → never
- reminder, another agent's visit → `sendReminder`
- cancelled after confirmation → `sendCancelled`
- cancelled after the listing mandate ended → never
- cancelled while `REQUESTED` (`scheduledAt == null`) → never
- customer no-show → `sendNoShow`
- agent no-show / completed / expired → never

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: FAIL — `CoBrokerageVisitNotifier` is unresolved in the test.

- [ ] **Step 3: Rewire `VisitService`**

Replace the `coBrokerageVisitMailet` constructor parameter and import with `coBrokerageVisitNotifier: CoBrokerageVisitNotifier`, then change the four co-brokerage calls only:

```kotlin
if (visit.agentId != listing.agent.id) {
    coBrokerageVisitNotifier.sendConfirmed(visit, listing, agent)
}
// sendNoShowEmails: same shape, with the existing visit.noShowParty == VisitParty.CUSTOMER guard
// sendCancelledEmail: inside the existing notifyListingAgentOfCancellation(visit, listing) branch
// sendReminderEmails: same shape as confirmation
```

Every guard, KDoc sentence about the triggers, event and job stays as it is. Update the KDoc of the four methods from "E-mails..." to "Notifies the listing agent..." where it names the co-brokerage send (e.g. `sendConfirmedEmails`: "Notifies the customer and agent, and the listing agent of a co-brokerage visit: Telegram when B's chat is linked, the e-mail otherwise or as the fallback"). Do not touch the requested-visit wiring (#148).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: PASS — all four triggers and all four exclusions.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): notify the listing agent through the notifier" -m "Refs #154"
```

---

### Task 4: Full-context test of a co-brokerage confirmation

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramCoBrokerageVisitEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt`

**Interfaces:**
- Consumes: `CoBrokerageVisitEndpoint.sql`'s visit 3 (agent 30's `REQUESTED` visit on agent 20's listing 101, match 50), `VisitFixtures.sql`, `POST /v1/visits/{id}/schedule`, the beans from Tasks 1–3, `@MockitoBean TelegramClient`, `awaitAsyncTasks()`.
- Produces: no production code; proves the wiring, B's link lookup, the message and the no-e-mail rule against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramCoBrokerageVisitEndpoint.sql` only links B, agent 20, to chat 2000:

```sql
-- Agent 20 owns listing 101; visit 3 (CoBrokerageVisitEndpoint.sql) is organized by agent 30.
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 2000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CoBrokerageVisitEndpoint.sql",
        "/db/test/telegram/TelegramCoBrokerageVisitEndpoint.sql",
    ],
)
class TelegramCoBrokerageVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun `confirming a co-brokerage visit messages the listing agent instead of e-mailing it`() {
        userId = 12L // John Doe, agent 30, the visit's agent (A)

        val response = rest.postForEntity(
            "/v1/visits/3/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitStatus.CONFIRMED, dao.findById(3L).get().status)
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(2000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Bonjour Kim Possible"))
        assertTrue(text.firstValue.contains("J'ai organisé une visite de votre bien Nice apartment le "))
        assertTrue(text.firstValue.contains(" à 10:00 (Sur place)."))
        assertTrue(text.firstValue.contains("Visite organisée par John Doe."))
        assertEquals(
            listOf(TelegramButton("Voir l'annonce", url = "https://partner.ndopify.com/listings/101#visits-title")),
            buttons.firstValue,
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "the customer and the organizing agent are e-mailed")
        assertFalse(
            smtp.receivedMessages.any { it.allRecipients.single().toString() == "Kim Possible <kim.possible@gmail.com>" },
            "no co-brokerage e-mail for a linked listing agent",
        )
    }

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant())
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramCoBrokerageVisitEndpointTest`
Expected: FAIL — the botlet or the notifier isn't wired yet, or B's link isn't read.

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: the notifier isn't a bean (missing `@Service`), `VisitService` still takes the mailet, or the seed misses `chat_id` / `linked_at`. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramCoBrokerageVisitEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramCoBrokerageVisitEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt
git commit -m "test(telegram): cover the co-brokerage visit notification" -m "Refs #154"
```

---

### Task 5: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next visit notification.

- [ ] **Step 1: Document the channel**

In the **visit** bullet of `modules/ndopify-server/CLAUDE.md`, replace the `CoBrokerageVisitMailet` sentence with the channel rule:

- `CoBrokerageVisitNotifier` Telegram-messages a linked B (`CoBrokerageVisitBotlet`, via `AgentTelegramService.chatId(listing.agent.id)`) and e-mails the others (`CoBrokerageVisitMailet`), falling back to the e-mail on a failed send. It keeps the mailet's four triggers and exclusions exactly (confirmation, 24 h reminder, cancellation after confirmation while the listing's mandate is live, customer-caused no-show; never `REQUESTED` / `EXPIRED` / `COMPLETED` / agent no-show / own-inventory).
- The message mirrors the e-mail in French plain text and closes with `Visite organisée par <A>.`, because the bot is the sender (the e-mail's agent footer has no equivalent); it carries one `Voir l'annonce` URL button to `{partnerCentralUrl}/listings/{id}#visits-title`, or none without a `partnerCentralUrl`, and never A's customer nor its data.
- The cancellation / no-show / type wording is shared by the e-mail and the message through `CoBrokerageVisitLabels`.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(visit): document the Telegram co-brokerage visit notification" -m "Refs #154"
```
