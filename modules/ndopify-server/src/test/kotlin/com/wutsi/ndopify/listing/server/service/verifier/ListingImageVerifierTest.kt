package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.sun.net.httpserver.HttpServer
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.time.Clock
import java.util.Date
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ListingImageVerifierTest {
    private val now = 1_700_000_000_000L
    private val extractor = mock<ListingImageInfoExtractor>()
    private val imageDao = mock<ListingImageRepository>()
    private val tx = mock<TransactionTemplate>()
    private val clock = mock<Clock>()
    private val verifier = ListingImageVerifier(extractor, imageDao, tx, clock)

    private val listing = ListingEntity(id = 100L, status = ListingStatus.DRAFT)

    // UrlUtils.download only accepts http(s) URLs: the images are served by a local server
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @BeforeEach
    fun setUp() {
        doReturn(now).whenever(clock).millis()
        doAnswer { invocation -> invocation.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock()) }
            .whenever(tx).execute(any<TransactionCallback<Any>>())
        doAnswer { invocation -> invocation.getArgument<ListingImageEntity>(0) }
            .whenever(imageDao).save(any<ListingImageEntity>())

        doReturn(emptyList<ListingImageEntity>()).whenever(imageDao).findByListingIdAndStatus(any(), any())
        doReturn(emptyList<ListingImageEntity>()).whenever(imageDao).findByListingIdOrderById(any())

        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        serve("/living-room.png", "image/png", png(40, 30))
        serve("/notes.txt", "text/plain", "not an image".toByteArray())
        server.start()
        baseUrl = "http://localhost:${server.address.port}"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `valid image - verified with its title, description and dimensions`() {
        val image = image(1L, "/living-room.png", ImageStatus.PENDING_VERIFICATION)
        mockImages(
            pending = listOf(image),
            underVerification = listOf(image.copy(status = ImageStatus.UNDER_VERIFICATION)),
        )
        doReturn(ListingImageInfo(title = "Salon", description = "Salon lumineux", valid = true))
            .whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(2)
        assertEquals(ImageStatus.UNDER_VERIFICATION, saved[0].status)
        assertEquals(Date(now), saved[0].modifiedAt)

        assertEquals(ImageStatus.VERIFIED, saved[1].status)
        assertEquals("Salon", saved[1].title)
        assertEquals("Salon lumineux", saved[1].description)
        assertEquals(40, saved[1].width)
        assertEquals(30, saved[1].height)
        assertNull(saved[1].errorMessage)
        assertEquals(Date(now), saved[1].modifiedAt)
    }

    @Test
    fun `downloaded file is sent to the extractor`() {
        mockImages(underVerification = listOf(image(1L, "/living-room.png", ImageStatus.UNDER_VERIFICATION)))
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any())

        verifier.verify(listing)

        val file = argumentCaptor<File>()
        verify(extractor).extract(file.capture())
        assertEquals("png", file.firstValue.extension)
        assertTrue(file.firstValue.readBytes().contentEquals(png(40, 30)))
    }

    @Test
    fun `invalid image - rejected with the extractor's reason`() {
        mockImages(underVerification = listOf(image(1L, "/living-room.png", ImageStatus.UNDER_VERIFICATION)))
        doReturn(ListingImageInfo(title = "Selfie", valid = false, reason = "Not the property"))
            .whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(1)[0]
        assertEquals(ImageStatus.REJECTED, saved.status)
        assertEquals("Not the property", saved.errorMessage)
        assertNull(saved.title)
        assertNull(saved.width)
        assertNull(saved.height)
    }

    @Test
    fun `image whose dimensions can't be read - verified without dimensions`() {
        mockImages(underVerification = listOf(image(1L, "/notes.txt", ImageStatus.UNDER_VERIFICATION)))
        doReturn(ListingImageInfo(title = "Salon", valid = true)).whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(1)[0]
        assertEquals(ImageStatus.VERIFIED, saved.status)
        assertNull(saved.width)
        assertNull(saved.height)
    }

    @Test
    fun `download failure - back to pending, for a retry`() {
        mockImages(underVerification = listOf(image(1L, "/missing.png", ImageStatus.UNDER_VERIFICATION)))

        verifier.verify(listing)

        val saved = savedImages(1)[0]
        assertEquals(ImageStatus.PENDING_VERIFICATION, saved.status)
        assertEquals(1, saved.retries)
        assertEquals("status: 404", saved.errorMessage)
        assertEquals(Date(now), saved.modifiedAt)
        verify(extractor, never()).extract(any())
    }

    @Test
    fun `extractor failure - back to pending, for a retry`() {
        mockImages(
            underVerification = listOf(image(1L, "/living-room.png", ImageStatus.UNDER_VERIFICATION, retries = 2)),
        )
        doThrow(IllegalStateException("AI is down")).whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(1)[0]
        assertEquals(ImageStatus.PENDING_VERIFICATION, saved.status)
        assertEquals(3, saved.retries)
        assertEquals("AI is down", saved.errorMessage)
    }

    @Test
    fun `failure after the last retry - rejected`() {
        mockImages(
            underVerification = listOf(image(1L, "/living-room.png", ImageStatus.UNDER_VERIFICATION, retries = 3)),
        )
        doThrow(IllegalStateException("AI is down")).whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(1)[0]
        assertEquals(ImageStatus.REJECTED, saved.status)
        assertEquals(4, saved.retries)
        assertEquals("AI is down", saved.errorMessage)
    }

    @Test
    fun `one image failure doesn't stop the others`() {
        mockImages(
            underVerification = listOf(
                image(1L, "/missing.png", ImageStatus.UNDER_VERIFICATION),
                image(2L, "/living-room.png", ImageStatus.UNDER_VERIFICATION),
            ),
        )
        doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any())

        verifier.verify(listing)

        val saved = savedImages(2)
        assertEquals(1L, saved[0].id)
        assertEquals(ImageStatus.PENDING_VERIFICATION, saved[0].status)
        assertEquals(2L, saved[1].id)
        assertEquals(ImageStatus.VERIFIED, saved[1].status)
    }

    @Test
    fun `enough verified images - listing unchanged`() {
        mockAllImages(List(5) { ImageStatus.VERIFIED } + ImageStatus.REJECTED)

        val result = verifier.verify(listing)

        assertSame(listing, result)
    }

    @Test
    fun `not enough verified images - listing rejected`() {
        mockAllImages(List(4) { ImageStatus.VERIFIED } + ImageStatus.REJECTED)

        val result = verifier.verify(listing)

        assertEquals(ListingStatus.REJECTED, result.status)
        assertEquals(listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES), result.errors)
    }

    @Test
    fun `not enough verified images - keeps the listing's other errors`() {
        mockAllImages(listOf(ImageStatus.VERIFIED))

        val result = verifier.verify(listing.copy(errors = listOf("other-error")))

        assertEquals(listOf("other-error", ErrorCode.LISTING_NOT_ENOUGH_IMAGES), result.errors)
    }

    @Test
    fun `images pending verification - listing unchanged`() {
        mockAllImages(List(2) { ImageStatus.VERIFIED } + ImageStatus.PENDING_VERIFICATION)

        val result = verifier.verify(listing)

        assertSame(listing, result)
    }

    @Test
    fun `images under verification - listing unchanged`() {
        mockAllImages(List(2) { ImageStatus.VERIFIED } + ImageStatus.UNDER_VERIFICATION)

        val result = verifier.verify(listing)

        assertSame(listing, result)
    }

    @Test
    fun `listing without id`() {
        assertFailsWith<IllegalStateException> {
            verifier.verify(listing.copy(id = null))
        }
        verify(imageDao, never()).findByListingIdAndStatus(any(), any())
    }

    private fun mockImages(
        pending: List<ListingImageEntity> = emptyList(),
        underVerification: List<ListingImageEntity> = emptyList(),
    ) {
        doReturn(pending).whenever(imageDao)
            .findByListingIdAndStatus(listing.id!!, ImageStatus.PENDING_VERIFICATION)
        doReturn(underVerification).whenever(imageDao)
            .findByListingIdAndStatus(listing.id!!, ImageStatus.UNDER_VERIFICATION)
    }

    private fun mockAllImages(statuses: List<ImageStatus>) {
        doReturn(statuses.mapIndexed { i, status -> image(i + 1L, "/living-room.png", status) })
            .whenever(imageDao).findByListingIdOrderById(listing.id!!)
    }

    private fun savedImages(count: Int): List<ListingImageEntity> {
        val captor = argumentCaptor<ListingImageEntity>()
        verify(imageDao, times(count)).save(captor.capture())
        return captor.allValues
    }

    private fun image(id: Long, path: String, status: ImageStatus, retries: Int? = null) =
        ListingImageEntity(
            id = id,
            listingId = listing.id!!,
            url = "$baseUrl$path",
            status = status,
            retries = retries,
            modifiedAt = Date(0),
        )

    private fun serve(path: String, contentType: String, bytes: ByteArray) {
        server.createContext(path) { exchange ->
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }
}
