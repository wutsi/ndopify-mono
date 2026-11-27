package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.GetOwnershipVerificationResponse
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The ownership verdict of a LISTING mandate: the assigned agent or an admin, never the customer nor the offered agent. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateOwnershipEndpoint.sql"])
class GetMandateOwnershipEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `the agent verifies the owner`() {
        val response = rest.getForEntity("/v1/mandates/100/ownership", GetOwnershipVerificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(OwnershipVerificationStatus.VERIFIED, response.body!!.verification.status)
        assertTrue(response.body!!.verification.reasons.isEmpty())
    }

    @Test
    fun `a lease is not ownership evidence`() {
        val verification = get(101)

        assertEquals(OwnershipVerificationStatus.PENDING, verification.status)
        assertEquals(
            listOf(OwnershipReasonCodes.DOCUMENT_MISSING),
            verification.reasons.map { reason -> reason.code },
        )
        assertTrue(verification.reasons.all { reason -> reason.details.isEmpty() })
    }

    @Test
    fun `an expired ID and a title naming someone else fail the owner`() {
        val verification = get(102)

        assertEquals(OwnershipVerificationStatus.FAILED, verification.status)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
            verification.reasons.map { reason -> reason.code },
        )
        assertTrue(verification.reasons.all { reason -> reason.details.isEmpty() })
    }

    @Test
    fun `another agent is denied`() {
        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.MANDATE_ACCESS_DENIED, "/v1/mandates/103/ownership")
    }

    @Test
    fun `an admin reads any listing mandate`() {
        admin = true

        assertEquals(OwnershipVerificationStatus.VERIFIED, get(103).status)
    }

    @Test
    fun `the agent the mandate is only offered to is denied`() {
        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.MANDATE_ACCESS_DENIED, "/v1/mandates/104/ownership")
    }

    @Test
    fun `the customer is denied`() {
        userId = 13L

        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.MANDATE_ACCESS_DENIED, "/v1/mandates/100/ownership")
    }

    @Test
    fun `a search mandate has no verdict`() {
        assertStatus(HttpStatus.BAD_REQUEST, ErrorCode.MANDATE_NOT_LISTING, "/v1/mandates/106/ownership")
    }

    @Test
    fun `the access check comes before the listing check`() {
        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.MANDATE_ACCESS_DENIED, "/v1/mandates/107/ownership")
    }

    @Test
    fun `a mandate of another tenant is not found`() {
        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, "/v1/mandates/200/ownership")
    }

    @Test
    fun `an unknown mandate is not found`() {
        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, "/v1/mandates/999/ownership")
    }

    @Test
    fun `anonymous is unauthorized`() {
        anonymousUser = true

        assertStatus(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "/v1/mandates/100/ownership")
    }

    @Test
    fun `the answer carries no document, no name and no path`() {
        val body = rest.getForEntity("/v1/mandates/102/ownership", String::class.java).body!!

        assertFalse(body.contains("Customer"), body)
        assertFalse(body.contains("Pierre"), body)
        assertFalse(body.contains("TF-"), body)
        assertFalse(body.contains("mandate/"), body)
        assertFalse(body.contains("1004"), body)
        assertFalse(body.contains("1005"), body)
    }

    @Test
    fun `the verdict is derived on every read`() {
        assertEquals(OwnershipVerificationStatus.VERIFIED, get(100).status)

        val identity = documentDao.findById(1000L).get()
        documentDao.save(identity.copy(status = DocumentStatus.UPLOADED))

        val verification = get(100)
        assertEquals(OwnershipVerificationStatus.PENDING, verification.status)
        assertTrue(verification.reasons.isEmpty())
    }

    private fun get(id: Long) =
        rest.getForEntity("/v1/mandates/$id/ownership", GetOwnershipVerificationResponse::class.java).body!!.verification

    private fun assertStatus(status: HttpStatus, code: String, url: String) {
        val response = rest.getForEntity(url, ErrorResponse::class.java)

        assertEquals(status, response.statusCode)
        assertEquals(code, response.body?.error?.code)
    }
}
