package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.GetMobileChangeResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/GetMobileChangeRequestEndpoint.sql"])
class GetMobileChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/mobile-changes/1", GetMobileChangeResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val change = response.body!!.change
        assertEquals(1L, change.id)
        assertEquals(1L, change.agentId)
        assertEquals("+237600000000", change.oldMobileNumber)
        assertEquals("+237611111111", change.newMobileNumber)
        assertEquals(MoMoGatewayType.ORANGE, change.newGateway)
        assertEquals("Ray Sponsible", change.holderName)
        assertEquals(KycStatus.VERIFIED, change.status)
        assertEquals(null, change.errorCode)
        assertEquals(0, change.retries)
    }

    @Test
    fun `not found`() {
        val response = rest.getForEntity("/v1/mobile-changes/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mobile change request belongs to another tenant`() {
        overrideTenantId = 999L

        val response = rest.getForEntity("/v1/mobile-changes/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MOBILE_CHANGE_NOT_FOUND, response.body?.error?.code)
    }
}
