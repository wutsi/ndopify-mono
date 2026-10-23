package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import org.springframework.test.context.jdbc.Sql
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/SearchAgentEndpoint.sql"])
class SearchAgentEndpointTest : AbstractAgentEndpointTest() {
    @Test
    fun all() {
        val response = rest.getForEntity("/v1/agents", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(3, agents.size)
        assertEquals(listOf(1L, 2L, 3L), agents.map { it.id })
    }

    @Test
    fun `by ids`() {
        val response = rest.getForEntity("/v1/agents?id=1&id=3", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertTrue(agents.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `by party id`() {
        val response = rest.getForEntity("/v1/agents?party-id=101", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
        assertEquals(101L, agents[0].party.id)
    }

    @Test
    fun `by city id`() {
        val response = rest.getForEntity("/v1/agents?city-id=2370201", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertTrue(agents.map { it.id }.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `by neighborhood id`() {
        val response = rest.getForEntity("/v1/agents?neighborhood-id=333", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
    }

    @Test
    fun `agent with multiple matching neighborhoods is not duplicated`() {
        val response =
            rest.getForEntity("/v1/agents?neighborhood-id=111&neighborhood-id=222", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(1L, agents[0].id)
    }

    @Test
    fun `by mobile money number`() {
        val uri = URI.create("/v1/agents?mobile-money-number=%2B237600000000")
        val response = rest.getForEntity(uri, SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
    }

    @Test
    fun `by kyc status`() {
        val response = rest.getForEntity("/v1/agents?kyc-status=VERIFIED", SearchAgentResponse::class.java)

        assertEquals(listOf(1L), response.body!!.agents.map { it.id })
    }

    @Test
    fun `by multiple kyc statuses`() {
        val response =
            rest.getForEntity("/v1/agents?kyc-status=VERIFIED&kyc-status=REJECTED", SearchAgentResponse::class.java)

        assertEquals(listOf(1L, 2L), response.body!!.agents.map { it.id })
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/agents?id=999", SearchAgentResponse::class.java)

        assertEquals(0, response.body!!.agents.size)
    }

    @Test
    fun `limit`() {
        val response = rest.getForEntity("/v1/agents?limit=2", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertEquals(listOf(1L, 2L), agents.map { it.id })
    }

    @Test
    fun offset() {
        val response = rest.getForEntity("/v1/agents?offset=1", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertEquals(listOf(2L, 3L), agents.map { it.id })
    }

    @Test
    fun `limit and offset`() {
        val response = rest.getForEntity("/v1/agents?limit=1&offset=1", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
    }

    @Test
    fun `agent from another tenant is not returned`() {
        val response = rest.getForEntity("/v1/agents", SearchAgentResponse::class.java)

        val agentIds = response.body!!.agents.map { it.id }
        assertTrue(!agentIds.contains(4L))
    }

    @Test
    fun `switching tenant context reveals only that tenant's agent`() {
        overrideTenantId = 2L

        val response = rest.getForEntity("/v1/agents", SearchAgentResponse::class.java)

        val agentIds = response.body!!.agents.map { it.id }
        assertEquals(listOf(4L), agentIds)
    }

    @Test
    fun `query count does not grow with the number of agents`() {
        val one = countQueries { rest.getForEntity("/v1/agents?id=1", SearchAgentResponse::class.java) }
        val all = countQueries { rest.getForEntity("/v1/agents", SearchAgentResponse::class.java) }

        assertEquals(one, all)
    }
}
