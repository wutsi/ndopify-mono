package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertFalse

class TelegramHealthDisabledTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `telegram is out of the qa health group`() {
        val response = rest.getForEntity("/actuator/health", Map::class.java)

        assertFalse(components(response.body!!).containsKey("telegram"))
        verify(client, never()).getMe()
    }

    @Suppress("UNCHECKED_CAST")
    private fun components(body: Map<*, *>): Map<String, Any> = body["components"] as Map<String, Any>
}
