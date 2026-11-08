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
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/** Tells the customer the visit is over, and asks whether the property interests it. */
@Service
class VisitCompletedMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: votre visite est terminée"
        const val TEMPLATE = "/mail/visit/completed.mjml"
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val imageUrl = imageService.cover(listing)?.url?.ifBlank { null }
        val customerName = customer.party.fullName()
        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
        val visitUrl = tenant.publicPortalUrl?.removeSuffix("/")?.let { base ->
            val url = "$base/visits/${visit.id}"
            token?.let { "$url?token=$it" } ?: url
        }
        mailService.send(
            Email(
                sender = AgentMailContext.from(agent),
                recipient = Receipient(email = customer.party.email, displayName = customerName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to customerName,
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "image_url" to imageUrl,
                        "visit_date" to formatter.date(visit.scheduledAt!!),
                        "visit_time" to formatter.time(visit.scheduledAt),
                        "visit_url" to visitUrl,
                    ) + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
    }
}
