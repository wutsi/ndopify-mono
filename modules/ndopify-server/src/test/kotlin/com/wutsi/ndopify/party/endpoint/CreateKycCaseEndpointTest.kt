package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/CreateKycCaseEndpoint.sql"])
class CreateKycCaseEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: KycCaseRepository

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `with payment method`() {
        val request = CreateKycCaseRequest(
            partyId = 100L,
            identificationType = IdentificationType.RESIDENT_CARD
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, CreateKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val caseId = response.body?.caseId
        val case = dao.findById(caseId!!).orElseThrow()
        assertEquals(100L, case.party.id)
        assertEquals(KycStatus.PENDING_VERIFICATION, case.status)
        assertEquals(IdentificationType.RESIDENT_CARD, case.identificationType)
        assertNull(case.score)
        assertNull(case.errorCode)
    }

    @Test
    fun `another party - forbidden`() {
        val request = CreateKycCaseRequest(
            partyId = 200L,
            identificationType = IdentificationType.RESIDENT_CARD,
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(0L, dao.count())
    }

    @Test
    fun `admin creates for another party`() {
        admin = true
        val request = CreateKycCaseRequest(
            partyId = 200L,
            identificationType = IdentificationType.RESIDENT_CARD,
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, CreateKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(200L, dao.findById(response.body!!.caseId).orElseThrow().party.id)
    }

    @Test
    fun `caller without a party - forbidden`() {
        userId = 12L
        val request = CreateKycCaseRequest(partyId = 100L)

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(0L, dao.count())
    }

    @Test
    fun `party not found`() {
        val request = CreateKycCaseRequest(
            partyId = 999L,
        )

        val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
