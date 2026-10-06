package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.server.dao.IdentificationImageRepository
import com.wutsi.ndopify.party.server.dao.IdentificationRepository
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.util.MimeUtils
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.net.URL
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
class IdentificationService(
    @param:Value("\${ndopify.party.identification.image.url-ttl-seconds}") val imageUrlTTL: Int,

    private val dao: IdentificationRepository,
    private val imageDao: IdentificationImageRepository,
    private val clock: Clock,
    private val partyService: PartyService,
    private val storageProvider: StorageServiceProvider,
) {
    fun findById(id: String): IdentificationEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
    }

    fun findByIdOrNull(id: String): IdentificationEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findImageByIdentificationAndImageType(
        identification: IdentificationEntity,
        imageType: IdentificationImageType
    ): IdentificationImageEntity {
        return imageDao.findByIdentificationAndImageType(identification, imageType)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND,
                    data = mapOf(
                        "identificationId" to identification.id,
                        "imageType" to imageType.name,
                    )
                )
            )
    }

    fun findImageById(id: String): IdentificationImageEntity {
        return imageDao.findById(id)
            .orElseThrow {
                NotFoundException(
                    error = Error(
                        code = ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND,
                        parameter = Parameter(id)
                    )
                )
            }
    }

    fun search(request: SearchIdentificationRequest): List<IdentificationEntity> {
        val spec = Specification<IdentificationEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.partyId?.let { partyId ->
                predicates.add(cb.equal(root.get<PartyEntity>("party").get<Long>("id"), partyId))
            }
            if (request.types.isNotEmpty()) {
                predicates.add(root.get<IdentificationType>("type").`in`(request.types))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<IdentificationStatus>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    @Transactional
    fun create(request: CreateIdentificationRequest): IdentificationEntity {
        val party = partyService.findById(request.partyId)
        val now = Date(clock.millis())

        // ID
        val identification = dao.save(
            IdentificationEntity(
                party = party,
                type = request.type,
                issuingCountryCode = request.issuingCountryCode,
                createdAt = now,
                status = IdentificationStatus.PENDING_VERIFICATION,
            )
        )

        // IMAGES
        request.imageTypes.forEach { imageType ->
            val imageId = UUID.randomUUID().toString()
            imageDao.save(
                IdentificationImageEntity(
                    id = imageId,
                    identification = identification,
                    imageType = imageType,
                    createdAt = now,
                )
            )
        }

        return identification
    }

    @Transactional
    fun upload(id: String, imageType: IdentificationImageType, file: MultipartFile) {
        ensureImage(file)

        val identification = findById(id)
        val image = findImageByIdentificationAndImageType(identification, imageType)
        ensureNotUploaded(image)

        // Store
        val mimeType = file.contentType
        val extension = MimeUtils.getExtensionFromMimeType(mimeType)
        val path = "identifications/${identification.id}/images/${image.id}.$extension"
        val storage = storageProvider.get()
        storage.store(path, file.inputStream, mimeType)

        imageDao.save(
            image.copy(
                path = path,
                storageType = storage.type(),
                mimeType = mimeType,
                uploadedAt = Date(clock.millis()),
            )
        )
    }

    fun getImageUrl(imageId: String): URL {
        val image = findImageById(imageId)
        if (image.path == null) {
            throw NotFoundException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_IMAGE_NO_CONTENT,
                    parameter = Parameter(imageId)
                )
            )
        }
        val storage = storageProvider.get(image.storageType)
        return storage.generatePresignedUrl(image.path, imageUrlTTL)
    }

    private fun ensureNotUploaded(image: IdentificationImageEntity) {
        if (image.path != null) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_IMAGE_ALREADY_UPLOADED,
                    data = mapOf(
                        "identificationId" to image.identification.id,
                        "imageType" to image.imageType.name,
                    )
                )
            )
        }
    }

    private fun ensureImage(file: MultipartFile) {
        val mimeType = file.contentType
        if (mimeType?.startsWith("image/") != true) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_IMAGE_INVALID_MIME_TYPE,
                    data = mapOf(
                        "mimeType" to (mimeType ?: ""),
                    )
                )
            )
        }
    }
}
