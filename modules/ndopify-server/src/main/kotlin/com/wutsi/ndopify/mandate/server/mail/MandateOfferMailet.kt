package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Offers a mandate to an agent: what the customer is looking for (or, for a listing, the property the owner offers),
 * and until when the agent can accept.
 */
@Service
class MandateOfferMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Un nouveau mandat vous est proposé à {{place}}"
        const val TEMPLATE = "/mail/mandate/offer.mjml"
        const val TEMPLATE_LISTING = "/mail/mandate/offer_listing.mjml"
    }

    fun send(assignment: AssignmentEntity, agent: AgentEntity) {
        val mandate = assignment.mandate
        val tenant = tenantService.findById(mandate.tenantId!!)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = agent.party
        val fullName = party.fullName()
        val formatter = TenantFormatter(tenant)
        val listing = mandate.mandateType == MandateType.LISTING
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{place}}", MandateMailLabels.place(mandate, city, locationService)),
                body = mailBodyResolver.resolve(
                    path = if (listing) TEMPLATE_LISTING else TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "listing_type" to MandateMailLabels.listingType(mandate),
                        "property_type" to MandateMailLabels.propertyType(mandate),
                        "rooms" to mandate.rooms?.ifBlank { null },
                        "city" to city,
                        "neighbourhoods" to MandateMailLabels.neighbourhoods(mandate, locationService),
                        // The agent reads it in its local time
                        "deadline_date" to formatter.date(assignment.expiresAt),
                        "deadline_time" to formatter.time(assignment.expiresAt),
                        "offer_url" to tenant.partnerCentralUrl,
                    ) + if (listing) {
                        mapOf(
                            "price_label" to MandateMailLabels.priceLabel(mandate),
                            "price" to MandateMailLabels.budget(mandate, formatter),
                            "area" to mandate.area,
                            "available_from" to mandate.availableFrom?.let { date -> formatter.date(date) },
                        )
                    } else {
                        mapOf("budget" to MandateMailLabels.budget(mandate, formatter))
                    }
                )
            )
        )
    }
}
