package com.wutsi.ndopify.match.server.event

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.server.event.ListingPublishedEvent
import com.wutsi.ndopify.mandate.server.event.MandateApprovedEvent
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.service.MatchInterestService
import com.wutsi.ndopify.match.server.service.MatchProposalService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchEventListenerTest {
    private val service = mock<MatchService>()
    private val proposalService = mock<MatchProposalService>()
    private val interestService = mock<MatchInterestService>()
    private val listener = MatchEventListener(service, proposalService, interestService)

    @Test
    fun `approved - the mandate is matched, in its tenant`() {
        var tenantId: Long? = null
        doAnswer {
            tenantId = TenantContext.get()
            2
        }.whenever(service).matchMandate(10L)

        listener.onMandateApproved(MandateApprovedEvent(mandateId = 10L, tenantId = 7L))

        verify(service).matchMandate(10L)
        verify(interestService).proposeOrigin(10L) // Its origin listing is proposed at once
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `approved - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).matchMandate(10L)

        listener.onMandateApproved(MandateApprovedEvent(mandateId = 10L, tenantId = 7L))

        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `published - the listing is matched, in its tenant`() {
        var tenantId: Long? = null
        doAnswer {
            tenantId = TenantContext.get()
            1
        }.whenever(service).matchListing(20L)

        listener.onListingPublished(ListingPublishedEvent(listingId = 20L, tenantId = 7L))

        verify(service).matchListing(20L)
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `published - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).matchListing(20L)

        listener.onListingPublished(ListingPublishedEvent(listingId = 20L, tenantId = 7L))

        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `proposed - the customer is e-mailed, in its tenant`() {
        var tenantId: Long? = null
        doAnswer { tenantId = TenantContext.get() }.whenever(proposalService).sendProposedEmail(listOf(30L, 31L))

        listener.onMatchProposed(MatchProposedEvent(matchIds = listOf(30L, 31L), tenantId = 7L))

        verify(proposalService).sendProposedEmail(listOf(30L, 31L))
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `proposed - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(proposalService).sendProposedEmail(listOf(30L, 31L))

        listener.onMatchProposed(MatchProposedEvent(matchIds = listOf(30L, 31L), tenantId = 7L))

        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `answered - the proposal service is told, in the tenant`() {
        var tenantId: Long? = null
        doAnswer { tenantId = TenantContext.get() }.whenever(proposalService).sendAnsweredNotification(2L)

        listener.onMatchAnswered(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 7L, status = MatchStatus.ACCEPTED),
        )

        verify(proposalService).sendAnsweredNotification(2L)
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `answered - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(proposalService).sendAnsweredNotification(2L)

        listener.onMatchAnswered(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 7L, status = MatchStatus.REJECTED),
        )

        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }
}
