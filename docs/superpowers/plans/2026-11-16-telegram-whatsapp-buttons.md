# Telegram WhatsApp buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the WhatsApp numbers sent as plain text in Telegram with `wa.me` URL buttons, and remove the co-brokerage digest's contact info so the agent opens the mandate page.

**Architecture:** Two botlets drop their `WhatsApp: <number>` line and add a `Contacter sur WhatsApp` URL button built by the existing `PhoneNumberUtils.toWhatsappUrl`. The co-brokerage digest botlet drops the contact from each interested-agent row and rewords its closing line; its `Voir le mandat` button already leads to the mandate page, whose proposal card carries the agent's WhatsApp link. No client, DTO or portal change.

**Tech Stack:** Kotlin, Spring Boot, `mockito-kotlin`, `kotlin.test`, Maven (JaCoCo + ktlint).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/182

## Global Constraints

- `modules/ndopify-server` only; no `ndopify-dto`, `ndopify-web-core`, Partner Central nor `src/main/html/` change.
- UI text is French; code, comments, KDoc and docs are English.
- Assertions use `kotlin.test`, never `org.junit.jupiter.api.Assertions.*`.
- `TelegramClient` already sends URL buttons (`TelegramButton(text, url = ...)`): do not change it.
- Button URL format: `PhoneNumberUtils.toWhatsappUrl(number)` → `https://wa.me/<digits only>`.
- Button order in the visit messages' single keyboard row: `Voir la visite` first, `Contacter sur WhatsApp` second.
- Tests live in the mirrored package; endpoint / job tests need a local MySQL (see the root `CLAUDE.md`).

## Review Focus

- A customer without a WhatsApp number: no `WhatsApp` text, no WhatsApp button, and no exception from a null number.
- A tenant without `partnerCentralUrl`: the WhatsApp button still appears (the old code returned an empty keyboard in that case).
- The `wa.me` URL: `+237 6 00 00 00 01` becomes `https://wa.me/237600000001` (no `+`, spaces nor dashes).
- A digest agent with an e-mail but no WhatsApp: the e-mail must not leak into the digest either.
- The digest's split: each message still carries only its own listings' `Voir le mandat` buttons.

---

### Task 1: Visit request message

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt:28-60`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitRequestedEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **visit** bullet)

**Interfaces:**
- Consumes: `PhoneNumberUtils.toWhatsappUrl(phone: String, text: String? = null): String`; `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`.
- Produces: `VisitRequestedBotlet.send`'s keyboard is `[Voir la visite, Contacter sur WhatsApp]`, `[Voir la visite]`, `[Contacter sur WhatsApp]` or empty.

- [ ] **Step 1: Update the failing unit tests**

In `VisitRequestedBotletTest`, change the first test's expected text (remove the `WhatsApp: +237 6 00 00 00 01` line) and keyboard:

```kotlin
assertEquals(
    listOf(
        TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
        TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
    ),
    buttons.firstValue,
)
```

Replace `no whatsapp and a blank note leave no line` with a test that also captures the buttons and asserts `[Voir la visite]`:

```kotlin
@Test
fun `no whatsapp - no number in the text and no whatsapp button`() {
    botlet.send(visit.copy(note = " "), customer.copy(whatsappNumber = null), agent, listing, 1000L)

    val text = argumentCaptor<String>()
    val buttons = argumentCaptor<List<TelegramButton>>()
    verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
    assertFalse(text.firstValue.contains("WhatsApp"))
    assertFalse(text.firstValue.contains("Note:"))
    assertEquals(
        listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
        buttons.firstValue,
    )
}
```

Replace `a tenant without Partner Central gets no button` with:

```kotlin
@Test
fun `a tenant without Partner Central keeps only the WhatsApp button`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    botlet.send(visit, customer, agent, listing, 1000L)

    val buttons = argumentCaptor<List<TelegramButton>>()
    verify(client).sendMessage(eq(1000L), any(), buttons.capture())
    assertEquals(
        listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001")),
        buttons.firstValue,
    )
}

@Test
fun `no whatsapp and no Partner Central - no button`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    botlet.send(visit, customer.copy(whatsappNumber = null), agent, listing, 1000L)

    verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: FAIL — the message still contains `WhatsApp: +237 6 00 00 00 01` and the keyboard has one button.

- [ ] **Step 3: Implement the botlet change**

In `VisitRequestedBotlet.send`, build the keyboard with `listOfNotNull` and pass it:

```kotlin
val url = tenant.partnerCentralUrl?.removeSuffix("/")
client.sendMessage(
    chatId,
    text(visit, customer, agent, listing, formatter),
    listOfNotNull(
        url?.let { TelegramButton("Voir la visite", url = "$it/visits/${visit.id}") },
        customer.whatsappNumber?.takeIf { it.isNotBlank() }?.let {
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(it))
        },
    ),
)
```

Import `com.wutsi.ndopify.platform.util.PhoneNumberUtils`. Remove the `formatter.phone(customer.whatsappNumber)?.let { lines.add("WhatsApp: $it") }` line from `text`. Keep the `formatter` parameter: the date and time still use it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: PASS.

- [ ] **Step 5: Update the endpoint test**

In `TelegramVisitRequestedEndpointTest`, remove `assertTrue(text.firstValue.contains("WhatsApp: +237 6 00 00 00 01"))`, add `assertFalse(text.firstValue.contains("WhatsApp:"))`, and expect both buttons:

```kotlin
assertEquals(
    listOf(
        TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/$id"),
        TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
    ),
    buttons.firstValue,
)
```

- [ ] **Step 6: Run the endpoint test**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitRequestedEndpointTest`
Expected: PASS (MySQL required).

- [ ] **Step 7: Update `modules/ndopify-server/CLAUDE.md`**

In the **visit** bullet, replace `The message carries the customer and its WhatsApp,` with `The message carries the customer's name and, when it has a WhatsApp number, a \`Contacter sur WhatsApp\` button to its \`wa.me\` link (never the number in the text),`.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(visit): link WhatsApp from the Telegram visit request"
```

---

### Task 2: Visit reminder message

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt:18-75`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt`

**Interfaces:**
- Consumes: Task 1's button list shape (`Voir la visite`, then `Contacter sur WhatsApp`) and `PhoneNumberUtils.toWhatsappUrl`.
- Produces: `VisitReminderBotlet.send`'s keyboard follows the same four cases as Task 1.

- [ ] **Step 1: Update the failing unit tests**

In `VisitReminderBotletTest`, remove the `WhatsApp: +237 6 00 00 00 01` line from the on-site and video expected texts, and update the on-site keyboard:

```kotlin
assertEquals(
    listOf(
        TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
        TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
    ),
    buttons.firstValue,
)
```

Replace `no WhatsApp number leaves no line` and `a tenant without Partner Central gets no button` with:

```kotlin
@Test
fun `no whatsapp number - no line and no whatsapp button`() {
    botlet.send(visit, customer.copy(whatsappNumber = null), agent, listing, 1000L)

    val text = argumentCaptor<String>()
    val buttons = argumentCaptor<List<TelegramButton>>()
    verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
    assertFalse(text.firstValue.contains("WhatsApp:"))
    assertEquals(
        listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
        buttons.firstValue,
    )
}

@Test
fun `a tenant without Partner Central keeps only the WhatsApp button`() {
    doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

    botlet.send(visit, customer, agent, listing, 1000L)

    val buttons = argumentCaptor<List<TelegramButton>>()
    verify(client).sendMessage(eq(1000L), any(), buttons.capture())
    assertEquals(
        listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001")),
        buttons.firstValue,
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: FAIL — the number is still in the text and the keyboard lacks the WhatsApp button.

- [ ] **Step 3: Implement the botlet change**

In `VisitReminderBotlet.send`, build the keyboard exactly as in Task 1:

```kotlin
val url = tenant.partnerCentralUrl?.removeSuffix("/")
client.sendMessage(
    chatId,
    text(visit, customer, agent, listing, tenant, formatter),
    listOfNotNull(
        url?.let { TelegramButton("Voir la visite", url = "$it/visits/${visit.id}") },
        customer.whatsappNumber?.takeIf { it.isNotBlank() }?.let {
            TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(it))
        },
    ),
)
```

Import `PhoneNumberUtils`. Remove the `formatter.phone(customer.whatsappNumber)?.let { lines.add("WhatsApp: $it") }` line; keep the `VIDEO` paragraph unchanged. Update the class KDoc line `the customer's WhatsApp number` to `a WhatsApp button to contact the customer`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest`
Expected: PASS.

- [ ] **Step 5: Update the job test**

In `TelegramVisitReminderJobTest`, remove the `WhatsApp:` text assertion, add `assertFalse(text.firstValue.contains("WhatsApp:"))`, and expect both buttons:

```kotlin
assertEquals(
    listOf(
        TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
        TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
    ),
    buttons.firstValue,
)
```

- [ ] **Step 6: Run the job test**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitReminderJobTest`
Expected: PASS (MySQL required).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(visit): link WhatsApp from the Telegram visit reminder"
```

---

### Task 3: Co-brokerage interest digest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt:140-166`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **match** bullet)

**Interfaces:**
- Consumes: `InterestedAgent(fullName, whatsapp, whatsappUrl, email, emailUrl, …)`; `criteria(agent)`.
- Produces: `row(agent)` returns `"• <fullName>\n  <criteria>\n"`, with no contact; `tail(digest)` ends with `Retrouvez les coordonnées de chaque agent dans le mandat.\n\nCordialement,\nL'équipe <tenant>`.

- [ ] **Step 1: Update the failing unit tests**

In `CoBrokerageInterestBotletTest`, replace the WhatsApp row assertion of `one listing - text and one-row button` with:

```kotlin
assertTrue(text.contains("• Kim Possible\n"))
assertFalse(text.contains("WhatsApp"))
assertTrue(
    text.endsWith(
        "Retrouvez les coordonnées de chaque agent dans le mandat.\n\nCordialement,\nL'équipe Ndopify",
    ),
)
```

Add a fixture and a test proving the e-mail fallback is gone too:

```kotlin
private val kimByEmail = kim.copy(
    whatsapp = null,
    whatsappUrl = null,
    email = "kim.possible@example.com",
    emailUrl = "mailto:kim.possible@example.com",
)

@Test
fun `an agent contacted by e-mail leaks no contact either`() {
    send(digest(listOf(listing(agents = listOf(kimByEmail)))))

    val (text, _) = sent().single()
    assertTrue(text.contains("• Kim Possible\n"))
    assertFalse(text.contains("kim.possible@example.com"))
    assertFalse(text.contains("mailto:"))
}
```

Keep `an agent without contact keeps its name and criteria`, but drop the now-useless `kim.copy(whatsapp = null, whatsappUrl = null)` — it passes for every agent now.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: FAIL — the row still contains ` — WhatsApp …`, and the old closing line is still there.

- [ ] **Step 3: Implement the digest change**

Replace `row`'s `when` block with a name and criteria only:

```kotlin
private fun row(agent: InterestedAgent): String =
    "• ${agent.fullName}\n  ${criteria(agent)}\n"
```

Change `tail`:

```kotlin
private fun tail(digest: CoBrokerageInterestDigest): String =
    "Retrouvez les coordonnées de chaque agent dans le mandat.\n\nCordialement,\nL'équipe ${digest.tenant.name}"
```

Keep the class KDoc's description of the digest and its buttons; add `It names the interested agents without their contact: the mandate page's proposal card carries it.` `InterestedAgent` keeps its `whatsapp` / `whatsappUrl` / `email` / `emailUrl` fields for `CoBrokerageInterestMailet`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-server/CLAUDE.md`**

In the **match** bullet, after the digest's button sentence (`one \`Voir le mandat — <title>\` button per listing on its own row`), add `, naming each interested agent without its contact (the mandate page's proposal card carries the agent's WhatsApp link)`. Keep the e-mail's contact unchanged.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): drop contact info from the co-brokerage Telegram digest"
```

---

### Task 4: Verify the module

**Files:**
- None.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo coverage gate pass (MySQL required).

- [ ] **Step 2: Confirm the tree is clean**

Run: `git status --porcelain`
Expected: no output. If the root `pom.xml` shows a local `-SNAPSHOT` edit, revert it (`git checkout -- pom.xml`): this story changes no library.
