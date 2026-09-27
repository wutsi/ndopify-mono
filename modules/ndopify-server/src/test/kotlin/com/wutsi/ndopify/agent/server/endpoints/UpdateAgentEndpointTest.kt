package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentEndpoint.sql"])
class UpdateAgentEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Test
    fun update() {
        val request = UpdateAgentRequest(
            agentType = AgentType.PARTNER,
            biography = "New bio",
            agencyName = "New Realty",
            cityId = 999L,
            neighborhoodIds = listOf(111L, 222L),
        )

        val response = rest.postForEntity("/v1/agents/1", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(1L).get()
        assertEquals(AgentType.PARTNER, agent.agentType)
        assertEquals("New bio", agent.biography)
        assertEquals("New Realty", agent.agencyName)
        assertEquals(999L, agent.cityId)
        assertEquals(listOf(111L, 222L), agent.neighborhoodIds)

        // Fields not part of UpdateAgentRequest must remain untouched (updated via UpdateIdentyRequest instead)
        assertEquals(1L, agent.userId)
        assertEquals("Ray", agent.firstName)
        assertEquals("Sponsible", agent.lastName)
    }

    @Test
    fun `no city`() {
        val request = UpdateAgentRequest(cityId = null)

        val response = rest.postForEntity("/v1/agents/1", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(1L).get()
        assertNull(agent.cityId)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateAgentRequest()

        val response = rest.postForEntity("/v1/agents/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `cannot update agent belonging to another tenant`() {
        overrideTenantId = 999L

        val request = UpdateAgentRequest(agencyName = "New Realty")
        val response = rest.postForEntity("/v1/agents/1", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)

        val agent = dao.findById(1L).get()
        assertEquals("Old Realty", agent.agencyName)
    }
}
