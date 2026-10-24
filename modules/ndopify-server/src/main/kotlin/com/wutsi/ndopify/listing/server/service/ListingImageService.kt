package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.SearchListingImageRequest
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.util.Date
import java.util.UUID

/** The photos of a listing. No access check: they're public, and any caller with a token may add one. */
@Service
class ListingImageService(
    private val dao: ListingImageRepository,
    private val listingService: ListingService,
    private val storageProvider: StorageServiceProvider,
    private val clock: Clock,
) {
    companion object {
        val IMAGE_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
    }

    /**
     * The listing's images, oldest first, in one of [SearchListingImageRequest.statuses] (any if empty). 404
     * LISTING_NOT_FOUND for an unknown listing.
     */
    fun search(listingId: Long, request: SearchListingImageRequest): List<ListingImageEntity> {
        listingService.findById(listingId)
        return if (request.statuses.isEmpty()) {
            dao.findByListingIdOrderById(listingId)
        } else {
            dao.findByListingIdAndStatusInOrderById(listingId, request.statuses)
        }
    }

    /** 404 LISTING_IMAGE_NOT_FOUND for an image that isn't the listing's. */
    fun findById(listingId: Long, id: Long): ListingImageEntity {
        return dao.findByIdAndListingId(id, listingId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_IMAGE_NOT_FOUND))
    }

    /** Adds a photo (a JPEG, PNG or WEBP image) to the listing, PENDING_VERIFICATION. */
    @Transactional
    fun upload(listingId: Long, file: MultipartFile): ListingImageEntity {
        val listing = listingService.findById(listingId)
        val mimeType = ensureImage(file)

        // A new path each time: the storage is public, an image is never overwritten
        val path = "listings/${listing.id}/images/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
        val storage = storageProvider.get()
        storage.store(path, file.inputStream, mimeType)

        val now = Date(clock.millis())
        return dao.save(
            ListingImageEntity(
                listingId = listingId,
                path = path,
                url = storage.publicUrl(path).toString(),
                mimeType = mimeType,
                status = ImageStatus.PENDING_VERIFICATION,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    private fun ensureImage(file: MultipartFile): String {
        val mimeType = file.contentType?.split(";")?.first()?.trim()?.lowercase()
        if (file.isEmpty || mimeType !in IMAGE_MIME_TYPES) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    message = "${file.originalFilename} is a $mimeType - The image must be a JPEG, PNG or WEBP image.",
                    parameter = Parameter(
                        name = "multipartFile",
                        type = ParameterType.PARAMETER_TYPE_PAYLOAD,
                        value = mimeType,
                    ),
                )
            )
        }
        return mimeType!!
    }
}
