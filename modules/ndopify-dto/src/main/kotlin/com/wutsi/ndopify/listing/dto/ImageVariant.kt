package com.wutsi.ndopify.listing.dto

/** One rendition of a listing photo: its exact pixel size and its URL. */
data class ImageVariant(
    val width: Int = 0,
    val height: Int = 0,
    val url: String = "",
)
