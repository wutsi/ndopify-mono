package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.ExistingSearchInterestEvent
import com.wutsi.ndopify.mandate.server.event.MandateApprovedEvent
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.mandate.server.event.MandateCreatedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.util.Date
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Approving, closing and cancelling: the age, the events and the notification, that the endpoint tests can't see. */
class MandateServiceTest {
    private val dao = mock<MandateRepository>()
    private val assignmentDao = mock<AssignmentRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val contractService = mock<ContractService>()
    private val approvedNotifier = mock<MandateApprovedNotifier>()
    private val originListingResolver = mock<OriginListingResolver>()
    private val service = MandateService(
        dao = dao,
        assignmentDao = assignmentDao,
        clock = clock,
        customerService = customerService,
        partyService = mock(),
        agentService = agentService,
        eventPublisher = eventPublisher,
        contractService = contractService,
        dispatcher = mock(),
        contractGenerator = mock(),
        storageProvider = mock(),
        tx = mock(),
        access = mock(),
        approvedNotifier = approvedNotifier,
        originListingResolver = originListingResolver,
        contractDurationMonths = 6,
    )

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun givenMandate(mandate: MandateEntity) {
        doReturn(Optional.of(mandate)).whenever(dao).findById(1L)
        doReturn(mandate.contractId).whenever(dao).findContractIdById(1L)
        doReturn(mandate).whenever(dao).findByIdForUpdate(1L)
        doReturn(emptyList<Any>()).whenever(assignmentDao).findByMandateAndStatusIn(any(), any())
    }

    private fun mandate(status: MandateStatus, approvedAt: Date? = null) = MandateEntity(
        id = 1L,
        status = status,
        createdAt = Date(now - 30 * day),
        approvedAt = approvedAt,
    )

    // What the visitor posts from the listing's page: contact, moving period and origin listing, never a criterion
    private val visitor = CreateMandateRequest(
        cityId = 9999L,
        neighbourhoodIds = listOf(99L),
        rooms = "9",
        price = Money(1, "EUR", PeriodicityType.YEARLY),
        movingPeriod = MovingPeriod.WITHIN_1_MONTH,
        firstName = "Awa",
        lastName = "Ndiaye",
        email = "awa@example.com",
        whatsappNumber = "+237650000001",
        originListingId = 7L,
    )

    // What the listing resolver returns: the listing's criteria, without any contact
    private val criteria = CreateMandateRequest(
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        cityId = 2370201L,
        neighbourhoodIds = listOf(11L),
        rooms = "2",
        waterTank = true,
        price = Money(175_000, "XAF", PeriodicityType.MONTHLY),
    )

    private fun givenCustomer() {
        doReturn(CustomerEntity(id = 10L)).whenever(customerService).findOrCreate(any())
        doAnswer { inv ->
            val entity = inv.getArgument<MandateEntity>(0)
            if (entity.id == null) entity.copy(id = 1L) else entity
        }.whenever(dao).save(any())
    }

    @Test
    fun `create seeds the criteria and the budget from the origin listing`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)

        val mandate = service.create(visitor)

        assertEquals(MandateType.SEARCH, mandate.mandateType)
        assertEquals(ListingType.RENTAL, mandate.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, mandate.propertyCategory)
        assertEquals(2370201L, mandate.cityId)
        assertEquals(listOf(11L), mandate.neighborhoodIds)
        assertEquals("2", mandate.rooms)
        assertTrue(mandate.waterTank)
        assertEquals(175_000L, mandate.price)
        assertEquals("XAF", mandate.currencyCode)
        assertEquals(PeriodicityType.MONTHLY, mandate.pricePeriodicityType)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, mandate.movingPeriod) // the visitor's
        assertEquals(7L, mandate.originListingId)
        assertEquals(10L, mandate.customerId)
    }

    @Test
    fun `create keeps no origin for a listing no longer published`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, false, criteria)).whenever(originListingResolver).resolve(7L)

        val mandate = service.create(visitor)

        assertEquals(2370201L, mandate.cityId) // still seeded from it
        assertEquals(175_000L, mandate.price)
        assertNull(mandate.originListingId)
    }

    @Test
    fun `create rejects an unknown origin listing`() {
        doReturn(null).whenever(originListingResolver).resolve(7L)

        val ex = assertFailsWith<NotFoundException> { service.create(visitor) }

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.error.code)
        verify(customerService, never()).findOrCreate(any())
        verify(dao, never()).save(any())
    }

    private fun givenExistingSearch(mandate: MandateEntity?) {
        doReturn(mandate).whenever(dao)
            .findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(
                any(), any(), any(), any(), any(),
            )
    }

    @Test
    fun `create attaches to an active search of the same city and type`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)
        givenExistingSearch(MandateEntity(id = 42L, customerId = 10L, cityId = 2370201L))

        val mandate = service.create(visitor)

        assertEquals(42L, mandate.id)
        assertEquals(7L, mandate.originListingId) // kept, for its proposal once the search is approved
        verify(eventPublisher).publishEvent(ExistingSearchInterestEvent(mandateId = 42L, listingId = 7L, tenantId = 1L))
    }

    @Test
    fun `attaching keeps the search's own origin`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)
        givenExistingSearch(MandateEntity(id = 42L, customerId = 10L, cityId = 2370201L, originListingId = 5L))

        val mandate = service.create(visitor)

        assertEquals(42L, mandate.id)
        assertEquals(5L, mandate.originListingId)
        verify(dao, never()).save(any())
        verify(eventPublisher).publishEvent(ExistingSearchInterestEvent(mandateId = 42L, listingId = 7L, tenantId = 1L))
    }

    @Test
    fun `another city or listing type creates a second mandate`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)
        givenExistingSearch(null)

        val mandate = service.create(visitor)

        assertEquals(1L, mandate.id)
        assertEquals(2370201L, mandate.cityId)
        verify(eventPublisher).publishEvent(MandateCreatedEvent(mandateId = 1L, tenantId = 1L))
        verify(eventPublisher, never())
            .publishEvent(ExistingSearchInterestEvent(mandateId = 42L, listingId = 7L, tenantId = 1L))
    }

    @Test
    fun `a fourth new search within 24 hours is refused`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)
        givenExistingSearch(null)
        doReturn(3L).whenever(dao).countByCustomerIdAndMandateTypeAndCreatedAtAfter(any(), any(), any())

        val ex = assertFailsWith<ConflictException> { service.create(visitor) }

        assertEquals(ErrorCode.MANDATE_RATE_LIMITED, ex.error.code)
        verify(dao, never()).save(any())
    }

    @Test
    fun `attaching is not capped`() {
        givenCustomer()
        doReturn(OriginListing(7L, 70L, true, criteria)).whenever(originListingResolver).resolve(7L)
        givenExistingSearch(MandateEntity(id = 42L, customerId = 10L, cityId = 2370201L))
        doReturn(3L).whenever(dao).countByCustomerIdAndMandateTypeAndCreatedAtAfter(any(), any(), any())

        assertEquals(42L, service.create(visitor).id)
    }

    @BeforeTest
    fun setUp() {
        TenantContext.set(1L)
        doReturn(now).whenever(clock).millis()
        doAnswer { it.getArgument<MandateEntity>(0) }.whenever(dao).save(any())
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `close - the age is the number of whole days since its approval`() {
        // 12 days and 23 hours
        doReturn(mandate(MandateStatus.APPROVED, Date(now - 13 * day + 3_600_000)))
            .whenever(dao).findByIdForUpdate(1L)

        val closed = service.close(1L, CloseMandateRequest(finalPrice = Money(amount = 100000)))

        assertEquals(MandateStatus.CLOSED, closed.status)
        assertEquals(12, closed.age)
        assertEquals(100000L, closed.finalPrice)
        assertEquals(Date(now), closed.closedAt)
        verify(eventPublisher).publishEvent(MandateClosedEvent(mandateId = 1L, tenantId = 1L))
    }

    @Test
    fun `close - approved today is 0 day old`() {
        doReturn(mandate(MandateStatus.APPROVED, Date(now - 60_000))).whenever(dao).findByIdForUpdate(1L)

        assertEquals(0, service.close(1L, CloseMandateRequest(finalPrice = Money(amount = 100000))).age)
    }

    @Test
    fun `close - without an approval date, the age is from its creation`() {
        doReturn(mandate(MandateStatus.APPROVED)).whenever(dao).findByIdForUpdate(1L)

        assertEquals(30, service.close(1L, CloseMandateRequest(finalPrice = Money(amount = 100000))).age)
    }

    @Test
    fun `close - not approved`() {
        doReturn(mandate(MandateStatus.ASSIGNED)).whenever(dao).findByIdForUpdate(1L)

        assertFailsWith<ConflictException> { service.close(1L, CloseMandateRequest()) }
        verify(dao, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `cancel - publishes the event`() {
        givenMandate(mandate(MandateStatus.ASSIGNED))

        val cancelled = service.cancel(1L, CancelMandateRequest(cancellationReason = "  "))

        assertEquals(MandateStatus.CANCELLED, cancelled.status)
        assertEquals(null, cancelled.cancellationReason)
        assertEquals(null, cancelled.age)
        val event = argumentCaptor<MandateClosedEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(MandateClosedEvent(mandateId = 1L, tenantId = 1L), event.firstValue)
    }

    @Test
    fun `cancel - cancels its contract before locking the mandate`() {
        givenMandate(mandate(MandateStatus.PENDING_APPROVAL).copy(contractId = "c-1"))

        service.cancel(1L, CancelMandateRequest())

        val order = inOrder(contractService, dao)
        order.verify(contractService).cancel("c-1")
        order.verify(dao).findByIdForUpdate(1L)
        verify(contractService, times(1)).cancel(any())
    }

    @Test
    fun `cancel - also cancels the contract linked meanwhile`() {
        doReturn(mandate(MandateStatus.ASSIGNED).copy(contractId = "c-2")).whenever(dao).findByIdForUpdate(1L)
        doReturn(emptyList<Any>()).whenever(assignmentDao).findByMandateAndStatusIn(any(), any())

        service.cancel(1L, CancelMandateRequest())

        verify(contractService).cancel("c-2")
        verify(contractService, times(1)).cancel(any())
    }

    @Test
    fun `cancel - without contract`() {
        givenMandate(mandate(MandateStatus.ASSIGNED))

        service.cancel(1L, CancelMandateRequest())

        verify(contractService, never()).cancel(any())
    }

    @Test
    fun `cancel - already closed`() {
        givenMandate(mandate(MandateStatus.CLOSED))

        assertFailsWith<ConflictException> { service.cancel(1L, CancelMandateRequest()) }
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `contract signed - approves the mandate and publishes the event`() {
        doReturn(1L).whenever(dao).findIdByContractId("c-1")
        doReturn(mandate(MandateStatus.PENDING_APPROVAL)).whenever(dao).findByIdForUpdate(1L)

        val approved = service.onContractSigned("c-1")

        assertEquals(MandateStatus.APPROVED, approved?.status)
        assertEquals(now, approved?.approvedAt?.time)
        val event = argumentCaptor<MandateApprovedEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(MandateApprovedEvent(mandateId = 1L, tenantId = 1L), event.firstValue)
    }

    @Test
    fun `contract signed - a mandate no longer waiting for approval is left as is`() {
        doReturn(1L).whenever(dao).findIdByContractId("c-1")
        doReturn(mandate(MandateStatus.CANCELLED)).whenever(dao).findByIdForUpdate(1L)

        assertNull(service.onContractSigned("c-1"))
        verify(dao, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `contract signed - a contract without mandate`() {
        doReturn(null).whenever(dao).findIdByContractId("c-1")

        assertNull(service.onContractSigned("c-1"))
        verify(dao, never()).findByIdForUpdate(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `contract signed - the mandate is locked before it's loaded`() {
        doReturn(1L).whenever(dao).findIdByContractId("c-1")
        doReturn(mandate(MandateStatus.PENDING_APPROVAL)).whenever(dao).findByIdForUpdate(1L)

        service.onContractSigned("c-1")

        verify(dao, never()).findByContractId(any())
        verify(dao, never()).findById(any())
    }

    @Test
    fun `update - locks the contract, then the mandate`() {
        givenMandate(mandate(MandateStatus.ASSIGNED).copy(contractId = "c-1"))

        val updated = service.update(1L, UpdateMandateRequest(comments = "Vue mer"))

        assertEquals(MandateStatus.ASSIGNED, updated.status)
        assertEquals("Vue mer", updated.comments)
        val order = inOrder(contractService, dao)
        order.verify(contractService).findByIdForUpdate("c-1")
        order.verify(dao).findByIdForUpdate(1L)
        verify(dao, never()).findById(any())
        verify(contractService, never()).cancel(any())
    }

    @Test
    fun `update - waiting for approval, its contract is cancelled and it's back to assigned`() {
        givenMandate(mandate(MandateStatus.PENDING_APPROVAL).copy(contractId = "c-1"))

        val updated = service.update(1L, UpdateMandateRequest(comments = "Vue mer"))

        assertEquals(MandateStatus.ASSIGNED, updated.status)
        assertEquals("c-1", updated.contractId)
        verify(contractService).cancel("c-1")
    }

    @Test
    fun `update - without contract`() {
        givenMandate(mandate(MandateStatus.PENDING_ASSIGNMENT))

        val updated = service.update(1L, UpdateMandateRequest(comments = "Vue mer"))

        assertEquals(MandateStatus.PENDING_ASSIGNMENT, updated.status)
        verify(contractService, never()).findByIdForUpdate(any())
        verify(contractService, never()).cancel(any())
    }

    @Test
    fun `update - approved`() {
        givenMandate(mandate(MandateStatus.APPROVED).copy(contractId = "c-1"))

        val ex = assertFailsWith<ConflictException> { service.update(1L, UpdateMandateRequest()) }

        assertEquals(ErrorCode.MANDATE_ALREADY_APPROVED, ex.error.code)
        verify(dao, never()).save(any())
        verify(contractService, never()).cancel(any())
    }

    @Test
    fun `update - closed or cancelled`() {
        listOf(MandateStatus.CLOSED, MandateStatus.CANCELLED).forEach { status ->
            givenMandate(mandate(status))

            val ex = assertFailsWith<ConflictException> { service.update(1L, UpdateMandateRequest()) }

            assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, ex.error.code)
        }
        verify(dao, never()).save(any())
    }

    @Test
    fun `update - contract replaced since it was locked`() {
        doReturn("c-1").whenever(dao).findContractIdById(1L)
        doReturn(mandate(MandateStatus.PENDING_APPROVAL).copy(contractId = "c-2")).whenever(dao).findByIdForUpdate(1L)

        val ex = assertFailsWith<ConflictException> { service.update(1L, UpdateMandateRequest()) }

        assertEquals(ErrorCode.MANDATE_VERSION_CONFLICT, ex.error.code)
        verify(dao, never()).save(any())
        verify(contractService, never()).cancel(any())
    }

    @Test
    fun `approved notification - sent to the agent`() {
        val mandate = mandate(MandateStatus.APPROVED).copy(customerId = 10L, agentId = 20L, contractId = "c-1")
        val contract = ContractEntity(id = "c-1")
        val customer = CustomerEntity(id = 10L)
        val agent = AgentEntity(id = 20L)
        doReturn(Optional.of(mandate)).whenever(dao).findById(1L)
        doReturn(contract).whenever(contractService).findById("c-1")
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        assertTrue(service.sendApprovedNotification(1L))

        verify(approvedNotifier).send(mandate, contract, customer, agent)
    }

    @Test
    fun `approved notification - not sent once the mandate has moved on`() {
        val mandate = mandate(MandateStatus.CLOSED).copy(agentId = 20L, contractId = "c-1")
        doReturn(Optional.of(mandate)).whenever(dao).findById(1L)

        assertFalse(service.sendApprovedNotification(1L))

        verifyNoInteractions(approvedNotifier)
    }

    @Test
    fun `approved notification - not sent without agent`() {
        val mandate = mandate(MandateStatus.APPROVED).copy(contractId = "c-1")
        doReturn(Optional.of(mandate)).whenever(dao).findById(1L)

        assertFalse(service.sendApprovedNotification(1L))

        verifyNoInteractions(approvedNotifier)
    }
}
