# Match Answered Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the search agent when its customer accepts or rejects a proposed match — over Telegram when its chat is linked, by e-mail otherwise or as the fallback — with the customer, the listing and its price, the mandate's criteria, and a rejection's reason and comment.

**Architecture:** `MatchProposalService.accept` / `reject` publish a `MatchAnsweredEvent` in their (new) transaction. `MatchEventListener.onMatchAnswered` (async, after commit) calls `MatchProposalService.sendAnsweredNotification`, which reloads the match and delegates to a new `MatchAnsweredNotifier`: the agent's chat id (`AgentTelegramService.chatId(match.agentId)`) picks `MatchAnsweredBotlet`, otherwise `MatchAnsweredMailet`; a botlet failure logs and sends the mailet, so the agent always hears the answer.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, JUnit 5 + `kotlin.test` + mockito-kotlin, MJML templates, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/147 (the story is the spec; it was groomed on 2026-11-15 with the decisions: name the customer, name the listing, leave expiry silent).

## Global Constraints

- **Precondition: #134, #135 and #136 are merged** (they are, on `main`). This plan consumes their code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier / botlet convention.
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` / `ndopify-web-core` change, no UI page, no new config.
- **Two class-name deviations from the story, for consistency with the module:** the listener method is `MatchEventListener.onMatchAnswered` (the match domain keeps one async listener, like `VisitEventListener`, rather than a second class); the notification method is `MatchProposalService.sendAnsweredNotification` (next to `sendProposedEmail`, which already resolves the customer and the agent), not `MatchService`. The story was updated accordingly.
- The message is French and the botlet mirrors `mail/match/answered.mjml`; code, comments, logs and docs are English.
- **Recipient only:** the match's agent (`Match.agentId`, the search mandate's agent). The listing's agent is never looked up nor told. The customer is the agent's own, so its name is in the message.
- The e-mail is the fallback, not a second channel: a linked agent gets no e-mail; a failed Telegram send logs (never the token nor the URL) and sends the mailet.
- `MatchAnsweredEvent` carries ids plus `tenantId`, never entities.
- Never log, throw or report the bot token or the Telegram URL.
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- Dates and amounts in the message go through `TenantFormatter` and `MandateMailLabels`, never a hard-coded pattern or "FCFA".
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- KDoc on classes and public methods; a service method's KDoc lists its error codes in the order they're checked.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #147` in the body of each.

## Review Focus

- A rejection with no comment, or a blank one: no empty `Commentaire:` line in the botlet, no empty `« »` in the e-mail.
- Telegram blocked or down for a linked agent: the e-mail is sent and no exception escapes the listener — the agent always hears the answer.
- A `409 MATCH_NOT_PROPOSED` (a second answer, a match that was never proposed), or a transaction that rolls back: no event, no message — the listener is after commit.
- A co-brokerage match (`match.agentId` is the search agent, the listing belongs to another agent): only the search agent is resolved and notified, never the listing's agent.
- A mandate without a `partnerCentralUrl`: no button, and no empty keyboard; a listing without a title: `Bien n° <id>`.
- The after-commit timing: a rolled-back accept/reject sends nothing; the listener must run with `TenantContext` set and clean up `KVLoggerThreadLocal` (executor threads are reused).

---

### Task 1: The answer botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredLabels.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`; `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`; `TenantService.findById(id: Long): TenantEntity`; `LocationService.findByIdOrNull(id: Long): LocationEntity?`; `TenantFormatter(tenant)` (`money`); `MandateMailLabels.listingType(mandate)`, `.propertyType(mandate)`, `.neighbourhoods(mandate, locationService)`, `.budget(mandate, formatter)`, `.priceLabel(mandate)`, `.price(amount, currencyCode, periodicity, formatter)`.
- Produces: `MatchAnsweredLabels.rejectionReason(code: MatchRejectionReason?): String?`; `MatchAnsweredBotlet(tenantService, locationService, client)` with `send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing botlet test**

```kotlin
class MatchAnsweredBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = MatchAnsweredBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(1001L),
        propertyType = PropertyType.APARTMENT,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val listing = ListingEntity(
        id = 102L,
        title = "Small apartment",
        price = 140000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 2L,
        tenantId = 1L,
        mandate = mandate,
        listing = listing,
        agentId = 20L,
        status = MatchStatus.ACCEPTED,
    )
    private val customer = CustomerEntity(id = 10L, party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"))
    private val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `an acceptance names the customer, the listing and the mandate`() {
        botlet.send(match, customer, agent, 1000L)

        assertEquals(
            """
            Bonjour Kim Possible,

            Bonne nouvelle : Ray Sponsible a accepté la proposition.

            Bien: Small apartment
            Loyer: 140 000 FCFA / mois

            Recherche: Location - Appartement
            Ville: Yaounde
            Quartier(s): Bastos
            Budget: 150 000 FCFA / mois

            Cette réponse est définitive.
            """.trimIndent(),
            text(),
        )
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons(),
        )
    }

    @Test
    fun `a rejection names its reason and comment`() {
        botlet.send(
            match.copy(status = MatchStatus.REJECTED, rejectionReasonCode = MatchRejectionReason.PRICE, rejectionReason = "Trop loin du travail."),
            customer,
            agent,
            1000L,
        )

        assertTrue(
            text().contains(
                "Ray Sponsible a refusé la proposition.\nMotif: trop cher\nCommentaire: Trop loin du travail.\n"
            )
        )
    }

    @Test
    fun `a rejection without a comment leaves no line`() {
        botlet.send(
            match.copy(status = MatchStatus.REJECTED, rejectionReasonCode = MatchRejectionReason.OTHER, rejectionReason = " "),
            customer,
            agent,
            1000L,
        )

        assertTrue(text().contains("Motif: autre raison"))
        assertFalse(text().contains("Commentaire:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(match.copy(listing = listing.copy(title = null)), customer, agent, 1000L)

        assertTrue(text().contains("Bien: Bien n° 102"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(match, customer, agent, 1000L)

        assertEquals(emptyList<TelegramButton>(), buttons())
    }

    private fun text(): String {
        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        return text.firstValue
    }

    private fun buttons(): List<TelegramButton> {
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), any(), buttons.capture())
        return buttons.firstValue
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest`
Expected: FAIL — `MatchAnsweredBotlet` is unresolved.

- [ ] **Step 3: Implement the labels and the botlet**

`MatchAnsweredLabels` (`internal object`, in `match/server/mail/`): a `rejectionReason(code: MatchRejectionReason?)` returning Partner Central's neutral wording, `null` for `null` / `UNKNOWN`:

```kotlin
PRICE -> "trop cher"
LOCATION -> "le quartier ne convient pas"
SIZE -> "trop petit ou trop grand"
AMENITIES -> "il manque des équipements"
AVAILABILITY -> "disponible trop tard"
OTHER -> "autre raison"
```

`MatchAnsweredBotlet(tenantService, locationService, client)`, `@Service`, one `send(match, customer, agent, chatId)`. KDoc: mirrors `MatchAnsweredMailet`; the recipient's chat comes from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail; Telegram refusing it throws, so the notifier falls back to the e-mail. Build the text as:

```text
Bonjour <agent.party.fullName()>,

<"Bonne nouvelle : " when ACCEPTED><customer.party.fullName()> a <accepté|refusé> la proposition.
[Motif: <MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)>]   (only when non-null)
[Commentaire: <match.rejectionReason>]                                     (only when not blank)

Bien: <listing.title, or "Bien n° <listing.id>" when blank>
<MandateMailLabels.priceLabel(mandate)>: <MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter)>

Recherche: <listingType, or "<listingType> - <propertyType>" through MandateMailLabels>
Ville: <city>
[Quartier(s): <MandateMailLabels.neighbourhoods>]                          (only when non-null)
Budget: <MandateMailLabels.budget(mandate, formatter)>

Cette réponse est définitive.
```

`city` is `locationService.findByIdOrNull(mandate.cityId)?.name ?: ""`. `criteria`: a private function, as in `MandateOfferBotlet`. Then:

```kotlin
client.sendMessage(
    chatId,
    text,
    tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
        listOf(TelegramButton("Voir le mandat", url = "$url/mandates/${match.mandate.id}"))
    } ?: emptyList(),
)
```

Leave the optional lines out entirely. A `REJECTED` match with a null `rejectionReasonCode` omits the `Motif` line too (defensive: production always stores `OTHER` at least).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredLabels.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt
git commit -m "feat(match): tell the agent on Telegram that its proposal was answered" -m "Refs #147"
```

---

### Task 2: The answer e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/resources/mail/match/answered.mjml`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredTemplateTest.kt`

**Interfaces:**
- Consumes: Task 1's `MatchAnsweredLabels.rejectionReason(code)`; `MailService.send(Email)`, `MailBodyResolver.resolve(path, context)`, `Receipient(email, displayName)`, `TenantService`, `LocationService`, `TenantFormatter`, `MandateMailLabels`.
- Produces: `MatchAnsweredMailet(tenantService, locationService, mailService, mailBodyResolver)` with `send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity)`; constants `SUBJECT_ACCEPTED`, `SUBJECT_REJECTED`, `TEMPLATE = "/mail/match/answered.mjml"`.

- [ ] **Step 1: Write the failing mailet test**

```kotlin
class MatchAnsweredMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MatchAnsweredMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(1001L),
        propertyType = PropertyType.APARTMENT,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val listing = ListingEntity(
        id = 102L,
        title = "Small apartment",
        price = 140000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 2L, tenantId = 1L, mandate = mandate, listing = listing, agentId = 20L, status = MatchStatus.ACCEPTED,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
        doReturn("<html>answered</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `an acceptance`() {
        mailet.send(match, customer, agent)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "accepted" to true,
                "rejection_reason" to null,
                "comment" to null,
                "listing_title" to "Small apartment",
                "price_label" to "Loyer",
                "price" to "140 000 FCFA / mois",
                "listing_type" to "Location",
                "property_type" to "Appartement",
                "city" to "Yaounde",
                "neighbourhoods" to "Bastos",
                "budget" to "150 000 FCFA / mois",
                "mandate_url" to "https://partner.ndopify.com/mandates/1",
            ),
            context(),
        )
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Ray Sponsible a accepté la proposition", email.firstValue.subject)
        assertEquals("<html>answered</html>", email.firstValue.body)
    }

    @Test
    fun `a rejection carries its reason and comment`() {
        mailet.send(
            match.copy(status = MatchStatus.REJECTED, rejectionReasonCode = MatchRejectionReason.PRICE, rejectionReason = "Trop loin du travail."),
            customer,
            agent,
        )

        assertEquals(false, context()["accepted"])
        assertEquals("trop cher", context()["rejection_reason"])
        assertEquals("Trop loin du travail.", context()["comment"])
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Ray Sponsible a refusé la proposition", email.firstValue.subject)
    }

    @Test
    fun `a blank comment is left out`() {
        mailet.send(
            match.copy(status = MatchStatus.REJECTED, rejectionReasonCode = MatchRejectionReason.OTHER, rejectionReason = " "),
            customer,
            agent,
        )

        assertEquals("autre raison", context()["rejection_reason"])
        assertNull(context()["comment"])
    }

    @Test
    fun `a listing without a title`() {
        mailet.send(match.copy(listing = listing.copy(title = null)), customer, agent)

        assertEquals("Bien n° 102", context()["listing_title"])
    }

    @Test
    fun `no Partner Central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(match, customer, agent)

        assertNull(context()["mandate_url"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchAnsweredMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
```

- [ ] **Step 2: Write the failing template test**

Mirror `MatchProposedTemplateTest`'s resolver setup, with a hand-written context (`{{__css__}}` is injected by `MailBodyResolver`, so render through it):

```kotlin
class MatchAnsweredTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "accepted" to true,
        "listing_title" to "Small apartment",
        "price_label" to "Loyer",
        "price" to "140 000 FCFA / mois",
        "listing_type" to "Location",
        "property_type" to "Appartement",
        "city" to "Yaounde",
        "neighbourhoods" to "Bastos",
        "budget" to "150 000 FCFA / mois",
        "mandate_url" to "https://partner.ndopify.com/mandates/1",
    )

    @Test
    fun `an acceptance`() {
        val html = resolver.resolve(MatchAnsweredMailet.TEMPLATE, context)

        assertTrue(html.contains("Bonne nouvelle"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("a accepté la proposition"))
        assertTrue(html.contains("Small apartment"))
        assertTrue(html.contains("140 000 FCFA / mois"))
        assertTrue(html.contains("Location - Appartement"))
        assertTrue(html.contains("Yaounde"))
        assertTrue(html.contains("Bastos"))
        assertTrue(html.contains("150 000 FCFA / mois"))
        assertTrue(html.contains("Cette réponse est définitive"))
        assertTrue(html.contains("https://partner.ndopify.com/mandates/1"))
        assertTrue(html.contains("Voir le mandat"))
        assertFalse(html.contains("Motif"))
    }

    @Test
    fun `a rejection`() {
        val html = resolver.resolve(
            MatchAnsweredMailet.TEMPLATE,
            context - "accepted" + mapOf("rejection_reason" to "trop cher", "comment" to "Trop loin du travail."),
        )

        assertTrue(html.contains("a refusé la proposition"))
        assertTrue(html.contains("trop cher"))
        assertTrue(html.contains("Trop loin du travail."))
        assertFalse(html.contains("Bonne nouvelle"))
    }

    @Test
    fun `no mandate link - no button`() {
        val html = resolver.resolve(MatchAnsweredMailet.TEMPLATE, context - "mandate_url")

        assertFalse(html.contains("Voir le mandat"))
        assertFalse(html.contains("href=\"\""))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchAnsweredMailetTest,MatchAnsweredTemplateTest'`
Expected: FAIL — `MatchAnsweredMailet` unresolved, template missing.

- [ ] **Step 4: Implement the mailet and the template**

`MatchAnsweredMailet`, `@Service`, `send(match, customer, agent)`: resolve the tenant, `TenantFormatter(tenant)`, the city, and `Accepted = match.status == MatchStatus.ACCEPTED`. Subject: `SUBJECT_ACCEPTED` / `SUBJECT_REJECTED` with `{{tenant_name}}` and `{{customer_name}}` replaced. Recipient: `Receipient(agent.party.email, agent.party.fullName())`. Context: the map asserted in Step 1, with `rejection_reason = MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)`, `comment = match.rejectionReason?.ifBlank { null }`, `listing_title = listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"`, `mandate_url = tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/mandates/${match.mandate.id}" }`.

`answered.mjml`, in the style of `mail/mandate/approved.mjml` (the recipient is the agent, so **no** `agent-footer` fragment and no agent signature): logo, divider, `Bonjour <strong>{{recipient_name}}</strong>,`, then:

- `{{#accepted}}Bonne nouvelle&#160;: <strong>{{customer_name}}</strong> a accepté la proposition.{{/accepted}}`
- `{{^accepted}}<strong>{{customer_name}}</strong> a refusé la proposition.{{/accepted}}`
- `{{#rejection_reason}}` a line `Motif&#160;: {{rejection_reason}}` `{{/rejection_reason}}`
- `{{#comment}}` a line `«&#160;{{comment}}&#160;»` `{{/comment}}`
- an `mj-text` list: `Bien&#160;: {{listing_title}}`, `{{price_label}}&#160;: {{price}}`, `Recherche&#160;: {{listing_type}}{{#property_type}} - {{property_type}}{{/property_type}}`, `Ville&#160;: {{city}}`, `{{#neighbourhoods}}Quartier(s)&#160;: {{neighbourhoods}}{{/neighbourhoods}}`, `Budget&#160;: {{budget}}`
- a line: `Cette réponse est définitive. Retrouvez le mandat pour la suite.`
- `{{#mandate_url}}` an `mj-button` `Voir le mandat` `{{/mandate_url}}`
- `Cordialement,<br/>L'équipe {{tenant_name}}`

No `{{__css__}}` outside the standard `mj-head` block `mail/mandate/approved.mjml` uses.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchAnsweredMailetTest,MatchAnsweredTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/resources/mail/match/answered.mjml \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredMailet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredMailetTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchAnsweredTemplateTest.kt
git commit -m "feat(match): e-mail the agent when its proposal was answered" -m "Refs #147"
```

---

### Task 3: Route the answer to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAnsweredNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAnsweredNotifierTest.kt`

**Interfaces:**
- Consumes: Task 1's `MatchAnsweredBotlet.send(match, customer, agent, chatId)`; Task 2's `MatchAnsweredMailet.send(match, customer, agent)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `MatchAnsweredNotifier(botlet, mailet, telegramService)` with `send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity)`.

- [ ] **Step 1: Write the failing notifier test**

Mirror `VisitRequestedNotifierTest`:

```kotlin
class MatchAnsweredNotifierTest {
    private val botlet = mock<MatchAnsweredBotlet>()
    private val mailet = mock<MatchAnsweredMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = MatchAnsweredNotifier(botlet, mailet, telegramService)

    private val match = MatchEntity(id = 2L, agentId = 20L, status = MatchStatus.ACCEPTED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(match, customer, agent)

        verify(mailet).send(match, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(match, customer, agent)

        verify(botlet).send(match, customer, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(match, customer, agent, 1000L)

        notifier.send(match, customer, agent)

        verify(mailet).send(match, customer, agent)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredNotifierTest`
Expected: FAIL — `MatchAnsweredNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

`send(match, customer, agent)`: `val chatId = telegramService.chatId(agent.id!!)`; null → `mailet.send(match, customer, agent)`; else `try { botlet.send(match, customer, agent, chatId) } catch (ex: Exception) { LOGGER.warn("Telegram match answered notification failed for Match#${match.id}, falling back to e-mail", ex); mailet.send(match, customer, agent) }`. Catch `Exception`, not only `TelegramException`. KDoc: the channel is #135's link, the e-mail is the fallback, and failures are logged without the token nor the URL.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAnsweredNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAnsweredNotifierTest.kt
git commit -m "feat(match): route an answered proposal to Telegram or e-mail" -m "Refs #147"
```

---

### Task 4: The event, the answer and the listener

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchAnsweredEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalService.kt` (constructor, `accept`, `reject`, new `sendAnsweredNotification`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListener.kt` (`onMatchAnswered`, KDoc)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListenerTest.kt`

**Interfaces:**
- Consumes: Task 3's `MatchAnsweredNotifier.send(match, customer, agent)`; `ApplicationEventPublisher.publishEvent`.
- Produces: `MatchAnsweredEvent(matchId: Long, mandateId: Long, tenantId: Long, status: MatchStatus)`; `MatchProposalService(..., mailet, answeredNotifier, eventPublisher, ...)` with `sendAnsweredNotification(matchId: Long): Unit`; `MatchEventListener.onMatchAnswered(event: MatchAnsweredEvent)`.

- [ ] **Step 1: Write the failing service test**

```kotlin
class MatchProposalServiceTest {
    private val dao = mock<MatchRepository>()
    private val matchService = mock<MatchService>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val mailet = mock<MatchProposedMailet>()
    private val notifier = mock<MatchAnsweredNotifier>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val service = MatchProposalService(
        dao, matchService, customerService, agentService, mailet, notifier, eventPublisher, clock, mock(), 7L,
    )

    private val mandate = MandateEntity(id = 1L, tenantId = 1L, customerId = 10L, agentId = 20L, status = MandateStatus.APPROVED)
    private val listing = ListingEntity(id = 102L, title = "Small apartment")
    private val match = MatchEntity(
        id = 2L, tenantId = 1L, mandate = mandate, listing = listing, agentId = 20L, status = MatchStatus.PROPOSED,
    )
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L)

    @BeforeTest
    fun setUp() {
        doReturn(match).whenever(matchService).findById(2L)
        doAnswer { it.arguments[0] }.whenever(dao).save(any())
    }

    @Test
    fun `accept publishes the answer`() {
        service.accept(2L)

        assertEquals(MatchStatus.ACCEPTED, saved().status)
        val event = argumentCaptor<MatchAnsweredEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 1L, status = MatchStatus.ACCEPTED),
            event.firstValue,
        )
    }

    @Test
    fun `reject publishes the answer with its reason`() {
        service.reject(2L, RejectMatchRequest(reasonCode = MatchRejectionReason.PRICE, reason = " Trop loin. "))

        val saved = saved()
        assertEquals(MatchStatus.REJECTED, saved.status)
        assertEquals(MatchRejectionReason.PRICE, saved.rejectionReasonCode)
        assertEquals("Trop loin.", saved.rejectionReason)
        val event = argumentCaptor<MatchAnsweredEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 1L, status = MatchStatus.REJECTED),
            event.firstValue,
        )
    }

    @Test
    fun `an answer to a match not proposed publishes nothing`() {
        doReturn(match.copy(status = MatchStatus.ACTIVE)).whenever(matchService).findById(2L)

        assertFailsWith<ConflictException> { service.accept(2L) }

        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `answered - the search agent is notified`() {
        doReturn(Optional.of(match.copy(status = MatchStatus.ACCEPTED))).whenever(dao).findById(2L)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        service.sendAnsweredNotification(2L)

        verify(notifier).send(match.copy(status = MatchStatus.ACCEPTED), customer, agent)
    }

    @Test
    fun `answered - a co-brokerage match notifies the search agent only`() {
        val listingMandate = MandateEntity(id = 11L, tenantId = 1L, agentId = 30L)
        val coBrokerage = match.copy(
            status = MatchStatus.ACCEPTED,
            agentId = 20L,
            listing = listing.copy(mandate = listingMandate),
        )
        doReturn(Optional.of(coBrokerage)).whenever(dao).findById(2L)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        service.sendAnsweredNotification(2L)

        verify(notifier).send(coBrokerage, customer, agent)
        verify(agentService, never()).findById(30L)
    }

    @Test
    fun `answered - nothing for a proposal not answered`() {
        doReturn(Optional.of(match)).whenever(dao).findById(2L)

        service.sendAnsweredNotification(2L)

        verifyNoInteractions(notifier)
    }

    @Test
    fun `answered - nothing for an unknown match`() {
        doReturn(Optional.empty<MatchEntity>()).whenever(dao).findById(999L)

        service.sendAnsweredNotification(999L)

        verifyNoInteractions(notifier)
    }

    private fun saved(): MatchEntity {
        val saved = argumentCaptor<MatchEntity>()
        verify(dao).save(saved.capture())
        return saved.firstValue
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchProposalServiceTest`
Expected: FAIL — `MatchAnsweredEvent` and `sendAnsweredNotification` are unresolved, the constructor arity is wrong.

- [ ] **Step 3: Implement the event, the transitions and the notification**

`MatchAnsweredEvent.kt`: a `data class` with the four fields, KDoc saying `MatchProposalService.accept` / `reject` publish it inside their transaction, `status` is `ACCEPTED` / `REJECTED`, and `tenantId` is there because the listener runs on a thread that has none.

`MatchProposalService`:
- constructor: `private val answeredNotifier: MatchAnsweredNotifier` right after `mailet`.
- `accept(id)`: add `@Transactional` (import `org.springframework.transaction.annotation.Transactional` if absent) and, after `dao.save`, publish `MatchAnsweredEvent(matchId = saved.id!!, mandateId = saved.mandate.id!!, tenantId = saved.tenantId!!, status = saved.status)`; return `saved`. KDoc: the event reaches the agent after commit.
- `reject(id, request)`: same, with the saved entity.
- new `@Transactional fun sendAnsweredNotification(matchId: Long)`: `val match = dao.findById(matchId).orElse(null) ?: return`; `if (match.status != MatchStatus.ACCEPTED && match.status != MatchStatus.REJECTED) return`; `answeredNotifier.send(match, customerService.findById(match.mandate.customerId), agentService.findById(match.agentId))`. KDoc: called by `MatchEventListener` after commit; the agent is the search agent (`match.agentId`), never the listing's; nothing once the match isn't answered.

`MatchEventListener`: add `onMatchAnswered`, same body as `onMatchProposed` (logger keys `event`, `tenant_id`, `match_id`), calling `proposalService.sendAnsweredNotification(event.matchId)`. Extend the class KDoc one sentence: the agent is told when its proposal is answered.

- [ ] **Step 4: Update the listener test**

Add to `MatchEventListenerTest`:

```kotlin
@Test
fun `answered - the proposal service is told, in the tenant`() {
    var tenantId: Long? = null
    doAnswer { tenantId = TenantContext.get() }.whenever(proposalService).sendAnsweredNotification(2L)

    listener.onMatchAnswered(
        MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 7L, status = MatchStatus.ACCEPTED),
    )

    verify(proposalService).sendAnsweredNotification(2L)
    assertEquals(7L, tenantId)
    assertNull(TenantContext.get())
    assertNull(KVLoggerThreadLocal.get())
}

@Test
fun `answered - a failure is only logged`() {
    doThrow(IllegalStateException("boom")).whenever(proposalService).sendAnsweredNotification(2L)

    listener.onMatchAnswered(
        MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 7L, status = MatchStatus.REJECTED),
    )

    assertNull(TenantContext.get())
    assertNull(KVLoggerThreadLocal.get())
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposalServiceTest,MatchEventListenerTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchAnsweredEvent.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListenerTest.kt
git commit -m "feat(match): notify the agent when its proposal is answered" -m "Refs #147"
```

---

### Task 5: Full-context coverage of the channel rule

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramMatchAnsweredEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramMatchAnsweredEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/MatchAnsweredEmailEndpointTest.kt`

**Interfaces:**
- Consumes: `POST /v1/matches/{id}/accept`, `POST /v1/matches/{id}/reject`; `/db/test/match/ProposeMatchEndpoint.sql` (match 2: mandate 1, customer 10 / user 13, agent 20, listing 102 "Small apartment" at 140 000 XAF / mois; city Yaounde, neighbourhood Bastos); the beans from Tasks 1–4; `@MockitoBean TelegramClient`; `awaitAsyncTasks()`; `smtp`.
- Produces: no production code; proves the wiring, the link lookup, the message and the e-mail rule against MySQL.

- [ ] **Step 1: Write the seed**

```sql
-- ProposeMatchEndpoint.sql's match 2: customer 10 answers, agent 20 is told
UPDATE T_TENANT SET partner_central_url = 'https://partner.ndopify.com' WHERE id = 1;
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing linked-agent test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/match/ProposeMatchEndpoint.sql",
        "/db/test/telegram/TelegramMatchAnsweredEndpoint.sql",
    ],
)
class TelegramMatchAnsweredEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    @BeforeTest
    fun asCustomer() {
        userId = 13L // Ray Sponsible, the customer of search 1
    }

    @Test
    fun `an acceptance messages the linked agent instead of e-mailing it`() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Bonne nouvelle : Ray Sponsible a accepté la proposition."))
        assertTrue(text.firstValue.contains("Bien: Small apartment"))
        assertTrue(text.firstValue.contains("Loyer: 140 000 FCFA / mois"))
        assertTrue(text.firstValue.contains("Recherche: Location - Appartement"))
        assertTrue(text.firstValue.contains("Ville: Yaounde"))
        assertTrue(text.firstValue.contains("Quartier(s): Bastos"))
        assertTrue(text.firstValue.contains("Budget: 150 000 FCFA / mois"))
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no e-mail for a linked agent")
    }

    @Test
    fun `a rejection messages the reason and the comment`() {
        val response = rest.postForEntity(
            "/v1/matches/2/reject",
            RejectMatchRequest(reasonCode = MatchRejectionReason.PRICE, reason = "Trop loin du travail."),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Ray Sponsible a refusé la proposition."))
        assertTrue(text.firstValue.contains("Motif: trop cher"))
        assertTrue(text.firstValue.contains("Commentaire: Trop loin du travail."))
    }
}
```

- [ ] **Step 3: Write the failing unlinked-agent test**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class MatchAnsweredEmailEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    @BeforeTest
    fun asCustomer() {
        userId = 13L
    }

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail expected")
        verifyNoInteractions(client)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramMatchAnsweredEndpointTest,MatchAnsweredEmailEndpointTest'`
Expected: PASS (MySQL running). If they fail, fix the wiring — likely a missing `@Service` on the notifier or the mailet, or the listener not registered.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramMatchAnsweredEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramMatchAnsweredEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/MatchAnsweredEmailEndpointTest.kt
git commit -m "test(match): cover the answered proposal notification end to end" -m "Refs #147"
```

---

### Task 6: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next match notification.

- [ ] **Step 1: Document the notification**

In `modules/ndopify-server/CLAUDE.md`:
- In the **match** bullet's *Proposal* paragraph, replace `The agent isn't told of the answer yet.` with: the answer publishes `MatchAnsweredEvent` in the accept / reject transaction, `MatchEventListener.onMatchAnswered` (async, after commit) calls `MatchProposalService.sendAnsweredNotification`, `MatchAnsweredNotifier` messages a linked agent (`AgentTelegramService.chatId(match.agentId)`) through `MatchAnsweredBotlet`, e-mails the others (`MatchAnsweredMailet`, `mail/match/answered.mjml`), and falls back to the e-mail when Telegram fails; the message names the customer, the listing and its price, the mandate's criteria via `MandateMailLabels`, a rejection's reason and comment, and a `Voir le mandat` button to `{partnerCentralUrl}/mandates/{mandateId}` (none without one); final, and the listing's agent is never told. An `EXPIRED` proposal notifies nobody.
- In the **Asynchronous e-mails** paragraph, add `MatchAnsweredEvent` to the events and `MatchProposalService.sendAnsweredNotification` to the methods, and add the answered answer to the leading list of e-mails.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(match): document the answered proposal notification" -m "Refs #147"
```
