package com.wutsi.ndopify.npc.ui.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val agentClient = mock<NdAgentClient>()
    private val now = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        doReturn(AGENT).whenever(currentAgent).get()

        val controller = TelegramController(currentAgent, agentClient, "ndopify_bot", clock, 15)
        mvc = mockMvc(controller)
    }

    private fun mockMvc(controller: TelegramController): MockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()

    @Test
    fun `the page shows the bot url`() {
        val result = mvc.perform(get("/telegram")).andReturn()

        assertEquals("telegram", result.modelAndView!!.viewName)
        assertEquals(false, result.modelAndView!!.model["telegramConnected"])
        assertEquals("https://t.me/ndopify_bot", result.modelAndView!!.model["telegramBotUrl"])
    }

    @Test
    fun `a successful request remembers the code for its ttl`() {
        val result = mvc.perform(post("/telegram/code")).andReturn()

        assertEquals(now + 15 * 60_000, result.request.getSession().getAttribute(TelegramRules.SENT_UNTIL))
    }

    @Test
    fun `a pending code keeps step 2 open after a refresh`() {
        val result = mvc.perform(
            get("/telegram").sessionAttr(TelegramRules.SENT_UNTIL, now + 60_000),
        ).andReturn()

        assertEquals(true, result.modelAndView!!.model["telegramCodePending"])
        assertNull(result.flashMap[TelegramRules.SENT])
    }

    @Test
    fun `an expired code closes step 2 again`() {
        val result = mvc.perform(
            get("/telegram").sessionAttr(TelegramRules.SENT_UNTIL, now - 1),
        ).andReturn()

        assertEquals(false, result.modelAndView!!.model["telegramCodePending"])
        assertNull(result.request.getSession().getAttribute(TelegramRules.SENT_UNTIL))
    }

    @Test
    fun `a blank bot username falls back to the default bot`() {
        val blank = TelegramController(currentAgent, agentClient, "", clock, 15)
        val mvc = mockMvc(blank)

        val result = mvc.perform(get("/telegram")).andReturn()

        assertEquals("https://t.me/ndopify_bot", result.modelAndView!!.model["telegramBotUrl"])
    }

    @Test
    fun `a connected agent sees the connected state`() {
        whenever(currentAgent.get()).thenReturn(AGENT.copy(telegramConnected = true))

        val result = mvc.perform(get("/telegram")).andReturn()

        assertEquals(true, result.modelAndView!!.model["telegramConnected"])
    }

    @Test
    fun `the button sends the code and comes back to the page`() {
        val result = mvc.perform(post("/telegram/code")).andReturn()

        assertEquals("/telegram", result.response.redirectedUrl)
        assertEquals(true, result.flashMap[TelegramRules.SENT])
        verify(agentClient).requestTelegramCode()
    }

    @Test
    fun `a connected agent gets the already-connected message`() {
        doThrow(conflict(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED)).whenever(agentClient).requestTelegramCode()

        val result = mvc.perform(post("/telegram/code")).andReturn()

        assertEquals("/telegram", result.response.redirectedUrl)
        assertEquals(TelegramRules.ALREADY_CONNECTED, result.flashMap[TelegramRules.ERROR])
        assertNull(result.flashMap[TelegramRules.SENT])
    }

    @Test
    fun `a server failure is reported`() {
        doThrow(ResourceAccessException("down")).whenever(agentClient).requestTelegramCode()

        val result = mvc.perform(post("/telegram/code")).andReturn()

        assertEquals(TelegramRules.SEND_FAILED, result.flashMap[TelegramRules.ERROR])
        assertNull(result.flashMap[TelegramRules.SENT])
    }

    @Test
    fun `another conflict is a server failure, not an already-connected account`() {
        doThrow(conflict(ErrorCode.AGENT_VERSION_CONFLICT)).whenever(agentClient).requestTelegramCode()

        val result = mvc.perform(post("/telegram/code")).andReturn()

        assertEquals(TelegramRules.SEND_FAILED, result.flashMap[TelegramRules.ERROR])
        assertNull(result.flashMap[TelegramRules.SENT])
    }

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }
}
