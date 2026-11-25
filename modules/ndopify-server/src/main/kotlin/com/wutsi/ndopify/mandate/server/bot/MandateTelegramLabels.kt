package com.wutsi.ndopify.mandate.server.bot

import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import org.springframework.stereotype.Service

/**
 * The one property line the mandate Telegram messages share: the criteria (`Location - Appartement`, `Achat - Terrain`),
 * the neighbourhoods in parenthesis, then the compact price behind ` - ` (`… - 150K/mois`, `… - 25M`). No `Prix :` nor
 * `Budget :` label, and no city: the agent reads a price, not a caption.
 *
 * A missing property type leaves the criteria alone (`Location`); no neighbourhood means no parenthesis; an unset price
 * (`< 0`) or a blank currency leaves the price out, with no trailing dash.
 */
@Service
class MandateTelegramLabels(private val locationService: LocationService) {
    fun line(mandate: MandateEntity, formatter: TenantFormatter): String = buildString {
        append(criteria(mandate))
        MandateMailLabels.neighbourhoods(mandate, locationService)?.let { append(" ($it)") }
        if (mandate.price >= 0 && mandate.currencyCode.isNotBlank()) {
            append(" - ")
            append(MandateMailLabels.compactPrice(mandate.price, mandate.pricePeriodicityType, formatter))
        }
    }

    // Ex: "Location - Appartement", or "Location" without a property type
    private fun criteria(mandate: MandateEntity): String {
        val propertyType = MandateMailLabels.propertyType(mandate)
        return if (propertyType == null) {
            MandateMailLabels.listingType(mandate)
        } else {
            "${MandateMailLabels.listingType(mandate)} - $propertyType"
        }
    }
}
