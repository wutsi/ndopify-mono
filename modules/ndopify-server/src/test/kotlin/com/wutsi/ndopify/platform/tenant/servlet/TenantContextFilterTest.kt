package com.wutsi.ndopify.platform.tenant.servlet

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.security.dto.JWTPrincipal
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TenantContextFilterTest {
    private val accessTokenService = mock<AccessTokenService>()
    private val request = mock<HttpServletRequest>()
    private val response = mock<ServletResponse>()
    private val chain = mock<FilterChain>()

    private val filter = TenantContextFilter(accessTokenService)

    @BeforeEach
    fun setUp() {
        TenantContext.remove()
    }

    @Test
    fun `sets tenant id from principal during the chain, then clears it`() {
        val principal = mock<JWTPrincipal>()
        doReturn(100L).whenever(principal).getTenantId()
        doReturn(principal).whenever(accessTokenService).getPrincipalOrNull()

        var tenantIdDuringChain: Long? = null
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(request, response)

        filter.doFilter(request, response, chain)

        assertEquals(100L, tenantIdDuringChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `no principal leaves tenant id null`() {
        doReturn(null).whenever(accessTokenService).getPrincipalOrNull()

        var tenantIdDuringChain: Long? = -1L
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(request, response)

        filter.doFilter(request, response, chain)

        assertNull(tenantIdDuringChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `falls back to the tenant id header when there is no principal`() {
        doReturn(null).whenever(accessTokenService).getPrincipalOrNull()
        doReturn("200").whenever(request).getHeader(HttpHeader.TENANT_ID)

        var tenantIdDuringChain: Long? = null
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(request, response)

        filter.doFilter(request, response, chain)

        assertEquals(200L, tenantIdDuringChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `principal tenant id takes precedence over the header`() {
        val principal = mock<JWTPrincipal>()
        doReturn(100L).whenever(principal).getTenantId()
        doReturn(principal).whenever(accessTokenService).getPrincipalOrNull()
        doReturn("200").whenever(request).getHeader(HttpHeader.TENANT_ID)

        var tenantIdDuringChain: Long? = null
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(request, response)

        filter.doFilter(request, response, chain)

        assertEquals(100L, tenantIdDuringChain)
    }

    @Test
    fun `decode failure leaves tenant id null and does not propagate`() {
        doThrow(
            ForbiddenException(error = Error(code = ErrorCode.AUTH_UNAUTHORIZED)),
        ).whenever(accessTokenService).getPrincipalOrNull()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(TenantContext.get())
    }
}
