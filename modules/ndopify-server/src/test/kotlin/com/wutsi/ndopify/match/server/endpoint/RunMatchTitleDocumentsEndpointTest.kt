package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.RunMatchResponse
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** #256: a land purchase is only matched with a land whose seller declared one of the required documents. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RunMatchTitleDocumentsEndpoint.sql"])
class RunMatchTitleDocumentsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `only the listing declaring a required document matches`() {
        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body!!.count)
        val matches = rest.getForEntity("/v1/matches?mandate-id=1", SearchMatchResponse::class.java).body!!.matches
        assertEquals(listOf(103L), matches.map { it.listingId })
    }

    @Test
    fun `a re-run deletes an ACTIVE match now ruled out`() {
        jdbc.update(
            "INSERT INTO T_MATCH(tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price) " +
                "VALUES (1, 1, 102, 20, 'ACTIVE', 0.9, 12000000)"
        )

        rest.postForEntity("/v1/matches/run?mandate-id=1", null, RunMatchResponse::class.java)

        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM T_MATCH WHERE mandate_id=1 AND listing_id=102",
                Int::class.java,
            ),
        )
    }
}
