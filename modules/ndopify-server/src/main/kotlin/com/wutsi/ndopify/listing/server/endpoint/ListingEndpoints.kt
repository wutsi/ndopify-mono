package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.SearchListingImageRequest
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.mapper.ListingMapper
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.listing.server.service.ListingSectionService
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * The reads are public (`PublicEndpoints.TENANT`); the writes need a token, any user's: no access check yet.
 */
@RestController
@RequestMapping("/v1/listings")
class ListingEndpoints(
    private val service: ListingService,
    private val mapper: ListingMapper,
    private val imageService: ListingImageService,
    private val sectionService: ListingSectionService,
) {
    @GetMapping
    fun search(@ModelAttribute request: SearchListingRequest): SearchListingResponse {
        val listings = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchListingResponse(listings = listings.map { listing -> mapper.toListingSummary(listing) })
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetListingResponse {
        val listing = service.findById(id)
        return GetListingResponse(listing = mapper.toListing(listing, sectionService.summaries(id)))
    }

    @PostMapping
    fun create(@RequestBody @Valid request: CreateListingRequest): CreateListingResponse {
        val listing = service.create(request)
        return CreateListingResponse(listingId = listing.id ?: -1)
    }

    @PostMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody @Valid request: UpdateListingRequest) {
        service.update(id, request)
    }

    @PostMapping("/{id}/verify")
    fun verify(@PathVariable id: Long) {
        service.verify(id)
    }

    /** Uploads a photo (multipart, a JPEG, PNG or WEBP image), PENDING_VERIFICATION. */
    @PostMapping("/{id}/images/upload")
    fun upload(
        @PathVariable id: Long,
        @RequestParam(required = false) multipartFile: MultipartFile,
    ) {
        imageService.upload(id, multipartFile)
    }

    /** Removes a photo of a DRAFT listing. */
    @DeleteMapping("/images/{id}")
    fun deleteImage(@PathVariable id: Long) {
        imageService.delete(id)
    }

    /** The listing's images, oldest first. */
    @GetMapping("/{id}/images")
    fun images(
        @PathVariable id: Long,
        @ModelAttribute request: SearchListingImageRequest,
    ): SearchListingImageResponse {
        val images = imageService.search(id, request)
        return SearchListingImageResponse(images = images.map { image -> mapper.toListingImage(image) })
    }
}
