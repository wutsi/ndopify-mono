package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.match.dto.CloseMatchRequest
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Sql(value = ["/db/test/clean.sql", "/db/test/match/CloseMatchEndpoint.sql"])
class CloseMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val request = CloseMatchRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

    @Test
    fun `closing the search on its match closes both mandates`() {
        val response = rest.postForEntity("/v1/matches/100/close", request, Any::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)

        listOf(1L, 6L).forEach { id ->
            val mandate = mandateDao.findById(id).get()
            assertEquals(MandateStatus.CLOSED, mandate.status)
            assertEquals(140000L, mandate.finalPrice)
            assertEquals(100L, mandate.dealMatchId)
            assertFalse(mandate.dealOutside)
        }
        assertEquals(
            "RENTED",
            jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=60", String::class.java),
        )
        assertEquals(
            "ACCEPTED",
            jdbc.queryForObject("SELECT status FROM T_MATCH WHERE id=100", String::class.java),
        )
    }

    @Test
    fun `closing the listing on its match closes both mandates`() {
        // The same operation: the match is the deal, whichever page sends it.
        rest.postForEntity("/v1/matches/100/close", request, Any::class.java)
        assertEquals(MandateStatus.CLOSED, mandateDao.findById(1L).get().status)
        assertEquals(MandateStatus.CLOSED, mandateDao.findById(6L).get().status)
    }
}
