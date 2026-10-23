package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.KycStatus
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * With `ndopify.endpoints.ops=false` (as in prod), `POST /v1/kyc/cases/{id}/verify` doesn't exist: the cases are
 * verified by `KycJobs` only.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/party/VerifyKycCaseEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class VerifyKycCaseOpsEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `not served`() {
        val response = rest.postForEntity("/v1/kyc/cases/kyc-case-submitted/verify", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
        val case = rest.getForEntity("/v1/kyc/cases/kyc-case-submitted", GetKycCaseResponse::class.java).body!!.case
        assertEquals(KycStatus.SUBMITTED, case.status)
    }

    @Test
    fun `the other endpoints are served`() {
        val response = rest.getForEntity("/v1/kyc/cases/kyc-case-submitted", GetKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
