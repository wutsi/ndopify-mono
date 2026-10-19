package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/GetContractEndpoint.sql"])
class GetContractEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    companion object {
        // Set in GetContractEndpoint.sql
        private const val CONTENT_HASH = "1111111111111111111111111111111111111111111111111111111111111111"
        private const val SIGNATURE_HASH = "2222222222222222222222222222222222222222222222222222222222222222"
    }

    @Test
    fun get() {
        val response = rest.getForEntity("/v1/contracts/c-1", GetContractResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val contract = response.body!!.contract
        assertEquals("c-1", contract.id)
        assertEquals("MNDT-2024-000001", contract.number)
        assertEquals(ContractType.SEARCH, contract.type)
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
        assertEquals("https://cdn.ndopify.com/contracts/c-1.pdf", contract.contentUrl)
        assertEquals(6, contract.durationMonths)
        assertNotNull(contract.signatureDeadline)
        assertNull(contract.validFrom)
        assertNull(contract.validUntil)
        assertNull(contract.signedAt)

        val parties = contract.parties.sortedBy { party -> party.id }
        assertEquals(listOf("cp-1", "cp-2"), parties.map { party -> party.id })

        assertEquals(100L, parties[0].partyId)
        assertEquals(ContractPartyRole.MANDATOR, parties[0].role)
        assertEquals("Ray Sponsible", parties[0].legalName)
        assertEquals("ray.sponsible@gmail.com", parties[0].legalEmail)
        assertTrue(parties[0].signed)
        assertEquals("10.0.0.1", parties[0].ipAddress)
        assertEquals("Mozilla/5.0", parties[0].userAgent)
        assertNotNull(parties[0].signedAt)

        assertEquals(101L, parties[1].partyId)
        assertEquals(ContractPartyRole.GUARANTOR, parties[1].role)
        assertFalse(parties[1].signed)
        assertNull(parties[1].signedAt)
    }

    @Test
    fun `hashes are never returned`() {
        val response = rest.getForEntity("/v1/contracts/c-1", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val body = response.body!!
        assertFalse(body.contains("contentHash"))
        assertFalse(body.contains("signatureHash"))
        assertFalse(body.contains(CONTENT_HASH))
        assertFalse(body.contains(SIGNATURE_HASH))
    }

    @Test
    fun `contract not found`() {
        val response = rest.getForEntity("/v1/contracts/xxx", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `contract from another tenant is not found`() {
        val response = rest.getForEntity("/v1/contracts/c-9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }
}
