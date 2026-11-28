package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.OwnershipLocation
import com.wutsi.ndopify.mandate.dto.OwnershipLocationStatus
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
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
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
import kotlin.test.assertNull
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
    private val locationService = mock<LocationService>()
    private val logger = CapturingKVLogger()

    private var nextId = 1L

    private val service = service(90.0)

    private fun service(threshold: Double) = OwnershipVerdictService(
        documentDao = documentDao,
        codec = codec,
        customerService = customerService,
        tenantService = tenantService,
        locationService = locationService,
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
    fun `a valid ID next to a failed one verifies`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE),
            ),
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    }

    @Test
    fun `an extracting ID keeps the verdict pending despite a failed one`() {
        val verification = evaluate(
            document(
                DocumentType.NATIONAL_ID,
                DocumentStatus.FAILED,
                problemCodes = listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE),
            ),
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTING),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
        )

        assertVerdict(verification, OwnershipVerificationStatus.PENDING)
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

    @Test
    fun `a title in the mandate's city matches case and accents`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "yaoundé")),
            mandateCity = yaounde,
            resolveCity = { name -> if (name == "yaoundé") yaounde else null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
        assertEquals("Yaounde", verification.location.mandateCity)
        assertEquals("yaoundé", verification.location.documentLocality)
    }

    @Test
    fun `a title in another known city fails the owner`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val douala = city(2370201, "Douala", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
            mandateCity = yaounde,
            resolveCity = { name -> if (name == "Douala") douala else null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.PROPERTY_MISMATCH)
        assertEquals(OwnershipLocationStatus.MISMATCH, verification.location.status)
        assertEquals("Yaounde", verification.location.mandateCity)
        assertEquals("Douala", verification.location.documentLocality)
    }

    @Test
    fun `an unresolved quarter is a hint, not a failure`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Mvog-Ada")),
            mandateCity = yaounde,
            resolveCity = { null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
        assertEquals("Yaounde", verification.location.mandateCity)
        assertEquals("Mvog-Ada", verification.location.documentLocality)
    }

    @Test
    fun `the arrondissement resolves when the locality is blank`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(
                DocumentType.LAND_TITLE,
                DocumentStatus.EXTRACTED,
                landTitle("Jean Paul Mbarga", locality = null, arrondissement = "Yaoundé"),
            ),
            mandateCity = yaounde,
            resolveCity = { name -> if (name == "Yaoundé") yaounde else null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
        assertEquals("Yaoundé", verification.location.documentLocality)
    }

    @Test
    fun `no mandate city is a hint, never a mismatch`() {
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
            mandateCity = null,
            resolveCity = { null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
        assertEquals("", verification.location.mandateCity)
        assertEquals("Douala", verification.location.documentLocality)
    }

    @Test
    fun `a non-city mandate location is a hint`() {
        val neighborhood = city(237010101, "Bastos", "CM", LocationType.NEIGHBORHOOD)
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
            mandateCity = neighborhood,
            resolveCity = { null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
        assertEquals("Bastos", verification.location.mandateCity)
    }

    @Test
    fun `a title with no locality is a hint`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
            mandateCity = yaounde,
            resolveCity = { null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
        assertEquals("", verification.location.documentLocality)
    }

    @Test
    fun `a title naming someone else keeps the name mismatch`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val douala = city(2370201, "Douala", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Pierre Durand", locality = "Douala")),
            mandateCity = yaounde,
            resolveCity = { name -> if (name == "Douala") douala else null },
        )

        assertVerdict(verification, OwnershipVerificationStatus.FAILED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH)
        assertEquals(OwnershipLocationStatus.UNKNOWN, verification.location.status)
    }

    @Test
    fun `a valid title in the mandate's city wins over another city`() {
        val yaounde = city(2370101, "Yaounde", "CM")
        val douala = city(2370201, "Douala", "CM")
        val verification = evaluate(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Yaoundé")),
            mandateCity = yaounde,
            resolveCity = { name ->
                if (name == "Yaoundé") {
                    yaounde
                } else if (name == "Douala") {
                douala
            } else {
                null
            }
            },
        )

        assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
        assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
    }

    @Test
    fun `verify - the mandate's city is resolved and the title compared`() {
        TenantContext.set(TENANT_ID)
        val yaounde = city(2370101, "Yaounde", "CM")
        whenever(tenantService.findById(TENANT_ID)) doReturn TenantEntity(id = TENANT_ID, timeZone = "Africa/Douala")
        whenever(customerService.findById(10)) doReturn CustomerEntity(
            id = 10,
            party = PartyEntity(firstName = "Jean Paul", lastName = "Mbarga"),
        )
        whenever(documentDao.findByMandateIdOrderByIdDesc(MANDATE_ID)) doReturn listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2026-01-02")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Yaoundé")),
        )
        whenever(locationService.findByIdOrNull(2370101)) doReturn yaounde
        whenever(locationService.findCity("Yaoundé", "CM")) doReturn yaounde

        val verification = service.verify(MandateEntity(id = MANDATE_ID, customerId = 10, cityId = 2370101))

        assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
        assertEquals(OwnershipLocationStatus.MATCH, verification.location.status)
    }

    @Test
    fun `verify - an unknown cityId is a hint, mandateCity blank`() {
        TenantContext.set(TENANT_ID)
        whenever(tenantService.findById(TENANT_ID)) doReturn TenantEntity(id = TENANT_ID, timeZone = "Africa/Douala")
        whenever(customerService.findById(10)) doReturn CustomerEntity(
            id = 10,
            party = PartyEntity(firstName = "Jean Paul", lastName = "Mbarga"),
        )
        whenever(documentDao.findByMandateIdOrderByIdDesc(MANDATE_ID)) doReturn listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2026-01-02")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        )

        val verification = service.verify(MandateEntity(id = MANDATE_ID, customerId = 10, cityId = -1))

        assertEquals(OwnershipVerificationStatus.VERIFIED, verification.status)
        assertEquals(OwnershipLocationStatus.UNRESOLVED, verification.location.status)
        assertEquals("", verification.location.mandateCity)
    }

    @Test
    fun `the log line carries no city or locality`() {
        TenantContext.set(TENANT_ID)
        val yaounde = city(2370101, "Yaounde", "CM")
        val douala = city(2370201, "Douala", "CM")
        whenever(tenantService.findById(TENANT_ID)) doReturn TenantEntity(id = TENANT_ID, timeZone = "Africa/Douala")
        whenever(customerService.findById(10)) doReturn CustomerEntity(
            id = 10,
            party = PartyEntity(firstName = "Jean Paul", lastName = "Mbarga"),
        )
        whenever(documentDao.findByMandateIdOrderByIdDesc(MANDATE_ID)) doReturn listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2026-01-02")),
            document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga", locality = "Douala")),
        )
        whenever(locationService.findByIdOrNull(2370101)) doReturn yaounde
        whenever(locationService.findCity("Douala", "CM")) doReturn douala

        service.verify(MandateEntity(id = MANDATE_ID, customerId = 10, cityId = 2370101))

        assertTrue(logger.entries.none { it.second?.contains("Douala") == true || it.second?.contains("Yaounde") == true })
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
        mandateCity: LocationEntity? = null,
        resolveCity: (String) -> LocationEntity? = { null },
    ): OwnershipVerification = service.evaluate(documents.toList(), customerName, TODAY, mandateCity, resolveCity)

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

    private fun landTitle(
        vararg owners: String,
        locality: String? = null,
        arrondissement: String? = null,
    ) = LandTitle(
        detectedType = "LAND_TITLE",
        titleNumber = "TF-1",
        owners = owners.toList(),
        arrondissement = arrondissement,
        locality = locality,
        valid = true,
        fieldConfidences = confidences,
    )

    private fun city(id: Long, name: String, country: String, type: LocationType = LocationType.CITY) =
        LocationEntity(id = id, name = name, country = country, type = type, asciiName = name)

    private fun resolve(vararg pairs: Pair<String, LocationEntity>): (String) -> LocationEntity? =
        { name -> pairs.firstOrNull { it.first == name }?.second }

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
