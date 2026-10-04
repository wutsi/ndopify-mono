package com.wutsi.ndopify.agent.server.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.server.service.PartyService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentEndpoint.sql"])
class UpdateAgentEndpointTest : AbstractAgentEndpointTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Autowired
    private lateinit var partyService: PartyService

    @MockitoBean
    private lateinit var clock: Clock

    private val now = System.currentTimeMillis()

    @BeforeEach
    override fun setUp() {
        super.setUp()

        doReturn(now).whenever(clock).millis()
    }

    @Test
    fun update() {
        Thread.sleep(100)
        val request = UpdateAgentRequest(
            firstName = "Ray",
            lastName = "Sponsible",
            email = "Ray.Sponsible@gmail.com",
            whatsappNumber = "+237600000099",
            agentType = AgentType.REAL_ESTATE_AGENT,
            experienceLevel = ExperienceLevel.SENIOR,
            cityId = 2370201L,
            neighborhoodIds = listOf(333L, 444L),
            biography = "Updated bio",
        )

        val response = rest.postForEntity("/v1/agents/100", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(100L).get()
        assertEquals(request.agentType, agent.agentType)
        assertEquals(request.experienceLevel, agent.experienceLevel)
        assertEquals(request.biography, agent.biography)
        assertEquals(request.cityId, agent.cityId)
        assertNeighborhoods(agent.id!!, request.neighborhoodIds!!)
        assertEquals(request.whatsappNumber, agent.whatsappNumber)
        assertTrue(agent.createdAt.time < now)
        assertTrue(agent.modifiedAt.time >= agent.createdAt.time)

        val party = partyService.findById(agent.party.id!!)
        assertEquals(request.firstName, party.firstName)
        assertEquals(request.lastName, party.lastName)
        assertEquals(request.email?.lowercase(), party.email)
        assertEquals(agent.modifiedAt, party.modifiedAt)
    }

    @Test
    fun `do not update the party`() {
        val request = UpdateAgentRequest(
            firstName = null,
            lastName = null,
            email = null,
            whatsappNumber = "+237600000099",
            agentType = AgentType.REAL_ESTATE_AGENT,
            experienceLevel = ExperienceLevel.SENIOR,
            cityId = 2370201L,
            neighborhoodIds = listOf(333L, 444L),
            biography = "Updated bio",
        )

        val response = rest.postForEntity("/v1/agents/101", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(101L).get()
        val party = partyService.findById(agent.party.id!!)
        assertEquals("J", party.firstName)
        assertEquals("Bond", party.lastName)
        assertEquals("007@gmail.com", party.email)
        assertTrue(party.modifiedAt.time < now)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateAgentRequest(
            firstName = "Ray",
            lastName = "Sponsible",
            email = "ray.sponsible@gmail.com",
            whatsappNumber = "+237600000777",
        )

        val response = rest.postForEntity("/v1/agents/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `email already used by another party`() {
        val request = UpdateAgentRequest(
            email = "007@gmail.com",
        )

        val response = rest.postForEntity("/v1/agents/100", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.PARTY_EMAIL_ALREADY_EXISTS, response.body?.error?.code)

        val party = partyService.findById(100L)
        assertEquals("roger.milla@gmail.com", party.email)
    }

    @Test
    fun `update email with different case of the same email`() {
        val request = UpdateAgentRequest(
            email = "ROGER.MILLA@gmail.com",
        )

        val response = rest.postForEntity("/v1/agents/100", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val party = partyService.findById(100L)
        assertEquals("roger.milla@gmail.com", party.email)
    }

    @Test
    fun `partial update of a single field`() {
        val request = UpdateAgentRequest(
            whatsappNumber = "+237600000555",
        )

        val response = rest.postForEntity("/v1/agents/101", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(101L).get()
        assertEquals(request.whatsappNumber, agent.whatsappNumber)
        assertEquals(AgentType.UNKNOWN, agent.agentType)
        assertEquals(ExperienceLevel.UNKNOWN, agent.experienceLevel)
        assertEquals(null, agent.cityId)
        assertEquals(null, agent.biography)
        assertNeighborhoods(agent.id!!, emptyList())
    }

    @Test
    fun `no neighborhoodIds keeps existing neighborhoods`() {
        val request = UpdateAgentRequest(
            whatsappNumber = "+237600000555",
            neighborhoodIds = null,
        )

        val response = rest.postForEntity("/v1/agents/102", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(102L).get()
        assertNeighborhoods(agent.id!!, listOf(555L, 666L))
    }
}
