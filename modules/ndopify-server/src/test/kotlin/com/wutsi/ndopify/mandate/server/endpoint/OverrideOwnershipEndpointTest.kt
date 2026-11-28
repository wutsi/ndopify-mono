package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.GetOwnershipVerificationResponse
import com.wutsi.ndopify.mandate.dto.OverrideOwnershipVerificationRequest
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The admin's override of a LISTING mandate's owner verdict (#377): set and clear, backed by both kinds of document,
 * an `@OpsEndpoint` (off in prod) only an `admin-console` admin runs.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateOwnershipEndpoint.sql"])
class OverrideOwnershipEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private var admin = false
    private var app = ApplicationCode.ADMIN_CONSOLE

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    override val application: String
        get() = app

    @Test
    fun `an admin sets and clears the override`() {
        admin = true
        assertEquals(HttpStatus.NO_CONTENT, override(102, REASON).statusCode)

        val set = get(102)
        assertEquals(OwnershipVerificationStatus.VERIFIED, set.status)
        assertTrue(set.overridden)
        assertTrue(set.reasons.isEmpty())
        assertEquals(REASON, jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java))
        assertEquals(11L, jdbc.queryForObject("SELECT ownership_override_by FROM T_MANDATE WHERE id=102", Long::class.java))
        assertNotNull(jdbc.queryForObject("SELECT ownership_override_at FROM T_MANDATE WHERE id=102", Date::class.java))

        assertEquals(HttpStatus.NO_CONTENT, clear(102).statusCode)
        val cleared = get(102)
        assertEquals(OwnershipVerificationStatus.FAILED, cleared.status)
        assertFalse(cleared.overridden)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
            cleared.reasons.map { it.code },
        )
        assertNull(jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java))
        assertNull(jdbc.queryForObject("SELECT ownership_override_by FROM T_MANDATE WHERE id=102", Long::class.java))
        assertNull(jdbc.queryForObject("SELECT ownership_override_at FROM T_MANDATE WHERE id=102", Date::class.java))
    }

    @Test
    fun `setting again replaces the audit fields`() {
        admin = true
        assertEquals(HttpStatus.NO_CONTENT, override(102, REASON).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, override(102, "Autre motif").statusCode)

        assertEquals(
            "Autre motif",
            jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java),
        )
    }

    @Test
    fun `clearing without an override is a no-op`() {
        admin = true

        assertEquals(HttpStatus.NO_CONTENT, clear(102).statusCode)
        assertNull(jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java))
        assertEquals(OwnershipVerificationStatus.FAILED, get(102).status)
    }

    @Test
    fun `an agent is denied`() {
        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.HTTP_ACCESS_DENIED, override(102, REASON))
        assertNothingWritten(102)
    }

    @Test
    fun `a customer is denied`() {
        app = ApplicationCode.PUBLIC_PORTAL
        userId = 13L

        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.HTTP_ACCESS_DENIED, override(102, REASON))
        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.HTTP_ACCESS_DENIED, clear(102))
        assertNothingWritten(102)
    }

    @Test
    fun `an e-signature party is denied`() {
        app = ApplicationCode.E_SIGNATURE
        userId = 13L

        assertStatus(HttpStatus.FORBIDDEN, ErrorCode.HTTP_ACCESS_DENIED, override(102, REASON))
        assertNothingWritten(102)
    }

    @Test
    fun `anonymous is unauthorized`() {
        anonymousUser = true

        assertStatus(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, override(102, REASON))
        assertNothingWritten(102)
    }

    @Test
    fun `an unknown mandate is not found`() {
        admin = true

        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, override(999, REASON))
        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, clear(999))
    }

    @Test
    fun `a mandate of another tenant is not found`() {
        admin = true

        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, override(200, REASON))
        assertStatus(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND, clear(200))
    }

    @Test
    fun `a search mandate is refused`() {
        admin = true

        assertStatus(HttpStatus.BAD_REQUEST, ErrorCode.MANDATE_NOT_LISTING, override(106, REASON))
        assertStatus(HttpStatus.BAD_REQUEST, ErrorCode.MANDATE_NOT_LISTING, clear(106))
        assertNothingWritten(106)
    }

    @Test
    fun `a mandate without an ownership document is refused`() {
        admin = true

        assertStatus(HttpStatus.CONFLICT, ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS, override(101, REASON))
        assertNothingWritten(101)
    }

    @Test
    fun `a mandate without an identity document is refused`() {
        admin = true
        jdbc.update(
            """
            INSERT INTO T_MANDATE(
                id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, city_id, property_type,
                rooms, price, price_periodicity_type, currency_code, comments, created_at, modified_at
            ) VALUES
                (113, 1, 10, 20, 'ASSIGNED', 'LISTING', 'RENTAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF',
                 'Title only', '2024-06-10 00:00:00', '2024-06-10 00:00:00')
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO T_MANDATE_DOCUMENT(
                id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at,
                expires_at, version, created_at, modified_at
            ) VALUES
                (2000, 1, 113, 'LAND_TITLE', 'Titre foncier', 'UPLOADED', NULL, NULL, NULL, NULL, NULL, 0,
                 '2024-06-11 00:00:00', '2024-06-11 00:00:00')
            """.trimIndent(),
        )

        assertStatus(HttpStatus.CONFLICT, ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS, override(113, REASON))
        assertNothingWritten(113)
    }

    @Test
    fun `a blank reason is refused`() {
        admin = true

        assertStatus(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, override(102, " "))
        assertNothingWritten(102)
    }

    @Test
    fun `a reason over 500 characters is refused`() {
        admin = true

        assertStatus(HttpStatus.BAD_REQUEST, ErrorCode.HTTP_INVALID_PARAMETER, override(102, "x".repeat(501)))
        assertNothingWritten(102)
    }

    @Test
    fun `the verdict carries the flag and none of the audit fields`() {
        admin = true
        override(102, REASON)

        val body = rest.getForEntity("/v1/mandates/102/ownership", String::class.java).body!!

        assertTrue(body.contains("\"overridden\":true"), body)
        assertFalse(body.contains(REASON), body)
        assertFalse(body.contains("ownershipOverride"), body)
        assertFalse(body.contains("ownership_override"), body)
    }

    private fun override(id: Long, reason: String): ResponseEntity<ErrorResponse> =
        rest.postForEntity(
            "/v1/mandates/$id/ownership/override",
            OverrideOwnershipVerificationRequest(reason),
            ErrorResponse::class.java,
        )

    private fun clear(id: Long): ResponseEntity<ErrorResponse> =
        rest.postForEntity("/v1/mandates/$id/ownership/override/clear", null, ErrorResponse::class.java)

    private fun get(id: Long) =
        rest.getForEntity("/v1/mandates/$id/ownership", GetOwnershipVerificationResponse::class.java)
            .body!!.verification

    private fun assertStatus(status: HttpStatus, code: String, response: ResponseEntity<ErrorResponse>) {
        assertEquals(status, response.statusCode)
        assertEquals(code, response.body?.error?.code)
    }

    private fun assertNothingWritten(mandateId: Long) {
        assertNull(
            jdbc.queryForObject(
                "SELECT ownership_override_reason FROM T_MANDATE WHERE id=?",
                String::class.java,
                mandateId,
            ),
        )
        assertNull(
            jdbc.queryForObject(
                "SELECT ownership_override_by FROM T_MANDATE WHERE id=?",
                Long::class.java,
                mandateId,
            ),
        )
    }

    private companion object {
        const val REASON = "Titre au nom de son père, vérifié en agence"
    }
}
