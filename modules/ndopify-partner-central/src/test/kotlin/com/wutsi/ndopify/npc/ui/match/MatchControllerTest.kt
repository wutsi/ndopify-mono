package com.wutsi.ndopify.npc.ui.match

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.ui.match.MatchFixtures.MATCH_A
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
import kotlin.test.assertNull

class MatchControllerTest {
    private val client = mock<NdMatchClient>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        doReturn(MATCH_A).whenever(client).get(9001)
        mvc = MockMvcBuilders.standaloneSetup(MatchController(client)).build()
    }

    private fun outcome(result: MvcResult) = result.flashMap["match"]

    private fun listing(result: MvcResult) = result.flashMap["matchListing"]

    @Test
    fun propose() {
        val result = mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()

        verify(client).propose(9001)
        assertEquals("/mandates/204#matches-title", result.response.redirectedUrl)
        assertEquals("proposed", outcome(result))
        assertEquals(3012L, listing(result))
    }

    @Test
    fun dismiss() {
        val result = mvc.perform(post("/mandates/204/matches/9001/dismiss")).andReturn()

        verify(client).dismiss(9001)
        assertEquals("/mandates/204#matches-title", result.response.redirectedUrl)
        assertEquals("dismissed", outcome(result))
        assertEquals(3012L, listing(result))
    }

    @Test
    fun `propose a match of another mandate`() {
        doReturn(MATCH_A.copy(mandateId = 205)).whenever(client).get(9001)

        val result = mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()

        verify(client, never()).propose(any())
        assertEquals("/mandates/204#matches-title", result.response.redirectedUrl)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `dismiss a match of another mandate`() {
        doReturn(MATCH_A.copy(mandateId = 205)).whenever(client).get(9001)

        val result = mvc.perform(post("/mandates/204/matches/9001/dismiss")).andReturn()

        verify(client, never()).dismiss(any())
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `propose a match no longer active`() {
        doReturn(MATCH_A.copy(status = MatchStatus.PROPOSED)).whenever(client).get(9001)

        val result = mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()

        verify(client, never()).propose(any())
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `propose - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(client).propose(9001)

        assertEquals("changed", outcome(mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()))
    }

    @Test
    fun `propose - not the mandate's agent`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(client).get(9001)

        val result = mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()

        verify(client, never()).propose(any())
        assertEquals("changed", outcome(result))
        assertNull(listing(result))
    }

    @Test
    fun `dismiss - not found`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(client).get(9001)

        assertEquals("changed", outcome(mvc.perform(post("/mandates/204/matches/9001/dismiss")).andReturn()))
    }

    @Test
    fun `propose - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(client).propose(9001)

        assertEquals("error", outcome(mvc.perform(post("/mandates/204/matches/9001/propose")).andReturn()))
    }

    @Test
    fun `dismiss - server down`() {
        doThrow(ResourceAccessException("down")).whenever(client).get(9001)

        assertEquals("error", outcome(mvc.perform(post("/mandates/204/matches/9001/dismiss")).andReturn()))
    }

    @Test
    fun `hand-edited ids`() {
        assertEquals("/", mvc.perform(post("/mandates/204/matches/abc/propose")).andReturn().response.redirectedUrl)
        assertEquals("/", mvc.perform(post("/mandates/abc/matches/9001/dismiss")).andReturn().response.redirectedUrl)
        verify(client, never()).get(any())
    }
}
