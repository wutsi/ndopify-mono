package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A listing sent back for correction announced over Telegram to a linked agent, end to end: the verifier's event,
 * the after-commit listener, the guard, the copy and the Partner Central link. No e-mail is sent to that agent.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SentBackListingsJob.sql"])
class TelegramSentBackListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `a rejected listing is messaged to the linked agent`() {
        jobs.verify()
        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.startsWith("Votre annonce n° 101 — Bastos, Yaoundé a été renvoyée pour correction"))
        assertTrue(text.firstValue.contains("Ouvrez-la dans Partner Central pour voir ce qui est à corriger"))
        assertEquals(
            listOf(TelegramButton("Corriger l'annonce", url = "https://partner.ndopify.com/listings/101")),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no sent-back e-mail for a linked agent")
    }
}
