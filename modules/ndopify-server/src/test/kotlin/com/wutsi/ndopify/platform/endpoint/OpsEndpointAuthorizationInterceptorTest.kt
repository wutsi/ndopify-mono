package com.wutsi.ndopify.platform.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpsEndpointAuthorizationInterceptorTest {
    private class Endpoints {
        @OpsEndpoint
        fun ops() {}

        fun regular() {}
    }

    private val caller = mock<CallerService>()
    private val interceptor = OpsEndpointAuthorizationInterceptor(caller)
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()

    private fun handler(name: String) = HandlerMethod(Endpoints(), Endpoints::class.java.getMethod(name))

    @Test
    fun `an admin calls an ops endpoint`() {
        val principal = mock<JwtPrincipal>()
        doReturn(principal).whenever(caller).getPrincipal()
        doReturn(true).whenever(caller).isAdmin(principal)

        assertTrue(interceptor.preHandle(request, response, handler("ops")))
    }

    @Test
    fun `a non-admin is forbidden`() {
        val principal = mock<JwtPrincipal>()
        doReturn(principal).whenever(caller).getPrincipal()
        doReturn(false).whenever(caller).isAdmin(principal)

        assertFailsWith<ForbiddenException> { interceptor.preHandle(request, response, handler("ops")) }
    }

    @Test
    fun `another handler is not checked`() {
        assertTrue(interceptor.preHandle(request, response, handler("regular")))
    }
}
