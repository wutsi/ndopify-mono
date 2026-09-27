package com.wutsi.ndopify.agent.server.cron

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.SearchMobileChangeRequest
import com.wutsi.ndopify.agent.server.domain.MobileChangeEntity
import com.wutsi.ndopify.agent.server.service.MobileChangeService
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.mockito.Mockito.mock
import kotlin.test.Test

class MobileChangeJobsTest {
    private val service = mock<MobileChangeService>()
    private val jobs = MobileChangeJobs(service)

    private val expectedSearchRequest = SearchMobileChangeRequest(
        status = KycStatus.PENDING,
        limit = 1000,
    )

    @Test
    fun `verify pending mobile change requests`() {
        val change1 = MobileChangeEntity(id = 1L)
        val change2 = MobileChangeEntity(id = 2L)
        doReturn(listOf(change1, change2)).whenever(service).search(expectedSearchRequest, null)

        jobs.verify()

        verify(service).search(expectedSearchRequest, null)
        verify(service).verify(change1)
        verify(service).verify(change2)
    }

    @Test
    fun `no pending mobile change requests`() {
        doReturn(emptyList<MobileChangeEntity>()).whenever(service).search(expectedSearchRequest, null)

        jobs.verify()

        verify(service).search(expectedSearchRequest, null)
        verify(service, times(0)).verify(any<MobileChangeEntity>())
    }

    @Test
    fun `continues verifying remaining requests when one fails`() {
        val change1 = MobileChangeEntity(id = 1L)
        val change2 = MobileChangeEntity(id = 2L)
        doReturn(listOf(change1, change2)).whenever(service).search(expectedSearchRequest, null)
        doThrow(RuntimeException("boom")).whenever(service).verify(change1)

        jobs.verify()

        verify(service).verify(change1)
        verify(service).verify(change2)
    }
}
