package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Generates the HTML content of a mandate's contract, from its MJML template under `/mandate/contract/`. Every value
 * is formatted here, in French: the templates only print them, and leave out the criteria the mandate doesn't set.
 * Must run with `TenantContext` set, since it loads the mandate's customer and agent.
 */
@Service
class MandateContractGenerator(
    private val tenantService: TenantService,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val locationService: LocationService,
    private val bodyResolver: MailBodyResolver,
) {
    companion object {
        private val TEMPLATES = mapOf(
            MandateType.SEARCH to "/mandate/contract/search.mjml",
        )

        private val CATEGORIES = mapOf(
            PropertyCategory.RESIDENTIAL to "Résidentiel",
            PropertyCategory.COMMERCIAL to "Commercial",
        )

        // Same labels as Partner Central's MandateLabels
        private val PROPERTY_TYPES = mapOf(
            PropertyType.ROOM to "Chambre",
            PropertyType.STUDIO to "Studio",
            PropertyType.APARTMENT to "Appartement",
            PropertyType.HOUSE to "Maison",
            PropertyType.VILLA to "Villa",
            PropertyType.DUPLEX to "Duplex",
            PropertyType.RETAIL to "Boutique, local commercial",
            PropertyType.OFFICE to "Bureau",
            PropertyType.INDUSTRIAL to "Entrepôt, local industriel",
            PropertyType.BUILDING to "Immeuble",
            PropertyType.LAND to "Terrain",
        )

        private val FURNITURES = mapOf(
            FurnitureType.FULLY_FURNISHED to "Oui",
            FurnitureType.UNFURNISHED to "Non",
        )

        private val ROOMS = mapOf(
            "1" to "1 chambre",
            "2" to "2 chambres",
            "3" to "3 chambres",
            "4" to "4 chambres",
            "5+" to "5 chambres et +",
        )

        private val PARKINGS = mapOf(
            "0" to "Sans parking",
            "1" to "1 place de parking",
            "2" to "2 places de parking",
            "3+" to "3 places de parking et +",
        )

        private val MOVING_PERIODS = mapOf(
            MovingPeriod.WITHIN_1_MONTH to "Dans moins d'1 mois",
            MovingPeriod.WITHIN_3_MONTHS to "Dans 1 à 3 mois",
            MovingPeriod.AFTER_3_MONTHS to "Dans plus de 3 mois",
        )

        private val CONTACT_PERIODS = mapOf(
            ContactPeriodType.MORNING to "Le matin",
            ContactPeriodType.AFTERNOON to "L'après-midi",
            ContactPeriodType.EVENING to "Le soir",
        )

        private val PERIODICITIES = mapOf(
            PeriodicityType.DAILY to "jour",
            PeriodicityType.WEEKLY to "semaine",
            PeriodicityType.MONTHLY to "mois",
            PeriodicityType.QUARTERLY to "trimestre",
            PeriodicityType.YEARLY to "an",
        )
    }

    fun generate(mandate: MandateEntity, contract: ContractEntity): String {
        val path = TEMPLATES[mandate.mandateType]
            ?: throw IllegalArgumentException("No contract template for the mandates ${mandate.mandateType}")
        return bodyResolver.resolve(path, context(mandate, contract))
    }

    private fun context(mandate: MandateEntity, contract: ContractEntity): Map<String, Any?> {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant) // Dates as the parties read them
        val customer = customerService.findById(mandate.customerId)
        val agent = mandate.agentId?.let { id -> agentService.findById(id) }

        val water = amenities(
            mandate.waterMeter to "Compteur d'eau",
            mandate.dedicatedWaterMeter to "Compteur d'eau individuel",
            mandate.waterTank to "Citerne",
            mandate.waterWell to "Forage",
        )
        val electricity = amenities(
            mandate.electricityMeter to "Compteur électrique",
            mandate.dedicatedElectricityMeter to "Compteur électrique individuel",
            mandate.generatorSet to "Groupe électrogène",
            mandate.solarPanel to "Panneaux solaires",
        )
        val roadAccess = amenities(
            mandate.roadPaved to "Route bitumée",
            mandate.trackAccessibleByCar to "Piste accessible en voiture",
            mandate.trackInaccessibleByCar to "Piste accessible à pied seulement",
        )
        val security = amenities(
            mandate.securedFenced to "Clôturé et sécurisé",
            mandate.securityGuard to "Gardien",
            mandate.videoSurveillance to "Vidéosurveillance",
        )
        val contactPeriod = mandate.preferredContactPeriod?.let { CONTACT_PERIODS[it] }
        val comments = mandate.comments?.ifBlank { null }

        return mapOf(
            // Contract
            "contract_number" to contract.number,
            "contract_date" to formatter.date(contract.createdAt),
            // Generated before the signature, from which the contract is valid: no dates yet
            "duration_months" to contract.durationMonths,

            // Parties
            "tenant_name" to tenant.name,
            "tenant_logo_url" to tenant.logoUrl,
            "client_name" to customer.party.fullName(),
            "client_phone" to customer.whatsappNumber?.ifBlank { null },
            "client_email" to customer.party.email.ifBlank { null },
            "agent_name" to agent?.party?.fullName(),

            // Property
            "property_category" to CATEGORIES[mandate.propertyCategory],
            "property_type" to (PROPERTY_TYPES[mandate.propertyType] ?: "Tout type de bien"),
            "furniture" to mandate.furnitureType?.let { FURNITURES[it] },
            "rooms" to mandate.rooms?.let { ROOMS[it] ?: it.ifBlank { null } },
            "parkings" to mandate.parkings?.let { PARKINGS[it] ?: it.ifBlank { null } },
            "area" to mandate.area,

            // Location
            "city" to (locationService.findByIdOrNull(mandate.cityId)?.name ?: ""),
            "neighbourhoods" to neighbourhoods(mandate),

            // Amenities
            "has_amenities" to listOf(water, electricity, roadAccess, security).any { it != null },
            "water" to water,
            "electricity" to electricity,
            "road_access" to roadAccess,
            "security" to security,

            // Terms
            "budget" to budget(mandate, formatter),
            "security_deposit" to months(mandate.securityDeposit),
            "advance_rent" to months(mandate.advanceRent),
            "lease_term" to months(mandate.leaseTerm),
            "notice_period" to months(mandate.noticePeriod),
            "moving_period" to MOVING_PERIODS[mandate.movingPeriod],

            // Contact
            "has_contact" to (contactPeriod != null || comments != null),
            "preferred_contact_period" to contactPeriod,
            "comments" to comments,
        )
    }

    // "Compteur d'eau, Forage", or null when none is checked (the template leaves the group out)
    private fun amenities(vararg amenities: Pair<Boolean, String>): String? =
        amenities.filter { (checked, _) -> checked }
            .joinToString(", ") { (_, label) -> label }
            .ifEmpty { null }

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

    // The deposit, advance, lease term and notice period are numbers of months
    private fun months(value: Int?): String? = value?.let { "$it mois" }
}
