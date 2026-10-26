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
    /** The panel above the checklist: [PANEL_DRAFT], [PANEL_SENT], [PANEL_PUBLISHED], or none. */
    val panel: String?,
    val sentAt: String?,
    val publishedAt: String?,
    /** Written by Ndopify on publication, from the visit and the photos: the agent never writes them. */
    val generatedTitle: String?,
    val generatedSummary: String?,
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
        const val PANEL_PUBLISHED = "PUBLISHED"
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
    /** Null until the listing exists. */
    val url: String?,
    val createUrl: String,
    /** Null when the listing couldn't be loaded: only the link is shown. */
    val text: String?,
    val action: String,
    /** The step's main button while there's something for the agent to do. */
    val primary: Boolean,
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
