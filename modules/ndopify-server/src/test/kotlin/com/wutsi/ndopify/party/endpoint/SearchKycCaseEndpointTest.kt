package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchKycCaseEndpoint.sql"])
class SearchKycCaseEndpointTest : BaseEndpointIntegrationTest() {
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
        assertEquals("100", case.partyId)
        assertEquals("identification-100", case.identificationId)
        assertEquals(80, case.score)
        assertEquals(KycStatus.VERIFIED, case.status)
    }

    @Test
    fun `filter by status`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?statuses=PENDING",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val cases = response.body!!.cases
        assertEquals(2, cases.size)
        assertTrue(cases.map { it.id }.containsAll(listOf("kyc-case-100-a", "kyc-case-101-a")))
    }

    @Test
    fun `filter by multiple statuses`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?statuses=PENDING&statuses=VERIFIED",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3, response.body!!.cases.size)
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
    fun `returns empty list when party has no cases`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=999",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.cases.isEmpty())
    }
}
