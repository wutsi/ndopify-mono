package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.SearchMobileChangeResponse
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/SearchMobileChangeRequestEndpoint.sql"])
class SearchMobileChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun all() {
        val response = rest.getForEntity("/v1/mobile-change-requests", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(3, changes.size)
        assertEquals(listOf(1L, 2L, 3L), changes.map { it.id })
    }

    @Test
    fun `by ids`() {
        val response = rest.getForEntity("/v1/mobile-change-requests?ids=1&ids=3", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `by agent id`() {
        val response = rest.getForEntity("/v1/mobile-change-requests?agentId=1", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `by status`() {
        val response = rest.getForEntity("/v1/mobile-change-requests?status=PENDING", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/mobile-change-requests?agentId=999", SearchMobileChangeResponse::class.java)

        assertEquals(0, response.body!!.changes.size)
    }

    @Test
    fun limit() {
        val response = rest.getForEntity("/v1/mobile-change-requests?limit=2", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertEquals(listOf(1L, 2L), changes.map { it.id })
    }

    @Test
    fun offset() {
        val response = rest.getForEntity("/v1/mobile-change-requests?offset=1", SearchMobileChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertEquals(listOf(2L, 3L), changes.map { it.id })
    }

    @Test
    fun `other tenant sees no mobile change requests`() {
        overrideTenantId = 999L

        val response = rest.getForEntity("/v1/mobile-change-requests", SearchMobileChangeResponse::class.java)

        assertEquals(0, response.body!!.changes.size)
    }
}
