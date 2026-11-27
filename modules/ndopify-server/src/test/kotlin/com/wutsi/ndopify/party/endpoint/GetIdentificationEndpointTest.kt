package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/GetIdentificationEndpoint.sql"])
class GetIdentificationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun get() {
        val response = rest.getForEntity("/v1/identifications/id-100", GetIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val identification = response.body!!.identification
        assertEquals("id-100", identification.id)
        assertEquals(100L, identification.partyId)
        assertEquals(IdentificationType.NATIONAL_ID, identification.type)
        assertEquals("CM", identification.issuingCountryCode)
        assertEquals("7890", identification.numberSuffix)
        assertEquals(IdentificationStatus.PENDING_VERIFICATION, identification.status)
    }

    @Test
    fun `another party - forbidden`() {
        userId = 12L
        val response = rest.getForEntity("/v1/identifications/id-100", ErrorResponse::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `admin reads another party's identification`() {
        admin = true
        assertEquals(HttpStatus.OK, rest.getForEntity("/v1/identifications/id-101", GetIdentificationResponse::class.java).statusCode)
    }

    @Test
    fun `caller without a party - forbidden`() {
        userId = 13L
        assertEquals(HttpStatus.FORBIDDEN, rest.getForEntity("/v1/identifications/id-100", ErrorResponse::class.java).statusCode)
    }

    @Test
    fun `identification not found`() {
        val response = rest.getForEntity("/v1/identifications/unknown-id", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `identification from another tenant is not found`() {
        val response = rest.getForEntity("/v1/identifications/id-900", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }
}
