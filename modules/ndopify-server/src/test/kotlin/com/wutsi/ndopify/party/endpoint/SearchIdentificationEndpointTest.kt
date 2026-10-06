package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.SearchIdentificationResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchIdentificationEndpoint.sql"])
class SearchIdentificationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun search() {
        val response = rest.getForEntity(
            "/v1/identifications?partyId=100",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(2, identifications.size)
        assertTrue(identifications.map { it.id }.containsAll(listOf("id-100-a", "id-100-b")))

        val national = identifications.first { it.id == "id-100-a" }
        assertEquals(100L, national.partyId)
        assertEquals(IdentificationType.NATIONAL_ID, national.type)
        assertEquals("CM", national.issuingCountryCode)
        assertEquals("7890", national.numberSuffix)
        assertEquals(IdentificationStatus.PENDING_VERIFICATION, national.status)

        val passport = identifications.first { it.id == "id-100-b" }
        assertEquals(IdentificationType.PASSPORT, passport.type)
        assertEquals(IdentificationStatus.VERIFIED, passport.status)
    }

    @Test
    fun `only returns identifications for the given party`() {
        val response = rest.getForEntity(
            "/v1/identifications?partyId=101",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(1, identifications.size)
        assertEquals("id-101-a", identifications[0].id)
    }

    @Test
    fun `returns empty list when party has no identifications`() {
        val response = rest.getForEntity(
            "/v1/identifications?partyId=999",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.identifications.isEmpty())
    }

    @Test
    fun `filter by type`() {
        val response = rest.getForEntity(
            "/v1/identifications?types=PASSPORT",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(1, identifications.size)
        assertEquals("id-100-b", identifications[0].id)
    }

    @Test
    fun `filter by multiple types`() {
        val response = rest.getForEntity(
            "/v1/identifications?types=NATIONAL_ID&types=PASSPORT",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(3, identifications.size)
        assertTrue(identifications.map { it.id }.containsAll(listOf("id-100-a", "id-100-b", "id-101-a")))
    }

    @Test
    fun `filter by status`() {
        val response = rest.getForEntity(
            "/v1/identifications?statuses=VERIFIED",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(1, identifications.size)
        assertEquals("id-100-b", identifications[0].id)
    }

    @Test
    fun `filter by party, type and status`() {
        val response = rest.getForEntity(
            "/v1/identifications?partyId=100&types=NATIONAL_ID&statuses=PENDING_VERIFICATION",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val identifications = response.body!!.identifications
        assertEquals(1, identifications.size)
        assertEquals("id-100-a", identifications[0].id)
    }

    @Test
    fun `no match for filter`() {
        val response = rest.getForEntity(
            "/v1/identifications?types=DRIVER_LICENSE",
            SearchIdentificationResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.identifications.isEmpty())
    }
}
