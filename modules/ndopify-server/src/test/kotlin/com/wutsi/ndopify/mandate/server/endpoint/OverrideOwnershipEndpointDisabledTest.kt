package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.GetOwnershipVerificationResponse
import com.wutsi.ndopify.mandate.dto.OverrideOwnershipVerificationRequest
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** With `ndopify.endpoints.ops=false` (as in prod), the override routes don't exist, admin or not. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateOwnershipEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class OverrideOwnershipEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `setting an override is not served`() {
        val response = rest.postForEntity(
            "/v1/mandates/102/ownership/override",
            OverrideOwnershipVerificationRequest("Titre au nom de son père, vérifié en agence"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `clearing an override is not served`() {
        val response = rest.postForEntity(
            "/v1/mandates/102/ownership/override/clear",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `nothing is written and the verdict is still read`() {
        rest.postForEntity(
            "/v1/mandates/102/ownership/override",
            OverrideOwnershipVerificationRequest("Titre au nom de son père, vérifié en agence"),
            ErrorResponse::class.java,
        )

        assertNull(jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java))
        val response = rest.getForEntity("/v1/mandates/102/ownership", GetOwnershipVerificationResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
