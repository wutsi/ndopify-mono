package com.wutsi.ndopify.agent.server.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AgentTelegramCodeCreatedEventTest {
    @Test
    fun `toString masks the code`() {
        val event = AgentTelegramCodeCreatedEvent(tenantId = 2L, agentId = 10L, code = "123456", ttlMinutes = 15)

        assertFalse(event.toString().contains("123456"), "the clear code must never be logged")
        assertEquals(
            "AgentTelegramCodeCreatedEvent(tenantId=2, agentId=10, code=******, ttlMinutes=15)",
            event.toString(),
        )
    }
}
