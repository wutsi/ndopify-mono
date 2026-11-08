package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/** The 24 h reminder, to both sides, as the confirmation: type, date, address or WhatsApp call, and the code. */
@Service
class VisitReminderMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT_CUSTOMER = "{{tenant_name}}: rappel de votre visite"
        const val SUBJECT_AGENT = "{{tenant_name}}: rappel de la visite avec {{customer_name}}"
        const val TEMPLATE_CUSTOMER = "/mail/visit/reminder.mjml"
        const val TEMPLATE_AGENT = "/mail/visit/reminder_agent.mjml"
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val imageUrl = imageService.cover(listing)?.url?.ifBlank { null }
        val customerName = customer.party.fullName()
        val agentName = agent.party.fullName()
        val onSite = visit.type == VisitType.ON_SITE
        val address = address(listing).takeIf { onSite }
        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
        val customerUrl = tenant.publicPortalUrl?.removeSuffix("/")?.let { base ->
            val url = "$base/visits/${visit.id}"
            token?.let { "$url?token=$it" } ?: url
        }
        val agentUrl = tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/visits/${visit.id}" }

        mailService.send(
            Email(
                sender = AgentMailContext.from(agent),
                recipient = Receipient(email = customer.party.email, displayName = customerName),
                subject = SUBJECT_CUSTOMER.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE_CUSTOMER,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to customerName,
                        "listing_title" to listingTitle(listing),
                        "image_url" to imageUrl,
                        "agent_full_name" to agentName,
                        "agent_whatsapp" to formatter.phone(agent.whatsappNumber),
                        "visit_date" to formatter.date(visit.scheduledAt!!),
                        "visit_time" to formatter.time(visit.scheduledAt),
                        "on_site" to onSite,
                        "address" to address,
                        "code" to visit.code,
                        "visit_url" to customerUrl,
                    ) + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = agentName),
                subject = SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{customer_name}}", customerName),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE_AGENT,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to agentName,
                        "customer_name" to customerName,
                        "customer_whatsapp" to formatter.phone(customer.whatsappNumber),
                        "listing_title" to listingTitle(listing),
                        "image_url" to imageUrl,
                        "visit_date" to formatter.date(visit.scheduledAt!!),
                        "visit_time" to formatter.time(visit.scheduledAt),
                        "on_site" to onSite,
                        "visit_url" to agentUrl,
                    ),
                ),
            ),
        )
    }

    private fun listingTitle(listing: ListingEntity): String =
        listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"

    private fun address(listing: ListingEntity): String? =
        listOfNotNull(
            listing.landmark?.trim()?.ifEmpty { null },
            listing.accessDirections?.trim()?.ifEmpty { null },
        ).joinToString(", ").ifEmpty { null }
}
