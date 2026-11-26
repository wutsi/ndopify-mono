package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.sun.net.httpserver.HttpServer
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ImageKind
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.image.ImageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Clock
import java.util.Date
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    private val imageService = mock<ImageService>()
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )
    private val verifier = ListingVerifierImage(extractor, dao, tx, clock, imageService, 2)

    private val listing = ListingEntity(id = LISTING_ID)
    private lateinit var server: HttpServer

    @BeforeEach
    fun setUp() {
        doReturn(NOW).whenever(clock).millis()
        doAnswer { it.getArgument<String>(0) }.whenever(imageService).transform(any(), any())

        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        serve("/image.png", "image/png", png(640, 480))
        serve("/image.webp", "image/webp", byteArrayOf(1, 2, 3))
        serve("/rendition.jpg", "image/jpeg", byteArrayOf(9, 9, 9))
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
            .whenever(extractor).extract(any(), any())

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
            .whenever(extractor).extract(any(), any())

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
        doReturn(null).whenever(extractor).extract(any(), any())

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
        doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any(), any())

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
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        verify(extractor, times(3)).extract(any(), any())
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
        verify(extractor, never()).extract(any(), any())
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

        verify(extractor, never()).extract(any(), any())
        verify(dao, never()).save(any<ListingImageEntity>())
    }

    @Test
    fun `listing without id`() {
        assertFailsWith<IllegalStateException> { verifier.verify(ListingEntity()) }
    }

    @Test
    fun `the kind of an accepted image is stored`() {
        mockImages(
            pending = listOf(image(11)),
            verified = listOf(image(11, status = ImageStatus.VERIFIED), image(12, status = ImageStatus.VERIFIED)),
        )
        doReturn(ListingImageInfo(title = "Plan du rez-de-chaussée", valid = true, quality = 4, kind = "FLOORPLAN"))
            .whenever(extractor).extract(any(), eq(listing))

        assertTrue(verifier.verify(listing).success)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageKind.FLOORPLAN, image.firstValue.kind)
        assertEquals(ImageStatus.VERIFIED, image.firstValue.status)
    }

    @Test
    fun `the kind of a rejected image is stored too`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(ListingImageInfo(valid = false, quality = 2, kind = "MAP", reason = "Generic map"))
            .whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageKind.MAP, image.firstValue.kind)
        assertEquals(ImageStatus.REJECTED, image.firstValue.status)
    }

    @Test
    fun `floor plans and maps only - missing photo`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(
                image(11, status = ImageStatus.VERIFIED).copy(kind = ImageKind.MAP),
                image(12, status = ImageStatus.VERIFIED).copy(kind = ImageKind.FLOORPLAN),
            ),
        )

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_MISSING_PHOTO, result.errorCode)
        verify(extractor, never()).extract(any(), any())
    }

    @Test
    fun `too few images wins over the missing photo`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(image(11, status = ImageStatus.VERIFIED).copy(kind = ImageKind.MAP)),
        )

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, result.errorCode)
    }

    @Test
    fun `the rendition is sent to the extractor`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
        var content: ByteArray? = null
        doAnswer {
            content = it.getArgument<File>(0).readBytes()
            ListingImageInfo(title = "Salon", valid = true, quality = 4)
        }.whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertEquals("jpg", file.firstValue.extension)
        assertContentEquals(byteArrayOf(9, 9, 9), content!!)
        verify(imageService).transform(image(11).url, ListingVerifierImage.VISION_TRANSFORMATION)

        val saved = argumentCaptor<ListingImageEntity>()
        verify(dao).save(saved.capture())
        assertEquals(640, saved.firstValue.width)
        assertEquals(480, saved.firstValue.height)
    }

    @Test
    fun `the original is used when the image service leaves the URL unchanged`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertEquals("png", file.firstValue.extension)
        verify(imageService).transform(image(11).url, ListingVerifierImage.VISION_TRANSFORMATION)
    }

    @Test
    fun `a failed rendition falls back to the original`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(url("/missing.jpg")).whenever(imageService).transform(any(), any())
        doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any(), any())

        val result = verifier.verify(listing)

        assertEquals(false, result.success) // the mock DAO reports no VERIFIED image, not a resize failure
        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertEquals("png", file.firstValue.extension)

        val saved = argumentCaptor<ListingImageEntity>()
        verify(dao).save(saved.capture())
        assertEquals(ImageStatus.VERIFIED, saved.firstValue.status)
        assertEquals(640, saved.firstValue.width)
        assertEquals(480, saved.firstValue.height)
    }

    @Test
    fun `a webp upload gets a jpeg rendition`() {
        mockImages(pending = listOf(image(11, path = "/image.webp")), verified = emptyList())
        doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertEquals("jpg", file.firstValue.extension)
    }

    @Test
    fun `the temporary files are deleted`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any(), any())
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val before = tempDir.listFiles()!!.toSet()

        verifier.verify(listing)

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertFalse(file.firstValue.exists())
        val leaked = tempDir.listFiles()!!.toSet() - before
        assertTrue(leaked.none { it.extension == "png" || it.extension == "jpg" }, "leaked: $leaked")
    }

    @Test
    fun `the temporary files are deleted when the extractor fails`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doThrow(IllegalStateException("boom")).whenever(extractor).extract(any(), any())

        assertFailsWith<IllegalStateException> { verifier.verify(listing) }

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture(), eq(listing))
        assertFalse(file.firstValue.exists())
    }
}
