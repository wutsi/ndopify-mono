package com.wutsi.ndopify.mandate.server.service.document

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinancialDocumentDataExtractorTest {
    private val chatModel = mock<ChatModel>()
    private val tenantService = mock<TenantService>()
    private val clock = Clock.fixed(Instant.parse("2026-11-25T23:30:00Z"), ZoneOffset.UTC)
    private val tenant = TenantEntity(
        id = 1,
        name = "Ndopify",
        currencyCode = "XAF",
        timeZone = "Africa/Douala",
    )
    private val extractor = FinancialDocumentDataExtractor(
        client = ChatClient.builder(chatModel).build(),
        tenantService = tenantService,
        clock = clock,
    )
    private val images = listOfNotNull(javaClass.getResource("/files/identity/cm/cni-front.png")).map { File(it.file) }

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(tenant).whenever(tenantService).findById(1)
        TenantContext.set(1)
    }

    @AfterEach
    fun tearDown() {
        TenantContext.remove()
    }

    private fun mockResponse(json: String) {
        doReturn(ChatResponse(listOf(Generation(AssistantMessage(json)))))
            .whenever(chatModel).call(any<Prompt>())
    }

    private fun prompt(): String {
        val prompt = argumentCaptor<Prompt>()
        verify(chatModel).call(prompt.capture())
        return prompt.firstValue.instructions.filterIsInstance<UserMessage>().last().text!!
    }

    @Test
    fun `types are the three financial documents`() {
        assertEquals(
            setOf(DocumentType.PAY_SLIP, DocumentType.BANK_PRE_APPROVAL, DocumentType.PROOF_OF_FUNDS),
            extractor.types,
        )
    }

    @Test
    fun `pay slip - the prompt asks for its fields, the tenant's currency and the last four digits`() {
        mockResponse(
            """
            {
                "detectedType": "PAY_SLIP",
                "employerName": "Wutsi",
                "employeeName": "John Doe",
                "payMonth": "2026-10",
                "grossSalary": 400000,
                "netSalary": 350000,
                "currency": "XAF",
                "valid": true,
                "fieldConfidences": {"employeeName": 0.9}
            }
            """.trimIndent(),
        )

        val data = extractor.extract(DocumentType.PAY_SLIP, images) as PaySlipData

        val prompt = prompt()
        listOf("employerName", "employeeName", "payMonth", "grossSalary", "netSalary", "currency").forEach { key ->
            assertTrue(prompt.contains(key), "$key missing from the prompt")
        }
        assertTrue(prompt.contains("XAF"), prompt)
        assertTrue(prompt.contains("last 4 digits"), prompt)
        assertTrue(prompt.contains("2026-11-26"), prompt)
        assertFalse(prompt.contains("{{"), prompt)

        assertEquals("John Doe", data.employeeName)
        assertEquals("2026-10", data.payMonth)
        assertEquals(BigDecimal("350000"), data.netSalary)
    }

    @Test
    fun `bank pre-approval - the prompt asks for its fields and the tenant's currency`() {
        mockResponse(
            """
            {
                "detectedType": "BANK_PRE_APPROVAL",
                "bankName": "Afriland First Bank",
                "beneficiaries": ["John Doe"],
                "amount": 25000000,
                "currency": "XAF",
                "issueDate": "2026-11-01",
                "validityDate": "2027-01-31",
                "valid": true
            }
            """.trimIndent(),
        )

        val data = extractor.extract(DocumentType.BANK_PRE_APPROVAL, images) as BankPreApprovalData

        val prompt = prompt()
        listOf("bankName", "beneficiaries", "amount", "currency", "issueDate", "validityDate").forEach { key ->
            assertTrue(prompt.contains(key), "$key missing from the prompt")
        }
        assertTrue(prompt.contains("XAF"), prompt)
        assertTrue(prompt.contains("last 4 digits"), prompt)

        assertEquals(listOf("John Doe"), data.beneficiaries)
        assertEquals(BigDecimal("25000000"), data.amount)
    }

    @Test
    fun `proof of funds - a full account number is masked before it leaves the extractor`() {
        mockResponse(
            """
            {
                "detectedType": "PROOF_OF_FUNDS",
                "bankName": "Afriland First Bank",
                "accountHolder": "John Doe",
                "amount": 12000000,
                "amountKind": "BALANCE",
                "currency": "XAF",
                "statementDate": "2026-11-20",
                "accountLast4": "CM2110005000011234567890123",
                "valid": true
            }
            """.trimIndent(),
        )

        val data = extractor.extract(DocumentType.PROOF_OF_FUNDS, images) as ProofOfFundsData

        val prompt = prompt()
        listOf("bankName", "accountHolder", "amount", "amountKind", "currency", "statementDate", "accountLast4")
            .forEach { key -> assertTrue(prompt.contains(key), "$key missing from the prompt") }
        assertTrue(prompt.contains("XAF"), prompt)
        assertTrue(prompt.contains("never write the full number or the IBAN"), prompt)

        assertEquals("0123", data.accountLast4)
        assertEquals(AmountKind.BALANCE, data.amountKind)
    }

    @Test
    fun `proof of funds - the prompt says BALANCE for a statement and CERTIFIED for an attestation`() {
        mockResponse("""{"detectedType":"PROOF_OF_FUNDS","valid":true}""")

        extractor.extract(DocumentType.PROOF_OF_FUNDS, images)

        val prompt = prompt()
        assertTrue(prompt.contains("BALANCE for a statement"), prompt)
        assertTrue(prompt.contains("CERTIFIED for an attestation"), prompt)
    }

    @Test
    fun `a null answer throws`() {
        mockResponse("null")

        assertFailsWith<DocumentExtractionException> { extractor.extract(DocumentType.PAY_SLIP, images) }
    }
}
