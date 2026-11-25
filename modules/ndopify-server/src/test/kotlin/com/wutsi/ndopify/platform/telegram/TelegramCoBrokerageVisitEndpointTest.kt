package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CoBrokerageVisitEndpoint.sql",
        "/db/test/telegram/TelegramCoBrokerageVisitEndpoint.sql",
    ],
)
class TelegramCoBrokerageVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun `confirming a co-brokerage visit messages the listing agent instead of e-mailing it`() {
        userId = 12L // John Doe, agent 30, the visit's agent (A)

        val response = rest.postForEntity(
            "/v1/visits/3/schedule",
            ScheduleVisitRequest(scheduledAt = slot(2, 10, 0)),
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(VisitStatus.CONFIRMED, dao.findById(3L).get().status)
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(2000L), text.capture(), buttons.capture())
        assertTrue(
            text.firstValue.startsWith(
                "✅ Visite confirmée : Nice apartment - 150K/mois, ",
            ),
        )
        assertTrue(text.firstValue.contains("Sur place — je vous y retrouve avec mon client."))
        assertTrue(text.firstValue.contains("Visite organisée par John Doe."))
        assertEquals(
            listOf(TelegramButton("Voir l'annonce", url = "https://partner.ndopify.com/listings/101#visits-title")),
            buttons.firstValue,
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "the customer and the organizing agent are e-mailed")
        assertFalse(
            smtp.receivedMessages.any { it.allRecipients.single().toString() == "Kim Possible <kim.possible@gmail.com>" },
            "no co-brokerage e-mail for a linked listing agent",
        )
    }

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant())
}
