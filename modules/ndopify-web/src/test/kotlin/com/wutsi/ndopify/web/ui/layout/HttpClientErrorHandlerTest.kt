package com.wutsi.ndopify.web.ui.layout

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientErrorHandlerTest {
    private val request = MockHttpServletRequest("GET", "/douala/listing/123")
    private val accessTokenService = mock<AccessTokenService>()
    private val handler = HttpClientErrorHandler(accessTokenService)

    @Test
    fun `not found from the server is a 404`() {
        val response = MockHttpServletResponse()

        handler.clientError(request, response, HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", HttpHeaders.EMPTY, null, null))

        assertEquals(404, response.status)
    }

    @Test
    fun `forbidden from the server is a 403`() {
        val response = MockHttpServletResponse()

        handler.clientError(request, response, HttpClientErrorException.create(HttpStatus.FORBIDDEN, "", HttpHeaders.EMPTY, null, null))

        assertEquals(403, response.status)
    }

    @Test
    fun `unauthorized with a cookie clears it and goes back to the page`() {
        doReturn("aaa.bbb.ccc").whenever(accessTokenService).get()
        val response = MockHttpServletResponse()

        val view = handler.unauthorized(request, response)

        assertEquals("redirect:/douala/listing/123", view)
        verify(accessTokenService).remove()
    }

    @Test
    fun `unauthorized without a cookie is a 404, no redirect`() {
        doReturn(null).whenever(accessTokenService).get()
        val response = MockHttpServletResponse()

        val view = handler.unauthorized(request, response)

        assertNull(view)
        assertEquals(404, response.status)
        verify(accessTokenService, never()).remove()
    }
}
