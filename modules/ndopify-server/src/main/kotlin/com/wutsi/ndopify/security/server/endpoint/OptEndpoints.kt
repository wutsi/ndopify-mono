package com.wutsi.ndopify.security.server.endpoint

import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.server.service.AuthenticationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth")
class AuthenticationEndpoints(private val service: AuthenticationService) {
    @PostMapping
    fun login(
        @RequestHeader(required = false, name = "X-Tenant-ID") tenantId: Long? = null,
        @Valid @RequestBody request: AuthenticateRequest
    ): AuthenticateResponse {
        val accessToken = service.authenticate(request, tenantId)
        return AuthenticateResponse(accessToken)
    }
}
