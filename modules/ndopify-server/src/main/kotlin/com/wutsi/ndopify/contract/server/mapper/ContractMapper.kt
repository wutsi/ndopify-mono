package com.wutsi.ndopify.contract.server.mapper

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractParty
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import org.springframework.stereotype.Service

@Service
class ContractMapper {
    fun toContract(entity: ContractEntity, parties: List<ContractPartyEntity>): Contract {
        return Contract(
            id = entity.id,
            number = entity.number,
            type = entity.type,
            status = entity.status,
            contentUrl = entity.contentUrl,
            parties = parties.map { party -> toContractParty(party) },
            createdAt = entity.createdAt,
            durationMonths = entity.durationMonths,
            signatureDeadline = entity.signatureDeadline,
            validFrom = entity.validFrom,
            validUntil = entity.validUntil,
            signedAt = entity.signedAt,
        )
    }

    fun toContractParty(entity: ContractPartyEntity): ContractParty {
        return ContractParty(
            id = entity.id,
            role = entity.role,
            partyId = entity.partyId,
            legalName = entity.legalName,
            legalEmail = entity.legalEmail,
            signed = entity.signed,
            ipAddress = entity.ipAddress,
            userAgent = entity.userAgent,
            signedAt = entity.signedAt,
        )
    }
}
