package com.wutsi.ndopify.party.server.mapper

import com.wutsi.ndopify.party.dto.Identification
import com.wutsi.ndopify.party.dto.IdentificationImage
import com.wutsi.ndopify.party.dto.IdentificationSummary
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import org.springframework.stereotype.Service

@Service
class IdentificationMapper {
    fun toIdentificationSummary(entity: IdentificationEntity): IdentificationSummary {
        return IdentificationSummary(
            id = entity.id,
            partyId = entity.party.id ?: -1,
            type = entity.type,
            issuingCountryCode = entity.issuingCountryCode,
            numberSuffix = entity.numberSuffix,
            status = entity.status,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toIdentification(entity: IdentificationEntity): Identification {
        return Identification(
            id = entity.id,
            partyId = entity.party.id ?: -1,
            type = entity.type,
            issuingCountryCode = entity.issuingCountryCode,
            numberSuffix = entity.numberSuffix,
            issuedAt = entity.issuedAt,
            expiresAt = entity.expiresAt,
            status = entity.status,
            images = entity.images.map { image -> toIdentificationImage(image) },
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toIdentificationImage(entity: IdentificationImageEntity): IdentificationImage {
        return IdentificationImage(
            id = entity.id,
            imageType = entity.imageType,
            createdAt = entity.createdAt,
            uploadedAt = entity.uploadedAt,
        )
    }
}
