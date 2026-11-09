package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
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
    private var admin = true

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val request = CloseMatchRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

    private val outsideRequest = CloseMandateRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

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

    @Test
    fun `the listing status is checked before the price`() {
        // Match 103's listing 70 is only DRAFT, so the listing side would be closed: 409 LISTING_NOT_PUBLISHED,
        // not the 400 of the invalid price, when both are wrong.
        val response = rest.postForEntity(
            "/v1/matches/103/close",
            CloseMatchRequest(finalPrice = Money(amount = 0, currency = "XAF")),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_PUBLISHED, response.body?.error?.code)
    }

    @Test
    fun `match not accepted`() {
        val response = rest.postForEntity("/v1/matches/101/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACCEPTED, response.body?.error?.code)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/999/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `listing not published`() {
        val response = rest.postForEntity("/v1/matches/103/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_PUBLISHED, response.body?.error?.code)
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(1L).get().status)
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(7L).get().status)
    }

    @Test
    fun `both sides already closed`() {
        rest.postForEntity("/v1/mandates/1/close", outsideRequest, Any::class.java)
        rest.postForEntity("/v1/mandates/6/close", outsideRequest, Any::class.java)

        val response = rest.postForEntity("/v1/matches/100/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
    }

    @Test
    fun `the listing side closed first`() {
        // Closing the listing outside first makes its listing 60 RENTED, but the search side is still open, so the
        // match closes it: 200, not 409 LISTING_NOT_PUBLISHED.
        rest.postForEntity("/v1/mandates/6/close", outsideRequest, Any::class.java)

        val response = rest.postForEntity("/v1/matches/100/close", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val search = mandateDao.findById(1L).get()
        assertEquals(MandateStatus.CLOSED, search.status)
        assertEquals(100L, search.dealMatchId)
        assertFalse(search.dealOutside)
        assertEquals(MandateStatus.CLOSED, mandateDao.findById(6L).get().status)
        assertEquals(
            "RENTED",
            jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=60", String::class.java),
        )
    }

    @Test
    fun `another agent's listing is only recorded`() {
        val response = rest.postForEntity("/v1/matches/102/close", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val search = mandateDao.findById(1L).get()
        assertEquals(MandateStatus.CLOSED, search.status)
        assertEquals(102L, search.dealMatchId)
        assertFalse(search.dealOutside)
        // The other agent's side is neither closed nor its listing touched
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(8L).get().status)
        assertEquals(
            "PUBLISHED",
            jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=80", String::class.java),
        )
    }

    @Test
    fun `zero price`() {
        val response = rest.postForEntity(
            "/v1/matches/100/close",
            CloseMatchRequest(finalPrice = Money(amount = 0, currency = "XAF")),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("finalPrice", response.body?.error?.parameter?.name)
    }

    @Test
    fun `not the agent`() {
        admin = false
        userId = 13L

        val response = rest.postForEntity("/v1/matches/100/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }
}
