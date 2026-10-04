package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.server.dao.IdentificationImageRepository
import com.wutsi.ndopify.party.server.dao.IdentificationRepository
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.KycStatus
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
class IdentificationService(
    private val dao: IdentificationRepository,
    private val imageDao: IdentificationImageRepository,
    private val clock: Clock,
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

    fun findByParty(party: PartyEntity): List<IdentificationEntity> {
        return dao.findByParty(party)
    }

    @Transactional
    fun create(party: PartyEntity, request: CreateIdentificationRequest): IdentificationEntity {
        val now = Date(clock.millis())

        // ID
        val identification = dao.save(
            IdentificationEntity(
                party = party,
                type = request.type,
                issuingCountryCode = request.issuingCountryCode,
                createdAt = now,
                status = KycStatus.PENDING,
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
                    mimeType = "image/jpeg",
                    path = "kyc/${party.id}/${identification.id}/$imageId.jpeg",
                    createdAt = now,
                    uploaded = false,
                )
            )
        }

        return identification
    }
}
