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
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells both sides a request expired: the agent didn't schedule it in time. The customer can ask for another visit.
 */
@Service
class VisitExpiredMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT_CUSTOMER = "{{tenant_name}}: votre demande de visite a expiré"
        const val SUBJECT_AGENT = "{{tenant_name}}: demande de visite expirée avec {{customer_name}}"
        const val TEMPLATE = "/mail/visit/expired.mjml"
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val customerName = customer.party.fullName()
        val agentName = agent.party.fullName()
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
                    path = TEMPLATE,
                    context = context(
                        tenant,
                        customerName,
                        agentName,
                        visit,
                        listing,
                        customerUrl,
                        agentRecipient = false,
                    ) + AgentMailContext.of(agent, TenantFormatter(tenant)),
                ),
            ),
        )
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = agentName),
                subject = SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{customer_name}}", customerName),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = context(tenant, agentName, customerName, visit, listing, agentUrl, agentRecipient = true),
                ),
            ),
        )
    }

    private fun context(
        tenant: TenantEntity,
        recipientName: String,
        otherParty: String,
        visit: VisitEntity,
        listing: ListingEntity,
        visitUrl: String?,
        agentRecipient: Boolean,
    ): Map<String, Any?> {
        val formatter = TenantFormatter(tenant)
        val imageUrl = imageService.cover(listing)?.url?.ifBlank { null }
        return mapOf(
            "tenant_logo_url" to tenant.logoUrl,
            "tenant_name" to tenant.name,
            "recipient_name" to recipientName,
            "other_party" to otherParty,
            "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
            "image_url" to imageUrl,
            "requested_date" to formatter.date(visit.requestedAt),
            "requested_time" to formatter.time(visit.requestedAt),
            "agent_recipient" to agentRecipient,
            "visit_url" to visitUrl,
        )
    }
}
