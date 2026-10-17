package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.refdata.dto.Money
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CloseMandateEndpoint.sql"])
class CloseMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MandateRepository

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
        val age = Duration.ofMillis(mandate.closedAt!!.time - mandate.approvedAt!!.time).toDays().toInt()
        assertEquals(age, mandate.age)
        assertNull(mandate.cancellationCode)
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
