package com.wutsi.ndopify.visit.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitConfirmedMailet
import com.wutsi.ndopify.visit.server.mail.VisitRequestedMailet
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisitServiceTest {
    private val dao = mock<VisitRepository>()
    private val matchDao = mock<MatchRepository>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val listingService = mock<ListingService>()
    private val tenantService = mock<TenantService>()
    private val requestedMailet = mock<VisitRequestedMailet>()
    private val confirmedMailet = mock<VisitConfirmedMailet>()
    private val codeGenerator = mock<VisitCodeGenerator>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val logger = DefaultKVLogger()

    // 2026-12-01 10:00 in Douala
    private val clock = Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneId.of("UTC"))

    private val service = VisitService(
        dao,
        matchDao,
        customerService,
        agentService,
        listingService,
        tenantService,
        requestedMailet,
        confirmedMailet,
        codeGenerator,
        eventPublisher,
        clock,
        logger,
    )

    private val tenant = TenantEntity(id = 1L, timeZone = "Africa/Douala")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        mandateId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.REQUESTED,
        type = VisitType.ON_SITE,
    )

    @BeforeTest
    fun setUp() {
        doReturn(visit).whenever(dao).findByIdForUpdate(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("1234").whenever(codeGenerator).generate()
        doAnswer { it.arguments[0] }.whenever(dao).save(any())
    }

    @Test
    fun `confirmed less than 24h ahead marks the reminder as sent`() {
        service.schedule(1L, ScheduleVisitRequest(scheduledAt = date(2026, 12, 2, 8, 0)))

        val saved = saved()
        assertEquals(VisitStatus.CONFIRMED, saved.status)
        assertEquals("1234", saved.code)
        assertEquals(Date(clock.millis()), saved.reminderSentAt)
    }

    @Test
    fun `confirmed 24h ahead or more leaves the reminder unsent`() {
        service.schedule(1L, ScheduleVisitRequest(scheduledAt = date(2026, 12, 2, 10, 0)))

        assertNull(saved().reminderSentAt)
    }

    private fun saved(): VisitEntity {
        val captor = argumentCaptor<VisitEntity>()
        verify(dao).save(captor.capture())
        return captor.firstValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
