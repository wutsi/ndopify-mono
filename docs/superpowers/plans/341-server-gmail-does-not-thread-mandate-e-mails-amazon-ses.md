# Gmail Threading through Amazon SES Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the mandate's customer e-mails thread in Gmail through Amazon SES by storing the `Message-ID` SES assigns to the conversation's first message (returned in the final SMTP `250` reply) and referencing that id from every later message.

**Architecture:** `ndopify-server` only. `MailService.send` sends through its own SMTP `Transport`, reads the `SMTPTransport`'s last `250` reply and returns the parsed id; `ServerMessageIdParser` accepts only SES-shaped replies. A new `T_MANDATE.conversation_message_id` column stores the first id once (CAS bulk update), `MandateConversationService` uses it as the conversation root (falling back to the pinned `mandate-<tenant>-<id>@<domain>`), and every mailet that opens the conversation records the id right after the first send.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA / Hibernate with `@TenantId` multi-tenancy, JavaMail (Angus Mail, GreenMail in tests), Flyway (MySQL), MJML/Mustache (untouched), `kotlin.test`, `mockito-kotlin`, Maven.

**Spec:** GitHub issue #341 — its enriched story body is the design artifact. It follows #323 (one e-mail conversation per mandate), which is already merged.

## Global Constraints

- `modules/ndopify-server` only: no `ndopify-dto`, no `ndopify-web-core`, no `pom.xml` version bump, no Partner Central / e-signature / public-site change, nothing under `src/main/html`.
- A new Flyway script goes after the latest applied one (`V19_0`): `V19_1`. Never edit an applied script.
- No `@Version` bump when the conversation id is written: use the same bulk-update pattern as `conversation_subject`.
- No hand-written tenant predicate: `@TenantId` filters every query, bulk updates included (see *Multi-Tenancy* in `modules/ndopify-server/CLAUDE.md`).
- The parser never throws: every unrecognised reply or host returns `null`.
- Only the first message's id is stored: every later message references the same root.
- Do not change any subject, template, `From:` or `Reply-To`. This plan adds no user-visible copy.
- Assertions use `kotlin.test`, mocks use `mockito-kotlin`, tests mirror the main package (`<package>/<Class>Test.kt`); no new table, so `clean.sql` is untouched.
- Verification: `cd modules/ndopify-server && mvn verify` (ktlint at `validate`, tests, coverage gate). Endpoint tests need the local MySQL the module already requires.

## Review Focus

1. **An SES reply parsed when it is not one.** Mailpit's `250 2.0.0 Ok: queued as ...` and GreenMail's `250 OK` must return `null`; the host check (only `email-smtp.<region>.amazonaws.com`) and the strict `250 Ok <id>` shape both apply.
2. **The wrong domain for the assigned id.** `us-east-1` gives `email.amazonses.com`; every other region gives `<region>.amazonses.com` (e.g. `eu-west-2.amazonses.com`). A full `id@domain` in the reply is kept as it is.
3. **The capture changed the send.** `MailServiceTest`'s existing header, content and `MailSendException` cases must still pass; the sent date, `saveChanges()` and the restored pinned `Message-ID` are reproduced exactly.
4. **The CAS bumping `@Version`** — an agent editing the mandate would get a 409 for a field it never saw. `MandateRepositoryTest` asserts the version did not move and that another tenant's mandate is not written.
5. **Recorded by the wrong e-mail.** Only the customer copy of the conversation's first message (`inReplyTo == null`) records; the agent copy of a shared mailet never does, and neither does a continuation.
6. **The root erased by a rollback.** `recordRoot` runs in `REQUIRES_NEW`: the message is already in the inbox when the caller rolls back.
7. **Old conversations.** A subject stored before this fix and no captured id falls back to the pinned root; nothing is backfilled.

---

### Task 1: Parse the id out of the SMTP `250` reply

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/ServerMessageIdParser.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/ServerMessageIdParserTest.kt` (new)

**Interfaces:**
- Produces: `ServerMessageIdParser.parse(reply: String?, host: String?): String?` — the assigned `Message-ID` **without** angle brackets, or `null` when the host is not an SES SMTP host or the reply names no id. It never throws.

- [ ] **Step 1: Write the failing tests**

New `ServerMessageIdParserTest`:

```kotlin
package com.wutsi.ndopify.platform.mail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerMessageIdParserTest {
    private val usEast1 = "email-smtp.us-east-1.amazonaws.com"
    private val euWest2 = "email-smtp.eu-west-2.amazonaws.com"

    @Test
    fun `a bare local part gets the us-east-1 domain`() {
        assertEquals(
            "01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000@email.amazonses.com",
            ServerMessageIdParser.parse(
                "250 Ok 01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000",
                usEast1,
            ),
        )
    }

    @Test
    fun `another region gets its own domain`() {
        assertEquals(
            "01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000@eu-west-2.amazonses.com",
            ServerMessageIdParser.parse(
                "250 Ok 01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000",
                euWest2,
            ),
        )
    }

    @Test
    fun `a reply that already names a domain is kept`() {
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok 0101-abc@email.amazonses.com", usEast1),
        )
    }

    @Test
    fun `angle brackets are stripped`() {
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok <0101-abc@email.amazonses.com>", usEast1),
        )
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok <0101-abc>", usEast1),
        )
    }

    @Test
    fun `a relay that names nothing captures nothing`() {
        assertNull(ServerMessageIdParser.parse("250 OK", usEast1))
        assertNull(ServerMessageIdParser.parse("250 2.0.0 Ok: queued as 4Wxyz", usEast1))
        assertNull(ServerMessageIdParser.parse(null, usEast1))
        assertNull(ServerMessageIdParser.parse("garbage", usEast1))
    }

    @Test
    fun `a non-SES host never captures`() {
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", "localhost"))
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", "smtp.example.com"))
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", null))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ServerMessageIdParserTest`
Expected: compilation failure — `ServerMessageIdParser` does not exist.

- [ ] **Step 3: Write the parser**

```kotlin
package com.wutsi.ndopify.platform.mail

/**
 * The `Message-ID` Amazon SES assigns to an accepted message, read from the final `250` reply of the SMTP conversation.
 * SES replaces the `Message-ID` we send with its own, so this id is the only one a mail client can thread against.
 *
 * Only SES is parsed: its SMTP host is `email-smtp.<region>.amazonaws.com`, and its reply is `250 Ok <id>` or
 * `250 Ok <id@domain>`. A relay that preserves our header (Mailpit, GreenMail) or an unrecognised reply yields `null`,
 * and no input throws.
 */
object ServerMessageIdParser {
    private val SES_HOST = Regex("^email-smtp\\.([a-z0-9-]+)\\.amazonaws\\.com$", RegexOption.IGNORE_CASE)
    private val ACCEPTED = Regex("^250[ -]Ok\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
    private val TOKEN = Regex("^<?([A-Za-z0-9._-]+)(?:@([A-Za-z0-9.-]+))?>?$")

    fun parse(reply: String?, host: String?): String? {
        val region = SES_HOST.find(host ?: return null)?.groupValues?.get(1) ?: return null
        val token = ACCEPTED.find(reply?.trim() ?: return null)?.groupValues?.get(1)?.trim() ?: return null
        val match = TOKEN.find(token) ?: return null
        val local = match.groupValues[1]
        val domain = match.groupValues[2].ifBlank {
            if (region.equals("us-east-1", ignoreCase = true)) "email.amazonses.com" else "$region.amazonses.com"
        }
        return "$local@$domain"
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ServerMessageIdParserTest`
Expected: PASS (8 cases).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/platform/mail/ServerMessageIdParser.kt \
        src/test/kotlin/com/wutsi/ndopify/platform/mail/ServerMessageIdParserTest.kt
git commit -m "feat(server): parse the Message-ID of an SES 250 reply" -m "Refs #341"
```

### Task 2: `MailService.send` returns the relay's id

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/MailService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt`

**Interfaces:**
- Produces: `MailService.send(email: Email): String?` — the server-assigned id (without angle brackets) or `null`; every existing caller keeps compiling and ignoring the value. `MailService.fromDomain` is unchanged.

- [ ] **Step 1: Write the failing test**

In `MailServiceTest`, add:

```kotlin
    @Test
    fun `a relay that names no message id captures none`() {
        val sentId = service.send(message.copy(messageId = "mandate-1-1@wutsi.com"))

        assertNull(sentId, "GreenMail answers 250 OK, not an SES id")
        assertEquals("<mandate-1-1@wutsi.com>", smtp.receivedMessages[0].getHeader("Message-ID")?.first())
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailServiceTest`
Expected: compilation failure — `service.send` returns `Unit`, so `val sentId` cannot be assigned.

- [ ] **Step 3: Send through the transport and capture its reply**

Replace `send` and add the two helpers. `JavaMailSenderImpl` exposes its `Session`, and the Angus Mail
`SMTPTransport` exposes `getLastServerResponse()` (`spring-boot-starter-mail` brings Angus Mail):

```kotlin
    /**
     * Sends the message and returns the `Message-ID` the relay assigned, without angle brackets, or null when it named
     * none (Mailpit, GreenMail). SES replaces the `Message-ID` we set, so its reply is the only source of the delivered
     * id. Each send takes its own transport: the last response belongs to this message, whatever runs concurrently.
     */
    fun send(email: Email): String? {
        val sender = mail as JavaMailSenderImpl
        try {
            val msg = createMessage(email)
            val transport = sender.session.getTransport(protocol(sender))
            try {
                transport.connect(
                    sender.host,
                    sender.port,
                    sender.username?.ifEmpty { null },
                    sender.password?.ifEmpty { null },
                )
                prepare(msg)
                transport.sendMessage(msg, msg.allRecipients)
                return ServerMessageIdParser.parse((transport as? SMTPTransport)?.lastServerResponse, sender.host)
            } finally {
                runCatching { transport.close() }
            }
        } catch (ex: MessagingException) {
            throw MailSendException("Mail server connection failed", ex)
        }
    }

    private fun protocol(sender: JavaMailSenderImpl): String =
        sender.protocol ?: sender.session.getProperty("mail.transport.protocol") ?: "smtp"

    // What JavaMailSenderImpl does before handing the message to the transport: a sent date, saveChanges() and the
    // pinned Message-ID restored (Spring's doSend(), which we bypass to keep its transport).
    private fun prepare(msg: MimeMessage) {
        if (msg.sentDate == null) {
            msg.sentDate = Date()
        }
        val messageId = msg.messageID
        msg.saveChanges()
        if (messageId != null) {
            msg.setHeader("Message-ID", messageId)
        }
    }
```

Imports to add: `jakarta.mail.MessagingException`, `org.eclipse.angus.mail.smtp.SMTPTransport`,
`org.springframework.mail.MailSendException`, `java.util.Date`. `Transport`/`Session` stay as they are.

- [ ] **Step 4: Run the whole mail test class**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailServiceTest`
Expected: PASS, including the three existing header cases, the content case and `error` (a stopped GreenMail is caught
as a `MessagingException` and rethrown as `MailSendException`).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/platform/mail/MailService.kt \
        src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt
git commit -m "feat(server): return the relay-assigned Message-ID from MailService" -m "Refs #341"
```

### Task 3: The conversation id on the mandate

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V19_1__mandate-conversation-message-id.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepositoryTest.kt`

**Interfaces:**
- Produces: `MandateEntity.conversationMessageId: String?` (column `conversation_message_id`);
  `MandateRepository.findConversationMessageIdById(id: Long): String?` (reads the database, not a loaded entity);
  `MandateRepository.markConversationMessageId(id: Long, messageId: String): Int` (writes it only when null; skips
  `@Version`; 0 when another e-mail recorded one first).

- [ ] **Step 1: Write the failing tests**

Add to `MandateRepositoryTest`, next to the conversation-subject tests:

```kotlin
    @Test
    fun `the conversation message id is written once`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute {
            dao.markConversationMessageId(1, "0101-abc@email.amazonses.com")
        }
        val again = transactionTemplate.execute {
            dao.markConversationMessageId(1, "0101-def@email.amazonses.com")
        }

        assertEquals(1, written)
        assertEquals(0, again)
        assertEquals(
            "0101-abc@email.amazonses.com",
            transactionTemplate.execute { dao.findConversationMessageIdById(1) },
        )
    }

    @Test
    fun `writing the conversation message id does not bump the version`() {
        TenantContext.set(TENANT_ID)
        val before = transactionTemplate.execute { dao.findById(1).get().version }

        transactionTemplate.execute { dao.markConversationMessageId(1, "0101-abc@email.amazonses.com") }

        assertEquals(before, transactionTemplate.execute { dao.findById(1).get().version })
    }

    @Test
    fun `the conversation message id is never another tenant's`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute {
            dao.markConversationMessageId(9, "0101-abc@email.amazonses.com")
        }

        assertEquals(0, written)
        assertNull(transactionTemplate.execute { dao.findConversationMessageIdById(9) })
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateRepositoryTest`
Expected: compilation failure — the two repository methods and the entity property do not exist (`V19_1` also has to
exist for the schema to boot).

- [ ] **Step 3: Add the column, the field and the queries**

`V19_1__mandate-conversation-message-id.sql`:

```sql
ALTER TABLE T_MANDATE
  ADD COLUMN conversation_message_id VARCHAR(255) NULL AFTER conversation_subject;
```

`MandateEntity`, right after `conversationSubject`:

```kotlin
    /**
     * The Message-ID the relay assigned to the conversation's first message, captured from its 250 reply and kept:
     * every later e-mail references it. Null when the relay named none (Mailpit / GreenMail) or for a conversation
     * opened before it was captured; the pinned root is then the fallback.
     */
    val conversationMessageId: String? = null,
```

`MandateRepository`, next to the conversation-subject queries:

```kotlin
    // Read from the database: a bulk update below is not reflected in an entity already loaded in the session
    @Query("SELECT m.conversationMessageId FROM MandateEntity m WHERE m.id = :id")
    fun findConversationMessageIdById(@Param("id") id: Long): String?

    /**
     * Stores the id the relay assigned to the conversation's first message once. A bulk update on purpose: it skips the
     * `@Version` check, so an agent editing the mandate meanwhile neither fails with a 409 nor loses the edit. 0 when
     * another e-mail recorded one first, which is then the root to use.
     */
    @Transactional
    @Modifying
    @Query(
        "UPDATE MandateEntity m SET m.conversationMessageId = :messageId " +
            "WHERE m.id = :id AND m.conversationMessageId IS NULL",
    )
    fun markConversationMessageId(@Param("id") id: Long, @Param("messageId") messageId: String): Int
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateRepositoryTest`
Expected: PASS. If `the conversation message id is never another tenant's` fails, stop and report: Hibernate must apply
the `@TenantId` filter to the bulk update — do not add a hand-written tenant predicate.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/common/V19_1__mandate-conversation-message-id.sql \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepositoryTest.kt
git commit -m "feat(server): store the id SES assigned a mandate's conversation" -m "Refs #341"
```

### Task 4: The conversation's root and the record hook

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceCommitTest.kt`

**Interfaces:**
- Consumes: `MandateRepository.findConversationMessageIdById` / `markConversationMessageId` (Task 3).
- Produces: `MandateConversationService.recordRoot(mandateId: Long, messageId: String?)` — writes the first message's
  id once, in its own transaction, no-op for `null`; `MandateConversationService.of(mandateId: Long)` now uses the
  stored id as its root when present.

- [ ] **Step 1: Write the failing tests**

In `MandateConversationServiceTest`, add:

```kotlin
    @Test
    fun `a captured SES id is the conversation's root`() {
        doReturn("0101-abc@email.amazonses.com").whenever(dao).findConversationMessageIdById(12L)
        doReturn("Votre recherche · mandat 12").whenever(dao).findConversationSubjectById(12L)

        val conversation = service.of(12L)

        assertEquals("0101-abc@email.amazonses.com", conversation.inReplyTo)
        assertEquals(listOf("0101-abc@email.amazonses.com"), conversation.references)
        assertTrue(conversation.messageId.startsWith("mandate-1-12-"))
    }

    @Test
    fun `a conversation opened before the capture falls back to the pinned root`() {
        doReturn(null).whenever(dao).findConversationMessageIdById(12L)
        doReturn("Votre recherche · mandat 12").whenever(dao).findConversationSubjectById(12L)

        val conversation = service.of(12L)

        assertEquals("mandate-1-12@wutsi.com", conversation.inReplyTo)
        assertEquals(listOf("mandate-1-12@wutsi.com"), conversation.references)
    }

    @Test
    fun `two mandates keep their own captured root`() {
        val other = mandate.copy(id = 13L)
        doReturn(Optional.of(other)).whenever(dao).findById(13L)
        doReturn("0101-abc@email.amazonses.com").whenever(dao).findConversationMessageIdById(12L)
        doReturn("0202-def@email.amazonses.com").whenever(dao).findConversationMessageIdById(13L)
        doReturn("Votre recherche · mandat 12").whenever(dao).findConversationSubjectById(12L)
        doReturn("Votre recherche · mandat 13").whenever(dao).findConversationSubjectById(13L)

        val first = service.of(12L)
        val second = service.of(13L)

        assertEquals(listOf("0101-abc@email.amazonses.com"), first.references)
        assertEquals(listOf("0202-def@email.amazonses.com"), second.references)
    }

    @Test
    fun `the server id is recorded once, and never for a null id`() {
        service.recordRoot(12L, "0101-abc@email.amazonses.com")
        service.recordRoot(12L, null)

        verify(dao).markConversationMessageId(12L, "0101-abc@email.amazonses.com")
        verify(dao, times(1)).markConversationMessageId(any(), any())
    }
```

(`times` is `com.nhaarman.mockitokotlin2.times`; `any`, `never`, `doReturn`, `verify` and `whenever` are already
imported in this test.)

In `MandateConversationServiceCommitTest`, add:

```kotlin
    /**
     * The e-mail is already in the customer's inbox when the id is recorded: the caller's later rollback must not erase
     * the root the next messages will reference.
     */
    @Test
    fun `the captured root is committed independently of the caller`() {
        val outer = TransactionTemplate(transactionManager)
        val other = TransactionTemplate(transactionManager).apply { propagationBehavior = PROPAGATION_REQUIRES_NEW }

        outer.executeWithoutResult {
            service.recordRoot(1L, "0101-abc@email.amazonses.com")

            assertEquals(
                "0101-abc@email.amazonses.com",
                other.execute { dao.findConversationMessageIdById(1L) },
            )
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateConversationServiceTest,MandateConversationServiceCommitTest'`
Expected: compilation failure — `recordRoot` and the repository methods do not exist (Task 3 may already have run).

- [ ] **Step 3: Use the stored root and add `recordRoot`**

In `of`, compute the pinned fallback and the root once:

```kotlin
        val pinned = "mandate-${mandate.tenantId}-${mandate.id}@${mailService.fromDomain}"
        val root = dao.findConversationMessageIdById(mandateId) ?: pinned
        dao.findConversationSubjectById(mandateId)?.let { return continueFrom(it, mandate, root) }
```

and pass `pinned` as the first message's `messageId`:

```kotlin
        return if (dao.markConversationSubject(mandateId, subject) > 0) {
            MandateConversation(subject, pinned, null, emptyList())
        } else {
            // Another e-mail stored a subject between the read and the write: its subject wins
            continueFrom(dao.findConversationSubjectById(mandateId) ?: subject, mandate, root)
        }
```

Add, after `of`:

```kotlin
    /**
     * Stores the id the relay assigned the conversation's first message, once. `null` (a relay that named none) writes
     * nothing. Runs in its own transaction on purpose: the message may already be in the customer's inbox, so the
     * caller's later rollback must not erase the root. A lost race leaves the first recorded id in place (CAS).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordRoot(mandateId: Long, messageId: String?) {
        if (messageId == null) return
        dao.markConversationMessageId(mandateId, messageId)
    }
```

Update the class KDoc: the root is the SES-assigned id when one was captured, the pinned
`mandate-<tenant>-<id>@<domain>` otherwise.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateConversationServiceTest,MandateConversationServiceCommitTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationService.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceTest.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceCommitTest.kt
git commit -m "feat(server): root a mandate's conversation in the id SES assigned" -m "Refs #341"
```

### Task 5: Mandate assigned and the contract signature request

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt`

**Interfaces:**
- Consumes: `MandateConversationService.recordRoot(mandateId: Long, messageId: String?)` (Task 4).
- Produces: both mailets record the id returned by `mailService.send` when the conversation's `inReplyTo` is `null`;
  they never record on a continuation.

- [ ] **Step 1: Write the failing tests**

`MandateAssignedMailetTest`'s `setUp` already stubs a first message (`inReplyTo = null`). Make the mocked send return
an id and add one assertion to `send assigned email to the customer`:

```kotlin
        doReturn("0101-abc@email.amazonses.com").whenever(mailService).send(any())
        // ...
        verify(conversationService).recordRoot(1L, "0101-abc@email.amazonses.com")
```

In `ContractSignatureMailetTest`, add a first-message case (the existing `a mandate's contract joins its conversation`
is a continuation, so add a `verify(conversationService, never()).recordRoot(any(), any())` there) and a new test:

```kotlin
    @Test
    fun `the first mandate e-mail records the relay's id`() {
        doReturn(
            MandateConversation("Votre recherche : appartement à Yaounde · mandat 7", "mandate-1-7@wutsi.com", null, emptyList()),
        ).whenever(conversationService).of(7L)
        doReturn("0101-abc@email.amazonses.com").whenever(mailService).send(any())

        mailet.send(contract, party, mandate = mandate)

        verify(conversationService).recordRoot(7L, "0101-abc@email.amazonses.com")
    }
```

(`any()` and `never()` are `com.nhaarman.mockitokotlin2.any` / `never`; add imports if missing.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateAssignedMailetTest,ContractSignatureMailetTest'`
Expected: FAIL — `recordRoot` is never called.

- [ ] **Step 3: Record the first send**

`MandateAssignedMailet.send`: capture the send's return and record it after the e-mail:

```kotlin
        val sentMessageId = mailService.send(
            Email(
                // unchanged
            ),
        )
        if (conversation.inReplyTo == null) {
            conversationService.recordRoot(mandate.id!!, sentMessageId)
        }
```

`ContractSignatureMailet.send`: with a mandate (and therefore a conversation), the same, after the send:

```kotlin
        val sentMessageId = mailService.send(
            Email(
                // unchanged
            ),
        )
        if (conversation != null && conversation.inReplyTo == null) {
            conversationService.recordRoot(mandate!!.id!!, sentMessageId)
        }
```

The lease path (`conversation == null`) writes nothing.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateAssignedMailetTest,ContractSignatureMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt \
        src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt
git commit -m "feat(server): record the assigned e-mail's server id" -m "Refs #341"
```

### Task 6: Match proposed and match visit reminder

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt`

**Interfaces:**
- Consumes: `MandateConversationService.recordRoot` (Task 4) — the mandate is `matches.first().mandate` /
  `match.mandate`.
- Produces: both mailets record the send's id only when their conversation's `inReplyTo` is `null`.

- [ ] **Step 1: Write the failing tests**

In each test, the `@BeforeTest` stubs a continuation. Add one first-message test per mailet:

```kotlin
    @Test
    fun `the first customer e-mail records the relay's id`() {
        doReturn(
            MandateConversation("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", "mandate-1-1@wutsi.com", null, emptyList()),
        ).whenever(conversationService).of(1L)
        doReturn("0101-abc@email.amazonses.com").whenever(mailService).send(any())

        mailet.send(/* the existing first-message invocation's arguments */)

        verify(conversationService).recordRoot(1L, "0101-abc@email.amazonses.com")
    }
```

and one assertion in the existing continuation test:

```kotlin
        verify(conversationService, never()).recordRoot(any(), any())
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposedMailetTest,MatchVisitReminderMailetTest'`
Expected: FAIL — `recordRoot` is never called.

- [ ] **Step 3: Record the first send**

`MatchProposedMailet.send` and `MatchVisitReminderMailet.send`: capture the `mailService.send(...)` return and, after
it:

```kotlin
        if (conversation.inReplyTo == null) {
            conversationService.recordRoot(mandate.id!!, sentMessageId)
        }
```

`MatchVisitReminderMailet`'s mandate is `match.mandate`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposedMailetTest,MatchVisitReminderMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt \
        src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt
git commit -m "feat(server): record the id of a first match e-mail" -m "Refs #341"
```

### Task 7: The six visit e-mails

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailet.kt`
- Test: the six matching `*MailetTest.kt` in `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/`

**Interfaces:**
- Consumes: `MandateConversationService.recordRoot` — the visit carries `visit.mandateId`.
- Produces: each mailet records the send's id on the customer copy only, and only when `conversation.inReplyTo == null`.
  The agent copy is untouched.

- [ ] **Step 1: Write the failing tests**

In each of the six tests, the customer conversation is a continuation. Add one first-message test per mailet (using
the existing customer-send invocation, as in Task 6) and one continuation assertion:

```kotlin
    @Test
    fun `the first customer e-mail records the relay's id`() {
        doReturn(
            MandateConversation("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", "mandate-1-1@wutsi.com", null, emptyList()),
        ).whenever(conversationService).of(1L)
        doReturn("0101-abc@email.amazonses.com").whenever(mailService).send(any())

        mailet.sendCustomer(/* unchanged arguments */)

        verify(conversationService).recordRoot(1L, "0101-abc@email.amazonses.com")
    }
```

```kotlin
        verify(conversationService, never()).recordRoot(any(), any()) // the continuation test
```

For `VisitCancelledMailet` the customer entry point is `sendToCustomer`; for `VisitReminderMailet` it is
`sendCustomer`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitCancelledMailetTest,VisitExpiredMailetTest,VisitNoShowMailetTest,VisitCompletedMailetTest'`
Expected: FAIL — `recordRoot` is never called.

- [ ] **Step 3: Record the first send on the customer copy**

- `VisitConfirmedMailet`, `VisitExpiredMailet`, `VisitNoShowMailet`, `VisitCompletedMailet`: wrap the customer
  `mailService.send(...)` in `val sentMessageId = ...` and add, after it:

```kotlin
        if (conversation.inReplyTo == null) {
            conversationService.recordRoot(visit.mandateId, sentMessageId)
        }
```

  The agent send, where present, stays untouched.
- `VisitReminderMailet.sendCustomer`: same.
- `VisitCancelledMailet`: in the private `send`, wrap the `mailService.send(...)` call and add:

```kotlin
        if (conversation != null && conversation.inReplyTo == null) {
            conversationService.recordRoot(visit.mandateId, sentMessageId)
        }
```

  `sendToAgent` passes `conversation = null`, so the agent copy writes nothing.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitCancelledMailetTest,VisitExpiredMailetTest,VisitNoShowMailetTest,VisitCompletedMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/visit/server/mail \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail
git commit -m "feat(server): record the id of a first visit e-mail" -m "Refs #341"
```

### Task 8: Pin the fallback and the captured root end to end

**Files:**
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AcceptAssignmentEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ProposeMatchEndpointTest.kt`

**Interfaces:**
- Consumes: everything above, through the real services, the async listeners and GreenMail.
- Produces: the proof that `V19_1` is applied, that GreenMail (which names no id) writes nothing and falls back to the
  pinned root, and that a stored id becomes the root of the next message.

- [ ] **Step 1: Write the failing assertions**

`AcceptAssignmentEndpointTest.accept` already asserts the pinned `Message-ID`, no `In-Reply-To` and the stored subject.
Add, next to the subject assertion:

```kotlin
        assertNull(
            mandateDao.findById(1L).get().conversationMessageId,
            "GreenMail names no id: nothing is captured",
        )
```

`ProposeMatchEndpointTest`: autowire `MandateRepository` as `mandateDao` and add:

```kotlin
    @Test
    fun `a captured SES id is the root of the later e-mails`() {
        rest.postForEntity("/v1/matches/1/propose", null, Any::class.java)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")

        // The first e-mail's 250 reply is what the mailet records; simulate the captured id here.
        mandateDao.markConversationMessageId(1L, "0101-abc@email.amazonses.com")
        rest.postForEntity("/v1/matches/10/propose", null, Any::class.java)

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "no second email received")
        val second = smtp.receivedMessages[1]
        assertEquals("<0101-abc@email.amazonses.com>", second.getHeader("In-Reply-To")?.first())
        assertEquals("<0101-abc@email.amazonses.com>", second.getHeader("References")?.first())
    }
```

- [ ] **Step 2: Run the tests to verify they fail or pass for the wrong reason**

Run: `cd modules/ndopify-server && mvn test -Dtest='AcceptAssignmentEndpointTest,ProposeMatchEndpointTest'`
Expected: compilation failure until `conversationMessageId` and `markConversationMessageId` exist (Tasks 3-4), then PASS.

- [ ] **Step 3: Run the whole module**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS. If a customer-subject expectation in another endpoint/job test fails, it is unrelated to this
plan (subjects are untouched); stop and report it.

- [ ] **Step 4: Confirm the diff's shape**

Run: `cd modules/ndopify-server && git diff --stat origin/main -- src/main src/test`
Expected: `platform/mail` (parser, `MailService`, tests), the mandate domain (migration, entity, repository, conversation
service), the mailets that call the conversation service and their tests — nothing under `src/main/html`, no `pom.xml`,
no other module, no DTO.

- [ ] **Step 5: Confirm the non-conversation e-mails are untouched**

Run: `cd modules/ndopify-server && mvn test -Dtest='MailServiceTest,KycMailetTest,WelcomeMailetTest,OtpMailetTest,VisitRequestedMailetTest,MatchAnsweredMailetTest,CoBrokerageVisitMailetTest'`
Expected: PASS — none of them passes a conversation to `MailService.send`.

- [ ] **Step 6: Commit**

```bash
git add src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AcceptAssignmentEndpointTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ProposeMatchEndpointTest.kt
git commit -m "test(server): pin the SES conversation root end to end" -m "Refs #341"
```

---

The story's last acceptance criterion is not the implementer's: once deployed on TEST, a human sends a mandate's
e-mails through SES and confirms one conversation in Gmail, Outlook and Apple Mail, with each event in the preview.
