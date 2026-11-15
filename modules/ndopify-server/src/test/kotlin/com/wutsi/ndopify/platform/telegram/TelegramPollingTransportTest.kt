package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.telegram.model.TelegramChat
import com.wutsi.ndopify.platform.telegram.model.TelegramMessage
import com.wutsi.ndopify.platform.telegram.model.TelegramUpdate
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramPollingTransportTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)

    @Test
    fun `pollOnce handles every update and advances the offset`() {
        doReturn(
            listOf(
                TelegramUpdate(updateId = 10L, message = TelegramMessage(chat = TelegramChat(id = 100L), text = "a")),
                TelegramUpdate(updateId = 11L, message = TelegramMessage(chat = TelegramChat(id = 101L), text = "b")),
            ),
        ).whenever(client).getUpdates(null, 20)
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(12L, 20)

        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)
        poller.pollOnce()
        poller.pollOnce()

        verify(client).sendMessage(100L, "OK")
        verify(client).sendMessage(101L, "OK")
        verify(client).getUpdates(12L, 20)
    }

    @Test
    fun `an enabled transport clears the webhook and starts`() {
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(null, 20)
        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)

        poller.start()

        verify(client, timeout(1000)).deleteWebhook()
        assertTrue(poller.isRunning())
        poller.stop()
        assertFalse(poller.isRunning())
    }

    @Test
    fun `a failed deleteWebhook does not prevent polling`() {
        doThrow(RuntimeException("Telegram is down")).whenever(client).deleteWebhook()
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(null, 20)
        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)

        poller.start()

        assertTrue(poller.isRunning())
        poller.stop()
    }

    @Test
    fun `disabled does not start`() {
        val poller = TelegramPollingTransport(client, handler, enabled = false, timeoutSeconds = 20)

        poller.start()

        assertFalse(poller.isRunning())
        verify(client, never()).deleteWebhook()
    }
}
