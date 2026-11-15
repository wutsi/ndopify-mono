package com.wutsi.ndopify.npc.ui.visit

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.visit.dto.CoBrokerageVisitSummary
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import org.junit.jupiter.api.AfterEach
import org.springframework.web.client.ResourceAccessException
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisitAgentsTest {
    private val client = mock<NdAgentClient>()
    private val parallel = Parallel()

    @AfterEach
    fun closeParallel() = parallel.close()

    @Test
    fun `ofVisits reads the co-brokerage visits' listing agents only`() {
        doReturn(VisitFixtures.OTHER_AGENT).whenever(client).get(VisitFixtures.OTHER_AGENT_ID)

        val agents = VisitAgents.ofVisits(
            client,
            parallel,
            AGENT,
            listOf(
                VisitFixtures.summary(VisitFixtures.confirmed()),
                VisitFixtures.summary(VisitFixtures.confirmed().copy(id = 7002, listingId = 3013)),
                VisitFixtures.summary(VisitFixtures.confirmed().copy(id = 7003, listingId = 3014)),
            ),
            mapOf(
                3012L to VisitFixtures.listingSummary().copy(agentId = VisitFixtures.OTHER_AGENT_ID),
                3013L to VisitFixtures.listingSummary().copy(id = 3013),
                3014L to VisitFixtures.listingSummary().copy(id = 3014, agentId = VisitFixtures.OTHER_AGENT_ID),
            ),
        )

        assertEquals(mapOf(VisitFixtures.OTHER_AGENT_ID to VisitFixtures.OTHER_AGENT), agents)
        verify(client).get(VisitFixtures.OTHER_AGENT_ID)
        verify(client, never()).get(VisitFixtures.AGENT_ID)
    }

    @Test
    fun `ofVisits leaves out an agent that cannot be read`() {
        doThrow(ResourceAccessException("down")).whenever(client).get(VisitFixtures.OTHER_AGENT_ID)

        val agents = VisitAgents.ofVisits(
            client,
            parallel,
            AGENT,
            listOf(VisitFixtures.summary(VisitFixtures.confirmed())),
            mapOf(3012L to VisitFixtures.listingSummary().copy(agentId = VisitFixtures.OTHER_AGENT_ID)),
        )

        assertTrue(agents.isEmpty())
    }

    @Test
    fun `ofCoBrokerage reads each organizer once and leaves out an unreadable one`() {
        doReturn(VisitFixtures.OTHER_AGENT).whenever(client).get(VisitFixtures.OTHER_AGENT_ID)
        doThrow(ResourceAccessException("down")).whenever(client).get(31L)

        val agents = VisitAgents.ofCoBrokerage(
            client,
            parallel,
            listOf(hosted(8001, VisitFixtures.OTHER_AGENT_ID), hosted(8002, VisitFixtures.OTHER_AGENT_ID), hosted(8003, 31L)),
        )

        assertEquals(mapOf(VisitFixtures.OTHER_AGENT_ID to VisitFixtures.OTHER_AGENT), agents)
        verify(client).get(VisitFixtures.OTHER_AGENT_ID)
    }

    private fun hosted(id: Long, agentId: Long) = CoBrokerageVisitSummary(
        id = id,
        listingId = 3012,
        agentId = agentId,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = Date(VisitFixtures.NOW),
    )
}
