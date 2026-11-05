package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.CreateListingResponse
import com.wutsi.ndopify.listing.dto.GetListingResponse
import com.wutsi.ndopify.listing.dto.SearchListingImageRequest
import com.wutsi.ndopify.listing.dto.SearchListingImageResponse
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.listing.dto.SearchListingStatsRequest
import com.wutsi.ndopify.listing.dto.SearchListingStatsResponse
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.mapper.ListingMapper
import com.wutsi.ndopify.listing.server.service.ListingAccessService
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.listing.server.service.ListingSectionService
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.listing.server.service.ListingStatsService
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
 * The reads are public (`PublicEndpoints.TENANT`), of the PUBLISHED listings only without a token
 * ([ListingAccessService]); the stats need a token and the agent of every listing; the writes need a token, any
 * user's: no access check yet.
 */
@RestController
@RequestMapping("/v1/listings")
class ListingEndpoints(
    private val service: ListingService,
    private val mapper: ListingMapper,
    private val imageService: ListingImageService,
    private val sectionService: ListingSectionService,
    private val accessService: ListingAccessService,
    private val statsService: ListingStatsService,
) {
    @GetMapping("/stats")
    fun stats(@ModelAttribute request: SearchListingStatsRequest): SearchListingStatsResponse {
        if (request.listingIds.isEmpty()) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = "listing-id", type = ParameterType.PARAMETER_TYPE_QUERY),
                )
            )
        }
        val ids = request.listingIds.distinct()
        val listings = service.findAllByIds(ids)
        if (listings.size != ids.size) {
            throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        }
        accessService.ensureAreAgent(listings)
        return SearchListingStatsResponse(stats = statsService.search(ids))
    }

    @GetMapping
    fun search(@ModelAttribute request: SearchListingRequest): SearchListingResponse {
        val restricted = accessService.restrict(request)
        val listings = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchListingResponse(listings = listings.map { listing -> mapper.toListingSummary(listing) })
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetListingResponse {
        val listing = service.findById(id)
        accessService.ensureCanRead(listing)
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

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long) {
        service.publish(id)
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
        val listing = service.findById(id)
        accessService.ensureCanRead(listing)
        val images = imageService.search(id, accessService.restrict(listing, request))
        return SearchListingImageResponse(images = images.map { image -> mapper.toListingImage(image) })
    }
}
