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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val agentClient = mock<NdAgentClient>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        doReturn(AGENT).whenever(currentAgent).get()

        val controller = TelegramController(currentAgent, agentClient, "ndopify_bot")
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    @Test
    fun `the page shows the bot url`() {
        val result = mvc.perform(get("/telegram")).andReturn()

        assertEquals("telegram", result.modelAndView!!.viewName)
        assertEquals(false, result.modelAndView!!.model["telegramConnected"])
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
