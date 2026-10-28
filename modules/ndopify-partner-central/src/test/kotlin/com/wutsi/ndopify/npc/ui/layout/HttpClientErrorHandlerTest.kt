package com.wutsi.ndopify.npc.ui.layout

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.npc.service.security.AccessTokenService
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
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
import kotlin.test.assertTrue

class HttpClientErrorHandlerTest {
    /** A page that lets ndopify-server's errors through, as the RestClient throws them. */
    @RestController
    class FailingController {
        @GetMapping("/failing/{status}")
        fun failing(@PathVariable status: Int): String =
            throw HttpClientErrorException.create(HttpStatus.valueOf(status), "", HttpHeaders.EMPTY, ByteArray(0), null)

        @GetMapping("/not-an-agent")
        fun notAnAgent(): String = throw SignInRequiredException()
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
    fun `unauthorized - signs out`() {
        val session = MockHttpSession()

        val response = mvc.perform(get("/failing/401").session(session)).andReturn().response

        assertEquals(302, response.status)
        assertEquals("/login", response.redirectedUrl)
        verify(accessTokenService).remove()
        assertTrue(session.isInvalid)
    }

    @Test
    fun `sign-in required - signs out`() {
        val session = MockHttpSession()

        val response = mvc.perform(get("/not-an-agent").session(session)).andReturn().response

        assertEquals(302, response.status)
        assertEquals("/login", response.redirectedUrl)
        verify(accessTokenService).remove()
        assertTrue(session.isInvalid)
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
