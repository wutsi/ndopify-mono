package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.JsonHelper
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentDataCodecTest {
    private val codec = DocumentDataCodec(JsonHelper(JsonMapper()))

    private fun roundTrip(data: DocumentData, type: DocumentType): DocumentData? =
        codec.read(codec.write(data), type, codec.version())

    @Test
    fun `write then read - each subclass`() {
        val datas = listOf(
            DocumentType.NATIONAL_ID to NationalId(
                detectedType = "NATIONAL_ID",
                number = "AA1",
                firstName = "John",
                lastName = "Doe",
                dateOfBirth = "1990-05-01",
                issueDate = "2020-01-12",
                expiryDate = "2032-01-12",
                countryCode = "CM",
                sidesPresent = listOf("FRONT", "BACK"),
                valid = true,
                confidence = 0.9,
                fieldConfidences = mapOf("number" to 0.9),
            ),
            DocumentType.PASSPORT to Passport(detectedType = "PASSPORT", number = "P1", valid = true),
            DocumentType.LAND_TITLE to LandTitle(
                detectedType = "LAND_TITLE",
                titleNumber = "TF-1",
                owners = listOf("A", "B"),
                department = "Centre",
                arrondissement = "Mfoundi",
                locality = "Yaoundé",
                surface = "500",
                issueDate = "2020-01-01",
                valid = true,
            ),
            DocumentType.CERTIFICATE_OF_OWNERSHIP to CertificateOfOwnership(
                detectedType = "CERTIFICATE_OF_OWNERSHIP",
                titleNumber = "CO-1",
                valid = true,
            ),
            DocumentType.SURVEY_PLAN to SurveyPlan(
                detectedType = "SURVEY_PLAN",
                parcelReference = "PB-1",
                locality = "Yaoundé",
                surface = "500",
                coordinates = "3.8, 11.5",
                valid = true,
            ),
            DocumentType.POWER_OF_ATTORNEY to PowerOfAttorney(
                detectedType = "POWER_OF_ATTORNEY",
                grantor = "A",
                attorney = "B",
                purpose = "Sale",
                notary = "Me X",
                issueDate = "2020-01-01",
                expiryDate = "2021-01-01",
                valid = true,
            ),
            DocumentType.LEASE_CONTRACT to LeaseContract(
                detectedType = "LEASE_CONTRACT",
                holderName = "A",
                address = "Rue 1",
                valid = true,
            ),
            DocumentType.UTILITY_BILL to UtilityBill(
                detectedType = "UTILITY_BILL",
                holderName = "A",
                locality = "Yaoundé",
                valid = true,
            ),
            DocumentType.TAX_RECEIPT to TaxReceipt(
                detectedType = "TAX_RECEIPT",
                holderName = "A",
                provider = "DGI",
                valid = true,
            ),
            DocumentType.OTHER to Other(detectedType = "OTHER", title = "Acte", summary = "Un acte", valid = true),
            DocumentType.PAY_SLIP to PaySlipData(
                detectedType = "PAY_SLIP",
                employerName = "Wutsi",
                employeeName = "John Doe",
                payMonth = "2026-10",
                grossSalary = BigDecimal("400000"),
                netSalary = BigDecimal("350000"),
                currency = "XAF",
                valid = true,
            ),
            DocumentType.BANK_PRE_APPROVAL to BankPreApprovalData(
                detectedType = "BANK_PRE_APPROVAL",
                bankName = "Afriland First Bank",
                beneficiaries = listOf("A", "B"),
                amount = BigDecimal("25000000"),
                currency = "XAF",
                issueDate = "2026-11-01",
                validityDate = "2027-01-31",
                valid = true,
            ),
            DocumentType.PROOF_OF_FUNDS to ProofOfFundsData(
                detectedType = "PROOF_OF_FUNDS",
                bankName = "Afriland First Bank",
                accountHolder = "A",
                amount = BigDecimal("12000000"),
                amountKind = AmountKind.CERTIFIED,
                currency = "XAF",
                statementDate = "2026-11-20",
                accountLast4 = "0123",
                valid = true,
            ),
        )

        datas.forEach { (type, data) -> assertEquals(data, roundTrip(data, type)) }
    }

    @Test
    fun `read - a missing field is null`() {
        val data = codec.read(
            """{"detectedType":"NATIONAL_ID","number":"AA1","valid":true}""",
            DocumentType.NATIONAL_ID,
            dataVersion = 1,
        ) as NationalId

        assertEquals("AA1", data.number)
        assertNull(data.dateOfBirth)
        assertNull(data.expiryDate)
        assertNull(data.sidesPresent)
        assertNull(data.fieldConfidences)
    }

    @Test
    fun `read - an unknown field is ignored`() {
        val data = codec.read(
            """{"detectedType":"NATIONAL_ID","number":"AA1","unknown":1,"valid":true}""",
            DocumentType.NATIONAL_ID,
            dataVersion = 1,
        ) as NationalId

        assertEquals("AA1", data.number)
    }

    @Test
    fun `read - an older version with only a few fields`() {
        val data = codec.read(
            """{"number":"AA1","valid":true}""",
            DocumentType.NATIONAL_ID,
            dataVersion = 0,
        ) as NationalId

        assertEquals("AA1", data.number)
        assertNull(data.firstName)
        assertNull(data.fieldConfidences)
    }

    @Test
    fun `read - a type without data, a malformed payload and a blank one are null`() {
        assertNull(codec.read("""{"a":1}""", DocumentType.UNKNOWN, 1))
        assertNull(codec.read("{not json", DocumentType.NATIONAL_ID, 1))
        assertNull(codec.read("[]", DocumentType.NATIONAL_ID, 1))
        assertNull(codec.read(null, DocumentType.NATIONAL_ID, 1))
        assertNull(codec.read("  ", DocumentType.NATIONAL_ID, 1))
    }

    @Test
    fun `read - a pay slip missing the gross salary`() {
        val data = codec.read(
            """{"detectedType":"PAY_SLIP","employeeName":"A","payMonth":"2026-10","netSalary":350000,"valid":true}""",
            DocumentType.PAY_SLIP,
            dataVersion = 1,
        ) as PaySlipData

        assertNull(data.grossSalary)
        assertEquals(BigDecimal("350000"), data.netSalary)
        assertEquals("2026-10", data.payMonth)
    }

    @Test
    fun `version is 1`() {
        assertEquals(1, codec.version())
    }

    @Test
    fun `toFields - identity keys in order, no internal field`() {
        val fields = NationalId(
            detectedType = "NATIONAL_ID",
            number = "AA1",
            firstName = "John",
            lastName = "Doe",
            dateOfBirth = "1990-05-01",
            issueDate = "2020-01-12",
            expiryDate = "2032-01-12",
            countryCode = "CM",
            sidesPresent = listOf("FRONT"),
            valid = true,
            confidence = 0.9,
            fieldConfidences = mapOf("number" to 0.9),
        ).toFields()

        assertEquals(
            listOf("number", "firstName", "lastName", "dateOfBirth", "issueDate", "expiryDate", "countryCode"),
            fields.keys.toList(),
        )
        assertEquals("1990-05-01", fields["dateOfBirth"])
    }

    @Test
    fun `toFields - owners are joined`() {
        val fields = LandTitle(titleNumber = "TF-1", owners = listOf("A", "B"), valid = true).toFields()

        assertEquals("A, B", fields["owners"])
        assertEquals("TF-1", fields["titleNumber"])
        assertEquals(
            listOf("titleNumber", "owners", "department", "arrondissement", "locality", "surface", "issueDate"),
            fields.keys.toList(),
        )
    }

    @Test
    fun `issuedAt and expiresAt parse the ISO dates`() {
        val id = NationalId(issueDate = "2020-01-12", expiryDate = "2032-01-12")
        assertEquals(LocalDate.of(2020, 1, 12), id.issuedAt())
        assertEquals(LocalDate.of(2032, 1, 12), id.expiresAt())

        val title = LandTitle(issueDate = "2020-01-01")
        assertEquals(LocalDate.of(2020, 1, 1), title.issuedAt())
        assertNull(title.expiresAt())

        val poa = PowerOfAttorney(issueDate = "2020-01-01", expiryDate = "2021-01-01")
        assertEquals(LocalDate.of(2020, 1, 1), poa.issuedAt())
        assertEquals(LocalDate.of(2021, 1, 1), poa.expiresAt())

        assertNull(SurveyPlan().issuedAt())
        assertNull(Other().expiresAt())
    }

    @Test
    fun `issuedAt and expiresAt - a blank or unparseable date is null`() {
        assertNull(NationalId(expiryDate = "next year").expiresAt())
        assertNull(NationalId(expiryDate = "  ").expiresAt())
        assertNull(NationalId(expiryDate = null).expiresAt())
    }
}
