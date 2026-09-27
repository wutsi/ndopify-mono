package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.stereotype.Service

@Service
class UserApplicationService(private val dao: UserApplicationRepository) {
    fun findByUserAndApplicationOrNull(user: UserEntity, application: ApplicationEntity): UserApplicationEntity? {
        return dao.findByUserAndApplication(user, application)
            .orElse(null)
    }

    fun findByUserAndApplicationOrCreate(user: UserEntity, application: ApplicationEntity): UserApplicationEntity {
        val userApp = findByUserAndApplicationOrNull(user, application)
        return if (userApp == null) {
            UserApplicationEntity(
                user = user,
                application = application
            )
        } else {
            userApp
        }
    }
}
