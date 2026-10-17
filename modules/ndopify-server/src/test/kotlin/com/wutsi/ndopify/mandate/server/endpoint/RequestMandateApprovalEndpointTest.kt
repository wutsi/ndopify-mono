package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ContractService is mocked: the contracts themselves are covered by the contract endpoint tests. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/RequestMandateApprovalEndpoint.sql"])
class RequestMandateApprovalEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MandateRepository

    @MockitoBean
    private lateinit var contractService: ContractService

    @BeforeTest
    override fun setUp() {
        super.setUp()
        doReturn(ContractEntity(id = "c-1")).whenever(contractService).create(any())
    }

    @Test
    fun `request approval`() {
        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-1", mandate.contractId)

        // The customer is the only party: the mandator
        val request = argumentCaptor<CreateContractRequest>()
        verify(contractService).create(request.capture())
        assertEquals(ContractType.SEARCH, request.firstValue.type)
        assertEquals(
            listOf(
                CreateContractParty(
                    partyId = 100L,
                    legalName = "Ray Sponsible",
                    legalEmail = "ray.sponsible@gmail.com",
                    role = ContractPartyRole.MANDATOR,
                )
            ),
            request.firstValue.parties,
        )
        // ndopify.mandate.contract.duration-days
        assertEquals(DateUtils.addDays(request.firstValue.validFrom, 180), request.firstValue.validTo)

        verify(contractService).requestSignature("c-1")
    }

    @Test
    fun `listing mandate`() {
        doReturn(ContractEntity(id = "c-2")).whenever(contractService).create(any())

        val response = rest.postForEntity("/v1/mandates/2/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(2L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-2", mandate.contractId)

        val request = argumentCaptor<CreateContractRequest>()
        verify(contractService).create(request.capture())
        assertEquals(ContractType.LISTING, request.firstValue.type)
        verify(contractService).requestSignature("c-2")
    }

    @Test
    fun `mandate not assigned`() {
        val response = rest.postForEntity("/v1/mandates/3/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_ASSIGNED, response.body?.error?.code)

        val mandate = dao.findById(3L).get()
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertNull(mandate.contractId)
        verify(contractService, never()).create(any())
        verify(contractService, never()).requestSignature(any())
    }

    @Test
    fun `signature request failure rolls back the approval`() {
        doThrow(ConflictException(Error(code = ErrorCode.CONTRACT_EXPIRED)))
            .whenever(contractService).requestSignature("c-1")

        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_EXPIRED, response.body?.error?.code)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.ASSIGNED, mandate.status)
        assertNull(mandate.contractId)
    }

    @Test
    fun `mandate not found`() {
        assertNotFound(999L)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        assertNotFound(9L)
    }

    private fun assertNotFound(id: Long) {
        val response = rest.postForEntity("/v1/mandates/$id/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        verify(contractService, never()).create(any())
    }
}
