package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"])
class GetAgentEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/agents/1", GetAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = response.body!!.agent
        assertEquals(1L, agent.id)
        assertEquals(1L, agent.userId)
        assertEquals("Ray", agent.firstName)
        assertEquals("Sponsible", agent.lastName)
        assertEquals(AgentType.REAL_ESTATE_AGENT, agent.agentType)
        assertEquals("Top agent in town", agent.biography)
        assertEquals("Ray Realty", agent.agencyName)
        assertEquals(2370201L, agent.cityId)
        assertEquals(listOf(111L, 222L), agent.neighborhoodIds)
        assertEquals("https://cdn.example.com/photo.jpg", agent.photoUrl)
        assertEquals("https://cdn.example.com/logo.jpg", agent.agencyLogoUrl)
        assertEquals("+237600000000", agent.mobileMoneyNumber)
        assertEquals(MoMoGatewayType.MTN, agent.mobileMoneyGateway)
    }

    @Test
    fun `not found`() {
        val response = rest.getForEntity("/v1/agents/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `agent belongs to another tenant`() {
        overrideTenantId = 999L

        val response = rest.getForEntity("/v1/agents/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
