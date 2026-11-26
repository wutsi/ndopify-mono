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
        "UPDATE T_MANDATE SET agent_id = 20, status = 'ASSIGNED' WHERE id IN (1, 2)",
        "UPDATE T_MANDATE SET agent_id = 20, status = 'PUBLISHED' WHERE id = 3",
        "UPDATE T_MANDATE SET agent_id = 30, status = 'PENDING_APPROVAL' WHERE id = 4",
        "UPDATE T_MANDATE SET agent_id = 90, status = 'ASSIGNED' WHERE id = 9",
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

    @Test
    fun `the conversation subject is written once`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute {
            dao.markConversationSubject(1, "Votre recherche : appartement à Yaounde · mandat 1")
        }
        val again = transactionTemplate.execute { dao.markConversationSubject(1, "autre sujet") }

        assertEquals(1, written)
        assertEquals(0, again)
        assertEquals(
            "Votre recherche : appartement à Yaounde · mandat 1",
            transactionTemplate.execute { dao.findConversationSubjectById(1) },
        )
    }

    @Test
    fun `writing the conversation subject does not bump the version`() {
        TenantContext.set(TENANT_ID)
        val before = transactionTemplate.execute { dao.findById(1).get().version }

        transactionTemplate.execute { dao.markConversationSubject(1, "Votre recherche · mandat 1") }

        assertEquals(before, transactionTemplate.execute { dao.findById(1).get().version })
    }

    @Test
    fun `never another tenant's mandate`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute { dao.markConversationSubject(9, "Votre recherche · mandat 9") }

        assertEquals(0, written)
        assertNull(transactionTemplate.execute { dao.findConversationSubjectById(9) })
    }

    @Test
    fun `the conversation message id is written once`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute {
            dao.markConversationMessageId(1, "0101-abc@email.amazonses.com")
        }
        val again = transactionTemplate.execute {
            dao.markConversationMessageId(1, "0101-def@email.amazonses.com")
        }

        assertEquals(1, written)
        assertEquals(0, again)
        assertEquals(
            "0101-abc@email.amazonses.com",
            transactionTemplate.execute { dao.findConversationMessageIdById(1) },
        )
    }

    @Test
    fun `writing the conversation message id does not bump the version`() {
        TenantContext.set(TENANT_ID)
        val before = transactionTemplate.execute { dao.findById(1).get().version }

        transactionTemplate.execute { dao.markConversationMessageId(1, "0101-abc@email.amazonses.com") }

        assertEquals(before, transactionTemplate.execute { dao.findById(1).get().version })
    }

    @Test
    fun `the conversation message id is never another tenant's`() {
        TenantContext.set(TENANT_ID)

        val written = transactionTemplate.execute {
            dao.markConversationMessageId(9, "0101-abc@email.amazonses.com")
        }

        assertEquals(0, written)
        assertNull(transactionTemplate.execute { dao.findConversationMessageIdById(9) })
    }
}
