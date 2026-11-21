package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Reminds the customer of an accepted match that it can book a visit: the listing, its price, the agent's signature
 * and a fresh link to its page on the public portal, `{publicPortalUrl}/match/{matchId}?token={token}`, where the visit
 * form waits (none without the tenant's `publicPortalUrl`: the customer then answers its agent). The token signs the
 * customer in to the public portal ([MatchLinkTokenService]); without one, the link has no `token`.
 */
@Service
class MatchVisitReminderMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val listingImageService: ListingImageService,
    private val imageService: ImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: n'oubliez pas de demander votre visite"
        const val TEMPLATE = "/mail/match/visit_reminder.mjml"
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(match.mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val listing = match.listing
        val party = customer.party
        val fullName = party.fullName()
        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
        mailService.send(
            Email(
                sender = AgentMailContext.from(agent),
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "image_url" to MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER),
                        "price_label" to MandateMailLabels.priceLabel(match.mandate),
                        "price" to MandateMailLabels.price(
                            listing.price,
                            listing.currencyCode,
                            listing.pricePeriodicityType,
                            formatter,
                        ),
                        "online" to tenant.publicPortalUrl?.let { true },
                        "listing_url" to matchUrl(tenant, match, token),
                    ) + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
    }

    private fun matchUrl(tenant: TenantEntity, match: MatchEntity, token: String?): String? {
        val baseUrl = tenant.publicPortalUrl ?: return null
        val url = baseUrl.removeSuffix("/") + "/match/" + match.id
        return token?.let { "$url?token=$it" } ?: url
    }
}
