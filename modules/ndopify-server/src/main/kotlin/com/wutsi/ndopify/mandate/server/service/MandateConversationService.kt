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
