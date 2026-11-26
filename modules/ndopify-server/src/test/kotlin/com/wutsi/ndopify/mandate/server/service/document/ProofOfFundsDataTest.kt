package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProofOfFundsDataTest {
    private val codec = DocumentDataCodec(JsonHelper(JsonMapper()))

    private val proof = ProofOfFundsData(
        detectedType = "PROOF_OF_FUNDS",
        bankName = "Afriland First Bank",
        accountHolder = "Jean Dupont",
        amount = BigDecimal("12000000"),
        amountKind = AmountKind.BALANCE,
        currency = "XAF",
        statementDate = "2026-11-20",
        accountLast4 = "0123",
        valid = true,
        fieldConfidences = mapOf(
            "bankName" to 0.9,
            "accountHolder" to 0.9,
            "amount" to 0.9,
            "amountKind" to 0.9,
            "currency" to 0.9,
            "statementDate" to 0.9,
            "accountLast4" to 0.9,
        ),
    )

    @Test
    fun `issuedAt is the statement date, there is no expiry`() {
        assertEquals(LocalDate.of(2026, 11, 20), proof.issuedAt())
        assertNull(proof.expiresAt())
    }

    @Test
    fun `toFields - the field keys in order, the amount kind as its name`() {
        assertEquals(
            listOf("bankName", "accountHolder", "amount", "amountKind", "currency", "statementDate", "accountLast4"),
            proof.toFields().keys.toList(),
        )
        assertEquals("BALANCE", proof.toFields()["amountKind"])
        assertEquals("12000000", proof.toFields()["amount"])
        assertEquals("0123", proof.toFields()["accountLast4"])
    }

    @Test
    fun `sanitized - a full account number keeps only its last four digits`() {
        val data = proof.copy(
            accountLast4 = "12345678901",
            accountHolder = "Jean Dupont 12345678901",
        ).sanitized()

        assertEquals("8901", data.accountLast4)
        assertFalse(data.accountHolder!!.contains("12345678"), data.accountHolder)
        assertEquals(proof.fieldConfidences, data.fieldConfidences)
        assertEquals(proof.confidence, data.confidence)
    }

    @Test
    fun `sanitized - an IBAN in any text field is masked`() {
        val data = proof.copy(
            bankName = "CM21 10005 00001 12345678901 23",
            accountHolder = "Jean Dupont FR76 3000 6000 0112 3456 7890 189",
        ).sanitized()

        assertFalse(data.bankName!!.contains("CM21 10005"), data.bankName)
        assertFalse(data.accountHolder!!.contains("FR76"), data.accountHolder)
        assertEquals("•••• 0123", data.bankName)
    }

    @Test
    fun `sanitized - fewer than four digits leaves no account suffix`() {
        assertNull(proof.copy(accountLast4 = "12").sanitized().accountLast4)
        assertNull(proof.copy(accountLast4 = null).sanitized().accountLast4)
    }

    @Test
    fun `write then read - the amount kind and the masked account round-trip`() {
        val data = codec.read(codec.write(proof), DocumentType.PROOF_OF_FUNDS, codec.version()) as ProofOfFundsData

        assertEquals(AmountKind.BALANCE, data.amountKind)
        assertEquals("0123", data.accountLast4)
        assertEquals(proof, data)
    }

    @Test
    fun `required fields are the holder, the amount, the currency and the statement date`() {
        assertEquals(
            setOf("accountHolder", "amount", "currency", "statementDate"),
            DocumentRequirements.of(DocumentType.PROOF_OF_FUNDS)!!.required,
        )
    }
}
