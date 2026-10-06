package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierManual
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierMoMo
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class KycVerifierProviderTest {
    private val momo = mock<KycVerifierMoMo>()
    private val id = mock<KycVerifierIdentification>()
    private val manual = mock<KycVerifierManual>()
    private val provider = KycVerifierProvider(momo, id, manual)

    private fun verification(
        type: KycVerificationType,
        paymentMethod: PaymentMethodEntity? = null,
    ): KycVerificationEntity {
        return KycVerificationEntity(
            type = type,
            case = KycCaseEntity(
                paymentMethod = paymentMethod,
            ),
        )
    }

    @Test
    fun `identification verification - identification verifier`() {
        val verif = verification(KycVerificationType.IDENTIFICATION)

        assertEquals(id, provider.get(verif))
    }

    @Test
    fun `payment method verification - mobile money - momo verifier`() {
        val verif = verification(
            KycVerificationType.PAYMENT_METHOD,
            PaymentMethodEntity(type = PaymentMethodType.MOBILE_MONEY),
        )

        assertEquals(momo, provider.get(verif))
    }

    @Test
    fun `payment method verification - bank account - manual verifier`() {
        val verif = verification(
            KycVerificationType.PAYMENT_METHOD,
            PaymentMethodEntity(type = PaymentMethodType.BANK_ACCOUNT),
        )

        assertEquals(manual, provider.get(verif))
    }

    @Test
    fun `payment method verification - no payment method - manual verifier`() {
        val verif = verification(KycVerificationType.PAYMENT_METHOD, null)

        assertEquals(manual, provider.get(verif))
    }

    @Test
    fun `unknown verification type - manual verifier`() {
        val verif = verification(KycVerificationType.UNKNOWN)

        assertEquals(manual, provider.get(verif))
    }
}
