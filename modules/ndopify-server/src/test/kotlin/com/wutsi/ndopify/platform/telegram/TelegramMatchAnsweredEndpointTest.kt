package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/match/ProposeMatchEndpoint.sql",
        "/db/test/telegram/TelegramMatchAnsweredEndpoint.sql",
    ],
)
class TelegramMatchAnsweredEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @MockitoBean
    private lateinit var client: TelegramClient

    @BeforeTest
    fun asCustomer() {
        userId = 13L // Ray Sponsible, the customer of search 1
    }

    @Test
    fun `an acceptance messages the linked agent instead of e-mailing it`() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(
            text.firstValue.startsWith(
                "✅ Ray Sponsible a accepté la proposition : Small apartment - 140K/mois",
            ),
        )
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no e-mail for a linked agent")
    }

    @Test
    fun `a rejection messages the reason and the comment`() {
        val response = rest.postForEntity(
            "/v1/matches/2/reject",
            RejectMatchRequest(reasonCode = MatchRejectionReason.PRICE, reason = "Trop loin du travail."),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(
            text.firstValue.startsWith(
                "❌ Ray Sponsible a refusé la proposition : Small apartment - 140K/mois",
            ),
        )
        assertTrue(text.firstValue.contains("Motif : trop cher · « Trop loin du travail. »"))
    }
}
