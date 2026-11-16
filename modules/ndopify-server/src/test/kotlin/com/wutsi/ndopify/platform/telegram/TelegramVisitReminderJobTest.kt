package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.visit.server.job.VisitJobs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The 24 h reminder's channel, end to end: the linked agent over Telegram, the others by e-mail. */
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/RemindVisitsJob.sql",
        "/db/test/telegram/TelegramVisitReminderJob.sql",
    ],
)
class TelegramVisitReminderJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: VisitJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a linked agent gets the reminder over Telegram, the customer by e-mail`() {
        jobs.remind()
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("Bonjour Kim Possible,"))
        assertTrue(text.firstValue.contains("Petit rappel"))
        assertTrue(text.firstValue.contains("Nice apartment"))
        assertTrue(text.firstValue.contains("Ray Sponsible"))
        assertTrue(text.firstValue.contains("Type: Sur place"))
        assertTrue(text.firstValue.contains("Lieu: Bastos, Yaounde"))
        assertFalse(text.firstValue.contains("WhatsApp:"))
        assertFalse(text.firstValue.contains("1111"))
        assertFalse(text.firstValue.contains("Rue 1.234"))
        assertFalse(text.firstValue.contains("Derrière la pharmacie"))
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
            ),
            buttons.firstValue,
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 3), "customer copy of visit 1 + both copies of tenant 2's visit")
        assertTrue(
            smtp.receivedMessages.none { message ->
                message.allRecipients.any { it.toString().contains("kim.possible") }
            },
            "no reminder e-mail for the linked agent",
        )
    }

    @Test
    fun `the reminder reaches the bot only once`() {
        jobs.remind()
        awaitAsyncTasks()

        jobs.remind()
        awaitAsyncTasks()

        verify(client, times(1)).sendMessage(any(), any(), any())
    }
}
