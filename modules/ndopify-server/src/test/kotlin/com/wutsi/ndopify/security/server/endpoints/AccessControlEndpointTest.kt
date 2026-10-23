package com.wutsi.ndopify.security.server.endpoints

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every endpoint needs a token but the public ones, and the `X-Tenant-ID` header alone opens none of a tenant's data. */
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/GetContractSignatureEndpoint.sql"])
class AccessControlEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `protected endpoint without token`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/agents/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    @Test
    fun `protected endpoint of the same path as a public one, without token`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/agents/1", mapOf("biography" to "x"), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    @Test
    fun `unknown path without token`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/unknown", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `public endpoint without token`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/contracts/c-100/signatures/${"f".repeat(64)}", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `global endpoint without token nor tenant`() {
        anonymousUser = true
        ignoreTenantIdHeader = true

        val response = rest.getForEntity("/v1/tenants", SearchTenantResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
