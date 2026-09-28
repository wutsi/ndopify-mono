package com.wutsi.ndopify.agent.server.endpoints

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.VerifyIdentityChangeResponse
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.agent.server.service.IdentityChangeService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.platform.identity.ai.AiIdentityInfoExtractor
import com.wutsi.ndopify.platform.identity.model.IdentityInfo
import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/VerifyIdentityChangeRequestEndpoint.sql"])
class VerifyIdentityChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var extractor: AiIdentityInfoExtractor

    @Autowired
    private lateinit var agentService: AgentService

    @Autowired
    private lateinit var identityChangeService: IdentityChangeService

    @Test
    fun `document type not supported`() {
        val response =
            rest.postForEntity("/v1/identity-changes/1/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.AUTO_REVIEW_NOT_SUPPORTED, response.body!!.errorCode)

        val change = identityChangeService.findById(1L, TENANT_ID)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, change.status)
        assertEquals(KycErrorCode.AUTO_REVIEW_NOT_SUPPORTED, change.errorCode)
        assertEquals(null, change.verifyByUserId)
    }

    @Test
    fun `invalid document`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Ray",
                lastName = "Sponsible",
                countryCode = "CM",
                status = IdentityStatus.INVALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/2/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.INVALID, response.body!!.errorCode)

        val change = identityChangeService.findById(2L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.INVALID, change.errorCode)
    }

    @Test
    fun `expired document`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Ray",
                lastName = "Sponsible",
                countryCode = "CM",
                status = IdentityStatus.EXPIRED,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/12/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.EXPIRED, response.body!!.errorCode)

        val change = identityChangeService.findById(12L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.EXPIRED, change.errorCode)
    }

    @Test
    fun `suspended document`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Ray",
                lastName = "Sponsible",
                countryCode = "CM",
                status = IdentityStatus.SUSPENDED,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/13/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.SUSPENDED, response.body!!.errorCode)

        val change = identityChangeService.findById(13L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.SUSPENDED, change.errorCode)
    }

    @Test
    fun `verified when holder name score is high`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "Kim",
                lastName = "Possible",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/4/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.VERIFIED, response.body!!.status)
        assertEquals(null, response.body!!.errorCode)

        val agent = agentService.findById(2L, TENANT_ID)
        assertEquals(KycStatus.VERIFIED, agent.identityKycStatus)
        assertEquals(AgentStatus.RESTRICTED, agent.status)
        assertNull(agent.identityChange)

        val change = identityChangeService.findById(4L, TENANT_ID)
        assertEquals(1.0, change.holderNameScore)
        assertEquals("Kim Possible", change.holderName)
    }

    @Test
    fun `requires manual review when holder name score is in the gray zone`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Raymondo",
                lastName = "Sponsible",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/5/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, response.body!!.errorCode)

        val change = identityChangeService.findById(5L, TENANT_ID)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, change.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, change.errorCode)
        assertEquals(true, change.holderNameScore!! <= 0.9 && change.holderNameScore >= 0.75)
        assertEquals("Raymondo Sponsible", change.holderName)
    }

    @Test
    fun `rejected when holder name score is too low`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Xyz",
                lastName = "Qwerty",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/6/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, response.body!!.errorCode)

        val change = identityChangeService.findById(6L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, change.errorCode)
        assertEquals(true, change.holderNameScore!! < 0.75)
        assertEquals("Xyz Qwerty", change.holderName)
    }

    @Test
    fun `rejected when country does not match`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Ray",
                lastName = "Sponsible",
                countryCode = "FR",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/7/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.COUNTRY_NOT_VALID, response.body!!.errorCode)

        val change = identityChangeService.findById(7L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.COUNTRY_NOT_VALID, change.errorCode)
        assertEquals(0.0, change.countryCodeScore)
    }

    @Test
    fun `rejected when document type does not match`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "Ray",
                lastName = "Sponsible",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/14/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.INVALID, response.body!!.errorCode)

        val change = identityChangeService.findById(14L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.INVALID, change.errorCode)
        assertEquals(0.0, change.documentTypeScore)
    }

    @Test
    fun `back to pending when gateway fails`() {
        doAnswer { throw RuntimeException("timeout") }.whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/8/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.PENDING, response.body!!.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, response.body!!.errorCode)

        val change = identityChangeService.findById(8L, TENANT_ID)
        assertEquals(KycStatus.PENDING, change.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, change.errorCode)
        assertEquals(2, change.retries)
        assertEquals("timeout", change.failureReason)
        assertEquals(null, change.verifyByUserId)
    }

    @Test
    fun `set to manual review when gateway fails after max retries`() {
        doAnswer { throw RuntimeException("timeout") }.whenever(extractor).extract(any())

        val response =
            rest.postForEntity("/v1/identity-changes/9/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, response.body!!.errorCode)

        val change = identityChangeService.findById(9L, TENANT_ID)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, change.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, change.errorCode)
        assertEquals(4, change.retries)
        assertEquals("timeout", change.failureReason)
    }

    @Test
    fun `cancelled when superseded by a newer identity change request`() {
        val response =
            rest.postForEntity("/v1/identity-changes/10/verify", null, VerifyIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.CANCELLED, response.body!!.status)

        val change = identityChangeService.findById(10L, TENANT_ID)
        assertEquals(KycStatus.CANCELLED, change.status)
    }

    @Test
    fun `not found`() {
        val response = rest.postForEntity("/v1/identity-changes/999/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `already processed`() {
        val response = rest.postForEntity("/v1/identity-changes/3/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_ALREADY_PROCEEDED, response.body?.error?.code)
    }

    @Test
    fun `identity change request belongs to another tenant`() {
        overrideTenantId = 999L

        val response = rest.postForEntity("/v1/identity-changes/1/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }
}
