# Telegram Notification First Line Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every agent notification sent over Telegram opens with a short line that says why it was sent, instead of `Bonjour <agent>,`.

**Architecture:** Each `*Botlet` builds its message as a list of lines and hands the joined text to `TelegramClient`. The only production change is the first line of each text (and the body sentence it would duplicate); the notifiers, event listeners, e-mails, Telegram client and every `send` signature stay as they are. Private text builders drop the `agent` parameter when the greeting was its only use.

**Tech Stack:** Kotlin, Spring Boot, `kotlin.test`, `mockito-kotlin` (`com.nhaarman.mockitokotlin2`). No new dependency.

**Spec:** GitHub issue #200 — https://github.com/wutsi/ndopify-mono/issues/200. The design lives in the story; there is no spec file.

## Global Constraints

- The first line is exactly the one in the story's table: short, no trailing period, no emoji, plain text.
- `TelegramClient` sends no `parse_mode`: no bold, markdown or HTML in the text.
- Message text is French; code, comments, KDoc and commits are English.
- Only botlet text changes. No `*Mailet`, notifier, `ndopify-dto`, `ndopify-web-core`, Partner Central, `src/main/html/`, Flyway nor configuration change.
- Keep every public `send` signature: the agent is the notification's recipient, the botlet convention, even when the text no longer repeats its name. Where the greeting was a private builder's only use of the agent, drop the parameter there (`text`, `caption`, `messages`, `lead`), not from `send`.
- Tests use `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertFalse`), never `org.junit.jupiter.api.Assertions.*`.
- Each commit is conventional, touches one concern, and carries `Refs #200` in its body.
- Run tests from `modules/ndopify-server`. The `qa` profile is configured by the tests; endpoint and job tests need the local MySQL (`root`, no password). Unit tests need neither MySQL nor Mailpit.
- Never log the Telegram token nor its URL (not touched here; just don't add logging).

## Review Focus

- **Empty city** (`MandateOfferBotlet`, `MandateApprovedBotlet`, `ListingPublishedBotlet`): the first line must read `Un nouveau mandat vous est proposé` / `Votre mandat est approuvé` / `Votre annonce est publiée`, with no dangling ` à ` and no double space. Tests: Tasks 7, 8 and 9.
- **Blank listing title**: the details keep the `Bien n° <id>` fallback, and no first line carries the title except the co-brokerage digest continuations. Tests: Tasks 1, 3, 4, 5, 6, 9 and 11 keep their blank-title tests.
- **Digest split over 4096 characters**: every part stays ≤ 4096, the first carries the count line, and no part starts with `Bonjour`. Tests: Task 10.
- **Optional fields** (note, reason, comment, neighbourhood, WhatsApp): removing the greeting and one sentence must not leave a leading blank line nor a double blank where a line was optional. Tests: the existing optional-field tests of each botlet, run after every task.
- **Photo caption fallback**: `ListingPublishedBotlet` sends the same first line in the photo caption and in the text sent when the photo is dropped. Tests: Task 9.

---

### Task 1: Visit requested

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt` (`text`, around line 46)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt` (line 70)

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton>)`; `VisitEntity.requestedAt`, `.type`, `.note`; `CustomerEntity.party.fullName()`; the existing `TenantFormatter`.
- Produces: nothing new. The text's first line is `${customer.party.fullName()} demande une visite`.

- [ ] **Step 1: Update the failing test's expected text**

Replace the expected block of `an on-site request carries the customer, the slot, the type and the note`:

```kotlin
        assertEquals(
            """
            Ray Sponsible demande une visite

            Bien: Nice apartment
            Date souhaitée: 01/12/2026 à 10:00
            Type: Sur place
            Note: Je viens avec ma femme

            Confirmez la visite avec la date et l'heure qui vous conviennent.
            """.trimIndent(),
            text.firstValue,
        )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: FAIL — the actual text starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} souhaite visiter un bien que vous lui avez proposé.")
```

with:

```kotlin
        lines.add("${customer.party.fullName()} demande une visite")
```

The blank `lines.add("")` that followed the removed sentence stays, so `Bien:` keeps its separation. Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: PASS (all tests of the class).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt
git commit -m "feat(visit): open the visit request message with its reason" -m "Refs #200"
```

---

### Task 2: Visit reminder and its job test

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt` (`text`, around line 53)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt` (lines 88 and 114)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt` (lines 43–44)

**Interfaces:**
- Consumes: `VisitEntity.scheduledAt`; `CustomerEntity.party.fullName()`; `TenantFormatter`; the existing `dayWord`.
- Produces: the text's first line is `Rappel de la visite avec ${customer.party.fullName()}`; the body sentence no longer opens with `Petit rappel : `.

- [ ] **Step 1: Update the failing tests' expected text**

In `VisitReminderBotletTest`, the on-site test becomes:

```kotlin
        assertEquals(
            """
            Rappel de la visite avec Ray Sponsible

            La visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Sur place
            Lieu: Bastos, Yaounde
            """.trimIndent(),
            text.firstValue,
        )
```

and the video test:

```kotlin
        assertEquals(
            """
            Rappel de la visite avec Ray Sponsible

            La visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Visite vidéo
            Lieu: Bastos, Yaounde

            Depuis le bien, appelez Ray Sponsible en vidéo sur WhatsApp. Le client vous lira son code de visite pendant l'appel.
            """.trimIndent(),
            text.firstValue,
        )
```

In `TelegramVisitReminderJobTest`, replace lines 43–44 with:

```kotlin
        assertTrue(text.firstValue.startsWith("Rappel de la visite avec Ray Sponsible"))
        assertTrue(text.firstValue.contains("La visite du bien"))
```

and drop the now-wrong `assertTrue(text.firstValue.contains("Petit rappel"))`.

In `a listing without a title is named by its id`, the assertion's capital changes with the sentence:
`assertTrue(text.firstValue.contains("La visite du bien Bien n° 101 avec Ray Sponsible"))`.

- [ ] **Step 2: Run the unit test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: FAIL — the actual text starts with `Bonjour Kim Possible,` and `Petit rappel : `.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            "Petit rappel : la visite du bien ${listingTitle(listing)} avec ${customer.party.fullName()} " +
```

with:

```kotlin
        lines.add("Rappel de la visite avec ${customer.party.fullName()}")
        lines.add("")
        lines.add(
            "La visite du bien ${listingTitle(listing)} avec ${customer.party.fullName()} " +
```

The rest of that sentence (`a lieu ${dayWord(visit, tenant)}, …`) is unchanged. Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: PASS.
Run the job test at the final verification (Task 12), or now with MySQL: `mvn test -Dtest=TelegramVisitReminderJobTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt
git commit -m "feat(visit): open the visit reminder message with its reason" -m "Refs #200"
```

---

### Task 3: Visit cancelled

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt` (`text`, around line 45)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt` (line 83)

**Interfaces:**
- Consumes: `VisitCancelledMailet.LABEL_BY_CUSTOMER` (already imported), `VisitEntity.cancellationReason`, `ListingEntity`, `CustomerEntity.party.fullName()`.
- Produces: the text's first line is `VisitCancelledMailet.LABEL_BY_CUSTOMER.removeSuffix(".")` → `Le client a annulé la visite`.

- [ ] **Step 1: Update the failing test's expected text**

```kotlin
        assertEquals(
            """
            Le client a annulé la visite

            Bien: Nice apartment
            Ville: Yaounde
            Quartier: Bastos
            Date: 02/12/2026 à 10:00
            Client: Ray Sponsible
            Motif: Empêchement
            """.trimIndent(),
            text.firstValue,
        )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest`
Expected: FAIL — the actual text starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(VisitCancelledMailet.LABEL_BY_CUSTOMER)
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
```

with:

```kotlin
        lines.add(VisitCancelledMailet.LABEL_BY_CUSTOMER.removeSuffix("."))
        lines.add("")
        lines.add("Bien: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
```

Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt
git commit -m "feat(visit): open the visit cancellation message with its reason" -m "Refs #200"
```

---

### Task 4: Visit feedback

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt` (`text`, around line 42)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt` (line 73)

**Interfaces:**
- Consumes: `VisitEntity.scheduledAt`, `.requestedAt`, `.interest`, `.feedbackReasonCode`, `.feedbackComment`; `VisitFeedbackLabels.interest`; `TenantFormatter`.
- Produces: the text's first line is `L'avis de ${customer.party.fullName()} sur la visite`.

- [ ] **Step 1: Update the failing test's expected text**

```kotlin
        assertEquals(
            """
            L'avis de Ray Sponsible sur la visite

            Bien: Nice apartment
            Date: 01/12/2026 à 10:00
            Client: Ray Sponsible
            Avis: Pas intéressé
            Raison: trop cher
            Commentaire: Trop cher pour moi
            """.trimIndent(),
            text.firstValue,
        )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest`
Expected: FAIL — the actual text starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} a donné son avis sur la visite.")
        lines.add("")
```

with:

```kotlin
        lines.add("L'avis de ${customer.party.fullName()} sur la visite")
        lines.add("")
```

Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt
git commit -m "feat(visit): open the visit feedback message with its reason" -m "Refs #200"
```

---

### Task 5: Match interest (new test class)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt` (`text`, around line 37)
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt`

**Interfaces:**
- Consumes: `TenantService.findById(id: Long): TenantEntity`; `TenantEntity.partnerCentralUrl`; `TelegramClient.sendMessage`; `MandateEntity.id`, `.tenantId`; `ListingEntity.id`, `.title`.
- Produces: the text's first line is `${customer.party.fullName()} s'intéresse à un logement`. `MatchInterestBotlet` has no unit test today; this task adds one.

- [ ] **Step 1: Write the failing test**

Create `MatchInterestBotletTest.kt` with exactly:

```kotlin
package com.wutsi.ndopify.match.server.bot

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
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchInterestBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = MatchInterestBotlet(tenantService, client)

    private val tenant = TenantEntity(id = 1L, partnerCentralUrl = "https://partner.ndopify.com/")
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val mandate = MandateEntity(id = 5L, tenantId = 1L)
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `the interest opens with the event, not a greeting`() {
        botlet.send(agent, customer, mandate, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Ray Sponsible s'intéresse à un logement

            Bien: Nice apartment

            Ce logement a été ajouté à son mandat de recherche. Proposez-lui la visite depuis son mandat.
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/5")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(agent, customer, mandate, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Bien n° 101"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest`
Expected: FAIL — the first line is `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("${customer.party.fullName()} s'intéresse à un logement.")
        lines.add("")
```

with:

```kotlin
        lines.add("${customer.party.fullName()} s'intéresse à un logement")
        lines.add("")
```

Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt
git commit -m "feat(match): open the match interest message with its reason" -m "Refs #200"
```

---

### Task 6: Match answered

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt` (`text`, around line 45)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt` (lines 96 and 133)

**Interfaces:**
- Consumes: `MatchEntity.status`, `.rejectionReasonCode`, `.rejectionReason`, `.mandate`, `.listing`; `MatchStatus.ACCEPTED`; `MatchAnsweredLabels.rejectionReason`; `MandateMailLabels`.
- Produces: the text's first line is `${customer.party.fullName()} a accepté la proposition` or `${customer.party.fullName()} a refusé la proposition`.

- [ ] **Step 1: Update the failing tests' expected text**

The acceptance test becomes:

```kotlin
        assertEquals(
            """
            Ray Sponsible a accepté la proposition

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
```

The rejection test's substring becomes:

```kotlin
        assertTrue(
            text().contains(
                "Ray Sponsible a refusé la proposition\nMotif: trop cher\nCommentaire: Trop loin du travail.\n",
            ),
        )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest`
Expected: FAIL — the text starts with `Bonjour Kim Possible,` and says `Bonne nouvelle : …`.

- [ ] **Step 3: Implement the new first line and the subject/optional block**

In `text`, replace:

```kotlin
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            (if (accepted) "Bonne nouvelle : " else "") +
                "${customer.party.fullName()} a ${if (accepted) "accepté" else "refusé"} la proposition.",
        )
        MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)
            ?.let { lines.add("Motif: $it") }
        match.rejectionReason?.ifBlank { null }?.let { lines.add("Commentaire: $it") }
        lines.add("")
```

with:

```kotlin
        val lines = mutableListOf<String>()
        lines.add("${customer.party.fullName()} a ${if (accepted) "accepté" else "refusé"} la proposition")
        MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)
            ?.let { lines.add("Motif: $it") }
        match.rejectionReason?.ifBlank { null }?.let { lines.add("Commentaire: $it") }
        lines.add("")
```

The first line touches `Motif:` directly (as the old sentence did), and the blank line separates the block from `Bien:`; an accepted answer therefore keeps one blank line before `Bien:`.

Drop `agent` from the private `text` signature (and from `send`'s call to it); `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt
git commit -m "feat(match): open the match answer message with its reason" -m "Refs #200"
```

---

### Task 7: Mandate offer

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt` (`text`, around line 50)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt` (line 65, and a new test)

**Interfaces:**
- Consumes: `AssignmentEntity.mandate`, `.expiresAt`; `MandateEntity.mandateType`, `.cityId`; `LocationService.findByIdOrNull`; `TenantFormatter`.
- Produces: the text's first line is `Un nouveau mandat vous est proposé à {city}`, or `Un nouveau mandat vous est proposé` when the city is empty.

- [ ] **Step 1: Update the failing test and add the empty-city test**

In the search-mandate test, replace line 65 with:

```kotlin
        assertTrue(text.firstValue.startsWith("Un nouveau mandat vous est proposé à Yaounde"))
        assertTrue(text.firstValue.contains("Un client recherche un bien, et nous vous proposons son mandat en exclusivité."))
```

Add a test for a mandate without a resolvable city:

```kotlin
    @Test
    fun `a mandate without a resolvable city has no dangling place`() {
        val assignment = AssignmentEntity(
            id = 98L,
            mandate = MandateEntity(
                id = 3L, tenantId = 1L, cityId = -1L, listingType = ListingType.RENTAL,
                propertyType = PropertyType.APARTMENT, price = 150000, currencyCode = "XAF",
                pricePeriodicityType = PeriodicityType.MONTHLY, mandateType = MandateType.SEARCH,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("Un nouveau mandat vous est proposé\n"))
        assertFalse(text.firstValue.contains(" à \n"))
    }
```

Add `import kotlin.test.assertFalse` to the test's imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest`
Expected: FAIL — the text starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            if (listing) {
                "Un propriétaire vous propose son mandat en exclusivité à $city."
            } else {
                "Un client recherche un bien à $city, et nous vous proposons son mandat en exclusivité."
            }
        )
```

with:

```kotlin
        lines.add(
            "Un nouveau mandat vous est proposé" + (if (city.isEmpty()) "" else " à $city")
        )
        lines.add("")
        lines.add(
            if (listing) {
                "Un propriétaire vous propose son mandat en exclusivité."
            } else {
                "Un client recherche un bien, et nous vous proposons son mandat en exclusivité."
            }
        )
```

`agent` is now unused by the private `text`: drop it from that signature and from its call in `send`. `send` keeps it (the botlet convention: the recipient is passed even when the text doesn't repeat its name).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt
git commit -m "feat(mandate): open the mandate offer message with its reason" -m "Refs #200"
```

---

### Task 8: Mandate approved

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt` (`text`, around line 42)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt` (lines 78, 122 and 176)

**Interfaces:**
- Consumes: `MandateEntity.mandateType`, `.cityId`; `LocationService.findByIdOrNull`; `MandateMailLabels`; `TenantFormatter`.
- Produces: the text's first line is `Votre mandat à {city} est approuvé`, or `Votre mandat est approuvé` when the city is empty.

- [ ] **Step 1: Update the failing tests' expected texts**

Search-mandate test:

```kotlin
        assertEquals(
            """
            Votre mandat à Yaounde est approuvé

            Votre client a signé le mandat. Vous pouvez commencer.

            Recherche: Location - Appartement
            Ville: Yaounde
            Quartier(s): Bastos, Mvan
            Budget: 1 500 000 XAF / mois
            """.trimIndent(),
            text.firstValue,
        )
```

Listing-mandate test:

```kotlin
        assertEquals(
            """
            Votre mandat à Yaounde est approuvé

            Votre client a signé le mandat. Vous pouvez commencer.

            Bien: Vente - Villa
            Ville: Yaounde
            Prix: 25 000 000 XAF
            """.trimIndent(),
            text.firstValue,
        )
```

No-city test:

```kotlin
        assertEquals(
            """
            Votre mandat est approuvé

            Votre client a signé le mandat. Vous pouvez commencer.

            Recherche: Location
            Budget: 1 500 000 XAF / mois
            """.trimIndent(),
            text.firstValue,
        )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest`
Expected: FAIL — the text starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `text`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add(
            "Votre client a signé le mandat${if (city.isEmpty()) "" else " à $city"}. " +
                "Le mandat est approuvé : vous pouvez commencer."
        )
```

with:

```kotlin
        lines.add("Votre mandat" + (if (city.isEmpty()) "" else " à $city") + " est approuvé")
        lines.add("")
        lines.add("Votre client a signé le mandat. Vous pouvez commencer.")
```

Drop `agent` from the private `text` signature and from its call in `send`; `send` keeps it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt
git commit -m "feat(mandate): open the mandate approval message with its reason" -m "Refs #200"
```

---

### Task 9: Listing published

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt` (`caption`, around line 59)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt` (the `caption` fixture, line 76)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt` (line 77)

**Interfaces:**
- Consumes: `ListingEntity.title`, `.cityId`, `.neighborhoodId`; `CustomerEntity.party.fullName()`; `LocationService.findByIdOrNull`; `TelegramClient.sendPhoto` / `sendMessage`.
- Produces: the caption's first line is `Votre annonce à {city} est publiée`, or `Votre annonce est publiée` when the city is empty.

- [ ] **Step 1: Update the failing tests**

`ListingPublishedBotletTest`'s `caption` fixture becomes:

```kotlin
    private val caption = """
        Votre annonce à Yaounde est publiée

        Le bien de Ray Sponsible est en ligne.

        Annonce: Appartement à louer, 3 chambres, Yaounde (Bastos)
        Ville: Yaounde
        Quartier: Bastos

        Prochaine étape : partagez le lien de l'annonce et organisez les visites.
    """.trimIndent()
```

Add an empty-city test (the fixture's `cityId` is `2370201`; stub the lookup to return null):

```kotlin
    @Test
    fun `a tenant without a resolvable city has no dangling place`() {
        doReturn(null).whenever(locationService).findByIdOrNull(2370201L)

        botlet.send(listing, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.startsWith("Votre annonce est publiée"))
    }
```

`TelegramPublishListingsJobTest` line 77 becomes:

```kotlin
        assertTrue(caption.firstValue.startsWith("Votre annonce à Yaoundé est publiée"))
```

(The SQL fixture's city name is `Yaoundé`, with the accent.)

- [ ] **Step 2: Run the unit test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: FAIL — the caption starts with `Bonjour Kim Possible,`.

- [ ] **Step 3: Implement the new first line**

In `caption`, replace:

```kotlin
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("L'annonce du bien de ${customer.party.fullName()} est désormais publiée.")
```

with:

```kotlin
        val city = locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }
        lines.add("Votre annonce" + (city?.let { " à $it" } ?: "") + " est publiée")
        lines.add("")
        lines.add("Le bien de ${customer.party.fullName()} est en ligne.")
```

Then reuse the computed `city` for the existing `Ville:` line instead of calling `findByIdOrNull` again, and drop `agent` from the private `caption` signature and its call in `send`; `send` keeps it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: PASS.
Run the job test at the final verification (Task 12), or now with MySQL: `mvn test -Dtest=TelegramPublishListingsJobTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt
git commit -m "feat(listing): open the publication message with its reason" -m "Refs #200"
```

---

### Task 10: Co-brokerage interest digest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt` (`messages`, `lead`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt` (lines 60–61, 133, and the two split tests)

**Interfaces:**
- Consumes: `CoBrokerageInterestDigest.agentCount`, `.listings`; `TelegramClient.sendMessageRows`.
- Produces: the first line is `1 agent s'intéresse à votre bien` or `{count} agents s'intéressent à vos biens`; continuation parts start with a listing header. `send(agent, digest, chatId)` keeps its signature; `messages` and `lead` drop `agent`.

- [ ] **Step 1: Update the failing tests**

In `CoBrokerageInterestBotletTest`:

- `one listing - text and one-row button`: replace line 60 with
  `assertTrue(text.startsWith("1 agent s'intéresse à votre bien\n\n"))`, and replace line 61 with
  `assertFalse(text.contains("vos annonces"))`.
- `more than 5 agents - et 1 autre agent`: line 133 becomes
  `assertTrue(text.contains("6 agents s'intéressent à vos biens"))`.
- Add to the two split tests (`over 4096 - split at listing boundaries`, `one listing over 4096 - split by rows, header repeated`), inside the per-message loop:
  `assertFalse(text.startsWith("Bonjour"), "a part starts with the greeting")`.

The helper `send(digest)` keeps `botlet.send(listingAgent, digest, 777L)`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: FAIL — the text starts with `Bonjour John Doe,`.

- [ ] **Step 3: Implement the new first line**

In `CoBrokerageInterestBotlet`:

- `send` keeps `(agent, digest, chatId)` and calls `messages(digest)`; `messages` and `lead` drop the `agent` parameter.
- `messages`' first line of work is `var buffer = StringBuilder(lead(digest))`.
- Replace `lead` with:

```kotlin
    private fun lead(digest: CoBrokerageInterestDigest): String =
        if (digest.agentCount == 1) {
            "1 agent s'intéresse à votre bien\n\n"
        } else {
            "${digest.agentCount} agents s'intéressent à vos biens\n\n"
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt
git commit -m "feat(match): open the co-brokerage interest digest with its reason" -m "Refs #200"
```

---

### Task 11: Co-brokerage visit (four messages)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt` (`sendConfirmed`, `sendReminder`, `sendCancelled`, `sendNoShow`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt` (lines 84, 119, 144 and 187)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt` (lines 58–60)

**Interfaces:**
- Consumes: `TenantFormatter`; `CoBrokerageVisitLabels.cancelledBy` / `.noShowReason` / `.typeLabel`; `ListingEntity.title`.
- Produces: the four first lines of the story's table; `send` still closes every message with `Visite organisée par <A>.`

- [ ] **Step 1: Update the failing tests**

Confirmed:

```kotlin
        assertEquals(
            """
            Visite de votre bien confirmée

            J'ai organisé une visite de Villa Bastos le 02/12/2026 à 10:00 (Sur place).

            Je vous y retrouve avec mon client.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
```

Reminder:

```kotlin
        assertEquals(
            """
            Rappel de la visite de votre bien

            La visite de Villa Bastos a lieu le 02/12/2026 à 10:00 (Sur place).

            Je vous y retrouve avec mon client.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
```

Cancelled:

```kotlin
        assertEquals(
            """
            La visite de votre bien est annulée

            Villa Bastos (Sur place), prévue le 02/12/2026 à 10:00.

            J'ai annulé la visite.

            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
```

No-show: no exact block exists today; add these assertions after the two sends:

```kotlin
        assertTrue(text.allValues[0].startsWith("La visite de votre bien n'a pas eu lieu\n\nVilla Bastos (Sur place), prévue le "))
```

and keep the two label assertions. In the blank-title test, replace `contains("votre bien Bien n° 101 le")` with `contains("une visite de Bien n° 101 le")`.

In `TelegramCoBrokerageVisitEndpointTest`, replace lines 58–60 with:

```kotlin
        assertTrue(text.firstValue.startsWith("Visite de votre bien confirmée"))
        assertTrue(text.firstValue.contains("J'ai organisé une visite de Nice apartment le "))
        assertTrue(text.firstValue.contains(" à 10:00 (Sur place)."))
```

Rename the unit test `confirmed - B is greeted, A signs, one listing button` to `confirmed - B is told why, A signs, one listing button`.

- [ ] **Step 2: Run the unit test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest`
Expected: FAIL — every message starts with `Bonjour John Doe,`.

- [ ] **Step 3: Implement the four first lines**

In `CoBrokerageVisitBotlet`, change the first element of each `listOf(...)` and the sentence below it:

- Confirmed: `"Visite de votre bien confirmée"`; the sentence becomes
  `"J'ai organisé une visite de $title le $slot ($type)."`
- Reminder: `"Rappel de la visite de votre bien"`; the sentence becomes
  `"La visite de $title a lieu le $slot ($type)."`
- Cancelled: `"La visite de votre bien est annulée"`; the sentence becomes
  `"$title ($type), prévue le $slot."`
- No-show: `"La visite de votre bien n'a pas eu lieu"`; the sentence becomes
  `"$title ($type), prévue le $slot."`

The empty strings already in each list keep the blank line after the subject; `onSite` and the two labels are unchanged, as is the closing signature in `send`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest`
Expected: PASS.
Run the endpoint test at the final verification (Task 12), or now with MySQL: `mvn test -Dtest=TelegramCoBrokerageVisitEndpointTest` → PASS after `mvn test -Dtest=TelegramCoBrokerageVisitEndpointTest` (the `TelegramCoBrokerageVisitEndpoint.sql` fixture is unchanged).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt
git commit -m "feat(visit): open the co-brokerage visit message with its reason" -m "Refs #200"
```

---

### Task 12: Full verification

**Files:** none.

- [ ] **Step 1: Run the whole module's checks**

Run: `cd modules/ndopify-server && mvn verify`
Expected: `BUILD SUCCESS` — ktlint clean, all unit and endpoint tests green, JaCoCo coverage gate met.

- [ ] **Step 2: Confirm only the expected files changed**

Run: `git status --short` and `git diff --stat origin/main`
Expected: only the 11 botlets, their tests, `TelegramVisitReminderJobTest`, `TelegramPublishListingsJobTest` and `TelegramCoBrokerageVisitEndpointTest`. No `*Mailet`, notifier, DTO, web-core, `src/main/html/`, Flyway, `pom.xml` nor configuration file.

- [ ] **Step 3: Confirm every notification's first line**

Run: `grep -rn "Bonjour" modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/*/server/bot/`
Expected: no match.

- [ ] **Step 4: Report**

Report the `mvn verify` result and the commits. Do not push: the story continues with `push <story-id>`.
