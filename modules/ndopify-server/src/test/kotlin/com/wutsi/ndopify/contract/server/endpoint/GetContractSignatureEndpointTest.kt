package com.wutsi.ndopify.contract.server.endpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.GetContractSignatureResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/GetContractSignatureEndpoint.sql"])
class GetContractSignatureEndpointTest : TenantAwareEndpointIntegrationTest() {
    companion object {
        // Set in GetContractSignatureEndpoint.sql
        private val HASH_A = "a".repeat(64)
        private val HASH_B = "b".repeat(64)
        private val HASH_C = "c".repeat(64)
        private val HASH_F = "f".repeat(64)
    }

    @Test
    fun signature() {
        val response = rest.getForEntity("/v1/contracts/c-100/signatures/$HASH_A", GetContractSignatureResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val signature = response.body!!.signature
        assertEquals("c-100", signature.contractId)
        assertEquals("MNDT-2026-000042", signature.contractNumber)
        assertEquals("Ray Sponsible", signature.legalName)
        assertEquals(Date.from(Instant.parse("2026-10-18T14:32:00Z")), signature.signedAt)
    }

    @Test
    fun `exposes nothing else`() {
        val response = rest.getForEntity("/v1/contracts/c-100/signatures/$HASH_A", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val signature = ObjectMapper().readTree(response.body).get("signature")
        assertEquals(
            setOf("contractId", "contractNumber", "legalName", "signedAt"),
            signature.fieldNames().asSequence().toSet(),
        )
    }

    @Test
    fun `pending contract`() {
        val response = rest.getForEntity("/v1/contracts/c-110/signatures/$HASH_C", GetContractSignatureResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("MNDT-2026-000043", response.body!!.signature.contractNumber)
    }

    @Test
    fun `unknown hash`() = assertNotFound("c-100", HASH_F)

    @Test
    fun `unknown contract`() = assertNotFound("xxx", HASH_A)

    @Test
    fun `hash of another contract`() = assertNotFound("c-110", HASH_A)

    @Test
    fun `other tenant`() = assertNotFound("c-200", HASH_B)

    private fun assertNotFound(contractId: String, hash: String) {
        val response = rest.getForEntity("/v1/contracts/$contractId/signatures/$hash", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND, response.body?.error?.code)
    }
}
