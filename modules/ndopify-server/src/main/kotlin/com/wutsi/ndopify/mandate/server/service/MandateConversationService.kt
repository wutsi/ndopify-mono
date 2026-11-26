package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
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
 * conversation subject (`MandateEntity.conversationSubject`) and carries the conversation's root id; every later one
 * keeps that subject and answers the root, whatever its event. The root is the id Amazon SES assigned the first
 * message, when one was captured (`recordRoot`), and the pinned `mandate-<tenant>-<id>@<domain>` otherwise. The
 * subject is never rebuilt once stored: a later edit of the mandate's place or type must not split the conversation.
 *
 * Runs in its own transaction: the e-mail is handed to SMTP from inside the caller's transaction, and the SMTP server
 * delivers it before that transaction commits. Joining the caller would leave the stored subject invisible to a client
 * reading the mandate once it has the message (`AcceptAssignmentEndpointTest`); the subject must be durable first.
 */
@Service
class MandateConversationService(
    private val dao: MandateRepository,
    private val locationService: LocationService,
    private val mailService: MailService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun of(mandateId: Long): MandateConversation {
        val mandate = dao.findById(mandateId).orElse(null)
            ?: throw IllegalStateException("Mandate#$mandateId not found")
        val pinned = "mandate-${mandate.tenantId}-${mandate.id}@${mailService.fromDomain}"
        val root = dao.findConversationMessageIdById(mandateId) ?: pinned
        dao.findConversationSubjectById(mandateId)?.let { return continueFrom(it, mandate, root) }

        val subject = MandateMailLabels.conversationSubject(
            mandate,
            locationService.findByIdOrNull(mandate.cityId)?.name ?: "",
            locationService,
        )
        return if (dao.markConversationSubject(mandateId, subject) > 0) {
            MandateConversation(subject, pinned, null, emptyList())
        } else {
            // Another e-mail stored a subject between the read and the write: its subject wins
            continueFrom(dao.findConversationSubjectById(mandateId) ?: subject, mandate, root)
        }
    }

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

    private fun continueFrom(subject: String, mandate: MandateEntity, root: String) = MandateConversation(
        subject = subject,
        messageId = "mandate-${mandate.tenantId}-${mandate.id}-${UUID.randomUUID()}@${mailService.fromDomain}",
        inReplyTo = root,
        references = listOf(root),
    )
}
