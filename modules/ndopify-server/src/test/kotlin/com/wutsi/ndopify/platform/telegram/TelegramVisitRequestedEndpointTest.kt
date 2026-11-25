package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.VisitType
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
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
        "/db/test/visit/CreateVisitEndpoint.sql",
        "/db/test/telegram/TelegramVisitRequestedEndpoint.sql",
    ],
)
class TelegramVisitRequestedEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun `a request messages the linked agent instead of e-mailing it`() {
        userId = 13L // Ray Sponsible, the customer of match 1
        val response = post(type = VisitType.ON_SITE, note = "Je viens avec ma femme")
        assertEquals(HttpStatus.OK, response.statusCode)
        val id = response.body!!.visitId

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(
            text.firstValue.startsWith(
                "📅 Ray Sponsible demande une visite : Nice apartment - 150K/mois",
            ),
        )
        assertTrue(text.firstValue.contains(" · Sur place"))
        assertTrue(text.firstValue.contains("Note : Je viens avec ma femme"))
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/$id"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
            ),
            buttons.firstValue,
        )

        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no request e-mail for a linked agent")
    }

    @Test
    fun `a video request says it is a video visit`() {
        userId = 13L
        val response = post(type = VisitType.VIDEO)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains(" · Visite vidéo"))
    }

    private fun post(type: VisitType, note: String? = null) =
        rest.postForEntity(
            "/v1/visits",
            CreateVisitRequest(matchId = 1L, type = type, requestedAt = slot(1, 10, 0), note = note),
            CreateVisitResponse::class.java,
        )

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant())
}
