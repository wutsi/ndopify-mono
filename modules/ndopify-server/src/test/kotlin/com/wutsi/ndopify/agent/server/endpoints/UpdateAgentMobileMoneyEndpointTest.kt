package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.UpdateMobileMoneyRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.dao.MobileChangeRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentMobileMoneyEndpoint.sql"])
class UpdateAgentMobileMoneyEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Autowired
    private lateinit var changeRequestDao: MobileChangeRepository

    @Test
    fun `update mobile money`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237611111111",
        )

        val response = rest.postForEntity("/v1/agents/1/mobile-money", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(1L).get()
        assertEquals(request.mobileNumber, agent.mobileMoneyNumber)
        assertEquals(request.gateway, agent.mobileMoneyGateway)
        assertEquals(KycStatus.PENDING, agent.mobileMoneyKycStatus)
        assertEquals(AgentStatus.LIMITED, agent.status)

        val requests = changeRequestDao.findAll().filter { it.agent.id == 1L }
        assertEquals(1, requests.size)
        assertEquals(request.mobileNumber, requests[0].oldMobileNumber)
        assertEquals("+237611111111", requests[0].newMobileNumber)
        assertEquals(MoMoGatewayType.MTN, requests[0].newGateway)
        assertEquals(KycStatus.PENDING, requests[0].status)
    }

    @Test
    fun `status stays restricted when identity is verified but momo is not yet`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237622222222",
        )

        val response = rest.postForEntity("/v1/agents/3/mobile-money", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(3L).get()
        assertEquals(KycStatus.PENDING, agent.mobileMoneyKycStatus)
        assertEquals(AgentStatus.RESTRICTED, agent.status)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237611111111",
        )

        val response = rest.postForEntity("/v1/agents/999/mobile-money", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no mobile number`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "",
        )

        val response = rest.postForEntity("/v1/agents/1/mobile-money", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `number already verified for another agent`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237600000000",
        )

        val response = rest.postForEntity("/v1/agents/1/mobile-money", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AGENT_MOBILE_MONEY_NUMBER_ALREADY_ASSIGNED, response.body?.error?.code)

        val agent = dao.findById(1L).get()
        assertEquals(null, agent.mobileMoneyNumber)
    }

    @Test
    fun `re-assigning own number is allowed`() {
        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237600000000",
        )

        val response = rest.postForEntity("/v1/agents/2/mobile-money", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(2L).get()
        assertEquals("+237600000000", agent.mobileMoneyNumber)
        assertEquals(KycStatus.VERIFIED, agent.mobileMoneyKycStatus)
        assertEquals(AgentStatus.LIMITED, agent.status)

        val requests = changeRequestDao.findAll().filter { it.agent.id == 2L }
        assertEquals(1, requests.size)
        assertEquals("+237600000000", requests[0].oldMobileNumber)
        assertEquals("+237600000000", requests[0].newMobileNumber)
    }

    @Test
    fun `cannot update agent belonging to another tenant`() {
        overrideTenantId = 999L

        val request = UpdateMobileMoneyRequest(
            gateway = MoMoGatewayType.MTN,
            mobileNumber = "+237611111111",
        )
        val response = rest.postForEntity("/v1/agents/1/mobile-money", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
