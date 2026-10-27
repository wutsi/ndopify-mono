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
)
