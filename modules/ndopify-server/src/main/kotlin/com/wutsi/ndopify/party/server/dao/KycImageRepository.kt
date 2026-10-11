package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KycImageRepository : CrudRepository<KycImageEntity, String> {
    fun findByKycCaseAndIdentificationTypeAndImageType(
        kycCase: KycCaseEntity,
        identificationType: IdentificationType,
        imageType: KycImageType
    ): KycImageEntity?
}
