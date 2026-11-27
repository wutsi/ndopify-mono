package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.listing.dto.CreateListingRequest
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.UpdateListingRequest
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.mandate.server.service.OwnershipVerdictService
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.mockito.Mockito.verifyNoInteractions
import java.time.Clock
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    private val ownershipVerdictService = mock<OwnershipVerdictService>()
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
        ownershipVerdictService,
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

    private val building = ListingEntity(
        id = 102L,
        tenantId = 1L,
        version = 0L,
        status = ListingStatus.DRAFT,
        listingType = ListingType.SALE,
        propertyType = PropertyType.BUILDING,
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

    @Test
    fun `create copies the counts of a building sale mandate`() {
        val mandate = MandateEntity(
            id = 1L,
            agentId = 20L,
            listingType = ListingType.SALE,
            propertyType = PropertyType.BUILDING,
            levels = 3,
            units = 12,
        )
        doReturn(mandate).whenever(mandateService).findById(1L)
        doReturn(false).whenever(dao).existsByMandate(mandate)
        doReturn(agent).whenever(agentService).findById(20L)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        service.create(CreateListingRequest(mandateId = 1L))

        val captor = argumentCaptor<ListingEntity>()
        verify(dao).save(captor.capture())
        assertEquals(3, captor.firstValue.levels)
        assertEquals(12, captor.firstValue.units)
    }

    @Test
    fun `a property update of a building saves its units and clears bathrooms and parkings`() {
        doReturn(Optional.of(building)).whenever(dao).findById(102L)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        service.update(
            102L,
            UpdateListingRequest(
                version = 0,
                section = ListingSection.PROPERTY,
                propertyCategory = PropertyCategory.COMMERCIAL,
                propertyType = PropertyType.BUILDING,
                bathrooms = 2,
                parkings = 3,
                area = 850,
                levels = 3,
                units = 12,
            ),
        )

        val captor = argumentCaptor<ListingEntity>()
        verify(dao).save(captor.capture())
        assertEquals(3, captor.firstValue.levels)
        assertEquals(12, captor.firstValue.units)
        assertNull(captor.firstValue.bathrooms)
        assertNull(captor.firstValue.parkings)
    }

    @Test
    fun `a building has no amenities section to save`() {
        doReturn(Optional.of(building)).whenever(dao).findById(102L)

        val exception = assertFailsWith<BadRequestException> {
            service.update(102L, UpdateListingRequest(
                version = 0,
                section = ListingSection.AMENITIES,
                roadPaved = true,
            ))
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("section", exception.error.parameter?.name)
        verify(dao, never()).save(any())
    }

    @Test
    fun `a villa still saves its amenities`() {
        val villa = building.copy(propertyType = PropertyType.VILLA)
        doReturn(Optional.of(villa)).whenever(dao).findById(102L)
        doReturn(villa).whenever(dao).save(any())

        service.update(
            102L,
            UpdateListingRequest(
                version = 0,
                section = ListingSection.AMENITIES,
                roadPaved = true,
                waterTank = true,
            ),
        )

        verify(dao).save(any())
    }

    @Test
    fun `verify passes a building whose six sections are verified`() {
        doReturn(Optional.of(building)).whenever(dao).findById(102L)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())
        doReturn(sections()).whenever(sectionService).summaries(building)

        val verified = service.verify(102L)

        assertEquals(ListingStatus.UNDER_VERIFICATION, verified.status)
    }

    @Test
    fun `a verified listing with a verified owner is sent for publication`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.VERIFIED))
            .whenever(ownershipVerdictService).verify(verified.mandate)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        val result = service.publish(verified)

        assertEquals(ListingStatus.PUBLISHING, result.status)
        assertEquals(0, result.retries)
    }

    @Test
    fun `a listing not verified is refused before the owner verdict`() {
        val ex = assertFailsWith<ConflictException> { service.publish(listing) }

        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, ex.error.code)
        verifyNoInteractions(ownershipVerdictService)
    }

    @Test
    fun `a verified listing whose owner is not verified is refused with the reasons`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        val reasons = listOf(
            OwnershipVerificationReason("OWNERSHIP_ID_MISSING"),
            OwnershipVerificationReason("OWNERSHIP_DOCUMENT_MISSING"),
        )
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.PENDING, reasons = reasons))
            .whenever(ownershipVerdictService).verify(verified.mandate)

        val ex = assertFailsWith<ConflictException> { service.publish(verified) }

        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
        assertEquals(mapOf("reasons" to reasons), ex.error.data)
        verify(dao, never()).save(any())
    }

    @Test
    fun `an in-flight owner verdict with no reason is still refused`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.PENDING))
            .whenever(ownershipVerdictService).verify(verified.mandate)

        val ex = assertFailsWith<ConflictException> { service.publish(verified) }

        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
        assertEquals(mapOf("reasons" to emptyList<OwnershipVerificationReason>()), ex.error.data)
    }

    @Test
    fun `a failed owner verdict is refused with its reasons`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        val reasons = listOf(
            OwnershipVerificationReason("OWNERSHIP_ID_UNUSABLE", listOf("DOCUMENT_UNREADABLE")),
        )
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED, reasons = reasons))
            .whenever(ownershipVerdictService).verify(verified.mandate)

        val ex = assertFailsWith<ConflictException> { service.publish(verified) }

        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
        assertEquals(mapOf("reasons" to reasons), ex.error.data)
    }

    @Test
    fun `the access check comes before the owner verdict`() {
        doReturn(Optional.of(listing.copy(status = ListingStatus.VERIFIED))).whenever(dao).findById(101L)
        doThrow(ForbiddenException(Error(ErrorCode.HTTP_ACCESS_DENIED)))
            .whenever(accessService).ensureCanUpdate(any())

        assertFailsWith<ForbiddenException> { service.publish(101L) }
        verifyNoInteractions(ownershipVerdictService)
    }

    private fun sections(): List<ListingSectionSummary> = listOf(
        ListingSection.LOCATION,
        ListingSection.GPS,
        ListingSection.PROPERTY,
        ListingSection.TERMS,
        ListingSection.PHOTOS,
        ListingSection.REMARKS,
    ).map { section ->
        ListingSectionSummary(
            section = section,
            status = ListingSectionStatus.VERIFIED,
            required = section != ListingSection.REMARKS,
        )
    }
}
