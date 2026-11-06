package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateStatsResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateStatsEndpoint.sql"])
class MandateStatsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `one row per id, in request order, zeros included`() {
        val stats = stats("/v1/mandates/stats?mandate-id=1&mandate-id=3&mandate-id=1")

        assertEquals(listOf(1L, 3L), stats.map { it.mandateId })
        assertEquals(listOf(4, 0), stats.map { it.properties })
        assertEquals(listOf(1, 0), stats.map { it.interested })
        assertEquals(listOf(3, 0), stats.map { it.upcomingVisits })
        assertEquals(listOf(1, 1), stats.map { it.completedVisits })
    }

    @Test
    fun `another agent's mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=2", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `another tenant's mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `gone mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no mandate id`() {
        val response = rest.getForEntity("/v1/mandates/stats", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun stats(url: String) =
        rest.getForEntity(url, SearchMandateStatsResponse::class.java).body!!.stats
}
