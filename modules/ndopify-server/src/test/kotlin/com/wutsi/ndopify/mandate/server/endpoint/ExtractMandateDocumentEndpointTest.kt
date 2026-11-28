package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.GetMandateDocumentResponse
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.service.document.DocumentDataExtractor
import com.wutsi.ndopify.mandate.server.service.document.DocumentDataExtractorProvider
import com.wutsi.ndopify.mandate.server.service.document.NationalId
import com.wutsi.ndopify.mandate.server.service.document.PaySlipData
import com.wutsi.ndopify.platform.image.ImageResizer
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The ops re-run of one document: synchronous, whatever its status. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/ExtractMandateDocumentEndpoint.sql"])
class ExtractMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @MockitoBean
    private lateinit var resizer: ImageResizer

    @MockitoBean
    private lateinit var extractors: DocumentDataExtractorProvider

    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    private val storage = mock<StorageService>()
    private val extractor = mock<DocumentDataExtractor>()

    @BeforeEach
    fun setUpExtraction() {
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(byteArrayOf(1, 2, 3)).whenever(storage).readPrefix(any(), any())
        doReturn(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9)).whenever(resizer).resize(any(), any())

        doReturn(true).whenever(extractors).supports(DocumentType.NATIONAL_ID)
        doReturn(extractor).whenever(extractors).get(DocumentType.NATIONAL_ID)
        doReturn(
            NationalId(
                detectedType = "NATIONAL_ID",
                number = "AA1",
                firstName = "John",
                lastName = "Doe",
                issueDate = "2020-01-12",
                expiryDate = "2032-01-12",
                sidesPresent = listOf("FRONT", "BACK"),
                valid = true,
                fieldConfidences = mapOf("number" to 0.9, "firstName" to 0.9, "lastName" to 0.9),
            ),
        ).whenever(extractor).extract(eq(DocumentType.NATIONAL_ID), any())

        doReturn(true).whenever(extractors).supports(DocumentType.PAY_SLIP)
        doReturn(extractor).whenever(extractors).get(DocumentType.PAY_SLIP)
        doReturn(
            PaySlipData(
                detectedType = "PAY_SLIP",
                employerName = "Wutsi",
                employeeName = "John Doe",
                payMonth = "2026-10",
                grossSalary = BigDecimal("400000"),
                netSalary = BigDecimal("350000"),
                currency = "XAF",
                valid = true,
                fieldConfidences = mapOf(
                    "employeeName" to 0.9,
                    "payMonth" to 0.9,
                    "netSalary" to 0.9,
                    "currency" to 0.9,
                ),
            ),
        ).whenever(extractor).extract(eq(DocumentType.PAY_SLIP), any())
    }

    @Test
    fun `extract - a national id is extracted synchronously`() {
        val response = rest.postForEntity("/v1/mandates/100/documents/1000/extract", null, Void::class.java)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        val document = rest.getForEntity("/v1/mandates/100/documents/1000", GetMandateDocumentResponse::class.java)
            .body!!.document
        assertEquals(DocumentStatus.EXTRACTED, document.status)
        assertEquals(java.time.LocalDate.of(2020, 1, 12), document.issuedAt)
        assertEquals(java.time.LocalDate.of(2032, 1, 12), document.expiresAt)
        assertEquals(emptyList(), document.problemCodes)
        val entity = documentDao.findById(1000).orElse(null)!!
        assertEquals(1, entity.dataVersion)
        assertTrue(entity.data != null)
        verify(storage, times(2)).readPrefix(any(), any())
    }

    @Test
    fun `extract - a document of another mandate`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1100/extract",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `extract - an unknown mandate`() {
        val response = rest.postForEntity("/v1/mandates/999/documents/1000/extract", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `extract - a financial document is extracted synchronously`() {
        val response = rest.postForEntity("/v1/mandates/100/documents/1003/extract", null, Void::class.java)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        val document = rest.getForEntity("/v1/mandates/100/documents/1003", GetMandateDocumentResponse::class.java)
            .body!!.document
        assertEquals(DocumentStatus.EXTRACTED, document.status)
        assertEquals(java.time.LocalDate.of(2026, 10, 1), document.issuedAt)
        assertEquals(null, document.expiresAt)
        val fields = document.fields.associate { field -> field.key to field.value }
        assertEquals("350000", fields["netSalary"])
        assertEquals("2026-10", fields["payMonth"])
        assertEquals("XAF", fields["currency"])
    }

    @Test
    fun `extract - a type without an extractor is a bad request`() {
        val response = rest.postForEntity("/v1/mandates/100/documents/1004/extract", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("type", response.body?.error?.parameter?.name)
    }

    @Test
    fun `extract - anonymous`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/mandates/100/documents/1000/extract", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
