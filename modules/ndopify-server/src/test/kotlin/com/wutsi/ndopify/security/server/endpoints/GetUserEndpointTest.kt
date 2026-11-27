package com.wutsi.ndopify.security.server.endpoints

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.security.dto.GetUserResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/security/GetUserEndpoint.sql"])
class GetUserEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun get() {
        userId = 1L

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
    fun `party of the tenant of the request`() {
        userId = 1L
        overrideTenantId = 2L

        val response = rest.getForEntity("/v1/users/1", GetUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1L, response.body!!.user.id)
        assertEquals(901L, response.body!!.user.party?.id)
        assertEquals("Raymond", response.body!!.user.party?.firstName)
    }

    @Test
    fun `user without party`() {
        userId = 2L

        val response = rest.getForEntity("/v1/users/2", GetUserResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2L, response.body!!.user.id)
        assertNull(response.body!!.user.party)
    }

    @Test
    fun `another user - forbidden`() {
        userId = 2L
        val response = rest.getForEntity("/v1/users/1", ErrorResponse::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `admin reads another user`() {
        admin = true
        assertEquals(HttpStatus.OK, rest.getForEntity("/v1/users/1", GetUserResponse::class.java).statusCode)
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

    @Test
    fun `user whose membership is deleted is not found`() {
        assertNotFound("/v1/users/5")
    }

    private fun assertNotFound(url: String) {
        val response = rest.getForEntity(url, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.USER_NOT_FOUND, response.body?.error?.code)
    }
}
