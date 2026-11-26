package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DocumentChecksTest {
    private val checks = DocumentChecks(0.7)

    private val confidences = mapOf(
        "number" to 0.9,
        "firstName" to 0.9,
        "lastName" to 0.9,
        "titleNumber" to 0.9,
        "owners" to 0.9,
        "locality" to 0.9,
        "parcelReference" to 0.9,
        "grantor" to 0.9,
        "attorney" to 0.9,
        "purpose" to 0.9,
        "holderName" to 0.9,
        "issueDate" to 0.9,
        "address" to 0.9,
        "summary" to 0.9,
        "expiryDate" to 0.9,
    )

    private fun nationalId(
        detectedType: String? = "NATIONAL_ID",
        sidesPresent: List<String>? = listOf("FRONT", "BACK"),
        expiryDate: String? = "2030-01-01",
        number: String? = "AA1",
        fieldConfidences: Map<String, Double>? = confidences,
    ) = NationalId(
        detectedType = detectedType,
        number = number,
        firstName = "John",
        lastName = "Doe",
        expiryDate = expiryDate,
        sidesPresent = sidesPresent,
        valid = true,
        fieldConfidences = fieldConfidences,
    )

    @Test
    fun `an unreadable document is the only code`() {
        val codes = checks.check(DocumentType.NATIONAL_ID, nationalId().copy(valid = false))

        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), codes)
    }

    @Test
    fun `a document of another type is a mismatch`() {
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH),
            checks.check(DocumentType.LAND_TITLE, LandTitle(
                detectedType = "UTILITY_BILL",
                titleNumber = "TF-1",
                owners = listOf("A"),
                locality = "Yaoundé",
                valid = true,
                fieldConfidences = confidences,
            )),
        )
    }

    @Test
    fun `a null detected type is not a mismatch`() {
        assertEquals(emptyList(), checks.check(DocumentType.LAND_TITLE, LandTitle(
            detectedType = null,
            titleNumber = "TF-1",
            owners = listOf("A"),
            locality = "Yaoundé",
            valid = true,
            fieldConfidences = confidences,
        )))
    }

    @Test
    fun `an unreadable document missing a side is a missing page, not unreadable`() {
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING),
            checks.check(DocumentType.NATIONAL_ID, nationalId(sidesPresent = listOf("FRONT")).copy(valid = false)),
        )
    }

    @Test
    fun `a national id needs both sides`() {
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING),
            checks.check(DocumentType.NATIONAL_ID, nationalId(sidesPresent = listOf("FRONT"))),
        )
        assertEquals(emptyList(), checks.check(DocumentType.NATIONAL_ID, nationalId()))
    }

    @Test
    fun `a passport needs its data page`() {
        val passport = Passport(
            detectedType = "PASSPORT",
            number = "P1",
            firstName = "John",
            lastName = "Doe",
            expiryDate = "2030-01-01",
            sidesPresent = listOf("BACK"),
            valid = true,
            fieldConfidences = confidences,
        )

        assertEquals(listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING), checks.check(DocumentType.PASSPORT, passport))
    }

    @Test
    fun `an unreadable expiry is its own code`() {
        listOf(null, "next year", "  ").forEach { expiry ->
            assertEquals(
                listOf(DocumentProblemCodes.EXPIRY_DATE_UNREADABLE),
                checks.check(DocumentType.NATIONAL_ID, nationalId(expiryDate = expiry)),
                "expiry: $expiry",
            )
        }
        assertEquals(emptyList(), checks.check(DocumentType.NATIONAL_ID, nationalId(expiryDate = "2030-01-01")))
    }

    @Test
    fun `a type without expiry is not checked for one`() {
        assertEquals(emptyList(), checks.check(DocumentType.LAND_TITLE, LandTitle(
            detectedType = "LAND_TITLE",
            titleNumber = "TF-1",
            owners = listOf("A"),
            locality = "Yaoundé",
            valid = true,
            fieldConfidences = confidences,
        )))
    }

    @Test
    fun `a required field blank or under the confidence threshold fails`() {
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE),
            checks.check(DocumentType.NATIONAL_ID, nationalId(number = "  ")),
        )
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE),
            checks.check(DocumentType.NATIONAL_ID, nationalId(fieldConfidences = confidences + ("number" to 0.69))),
        )
        assertEquals(
            emptyList(),
            checks.check(DocumentType.NATIONAL_ID, nationalId(fieldConfidences = confidences + ("number" to 0.7))),
        )
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE),
            checks.check(DocumentType.NATIONAL_ID, nationalId(fieldConfidences = confidences - "number")),
        )
    }

    @Test
    fun `an optional field can be low or absent`() {
        val data = LandTitle(
            detectedType = "LAND_TITLE",
            titleNumber = "TF-1",
            owners = listOf("A"),
            locality = "Yaoundé",
            surface = "500",
            valid = true,
            fieldConfidences = confidences + ("surface" to 0.3),
        )

        assertEquals(emptyList(), checks.check(DocumentType.LAND_TITLE, data))
    }

    @Test
    fun `an occupancy document needs an address or a locality`() {
        val withLowAddress = UtilityBill(
            detectedType = "UTILITY_BILL",
            holderName = "A",
            address = "Rue 1",
            issueDate = "2020-01-01",
            valid = true,
            fieldConfidences = confidences + ("address" to 0.4),
        )
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE),
            checks.check(DocumentType.UTILITY_BILL, withLowAddress),
        )

        assertEquals(
            emptyList(),
            checks.check(
                DocumentType.UTILITY_BILL,
                withLowAddress.copy(locality = "Yaoundé", fieldConfidences = confidences + ("address" to 0.4)),
            ),
        )
    }

    @Test
    fun `another document needs its summary`() {
        assertEquals(
            listOf(DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE),
            checks.check(DocumentType.OTHER, Other(summary = " ", valid = true, fieldConfidences = confidences)),
        )
    }

    @Test
    fun `every failing check is listed in the fixed order`() {
        val data = NationalId(
            detectedType = "DRIVER_LICENSE",
            number = "",
            firstName = "John",
            lastName = "Doe",
            expiryDate = null,
            sidesPresent = listOf("FRONT"),
            valid = true,
            fieldConfidences = mapOf("firstName" to 0.9, "lastName" to 0.9),
        )

        assertEquals(
            listOf(
                DocumentProblemCodes.DOCUMENT_TYPE_MISMATCH,
                DocumentProblemCodes.DOCUMENT_PAGE_MISSING,
                DocumentProblemCodes.DOCUMENT_FIELD_LOW_CONFIDENCE,
                DocumentProblemCodes.EXPIRY_DATE_UNREADABLE,
            ),
            checks.check(DocumentType.NATIONAL_ID, data),
        )
    }
}
