package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierMoMo
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierSelfie
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The verifiers are unit-tested on their own, so they're mocked here: this test only covers how the endpoint
 * orchestrates them and aggregates their results into the case, party and payment method statuses.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/party/VerifyKycCaseEndpoint.sql"])
class VerifyKycCaseEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    @Autowired
    private lateinit var paymentMethodDao: PaymentMethodRepository

    @MockitoBean
    private lateinit var idVerifier: KycCaseVerifierIdentification

    @MockitoBean
    private lateinit var selfieVerifier: KycCaseVerifierSelfie

    @MockitoBean
    private lateinit var paymentMethodVerifier: KycCaseVerifierMoMo

    @BeforeEach
    fun stubPaymentMethodVerifier() {
        setUpPaymentMethodVerifier("payment-method-pending", KycStatus.VERIFIED)
    }

    private fun setUpPaymentMethodVerifier(paymentMethodId: String?, status: KycStatus) {
        val paymentMethod = paymentMethodId?.let { paymentMethodDao.findById(it).orElseThrow() }
        doReturn(
            listOf(
                KycVerificationEntity(
                    type = KycVerificationType.PAYMENT_METHOD,
                    paymentMethod = paymentMethod,
                    status = status,
                    score = if (status == KycStatus.VERIFIED) 100 else 10,
                )
            )
        ).whenever(paymentMethodVerifier).verify(any())
    }

    private fun setUpVerifier(
        verifier: KycCaseVerifier,
        type: KycVerificationType,
        status: KycStatus,
        score: Int?,
    ) {
        doReturn(listOf(KycVerificationEntity(type = type, status = status, score = score)))
            .whenever(verifier).verify(any())
    }

    private fun setUpNoVerification(verifier: KycCaseVerifier) {
        doReturn(emptyList<KycVerificationEntity>()).whenever(verifier).verify(any())
    }

    private fun verify(id: String = "kyc-case-pending") =
        rest.postForEntity("/v1/kyc/cases/$id/verify", null, Void::class.java)

    private fun verifyWithError(id: String) =
        rest.postForEntity("/v1/kyc/cases/$id/verify", null, ErrorResponse::class.java)

    private fun assertCase(status: KycStatus, score: Int?, id: String = "kyc-case-pending") {
        val case = dao.findById(id).orElseThrow()
        assertEquals(status, case.status)
        assertEquals(score, case.score)
    }

    private fun assertPartyKycStatus(status: KycStatus, partyId: Long = 100L) {
        val party = partyDao.findById(partyId).orElseThrow()
        assertEquals(status, party.kycStatus)
    }

    private fun assertPaymentMethodStatus(status: PaymentMethodStatus, id: String = "payment-method-pending") {
        val paymentMethod = paymentMethodDao.findById(id).orElseThrow()
        assertEquals(status, paymentMethod.status)
    }

    private fun assertNoVerifierCalled() {
        verify(idVerifier, never()).verify(any())
        verify(selfieVerifier, never()).verify(any())
        verify(paymentMethodVerifier, never()).verify(any())
    }

    @Test
    fun `identification and selfie verified - case and party verified`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95)
        assertPartyKycStatus(KycStatus.VERIFIED)
        assertPaymentMethodStatus(PaymentMethodStatus.VERIFIED)

        val isPendingCase = { c: KycCaseEntity -> c.id == "kyc-case-pending" }
        verify(idVerifier).verify(argThat(isPendingCase))
        verify(selfieVerifier).verify(argThat(isPendingCase))
        verify(paymentMethodVerifier).verify(argThat(isPendingCase))
    }

    @Test
    fun `identification rejected - case and party rejected`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.REJECTED, 40)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REJECTED, 70)
        assertPartyKycStatus(KycStatus.REJECTED)
    }

    @Test
    fun `selfie rejected - case and party rejected`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 100)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.REJECTED, 20)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REJECTED, 60)
        assertPartyKycStatus(KycStatus.REJECTED)
    }

    @Test
    fun `no selfie verification - case status and score from identification only`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpNoVerification(selfieVerifier)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 90)
        assertPartyKycStatus(KycStatus.VERIFIED)
    }

    @Test
    fun `missing scores - counted as zero in the case score`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, null)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 80)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 40)
    }

    @Test
    fun `payment method rejected - payment method rejected, without affecting the case status nor score`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)
        setUpPaymentMethodVerifier("payment-method-pending", KycStatus.REJECTED)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95)
        assertPartyKycStatus(KycStatus.VERIFIED)
        assertPaymentMethodStatus(PaymentMethodStatus.REJECTED)
    }

    @Test
    fun `identification rejected - payment method still verified on its own result`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.REJECTED, 40)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REJECTED, 70)
        assertPaymentMethodStatus(PaymentMethodStatus.VERIFIED)
    }

    @Test
    fun `payment method not pending verification - status unchanged`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)
        setUpPaymentMethodVerifier("payment-method-verified", KycStatus.REJECTED)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertPaymentMethodStatus(PaymentMethodStatus.VERIFIED, "payment-method-verified")
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)
    }

    @Test
    fun `payment method verification without payment method - no payment method updated`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)
        setUpPaymentMethodVerifier(null, KycStatus.VERIFIED)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)
        assertPaymentMethodStatus(PaymentMethodStatus.VERIFIED, "payment-method-verified")
    }

    @Test
    fun `no identification verification - conflict and case left pending`() {
        setUpNoVerification(idVerifier)

        val response = verifyWithError("kyc-case-pending")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.KYC_IMAGE_NO_IDENTIFICATION_VERIFICATION, response.body?.error?.code)

        // The transaction is rolled back: the case doesn't stay IN_PROGRESS
        assertCase(KycStatus.PENDING, null)
        assertPartyKycStatus(KycStatus.PENDING)
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)

        verify(selfieVerifier, never()).verify(any())
        verify(paymentMethodVerifier, never()).verify(any())
    }

    @Test
    fun `case not pending - bad request`() {
        val response = verifyWithError("kyc-case-already-verified")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)

        assertCase(KycStatus.VERIFIED, 95, "kyc-case-already-verified")
        assertNoVerifierCalled()
    }

    @Test
    fun `case not found`() {
        val response = verifyWithError("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        assertNull(response.body?.error?.data)

        assertNoVerifierCalled()
    }

    @Test
    fun `case of another tenant - not found`() {
        val response = verifyWithError("kyc-case-other-tenant")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)

        assertNoVerifierCalled()
    }
}
