package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MandateRepository : CrudRepository<MandateEntity, Long>, JpaSpecificationExecutor<MandateEntity>
