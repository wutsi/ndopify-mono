package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/CoBrokerageMatchEndpoint.sql"])
class CoBrokerageMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `falls back to another agent's listing`() {
        run(1)
        assertEquals(
            mapOf(101L to true),
            coBrokerageByListing(1L), // 103: the customer's own   104: another currency   105: not published
        )
    }

    @Test
    fun `own inventory wins`() {
        run(2)
        assertEquals(mapOf(102L to false), coBrokerageByListing(2L)) // 101 also matches, but is not offered
    }

    @Test
    fun `keeps the co-brokerage match when own inventory appears`() {
        run(3)
        assertEquals(mapOf(102L to false, 101L to true), coBrokerageByListing(3L))

        val matches = rest.getForEntity("/v1/matches?mandate-id=3", SearchMatchResponse::class.java).body!!.matches
        assertEquals(listOf(102L, 101L), matches.map { it.listingId }) // Own first
    }

    @Test
    fun `a dismissed own match does not block the fallback`() {
        run(4)
        assertEquals(MatchStatus.DISMISSED, status(mandateId = 4L, listingId = 102L))
        assertEquals(mapOf(102L to false, 101L to true), coBrokerageByListing(4L))
    }

    @Test
    fun `a rejected own match does not block the fallback`() {
        run(5)
        assertEquals(MatchStatus.REJECTED, status(mandateId = 5L, listingId = 102L))
        assertTrue(coBrokerageByListing(5L).getValue(101L)!!)
    }

    @Test
    fun `a co-brokerage match no longer qualifying is kept`() {
        run(6)
        assertEquals(MatchStatus.ACTIVE, status(mandateId = 6L, listingId = 104L))
        assertTrue(coBrokerageByListing(6L).getValue(101L)!!)
    }

    @Test
    fun `a co-brokerage match of a rented listing is not listed`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=7", SearchMatchResponse::class.java).body!!.matches
        assertTrue(matches.isEmpty())
    }

    private fun run(mandateId: Long) {
        rest.postForEntity("/v1/matches/run?mandate-id=$mandateId", null, String::class.java)
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun coBrokerageByListing(mandateId: Long): Map<Long, Boolean> =
        jdbc.queryForList("SELECT listing_id, co_brokerage FROM T_MATCH WHERE mandate_id=?", mandateId).associate { row ->
            (row["listing_id"] as Number).toLong() to asBoolean(row["co_brokerage"])
        }

    private fun status(mandateId: Long, listingId: Long): MatchStatus =
        jdbc.queryForList(
            "SELECT status FROM T_MATCH WHERE mandate_id=? AND listing_id=?",
            mandateId,
            listingId,
        ).single().let { MatchStatus.valueOf(it["status"] as String) }

    private fun asBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> false
    }
}
