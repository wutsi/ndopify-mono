package com.wutsi.ndopify.platform.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.platform.telegram.model.TelegramUser
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals

@TestPropertySource(
    properties = [
        "management.health.telegram.enabled=true",
        "ndopify.telegram.health.up-ttl-minutes=0",
        "ndopify.telegram.health.down-ttl-minutes=0",
    ],
)
class TelegramHealthEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `telegram is in the corehealth group`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()

        val response = rest.getForEntity("/actuator/health", Map::class.java)
        val component = component(response.body!!)

        assertEquals("UP", component["status"])
        assertEquals("ndopify_bot", (component["details"] as Map<*, *>)["username"])
    }

    @Test
    fun `an unreachable Telegram makes the component DOWN`() {
        doThrow(RuntimeException("token 123:secret leaked")).whenever(client).getMe()

        val response = rest.getForEntity("/actuator/health", Map::class.java)
        val component = component(response.body!!)

        assertEquals("DOWN", component["status"])
        assertEquals("RuntimeException", (component["details"] as Map<*, *>)["error"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun component(body: Map<*, *>): Map<String, Any> =
        (body["components"] as Map<String, Any>)["telegram"] as Map<String, Any>
}
