package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierMoMo
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierSelfie
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SubmitKycCaseEndpoint.sql"])
class SubmitKycCaseEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    // Mocked only to prove submitting never verifies: verification runs offline, on submitted cases.
    @MockitoBean
    private lateinit var idVerifier: KycCaseVerifierIdentification

    @MockitoBean
    private lateinit var selfieVerifier: KycCaseVerifierSelfie

    @MockitoBean
    private lateinit var paymentMethodVerifier: KycCaseVerifierMoMo

    @AfterEach
    fun assertNoVerifierCalled() {
        verify(idVerifier, never()).verify(any())
        verify(selfieVerifier, never()).verify(any())
        verify(paymentMethodVerifier, never()).verify(any())
    }

    private fun submit(id: String) =
        rest.postForEntity("/v1/kyc/cases/$id/submit", null, Void::class.java)

    private fun submitWithError(id: String) =
        rest.postForEntity("/v1/kyc/cases/$id/submit", null, ErrorResponse::class.java)

    private fun assertStatus(status: KycStatus, id: String) {
        assertEquals(status, dao.findById(id).orElseThrow().status)
    }

    @Test
    fun `case with front, back and selfie - submitted`() {
        val response = submit("kyc-case-complete")

        assertEquals(HttpStatus.OK, response.statusCode)
        val case = dao.findById("kyc-case-complete").orElseThrow()
        assertEquals(KycStatus.SUBMITTED, case.status)
        assertTrue(case.modifiedAt.after(SimpleDateFormat("yyyy-MM-dd").parse("2024-06-10")))

        // The party's KYC status only changes once the case is verified.
        assertEquals(KycStatus.PENDING, partyDao.findById(100L).orElseThrow().kycStatus)
    }

    @Test
    fun `case without selfie - submitted, the selfie is optional`() {
        val response = submit("kyc-case-no-selfie")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(KycStatus.SUBMITTED, "kyc-case-no-selfie")
    }

    @Test
    fun `national ID without back - bad request`() {
        val response = submitWithError("kyc-case-missing-back")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-missing-back")
    }

    @Test
    fun `national ID whose back has no content - bad request`() {
        val response = submitWithError("kyc-case-back-no-content")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-back-no-content")
    }

    @Test
    fun `passport with front only - submitted, a passport has no back`() {
        val response = submit("kyc-case-passport")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(KycStatus.SUBMITTED, "kyc-case-passport")
    }

    @Test
    fun `passport without front - bad request`() {
        val response = submitWithError("kyc-case-passport-no-front")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-passport-no-front")
    }

    @Test
    fun `case already submitted - bad request`() {
        val response = submitWithError("kyc-case-submitted")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        assertStatus(KycStatus.SUBMITTED, "kyc-case-submitted")
    }

    @Test
    fun `case rejected - bad request`() {
        val response = submitWithError("kyc-case-rejected")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        assertStatus(KycStatus.REJECTED, "kyc-case-rejected")
    }

    @Test
    fun `case not found`() {
        val response = submitWithError("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `case of another tenant - not found`() {
        val response = submitWithError("kyc-case-other-tenant")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }
}
