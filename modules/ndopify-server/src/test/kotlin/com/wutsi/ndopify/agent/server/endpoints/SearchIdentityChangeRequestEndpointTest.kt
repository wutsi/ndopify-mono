package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.SearchIdentityChangeResponse
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/SearchIdentityChangeRequestEndpoint.sql"])
class SearchIdentityChangeRequestEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun all() {
        val response = rest.getForEntity("/v1/identity-changes", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(3, changes.size)
        assertEquals(listOf(1L, 2L, 3L), changes.map { it.id })
    }

    @Test
    fun `by ids`() {
        val response = rest.getForEntity("/v1/identity-changes?ids=1&ids=3", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `by agent id`() {
        val response = rest.getForEntity("/v1/identity-changes?agentId=1", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `by status`() {
        val response = rest.getForEntity("/v1/identity-changes?status=PENDING", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertTrue(changes.map { it.id }.containsAll(listOf(1L, 3L)))
    }

    @Test
    fun `no match`() {
        val response = rest.getForEntity("/v1/identity-changes?agentId=999", SearchIdentityChangeResponse::class.java)

        assertEquals(0, response.body!!.changes.size)
    }

    @Test
    fun limit() {
        val response = rest.getForEntity("/v1/identity-changes?limit=2", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertEquals(listOf(1L, 2L), changes.map { it.id })
    }

    @Test
    fun offset() {
        val response = rest.getForEntity("/v1/identity-changes?offset=1", SearchIdentityChangeResponse::class.java)

        val changes = response.body!!.changes
        assertEquals(2, changes.size)
        assertEquals(listOf(2L, 3L), changes.map { it.id })
    }

    @Test
    fun `other tenant sees no identity change requests`() {
        overrideTenantId = 999L

        val response = rest.getForEntity("/v1/identity-changes", SearchIdentityChangeResponse::class.java)

        assertEquals(0, response.body!!.changes.size)
    }
}
