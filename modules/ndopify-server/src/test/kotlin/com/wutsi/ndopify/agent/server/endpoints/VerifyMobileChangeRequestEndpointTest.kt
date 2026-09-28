package com.wutsi.ndopify.agent.server.endpoints

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.VerifyMobileChangeResponse
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.agent.server.service.MobileChangeService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.platform.momo.MoMoError
import com.wutsi.ndopify.platform.momo.MoMoException
import com.wutsi.ndopify.platform.momo.mtn.MtnCollectionProduct
import com.wutsi.ndopify.platform.momo.mtn.model.MtnBasicUserInfoResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUserStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/VerifyMobileChangeRequestEndpoint.sql"])
class VerifyMobileChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var mtnCollection: MtnCollectionProduct

    @Autowired
    private lateinit var agentService: AgentService

    @Autowired
    private lateinit var mobileChangeService: MobileChangeService

    @Test
    fun `gateway not supported`() {
        val response =
            rest.postForEntity("/v1/mobile-changes/1/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.AUTO_REVIEW_NOT_SUPPORTED, response.body!!.errorCode)

        val change = mobileChangeService.findById(1L, TENANT_ID)
        assertEquals(null, change.verifyByUserId)
    }

    @Test
    fun `account not active`() {
        doReturn(MtnBasicUserInfoResponse(givenName = "Ray", familyName = "Sponsible", status = MtnUserStatus.INACTIVE))
            .whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/2/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.INACTIVE, response.body!!.errorCode)
    }

    @Test
    fun `verified when holder name score is high`() {
        doReturn(MtnBasicUserInfoResponse(givenName = "Ray", familyName = "Ray", status = MtnUserStatus.ACTIVE))
            .whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/4/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.VERIFIED, response.body!!.status)
        assertEquals(null, response.body!!.errorCode)

        val agent = agentService.findById(2L, TENANT_ID)
        assertEquals("+237600000002", agent.mobileMoneyNumber)
        assertEquals(MoMoGatewayType.MTN, agent.mobileMoneyGateway)
        assertEquals(KycStatus.VERIFIED, agent.mobileMoneyKycStatus)
        assertNull(agent.mobileChange)

        val change = mobileChangeService.findById(4L, TENANT_ID)
        assertEquals(KycStatus.VERIFIED, change.status)
        assertEquals(null, change.errorCode)
        assertEquals(null, change.failureReason)
        assertEquals(0, change.retries)
        assertEquals(null, change.verifyByUserId)
        assertEquals("Ray Ray", change.holderName)
        assertEquals(1.0, change.holderNameScore)
        assertEquals(1.0, change.countryCodeScore)
    }

    @Test
    fun `requires manual review when holder name score is in the gray zone`() {
        doReturn(
            MtnBasicUserInfoResponse(
                givenName = "Raymondo",
                familyName = "Sponsible",
                status = MtnUserStatus.ACTIVE
            )
        )
            .whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/5/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, response.body!!.errorCode)

        val change = mobileChangeService.findById(5L, TENANT_ID)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, change.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, change.errorCode)
        assertEquals("Raymondo Sponsible", change.holderName)
        assertEquals(true, change.holderNameScore!! < .9)
        assertEquals(1.0, change.countryCodeScore)
    }

    @Test
    fun `rejected when holder name score is too low`() {
        doReturn(MtnBasicUserInfoResponse(givenName = "Xyz", familyName = "Xyz", status = MtnUserStatus.ACTIVE))
            .whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/6/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, response.body!!.errorCode)

        val change = mobileChangeService.findById(6L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.NAME_MISMATCH, change.errorCode)
        assertEquals("Xyz Xyz", change.holderName)
        assertEquals(true, change.holderNameScore!! < .75)
        assertEquals(1.0, change.countryCodeScore)
    }

    @Test
    fun `rejected when country does not match`() {
        doReturn(MtnBasicUserInfoResponse(givenName = "Kim", familyName = "Possible", status = MtnUserStatus.ACTIVE))
            .whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/7/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REJECTED, response.body!!.status)
        assertEquals(KycErrorCode.COUNTRY_NOT_VALID, response.body!!.errorCode)

        val change = mobileChangeService.findById(7L, TENANT_ID)
        assertEquals(KycStatus.REJECTED, change.status)
        assertEquals(KycErrorCode.COUNTRY_NOT_VALID, change.errorCode)
        assertEquals("Kim Possible", change.holderName)
        assertEquals(1.0, change.holderNameScore)
        assertEquals(0.0, change.countryCodeScore)
    }

    @Test
    fun `back to pending when gateway fails`() {
        doAnswer { throw MoMoException(MoMoError(), "timeout") }.whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/8/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.PENDING, response.body!!.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, response.body!!.errorCode)

        val change = mobileChangeService.findById(8L, TENANT_ID)
        assertEquals(KycStatus.PENDING, change.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, change.errorCode)
        assertEquals(2, change.retries)
        assertEquals("timeout", change.failureReason)
        assertEquals(null, change.verifyByUserId)
    }

    @Test
    fun `set to  manual review  when gateway fails after max retries`() {
        doAnswer { throw MoMoException(MoMoError(), "timeout") }.whenever(mtnCollection).userBasicInfo(any())

        val response =
            rest.postForEntity("/v1/mobile-changes/9/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, response.body!!.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, response.body!!.errorCode)

        val change = mobileChangeService.findById(9L, TENANT_ID)
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, change.status)
        assertEquals(KycErrorCode.GATEWAY_ERROR, change.errorCode)
        assertEquals(4, change.retries)
        assertEquals("timeout", change.failureReason)
        assertEquals(null, change.verifyByUserId)
    }

    @Test
    fun `cancelled when superseded by a newer mobile change request`() {
        val response =
            rest.postForEntity("/v1/mobile-changes/10/verify", null, VerifyMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(KycStatus.CANCELLED, response.body!!.status)

        val change = mobileChangeService.findById(10L, TENANT_ID)
        assertEquals(KycStatus.CANCELLED, change.status)
    }

    @Test
    fun `not found`() {
        val response = rest.postForEntity("/v1/mobile-changes/999/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `already processed`() {
        val response = rest.postForEntity("/v1/mobile-changes/3/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_ALREADY_PROCEEDED, response.body?.error?.code)
    }

    @Test
    fun `mobile change request belongs to another tenant`() {
        overrideTenantId = 999L

        val response = rest.postForEntity("/v1/mobile-changes/1/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_NOT_FOUND, response.body?.error?.code)
    }
}
