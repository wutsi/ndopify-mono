package com.wutsi.ndopify.listing.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchListingImageRequest(
    @param:BindParam("status") val statuses: List<ImageStatus> = emptyList()
)
