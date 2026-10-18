package com.wutsi.ndopify.npc.ui.layout

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
        @GetMapping("/failing/{status}")
        fun failing(@PathVariable status: Int): String =
            throw HttpClientErrorException.create(HttpStatus.valueOf(status), "", HttpHeaders.EMPTY, ByteArray(0), null)
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(FailingController())
        .setControllerAdvice(HttpClientErrorHandler())
        .build()

    private fun status(status: Int): Int = mvc.perform(get("/failing/$status")).andReturn().response.status

    @Test
    fun forbidden() {
        assertEquals(403, status(403))
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
