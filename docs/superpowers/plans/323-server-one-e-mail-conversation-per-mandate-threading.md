# One E-mail Conversation per Mandate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every customer e-mail about a mandate land in one mail conversation: one fixed subject per mandate, saved on the mandate, and reference headers pointing to the conversation's first message — while every agent, co-brokerage and platform e-mail stays byte-for-byte what it is today.

**Architecture:** `ndopify-server` only. `platform/mail/Email` gains three optional header values; `MailService` writes `<Message-ID>`, `In-Reply-To`, `References` and `Thread-Topic`, and pins the `Message-ID` with a `MimeMessage` subclass because JavaMail regenerates it in `saveChanges()`. A mandate domain service resolves a mandate's conversation (its saved subject plus this message's ids), stores the subject on first use with a bulk update that skips `@Version`, and each in-scope mailet hands its customer copy that subject and those headers. The sentence itself is built once by `MandateMailLabels`, next to the other French copy.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA / Hibernate with `@TenantId` multi-tenancy, JavaMail (GreenMail in tests), Flyway (MySQL), MJML/Mustache (untouched here), `kotlin.test`, `mockito-kotlin`, Maven.

**Spec:** GitHub issue #323 — its enriched story body is the design artifact (decisions settled with the requester on 2026-11-25: the mandate's contract e-mail joins the conversation, the subject wording per mandate type, the mandate number appended to every subject). It replaces a design doc.

## Global Constraints

- `modules/ndopify-server` only: no `ndopify-dto`, no `ndopify-web-core`, no version bump in any `pom.xml`, no Partner Central / e-signature / public-site change, nothing under `src/main/html` (prototypes are never served and never edited here).
- **#322 lands first.** This plan assumes #322's customer copy and its subject constants are in place: it stops *using* those constants (`MandateAssignedMailet.SUBJECT`, `MatchProposedMailet.SUBJECT_ONE` / `SUBJECT_MANY`, `MatchVisitReminderMailet.SUBJECT`, `VisitConfirmedMailet.SUBJECT_CUSTOMER`, `VisitReminderMailet.SUBJECT_CUSTOMER`, `VisitCancelledMailet.SUBJECT` for the customer, `VisitExpiredMailet.SUBJECT_CUSTOMER`, `VisitNoShowMailet.SUBJECT_CUSTOMER`, `VisitCompletedMailet.SUBJECT`, `ContractSignatureMailet.SUBJECT_CUSTOMER`) without touching the templates. If #322 is not merged, stop and report.
- Exactly these customer e-mails change: mandate assigned, match proposed (incl. resend / accepted copy), match visit reminder, visit confirmed / reminder / cancelled-by-agent / expired / no-show / completed, and a mandate's contract signature request. Every agent copy, every co-brokerage e-mail and every platform e-mail (OTP, KYC, welcome, Telegram code, offer, approved, listing, match interest / answered / requested / feedback / nudge) keeps today's subject, `From:`, template, links and gets **no** new header.
- No `Reply-To` header anywhere.
- The subject is the mandate's, **stored once and never rebuilt**: an edit of the mandate's city, neighbourhoods or property type never changes it.
- No hand-written tenant predicate: `@TenantId` filters every query, bulk updates included (see *Multi-Tenancy* in `modules/ndopify-server/CLAUDE.md`).
- French copy is exact (see the tasks); code, comments, logs and docs in English.
- A new Flyway script goes after the latest applied one (`V18_3`): `V18_4`. Never edit an applied script.
- Assertions use `kotlin.test`, mocks use `mockito-kotlin`, tests mirror the main package (`<package>/<Class>Test.kt`); a new table would go in `clean.sql` (none here: one column on an existing table).
- Verification: `cd modules/ndopify-server && mvn verify` (ktlint at `validate`, tests, coverage gate). The endpoint tests need the local MySQL the module already requires.

## Review Focus

1. **A shared mailet reaching the agent with the customer's conversation.** `VisitCancelledMailet`, `VisitNoShowMailet`, `VisitExpiredMailet`, `VisitReminderMailet`, `VisitConfirmedMailet` each send two copies: the agent's keeps its `{{tenant_name}}:` subject and carries no thread header. Every one of their tests pins both copies.
2. **The pinned `Message-ID` silently replaced by JavaMail.** The test reads the header GreenMail received (`smtp.receivedMessages[0].getHeader("Message-ID")`), never the `MimeMessage` built in memory.
3. **The subject rebuilt from current criteria.** A mandate whose city / neighbourhoods / type differ from the first send must still produce the stored subject; `MandateConversationService.of` must never compute again once a subject is stored.
4. **The bulk update bumping `@Version`** — an agent editing the mandate would get a 409 for a field it never saw. `MandateRepositoryTest` asserts the version did not move, and that another tenant's mandate is not written.
5. **Two e-mails racing on the first send.** The loser must neither fail nor take the root: it follows the winner's stored subject and continues the conversation.
6. **The first e-mail not being the assigned one.** A proposal (or any customer e-mail) sent before the assigned one starts the conversation: the root id belongs to the first message actually sent, so later ones always reference a message the inbox has. `MandateConversationServiceTest`'s first case pins the rule (no mailet in it), and Task 8's proposal test pins a continuation.

---

### Task 1: Thread headers in `Email` and `MailService`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/Email.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/MailService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt`

**Interfaces:**
- Produces: `Email.messageId: String?`, `Email.inReplyTo: String?`, `Email.references: List<String>` (ids **without** angle brackets; the defaults leave them empty, so no other caller changes); `MailService.fromDomain: String` — the configured sender's domain (`wutsi.com` with the QA/test profile), so every caller builds a message id without parsing an address itself.

- [ ] **Step 1: Write the failing tests**

In `MailServiceTest`, add (the existing helpers `smtp`, `service`, `message` are reused; `assertNull` is already imported):

```kotlin
@Test
fun `a pinned Message-ID survives the send`() {
    service.send(
        message.copy(
            messageId = "mandate-1-1@wutsi.com",
            inReplyTo = "mandate-1-1@wutsi.com",
            references = listOf("mandate-1-1@wutsi.com"),
        ),
    )

    val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
    assertEquals("<mandate-1-1@wutsi.com>", headers["Message-ID"])
    assertEquals("<mandate-1-1@wutsi.com>", headers["In-Reply-To"])
    assertEquals("<mandate-1-1@wutsi.com>", headers["References"])
    assertEquals("Hello", headers["Thread-Topic"])
    assertEquals("wutsi.com", service.fromDomain)
}

@Test
fun `several references are one space-separated header`() {
    service.send(
        message.copy(messageId = "mandate-1-1-x@wutsi.com", references = listOf("mandate-1-1@wutsi.com", "other@wutsi.com")),
    )

    val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
    assertEquals("<mandate-1-1@wutsi.com> <other@wutsi.com>", headers["References"])
}

@Test
fun `no conversation headers without a message id`() {
    service.send(message)

    val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
    assertNull(headers["In-Reply-To"], "no In-Reply-To on a platform e-mail")
    assertNull(headers["References"], "no References on a platform e-mail")
    assertNull(headers["Thread-Topic"], "no Thread-Topic on a platform e-mail")
    assertFalse(headers["Message-ID"].orEmpty().contains("mandate-"), "JavaMail keeps generating its own Message-ID")
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailServiceTest`
Expected: compilation failure — `messageId`, `fromDomain` do not exist.

- [ ] **Step 3: Add the fields and write the headers**

`Email`, after `attachments`:

```kotlin
    /** The conversation's id for this message, without angle brackets: the header is `<id>`. */
    val messageId: String? = null,
    /** The id of the message this one answers, without angle brackets. */
    val inReplyTo: String? = null,
    /** The conversation's root ids, without angle brackets, in the `References` order. */
    val references: List<String> = emptyList(),
```

`MailService`: add a public property and build the message through a factory. The subclass is required because `saveChanges()` (which every SMTP send runs) calls `updateMessageID()`:

```kotlin
    /** The domain of the configured sender, for the message ids a caller builds (`<mandate-1-1@wutsi.com>`). */
    val fromDomain: String get() = from.substringAfterLast('@').ifBlank { "ndopify.com" }

    /**
     * JavaMail regenerates the Message-ID in `saveChanges()`, which the SMTP send always runs: a conversation's fixed
     * id would be replaced. Overriding `updateMessageID` is the only hook it offers.
     */
    private class PinnedMimeMessage(session: Session, private val id: String) : MimeMessage(session) {
        override fun updateMessageID() {
            setHeader("Message-ID", "<$id>")
        }
    }

    private fun newMessage(email: Email): MimeMessage =
        email.messageId?.let { id ->
            PinnedMimeMessage((mail as JavaMailSenderImpl).session, id).also { it.setHeader("Message-ID", "<$id>") }
        } ?: mail.createMimeMessage()
```

In `createMessage`, replace `val msg = mail.createMimeMessage()` with `val msg = newMessage(message)`, and after the recipients:

```kotlin
        message.inReplyTo?.let { msg.setHeader("In-Reply-To", "<$it>") }
        if (message.references.isNotEmpty()) {
            msg.setHeader("References", message.references.joinToString(" ") { "<$it>" })
        }
        if (message.messageId != null) {
            // Outlook threads by subject: it needs the fixed subject as the conversation topic
            msg.setHeader("Thread-Topic", message.subject)
        }
```

(Cast note for the KDoc: Spring Boot wires a `JavaMailSenderImpl`; `MailConfiguration` is the only place a `MailService` is built.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailServiceTest`
Expected: PASS (all cases, the old ones included).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/platform/mail/Email.kt \
        src/main/kotlin/com/wutsi/ndopify/platform/mail/MailService.kt \
        src/test/kotlin/com/wutsi/ndopify/platform/mail/MailServiceTest.kt
git commit -m "feat(server): set a mail conversation's Thread headers" -m "Refs #323"
```

### Task 2: The conversation subject on the mandate

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V18_4__mandate-conversation-subject.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepositoryTest.kt`

**Interfaces:**
- Produces: `MandateEntity.conversationSubject: String?` (column `conversation_subject`); `MandateRepository.findConversationSubjectById(id: Long): String?` (reads the database, not a loaded entity); `MandateRepository.markConversationSubject(id: Long, subject: String): Int` (writes it only when null; skips `@Version`; 0 when another e-mail already wrote one).

- [ ] **Step 1: Write the failing tests**

Add to `MandateRepositoryTest` (the class already sets `TenantContext`, wraps writes in `transactionTemplate`, and its fixture has mandate 1 in tenant 1 and mandate 9 in tenant 2):

```kotlin
@Test
fun `the conversation subject is written once`() {
    TenantContext.set(TENANT_ID)

    val written = transactionTemplate.execute { dao.markConversationSubject(1, "Votre recherche : appartement à Yaounde · mandat 1") }
    val again = transactionTemplate.execute { dao.markConversationSubject(1, "autre sujet") }

    assertEquals(1, written)
    assertEquals(0, again)
    assertEquals("Votre recherche : appartement à Yaounde · mandat 1", transactionTemplate.execute { dao.findConversationSubjectById(1) })
}

@Test
fun `writing the conversation subject does not bump the version`() {
    TenantContext.set(TENANT_ID)
    val before = transactionTemplate.execute { dao.findById(1).get().version }

    transactionTemplate.execute { dao.markConversationSubject(1, "Votre recherche · mandat 1") }

    assertEquals(before, transactionTemplate.execute { dao.findById(1).get().version })
}

@Test
fun `never another tenant's mandate`() {
    TenantContext.set(TENANT_ID)

    val written = transactionTemplate.execute { dao.markConversationSubject(9, "Votre recherche · mandat 9") }

    assertEquals(0, written)
    assertNull(transactionTemplate.execute { dao.findConversationSubjectById(9) })
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateRepositoryTest`
Expected: compilation failure — the two repository methods do not exist (`V18_4` also has to exist for the schema to boot).

- [ ] **Step 3: Add the column, the field and the queries**

`V18_4__mandate-conversation-subject.sql`:

```sql
ALTER TABLE T_MANDATE
  ADD COLUMN conversation_subject VARCHAR(255) NULL AFTER contract_id;
```

`MandateEntity`, next to `contractId`:

```kotlin
    /** The subject of the customer's e-mail conversation about the mandate, set by the first e-mail sent and kept. */
    val conversationSubject: String? = null,
```

`MandateRepository` (imports as in `MatchRepository`: `Modifying`, `Param`, `Transactional`):

```kotlin
    // Read from the database: a bulk update below is not reflected in an entity already loaded in the session
    @Query("SELECT m.conversationSubject FROM MandateEntity m WHERE m.id = :id")
    fun findConversationSubjectById(@Param("id") id: Long): String?

    /**
     * Stores the mandate's conversation subject once. A bulk update on purpose: it skips the `@Version` check, so an
     * agent editing the mandate meanwhile neither fails with a 409 nor loses the edit. 0 when another e-mail stored a
     * subject first, which is then the one to use.
     */
    @Transactional
    @Modifying
    @Query("UPDATE MandateEntity m SET m.conversationSubject = :subject WHERE m.id = :id AND m.conversationSubject IS NULL")
    fun markConversationSubject(@Param("id") id: Long, @Param("subject") subject: String): Int
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateRepositoryTest`
Expected: PASS. If `never another tenant's mandate` fails, stop and report: Hibernate must apply the `@TenantId` filter to the bulk update — do not add a hand-written tenant predicate.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/common/V18_4__mandate-conversation-subject.sql \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepository.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateRepositoryTest.kt
git commit -m "feat(server): store a mandate's conversation subject" -m "Refs #323"
```

### Task 3: The subject's wording and the conversation service

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceTest.kt` (new)

**Interfaces:**
- Consumes: `MandateRepository.findConversationSubjectById` / `markConversationSubject` (Task 2), `MailService.fromDomain` (Task 1), `LocationService.findByIdOrNull(id: Long): LocationEntity?`, `MandateMailLabels.propertyType(mandate): String?` and `place(mandate, city, locationService): String`.
- Produces: `data class MandateConversation(subject: String, messageId: String, inReplyTo: String?, references: List<String>)` (ids without angle brackets; `inReplyTo` null and `references` empty on the root message); `MandateConversationService.of(mandateId: Long): MandateConversation`; `MandateMailLabels.conversationSubject(mandate: MandateEntity, city: String, locationService: LocationService): String`.

- [ ] **Step 1: Write the failing tests**

New `MandateConversationServiceTest`, mocking the three collaborators:

```kotlin
package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateConversationServiceTest {
    private val dao = mock<MandateRepository>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val service = MandateConversationService(dao, locationService, mailService)

    private val mandate = MandateEntity(
        id = 12L,
        tenantId = 1L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
        propertyType = PropertyType.APARTMENT,
    )

    @BeforeTest
    fun setUp() {
        doReturn(Optional.of(mandate)).whenever(dao).findById(12L)
        doReturn("wutsi.com").whenever(mailService).fromDomain
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
    }

    @Test
    fun `the first e-mail carries the root id and the built subject`() {
        doReturn(null).whenever(dao).findConversationSubjectById(12L)
        doReturn(1).whenever(dao).markConversationSubject(12L, "Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12")

        val conversation = service.of(12L)

        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.messageId)
        assertNull(conversation.inReplyTo)
        assertEquals(emptyList(), conversation.references)
    }

    @Test
    fun `a stored subject is never rebuilt`() {
        doReturn("Votre bien à Bonapriso (Douala) · mandat 12").whenever(dao).findConversationSubjectById(12L)

        // The mandate's criteria changed since the first e-mail: its city, neighbourhoods and type are different now
        val conversation = service.of(12L)

        assertEquals("Votre bien à Bonapriso (Douala) · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.inReplyTo)
        assertEquals(listOf("mandate-1-12@wutsi.com"), conversation.references)
        assertTrue(conversation.messageId.startsWith("mandate-1-12-"))
        assertTrue(conversation.messageId.endsWith("@wutsi.com"))
        verify(dao, never()).markConversationSubject(any(), any())
    }

    @Test
    fun `a lost race follows the stored subject`() {
        doReturn(null, "Votre recherche · mandat 12").whenever(dao).findConversationSubjectById(12L)
        doReturn(0).whenever(dao).markConversationSubject(any(), any())

        val conversation = service.of(12L)

        assertEquals("Votre recherche · mandat 12", conversation.subject)
        assertEquals("mandate-1-12@wutsi.com", conversation.inReplyTo)
    }

    @Test
    fun `a listing mandate names the owner's property`() {
        doReturn(Optional.of(mandate.copy(mandateType = MandateType.LISTING))).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre bien à Bastos, Mvan (Yaounde) · mandat 12")
    }

    @Test
    fun `no property type, no place`() {
        doReturn(
            Optional.of(mandate.copy(propertyType = null, cityId = -1L, neighborhoodIds = emptyList())),
        ).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre recherche · mandat 12")
    }

    @Test
    fun `no property type, the city alone`() {
        doReturn(Optional.of(mandate.copy(propertyType = null, neighborhoodIds = emptyList()))).whenever(dao).findById(12L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L)

        service.of(12L)

        verify(dao).markConversationSubject(12L, "Votre recherche à Yaounde · mandat 12")
    }

    @Test
    fun `two mandates, two conversations`() {
        val other = mandate.copy(id = 13L)
        doReturn(Optional.of(other)).whenever(dao).findById(13L)
        doReturn(null).whenever(dao).findConversationSubjectById(12L, 13L)
        doReturn(1).whenever(dao).markConversationSubject(any(), any())

        val first = service.of(12L)
        val second = service.of(13L)

        assertEquals("mandate-1-12@wutsi.com", first.messageId)
        assertEquals("mandate-1-13@wutsi.com", second.messageId)
        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12", first.subject)
        assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 13", second.subject)
    }
}
```

(`any()` needs `com.nhaarman.mockitokotlin2.any`; `Optional` is `java.util.Optional`.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateConversationServiceTest`
Expected: compilation failure — neither the class nor `conversationSubject` exists.

- [ ] **Step 3: Write the wording**

In `MandateMailLabels`, next to `place`:

```kotlin
    // The subject of the customer's conversation about the mandate, fixed at the first e-mail and stored: event-free,
    // never the tenant's name, and naming the mandate so two mandates in one place never share it.
    // Ex: "Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 12", "Votre bien à Yaounde · mandat 12"
    fun conversationSubject(mandate: MandateEntity, city: String, locationService: LocationService): String {
        val owner = mandate.mandateType == MandateType.LISTING
        val type = if (owner) null else propertyType(mandate)?.lowercase()
        val where = place(mandate, city, locationService).ifBlank { null }
        val subject = (if (owner) "Votre bien" else "Votre recherche") +
            (type?.let { " : $it" } ?: "") +
            (where?.let { " à $it" } ?: "")
        return "$subject · mandat ${mandate.id}"
    }
```

- [ ] **Step 4: Write the service**

```kotlin
package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.springframework.stereotype.Service
import java.util.UUID

/** One customer conversation per mandate: its fixed subject and this message's threading ids (without angle brackets). */
data class MandateConversation(
    val subject: String,
    val messageId: String,
    /** Null on the conversation's first message. */
    val inReplyTo: String?,
    /** Empty on the conversation's first message. */
    val references: List<String>,
)

/**
 * Opens (or continues) the customer's e-mail conversation about a mandate. The first e-mail sent stores the mandate's
 * conversation subject (`MandateEntity.conversationSubject`) and carries the conversation's root id,
 * `mandate-<tenant>-<id>@<domain>`; every later one keeps that subject and answers the root, whatever its event. The
 * subject is never rebuilt once stored: a later edit of the mandate's place or type must not split the conversation.
 */
@Service
class MandateConversationService(
    private val dao: MandateRepository,
    private val locationService: LocationService,
    private val mailService: MailService,
) {
    fun of(mandateId: Long): MandateConversation {
        val mandate = dao.findById(mandateId).orElse(null)
            ?: throw IllegalStateException("Mandate#$mandateId not found")
        val root = "mandate-${mandate.tenantId}-${mandate.id}@${mailService.fromDomain}"
        dao.findConversationSubjectById(mandateId)?.let { return continueFrom(it, mandate, root) }

        val subject = MandateMailLabels.conversationSubject(
            mandate,
            locationService.findByIdOrNull(mandate.cityId)?.name ?: "",
            locationService,
        )
        return if (dao.markConversationSubject(mandateId, subject) > 0) {
            MandateConversation(subject, root, null, emptyList())
        } else {
            // Another e-mail stored a subject between the read and the write: its subject wins
            continueFrom(dao.findConversationSubjectById(mandateId) ?: subject, mandate, root)
        }
    }

    private fun continueFrom(subject: String, mandate: MandateEntity, root: String) = MandateConversation(
        subject = subject,
        messageId = "mandate-${mandate.tenantId}-${mandate.id}-${UUID.randomUUID()}@${mailService.fromDomain}",
        inReplyTo = root,
        references = listOf(root),
    )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateConversationServiceTest,MandateOfferMailetTest,MandateApprovedMailetTest`
Expected: PASS (the two mailet tests prove `MandateMailLabels` still words the Telegram/e-mail labels as before).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt \
        src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationService.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateConversationServiceTest.kt
git commit -m "feat(server): open a mandate's e-mail conversation" -m "Refs #323"
```

### Task 4: Mandate assigned, the conversation's first message

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt`

**Interfaces:**
- Consumes: `MandateConversationService.of(mandateId: Long)` and `MandateConversation` (Task 3).
- Produces: `MandateAssignedMailet(tenantService, locationService, mailService, mailBodyResolver, conversationService)` — the customer `Email` carries `conversation.subject` / `messageId` / `inReplyTo` / `references`; `SUBJECT` and its `{{place}}` replacement go away (the template context is unchanged, `place` never was one).

- [ ] **Step 1: Write the failing tests**

In `MandateAssignedMailetTest`: mock a `MandateConversationService` returning
`MandateConversation("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 1", "mandate-1-1@wutsi.com", null, emptyList())`, pass it to the constructor, then replace the three subject assertions with:

```kotlin
@Test
fun `the assigned e-mail opens the mandate's conversation`() {
    mailet.send(mandate, customer, agent)

    verify(conversationService).of(1L)
    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 1", email.firstValue.subject)
    assertEquals("mandate-1-1@wutsi.com", email.firstValue.messageId)
    assertNull(email.firstValue.inReplyTo)
    assertEquals(emptyList(), email.firstValue.references)
}

@Test
fun `a reassigned mandate joins the conversation`() {
    doReturn(
        MandateConversation(
            "Votre recherche : appartement à Bastos, Mvan (Yaounde) · mandat 1",
            "mandate-1-1-7f0f@wutsi.com",
            "mandate-1-1@wutsi.com",
            listOf("mandate-1-1@wutsi.com"),
        ),
    ).whenever(conversationService).of(1L)

    mailet.send(mandate, customer, agent)

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("mandate-1-1-7f0f@wutsi.com", email.firstValue.messageId)
    assertEquals("mandate-1-1@wutsi.com", email.firstValue.inReplyTo)
    assertEquals(listOf("mandate-1-1@wutsi.com"), email.firstValue.references)
}
```

Delete `no neighbourhoods - the city in the subject`, `unknown city - the neighbourhoods only` and the subject line of `send assigned email to the customer` (the wording is `MandateConversationServiceTest`'s job now); keep every template-context test.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateAssignedMailetTest`
Expected: compilation failure — no `conversationService` parameter.

- [ ] **Step 3: Use the conversation**

Add `private val conversationService: MandateConversationService` as the last constructor parameter; in `send`, after `tenant`:

```kotlin
        val conversation = conversationService.of(mandate.id!!)
```

and in the `Email`: `subject = conversation.subject`, plus `messageId = conversation.messageId`, `inReplyTo = conversation.inReplyTo`, `references = conversation.references`. Remove the `SUBJECT` constant and the `.replace(...)` chain; `MandateMailLabels.place` is no longer needed here (drop the import if unused).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateAssignedMailetTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateAssignedMailetTest.kt
git commit -m "feat(server): introduce the agent in the mandate's conversation" -m "Refs #323"
```

### Task 5: Match proposed and match visit reminder

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt`

**Interfaces:**
- Consumes: `MandateConversationService` (Task 3) — the mandate is `matches.first().mandate` / `match.mandate`.
- Produces: both mailets take `conversationService` as their last constructor parameter and send `conversation.subject` / `messageId` / `inReplyTo` / `references`; `SUBJECT_ONE`, `SUBJECT_MANY` and `SUBJECT` go away.

- [ ] **Step 1: Write the failing tests**

In both tests, inject the mocked service returning
`MandateConversation("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", "mandate-1-1-abc@wutsi.com", "mandate-1-1@wutsi.com", listOf("mandate-1-1@wutsi.com"))`, and assert on the captured `Email`: the subject above, those three header values, and `verify(conversationService).of(1L)`. Delete the old subject assertions (`Ndopify: Kim Possible vous propose…`, `Ndopify: n'oubliez pas de demander votre visite`) and keep every template-context assertion.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposedMailetTest,MatchVisitReminderMailetTest'`
Expected: compilation failure — no `conversationService` parameter.

- [ ] **Step 3: Use the conversation**

In `MatchProposedMailet.send`, after `val mandate = matches.first().mandate`:

```kotlin
        val conversation = conversationService.of(mandate.id!!)
```

and in the `Email`: `subject = conversation.subject` + the three header values (the `{{count}}` / `{{place}}` replacements go; the template context keeps `count`). Same in `MatchVisitReminderMailet.send` with `match.mandate.id!!`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MatchProposedMailetTest,MatchVisitReminderMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt \
        src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt \
        src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt
git commit -m "feat(server): propose and remind inside the mandate's conversation" -m "Refs #323"
```

### Task 6: The six visit e-mails

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailet.kt`
- Test: the six matching `*MailetTest.kt` in `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/`

**Interfaces:**
- Consumes: `MandateConversationService.of(mandateId: Long)` — the visit carries `visit.mandateId`; there is no mandate entity to load.
- Produces: each mailet takes `conversationService` as its last constructor parameter. **Only the customer copy** gets the conversation; the agent copy keeps `SUBJECT_AGENT` / `SUBJECT` and no header. `VisitCancelledMailet`'s private `send` gains `conversation: MandateConversation? = null`.

- [ ] **Step 1: Write the failing tests**

In each of the six tests, inject the mocked service (returning
`MandateConversation("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", "mandate-1-1-abc@wutsi.com", "mandate-1-1@wutsi.com", listOf("mandate-1-1@wutsi.com"))`) and assert:

```kotlin
// customer copy: VisitCancelledMailetTest, the agent-cancelled case
val conversationEmail = argumentCaptor<Email>()
verify(mailService).send(conversationEmail.capture())
assertEquals("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", conversationEmail.firstValue.subject)
assertEquals("mandate-1-1-abc@wutsi.com", conversationEmail.firstValue.messageId)
assertEquals("mandate-1-1@wutsi.com", conversationEmail.firstValue.inReplyTo)
assertEquals(listOf("mandate-1-1@wutsi.com"), conversationEmail.firstValue.references)
```

and, for the same mailet, the agent-cancelled-by-customer case (the agent's copy):

```kotlin
val email = argumentCaptor<Email>()
verify(mailService).send(email.capture())
assertEquals("Ndopify: la visite a été annulée", email.firstValue.subject)
assertNull(email.firstValue.messageId, "the agent's copy is not threaded")
assertNull(email.firstValue.inReplyTo)
assertEquals(emptyList(), email.firstValue.references)
```

Repeat the customer/agent pair in `VisitConfirmedMailetTest` (`SUBJECT_AGENT` unchanged), `VisitReminderMailetTest` (`sendCustomer` vs `sendAgent`), `VisitExpiredMailetTest` (`captor.allValues[0]` customer, `[1]` agent), `VisitNoShowMailetTest` (customer always, agent only when `noShowParty = AGENT`), `VisitCompletedMailetTest` (customer only). Delete the old customer subject assertions; keep every template-context assertion.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitCancelledMailetTest,VisitExpiredMailetTest,VisitNoShowMailetTest,VisitCompletedMailetTest'`
Expected: compilation failure — no `conversationService` parameter.

- [ ] **Step 3: Use the conversation on the customer copy only**

Each mailet, at the top of the customer path (`send`'s customer e-mail, or `sendCustomer`):

```kotlin
        val conversation = conversationService.of(visit.mandateId)
```

then `subject = conversation.subject`, `messageId = conversation.messageId`, `inReplyTo = conversation.inReplyTo`, `references = conversation.references`.

- `VisitCancelledMailet`: `sendToCustomer` passes `conversation = conversationService.of(visit.mandateId)` to the private `send`; `sendToAgent` passes nothing. The private `send` uses `subject = conversation?.subject ?: SUBJECT.replace("{{tenant_name}}", tenant.name)` and the three header values from `conversation`.
- `VisitExpiredMailet` / `VisitNoShowMailet`: the customer `Email` uses the conversation; the agent `Email` is untouched (`SUBJECT_AGENT`, no header).
- `VisitConfirmedMailet`, `VisitReminderMailet.sendCustomer`, `VisitCompletedMailet`: customer copy only.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitConfirmedMailetTest,VisitReminderMailetTest,VisitCancelledMailetTest,VisitExpiredMailetTest,VisitNoShowMailetTest,VisitCompletedMailetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/visit/server/mail \
        src/test/kotlin/com/wutsi/ndopify/visit/server/mail
git commit -m "feat(server): write the visit e-mails in the mandate's conversation" -m "Refs #323"
```

### Task 7: The mandate's contract signature request

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt`

**Interfaces:**
- Consumes: `MandateConversationService` (Task 3); `ContractService.sendSignatureRequestEmails` already loads `mandateDao.findByContractId(id)` for the agent.
- Produces: `ContractSignatureMailet.send(contract: ContractEntity, contractParty: ContractPartyEntity, agent: AgentEntity? = null, mandate: MandateEntity? = null)` — with a mandate the e-mail takes the conversation's subject and headers; without one (a lease) it keeps `SUBJECT` and no header. The mailet takes `conversationService` as its last constructor parameter.

- [ ] **Step 1: Write the failing tests**

In `ContractSignatureMailetTest`, add a mandate fixture (`MandateEntity(id = 7L, tenantId = 1L)`) and the mocked service returning
`MandateConversation("Votre recherche : appartement à Yaounde · mandat 7", "mandate-1-7-abc@wutsi.com", "mandate-1-7@wutsi.com", listOf("mandate-1-7@wutsi.com"))`:

```kotlin
@Test
fun `a mandate's contract joins its conversation`() {
    mailet.send(contract, party, mandate = mandate)

    verify(conversationService).of(7L)
    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("Votre recherche : appartement à Yaounde · mandat 7", email.firstValue.subject)
    assertEquals("mandate-1-7-abc@wutsi.com", email.firstValue.messageId)
    assertEquals("mandate-1-7@wutsi.com", email.firstValue.inReplyTo)
}

@Test
fun `a lease keeps the platform subject`() {
    mailet.send(contract, party)

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("Ndopify: Votre contrat BAIL-2026-000042 est prêt à être signé", email.firstValue.subject)
    assertNull(email.firstValue.messageId)
    assertNull(email.firstValue.inReplyTo)
    assertEquals(emptyList(), email.firstValue.references)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ContractSignatureMailetTest`
Expected: compilation failure — no fourth parameter, no `conversationService`.

- [ ] **Step 3: Thread the mandate's contract**

In `ContractSignatureMailet.send`:

```kotlin
    fun send(
        contract: ContractEntity,
        contractParty: ContractPartyEntity,
        agent: AgentEntity? = null,
        mandate: MandateEntity? = null,
    ) {
        val conversation = mandate?.let { conversationService.of(it.id!!) }
        ...
        Email(
            ...
            subject = conversation?.subject ?: SUBJECT.replace("{{tenant_name}}", tenant.name)
                .replace("{{contract_number}}", contract.number),
            messageId = conversation?.messageId,
            inReplyTo = conversation?.inReplyTo,
            references = conversation?.references ?: emptyList(),
```

In `ContractService.sendSignatureRequestEmails`, keep the loaded mandate and pass it:

```kotlin
        val mandate = mandateDao.findByContractId(id)
        val agent = mandate?.agentId?.let { agentService.findByIdOrNull(it) }
        ...
                    signatureMailet.send(contract, party, agent, mandate)
```

(`SUBJECT_CUSTOMER` from #322 becomes unused — every contract party that is a mandate's now goes through the conversation — so remove it; `SUBJECT` stays for the lease / platform copy. If #322 named the customer constant differently, remove that one instead.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='ContractSignatureMailetTest,RequestContractSignatureEndpointTest'`
Expected: PASS — `RequestContractSignatureEndpointTest`'s contracts have no mandate, so its `Ndopify: Votre contrat …` subject is untouched.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt \
        src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt \
        src/test/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt
git commit -m "feat(server): ask for the mandate's signature inside its conversation" -m "Refs #323"
```

### Task 8: Pin the conversation end to end

**Files:**
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AcceptAssignmentEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ProposeMatchEndpointTest.kt`
- Modify (expected subjects only): `match/server/endpoint/ProposeMatchesEndpointTest.kt`, `match/server/job/RemindAcceptedMatchesJobTest.kt`, `visit/server/endpoint/ScheduleVisitEndpointTest.kt`, `visit/server/endpoint/CreateMatchVisitEndpointTest.kt`, `visit/server/endpoint/CheckOutVisitEndpointTest.kt`, `visit/server/endpoint/NoShowVisitEndpointTest.kt`, `visit/server/job/VisitJobsTest.kt`, `visit/server/job/RemindVisitsJobTest.kt` (all under `src/test/kotlin/com/wutsi/ndopify/`)

**Interfaces:**
- Consumes: everything above, through the real services, the async listeners and GreenMail.
- Produces: the proof that `V18_4` is applied and the subject is stored; the root/continuation headers on two real sends; every endpoint/job expectation on a customer subject updated.

- [ ] **Step 1: Write the failing assertions**

`AcceptAssignmentEndpointTest.accept` — replace the subject line and add the header and storage checks (autowire `MandateRepository` as `mandateDao`):

```kotlin
        // The customer is told who its agent is, asynchronously, in the mandate's conversation
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val message = smtp.receivedMessages.single()
        assertEquals("Votre recherche : appartement à Yaounde · mandat 1", message.subject)
        assertEquals("<mandate-1-1@wutsi.com>", message.getHeader("Message-ID")?.first())
        assertNull(message.getHeader("In-Reply-To")?.first(), "the assigned e-mail opens the conversation")
        assertEquals("Votre recherche : appartement à Yaounde · mandat 1", message.getHeader("Thread-Topic")?.first())
        assertEquals(
            "Votre recherche : appartement à Yaounde · mandat 1",
            mandateDao.findById(1L).get().conversationSubject,
        )
```

`ProposeMatchEndpointTest` — its fixture starts a conversation that already has a subject, so the proposal answers it. Add a `statements` entry to the class's existing `@Sql` (fixture `/db/test/match/ProposeMatchEndpoint.sql`, mandate 1, city Yaounde, neighbourhood Bastos):

```kotlin
@Sql(
    value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"],
    statements = ["UPDATE T_MANDATE SET conversation_subject = 'Votre recherche : appartement à Bastos (Yaounde) · mandat 1' WHERE id = 1"],
)
```

and assert the received message:

```kotlin
        assertEquals("Votre recherche : appartement à Bastos (Yaounde) · mandat 1", message.subject)
        assertEquals("<mandate-1-1@wutsi.com>", message.getHeader("In-Reply-To")?.first())
        assertEquals("<mandate-1-1@wutsi.com>", message.getHeader("References")?.first())
        assertTrue(message.getHeader("Message-ID")?.first().orEmpty().startsWith("<mandate-1-1-"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='AcceptAssignmentEndpointTest,ProposeMatchEndpointTest'`
Expected: FAIL on the old subject / missing headers.

- [ ] **Step 3: Update every remaining customer-subject expectation**

Replace the in-scope subject strings with the mandate's subject and, where the test sends several customer e-mails for one mandate, the continuation headers. The files and today's strings:

- `ProposeMatchesEndpointTest`: `Ndopify: Kim Possible vous propose 2 biens à Bastos (Yaounde)` → the mandate's subject (one e-mail, one conversation).
- `RemindAcceptedMatchesJobTest`: `Ndopify: n'oubliez pas de demander votre visite`.
- `ScheduleVisitEndpointTest`: `Ndopify: votre visite est confirmée` (customer only — the agent's stays).
- `CreateMatchVisitEndpointTest`: `Ndopify: votre visite est confirmée`.
- `CheckOutVisitEndpointTest`: `Ndopify: votre visite est terminée`.
- `NoShowVisitEndpointTest`: `Ndopify: la visite n'a pas eu lieu` (customer copy).
- `VisitJobsTest`: `Ndopify: votre demande de visite a expiré` and `Ndopify: la visite n'a pas eu lieu` (customer copies).
- `RemindVisitsJobTest`: `Ndopify: rappel de votre visite`.

Derive each expected subject from the fixture's mandate (type, property type, city, neighbourhoods, id) with the wording of Task 3 — do not guess: read the fixture's `T_MANDATE` row.

- [ ] **Step 4: Run the whole module**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/wutsi/ndopify
git commit -m "test(server): pin the mandate's conversation end to end" -m "Refs #323"
```

### Task 9: Walk the untouched copies

**Files:** none (verification only).

- [ ] **Step 1: Confirm only the in-scope customer copies changed**

Run: `cd modules/ndopify-server && grep -rn "SUBJECT" src/main/kotlin/com/wutsi/ndopify/{mandate,match,visit,contract,agent,listing}/server/mail/`
Expected: the agent / co-brokerage / platform constants are all still there and still prefixed with `{{tenant_name}}:`; the customer ones this plan replaced are gone.

- [ ] **Step 2: Confirm the diff's shape**

Run: `cd modules/ndopify-server && git diff --stat origin/main -- src/main src/test`
Expected: `platform/mail`, the mandate domain (entity, repository, migration, labels, assigned mailet), `match/server/mail`, `visit/server/mail`, `contract/server/mail` + `ContractService`, and their tests — nothing under `src/main/html`, no `pom.xml`, no other module.

- [ ] **Step 3: Confirm the platform e-mails are untouched**

Run: `cd modules/ndopify-server && mvn test -Dtest='MailServiceTest,KycMailetTest,WelcomeMailetTest,OtpMailetTest,VisitRequestedMailetTest,MatchAnsweredMailetTest,CoBrokerageVisitMailetTest'`
Expected: PASS — none of them sends a conversation e-mail, and this plan changed none of their builders.

---

The story's last acceptance criterion is not the implementer's: once deployed on TEST, a human sends a mandate's e-mails and confirms one conversation in Gmail, Outlook and Apple Mail, with each event in the preview.
