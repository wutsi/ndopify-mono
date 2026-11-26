package com.wutsi.ndopify.mandate.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.server.service.document.MandateDocumentExtractionService
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

class MandateDocumentJobsTest {
    private val service = mock<MandateDocumentExtractionService>()
    private val tenantService = mock<TenantService>()
    private val jobs = MandateDocumentJobs(service, tenantService)

    private val tenants = mutableListOf<Long?>()
    private val timeZones = mutableListOf<String>()
    private val loggers = mutableListOf<Boolean>()

    @BeforeTest
    fun setUp() {
        doReturn(
            listOf(
                TenantEntity(id = 1L, timeZone = "UTC"),
                TenantEntity(id = 2L, timeZone = "Africa/Douala"),
            ),
        ).whenever(tenantService).search(true)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `extract - each active tenant, with its tenant and time zone`() {
        doAnswer {
            tenants.add(TenantContext.get())
            timeZones.add(it.getArgument(0))
            loggers.add(KVLoggerThreadLocal.get() != null)
            3
        }.whenever(service).extractDue(any())

        jobs.extract()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertEquals(listOf("UTC", "Africa/Douala"), timeZones)
        assertTrue(loggers.all { it })
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `extract - a failing tenant doesn't stop the others`() {
        doAnswer {
            tenants.add(TenantContext.get())
            timeZones.add(it.getArgument(0))
            loggers.add(KVLoggerThreadLocal.get() != null)
            if (TenantContext.get() == 1L) throw IllegalStateException("boom")
            0
        }.whenever(service).extractDue(any())

        jobs.extract()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertEquals(listOf("UTC", "Africa/Douala"), timeZones)
        assertTrue(loggers.all { it })
        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `extract - no active tenant, nothing to do`() {
        doReturn(emptyList<TenantEntity>()).whenever(tenantService).search(true)

        jobs.extract()

        assertEquals(emptyList<Long?>(), tenants)
        assertNull(TenantContext.get())
    }
}
