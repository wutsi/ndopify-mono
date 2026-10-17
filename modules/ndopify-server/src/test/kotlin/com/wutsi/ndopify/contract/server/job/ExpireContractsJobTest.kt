package com.wutsi.ndopify.contract.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.event.ContractExpiredEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@RecordApplicationEvents
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/ExpireContractsJob.sql"])
class ExpireContractsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ContractJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var events: ApplicationEvents

    @Test
    fun expire() {
        jobs.expire()

        assertEquals(
            mapOf(
                "c-1" to ContractStatus.EXPIRED,
                "c-2" to ContractStatus.EXPIRED,
                "c-3" to ContractStatus.PENDING_SIGNATURE,
                "c-4" to ContractStatus.DRAFT,
                "c-5" to ContractStatus.SIGNED,
                "c-6" to ContractStatus.EXPIRED,
                "c-7" to ContractStatus.CANCELLED,
                "c-21" to ContractStatus.EXPIRED,
                "c-31" to ContractStatus.PENDING_SIGNATURE, // Inactive tenant
            ),
            statuses(),
        )

        assertEquals(
            listOf(
                ContractExpiredEvent(contractId = "c-1", tenantId = 1L),
                ContractExpiredEvent(contractId = "c-2", tenantId = 1L),
                ContractExpiredEvent(contractId = "c-21", tenantId = 2L),
            ),
            events.stream(ContractExpiredEvent::class.java).toList().sortedBy { event -> event.contractId },
        )
    }

    @Test
    fun `nothing left to expire on the next run`() {
        jobs.expire()
        events.clear()

        jobs.expire()

        assertEquals(0, events.stream(ContractExpiredEvent::class.java).count())
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun statuses(): Map<String, ContractStatus> =
        jdbc.queryForList("SELECT id, status FROM T_CONTRACT").associate { row ->
            row["id"] as String to ContractStatus.valueOf(row["status"] as String)
        }
}
