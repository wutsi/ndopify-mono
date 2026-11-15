# Telegram Mandate Approval Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the agent over Telegram that its mandate is approved, with the e-mail as the fallback for an unlinked agent or a failed send.

**Architecture:** `MandateApprovedEvent` still reaches `MandateEventListener` (async, after commit), which calls `MandateService.sendApprovedNotification`. That method delegates to a new `MandateApprovedNotifier`: the agent's chat id (`AgentTelegramService.chatId`) picks the new `MandateApprovedBotlet`, otherwise the existing `MandateApprovedMailet`; any botlet failure logs and sends the mailet, so an approval is never lost.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/145 (the story is the spec; it was groomed with a "summary only" message decision).

## Global Constraints

- **Precondition: #136 is merged.** This plan consumes its code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier/botlet convention (`docs/superpowers/plans/2026-11-15-telegram-assignment-notification.md`). #136 is on `feat/telegram-assignment-notification`; do not start before it lands on `main`. If it named anything differently, adapt the calls, not the design.
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` nor `ndopify-web-core` change, no UI page.
- The bot's text is French; code, comments, logs and docs are English.
- **Summary only:** the message carries the city, the mandate type, its neighbourhoods and its budget or price. It never carries the customer's name, the contract number nor the validity dates (grooming decision); the mandate page has those.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- Never log, throw or report the bot token or the Telegram URL.
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- KDoc on classes and public methods; a service method's KDoc lists its error codes in the order they're checked.
- One module per commit (only `ndopify-server` here); conventional commit messages.

## Review Focus

- A linked agent whose bot is blocked or Telegram is down: the e-mail is sent, and no exception escapes the listener (an approval is never lost).
- A mandate that isn't APPROVED, has no agent, or no contract: neither channel is called.
- A tenant without `partnerCentralUrl`: the message carries no button, and no empty keyboard is sent.
- A LISTING mandate: the listing wording (`Bien` / `Vente` / `Prix` or `Loyer`), not the search one (`Recherche` / `Achat` / `Budget`).
- The summary-only rule: the text never contains the customer's name, the contract number nor a validity date.
- A mandate without a resolvable city: the sentence has no dangling `à`, and no `Ville:` line.

---

### Task 1: The approval botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt`

**Interfaces:**
- Consumes: #136's `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())` and `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`; `TenantService.findById(id: Long): TenantEntity`; `LocationService.findByIdOrNull(id: Long): LocationEntity?`; `TenantFormatter(tenant)` (`money`, `date`); `MandateMailLabels.listingType` / `.propertyType` / `.neighbourhoods` / `.priceLabel` / `.budget`.
- Produces: `MandateApprovedBotlet(tenantService, locationService, client)` with `send(mandate: MandateEntity, agent: AgentEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing botlet tests**

Model the fixtures on `MandateApprovedMailetTest` (`tenant`, `agent`, `mandate`), and assert the whole text, not fragments:

```kotlin
class MandateApprovedBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = MandateApprovedBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com",
        timeZone = "Africa/Douala",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
    }

    @Test
    fun `a search approval carries its criteria, the city and the mandate link`() {
        val mandate = MandateEntity(
            id = 1L, tenantId = 1L, customerId = 10L, agentId = 20L, contractId = "c-1",
            status = MandateStatus.APPROVED, mandateType = MandateType.SEARCH,
            cityId = 2370201L, neighborhoodIds = listOf(111L, 222L),
            listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT,
            price = 1500000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, agent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Votre client a signé le mandat à Yaounde. Le mandat est approuvé : vous pouvez commencer.

            Recherche: Location - Appartement
            Ville: Yaounde
            Quartier(s): Bastos, Mvan
            Budget: 1 500 000 XAF / mois
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons.firstValue,
        )

        // Summary only: the page has the customer, the contract and its dates
        assertFalse(text.firstValue.contains("Sponsible"))
        assertFalse(text.firstValue.contains("MNDT-"))
        assertFalse(text.firstValue.contains("15/10/2026"))
    }

    @Test
    fun `a listing approval carries its price and no search wording`() {
        val mandate = MandateEntity(
            id = 2L, tenantId = 1L, agentId = 20L, status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING, cityId = 2370201L,
            listingType = ListingType.SALE, propertyType = PropertyType.VILLA,
            price = 25_000_000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.UNKNOWN,
        )

        botlet.send(mandate, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour Kim Possible,

            Votre client a signé le mandat à Yaounde. Le mandat est approuvé : vous pouvez commencer.

            Bien: Vente - Villa
            Ville: Yaounde
            Prix: 25 000 000 XAF
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a tenant without a Partner Central URL gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)
        val mandate = MandateEntity(
            id = 1L, tenantId = 1L, agentId = 20L, status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH, cityId = 2370201L,
            listingType = ListingType.RENTAL, price = 1500000, currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, agent, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `a mandate without a resolvable city has no dangling place`() {
        val mandate = MandateEntity(
            id = 1L, tenantId = 1L, agentId = 20L, status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH, cityId = -1L,
            listingType = ListingType.RENTAL, price = 1500000, currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour Kim Possible,

            Votre client a signé le mandat. Le mandat est approuvé : vous pouvez commencer.

            Recherche: Location
            Budget: 1 500 000 XAF / mois
            """.trimIndent(),
            text.firstValue,
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest`
Expected: FAIL — `MandateApprovedBotlet` is unresolved.

- [ ] **Step 3: Implement the botlet**

`MandateApprovedBotlet(tenantService, locationService, client)`, `@Service`, one `send(mandate, agent, chatId)`. KDoc: mirrors `MandateApprovedMailet`, and the recipient's chat comes from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail; Telegram refusing it throws, so the notifier falls back to the e-mail. Build the text as:

```text
Bonjour <agent.party.fullName()>,

Votre client a signé le mandat[ à <city>]. Le mandat est approuvé : vous pouvez commencer.

<Recherche|Bien>: <listingType>[- <propertyType>]
[Ville: <city>]                                       (only when the city resolves)
[Quartier(s): <neighbourhoods>]                       (only when any)
[<price_label>: <price>]                              (listing: "Prix" / "Loyer", no period for a sale)
[Budget: <budget>]                                    (search only)
```

Reuse `MandateMailLabels` exactly as `MandateOfferBotlet` does (including its private `criteria(mandate)` helper: `"<listingType> - <propertyType>"`, or `<listingType>` without a property type). Then:

```kotlin
client.sendMessage(
    chatId,
    text,
    tenant.partnerCentralUrl?.let { url ->
        listOf(TelegramButton("Voir le mandat", url = "${url.removeSuffix("/")}/mandates/${mandate.id}"))
    } ?: emptyList(),
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt
git commit -m "feat(mandate): notify an approval over Telegram"
```

---

### Task 2: Route the approval to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateApprovedNotifier.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt` (constructor and `sendApprovedEmail`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/event/MandateEventListener.kt` (`onMandateApproved`)
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateApprovedNotifierTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceCloseDealTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/event/MandateEventListenerTest.kt`

**Interfaces:**
- Consumes: Task 1's `MandateApprovedBotlet.send(mandate, agent, chatId)`; `MandateApprovedMailet.send(mandate, contract, customer, agent)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces:
  - `MandateApprovedNotifier(botlet, mailet, telegramService)` with `send(mandate: MandateEntity, contract: ContractEntity, customer: CustomerEntity, agent: AgentEntity)`.
  - `MandateService.sendApprovedNotification(id: Long): Boolean`, replacing `sendApprovedEmail`.
  - `MandateService`'s constructor takes `approvedNotifier: MandateApprovedNotifier` instead of `approvedMailet: MandateApprovedMailet`.

- [ ] **Step 1: Write the failing notifier test**

Mirror `AssignmentOfferNotifierTest`:

```kotlin
class MandateApprovedNotifierTest {
    private val botlet = mock<MandateApprovedBotlet>()
    private val mailet = mock<MandateApprovedMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MandateApprovedNotifier(botlet, mailet, telegramService)

    private val mandate = MandateEntity(id = 1L, agentId = 20L)
    private val contract = ContractEntity(id = "c-1")
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(mandate, contract, customer, agent)

        verify(mailet).send(mandate, contract, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(mandate, contract, customer, agent)

        verify(botlet).send(mandate, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(mandate, agent, 1000L)

        notifier.send(mandate, contract, customer, agent)

        verify(mailet).send(mandate, contract, customer, agent)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedNotifierTest`
Expected: FAIL — `MandateApprovedNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

`send(mandate, contract, customer, agent)`: `val chatId = telegramService.chatId(agent.id!!)`; null → `mailet.send(mandate, contract, customer, agent)`; else `try { botlet.send(mandate, agent, chatId) } catch (ex: Exception) { LOGGER.warn("Telegram approval failed for Mandate#${mandate.id}, falling back to e-mail", ex); mailet.send(mandate, contract, customer, agent) }`. Catch `Exception`, not only `TelegramException`. KDoc: the channel is #135's link; the e-mail is the fallback; failures are logged without the token nor the URL.

- [ ] **Step 4: Update the failing service and listener tests**

In `MandateServiceTest`: replace the `approvedMailet` field with `private val approvedNotifier = mock<MandateApprovedNotifier>()`, pass `approvedNotifier = approvedNotifier` to the constructor, and rename `sendApprovedEmail` → `sendApprovedNotification` in the three tests. Replace the mailet verifications with:

```kotlin
verify(approvedNotifier).send(mandate, contract, customer, agent)
```

and, in the two guard tests, `verifyNoInteractions(approvedNotifier)`. Rename the test names to `approved notification - ...`.

In `MandateServiceCloseDealTest`: `approvedMailet = mock()` → `approvedNotifier = mock()`.

In `MandateEventListenerTest`: rename the two stubs / verifies of `sendApprovedEmail(10L)` to `sendApprovedNotification(10L)` and the test name to `approved - the agent is notified, in its tenant`.

- [ ] **Step 5: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateApprovedNotifierTest,MandateServiceTest,MandateServiceCloseDealTest,MandateEventListenerTest'`
Expected: FAIL — `sendApprovedNotification` and `MandateApprovedNotifier` are unresolved in the service.

- [ ] **Step 6: Rewire the service and the listener**

`MandateService`: constructor `private val approvedNotifier: MandateApprovedNotifier`; rename `sendApprovedEmail(id: Long): Boolean` to `sendApprovedNotification`; keep its `@Transactional`, its `Boolean` result and its guard (`status != APPROVED || agentId == null || contractId == null` → `false`); replace `approvedMailet.send(...)` with `approvedNotifier.send(mandate, contract, contractService.findById(contractId), customerService.findById(customerId), agentService.findById(agentId))` (same values as today). KDoc: the channel is Telegram for a linked agent, the e-mail otherwise or as the fallback; the boolean says a channel delivered.

`MandateEventListener.onMandateApproved`: call `service.sendApprovedNotification(event.mandateId)`; rename the log key `email_sent` to `notification_sent`. Its KDoc becomes "Notifies the agent that its mandate is approved...".

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateApprovedNotifierTest,MandateServiceTest,MandateServiceCloseDealTest,MandateEventListenerTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateApprovedNotifier.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/event/MandateEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateApprovedNotifierTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceCloseDealTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/event/MandateEventListenerTest.kt
git commit -m "feat(mandate): route an approval to Telegram or e-mail"
```

---

### Task 3: Full-context test of an approval

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramApprovedMandateEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramApprovedMandateEndpointTest.kt`

**Interfaces:**
- Consumes: the sign endpoint and `CleanContractEvent`'s approval path (the last signature), the beans from Tasks 1 and 2, and `SignContractEndpoint.sql`'s fixtures (agent 20, mandate 2, contract c-2, party cp-22).
- Produces: no production code; proves the wiring, the link lookup and the fallback against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramApprovedMandateEndpoint.sql` only links agent 20 to chat 1000: `T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at)` = `(1, 20, 1000, '2026-11-15 00:00:00')`. The rest of the fixture comes from `SignContractEndpoint.sql`.

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/contract/SignContractEndpoint.sql",
        "/db/test/telegram/TelegramApprovedMandateEndpoint.sql",
    ],
)
class TelegramApprovedMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    companion object {
        private const val CONTENT_HASH =
            "1111111111111111111111111111111111111111111111111111111111111111" // every contract but c-10
    }

    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    // The signed PDF isn't this test's subject
    @MockitoBean
    private lateinit var signedContractService: SignedContractService

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Test
    fun `the last signature messages the linked agent instead of e-mailing it`() {
        userId = 12L // party 101 signs cp-22, the last required party of c-2

        val body = SignContractRequest(
            partyId = "cp-22",
            ipAddress = "192.168.1.10",
            userAgent = "Mozilla/5.0 (iPhone)",
            contentHash = CONTENT_HASH,
        )
        assertEquals(
            HttpStatus.OK,
            rest.postForEntity("/v1/contracts/c-2/sign", body, Any::class.java).statusCode,
        )
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(2L).get().status)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Votre client a signé le mandat à Yaounde"))
        assertTrue(text.firstValue.contains("Recherche: Location"))
        assertTrue(text.firstValue.contains("Budget: 150 000 XAF / mois"))
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/2")),
            buttons.firstValue,
        )

        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no approval e-mail for a linked agent")
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramApprovedMandateEndpointTest`
Expected: FAIL — the botlet or the notifier isn't wired yet (or the seed table is missing a column).

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: the notifier isn't a bean (missing `@Service`), or `MandateService` still takes the mailet. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramApprovedMandateEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramApprovedMandateEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramApprovedMandateEndpointTest.kt
git commit -m "test(telegram): cover the mandate approval notification"
```

---

### Task 4: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next notification.

- [ ] **Step 1: Document the channel and the rename**

In `modules/ndopify-server/CLAUDE.md`:
- In the **mandate** bullet, replace the `MandateApprovedMailet` sentence with the channel rule: `MandateApprovedNotifier` Telegram-messages a linked agent (`MandateApprovedBotlet`, via `AgentTelegramService.chatId`) and e-mails the others, falling back to the e-mail on a failed send; the message carries the city, type, neighbourhoods and budget/price and a `Voir le mandat` button, never the customer's name, the contract number nor the validity dates (summary only).
- In the **Asynchronous e-mails** list, rename `AssignmentService.sendOfferEmail`'s sibling `MandateService.sendApprovedEmail` to `sendApprovedNotification` wherever it appears.
- If #136 didn't already document the `bot/` layering, add it next to `mail/`: a `*Botlet` is the Telegram counterpart of a `*Mailet`; a `*Notifier` picks the channel and owns the e-mail fallback.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(mandate): document the Telegram approval notification"
```
