package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/NoShowVisitEndpoint.sql"])
class NoShowVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun `customer absent`() {
        val response = rest.postForEntity(
            "/v1/visits/1/no-show",
            NoShowVisitRequest(reason = VisitNoShowReason.CUSTOMER_ABSENT, latitude = 4.051550, longitude = 9.767900),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.NO_SHOW, visit.status)
        assertEquals(VisitParty.CUSTOMER, visit.noShowParty)
        assertEquals(VisitNoShowReason.CUSTOMER_ABSENT, visit.noShowReason)
        assertEquals(4.051550, visit.noShowLatitude)
        assertEquals(9.767900, visit.noShowLongitude)

        // The agent reported it: only the customer is told
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: la visite n'a pas eu lieu", message.subject)
        assertTrue(message.content.toString().contains("Nice apartment"))
    }

    @Test
    fun `code refused`() {
        val response = rest.postForEntity(
            "/v1/visits/6/no-show",
            NoShowVisitRequest(reason = VisitNoShowReason.CODE_REFUSED),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(6L).get()
        assertEquals(VisitStatus.NO_SHOW, visit.status)
        assertEquals(VisitNoShowReason.CODE_REFUSED, visit.noShowReason)
        assertNotNull(visit.noShowParty)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
    }

    @Test
    fun `too early`() {
        val response = rest.postForEntity("/v1/visits/2/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_OUTSIDE_WINDOW, response.body?.error?.code)
    }

    @Test
    fun `too late`() {
        val response = rest.postForEntity("/v1/visits/3/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_OUTSIDE_WINDOW, response.body?.error?.code)
    }

    @Test
    fun `an agent can't report the agent's own absence`() {
        val response = rest.postForEntity(
            "/v1/visits/1/no-show",
            NoShowVisitRequest(reason = VisitNoShowReason.AGENT_ABSENT),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `a cancelled visit stays cancelled`() {
        val response = rest.postForEntity("/v1/visits/4/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_CONFIRMED, response.body?.error?.code)
    }

    @Test
    fun `visit of another agent`() {
        val response = rest.postForEntity("/v1/visits/5/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `as the customer`() {
        userId = 13L

        val response = rest.postForEntity("/v1/visits/1/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/visits/1/no-show", NoShowVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
