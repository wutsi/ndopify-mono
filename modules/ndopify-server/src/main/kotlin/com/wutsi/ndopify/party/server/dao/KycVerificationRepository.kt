package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KycVerificationRepository : CrudRepository<KycVerificationEntity, String>
