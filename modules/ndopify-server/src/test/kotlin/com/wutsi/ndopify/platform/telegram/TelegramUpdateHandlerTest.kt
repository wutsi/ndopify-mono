package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions

class TelegramUpdateHandlerTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)

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
}
