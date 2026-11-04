package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/GetVisitEndpoint.sql"])
class GetVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `the agent never sees the code nor the address`() {
        val response = rest.getForEntity("/v1/visits/1", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = response.body!!.visit
        assertEquals(1L, visit.id)
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(VisitType.ON_SITE, visit.type)
        assertNull(visit.code)
        assertNull(visit.address)
        assertNull(visit.latitude)
        assertNull(visit.longitude)
        assertEquals("Client en retard", visit.agentComment) // Private to the agent and the admins
    }

    @Test
    fun `the customer sees the code and the address of an ON_SITE visit`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits/1", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = response.body!!.visit
        assertEquals("1234", visit.code)
        assertEquals("Rue 1.234, Bonapriso, Derrière la pharmacie", visit.address)
        assertEquals(4.0511, visit.latitude)
        assertEquals(9.7679, visit.longitude)
        assertNull(visit.agentComment) // Private to the agent and the admins
    }

    @Test
    fun `the customer sees the code but no address of a VIDEO visit`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits/2", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = response.body!!.visit
        assertEquals(VisitType.VIDEO, visit.type)
        assertEquals("5678", visit.code)
        assertNull(visit.address)
        assertNull(visit.latitude)
        assertNull(visit.longitude)
    }

    @Test
    fun `a requested visit has no code nor address yet`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits/3", GetVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitStatus.REQUESTED, response.body!!.visit.status)
        assertNull(response.body!!.visit.code)
        assertNull(response.body!!.visit.address)
    }

    @Test
    fun `another party`() {
        userId = 15L

        val response = rest.getForEntity("/v1/visits/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `visit not found`() {
        val response = rest.getForEntity("/v1/visits/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `visit of another tenant is not found`() {
        val response = rest.getForEntity("/v1/visits/9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/visits/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
