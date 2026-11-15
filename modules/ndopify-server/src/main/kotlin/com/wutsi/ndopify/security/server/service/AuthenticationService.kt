package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthenticationService(
    private val provider: AuthenticatorProvider,
) {
    /**
     * `noRollbackFor` keeps the wrong-code count: an authenticator writes it before answering 409, and a rollback
     * with that 409 would make every attempt the first one (no account ever locks).
     */
    @Transactional(noRollbackFor = [ConflictException::class])
    fun authenticate(request: AuthenticateRequest): String {
        val authenticator = provider.get(request.authType)
            ?: throw BadRequestException(
                Error(code = ErrorCode.AUTH_TYPE_NOT_SUPPORTED, parameter = Parameter(value = request.authType)),
            )
        return authenticator.authenticate(request)
    }
}
