package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface IdentificationImageRepository : CrudRepository<IdentificationImageEntity, String>
