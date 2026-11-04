package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CreateVisitEndpoint.sql"])
class CreateVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun create() {
        asCustomer()

        val response = post(request(matchId = 1L, note = "Je viens avec ma femme"))

        assertEquals(HttpStatus.OK, response.statusCode)
        val id = response.body!!.visitId
        val visit = dao.findById(id).get()
        assertEquals(1L, visit.matchId)
        assertEquals(1L, visit.mandateId)
        assertEquals(101L, visit.listingId)
        assertEquals(20L, visit.agentId)
        assertEquals(10L, visit.customerId)
        assertEquals(VisitStatus.REQUESTED, visit.status)
        assertEquals(VisitType.ON_SITE, visit.type)
        assertEquals(slot(1, 10, 0), visit.requestedAt)
        assertEquals("Je viens avec ma femme", visit.note)

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val message = smtp.receivedMessages.single()
        assertEquals("Kim Possible <kim.possible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Ray Sponsible demande une visite", message.subject)
        val body = message.content.toString()
        assertTrue(body.contains("Nice apartment"))
        assertTrue(body.contains(date(1)))
        assertTrue(body.contains("10:00"))
        assertTrue(body.contains("Je viens avec ma femme"))
        assertTrue(body.contains("https://partner.ndopify.com/visits/$id"))
    }

    @Test
    fun `video visit`() {
        asCustomer()

        val response = post(request(matchId = 1L, type = VisitType.VIDEO))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitType.VIDEO, dao.findById(response.body!!.visitId).get().type)
    }

    @Test
    fun `video visit without whatsapp`() {
        userId = 16L

        val response = postError(request(matchId = 7L, type = VisitType.VIDEO))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_WHATSAPP_MISSING, response.body?.error?.code)
    }

    @Test
    fun `unknown type`() {
        asCustomer()

        val response = postError(request(matchId = 1L, type = VisitType.UNKNOWN))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `invalid slots`() {
        asCustomer()

        listOf(
            slot(0, 20, 0), // today
            slot(8, 8, 0), // too far
            slot(1, 21, 0), // too late
            slot(1, 10, 15), // not on a 30-minute boundary
            slot(1, 7, 30), // too early
        ).forEach { at ->
            val response = postError(request(matchId = 1L, at = at))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "slot $at")
            assertEquals(ErrorCode.VISIT_INVALID_SLOT, response.body?.error?.code, "slot $at")
        }
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    @Test
    fun `match not accepted`() {
        asCustomer()

        val response = postError(request(matchId = 4L))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACCEPTED, response.body?.error?.code)
    }

    @Test
    fun `an open visit already exists`() {
        asCustomer()
        val first = post(request(matchId = 1L))
        assertEquals(HttpStatus.OK, first.statusCode)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")

        val second = postError(request(matchId = 1L))

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
        assertEquals(ErrorCode.VISIT_ALREADY_EXISTS, second.body?.error?.code)
        assertEquals(1, dao.findAll().count { it.matchId == 1L && it.status == VisitStatus.REQUESTED })
        assertFalse(smtp.waitForIncomingEmail(1000, 2), "one e-mail only")
    }

    @Test
    fun `a cancelled visit does not prevent a new one`() {
        asCustomer()

        val response = post(request(matchId = 1L))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    }

    @Test
    fun `as the agent`() {
        val response = postError(request(matchId = 1L))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = postError(request(matchId = 1L))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun asCustomer() {
        userId = 13L
    }

    private fun post(request: CreateVisitRequest) =
        rest.postForEntity("/v1/visits", request, CreateVisitResponse::class.java)

    private fun postError(request: CreateVisitRequest) =
        rest.postForEntity("/v1/visits", request, ErrorResponse::class.java)

    private fun request(
        matchId: Long,
        type: VisitType = VisitType.ON_SITE,
        at: Date = slot(1, 10, 0),
        note: String? = null,
    ) = CreateVisitRequest(matchId = matchId, type = type, requestedAt = at, note = note)

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant())

    private fun date(dayOffset: Long): String =
        LocalDate.now(zone).plusDays(dayOffset).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
