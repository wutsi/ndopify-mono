package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListingServiceTest {
    private val dao = mock<ListingRepository>()
    private val mandateService = mock<MandateService>()
    private val agentService = mock<AgentService>()
    private val customerService = mock<CustomerService>()
    private val publishedNotifier = mock<ListingPublishedNotifier>()
    private val sentBackNotifier = mock<ListingSentBackNotifier>()
    private val sectionService = mock<ListingSectionService>()
    private val locationService = mock<LocationService>()
    private val accessService = mock<ListingAccessService>()
    private val clock = mock<Clock>()
    private val service = ListingService(
        dao,
        mandateService,
        agentService,
        customerService,
        publishedNotifier,
        sentBackNotifier,
        sectionService,
        locationService,
        accessService,
        clock,
    )

    private val agent = AgentEntity(id = 20L)
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        status = ListingStatus.DRAFT,
        errorCodes = listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES),
        agent = agent,
    )

    @Test
    fun `a sent-back listing notifies its agent`() {
        doReturn(Optional.of(listing)).whenever(dao).findById(101L)

        assertTrue(service.sendSentBackNotification(101L))
        verify(sentBackNotifier).send(listing, agent)
    }

    @Test
    fun `a listing no longer in draft is not notified`() {
        doReturn(Optional.of(listing.copy(status = ListingStatus.VERIFIED))).whenever(dao).findById(101L)

        assertFalse(service.sendSentBackNotification(101L))
        verifyNoInteractions(sentBackNotifier)
    }

    @Test
    fun `a listing without errors is not notified`() {
        doReturn(Optional.of(listing.copy(errorCodes = emptyList()))).whenever(dao).findById(101L)

        assertFalse(service.sendSentBackNotification(101L))
        verifyNoInteractions(sentBackNotifier)
    }
}
