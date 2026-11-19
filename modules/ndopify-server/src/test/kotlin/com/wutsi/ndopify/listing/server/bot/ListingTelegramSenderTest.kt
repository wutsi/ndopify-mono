package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import java.io.OutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ListingTelegramSenderTest {
    private val imageService = mock<ListingImageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val client = mock<TelegramClient>()
    private val sender = ListingTelegramSender(imageService, storageProvider, client)

    private val listing = ListingEntity(id = 101L, tenantId = 1L, title = "Nice apartment")
    private val cover = ListingImageEntity(
        id = 7L,
        listingId = 101L,
        path = "listings/101/7.jpg",
        url = "https://cdn.ndopify.com/7.jpg",
        mimeType = "image/jpeg",
    )
    private val bytes = byteArrayOf(1, 2, 3)
    private val buttons = listOf(TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing/101"))

    @BeforeTest
    fun setUp() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(cover).whenever(imageService).cover(listing)
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/101/7.jpg"), any())
    }

    @Test
    fun `a cover is uploaded as the photo with the text and the buttons`() {
        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("101.jpg"),
            eq("image/jpeg"),
            eq("Nice apartment, 2 chambres"),
            eq(buttons),
        )
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `no cover sends the text`() {
        doReturn(null).whenever(imageService).cover(listing)

        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", buttons)
        verify(client, never()).sendPhoto(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a text over 1024 characters is sent as text without reading the image`() {
        val text = "x".repeat(1025)

        sender.send(1000L, listing, text, buttons)

        verify(client).sendMessage(1000L, text, buttons)
        verify(imageService, never()).cover(any())
    }

    @Test
    fun `a text of exactly 1024 characters fits the caption`() {
        val text = "x".repeat(1024)

        sender.send(1000L, listing, text, buttons)

        verify(client).sendPhoto(eq(1000L), any(), eq("101.jpg"), eq("image/jpeg"), eq(text), eq(buttons))
    }

    @Test
    fun `a storage failure sends the text`() {
        doThrow(IllegalStateException("no such object")).whenever(storage).get(eq("listings/101/7.jpg"), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
    }

    @Test
    fun `a refused photo sends the text`() {
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(any(), any(), any(), any(), any(), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
    }

    @Test
    fun `a refused text still throws`() {
        doReturn(null).whenever(imageService).cover(listing)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(client).sendMessage(any(), any(), any())

        assertFailsWith<TelegramException> {
            sender.send(1000L, listing, "Nice apartment, 2 chambres")
        }
    }
}
