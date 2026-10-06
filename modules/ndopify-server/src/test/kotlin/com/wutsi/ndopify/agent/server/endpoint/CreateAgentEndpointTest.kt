package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.service.IdentificationService
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.party.server.service.PaymentMethodService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/CreateAgentEndpoint.sql"])
class CreateAgentEndpointTest : AbstractAgentEndpointTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var paymentMethodService: PaymentMethodService

    @Autowired
    private lateinit var identificationService: IdentificationService

    @Autowired
    private lateinit var kycService: KycService

    @Test
    fun create() {
        val request = CreateAgentRequest(
            firstName = "Ray",
            lastName = "Sponsible",
            email = "Ray.Sponsible@gmail.com",
            mobileMoneyNumber = "+237671234567",
            whatsappNumber = "+237650000000",
            agentType = AgentType.REAL_ESTATE_AGENT,
            experienceLevel = ExperienceLevel.SENIOR,

            biography = "Top agent in town",
            cityId = 2370201L,
            neighborhoodIds = listOf(111L, 222L),
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agentId = response.body!!.agentId
        assertNotNull(agentId)

        val agent = dao.findById(agentId).get()
        assertEquals(request.agentType, agent.agentType)
        assertEquals(request.experienceLevel, agent.experienceLevel)
        assertEquals(request.biography, agent.biography)
        assertEquals(request.cityId, agent.cityId)
        assertNeighborhoods(agent.id!!, request.neighborhoodIds)
        assertEquals(request.whatsappNumber, agent.whatsappNumber)

        val party = partyService.findByEmailOrNull(request.email)
        assertEquals(request.firstName, party?.firstName)
        assertEquals(request.lastName, party?.lastName)
        assertEquals(request.email.lowercase(), party?.email)
        assertEquals(KycStatus.PENDING, party?.kycStatus)

        val paymentMethod = paymentMethodService.findByParty(party!!).first()
        assertEquals(agent.party.id, paymentMethod.party.id)
        assertEquals(request.mobileMoneyNumber, paymentMethod.number)
        assertEquals(PaymentMethodStatus.PENDING_VERIFICATION, paymentMethod.status)
        assertNull(paymentMethod.expiresAt)

        val identifications = identificationService.search(SearchIdentificationRequest(partyId = party.id))
        assertEquals(1, identifications.size)
        val identification = identifications.first()
        assertEquals(IdentificationStatus.PENDING_VERIFICATION, identification.status)
        assertEquals(null, identification.number)
        assertEquals(null, identification.expiresAt)

        val cases = kycService.search(SearchKycCaseRequest(partyId = party.id))
        assertEquals(1, cases.size)
        val case = cases.first()
        assertEquals(party.id, case.party.id)
        assertEquals(identification.id, case.identification.id)
        assertEquals(paymentMethod.id, case.paymentMethod?.id)
        assertEquals(KycStatus.PENDING, case.status)
    }

    @Test
    fun `create for existing party`() {
        val request = CreateAgentRequest(
            firstName = "James",
            lastName = "B.",
            email = "007@gmail.com",
            mobileMoneyNumber = "+237650000001",
            whatsappNumber = "+237650000001",

            cityId = 2370201L,
        )

        val response = rest.postForEntity("/v1/agents", request, CreateAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agentId = response.body!!.agentId

        val agent = dao.findById(agentId).get()
        assertEquals(101L, agent.party.id)
    }

    @Test
    fun `duplicate agent`() {
        val request = CreateAgentRequest(
            firstName = "Omam",
            lastName = "Mbiyick",
            email = "omam.mbiyick@gmail.com",
            mobileMoneyNumber = "+237650000044",
            whatsappNumber = "+237660000044"
        )

        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AGENT_ALREADY_EXISTS, response.body?.error?.code)
    }

    @Test
    fun `no first name`() {
        val request = CreateAgentRequest(
            firstName = "",
            lastName = "Man",
            email = "yo.man@gmail.com",
            mobileMoneyNumber = "+237650000044",
            whatsappNumber = "+237660000044"
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no last name`() {
        val request = CreateAgentRequest(
            firstName = "Yo",
            lastName = "",
            email = "yo.man@gmail.com",
            mobileMoneyNumber = "+237650000044",
            whatsappNumber = "+237660000044"
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no email`() {
        val request = CreateAgentRequest(
            firstName = "Yo",
            lastName = "Man",
            email = "",
            mobileMoneyNumber = "+237650000044",
            whatsappNumber = "+237660000044"
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no mobile money number`() {
        val request = CreateAgentRequest(
            firstName = "Yo",
            lastName = "Man",
            email = "yo.man@gmail.com",
            mobileMoneyNumber = "",
            whatsappNumber = "+237660000044"
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `no mobile whatsapp number`() {
        val request = CreateAgentRequest(
            firstName = "Yo",
            lastName = "Man",
            email = "yo.man@gmail.com",
            mobileMoneyNumber = "+237660000044",
            whatsappNumber = ""
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `invalid method payment number`() {
        val request = CreateAgentRequest(
            firstName = "Yo",
            lastName = "Man",
            email = "yo.man@gmail.com",
            mobileMoneyNumber = "+15147589999",
            whatsappNumber = "+237660000044"
        )
        val response = rest.postForEntity("/v1/agents", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NUMBER_NOT_VALID, response.body?.error?.code)
    }
}
