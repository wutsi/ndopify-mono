package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * Tells a search agent which listings of OTHER agents' inventories match its customers' searches: one e-mail for all
 * its new co-brokerage matches, grouped by mandate (best score first), at most [MAX_LISTINGS] listings each, the others
 * counted; each with a thumbnail of its photo ([ListingImageService.cover], none without a verified one) and the
 * listing agent's contact (WhatsApp, or e-mail when there is none). Sent by the co-brokerage job, which marks the
 * matches notified only once this returns. The listing's own customer is never read: only the recipient's search
 * customer is.
 */
@Service
class CoBrokerageMatchMailet(
    private val tenantService: TenantService,
    private val customerService: CustomerService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT_ONE = "{{tenant_name}}: 1 bien d'un autre agent correspond à vos recherches"
        const val SUBJECT_MANY = "{{tenant_name}}: {{count}} biens d'autres agents correspondent à vos recherches"
        const val TEMPLATE = "/mail/match/co_brokerage_matches.mjml"
        const val MAX_LISTINGS = 5
    }

    /** [matches]: the search agent's, all of one tenant. */
    fun send(agent: AgentEntity, matches: List<MatchEntity>) {
        val tenant = tenantService.findById(matches.first().mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val party = agent.party
        val fullName = party.fullName()

        val mandates = matches.groupBy { match -> match.mandate.id }
            .values
            .map { group -> group.sortedByDescending { match -> match.score } }
            .sortedByDescending { group -> group.first().score }
            .map { group ->
                val mandate = group.first().mandate
                val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
                mapOf(
                    "customer_name" to customerService.findByIdOrNull(mandate.customerId)?.party?.fullName(),
                    "listing_type" to MandateMailLabels.listingType(mandate),
                    "property_type" to MandateMailLabels.propertyType(mandate),
                    "place" to MandateMailLabels.place(mandate, city, locationService),
                    "mandate_url" to tenant.partnerCentralUrl?.let { url -> "$url/mandates/${mandate.id}" },
                    "listings" to group.take(MAX_LISTINGS).map { match ->
                        val listing = match.listing
                        val listingAgent = listing.agent
                        val contact = AgentMailContext.of(listingAgent, formatter).toMutableMap()
                        if (listingAgent.whatsappNumber.isNullOrBlank()) {
                            val email = listingAgent.party.email.takeIf { it.isNotBlank() }
                            contact["agent_email"] = email
                            contact["agent_email_url"] = email?.let { "mailto:$it" }
                        }
                        mapOf(
                            "image_url" to imageService.cover(listing)?.url?.ifBlank { null },
                            "title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                            "price" to MandateMailLabels.price(
                                listing.price,
                                listing.currencyCode,
                                listing.pricePeriodicityType,
                                formatter,
                            ),
                            "score" to (match.score * 100).roundToInt(),
                        ) + contact
                    },
                    "more" to (group.size - MAX_LISTINGS).takeIf { more -> more > 0 },
                )
            }

        val subject = if (matches.size == 1) SUBJECT_ONE else SUBJECT_MANY.replace("{{count}}", "${matches.size}")
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = subject.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "mandates" to mandates,
                    ),
                ),
            ),
        )
    }
}
