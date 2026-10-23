package com.wutsi.ndopify.contract.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.time.Clock
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignedContractServiceTest {
    /** Keeps the files in memory, by path. Its public URLs are `https://s.com/{path}`. */
    private class FakeStorage : StorageService {
        val files = mutableMapOf<String, ByteArray>()
        val contentTypes = mutableMapOf<String, String?>()

        override fun type() = StorageType.LOCAL

        override fun store(path: String, content: InputStream, contentType: String?) {
            files[path] = content.readBytes()
            contentTypes[path] = contentType
        }

        override fun get(path: String, os: OutputStream) {
            os.write(files[path]!!)
        }

        override fun generatePresignedUrl(path: String, expirationInSeconds: Int) = publicUrl(path)

        override fun publicUrl(path: String) = URL("https://s.com/$path")
    }

    private val content = "<html><body><p>Mandat</p></body></html>".toByteArray()
    private val contentPath = "mandates/1/contracts/c1/a.html"
    private val pdf = "%PDF-signed".toByteArray()

    private val storage = FakeStorage()
    private val contractService = mock<ContractService>()
    private val tenantService = mock<TenantService>()
    private val renderer = mock<SignedContractRenderer>()
    private val clock = mock<Clock>()
    private val now = 1792330320000L
    private val service = SignedContractService(
        contractService = contractService,
        tenantService = tenantService,
        storageProvider = StorageServiceProvider(StorageType.LOCAL, storage, storage),
        renderer = renderer,
        clock = clock,
        delayMinutes = 10,
        windowDays = 7,
    )

    private val tenant = TenantEntity(
        id = 1L,
        locales = listOf("fr-CM"),
        timeZone = "Africa/Douala",
        eSignaturePortalUrl = "https://sign.ndopify.com/",
    )

    private val contract = ContractEntity(
        id = "c1",
        number = "MNDT-2026-000001",
        status = ContractStatus.SIGNED,
        contentUrl = "https://s.com/$contentPath",
        contentHash = ContractHasher.contentHash(content),
    )

    private val signedAt = Date(1792330320000) // 2026-10-18 13:32 UTC, 14:32 in Douala

    private val customer = ContractPartyEntity(
        id = "cp-1",
        contract = contract,
        role = ContractPartyRole.MANDATOR,
        legalName = "Ray Sponsible",
        signed = true,
        signedAt = signedAt,
        signatureSalt = "1".repeat(32),
        signatureHash = "a".repeat(64),
    )

    private val guarantor = customer.copy(
        id = "cp-2",
        role = ContractPartyRole.GUARANTOR,
        legalName = "John Smith",
        signatureHash = "b".repeat(64),
    )

    // The agent of a mandate: a party that doesn't sign
    private val agent = customer.copy(
        id = "cp-3",
        role = ContractPartyRole.AGENT,
        legalName = "Kim Possible",
        signatureRequired = false,
        signed = false,
        signedAt = null,
        signatureSalt = null,
        signatureHash = null,
    )

    @BeforeTest
    fun setUp() {
        TenantContext.set(1L)
        storage.files[contentPath] = content

        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(contract).whenever(contractService).findById("c1")
        doReturn(listOf(customer, agent, guarantor)).whenever(contractService).findParties(any())
        doAnswer { invocation -> (invocation.arguments[0] as String).toByteArray() }
            .whenever(renderer).qrCode(any())
        doReturn(pdf).whenever(renderer).render(any(), any())
        doAnswer { invocation ->
            contract.copy(
                signedContentUrl = invocation.arguments[1] as String,
                signedContentHash = invocation.arguments[2] as String,
            )
        }.whenever(contractService).setSignedContent(any(), any(), any())
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    private val token = "[0-9a-f]{32}"

    private fun pdfPaths() = storage.files.keys.filter { path -> path.endsWith(".pdf") }

    @Test
    fun `generate stores the PDF and the QR codes next to the content`() {
        val url = service.generate("c1")

        // A random token in the names: the directory and the contract number are known to whoever has a QR code
        val pdfPath = pdfPaths().single()
        assertTrue(Regex("mandates/1/contracts/c1/MNDT-2026-000001-$token\\.pdf").matches(pdfPath), pdfPath)
        assertEquals("https://s.com/$pdfPath", url)
        assertContentEquals(pdf, storage.files[pdfPath])
        assertEquals("application/pdf", storage.contentTypes[pdfPath])

        // One per signer, with the PDF's token, never named after the contract party (the secret of its sign link)
        val prefix = pdfPath.removeSuffix(".pdf")
        val qrPaths = storage.files.keys.filter { path -> path.endsWith(".png") }.sorted()
        assertEquals(listOf("$prefix-signature-1.png", "$prefix-signature-2.png"), qrPaths)
        assertTrue(qrPaths.all { path -> storage.contentTypes[path] == "image/png" })
        assertTrue(storage.files.keys.none { path -> path.contains("cp-") })

        verify(contractService).setSignedContent("c1", "https://s.com/$pdfPath", ContractHasher.contentHash(pdf))
    }

    @Test
    fun `each run stores its own files`() {
        // Two runs at once (listener and ops endpoint): neither overwrites the files of the other
        service.generate("c1")
        service.generate("c1")

        assertEquals(2, pdfPaths().size)
        assertEquals(4, storage.files.keys.count { path -> path.endsWith(".png") })
    }

    @Test
    fun `QR code links to the verify page`() {
        service.generate("c1")

        verify(renderer).qrCode("https://sign.ndopify.com/verify/c1/${"a".repeat(64)}")
        verify(renderer).qrCode("https://sign.ndopify.com/verify/c1/${"b".repeat(64)}")
        assertContentEquals(
            "https://sign.ndopify.com/verify/c1/${"a".repeat(64)}".toByteArray(),
            storage.files[pdfPaths().single().removeSuffix(".pdf") + "-signature-1.png"],
        )
    }

    @Test
    fun `renders the content with one block per signer`() {
        service.generate("c1")

        val html = argumentCaptor<String>()
        val blocks = argumentCaptor<List<SignatureBlock>>()
        verify(renderer).render(html.capture(), blocks.capture())
        assertEquals(String(content), html.firstValue)

        // Not the agent, who doesn't sign. Dated in the tenant's time zone
        val formatter = TenantFormatter(tenant)
        assertEquals(listOf("Ray Sponsible", "John Smith"), blocks.firstValue.map { block -> block.legalName })
        assertEquals(listOf("a".repeat(64), "b".repeat(64)), blocks.firstValue.map { block -> block.signatureHash })
        assertEquals("${formatter.date(signedAt)} à ${formatter.time(signedAt)}", blocks.firstValue[0].signedAt)
        assertTrue(blocks.firstValue[0].signedAt.endsWith(" à 14:32"))
    }

    @Test
    fun `null content hash is accepted`() {
        doReturn(contract.copy(contentHash = null)).whenever(contractService).findById("c1")

        val url = service.generate("c1")

        assertEquals("https://s.com/${pdfPaths().single()}", url)
    }

    @Test
    fun `content at the storage root`() {
        storage.files["a.html"] = content
        doReturn(contract.copy(contentUrl = "https://s.com/a.html")).whenever(contractService).findById("c1")

        val url = service.generate("c1")

        val pdfPath = pdfPaths().single()
        assertTrue(Regex("MNDT-2026-000001-$token\\.pdf").matches(pdfPath), pdfPath)
        assertEquals("https://s.com/$pdfPath", url)
        assertTrue(storage.files.containsKey(pdfPath.removeSuffix(".pdf") + "-signature-1.png"))
    }

    @Test
    fun `contract not signed`() {
        assertNothingGenerated(contract.copy(status = ContractStatus.PENDING_SIGNATURE))
    }

    @Test
    fun `signed content already generated`() {
        assertNothingGenerated(contract.copy(signedContentUrl = "https://s.com/x.pdf"))
    }

    @Test
    fun `no content`() {
        assertNothingGenerated(contract.copy(contentUrl = null))
    }

    @Test
    fun `content not in the storage`() {
        assertNothingGenerated(contract.copy(contentUrl = "https://evil.com/$contentPath"))
    }

    @Test
    fun `content changed since it was signed`() {
        assertNothingGenerated(contract.copy(contentHash = "f".repeat(64)))
    }

    @Test
    fun `tenant without e-signature portal`() {
        doReturn(tenant.copy(eSignaturePortalUrl = null)).whenever(tenantService).findById(1L)
        assertNothingGenerated(contract)

        doReturn(tenant.copy(eSignaturePortalUrl = " ")).whenever(tenantService).findById(1L)
        assertNothingGenerated(contract)
    }

    @Test
    fun `signature without hash`() {
        doReturn(listOf(customer.copy(signatureHash = null))).whenever(contractService).findParties(any())

        assertNothingGenerated(contract)
    }

    @Test
    fun `signature without salt`() {
        doReturn(listOf(customer.copy(signatureSalt = null))).whenever(contractService).findParties(any())

        assertNothingGenerated(contract)
    }

    @Test
    fun `generateMissing - the contracts signed in the window`() {
        doReturn(now).whenever(clock).millis()
        doReturn(listOf("c1")).whenever(contractService).findIdsWithoutSignedContent(any(), any())

        assertEquals(1, service.generateMissing())

        // Signed 7 days to 10 minutes ago
        verify(contractService).findIdsWithoutSignedContent(
            Date(now - 7 * 24 * 60 * 60 * 1000L),
            Date(now - 10 * 60 * 1000L),
        )
        assertEquals(1, pdfPaths().size)
    }

    @Test
    fun `generateMissing - a failing contract doesn't stop the others`() {
        doReturn(now).whenever(clock).millis()
        doReturn(listOf("c0", "c1", "c2")).whenever(contractService).findIdsWithoutSignedContent(any(), any())
        doAnswer { throw IllegalStateException("boom") }.whenever(contractService).findById("c0")
        doReturn(contract.copy(id = "c2", status = ContractStatus.CANCELLED)).whenever(contractService).findById("c2")

        assertEquals(1, service.generateMissing()) // c1 only: c0 failed, c2 was skipped

        verify(contractService).setSignedContent(eq("c1"), any(), any())
    }

    @Test
    fun `generateMissing - nothing to generate`() {
        doReturn(now).whenever(clock).millis()
        doReturn(emptyList<String>()).whenever(contractService).findIdsWithoutSignedContent(any(), any())

        assertEquals(0, service.generateMissing())

        verify(renderer, never()).render(any(), any())
    }

    private fun assertNothingGenerated(contract: ContractEntity) {
        doReturn(contract).whenever(contractService).findById("c1")

        assertNull(service.generate("c1"))

        assertEquals(setOf(contentPath), storage.files.keys) // Nothing stored
        verify(renderer, never()).render(any(), any())
        verify(contractService, never()).setSignedContent(eq("c1"), any(), any())
    }
}
