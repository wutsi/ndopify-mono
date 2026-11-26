package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchKycCaseEndpoint.sql"])
class SearchKycCaseEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `filter by party`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=100",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val cases = response.body!!.cases
        assertEquals(2, cases.size)
        assertTrue(cases.map { it.id }.containsAll(listOf("kyc-case-100-a", "kyc-case-100-b")))

        val case = cases.first { it.id == "kyc-case-100-b" }
        assertEquals(100L, case.partyId)
        assertEquals(80, case.score)
        assertEquals(KycStatus.VERIFIED, case.status)
    }

    @Test
    fun `filter by status`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?statuses=PENDING_VERIFICATION",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val cases = response.body!!.cases
        assertEquals(1, cases.size)
        assertEquals("kyc-case-100-a", cases[0].id)
    }

    @Test
    fun `filter by multiple statuses`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?statuses=PENDING_VERIFICATION&statuses=VERIFIED",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, response.body!!.cases.size)
    }

    @Test
    fun `filter by party and status`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=100&statuses=VERIFIED",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val cases = response.body!!.cases
        assertEquals(1, cases.size)
        assertEquals("kyc-case-100-b", cases[0].id)
    }

    @Test
    fun `no match for filter`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?statuses=REJECTED",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.cases.isEmpty())
    }

    @Test
    fun `another partyId is ignored`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=999",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, response.body!!.cases.size)
        assertTrue(response.body!!.cases.all { it.partyId == 100L })
    }

    @Test
    fun `another party's cases are not returned`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=101",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(
            setOf("kyc-case-100-a", "kyc-case-100-b"),
            response.body!!.cases.map { it.id }.toSet(),
        )
    }

    @Test
    fun `admin filters by another party`() {
        admin = true
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=101",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("kyc-case-101-a"), response.body!!.cases.map { it.id })
    }

    @Test
    fun `admin sees every case`() {
        admin = true
        val response = rest.getForEntity(
            "/v1/kyc/cases?limit=10",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3, response.body!!.cases.size)
    }

    @Test
    fun `caller without a party is denied`() {
        userId = 12L
        val response = rest.getForEntity("/v1/kyc/cases", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `newest case first`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=100&limit=1",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("kyc-case-100-b"), response.body!!.cases.map { it.id })
    }

    @Test
    fun `limit and offset`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?limit=2&offset=1",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("kyc-case-100-a"), response.body!!.cases.map { it.id })
    }
}
