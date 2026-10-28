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

    private val listing = ListingEntity(id = 1L, status = ListingStatus.PENDING_VERIFICATION)

    @BeforeEach
    fun setUp() {
        doReturn(NOW).whenever(clock).millis()
        doAnswer { invocation -> invocation.arguments[0] }.whenever(dao).save(any<ListingEntity>())
        doReturn(ListingVerification(success = true)).whenever(image).verify(any())
        doReturn(ListingVerification(success = true)).whenever(geo).verify(any())
    }

    private fun saved(): List<ListingEntity> {
        val listing = argumentCaptor<ListingEntity>()
        verify(dao, times(2)).save(listing.capture())
        return listing.allValues
    }

    @Test
    fun verified() {
        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.UNDER_VERIFICATION, saved[0].status)
        assertEquals(Date(NOW), saved[0].modifiedAt)

        assertEquals(ListingStatus.VERIFIED, saved[1].status)
        assertTrue(saved[1].errorCodes.isEmpty())
        assertNull(saved[1].errorMessage)
        assertEquals(Date(NOW), saved[1].verifiedAt)
        assertEquals(Date(NOW), saved[1].modifiedAt)

        // The verifiers get the listing under verification
        verify(image).verify(saved[0])
        verify(geo).verify(saved[0])
    }

    @Test
    fun `errors - back to draft`() {
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)).whenever(image).verify(any())
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD)).whenever(geo).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved[1].status)
        assertEquals(
            listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
            saved[1].errorCodes,
        )
        assertEquals(Date(NOW), saved[1].verifiedAt)
    }

    @Test
    fun `error of one verifier - back to draft`() {
        doReturn(ListingVerification(errorCode = ErrorCode.LISTING_MISSING_LAT_LONG)).whenever(geo).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved[1].status)
        assertEquals(listOf(ErrorCode.LISTING_MISSING_LAT_LONG), saved[1].errorCodes)
    }

    @Test
    fun `clear the errors of the previous attempt`() {
        service.verify(listing.copy(errorCodes = listOf(ErrorCode.INTERNAL_ERROR), errorMessage = "failed", retries = 1))

        val saved = saved()
        assertEquals(ListingStatus.VERIFIED, saved[1].status)
        assertTrue(saved[1].errorCodes.isEmpty())
        assertNull(saved[1].errorMessage)
    }

    @Test
    fun `failure - retry`() {
        doThrow(RuntimeException("boom")).whenever(image).verify(any())

        service.verify(listing)

        val saved = saved()
        assertEquals(ListingStatus.PENDING_VERIFICATION, saved[1].status)
        assertEquals(1, saved[1].retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved[1].errorCodes)
        assertEquals("boom", saved[1].errorMessage)
        assertNull(saved[1].verifiedAt)
        assertEquals(Date(NOW), saved[1].modifiedAt)
    }

    @Test
    fun `failure - last retry`() {
        doThrow(RuntimeException("boom")).whenever(geo).verify(any())

        service.verify(listing.copy(retries = ListingVerifierService.MAX_RETRIES - 1))

        val saved = saved()
        assertEquals(ListingStatus.PENDING_VERIFICATION, saved[1].status)
        assertEquals(ListingVerifierService.MAX_RETRIES, saved[1].retries)
    }

    @Test
    fun `failure - too many retries - back to draft`() {
        doThrow(RuntimeException("boom")).whenever(geo).verify(any())

        service.verify(listing.copy(retries = ListingVerifierService.MAX_RETRIES))

        val saved = saved()
        assertEquals(ListingStatus.DRAFT, saved[1].status)
        assertEquals(0, saved[1].retries)
        assertEquals(listOf(ErrorCode.INTERNAL_ERROR), saved[1].errorCodes)
        assertEquals("boom", saved[1].errorMessage)
    }

    @Test
    fun `not pending verification`() {
        assertFailsWith<IllegalStateException> {
            service.verify(listing.copy(status = ListingStatus.DRAFT))
        }

        verify(dao, never()).save(any<ListingEntity>())
        verify(image, never()).verify(any())
    }
}
