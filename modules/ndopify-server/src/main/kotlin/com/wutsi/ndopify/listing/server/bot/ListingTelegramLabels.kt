package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import org.springframework.stereotype.Service

/**
 * The listing labels the agent Telegram messages share: the property's title, its compact price, and the one-line
 * `{title} - {price}` that opens them. No neighbourhood after a title — the generated title already names it — and no
 * `Prix :` label: the agent reads the price of `500K/mois` or `25M`.
 *
 * A blank title falls back to `<type> à louer|à vendre, <city> (<neighbourhood>)`, e.g.
 * `Appartement à louer, Yaoundé (Mballa II)`; `Bien` without a property type, the city alone without a neighbourhood,
 * and no dangling comma, parenthesis or hyphen. A price that is unset (`< 0`) or in a blank currency is left out (no
 * trailing ` - `). Amounts go through [MandateMailLabels.compactPrice], so the tenant's [TenantFormatter] rules apply,
 * never a hard-coded pattern or currency.
 */
@Service
class ListingTelegramLabels(private val locationService: LocationService) {
    fun title(listing: ListingEntity): String =
        listing.title?.ifBlank { null } ?: fallbackTitle(listing)

    fun titleAndPrice(listing: ListingEntity, formatter: TenantFormatter): String {
        val price = price(listing, formatter) ?: return title(listing)
        return "${title(listing)} - $price"
    }

    fun price(listing: ListingEntity, formatter: TenantFormatter): String? =
        if (listing.price < 0 || listing.currencyCode.isBlank()) {
            null
        } else {
            MandateMailLabels.compactPrice(listing.price, listing.pricePeriodicityType, formatter)
        }

    private fun fallbackTitle(listing: ListingEntity): String = buildString {
        append(MandateMailLabels.propertyType(listing.propertyType) ?: "Bien")
        append(if (listing.listingType == ListingType.SALE) " à vendre" else " à louer")
        city(listing)?.let { append(", $it") }
        neighbourhood(listing)?.let { append(" ($it)") }
    }

    private fun city(listing: ListingEntity): String? =
        locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }

    private fun neighbourhood(listing: ListingEntity): String? =
        listing.neighborhoodId?.let { id -> locationService.findByIdOrNull(id)?.name?.ifBlank { null } }
}
