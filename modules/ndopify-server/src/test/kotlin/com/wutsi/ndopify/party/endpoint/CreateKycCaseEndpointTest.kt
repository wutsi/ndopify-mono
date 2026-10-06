package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/CreateKycCaseEndpoint.sql"])
class CreateKycCaseEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    @Autowired
    private lateinit var daoVerification: KycVerificationRepository

    @Test
    fun `with payment method`() {
        val request = CreateKycCaseRequest(
            identificationId = "identification-100",
            paymentMethodId = "payment-method-100",
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, CreateKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val caseId = response.body?.caseId
        val case = dao.findById(caseId!!).orElseThrow()
        assertEquals(100L, case.party.id)
        assertEquals("identification-100", case.identification.id)
        assertEquals("payment-method-100", case.paymentMethod?.id)
        assertEquals(KycStatus.PENDING, case.status)
        assertNull(case.score)
        assertNull(case.errorCode)

        val verifications = daoVerification.findAll().filter { it.case.id == caseId }
        assertEquals(2, verifications.size)
        assertEquals(
            setOf(KycVerificationType.IDENTIFICATION, KycVerificationType.PAYMENT_METHOD),
            verifications.map { it.type }.toSet(),
        )
        verifications.forEach { verification ->
            assertEquals(KycStatus.PENDING, verification.status)
        }
    }

    @Test
    fun `without payment method`() {
        val request = CreateKycCaseRequest(
            identificationId = "identification-100",
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, CreateKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val caseId = response.body?.caseId
        val case = dao.findById(caseId!!).orElseThrow()
        assertEquals(100L, case.party.id)
        assertEquals("identification-100", case.identification.id)
        assertNull(case.paymentMethod)

        val verifications = daoVerification.findAll().filter { it.case.id == caseId }
        assertEquals(1, verifications.size)
        assertEquals(KycVerificationType.IDENTIFICATION, verifications[0].type)
    }

    @Test
    fun `identification not found`() {
        val request = CreateKycCaseRequest(
            identificationId = "identification-999",
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `payment method not found`() {
        val request = CreateKycCaseRequest(
            identificationId = "identification-100",
            paymentMethodId = "payment-method-999",
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `payment method belongs to a different party than the identification`() {
        val request = CreateKycCaseRequest(
            identificationId = "identification-100",
            paymentMethodId = "payment-method-200",
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_PARTY_MISMATCH, response.body?.error?.code)

        val cases = dao.findAll().toList()
        assertEquals(0, cases.size)
    }
}
