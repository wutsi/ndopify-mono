package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.service.VisitFeedbackLabels
import org.springframework.stereotype.Service

/**
 * Tells the agent that its customer answered the visit feedback form: who, which listing, when, the answer (with the
 * reason and the comment when the property doesn't interest it), and a link to the visit's page on Partner Central.
 * Never the visit's code, the exact address nor the customer's token.
 * The photo is resized through [MailImages] for the e-mail (never the original URL).
 */
@Service
class VisitFeedbackMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val listingImageService: ListingImageService,
    private val imageService: ImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: l'avis de {{customer_name}} sur la visite"
        const val TEMPLATE = "/mail/visit/feedback.mjml"
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val imageUrl = MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER)
        val customerName = customer.party.fullName()
        val agentName = agent.party.fullName()
        val at = visit.scheduledAt ?: visit.requestedAt
        val notInterested = visit.interest == VisitInterest.NOT_INTERESTED
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = agentName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{customer_name}}", customerName),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to agentName,
                        "customer_name" to customerName,
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "image_url" to imageUrl,
                        "visit_date" to formatter.date(at),
                        "visit_time" to formatter.time(at),
                        "interest" to VisitFeedbackLabels.interest(visit.interest!!),
                        "reason" to visit.feedbackReasonCode.takeIf { notInterested }?.let { VisitFeedbackLabels.reason(it) },
                        "comment" to visit.feedbackComment?.ifBlank { null },
                        "visit_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/visits/${visit.id}" },
                    ),
                ),
            ),
        )
    }
}
