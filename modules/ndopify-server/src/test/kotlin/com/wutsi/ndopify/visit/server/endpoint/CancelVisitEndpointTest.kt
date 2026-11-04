package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
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

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CancelVisitEndpoint.sql"])
class CancelVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    @Test
    fun `the customer cancels a requested visit`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/cancel",
            CancelVisitRequest(reason = "Empêchement"),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.CANCELLED, visit.status)
        assertEquals(VisitParty.CUSTOMER, visit.cancelledBy)
        assertEquals("Empêchement", visit.cancellationReason)
        assertNotNull(visit.cancelledAt)

        // The other side is told: the agent
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Kim Possible <kim.possible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: la visite a été annulée", message.subject)
        val body = message.content.toString()
        assertTrue(body.contains("Ray Sponsible"))
        assertTrue(body.contains("Emp"))
    }

    @Test
    fun `the customer cancels a confirmed visit`() {
        userId = 13L

        val response = rest.postForEntity("/v1/visits/2/cancel", CancelVisitRequest(), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitStatus.CANCELLED, dao.findById(2L).get().status)
        assertEquals(VisitParty.CUSTOMER, dao.findById(2L).get().cancelledBy)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
    }

    @Test
    fun `the agent cancels`() {
        val response = rest.postForEntity(
            "/v1/visits/2/cancel",
            CancelVisitRequest(reason = "Le bien est loué"),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(2L).get()
        assertEquals(VisitStatus.CANCELLED, visit.status)
        assertEquals(VisitParty.AGENT, visit.cancelledBy)
        assertEquals("Le bien est loué", visit.cancellationReason)

        // The other side is told: the customer
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertTrue(message.content.toString().contains("Le bien est lou"))
    }

    @Test
    fun `cannot be cancelled`() {
        listOf(3L, 4L, 5L).forEach { id -> // CHECKED_IN, COMPLETED, CANCELLED
            val response = rest.postForEntity("/v1/visits/$id/cancel", CancelVisitRequest(), ErrorResponse::class.java)

            assertEquals(HttpStatus.CONFLICT, response.statusCode, "visit $id")
            assertEquals(ErrorCode.VISIT_CANNOT_BE_CANCELLED, response.body?.error?.code, "visit $id")
        }
    }

    @Test
    fun `another party`() {
        userId = 15L

        val response = rest.postForEntity("/v1/visits/1/cancel", CancelVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(VisitStatus.REQUESTED, dao.findById(1L).get().status)
    }

    @Test
    fun `visit not found`() {
        val response = rest.postForEntity("/v1/visits/999/cancel", CancelVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/visits/1/cancel", CancelVisitRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
