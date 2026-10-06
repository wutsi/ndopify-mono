package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Clock
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class KycVerifierManualTest {
    private val dao = mock<KycVerificationRepository>()
    private val clock = mock<Clock>()
    private val verifier = KycVerifierManual(dao, clock)

    @Test
    fun verify() {
        val now = 1_000_000L
        doReturn(now).whenever(clock).millis()

        val verification = KycVerificationEntity(
            id = "verification-100",
            status = KycStatus.PENDING,
        )

        val result = verifier.verify(verification)

        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, result.status)
        assertEquals(Date(now), result.modifiedAt)
        verify(dao).save(result)
    }
}
