package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"])
class GetAgentEndpointTest : AbstractAgentEndpointTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/agents/1", GetAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = response.body!!.agent
        assertEquals(1L, agent.id)
        assertEquals(AgentType.REAL_ESTATE_AGENT, agent.agentType)
        assertEquals(ExperienceLevel.NOVICE, agent.experienceLevel)
        assertEquals("Top agent in town", agent.biography)
        assertEquals(2370201L, agent.cityId)
        assertEquals("+23761111111", agent.whatsappNumber)
        assertEquals(listOf(111L, 222L), agent.neighborhoodIds.sorted())
        assertEquals("2024-06-11", SimpleDateFormat("yyyy-MM-dd").format(agent.joinedAt!!))
        assertEquals(4.5, agent.rating)
        assertFalse(agent.telegramConnected)

        val party = agent.party
        assertEquals(100L, party.id)
        assertEquals("Ray", party.firstName)
        assertEquals("Sponsible", party.lastName)
        assertEquals("ray.sponsible@gmail.com", party.email)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
        statements = [
            "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES (101, 1, 'u@x.com', 'Un', 'Rated')",
            "INSERT INTO T_AGENT (id, tenant_id, party_id, city_id) VALUES (3, 1, 101, 2370201)",
        ],
    )
    fun `unrated agent`() {
        val response = rest.getForEntity("/v1/agents/3", GetAgentResponse::class.java)

        assertEquals(3.0, response.body!!.agent.rating)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
        statements = [
            "INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES (1, 1, 777, NOW())",
        ],
    )
    fun `telegram connected`() {
        val response = rest.getForEntity("/v1/agents/1", GetAgentResponse::class.java)

        assertTrue(response.body!!.agent.telegramConnected)
    }

    @Test
    fun `agent not found`() {
        val response = rest.getForEntity("/v1/agents/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `agent from another tenant is not found`() {
        val response = rest.getForEntity("/v1/agents/2", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
