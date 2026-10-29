package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.listing.server.service.publisher.ListingPublisherService
import com.wutsi.ndopify.listing.server.service.verifier.ListingVerifierService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingJobsTest {
    private val service = mock<ListingService>()
    private val verifierService = mock<ListingVerifierService>()
    private val publisherService = mock<ListingPublisherService>()
    private val tenantService = mock<TenantService>()
    private val jobs = ListingJobs(service, verifierService, publisherService, tenantService)

    private val tenant1 = TenantEntity(id = 1L)
    private val tenant2 = TenantEntity(id = 2L)

    private val listing1 = ListingEntity(id = 1L, status = ListingStatus.UNDER_VERIFICATION)
    private val listing2 = ListingEntity(id = 2L, status = ListingStatus.UNDER_VERIFICATION)
    private val listing3 = ListingEntity(id = 3L, status = ListingStatus.UNDER_VERIFICATION)

    @BeforeTest
    fun setUp() {
        doReturn(listOf(tenant1)).whenever(tenantService).search(true)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    private fun setUpPendingListings(vararg listings: ListingEntity) {
        doReturn(listings.toList()).whenever(service).search(any())
    }

    @Test
    fun `searches the pending listings only, at most 100 per run`() {
        setUpPendingListings()

        jobs.verify()

        verify(service).search(
            SearchListingRequest(
                statuses = listOf(ListingStatus.UNDER_VERIFICATION),
                limit = 100,
            )
        )
    }

    @Test
    fun `verifies every pending listing, in the order found`() {
        setUpPendingListings(listing1, listing2, listing3)

        jobs.verify()

        val order = inOrder(verifierService)
        order.verify(verifierService).verify(listing1)
        order.verify(verifierService).verify(listing2)
        order.verify(verifierService).verify(listing3)
    }

    @Test
    fun `no pending listing - nothing verified`() {
        setUpPendingListings()

        jobs.verify()

        verify(verifierService, never()).verify(any())
    }

    @Test
    fun `a failing listing doesn't stop the others`() {
        // e.g. no longer UNDER_VERIFICATION, picked up by a concurrent run
        setUpPendingListings(listing1, listing2, listing3)
        doThrow(IllegalStateException("not pending")).whenever(verifierService).verify(listing2)

        jobs.verify()

        verify(verifierService, times(3)).verify(any())
    }

    @Test
    fun `only the active tenants are processed`() {
        setUpPendingListings()

        jobs.verify()

        verify(tenantService).search(true)
        verify(tenantService, never()).search(false)
        verify(tenantService, never()).search(null)
    }

    @Test
    fun `each tenant processed with its id in the tenant context`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        val searchTenants = mutableListOf<Long?>()
        val verifyTenants = mutableListOf<Long?>()
        doAnswer {
            searchTenants.add(TenantContext.get())
            if (TenantContext.get() == 1L) listOf(listing1) else listOf(listing2)
        }.whenever(service).search(any())
        doAnswer {
            verifyTenants.add(TenantContext.get())
        }.whenever(verifierService).verify(any())

        jobs.verify()

        assertEquals(listOf<Long?>(1L, 2L), searchTenants)
        assertEquals(listOf<Long?>(1L, 2L), verifyTenants)
        assertNull(TenantContext.get()) // Removed: the scheduler threads are reused
    }

    @Test
    fun `a failing tenant doesn't stop the others`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        doAnswer {
            if (TenantContext.get() == 1L) throw RuntimeException("DB down") else listOf(listing2)
        }.whenever(service).search(any())

        jobs.verify()

        verify(verifierService).verify(listing2)
        assertNull(TenantContext.get())
    }

    private fun setUpPublishListings(verified: List<ListingEntity>, publishing: List<ListingEntity>) {
        doReturn(verified).whenever(service).search(
            SearchListingRequest(statuses = listOf(ListingStatus.VERIFIED), limit = 100)
        )
        doReturn(publishing).whenever(service).search(
            SearchListingRequest(statuses = listOf(ListingStatus.PUBLISHING), limit = 100)
        )
    }

    @Test
    fun `publish - searches the verified, then the publishing listings, at most 100 each per run`() {
        setUpPublishListings(emptyList(), emptyList())

        jobs.publish()

        val order = inOrder(service)
        order.verify(service).search(
            SearchListingRequest(
                statuses = listOf(ListingStatus.VERIFIED),
                limit = 100,
            )
        )
        order.verify(service).search(
            SearchListingRequest(
                statuses = listOf(ListingStatus.PUBLISHING),
                limit = 100,
            )
        )
    }

    @Test
    fun `publish - sends every verified listing for publication, in the order found`() {
        val verified = listOf(listing1, listing2, listing3).map { it.copy(status = ListingStatus.VERIFIED) }
        setUpPublishListings(verified, emptyList())

        jobs.publish()

        val order = inOrder(service)
        verified.forEach { listing -> order.verify(service).publish(listing) }
        verify(publisherService, never()).publish(any())
    }

    @Test
    fun `publish - publishes every publishing listing, in the order found`() {
        val publishing = listOf(listing1, listing2, listing3).map { it.copy(status = ListingStatus.PUBLISHING) }
        setUpPublishListings(emptyList(), publishing)

        jobs.publish()

        val order = inOrder(publisherService)
        publishing.forEach { listing -> order.verify(publisherService).publish(listing) }
        verify(service, never()).publish(any<ListingEntity>())
        verify(verifierService, never()).verify(any())
    }

    @Test
    fun `publish - a verified listing is published in the same run`() {
        val verified = listing1.copy(status = ListingStatus.VERIFIED)
        val publishing = listing1.copy(status = ListingStatus.PUBLISHING)
        setUpPublishListings(listOf(verified), listOf(publishing)) // The second search finds it sent by the first pass

        jobs.publish()

        val order = inOrder(service, publisherService)
        order.verify(service).publish(verified)
        order.verify(publisherService).publish(publishing)
    }

    @Test
    fun `publish - a failing listing doesn't stop the others`() {
        val verified = listOf(listing1, listing2, listing3).map { it.copy(status = ListingStatus.VERIFIED) }
        val publishing = listOf(listing1, listing2, listing3).map { it.copy(status = ListingStatus.PUBLISHING) }
        setUpPublishListings(verified, publishing)
        doThrow(IllegalStateException("not verified")).whenever(service).publish(verified[1])
        doThrow(IllegalStateException("not publishing")).whenever(publisherService).publish(publishing[1])

        jobs.publish()

        verify(service, times(3)).publish(any<ListingEntity>())
        verify(publisherService, times(3)).publish(any())
    }

    @Test
    fun `publish - each tenant processed with its id in the tenant context`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        doAnswer {
            val request = it.arguments[0] as SearchListingRequest
            val listing = if (TenantContext.get() == 1L) listing1 else listing2
            listOf(listing.copy(status = request.statuses.first()))
        }.whenever(service).search(any())
        val sendTenants = mutableListOf<Long?>()
        doAnswer {
            sendTenants.add(TenantContext.get())
            it.arguments[0]
        }.whenever(service).publish(any<ListingEntity>())
        val publishTenants = mutableListOf<Long?>()
        doAnswer {
            publishTenants.add(TenantContext.get())
            it.arguments[0]
        }.whenever(publisherService).publish(any())

        jobs.publish()

        assertEquals(listOf<Long?>(1L, 2L), sendTenants)
        assertEquals(listOf<Long?>(1L, 2L), publishTenants)
        assertNull(TenantContext.get())
    }

    @Test
    fun `publish - a failing tenant doesn't stop the others`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        doAnswer {
            val request = it.arguments[0] as SearchListingRequest
            if (TenantContext.get() == 1L) throw RuntimeException("DB down")
            listOf(listing2.copy(status = request.statuses.first()))
        }.whenever(service).search(any())

        jobs.publish()

        verify(service).publish(listing2.copy(status = ListingStatus.VERIFIED))
        verify(publisherService).publish(listing2.copy(status = ListingStatus.PUBLISHING))
        assertNull(TenantContext.get())
    }
}
