package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.GetIdentityChangeResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/GetIdentityChangeRequestEndpoint.sql"])
class GetIdentityChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/identity-changes/1", GetIdentityChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = response.body!!.change
        assertEquals(1L, change.id)
        assertEquals(1L, change.agentId)
        assertEquals("Ray", change.oldFirstName)
        assertEquals("Sponsible", change.oldLastName)
        assertEquals("Ray", change.newFirstName)
        assertEquals("Sponsible", change.newLastName)
        assertEquals(IdentityType.NATIONAL_ID, change.identityType)
        assertEquals(listOf("https://example.com/page1.png", "https://example.com/page2.png"), change.imageUrls)
        assertEquals("Ray Sponsible", change.holderName)
        assertEquals(KycStatus.VERIFIED, change.status)
        assertEquals(null, change.errorCode)
        assertEquals(0, change.retries)
    }

    @Test
    fun `not found`() {
        val response = rest.getForEntity("/v1/identity-changes/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `identity change request belongs to another tenant`() {
        overrideTenantId = 999L

        val response = rest.getForEntity("/v1/identity-changes/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTITY_CHANGE_NOT_FOUND, response.body?.error?.code)
    }
}
