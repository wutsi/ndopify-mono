# Telegram Visit Cancellation Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the agent over Telegram that its customer cancelled a scheduled visit — with the e-mail as the fallback — while a system cancellation (mandate ended) and a cancellation of a never-scheduled visit notify nobody.

**Architecture:** `VisitCancelledEvent` still reaches `VisitEventListener` (async, after commit), which calls `VisitService.sendCancelledNotification` (renamed from `sendCancelledEmail`). That method returns unless the visit is `CANCELLED`, a party cancelled it (`CUSTOMER` / `AGENT`, never `SYSTEM`) and it had a `scheduledAt`; a customer cancellation goes to the new `VisitCancelledNotifier` (linked agent → `VisitCancelledBotlet`, otherwise the e-mail; the e-mail on any bot failure), an agent cancellation to the split `VisitCancelledMailet.sendToCustomer`. The mailet's single `send` is split into `sendToCustomer` / `sendToAgent` so that only the agent's copy can be rerouted.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/150

## Global Constraints

- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- Written against `origin/main` with #148 merged: `VisitRequestedNotifier`, `VisitRequestedBotlet` and the renamed `VisitService.sendRequestedNotification` already exist there.
- No database migration, no `ndopify-dto` nor `ndopify-web-core` change, no UI page.
- The bot's texts are French; code, comments, logs and docs are English.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- Never log, throw or report the bot token or the Telegram URL (see #134).
- The `qa` profile must make no Telegram call; a `@SpringBootTest` reaches only a mocked `TelegramClient`.
- KDoc on classes and public methods; a service method's KDoc lists the rules it enforces.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #150` in the body.

## Review Focus

- A customer cancellation of a **scheduled** visit with a linked agent: exactly one bot message, no cancellation e-mail to the agent (Task 4 gates on the cause, Task 5 proves it end to end).
- A **`SYSTEM`** cancellation of a scheduled visit: no bot message and no e-mail to either side, while the co-brokerage e-mail to the listing agent still goes (Task 4).
- A **`REQUESTED`** visit (`scheduledAt == null`) cancelled by the customer: no Telegram call at all — the botlet must never be reached without a slot (Task 4, Task 5).
- Telegram down or the bot blocked: the agent's e-mail still goes, once, and the failure is logged without the token nor the URL (Task 3).
- An **agent** cancellation: the customer's e-mail only; the botlet is never reached (it would tell the agent it cancelled itself) and no customer link token is created on the agent path (Task 4).

---

### Task 1: Split the cancellation e-mail by recipient

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt`

**Interfaces:**
- Consumes: the existing private `send(tenant, formatter, visit, listing, recipient, otherParty, sender, label, visitUrl, reason, footer)` and `context(...)`.
- Produces:
  - `VisitCancelledMailet.sendToCustomer(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)` — the customer's copy; nothing unless `visit.cancelledBy` is `AGENT` or `SYSTEM`
  - `VisitCancelledMailet.sendToAgent(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)` — the agent's copy; nothing unless `visit.cancelledBy` is `CUSTOMER` or `SYSTEM`
  - `VisitCancelledMailet.send(...)` is removed.

- [ ] **Step 1: Point the mailet tests at the two methods (failing)**

In `VisitCancelledMailetTest`:
- `the customer cancelled - the agent is told`: call `mailet.sendToAgent(visit, customer, agent, listing)`. Assertions unchanged.
- `the agent cancelled - the customer is told`: call `mailet.sendToCustomer(visit.copy(cancelledBy = VisitParty.AGENT), customer, agent, listing)`. Assertions unchanged.
- `no reason`: call `mailet.sendToAgent(visit.copy(cancellationReason = null), customer, agent, listing)`.
- `the system cancelled - both sides get the neutral wording, never the mandate reason`: call both methods with `visit.copy(cancelledBy = VisitParty.SYSTEM, cancellationReason = "Mandat clôturé")`; the two-emails / two-contexts assertions stay.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledMailetTest`
Expected: compilation error — `sendToAgent` / `sendToCustomer` unresolved.

- [ ] **Step 3: Implement the split**

In `VisitCancelledMailet`, replace `fun send(...)` with the two methods. Each keeps the `when (visit.cancelledBy)` branches for the copies it owns — `sendToCustomer` renders the `AGENT` and `SYSTEM` copies (with the token, `AgentMailContext.from(agent)` as sender and `AgentMailContext.of(agent, formatter)` as footer), `sendToAgent` renders the `CUSTOMER` and `SYSTEM` copies (sender `null`, empty footer, `partnerCentralUrl(tenant, visit)`). Both go through the existing private `send(...)`; no context key, template or wording changes.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledMailetTest`
Expected: PASS.

- [ ] **Step 5: Update the service call site so the module compiles**

In `VisitService.sendCancelledEmail`, keep the existing status / `cancelledBy` guard and replace the single `cancelledMailet.send(...)` by:

```kotlin
when (visit.cancelledBy) {
    VisitParty.CUSTOMER -> cancelledMailet.sendToAgent(visit, customer, agent, listing)
    VisitParty.AGENT -> cancelledMailet.sendToCustomer(visit, customer, agent, listing)
    else -> {
        cancelledMailet.sendToCustomer(visit, customer, agent, listing)
        cancelledMailet.sendToAgent(visit, customer, agent, listing)
    }
}
```

Load `customer` alongside `agent` and `listing` before the `when`. Behavior is unchanged in this task.

- [ ] **Step 6: Run the visit tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledMailetTest,VisitServiceTest`
Expected: PASS (Task 4 changes the routing).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt
git commit -m "refactor(visit): split the cancellation e-mail by recipient" -m "Refs #150"
```

---

### Task 2: The cancellation botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`, `TenantService.findById(id: Long): TenantEntity`, `TenantFormatter(tenant).date(Date)` / `.time(Date)`, `LocationService.findByIdOrNull(id: Long): LocationEntity?`, `AgentEntity.party.fullName()`, `CustomerEntity.party.fullName()`, `VisitCancelledMailet.LABEL_BY_CUSTOMER`.
- Produces: `VisitCancelledBotlet.send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long)` — a customer cancellation only; it throws when Telegram refuses.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.visit.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitCancelledBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = VisitCancelledBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val listing = ListingEntity(id = 101L, title = "Nice apartment", cityId = 2370201, neighborhoodId = 1001)
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CANCELLED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        cancelledBy = VisitParty.CUSTOMER,
        cancellationReason = "Empêchement",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201)
        doReturn(LocationEntity(id = 1001, name = "Bastos")).whenever(locationService).findByIdOrNull(1001)
    }

    @Test
    fun `a customer cancellation carries the slot, the listing, the customer and the reason`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Le client a annulé la visite.

            Bien: Nice apartment
            Ville: Yaounde
            Quartier: Bastos
            Date: 02/12/2026 à 10:00
            Client: Ray Sponsible
            Motif: Empêchement
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `no reason and no neighbourhood leave no line`() {
        botlet.send(
            visit.copy(cancellationReason = " "),
            customer,
            agent,
            listing.copy(neighborhoodId = null),
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Motif:"))
        assertFalse(text.firstValue.contains("Quartier:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Bien n° 101"))
    }

    @Test
    fun `an unknown city leaves no Ville line`() {
        doReturn(null).whenever(locationService).findByIdOrNull(2370201)

        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Ville:"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest`
Expected: compilation error — `VisitCancelledBotlet` unresolved.

- [ ] **Step 3: Implement `VisitCancelledBotlet`**

A `@Service` in `visit/server/bot/` with the constructor `(tenantService, locationService, client)`. `send` resolves the tenant and its `TenantFormatter`, then calls `client.sendMessage(chatId, text, buttons)`. The text, line by line: `"Bonjour ${agent.party.fullName()},"`, blank, `VisitCancelledMailet.LABEL_BY_CUSTOMER`, blank, then `"Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}")`, `"Ville: ${city}"` only when `locationService.findByIdOrNull(listing.cityId)?.name` is non-blank, `"Quartier: ${neighbourhood}"` only when `listing.neighborhoodId` resolves to a non-blank name, `"Date: ${formatter.date(it)} à ${formatter.time(it)}"` when `visit.scheduledAt != null`, `"Client: ${customer.party.fullName()}"`, and `"Motif: $it"` when `visit.cancellationReason` is non-blank. Buttons: `listOf(TelegramButton("Voir la visite", url = "$base/visits/${visit.id}"))` where `base = tenant.partnerCentralUrl?.removeSuffix("/")`, else `emptyList()`.

KDoc: the mirror of the e-mail's agent copy for a customer cancellation — never the customer's code, exact address or public-portal token; the recipient chat comes from #135's link; Telegram refusing throws.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt
git commit -m "feat(visit): send the cancellation notice over Telegram" -m "Refs #150"
```

---

### Task 3: The cancellation notifier

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitCancelledNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitCancelledNotifierTest.kt`

**Interfaces:**
- Consumes: Task 1's `VisitCancelledMailet.sendToAgent(visit, customer, agent, listing)`, Task 2's `VisitCancelledBotlet.send(visit, customer, agent, listing, chatId)`, `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `VisitCancelledNotifier.send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)` — only for the agent-addressed copy (a customer cancellation); the caller routes.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.visit.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.bot.VisitCancelledBotlet
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test

class VisitCancelledNotifierTest {
    private val botlet = mock<VisitCancelledBotlet>()
    private val mailet = mock<VisitCancelledMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = VisitCancelledNotifier(botlet, mailet, telegramService)

    private val visit = VisitEntity(id = 1L, agentId = 20L, status = VisitStatus.CANCELLED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendToAgent(visit, customer, agent, listing)
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
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(visit, customer, agent, listing, 1000L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).sendToAgent(visit, customer, agent, listing)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledNotifierTest`
Expected: compilation error — `VisitCancelledNotifier` unresolved.

- [ ] **Step 3: Implement `VisitCancelledNotifier`**

A `@Service` in `visit/server/service/` with `(botlet, mailet, telegramService)` and a private `LOGGER`. `send` resolves `telegramService.chatId(agent.id!!)`; a null chat id calls `mailet.sendToAgent(...)` and returns; otherwise the botlet, and `catch (ex: Exception)` logs `Telegram visit cancellation failed for Visit#<id>, falling back to e-mail` then calls `mailet.sendToAgent(...)` — any transport failure falls back, not only `TelegramException`, and the token / URL never appear.

KDoc: tells the agent its customer cancelled a scheduled visit, over Telegram when #135's link gives it a chat, by e-mail otherwise; the e-mail is the failure fallback, so the agent never waits on a slot that is gone; called for a customer-initiated cancellation only.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitCancelledNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitCancelledNotifierTest.kt
git commit -m "feat(visit): route the cancellation notice to Telegram or e-mail" -m "Refs #150"
```

---

### Task 4: Notify only party-initiated, scheduled cancellations

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt`

**Interfaces:**
- Consumes: Task 3's `VisitCancelledNotifier.send(visit, customer, agent, listing)`; Task 1's split mailet.
- Produces: `VisitService.sendCancelledNotification(visitId: Long)` replacing `sendCancelledEmail`; `VisitCancelledMailet.sendToCustomer` only for `AGENT`, `sendToAgent` only for `CUSTOMER`.

- [ ] **Step 1: Write the failing service tests**

In `VisitServiceTest`, add the mock `private val cancelledNotifier = mock<VisitCancelledNotifier>()` and pass it to the `VisitService` constructor immediately before `cancelledMailet`. Rename every `service.sendCancelledEmail(` to `service.sendCancelledNotification(`. Add:

```kotlin
@Test
fun `customer cancelled - the agent is notified, not e-mailed`() {
    doReturn(
        Optional.of(
            visit.copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.CUSTOMER,
                scheduledAt = date(2026, 12, 2, 10, 0),
            ),
        ),
    ).whenever(dao).findById(1L)

    service.sendCancelledNotification(1L)

    verify(cancelledNotifier).send(any(), eq(customer), eq(agent20), eq(coBrokerageListing))
    verify(cancelledMailet, never()).sendToCustomer(any(), any(), any(), any())
}

@Test
fun `agent cancelled - the customer is e-mailed, Telegram is not used`() {
    doReturn(
        Optional.of(
            visit.copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.AGENT,
                scheduledAt = date(2026, 12, 2, 10, 0),
            ),
        ),
    ).whenever(dao).findById(1L)

    service.sendCancelledNotification(1L)

    verify(cancelledMailet).sendToCustomer(any(), eq(customer), eq(agent20), eq(coBrokerageListing))
    verifyNoInteractions(cancelledNotifier)
}

@Test
fun `system cancelled - neither side is notified`() {
    doReturn(
        Optional.of(
            visit.copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = VisitParty.SYSTEM,
                scheduledAt = date(2026, 12, 2, 10, 0),
            ),
        ),
    ).whenever(dao).findById(1L)

    service.sendCancelledNotification(1L)

    verifyNoInteractions(cancelledNotifier)
    verify(cancelledMailet, never()).sendToCustomer(any(), any(), any(), any())
}

@Test
fun `a visit never scheduled - nobody is notified`() {
    listOf(VisitParty.CUSTOMER, VisitParty.AGENT, VisitParty.SYSTEM).forEach { party ->
        doReturn(Optional.of(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = party, scheduledAt = null)))
            .whenever(dao).findById(1L)

        service.sendCancelledNotification(1L)
    }

    verifyNoInteractions(cancelledNotifier)
    verify(cancelledMailet, never()).sendToCustomer(any(), any(), any(), any())
    verify(coBrokerageVisitMailet, never()).sendCancelled(any(), any(), any())
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: compilation error — `sendCancelledNotification` unresolved.

- [ ] **Step 3: Rewire the service**

In `VisitService`: add the constructor parameter `private val cancelledNotifier: VisitCancelledNotifier` right before `cancelledMailet`. Replace `sendCancelledEmail` by:

```kotlin
@Transactional
fun sendCancelledNotification(visitId: Long) {
    val visit = dao.findById(visitId).orElse(null) ?: return
    if (visit.status != VisitStatus.CANCELLED || visit.cancelledBy == null || visit.scheduledAt == null) {
        return
    }
    logger.add("visit_id", visitId)
    val agent = agentService.findById(visit.agentId)
    val listing = listingService.findById(visit.listingId)
    val customer = customerService.findById(visit.customerId)
    when (visit.cancelledBy) {
        VisitParty.CUSTOMER -> cancelledNotifier.send(visit, customer, agent, listing)
        VisitParty.AGENT -> cancelledMailet.sendToCustomer(visit, customer, agent, listing)
        else -> Unit // SYSTEM: the platform's own cancellation is not notified
    }
    if (notifyListingAgentOfCancellation(visit, listing)) {
        coBrokerageVisitMailet.sendCancelled(visit, listing, agent)
    }
}
```

KDoc: notifies the side a party left behind, once the visit had a slot: a customer cancellation reaches the agent over Telegram or e-mail, an agent cancellation reaches the customer by e-mail; a `SYSTEM` cancellation and a visit that was never scheduled notify nobody, and the listing agent's co-brokerage e-mail is untouched. Nothing when the visit isn't `CANCELLED`.

Point `VisitEventListener.onVisitCancelled` at `service.sendCancelledNotification(event.visitId)` and say "the notifications (e-mails, Telegram)" in its class KDoc, as #148 did.

- [ ] **Step 4: Remove the system path from the mailet**

In `VisitCancelledMailet`: `sendToCustomer` returns unless `visit.cancelledBy == VisitParty.AGENT` (drop the `SYSTEM` branch); `sendToAgent` returns unless `visit.cancelledBy == VisitParty.CUSTOMER` (drop the `SYSTEM` branch); delete the `LABEL_BY_SYSTEM` constant. Keep the `system_cancelled` context key (still read by the template) and the template itself untouched.

In `VisitCancelledMailetTest`: delete the system-cancellation test and add:

```kotlin
@Test
fun `the agent is not told when it cancelled itself`() {
    mailet.sendToAgent(visit.copy(cancelledBy = VisitParty.AGENT), customer, agent, listing)
    mailet.sendToAgent(visit.copy(cancelledBy = VisitParty.SYSTEM), customer, agent, listing)

    verifyNoInteractions(mailService)
}

@Test
fun `the customer is not told when it cancelled itself`() {
    mailet.sendToCustomer(visit, customer, agent, listing)
    mailet.sendToCustomer(visit.copy(cancelledBy = VisitParty.SYSTEM), customer, agent, listing)

    verifyNoInteractions(mailService)
}
```

- [ ] **Step 5: Run the visit tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest,VisitCancelledMailetTest,VisitCancelledNotifierTest,VisitCancelledBotletTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/ \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/
git commit -m "feat(visit): notify only party-initiated, scheduled cancellations" -m "Refs #150"
```

---

### Task 5: Endpoint coverage

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitCancelledEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitCancelledEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CancelVisitEndpointTest.kt`

**Interfaces:**
- Consumes: `POST /v1/visits/{id}/cancel`, `CancelVisitEndpoint.sql` (1: `REQUESTED`, 2: `CONFIRMED` on listing 102), `@MockitoBean TelegramClient`, `awaitAsyncTasks()`, `smtp`.
- Produces: no production code; proves the rule against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramVisitCancelledEndpoint.sql`:

```sql
-- The agent of VisitFixtures.sql's visit 2 (agent 20), linked to the bot
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CancelVisitEndpoint.sql",
        "/db/test/telegram/TelegramVisitCancelledEndpoint.sql",
    ],
)
class TelegramVisitCancelledEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a customer cancellation messages the linked agent instead of e-mailing it`() {
        userId = 13L // Ray Sponsible, the customer of visit 2's mandate
        val response = rest.postForEntity("/v1/visits/2/cancel", CancelVisitRequest(), Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Le client a annulé la visite."))
        assertTrue(text.firstValue.contains("Bien: Small apartment"))
        assertTrue(text.firstValue.contains("Ville: Yaounde"))
        assertTrue(text.firstValue.contains("Quartier: Bastos"))
        assertTrue(text.firstValue.contains("Client: Ray Sponsible"))
        assertFalse(text.firstValue.contains("Motif:"))
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/2")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no cancellation e-mail for a linked agent")
    }

    @Test
    fun `a visit never scheduled is not notified`() {
        userId = 13L
        val response = rest.postForEntity(
            "/v1/visits/1/cancel",
            CancelVisitRequest(reason = "Empêchement"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no cancellation e-mail for a visit never scheduled")
    }

    @Test
    fun `an agent cancellation still e-mails the customer, never Telegram`() {
        val response = rest.postForEntity(
            "/v1/visits/2/cancel",
            CancelVisitRequest(reason = "Le bien est loué"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail to the customer")
        assertEquals(
            "Ray Sponsible <ray.sponsible@gmail.com>",
            smtp.receivedMessages.single().allRecipients.single().toString(),
        )
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitCancelledEndpointTest`
Expected: FAIL — the notifier or its wiring is missing, or the message differs.

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: `VisitCancelledNotifier` not a bean (missing `@Service`), or Task 4's constructor parameter absent. Change production code only for a real gap the test finds.

- [ ] **Step 5: Update `CancelVisitEndpointTest` for the new rule**

`the customer cancels a requested visit` (visit 1 has no `scheduledAt`): drop the e-mail assertions — after the status assertions, call `awaitAsyncTasks()` and `assertFalse(smtp.waitForIncomingEmail(1000, 1), "no cancellation e-mail for a visit never scheduled")`. Keep `the customer cancels a confirmed visit` and `the agent cancels` as they are.

- [ ] **Step 6: Run the endpoint tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitCancelledEndpointTest,CancelVisitEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitCancelledEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitCancelledEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/CancelVisitEndpointTest.kt
git commit -m "test(telegram): cover the visit cancellation notification" -m "Refs #150"
```

---

### Task 6: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next visit notification.

- [ ] **Step 1: Document the channel and the rule**

In the **visit** bullet, after the `CoBrokerageVisitMailet` sentence, add: a cancellation is notified only when a party cancelled a **scheduled** visit — the agent over Telegram (`VisitCancelledBotlet` through `VisitCancelledNotifier`, via `AgentTelegramService.chatId`, with a `Voir la visite` button to `{partnerCentralUrl}/visits/{id}`) or e-mail (`VisitCancelledMailet.sendToAgent`), the customer by e-mail (`sendToCustomer`); a `SYSTEM` cancellation (mandate ended, sweep) and a cancelled `REQUESTED` visit notify nobody. The message carries the date, the listing's title, city and neighbourhood, the customer and the reason, never the check-in code, the exact address nor the customer's token.

In the **Asynchronous e-mails** list, rename `VisitService.sendCancelledEmail` to `sendCancelledNotification` and note that the cancellation entry now goes over Telegram or e-mail.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(visit): document the Telegram visit cancellation notification" -m "Refs #150"
```
