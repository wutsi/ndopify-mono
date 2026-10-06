package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KycCaseRepository : CrudRepository<KycCaseEntity, String>, JpaSpecificationExecutor<KycCaseEntity>
