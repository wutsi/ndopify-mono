package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CloseMandateEndpoint.sql"])
class CloseMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val request = CloseMandateRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

    @Test
    fun close() {
        val response = rest.postForEntity("/v1/mandates/1/close", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.CLOSED, mandate.status)
        assertEquals(140000L, mandate.finalPrice)
        assertNotNull(mandate.closedAt)
        assertEquals(mandate.closedAt, mandate.modifiedAt)

        // Days from its approval (2024-06-10) to now
        val age = Duration.ofMillis(mandate.closedAt.time - mandate.approvedAt!!.time).toDays().toInt()
        assertEquals(age, mandate.age)
        assertNull(mandate.cancellationCode)
    }

    @Test
    fun `the published listing of a sale is sold`() {
        assertListingClosed(5L, 50L, "SOLD")
    }

    @Test
    fun `the published listing of a rental is rented`() {
        assertListingClosed(6L, 60L, "RENTED")
    }

    @Test
    fun `a listing not published is left as is`() {
        val response = rest.postForEntity("/v1/mandates/7/close", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val listing = jdbc.queryForMap("SELECT status, closed_at FROM T_LISTING WHERE id=70")
        assertEquals("DRAFT", listing["status"])
        assertNull(listing["closed_at"])
    }

    private fun assertListingClosed(mandateId: Long, listingId: Long, status: String) {
        val response = rest.postForEntity("/v1/mandates/$mandateId/close", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        // Read without the tenant filter: the test thread has no tenant
        val listing = jdbc.queryForMap("SELECT status, closed_at FROM T_LISTING WHERE id=$listingId")
        assertEquals(status, listing["status"])
        assertNotNull(listing["closed_at"])
    }

    @Test
    fun `mandate not approved`() {
        assertConflict(2L, ErrorCode.MANDATE_NOT_APPROVED, MandateStatus.ASSIGNED)
    }

    @Test
    fun `mandate already closed`() {
        assertConflict(3L, ErrorCode.MANDATE_ALREADY_CLOSED, MandateStatus.CLOSED)
    }

    @Test
    fun `mandate cancelled`() {
        assertConflict(4L, ErrorCode.MANDATE_ALREADY_CLOSED, MandateStatus.CANCELLED)
    }

    @Test
    fun `mandate not found`() {
        assertNotFound(999L)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        assertNotFound(9L)
    }

    private fun assertConflict(id: Long, code: String, status: MandateStatus) {
        val response = rest.postForEntity("/v1/mandates/$id/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)

        val mandate = dao.findById(id).get()
        assertEquals(status, mandate.status)
        assertNull(mandate.finalPrice)
        assertNull(mandate.age)
    }

    private fun assertNotFound(id: Long) {
        val response = rest.postForEntity("/v1/mandates/$id/close", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }
}
