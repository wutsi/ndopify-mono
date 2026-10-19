package com.wutsi.ndopify.contract.server.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.contract.server.service.ContractHasher
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.local.StorageServiceLocal
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The signed PDF, generated on demand (ops endpoint) into a local storage of a temporary directory. */
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/GenerateSignedContentEndpoint.sql"])
class GenerateSignedContentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = true

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @Autowired
    private lateinit var dao: ContractRepository

    @Autowired
    private lateinit var contractService: ContractService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @TempDir
    lateinit var dir: File

    private lateinit var storage: StorageServiceLocal

    private val content = "<html><body><p>Mandat de recherche</p></body></html>".toByteArray()

    @BeforeTest
    override fun setUp() {
        super.setUp()

        storage = StorageServiceLocal(dir.absolutePath, "/storage", 8080)
        doReturn(storage).whenever(storageProvider).get()

        // c-1's content, as MandateService stores it
        val path = "mandates/1/contracts/c-1/content.html"
        storage.store(path, ByteArrayInputStream(content), "text/html")
        jdbc.update(
            "UPDATE T_CONTRACT SET content_url=?, content_hash=? WHERE id='c-1'",
            storage.publicUrl(path).toString(),
            ContractHasher.contentHash(content),
        )
    }

    @Test
    fun generate() {
        val response = rest.postForEntity("/v1/contracts/c-1/signed-content", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val url = rest.getForEntity("/v1/contracts/c-1", GetContractResponse::class.java).body!!.contract.signedContentUrl!!
        val pdfPath = storage.pathOf(url)!!
        assertTrue(Regex("mandates/1/contracts/c-1/MNDT-2024-000001-[0-9a-f]{32}\\.pdf").matches(pdfPath), pdfPath)

        // Next to the content: the PDF, and one QR code per signer (not the agent)
        val name = pdfPath.substringAfterLast('/').removeSuffix(".pdf")
        val files = File(dir, "mandates/1/contracts/c-1").list()!!.sorted()
        assertEquals(listOf("$name-signature-1.png", "$name-signature-2.png", "$name.pdf", "content.html"), files)

        val pdf = File(dir, pdfPath).readBytes()
        assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(ContractHasher.contentHash(pdf), dao.findById("c-1").get().signedContentHash)
    }

    @Test
    fun `generated once`() {
        rest.postForEntity("/v1/contracts/c-1/signed-content", null, Any::class.java)
        val first = dao.findById("c-1").get()

        rest.postForEntity("/v1/contracts/c-1/signed-content", null, Any::class.java)

        val second = dao.findById("c-1").get()
        assertEquals(first.signedContentUrl, second.signedContentUrl)
        assertEquals(first.signedContentHash, second.signedContentHash)
    }

    @Test
    fun `keeps the first signed content`() {
        // TenantContext is set on this thread (TenantAwareEndpointIntegrationTest)
        contractService.setSignedContent("c-1", "https://s.com/first.pdf", "1".repeat(64))
        val second = contractService.setSignedContent("c-1", "https://s.com/second.pdf", "2".repeat(64))
        assertEquals("https://s.com/first.pdf", second.signedContentUrl)

        val contract = dao.findById("c-1").get()
        assertEquals("https://s.com/first.pdf", contract.signedContentUrl)
        assertEquals("1".repeat(64), contract.signedContentHash)
    }

    @Test
    fun `contract not signed`() {
        val response = rest.postForEntity("/v1/contracts/c-2/signed-content", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(dao.findById("c-2").get().signedContentUrl)
    }

    @Test
    fun `contract not found`() {
        val response = rest.postForEntity("/v1/contracts/c-9/signed-content", null, Any::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `admin only`() {
        admin = false

        val response = rest.postForEntity("/v1/contracts/c-1/signed-content", null, Any::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertNull(dao.findById("c-1").get().signedContentUrl)
    }
}
