package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/SearchContractEndpoint.sql"])
class SearchContractEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = true

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun all() {
        val contracts = search("")
        assertEquals(listOf("c-3", "c-2", "c-1"), contracts.map { it.id }) // Newest first

        val contract = contracts[1]
        assertEquals("MNDT-2024-000002", contract.number)
        assertEquals(ContractType.SEARCH, contract.type)
        assertEquals(ContractStatus.SIGNED, contract.status)
        assertEquals(12, contract.durationMonths)
        assertEquals(Date.from(Instant.parse("2024-05-25T00:00:00Z")), contract.signatureRequestedAt)
        assertNotNull(contract.signatureDeadline)
        assertNotNull(contract.validFrom)
        assertNotNull(contract.validUntil)
        assertNotNull(contract.signedAt)
        assertNull(contracts[0].signatureDeadline)
        assertNull(contracts[0].signatureRequestedAt)
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
    fun `by contract party ids`() {
        assertEquals(listOf("c-2", "c-1"), search("contract-party-id=cp-11&contract-party-id=cp-21").map { it.id })
    }

    @Test
    fun `by contract party id, as the party of that contract party`() {
        admin = false // user 11 is party 100, the party of cp-11

        assertEquals(listOf("c-1"), search("contract-party-id=cp-11").map { it.id })
    }

    @Test
    fun `contract party of another party returns nothing`() {
        admin = false // user 11 is party 100, cp-21 is party 101's

        assertTrue(search("contract-party-id=cp-21").isEmpty())
    }

    @Test
    fun `contract party of a co-party of the same contract returns nothing`() {
        admin = false // user 11 is party 100, a party of c-1, but cp-12 is party 101's

        assertTrue(search("contract-party-id=cp-12").isEmpty())
    }

    @Test
    fun `by contract party id and party id, on the same contract party`() {
        assertEquals(listOf("c-1"), search("contract-party-id=cp-12&party-id=101").map { it.id })
        assertTrue(search("contract-party-id=cp-12&party-id=100").isEmpty())
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
