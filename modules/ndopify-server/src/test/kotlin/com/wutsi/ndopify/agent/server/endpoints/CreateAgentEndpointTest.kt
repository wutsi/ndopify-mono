package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/CreateAgentEndpoint.sql"])
class CreateAgentEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Test
    fun create() {
        val request = CreateAgentRequest(
            userId = 1L,
            firstName = "Ray",
            lastName = "Sponsible",
            agentType = AgentType.REAL_ESTATE_AGENT,
            biography = "Top agent in town",
            agencyName = "Ray Realty",
            cityId = 2370201L,
            neighborhoodIds = listOf(111L, 222L),
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agentId = response.body!!.agentId
        assertNotNull(agentId)

        val agent = dao.findById(agentId).get()
        assertEquals(TENANT_ID, agent.tenantId)
        assertEquals(1L, agent.userId)
        assertEquals("Ray", agent.firstName)
        assertEquals("Sponsible", agent.lastName)
        assertEquals(AgentType.REAL_ESTATE_AGENT, agent.agentType)
        assertEquals("Top agent in town", agent.biography)
        assertEquals("Ray Realty", agent.agencyName)
        assertEquals(2370201L, agent.cityId)
        assertEquals(listOf(111L, 222L), agent.neighborhoodIds)
        assertEquals(KycStatus.UNKNOWN, agent.identityKycStatus)
        assertEquals(KycStatus.UNKNOWN, agent.mobileMoneyKycStatus)
        assertEquals(AgentStatus.LIMITED, agent.status)
    }

    @Test
    fun `no city`() {
        val request = CreateAgentRequest(
            userId = 1L,
            firstName = "Ray",
            lastName = "Sponsible",
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(response.body!!.agentId).get()
        assertNull(agent.cityId)
    }

    @Test
    fun `no user id`() {
        val request = CreateAgentRequest(
            firstName = "Ray",
            lastName = "Sponsible",
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(response.body!!.agentId).get()
        assertNull(agent.userId)
    }

    @Test
    fun `no first name`() {
        val request = CreateAgentRequest(
            userId = 1L,
            firstName = "",
            lastName = "Sponsible",
        )

        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `unknown user id is stored as-is`() {
        val request = CreateAgentRequest(
            userId = 999L,
            firstName = "Ray",
            lastName = "Sponsible",
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(response.body!!.agentId).get()
        assertEquals(999L, agent.userId)
    }

    @Test
    fun `unknown city id is stored as-is`() {
        val request = CreateAgentRequest(
            userId = 1L,
            firstName = "Ray",
            lastName = "Sponsible",
            cityId = 999L,
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(response.body!!.agentId).get()
        assertEquals(999L, agent.cityId)
    }
}
