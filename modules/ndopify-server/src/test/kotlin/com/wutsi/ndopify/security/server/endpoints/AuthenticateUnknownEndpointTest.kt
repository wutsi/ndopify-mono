package com.wutsi.ndopify.security.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticateUnknownEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun login() {
        Thread.sleep(1000L)

        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.UNKNOWN,
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        assertEquals(ErrorCode.AUTH_TYPE_NOT_SUPPORTED, response.body?.error?.code)
    }
}
