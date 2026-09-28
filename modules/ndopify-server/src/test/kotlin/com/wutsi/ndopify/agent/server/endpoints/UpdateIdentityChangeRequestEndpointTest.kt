package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.UpdateIdentityChangeRequest
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.agent.server.service.IdentityChangeService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateIdentityChangeRequestEndpoint.sql"])
class UpdateIdentityChangeRequestEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var identityChangeService: IdentityChangeService

    @Autowired
    private lateinit var agentService: AgentService

    @Test
    fun verified() {
        val request = UpdateIdentityChangeRequest(
            holderName = "Ray Sponsible",
            status = KycStatus.VERIFIED,
            errorCode = null,
            failureReason = null,
        )

        val response = rest.postForEntity("/v1/identity-changes/1", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = identityChangeService.findById(1L, TENANT_ID)
        assertEquals(KycStatus.VERIFIED, change.status)
        assertEquals("Ray Sponsible", change.holderName)
        assertEquals(null, change.errorCode)
        assertEquals(null, change.failureReason)
        assertEquals(USER_ID, change.verifyByUserId)

        val agent = agentService.findById(1L, TENANT_ID)
        assertEquals(KycStatus.VERIFIED, agent.identityKycStatus)
    }

    @Test
    fun rejected() {
        val request = UpdateIdentityChangeRequest(
            status = KycStatus.REJECTED,
            errorCode = "NAME_MISMATCH",
            failureReason = "Name does not match",
        )

        val response = rest.postForEntity("/v1/identity-changes/2", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = identityChangeService.findById(2L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals("NAME_MISMATCH", change.errorCode)
        assertEquals("Name does not match", change.failureReason)
        assertEquals(USER_ID, change.verifyByUserId)
    }

    @Test
    fun `not for manual review`() {
        val request = UpdateIdentityChangeRequest(status = KycStatus.VERIFIED)

        val response = rest.postForEntity("/v1/identity-changes/3", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOR_MANUAL_REVIEW, response.body?.error?.code)
    }

    @Test
    fun `not found`() {
        val request = UpdateIdentityChangeRequest(status = KycStatus.VERIFIED)

        val response = rest.postForEntity("/v1/identity-changes/999", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `identity change request belongs to another tenant`() {
        overrideTenantId = 999L

        val request = UpdateIdentityChangeRequest(status = KycStatus.VERIFIED)
        val response = rest.postForEntity("/v1/identity-changes/1", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun unauthorized() {
        anonymousUser = true

        val request = UpdateIdentityChangeRequest(status = KycStatus.VERIFIED)
        val response = rest.postForEntity("/v1/identity-changes/4", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
