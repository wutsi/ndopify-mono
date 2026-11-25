# Agent-Voiced Customer E-mails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every customer e-mail about a mandate, a match, a visit or a contract read as a message from the agent — first person, no tenant prefix in the subject, a WhatsApp reply button instead of a lost e-mail reply — without touching a single agent, co-brokerage or platform e-mail.

**Architecture:** `ndopify-server` only. `AgentMailContext` gains the one builder of the pre-filled `wa.me` reply URL; a shared MJML fragment (`whatsapp-reply.mjml`) renders the secondary button and the grey "replies aren't read" box when that URL is in the context; a shared head fragment (`styles.mjml`) carries the mobile rules; `agent-footer.mjml` becomes an `mj-group` signature with a "Envoyé via" tenant line. Each in-scope mailet gets its customer copy a new subject, first-person context and the reply URL, while every agent copy keeps today's bytes (shared templates branch on `agent_recipient` / `agent_full_name`).

**Tech Stack:** Kotlin, Spring Boot, MJML (`jcputney` 1.0.5) + Mustache, Jsoup (test scope), `kotlin.test`, `mockito-kotlin`, Maven. Run from `modules/ndopify-server`.

**Spec:** GitHub issue #322 — the enriched story body is the design artifact (context, decisions settled with the requester, acceptance criteria 1–10, assumptions); it replaces a design doc. The approved prototype is `modules/ndopify-server/src/main/html/mail/visit-confirmed.html` (approved 2026-11-25, five frames: desktop default, video, no photo, no WhatsApp number, 375 px). Its French copy is final: port it verbatim, do not redesign it.

## Global Constraints

- `modules/ndopify-server` only: no `ndopify-dto`, no `ndopify-web-core`, no version change in `pom.xml`, no Partner Central / e-signature / public-site change.
- Only these 11 templates change: `mandate/assigned.mjml`, `mandate/assigned_listing.mjml`, `match/proposed.mjml`, `match/visit_reminder.mjml`, `visit/confirmed.mjml`, `visit/reminder.mjml`, `visit/cancelled.mjml`, `visit/expired.mjml`, `visit/no_show.mjml`, `visit/completed.mjml`, `contract/signature_request.mjml` — plus `fragments/agent-footer.mjml`, the new `fragments/styles.mjml` and `fragments/whatsapp-reply.mjml`, and `mail.css`.
- Every e-mail to an agent, every co-brokerage e-mail and every platform e-mail (OTP, KYC, welcome, Telegram code, offer/approved/interest/answered/requested/feedback/nudge, `*_agent` copies) keeps today's subject, `From:`, template and links. A contract e-mail without an agent (a lease) is a platform e-mail.
- No `Reply-To` header anywhere; `MailService` keeps sending from the tenant address with the agent's name as display name.
- The WhatsApp reply button and its grey box appear **only** when the agent has a WhatsApp number: no number, no button, no box, no empty `href`.
- One primary button at most per e-mail; the WhatsApp reply is a **secondary** button (white, `#1d7edf` border/text), and both buttons go full width at `max-width: 480px` only.
- French copy is final and comes from the prototype's pattern; code, comments, logs and docs in English. Dates/times in copy go through `TenantFormatter`, never a hard-coded pattern.
- Every in-scope e-mail gets an `mj-preview` with its event line.
- Assertions use `kotlin.test` (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`), never `org.junit.jupiter.api.Assertions.*`; mocks use `mockito-kotlin`; tests mirror the main package.
- Coordination with **#321** (already planned on `main`): it links the WhatsApp numbers inside `visit/confirmed.mjml` and `visit/reminder.mjml` and adds `customer_whatsapp_url` to the agent copies. This plan rewrites the customer copies of those two templates: keep the numbers the agent/customer already had as links if #321 has landed, and never drop this plan's reply button. Do not change `visit/confirmed_agent.mjml`, `visit/reminder_agent.mjml`, `visit/requested.mjml`.
- Verification: `cd modules/ndopify-server && mvn verify` (ktlint at `validate`, tests, coverage gate). The mail/template tests are plain unit tests; the endpoint tests need the local MySQL the module already requires.

## Review Focus

1. **A shared template rewritten for the customer reaches the agent.** `visit/cancelled.mjml`, `visit/no_show.mjml` and `visit/expired.mjml` serve both parties: a first-person sentence rendered on the agent copy is a bug. Every rendering test pins the agent branch's text, subject prefix and absence of the reply button.
2. **A shared subject constant keeps the tenant prefix.** `VisitCancelledMailet`, `VisitNoShowMailet` and `VisitExpiredMailet` must split `SUBJECT` into a customer constant (no prefix) and an agent constant (`{{tenant_name}}: …`), or AC7/AC8 break silently.
3. **No WhatsApp number.** The button, the grey box and the footer link disappear; no `wa.me/`, no `WhatsApp null`, no empty `<a>`. Rendering with `agent_whatsapp_url` and `agent_whatsapp_reply_url` absent pins it.
4. **The pre-filled text survives encoding and HTML escaping.** The `href` goes through `UrlEncoder` (space → `+`) then `HtmlEscapeFilter` (`&` → `&amp;`); decoding it back must give the exact French sentence, accent and punctuation included.
5. **The footer stays side by side at 375 px.** `mj-group` wraps the photo and the details columns; `AgentFooterLayoutTest` must assert the group, not direct children, and still assert two columns and no tenant logo.
6. **A contract without an agent keeps the tenant voice**: prefixed subject, "L'équipe {{tenant_name}}" sign-off, no reply button.
7. **`mj-preview` is present and invisible**: it renders as the first hidden `div` of `<body>`, carries the event line, and never shows in the card.

---

### Task 1: The pre-filled WhatsApp reply URL

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContext.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContextTest.kt` (new)

**Interfaces:**
- Consumes: `PhoneNumberUtils.toWhatsappUrl(phone: String, text: String? = null): String` (`platform/util/PhoneNumberUtils.kt`, already URL-encodes the text).
- Produces: `AgentMailContext.replyUrl(agent: AgentEntity, context: String): String?` — `https://wa.me/<digits>?text=Bonjour%2C+je+vous+r%C3%A9ponds+au+sujet+de+<context>`, or null when the agent has no number with at least one digit. Every mailet task below uses it.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentMailContextTest {
    private fun agent(number: String?) = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
        whatsappNumber = number,
    )

    @Test
    fun `reply URL carries the pre-filled context`() {
        val url = AgentMailContext.replyUrl(agent("+237 6 99 00 00 01"), "de votre mandat à Bonapriso, Douala")

        assertEquals("https://wa.me/237699000001?text=", url!!.substringBefore("Bonjour"))
        assertEquals(
            "Bonjour, je vous réponds au sujet de votre mandat à Bonapriso, Douala.",
            URLDecoder.decode(url.substringAfter("?text="), "UTF-8"),
        )
    }

    @Test
    fun `no reply URL without a usable number`() {
        assertNull(AgentMailContext.replyUrl(agent(null), "de votre mandat"))
        assertNull(AgentMailContext.replyUrl(agent(" "), "de votre mandat"))
        assertNull(AgentMailContext.replyUrl(agent("n/a"), "de votre mandat"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentMailContextTest`
Expected: compilation failure — `replyUrl` does not exist.

- [ ] **Step 3: Implement `replyUrl`**

In `AgentMailContext`, next to `from` and `of`, add:

```kotlin
/**
 * The reply button's URL in a customer e-mail's body: the agent's WhatsApp chat, pre-filled with the e-mail's
 * own context (ex: "de votre mandat à Bonapriso, Douala"). Null without a number, so the template renders no button.
 */
fun replyUrl(agent: AgentEntity, context: String): String? =
    agent.whatsappNumber
        ?.takeIf { number -> number.any(Char::isDigit) }
        ?.let { PhoneNumberUtils.toWhatsappUrl(it, "Bonjour, je vous réponds au sujet $context.") }
```

Update the object's KDoc: the no-Reply-To note now says the customer is invited to answer on WhatsApp instead.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentMailContextTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContext.kt \
        src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentMailContextTest.kt
git commit -m "feat(server): build the pre-filled WhatsApp reply URL" -m "Refs #322"
```

### Task 2: The weekday of a subject line

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatter.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatterTest.kt`

**Interfaces:**
- Produces: `TenantFormatter.weekday(date: Date): String` — the tenant's locale and time zone, lowercase (`samedi`; `saturday` for an English tenant). Task 4 uses it in the confirmed subject.

- [ ] **Step 1: Write the failing test**

Add to `TenantFormatterTest`:

```kotlin
@Test
fun `weekday in the first locale and time zone`() {
    // 23:30 UTC on Friday: already Saturday in Douala
    assertEquals("samedi", TenantFormatter(tenant).weekday(date))
    assertEquals("saturday", TenantFormatter(tenant.copy(locales = listOf("en-CM"))).weekday(date))
    assertEquals("friday", TenantFormatter(tenant.copy(timeZone = "America/Toronto")).weekday(date))
}
```

(`date` is the fixture `2026-10-16T23:30:00Z`; 2026-10-17 is a Saturday, 2026-10-16 a Friday in Toronto.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TenantFormatterTest`
Expected: compilation failure — `weekday` does not exist.

- [ ] **Step 3: Implement `weekday`**

```kotlin
/** The weekday in the tenant's first locale and time zone, lowercase, for a subject line ("samedi"). */
fun weekday(date: Date): String =
    date.toInstant().atZone(zone)
        .format(DateTimeFormatter.ofPattern("EEEE", locale))
        .lowercase(locale)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TenantFormatterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatter.kt \
        src/test/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatterTest.kt
git commit -m "feat(server): format a weekday for the e-mail subjects" -m "Refs #322"
```

### Task 3: The agent footer, side by side at every width

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/fragments/agent-footer.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/AgentFooterLayoutTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/AgentSignatureTest.kt` (template list only)

**Interfaces:**
- Produces: a footer with a divider, an `mj-group` of one or two columns (56 px round photo when there is one, name, WhatsApp link, then `Envoyé via {{tenant_name}}`), rendered by every in-scope template that already includes it.

- [ ] **Step 1: Extend the footer tests, and make them fail**

In `AgentFooterLayoutTest`, replace `the agent footer shows the photo next to its details` with:

```kotlin
@Test
fun `the agent footer shows the photo next to its details in a group`() {
    templates.forEach { path ->
        val doc = Jsoup.parse(resolver.resolve(path, context))
        val link = assertNotNull(
            doc.selectFirst("a[href='https://wa.me/237699000001']"),
            "$path: the agent footer is missing",
        )
        val details = link.parents().first { isColumn(it) }
        val photo = assertNotNull(
            doc.selectFirst("img[alt='Photo de Kim Possible']"),
            "$path: the agent photo is missing",
        )
        val photoColumn = photo.parents().first { isColumn(it) }
        val group = details.parents().first { it.classNames().any { name -> name.startsWith("mj-group") } }

        assertEquals(photoColumn.parent(), group, "$path: the photo and the details are not in one mj-group")
        assertEquals(2, group.children().count { isColumn(it) }, "$path: the footer needs 2 columns")
        assertTrue(details.text().contains("Kim Possible"), "$path: the details do not name the agent")
        assertTrue(details.text().contains("WhatsApp"), "$path: the details have no WhatsApp link")
        assertTrue(details.text().contains("Envoyé via Ndopify"), "$path: the details do not carry the tenant line")
        assertFalse(details.text().contains("Votre agent"), "$path: the details still say 'Votre agent'")
        assertEquals(0, doc.select("img[alt='Ndopify']").size, "$path: the footer still shows the tenant logo")
    }
}

@Test
fun `the agent footer without a photo keeps the details full width`() {
    templates.forEach { path ->
        val doc = Jsoup.parse(resolver.resolve(path, context - "agent_photo_url"))
        assertNull(doc.selectFirst("img[alt='Photo de Kim Possible']"), "$path: a photo renders without one")
        val details = assertNotNull(
            doc.selectFirst("a[href='https://wa.me/237699000001']"),
            "$path: the agent footer is missing",
        ).parents().first { isColumn(it) }
        assertTrue(details.text().contains("Envoyé via Ndopify"), "$path: the tenant line is missing")
    }
}
```

Add `"/mail/match/visit_reminder.mjml"` to the `templates` list of both `AgentFooterLayoutTest` and `AgentSignatureTest`. Add `import kotlin.test.assertNull` where missing.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentFooterLayoutTest,AgentSignatureTest'`
Expected: FAIL — no `mj-group`, no "Envoyé via", a photo still renders without one.

- [ ] **Step 3: Rewrite `agent-footer.mjml`**

```mjml
{{#agent_full_name}}
<!-- The agent's signature: the photo and the details stay side by side on a phone (mj-group). -->
<mj-section padding="0">
    <mj-column>
        <mj-divider css-class="divider"/>
    </mj-column>
</mj-section>
<mj-section padding="0 24px 16px 24px">
    <mj-group>
        {{#agent_photo_url}}
        <mj-column width="20%" vertical-align="middle">
            <mj-image align="left" width="56px" height="56px" border-radius="28px" padding="0"
                      src="{{agent_photo_url}}" alt="Photo de {{agent_full_name}}"/>
        </mj-column>
        {{/agent_photo_url}}
        <mj-column width="{{#agent_photo_url}}80%{{/agent_photo_url}}{{^agent_photo_url}}100%{{/agent_photo_url}}"
                   vertical-align="middle">
            <mj-text font-weight="bold" font-size="16px" padding="0">{{agent_full_name}}</mj-text>
            {{#agent_whatsapp_url}}
            <mj-text padding="4px 0 0 0">
                <a href="{{agent_whatsapp_url}}">WhatsApp {{agent_whatsapp}}</a>
            </mj-text>
            {{/agent_whatsapp_url}}
            <mj-text padding="4px 0 0 0" font-size="13px" color="#64748b">Envoyé via {{tenant_name}}</mj-text>
        </mj-column>
    </mj-group>
</mj-section>
{{/agent_full_name}}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentFooterLayoutTest,AgentSignatureTest'`
Expected: PASS. If `mj-group` renders without a `mj-group` class in the DOM, print the section's HTML in the test once and assert the closest common ancestor of the two columns instead — the requirement is the two columns share a group that does not stack; keep the assertion.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/mail/fragments/agent-footer.mjml \
        src/test/kotlin/com/wutsi/ndopify/platform/mail/AgentFooterLayoutTest.kt \
        src/test/kotlin/com/wutsi/ndopify/platform/mail/AgentSignatureTest.kt
git commit -m "feat(server): keep the agent footer side by side on mobile" -m "Refs #322"
```

### Task 4: Visit confirmed — port the approved prototype

**Files:**
- Create: `modules/ndopify-server/src/main/resources/mail/fragments/styles.mjml`
- Create: `modules/ndopify-server/src/main/resources/mail/fragments/whatsapp-reply.mjml`
- Modify: `modules/ndopify-server/src/main/resources/mail/mail.css`
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/confirmed.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl` (Task 1), `TenantFormatter.weekday` (Task 2).
- Produces: `mail/fragments/styles.mjml` (the inline `{{__css__}}` plus the ≤480 px rule for `.nd-btn`) and `mail/fragments/whatsapp-reply.mjml` (secondary button + grey box, rendered when `agent_whatsapp_reply_url` is set) — every later template task includes them at `<mj-head>` and above the sign-off; `VisitConfirmedMailet.SUBJECT_CUSTOMER = "Votre visite de {{weekday}} est confirmée"`; the customer context key `agent_whatsapp_reply_url`.

- [ ] **Step 1: Write the failing tests**

In `VisitConfirmedMailetTest`, keep the captured customer context map in a local `val customerContext`, replace the exact `mapOf(...)` assertion with a comparison that excludes the new key, then add:

```kotlin
val expected = mapOf(/* today's entries, unchanged */)
assertEquals(expected, customerContext - "agent_whatsapp_reply_url")
val replyUrl = customerContext["agent_whatsapp_reply_url"] as String
assertEquals(
    "Bonjour, je vous réponds au sujet de la visite du bien Nice apartment le 02/12/2026 à 10:00.",
    URLDecoder.decode(replyUrl.substringAfter("?text="), "UTF-8"),
)
assertEquals("Votre visite de mercredi est confirmée", customerEmail.subject)
assertEquals("Ndopify: visite confirmée avec Ray Sponsible", agentEmail.subject)
assertNull(agentContext["agent_whatsapp_reply_url"], "the agent copy has no reply button")
```

Add a case with `agent.whatsappNumber = null` asserting `customerContext["agent_whatsapp_reply_url"]` is null.

In `VisitConfirmedTemplateTest`, add:

```kotlin
@Test
fun `the copy speaks as the agent, replies on WhatsApp`() {
    val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite)

    assertFalse(html.contains("Votre agent"))
    assertFalse(html.contains("L'équipe Ndopify"))
    assertTrue(html.contains("Je vous confirme notre visite"))
    assertTrue(html.contains("Me répondre sur WhatsApp"))
    assertTrue(html.contains("Les réponses à cet e-mail ne me parviennent pas"))
    assertTrue(html.contains("https://wa.me/237699000001?text="))
    assertTrue(html.contains("Envoyé via Ndopify"))
}

@Test
fun `the reply button and its box need a WhatsApp number`() {
    val html = resolver.resolve(
        VisitConfirmedMailet.TEMPLATE_CUSTOMER,
        onSite - "agent_whatsapp_reply_url" - "agent_whatsapp_url" - "agent_whatsapp",
    )

    assertFalse(html.contains("Me répondre sur WhatsApp"))
    assertFalse(html.contains("Les réponses à cet e-mail ne me parviennent pas"))
    assertFalse(html.contains("wa.me/"))
}

@Test
fun `the preview carries the event`() {
    val doc = Jsoup.parse(resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite))
    val preview = assertNotNull(doc.selectFirst("div[style*=display:none]"))

    assertEquals("02/12/2026 à 10:00 · Nice apartment", preview.text())
}
```

Add the `agent_whatsapp_reply_url` key to the `onSite` fixture (`https://wa.me/237699000001?text=Bonjour`), and `URLDecoder` / `Jsoup` / `assertNotNull` / `assertFalse` imports as needed. Drop the placeholder assertion line in the first test — it asserts nothing.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitConfirmedTemplateTest'`
Expected: FAIL — old copy, prefixed subject, no reply button, no preview.

- [ ] **Step 3: Create the shared fragments and the CSS**

`mail/fragments/styles.mjml`:

```mjml
<mj-style inline="inline">
    {{__css__}}
</mj-style>
<mj-style>
    @media only screen and (max-width: 480px) {
        .nd-btn a {
            display: block !important;
            width: 100% !important;
            box-sizing: border-box !important;
            text-align: center !important;
        }
        .nd-btn td {
            width: 100% !important;
        }
    }
</mj-style>
```

`mail/fragments/whatsapp-reply.mjml`:

```mjml
{{#agent_whatsapp_reply_url}}
<!-- The reply is the quieter action: a secondary button, never a second primary one. -->
<mj-section padding="0 24px">
    <mj-column>
        <mj-button href="{{agent_whatsapp_reply_url}}" css-class="btn-secondary nd-btn" align="center">
            Me répondre sur WhatsApp
        </mj-button>
        <mj-text css-class="reply-note" align="center">
            Les réponses à cet e-mail ne me parviennent pas&#160;: écrivez-moi sur WhatsApp.
        </mj-text>
    </mj-column>
</mj-section>
{{/agent_whatsapp_reply_url}}
```

In `mail.css`, after `.btn-primary a { … }`:

```css
.btn-secondary td {
    background-color: #ffffff !important;
}

.btn-secondary a {
    display: inline-block !important;
    background-color: #ffffff !important;
    color: #1d7edf !important;
    border: 1px solid #1d7edf !important;
    font-size: 15px !important;
    font-weight: bold !important;
    border-radius: 30px !important;
    padding: 12px 24px !important;
    text-decoration: none !important;
}

.reply-note {
    background-color: #f1f5f9 !important;
    border-radius: 8px !important;
    padding: 14px 16px !important;
    color: #334155 !important;
    font-size: 14px !important;
    line-height: 1.5 !important;
}
```

Add `css-class="btn-primary nd-btn"` to the existing primary `mj-button` of every template this plan touches (only `nd-btn` is new; `btn-primary` stays).

- [ ] **Step 4: Rewrite `visit/confirmed.mjml` from the prototype**

`<mj-head>`: `{{> mail/fragments/styles.mjml}}` then `<mj-preview>{{visit_date}} à {{visit_time}} · {{listing_title}}</mj-preview>`.
`<mj-body>`: the white `mj-wrapper` unchanged; remove the tenant logo image; keep one `mj-divider css-class="divider"` at the top of the card; keep the listing photo block; then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
{{#by_agent}}
<mj-text>Je vous confirme la visite que j'ai organisée&#160;: <strong>{{listing_title}}</strong>,
    <strong>{{visit_date}} à {{visit_time}}</strong>.</mj-text>
{{/by_agent}}
{{^by_agent}}
<mj-text>Je vous confirme notre visite&#160;: <strong>{{listing_title}}</strong>,
    <strong>{{visit_date}} à {{visit_time}}</strong>.</mj-text>
{{/by_agent}}
{{#place}}
<mj-text>Localisation&#160;: <strong>{{place}}</strong>{{#landmark}} — {{landmark}}{{/landmark}}</mj-text>
{{/place}}
{{#on_site}}
<mj-text>Je vous attends sur place. Votre code de visite est <strong>{{code}}</strong>&#160;: présentez-le-moi à
    votre arrivée.</mj-text>
<mj-text>Je vous communiquerai l'adresse exacte et l'itinéraire avant la visite.</mj-text>
{{/on_site}}
{{^on_site}}
<mj-text>Je vous appellerai en vidéo sur WhatsApp depuis le bien, à l'heure convenue.</mj-text>
<mj-text>Votre code de visite est <strong>{{code}}</strong>&#160;: lisez-le-moi pendant l'appel.</mj-text>
{{/on_site}}
{{#by_agent}}
<mj-text>Vous pouvez annuler la visite à tout moment depuis votre page de visite.</mj-text>
{{/by_agent}}
{{#visit_url}}
<mj-button href="{{visit_url}}" css-class="btn-primary nd-btn" align="center">Voir ma visite</mj-button>
{{/visit_url}}
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

No *"Votre agent"*, no *"Je vous attends… joindre sur WhatsApp au …"*, no *"L'équipe"* fallback, no number in the body.

- [ ] **Step 5: Update `VisitConfirmedMailet`**

```kotlin
const val SUBJECT_CUSTOMER = "Votre visite de {{weekday}} est confirmée"
// SUBJECT_AGENT unchanged
```

Build `val event = "la visite du bien ${listingTitle(listing)} le ${formatter.date(visit.scheduledAt!!)} à ${formatter.time(visit.scheduledAt)}"` and add `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, event)` to the customer context. Replace the customer subject with `SUBJECT_CUSTOMER.replace("{{weekday}}", formatter.weekday(visit.scheduledAt!!))`. The agent copy is untouched.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitConfirmedTemplateTest,AgentFooterLayoutTest,AgentSignatureTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/mail/fragments/styles.mjml \
        src/main/resources/mail/fragments/whatsapp-reply.mjml \
        src/main/resources/mail/mail.css \
        src/main/resources/mail/visit/confirmed.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedTemplateTest.kt
git commit -m "feat(server): write the confirmed visit e-mail as the agent" -m "Refs #322"
```

### Task 5: Visit reminder

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/reminder.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `VisitReminderMailet.SUBJECT_CUSTOMER = "Votre visite, c'est demain"`; `agent_whatsapp_reply_url` on `sendCustomer` only.

- [ ] **Step 1: Write the failing tests**

In `VisitReminderMailetTest`: expect `"Votre visite, c'est demain"` for the customer and today's `"Ndopify: rappel de la visite avec Ray Sponsible"` for the agent; assert the customer context's `agent_whatsapp_reply_url` decodes to `"Bonjour, je vous réponds au sujet de la visite du bien Nice apartment le 02/12/2026 à 10:00."`; assert `sendAgent`'s context has no such key.

In `VisitReminderTemplateTest`, add `the reminder speaks as the agent` asserting: no `"Votre agent"`, `"Je vous attends sur place"`, `"Me répondre sur WhatsApp"`, `"Envoyé via Ndopify"`; and `no number, no reply` with the three agent WhatsApp keys removed; and the preview text `"Demain 02/12/2026 à 10:00 · Nice apartment"`. Add the reply URL to the fixture.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitReminderMailetTest,VisitReminderTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the customer copy**

Head: the styles fragment and `<mj-preview>Demain {{visit_date}} à {{visit_time}} · {{listing_title}}</mj-preview>`. Card: keep the divider at the top (no logo), the photo, then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
<mj-text>Petit rappel&#160;: notre visite du bien <strong>{{listing_title}}</strong> a lieu demain,
    <strong>{{visit_date}} à {{visit_time}}</strong>.</mj-text>
{{#place}}
<mj-text>Localisation&#160;: <strong>{{place}}</strong>{{#landmark}} — {{landmark}}{{/landmark}}</mj-text>
{{/place}}
{{#on_site}}
<mj-text>Je vous attends sur place. Votre code de visite est <strong>{{code}}</strong>&#160;: présentez-le-moi à
    votre arrivée.</mj-text>
<mj-text>Je vous communiquerai l'adresse exacte et l'itinéraire avant la visite.</mj-text>
{{/on_site}}
{{^on_site}}
<mj-text>Je vous appellerai en vidéo sur WhatsApp depuis le bien, à l'heure convenue.</mj-text>
<mj-text>Votre code de visite est <strong>{{code}}</strong>&#160;: lisez-le-moi pendant l'appel.</mj-text>
{{/on_site}}
{{#visit_url}}
<mj-button href="{{visit_url}}" css-class="btn-primary nd-btn" align="center">Voir ma visite</mj-button>
{{/visit_url}}
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

- [ ] **Step 4: Update `VisitReminderMailet.sendCustomer`**

`SUBJECT_CUSTOMER = "Votre visite, c'est demain"`; add `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "la visite du bien ${listingTitle(listing)} le ${formatter.date(visit.scheduledAt!!)} à ${formatter.time(visit.scheduledAt)}")`. `sendAgent` unchanged.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitReminderMailetTest,VisitReminderTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/visit/reminder.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderTemplateTest.kt
git commit -m "feat(server): write the visit reminder as the agent" -m "Refs #322"
```

### Task 6: Visit cancelled — the customer copy only

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/cancelled.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `VisitCancelledMailet.SUBJECT_CUSTOMER = "Je dois annuler notre visite"`, `SUBJECT_AGENT = "{{tenant_name}}: la visite a été annulée"`; the customer's context gains `agent_whatsapp_reply_url`; the agent's copy keeps today's bytes.

- [ ] **Step 1: Write the failing tests**

`VisitCancelledMailetTest`: customer subject `"Je dois annuler notre visite"`, agent subject `"Ndopify: la visite a été annulée"`; the customer context's reply URL decodes to `"Bonjour, je vous réponds au sujet de notre visite du bien Nice apartment le 02/12/2026 à 10:00."`; the agent's context has no `agent_whatsapp_reply_url`. Add a case with `visit.scheduledAt = null`: the decoded text is `"… au sujet de notre visite du bien Nice apartment."`.

`VisitCancelledTemplateTest`: customer rendering has `"Je dois annuler notre visite du bien"`, no `"La visite du bien"`, no `"Votre agent"`, the reply button, `"Envoyé via Ndopify"` and the preview `"02/12/2026 à 10:00 · Nice apartment"`; agent rendering (`context - "agent_full_name" - "agent_whatsapp_reply_url"`) still says `"La visite du bien Nice apartment"`, `"Le client a annulé la visite."`, `"Contactez Ray Sponsible"`, `"Cordialement, L'équipe Ndopify"`, and has no reply button.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitCancelledMailetTest,VisitCancelledTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the template's customer branch**

Head: styles fragment + `<mj-preview>{{#visit_date}}{{visit_date}} à {{visit_time}} · {{/visit_date}}{{listing_title}}</mj-preview>`. Keep the divider at the top (no logo), the photo, then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
{{#agent_full_name}}
<mj-text>Je dois annuler notre visite du bien <strong>{{listing_title}}</strong>{{#visit_date}} prévue le
    <strong>{{visit_date}} à {{visit_time}}</strong>{{/visit_date}}.</mj-text>
{{#cancellation_reason}}
<mj-text><b>Motif</b>&#160;: {{cancellation_reason}}</mj-text>
{{/cancellation_reason}}
{{/agent_full_name}}
{{^agent_full_name}}
<mj-text>La visite du bien <strong>{{listing_title}}</strong>
    {{#visit_date}}prévue le {{visit_date}} à {{visit_time}}{{/visit_date}}{{^visit_date}}que vous avez demandée{{/visit_date}}
    a été annulée.</mj-text>
<mj-text>{{cancelled_by_label}}</mj-text>
{{#cancellation_reason}}
<mj-text><b>Motif</b>&#160;: {{cancellation_reason}}</mj-text>
{{/cancellation_reason}}
{{^system_cancelled}}
<mj-text>Contactez {{other_party}} pour convenir d'une autre date.</mj-text>
{{/system_cancelled}}
{{/agent_full_name}}
{{#visit_url}}
<mj-button href="{{visit_url}}" css-class="btn-primary nd-btn" align="center">Voir la visite</mj-button>
{{/visit_url}}
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,{{^agent_full_name}}<br/>L'équipe {{tenant_name}}{{/agent_full_name}}</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

- [ ] **Step 4: Split the subject and add the reply URL**

In `VisitCancelledMailet`: `SUBJECT_CUSTOMER` / `SUBJECT_AGENT`; `send(...)` gains a `subject: String` parameter, `sendToCustomer` passes `SUBJECT_CUSTOMER`, `sendToAgent` passes `SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name)`; `sendToCustomer` adds `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "notre visite du bien ${listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"}" + (visit.scheduledAt?.let { " le ${formatter.date(it)} à ${formatter.time(it)}" } ?: ""))`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitCancelledMailetTest,VisitCancelledTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/visit/cancelled.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledTemplateTest.kt
git commit -m "feat(server): let the agent cancel the visit in their own words" -m "Refs #322"
```

### Task 7: Visit expired

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/expired.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `VisitExpiredMailet.SUBJECT_CUSTOMER = "Je n'ai pas pu confirmer votre visite"`, `SUBJECT_AGENT = "{{tenant_name}}: demande de visite expirée avec {{customer_name}}"`; reply URL on the customer copy only.

- [ ] **Step 1: Write the failing tests**

Mailet: customer subject unprefixed, agent subject unchanged; customer reply URL decodes to `"Bonjour, je vous réponds au sujet de votre demande de visite du bien Nice apartment."`; no reply URL on the agent copy. Template: customer has the reply button, no "Votre agent", the preview `"Demande du 02/12/2026 · Nice apartment"`; agent rendering (`agent_recipient = true`, no agent keys) unchanged and without a reply button.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitExpiredMailetTest,VisitExpiredTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the customer copy**

Head: styles fragment + `<mj-preview>Demande du {{requested_date}} · {{listing_title}}</mj-preview>`. Card: divider at top (no logo), photo, then keep the two sentences as they are (`La demande de visite … a expiré : je n'ai pas pu la confirmer à temps.` / `Vous pouvez demander une nouvelle visite : je l'organiserai pour vous.`), the button `Demander une autre visite` with `css-class="btn-primary nd-btn"`, then `{{> mail/fragments/whatsapp-reply.mjml}}`, the existing sign-off (with the `L'équipe` fallback for the agent branch) and the footer.

- [ ] **Step 4: Split the subject and add the reply URL**

`SUBJECT_CUSTOMER` / `SUBJECT_AGENT`; the customer `Email.subject` uses `SUBJECT_CUSTOMER`; the customer context adds `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "votre demande de visite du bien ${listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"}")`. The agent context and subject are untouched.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitExpiredMailetTest,VisitExpiredTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/visit/expired.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredTemplateTest.kt
git commit -m "feat(server): own the expired visit in the agent's voice" -m "Refs #322"
```

### Task 8: Visit no-show

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/no_show.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `VisitNoShowMailet.SUBJECT_CUSTOMER = "Notre visite n'a pas eu lieu"`, `SUBJECT_AGENT = "{{tenant_name}}: visite manquée avec {{customer_name}}"`; reply URL on the customer copy only.

- [ ] **Step 1: Write the failing tests**

Mailet: customer subject unprefixed; agent subject unchanged; customer reply URL decodes to `"Bonjour, je vous réponds au sujet de notre visite du bien Nice apartment."`; the agent copy (`noShowParty = AGENT`) has no reply URL. Template: customer has the reply button and the preview `"02/12/2026 · Nice apartment"`; the reason labels stay (`"Vous ne vous êtes pas présenté."`, `"Je ne me suis pas présenté."`, `"Le code de visite n'a pas pu être vérifié."`); the agent rendering keeps `"Le client ne s'est pas présenté."` and no button.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitNoShowMailetTest,VisitNoShowTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the customer copy**

Head: styles fragment + `<mj-preview>{{visit_date}} · {{listing_title}}</mj-preview>`. Card: divider at top (no logo), photo, the existing sentences (`La visite du bien … n'a pas eu lieu.` / `{{reason_label}}` / the `agent_recipient` branches), the button `Demander une autre visite` with `css-class="btn-primary nd-btn"`, `{{> mail/fragments/whatsapp-reply.mjml}}`, the existing sign-off (fallback kept for the agent branch) and the footer.

- [ ] **Step 4: Split the subject and add the reply URL**

`SUBJECT_CUSTOMER` / `SUBJECT_AGENT`; the customer copy uses the new constant and adds `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "notre visite du bien ${listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"}")`. The agent copy is untouched.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitNoShowMailetTest,VisitNoShowTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/visit/no_show.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowTemplateTest.kt
git commit -m "feat(server): report the missed visit as the agent" -m "Refs #322"
```

### Task 9: Visit completed

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/completed.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `VisitCompletedMailet.SUBJECT = "Alors, ce bien vous intéresse ?"`; the context gains `agent_whatsapp_reply_url`.

- [ ] **Step 1: Write the failing tests**

Mailet: subject `"Alors, ce bien vous intéresse ?"` and a reply URL decoding to `"Bonjour, je vous réponds au sujet de la visite du bien Nice apartment le 02/12/2026 à 10:00."`. Template: `"Notre visite du bien"`, `"Alors, ce bien vous intéresse ?"`, the reply button, `"Envoyé via Ndopify"`, no `"Votre agent"`, no `"L'équipe Ndopify"`, and the preview `"02/12/2026 · Nice apartment"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitCompletedMailetTest,VisitCompletedTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the template**

Head: styles fragment + `<mj-preview>{{visit_date}} · {{listing_title}}</mj-preview>`. Card: the divider at top (no logo), the photo, then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
<mj-text>Notre visite du bien <strong>{{listing_title}}</strong> du {{visit_date}} à {{visit_time}} est
    terminée.</mj-text>
<mj-text font-weight="bold" font-size="18px">Alors, ce bien vous intéresse&#160;?</mj-text>
<mj-text>Dites-le-moi&#160;: je vous accompagnerai pour la suite. Sinon, dites-le-moi aussi, j'en tiendrai compte
    pour mes prochaines propositions.</mj-text>
{{#visit_url}}
<mj-button href="{{visit_url}}" css-class="btn-primary nd-btn" align="center">Donner mon avis</mj-button>
{{/visit_url}}
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

- [ ] **Step 4: Update the mailet**

`SUBJECT = "Alors, ce bien vous intéresse ?"`; add `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "la visite du bien ${listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"} le ${formatter.date(visit.scheduledAt!!)} à ${formatter.time(visit.scheduledAt)}")`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitCompletedMailetTest,VisitCompletedTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/visit/completed.mjml \
        src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedTemplateTest.kt
git commit -m "feat(server): close the visit in the agent's voice" -m "Refs #322"
```

### Task 10: Mandate assigned — the two templates

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/mandate/assigned.mjml`
- Modify: `modules/ndopify-server/src/main/resources/mail/mandate/assigned_listing.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedTemplateTest.kt` (new, renders `assigned.mjml`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateListingTemplateTest.kt` (renders `assigned_listing.mjml`)

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `MandateAssignedMailet.SUBJECT = "Je prends en charge votre mandat à {{place}}"`; the template context gains `"place"` (the same label the subject uses) and `"agent_whatsapp_reply_url"`.

- [ ] **Step 1: Write the failing tests**

Mailet: the subject becomes `"Je prends en charge votre mandat à Bastos, Yaounde"` (whatever `MandateMailLabels.place` returns for the fixture); the context gains `"place"` with that label and `agent_whatsapp_reply_url` decoding to `"Bonjour, je vous réponds au sujet de votre mandat à <place>."`; the template path choice (LISTING vs other) is unchanged. A new `MandateAssignedTemplateTest` renders `/mail/mandate/assigned.mjml` (copy it from `MandateListingTemplateTest`'s resolver fixture) and, with `MandateListingTemplateTest` for `assigned_listing.mjml`, asserts: `"Je prends en charge"`, `"Je serai votre interlocuteur pour ce mandat"`, the agent's name and biography, no `"votre agent dédié"`, no portrait in the body, the reply button, no `"L'équipe Ndopify"`, and the preview `"Votre mandat à <place>"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateAssignedMailetTest,MandateAssignedTemplateTest,MandateListingTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite both templates**

Head: styles fragment + `<mj-preview>Votre mandat à {{place}}</mj-preview>`. Card: the divider at top (no logo), then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
<mj-text>{{^sale}}Je prends en charge la recherche de votre bien.{{/sale}}{{#sale}}Je prends en charge la vente de
    votre bien.{{/sale}} Je serai votre interlocuteur pour ce mandat.</mj-text>
<mj-text>Je me présente&#160;:</mj-text>
<mj-text font-weight="bold" font-size="18px">{{agent_full_name}}</mj-text>
{{#agent_presentation}}
<mj-text>{{agent_presentation}}</mj-text>
{{/agent_presentation}}
<mj-text font-weight="bold" font-size="18px">Prochaine étape&#160;:</mj-text>
<mj-text font-weight="bold">Je vous contacterai très prochainement par téléphone afin de faire le point,
    {{^sale}}d'affiner vos critères de recherche{{/sale}}{{#sale}}d'organiser une visite de votre bien{{/sale}} et de
    répondre à vos premières questions.</mj-text>
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

`assigned_listing.mjml` uses the same block with `{{^sale}}` = *"la mise en location de votre bien"* and `{{#sale}}` = *"la vente de votre bien"*, and the *"d'organiser une visite de votre bien"* next step; `assigned.mjml` keeps *"la recherche de votre bien"* / *"d'affiner vos critères de recherche"*. The in-body photo disappears (the footer carries it); the biography stays.

- [ ] **Step 4: Update the mailet**

```kotlin
const val SUBJECT = "Je prends en charge votre mandat à {{place}}"
val place = MandateMailLabels.place(mandate, city, locationService)
// subject = SUBJECT.replace("{{place}}", place)
// context gains:
"place" to place,
"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "de votre mandat à $place"),
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateAssignedMailetTest,MandateAssignedTemplateTest,MandateListingTemplateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/mandate/assigned.mjml \
        src/main/resources/mail/mandate/assigned_listing.mjml \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedTemplateTest.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateListingTemplateTest.kt
git commit -m "feat(server): introduce the agent in their own words" -m "Refs #322"
```

### Task 11: Match proposed and match visit reminder

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/match/proposed.mjml`
- Modify: `modules/ndopify-server/src/main/resources/mail/match/visit_reminder.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedTemplateTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `MatchProposedMailet.SUBJECT_ONE = "Je vous propose un bien à {{place}}"`, `SUBJECT_MANY = "Je vous propose {{count}} biens à {{place}}"`; `MatchVisitReminderMailet.SUBJECT = "N'oubliez pas de demander votre visite"`; each customer context gains `agent_whatsapp_reply_url`.

- [ ] **Step 1: Write the failing tests**

`MatchProposedMailetTest`: subjects `"Je vous propose un bien à Bastos, Yaounde"` / `"Je vous propose 2 biens à …"`; the reply URL decodes to `"Bonjour, je vous réponds au sujet des biens que je vous ai proposés."`. `MatchProposedTemplateTest`: preview `"Un bien qui correspond à votre recherche"` (and `"2 biens qui correspondent à votre recherche"`), the reply button, no `"L'équipe Ndopify"`, no `"Votre agent"`, and — with `online` false — `"Écrivez-moi sur WhatsApp"` instead of `"Répondez-moi"`, no `"Répondez-moi"` anywhere. `MatchVisitReminderMailetTest`: subject `"N'oubliez pas de demander votre visite"`, reply URL `"… au sujet de la visite du bien Nice apartment."`. `MatchVisitReminderTemplateTest`: preview `"Nice apartment · Loyer 150 000 FCFA"` (the fixture's values), the reply button, and with `online` false the copy says `"Écrivez-moi sur WhatsApp"`, never `"Répondez-moi"`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposed*Test,MatchVisitReminder*Test'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the two templates**

`match/proposed.mjml`: head = styles fragment + `<mj-preview>{{#count}}{{count}} biens{{/count}}{{^count}}Un bien{{/count}} qui correspond{{#count}}ent{{/count}} à votre recherche</mj-preview>`; remove the logo (divider at top); add `nd-btn` to the `Voir le bien et répondre` button; replace the `{{^online}}` sentence *"Répondez-moi&#160;: j'organiserai la visite avec vous. S'il ne vous convient pas, dites-le-moi aussi, j'en tiendrai compte pour mes prochaines propositions."* with *"Écrivez-moi sur WhatsApp&#160;: j'organiserai la visite avec vous. S'il ne vous convient pas, dites-le-moi aussi, j'en tiendrai compte pour mes prochaines propositions."*; add `{{> mail/fragments/whatsapp-reply.mjml}}` before the sign-off; the sign-off becomes `Cordialement,` (no tenant fallback).

`match/visit_reminder.mjml`: head = styles fragment + `<mj-preview>{{listing_title}} · {{price_label}} {{price}}</mj-preview>`; remove the logo; add `nd-btn` to `Demander une visite`; replace *"Répondez-moi et je vous proposerai un créneau."* with *"Écrivez-moi sur WhatsApp et je vous proposerai un créneau."*; add the reply fragment; the sign-off becomes `Cordialement,`.

- [ ] **Step 4: Update the two mailets**

`MatchProposedMailet`: new subject constants; customer context adds `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "des biens que je vous ai proposés")`. Drop the now-unused `{{agent_name}}` replacement only if no constant uses it (it does not).

`MatchVisitReminderMailet`: `SUBJECT = "N'oubliez pas de demander votre visite"`; context adds `"agent_whatsapp_reply_url" to AgentMailContext.replyUrl(agent, "la visite du bien ${listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"}")`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposed*Test,MatchVisitReminder*Test'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/match/proposed.mjml src/main/resources/mail/match/visit_reminder.mjml \
        src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt \
        src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedTemplateTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderTemplateTest.kt
git commit -m "feat(server): propose the matches as the agent" -m "Refs #322"
```

### Task 12: Contract signature request — only when an agent signed it

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/mail/contract/signature_request.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentMailContext.replyUrl`, the fragments of Task 4.
- Produces: `ContractSignatureMailet.SUBJECT_CUSTOMER = "Votre contrat {{contract_number}} est prêt à être signé"` (used only when `agent != null`), the existing `SUBJECT` kept for the platform copy; `agent_whatsapp_reply_url` in the context only when there is an agent.

- [ ] **Step 1: Write the failing tests**

Mailet: with an agent, subject `"Votre contrat C-123 est prêt à être signé"`, sender display name the agent, and `agent_whatsapp_reply_url` decoding to `"Bonjour, je vous réponds au sujet de votre contrat C-123."`. Without an agent, subject `"Ndopify: Votre contrat C-123 est prêt à être signé"`, no sender, no reply URL. Template: with agent keys, no `"L'équipe Ndopify"`, `"Je vous ai préparé"`, the reply button, the preview `"À signer avant le 02/12/2026"`; without agent keys, the tenant voice (`"Votre … est prêt"`, `"Cordialement, L'équipe Ndopify"`) and no reply button.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='ContractSignature*Test'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the template**

Head: styles fragment + `<mj-preview>À signer avant le {{deadline}}</mj-preview>`. Card: the divider at top (no logo), then:

```mjml
<mj-text>Bonjour <strong>{{recipient_name}}</strong>,</mj-text>
{{#agent_full_name}}
<mj-text>Je vous ai préparé votre <strong>{{contract_type}}</strong> n° <strong>{{contract_number}}</strong>&#160;:
    lisez-le attentivement, puis signez-le électroniquement.</mj-text>
{{/agent_full_name}}
{{^agent_full_name}}
<mj-text>Votre <strong>{{contract_type}}</strong> n° <strong>{{contract_number}}</strong> est prêt. Merci de le lire
    attentivement, puis de le signer électroniquement.</mj-text>
{{/agent_full_name}}
<mj-text font-weight="bold">Vous avez jusqu'au <span class="text-primary">{{deadline}}</span> pour le signer.</mj-text>
{{#sign_url}}
<mj-button href="{{sign_url}}" css-class="btn-primary nd-btn" align="center">Lire et signer le contrat</mj-button>
{{/sign_url}}
{{> mail/fragments/whatsapp-reply.mjml}}
<mj-text>Cordialement,{{^agent_full_name}}<br/>L'équipe {{tenant_name}}{{/agent_full_name}}</mj-text>
{{> mail/fragments/agent-footer.mjml}}
```

- [ ] **Step 4: Update the mailet**

```kotlin
const val SUBJECT = "{{tenant_name}}: Votre contrat {{contract_number}} est prêt à être signé"   // platform copy
const val SUBJECT_CUSTOMER = "Votre contrat {{contract_number}} est prêt à être signé"          // agent copy
// subject = (if (agent != null) SUBJECT_CUSTOMER else SUBJECT).replace(...)
// context += agent?.let {
//     mapOf("agent_whatsapp_reply_url" to AgentMailContext.replyUrl(it, "votre contrat ${contract.number}"))
// } ?: emptyMap()
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='ContractSignature*Test'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/mail/contract/signature_request.mjml \
        src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureTemplateTest.kt
git commit -m "feat(server): sign the contract request with the agent's name" -m "Refs #322"
```

### Task 13: Whole-module walk

**Files:**
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt` (one assertion)

- [ ] **Step 1: Pin the missing `Reply-To`**

In `MailServiceTest.send`, after the `Content-Type` assertion:

```kotlin
assertNull(headers["Reply-To"], "a customer reply must not reach the agent")
```

(`assertNull` already imported? Replace with `assertEquals(null, headers["Reply-To"])` to reuse the file's imports.)

Run: `cd modules/ndopify-server && mvn test -Dtest=MailServiceTest`
Expected: PASS — `MailService` sets no `Reply-To` and this story doesn't add one.

- [ ] **Step 2: Run the module's full gate**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests, coverage). Fix any ktlint finding with `mvn antrun:run@ktlint-format`, then rerun.

- [ ] **Step 3: Walk the copy against the approved prototype**

Open `modules/ndopify-server/src/main/html/mail/visit-confirmed.html` and `src/main/resources/mail/visit/confirmed.mjml` side by side. Check: the same sentences, the divider instead of the logo, one primary button, the secondary WhatsApp button, the grey box, `Envoyé via Ndopify`, and the footer photo/name side by side. The other ten templates reuse that pattern; every one has its `mj-preview`.

- [ ] **Step 4: Check the untouched e-mails**

Run: `git diff origin/main --stat -- src/main/resources/mail src/main/kotlin` and confirm only the eleven in-scope templates, the three fragments/`mail.css`, their mailets and tests changed. Then confirm the agent copies' subjects with `grep -rn "SUBJECT_AGENT" src/main/kotlin/com/wutsi/ndopify/{visit,match}/server/mail/` and the platform e-mails' `{{tenant_name}}:` subjects still in place.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt
git commit -m "test(server): pin the absence of Reply-To" -m "Refs #322"
```
