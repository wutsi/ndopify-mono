package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.sun.net.httpserver.HttpServer
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Clock
import java.util.Date
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingVerifierImageTest {
    companion object {
        private const val LISTING_ID = 1L
        private const val NOW = 1_700_000_000_000L
    }

    private val extractor = mock<ListingImageInfoExtractor>()
    private val dao = mock<ListingImageRepository>()
    private val clock = mock<Clock>()
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )
    private val verifier = ListingVerifierImage(extractor, dao, tx, clock, 2)

    private val listing = ListingEntity(id = LISTING_ID)
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        doReturn(NOW).whenever(clock).millis()

        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        serve("/image.png", "image/png", png(640, 480))
        serve("/image.webp", "image/webp", byteArrayOf(1, 2, 3))
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun serve(path: String, contentType: String, content: ByteArray) {
        server.createContext(path) { exchange ->
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun url(path: String) = "http://localhost:${server.address.port}$path"

    private fun image(id: Long, path: String = "/image.png", status: ImageStatus = ImageStatus.PENDING_VERIFICATION) =
        ListingImageEntity(id = id, listingId = LISTING_ID, url = url(path), status = status)

    private fun mockImages(pending: List<ListingImageEntity>, verified: List<ListingImageEntity>) {
        doReturn(pending).whenever(dao).findByListingIdAndStatus(LISTING_ID, ImageStatus.PENDING_VERIFICATION)
        doReturn(verified).whenever(dao).findByListingIdAndStatus(LISTING_ID, ImageStatus.VERIFIED)
    }

    @Test
    fun `valid image`() {
        mockImages(
            pending = listOf(image(11)),
            verified = listOf(image(11, status = ImageStatus.VERIFIED), image(12, status = ImageStatus.VERIFIED)),
        )
        doReturn(ListingImageInfo(title = "Salon", description = "Grand salon", valid = true, quality = 4))
            .whenever(extractor).extract(any())

        val result = verifier.verify(listing)

        assertTrue(result.success)
        assertNull(result.errorCode)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(11L, image.firstValue.id)
        assertEquals(ImageStatus.VERIFIED, image.firstValue.status)
        assertEquals("Salon", image.firstValue.title)
        assertEquals("Grand salon", image.firstValue.description)
        assertEquals(4, image.firstValue.quality)
        assertEquals(640, image.firstValue.width)
        assertEquals(480, image.firstValue.height)
        assertNull(image.firstValue.errorMessage)
        assertEquals(Date(NOW), image.firstValue.modifiedAt)
    }

    @Test
    fun `invalid image`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(ListingImageInfo(title = "Carte", valid = false, quality = 2, reason = "Generic map"))
            .whenever(extractor).extract(any())

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, result.errorCode)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageStatus.REJECTED, image.firstValue.status)
        assertEquals("Generic map", image.firstValue.errorMessage)
        assertNull(image.firstValue.title)
        assertNull(image.firstValue.quality)
        assertNull(image.firstValue.width)
        assertEquals(Date(NOW), image.firstValue.modifiedAt)
    }

    @Test
    fun `no image info`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(null).whenever(extractor).extract(any())

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, result.errorCode)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageStatus.REJECTED, image.firstValue.status)
        assertEquals("Unable to extract image info", image.firstValue.errorMessage)
        assertNull(image.firstValue.title)
        assertNull(image.firstValue.width)
        assertEquals(Date(NOW), image.firstValue.modifiedAt)
    }

    @Test
    fun `image format without dimensions`() {
        mockImages(pending = listOf(image(11, path = "/image.webp")), verified = emptyList())
        doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any())

        verifier.verify(listing)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageStatus.VERIFIED, image.firstValue.status)
        assertNull(image.firstValue.width)
        assertNull(image.firstValue.height)
    }

    @Test
    fun `verify every pending image`() {
        mockImages(pending = listOf(image(11), image(12), image(13)), verified = emptyList())
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any())

        verifier.verify(listing)

        verify(extractor, times(3)).extract(any())
        verify(dao, times(3)).save(any<ListingImageEntity>())
    }

    @Test
    fun `no pending image, enough verified`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(image(11, status = ImageStatus.VERIFIED), image(12, status = ImageStatus.VERIFIED)),
        )

        val result = verifier.verify(listing)

        assertTrue(result.success)
        verify(extractor, never()).extract(any())
        verify(dao, never()).save(any<ListingImageEntity>())
    }

    @Test
    fun `not enough images`() {
        mockImages(pending = emptyList(), verified = listOf(image(11, status = ImageStatus.VERIFIED)))

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, result.errorCode)
    }

    @Test
    fun `download error`() {
        mockImages(pending = listOf(image(11, path = "/not-found.png")), verified = emptyList())

        assertFailsWith<IOException> { verifier.verify(listing) }

        verify(extractor, never()).extract(any())
        verify(dao, never()).save(any<ListingImageEntity>())
    }

    @Test
    fun `listing without id`() {
        assertFailsWith<IllegalStateException> { verifier.verify(ListingEntity()) }
    }
}
