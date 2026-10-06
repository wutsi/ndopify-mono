package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.platform.momo.MoMoGateway
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.time.Clock
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class KycVerifierMoMoTest {
    private val dao = mock<KycVerificationRepository>()
    private val provider = mock<MoMoGatewayProvider>()
    private val clock = mock<Clock>()
    private val verifier = KycVerifierMoMo(dao, clock, provider)

    private val party = PartyEntity(
        id = 100L,
        firstName = "Ray",
        lastName = "Sponsible",
    )

    private val paymentMethod = PaymentMethodEntity(
        id = "payment-method-100",
        party = party,
        number = "+237671234567",
        type = PaymentMethodType.MOBILE_MONEY,
    )

    private fun verification(paymentMethod: PaymentMethodEntity?): KycVerificationEntity {
        return KycVerificationEntity(
            id = "verification-100",
            case = KycCaseEntity(
                party = party,
                paymentMethod = paymentMethod,
            ),
            status = KycStatus.PENDING,
        )
    }

    @Test
    fun `not a mobile money payment method`() {
        val bankAccount = paymentMethod.copy(type = PaymentMethodType.BANK_ACCOUNT)
        val verif = verification(bankAccount)
        val result = verifier.verify(verif)

        assertEquals(verif.status, result.status)
        verify(dao, never()).save(any())
    }

    @Test
    fun `no payment method`() {
        val verif = verification(null)
        val result = verifier.verify(verif)

        assertEquals(verif.status, result.status)
        verify(dao, never()).save(any())
    }

    @Test
    fun `no gateway for the phone number - manual review`() {
        val now = 1_000_000L
        doReturn(now).whenever(clock).millis()
        doReturn(null).whenever(provider).getByPhoneNumber(paymentMethod.number)

        val verification = verification(paymentMethod)
        val result = verifier.verify(verification)

        verify(dao).save(
            verification.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(
            verification.copy(
                status = KycStatus.REQUIRES_MANUAL_REVIEW,
                modifiedAt = Date(now),
            )
        )
    }

    @Test
    fun `active match - verified`() {
        val now = 1_000_000L
        doReturn(now).whenever(clock).millis()

        val gateway = mock<MoMoGateway>()
        doReturn(gateway).whenever(provider).getByPhoneNumber(paymentMethod.number)
        doReturn(
            MoMoKycMatchResponse(
                holderNameScore = 0.9,
                countryCodeScore = 1.0,
                active = true,
                holderName = "Ray Sponsible",
            )
        ).whenever(gateway).kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = "Ray Sponsible",
                countryCode = "",
            )
        )

        val verification = verification(paymentMethod)
        verifier.verify(verification)

        verify(dao).save(
            verification.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(
            verification.copy(
                score = 90,
                status = KycStatus.VERIFIED,
                errorCode = null,
                modifiedAt = Date(now),
            )
        )
    }

    @Test
    fun `active match but low score - rejected`() {
        val now = 1_000_000L
        doReturn(now).whenever(clock).millis()

        val gateway = mock<MoMoGateway>()
        doReturn(gateway).whenever(provider).getByPhoneNumber(paymentMethod.number)
        doReturn(
            MoMoKycMatchResponse(
                holderNameScore = 0.89,
                countryCodeScore = 1.0,
                active = true,
                holderName = "Ray Sponsible",
            )
        ).whenever(gateway).kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = "Ray Sponsible",
                countryCode = "",
            )
        )

        val verification = verification(paymentMethod)
        verifier.verify(verification)

        verify(dao).save(
            verification.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(
            verification.copy(
                score = 89,
                status = KycStatus.REJECTED,
                errorCode = KycErrorCode.LOW_SCORE,
                modifiedAt = Date(now),
            )
        )
    }

    @Test
    fun `inactive match - rejected`() {
        val now = 1_000_000L
        doReturn(now).whenever(clock).millis()

        val gateway = mock<MoMoGateway>()
        doReturn(gateway).whenever(provider).getByPhoneNumber(paymentMethod.number)
        doReturn(
            MoMoKycMatchResponse(
                holderNameScore = 1.0,
                countryCodeScore = 1.0,
                active = false,
                holderName = "Ray Sponsible",
            )
        ).whenever(gateway).kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = "Ray Sponsible",
                countryCode = "",
            )
        )

        val verification = verification(paymentMethod)
        verifier.verify(verification)

        verify(dao).save(
            verification.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(
            verification.copy(
                score = 100,
                status = KycStatus.REJECTED,
                errorCode = KycErrorCode.INACTIVE,
                modifiedAt = Date(now),
            )
        )
    }
}
