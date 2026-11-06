package com.wutsi.ndopify.npc.service.security

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentAgentTest {
    private val agentClient = mock<NdAgentClient>()
    private val currentAgent = CurrentAgent(agentClient)

    @Test
    fun agent() {
        doReturn(Agent(id = 20L)).whenever(agentClient).me()

        assertEquals(20L, currentAgent.get()?.id)
    }

    @Test
    fun `not an agent`() {
        doReturn(null).whenever(agentClient).me()

        assertNull(currentAgent.get())
    }
}
