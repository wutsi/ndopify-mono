package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals

@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
class TelegramWebhookEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `Telegram posts an update, the bot replies OK`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a request without the secret is rejected`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(client, never()).sendMessage(anyLong(), anyString())
    }
}
