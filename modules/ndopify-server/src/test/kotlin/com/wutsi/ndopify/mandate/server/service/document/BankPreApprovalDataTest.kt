package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankPreApprovalDataTest {
    private val codec = DocumentDataCodec(JsonHelper(JsonMapper()))

    private val preApproval = BankPreApprovalData(
        detectedType = "BANK_PRE_APPROVAL",
        bankName = "Afriland First Bank",
        beneficiaries = listOf("John Doe", "Jane Doe"),
        amount = BigDecimal("25000000"),
        currency = "XAF",
        issueDate = "2026-11-01",
        validityDate = "2027-01-31",
        valid = true,
        fieldConfidences = mapOf(
            "bankName" to 0.9,
            "beneficiaries" to 0.9,
            "amount" to 0.9,
            "currency" to 0.9,
            "issueDate" to 0.9,
            "validityDate" to 0.9,
        ),
    )

    @Test
    fun `issuedAt is the issue date, expiresAt the validity date`() {
        assertEquals(LocalDate.of(2026, 11, 1), preApproval.issuedAt())
        assertEquals(LocalDate.of(2027, 1, 31), preApproval.expiresAt())
    }

    @Test
    fun `an unreadable validity date has no expiry`() {
        assertNull(preApproval.copy(validityDate = null).expiresAt())
        assertNull(preApproval.copy(validityDate = "end of January").expiresAt())
    }

    @Test
    fun `toFields - the field keys in order, beneficiaries joined, amount a plain number`() {
        assertEquals(
            listOf("bankName", "beneficiaries", "amount", "currency", "issueDate", "validityDate"),
            preApproval.toFields().keys.toList(),
        )
        assertEquals("John Doe, Jane Doe", preApproval.toFields()["beneficiaries"])
        assertEquals("25000000", preApproval.toFields()["amount"])
    }

    @Test
    fun `write then read - the beneficiaries round-trip as a list`() {
        val data = codec.read(codec.write(preApproval), DocumentType.BANK_PRE_APPROVAL, codec.version())
            as BankPreApprovalData

        assertEquals(listOf("John Doe", "Jane Doe"), data.beneficiaries)
        assertEquals(preApproval, data)
    }

    @Test
    fun `required fields and the expiry rule`() {
        val requirements = DocumentRequirements.of(DocumentType.BANK_PRE_APPROVAL)!!

        assertEquals(
            setOf("bankName", "beneficiaries", "amount", "currency", "validityDate"),
            requirements.required,
        )
        assertEquals(true, requirements.requiresExpiry)
    }
}
