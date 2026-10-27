package com.wutsi.ndopify.listing.server.service.verifier

data class ListingVerification(
    val success: Boolean = false,
    val errorCode: String? = null,
)
