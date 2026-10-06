package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.service.KycVerifier
import com.wutsi.ndopify.party.server.service.KycVerifierProvider
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/VerifyKycCaseEndpoint.sql"])
class VerifyKycCaseEndpointTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    @MockitoBean
    private lateinit var verifierProvider: KycVerifierProvider

    private val verifier = mock<KycVerifier>()

    @BeforeEach
    fun stubVerifierProvider() {
        doReturn(verifier).whenever(verifierProvider).get(any())
    }

    @Test
    fun `identification verified - case and party verified`() {
        val response = rest.postForEntity(
            "/v1/kyc/cases/kyc-case-identification-verified/verify",
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        // Only the identification verification is considered for the overall score/status,
        // even though the (rejected) payment-method verification was also run.
        val case = dao.findById("kyc-case-identification-verified").orElseThrow()
        assertEquals(KycStatus.VERIFIED, case.status)
        assertEquals(95, case.score)

        val party = partyDao.findById(100L).orElseThrow()
        assertEquals(KycStatus.VERIFIED, party.kycStatus)

        verify(verifierProvider, times(2)).get(any())
        verify(verifier, times(2)).verify(any())
    }

    @Test
    fun `identification rejected - case and party rejected`() {
        val response = rest.postForEntity(
            "/v1/kyc/cases/kyc-case-identification-rejected/verify",
            null,
            Void::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        // Only the identification verification is considered, even though the (verified)
        // payment-method verification was also run.
        val case = dao.findById("kyc-case-identification-rejected").orElseThrow()
        assertEquals(KycStatus.REJECTED, case.status)
        assertEquals(50, case.score)

        val party = partyDao.findById(200L).orElseThrow()
        assertEquals(KycStatus.REJECTED, party.kycStatus)
    }

    @Test
    fun `case not pending - bad request`() {
        val response = rest.postForEntity(
            "/v1/kyc/cases/kyc-case-already-verified/verify",
            null,
            ErrorResponse::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)

        val case = dao.findById("kyc-case-already-verified").orElseThrow()
        assertEquals(KycStatus.VERIFIED, case.status)
        assertEquals(95, case.score)
    }

    @Test
    fun `case not found`() {
        val response = rest.postForEntity("/v1/kyc/cases/unknown-id/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        assertNull(response.body?.error?.data)
    }
}
