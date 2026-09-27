package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserApplicationRepository : CrudRepository<UserApplicationEntity, Long> {
    fun findByUserAndApplication(user: UserEntity, application: ApplicationEntity): Optional<UserApplicationEntity>
}
