package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
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
 * `RestConfiguration`). ndopify-server doesn't check who reads or writes a listing: the pages check its `agentId`.
 */
@Service
class NdListingClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** Creates the DRAFT listing of an approved LISTING mandate, pre-filled from it, and returns its id. */
    fun create(mandateId: Long): Long =
        rest.post()
            .uri("/v1/listings")
            .body(CreateListingRequest(mandateId = mandateId))
            .retrieve()
            .body<CreateListingResponse>()
            ?.listingId
            ?: -1

    fun get(id: Long): Listing =
        rest.get()
            .uri("/v1/listings/{id}", id)
            .retrieve()
            .body<GetListingResponse>()
            ?.listing
            ?: Listing()

    /** Sends a DRAFT listing to be checked (UNDER_VERIFICATION). */
    fun verify(id: Long) {
        rest.post().uri("/v1/listings/{id}/verify", id).retrieve().toBodilessEntity()
    }

    /** The listing's photos, oldest first. */
    fun images(id: Long): List<ListingImage> =
        rest.get()
            .uri("/v1/listings/{id}/images", id)
            .retrieve()
            .body<SearchListingImageResponse>()
            ?.images
            ?: emptyList()
}
