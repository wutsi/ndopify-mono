package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.document.DocumentData
import com.wutsi.ndopify.mandate.server.service.document.DocumentDataCodec
import com.wutsi.ndopify.mandate.server.service.document.DocumentProblemCodes
import com.wutsi.ndopify.mandate.server.service.document.LandTitle
import com.wutsi.ndopify.mandate.server.service.document.NationalId
import com.wutsi.ndopify.mandate.server.service.document.Passport
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnershipVerdictServiceTest {
    companion object {
        private const val TENANT_ID = 1L
        private const val MANDATE_ID = 100L
        private val TODAY: LocalDate = LocalDate.of(2026, 11, 27)
        private val NOW: Instant = Instant.parse("2026-01-01T23:30:00Z")
        private const val CUSTOMER_NAME = "Jean Paul Mbarga"
    }

    private val codec = DocumentDataCodec(JsonHelper(JsonMapper()))
    private val documentDao = mock<MandateDocumentRepository>()
    private val customerService = mock<CustomerService>()
    private val tenantService = mock<TenantService>()
    private val logger = CapturingKVLogger()

    private var nextId = 1L

    private val service = service(90.0)

    private fun service(threshold: Double) = OwnershipVerdictService(
        documentDao = documentDao,
        codec = codec,
        customerService = customerService,
        tenantService = tenantService,
        logger = logger,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        nameThreshold = threshold,
    )

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `an exact ID and title verify the owner`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.EXTRACTED,
                nationalId("Jean Paul", "Mbarga", "2030-01-01"),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `case, accents and a hyphen match`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.EXTRACTED,
                nationalId("JEAN-PAUL", "MBARGA", "2030-01-01"),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `a missing middle name matches`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `another person does not match`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Pierre", "Durand", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.ID_NAME_MISMATCH)
    }

    @Test
    fun `an expired ID fails`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.EXTRACTED,
                nationalId("Jean Paul", "Mbarga", "2020-01-01"),
                listOf(DocumentProblemCodes.DOCUMENT_EXPIRED),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.ID_EXPIRED)
    }

    @Test
    fun `a valid passport next to an expired ID verifies`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.EXTRACTED,
                nationalId("Jean Paul", "Mbarga", "2020-01-01"),
                listOf(DocumentProblemCodes.DOCUMENT_EXPIRED),
            ),
            document(DocumentType.PASSPORT, DocumentStatus.EXTRACTED, passport("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `an unusable ID reports its problem codes`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(
            verification,
            OwnershipVerificationStatus.FAILED,
            OwnershipReasonCodes.ID_UNUSABLE to listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING),
        )
    }

    @Test
    fun `two unusable IDs deduplicate their codes`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE),
            ),
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(
                    DocumentProblemCodes.DOCUMENT_UNREADABLE,
                    DocumentProblemCodes.DOCUMENT_PAGE_MISSING,
                ),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(
            verification,
            OwnershipVerificationStatus.FAILED,
            OwnershipReasonCodes.ID_UNUSABLE to listOf(
                DocumentProblemCodes.DOCUMENT_UNREADABLE,
                DocumentProblemCodes.DOCUMENT_PAGE_MISSING,
            ),
        )
    }

    @Test
    fun `a title naming someone else fails`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Pierre Durand")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH)
    }

    @Test
    fun `a lease is not ownership evidence`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LEASE_CONTRACT, DocumentStatus.EXTRACTED),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING, OwnershipReasonCodes.DOCUMENT_MISSING)
    }

    @Test
    fun `a utility bill is not ownership evidence`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.UTILITY_BILL, DocumentStatus.EXTRACTED),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING, OwnershipReasonCodes.DOCUMENT_MISSING)
    }

    @Test
    fun `an uploaded ID keeps the verdict pending`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.UPLOADED),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING)
    }

    @Test
    fun `an extracting ID keeps the verdict pending`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTING),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING)
    }

    @Test
    fun `a missing ID is pending`() {
        val verification = evaluate(
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING, OwnershipReasonCodes.ID_MISSING)
    }

    @Test
    fun `a missing title is pending`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING, OwnershipReasonCodes.DOCUMENT_MISSING)
    }

    @Test
    fun `a failed ID wins over an extracting title`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTING),
        )

        assertVerdict(
            verification,
            OwnershipVerificationStatus.FAILED,
            OwnershipReasonCodes.ID_UNUSABLE to listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE),
        )
    }

    @Test
    fun `both checks fail, ID first`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2020-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Pierre Durand")),
        )

        assertVerdict(
            verification,
            OwnershipVerificationStatus.FAILED,
            OwnershipReasonCodes.ID_EXPIRED,
            OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH,
        )
    }

    @Test
    fun `the threshold decides`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
            service = service(96.0),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.ID_NAME_MISMATCH)
    }

    @Test
    fun `a certificate of ownership counts`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.CERTIFICATE_OF_OWNERSHIP, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `an EXTRACTED document is not re-checked`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.EXTRACTED,
                nationalId("Jean Paul", "Mbarga", "2030-01-01"),
                listOf(DocumentProblemCodes.DOCUMENT_EXPIRED),
            ),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `a blank customer name never verifies`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
            customerName = "",
        )

        assertVerdict(
            verification,
            OwnershipVerificationStatus.FAILED,
            OwnershipReasonCodes.ID_NAME_MISMATCH,
            OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH,
        )
    }

    @Test
    fun `a title with no owner is a name mismatch`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle()),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH)
    }

    @Test
    fun `an undecodable payload is unusable`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED).copy(data = "[]", dataVersion = 1),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.ID_UNUSABLE)
    }

    @Test
    fun `verify - an ID expiring tomorrow in the tenant's zone is valid`() {
        TenantContext.set(TENANT_ID)
        mockMandate(nationalId("Jean Paul", "Mbarga", "2026-01-02"))

        val verification = service.verify(MandateEntity(id = MANDATE_ID, customerId = 10))

        assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
        assertTrue(verification.reasons.isEmpty())
    }

    @Test
    fun `verify - an ID expired today in the tenant's zone fails`() {
        TenantContext.set(TENANT_ID)
        mockMandate(nationalId("Jean Paul", "Mbarga", "2026-01-01"))

        val verification = service.verify(MandateEntity(id = MANDATE_ID, customerId = 10))

        assertEquals(OwnershipVerificationStatus.FAILED, verification.status)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_EXPIRED),
            verification.reasons.map { reason -> reason.code },
        )
    }

    private fun mockMandate(id: DocumentData) {
        whenever(tenantService.findById(TENANT_ID)) doReturn TenantEntity(id = TENANT_ID, timeZone = "Africa/Douala")
        whenever(customerService.findById(10)) doReturn CustomerEntity(
            id = 10,
            party = PartyEntity(firstName = "Jean Paul", lastName = "Mbarga"),
        )
        whenever(documentDao.findByMandateIdOrderByIdDesc(MANDATE_ID)) doReturn listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, id),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )
    }

    private fun evaluate(
        vararg documents: MandateDocumentEntity,
        customerName: String = CUSTOMER_NAME,
        service: OwnershipVerdictService = this.service,
    ): OwnershipVerification = service.evaluate(documents.toList(), customerName, TODAY)

    private fun assertVerdict(
        verification: OwnershipVerification,
        status: OwnershipVerificationStatus,
        vararg reasons: Any,
    ) {
        assertEquals(status, verification.status)
        val expected = reasons.map { reason ->
            if (reason is Pair<*, *>) {
                reason.first as String to reason.second as List<String>
            } else {
                reason as String to emptyList()
            }
        }
        assertEquals(expected.map { it.first }, verification.reasons.map { it.code })
        assertEquals(expected.map { it.second }, verification.reasons.map { it.details })
    }

    private fun document(
        type: DocumentType,
        status: DocumentStatus,
        data: DocumentData? = null,
        problemCodes: List<String> = emptyList(),
    ) = MandateDocumentEntity(
        id = nextId++,
        mandateId = 1,
        type = type,
        title = "Doc",
        status = status,
        problemCodes = problemCodes,
        data = data?.let { codec.write(it) },
        dataVersion = data?.let { 1 },
    )

    private fun nationalId(firstName: String, lastName: String, expiryDate: String) = NationalId(
        detectedType = "NATIONAL_ID",
        number = "AA1",
        firstName = firstName,
        lastName = lastName,
        expiryDate = expiryDate,
        sidesPresent = listOf("FRONT", "BACK"),
        valid = true,
        fieldConfidences = confidences,
    )

    private fun passport(firstName: String, lastName: String, expiryDate: String) = Passport(
        detectedType = "PASSPORT",
        number = "AA1",
        firstName = firstName,
        lastName = lastName,
        expiryDate = expiryDate,
        sidesPresent = listOf("FRONT"),
        valid = true,
        fieldConfidences = confidences,
    )

    private fun landTitle(vararg owners: String) = LandTitle(
        detectedType = "LAND_TITLE",
        titleNumber = "TF-1",
        owners = owners.toList(),
        valid = true,
        fieldConfidences = confidences,
    )

    private val confidences = mapOf(
        "number" to 0.9,
        "firstName" to 0.9,
        "lastName" to 0.9,
        "expiryDate" to 0.9,
        "titleNumber" to 0.9,
        "owners" to 0.9,
    )

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
