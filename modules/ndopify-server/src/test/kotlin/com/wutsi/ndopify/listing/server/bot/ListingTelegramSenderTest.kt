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
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import java.awt.Dimension
import java.io.OutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ListingTelegramSenderTest {
    private val listingImageService = mock<ListingImageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val client = mock<TelegramClient>()
    private val imageService = mock<ImageService>()
    private val sender = ListingTelegramSender(listingImageService, storageProvider, client, imageService)

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
        doReturn(cover).whenever(listingImageService).cover(listing)
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/101/7.jpg"), any())
        doReturn(cover.url).whenever(imageService).transform(eq(cover.url), any())
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
        doReturn(null).whenever(listingImageService).cover(listing)

        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", buttons)
        verify(client, never()).sendPhoto(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a text over 1024 characters is sent as text without reading the image`() {
        val text = "x".repeat(1025)

        sender.send(1000L, listing, text, buttons)

        verify(client).sendMessage(1000L, text, buttons)
        verify(listingImageService, never()).cover(any())
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
        doReturn(null).whenever(listingImageService).cover(listing)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(client).sendMessage(any(), any(), any())

        assertFailsWith<TelegramException> {
            sender.send(1000L, listing, "Nice apartment, 2 chambres")
        }
    }

    @Test
    fun `a resized cover is given to Telegram as a URL`() {
        val resized = "https://ik.imagekit.io/key/tr:w-1280,h-1280,f-jpg,c-at_max,q-80/7.jpg"
        doReturn(resized).whenever(imageService).transform(eq(cover.url), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(imageService).transform(cover.url, ListingTelegramSender.PHOTO_TRANSFORMATION)
        verify(client).sendPhoto(1000L, resized, "Nice apartment, 2 chambres", buttons)
        verify(storage, never()).get(any(), any())
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `a refused resized cover sends the text without reading the storage`() {
        val resized = "https://ik.imagekit.io/key/tr:w-1280,h-1280,f-jpg,c-at_max,q-80/7.jpg"
        doReturn(resized).whenever(imageService).transform(eq(cover.url), any())
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(eq(1000L), eq(resized), any(), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
        verify(storage, never()).get(any(), any())
    }

    @Test
    fun `a failed resize sends the text without reading the storage`() {
        doThrow(IllegalStateException("bad origin")).whenever(imageService).transform(eq(cover.url), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
        verify(storage, never()).get(any(), any())
    }

    @Test
    fun `the photo transformation fits 1280 by 1280 as a JPEG at quality 80`() {
        assertEquals(
            Transformation(
                dimension = Dimension(1280, 1280),
                format = Format.JPG,
                crop = Crop.AT_MAX,
                quality = 80,
            ),
            ListingTelegramSender.PHOTO_TRANSFORMATION,
        )
    }
}
