package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import org.springframework.stereotype.Service

/**
 * The listing labels the agent Telegram messages share: the property's title, and the one-line
 * `{title} ({neighbourhood}) — {price}` that opens them.
 *
 * A blank title falls back to `Bien n° <id>`; an unknown neighbourhood is left out (no empty `()`); a price that is
 * unset (`< 0`) or in a blank currency is left out (no trailing ` — `). Amounts go through [MandateMailLabels.price],
 * so the tenant's [TenantFormatter] rules apply, never a hard-coded pattern or currency.
 */
@Service
class ListingTelegramLabels(private val locationService: LocationService) {
    fun title(listing: ListingEntity): String =
        listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"

    fun titleAndPrice(listing: ListingEntity, formatter: TenantFormatter): String = buildString {
        append(title(listing))
        neighbourhood(listing)?.let { append(" ($it)") }
        if (listing.price >= 0 && listing.currencyCode.isNotBlank()) {
            append(" — ")
            append(MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter))
        }
    }

    private fun neighbourhood(listing: ListingEntity): String? =
        listing.neighborhoodId?.let { id -> locationService.findByIdOrNull(id)?.name?.ifBlank { null } }
}
