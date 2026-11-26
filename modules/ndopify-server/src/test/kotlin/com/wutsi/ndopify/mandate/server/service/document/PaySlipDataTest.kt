package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.util.jpa.JsonHelper
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaySlipDataTest {
    private val codec = DocumentDataCodec(JsonHelper(JsonMapper()))

    private val paySlip = PaySlipData(
        detectedType = "PAY_SLIP",
        employerName = "Wutsi",
        employeeName = "John Doe",
        payMonth = "2026-10",
        grossSalary = BigDecimal("400000"),
        netSalary = BigDecimal("350000"),
        currency = "XAF",
        valid = true,
        fieldConfidences = mapOf(
            "employerName" to 0.9,
            "employeeName" to 0.9,
            "payMonth" to 0.9,
            "grossSalary" to 0.8,
            "netSalary" to 0.9,
            "currency" to 0.9,
        ),
    )

    @Test
    fun `issuedAt is the first day of the pay month`() {
        assertEquals(LocalDate.of(2026, 10, 1), paySlip.issuedAt())
        assertNull(paySlip.expiresAt())
    }

    @Test
    fun `issuedAt - a missing or unparseable pay month has none`() {
        assertNull(PaySlipData().issuedAt())
        assertNull(PaySlipData(payMonth = "last month").issuedAt())
        assertNull(PaySlipData(payMonth = "  ").issuedAt())
    }

    @Test
    fun `toFields - the field keys in order, amounts as plain numbers`() {
        assertEquals(
            listOf("employerName", "employeeName", "payMonth", "grossSalary", "netSalary", "currency"),
            paySlip.toFields().keys.toList(),
        )
        assertEquals("2026-10", paySlip.toFields()["payMonth"])
        assertEquals("350000", paySlip.toFields()["netSalary"])
    }

    @Test
    fun `read - a missing field is null and an unknown one is ignored`() {
        val data = codec.read(
            """{"detectedType":"PAY_SLIP","employeeName":"A","foo":1,"valid":true}""",
            DocumentType.PAY_SLIP,
            dataVersion = 1,
        ) as PaySlipData

        assertEquals("A", data.employeeName)
        assertNull(data.grossSalary)
        assertNull(data.netSalary)
        assertNull(data.currency)
    }

    @Test
    fun `write then read - the amounts stay decimals`() {
        val data = codec.read(codec.write(paySlip), DocumentType.PAY_SLIP, codec.version()) as PaySlipData

        assertEquals(BigDecimal("350000"), data.netSalary)
        assertEquals(BigDecimal("400000"), data.grossSalary)
        assertEquals(paySlip, data)
    }

    @Test
    fun `required fields are the employee, the pay month, the net salary and the currency`() {
        assertEquals(
            setOf("employeeName", "payMonth", "netSalary", "currency"),
            DocumentRequirements.of(DocumentType.PAY_SLIP)!!.required,
        )
    }
}
