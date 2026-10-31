package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.RunMatchResponse
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The worked example of the plan: A, C and B match, in that order; D, E, agent 30's and the customer's own don't. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RunMatchEndpoint.sql"])
class RunMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var service: MatchService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun run() {
        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(3, response.body!!.count)

        val matches = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java).body!!.matches
        assertEquals(listOf(101L, 103L, 102L), matches.map { it.listingId }) // A, C, B
        assertEquals(0.95, matches[0].score, 0.005)
        assertEquals(0.745, matches[1].score, 0.005)
        assertEquals(0.58, matches[2].score, 0.01)

        // What explains them: B is in a nearby neighbourhood, A and C lack amenities searched
        assertEquals(0.0, matches[0].distanceKm)
        assertEquals(listOf("generatorSet"), matches[0].missingAmenities)
        assertEquals(listOf("waterTank", "generatorSet"), matches[1].missingAmenities)
        assertEquals(emptyList(), matches[2].missingAmenities)
        assertEquals(1.5, matches[2].distanceKm!!, 0.3)
    }

    @Test
    fun `run twice`() {
        rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)
        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(3, response.body!!.count)
        val matches = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java).body!!.matches
        assertEquals(3, matches.size)
    }

    @Test
    fun `re-score keeps notifiedAt`() {
        // A was matched, and the agent told, on a previous run
        jdbc.update(
            "INSERT INTO T_MATCH(tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, notified_at) " +
                "VALUES (1, 1, 101, 20, 'ACTIVE', 0.9, 240000, '2026-10-01 10:00:00')"
        )

        rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        // Read with SQL rather than the repository, whose queries are filtered by the tenant
        val notifiedAt = jdbc.queryForList(
            "SELECT listing_id, DATE_FORMAT(notified_at, '%Y-%m-%d %H:%i') AS notified_at FROM T_MATCH WHERE mandate_id=1"
        ).associate { row -> (row["listing_id"] as Number).toLong() to row["notified_at"] }
        assertEquals("2026-10-01 10:00", notifiedAt[101L]) // A: re-scored, the agent was already told
        assertNull(notifiedAt[102L]) // B and C: new
        assertNull(notifiedAt[103L])
    }

    @Test
    fun `mandate not found`() {
        val response = rest.postForEntity("/v1/matches/run?mandate-id=999", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    // As MatchEventListener calls it: on a thread without request (no open session), the tenant set
    @Test
    fun `outside a request`() {
        TenantContext.set(1L)
        try {
            assertEquals(3, service.matchMandate(1L))
            assertEquals(1, service.matchListing(101L))
        } finally {
            TenantContext.remove()
        }
    }
}
