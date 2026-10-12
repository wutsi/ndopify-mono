package com.wutsi.ndopify.party.server.job

import com.nhaarman.mockitokotlin2.any
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
import com.wutsi.ndopify.party.server.service.KycMailet
import com.wutsi.ndopify.party.server.service.KycService
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import kotlin.test.Test

class KycJobsTest {
    private val service = mock<KycService>()
    private val mailet = mock<KycMailet>()
    private val jobs = KycJobs(service, mailet)

    private val case1 = KycCaseEntity(id = "case-1", status = KycStatus.SUBMITTED)
    private val case2 = KycCaseEntity(id = "case-2", status = KycStatus.SUBMITTED)
    private val case3 = KycCaseEntity(id = "case-3", status = KycStatus.SUBMITTED)

    // What verify() returns: the case once verified, which is the one the mail is about.
    private val verified1 = case1.copy(status = KycStatus.VERIFIED)
    private val verified2 = case2.copy(status = KycStatus.REJECTED)
    private val verified3 = case3.copy(status = KycStatus.VERIFIED)

    @BeforeEach
    fun setUp() {
        doReturn(verified1).whenever(service).verify(case1)
        doReturn(verified2).whenever(service).verify(case2)
        doReturn(verified3).whenever(service).verify(case3)
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
        order.verify(service).verify(case1)
        order.verify(service).verify(case2)
        order.verify(service).verify(case3)
    }

    @Test
    fun `no submitted case - nothing verified`() {
        setUpSubmittedCases()

        jobs.verify()

        verify(service, never()).verify(any<KycCaseEntity>())
        verify(mailet, never()).send(any())
    }

    @Test
    fun `mails the agent the verified case, whatever the outcome`() {
        setUpSubmittedCases(case1, case2)

        jobs.verify()

        val order = inOrder(service, mailet)
        order.verify(service).verify(case1)
        order.verify(mailet).send(verified1)
        order.verify(service).verify(case2)
        order.verify(mailet).send(verified2)
    }

    @Test
    fun `a failing case doesn't stop the others`() {
        setUpSubmittedCases(case1, case2, case3)
        doThrow(RuntimeException("LLM unavailable")).whenever(service).verify(case2)

        jobs.verify()

        verify(service).verify(case1)
        verify(service).verify(case2)
        verify(service).verify(case3)
        verify(mailet).send(verified1)
        verify(mailet).send(verified3)
        verify(mailet, times(2)).send(any())
    }

    @Test
    fun `a failing mail doesn't stop the others`() {
        setUpSubmittedCases(case1, case2)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(verified1)

        jobs.verify()

        verify(service).verify(case2)
        verify(mailet).send(verified2)
    }

    @Test
    fun `a case no longer submitted when verified - skipped, the others still verified`() {
        // e.g. picked up by a concurrent run between the search and the verification
        setUpSubmittedCases(case1, case2)
        doThrow(BadRequestException(error = Error(code = ErrorCode.KYC_CASE_NOT_SUBMITTED)))
            .whenever(service).verify(case1)

        jobs.verify()

        verify(service).verify(case2)
        verify(mailet, never()).send(case1)
        verify(mailet).send(verified2)
    }

    @Test
    fun `every case failing - the job itself doesn't fail`() {
        setUpSubmittedCases(case1, case2)
        doThrow(RuntimeException("storage down")).whenever(service).verify(any<KycCaseEntity>())

        jobs.verify()

        verify(service, times(2)).verify(any<KycCaseEntity>())
        verify(mailet, never()).send(any())
    }
}
