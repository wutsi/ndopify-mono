package com.wutsi.ndopify.listing.dto

data class ListingImage(
    val id: Long = -1,
    val listingId: Long = -1,
    val url: String = "",
    val mimeType: String? = null,
    val status: ImageStatus = ImageStatus.UNKNOWN,
    val title: String? = null,
    val description: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val quality: Int? = null,
    val errorMessage: String? = null,

    /** The 3:2 renditions, ascending width; the browser picks one with `srcset`. */
    val variants: List<ImageVariant> = emptyList(),

    /** The 1200×630 rendition for `og:image`. */
    val socialUrl: String = "",
)
