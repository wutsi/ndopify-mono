package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CancelVisitEndpoint.sql",
        "/db/test/telegram/TelegramVisitCancelledEndpoint.sql",
    ],
)
class TelegramVisitCancelledEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a customer cancellation messages the linked agent instead of e-mailing it`() {
        userId = 13L // Ray Sponsible, the customer of visit 2's mandate
        val response = rest.postForEntity("/v1/visits/2/cancel", CancelVisitRequest(), Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("Le client a annulé la visite"))
        assertTrue(text.firstValue.contains("Bien: Small apartment"))
        assertTrue(text.firstValue.contains("Ville: Yaounde"))
        assertTrue(text.firstValue.contains("Quartier: Bastos"))
        assertTrue(text.firstValue.contains("Client: Ray Sponsible"))
        assertFalse(text.firstValue.contains("Motif:"))
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/2")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no cancellation e-mail for a linked agent")
    }

    @Test
    fun `a visit never scheduled is not notified`() {
        userId = 13L
        val response = rest.postForEntity(
            "/v1/visits/1/cancel",
            CancelVisitRequest(reason = "Empêchement"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no cancellation e-mail for a visit never scheduled")
    }

    @Test
    fun `an agent cancellation still e-mails the customer, never Telegram`() {
        val response = rest.postForEntity(
            "/v1/visits/2/cancel",
            CancelVisitRequest(reason = "Le bien est loué"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail to the customer")
        assertEquals(
            "Ray Sponsible <ray.sponsible@gmail.com>",
            smtp.receivedMessages.single().allRecipients.single().toString(),
        )
    }
}
