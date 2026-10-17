package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/RequestContractSignatureEndpoint.sql"])
class RequestContractSignatureEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ContractRepository

    @Test
    fun `request signature`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-1/request-signature",
            null,
            GetContractResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ContractStatus.PENDING_SIGNATURE, response.body!!.contract.status)
        assertEquals(ContractStatus.PENDING_SIGNATURE, dao.findById("c-1").get().status)

        // Each party is asked to sign, asynchronously
        assertTrue(smtp.waitForIncomingEmail(5000, 2), "no email received")
        val messages = smtp.receivedMessages.sortedBy { message -> message.allRecipients.single().toString() }
        assertEquals(2, messages.size)
        assertEquals(
            listOf("John Smith <john.smith@gmail.com>", "Ray Sponsible <ray.sponsible@gmail.com>"),
            messages.map { message -> message.allRecipients.single().toString() },
        )
        messages.forEach { message ->
            assertEquals("Ndopify: Votre contrat MNDT-2024-000001 est prêt à être signé", message.subject)
        }

        val body = messages[1].content.toString()
        assertTrue(body.contains("Ray Sponsible"))
        assertTrue(body.contains("Mandat de recherche"))
        assertFalse(body.contains("https://cdn.ndopify.com/contracts/c-1.pdf")) // Only reached through the sign page
        assertTrue(body.contains("01/06/2099"))
        assertTrue(body.contains("https://partner.ndopify.com/contracts/c-1/sign?party-id=cp-11"))
        assertTrue(messages[0].content.toString().contains("https://partner.ndopify.com/contracts/c-1/sign?party-id=cp-12"))
    }

    @Test
    fun `request signature again`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-2/request-signature",
            null,
            GetContractResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ContractStatus.PENDING_SIGNATURE, response.body!!.contract.status)
        assertEquals(ContractStatus.PENDING_SIGNATURE, dao.findById("c-2").get().status)

        // Only the party that hasn't signed yet is asked again
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertFalse(smtp.waitForIncomingEmail(1000, 2), "only one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Votre contrat MNDT-2024-000002 est prêt à être signé", message.subject)
        assertTrue(message.content.toString().contains("https://partner.ndopify.com/contracts/c-2/sign?party-id=cp-21"))
    }

    @Test
    fun `contract past its end date`() {
        assertConflict("c-3", ErrorCode.CONTRACT_EXPIRED, ContractStatus.DRAFT)
    }

    @Test
    fun `contract expired`() {
        assertConflict("c-4", ErrorCode.CONTRACT_EXPIRED, ContractStatus.EXPIRED)
    }

    @Test
    fun `contract cancelled`() {
        assertConflict("c-5", ErrorCode.CONTRACT_CANCELLED, ContractStatus.CANCELLED)
    }

    @Test
    fun `contract already signed`() {
        assertConflict("c-6", ErrorCode.CONTRACT_ALREADY_SIGNED, ContractStatus.SIGNED)
    }

    @Test
    fun `contract not found`() {
        assertNotFound("xxx")
    }

    @Test
    fun `contract from another tenant is not found`() {
        assertNotFound("c-9")
    }

    private fun assertConflict(id: String, code: String, status: ContractStatus) {
        val response = rest.postForEntity("/v1/contracts/$id/request-signature", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(status, dao.findById(id).get().status)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    private fun assertNotFound(id: String) {
        val response = rest.postForEntity("/v1/contracts/$id/request-signature", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }
}
