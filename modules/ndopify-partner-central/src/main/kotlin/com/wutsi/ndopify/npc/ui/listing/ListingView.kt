package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus

/**
 * A listing as its checklist page shows it (`templates/listing.html`): dates already worded in the tenant's time
 * zone, null when the status has none.
 */
data class ListingView(
    val id: Long,
    val mandateId: Long,
    /** "Annonce · Appartement, Bonapriso". */
    val title: String,
    val tag: ListingRules.Tag,
    val status: ListingStatus,
    val createdAt: String,
    /** The panel above the checklist: [PANEL_DRAFT], [PANEL_TO_FIX], [PANEL_SENT], or none (PUBLISHED: its date is in the tag). */
    val panel: String?,
    val sentAt: String?,
    /** Why the verification sent the DRAFT back (ListingRules.rejections), empty otherwise. */
    val rejections: List<String>,
    val publishedAt: String?,
    /** The published listing's page on the public portal; null without its `url` or the tenant's portal. */
    val publicUrl: String?,
    val rows: List<ChecklistRow>,
    /** "4 sections sur 6 vérifiées". */
    val progress: String,
    /** "Encore à faire : Position GPS, 2 photos.", or that everything is checked. */
    val missing: String,
    val editable: Boolean,
    /** Every required section done: "Envoyer pour vérification" is active. */
    val sendable: Boolean,
) {
    companion object {
        const val PANEL_DRAFT = "DRAFT"
        const val PANEL_SENT = "SENT"
        const val PANEL_TO_FIX = "TO_FIX"
    }
}

/** A row of the checklist: it opens its section, to edit it while a draft, to view it once sent. */
data class ChecklistRow(
    val section: ListingSection,
    val label: String,
    val url: String,
    /** The section's main values, on one line. */
    val summary: String,
    val state: ListingRules.Tag,
)

/**
 * The listing's line on the mandate page (an approved LISTING mandate only): "Créer l'annonce" (a form posting to
 * [createUrl]) until it exists, then where it stands and a link to it ([url]).
 */
data class MandateListingView(
    /** Its number and status, above the text. */
    val line: ListingLineView,
    /** Null until the listing exists. */
    val url: String?,
    val createUrl: String,
    /** Null when the listing couldn't be loaded (only the link is shown), or published: the line says it all. */
    val text: String?,
    /** The button's label; null once published: the line's number is the link. */
    val action: String?,
    /** The step's main button while there's something for the agent to do. */
    val primary: Boolean,
    /** Published only: "2 personnes intéressées" (`MatchRules.interest`); null else, or when it can't be had. */
    val interest: String? = null,
)

/**
 * The listing of a landlord's signed mandate, as one line (the Espace agent's card, the mandate page): "Annonce n° 501"
 * and its status ("Annonce" on the card, to fit one line on a phone), "Annonce" "À créer" before it's created.
 */
data class ListingLineView(
    val label: String,
    /** The listing, or the mandate (where it's created) before it exists: the card's link. */
    val url: String,
    /** Null when the listing couldn't be loaded: only its number is shown. */
    val tag: ListingRules.Tag?,
)

/** A section's page (`templates/listing/section.html`, `listing/photos.html`): what's around its form. */
data class SectionView(
    val listingId: Long,
    val mandateId: Long,
    val slug: String,
    /** "Le bien": the page's h1. */
    val heading: String,
    /** "Annonce · Appartement, Bonapriso · mandat n° 203". */
    val meta: String,
    val pageTitle: String,
    /** A DRAFT: the form saves. Otherwise the answers are only shown, with [readonlyText]. */
    val editable: Boolean,
    val readonlyText: String?,
)

/** A property type of *Le bien*'s choices: its category (`data-categories`, for listing.js) and label. */
data class TypeChoice(
    val value: String,
    val category: String,
    val label: String,
)

/** The photos page's list (`listing/photos.html`): the photos, oldest first, and where they stand. */
data class PhotosView(
    val photos: List<PhotoView>,
    /** "3 photos · encore 2 au moins". */
    val count: String,
    /** [ListingRules.MAX_PHOTOS] photos: nothing more can be added. */
    val full: Boolean,
)

data class PhotoView(
    val id: Long,
    val url: String,
    /** "Photo 2". */
    val alt: String,
    /** "Envoyée", "Vérifiée", or why it was refused. */
    val note: String,
    val rejected: Boolean,
    val deleteLabel: String,
)
