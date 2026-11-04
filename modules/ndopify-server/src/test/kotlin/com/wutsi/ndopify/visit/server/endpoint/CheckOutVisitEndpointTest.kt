package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CheckOutVisitEndpoint.sql"])
class CheckOutVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun checkOut() {
        val response = rest.postForEntity(
            "/v1/visits/1/check-out",
            CheckOutVisitRequest(comment = "Client intéressé", latitude = 4.051550, longitude = 9.767900),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.COMPLETED, visit.status)
        assertNotNull(visit.checkedOutAt)
        assertEquals("Client intéressé", visit.agentComment)
        assertEquals(4.051550, visit.checkOutLatitude)

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: votre visite est terminée", message.subject)
        assertTrue(message.content.toString().contains("Le bien vous int"))
    }

    @Test
    fun `less than 10 minutes after the check-in`() {
        val response = rest.postForEntity(
            "/v1/visits/2/check-out",
            CheckOutVisitRequest(comment = "Trop tôt"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_OUTSIDE_WINDOW, response.body?.error?.code)
        assertEquals(VisitStatus.CHECKED_IN, dao.findById(2L).get().status)
    }

    @Test
    fun `empty comment`() {
        val response = rest.postForEntity("/v1/visits/1/check-out", CheckOutVisitRequest(comment = ""), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(VisitStatus.CHECKED_IN, dao.findById(1L).get().status)
    }

    @Test
    fun `not checked in`() {
        val response = rest.postForEntity(
            "/v1/visits/3/check-out",
            CheckOutVisitRequest(comment = "Pas encore arrivé"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_CHECKED_IN, response.body?.error?.code)
    }

    @Test
    fun `visit of another agent`() {
        val response = rest.postForEntity(
            "/v1/visits/4/check-out",
            CheckOutVisitRequest(comment = "Pas la mienne"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `as the customer`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/check-out",
            CheckOutVisitRequest(comment = "Pas moi"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `visit not found`() {
        val response = rest.postForEntity(
            "/v1/visits/999/check-out",
            CheckOutVisitRequest(comment = "Aucune"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/visits/1/check-out",
            CheckOutVisitRequest(comment = "Anonyme"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
