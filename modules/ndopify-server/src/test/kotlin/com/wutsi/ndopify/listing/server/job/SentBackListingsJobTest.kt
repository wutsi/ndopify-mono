package com.wutsi.ndopify.listing.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.TelegramClient
import org.jsoup.Jsoup
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A listing sent back for correction e-mailed to an agent whose chat isn't linked, end to end: the verifier's event,
 * the after-commit listener, the guard, the copy and the Partner Central link. Telegram is never called.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SentBackListingsJobNoTelegram.sql"])
class SentBackListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `an unlinked agent is e-mailed the generic message and the fix link`() {
        jobs.verify()
        awaitAsyncTasks()

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no e-mail received")
        val message = smtp.receivedMessages.single()
        assertEquals("Kim Possible <kim.possible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Votre annonce a été renvoyée pour correction", message.subject)
        // MJML HTML-encodes the French copy: read the text, not the markup
        val content = Jsoup.parse(message.content.toString()).text()
        assertTrue(content.contains("n° 101 — Bastos, Yaoundé"))
        assertTrue(message.content.toString().contains("https://partner.ndopify.com/listings/101"))
        verifyNoInteractions(client)
    }
}
