package com.wutsi.ndopify.party.server.mapper

import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.stereotype.Service

@Service
class PartyMapper {
    fun toParty(entity: PartyEntity): Party {
        return Party(
            id = entity.id ?: -1,
            firstName = entity.firstName,
            lastName = entity.lastName,
            email = entity.email,
            kycStatus = entity.kycStatus,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            photoUrl = entity.photoUrl,
        )
    }
}
