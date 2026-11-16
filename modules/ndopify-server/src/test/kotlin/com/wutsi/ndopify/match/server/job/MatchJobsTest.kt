package com.wutsi.ndopify.match.server.job

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.match.server.service.CoBrokerageNotificationService
import com.wutsi.ndopify.match.server.service.MatchAutoProposalService
import com.wutsi.ndopify.match.server.service.MatchNotificationService
import com.wutsi.ndopify.match.server.service.MatchProposalService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.match.server.service.MatchVisitReminderService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchJobsTest {
    private val notificationService = mock<MatchNotificationService>()
    private val coBrokerageNotificationService = mock<CoBrokerageNotificationService>()
    private val proposalService = mock<MatchProposalService>()
    private val autoProposalService = mock<MatchAutoProposalService>()
    private val matchService = mock<MatchService>()
    private val reminderService = mock<MatchVisitReminderService>()
    private val tenantService = mock<TenantService>()
    private val jobs = MatchJobs(
        notificationService = notificationService,
        coBrokerageNotificationService = coBrokerageNotificationService,
        proposalService = proposalService,
        autoProposalService = autoProposalService,
        matchService = matchService,
        reminderService = reminderService,
        tenantService = tenantService,
    )

    private val tenants = mutableListOf<Long?>()

    @BeforeTest
    fun setUp() {
        doReturn(listOf(TenantEntity(id = 1L), TenantEntity(id = 2L)))
            .whenever(tenantService).search(true)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `autoPropose - each active tenant, with its tenant set`() {
        doAnswer {
            tenants.add(TenantContext.get())
            3
        }.whenever(autoProposalService).autoProposeDue()

        jobs.autoPropose()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertNull(TenantContext.get())
    }

    @Test
    fun `autoPropose - a failing tenant doesn't stop the others`() {
        doAnswer {
            tenants.add(TenantContext.get())
            if (TenantContext.get() == 1L) throw IllegalStateException("boom")
            0
        }.whenever(autoProposalService).autoProposeDue()

        jobs.autoPropose()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertNull(TenantContext.get())
    }

    @Test
    fun `remindAcceptedWithoutVisit - each active tenant, with its tenant set`() {
        doAnswer {
            tenants.add(TenantContext.get())
            3
        }.whenever(reminderService).remindDue()

        jobs.remindAcceptedWithoutVisit()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertNull(TenantContext.get())
    }
}
