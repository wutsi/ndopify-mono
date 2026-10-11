package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierMoMo
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierSelfie
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KycCaseVerifierProviderTest {
    private val id = mock<KycCaseVerifierIdentification>()
    private val selfie = mock<KycCaseVerifierSelfie>()
    private val momo = mock<KycCaseVerifierMoMo>()
    private val provider = KycCaseVerifierProvider(id, selfie, momo)

    @Test
    fun `identification - identification verifier`() {
        assertEquals(id, provider.get(KycVerificationType.IDENTIFICATION))
    }

    @Test
    fun `payment method - momo verifier`() {
        assertEquals(momo, provider.get(KycVerificationType.PAYMENT_METHOD))
    }

    @Test
    fun `selfie - selfie verifier`() {
        assertEquals(selfie, provider.get(KycVerificationType.SELFIE))
    }

    @Test
    fun `unknown - no verifier`() {
        assertNull(provider.get(KycVerificationType.UNKNOWN))
    }
}
