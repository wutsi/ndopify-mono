package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.security.server.dao.MembershipRepository
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class UserService(
    private val dao: UserRepository,
    private val membershipDao: MembershipRepository,
) {
    /**
     * Users are global, so a user who isn't a member of the current tenant is reported as not found, like any
     * tenant-scoped entity (memberships are @TenantId-filtered).
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
        val user = dao.findById(id).orElse(null)
        if (user == null || user.deleted) {
            return null
        }
        val membership = membershipDao.findByUser(user)
        return if (membership == null || membership.deleted) null else user
    }

    fun findByPartyIdOrNull(partyId: Long): UserEntity? {
        val membership = membershipDao.findByPartyId(partyId)
        if (membership == null || membership.deleted || membership.user.deleted) {
            return null
        }
        return membership.user
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

    /** Global lookup: doesn't check that the user is a member of the current tenant. */
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
}
