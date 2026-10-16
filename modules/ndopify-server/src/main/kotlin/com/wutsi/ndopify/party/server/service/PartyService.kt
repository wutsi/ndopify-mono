package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreatePartyRequest
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchPartyRequest
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
class PartyService(
    private val dao: PartyRepository,
    private val storageProvider: StorageServiceProvider,
    private val clock: Clock,
) {
    companion object {
        val PHOTO_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
    }

    fun findById(id: Long): PartyEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.PARTY_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
    }

    fun findByIdOrNull(id: Long): PartyEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findByEmailOrNull(email: String): PartyEntity? {
        return dao.findByEmail(email.lowercase())
    }

    fun search(request: SearchPartyRequest): List<PartyEntity> {
        val spec = Specification<PartyEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.id.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.id))
            }
            request.email?.takeIf { it.isNotBlank() }?.let { email ->
                predicates.add(cb.equal(root.get<String>("email"), email.trim().lowercase()))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, Sort.by("id"))).content
    }

    @Transactional
    fun create(request: CreatePartyRequest): PartyEntity {
        ensureEmailUnique(request.email.lowercase())

        val now = Date(clock.millis())
        return dao.save(
            PartyEntity(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email.lowercase(),
                kycStatus = KycStatus.PENDING,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    @Transactional
    fun findOrCreate(request: CreatePartyRequest): PartyEntity {
        return findByEmailOrNull(request.email)
            ?: create(request)
    }

    /**
     * The party's email is its contact email in this tenant only: it isn't synced to the user's login email, which
     * is global (shared by every tenant the user is a member of).
     */
    @Transactional
    fun update(party: PartyEntity, request: UpdatePartyRequest): PartyEntity {
        val email = request.email?.lowercase()
        if (email != null) {
            ensureEmailUnique(email, party.id)
        }

        val now = Date(clock.millis())
        return dao.save(
            party.copy(
                firstName = request.firstName ?: party.firstName,
                lastName = request.lastName ?: party.lastName,
                email = email ?: party.email,
                modifiedAt = now,
            )
        )
    }

    /**
     * Stores the party's profile photo and makes it its [PartyEntity.photoUrl]. The photo is public (customers see
     * it, ex: in the e-mail of an assigned mandate), so it gets the storage's permanent URL, not a presigned one.
     * Each upload gets a new file name, so mail clients and caches never show a replaced photo.
     */
    @Transactional
    fun uploadPhoto(id: Long, file: MultipartFile): PartyEntity {
        val party = findById(id)
        val mimeType = ensurePhoto(file)

        val path = "party/${party.id}/photo/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"
        val storage = storageProvider.get()
        storage.store(path, file.inputStream, mimeType)

        val now = Date(clock.millis())
        return dao.save(
            party.copy(
                photoUrl = storage.publicUrl(path).toString(),
                modifiedAt = now,
            )
        )
    }

    @Transactional
    fun updateKyStatus(party: PartyEntity, kycStatus: KycStatus): PartyEntity {
        val now = Date(clock.millis())
        return dao.save(
            party.copy(
                kycStatus = kycStatus,
                modifiedAt = now,
            )
        )
    }

    private fun ensureEmailUnique(email: String, partyId: Long? = null) {
        val existingParty = findByEmailOrNull(email)
        if (existingParty != null && existingParty.id != partyId) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.PARTY_EMAIL_ALREADY_EXISTS,
                    parameter = Parameter(value = email)
                )
            )
        }
    }

    private fun ensurePhoto(file: MultipartFile): String {
        val mimeType = file.contentType?.split(";")?.first()?.trim()?.lowercase()
        if (file.isEmpty || mimeType !in PHOTO_MIME_TYPES) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    message = "${file.originalFilename} is a $mimeType - The photo must be a JPEG, PNG or WEBP image.",
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
