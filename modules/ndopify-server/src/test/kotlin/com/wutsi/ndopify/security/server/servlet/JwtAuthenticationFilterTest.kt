package com.wutsi.ndopify.security.server.servlet

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JwtAuthenticationFilterTest {
    private val accessTokenService = mock<AccessTokenService>()
    private val chain = mock<FilterChain>()
    private val response = MockHttpServletResponse()

    private val filter = JwtAuthenticationFilter(accessTokenService)

    private var authenticationDuringChain: Authentication? = null

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        doAnswer { authenticationDuringChain = SecurityContextHolder.getContext().authentication }
            .whenever(chain).doFilter(any(), any())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `authenticates the principal of the token`() {
        val principal = mock<JwtPrincipal>()
        doReturn(principal).whenever(accessTokenService).decode("token")
        val request = request("Bearer token")

        filter.doFilter(request, response, chain)

        assertTrue(authenticationDuringChain!!.isAuthenticated)
        assertSame(principal, authenticationDuringChain!!.principal)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `request without token stays anonymous`() {
        filter.doFilter(request(null), response, chain)

        assertNull(authenticationDuringChain)
        verifyNoInteractions(accessTokenService)
    }

    @Test
    fun `token that does not decode leaves the request anonymous`() {
        doThrow(IllegalStateException("expired")).whenever(accessTokenService).decode("token")
        val request = request("Bearer token")

        filter.doFilter(request, response, chain)

        assertNull(authenticationDuringChain)
        verify(chain).doFilter(request, response)
        assertEquals(200, response.status)
    }

    private fun request(authorization: String?) =
        MockHttpServletRequest("GET", "/v1/agents/1").apply {
            authorization?.let { addHeader("Authorization", it) }
        }
}
