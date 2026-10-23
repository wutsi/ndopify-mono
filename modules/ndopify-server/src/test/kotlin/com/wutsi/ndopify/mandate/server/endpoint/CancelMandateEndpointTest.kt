package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CancelMandateEndpoint.sql"])
class CancelMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var assignmentDao: AssignmentRepository

    @Autowired
    private lateinit var contractDao: ContractRepository

    private val request = CancelMandateRequest(
        cancellationCode = MandateCancellationCode.FOUND_INDEPENDENTLY,
        cancellationReason = "J'ai trouvé un appartement par un ami",
    )

    @Test
    fun cancel() {
        val response = rest.postForEntity("/v1/mandates/2/cancel", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(2L).get()
        assertEquals(MandateStatus.CANCELLED, mandate.status)
        assertEquals(MandateCancellationCode.FOUND_INDEPENDENTLY, mandate.cancellationCode)
        assertEquals("J'ai trouvé un appartement par un ami", mandate.cancellationReason)
        assertNotNull(mandate.closedAt)
        assertNull(mandate.age)
        assertNull(mandate.finalPrice)

        assertEquals(AssignmentStatus.ACCEPTED, assignmentDao.findById(2L).get().status)
        assertEquals(ContractStatus.CANCELLED, contractDao.findById("c-2").get().status)
    }

    @Test
    fun `the draft contract is cancelled`() {
        assertContractStatus(5L, "c-5", ContractStatus.CANCELLED)
    }

    @Test
    fun `the contract waiting for signature is cancelled`() {
        assertContractStatus(6L, "c-6", ContractStatus.CANCELLED)
    }

    @Test
    fun `the expired contract stays expired`() {
        assertContractStatus(7L, "c-7", ContractStatus.EXPIRED)
    }

    @Test
    fun `the pending offer is expired`() {
        val response = rest.postForEntity("/v1/mandates/1/cancel", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.CANCELLED, dao.findById(1L).get().status)
        assertEquals(AssignmentStatus.EXPIRED, assignmentDao.findById(1L).get().status)
    }

    @Test
    fun `without a code`() {
        val response = rest.postForEntity("/v1/mandates/2/cancel", CancelMandateRequest(), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(2L).get()
        assertEquals(MandateStatus.CANCELLED, mandate.status)
        assertEquals(MandateCancellationCode.OTHER, mandate.cancellationCode)
        assertNull(mandate.cancellationReason)
    }

    @Test
    fun `mandate already closed`() {
        assertConflict(3L, MandateStatus.CLOSED)
    }

    @Test
    fun `mandate already cancelled`() {
        assertConflict(4L, MandateStatus.CANCELLED)
    }

    @Test
    fun `mandate not found`() {
        assertNotFound(999L)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        assertNotFound(9L)
    }

    private fun assertContractStatus(id: Long, contractId: String, status: ContractStatus) {
        val response = rest.postForEntity("/v1/mandates/$id/cancel", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.CANCELLED, dao.findById(id).get().status)
        assertEquals(status, contractDao.findById(contractId).get().status)
    }

    private fun assertConflict(id: Long, status: MandateStatus) {
        val response = rest.postForEntity("/v1/mandates/$id/cancel", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)

        val mandate = dao.findById(id).get()
        assertEquals(status, mandate.status)
        assertNull(mandate.cancellationReason)
    }

    private fun assertNotFound(id: Long) {
        val response = rest.postForEntity("/v1/mandates/$id/cancel", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }
}
