package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Nudges the agent that its customer accepted a match more than 48 h ago and still has no visit: the customer, the
 * listing and its price, and two buttons — call the customer on WhatsApp, then book the visit on the mandate's page
 * (`{partnerCentralUrl}/mandates/{mandateId}#match-{matchId}`, none without a `partnerCentralUrl`). The fallback of the
 * Telegram message ([com.wutsi.ndopify.match.server.bot.MatchVisitAgentNudgeBotlet]) for an agent without a linked chat.
 * The photo is resized through [MailImages] for the e-mail (never the original URL).
 */
@Service
class MatchVisitAgentNudgeMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val listingImageService: ListingImageService,
    private val imageService: ImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: {{customer_name}} attend une visite"
        const val TEMPLATE = "/mail/match/agent_visit_nudge.mjml"
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(match.mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val listing = match.listing
        val customerName = customer.party.fullName()
        val baseUrl = tenant.partnerCentralUrl?.removeSuffix("/")
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = agent.party.fullName()),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{customer_name}}", customerName),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to agent.party.fullName(),
                        "customer_name" to customerName,
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "image_url" to MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER),
                        "price_label" to MandateMailLabels.priceLabel(match.mandate),
                        "price" to MandateMailLabels.price(
                            listing.price,
                            listing.currencyCode,
                            listing.pricePeriodicityType,
                            formatter,
                        ),
                        "whatsapp_url" to PhoneNumberUtils.whatsappUrlOrNull(customer.whatsappNumber),
                        "visit_url" to baseUrl?.let { "$it/mandates/${match.mandate.id}#match-${match.id}" },
                    ) + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
    }
}
