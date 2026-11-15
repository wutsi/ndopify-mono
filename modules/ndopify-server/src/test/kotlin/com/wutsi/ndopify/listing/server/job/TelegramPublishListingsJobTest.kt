package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentGenerator
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentInfo
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.local.StorageServiceLocal
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A publication announced over Telegram to a linked agent, with the listing's hero image read from the storage and
 * uploaded to Telegram: no e-mail is sent to that agent.
 */
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/listing/PublishListingsJob.sql",
        "/db/test/telegram/TelegramPublishListingEndpoint.sql",
    ],
)
class TelegramPublishListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var client: TelegramClient

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @TempDir
    lateinit var dir: File

    @Test
    fun `a publication uploads the hero image to the linked agent`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val storage = StorageServiceLocal(dir.absolutePath, "/storage", 8080)
        storage.store("listings/100/7.jpg", ByteArrayInputStream(bytes), "image/jpeg")
        doReturn(storage).whenever(storageProvider).get()
        doReturn(ListingContentInfo(title = "Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
            .whenever(generator).generate(any())

        jobs.publish()
        awaitAsyncTasks()

        val caption = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("100.jpg"),
            eq("image/jpeg"),
            caption.capture(),
            buttons.capture(),
        )
        assertTrue(caption.firstValue.contains("Bonjour Kim Possible"))
        assertTrue(caption.firstValue.contains("Annonce: Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
        assertEquals(
            listOf(
                TelegramButton(
                    "Voir l'annonce",
                    url = "https://www.ndopify.com/yaounde/bastos/listing/100/appartement-a-louer-3-chambres-yaounde-bastos",
                ),
            ),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no publication e-mail for a linked agent")
    }
}
