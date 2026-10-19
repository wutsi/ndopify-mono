package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateContractGenerator
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.io.InputStream
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ContractService, MandateContractGenerator and StorageService are mocked: the contracts, their content and the
 * storage are covered by their own tests.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/RequestMandateApprovalEndpoint.sql"])
class RequestMandateApprovalEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    companion object {
        private const val HTML = "<html>Mandat de recherche à Yaoundé</html>"

        // Of the stored bytes: UTF-8
        private val CONTENT_HASH = DigestUtils.sha256Hex(HTML.toByteArray(Charsets.UTF_8))
    }

    @Autowired
    private lateinit var dao: MandateRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var contractService: ContractService

    @MockitoBean
    private lateinit var contractGenerator: MandateContractGenerator

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    // Returned by the mocked ContractService: an entity is only equal to itself (createdAt)
    private val contract1 = ContractEntity(id = "c-1")
    private val contract4 = ContractEntity(id = "c-4")

    // What was stored, by path
    private val stored = mutableMapOf<String, Pair<String, String?>>()

    @BeforeTest
    override fun setUp() {
        super.setUp()
        stored.clear()

        doReturn(storage).whenever(storageProvider).get()
        doReturn(contract1).whenever(contractService).create(any())
        doReturn(contract1).whenever(contractService).findById("c-1")
        doReturn(contract4).whenever(contractService).findById("c-4")
        doReturn(HTML).whenever(contractGenerator).generate(any(), any())
        doAnswer { invocation ->
            val path = invocation.getArgument<String>(0)
            val content = invocation.getArgument<InputStream>(1).readAllBytes().toString(Charsets.UTF_8)
            stored[path] = content to invocation.getArgument<String?>(2)
            null
        }.whenever(storage).store(any(), any(), any())
        doAnswer { invocation -> URI("https://cdn.ndopify.com/${invocation.getArgument<String>(0)}").toURL() }
            .whenever(storage).publicUrl(any())
    }

    @Test
    fun `request approval`() {
        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-1", mandate.contractId)

        // The customer signs it, as the mandator. The agent is a party, but doesn't sign it
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
                    signatureRequired = true,
                ),
                CreateContractParty(
                    partyId = 200L,
                    legalName = "Kim Possible",
                    legalEmail = "kim.possible@gmail.com",
                    role = ContractPartyRole.AGENT,
                    signatureRequired = false,
                ),
            ),
            request.firstValue.parties,
        )
        assertEquals(6, request.firstValue.durationMonths) // ndopify.mandate.contract.duration-months

        // The content is generated from the mandate, stored, and set as the contract's
        val generated = argumentCaptor<MandateEntity>()
        verify(contractGenerator).generate(generated.capture(), eq(contract1))
        assertEquals(1L, generated.firstValue.id)
        val path = assertStored("mandates/1/contracts/c-1/")
        verify(contractService).setContent(
            "c-1",
            SetContractRequest(url = "https://cdn.ndopify.com/$path"),
            CONTENT_HASH,
        )

        verify(contractService).requestSignature("c-1")
    }

    @Test
    fun `concurrent requests - one contract`() {
        // Slow enough for the 2nd request to arrive while the 1st creates the contract
        doAnswer {
            Thread.sleep(500)
            contract1
        }.whenever(contractService).create(any())

        val pool = Executors.newFixedThreadPool(2)
        val statuses = try {
            List(2) {
                pool.submit<HttpStatusCode> {
                    rest.postForEntity("/v1/mandates/1/request-approval", null, Any::class.java).statusCode
                }
            }.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }

        // The 2nd waits for the 1st's lock, then reuses its contract. It can still find the mandate sent meanwhile: 409
        assertTrue(HttpStatus.OK in statuses, "statuses: $statuses")
        assertTrue(statuses.all { it == HttpStatus.OK || it == HttpStatus.CONFLICT }, "statuses: $statuses")
        verify(contractService, times(1)).create(any())

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-1", mandate.contractId)
    }

    @Test
    fun `expired contract - replaced by a new one`() {
        doReturn(contract4.copy(status = ContractStatus.EXPIRED)).whenever(contractService).findById("c-4")

        val response = rest.postForEntity("/v1/mandates/4/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(contractService).create(any())
        assertEquals("c-1", dao.findById(4L).get().contractId)
        assertStored("mandates/4/contracts/c-1/")
        verify(contractService).requestSignature("c-1")
    }

    @Test
    fun `mandate updated while its content was generated - 409, not sent`() {
        doAnswer {
            // Another request's update, committed between the generation and the end of the approval
            jdbc.update("UPDATE T_MANDATE SET version = version + 1, comments = 'Changed' WHERE id = 1")
            HTML
        }.whenever(contractGenerator).generate(any(), any())

        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_VERSION_CONFLICT, response.body?.error?.code)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.ASSIGNED, mandate.status)
        assertEquals("Changed", mandate.comments)
        assertEquals("c-1", mandate.contractId) // Kept for the next request
    }

    @Test
    fun `listing mandate`() {
        val contract2 = ContractEntity(id = "c-2")
        doReturn(contract2).whenever(contractService).create(any())
        doReturn(contract2).whenever(contractService).findById("c-2")

        val response = rest.postForEntity("/v1/mandates/2/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(2L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-2", mandate.contractId)

        val request = argumentCaptor<CreateContractRequest>()
        verify(contractService).create(request.capture())
        assertEquals(ContractType.LISTING, request.firstValue.type)
        assertStored("mandates/2/contracts/c-2/")
        verify(contractService).requestSignature("c-2")
    }

    @Test
    fun `request approval again`() {
        val response = rest.postForEntity("/v1/mandates/4/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val mandate = dao.findById(4L).get()
        assertEquals(MandateStatus.PENDING_APPROVAL, mandate.status)
        assertEquals("c-4", mandate.contractId)

        // Its contract is reused, with a new content
        verify(contractService, never()).create(any())
        val path = assertStored("mandates/4/contracts/c-4/")
        verify(contractService).setContent(
            "c-4",
            SetContractRequest(url = "https://cdn.ndopify.com/$path"),
            CONTENT_HASH,
        )
        verify(contractService).requestSignature("c-4")
    }

    @Test
    fun `mandate not assigned`() {
        assertCannotBeApproved(3L, MandateStatus.PENDING_ASSIGNMENT)
    }

    @Test
    fun `mandate already approved`() {
        assertCannotBeApproved(5L, MandateStatus.APPROVED)
    }

    @Test
    fun `signature request failure - not sent, the contract kept for the next request`() {
        doThrow(ConflictException(Error(code = ErrorCode.CONTRACT_EXPIRED)))
            .whenever(contractService).requestSignature("c-1")

        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_EXPIRED, response.body?.error?.code)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.ASSIGNED, mandate.status)
        assertEquals("c-1", mandate.contractId)
    }

    @Test
    fun `storage failure - not sent, the contract reused by the next request`() {
        doThrow(IllegalStateException("storage down")).whenever(storage).store(any(), any(), any())

        val response = rest.postForEntity("/v1/mandates/1/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)

        val mandate = dao.findById(1L).get()
        assertEquals(MandateStatus.ASSIGNED, mandate.status)
        assertEquals("c-1", mandate.contractId)
        verify(contractService, never()).setContent(any<String>(), any(), anyOrNull())
        verify(contractService, never()).requestSignature(any())

        // Storage back: the same contract, no new number taken
        doNothing().whenever(storage).store(any(), any(), any())
        val retry = rest.postForEntity("/v1/mandates/1/request-approval", null, Any::class.java)

        assertEquals(HttpStatus.OK, retry.statusCode)
        verify(contractService, times(1)).create(any())
        assertEquals(MandateStatus.PENDING_APPROVAL, dao.findById(1L).get().status)
    }

    @Test
    fun `mandate not found`() {
        assertNotFound(999L)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        assertNotFound(9L)
    }

    // The one HTML file stored under [prefix]: the generated content. Returns its path
    private fun assertStored(prefix: String): String {
        val (path, file) = stored.entries.single().toPair()
        assertTrue(path.startsWith(prefix) && path.endsWith(".html"), "path: $path")
        assertEquals(HTML to "text/html", file)
        return path
    }

    private fun assertCannotBeApproved(id: Long, status: MandateStatus) {
        val response = rest.postForEntity("/v1/mandates/$id/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_CANNOT_BE_APPROVED, response.body?.error?.code)

        val mandate = dao.findById(id).get()
        assertEquals(status, mandate.status)
        assertNull(mandate.contractId)
        assertNothingDone()
    }

    private fun assertNotFound(id: Long) {
        val response = rest.postForEntity("/v1/mandates/$id/request-approval", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        assertNothingDone()
    }

    private fun assertNothingDone() {
        verify(contractService, never()).create(any())
        verify(contractGenerator, never()).generate(any(), any())
        verify(storage, never()).store(any(), any(), any())
        verify(contractService, never()).requestSignature(any())
    }
}
