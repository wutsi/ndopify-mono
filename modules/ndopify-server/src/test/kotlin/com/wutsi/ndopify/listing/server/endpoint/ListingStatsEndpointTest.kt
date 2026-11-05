package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.SearchListingStatsResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/ListingStatsVisits.sql"])
class ListingStatsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `interested clients - one row per id, in request order, zeros included`() {
        val stats = stats("/v1/listings/stats?listing-id=101&listing-id=104&listing-id=102&listing-id=101")

        assertEquals(listOf(101L, 104L, 102L), stats.map { it.listingId })
        assertEquals(listOf(2, 0, 1), stats.map { it.interested }) // 101: matches 1 & 5, 104: PROPOSED only, 102: match 2
    }

    @Test
    fun `visits - upcoming, completed, stale and closed`() {
        val stats = stats("/v1/listings/stats?listing-id=101&listing-id=102&listing-id=103")

        assertEquals(3, stats[0].upcomingVisits) // fresh request, future confirmation, checked in
        assertEquals(1, stats[0].completedVisits)
        assertEquals(0, stats[1].upcomingVisits)
        assertEquals(0, stats[1].completedVisits)
        assertEquals(0, stats[2].upcomingVisits)
        assertEquals(1, stats[2].completedVisits)
    }

    @Test
    fun `another agent's listing`() {
        val response = rest.getForEntity("/v1/listings/stats?listing-id=109", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `another tenant's listing`() {
        val response = rest.getForEntity("/v1/listings/stats?listing-id=901", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `another tenant's listing before another agent's`() {
        val response = rest.getForEntity("/v1/listings/stats?listing-id=109&listing-id=901", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no listing id`() {
        val response = rest.getForEntity("/v1/listings/stats", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/stats?listing-id=101", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun stats(url: String) =
        rest.getForEntity(url, SearchListingStatsResponse::class.java).body!!.stats
}
