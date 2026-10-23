package com.wutsi.ndopify.npc.service.security

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentAgentTest {
    private val currentParty = mock<CurrentParty>()
    private val agentClient = mock<NdAgentClient>()
    private val currentAgent = CurrentAgent(currentParty, agentClient)

    @Test
    fun agent() {
        doReturn(100L).whenever(currentParty).id()
        doReturn(Agent(id = 20L)).whenever(agentClient).findByPartyId(100L)

        assertEquals(20L, currentAgent.get()?.id)
    }

    @Test
    fun `party is not an agent`() {
        doReturn(100L).whenever(currentParty).id()
        doReturn(null).whenever(agentClient).findByPartyId(100L)

        assertNull(currentAgent.get())
    }

    @Test
    fun `nobody signed in`() {
        doReturn(null).whenever(currentParty).id()

        assertNull(currentAgent.get())
        verify(agentClient, never()).findByPartyId(any())
    }
}
