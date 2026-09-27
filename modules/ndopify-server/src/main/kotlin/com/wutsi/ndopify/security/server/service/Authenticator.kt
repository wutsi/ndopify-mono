package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.security.dto.AuthenticateRequest

interface Authenticator {
    fun authenticate(request: AuthenticateRequest, tenantId: Long?): String
}
