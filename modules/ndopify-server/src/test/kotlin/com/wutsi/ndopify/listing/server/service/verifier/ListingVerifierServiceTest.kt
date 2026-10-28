package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingVerifierServiceTest {
    companion object {
        private const val NOW = 1_700_000_000_000L
    }

    private val dao = mock<ListingRepository>()
    private val clock = mock<Clock>()
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )
    private val image = mock<ListingVerifierImage>()
    private val geo = mock<ListingVerifierGeoLocalization>()
    private val service = ListingVerifierService(dao, tx, clock, image, geo)

    private val listing = ListingEntity(id = 1L, status = ListingStatus.UNDER_VERIFICATION)

    @BeforeEach
    fun setUp() {
        doReturn(NOW).whenever(clock).millis()
        doAnswer { invocation -> invocation.arguments[0] }.whenever(dao).save(any<ListingEntity>())
        doReturn(ListingVerification(success = true)).whenever(image).verify(any())
        doReturn(ListingVerification(success = true)).whenever(geo).verify(any())
    }

    private fun saved(): ListingEntity {
        val listing = argumentCaptor<ListingEntity>()
        verify(dao).save(listing.capture())
        return listing.firstValue
    }

    @Test
    fun verified() {
        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.VERIFIED, saved.status)
        assertTrue(saved.errorCodes.isEmpty())
        assertNull(saved.errorMessage)
        assertEquals(Date(NOW), saved.verifiedAt)
        assertEquals(Date(NOW), saved.modifiedAt)

        verify(image).verify(listing)
        verify(geo).verify(listing)
    }

    @Test
    fun `errors - back to draft`() {
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)).whenever(image).verify(any())
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD)).whenever(geo).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved.status)
        assertEquals(
            listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
            saved.errorCodes,
        )
        assertEquals(Date(NOW), saved.verifiedAt)
    }

    @Test
    fun `error of one verifier - back to draft`() {
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_MISSING_LAT_LONG)).whenever(geo).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved.status)
        assertEquals(listOf(ErrorCode.LISTING_MISSING_LAT_LONG), saved.errorCodes)
    }

    @Test
    fun `clear the errors of the previous attempt`() {
        service.verify(listing.copy(errorCodes = listOf(ErrorCode.INTERNAL_ERROR), errorMessage = "failed", retries = 1))

        val saved = saved()
        assertEquals(ListingStatus.VERIFIED, saved.status)
        assertTrue(saved.errorCodes.isEmpty())
        assertNull(saved.errorMessage)
    }

    @Test
    fun `failure - retry`() {
        doThrow(RuntimeException("boom")).whenever(image).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.UNDER_VERIFICATION, saved.status)
        assertEquals(1, saved.retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved.errorCodes)
        assertEquals("boom", saved.errorMessage)
        assertNull(saved.verifiedAt)
        assertEquals(Date(NOW), saved.modifiedAt)
    }

    @Test
    fun `failure - last retry`() {
        doThrow(RuntimeException("boom")).whenever(geo).verify(any())

        service.verify(listing.copy(retries = ListingVerifierService.MAX_RETRIES - 1))

        val saved = saved()
        assertEquals(ListingStatus.UNDER_VERIFICATION, saved.status)
        assertEquals(ListingVerifierService.MAX_RETRIES, saved.retries)
    }

    @Test
    fun `failure - too many retries - back to draft`() {
        doThrow(RuntimeException("boom")).whenever(geo).verify(any())

        service.verify(listing.copy(retries = ListingVerifierService.MAX_RETRIES))

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved.status)
        assertEquals(0, saved.retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved.errorCodes)
        assertEquals("boom", saved.errorMessage)
    }

    @Test
    fun `not under verification`() {
        assertFailsWith<IllegalStateException> {
            service.verify(listing.copy(status = ListingStatus.DRAFT))
        }

        verify(dao, never()).save(any<ListingEntity>())
        verify(image, never()).verify(any())
    }
}
