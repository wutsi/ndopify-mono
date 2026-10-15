package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The parts of `AgentService` used by the mandate dispatch. */
@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"],
    statements = [
        "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES " +
            "(400, 1, 'a40@x.com', 'A', 'Forty'), (500, 1, 'a50@x.com', 'A', 'Fifty')",
        "INSERT INTO T_AGENT(id, tenant_id, party_id, city_id, last_assigned_at) VALUES " +
            "(40, 1, 400, 2370201, '2024-06-01 00:00:00'), (50, 1, 500, 2370201, '2024-05-01 00:00:00')",
        "UPDATE T_AGENT SET last_assigned_at = '2024-07-01 00:00:00' WHERE id = 20",
    ],
)
class AgentServiceDispatchTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: AgentService

    @AfterTest
    fun cleanup() {
        TenantContext.remove()
    }

    @Test
    fun `search - least recently assigned first, never assigned first of all`() {
        TenantContext.set(TENANT_ID)

        val agents = service.search(SearchAgentRequest(cityId = 2370201), AgentService.SORT_LEAST_RECENTLY_ASSIGNED)

        // 30: never assigned, 50: May, 40: June, 20: July
        assertEquals(listOf(30L, 50L, 40L, 20L), agents.map { it.id })
    }

    @Test
    fun `search - by id by default`() {
        TenantContext.set(TENANT_ID)

        val agents = service.search(SearchAgentRequest(cityId = 2370201))

        assertEquals(listOf(20L, 30L, 40L, 50L), agents.map { it.id })
    }

    @Test
    fun `mark assigned`() {
        TenantContext.set(TENANT_ID)
        val date = Date(1_800_000_000_000)

        service.markAssigned(service.findById(30), date)

        val agent = service.findById(30)
        assertEquals(date.time, agent.lastAssignedAt?.time)
        assertEquals(date.time, agent.modifiedAt.time)
    }
}
