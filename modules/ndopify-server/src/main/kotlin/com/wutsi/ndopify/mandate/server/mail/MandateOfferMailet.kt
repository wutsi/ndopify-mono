package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/** Offers a mandate to an agent: what the customer is looking for, and until when the agent can accept. */
@Service
class MandateOfferMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Un nouveau mandat vous est proposé à {{city}}"

        private val PROPERTY_TYPES = mapOf(
            PropertyType.ROOM to "Chambre",
            PropertyType.STUDIO to "Studio",
            PropertyType.APARTMENT to "Appartement",
            PropertyType.VILLA to "Villa",
            PropertyType.DUPLEX to "Duplex",
            PropertyType.HOUSE to "Maison",
            PropertyType.BUILDING to "Immeuble",
            PropertyType.LAND to "Terrain",
            PropertyType.RETAIL to "Local commercial",
            PropertyType.INDUSTRIAL to "Local industriel",
            PropertyType.OFFICE to "Bureau",
        )

        private val PERIODICITIES = mapOf(
            PeriodicityType.DAILY to "jour",
            PeriodicityType.WEEKLY to "semaine",
            PeriodicityType.MONTHLY to "mois",
            PeriodicityType.QUARTERLY to "trimestre",
            PeriodicityType.YEARLY to "an",
        )
    }

    fun send(assignment: AssignmentEntity, agent: AgentEntity) {
        val mandate = assignment.mandate
        val tenant = tenantService.findById(mandate.tenantId!!)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = agent.party
        val fullName = party.fullName()
        val formatter = TenantFormatter(tenant)
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name).replace("{{city}}", city),
                body = mailBodyResolver.resolve(
                    path = "/mail/mandate/offer.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "listing_type" to if (mandate.listingType == ListingType.SALE) "Achat" else "Location",
                        "property_type" to mandate.propertyType?.let { PROPERTY_TYPES[it] },
                        "rooms" to mandate.rooms?.ifBlank { null },
                        "city" to city,
                        "neighbourhoods" to neighbourhoods(mandate),
                        "budget" to budget(mandate, formatter),
                        // The agent reads it in its local time
                        "deadline_date" to formatter.date(assignment.expiresAt),
                        "deadline_time" to formatter.time(assignment.expiresAt),
                        "offer_url" to tenant.partnerCentralUrl,
                    )
                )
            )
        )
    }

    private fun neighbourhoods(mandate: MandateEntity): String? =
        mandate.neighborhoodIds
            .mapNotNull { id -> locationService.findByIdOrNull(id)?.name }
            .joinToString(", ")
            .ifEmpty { null }

    // Ex: 150 000 FCFA / mois
    private fun budget(mandate: MandateEntity, formatter: TenantFormatter): String {
        val amount = formatter.money(mandate.price, mandate.currencyCode)
        val periodicity = PERIODICITIES[mandate.pricePeriodicityType]?.let { " / $it" } ?: ""
        return "$amount$periodicity"
    }
}
