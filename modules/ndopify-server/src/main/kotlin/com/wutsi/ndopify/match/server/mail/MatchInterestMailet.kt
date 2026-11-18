package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.platform.util.PhoneNumberUtils
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent that its customer asked for a listing on its public page: who, which listing (title, place, price),
 * how to reach the customer (a WhatsApp button when it has a number) and a link to the search's page on Partner
 * Central, where it organises the visit. The customer keeps its agent: this message never names another one.
 */
@Service
class MatchInterestMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val imageService: ListingImageService,
    private val locationService: LocationService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: {{customer_name}} s'intéresse à un logement"
        const val TEMPLATE = "/mail/match/interest.mjml"
    }

    fun send(agent: AgentEntity, customer: CustomerEntity, mandate: MandateEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val imageUrl = imageService.cover(listing)?.url?.ifBlank { null }
        val customerName = customer.party.fullName()
        val agentName = agent.party.fullName()
        val mandateUrl = tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/mandates/${mandate.id}" }
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
                        "price" to MandateMailLabels.price(
                            listing.price,
                            listing.currencyCode,
                            listing.pricePeriodicityType,
                            formatter,
                        ),
                        "place" to place(listing),
                        "mandate_url" to mandateUrl,
                        "whatsapp_url" to customer.whatsappNumber?.takeIf { it.isNotBlank() }
                            ?.let { PhoneNumberUtils.toWhatsappUrl(it) },
                    ),
                ),
            ),
        )
    }

    // "Bonapriso, Douala", or only what the listing has
    private fun place(listing: ListingEntity): String {
        val city = locationService.findByIdOrNull(listing.cityId)?.name ?: ""
        val neighbourhood = listing.neighborhoodId?.let { id -> locationService.findByIdOrNull(id)?.name } ?: ""
        return listOfNotNull(neighbourhood.ifEmpty { null }, city.ifEmpty { null }).joinToString(", ")
    }
}
