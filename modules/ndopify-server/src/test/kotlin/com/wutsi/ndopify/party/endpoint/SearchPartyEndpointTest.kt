package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchPartyResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/SearchPartyEndpoint.sql"])
class SearchPartyEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun search() {
        val response = rest.getForEntity("/v1/parties", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val parties = response.body!!.parties
        assertEquals(listOf(100L, 101L, 102L), parties.map { it.id })

        val ray = parties[0]
        assertEquals("Ray", ray.firstName)
        assertEquals("Sponsible", ray.lastName)
        assertEquals("ray.sponsible@gmail.com", ray.email)
        assertEquals(KycStatus.PENDING, ray.kycStatus)
        assertEquals("https://img.com/ray.png", ray.photoUrl)

        assertNull(parties[1].photoUrl)
    }

    @Test
    fun `filter by id`() {
        val response = rest.getForEntity("/v1/parties?id=100&id=102", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(100L, 102L), response.body!!.parties.map { it.id })
    }

    @Test
    fun `filter by email is case-insensitive`() {
        val response = rest.getForEntity("/v1/parties?email=007@GMAIL.com", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(101L), response.body!!.parties.map { it.id })
    }

    @Test
    fun `filter by id and email`() {
        val response = rest.getForEntity(
            "/v1/parties?id=100&id=101&email=007@gmail.com",
            SearchPartyResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(101L), response.body!!.parties.map { it.id })
    }

    @Test
    fun pagination() {
        val response = rest.getForEntity("/v1/parties?limit=1&offset=1", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(101L), response.body!!.parties.map { it.id })
    }

    @Test
    fun `parties of other tenants are not returned`() {
        val response = rest.getForEntity("/v1/parties?id=200", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.parties.isEmpty())
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/parties?email=nobody@gmail.com", SearchPartyResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.parties.isEmpty())
    }
}
