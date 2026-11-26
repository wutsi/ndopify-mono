package com.wutsi.ndopify.mandate.server.service.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.server.service.kyc.KycInfo
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorIdentification
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentityDocumentDataExtractorTest {
    private val kyc = mock<KycInfoExtractorIdentification>()
    private val extractor = IdentityDocumentDataExtractor(kyc)

    private val fmt = SimpleDateFormat("yyyy-MM-dd").apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun date(value: String): Date = fmt.parse(value)

    @Test
    fun `types are the identity documents`() {
        assertEquals(setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT), extractor.types)
    }

    @Test
    fun `extract - a national id`() {
        doReturn(
            KycInfo(
                type = IdentificationType.NATIONAL_ID,
                number = "AA00000000",
                firstName = "DANIEL",
                lastName = "TANDENT",
                countryCode = "CM",
                valid = true,
                confidence = 0.9,
                expiryDate = date("2032-01-12"),
                dateOfBirth = "1990-05-01",
                issueDate = "2020-01-12",
                sidesPresent = listOf("FRONT", "BACK"),
                fieldConfidences = mapOf("number" to 0.9, "firstName" to 0.8),
            ),
        ).whenever(kyc).extract(any())

        val data = extractor.extract(DocumentType.NATIONAL_ID, emptyList()) as NationalId

        assertEquals("NATIONAL_ID", data.detectedType)
        assertEquals("AA00000000", data.number)
        assertEquals("DANIEL", data.firstName)
        assertEquals("TANDENT", data.lastName)
        assertEquals("CM", data.countryCode)
        assertEquals("1990-05-01", data.dateOfBirth)
        assertEquals("2020-01-12", data.issueDate)
        assertEquals("2032-01-12", data.expiryDate)
        assertEquals(listOf("FRONT", "BACK"), data.sidesPresent)
        assertEquals(mapOf("number" to 0.9, "firstName" to 0.8), data.fieldConfidences)
        assertEquals(true, data.valid)
        assertEquals(0.9, data.confidence)
        assertEquals(0.9, data.fieldConfidences?.get("number"))
    }

    @Test
    fun `extract - a passport`() {
        doReturn(
            KycInfo(
                type = IdentificationType.PASSPORT,
                number = "01585140",
                firstName = "SAMUEL",
                lastName = "ETO'O",
                countryCode = "CM",
                valid = true,
                sidesPresent = listOf("FRONT"),
                expiryDate = date("2032-01-12"),
            ),
        ).whenever(kyc).extract(any())

        val data = extractor.extract(DocumentType.PASSPORT, emptyList())

        assertEquals(Passport::class, data::class)
        assertEquals("PASSPORT", data.detectedType)
        assertEquals("01585140", (data as Passport).number)
    }

    @Test
    fun `extract - an empty answer is a technical failure`() {
        doReturn(KycInfo()).whenever(kyc).extract(any())

        assertFailsWith<DocumentExtractionException> { extractor.extract(DocumentType.NATIONAL_ID, emptyList()) }
    }

    @Test
    fun `extract - an invalid answer without a reason is mapped`() {
        doReturn(KycInfo(type = IdentificationType.NATIONAL_ID, valid = false, invalidityReason = null))
            .whenever(kyc).extract(any())

        val data = extractor.extract(DocumentType.NATIONAL_ID, emptyList()) as NationalId

        assertEquals(false, data.valid)
    }

    @Test
    fun `extract - a detected driver license keeps its name`() {
        doReturn(KycInfo(type = IdentificationType.DRIVER_LICENSE, number = "D1", valid = true))
            .whenever(kyc).extract(any())

        val data = extractor.extract(DocumentType.NATIONAL_ID, emptyList())

        assertEquals("DRIVER_LICENSE", data.detectedType)
    }
}
