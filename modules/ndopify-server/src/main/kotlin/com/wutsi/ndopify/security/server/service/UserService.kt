package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class UserService(
    private val dao: UserRepository,
    private val userApplicationDao: UserApplicationRepository,
    private val applicationService: ApplicationService,
) {
    /**
     * Users aren't @TenantId-filtered (see [UserEntity.tenantId]), so a user whose home tenant isn't the current
     * one is reported as not found, like any tenant-scoped entity. Cross-tenant users (no home tenant) are visible.
     */
    fun findById(id: Long): UserEntity {
        val user = findByIdOrNull(id)
        return user
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.USER_NOT_FOUND,
                    message = "User not found: $id",
                )
            )
    }

    fun findByIdOrNull(id: Long): UserEntity? {
        val tenantId = TenantContext.get()
        return if (dao.isVisible(id, tenantId)) {
            dao.findById(id).orElse(null)
        } else {
            null
        }
    }

    fun findByPartyIdOrNull(partyId: Long): UserEntity? {
        val user = dao.findByPartyId(partyId)
        if (user == null || user.deleted) {
            return null
        }
        return user
    }

    fun findByEmail(email: String): UserEntity {
        return findByEmailOrNull(email)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.USER_NOT_FOUND,
                    message = "User not found: $email",
                )
            )
    }

    fun findByEmailOrNull(email: String): UserEntity? {
        val user = dao.findByEmailIgnoreCase(email).orElse(null)
        if (user == null || user.deleted) {
            return null
        }
        return user
    }

    @Transactional
    fun findByEmailOrCreate(email: String): UserEntity {
        val user = findByEmailOrNull(email)
        return if (user == null) {
            dao.save(UserEntity(email = email.lowercase()))
        } else {
            user
        }
    }

    @Transactional
    fun create(party: PartyEntity): UserEntity {
        return dao.save(
            UserEntity(
                tenantId = party.tenantId,
                email = party.email.lowercase(),
                party = party
            )
        )
    }

    @Transactional
    fun grantAccess(user: UserEntity, applicationCode: String) {
        val application = applicationService.findByCode(applicationCode)
        userApplicationDao.save(
            UserApplicationEntity(
                user = user,
                application = application,
            )
        )
    }
}
