package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server listing endpoints (`/v1/listings`). Listings are tenant-scoped (`X-Tenant-ID`, see
 * `RestConfiguration`). Without a token, ndopify-server only returns a PUBLISHED listing and its VERIFIED images: any
 * other is a 404 ([org.springframework.web.client.HttpClientErrorException.NotFound]).
 */
@Service
class NdListingClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Listing =
        rest.get()
            .uri("/v1/listings/{id}", id)
            .retrieve()
            .body<GetListingResponse>()
            ?.listing
            ?: Listing()

    /** The listing's photos, oldest first. */
    fun images(id: Long): List<ListingImage> =
        rest.get()
            .uri("/v1/listings/{id}/images", id)
            .retrieve()
            .body<SearchListingImageResponse>()
            ?.images
            ?: emptyList()
}
