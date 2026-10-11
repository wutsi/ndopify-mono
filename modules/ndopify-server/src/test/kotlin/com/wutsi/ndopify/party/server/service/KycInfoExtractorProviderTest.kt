package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorNull
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorSelfie
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.mock
import kotlin.test.Test

class KycInfoExtractorProviderTest {
    private val ai = mock(KycInfoExtractorIdentification::class.java)
    private val selfie = mock(KycInfoExtractorSelfie::class.java)
    private val none = mock(KycInfoExtractorNull::class.java)
    private val provider = KycInfoExtractorProvider(ai, selfie, none)

    @Test
    fun id() {
        // WHEN
        val extractor = provider.get(KycVerificationType.IDENTIFICATION)

        // THEN
        assertEquals(ai, extractor)
    }

    @Test
    fun selfie() {
        // WHEN
        val extractor = provider.get(KycVerificationType.SELFIE)

        // THEN
        assertEquals(selfie, extractor)
    }

    @Test
    fun paymentMethod() {
        // WHEN
        val extractor = provider.get(KycVerificationType.PAYMENT_METHOD)

        // THEN
        assertEquals(none, extractor)
    }
}
