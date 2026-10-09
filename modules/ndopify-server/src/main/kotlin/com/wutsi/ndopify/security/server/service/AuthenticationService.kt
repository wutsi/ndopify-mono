package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
    private val provider: AuthenticatorProvider,
) {
    @Transactional
    fun authenticate(request: AuthenticateRequest): String {
        val authenticator = provider.get(request.authType)
            ?: throw BadRequestException(
                Error(code = ErrorCode.AUTH_TYPE_NOT_SUPPORTED, parameter = Parameter(value = request.authType)),
            )
        val tenantId = TenantContext.get()
        return authenticator.authenticate(request, tenantId)
    }
}
