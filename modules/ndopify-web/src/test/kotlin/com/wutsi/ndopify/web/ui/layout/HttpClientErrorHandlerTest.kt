package com.wutsi.ndopify.web.ui.layout

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.client.HttpClientErrorException
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientErrorHandlerTest {
    private val request = MockHttpServletRequest("GET", "/douala/listing/123")

    @Test
    fun `not found from the server is a 404`() {
        val response = MockHttpServletResponse()

        HttpClientErrorHandler().clientError(request, response, HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", HttpHeaders.EMPTY, null, null))

        assertEquals(404, response.status)
    }

    @Test
    fun `forbidden from the server is a 403`() {
        val response = MockHttpServletResponse()

        HttpClientErrorHandler().clientError(request, response, HttpClientErrorException.create(HttpStatus.FORBIDDEN, "", HttpHeaders.EMPTY, null, null))

        assertEquals(403, response.status)
    }
}
