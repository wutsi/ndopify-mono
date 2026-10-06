package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/GetKycCaseEndpoint.sql"])
class GetKycCaseEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun `with payment method`() {
        val response = rest.getForEntity("/v1/kyc/cases/kyc-case-100", GetKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val case = response.body!!.case
        assertEquals("kyc-case-100", case.id)
        assertEquals("100", case.partyId)
        assertEquals("identification-100", case.identificationId)
        assertEquals("payment-method-100", case.paymentMethodId)
        assertEquals(80, case.score)
        assertEquals(KycStatus.VERIFIED, case.status)
        assertNull(case.errorCode)

        val verifications = case.verifications
        assertEquals(2, verifications.size)
        assertEquals(
            setOf(KycVerificationType.IDENTIFICATION, KycVerificationType.PAYMENT_METHOD),
            verifications.map { it.type }.toSet(),
        )
        verifications.forEach { verification ->
            assertEquals("kyc-case-100", verification.kycCaseId)
            assertEquals(KycStatus.VERIFIED, verification.status)
        }
    }

    @Test
    fun `without payment method`() {
        val response = rest.getForEntity("/v1/kyc/cases/kyc-case-200", GetKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val case = response.body!!.case
        assertEquals("kyc-case-200", case.id)
        assertEquals("100", case.partyId)
        assertEquals("identification-100", case.identificationId)
        assertNull(case.paymentMethodId)
        assertNull(case.score)
        assertEquals(KycStatus.PENDING, case.status)
        assertEquals(0, case.verifications.size)
    }

    @Test
    fun `case not found`() {
        val response = rest.getForEntity("/v1/kyc/cases/unknown-id", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `case from another tenant is not found`() {
        val response = rest.getForEntity("/v1/kyc/cases/kyc-case-900", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }
}
