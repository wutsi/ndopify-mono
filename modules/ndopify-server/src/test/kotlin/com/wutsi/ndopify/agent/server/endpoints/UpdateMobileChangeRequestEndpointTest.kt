package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.UpdateMobileChangeRequest
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.agent.server.service.MobileChangeService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateMobileChangeRequestEndpoint.sql"])
class UpdateMobileChangeRequestEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var mobileChangeService: MobileChangeService

    @Autowired
    private lateinit var agentService: AgentService

    @Test
    fun verified() {
        val request = UpdateMobileChangeRequest(
            holderName = "Ray Sponsible",
            status = KycStatus.VERIFIED,
            errorCode = null,
            failureReason = null,
        )

        val response = rest.postForEntity("/v1/mobile-change-requests/1", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = mobileChangeService.findById(1L, TENANT_ID)
        assertEquals(KycStatus.VERIFIED, change.status)
        assertEquals("Ray Sponsible", change.holderName)
        assertEquals(null, change.errorCode)
        assertEquals(null, change.failureReason)
        assertEquals(USER_ID, change.verifyByUserId)

        val agent = agentService.findById(1L, TENANT_ID)
        assertEquals("+237600000000", agent.mobileMoneyNumber)
        assertEquals(MoMoGatewayType.MTN, agent.mobileMoneyGateway)
        assertEquals(KycStatus.VERIFIED, agent.mobileMoneyKycStatus)
    }

    @Test
    fun rejected() {
        val request = UpdateMobileChangeRequest(
            status = KycStatus.REJECTED,
            errorCode = "NAME_MISMATCH",
            failureReason = "Name does not match",
        )

        val response = rest.postForEntity("/v1/mobile-change-requests/2", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = mobileChangeService.findById(2L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals("NAME_MISMATCH", change.errorCode)
        assertEquals("Name does not match", change.failureReason)
        assertEquals(USER_ID, change.verifyByUserId)
    }

    @Test
    fun `not for manual review`() {
        val request = UpdateMobileChangeRequest(status = KycStatus.VERIFIED)

        val response = rest.postForEntity("/v1/mobile-change-requests/3", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_REQUEST_NOT_FOR_MANUAL_REVIEW, response.body?.error?.code)
    }

    @Test
    fun `not found`() {
        val request = UpdateMobileChangeRequest(status = KycStatus.VERIFIED)

        val response = rest.postForEntity("/v1/mobile-change-requests/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_REQUEST_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mobile change request belongs to another tenant`() {
        overrideTenantId = 999L

        val request = UpdateMobileChangeRequest(status = KycStatus.VERIFIED)
        val response = rest.postForEntity("/v1/mobile-change-requests/1", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_REQUEST_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun unauthorized() {
        anonymousUser = true

        val request = UpdateMobileChangeRequest(status = KycStatus.VERIFIED)
        val response = rest.postForEntity("/v1/mobile-change-requests/4", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
