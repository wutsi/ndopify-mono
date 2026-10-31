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
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The worked example of the plan: A, C and B match, in that order; D, E, agent 30's and the customer's own don't. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RunMatchEndpoint.sql"])
class RunMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var service: MatchService

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
