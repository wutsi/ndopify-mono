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
        val response = rest.getForEntity("/v1/agents?ids=1&ids=3", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertTrue(agents.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `by party id`() {
        val response = rest.getForEntity("/v1/agents?partyIds=101", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
        assertEquals(101L, agents[0].party.id)
    }

    @Test
    fun `by city id`() {
        val response = rest.getForEntity("/v1/agents?cityId=2370201", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(2, agents.size)
        assertTrue(agents.map { it.id }.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `by neighborhood id`() {
        val response = rest.getForEntity("/v1/agents?neighborhoodIds=333", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
    }

    @Test
    fun `agent with multiple matching neighborhoods is not duplicated`() {
        val response =
            rest.getForEntity("/v1/agents?neighborhoodIds=111&neighborhoodIds=222", SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(1L, agents[0].id)
    }

    @Test
    fun `by mobile money number`() {
        val uri = URI.create("/v1/agents?mobileMoneyNumber=%2B237600000000")
        val response = rest.getForEntity(uri, SearchAgentResponse::class.java)

        val agents = response.body!!.agents
        assertEquals(1, agents.size)
        assertEquals(2L, agents[0].id)
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/agents?ids=999", SearchAgentResponse::class.java)

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
}
