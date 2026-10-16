package com.wutsi.ndopify.npc.ui.assignment

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals

class AssignmentControllerTest {
    private val client = mock<NdAssignmentClient>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(AssignmentController(client)).build()
    }

    private fun outcome(result: MvcResult) = result.flashMap["assignment"]

    @Test
    fun accept() {
        val result = mvc.perform(post("/assignments/7/accept")).andReturn()

        verify(client).accept(7)
        assertEquals("/", result.response.redirectedUrl)
        assertEquals("accepted", outcome(result))
    }

    @Test
    fun decline() {
        val result = mvc.perform(post("/assignments/7/decline")).andReturn()

        verify(client).decline(7)
        assertEquals("/", result.response.redirectedUrl)
        assertEquals("declined", outcome(result))
    }

    @Test
    fun `accept - no longer pending`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(client).accept(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/accept")).andReturn()))
    }

    @Test
    fun `decline - not the assignee`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(client).decline(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/decline")).andReturn()))
    }

    @Test
    fun `accept - unknown offer`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(client).accept(7)

        assertEquals("closed", outcome(mvc.perform(post("/assignments/7/accept")).andReturn()))
    }

    @Test
    fun `accept - bad request`() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(client).accept(7)

        assertEquals("error", outcome(mvc.perform(post("/assignments/7/accept")).andReturn()))
    }

    @Test
    fun `accept - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(client).accept(7)

        val result = mvc.perform(post("/assignments/7/accept")).andReturn()

        assertEquals("/", result.response.redirectedUrl)
        assertEquals("error", outcome(result))
    }

    @Test
    fun `decline - unreachable`() {
        doThrow(ResourceAccessException("timeout")).whenever(client).decline(7)

        assertEquals("error", outcome(mvc.perform(post("/assignments/7/decline")).andReturn()))
    }
}
