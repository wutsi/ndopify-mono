package com.wutsi.ndopify.security.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = UserAccessService(callerService)

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may read any user`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanRead(UserEntity(id = 1))
    }

    @Test
    fun `the user may read itself`() {
        whenever(principal.getUserId()).thenReturn(1L)
        service.ensureCanRead(UserEntity(id = 1))
    }

    @Test
    fun `another user may not read it`() {
        whenever(principal.getUserId()).thenReturn(2L)
        assertDenied { service.ensureCanRead(UserEntity(id = 1)) }
    }

    @Test
    fun `a user without an id is denied`() {
        whenever(principal.getUserId()).thenReturn(1L)
        assertDenied { service.ensureCanRead(UserEntity()) }
    }
}
