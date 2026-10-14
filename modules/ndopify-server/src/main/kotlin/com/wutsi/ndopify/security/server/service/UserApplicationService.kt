package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import org.springframework.stereotype.Service

@Service
class UserApplicationService(private val dao: UserApplicationRepository) {
    fun findByMembershipAndApplicationOrNull(
        membership: MembershipEntity,
        application: ApplicationEntity
    ): UserApplicationEntity? {
        return dao.findByMembershipAndApplication(membership, application)
            .orElse(null)
    }

    fun findByMembershipAndApplicationOrCreate(
        membership: MembershipEntity,
        application: ApplicationEntity
    ): UserApplicationEntity {
        val userApp = findByMembershipAndApplicationOrNull(membership, application)
        return if (userApp == null) {
            UserApplicationEntity(
                membership = membership,
                application = application
            )
        } else {
            userApp
        }
    }
}
