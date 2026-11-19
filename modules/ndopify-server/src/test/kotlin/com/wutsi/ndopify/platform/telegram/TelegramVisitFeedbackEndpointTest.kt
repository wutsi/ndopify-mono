package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.dto.VisitFeedbackRequest
import com.wutsi.ndopify.visit.dto.VisitInterest
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
        "/db/test/visit/VisitFeedbackEndpoint.sql",
        "/db/test/telegram/TelegramVisitFeedback.sql",
    ],
)
class TelegramVisitFeedbackEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.NOT_INTERESTED, comment = "Trop cher"),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("L'avis de Ray Sponsible sur la visite"))
        assertTrue(text.firstValue.contains("Bien: Nice apartment"))
        // The fixture's DATETIME is read in the JVM's zone: the exact hour is tenant-dependent (see
        // VisitFeedbackBotletTest, which builds its dates in Africa/Douala), the day is not
        assertTrue(text.firstValue.contains("Date: 01/12/2026 à "))
        assertTrue(text.firstValue.contains("Avis: Pas intéressé"))
        assertTrue(text.firstValue.contains("Raison: autre raison"))
        assertTrue(text.firstValue.contains("Commentaire: Trop cher"))
        assertFalse(text.firstValue.contains("1234"), "never the visit code")
        assertFalse(text.firstValue.contains("Rue 1.234"), "never the exact address")
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no e-mail for a linked agent")
    }

    @Sql(
        value = [
            "/db/test/clean.sql",
            "/db/test/visit/VisitFixtures.sql",
            "/db/test/visit/VisitFeedbackEndpoint.sql",
        ],
    )
    @Test
    fun `an unlinked agent is e-mailed, never messaged`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/1/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "one e-mail to the agent")
        assertEquals(
            "Kim Possible <kim.possible@gmail.com>",
            smtp.receivedMessages.single().allRecipients.single().toString(),
        )
    }

    @Test
    fun `an already answered visit notifies nobody`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/2/feedback",
            VisitFeedbackRequest(interest = VisitInterest.NOT_INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no notification for a 409")
    }

    @Test
    fun `a visit not completed notifies nobody`() {
        userId = 13L

        val response = rest.postForEntity(
            "/v1/visits/3/feedback",
            VisitFeedbackRequest(interest = VisitInterest.INTERESTED),
            Any::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        awaitAsyncTasks()

        verifyNoInteractions(client)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no notification for a visit not COMPLETED")
    }
}
