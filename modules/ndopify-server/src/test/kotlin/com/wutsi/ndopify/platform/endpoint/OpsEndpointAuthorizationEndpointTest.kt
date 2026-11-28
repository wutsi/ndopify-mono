package com.wutsi.ndopify.platform.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchMandateEndpoint.sql"])
class OpsEndpointAuthorizationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private fun dispatch() = rest.postForEntity("/v1/mandates/999/dispatch", null, ErrorResponse::class.java)

    @Test
    fun `a non-admin is forbidden`() {
        val response = dispatch()

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `an admin reaches the endpoint`() {
        admin = true

        val response = dispatch()

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `an anonymous caller is unauthorized`() {
        anonymousUser = true

        val response = dispatch()

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
