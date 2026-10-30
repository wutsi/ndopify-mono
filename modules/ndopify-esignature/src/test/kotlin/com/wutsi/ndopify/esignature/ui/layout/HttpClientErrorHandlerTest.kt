package com.wutsi.ndopify.esignature.ui.layout

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientErrorHandlerTest {
    /** A page that lets ndopify-server's errors through, as the RestClient throws them. */
    @RestController
    class FailingController {
        @GetMapping("/failing/{status}", "/sign/{id}/failing/{status}", "/verify/{id}/{hash}/failing/{status}")
        fun failing(@PathVariable status: Int): String =
            throw HttpClientErrorException.create(HttpStatus.valueOf(status), "", HttpHeaders.EMPTY, ByteArray(0), null)
    }

    private val accessTokenService = mock<AccessTokenService>()

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FailingController())
        .setControllerAdvice(HttpClientErrorHandler(accessTokenService))
        .build()

    private fun status(status: Int): Int = mvc.perform(get("/failing/$status")).andReturn().response.status

    @Test
    fun forbidden() {
        assertEquals(403, status(403))
        verify(accessTokenService, never()).remove()
    }

    @Test
    fun `unauthorized on the sign page - signs out, back to the sign link`() {
        val response = mvc.perform(get("/sign/cp-1/failing/401")).andReturn().response

        assertEquals(302, response.status)
        assertEquals("/sign/cp-1", response.redirectedUrl)
        verify(accessTokenService).remove()
    }

    @Test
    fun `unauthorized on the verify page - signs out, same page`() {
        val response = mvc.perform(get("/verify/c-1/abc/failing/401")).andReturn().response

        assertEquals(302, response.status)
        assertEquals("/verify/c-1/abc/failing/401", response.redirectedUrl)
        verify(accessTokenService).remove()
    }

    @Test
    fun `unauthorized anywhere else - error page`() {
        val response = mvc.perform(get("/failing/401")).andReturn().response

        assertEquals("/error", response.redirectedUrl)
    }

    @Test
    fun `not found`() {
        assertEquals(404, status(404))
    }

    @Test
    fun `any other error - not handled here`() {
        val ex = assertFailsWith<Exception> { mvc.perform(get("/failing/409")) }
        assertEquals(true, ex.cause is RestClientException)
    }
}
