package com.wutsi.ndopify.listing.server.service.publisher

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
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingPublisherServiceTest {
    companion object {
        private const val NOW = 1_700_000_000_000L
    }

    private val generator = mock<ListingContentGenerator>()
    private val dao = mock<ListingRepository>()
    private val imageDao = mock<ListingImageRepository>()
    private val clock = mock<Clock>()
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )
    private val service = ListingPublisherService(generator, dao, imageDao, tx, clock)

    private val listing = ListingEntity(id = 1L, status = ListingStatus.PUBLISHING)
    private val content = ListingContentInfo(
        title = "Appartement à louer, 3 chambres, Douala (Bonapriso)",
        summary = "Bel appartement lumineux au coeur de Bonapriso",
        description = "Un appartement spacieux et lumineux.",
        heroImageId = 11L,
    )

    @BeforeEach
    fun setUp() {
        doReturn(NOW).whenever(clock).millis()
        doAnswer { invocation -> invocation.arguments[0] }.whenever(dao).save(any<ListingEntity>())
        doReturn(content).whenever(generator).generate(any())
    }

    private fun saved(count: Int = 1): List<ListingEntity> {
        val listing = argumentCaptor<ListingEntity>()
        verify(dao, times(count)).save(listing.capture())
        return listing.allValues
    }

    @Test
    fun published() {
        val result = service.publish(listing)

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHED, saved.status)
        assertEquals(content.title, saved.title)
        assertEquals(content.summary, saved.summary)
        assertEquals(content.description, saved.description)
        assertEquals(content.heroImageId, saved.heroImageId)
        assertEquals(Date(NOW), saved.publishedAt)
        assertEquals(Date(NOW), saved.modifiedAt)
        assertEquals(saved, result)

        verify(generator).generate(listing)
        verify(imageDao, never()).findByListingIdAndStatus(any(), any())
    }

    @Test
    fun `no hero image - highest quality, then largest`() {
        doReturn(content.copy(heroImageId = null)).whenever(generator).generate(any())
        doReturn(
            listOf(
                ListingImageEntity(id = 21L, quality = 5, width = 800, height = 600),
                ListingImageEntity(id = 22L, quality = 5, width = 1920, height = 1080),
                ListingImageEntity(id = 23L, quality = 4, width = 4000, height = 3000),
                ListingImageEntity(id = 24L, quality = 5, width = null, height = null),
            ),
        ).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(listing)

        assertEquals(22L, saved().single().heroImageId)
    }

    @Test
    fun `no hero image - highest quality without dimensions`() {
        doReturn(content.copy(heroImageId = null)).whenever(generator).generate(any())
        doReturn(
            listOf(
                ListingImageEntity(id = 21L, quality = 3, width = 1920, height = 1080),
                ListingImageEntity(id = 22L, quality = 4),
            ),
        ).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(listing)

        assertEquals(22L, saved().single().heroImageId)
    }

    @Test
    fun `no hero image - no verified image`() {
        doReturn(content.copy(heroImageId = null)).whenever(generator).generate(any())
        doReturn(emptyList<ListingImageEntity>()).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(listing)

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHED, saved.status)
        assertNull(saved.heroImageId)
    }

    @Test
    fun `clear the errors of the previous attempt`() {
        service.publish(listing.copy(errorCodes = listOf(ErrorCode.INTERNAL_ERROR), errorMessage = "failed", retries = 2))

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHED, saved.status)
        assertTrue(saved.errorCodes.isEmpty())
        assertNull(saved.errorMessage)
        assertEquals(0, saved.retries)
    }

    @Test
    fun `title and summary truncated to their columns`() {
        doReturn(content.copy(title = "t".repeat(150), summary = "s".repeat(300))).whenever(generator).generate(any())

        service.publish(listing)

        val saved = saved().single()
        assertEquals("t".repeat(100), saved.title)
        assertEquals("s".repeat(255), saved.summary)
    }

    @Test
    fun `no content - retry`() {
        doReturn(null).whenever(generator).generate(any())

        service.publish(listing)

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHING, saved.status)
        assertEquals(1, saved.retries)
        assertTrue(saved.errorCodes.isEmpty())
        assertEquals("No content", saved.errorMessage)
        assertNull(saved.publishedAt)
        assertEquals(Date(NOW), saved.modifiedAt)
    }

    @Test
    fun `failure - retry`() {
        doThrow(RuntimeException("boom")).whenever(generator).generate(any())

        service.publish(listing.copy(retries = 1))

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHING, saved.status)
        assertEquals(2, saved.retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved.errorCodes)
        assertEquals("boom", saved.errorMessage)
        assertNull(saved.publishedAt)
        assertEquals(Date(NOW), saved.modifiedAt)
    }

    @Test
    fun `failure to save the content - retry`() {
        doThrow(DataIntegrityViolationException("too long"))
            .doAnswer { invocation -> invocation.arguments[0] }
            .whenever(dao).save(any<ListingEntity>())

        service.publish(listing)

        // The published listing isn't saved, the failure is
        val saved = saved(2)
        assertEquals(ListingStatus.PUBLISHED, saved[0].status)
        assertEquals(ListingStatus.PUBLISHING, saved[1].status)
        assertEquals(1, saved[1].retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved[1].errorCodes)
        assertEquals("too long", saved[1].errorMessage)
    }

    @Test
    fun `too many retries - published without content`() {
        doReturn(
            listOf(
                ListingImageEntity(id = 21L, quality = 3),
                ListingImageEntity(id = 22L, quality = 5),
            ),
        ).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(
            listing.copy(
                retries = ListingPublisherService.MAX_RETRIES,
                title = "old title",
                summary = "old summary",
                description = "old description",
                errorCodes = listOf(ErrorCode.INTERNAL_ERROR),
                errorMessage = "boom",
            ),
        )

        val saved = saved().single()
        assertEquals(ListingStatus.PUBLISHED, saved.status)
        assertNull(saved.title)
        assertNull(saved.summary)
        assertNull(saved.description)
        assertEquals(22L, saved.heroImageId)
        assertEquals(Date(NOW), saved.publishedAt)
        assertEquals(Date(NOW), saved.modifiedAt)

        // The last error is kept: why it has no content
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved.errorCodes)
        assertEquals("boom", saved.errorMessage)

        verify(generator, never()).generate(any())
    }

    @Test
    fun `last retry`() {
        service.publish(listing.copy(retries = ListingPublisherService.MAX_RETRIES - 1))

        assertEquals(ListingStatus.PUBLISHED, saved().single().status)
        verify(generator).generate(any())
    }

    @Test
    fun `not publishing`() {
        assertFailsWith<IllegalStateException> {
            service.publish(listing.copy(status = ListingStatus.VERIFIED))
        }

        verify(dao, never()).save(any<ListingEntity>())
        verify(generator, never()).generate(any())
    }
}
