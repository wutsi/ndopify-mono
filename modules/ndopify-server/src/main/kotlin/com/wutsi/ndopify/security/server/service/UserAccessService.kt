package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.stereotype.Service

/**
 * Who may read a user: the user itself or an admin (see [CallerService.isAdmin]). 401 AUTH_UNAUTHORIZED without a
 * token, 403 HTTP_ACCESS_DENIED otherwise. Unknown, deleted and non-member ids keep their 404 USER_NOT_FOUND (see
 * [UserService.findById], which runs first).
 */
@Service
class UserAccessService(private val callerService: CallerService) {
    /** The user itself or an admin. 401 without a token, 403 HTTP_ACCESS_DENIED otherwise. */
    fun ensureCanRead(user: UserEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }
        if (user.id != null && principal.getUserId() == user.id) {
            return
        }
        throw accessDenied()
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
