package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/SearchListingEndpoint.sql"])
class SearchListingEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // Every status: see ListingAccessEndpointTest for the others

    private fun search(query: String = ""): List<Long> {
        val response = rest.getForEntity("/v1/listings?$query", SearchListingResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.listings.map { listing -> listing.id }
    }

    @Test
    fun `all - newest first, of the tenant only`() {
        assertEquals(listOf(102L, 101L, 100L), search())
    }

    @Test
    fun `public - no token needed, PUBLISHED only`() {
        anonymousUser = true

        assertEquals(listOf(102L, 100L), search())
    }

    @Test
    fun `public - the statuses asked for are ignored`() {
        anonymousUser = true

        assertEquals(listOf(102L, 100L), search("status=DRAFT"))
        assertEquals(emptyList(), search("property-type=HOUSE&status=DRAFT"))
    }

    @Test
    fun summary() {
        val response = rest.getForEntity("/v1/listings?mandate-id=1", SearchListingResponse::class.java)

        val listings = response.body!!.listings
        assertEquals(1, listings.size)
        assertEquals(100L, listings[0].id)
        assertEquals(1L, listings[0].mandateId)
        assertEquals(20L, listings[0].agentId)
        assertEquals(ListingStatus.PUBLISHED, listings[0].status)
        assertEquals(PropertyType.APARTMENT, listings[0].propertyType)
        assertEquals(111L, listings[0].neighborhoodId)
        assertEquals(2, listings[0].rooms)
        assertEquals(150000L, listings[0].price)
        assertEquals("Nice apartment", listings[0].title)
    }

    @Test
    fun `by mandates`() {
        assertEquals(listOf(100L), search("mandate-id=1"))
        assertEquals(listOf(102L, 100L), search("mandate-id=1&mandate-id=3"))
        assertEquals(emptyList(), search("mandate-id=9"))
    }

    @Test
    fun `by agent`() {
        assertEquals(listOf(102L, 101L, 100L), search("agent-id=20"))
        assertEquals(emptyList(), search("agent-id=90"))
    }

    @Test
    fun `by status`() {
        assertEquals(listOf(102L, 100L), search("status=PUBLISHED"))
        assertEquals(listOf(102L, 101L, 100L), search("status=PUBLISHED&status=DRAFT"))
    }

    @Test
    fun `by listing type and category`() {
        assertEquals(listOf(102L), search("listing-type=SALE"))
        assertEquals(listOf(102L, 101L, 100L), search("property-category=RESIDENTIAL"))
    }

    @Test
    fun `by property type`() {
        assertEquals(listOf(101L), search("property-type=HOUSE"))
        assertEquals(listOf(102L, 101L, 100L), search("property-type=HOUSE&property-type=APARTMENT"))
    }

    @Test
    fun `by location`() {
        assertEquals(listOf(101L, 100L), search("city-id=2370201"))
        assertEquals(listOf(100L), search("city-id=2370201&neighborhood-id=111"))
    }

    @Test
    fun `by furniture type`() {
        assertEquals(listOf(100L), search("furniture-type=FULLY_FURNISHED"))
    }

    @Test
    fun `by price`() {
        assertEquals(listOf(102L, 101L), search("min-price=200000"))
        assertEquals(listOf(100L), search("max-price=200000"))
        assertEquals(listOf(101L), search("min-price=200000&max-price=500000"))
    }

    @Test
    fun `by availability - available by the date, or without a date`() {
        assertEquals(listOf(101L, 100L), search("available-from=2026-12-01"))
        assertEquals(listOf(101L), search("available-from=2026-10-01"))
    }

    @Test
    fun `by rooms`() {
        assertEquals(listOf(100L), search("rooms=2"))
        // As a URI variable, so that the + is encoded (%2B), not read as a space
        val response = rest.getForEntity("/v1/listings?rooms={rooms}", SearchListingResponse::class.java, "3+")
        assertEquals(listOf(102L, 101L), response.body!!.listings.map { listing -> listing.id })
        assertEquals(listOf(102L, 101L, 100L), search("rooms=invalid"))
    }

    @Test
    fun paging() {
        assertEquals(listOf(102L, 101L), search("limit=2"))
        assertEquals(listOf(100L), search("limit=2&offset=2"))
    }
}
