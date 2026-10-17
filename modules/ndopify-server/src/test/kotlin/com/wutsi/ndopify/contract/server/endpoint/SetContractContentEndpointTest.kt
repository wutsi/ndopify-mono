package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/SetContractContentEndpoint.sql"])
class SetContractContentEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ContractRepository

    private val request = SetContractRequest(url = " https://cdn.ndopify.com/contracts/new.pdf ")

    @Test
    fun `draft contract`() {
        val response = rest.postForEntity("/v1/contracts/c-1/content", request, GetContractResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("https://cdn.ndopify.com/contracts/new.pdf", response.body!!.contract.contentUrl)

        val contract = dao.findById("c-1").get()
        assertEquals("https://cdn.ndopify.com/contracts/new.pdf", contract.contentUrl)
        assertEquals(ContractStatus.DRAFT, contract.status)
    }

    @Test
    fun `contract pending signature`() {
        val response = rest.postForEntity("/v1/contracts/c-2/content", request, GetContractResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val contract = dao.findById("c-2").get()
        assertEquals("https://cdn.ndopify.com/contracts/new.pdf", contract.contentUrl)
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
    }

    @Test
    fun `missing url`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-1/content",
            SetContractRequest(url = ""),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertNull(dao.findById("c-1").get().contentUrl)
    }

    @Test
    fun `contract expired`() {
        assertConflict("c-4", ErrorCode.CONTRACT_EXPIRED)
    }

    @Test
    fun `contract cancelled`() {
        assertConflict("c-5", ErrorCode.CONTRACT_CANCELLED)
    }

    @Test
    fun `contract signed`() {
        assertConflict("c-6", ErrorCode.CONTRACT_ALREADY_SIGNED)
    }

    @Test
    fun `contract not found`() {
        assertNotFound("xxx")
    }

    @Test
    fun `contract from another tenant is not found`() {
        assertNotFound("c-9")
    }

    private fun assertConflict(id: String, code: String) {
        val contentUrl = dao.findById(id).get().contentUrl

        val response = rest.postForEntity("/v1/contracts/$id/content", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(contentUrl, dao.findById(id).get().contentUrl)
    }

    private fun assertNotFound(id: String) {
        val response = rest.postForEntity("/v1/contracts/$id/content", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }
}
