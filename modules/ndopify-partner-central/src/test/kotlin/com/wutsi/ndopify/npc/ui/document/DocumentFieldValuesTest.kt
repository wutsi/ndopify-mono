package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.npc.service.TenantFixtures.FORMATTER
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DocumentFieldValuesTest {
    private fun value(key: String, value: String, currency: String? = null) =
        DocumentFieldValues.of(key, value, currency, FORMATTER)

    @Test
    fun `dates read in the tenant format`() {
        assertEquals("20 mai 2032", value("expiryDate", "2032-05-20"))
        assertEquals("14 juin 2019", value("issueDate", "2019-06-14"))
        assertEquals("08 mars 1979", value("dateOfBirth", "1979-03-08"))
        assertEquals("01 déc. 2026", value("statementDate", "2026-12-01"))
        assertEquals("31 janv. 2027", value("validityDate", "2027-01-31"))
    }

    @Test
    fun `a pay month reads as a month`() {
        assertEquals("Octobre 2026", value("payMonth", "2026-10"))
    }

    @Test
    fun `amounts read short with the document currency`() {
        assertEquals("350K\u00A0FCFA", value("netSalary", "350000", "XAF"))
        assertEquals("12,5M\u00A0FCFA", value("amount", "12500000", "XAF"))
        assertEquals("125K\u00A0EUR", value("amount", "125000", "EUR"))
        assertEquals("85K\u00A0FCFA", value("grossSalary", "85000", "XAF"))
    }

    @Test
    fun `an amount without a currency uses the tenant's`() {
        assertEquals("350K\u00A0FCFA", value("amount", "350000"))
        assertEquals("350K\u00A0FCFA", value("amount", "350000", "  "))
    }

    @Test
    fun `an account number is masked`() {
        assertEquals("•••• 0123", value("accountLast4", "0123"))
    }

    @Test
    fun `a surface is a grouped count`() {
        assertEquals(
            "1 200 m²",
            value("surface", "1200").replace('\u00A0', ' ').replace('\u202F', ' '),
        )
    }

    @Test
    fun `amount kinds are words`() {
        assertEquals("Solde", value("amountKind", "BALANCE"))
        assertEquals("Attesté", value("amountKind", "CERTIFIED"))
    }

    @Test
    fun `unparseable values read as sent`() {
        assertEquals("vers 2030", value("expiryDate", "vers 2030"))
        assertEquals("octobre", value("payMonth", "octobre"))
        assertEquals("beaucoup", value("amount", "beaucoup"))
        assertEquals("beaucoup", value("surface", "beaucoup"))
        assertEquals("12345", value("accountLast4", "12345"))
        assertEquals("LOCATAIRE", value("amountKind", "LOCATAIRE"))
    }

    @Test
    fun `other values read as sent`() {
        assertEquals("Afriland First Bank", value("bankName", "Afriland First Bank"))
        assertEquals("Chargée de clientèle", value("position", "Chargée de clientèle"))
    }

    @Test
    fun `the currency is never a row`() {
        assertEquals(setOf("currency"), DocumentFieldValues.HIDDEN)
        assertEquals("currency", DocumentFieldValues.CURRENCY)
    }
}
