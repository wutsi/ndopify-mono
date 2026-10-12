package com.wutsi.ndopify.security.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.security.dto.GetUserResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/security/GetUserEndpoint.sql"])
class GetUserEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/users/1", GetUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val user = response.body!!.user
        assertEquals(1L, user.id)
        assertEquals("ray.sponsible@gmail.com", user.email)

        val party = user.party!!
        assertEquals(100L, party.id)
        assertEquals("Ray", party.firstName)
        assertEquals("Sponsible", party.lastName)
    }

    @Test
    fun `user without party`() {
        val response = rest.getForEntity("/v1/users/2", GetUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2L, response.body!!.user.id)
        assertNull(response.body!!.user.party)
    }

    @Test
    fun `user not found`() {
        assertNotFound("/v1/users/999")
    }

    @Test
    fun `deleted user is not found`() {
        assertNotFound("/v1/users/3")
    }

    @Test
    fun `user from another tenant is not found`() {
        assertNotFound("/v1/users/4")
    }

    private fun assertNotFound(url: String) {
        val response = rest.getForEntity(url, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.USER_NOT_FOUND, response.body?.error?.code)
    }
}
