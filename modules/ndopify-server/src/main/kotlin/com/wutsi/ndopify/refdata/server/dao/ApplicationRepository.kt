package com.wutsi.ndopify.refdata.server.dao

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ApplicationRepository : CrudRepository<ApplicationEntity, Long> {
    fun findByCode(code: String): Optional<ApplicationEntity>
}
