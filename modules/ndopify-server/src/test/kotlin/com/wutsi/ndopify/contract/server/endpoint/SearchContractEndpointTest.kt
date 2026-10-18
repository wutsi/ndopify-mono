package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/SearchContractEndpoint.sql"])
class SearchContractEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun all() {
        val contracts = search("")
        assertEquals(listOf("c-3", "c-2", "c-1"), contracts.map { it.id }) // Newest first

        val contract = contracts[1]
        assertEquals("MNDT-2024-000002", contract.number)
        assertEquals(ContractType.SEARCH, contract.type)
        assertEquals(ContractStatus.SIGNED, contract.status)
        assertEquals(12, contract.durationMonths)
        assertNotNull(contract.signatureDeadline)
        assertNotNull(contract.validFrom)
        assertNotNull(contract.validUntil)
        assertNotNull(contract.signedAt)
        assertNull(contracts[0].signatureDeadline)
    }

    @Test
    fun `by ids`() {
        assertEquals(listOf("c-3", "c-1"), search("id=c-1&id=c-3").map { it.id })
    }

    @Test
    fun `by statuses`() {
        assertEquals(listOf("c-3", "c-1"), search("status=DRAFT&status=PENDING_SIGNATURE").map { it.id })
    }

    @Test
    fun `by party ids`() {
        assertEquals(listOf("c-2", "c-1"), search("party-id=100&party-id=101").map { it.id })
    }

    @Test
    fun `by party id - a party twice in a contract returns it once`() {
        assertEquals(listOf("c-1"), search("party-id=100").map { it.id })
    }

    @Test
    fun `combined filters`() {
        assertEquals(listOf("c-1"), search("id=c-1&id=c-2&status=PENDING_SIGNATURE&party-id=100").map { it.id })
    }

    @Test
    fun `no match`() {
        assertTrue(search("id=xxx").isEmpty())
    }

    @Test
    fun `limit and offset`() {
        assertEquals(listOf("c-2"), search("limit=1&offset=1").map { it.id })
    }

    @Test
    fun `contract from another tenant is not returned`() {
        assertTrue(search("id=c-9").isEmpty())
        assertTrue(search("party-id=900").isEmpty())
    }

    @Test
    fun `switching tenant context reveals only that tenant's contract`() {
        overrideTenantId = 2L

        assertEquals(listOf("c-9"), search("").map { it.id })
    }

    private fun search(query: String) =
        rest.getForEntity("/v1/contracts?$query", SearchContractResponse::class.java).body!!.contracts
}
