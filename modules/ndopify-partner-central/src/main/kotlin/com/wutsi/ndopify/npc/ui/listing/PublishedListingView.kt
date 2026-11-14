package com.wutsi.ndopify.npc.ui.listing

/**
 * A published listing as its page shows it to both audiences (`templates/listing.html`): the description, the photos,
 * the key facts and the read-only property sheet. The private location is owner-only; a co-broker gets [placeNote]
 * instead.
 */
data class PublishedListingView(
    val description: String?,
    val photos: List<GalleryPhotoView>,
    val facts: List<FactView>,
    val property: List<SpecView>,
    val place: PlaceView,
    /** The co-broker's note: the exact address is shared by the agent; null for the owner. */
    val placeNote: String?,
    val amenities: List<AmenityGroupView>,
    val terms: List<SpecView>,
    /** The listing agent's remarks, shown to both audiences. */
    val remarks: String?,
    /** Owner only: the exact position and the access directions. */
    val privateLocation: PrivateLocationView?,
)

/** A photo of the gallery, [alt] from the image's title or description, else "Photo N". */
data class GalleryPhotoView(
    val url: String,
    val alt: String,
    /** The grid's first photo, on the hero tile. */
    val hero: Boolean = false,
    /** Beyond the fifth: hidden in the grid, still a slide of the viewer. */
    val extra: Boolean = false,
) {
    /** The tile's extra class; empty for a plain tile (`__` sequences break Thymeleaf's preprocessing in the template). */
    val cssClass: String
        get() = when {
            hero -> "listing-gallery__item--hero"
            extra -> "listing-gallery__item--extra"
            else -> ""
        }
}

/** A key fact of the strip, [icon] naming one of the template's SVGs ("bed", "bath", "area", "furnished", "parking"). */
data class FactView(val icon: String, val value: String)

/** A term → value row of the sheet. */
data class SpecView(val label: String, val value: String)

/** The public place of the property: its neighbourhood, city and landmark. */
data class PlaceView(val neighbourhood: String?, val city: String?, val landmark: String?) {
    /** "Bonapriso, Douala", whichever names are known. */
    val label: String get() = listOfNotNull(neighbourhood, city).joinToString(", ")
}

/** The amenities of one group ("Accès à l'eau"), labels only. */
data class AmenityGroupView(val label: String, val items: List<String>)

/** The owner-only position of the property, with its Maps link and access directions. */
data class PrivateLocationView(val coordinates: String?, val mapsUrl: String?, val accessDirections: String?)

/** The listing agent's card on a co-broker's page. */
data class ListingAgentView(
    val id: Long,
    val name: String,
    val initials: String,
    val photoUrl: String?,
    val whatsappUrl: String?,
    val whatsappNumber: String?,
    val profileUrl: String,
)
