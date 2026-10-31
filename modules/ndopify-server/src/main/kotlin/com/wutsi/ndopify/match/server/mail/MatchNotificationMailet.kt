package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
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
 * Tells an agent which listings of its inventory match its customers' searches: one e-mail for all its new matches,
 * grouped by mandate (best score first), at most [MAX_LISTINGS] listings each, the others counted. Sent by
 * `MatchNotificationService`, which marks the matches notified only once this returns.
 */
@Service
class MatchNotificationMailet(
    private val tenantService: TenantService,
    private val customerService: CustomerService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT_ONE = "{{tenant_name}}: 1 nouveau bien correspond à vos recherches"
        const val SUBJECT_MANY = "{{tenant_name}}: {{count}} nouveaux biens correspondent à vos recherches"
        const val TEMPLATE = "/mail/match/new_matches.mjml"
        const val MAX_LISTINGS = 5
    }

    /** [matches]: the agent's, all of one tenant. */
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
                        mapOf(
                            "title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                            "price" to MandateMailLabels.price(
                                listing.price,
                                listing.currencyCode,
                                listing.pricePeriodicityType,
                                formatter,
                            ),
                            "score" to (match.score * 100).roundToInt(),
                        )
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
