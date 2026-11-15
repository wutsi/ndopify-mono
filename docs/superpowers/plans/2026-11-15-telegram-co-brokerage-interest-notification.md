# Telegram Co-Brokerage Interest Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell a listing agent B over Telegram when other agents' searches showed interest in its listings, grouped by listing, with one `Voir le mandat — <title>` button per listing; the e-mail stays the fallback.

**Architecture:** `CoBrokerageNotificationService` builds one `CoBrokerageInterestDigest` per listing agent (the listing grouping extracted from `CoBrokerageInterestMailet` into a shared `CoBrokerageInterestDigestBuilder`) and hands it to a new `CoBrokerageInterestNotifier`: a linked agent (`AgentTelegramService.chatId`) gets `CoBrokerageInterestBotlet` in `match/server/bot/`, an unlinked one gets the mailet, and any Telegram failure falls back to the mailet. The botlet splits a digest over 4096 characters at listing boundaries (an oversized listing splits at agent-row boundaries) and sends one message per part, with the buttons of the listings it holds, each on its own row.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/157 (the story is the spec; groomed 2026-11-15 with the button-layout decision).

## Global Constraints

- **Precondition: #136 is merged.** This plan consumes its code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier/botlet convention (`docs/superpowers/plans/2026-11-15-telegram-assignment-notification.md`). #136 is on `feat/telegram-assignment-notification`; do not start before it lands on `main`. If it named anything differently, adapt the calls, not the design.
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` / `ndopify-web-core` change, no UI page, no new config.
- The bot's text is French and mirrors `mail/match/co_brokerage_interest.mjml`; code, comments, logs and docs are English.
- **Anti-sautage:** the digest never reads the search customer. No `CustomerService` import in `match/server/bot/`, `match/server/service/CoBrokerageInterestDigest*` or `match/server/mail/`; the bot text never names nor describes the customer.
- **The e-mail is unchanged:** for the same matches, `CoBrokerageInterestMailet`'s MJML context keeps its current keys and values, its subject and its template.
- One shared digest: the mailet and the botlet render the same `CoBrokerageInterestDigest`; never re-group or re-order in a channel.
- Amounts and phone numbers go through `TenantFormatter` (inside the digest builder), never a hard-coded pattern.
- Telegram limits: a message is at most **4096** characters, a button label at most **64**. A row is never split nor truncated; a message under the limit is one message.
- Never log, throw or report the bot token or the Telegram URL (see #134).
- The `qa` profile must make no Telegram call: every test reaches only a mocked `TelegramClient` or a mocked notifier.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- KDoc on classes and public methods.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #157` in the body of each.

## Review Focus

- A single listing group over 4096 characters: its agent rows span messages, the listing header is repeated, every row appears in exactly one message, and no row is cut (Task 4).
- Buttons: each listing's is on its own row, carries the title, and is absent without `partnerCentralUrl`; a split message carries only the buttons of the groups whose header it holds (Task 4).
- A linked B when Telegram refuses or is down: the e-mail is sent instead, nothing escapes, and the failure is logged without the token nor the URL (Task 5).
- Both channels failing: the exception reaches `CoBrokerageNotificationService`, which leaves `listingAgentNotifiedAt` null for the next run (Task 6).
- More than 5 interested agents on one listing: exactly 5 rows and the others counted, like the e-mail (Tasks 2, 4).
- The search customer never leaks into the bot text, and an unknown interested agent is dropped (Task 2).
- An unlinked B: no Telegram call at all, the e-mail goes out (Task 5).

---

### Task 1: `TelegramClient.sendMessageRows`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`

**Interfaces:**
- Consumes: #136's `TelegramButton`, `TelegramMessageResponse`, the private `check()` / `url(path)` / `List<TelegramButton>.toPayload()`, `Http.post(uri, payload, responseType, headers)`.
- Produces: `open fun sendMessageRows(chatId: Long, text: String, rows: List<List<TelegramButton>>)` — the same `sendMessage`, with the keyboard's rows given explicitly; no `reply_markup` when `rows` is empty.

- [ ] **Step 1: Write the failing client test**

Add to `TelegramClientTest` (`base` is `https://api.telegram.org/bot123:token`):

```kotlin
@Test
fun `sendMessageRows posts one keyboard row per button`() {
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

    client.sendMessageRows(
        123L,
        "Digest",
        listOf(
            listOf(TelegramButton("Voir le mandat — Villa Bastos", url = "https://pc.example/mandates/11")),
            listOf(TelegramButton("Voir le mandat — Studio Mvan", url = "https://pc.example/mandates/12")),
        ),
    )

    verify(http).post(
        eq("$base/sendMessage"),
        eq(
            mapOf(
                "chat_id" to 123L,
                "text" to "Digest",
                "reply_markup" to mapOf(
                    "inline_keyboard" to listOf(
                        listOf(mapOf("text" to "Voir le mandat — Villa Bastos", "url" to "https://pc.example/mandates/11")),
                        listOf(mapOf("text" to "Voir le mandat — Studio Mvan", "url" to "https://pc.example/mandates/12")),
                    ),
                ),
            ),
        ),
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}

@Test
fun `sendMessageRows without rows posts no keyboard`() {
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

    client.sendMessageRows(123L, "Digest", emptyList())

    verify(http).post(
        eq("$base/sendMessage"),
        eq(mapOf("chat_id" to 123L, "text" to "Digest")),
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: FAIL — `sendMessageRows` is unresolved.

- [ ] **Step 3: Implement `TelegramClient.sendMessageRows`**

Same body as `sendMessage`, with `"inline_keyboard" to rows.filter { row -> row.isNotEmpty() }.map { row -> row.toPayload() }` and the `reply_markup` only when that list isn't empty. Do not change `sendMessage` nor its one-row payload. KDoc: "The same `sendMessage`, with the keyboard's rows given explicitly: one row per button list. A digest labels its buttons per listing, so they can't share a row."

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: PASS (the existing `sendMessage` tests stay green).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt
git commit -m "feat(telegram): send a message with explicit keyboard rows"
```

---

### Task 2: The digest builder

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilder.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilderTest.kt`

**Interfaces:**
- Consumes: `TenantService.findById(id)`, `AgentService.findByIdOrNull(id)`, `LocationService.findByIdOrNull(id)`, `ListingImageService.cover(listing)`, `TenantFormatter(tenant)`, `AgentMailContext.of(agent, formatter)`, `MandateMailLabels.listingType/propertyType/place/budget`, and `MatchEntity.mandate` / `.listing` / `.score`.
- Produces:
  - `data class CoBrokerageInterestDigest(val tenant: TenantEntity, val agentCount: Int, val listings: List<ListingInterest>)`
  - `data class ListingInterest(val listingId: Long, val title: String?, val price: String, val place: String, val imageUrl: String?, val mandateUrl: String?, val agents: List<InterestedAgent>, val more: Int?)` — `title` is the listing's own, null when it has none; `more` is the agents beyond `MAX_AGENTS` (null when none).
  - `data class InterestedAgent(val fullName: String, val whatsapp: String?, val whatsappUrl: String?, val email: String?, val emailUrl: String?, val photoUrl: String?, val listingType: String, val propertyType: String?, val place: String, val roomsLabel: String?, val budget: String, val searchCount: Int?)`
  - `@Service class CoBrokerageInterestDigestBuilder(tenantService, agentService, locationService, imageService)` with `fun build(matches: List<MatchEntity>): CoBrokerageInterestDigest` and `companion object { const val MAX_AGENTS = 5 }`. Requires a non-empty list; the caller never passes an empty one.

Behaviour, exactly as `CoBrokerageInterestMailet` groups today:

- listings by their best score, descending; within a listing, the interested agents by their best score, descending, at most `MAX_AGENTS`, the rest in `more`;
- one row per search agent (its several searches of the same listing collapse into one row, `searchCount` > 1 only);
- an interested agent no longer found is dropped; a listing left without agents is dropped;
- `agentCount` is the number of distinct known search agents across all listings (what the e-mail subject counts);
- `title` is the listing's `title` (blank → null); `price` = `MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter)`; `place` from the listing's `neighborhoodId` / `cityId` ("Bastos (Yaounde)", "Yaounde", or ""); `imageUrl` = `imageService.cover(listing)?.url?.ifBlank { null }`; `mandateUrl` = `tenant.partnerCentralUrl?.let { "$it/mandates/${listing.mandate.id}" }`;
- the contact: `whatsapp` / `whatsappUrl` from `AgentMailContext.of`; `email` / `emailUrl` (`mailto:`) only when WhatsApp is blank and the e-mail isn't; `roomsLabel` as today ("2 pièces", "1 pièce", null when not numeric); `budget` = `MandateMailLabels.budget(mandate, formatter)`.

- [ ] **Step 1: Write the failing builder test**

Mirror `CoBrokerageInterestMailetTest`'s fixtures (tenant with `partnerCentralUrl`, `currencyCode = "XAF"`, `monetaryFormat = "#,###,##0 FCFA"`; John Doe / agent 30; Kim Possible / agent 20 with `whatsappNumber = "+237699000001"`; a SEARCH mandate at `cityId 2370201` / neighbourhood 111, `rooms = "2"`, 100 000 XAF monthly; a LISTING mandate 11 / listing 101 "Villa Bastos", 90 000 XAF monthly, city 2370201 / neighbourhood 111):

```kotlin
class CoBrokerageInterestDigestBuilderTest {
    // … mocks for tenantService, agentService, locationService, imageService, as in CoBrokerageInterestMailetTest
    private val builder = CoBrokerageInterestDigestBuilder(tenantService, agentService, locationService, imageService)

    @Test
    fun `one listing - header, row and contact`() {
        val digest = builder.build(listOf(match))

        assertEquals(1, digest.agentCount)
        val listing = digest.listings.single()
        assertEquals("Villa Bastos", listing.title)
        assertEquals("90 000 FCFA / mois", listing.price)
        assertEquals("Bastos (Yaounde)", listing.place)
        assertEquals("https://pc.ndopify.com/mandates/11", listing.mandateUrl)
        assertNull(listing.more)
        val agent = listing.agents.single()
        assertEquals("Kim Possible", agent.fullName)
        assertEquals("+237 6 99 00 00 01", agent.whatsapp)  // TenantFormatter.phone
        assertEquals("https://wa.me/237699000001", agent.whatsappUrl)
        assertNull(agent.email)
        assertEquals("Location", agent.listingType)
        assertEquals("Appartement", agent.propertyType)
        assertEquals("Bastos (Yaounde)", agent.place)
        assertEquals("2 pièces", agent.roomsLabel)
        assertEquals("100 000 FCFA / mois", agent.budget)
        assertNull(agent.searchCount)
    }
}
```

Plus these tests, with the existing mailet test's values:
- `listings best score first, agents best score first` — two listings scored 0.4 / 0.9; two agents 0.5 / 0.8;
- `more than 5 agents - 5 rows and the count` — 6 agents → `agents.size == 5`, `more == 1`, `agentCount == 6`;
- `several searches of one agent - one row with the count` — `searchCount == 2`, budget of the best score;
- `no WhatsApp - the email is the contact` — `email == "kim.possible@gmail.com"`, `emailUrl == "mailto:…"`, `whatsappUrl == null`;
- `no WhatsApp and no email - no contact` — both null;
- `no partner portal - no mandate url` — `mandateUrl == null`;
- `no title - null` and `cover image` (`imageUrl`);
- `an unknown search agent is dropped` — mandates agent 99 missing → that listing/row absent, `agentCount` excludes it;
- `no customer in the digest` — the fixtures' search has a customer named "Secret Client" / `secret@example.com`: `digest.toString()` contains neither.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestDigestBuilderTest`
Expected: FAIL — the classes are unresolved.

- [ ] **Step 3: Implement the models and the builder**

Move the grouping body of `CoBrokerageInterestMailet.send` (its `matches.groupBy { it.listing.id } …` down to `agents.take(MAX_AGENTS)`) into `CoBrokerageInterestDigestBuilder.build`, returning the models above instead of a Mustache context. `MAX_AGENTS` moves to the builder.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestDigestBuilderTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilder.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilderTest.kt
git commit -m "feat(match): build the co-brokerage interest digest once"
```

---

### Task 3: The mailet renders the digest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt`

**Interfaces:**
- Consumes: Task 2's `CoBrokerageInterestDigest`, `ListingInterest`, `InterestedAgent`, `CoBrokerageInterestDigestBuilder`.
- Produces: `CoBrokerageInterestMailet(mailService: MailService, mailBodyResolver: MailBodyResolver)` with `fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest)`; `SUBJECT_ONE`, `SUBJECT_MANY`, `TEMPLATE` unchanged.

- [ ] **Step 1: Update the test to the new constructor and input**

Wire a real builder (`CoBrokerageInterestDigestBuilder(tenantService, agentService, locationService, imageService)`), construct `CoBrokerageInterestMailet(mailService, mailBodyResolver)`, and add `private fun send(matches: List<MatchEntity>) = mailet.send(listingAgent, builder.build(matches))` so each existing call `mailet.send(listingAgent, listOf(match))` becomes `send(listOf(match))`. Keep every assertion as it is: the context (`listings`, `agents`, `more`, `mandate_url`, `image_url`, `search_count`), the subjects ("1 agent s'intéresse à votre bien", "2 agents s'intéressent à vos biens"), the template test and the no-customer checks must pass unchanged. Replace `no matches - no email` with:

```kotlin
@Test
fun `no listing - no email`() {
    mailet.send(listingAgent, CoBrokerageInterestDigest(TenantEntity(), 0, emptyList()))

    verify(mailService, never()).send(any())
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestMailetTest`
Expected: FAIL — the constructor and `send` don't match.

- [ ] **Step 3: Implement the mailet on the digest**

Drop `tenantService`, `agentService`, `locationService` and `imageService` (they live in the builder). `send` returns when `digest.listings.isEmpty()`, keeps the subject logic (`digest.agentCount`), and builds the same context:

```kotlin
mapOf(
    "tenant_logo_url" to digest.tenant.logoUrl,
    "tenant_name" to digest.tenant.name,
    "recipient_name" to agent.party.fullName(),
    "listings" to digest.listings.map { listing ->
        mapOf(
            "image_url" to listing.imageUrl,
            "title" to (listing.title ?: "Bien n° ${listing.listingId}"),
            "price" to listing.price,
            "mandate_url" to listing.mandateUrl,
            "agents" to listing.agents.map { agent -> agent.toContext() },
            "more" to listing.more,
        )
    },
)
```

`InterestedAgent.toContext()` maps to `agent_full_name`, `agent_photo_url`, `agent_whatsapp`, `agent_whatsapp_url`, `agent_email`, `agent_email_url`, `search_count`, `listing_type`, `property_type`, `place`, `rooms_label`, `budget` — the keys the template uses today, same values. Update the KDoc: the mailet renders the digest built once for the agent.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestMailetTest`
Expected: PASS with the template and subject assertions unchanged.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt
git commit -m "refactor(match): render the co-brokerage interest e-mail from its digest"
```

---

### Task 4: The botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt`

**Interfaces:**
- Consumes: Task 1's `TelegramClient.sendMessageRows(chatId, text, rows)`, #136's `TelegramButton(text, url = …)`; Task 2's digest models.
- Produces: `@Service class CoBrokerageInterestBotlet(client: TelegramClient)` with `fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest, chatId: Long)`; throws when Telegram refuses.

- [ ] **Step 1: Write the failing botlet tests**

With a mocked `TelegramClient`, capture every `sendMessageRows` call:

```kotlin
private val textCaptor = argumentCaptor<String>()
private val rowsCaptor = argumentCaptor<List<List<TelegramButton>>>()

private fun sent(): List<Pair<String, List<List<TelegramButton>>>> {
    verify(client, atLeast(1)).sendMessageRows(eq(777L), textCaptor.capture(), rowsCaptor.capture())
    return textCaptor.allValues.zip(rowsCaptor.allValues)
}

private fun send(digest: CoBrokerageInterestDigest) = botlet.send(listingAgent, digest, 777L)
```

- `one listing - text and one-row button`:

```kotlin
send(digest)
val (text, rows) = sent().single()
assertTrue(text.startsWith("Bonjour John Doe,"))
assertTrue(text.contains("1 agent s'intéresse à vos annonces :"))
assertTrue(text.contains("Villa Bastos"))
assertTrue(text.contains("90 000 FCFA / mois — Bastos (Yaounde)"))
assertTrue(text.contains("• Kim Possible — WhatsApp +237 6 99 00 00 01"))
assertTrue(text.contains("Location - Appartement à Bastos (Yaounde) - 2 pièces - 100 000 FCFA / mois"))
assertTrue(text.endsWith("Cordialement,\nL'équipe Ndopify"))
assertEquals(
    listOf(listOf(TelegramButton("Voir le mandat — Villa Bastos", url = "https://pc.ndopify.com/mandates/11"))),
    rows,
)
```

- `several listings - one message with one row per button` — two listings, one `sendMessageRows` call, rows in group order, both labels carrying their titles;
- `long title - the button label is at most 64 characters` — a 100-character title → `label.length <= 64` and ends with "…";
- `no title - Bien n° and a plain button` — header "Bien n° 101", button `Voir le mandat`;
- `no partner portal - no button` — `sent().single().second.isEmpty()`, one text;
- `more than 5 agents - et 1 autre agent` — 6 agents → 5 rows and the line "et 1 autre agent", "6 agents s'intéressent à vos annonces";
- `over 4096 - split at listing boundaries` — enough listings that the digest exceeds 4096: every text `length <= 4096`, the concatenation of the texts contains every listing title and every agent row exactly once, each text's rows are its own groups' buttons;
- `one listing over 4096 - split by rows, header repeated` — one listing with a ~4000-character title and 5 agents: at least 2 texts, each `length <= 4096`, each contains the listing header, each agent row is whole and appears in exactly one text, the first part carries the button, the last carries "et …" if any;
- `empty digest - nothing sent` — `verify(client, never()).sendMessageRows(any(), any(), any())`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: FAIL — the class is unresolved.

- [ ] **Step 3: Implement the botlet**

Constants: `MAX_LENGTH = 4096`, `BUTTON_MAX_LENGTH = 64`. `send` renders the digest into messages and calls `client.sendMessageRows(chatId, message.text, message.buttons.map { listOf(it) })` for each.

Copy, mirroring the template:

```text
Bonjour <full name>,

<n agent(s) s'intéresse(nt) à vos annonces> :

<title or "Bien n° <id>">
<price>[ — <place>]

• <agent name>[ — WhatsApp <whatsapp>| — <email>]
  <listing type>[ - <property type>][ à <place>][ - <rooms label>] - <budget>[ - <search count> recherches]
…
[et <n> autre(s) agent(s)]

Contactez l'agent intéressé pour organiser la visite.

Cordialement,
L'équipe <tenant name>
```

Pluralization: `1 agent s'intéresse` / `N agents s'intéressent`; `et 1 autre agent` / `et N autres agents`. Omit the "à <place>" when the place is blank, and the whole line when an agent has no contact.

Splitting (pure, private `messages(agent, digest): List<Message>` with `private data class Message(val text: String, val buttons: List<TelegramButton>)`):

1. start the first buffer with the lead (greeting, blank line, count line, blank line);
2. for each listing, render its block (title, price line, blank line, rows, optional "et …" line, blank line); append it when it fits in the current buffer, else flush and start a new buffer with it;
3. a block alone over `MAX_LENGTH`: split it at row boundaries, repeating the title and price lines on each part, the "et …" line on the last part, and flush between parts;
4. a listing's button joins the message that holds the group's first part, labelled `Voir le mandat — <title>` (truncated to `BUTTON_MAX_LENGTH`, `…` last) or plain `Voir le mandat` without a title;
5. append the tail (blank line, "Contactez…", blank line, "Cordialement," and the tenant) to the last buffer when it fits, else flush and make it its own message;
6. never split a row, never send an empty message.

KDoc: one French digest per run, the e-mail's grouping, split under Telegram's limit, the customer never read; throws when Telegram refuses.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt
git commit -m "feat(match): send the co-brokerage interest digest over Telegram"
```

---

### Task 5: The notifier

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifierTest.kt`

**Interfaces:**
- Consumes: Task 4's botlet, Task 3's mailet, #136's `AgentTelegramService.chatId(agentId)`.
- Produces: `@Service class CoBrokerageInterestNotifier(botlet: CoBrokerageInterestBotlet, mailet: CoBrokerageInterestMailet, telegramService: AgentTelegramService)` with `fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest)`.

- [ ] **Step 1: Write the failing notifier tests**

- `a linked agent gets the digest on Telegram only` — `chatId(30L) = 777L` → `verify(botlet).send(agent, digest, 777L)`, `verify(mailet, never()).send(any(), any())`;
- `an unlinked agent gets the e-mail only` — `chatId` null → `verify(mailet).send(agent, digest)`, `verify(botlet, never()).send(any(), any(), any())`;
- `Telegram failing falls back to the e-mail` — `botlet.send` throws `TelegramException("Bot was blocked by the user")` → `verify(mailet).send(agent, digest)`, no exception;
- `both channels failing throws so the matches stay unnotified` — `assertFailsWith<RuntimeException> { notifier.send(agent, digest) }`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestNotifierTest`
Expected: FAIL — the class is unresolved.

- [ ] **Step 3: Implement the notifier**

Exactly the `AssignmentOfferNotifier` shape: null `chatId` → mailet; else the botlet in a `try`, and on any exception (`catch (ex: Exception)`), log `LOGGER.warn("Telegram co-brokerage interest failed for Agent#${agent.id}, falling back to e-mail", ex)` and send the mailet. No token nor URL in the message. KDoc: the failure is never swallowed when the e-mail fails too — its exception propagates.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifierTest.kt
git commit -m "feat(match): route the co-brokerage interest to Telegram or e-mail"
```

---

### Task 6: Wire the service, the job test and the module docs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (match domain and `platform/telegram`)

**Interfaces:**
- Consumes: Tasks 2, 3 and 5.
- Produces: `CoBrokerageNotificationService(dao, agentService, mailet, interestNotifier, digestBuilder, clock)`; `notifyListingAgents(): Int` unchanged in behaviour.

- [ ] **Step 1: Update the service test**

Replace the `interestMailet` mock with:

```kotlin
private val interestNotifier = mock<CoBrokerageInterestNotifier>()
private val digestBuilder = mock<CoBrokerageInterestDigestBuilder>()
private val service = CoBrokerageNotificationService(dao, agentService, mailet, interestNotifier, digestBuilder, clock)
```

Stub `digestBuilder.build(any())` to `CoBrokerageInterestDigest(TenantEntity(id = 1L), 1, emptyList())` in the reverse tests, then `verify(interestNotifier).send(listingAgent, digest)` instead of `verify(interestMailet).send(...)`. Keep every existing case: one send per listing agent, mark after a success, a failed send leaves the rows unmarked, an unknown listing agent is skipped, an unknown interested search agent is filtered out of the digest input.

- [ ] **Step 2: Update the job test**

Replace `@MockitoBean private lateinit var interestMailet: CoBrokerageInterestMailet` with `@MockitoBean private lateinit var interestNotifier: CoBrokerageInterestNotifier`. In `notifies the listing agent when a search agent is interested`, `verify(interestNotifier).send(argThat { id == 30L }, any())`; in `nothing is sent on the next run`, `verify(interestNotifier, never()).send(any(), any())`. The digest builder stays real: it runs against the fixture and makes no Telegram call. Remove the now-unused `CoBrokerageInterestMailet` import.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='CoBrokerageNotificationServiceTest,NotifyCoBrokerageJobTest'`
Expected: FAIL — the service constructor doesn't match.

- [ ] **Step 4: Implement the service wiring**

`CoBrokerageNotificationService` keeps `CoBrokerageMatchMailet` for `notifySearchAgents`. Replace `interestMailet` with `interestNotifier` and `digestBuilder`; in `notifyListingAgents`:

```kotlin
send = { agent, matches -> interestNotifier.send(agent, digestBuilder.build(matches)) },
```

Nothing else changes: the `knownSearchAgents` pre-filter, the per-agent try/catch, `markListingAgentNotified` only after the send. Update the class KDoc: the reverse goes over Telegram or e-mail through `CoBrokerageInterestNotifier`, the digest built once per agent.

- [ ] **Step 5: Update `modules/ndopify-server/CLAUDE.md`**

- In the **match** domain paragraph, replace "the listing agent is e-mailed the interested agent's contact and anonymized search criteria only after a shortlist/proposal" with: the listing agent is messaged over Telegram when linked (`CoBrokerageInterestBotlet`, one digest built by `CoBrokerageInterestDigestBuilder`, split under 4096 at listing boundaries, one `Voir le mandat — <title>` button per listing), e-mailed otherwise (`CoBrokerageInterestMailet`), with the e-mail as the fallback of a Telegram failure; the notifier throws when both fail, so `listingAgentNotifiedAt` stays null.
- In `platform/telegram`, add `sendMessageRows` (the keyboard's rows given explicitly, ex: one row per listing) to the `TelegramClient` description.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='CoBrokerageNotificationServiceTest,NotifyCoBrokerageJobTest'`
Expected: PASS.

- [ ] **Step 7: Full verification and commit**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests, JaCoCo ≥ 0.95).

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(match): notify co-brokerage interest over Telegram"
```
