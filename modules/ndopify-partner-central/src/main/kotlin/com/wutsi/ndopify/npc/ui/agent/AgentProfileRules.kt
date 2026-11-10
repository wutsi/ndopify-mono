package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.springframework.web.util.UriComponentsBuilder

/** Rules of the agent profile page (`agent.html`): its pager, back link and the wording of a listing's card. */
object AgentProfileRules {
    const val PAGE_SIZE = 3

    private const val FALLBACK_TITLE = "Bien"

    /** The whole pages of [total], at least one (the pager is hidden then). */
    fun pages(total: Long): Int = ((total + PAGE_SIZE - 1) / PAGE_SIZE).toInt().coerceAtLeast(1)

    /** [requested] clamped to the first .. [pages] page. */
    fun page(total: Long, requested: Int): Int = requested.coerceIn(1, pages(total))

    /**
     * [value] when it's a same-origin path: a `/`-rooted path that isn't protocol-relative (`//host`) and holds no
     * scheme (`:`) or backslash. Anything else is dropped, and the page falls back to the Espace agent.
     */
    fun back(value: String?): String? =
        value?.takeIf { it.startsWith("/") && !it.startsWith("//") && ':' !in it && '\\' !in it && it.isNotBlank() }

    /** The page URL, with [id], [page] and a valid [back]. */
    fun pageUrl(id: Long, page: Int, back: String?): String {
        val builder = UriComponentsBuilder.fromPath("/agent")
            .queryParam("id", id)
            .queryParam("page", page)
        back(back)?.let { builder.queryParam("back", it) }
        return builder.build().toUriString()
    }

    /** "Appartement 2 chambres", "Studio" (no rooms for a studio or a room), or "Bien" without a type. */
    fun listingTitle(summary: ListingSummary): String {
        val type = MandateLabels.shortPropertyType(summary.propertyType) ?: FALLBACK_TITLE
        val rooms = summary.rooms?.takeIf {
            summary.propertyType != PropertyType.STUDIO && summary.propertyType != PropertyType.ROOM
        }
        return listOfNotNull(type, rooms?.let { "$it " + if (it > 1) "chambres" else "chambre" }).joinToString(" ")
    }

    /** "Bonapriso, Douala", whichever names are known; null without any. */
    fun listingPlace(summary: ListingSummary, locations: Map<Long, Location>): String? =
        listOfNotNull(
            summary.neighborhoodId?.let { locations[it]?.name },
            locations[summary.cityId]?.name,
        )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifEmpty { null }

    /** "80 m² · Meublé", whichever facts are known; null without any. */
    fun listingFacts(summary: ListingSummary, formatter: TenantFormatter): String? =
        listOfNotNull(
            summary.area?.takeIf { it > 0 }?.let { formatter.count(it.toLong()) + " m²" },
            MandateLabels.FURNITURE_TYPE_LABELS[summary.furnitureType],
        )
            .joinToString(" · ")
            .ifEmpty { null }

    /** "250K FCFA / mois", in the tenant's compact format; null without a price. */
    fun listingPrice(summary: ListingSummary, formatter: TenantFormatter): String? =
        summary.price.takeIf { it > 0 }?.let {
            MandateLabels.price(
                Money(amount = it, currency = summary.currencyCode, periodicity = summary.pricePeriodicityType),
                formatter,
                compact = true,
            )
        }

    /** The wa.me link of [number]'s digits; null without a number. */
    fun whatsappUrl(number: String?): String? =
        number?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { "https://wa.me/$it" }
}
