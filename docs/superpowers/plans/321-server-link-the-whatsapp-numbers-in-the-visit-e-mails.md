# Link the WhatsApp Numbers in the Visit E-mails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the WhatsApp number printed in the five visit e-mails open the other party's `wa.me` chat in one click, and render as plain text when there is no number.

**Architecture:** `ndopify-server` only. A nullable-aware `PhoneNumberUtils.whatsappUrlOrNull` builds the bare `https://wa.me/<digits>` URL; the three visit mailets put `customer_whatsapp_url` next to `customer_whatsapp` (the customer copies already get `agent_whatsapp_url` from `AgentMailContext`); the five MJML templates wrap the number in `{{#x_whatsapp_url}}<a href=…>{{x_whatsapp}}</a>{{/x_whatsapp_url}}{{^x_whatsapp_url}}{{x_whatsapp}}{{/x_whatsapp_url}}`. `AgentMailContext` and `MatchVisitAgentNudgeMailet` move onto the same helper, so the mail code has one definition of the URL.

**Tech Stack:** Kotlin, Spring Boot, MJML (`jcputney` renderer) + Mustache, `kotlin.test`, `mockito-kotlin`, Jsoup (test scope), Maven. Run from `modules/ndopify-server`.

**Spec:** GitHub issue #321 — the enriched story body is the design artifact (context, decisions settled with the requester, acceptance criteria 1–10, assumptions A1–A5). It replaces a design doc.

## Global Constraints

- `modules/ndopify-server` only: no `ndopify-dto`, no `ndopify-web-core`, no `pom.xml` version change, no prototype (`src/main/html`), no page of Partner Central, the e-signature portal or the public site.
- No new e-mail, no changed recipient, subject, sender or template path. No change to any template other than the five named in Task 3.
- Every `wa.me` URL the server's mail code builds comes from `PhoneNumberUtils`. No `?text=` parameter anywhere (decision: the click opens the chat, the recipient starts the call).
- The number stays visible: the anchor's text is the number alone, and the fallback renders the number as text — never an empty `<a href>`, never a bare `https://wa.me/`.
- The URL is computed from the raw stored `whatsappNumber` (E.164), never from the formatted `x_whatsapp` that carries `+` and spaces (assumption A2).
- **Coordination with #322** (`server: customer e-mails written by the agent, replies on WhatsApp`): it rewrites the customer copies of `visit/confirmed.mjml` and `visit/reminder.mjml`. If it has already landed, locate the agent's number in the new copy and wrap it — do not touch its *"Me répondre sur WhatsApp"* link, and do not "restore" the old paragraph. The line numbers below are from `main` before #322.
- Assertions use `kotlin.test` (`assertEquals`, `assertNull`, `assertTrue`), never `org.junit.jupiter.api.Assertions.*`.
- Commit messages: subject + body `Refs #321`. Branch: `feat/321-server-link-whatsapp-numbers-in-visit-emails`.
- Verification: `cd modules/ndopify-server && mvn verify` (ktlint at `validate`, tests, coverage gate). It needs the local MySQL the module's tests already require; the classes this plan touches are plain unit tests.

## File Structure

| File | Responsibility |
|---|---|
| `src/main/kotlin/com/wutsi/ndopify/platform/util/PhoneNumberUtils.kt` | the one place that builds `wa.me` URLs, nullable-aware |
| `src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContext.kt` | the customer e-mails' footer context — now uses the helper |
| `src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailet.kt` | the agent's visit nudge — now uses the helper |
| `src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt` | the agent's copy gains `customer_whatsapp_url` |
| `src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt` | `sendAgent` gains `customer_whatsapp_url` |
| `src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitRequestedMailet.kt` | gains `customer_whatsapp_url` |
| `src/main/resources/mail/visit/{confirmed,reminder,confirmed_agent,reminder_agent,requested}.mjml` | the five templates that print a number |
| the matching `*MailetTest.kt` / `*TemplateTest.kt` | mailet context and rendered HTML, per template |

## Review Focus

1. **A party with no number gets no link.** The rendered e-mail must contain neither an empty `<a>` nor a `https://wa.me/` with nothing after it; the number's sentence reads as it does today (Task 3, fallback tests).
2. **The number alone is the anchor text.** "je joindre sur WhatsApp au **+237 6 00 00 00 01**." keeps its wording, `<strong>` and final period; only the number is wrapped — not the sentence, and not the footer's "WhatsApp <number>" text (Task 3).
3. **The href is exact.** `https://wa.me/237600000001`: no `+`, no spaces, no `?text=`, no trailing slash (Tasks 1 and 3).
4. **The footer does not move.** For the same agent, `agent_whatsapp_url` is byte-identical before and after the helper adoption — the 16 footer-carrying templates depend on it (Task 4).
5. **A missing key is not a crash.** Rendering a template with the `*_url` key absent from the context entirely (not merely null) prints the number as text, so a mailet that forgets the key degrades instead of breaking (Task 3).

---

### Task 1: `PhoneNumberUtils.whatsappUrlOrNull`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/PhoneNumberUtils.kt:21-28`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/util/PhoneNumberUtilsTest.kt`

**Interfaces:**
- Consumes: `PhoneNumberUtils.toWhatsappUrl(phone: String, text: String? = null): String` (existing).
- Produces: `PhoneNumberUtils.whatsappUrlOrNull(phone: String?): String?` — `https://wa.me/<digits>` or null. Tasks 2, 3 and 4 use it.

- [ ] **Step 1: Write the failing test**

The file imports `org.junit.jupiter.api.Assertions.assertEquals` and `org.junit.jupiter.api.Test`; switch both to `kotlin.test` (the repository convention), then add the new test:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
```

```kotlin
    @Test
    fun `whatsappUrlOrNull`() {
        assertEquals("https://wa.me/237699000001", PhoneNumberUtils.whatsappUrlOrNull("+237 6 99 00 00 01"))
        assertEquals("https://wa.me/237699000001", PhoneNumberUtils.whatsappUrlOrNull("237699000001"))
        assertEquals("https://wa.me/237699000001", PhoneNumberUtils.whatsappUrlOrNull("+237-6-99-00-00-01"))
        assertNull(PhoneNumberUtils.whatsappUrlOrNull(null))
        assertNull(PhoneNumberUtils.whatsappUrlOrNull("   "))
        assertNull(PhoneNumberUtils.whatsappUrlOrNull("N/A"))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=PhoneNumberUtilsTest`
Expected: compilation failure — `unresolved reference: whatsappUrlOrNull`.

- [ ] **Step 3: Implement the helper**

In `PhoneNumberUtils`, below `toWhatsappUrl` (assumption A1: keep the digits, then delegate, so formatted and digits-only inputs give the same URL and a value with no digit gives null):

```kotlin
    /**
     * The `https://wa.me/<digits>` chat link of [phone], or null when there is nothing to open: a null, blank or
     * digit-less value. Only the digits are kept — a country code is never guessed — and no `?text=` is added.
     */
    fun whatsappUrlOrNull(phone: String?): String? =
        phone?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { toWhatsappUrl(it) }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=PhoneNumberUtilsTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/PhoneNumberUtils.kt modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/util/PhoneNumberUtilsTest.kt
git commit -m "feat(server): build a WhatsApp URL from a nullable number" -m "Refs #321"
```

---

### Task 2: The three mailets provide `customer_whatsapp_url`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt:102` (agent copy)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt:107` (`sendAgent`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitRequestedMailet.kt:55`
- Test: `VisitConfirmedMailetTest.kt`, `VisitReminderMailetTest.kt`, `VisitRequestedMailetTest.kt` (same package, `src/test/kotlin/com/wutsi/ndopify/visit/server/mail/`)

**Interfaces:**
- Consumes: `PhoneNumberUtils.whatsappUrlOrNull(phone: String?): String?` (Task 1).
- Produces: each of the three context maps carries `"customer_whatsapp_url" to String?`; the templates of Task 3 read it as `customer_whatsapp_url`.

- [ ] **Step 1: Write the failing tests**

`VisitRequestedMailetTest.send` asserts the whole context map: add `"customer_whatsapp_url" to "https://wa.me/237600000001",` right after `"customer_whatsapp" to "+237 6 00 00 00 01",`. Add the no-number case:

```kotlin
    @Test
    fun `a customer without a WhatsApp number has no link`() {
        mailet.send(visit, customer.copy(whatsappNumber = null), agent, listing)

        assertNull(context()["customer_whatsapp"])
        assertNull(context()["customer_whatsapp_url"])
    }
```

`VisitConfirmedMailetTest` asserts the whole agent map too: add `"customer_whatsapp_url" to "https://wa.me/237600000001",` after `"customer_whatsapp" to "+237 6 00 00 00 01",` in the agent `mapOf`, and add:

```kotlin
    @Test
    fun `an agent copy without the customer number has no WhatsApp link`() {
        mailet.send(visit, customer.copy(whatsappNumber = null), agent, listing)

        val context = agentContext()
        assertNull(context["customer_whatsapp"])
        assertNull(context["customer_whatsapp_url"])
    }
```

`VisitReminderMailetTest` asserts single keys; in `sendAgent sends only the agent copy`, after the existing `customer_whatsapp` assertion:

```kotlin
        assertEquals("https://wa.me/237600000001", agentContext["customer_whatsapp_url"])
```

and add the no-number case, through `sendAgent` and this class's `context(VisitReminderMailet.TEMPLATE_AGENT)`:

```kotlin
    @Test
    fun `an agent copy without the customer number has no WhatsApp link`() {
        mailet.sendAgent(visit, customer.copy(whatsappNumber = null), agent, listing)

        val context = context(VisitReminderMailet.TEMPLATE_AGENT)
        assertNull(context["customer_whatsapp"])
        assertNull(context["customer_whatsapp_url"])
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitRequestedMailetTest'`
Expected: FAIL — `expected:<... customer_whatsapp_url=https://wa.me/237600000001 ...> but was:<... null ...>` (and the map equality fails on the extra key).

- [ ] **Step 3: Add the key in the three mailets**

In each context map, immediately after the existing `"customer_whatsapp" to formatter.phone(customer.whatsappNumber),` line:

```kotlin
                        "customer_whatsapp_url" to PhoneNumberUtils.whatsappUrlOrNull(customer.whatsappNumber),
```

Add `import com.wutsi.ndopify.platform.util.PhoneNumberUtils` to each file (ktlint sorts it after `platform.mail.Receipient`, before the `refdata` imports). Touch only these three lines: the customer copies already get `agent_whatsapp_url` through `AgentMailContext.of`, and the co-brokerage copies carry no customer.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitRequestedMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/ modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/
git commit -m "feat(visit): the agent copies carry the customer's WhatsApp link" -m "Refs #321"
```

---

### Task 3: The five visit templates link the number

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/confirmed.mjml:45-46,57`
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/reminder.mjml:39-40,51`
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/confirmed_agent.mjml:34-35,44-45` (two occurrences of the same line)
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/reminder_agent.mjml:33-34,43-44` (two occurrences of the same line)
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/requested.mjml:41`
- Test: `VisitConfirmedTemplateTest.kt`, `VisitReminderTemplateTest.kt`, `VisitRequestedTemplateTest.kt`

**Interfaces:**
- Consumes: `agent_whatsapp_url` (already in the customer copies' context via `AgentMailContext.of`), `customer_whatsapp_url` (Task 2).
- Produces: nothing later tasks consume.

- [ ] **Step 1: Write the failing tests**

The anchor's text must be distinguishable from the footer's `WhatsApp <number>` link, so select the exact href with Jsoup (already a test dependency, see `AgentSignatureTest`) and assert its text. Add `import org.jsoup.Jsoup` and `import kotlin.test.assertEquals` to each of the three test classes as needed.

`VisitConfirmedTemplateTest` — in `customer - ON_SITE`, after the existing `https://wa.me/237699000001` assertion, pin that the *number itself* is a link (the footer has a second anchor with the same href but the text `WhatsApp <number>`, so match on the text, not on the first anchor):

```kotlin
        assertTrue(
            Jsoup.parse(html).select("""a[href="https://wa.me/237699000001"]""").any { it.text() == "+237699000001" },
        )
```

add `"customer_whatsapp_url" to "https://wa.me/237600000001",` to `agentContext`, and assert in `agent - ON_SITE` and `agent - VIDEO`:

```kotlin
        assertEquals("+237600000001", Jsoup.parse(html).selectFirst("""a[href="https://wa.me/237600000001"]""")?.text())
```

Add the fallbacks:

```kotlin
    @Test
    fun `customer - without the agent WhatsApp URL`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite - "agent_whatsapp_url")

        assertTrue(html.contains("+237699000001"))
        assertTrue(Jsoup.parse(html).select("""a[href="https://wa.me/237699000001"]""").isEmpty())
    }

    @Test
    fun `agent - without the customer WhatsApp URL`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_AGENT, agentContext - "customer_whatsapp_url")

        assertTrue(html.contains("+237600000001"))
        assertTrue(Jsoup.parse(html).select("""a[href^="https://wa.me/"]""").isEmpty())
    }
```

The existing `agent - ON_SITE` assertion `assertFalse(html.contains("https://wa.me/237699000001"))` stays: the agent is not in its own copy.

`VisitReminderTemplateTest` — add `"agent_whatsapp_url" to "https://wa.me/237699000001",` to `onSite`, pin the number-as-link in `customer - ON_SITE` and `customer - VIDEO` with the same `assertTrue(…any { it.text() == "+237699000001" })` form, add the `customer - without the agent WhatsApp URL` fallback (as above, for `TEMPLATE_CUSTOMER`), and cover the agent side that has no template test yet:

```kotlin
    private val agent = onSite + mapOf(
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "customer_whatsapp" to "+237600000001",
        "customer_whatsapp_url" to "https://wa.me/237600000001",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun `agent`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_AGENT, agent)

        assertEquals("+237600000001", Jsoup.parse(html).selectFirst("""a[href="https://wa.me/237600000001"]""")?.text())
        assertTrue(html.contains("Demandez le code de visite"))
    }

    @Test
    fun `agent - without the customer WhatsApp URL`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_AGENT, agent - "customer_whatsapp_url")

        assertTrue(html.contains("+237600000001"))
        assertTrue(Jsoup.parse(html).select("""a[href^="https://wa.me/"]""").isEmpty())
    }
```

`VisitRequestedTemplateTest` — add `"customer_whatsapp_url" to "https://wa.me/237600000001",` to `context` and assert in `render`:

```kotlin
        assertEquals("+237600000001", Jsoup.parse(html).selectFirst("""a[href="https://wa.me/237600000001"]""")?.text())
        assertFalse(html.contains("?text="))
```

(`assertFalse` for the no-prefilled-message rule, acceptance criterion 7; the visit request is the only one of the five whose number is not inside a sentence, so it is also the cheapest place to pin it.)

plus the fallback:

```kotlin
    @Test
    fun `without the customer WhatsApp URL`() {
        val html = resolver.resolve(VisitRequestedMailet.TEMPLATE, context - "customer_whatsapp_url")

        assertTrue(html.contains("+237600000001"))
        assertTrue(Jsoup.parse(html).select("""a[href^="https://wa.me/"]""").isEmpty())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedTemplateTest,VisitReminderTemplateTest,VisitRequestedTemplateTest'`
Expected: FAIL — `expected:<+237699000001> but was:<null>` (no anchor yet), and the fallback tests fail on the second assertion once the anchor exists without a fallback.

- [ ] **Step 3: Wrap the number in the five templates**

The pattern, for the party `x` (`agent` in the customer copies, `customer` in the agent copies), keeping the `<strong>` the number already sits in:

```text
{{#x_whatsapp_url}}<a href="{{x_whatsapp_url}}">{{x_whatsapp}}</a>{{/x_whatsapp_url}}{{^x_whatsapp_url}}{{x_whatsapp}}{{/x_whatsapp_url}}
```

- `confirmed.mjml` (on-site line 46, video line 57) and `reminder.mjml` (on-site line 40, video line 51): replace `<strong>{{agent_whatsapp}}</strong>` with the pattern inside `<strong>` — `<strong>{{#agent_whatsapp_url}}<a href="{{agent_whatsapp_url}}">{{agent_whatsapp}}</a>{{/agent_whatsapp_url}}{{^agent_whatsapp_url}}{{agent_whatsapp}}{{/agent_whatsapp_url}}</strong>`.
- `confirmed_agent.mjml` (lines 35 and 45) and `reminder_agent.mjml` (lines 34 and 44): the same with `customer`, on both occurrences of `<strong>{{customer_whatsapp}}</strong>`.
- `requested.mjml` line 41: `<b>WhatsApp</b>&#160;: {{#customer_whatsapp_url}}<a href="{{customer_whatsapp_url}}">{{customer_whatsapp}}</a>{{/customer_whatsapp_url}}{{^customer_whatsapp_url}}{{customer_whatsapp}}{{/customer_whatsapp_url}}` — the `<li>`'s `{{#customer_whatsapp}}` guard stays.

Every one of these is inside an `<mj-text>`, where raw HTML is kept (as `<strong>` already is); do not add a `style` attribute — `mail.css` has no `a` rule and the footer's link carries none.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedTemplateTest,VisitReminderTemplateTest,VisitRequestedTemplateTest,AgentSignatureTest'`
Expected: PASS. `AgentSignatureTest` renders 16 templates that include the footer: it must stay green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/resources/mail/visit/ modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/
git commit -m "feat(visit): link the WhatsApp numbers of the visit e-mails" -m "Refs #321"
```

---

### Task 4: One builder for the mail code's WhatsApp URLs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContext.kt:19-24`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailet.kt:43,67`
- Test: create `src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContextTest.kt`; `MatchVisitAgentNudgeMailetTest` already pins the value.

**Interfaces:**
- Consumes: `PhoneNumberUtils.whatsappUrlOrNull(phone: String?): String?` (Task 1).
- Produces: nothing new; `agent_whatsapp_url` and `whatsapp_url` keep the exact strings they have today (assumption A3).

- [ ] **Step 1: Write the pinning test**

```kotlin
package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The footer's link, built by the same helper as the visit e-mails' numbers. */
class AgentMailContextTest {
    private val formatter = TenantFormatter(TenantEntity(id = 1L, name = "Ndopify"))
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
        whatsappNumber = "+237 6 99 00 00 01",
    )

    @Test
    fun `whatsapp url`() {
        assertEquals("https://wa.me/237699000001", AgentMailContext.of(agent, formatter)["agent_whatsapp_url"])
    }

    @Test
    fun `no whatsapp number`() {
        assertNull(AgentMailContext.of(agent.copy(whatsappNumber = null), formatter)["agent_whatsapp_url"])
        assertNull(AgentMailContext.of(agent.copy(whatsappNumber = "N/A"), formatter)["agent_whatsapp_url"])
    }
}
```

- [ ] **Step 2: Run the test to verify it passes before the change**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentMailContextTest`
Expected: PASS — it pins today's output. It is the regression net for Step 3; a failure here means the helper's digits differ from `AgentMailContext`'s filter, and Step 3 must not be applied until they agree.

- [ ] **Step 3: Replace the two hand-built URLs**

`AgentMailContext.of`, last entry of the map:

```kotlin
        "agent_whatsapp_url" to PhoneNumberUtils.whatsappUrlOrNull(agent.whatsappNumber),
```

with `import com.wutsi.ndopify.platform.util.PhoneNumberUtils`. `MatchVisitAgentNudgeMailet.send`: delete `val digits = customer.whatsappNumber.orEmpty().filter(Char::isDigit)` (line 43) and replace line 67 with

```kotlin
                        "whatsapp_url" to PhoneNumberUtils.whatsappUrlOrNull(customer.whatsappNumber),
```

plus the same import. Do not touch the three `*Botlet`s: #320 owns them.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentMailContextTest,MatchVisitAgentNudgeMailetTest,MatchVisitAgentNudgeTemplateTest,ContractSignatureMailetTest,AgentSignatureTest'`
Expected: PASS, with the same `https://wa.me/…` strings as before.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContext.kt modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailet.kt modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContextTest.kt
git commit -m "refactor(server): one builder for the mail code's WhatsApp URLs" -m "Refs #321"
```

---

### Task 5: Full build and a rendered-sample check

**Files:** none.

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: the green build the story's acceptance criterion 10 requires.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: `BUILD SUCCESS` — ktlint clean, every test green, JaCoCo line coverage at least the `0.95` the module's `pom.xml` sets. A coverage failure naming `PhoneNumberUtils` means Step 1 of Task 1 is missing its null and digit-less cases.

- [ ] **Step 2: Check the change stayed inside the plan's perimeter**

Run: `git diff --stat main`
Expected: exactly the six production files (helper, `AgentMailContext`, `MatchVisitAgentNudgeMailet`, the three visit mailets), the five `mail/visit/*.mjml` templates and the six test files — no `*Botlet`, no template outside `mail/visit/`, no `pom.xml`, no other module.

- [ ] **Step 3: Report**

Report to the reviewer: the five templates, the three mailets, the helper and the two adoption sites touched; the `mvn verify` output; and that #320's botlets were left untouched.
