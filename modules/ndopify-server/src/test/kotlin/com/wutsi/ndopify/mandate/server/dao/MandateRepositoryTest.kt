package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"],
    statements = [
        // status: 2=ASSIGNED, 3=PENDING_APPROVAL, 5=PUBLISHED
        "UPDATE T_MANDATE SET agent_id = 20, status = 2 WHERE id IN (1, 2)",
        "UPDATE T_MANDATE SET agent_id = 20, status = 5 WHERE id = 3",
        "UPDATE T_MANDATE SET agent_id = 30, status = 3 WHERE id = 4",
        "UPDATE T_MANDATE SET agent_id = 90, status = 2 WHERE id = 9",
    ],
)
class MandateRepositoryTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @AfterTest
    fun cleanup() {
        TenantContext.remove()
    }

    @Test
    fun `count by agent ids and status - only the statuses asked`() {
        TenantContext.set(TENANT_ID)

        val counts = dao.countByAgentIdsAndStatusIn(
            listOf(20L, 30L),
            listOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL),
        ).associate { it.agentId to it.total }

        // Agent 20's PUBLISHED mandate isn't counted
        assertEquals(mapOf(20L to 2L, 30L to 1L), counts)
    }

    @Test
    fun `count by agent ids and status - never another tenant's`() {
        TenantContext.set(TENANT_ID)

        val counts = dao.countByAgentIdsAndStatusIn(listOf(90L), listOf(MandateStatus.ASSIGNED))

        assertEquals(emptyList(), counts)
    }

    @Test
    fun `find by id for update`() {
        TenantContext.set(TENANT_ID)

        // The lock needs a transaction
        val mandate = transactionTemplate.execute { dao.findByIdForUpdate(1) }

        assertEquals(1L, mandate?.id)
    }

    @Test
    fun `find by id for update - another tenant's is not found`() {
        TenantContext.set(TENANT_ID)

        assertNull(transactionTemplate.execute { dao.findByIdForUpdate(9) })
    }
}
