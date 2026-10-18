package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who may see, change or sign a contract: one of its parties (user 11 is party 100, user 12 party 101) or an admin
 * (user 14) may see it, only an admin may change it, and only the party itself may sign, never an admin.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/ContractAccessEndpoint.sql"])
class ContractAccessEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ContractRepository

    @Autowired
    private lateinit var partyDao: ContractPartyRepository

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private fun signAsAdmin() {
        admin = true
        userId = 14L
    }

    @Test
    fun `party gets its contract`() {
        assertEquals("c-1", get("c-1").id)
    }

    @Test
    fun `party can't get the contract of another party`() {
        assertDenied(rest.getForEntity("/v1/contracts/c-2", ErrorResponse::class.java))
    }

    @Test
    fun `user without party can't get a contract`() {
        userId = 13L

        assertDenied(rest.getForEntity("/v1/contracts/c-1", ErrorResponse::class.java))
    }

    @Test
    fun `anonymous can't get a contract`() {
        anonymousUser = true

        assertUnauthorized(rest.getForEntity("/v1/contracts/c-1", ErrorResponse::class.java))
    }

    @Test
    fun `not found before access denied`() {
        val response = rest.getForEntity("/v1/contracts/xxx", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `admin gets any contract`() {
        signAsAdmin()

        assertEquals("c-2", get("c-2").id)
    }

    @Test
    fun `party searches its own contracts only`() {
        assertEquals(listOf("c-3", "c-1"), search("").map { it.id })
        assertEquals(listOf("c-3", "c-1"), search("party-id=100").map { it.id })
        assertTrue(search("id=c-2").isEmpty())
    }

    @Test
    fun `party can't search the contracts of another party`() {
        assertDenied(rest.getForEntity("/v1/contracts?party-id=101", ErrorResponse::class.java))
        assertDenied(rest.getForEntity("/v1/contracts?party-id=100&party-id=101", ErrorResponse::class.java))
    }

    @Test
    fun `user without party can't search`() {
        userId = 13L

        assertDenied(rest.getForEntity("/v1/contracts", ErrorResponse::class.java))
    }

    @Test
    fun `anonymous can't search`() {
        anonymousUser = true

        assertUnauthorized(rest.getForEntity("/v1/contracts", ErrorResponse::class.java))
    }

    @Test
    fun `admin searches all contracts`() {
        signAsAdmin()

        assertEquals(listOf("c-3", "c-2", "c-1"), search("").map { it.id })
    }

    @Test
    fun `party can't create a contract`() {
        val request = CreateContractRequest(
            type = ContractType.SEARCH,
            durationMonths = 6,
            parties = listOf(
                CreateContractParty(
                    partyId = 100L,
                    legalName = "Ray Sponsible",
                    legalEmail = "ray.sponsible@gmail.com",
                    role = ContractPartyRole.MANDATOR,
                ),
            ),
        )

        assertDenied(rest.postForEntity("/v1/contracts", request, ErrorResponse::class.java))
        assertEquals(3, dao.count())
    }

    @Test
    fun `party can't set the content of its contract`() {
        val request = SetContractRequest(url = "https://evil.com/c-3.pdf")

        assertDenied(rest.postForEntity("/v1/contracts/c-3/content", request, ErrorResponse::class.java))
        assertNull(dao.findById("c-3").get().contentUrl)
    }

    @Test
    fun `party can't request the signature of its contract`() {
        assertDenied(rest.postForEntity("/v1/contracts/c-3/request-signature", null, ErrorResponse::class.java))
        assertEquals(ContractStatus.DRAFT, dao.findById("c-3").get().status)
    }

    @Test
    fun `anonymous can't request a signature`() {
        anonymousUser = true

        assertUnauthorized(rest.postForEntity("/v1/contracts/c-3/request-signature", null, ErrorResponse::class.java))
        assertEquals(ContractStatus.DRAFT, dao.findById("c-3").get().status)
    }

    @Test
    fun `party signs its contract`() {
        val response = rest.postForEntity("/v1/contracts/c-1/sign", sign("cp-1"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(partyDao.findById("cp-1").get().signed)
    }

    @Test
    fun `party can't sign for another party`() {
        assertDenied(rest.postForEntity("/v1/contracts/c-2/sign", sign("cp-2"), ErrorResponse::class.java))
        assertFalse(partyDao.findById("cp-2").get().signed)
    }

    @Test
    fun `admin can't sign for a party`() {
        signAsAdmin()

        assertDenied(rest.postForEntity("/v1/contracts/c-1/sign", sign("cp-1"), ErrorResponse::class.java))
        assertFalse(partyDao.findById("cp-1").get().signed)
    }

    @Test
    fun `user without party can't sign`() {
        userId = 13L

        assertDenied(rest.postForEntity("/v1/contracts/c-1/sign", sign("cp-1"), ErrorResponse::class.java))
        assertFalse(partyDao.findById("cp-1").get().signed)
    }

    @Test
    fun `anonymous can't sign`() {
        anonymousUser = true

        assertUnauthorized(rest.postForEntity("/v1/contracts/c-1/sign", sign("cp-1"), ErrorResponse::class.java))
        assertFalse(partyDao.findById("cp-1").get().signed)
    }

    private fun get(id: String) =
        rest.getForEntity("/v1/contracts/$id", GetContractResponse::class.java).body!!.contract

    private fun search(query: String) =
        rest.getForEntity("/v1/contracts?$query", SearchContractResponse::class.java).body!!.contracts

    private fun sign(partyId: String) =
        SignContractRequest(partyId = partyId, ipAddress = "10.0.0.1", userAgent = "Mozilla/5.0")

    private fun assertDenied(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_ACCESS_DENIED, response.body?.error?.code)
    }

    private fun assertUnauthorized(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
