package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/ScheduleVisitEndpoint.sql"])
class ScheduleVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun schedule() {
        val response = rest.postForEntity("/v1/visits/1/schedule", ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val visit = dao.findById(1L).get()
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertEquals(slot(2, 10, 0), visit.scheduledAt)
        assertEquals(VisitType.ON_SITE, visit.type)
        assertTrue(Regex("^\\d{4}$").matches(visit.code!!), "invalid code ${visit.code}")
        assertNull(visit.reminderSentAt) // More than 24h ahead

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "two emails expected")
        val messages = smtp.receivedMessages.toList()
        val customerMessage = messages.single { it.allRecipients.single().toString() == "Ray Sponsible <ray.sponsible@gmail.com>" }
        assertEquals("Ndopify: votre visite est confirmée", customerMessage.subject)
        val customerBody = customerMessage.content.toString()
        assertTrue(customerBody.contains(visit.code!!))
        assertTrue(customerBody.contains("Bastos, Yaounde"), customerBody)
        assertTrue(customerBody.contains("Rue 1.234, Bonapriso"), customerBody)
        assertTrue(customerBody.contains("Je vous communiquerai l'adresse exacte et l"), customerBody)
        assertFalse(customerBody.contains("pharmacie"), "the address must not be forwarded in the e-mail")
        assertTrue(customerBody.contains("+237 6 99 00 00 01"))
        assertTrue(customerBody.contains("https://www.ndopify.com/visits/1?token="))

        val agentMessage = messages.single { it.allRecipients.single().toString() == "Kim Possible <kim.possible@gmail.com>" }
        assertEquals("Ndopify: visite confirmée avec Ray Sponsible", agentMessage.subject)
        val agentBody = agentMessage.content.toString()
        assertTrue(agentBody.contains("+237 6 00 00 00 01"))
        assertTrue(agentBody.contains("Demandez le code de visite"))
        assertTrue(agentBody.contains("https://partner.ndopify.com/visits/1"))
        assertFalse(agentBody.contains(visit.code!!), "the agent must not receive the code")
    }

    @Test
    fun `a date different from the requested one`() {
        val response = rest.postForEntity(
            "/v1/visits/1/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 15, 30)),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(slot(2, 15, 30), dao.findById(1L).get().scheduledAt)
    }

    @Test
    fun `ON_SITE to VIDEO`() {
        val response = rest.postForEntity(
            "/v1/visits/1/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0), type = VisitType.VIDEO),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitType.VIDEO, dao.findById(1L).get().type)
        assertTrue(smtp.waitForIncomingEmail(5000, 2), "two emails expected")
        val customerBody = smtp.receivedMessages.single {
            it.allRecipients.single().toString() == "Ray Sponsible <ray.sponsible@gmail.com>"
        }.content.toString()
        assertTrue(customerBody.contains("Je vous appellerai en vid"), customerBody)
        assertTrue(customerBody.contains("sur WhatsApp"), customerBody)
        assertTrue(customerBody.contains("Bastos, Yaounde"), customerBody)
        assertTrue(customerBody.contains("Rue 1.234, Bonapriso"), customerBody)
        assertFalse(customerBody.contains("pharmacie"), "no address for a VIDEO visit")
    }

    @Test
    fun `VIDEO without whatsapp`() {
        val response = rest.postForEntity(
            "/v1/visits/3/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0), type = VisitType.VIDEO),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_WHATSAPP_MISSING, response.body?.error?.code)
        assertEquals(VisitStatus.REQUESTED, dao.findById(3L).get().status)
    }

    @Test
    fun `invalid slot`() {
        listOf(
            slot(0, 20, 0),
            slot(8, 8, 0),
            slot(1, 21, 0),
            slot(1, 10, 15),
        ).forEach { at ->
            val response = rest.postForEntity("/v1/visits/1/schedule", ScheduleVisitRequest(scheduledAt = at), ErrorResponse::class.java)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "slot $at")
            assertEquals(ErrorCode.VISIT_INVALID_SLOT, response.body?.error?.code, "slot $at")
        }
    }

    @Test
    fun `not requested`() {
        val response = rest.postForEntity(
            "/v1/visits/4/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_REQUESTED, response.body?.error?.code)
    }

    @Test
    fun `visit of another agent`() {
        val response = rest.postForEntity(
            "/v1/visits/5/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `as the customer`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `visit not found`() {
        val response = rest.postForEntity(
            "/v1/visits/999/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.VISIT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/visits/1/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant())
}
