package com.wutsi.ndopify.listing.dto

data class ListingImage(
    val id: Long = -1,
    val listingId: Long = -1,
    val url: String = "",
    val mimeType: String? = null,
    val status: ImageStatus = ImageStatus.UNKNOWN,
    val errorMessage: String? = null,
)
