package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingStatus

/**
 * The rules of a listing's pages (the prototype's `LISTING_SECTIONS` / `LISTING_TAGS` in partner-central.js): its
 * checklist, in order, and how its status reads. The checklist's state (each section VERIFIED or not) is
 * ndopify-server's: `Listing.sections`.
 */
object ListingRules {
    /** Same as ndopify-server's `ListingSectionService.MIN_PHOTOS`. */
    const val MIN_PHOTOS = 5

    /** The checklist, in order. */
    val SECTIONS: List<ListingSection> = listOf(
        ListingSection.LOCATION,
        ListingSection.GPS,
        ListingSection.PROPERTY,
        ListingSection.AMENITIES,
        ListingSection.TERMS,
        ListingSection.PHOTOS,
        ListingSection.REMARKS,
    )

    /** The sections saved with a form: the photos are uploaded instead. */
    val FORM_SECTIONS: List<ListingSection> = SECTIONS - ListingSection.PHOTOS

    val LABELS: Map<ListingSection, String> = linkedMapOf(
        ListingSection.LOCATION to "Emplacement",
        ListingSection.GPS to "Position GPS",
        ListingSection.PROPERTY to "Le bien",
        ListingSection.AMENITIES to "Eau, électricité, accès",
        ListingSection.TERMS to "Loyer et conditions",
        ListingSection.PHOTOS to "Photos",
        ListingSection.REMARKS to "Vos remarques",
    )

    // Sent, and not published yet: the prototype's single "PENDING" step.
    private val SENT = setOf(ListingStatus.UNDER_VERIFICATION, ListingStatus.VERIFIED, ListingStatus.PUBLISHING)

    data class Tag(val label: String, val style: String)

    private val DRAFT_TAG = Tag("Brouillon", "info")
    private val SENT_TAG = Tag("En vérification", "warning")
    private val PUBLISHED_TAG = Tag("Publiée", "success")

    // Rented, sold, expired, cancelled: no wording in the prototype, which stops at the publication.
    private val CLOSED = setOf(ListingStatus.RENTED, ListingStatus.SOLD, ListingStatus.EXPIRED, ListingStatus.CANCELLED)
    private val CLOSED_TAG = Tag("Fermée", "neutral")

    // PENDING (and UNKNOWN, never shown: the page is then locked): no code sets it yet, neither closed nor sent.
    private val PENDING_TAG = Tag("En attente", "warning")

    /** The URL's name of a section: `/listings/{id}/location`. */
    fun slug(section: ListingSection): String = section.name.lowercase()

    /** The form section of a URL's name; null for the photos (their own page) or anything else. */
    fun section(slug: String): ListingSection? = FORM_SECTIONS.firstOrNull { slug(it) == slug }

    fun tag(status: ListingStatus): Tag = when {
        status == ListingStatus.DRAFT -> DRAFT_TAG
        sent(status) -> SENT_TAG
        status == ListingStatus.PUBLISHED -> PUBLISHED_TAG
        status in CLOSED -> CLOSED_TAG
        else -> PENDING_TAG
    }

    /** Being checked by Ndopify: sent, not published yet. */
    fun sent(status: ListingStatus): Boolean = status in SENT

    /** Saved by the agent on site (PHOTOS: enough of them, none refused), as ndopify-server's checklist says. */
    fun done(listing: Listing, section: ListingSection): Boolean =
        listing.sections.any { it.section == section && it.status == ListingSectionStatus.VERIFIED }

    /**
     * Needed to send the listing, as ndopify-server's checklist says (`ListingSectionSummary.required`: only REMARKS
     * isn't, today); a section it doesn't list is.
     */
    fun required(listing: Listing, section: ListingSection): Boolean =
        listing.sections.firstOrNull { it.section == section }?.required ?: true

    /** Every required section done: the listing can be sent. Without its checklist, it can't be told: no. */
    fun complete(listing: Listing): Boolean =
        listing.sections.isNotEmpty() && SECTIONS.filter { required(listing, it) }.all { done(listing, it) }

    /** Only a DRAFT changes: nothing does while it's checked, nor once published. */
    fun editable(listing: Listing): Boolean = listing.status == ListingStatus.DRAFT
}
