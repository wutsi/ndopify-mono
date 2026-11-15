package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
class TelegramWebhookRegistrationTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `the webhook is registered at startup`() {
        verify(client).setWebhook("https://test.example/telegram/webhook", "test-secret")
    }
}
