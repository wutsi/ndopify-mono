package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ExperienceLevel
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

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

        val party = agent.party
        assertEquals(100L, party.id)
        assertEquals("Ray", party.firstName)
        assertEquals("Sponsible", party.lastName)
        assertEquals("ray.sponsible@gmail.com", party.email)
    }

    @Test
    fun `agent not found`() {
        val response = rest.getForEntity("/v1/agents/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
