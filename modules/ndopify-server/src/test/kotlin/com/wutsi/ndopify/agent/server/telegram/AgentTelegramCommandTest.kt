package com.wutsi.ndopify.agent.server.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentTelegramLinkResult
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentTelegramCommandTest {
    private val service = mock<AgentTelegramService>()
    private val command = AgentTelegramCommand(service)

    @Test
    fun `a valid connect command links the chat`() {
        doReturn(AgentTelegramLinkResult.CONNECTED).whenever(service).connect("123456", 777L)

        assertEquals(AgentTelegramCommand.CONNECTED, command.replyTo(message("/connect 123456")))
    }

    @Test
    fun `the group form with the bot name also works`() {
        doReturn(AgentTelegramLinkResult.CONNECTED).whenever(service).connect("123456", 777L)

        assertEquals(AgentTelegramCommand.CONNECTED, command.replyTo(message("/connect@ndopify_bot 123456")))
    }

    @Test
    fun `an invalid code says so`() {
        doReturn(AgentTelegramLinkResult.INVALID_CODE).whenever(service).connect("123456", 777L)

        assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect 123456")))
    }

    @Test
    fun `a chat linked elsewhere is refused`() {
        doReturn(AgentTelegramLinkResult.CHAT_ALREADY_LINKED).whenever(service).connect("123456", 777L)

        assertEquals(AgentTelegramCommand.CHAT_LINKED, command.replyTo(message("/connect 123456")))
    }

    @Test
    fun `a connect without a code is invalid without a lookup`() {
        assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect")))
        assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect 12345")))
        assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect 1234567")))
        assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect abcdef")))
        verifyNoInteractions(service)
    }

    @Test
    fun `another message is not its command`() {
        assertNull(command.replyTo(message("bonjour")))
        assertNull(command.replyTo(TelegramMessage(chat = TelegramChat(id = 777L), text = null)))
        verifyNoInteractions(service)
    }

    @Test
    fun `the command is case-insensitive and tolerates spaces`() {
        doReturn(AgentTelegramLinkResult.CONNECTED).whenever(service).connect("123456", 777L)

        assertEquals(AgentTelegramCommand.CONNECTED, command.replyTo(message("  /CONNECT  123456 ")))
    }

    private fun message(text: String?) = TelegramMessage(chat = TelegramChat(id = 777L), text = text)
}
