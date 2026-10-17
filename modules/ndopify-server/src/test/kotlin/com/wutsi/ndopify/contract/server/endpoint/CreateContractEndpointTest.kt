package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.CreateContractResponse
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/CreateContractEndpoint.sql"])
class CreateContractEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ContractRepository

    @Autowired
    private lateinit var partyDao: ContractPartyRepository

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    // The tenant's time zone (T_TENANT.time_zone default)
    private val year = ZonedDateTime.now(ZoneId.of("Africa/Douala")).year

    private val request = CreateContractRequest(
        type = ContractType.SEARCH,
        contentUrl = "https://cdn.ndopify.com/contracts/1.pdf",
        validFrom = fmt.parse("2024-06-01 00:00:00"),
        validTo = fmt.parse("2099-06-01 00:00:00"),
        parties = listOf(
            CreateContractParty(
                partyId = 100L,
                legalName = "Ray Sponsible",
                legalEmail = "Ray.Sponsible@gmail.com",
                role = ContractPartyRole.MANDATOR,
            ),
            CreateContractParty(
                partyId = 101L,
                legalName = "John Smith",
                legalEmail = "john.smith@gmail.com",
                role = ContractPartyRole.GUARANTOR,
            ),
        ),
    )

    @Test
    fun create() {
        val response = rest.postForEntity("/v1/contracts", request, CreateContractResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val contract = dao.findById(response.body!!.contractId).get()
        assertEquals(TENANT_ID, contract.tenantId)
        assertEquals("MNDT-$year-000001", contract.number)
        assertEquals(ContractType.SEARCH, contract.type)
        assertEquals(ContractStatus.DRAFT, contract.status)
        assertEquals(request.contentUrl, contract.contentUrl)
        assertEquals(request.validFrom.time, contract.validFrom.time)
        assertEquals(request.validTo.time, contract.validUntil.time)
        assertNull(contract.signedAt)

        val parties = partyDao.findByContract(contract).sortedBy { party -> party.partyId }
        assertEquals(2, parties.size)

        assertEquals(TENANT_ID, parties[0].tenantId)
        assertEquals(100L, parties[0].partyId)
        assertEquals(ContractPartyRole.MANDATOR, parties[0].role)
        assertEquals("Ray Sponsible", parties[0].legalName)
        assertEquals("ray.sponsible@gmail.com", parties[0].legalEmail)
        assertFalse(parties[0].signed)
        assertNull(parties[0].ipAddress)
        assertNull(parties[0].userAgent)
        assertNull(parties[0].signedAt)

        assertEquals(TENANT_ID, parties[1].tenantId)
        assertEquals(101L, parties[1].partyId)
        assertEquals(ContractPartyRole.GUARANTOR, parties[1].role)
        assertEquals("John Smith", parties[1].legalName)
        assertEquals("john.smith@gmail.com", parties[1].legalEmail)
        assertFalse(parties[1].signed)
    }

    @Test
    fun `numbers follow each other`() {
        assertEquals("MNDT-$year-000001", createAndGetNumber(request))
        assertEquals("MNDT-$year-000002", createAndGetNumber(request.copy(type = ContractType.LISTING)))
    }

    @Test
    fun `lease agreement`() {
        assertEquals("BAIL-$year-000042", createAndGetNumber(request.copy(type = ContractType.LEASE_AGREEMENT)))
    }

    @Test
    fun `rejected contract consumes no number`() {
        val parties = listOf(request.parties[0].copy(partyId = 999L))
        rest.postForEntity("/v1/contracts", request.copy(parties = parties), ErrorResponse::class.java)

        assertEquals("MNDT-$year-000001", createAndGetNumber(request))
    }

    @Test
    fun `missing content url`() {
        assertBadRequest(request.copy(contentUrl = ""), ErrorCode.HTTP_INVALID_PARAMETER)
    }

    @Test
    fun `no party`() {
        assertBadRequest(request.copy(parties = emptyList()), ErrorCode.HTTP_INVALID_PARAMETER)
    }

    @Test
    fun `party without legal name`() {
        val parties = listOf(request.parties[0].copy(legalName = ""))
        assertBadRequest(request.copy(parties = parties), ErrorCode.HTTP_INVALID_PARAMETER)
    }

    @Test
    fun `unknown type`() {
        assertBadRequest(request.copy(type = ContractType.UNKNOWN), ErrorCode.HTTP_INVALID_PARAMETER, "type")
    }

    @Test
    fun `unknown party role`() {
        val parties = listOf(request.parties[0].copy(role = ContractPartyRole.UNKNOWN))
        assertBadRequest(request.copy(parties = parties), ErrorCode.HTTP_INVALID_PARAMETER, "parties.role")
    }

    @Test
    fun `valid to before valid from`() {
        assertBadRequest(
            request.copy(validTo = fmt.parse("2024-05-01 00:00:00")),
            ErrorCode.HTTP_INVALID_PARAMETER,
            "validTo",
        )
    }

    @Test
    fun `party not found`() {
        val parties = listOf(request.parties[0].copy(partyId = 999L))
        assertPartyNotFound(request.copy(parties = parties))
    }

    @Test
    fun `party from another tenant`() {
        val parties = listOf(request.parties[0].copy(partyId = 900L))
        assertPartyNotFound(request.copy(parties = parties))
    }

    private fun createAndGetNumber(request: CreateContractRequest): String {
        val response = rest.postForEntity("/v1/contracts", request, CreateContractResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return dao.findById(response.body!!.contractId).get().number
    }

    private fun assertBadRequest(request: CreateContractRequest, code: String, parameter: String? = null) {
        val response = rest.postForEntity("/v1/contracts", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        parameter?.let { assertEquals(parameter, response.body?.error?.parameter?.name) }
        assertTrue(dao.findAll().none())
    }

    private fun assertPartyNotFound(request: CreateContractRequest) {
        val response = rest.postForEntity("/v1/contracts", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
        assertTrue(dao.findAll().none())
    }
}
