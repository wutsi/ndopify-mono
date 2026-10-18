package com.wutsi.ndopify.security.server.servlet

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TenantContextFilterTest {
    private val accessTokenService = mock<AccessTokenService>()
    private val tenantService = mock<TenantService>()
    private val chain = mock<FilterChain>()
    private val response = MockHttpServletResponse()

    private val filter = TenantContextFilter(accessTokenService, tenantService)

    private var tenantIdDuringChain: Long? = null

    @BeforeEach
    fun setUp() {
        TenantContext.remove()
        tenantIdDuringChain = null
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(any(), any())

        doReturn(TenantEntity(id = 100L, active = true)).whenever(tenantService).findByIdOrNull(100L)
        doReturn(TenantEntity(id = 200L, active = true)).whenever(tenantService).findByIdOrNull(200L)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    // Tenant resolution

    @Test
    fun `sets the tenant of the principal during the chain, then clears it`() {
        givenPrincipalTenant(100L)
        val request = request()

        filter.doFilter(request, response, chain)

        assertEquals(100L, tenantIdDuringChain)
        assertNull(TenantContext.get())
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `falls back to the tenant id header when there is no principal`() {
        val request = request(tenantHeader = "200")

        filter.doFilter(request, response, chain)

        assertEquals(200L, tenantIdDuringChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `tenant of the principal takes precedence over the header`() {
        givenPrincipalTenant(100L)

        filter.doFilter(request(tenantHeader = "200"), response, chain)

        assertEquals(100L, tenantIdDuringChain)
        verify(tenantService, never()).findByIdOrNull(200L)
    }

    @Test
    fun `principal without tenant falls back to the header`() {
        givenPrincipalTenant(null)

        filter.doFilter(request(tenantHeader = "200"), response, chain)

        assertEquals(200L, tenantIdDuringChain)
    }

    @Test
    fun `token decode failure falls back to the header`() {
        doThrow(ForbiddenException(error = Error(code = ErrorCode.AUTH_UNAUTHORIZED)))
            .whenever(accessTokenService).getPrincipalOrNull()

        filter.doFilter(request(tenantHeader = "200"), response, chain)

        assertEquals(200L, tenantIdDuringChain)
    }

    @Test
    fun `clears the tenant when the chain fails`() {
        givenPrincipalTenant(100L)
        doThrow(IllegalStateException("boom")).whenever(chain).doFilter(any(), any())

        assertFailsWith<IllegalStateException> {
            filter.doFilter(request(), response, chain)
        }

        assertNull(TenantContext.get())
    }

    // Rejections

    @Test
    fun `no principal nor header is a bad request`() {
        assertRejected(request(), ErrorCode.TENANT_MISSING_HEADER)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "1.5"])
    fun `header that is not a number is a bad request`(header: String) {
        assertRejected(request(tenantHeader = header), ErrorCode.TENANT_MISSING_HEADER)
    }

    @Test
    fun `unknown tenant is a bad request`() {
        doReturn(null).whenever(tenantService).findByIdOrNull(999L)

        assertRejected(request(tenantHeader = "999"), ErrorCode.TENANT_ID_NOT_VALID)
    }

    @Test
    fun `inactive tenant is a bad request`() {
        doReturn(TenantEntity(id = 300L, active = false)).whenever(tenantService).findByIdOrNull(300L)

        assertRejected(request(tenantHeader = "300"), ErrorCode.TENANT_NOT_ACTIVE)
    }

    @Test
    fun `inactive tenant of the principal is a bad request`() {
        givenPrincipalTenant(300L)
        doReturn(TenantEntity(id = 300L, active = false)).whenever(tenantService).findByIdOrNull(300L)

        assertRejected(request(), ErrorCode.TENANT_NOT_ACTIVE)
    }

    // Excluded paths

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/v1/tenants",
            "/v1/tenants/1",
            "/v1/locations",
            "/v1/locations/import",
            "/v1/locations/123",
            "/v1/applications",
            "/v1/applications/partner-central",
        ],
    )
    fun `refdata paths are not filtered`(path: String) {
        val request = request(uri = path)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        assertNull(tenantIdDuringChain)
        verifyNoInteractions(accessTokenService, tenantService)
    }

    @Test
    fun `refdata paths are not filtered under a context path`() {
        val request = request(uri = "/api/v1/locations").apply { contextPath = "/api" }

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        verifyNoInteractions(accessTokenService, tenantService)
    }

    @Test
    fun `refdata paths ignore the tenant id header`() {
        filter.doFilter(request(uri = "/v1/tenants", tenantHeader = "200"), response, chain)

        assertNull(tenantIdDuringChain)
        verifyNoInteractions(tenantService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/v1/tenantsfoo", "/v1/locations-x", "/v2/locations", "/v1/agents", "/v1/tenant"])
    fun `paths that only look like refdata are filtered`(path: String) {
        assertRejected(request(uri = path), ErrorCode.TENANT_MISSING_HEADER)
    }

    private fun request(uri: String = "/v1/agents", tenantHeader: String? = null) =
        MockHttpServletRequest("GET", uri).apply {
            tenantHeader?.let { addHeader(HttpHeader.TENANT_ID, it) }
        }

    private fun givenPrincipalTenant(tenantId: Long?) {
        val principal = mock<JwtPrincipal>()
        doReturn(tenantId).whenever(principal).getTenantId()
        doReturn(principal).whenever(accessTokenService).getPrincipalOrNull()
    }

    private fun assertRejected(request: MockHttpServletRequest, code: String) {
        val ex = assertFailsWith<BadRequestException> {
            filter.doFilter(request, response, chain)
        }

        assertEquals(code, ex.error.code)
        verify(chain, never()).doFilter(any(), any())
        assertNull(TenantContext.get())
    }
}
