package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val authenticatorFactory: AuthenticatorFactory
) {
    @Transactional
    fun authenticate(request: AuthenticateRequest, tenantId: Long?): String {
        val authenticator = authenticatorFactory.getAuthenticator(request.authType)
            ?: throw BadRequestException(
                Error(code = ErrorCode.AUTH_TYPE_NOT_SUPPORTED, parameter = Parameter(value = request.authType)),
            )

        return authenticator.authenticate(request, tenantId)
    }
}
