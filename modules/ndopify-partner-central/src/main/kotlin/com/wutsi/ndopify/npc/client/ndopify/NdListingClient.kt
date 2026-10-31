package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
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

    /**
     * The id of the listing of [mandateId], null before it's created. [agentId] must be the signed-in agent's: without
     * it, ndopify-server only searches the PUBLISHED listings.
     */
    fun findIdByMandate(mandateId: Long, agentId: Long): Long? =
        rest.get()
            .uri("/v1/listings?mandate-id={mandateId}&agent-id={agentId}&limit=1", mandateId, agentId)
            .retrieve()
            .body<SearchListingResponse>()
            ?.listings
            ?.firstOrNull()
            ?.id

    /**
     * The listings of [mandateIds] (one at most each), in one search. [agentId] must be the signed-in agent's, as for
     * [findIdByMandate]. The summaries have no `errorCodes`: a DRAFT sent back to fix can't be told from another.
     */
    fun searchByMandates(mandateIds: List<Long>, agentId: Long): List<ListingSummary> =
        rest.get()
            .uri { uri ->
                mandateIds.forEach { uri.queryParam("mandate-id", it) }
                uri.path("/v1/listings")
                    .queryParam("agent-id", agentId)
                    .queryParam("limit", mandateIds.size)
                    .build()
            }
            .retrieve()
            .body<SearchListingResponse>()
            ?.listings
            ?: emptyList()

    /**
     * The PUBLISHED listings among [ids], in one search: without `agent-id`, ndopify-server returns no other. No call
     * without an id.
     */
    fun searchByIds(ids: List<Long>): List<ListingSummary> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return rest.get()
            .uri { uri ->
                ids.forEach { uri.queryParam("id", it) }
                uri.path("/v1/listings")
                    .queryParam("limit", ids.size)
                    .build()
            }
            .retrieve()
            .body<SearchListingResponse>()
            ?.listings
            ?: emptyList()
    }

    /** Sends a DRAFT listing to be checked (UNDER_VERIFICATION). */
    fun verify(id: Long) {
        rest.post().uri("/v1/listings/{id}/verify", id).retrieve().toBodilessEntity()
    }

    /**
     * Saves one section of a DRAFT listing, which is then VERIFIED on its checklist. Fails with 409
     * LISTING_VERSION_CONFLICT if [UpdateListingRequest.version] isn't the listing's, LISTING_NOT_DRAFT once sent.
     */
    fun update(id: Long, request: UpdateListingRequest) {
        rest.post().uri("/v1/listings/{id}", id).body(request).retrieve().toBodilessEntity()
    }

    /**
     * Adds a photo, PENDING_VERIFICATION. [mimeType] is sent as the part's content type: the server only takes a JPEG,
     * PNG or WEBP image.
     */
    fun upload(id: Long, content: ByteArray, mimeType: String, filename: String) {
        val file = object : ByteArrayResource(content) {
            // A file name makes the part a file upload (a `MultipartFile`) on the server side.
            override fun getFilename(): String = filename
        }
        val partHeaders = HttpHeaders().apply { contentType = MediaType.parseMediaType(mimeType) }
        val body = LinkedMultiValueMap<String, Any>().apply { add("multipartFile", HttpEntity(file, partHeaders)) }

        rest.post()
            .uri("/v1/listings/{id}/images/upload", id)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    /**
     * Removes a photo of a DRAFT listing (409 LISTING_NOT_DRAFT once sent). ndopify-server doesn't check which listing
     * the image is of: the caller does.
     */
    fun deleteImage(imageId: Long) {
        rest.delete().uri("/v1/listings/images/{id}", imageId).retrieve().toBodilessEntity()
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
