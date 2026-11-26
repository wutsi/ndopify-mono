package com.wutsi.ndopify.agent.server.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentEndpoint.sql"])
class UpdateAgentEndpointTest : AbstractAgentEndpointTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Autowired
    private lateinit var partyService: PartyService

    @MockitoBean
    private lateinit var clock: Clock

    private val now = System.currentTimeMillis()

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @BeforeEach
    override fun setUp() {
        super.setUp()

        doReturn(now).whenever(clock).millis()
    }

    @Test
    fun update() {
        val request = UpdateAgentRequest(
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
        assertEquals(AgentEntity.DEFAULT_RATING, agent.rating) // Read-only: an agent can't rate itself
        assertEquals(Date.from(Instant.parse("2024-06-10T00:00:00Z")), agent.createdAt)
        assertTrue(agent.modifiedAt.time >= agent.createdAt.time)

        // Names are verified by KYC: the party is never updated
        val party = partyService.findById(agent.party.id!!)
        assertEquals("Roger", party.firstName)
        assertEquals("Milla", party.lastName)
        assertEquals("roger.milla@gmail.com", party.email)
        assertTrue(party.modifiedAt.time < now)
    }

    @Test
    fun `current version - saved, and the next update needs the new one`() {
        userId = 12L
        val request = UpdateAgentRequest(biography = "Updated bio", version = 0)

        val response = rest.postForEntity("/v1/agents/101", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(request.biography, dao.findById(101L).get().biography)
        val agent = rest.getForEntity("/v1/agents/101", GetAgentResponse::class.java).body!!.agent
        assertEquals(1L, agent.version)
    }

    @Test
    fun `stale version - 409, nothing saved`() {
        userId = 12L
        val request = UpdateAgentRequest(biography = "Updated bio", version = 7)

        val response = rest.postForEntity("/v1/agents/101", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AGENT_VERSION_CONFLICT, response.body?.error?.code)

        val agent = dao.findById(101L).get()
        assertEquals(0L, agent.version)
        assertNull(agent.biography)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateAgentRequest(
            whatsappNumber = "+237600000777",
        )

        val response = rest.postForEntity("/v1/agents/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `partial update of a single field`() {
        userId = 12L
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
        userId = 13L
        val request = UpdateAgentRequest(
            whatsappNumber = "+237600000555",
            neighborhoodIds = null,
        )

        val response = rest.postForEntity("/v1/agents/102", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(102L).get()
        assertNeighborhoods(agent.id!!, listOf(555L, 666L))
    }

    @Test
    fun `another agent's profile - forbidden, nothing saved`() {
        val response = rest.postForEntity(
            "/v1/agents/101",
            UpdateAgentRequest(whatsappNumber = "+237600000888", biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)

        val agent = dao.findById(101L).get()
        assertNull(agent.whatsappNumber)
        assertNull(agent.biography)
        assertEquals(AgentType.UNKNOWN, agent.agentType)
        assertEquals(Date.from(Instant.parse("2024-06-10T00:00:00Z")), agent.modifiedAt)
    }

    @Test
    fun `caller without a party - forbidden, nothing saved`() {
        userId = 14L
        val response = rest.postForEntity(
            "/v1/agents/100",
            UpdateAgentRequest(biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertNull(dao.findById(100L).get().biography)
    }

    @Test
    fun `admin updates another agent`() {
        admin = true
        val response = rest.postForEntity(
            "/v1/agents/101",
            UpdateAgentRequest(biography = "Admin edit"),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Admin edit", dao.findById(101L).get().biography)
    }

    @Test
    fun `anonymous caller - unauthorized`() {
        anonymousUser = true
        val response = rest.postForEntity(
            "/v1/agents/100",
            UpdateAgentRequest(biography = "Hijacked"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(dao.findById(100L).get().biography)
    }
}
