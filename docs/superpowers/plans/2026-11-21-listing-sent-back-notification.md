# Listing Sent-Back Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell a listing's agent over Telegram (e-mail as the fallback) when its listing is sent back to `DRAFT` for correction, with a generic message and a link to its Partner Central page.

**Architecture:** `ListingVerifierService` publishes a `ListingSentBackEvent` inside the transaction that saves `DRAFT` + `errorCodes` (a verifier rejection, or the `INTERNAL_ERROR` left after `MAX_RETRIES`). An async after-commit listener calls `ListingService.sendSentBackNotification`, which guards on `DRAFT` + non-empty `errorCodes` and delegates to a new `ListingSentBackNotifier`: Telegram when the agent's chat is linked, e-mail otherwise or when Telegram fails. A small copy object carries the generic French wording and the property label (`n° <id> — <neighbourhood>, <city>`); a shared `ListingUrls.fixUrl` builds `{partnerCentralUrl}/listings/{id}`.

**Tech Stack:** Kotlin, Spring Boot (application events, `TransactionTemplate`, `@Async` listeners), JUnit 5 + `kotlin.test`, mockito-kotlin, Mustache/MJML e-mail templates, Spring Data JPA with MySQL-backed endpoint tests.

**Spec:** Story #230 — https://github.com/wutsi/ndopify-mono/issues/230 (the design and acceptance criteria live in the issue description).

## Global Constraints

- UI text, e-mail copy and Telegram copy are in French; code, comments, logs and docs are in English.
- `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`), never `org.junit.jupiter.api.Assertions.*`; mocks with mockito-kotlin (`mock`, `doReturn(...).whenever(...)`, `verify`, `argumentCaptor`).
- KDoc on every new class and public method: what it is for and the non-obvious rules (the guard, the fallback, the link rule).
- The message is **generic**: no per-reason wording in the server. `ListingRules.REJECTIONS` stays Partner Central's; the message points at the page instead.
- Trigger: every send-back, on each occurrence — ordinary verifier `errorCodes`, and `INTERNAL_ERROR` after `MAX_RETRIES`. Never on a successful verification nor on an attempt left `UNDER_VERIFICATION`.
- No `ndopify-dto`, `ndopify-web-core`, Partner Central, `src/main/html/`, Flyway or configuration-key change. No API change.
- The Telegram failure path falls back to e-mail and is logged without the bot token nor the Telegram URL; an async listener failure is logged, never rethrown.
- Every task ends with `mvn test -Dtest=...` green; the last task runs the module's full `mvn verify` (`modules/ndopify-server` needs its local MySQL).
- Commits: conventional (`feat(server): ...`, `test(server): ...`, `docs(server): ...`), with `Refs #230` in the body.
- Work on branch `feat/listing-sent-back-notification` in a worktree; never on `main`.

## Review Focus

- A listing resubmitted between the send-back save and the async listener: nothing may be sent for a listing that is no longer `DRAFT` or has no errors (`ListingServiceTest`).
- The retry distinction: a verifier failure left `UNDER_VERIFICATION` notifies nobody; the failure that exhausts `MAX_RETRIES` and writes `DRAFT` + `INTERNAL_ERROR` notifies (`ListingVerifierServiceTest`).
- `partnerCentralUrl` missing, blank or ending in `/`: no button/link, or no double slash (`ListingUrlsTest`, the botlet and mailet tests).
- The generic copy: no reasons in the message; a blank city/neighbourhood gives `n° <id>` with no dangling separator (`ListingSentBackCopyTest`, the botlet and template tests).
- Telegram refusing the message, or no linked chat: exactly one e-mail, never a lost notice (`ListingSentBackNotifierTest`).
- End to end: a linked agent is messaged (no e-mail), an unlinked one is e-mailed (no Telegram) (`Task 7`).

---

### Task 1: The copy and the Partner Central fix URL

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackCopy.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingUrls.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackCopyTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingUrlsTest.kt`

**Interfaces:**
- Produces: `object ListingSentBackCopy` with `HEADLINE = "Votre annonce"`, `STATUS = "a été renvoyée pour correction"`, `NEXT_STEP = "Ouvrez-la dans Partner Central pour voir ce qui est à corriger, puis renvoyez-la."`, `BUTTON = "Corriger l'annonce"`, `SUBJECT = "{{tenant_name}}: Votre annonce a été renvoyée pour correction"`, and `property(id: Long, city: String?, neighbourhood: String?): String`.
- Produces: `ListingUrls.fixUrl(tenant: TenantEntity, listing: ListingEntity): String?`.

- [ ] **Step 1: Write the failing copy test**

```kotlin
class ListingSentBackCopyTest {
    @Test
    fun `the property label carries the id and, when known, the place`() {
        assertEquals("n° 101 — Bastos, Yaoundé", ListingSentBackCopy.property(101L, "Yaoundé", "Bastos"))
        assertEquals("n° 101 — Yaoundé", ListingSentBackCopy.property(101L, "Yaoundé", null))
        assertEquals("n° 101 — Bastos", ListingSentBackCopy.property(101L, null, "Bastos"))
        assertEquals("n° 101", ListingSentBackCopy.property(101L, null, null))
        assertEquals("n° 101", ListingSentBackCopy.property(101L, " ", "  "))
    }

    @Test
    fun `the copy names no reason`() {
        assertEquals("Votre annonce", ListingSentBackCopy.HEADLINE)
        assertEquals("a été renvoyée pour correction", ListingSentBackCopy.STATUS)
        assertEquals("Corriger l'annonce", ListingSentBackCopy.BUTTON)
    }
}
```

- [ ] **Step 2: Write the failing URL test**

```kotlin
class ListingUrlsTest {
    private val tenant = TenantEntity(id = 1L, partnerCentralUrl = "https://partner.ndopify.com/")
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `the fix URL is the listing page of Partner Central`() {
        assertEquals("https://partner.ndopify.com/listings/101", ListingUrls.fixUrl(tenant, listing))
        assertEquals(
            "https://partner.ndopify.com/listings/101",
            ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = "https://partner.ndopify.com"), listing),
        )
    }

    @Test
    fun `no partner central URL means no link`() {
        assertNull(ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = null), listing))
        assertNull(ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = " "), listing))
    }

    @Test
    fun `a listing without an id means no link`() {
        assertNull(ListingUrls.fixUrl(tenant, ListingEntity()))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackCopyTest,ListingUrlsTest`
Expected: compilation failure — the copy and `fixUrl` are unresolved.

- [ ] **Step 4: Write `ListingSentBackCopy`**

An `object` with the constants above. `property` joins `neighbourhood` then `city`, skipping null/blank values, and returns `n° <id>` alone when both are blank, else `n° <id> — <place>`.

- [ ] **Step 5: Implement `ListingUrls.fixUrl`**

Add to the existing object, with KDoc: the listing's page in Partner Central (a `DRAFT` has no public URL), `null` without a non-blank `partnerCentralUrl` or a listing id. Trim the trailing `/` from the base before appending `/listings/{id}`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackCopyTest,ListingUrlsTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackCopy.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingUrls.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackCopyTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingUrlsTest.kt
git commit -m "feat(server): add the sent-back copy and fix URL" -m "Refs #230"
```

---

### Task 2: The event, published by the verifier

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingSentBackEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierServiceTest.kt`

**Interfaces:**
- Produces: `data class ListingSentBackEvent(val listingId: Long, val tenantId: Long)`.
- Produces: `ListingVerifierService(dao, tx, clock, image, geo, eventPublisher: ApplicationEventPublisher)`.

- [ ] **Step 1: Write the failing verifier tests**

Add `private val eventPublisher = mock<ApplicationEventPublisher>()` and pass it to the service. Change the fixture to `ListingEntity(id = 1L, tenantId = 7L, status = ListingStatus.UNDER_VERIFICATION)`. Add:

```kotlin
@Test
fun `a rejection publishes a sent-back event`() {
    doReturn(ListingVerification(errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)).whenever(image).verify(any())

    service.verify(listing)

    verify(eventPublisher).publishEvent(ListingSentBackEvent(listingId = 1L, tenantId = 7L))
}

@Test
fun `a verified listing publishes no sent-back event`() {
    service.verify(listing)

    verify(eventPublisher, never()).publishEvent(any<Any>())
}

@Test
fun `a retry left under verification publishes no sent-back event`() {
    doThrow(RuntimeException("boom")).whenever(image).verify(any())

    service.verify(listing)

    verify(eventPublisher, never()).publishEvent(any<Any>())
}

@Test
fun `the last retry publishes a sent-back event`() {
    doThrow(RuntimeException("boom")).whenever(geo).verify(any())

    service.verify(listing.copy(retries = ListingVerifierService.MAX_RETRIES))

    verify(eventPublisher).publishEvent(ListingSentBackEvent(listingId = 1L, tenantId = 7L))
}
```

Update the existing tests' construction and keep their assertions unchanged.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierServiceTest`
Expected: compilation failure — the event and the constructor parameter are unresolved.

- [ ] **Step 3: Create `ListingSentBackEvent`**

```kotlin
package com.wutsi.ndopify.listing.server.event

/**
 * Published by `ListingVerifierService.verify`, inside the transaction that saves a listing back to DRAFT with
 * `errorCodes` (a verifier rejection, or `INTERNAL_ERROR` after `MAX_RETRIES`). Carries ids rather than the entity,
 * so listeners reload what they need. [tenantId] is the listing's tenant, since listeners run on a thread with none.
 */
data class ListingSentBackEvent(
    val listingId: Long,
    val tenantId: Long,
)
```

- [ ] **Step 4: Publish it from the verifier**

Add `private val eventPublisher: ApplicationEventPublisher` as the last constructor parameter. In both `tx.execute` blocks, capture `val saved = dao.save(...)` and, when `saved.status == ListingStatus.DRAFT`, call `eventPublisher.publishEvent(ListingSentBackEvent(listingId = saved.id!!, tenantId = saved.tenantId!!))` inside the same block (an `AFTER_COMMIT` listener only fires for an event published in the transaction). Update the class KDoc: a send-back publishes the event.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingSentBackEvent.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierServiceTest.kt
git commit -m "feat(server): publish a listing sent-back event" -m "Refs #230"
```

---

### Task 3: The Telegram botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingSentBackBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingSentBackBotletTest.kt`

**Interfaces:**
- Consumes: `ListingSentBackCopy`, `ListingUrls.fixUrl`, `TenantService.findById`, `LocationService.findByIdOrNull`, `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton>)`.
- Produces: `ListingSentBackBotlet.send(listing: ListingEntity, chatId: Long)`; throws `TelegramException` when Telegram refuses.

- [ ] **Step 1: Write the failing botlet test**

Use mocked `TenantService`, `LocationService` and `TelegramClient`. Fixture:

```kotlin
class ListingSentBackBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = ListingSentBackBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", partnerCentralUrl = "https://partner.ndopify.com/")
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        errorCodes = listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
    )
    private val text = """
        Votre annonce n° 101 — Bastos, Yaoundé a été renvoyée pour correction

        Ouvrez-la dans Partner Central pour voir ce qui est à corriger, puis renvoyez-la.
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaoundé")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    @Test
    fun `the message and its fix button are sent`() {
        botlet.send(listing, 1000L)

        verify(client).sendMessage(
            1000L,
            text,
            listOf(TelegramButton("Corriger l'annonce", url = "https://partner.ndopify.com/listings/101")),
        )
    }

    @Test
    fun `no partner central URL means no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(listing, 1000L)

        verify(client).sendMessage(1000L, text, emptyList())
    }

    @Test
    fun `no place means the id alone`() {
        val text = argumentCaptor<String>()
        botlet.send(listing.copy(cityId = -1L, neighborhoodId = null), 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("Votre annonce n° 101 a été renvoyée pour correction\n"))
    }

    @Test
    fun `a Telegram refusal propagates`() {
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(client).sendMessage(eq(1000L), any(), any())

        assertFailsWith<TelegramException> { botlet.send(listing, 1000L) }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackBotletTest`
Expected: compilation failure — `ListingSentBackBotlet` is unresolved.

- [ ] **Step 3: Implement `ListingSentBackBotlet`**

A `@Service` with `tenantService`, `locationService`, `client`. `send` loads the tenant from `listing.tenantId!!`, resolves the city and neighbourhood names (null for missing/blank), builds the lines:

1. `"${ListingSentBackCopy.HEADLINE} ${ListingSentBackCopy.property(listing.id!!, city, neighbourhood)} ${ListingSentBackCopy.STATUS}"`
2. blank line, then `ListingSentBackCopy.NEXT_STEP`

Then `client.sendMessage(chatId, lines.joinToString("\n"), buttons)` where `buttons` is one `TelegramButton(ListingSentBackCopy.BUTTON, url = ListingUrls.fixUrl(tenant, listing))` when the URL exists, else `emptyList()`. KDoc: the recipient is the chat from #135's link; Telegram refusing throws so the notifier falls back.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingSentBackBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingSentBackBotletTest.kt
git commit -m "feat(server): add the listing sent-back Telegram message" -m "Refs #230"
```

---

### Task 4: The e-mail and its template

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/listing/sent_back_agent.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackTemplateTest.kt`

**Interfaces:**
- Consumes: `ListingSentBackCopy`, `ListingUrls.fixUrl`, `MailService.send(Email)`, `MailBodyResolver.resolve(path, context)`, `TenantService`, `LocationService`.
- Produces: `ListingSentBackMailet.send(listing: ListingEntity, agent: AgentEntity)`; `SUBJECT_AGENT = ListingSentBackCopy.SUBJECT`; `TEMPLATE_AGENT = "/mail/listing/sent_back_agent.mjml"`.

- [ ] **Step 1: Write the failing mailet test**

Mirror `ListingPublishedMailetTest` with mocks for `TenantService`, `LocationService`, `MailService` and `MailBodyResolver`. Fixtures: tenant with `logoUrl` and `partnerCentralUrl`, listing `id = 101`, `tenantId = 1`, `cityId = 2370201`, `neighborhoodId = 111`, both error codes; agent `Kim Possible <kim.possible@gmail.com>`. Stub `mailBodyResolver.resolve(any(), any())` to `"<html>sent back</html>"`.

```kotlin
@Test
fun `send to the agent only`() {
    mailet.send(listing, agent)

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
    assertEquals("Kim Possible", email.firstValue.recipient.displayName)
    assertEquals("Ndopify: Votre annonce a été renvoyée pour correction", email.firstValue.subject)
    assertEquals("<html>sent back</html>", email.firstValue.body)
}

@Test
fun `the e-mail context carries the property and the link`() {
    mailet.send(listing, agent)

    val context = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver).resolve(eq(ListingSentBackMailet.TEMPLATE_AGENT), context.capture())
    assertEquals(
        mapOf(
            "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
            "tenant_name" to "Ndopify",
            "recipient_name" to "Kim Possible",
            "property" to "n° 101 — Bastos, Yaoundé",
            "fix_url" to "https://partner.ndopify.com/listings/101",
        ),
        context.firstValue,
    )
}

@Test
fun `no partner central URL means no link`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    mailet.send(listing, agent)

    val context = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver).resolve(eq(ListingSentBackMailet.TEMPLATE_AGENT), context.capture())
    assertNull(context.firstValue["fix_url"])
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackMailetTest`
Expected: compilation failure — `ListingSentBackMailet` is unresolved.

- [ ] **Step 3: Implement `ListingSentBackMailet` and the template**

The mailet loads the tenant, resolves the city and neighbourhood names (blank → null), builds `property = ListingSentBackCopy.property(listing.id!!, city, neighbourhood)` and `fix_url = ListingUrls.fixUrl(tenant, listing)`, then sends one `Email` to `agent.party` with `SUBJECT_AGENT` (the tenant's name substituted) and the resolved body. The context also carries `tenant_logo_url`, `tenant_name` and `recipient_name`.

Template `mail/listing/sent_back_agent.mjml`, following `published_agent.mjml`'s structure and CSS classes:

```xml
<mjml>
    <mj-head>
        <mj-style inline="inline">
            {{__css__}}
        </mj-style>
    </mj-head>
    <mj-body css-class="body">
        <mj-section css-class="main-content">
            <mj-column>
                <mj-image align="center" src="{{tenant_logo_url}}" alt="Logo" width="128" />
                <mj-divider css-class="divider"/>

                <mj-text>
                    Bonjour <strong>{{recipient_name}}</strong>,
                </mj-text>
                <mj-text>
                    Votre annonce <strong>{{property}}</strong> a été
                    <span class="text-primary"><strong>renvoyée pour correction</strong></span>.
                </mj-text>

                <mj-text>
                    Ouvrez-la dans Partner Central pour voir ce qui est à corriger, puis renvoyez-la.
                </mj-text>

                {{#fix_url}}
                <mj-spacer/>
                <mj-button href="{{fix_url}}" css-class="btn-primary" align="center">
                    Corriger l'annonce
                </mj-button>
                <mj-spacer/>
                {{/fix_url}}

                <mj-spacer/>
                <mj-text>
                    Cordialement,
                    <br/>
                    L'équipe {{tenant_name}}
                </mj-text>
            </mj-column>
        </mj-section>
    </mj-body>
</mjml>
```

- [ ] **Step 4: Write the failing template test**

```kotlin
class ListingSentBackTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )
    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "property" to "n° 101 — Bastos, Yaoundé",
        "fix_url" to "https://partner.ndopify.com/listings/101",
    )

    @Test
    fun `the e-mail carries the generic message and the fix button`() {
        val html = resolver.resolve(ListingSentBackMailet.TEMPLATE_AGENT, context)

        assertTrue(html.contains("renvoyée pour correction"))
        assertTrue(html.contains("n° 101 — Bastos, Yaoundé"))
        assertTrue(html.contains("Ouvrez-la dans Partner Central"))
        assertTrue(html.contains("https://partner.ndopify.com/listings/101"))
        assertTrue(html.contains("Corriger l'annonce"))
    }

    @Test
    fun `without a link there is no button`() {
        val html = resolver.resolve(ListingSentBackMailet.TEMPLATE_AGENT, context - "fix_url")

        assertFalse(html.contains("partner.ndopify.com"))
        assertFalse(html.contains("Corriger l'annonce"))
    }
}
```

- [ ] **Step 5: Run both tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackMailetTest,ListingSentBackTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackMailet.kt \
        modules/ndopify-server/src/main/resources/mail/listing/sent_back_agent.mjml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackMailetTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingSentBackTemplateTest.kt
git commit -m "feat(server): add the listing sent-back e-mail" -m "Refs #230"
```

---

### Task 5: The notifier (Telegram or e-mail)

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSentBackNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingSentBackNotifierTest.kt`

**Interfaces:**
- Consumes: `ListingSentBackBotlet.send(listing, chatId)`, `ListingSentBackMailet.send(listing, agent)`, `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `ListingSentBackNotifier.send(listing: ListingEntity, agent: AgentEntity)`.

- [ ] **Step 1: Write the failing notifier test**

Mirror `ListingPublishedNotifierTest`:

```kotlin
class ListingSentBackNotifierTest {
    private val botlet = mock<ListingSentBackBotlet>()
    private val mailet = mock<ListingSentBackMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = ListingSentBackNotifier(botlet, mailet, telegramService)

    private val listing = ListingEntity(id = 101L)
    private val agent = AgentEntity(id = 20L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(listing, agent)

        verify(mailet).send(listing, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(listing, agent)

        verify(botlet).send(listing, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user")).whenever(botlet).send(listing, 1000L)

        notifier.send(listing, agent)

        verify(mailet).send(listing, agent)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackNotifierTest`
Expected: compilation failure — `ListingSentBackNotifier` is unresolved.

- [ ] **Step 3: Implement `ListingSentBackNotifier`**

Same shape as `ListingPublishedNotifier`: resolve the chat; no chat → mailet; else try the botlet and, on any `Exception`, log `Telegram send-back failed for Listing#<id>, falling back to e-mail` (never the token nor the URL) and send the mailet. KDoc the channel rule and the fallback.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingSentBackNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSentBackNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingSentBackNotifierTest.kt
git commit -m "feat(server): route the sent-back notice to Telegram or e-mail" -m "Refs #230"
```

---

### Task 6: The service method and the after-commit listener

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListenerTest.kt`

**Interfaces:**
- Consumes: `ListingSentBackNotifier.send(listing, agent)`, `ListingSentBackEvent(listingId, tenantId)`.
- Produces: `ListingService(dao, mandateService, agentService, customerService, publishedNotifier, sentBackNotifier, sectionService, locationService, accessService, clock)` — the new parameter after `publishedNotifier`; `ListingService.sendSentBackNotification(id: Long): Boolean`; `ListingEventListener.onListingSentBack(event: ListingSentBackEvent)`.

- [ ] **Step 1: Write the failing service test**

```kotlin
class ListingServiceTest {
    private val dao = mock<ListingRepository>()
    private val mandateService = mock<MandateService>()
    private val agentService = mock<AgentService>()
    private val customerService = mock<CustomerService>()
    private val publishedNotifier = mock<ListingPublishedNotifier>()
    private val sentBackNotifier = mock<ListingSentBackNotifier>()
    private val sectionService = mock<ListingSectionService>()
    private val locationService = mock<LocationService>()
    private val accessService = mock<ListingAccessService>()
    private val clock = mock<Clock>()
    private val service = ListingService(
        dao,
        mandateService,
        agentService,
        customerService,
        publishedNotifier,
        sentBackNotifier,
        sectionService,
        locationService,
        accessService,
        clock,
    )

    private val agent = AgentEntity(id = 20L)
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        status = ListingStatus.DRAFT,
        errorCodes = listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES),
        agent = agent,
    )

    @Test
    fun `a sent-back listing notifies its agent`() {
        doReturn(Optional.of(listing)).whenever(dao).findById(101L)

        assertTrue(service.sendSentBackNotification(101L))
        verify(sentBackNotifier).send(listing, agent)
    }

    @Test
    fun `a listing no longer in draft is not notified`() {
        doReturn(Optional.of(listing.copy(status = ListingStatus.VERIFIED))).whenever(dao).findById(101L)

        assertFalse(service.sendSentBackNotification(101L))
        verifyNoInteractions(sentBackNotifier)
    }

    @Test
    fun `a listing without errors is not notified`() {
        doReturn(Optional.of(listing.copy(errorCodes = emptyList()))).whenever(dao).findById(101L)

        assertFalse(service.sendSentBackNotification(101L))
        verifyNoInteractions(sentBackNotifier)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest`
Expected: compilation failure — the new constructor parameter and method are unresolved.

- [ ] **Step 3: Implement the service method**

Add `sentBackNotifier` after `publishedNotifier`. `sendSentBackNotification(id)` loads the listing, returns `false` when `status != DRAFT || errorCodes.isEmpty()`, else calls `sentBackNotifier.send(listing, listing.agent)` and returns `true`. `@Transactional`, KDoc: the guard (a resubmission may have moved the listing on), Telegram/e-mail rule, and that the listener swallows failures.

- [ ] **Step 4: Write the failing listener tests**

Add to `ListingEventListenerTest`:

```kotlin
@Test
fun `sent back - the agent is notified, in its tenant`() {
    var tenantId: Long? = null
    doAnswer {
        tenantId = TenantContext.get()
        true
    }.whenever(service).sendSentBackNotification(10L)

    listener.onListingSentBack(ListingSentBackEvent(listingId = 10L, tenantId = 7L))

    verify(service).sendSentBackNotification(10L)
    assertEquals(7L, tenantId)
    assertNull(TenantContext.get())
    assertNull(KVLoggerThreadLocal.get())
}

@Test
fun `sent back - a failure is only logged`() {
    doThrow(IllegalStateException("boom")).whenever(service).sendSentBackNotification(10L)

    listener.onListingSentBack(ListingSentBackEvent(listingId = 10L, tenantId = 7L))

    assertNull(TenantContext.get())
    assertNull(KVLoggerThreadLocal.get())
}
```

- [ ] **Step 5: Implement the listener method**

Add `onListingSentBack` with the same annotations and body as `onListingPublished`: set the logger and tenant, log the event/tenant/listing ids, call `service.sendSentBackNotification(event.listingId)`, log `notification_sent`, catch every exception into the logger, and remove the tenant and logger in `finally`. Update the class KDoc to name both notices.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest,ListingEventListenerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListenerTest.kt
git commit -m "feat(server): send the sent-back notice after commit" -m "Refs #230"
```

---

### Task 7: End-to-end job tests (Telegram and e-mail)

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/listing/SentBackListingsJob.sql`
- Create: `modules/ndopify-server/src/test/resources/db/test/listing/SentBackListingsJobNoTelegram.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramSentBackListingsJobTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/SentBackListingsJobTest.kt`

**Interfaces:**
- Consumes: `ListingJobs.verify()`, `BaseEndpointIntegrationTest` (`smtp`, `awaitAsyncTasks`), `TelegramClient.sendMessage`.
- Produces: no production code; these tests prove the wiring from `jobs.verify()` to the botlet / mailet.

- [ ] **Step 1: Write the SQL fixture**

Copy `VerifyListingsJob.sql` and keep two listings: `100` at the neighbourhood's centre (valid) and `101` 30 km away (outside). Both are `UNDER_VERIFICATION` — the job only selects that status, and these tests call `jobs.verify()` directly, not the `POST /v1/listings/{id}/verify` endpoint. The tenant carries `partner_central_url = 'https://partner.ndopify.com'` and `active = true`; the agent's party is `Kim Possible <kim.possible@gmail.com>`. Each listing gets the five required sections and five `VERIFIED` photos (no download in the verifier).

`SentBackListingsJob.sql` adds the linked chat: `T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES (1, 20, 1000, '2026-11-15 00:00:00')`. `SentBackListingsJobNoTelegram.sql` is the same fixture without that row, for the e-mail test (a second file, rather than a `@Sql` delete whose ordering against the class-level fixture would be fragile).

- [ ] **Step 2: Write the failing Telegram job test**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SentBackListingsJob.sql"])
class TelegramSentBackListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a rejected listing is messaged to the linked agent`() {
        jobs.verify()
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("Votre annonce n° 101 — Bastos, Yaoundé a été renvoyée pour correction"))
        assertTrue(text.firstValue.contains("Ouvrez-la dans Partner Central pour voir ce qui est à corriger"))
        assertEquals(
            listOf(TelegramButton("Corriger l'annonce", url = "https://partner.ndopify.com/listings/101")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no sent-back e-mail for a linked agent")
    }
}
```

`verify(client).sendMessage(...)` is the only Telegram interaction: listing `100` passes verification, where nothing is sent. Note: `jobs.verify()` leaves `100` `VERIFIED`; no publish job runs.

- [ ] **Step 3: Write the failing e-mail job test**

Same fixture without the Telegram link:

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SentBackListingsJobNoTelegram.sql"])
class SentBackListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `an unlinked agent is e-mailed the generic message and the fix link`() {
        jobs.verify()
        awaitAsyncTasks()

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no e-mail received")
        val message = smtp.receivedMessages.single()
        assertEquals("Kim Possible <kim.possible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Votre annonce a été renvoyée pour correction", message.subject)
        assertTrue(message.content.toString().contains("n° 101 — Bastos, Yaoundé"))
        assertTrue(message.content.toString().contains("https://partner.ndopify.com/listings/101"))
        verifyNoInteractions(client)
    }
}
```

- [ ] **Step 4: Run them**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramSentBackListingsJobTest,SentBackListingsJobTest`
Expected: PASS once Tasks 1–6 are complete. These tests pin the wiring end to end rather than drive new production code: a failure means the event, the listener, the guard, the copy or the link is wrong, not that the test needs loosening. If the fixture itself is wrong, fix it first.

- [ ] **Step 5: Fix the owning class, never the assertion**

A failure points at one of: the event not published in the verifier transaction, the listener not registered, the guard rejecting the listing, the property label, or the link. Fix the class from Tasks 1–6. Change an assertion only if it contradicts story #230.

- [ ] **Step 6: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramSentBackListingsJobTest,SentBackListingsJobTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/listing/SentBackListingsJob.sql \
        modules/ndopify-server/src/test/resources/db/test/listing/SentBackListingsJobNoTelegram.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramSentBackListingsJobTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/SentBackListingsJobTest.kt
git commit -m "test(server): cover the sent-back notification end to end" -m "Refs #230"
```

---

### Task 8: Documentation and the module build

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

- [ ] **Step 1: Document the notice in the listing section**

After the *Send* paragraph (the send-back to `DRAFT`), add a short paragraph: a send-back publishes `ListingSentBackEvent`; `ListingSentBackNotifier` messages a linked agent (`ListingSentBackBotlet`, via `AgentTelegramService.chatId`) and e-mails the others (`ListingSentBackMailet`, `mail/listing/sent_back_agent.mjml`), falling back to e-mail on a failed send; the message is generic, names the property (`n° <id>` plus neighbourhood/city) and carries a `Corriger l'annonce` button to `{partnerCentralUrl}/listings/{id}` (none without it); nothing is sent when the listing is no longer `DRAFT` with errors.

- [ ] **Step 2: Document the event and the notifier under asynchronous e-mails**

Add `ListingSentBackEvent` to the event list, `ListingService.sendSentBackNotification` to the service-method list, and `ListingSentBackNotifier` to the sentence listing the notices that go over Telegram with an e-mail fallback.

- [ ] **Step 3: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate green.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the listing sent-back notification" -m "Refs #230"
```

---

## Done when

- Every task's commit is on `feat/listing-sent-back-notification`, `mvn verify` is green in `modules/ndopify-server`.
- Nothing is pushed: the story is ready for `push 230`.
- The story #230 is still `Ready` until `implement` moves it to `In progress`.
