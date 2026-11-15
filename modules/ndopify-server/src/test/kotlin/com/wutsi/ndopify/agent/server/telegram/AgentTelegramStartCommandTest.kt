package com.wutsi.ndopify.agent.server.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentTelegramStartCommandTest {
    private val service = mock<AgentTelegramService>()
    private val command = AgentTelegramStartCommand(service)

    @Test
    fun `start explains how to connect an unlinked chat`() {
        doReturn(false).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start")))
    }

    @Test
    fun `start on a linked chat says it is already connected`() {
        doReturn(true).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.ALREADY_CONNECTED, command.replyTo(message("/start")))
    }

    @Test
    fun `the group form and a deep-link payload get the same reply`() {
        doReturn(false).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start@ndopify_bot")))
        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start 123456")))
    }

    @Test
    fun `another message is not its command`() {
        assertNull(command.replyTo(message("bonjour")))
        assertNull(command.replyTo(TelegramMessage(chat = TelegramChat(id = 777L), text = null)))
        verifyNoInteractions(service)
    }

    private fun message(text: String?) = TelegramMessage(chat = TelegramChat(id = 777L), text = text)
}
