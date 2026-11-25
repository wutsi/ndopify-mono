package com.wutsi.ndopify.listing.server.domain

/**
 * What a listing image shows: [PHOTO] a view of the property itself (a room, the facade, the garden, the land, the
 * street), [FLOORPLAN] a plan or a drawing of the property, [MAP] a map or a satellite view of the land.
 *
 * The vision prompt decides which is valid: a floor plan on any property type, a parcel map on a land listing only.
 * The kind decides the listing's hero image (a photo only) and whether it has a photo at all. Server-only: it is
 * stored on `T_LISTING_IMAGE`, never exposed through `ndopify-dto` nor the API.
 */
enum class ImageKind {
    PHOTO,
    FLOORPLAN,
    MAP,
    ;

    companion object {
        /**
         * The model's answer as a kind, its case and blanks ignored; [PHOTO] for anything else (missing, empty,
         * unknown) so that an unexpected answer never fails the extraction.
         */
        fun parse(value: String?): ImageKind =
            entries.firstOrNull { kind -> kind.name == value?.trim()?.uppercase() } ?: PHOTO
    }
}
