package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierMoMo
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierSelfie
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The verifiers are unit-tested on their own, so they're mocked here: this test only covers how the endpoint
 * orchestrates them and aggregates their results into the case, party and payment method statuses.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/party/VerifyKycCaseEndpoint.sql"])
class VerifyKycCaseEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    @Autowired
    private lateinit var paymentMethodDao: PaymentMethodRepository

    @Autowired
    private lateinit var verificationDao: KycVerificationRepository

    @Autowired
    private lateinit var tenantService: TenantService

    @Autowired
    private lateinit var agentDao: AgentRepository

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
                    id = UUID.randomUUID().toString(),
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
        doReturn(
            listOf(
                KycVerificationEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    status = status,
                    score = score
                )
            )
        )
            .whenever(verifier).verify(any())
    }

    private fun setUpNoVerification(verifier: KycCaseVerifier) {
        doReturn(emptyList<KycVerificationEntity>()).whenever(verifier).verify(any())
    }

    private fun verify(id: String = "kyc-case-submitted") =
        rest.postForEntity("/v1/kyc/cases/$id/verify", null, Void::class.java)

    private fun verifyWithError(id: String) =
        rest.postForEntity("/v1/kyc/cases/$id/verify", null, ErrorResponse::class.java)

    private fun assertCase(
        status: KycStatus,
        score: Int?,
        id: String = "kyc-case-submitted",
        errorCode: String? = null
    ) {
        val case = dao.findById(id).orElseThrow()
        assertEquals(status, case.status)
        assertEquals(score, case.score)
        assertEquals(errorCode, case.errorCode)
    }

    private fun assertRetry(retries: Int, errorMessage: String?, id: String = "kyc-case-submitted") {
        val case = dao.findById(id).orElseThrow()
        assertEquals(retries, case.retries)
        assertEquals(errorMessage, case.errorMessage)
    }

    private fun assertPartyKycStatus(status: KycStatus, partyId: Long = 100L) {
        val party = partyDao.findById(partyId).orElseThrow()
        assertEquals(status, party.kycStatus)
    }

    private fun joinedAt(agentId: Long = 100L) = agentDao.findById(agentId).orElseThrow().joinedAt

    private fun assertPaymentMethodStatus(status: PaymentMethodStatus, id: String = "payment-method-pending") {
        val paymentMethod = paymentMethodDao.findById(id).orElseThrow()
        assertEquals(status, paymentMethod.status)
    }

    /** The single status e-mail sent to the case's party ("Pending Party", tenant 1), and its body. */
    private fun assertStatusEmail(subject: String, link: String) {
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received") // sent asynchronously
        val tenant = tenantService.findById(1L)
        val messages = smtp.receivedMessages
        assertEquals(1, messages.size)
        assertEquals(subject, messages[0].subject)
        assertEquals("Pending Party <pending@gmail.com>", messages[0].allRecipients.single().toString())

        val body = messages[0].content.toString()
        assertTrue(body.contains(tenant.name))
        assertTrue(body.contains(tenant.logoUrl!!))
        assertTrue(body.contains("Pending Party"))
        assertTrue(body.contains(link))
    }

    private fun assertVerifiedEmail() = assertStatusEmail(
        "Ndopify: Votre vérification KYC a réussi",
        "http://localhost:8081/partner-central",
    )

    private fun assertRejectedEmail() = assertStatusEmail(
        "Ndopify: Mise à jour concernant votre vérification KYC",
        "http://localhost:8081/partner-central/kyc",
    )

    private fun assertNoEmail() {
        // The email is sent asynchronously: give a wrongly sent one the time to arrive
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
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

        val start = System.currentTimeMillis()
        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95)
        assertPartyKycStatus(KycStatus.VERIFIED)
        assertPaymentMethodStatus(PaymentMethodStatus.VERIFIED)
        assertVerifiedEmail()

        val joinedAt = joinedAt()
        assertNotNull(joinedAt)
        assertTrue(joinedAt.time >= start - 1000) // DATETIME drops the milliseconds

        val isSubmittedCase = { c: KycCaseEntity -> c.id == "kyc-case-submitted" }
        verify(idVerifier).verify(argThat(isSubmittedCase))
        verify(selfieVerifier).verify(argThat(isSubmittedCase))
        verify(paymentMethodVerifier).verify(argThat(isSubmittedCase))
    }

    @Test
    fun `identification rejected - case and party rejected`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.REJECTED, 40)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REJECTED, 70, errorCode = KycErrorCode.IDENTIFICATION_REJECTED)
        assertPartyKycStatus(KycStatus.REJECTED)
        assertRejectedEmail()
        assertNull(joinedAt())
    }

    @Test
    fun `selfie rejected - case and party rejected`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 100)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.REJECTED, 20)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REJECTED, 60, errorCode = KycErrorCode.SELFIE_REJECTED)
        assertPartyKycStatus(KycStatus.REJECTED)
        assertRejectedEmail()
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
        assertCase(KycStatus.REJECTED, 70, errorCode = KycErrorCode.IDENTIFICATION_REJECTED)
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
    fun `ID verifier failure - case back to pending, retry counted`() {
        doThrow(RuntimeException("Gateway down")).whenever(idVerifier).verify(any())

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.SUBMITTED, null, errorCode = KycErrorCode.GATEWAY_ERROR)
        assertRetry(1, "java.lang.RuntimeException: Gateway down")
        assertPartyKycStatus(KycStatus.PENDING_VERIFICATION)
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)

        verify(selfieVerifier, never()).verify(any())
        verify(paymentMethodVerifier, never()).verify(any())
    }

    @Test
    fun `payment verifier failure - ignore`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 100)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)
        doThrow(RuntimeException("Gateway down")).whenever(paymentMethodVerifier).verify(any())

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 100)
        assertPartyKycStatus(KycStatus.VERIFIED)
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)
    }

    @Test
    fun `no identification verification - case back to submitted, retry counted`() {
        setUpNoVerification(idVerifier)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.SUBMITTED, null, errorCode = KycErrorCode.GATEWAY_ERROR)
        assertEquals(1, dao.findById("kyc-case-submitted").orElseThrow().retries)
        assertPartyKycStatus(KycStatus.PENDING_VERIFICATION)

        verify(selfieVerifier, never()).verify(any())
        verify(paymentMethodVerifier, never()).verify(any())
    }

    @Test
    fun `verifier failure on a retried case - retries incremented`() {
        doThrow(RuntimeException("Gateway down")).whenever(idVerifier).verify(any())

        val response = verify("kyc-case-retried")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.SUBMITTED, null, "kyc-case-retried", KycErrorCode.GATEWAY_ERROR)
        assertRetry(2, "java.lang.RuntimeException: Gateway down", "kyc-case-retried")
    }

    @Test
    fun `verifier failure on the last retry - case requires manual review`() {
        doThrow(RuntimeException("Gateway down")).whenever(idVerifier).verify(any())

        val response = verify("kyc-case-last-retry")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.REQUIRES_MANUAL_VERIFICATION, null, "kyc-case-last-retry", KycErrorCode.GATEWAY_ERROR)
        assertRetry(3, "Gateway down", "java.lang.RuntimeException: kyc-case-last-retry")
        assertPartyKycStatus(KycStatus.PENDING_VERIFICATION)
    }

    @Test
    fun `retried case verified - previous error cleared, retries kept`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify("kyc-case-retried")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95, "kyc-case-retried")
        assertRetry(1, null, "kyc-case-retried")
        assertPartyKycStatus(KycStatus.VERIFIED)
        assertVerifiedEmail()
    }

    @Test
    fun `agent already joined - joinedAt kept`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify("kyc-case-already-joined")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95, "kyc-case-already-joined")
        assertEquals("2024-06-11", SimpleDateFormat("yyyy-MM-dd").format(joinedAt(400L)!!))
    }

    @Test
    fun `party not an agent - case verified, no agent created`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify("kyc-case-not-an-agent")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95, "kyc-case-not-an-agent")
        assertPartyKycStatus(KycStatus.VERIFIED, 500L)
        assertNull(agentDao.findByParty(partyDao.findById(500L).orElseThrow()))
    }

    @Test
    fun `email delivery failure - case still verified`() {
        smtp.stop()
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.VERIFIED, 100)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.VERIFIED, 95)
        assertPartyKycStatus(KycStatus.VERIFIED)
    }

    @Test
    fun `case pending - bad request, not submitted for review yet`() {
        val response = verifyWithError("kyc-case-pending")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_SUBMITTED, response.body?.error?.code)

        assertCase(KycStatus.PENDING_VERIFICATION, null, "kyc-case-pending")
        assertNoVerifierCalled()
        assertNoEmail()
    }

    @Test
    fun `case already verified - bad request`() {
        val response = verifyWithError("kyc-case-already-verified")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_SUBMITTED, response.body?.error?.code)

        assertCase(KycStatus.VERIFIED, 95, "kyc-case-already-verified")
        assertNoVerifierCalled()
        assertNoEmail()
    }

    @Test
    fun `case not found`() {
        val response = verifyWithError("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        assertNull(response.body?.error?.data)

        assertNoVerifierCalled()
        assertNoEmail()
    }

    @Test
    fun `case of another tenant - not found`() {
        val response = verifyWithError("kyc-case-other-tenant")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)

        assertNoVerifierCalled()
        assertNoEmail()
    }

    @Test
    fun `verifications saved with their final status`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        setUpVerifier(selfieVerifier, KycVerificationType.SELFIE, KycStatus.REJECTED, 10)

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        val verifications = verificationDao.findAll().filter { it.case.id == "kyc-case-submitted" }
        assertEquals(
            mapOf(
                KycVerificationType.IDENTIFICATION to KycStatus.VERIFIED,
                KycVerificationType.SELFIE to KycStatus.REJECTED,
                KycVerificationType.PAYMENT_METHOD to KycStatus.VERIFIED,
            ),
            verifications.associate { it.type to it.status },
        )
        assertEquals("payment-method-pending", verifications.single { it.paymentMethod != null }.paymentMethod?.id)
    }

    @Test
    fun `verifier failure - no verification saved`() {
        setUpVerifier(idVerifier, KycVerificationType.IDENTIFICATION, KycStatus.VERIFIED, 90)
        doThrow(RuntimeException("Gateway down")).whenever(selfieVerifier).verify(any())

        val response = verify()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertCase(KycStatus.SUBMITTED, null, errorCode = KycErrorCode.GATEWAY_ERROR)
        assertTrue(verificationDao.findAll().none { it.case.id == "kyc-case-submitted" })
        assertPartyKycStatus(KycStatus.PENDING_VERIFICATION)
        assertPaymentMethodStatus(PaymentMethodStatus.PENDING_VERIFICATION)
    }
}
