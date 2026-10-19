package com.wutsi.ndopify.contract.server.mapper

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractParty
import com.wutsi.ndopify.contract.dto.ContractSummary
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
            signedContentUrl = entity.signedContentUrl,
            parties = parties.map { party -> toContractParty(party) },
            createdAt = entity.createdAt,
            durationMonths = entity.durationMonths,
            signatureRequestedAt = entity.signatureRequestedAt,
            signatureDeadline = entity.signatureDeadline,
            validFrom = entity.validFrom,
            validUntil = entity.validUntil,
            signedAt = entity.signedAt,
        )
    }

    fun toContractSummary(entity: ContractEntity): ContractSummary {
        return ContractSummary(
            id = entity.id,
            number = entity.number,
            type = entity.type,
            status = entity.status,
            signedContentUrl = entity.signedContentUrl,
            createdAt = entity.createdAt,
            durationMonths = entity.durationMonths,
            signatureRequestedAt = entity.signatureRequestedAt,
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
            signatureRequired = entity.signatureRequired,
            signed = entity.signed,
            ipAddress = entity.ipAddress,
            userAgent = entity.userAgent,
            signedAt = entity.signedAt,
        )
    }
}
