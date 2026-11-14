package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.visit.VisitCardView
import com.wutsi.ndopify.npc.ui.visit.VisitStats

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
    /** The published or closed listing's page on the public portal; null without its `url` or the tenant's portal. */
    val publicUrl: String?,
    /** Published only: "2 personnes intéressées · 3 visites"; null else, or when its stats can't be had. */
    val stats: String? = null,
    val rows: List<ChecklistRow>,
    /** "4 sections sur 6 vérifiées". */
    val progress: String,
    /** "Encore à faire : Position GPS, 2 photos.", or that everything is checked. */
    val missing: String,
    val editable: Boolean,
    /** Every required section done: "Envoyer pour vérification" is active. */
    val sendable: Boolean,
    /** A co-broker's view: the published sheet without the owner's blocks nor the checklist. */
    val coBrokerage: Boolean = false,
    /** The published sheet; null until PUBLISHED, RENTED or SOLD. */
    val published: PublishedListingView? = null,
    /** The listing agent's card, co-broker only. */
    val agent: ListingAgentView? = null,
    /** The mandate for its agent, the search's *Biens correspondants* for a co-broker. */
    val backUrl: String = "/mandates/$mandateId",
    val backLabel: String = "← Retour au mandat",
) {
    companion object {
        const val PANEL_DRAFT = "DRAFT"
        const val PANEL_SENT = "SENT"
        const val PANEL_TO_FIX = "TO_FIX"
    }
}

/**
 * A published listing's clients (`ListingViews.clients`), on its mandate's page: its *Propositions et visites*.
 */
data class ListingClientsView(
    /** The listing's clients, the visits to confirm first; [ProposalsView.visitsUnavailable] when they can't be read. */
    val proposals: ProposalsView,
    /** The listing's total visits (open and completed), null when they can't be read. */
    val stats: VisitStats?,
    /** The clients who accepted it (its ACCEPTED matches), including another agent's. */
    val interested: Int,
)

/** A published listing's *Propositions et visites* (`ListingViews.clients`), on its mandate's page. */
data class ProposalsView(
    /** One card per client who didn't refuse it, grouped by what the agent does next (the refused ones are only counted). */
    val rows: List<ProposalRow>,
    /** "1 refusé · 1 sans réponse" (`MatchRules.outcomes`); null when there is neither. */
    val outcomes: String? = null,
    /** The visits couldn't be read: every card stays, without its visit block. */
    val visitsUnavailable: Boolean = false,
) {
    val groups: List<ProposalGroupView>
        get() = rows.groupBy { it.group }.map { ProposalGroupView(it.key.label, it.value) }
}

/**
 * A client the listing was sent to. Another agent's client is "Client d'un autre agent" with no [url] and no [visit]:
 * that agent's mandate is protected, neither its customer nor its mandate is on the page. A visit the client asked for
 * sits in the same card, as [visit].
 */
data class ProposalRow(
    val matchId: Long,
    val client: String,
    /** The search's *Biens correspondants*, where the match is handled; null for another agent's. */
    val url: String?,
    /** "Intéressé", "En attente". */
    val tag: MandateRules.Tag,
    /** "Envoyé le 28 oct. 2026" / "Intéressé le 29 oct. 2026"; for a visit card, the sent date only. */
    val sent: String,
    /** The visit the client asked for, inside the card that led to it; null without one. */
    val visit: VisitCardView? = null,
    val group: ProposalGroup = ProposalGroup.WAITING,
)

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
    /** Published only: "2 personnes intéressées · 3 visites"; null else, or when it can't be had. */
    val stats: String? = null,
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
