package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserApplicationRepository : CrudRepository<UserApplicationEntity, Long> {
    fun findByMembershipAndApplication(
        membership: MembershipEntity,
        application: ApplicationEntity
    ): Optional<UserApplicationEntity>
}
