package com.wutsi.ndopify.party.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KycJobsTest {
    private val service = mock<KycService>()
    private val tenantService = mock<TenantService>()
    private val jobs = KycJobs(service, tenantService)

    private val tenant1 = TenantEntity(id = 1L)
    private val tenant2 = TenantEntity(id = 2L)

    private val case1 = KycCaseEntity(id = "case-1", status = KycStatus.SUBMITTED)
    private val case2 = KycCaseEntity(id = "case-2", status = KycStatus.SUBMITTED)
    private val case3 = KycCaseEntity(id = "case-3", status = KycStatus.SUBMITTED)

    // What verify() returns: the case once verified
    private val verified1 = case1.copy(status = KycStatus.VERIFIED)
    private val verified2 = case2.copy(status = KycStatus.REJECTED)
    private val verified3 = case3.copy(status = KycStatus.VERIFIED)

    @BeforeEach
    fun setUp() {
        doReturn(listOf(tenant1)).whenever(tenantService).search(true)
        doReturn(verified1).whenever(service).verify(case1.id)
        doReturn(verified2).whenever(service).verify(case2.id)
        doReturn(verified3).whenever(service).verify(case3.id)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    private fun setUpSubmittedCases(vararg cases: KycCaseEntity) {
        doReturn(cases.toList()).whenever(service).search(any())
    }

    @Test
    fun `searches the submitted cases only, at most 100 per run`() {
        setUpSubmittedCases()

        jobs.verify()

        verify(service).search(
            SearchKycCaseRequest(
                statuses = listOf(KycStatus.SUBMITTED),
                limit = 100,
            )
        )
    }

    @Test
    fun `verifies every submitted case, in the order found`() {
        setUpSubmittedCases(case1, case2, case3)

        jobs.verify()

        val order = inOrder(service)
        order.verify(service).verify(case1.id)
        order.verify(service).verify(case2.id)
        order.verify(service).verify(case3.id)
    }

    @Test
    fun `no submitted case - nothing verified`() {
        setUpSubmittedCases()

        jobs.verify()

        verify(service, never()).verify(any<String>())
    }

    @Test
    fun `a failing case doesn't stop the others`() {
        setUpSubmittedCases(case1, case2, case3)
        doThrow(RuntimeException("LLM unavailable")).whenever(service).verify(case2.id)

        jobs.verify()

        verify(service).verify(case1.id)
        verify(service).verify(case2.id)
        verify(service).verify(case3.id)
    }

    @Test
    fun `a case no longer submitted when verified - skipped, the others still verified`() {
        // e.g. picked up by a concurrent run between the search and the verification
        setUpSubmittedCases(case1, case2)
        doThrow(BadRequestException(error = Error(code = ErrorCode.KYC_CASE_NOT_SUBMITTED)))
            .whenever(service).verify(case1.id)

        jobs.verify()

        verify(service).verify(case2.id)
    }

    @Test
    fun `every case failing - the job itself doesn't fail`() {
        setUpSubmittedCases(case1, case2)
        doThrow(RuntimeException("storage down")).whenever(service).verify(any<String>())

        jobs.verify()

        verify(service, times(2)).verify(any<String>())
    }

    @Test
    fun `recovers the interrupted verifications before searching the submitted cases`() {
        setUpSubmittedCases(case1)

        jobs.verify()

        val order = inOrder(service)
        order.verify(service).recoverInterrupted()
        order.verify(service).search(any())
        order.verify(service).verify(case1.id)
    }

    @Test
    fun `recovery failing - the submitted cases still verified`() {
        setUpSubmittedCases(case1)
        doThrow(RuntimeException("DB down")).whenever(service).recoverInterrupted()

        jobs.verify()

        verify(service).verify(case1.id)
    }

    @Test
    fun `interrupted verifications recovered - submitted cases still verified`() {
        doReturn(listOf(case2)).whenever(service).recoverInterrupted()
        setUpSubmittedCases(case1, case2)

        jobs.verify()

        verify(service).verify(case1.id)
        verify(service).verify(case2.id)
    }

    @Test
    fun `only the active tenants are processed`() {
        setUpSubmittedCases()

        jobs.verify()

        verify(tenantService).search(true)
        verify(tenantService, never()).search(false)
        verify(tenantService, never()).search(null)
    }

    @Test
    fun `each tenant processed with its id in the tenant context`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        val recoverTenants = mutableListOf<Long?>()
        val searchTenants = mutableListOf<Long?>()
        val verifyTenants = mutableListOf<Long?>()
        doAnswer {
            recoverTenants.add(TenantContext.get())
            emptyList<KycCaseEntity>()
        }.whenever(service).recoverInterrupted()
        doAnswer {
            searchTenants.add(TenantContext.get())
            if (TenantContext.get() == 1L) listOf(case1) else listOf(case2)
        }.whenever(service).search(any())
        doAnswer {
            verifyTenants.add(TenantContext.get())
            if (it.getArgument<String>(0) == case1.id) verified1 else verified2
        }.whenever(service).verify(any<String>())

        jobs.verify()

        assertEquals(listOf<Long?>(1L, 2L), recoverTenants)
        assertEquals(listOf<Long?>(1L, 2L), searchTenants)
        assertEquals(listOf<Long?>(1L, 2L), verifyTenants)
    }

    @Test
    fun `tenant context removed once done`() {
        setUpSubmittedCases(case1)

        jobs.verify()

        assertNull(TenantContext.get())
    }

    @Test
    fun `tenant context removed when a tenant fails`() {
        doThrow(RuntimeException("DB down")).whenever(service).search(any())

        jobs.verify()

        assertNull(TenantContext.get())
    }

    @Test
    fun `a failing tenant doesn't stop the others`() {
        doReturn(listOf(tenant1, tenant2)).whenever(tenantService).search(true)
        doAnswer {
            if (TenantContext.get() == 1L) throw RuntimeException("DB down") else listOf(case2)
        }.whenever(service).search(any())

        jobs.verify()

        verify(service).verify(case2.id)
    }
}
