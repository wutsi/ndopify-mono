package com.wutsi.ndopify.party.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartyAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = PartyAccessService(callerService)

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may search parties`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanSearch()
    }

    @Test
    fun `non-admin may not search parties`() {
        whenever(callerService.isAdmin(principal)).thenReturn(false)
        assertDenied { service.ensureCanSearch() }
    }
}
