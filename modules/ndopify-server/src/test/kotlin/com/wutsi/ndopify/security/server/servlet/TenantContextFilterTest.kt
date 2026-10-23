package com.wutsi.ndopify.security.server.servlet

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.error.server.exception.WutsiException
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TenantContextFilterTest {
    private val accessTokenService = mock<AccessTokenService>()
    private val tenantService = mock<TenantService>()
    private val exceptionResolver = mock<HandlerExceptionResolver>()
    private val chain = mock<FilterChain>()
    private val response = MockHttpServletResponse()

    private val filter = TenantContextFilter(accessTokenService, tenantService, exceptionResolver)

    private var tenantIdDuringChain: Long? = null

    @BeforeEach
    fun setUp() {
        TenantContext.remove()
        tenantIdDuringChain = null
        doAnswer { tenantIdDuringChain = TenantContext.get() }.whenever(chain).doFilter(any(), any())

        doReturn(TenantEntity(id = 100L, active = true)).whenever(tenantService).findByIdOrNull(100L)
        doReturn(TenantEntity(id = 200L, active = true)).whenever(tenantService).findByIdOrNull(200L)
        doReturn(ModelAndView()).whenever(exceptionResolver).resolveException(any(), any(), anyOrNull(), any())
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
    fun `falls back to the tenant id header on a public endpoint when there is no principal`() {
        val request = publicRequest(tenantHeader = "200")

        filter.doFilter(request, response, chain)

        assertEquals(200L, tenantIdDuringChain)
        assertNull(TenantContext.get())
    }

    @Test
    fun `header naming the tenant of the principal is accepted`() {
        givenPrincipalTenant(100L)

        filter.doFilter(request(tenantHeader = "100"), response, chain)

        assertEquals(100L, tenantIdDuringChain)
    }

    @Test
    fun `principal without tenant falls back to the header on a public endpoint`() {
        givenPrincipalTenant(null)

        filter.doFilter(publicRequest(tenantHeader = "200"), response, chain)

        assertEquals(200L, tenantIdDuringChain)
    }

    @ParameterizedTest
    @CsvSource(
        "POST, /v1/otp",
        "POST, /v1/auth",
        "POST, /v1/agents",
        "POST, /v1/mandates",
        "GET, /v1/contracts/c-1/signatures/abc",
    )
    fun `public endpoints take the tenant from the header`(method: String, path: String) {
        filter.doFilter(request(method = method, uri = path, tenantHeader = "200"), response, chain)

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

    @ParameterizedTest
    @CsvSource(
        "GET, /v1/agents",
        "GET, /v1/agents/1",
        "POST, /v1/agents/1",
        "GET, /v1/mandates",
        "POST, /v1/mandates/1/close",
        "GET, /v1/otp",
        "GET, /v1/contracts/c-1",
        "GET, /v1/kyc/cases",
    )
    fun `header without principal is unauthorized on a protected endpoint`(method: String, path: String) {
        val ex = assertRejected(request(method = method, uri = path, tenantHeader = "200"), ErrorCode.AUTH_UNAUTHORIZED)

        assertTrue(ex is UnauthorizedException)
        verifyNoInteractions(tenantService)
    }

    @Test
    fun `principal without tenant is unauthorized on a protected endpoint`() {
        givenPrincipalTenant(null)

        assertRejected(request(tenantHeader = "200"), ErrorCode.AUTH_UNAUTHORIZED)
    }

    @Test
    fun `header naming another tenant than the principal's is forbidden`() {
        givenPrincipalTenant(100L)

        val ex = assertRejected(request(tenantHeader = "200"), ErrorCode.AUTH_ACCESS_DENIED)

        assertTrue(ex is ForbiddenException)
        verifyNoInteractions(tenantService)
    }

    @Test
    fun `no principal nor header is a bad request on a public endpoint`() {
        assertRejected(publicRequest(), ErrorCode.TENANT_MISSING_HEADER)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "1.5"])
    fun `header that is not a number is a bad request`(header: String) {
        assertRejected(publicRequest(tenantHeader = header), ErrorCode.TENANT_MISSING_HEADER)
    }

    @Test
    fun `unknown tenant is a bad request`() {
        doReturn(null).whenever(tenantService).findByIdOrNull(999L)

        assertRejected(publicRequest(tenantHeader = "999"), ErrorCode.TENANT_ID_NOT_VALID)
    }

    @Test
    fun `inactive tenant is a bad request`() {
        doReturn(TenantEntity(id = 300L, active = false)).whenever(tenantService).findByIdOrNull(300L)

        assertRejected(publicRequest(tenantHeader = "300"), ErrorCode.TENANT_NOT_ACTIVE)
    }

    @Test
    fun `inactive tenant of the principal is a bad request`() {
        givenPrincipalTenant(300L)
        doReturn(TenantEntity(id = 300L, active = false)).whenever(tenantService).findByIdOrNull(300L)

        assertRejected(request(), ErrorCode.TENANT_NOT_ACTIVE)
    }

    @Test
    fun `token that does not decode is unauthorized, even with a tenant id header`() {
        val error = Error(code = ErrorCode.AUTH_UNAUTHORIZED, message = "The Token has expired")
        doThrow(ForbiddenException(error)).whenever(accessTokenService).getPrincipalOrNull()

        val ex = assertRejected(request(tenantHeader = "200"), ErrorCode.AUTH_UNAUTHORIZED)

        assertTrue(ex is UnauthorizedException)
        assertNull(ex.error.message)
        verifyNoInteractions(tenantService)
    }

    @Test
    fun `rejection is thrown when the resolver does not handle it`() {
        doReturn(null).whenever(exceptionResolver).resolveException(any(), any(), anyOrNull(), any())

        val ex = assertFailsWith<BadRequestException> {
            filter.doFilter(publicRequest(), response, chain)
        }

        assertEquals(ErrorCode.TENANT_MISSING_HEADER, ex.error.code)
        verify(chain, never()).doFilter(any(), any())
        assertNull(TenantContext.get())
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
            "/api.html",
            "/swagger-ui",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/v3/api-docs/agent",
            "/error",
        ],
    )
    fun `refdata and swagger paths are not filtered`(path: String) {
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
    @ValueSource(strings = ["/v1/tenantsfoo", "/v1/locations-x", "/v2/locations", "/v1/agents", "/v1/tenant", "/api.htmlx", "/swagger-uix", "/v3/api-docsx"])
    fun `paths that only look like excluded ones are filtered`(path: String) {
        assertRejected(request(uri = path), ErrorCode.AUTH_UNAUTHORIZED)
    }

    private fun request(method: String = "GET", uri: String = "/v1/agents", tenantHeader: String? = null) =
        MockHttpServletRequest(method, uri).apply {
            tenantHeader?.let { addHeader(HttpHeader.TENANT_ID, it) }
        }

    private fun publicRequest(tenantHeader: String? = null) =
        request(method = "POST", uri = "/v1/otp", tenantHeader = tenantHeader)

    private fun givenPrincipalTenant(tenantId: Long?) {
        val principal = mock<JwtPrincipal>()
        doReturn(tenantId).whenever(principal).getTenantId()
        doReturn(principal).whenever(accessTokenService).getPrincipalOrNull()
    }

    // The rejection goes to Spring MVC's resolver (ControllerErrorHandler), without a handler since none was reached
    private fun assertRejected(request: MockHttpServletRequest, code: String): WutsiException {
        filter.doFilter(request, response, chain)

        val ex = argumentCaptor<Exception>()
        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), ex.capture())
        val rejection = ex.firstValue as WutsiException
        assertEquals(code, rejection.error.code)
        verify(chain, never()).doFilter(any(), any())
        assertNull(TenantContext.get())
        return rejection
    }
}
