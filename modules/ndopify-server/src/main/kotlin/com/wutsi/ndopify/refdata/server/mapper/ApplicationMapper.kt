package com.wutsi.ndopify.refdata.server.mapper

import com.wutsi.ndopify.refdata.dto.Application
import com.wutsi.ndopify.refdata.dto.Role
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.domain.RoleEntity
import org.springframework.stereotype.Service

@Service
class ApplicationMapper {
    fun toApplication(entity: ApplicationEntity): Application {
        return Application(
            id = entity.id,
            code = entity.code,
            roles = entity.roles.map { role -> toRole(role) },
            supportedAuthTypes = entity.supportedAuthTypes,
        )
    }

    fun toRole(entity: RoleEntity): Role {
        return Role(
            id = entity.id ?: -1L,
            code = entity.code,
            active = entity.active,
        )
    }
}
