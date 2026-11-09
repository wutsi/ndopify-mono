package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.Money
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Closing both sides of a deal: the lock order, and the no-op rule. */
class MandateServiceCloseDealTest {
    private val dao = mock<MandateRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()
    private val service = MandateService(
        dao = dao,
        assignmentDao = mock(),
        clock = clock,
        customerService = mock(),
        agentService = mock(),
        eventPublisher = eventPublisher,
        contractService = mock(),
        dispatcher = mock(),
        contractGenerator = mock(),
        storageProvider = mock(),
        tx = mock(),
        access = mock(),
        approvedMailet = mock(),
        contractDurationMonths = 6,
    )

    private val now = 1_800_000_000_000L
    private val price = Money(amount = 140000, currency = "XAF")

    private fun mandate(id: Long, status: MandateStatus) = MandateEntity(
        id = id,
        status = status,
        createdAt = Date(now - 30L * 24 * 60 * 60 * 1000),
        approvedAt = Date(now - 10L * 24 * 60 * 60 * 1000),
        agentId = 20L,
    )

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
    fun `locks the mandates in ascending id order`() {
        doReturn(mandate(1, MandateStatus.APPROVED)).whenever(dao).findByIdForUpdate(1L)
        doReturn(mandate(6, MandateStatus.APPROVED)).whenever(dao).findByIdForUpdate(6L)

        service.closeDeal(searchMandateId = 6, counterpartMandateId = 1, matchId = 100, finalPrice = price)

        val order = inOrder(dao)
        order.verify(dao).findByIdForUpdate(1L)
        order.verify(dao).findByIdForUpdate(6L)
    }

    @Test
    fun `both sides closed is a conflict`() {
        doReturn(mandate(1, MandateStatus.CLOSED)).whenever(dao).findByIdForUpdate(1L)
        doReturn(mandate(6, MandateStatus.CLOSED)).whenever(dao).findByIdForUpdate(6L)

        val ex = assertFailsWith<ConflictException> {
            service.closeDeal(searchMandateId = 6, counterpartMandateId = 1, matchId = 100, finalPrice = price)
        }

        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, ex.error.code)
    }
}
