package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.CreateMatchVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The agent (or an admin) books a visit for an accepted match: straight to CONFIRMED, the customer e-mailed. */
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CreateMatchVisitEndpoint.sql",
    ],
)
class CreateMatchVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun create() {
        val response = post(1L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(response.body!!.visitId).get()
        assertEquals(1L, visit.matchId)
        assertEquals(1L, visit.mandateId)
        assertEquals(101L, visit.listingId)
        assertEquals(20L, visit.agentId)
        assertEquals(10L, visit.customerId)
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(VisitParty.AGENT, visit.createdBy)
        assertEquals(VisitType.ON_SITE, visit.type)
        assertEquals(slot(2, 10, 0), visit.scheduledAt)
        assertTrue(Regex("^\\d{4}$").matches(visit.code!!), "invalid code ${visit.code}")
        assertNull(visit.reminderSentAt) // more than 24 h ahead

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "customer + agent e-mails")
        val customer = smtp.receivedMessages.single { "ray.sponsible" in it.allRecipients.single().toString() }
        assertEquals("Votre visite de ${weekday(slot(2, 10, 0))} est confirmée", customer.subject)
        assertTrue(customer.content.toString().contains(visit.code!!))
        // The customer's copy is the agent-booked wording ("Je vous confirme la visite que j'ai organisée"); the
        // e-mail body is quoted-printable, so the assertion stays ASCII.
        assertTrue(
            customer.content.toString().contains("Je vous confirme la visite que j'ai organis"),
            customer.content.toString(),
        )
        assertTrue(customer.content.toString().contains("Vous pouvez annuler"), customer.content.toString())
        assertTrue(customer.content.toString().contains("Bonapriso"))
    }

    @Test
    fun `another agent`() {
        userId = 12L // agent 30

        val response = postError(1L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertTrue(visits(1L).isEmpty())
    }

    @Test
    fun `the listing agent of a co-brokerage match`() {
        userId = 12L // agent 30 owns listing 109; match 8 is agent 20's, whose customer accepted it

        val response = postError(8L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertTrue(visits(8L).isEmpty())
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = postError(1L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertTrue(visits(1L).isEmpty())
    }

    @Test
    fun `match not found`() {
        val response = postError(999L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `match not accepted`() {
        val response = postError(4L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACCEPTED, response.body?.error?.code)
        assertTrue(visits(4L).isEmpty())
    }

    @Test
    fun `mandate not approved`() {
        val response = postError(10L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_APPROVED, response.body?.error?.code)
        assertTrue(visits(10L).isEmpty())
    }

    @Test
    fun `listing not published`() {
        val response = postError(11L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_PUBLISHED, response.body?.error?.code)
        assertTrue(visits(11L).isEmpty())
    }

    @Test
    fun `video without whatsapp`() {
        val response = postError(7L, CreateMatchVisitRequest(VisitType.VIDEO, slot(2, 10, 0)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_WHATSAPP_MISSING, response.body?.error?.code)
        assertTrue(visits(7L).isEmpty())
    }

    @Test
    fun `an open visit already exists`() {
        val first = post(1L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(2, 10, 0)))
        assertEquals(HttpStatus.OK, first.statusCode)
        assertTrue(smtp.waitForIncomingEmail(5000, 2), "two e-mails expected")

        val second = postError(2L, CreateMatchVisitRequest(VisitType.ON_SITE, slot(3, 10, 0)))

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
        assertEquals(ErrorCode.VISIT_ALREADY_EXISTS, second.body?.error?.code)
        assertEquals(1, visits(2L).size)
        assertFalse(smtp.waitForIncomingEmail(1000, 3), "no second e-mail")
    }

    @Test
    fun `unknown type`() {
        val response = postError(1L, CreateMatchVisitRequest(VisitType.UNKNOWN, slot(2, 10, 0)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertTrue(visits(1L).isEmpty())
    }

    @Test
    fun `invalid slots`() {
        listOf(
            slot(0, 20, 0), // today
            slot(8, 8, 0), // too far
            slot(1, 21, 0), // too late
            slot(1, 10, 15), // not on a 30-minute boundary
            slot(1, 7, 30), // too early
        ).forEach { at ->
            val response = postError(1L, CreateMatchVisitRequest(VisitType.ON_SITE, at))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "slot $at")
            assertEquals(ErrorCode.VISIT_INVALID_SLOT, response.body?.error?.code, "slot $at")
        }
        assertTrue(visits(1L).isEmpty())
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no e-mail expected")
    }

    private fun visits(matchId: Long): List<VisitEntity> = dao.findAll().filter { it.matchId == matchId }

    private fun post(matchId: Long, request: CreateMatchVisitRequest) =
        rest.postForEntity("/v1/matches/$matchId/visits", request, CreateVisitResponse::class.java)

    private fun postError(matchId: Long, request: CreateMatchVisitRequest) =
        rest.postForEntity("/v1/matches/$matchId/visits", request, ErrorResponse::class.java)

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant())

    private fun weekday(date: Date): String =
        date.toInstant().atZone(zone).format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("fr-CM")))
}
