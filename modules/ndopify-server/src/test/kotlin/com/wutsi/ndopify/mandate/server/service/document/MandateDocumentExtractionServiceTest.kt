package com.wutsi.ndopify.mandate.server.service.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateDocumentService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.platform.image.ImageResizeException
import com.wutsi.ndopify.platform.image.ImageResizer
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.StorageType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDocumentExtractionServiceTest {
    companion object {
        private const val DOCUMENT_ID = 10L
        private const val MANDATE_ID = 100L
        private const val TENANT_ID = 1L
        private val NOW = Instant.parse("2026-11-26T00:00:00Z")
        private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }

    private val mandate = MandateEntity(id = MANDATE_ID, tenantId = TENANT_ID)
    private val tenant = TenantEntity(id = TENANT_ID, name = "Ndopify", timeZone = "UTC")

    private val documents = mutableMapOf<Long, MandateDocumentEntity>()
    private val pages = mutableMapOf<Long, List<MandateDocumentPageEntity>>()

    private val mandateService = mock<MandateService>()
    private val documentService = mock<MandateDocumentService>()
    private val documentDao = mock<MandateDocumentRepository>()
    private val pageDao = mock<MandateDocumentPageRepository>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val extractors = mock<DocumentDataExtractorProvider>()
    private val extractor = mock<DocumentDataExtractor>()
    private val resizer = mock<ImageResizer>()
    private val tenantService = mock<TenantService>()
    private val logger = CapturingKVLogger()

    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )

    private val service = MandateDocumentExtractionService(
        mandateService = mandateService,
        documentService = documentService,
        documentDao = documentDao,
        pageDao = pageDao,
        storageProvider = storageProvider,
        extractors = extractors,
        resizer = resizer,
        checks = DocumentChecks(0.7),
        codec = DocumentDataCodec(JsonHelper(JsonMapper())),
        tenantService = tenantService,
        clock = clock,
        tx = tx,
        logger = logger,
        batchSize = 100,
        maxAttempts = 3,
        inProgressTimeoutMinutes = 15,
    )

    private var resizedInputs = mutableListOf<ByteArray>()
    private var extractedImages: List<File> = emptyList()
    private var extractedImagesContent: List<ByteArray> = emptyList()

    private fun document(
        id: Long = DOCUMENT_ID,
        type: DocumentType = DocumentType.NATIONAL_ID,
        status: DocumentStatus = DocumentStatus.UPLOADED,
        data: String? = null,
        dataVersion: Int? = null,
        issuedAt: java.time.LocalDate? = null,
        expiresAt: java.time.LocalDate? = null,
        problemCodes: List<String> = emptyList(),
        modifiedAt: Date = Date(NOW.toEpochMilli()),
    ) = MandateDocumentEntity(
        id = id,
        tenantId = TENANT_ID,
        mandateId = MANDATE_ID,
        type = type,
        title = "Document",
        status = status,
        data = data,
        dataVersion = dataVersion,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        problemCodes = problemCodes,
        version = 0,
        createdAt = modifiedAt,
        modifiedAt = modifiedAt,
    )

    private fun page(id: Long, documentId: Long = DOCUMENT_ID, index: Int = 0, path: String) =
        MandateDocumentPageEntity(
            id = id,
            tenantId = TENANT_ID,
            documentId = documentId,
            pageIndex = index,
            path = path,
            storageType = StorageType.S3,
            mimeType = "image/png",
        )

    @BeforeEach
    fun setUp() {
        doAnswer { documents[it.getArgument<Long>(0)] }.whenever(documentDao).findByIdForUpdate(any())
        doAnswer { entity ->
            val saved = entity.getArgument<MandateDocumentEntity>(0)
            documents[saved.id!!] = saved
            saved
        }.whenever(documentDao).save(any())
        doAnswer { invocation ->
            val types = invocation.getArgument<Collection<DocumentType>>(0)
            val status = invocation.getArgument<DocumentStatus>(1)
            val pageable = invocation.getArgument<Pageable>(2)
            documents.values.filter { it.status == status && it.type in types }.sortedBy { it.id }.take(pageable.pageSize)
        }.whenever(documentDao).findByTypeInAndStatusOrderByIdAsc(any(), any(), any())
        doAnswer { invocation ->
            val status = invocation.getArgument<DocumentStatus>(0)
            val cutoff = invocation.getArgument<Date>(1)
            documents.values.filter { it.status == status && it.modifiedAt.before(cutoff) }.sortedBy { it.id }
        }.whenever(documentDao).findByStatusAndModifiedAtBefore(any(), any())
        doAnswer { invocation ->
            val type = invocation.getArgument<DocumentType>(0)
            val status = invocation.getArgument<DocumentStatus>(1)
            documents.values.filter { it.type == type && it.status != status }.sortedBy { it.id }
        }.whenever(documentDao).findByTypeAndStatusNot(any(), any())

        doAnswer { pages[it.getArgument<Long>(0)].orEmpty() }
            .whenever(pageDao).findByDocumentIdOrderByPageIndex(any())

        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(byteArrayOf(1, 2, 3)).whenever(storage).readPrefix(eq("p0.png"), any())
        doReturn(byteArrayOf(4, 5, 6)).whenever(storage).readPrefix(eq("p1.png"), any())

        resizedInputs = mutableListOf()
        doAnswer { invocation ->
            resizedInputs.add(invocation.getArgument<InputStream>(0).readBytes())
            JPEG
        }.whenever(resizer).resize(any(), any())

        doReturn(setOf(DocumentType.NATIONAL_ID)).whenever(extractors).types()
        doReturn(true).whenever(extractors).supports(DocumentType.NATIONAL_ID)
        doReturn(extractor).whenever(extractors).get(DocumentType.NATIONAL_ID)
        doAnswer { invocation ->
            extractedImages = invocation.getArgument<List<File>>(1).toList()
            extractedImagesContent = extractedImages.map { file -> file.readBytes() }
            nationalId()
        }.whenever(extractor).extract(eq(DocumentType.NATIONAL_ID), any())

        doReturn(tenant).whenever(tenantService).findById(TENANT_ID)
        doReturn(mandate).whenever(mandateService).findById(MANDATE_ID)
        doAnswer { invocation -> documents[invocation.getArgument<Long>(1)] }
            .whenever(documentService).findDocument(eq(mandate), any())
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    private fun nationalId(
        valid: Boolean = true,
        detectedType: String = "NATIONAL_ID",
        sides: List<String> = listOf("FRONT", "BACK"),
        expiryDate: String? = "2030-01-01",
        number: String = "AA1",
        fieldConfidences: Map<String, Double> = mapOf(
            "number" to 0.9,
            "firstName" to 0.9,
            "lastName" to 0.9,
        ),
    ) = NationalId(
        detectedType = detectedType,
        number = number,
        firstName = "John",
        lastName = "Doe",
        issueDate = "2020-01-12",
        expiryDate = expiryDate,
        sidesPresent = sides,
        valid = valid,
        confidence = 0.9,
        fieldConfidences = fieldConfidences,
    )

    private fun landTitle(surfaceConfidence: Double = 0.9) = LandTitle(
        detectedType = "LAND_TITLE",
        titleNumber = "TF-1",
        owners = listOf("A"),
        locality = "Yaoundé",
        surface = "500",
        issueDate = "2020-01-01",
        valid = true,
        fieldConfidences = mapOf(
            "titleNumber" to 0.9,
            "owners" to 0.9,
            "locality" to 0.9,
            "surface" to surfaceConfidence,
        ),
    )

    private fun paySlip(
        netSalaryConfidence: Double = 0.95,
        currency: String? = "XAF",
    ) = PaySlipData(
        detectedType = "PAY_SLIP",
        employerName = "Wutsi",
        employeeName = "John Doe",
        payMonth = "2026-10",
        grossSalary = BigDecimal("400000"),
        netSalary = BigDecimal("350000"),
        currency = currency,
        valid = true,
        fieldConfidences = mapOf(
            "employerName" to 0.9,
            "employeeName" to 0.95,
            "payMonth" to 0.95,
            "grossSalary" to 0.8,
            "netSalary" to netSalaryConfidence,
            "currency" to 0.95,
        ),
    )

    private fun bankPreApproval(validityDate: String? = "2027-01-31") = BankPreApprovalData(
        detectedType = "BANK_PRE_APPROVAL",
        bankName = "Afriland First Bank",
        beneficiaries = listOf("John Doe"),
        amount = BigDecimal("25000000"),
        currency = "XAF",
        issueDate = "2026-11-01",
        validityDate = validityDate,
        valid = true,
        fieldConfidences = mapOf(
            "bankName" to 0.95,
            "beneficiaries" to 0.95,
            "amount" to 0.95,
            "currency" to 0.95,
            "issueDate" to 0.95,
            "validityDate" to 0.95,
        ),
    )

    private fun proofOfFunds(accountLast4: String? = "0123") = ProofOfFundsData(
        detectedType = "PROOF_OF_FUNDS",
        bankName = "Afriland First Bank",
        accountHolder = "Jean Dupont",
        amount = BigDecimal("1200000"),
        amountKind = AmountKind.BALANCE,
        currency = "XAF",
        statementDate = "2026-11-20",
        accountLast4 = accountLast4,
        valid = true,
        fieldConfidences = mapOf(
            "bankName" to 0.95,
            "accountHolder" to 0.95,
            "amount" to 0.95,
            "amountKind" to 0.95,
            "currency" to 0.95,
            "statementDate" to 0.95,
            "accountLast4" to 0.95,
        ),
    )

    /** Makes the mocked provider handle [types]; the same extractor answers all of them. */
    private fun support(vararg types: DocumentType) {
        doReturn(types.toSet()).whenever(extractors).types()
        types.forEach { type ->
            doReturn(true).whenever(extractors).supports(type)
            doReturn(extractor).whenever(extractors).get(type)
        }
    }

    private fun extractedData(document: MandateDocumentEntity): DocumentData =
        DocumentDataCodec(JsonHelper(JsonMapper())).read(document.data, document.type, document.dataVersion)!!

    private fun add(document: MandateDocumentEntity, vararg page: MandateDocumentPageEntity) {
        documents[document.id!!] = document
        pages[document.id!!] = page.toList()
    }

    @Test
    fun `extractDue - recovers an interrupted document and extracts it`() {
        val interrupted = document(
            id = 11,
            status = DocumentStatus.EXTRACTING,
            modifiedAt = Date(NOW.toEpochMilli() - 16 * 60_000),
        )
        add(interrupted, page(200, documentId = 11, path = "p0.png"), page(201, documentId = 11, index = 1, path = "p1.png"))

        val count = service.extractDue("UTC")

        assertEquals(1, count)
        assertEquals(DocumentStatus.EXTRACTED, documents[11]!!.status)
        assertEquals(1, documents[11]!!.dataVersion)
    }

    @Test
    fun `extractDue - a document that throws does not stop the others`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"), page(101, index = 1, path = "p1.png"))
        val other = document(id = 12)
        add(other, page(200, documentId = 12, path = "p0.png"))
        doThrow(IllegalStateException("boom")).whenever(pageDao).findByDocumentIdOrderByPageIndex(DOCUMENT_ID)

        val count = service.extractDue("UTC")

        assertEquals(1, count)
        assertEquals(DocumentStatus.EXTRACTED, documents[12]!!.status)
    }

    @Test
    fun `extract - a national id ends extracted with its fields and dates`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"), page(101, index = 1, path = "p1.png"))
        var statusWhileExtracting: DocumentStatus? = null
        doAnswer { invocation ->
            statusWhileExtracting = documents[DOCUMENT_ID]!!.status
            nationalId()
        }.whenever(extractor).extract(eq(DocumentType.NATIONAL_ID), any())

        val count = service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTING, statusWhileExtracting)
        assertEquals(1, count)
        val extracted = documents[DOCUMENT_ID]!!
        assertEquals(DocumentStatus.EXTRACTED, extracted.status)
        assertEquals(1, extracted.dataVersion)
        assertNotNull(extracted.data)
        assertTrue(extracted.data!!.contains("AA1"))
        assertEquals(java.time.LocalDate.of(2020, 1, 12), extracted.issuedAt)
        assertEquals(java.time.LocalDate.of(2030, 1, 1), extracted.expiresAt)
        assertEquals(emptyList(), extracted.problemCodes)
        verify(extractor, times(1)).extract(eq(DocumentType.NATIONAL_ID), any())
    }

    @Test
    fun `extract - one derived JPEG per page, the originals untouched, the temp files deleted`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"), page(101, index = 1, path = "p1.png"))

        service.extractDue("UTC")

        assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(4, 5, 6)), resizedInputs.map { bytes -> bytes.toList() })
        assertEquals(2, extractedImages.size)
        assertEquals(2, extractedImagesContent.size)
        assertTrue(extractedImages.all { it.name.endsWith(".jpg") })
        extractedImagesContent.forEach { assertEquals(JPEG.toList(), it.toList()) }
        extractedImages.forEach { assertFalse(it.exists(), "${it.name} was not deleted") }
    }

    @Test
    fun `extract - a page the resizer rejects is unreadable, without an LLM call`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doThrow(ImageResizeException("too large")).whenever(resizer).resize(any(), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), documents[DOCUMENT_ID]!!.problemCodes)
        verify(extractor, never()).extract(any(), any())
    }

    @Test
    fun `extract - a document reset by a page change is extracted again from its new pages`() {
        add(
            document(DOCUMENT_ID, status = DocumentStatus.UPLOADED),
            page(100, path = "p0.png"),
        )

        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf<Byte>(1, 2, 3), resizedInputs.single().toList())
    }

    @Test
    fun `extract - an unreadable answer fails once, never retried`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(valid = false)).whenever(extractor).extract(eq(DocumentType.NATIONAL_ID), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), documents[DOCUMENT_ID]!!.problemCodes)
        assertNull(documents[DOCUMENT_ID]!!.data)
        assertNull(documents[DOCUMENT_ID]!!.issuedAt)
        verify(extractor, times(1)).extract(eq(DocumentType.NATIONAL_ID), any())
    }

    @Test
    fun `extract - a wrong type fails`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(detectedType = "DRIVER_LICENSE")).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a missing back page fails`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(sides = listOf("FRONT"))).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - an unreadable expiry fails with its own code`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(expiryDate = null)).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(listOf(DocumentProblemCodes.EXPIRY_DATE_UNREADABLE), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a required field under the threshold fails, an optional one does not`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(fieldConfidences = mapOf("number" to 0.6, "firstName" to 0.9, "lastName" to 0.9)))
            .whenever(extractor).extract(any(), any())

        service.extractDue("UTC")
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE), documents[DOCUMENT_ID]!!.problemCodes)

        // Optional field at 0.3: the document is still extracted
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(setOf(DocumentType.LAND_TITLE)).whenever(extractors).types()
        doReturn(true).whenever(extractors).supports(DocumentType.LAND_TITLE)
        doReturn(extractor).whenever(extractors).get(DocumentType.LAND_TITLE)
        doReturn(landTitle(surfaceConfidence = 0.3)).whenever(extractor).extract(eq(DocumentType.LAND_TITLE), any())
        documents[DOCUMENT_ID] = documents[DOCUMENT_ID]!!.copy(type = DocumentType.LAND_TITLE)

        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(emptyList(), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - an expired document is extracted with the warning, today is not`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(expiryDate = "2026-11-25")).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_EXPIRED), documents[DOCUMENT_ID]!!.problemCodes)
        assertEquals(java.time.LocalDate.of(2026, 11, 25), documents[DOCUMENT_ID]!!.expiresAt)

        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(expiryDate = "2026-11-26")).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(emptyList(), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a technical failure is retried at most three times`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doThrow(IllegalStateException("timeout")).whenever(extractor).extract(any(), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.EXTRACTION_FAILED), documents[DOCUMENT_ID]!!.problemCodes)
        verify(extractor, times(3)).extract(any(), any())
        assertNull(documents[DOCUMENT_ID]!!.data)
    }

    @Test
    fun `extract - a technical failure that ends up succeeding is extracted`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        whenever(extractor.extract(any(), any()))
            .thenThrow(IllegalStateException("timeout"))
            .thenThrow(IllegalStateException("timeout"))
            .thenReturn(nationalId())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        verify(extractor, times(3)).extract(any(), any())
    }

    @Test
    fun `extractDue - a financial document stays uploaded and is never sent to the model`() {
        add(document(DOCUMENT_ID, type = DocumentType.PAY_SLIP), page(100, path = "p0.png"))

        val count = service.extractDue("UTC")

        assertEquals(0, count)
        assertEquals(DocumentStatus.UPLOADED, documents[DOCUMENT_ID]!!.status)
        verify(extractor, never()).extract(any(), any())
    }

    @Test
    fun `extract - a pay slip ends extracted, its issuedAt the first day of the pay month`() {
        support(DocumentType.PAY_SLIP)
        add(document(DOCUMENT_ID, type = DocumentType.PAY_SLIP), page(100, path = "p0.png"))
        doReturn(paySlip()).whenever(extractor).extract(eq(DocumentType.PAY_SLIP), any())

        service.extractDue("UTC")

        val extracted = documents[DOCUMENT_ID]!!
        assertEquals(DocumentStatus.EXTRACTED, extracted.status)
        assertEquals(java.time.LocalDate.of(2026, 10, 1), extracted.issuedAt)
        assertNull(extracted.expiresAt)
        assertEquals(emptyList(), extracted.problemCodes)

        val fields = extractedData(extracted).toFields()
        assertEquals(
            listOf("employerName", "employeeName", "payMonth", "grossSalary", "netSalary", "currency"),
            fields.keys.toList(),
        )
        assertEquals("350000", fields["netSalary"])
        assertEquals("2026-10", fields["payMonth"])
    }

    @Test
    fun `extract - a pay slip whose net salary or currency is uncertain fails`() {
        support(DocumentType.PAY_SLIP)
        add(document(DOCUMENT_ID, type = DocumentType.PAY_SLIP), page(100, path = "p0.png"))
        doReturn(paySlip(netSalaryConfidence = 0.3)).whenever(extractor).extract(eq(DocumentType.PAY_SLIP), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE), documents[DOCUMENT_ID]!!.problemCodes)

        add(document(DOCUMENT_ID, type = DocumentType.PAY_SLIP), page(100, path = "p0.png"))
        doReturn(paySlip(currency = null)).whenever(extractor).extract(eq(DocumentType.PAY_SLIP), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a pre-approval valid until the tenant's today is not expired`() {
        support(DocumentType.BANK_PRE_APPROVAL)
        add(document(DOCUMENT_ID, type = DocumentType.BANK_PRE_APPROVAL), page(100, path = "p0.png"))
        doReturn(bankPreApproval("2026-11-25")).whenever(extractor)
            .extract(eq(DocumentType.BANK_PRE_APPROVAL), any())

        // At 2026-11-26T00:00Z it's still 2026-11-25 in Honolulu: the tenant's today, the pre-approval's last day
        service.extractDue("Pacific/Honolulu")

        val extracted = documents[DOCUMENT_ID]!!
        assertEquals(DocumentStatus.EXTRACTED, extracted.status)
        assertEquals(java.time.LocalDate.of(2026, 11, 1), extracted.issuedAt)
        assertEquals(java.time.LocalDate.of(2026, 11, 25), extracted.expiresAt)
        assertEquals(emptyList(), extracted.problemCodes)

        add(document(DOCUMENT_ID, type = DocumentType.BANK_PRE_APPROVAL), page(100, path = "p0.png"))
        service.extractDue("UTC")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_EXPIRED), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a pre-approval without a validity date fails with the expiry code alone`() {
        support(DocumentType.BANK_PRE_APPROVAL)
        add(document(DOCUMENT_ID, type = DocumentType.BANK_PRE_APPROVAL), page(100, path = "p0.png"))
        doReturn(bankPreApproval(null)).whenever(extractor).extract(eq(DocumentType.BANK_PRE_APPROVAL), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.EXPIRY_DATE_UNREADABLE), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a proof of funds ends extracted, its account masked to four digits`() {
        support(DocumentType.PROOF_OF_FUNDS)
        add(document(DOCUMENT_ID, type = DocumentType.PROOF_OF_FUNDS), page(100, path = "p0.png"))
        doReturn(proofOfFunds()).whenever(extractor).extract(eq(DocumentType.PROOF_OF_FUNDS), any())

        service.extractDue("UTC")

        val extracted = documents[DOCUMENT_ID]!!
        assertEquals(DocumentStatus.EXTRACTED, extracted.status)
        assertEquals(java.time.LocalDate.of(2026, 11, 20), extracted.issuedAt)
        assertNull(extracted.expiresAt)
        val fields = extractedData(extracted).toFields()
        assertEquals("0123", fields["accountLast4"])
        assertEquals("BALANCE", fields["amountKind"])
        assertFalse(Regex("""\d{8,}""").containsMatchIn(extracted.data!!), extracted.data!!)
    }

    @Test
    fun `extract - a proof of funds read as a pay slip is a mismatch`() {
        support(DocumentType.PROOF_OF_FUNDS)
        add(document(DOCUMENT_ID, type = DocumentType.PROOF_OF_FUNDS), page(100, path = "p0.png"))
        doReturn(proofOfFunds().copy(detectedType = "PAY_SLIP")).whenever(extractor)
            .extract(eq(DocumentType.PROOF_OF_FUNDS), any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.FAILED, documents[DOCUMENT_ID]!!.status)
        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a financial document's values never reach the logger`() {
        support(DocumentType.PROOF_OF_FUNDS)
        add(document(DOCUMENT_ID, type = DocumentType.PROOF_OF_FUNDS), page(100, path = "p0.png"))
        doReturn(proofOfFunds()).whenever(extractor).extract(eq(DocumentType.PROOF_OF_FUNDS), any())

        service.extractDue("UTC")

        assertTrue(logger.entries.isNotEmpty())
        logger.entries.forEach { (key, value) ->
            listOf("1200000", "Jean Dupont", "0123").forEach { secret ->
                assertFalse(value?.contains(secret) == true, "$key=$value")
            }
        }
    }

    @Test
    fun `requeueByType - a financial type is requeued, another type is left untouched`() {
        add(
            document(
                id = 1001,
                type = DocumentType.PAY_SLIP,
                status = DocumentStatus.EXTRACTED,
                data = "{}",
                dataVersion = 1,
            ),
        )
        add(
            document(
                id = 1002,
                type = DocumentType.NATIONAL_ID,
                status = DocumentStatus.EXTRACTED,
                data = "{}",
                dataVersion = 1,
            ),
        )

        val count = service.requeueByType(DocumentType.PAY_SLIP)

        assertEquals(1, count)
        assertEquals(DocumentStatus.UPLOADED, documents[1001]!!.status)
        assertEquals(DocumentStatus.EXTRACTED, documents[1002]!!.status)
    }

    @Test
    fun `extractDue - a document without an extractor doesn't fill the batch`() {
        // An unknown document older than the identity one: the batch of 1 must still pick the identity up
        add(document(id = 9, type = DocumentType.UNKNOWN), page(90, documentId = 9, path = "p0.png"))
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        val small = MandateDocumentExtractionService(
            mandateService = mandateService,
            documentService = documentService,
            documentDao = documentDao,
            pageDao = pageDao,
            storageProvider = storageProvider,
            extractors = extractors,
            resizer = resizer,
            checks = DocumentChecks(0.7),
            codec = DocumentDataCodec(JsonHelper(JsonMapper())),
            tenantService = tenantService,
            clock = clock,
            tx = tx,
            logger = logger,
            batchSize = 1,
            maxAttempts = 3,
            inProgressTimeoutMinutes = 15,
        )

        small.extractDue("UTC")

        verify(documentDao).findByTypeInAndStatusOrderByIdAsc(
            eq(setOf(DocumentType.NATIONAL_ID)),
            eq(DocumentStatus.UPLOADED),
            any(),
        )
        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(DocumentStatus.UPLOADED, documents[9]!!.status)
    }

    @Test
    fun `extract - the expiry is the tenant's today`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        doReturn(nationalId(expiryDate = "2026-11-25")).whenever(extractor).extract(any(), any())

        // In Honolulu (UTC-10) it's still 2026-11-25 at 2026-11-26T00:00Z: the document isn't expired yet
        service.extractDue("Pacific/Honolulu")

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(emptyList(), documents[DOCUMENT_ID]!!.problemCodes)

        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        service.extractDue("UTC")

        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_EXPIRED), documents[DOCUMENT_ID]!!.problemCodes)
    }

    @Test
    fun `extract - a page change wins, the stale save is skipped`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))
        var saves = 0
        doAnswer { invocation ->
            saves++
            if (saves > 1) {
                // The page change committed while the extraction ran: the document is UPLOADED again, with a new version
                documents[DOCUMENT_ID] = documents[DOCUMENT_ID]!!.copy(status = DocumentStatus.UPLOADED, data = null)
                throw ObjectOptimisticLockingFailureException(MandateDocumentEntity::class.java, DOCUMENT_ID)
            }
            val saved = invocation.getArgument<MandateDocumentEntity>(0)
            documents[saved.id!!] = saved
            saved
        }.whenever(documentDao).save(any())

        service.extractDue("UTC")

        assertEquals(DocumentStatus.UPLOADED, documents[DOCUMENT_ID]!!.status)
        assertNull(documents[DOCUMENT_ID]!!.data)
    }

    @Test
    fun `extract - the logger never receives a field value`() {
        add(document(DOCUMENT_ID), page(100, path = "p0.png"))

        service.extractDue("UTC")

        val allowed = setOf(
            "mandate_document_id",
            "document_type",
            "document_status",
            "attempt",
            "problem_codes",
        )
        assertTrue(logger.entries.isNotEmpty())
        logger.entries.forEach { (key, value) ->
            assertTrue(key in allowed, "unexpected log key $key")
            assertFalse(value == "John" || value == "Doe" || value == "AA1" || value == "2020-01-12", "$key=$value")
        }
    }

    @Test
    fun `requeueByType - resets the type's documents but an extracting one`() {
        add(
            document(
                id = 1001,
                type = DocumentType.LAND_TITLE,
                status = DocumentStatus.EXTRACTED,
                data = "{}",
                dataVersion = 1,
                issuedAt = java.time.LocalDate.of(2020, 1, 1),
                expiresAt = java.time.LocalDate.of(2021, 1, 1),
                problemCodes = listOf("DOCUMENT_EXPIRED"),
            ),
        )
        add(document(id = 1002, type = DocumentType.LAND_TITLE, status = DocumentStatus.EXTRACTING))
        add(document(id = 1003, type = DocumentType.PAY_SLIP))

        val count = service.requeueByType(DocumentType.LAND_TITLE)

        assertEquals(1, count)
        assertEquals(DocumentStatus.UPLOADED, documents[1001]!!.status)
        assertNull(documents[1001]!!.data)
        assertNull(documents[1001]!!.dataVersion)
        assertNull(documents[1001]!!.issuedAt)
        assertNull(documents[1001]!!.expiresAt)
        assertEquals(emptyList(), documents[1001]!!.problemCodes)
        assertEquals(DocumentStatus.EXTRACTING, documents[1002]!!.status)
        assertEquals(DocumentStatus.UPLOADED, documents[1003]!!.status)
    }

    @Test
    fun `requeueByType - a type without an extractor is a bad request`() {
        val exception = assertFailsWith<BadRequestException> { service.requeueByType(DocumentType.UNKNOWN) }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("type", exception.error.parameter?.name)
    }

    @Test
    fun `extract(mandate, document) - re-extracts an extracted document`() {
        TenantContext.set(TENANT_ID)
        add(
            document(
                DOCUMENT_ID,
                status = DocumentStatus.EXTRACTED,
                data = "{}",
                dataVersion = 1,
            ),
            page(100, path = "p0.png"),
        )

        service.extract(MANDATE_ID, DOCUMENT_ID)

        assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
        assertEquals(1, documents[DOCUMENT_ID]!!.dataVersion)
        verify(extractor, times(1)).extract(any(), any())
    }

    @Test
    fun `extract(mandate, document) - a document of another mandate is not found`() {
        TenantContext.set(TENANT_ID)
        doThrow(NotFoundException(Error(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND)))
            .whenever(documentService).findDocument(any(), any())

        val exception = assertFailsWith<NotFoundException> { service.extract(MANDATE_ID, 999) }

        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, exception.error.code)
    }

    @Test
    fun `extract(mandate, document) - a financial document is a bad request`() {
        TenantContext.set(TENANT_ID)
        add(document(DOCUMENT_ID, type = DocumentType.PAY_SLIP), page(100, path = "p0.png"))

        val exception = assertFailsWith<BadRequestException> { service.extract(MANDATE_ID, DOCUMENT_ID) }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("type", exception.error.parameter?.name)
        verify(extractor, never()).extract(any(), any())
    }

    private class CapturingKVLogger : KVLogger {
        val entries = mutableListOf<Pair<String, String?>>()

        override fun log() {}
        override fun setException(ex: Throwable) {}
        override fun add(key: String, value: String?) {
            entries.add(key to value)
        }
        override fun add(key: String, value: Long?) {
            entries.add(key to value?.toString())
        }
        override fun add(key: String, value: Double?) {
            entries.add(key to value?.toString())
        }
        override fun add(key: String, value: Optional<*>) {
            entries.add(key to value.orElse(null)?.toString())
        }
        override fun add(key: String, values: Collection<*>?) {
            values?.forEach { entries.add(key to it?.toString()) }
        }
        override fun add(key: String, value: Any?) {
            entries.add(key to value?.toString())
        }
    }
}
