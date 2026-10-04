package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface IdentificationRepository : CrudRepository<IdentificationEntity, Long>
