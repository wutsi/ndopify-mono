package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.platform.momo.MoMoGateway
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KycCaseVerifierMoMoTest {
    private val dao = mock<KycVerificationRepository>()
    private val paymentMethodDao = mock<PaymentMethodRepository>()
    private val clock = mock<Clock>()
    private val gatewayProvider = mock<MoMoGatewayProvider>()
    private val gateway = mock<MoMoGateway>()
    private val verifier = KycCaseVerifierMoMo(dao, paymentMethodDao, clock, gatewayProvider)

    private val now = 1_700_000_000_000L

    private val party = PartyEntity(
        id = 100L,
        firstName = "Ray",
        lastName = "Sponsible",
    )

    private val momo = PaymentMethodEntity(
        id = "payment-method-1",
        party = party,
        number = "+237671234567",
        type = PaymentMethodType.MOBILE_MONEY,
        status = PaymentMethodStatus.PENDING_VERIFICATION,
    )

    private val case = KycCaseEntity(
        id = "case-1",
        party = party,
    )

    @BeforeEach
    fun setUp() {
        doReturn(now).whenever(clock).millis()
        doAnswer { it.arguments[0] }.whenever(dao).save(any<KycVerificationEntity>())
        doReturn(gateway).whenever(gatewayProvider).getByPhoneNumber(any())
    }

    private fun setUpPaymentMethods(vararg methods: PaymentMethodEntity) {
        doReturn(methods.toList()).whenever(paymentMethodDao).findByParty(party)
    }

    private fun setUpMatch(
        holderNameScore: Double = 1.0,
        active: Boolean = true,
    ) {
        doReturn(
            MoMoKycMatchResponse(
                holderNameScore = holderNameScore,
                countryCodeScore = 1.0,
                active = active,
                holderName = "Ray Sponsible",
            )
        ).whenever(gateway).kycMatch(any())
    }

    @Test
    fun `active match - verified`() {
        setUpPaymentMethods(momo)
        setUpMatch()

        val results = verifier.verify(case)

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(KycVerificationType.PAYMENT_METHOD, result.type)
        assertEquals(100, result.score)
        assertNull(result.errorCode)
        assertEquals(case, result.case)
        assertEquals(momo, result.paymentMethod)
        assertNotNull(result.id)
        assertEquals(Date(now), result.createdAt)
        assertEquals(Date(now), result.modifiedAt)

        val captor = argumentCaptor<KycVerificationEntity>()
        verify(dao, times(2)).save(captor.capture())
        val started = captor.firstValue
        assertEquals(KycStatus.IN_PROGRESS, started.status)
        assertEquals(KycVerificationType.PAYMENT_METHOD, started.type)
        assertEquals(momo, started.paymentMethod)
        assertEquals(started.id, captor.secondValue.id)
        assertEquals(result, captor.secondValue)
    }

    @Test
    fun `gateway is called with the number and the party name`() {
        setUpPaymentMethods(momo)
        setUpMatch()

        verifier.verify(case)

        verify(gatewayProvider).getByPhoneNumber(momo.number)
        verify(gateway).kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = momo.number,
                holderName = "Ray Sponsible",
                countryCode = "",
            )
        )
    }

    @Test
    fun `score at threshold - verified`() {
        setUpPaymentMethods(momo)
        setUpMatch(holderNameScore = 0.9)

        val result = verifier.verify(case)[0]

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(90, result.score)
        assertNull(result.errorCode)
    }

    @Test
    fun `low score - rejected`() {
        setUpPaymentMethods(momo)
        setUpMatch(holderNameScore = 0.89)

        val result = verifier.verify(case)[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(89, result.score)
        assertEquals(KycErrorCode.LOW_SCORE, result.errorCode)
    }

    @Test
    fun `inactive account - rejected`() {
        setUpPaymentMethods(momo)
        setUpMatch(active = false)

        val result = verifier.verify(case)[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(100, result.score)
        assertEquals(KycErrorCode.INACTIVE, result.errorCode)
    }

    @Test
    fun `no gateway for the number - manual review`() {
        setUpPaymentMethods(momo)
        doReturn(null).whenever(gatewayProvider).getByPhoneNumber(momo.number)

        val result = verifier.verify(case)[0]

        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, result.status)
        assertNull(result.score)
        assertNull(result.errorCode)
        verify(gateway, never()).kycMatch(any())
    }

    @Test
    fun `one verification per pending mobile money payment method`() {
        val other = momo.copy(id = "payment-method-2", number = "+237699999999")
        setUpPaymentMethods(momo, other)
        setUpMatch()

        val results = verifier.verify(case)

        assertEquals(listOf(momo, other), results.map { it.paymentMethod })
        assertTrue(results.all { it.status == KycStatus.VERIFIED })
        assertEquals(2, results.map { it.id }.toSet().size)
        verify(dao, times(4)).save(any<KycVerificationEntity>())
    }

    @Test
    fun `only pending mobile money payment methods are verified`() {
        val bankAccount = momo.copy(id = "payment-method-2", type = PaymentMethodType.BANK_ACCOUNT)
        val verified = momo.copy(id = "payment-method-3", status = PaymentMethodStatus.VERIFIED)
        setUpPaymentMethods(bankAccount, momo, verified)
        setUpMatch()

        val results = verifier.verify(case)

        assertEquals(listOf(momo), results.map { it.paymentMethod })
    }

    @Test
    fun `no eligible payment method - no verification`() {
        setUpPaymentMethods(
            momo.copy(type = PaymentMethodType.BANK_ACCOUNT),
            momo.copy(status = PaymentMethodStatus.REJECTED),
        )

        val results = verifier.verify(case)

        assertTrue(results.isEmpty())
        verify(dao, never()).save(any<KycVerificationEntity>())
        verify(gatewayProvider, never()).getByPhoneNumber(any())
    }

    @Test
    fun `no payment method - no verification`() {
        setUpPaymentMethods()

        val results = verifier.verify(case)

        assertTrue(results.isEmpty())
        verify(dao, never()).save(any<KycVerificationEntity>())
    }
}
