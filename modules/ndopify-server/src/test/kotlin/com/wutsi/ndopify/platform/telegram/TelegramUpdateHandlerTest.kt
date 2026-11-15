package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions

class TelegramUpdateHandlerTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client, emptyList())

    @Test
    fun `a message gets OK`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = TelegramMessage(chat = TelegramChat(id = 123L), text = "hello")))

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a message without text gets OK`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = TelegramMessage(chat = TelegramChat(id = 123L), text = null)))

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `an update without a message is ignored`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = null))

        verifyNoInteractions(client)
    }

    @Test
    fun `the first command that answers wins`() {
        val ignored = mock<TelegramCommand>()
        val command = mock<TelegramCommand>()
        val message = TelegramMessage(chat = TelegramChat(id = 123L), text = "/connect 123456")
        doReturn(null).whenever(ignored).replyTo(message)
        doReturn("reçu").whenever(command).replyTo(message)
        val handler = TelegramUpdateHandler(client, listOf(ignored, command))

        handler.handle(TelegramUpdate(updateId = 1L, message = message))

        verify(ignored).replyTo(message)
        verify(command).replyTo(message)
        verify(client).sendMessage(123L, "reçu")
    }
}
