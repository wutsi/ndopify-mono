package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramWebhookTransportTest {
    private val client = mock<TelegramClient>()

    @Test
    fun `webhook mode registers the URL and the secret`() {
        val transport = TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "secret")

        transport.start()

        verify(client).setWebhook("https://host/telegram/webhook", "secret")
        assertTrue(transport.isRunning())
        transport.stop()
        assertFalse(transport.isRunning())
    }

    @Test
    fun `not running before start`() {
        // Spring skips start() on a Lifecycle that already reports itself running
        assertFalse(TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "secret").isRunning())
    }

    @Test
    fun `a blank URL fails fast`() {
        assertFailsWith<IllegalStateException> {
            TelegramWebhookTransport(client, "webhook", "", "secret").start()
        }
    }

    @Test
    fun `a blank secret fails fast`() {
        assertFailsWith<IllegalStateException> {
            TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "").start()
        }
    }

    @Test
    fun `polling mode does nothing`() {
        val transport = TelegramWebhookTransport(client, "polling", "https://host/telegram/webhook", "secret")

        transport.start()

        verifyNoInteractions(client)
        assertFalse(transport.isRunning())
    }

    @Test
    fun `a failed registration does not crash startup`() {
        doThrow(RuntimeException("Telegram is down")).whenever(client).setWebhook(any(), any())

        TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "secret").start()

        verify(client).setWebhook("https://host/telegram/webhook", "secret")
    }
}
