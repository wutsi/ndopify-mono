package com.wutsi.ndopify.mandate.server.job

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateJobsTest {
    private val service = mock<AssignmentService>()
    private val tenantService = mock<TenantService>()
    private val jobs = MandateJobs(service, tenantService)

    // The tenant set in TenantContext on each call
    private val tenants = mutableListOf<Long?>()

    // Whether a KVLogger was installed on the scheduler thread during each call
    private val loggers = mutableListOf<Boolean>()

    @BeforeTest
    fun setUp() {
        doReturn(listOf(TenantEntity(id = 1L), TenantEntity(id = 2L))).whenever(tenantService).search(true)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `expire - each active tenant, with its tenant set`() {
        doAnswer {
            tenants.add(TenantContext.get())
            loggers.add(KVLoggerThreadLocal.get() != null)
            3
        }.whenever(service).expireDue()

        jobs.expire()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertTrue(loggers.all { it }) // A KVLogger was installed on the scheduler thread
        assertNull(TenantContext.get()) // Removed: the scheduler threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `expire - a failing tenant doesn't stop the others`() {
        doAnswer {
            tenants.add(TenantContext.get())
            loggers.add(KVLoggerThreadLocal.get() != null)
            if (TenantContext.get() == 1L) throw IllegalStateException("boom")
            0
        }.whenever(service).expireDue()

        jobs.expire()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertTrue(loggers.all { it })
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `dispatch - each active tenant, with its tenant set`() {
        doAnswer {
            tenants.add(TenantContext.get())
            loggers.add(KVLoggerThreadLocal.get() != null)
            1
        }.whenever(service).dispatchPending()

        jobs.dispatch()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertTrue(loggers.all { it })
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `dispatch - a failing tenant doesn't stop the others`() {
        doAnswer {
            tenants.add(TenantContext.get())
            loggers.add(KVLoggerThreadLocal.get() != null)
            if (TenantContext.get() == 1L) throw IllegalStateException("boom")
            0
        }.whenever(service).dispatchPending()

        jobs.dispatch()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertTrue(loggers.all { it })
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `no active tenant - nothing to do`() {
        doReturn(emptyList<TenantEntity>()).whenever(tenantService).search(true)

        jobs.expire()
        jobs.dispatch()

        assertEquals(emptyList<Long?>(), tenants)
    }
}
