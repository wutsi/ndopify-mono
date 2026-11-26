package com.wutsi.ndopify.contract.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateConversation
import com.wutsi.ndopify.mandate.server.service.MandateConversationService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import java.net.URLDecoder
import java.time.Instant
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractSignatureMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val conversationService = mock<MandateConversationService>()
    private val mailet = ContractSignatureMailet(tenantService, mailService, mailBodyResolver, conversationService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        eSignaturePortalUrl = "https://sign.ndopify.com",
    )
    private val contract = ContractEntity(
        id = "c-1",
        tenantId = 1L,
        number = "BAIL-2026-000042",
        type = ContractType.LEASE_AGREEMENT,
        // 23:30 UTC: already the next day in Douala (UTC+1)
        signatureDeadline = Date.from(Instant.parse("2026-12-31T23:30:00Z")),
    )
    private val mandate = MandateEntity(id = 7L, tenantId = 1L)
    private val party = ContractPartyEntity(
        id = "cp-1",
        contract = contract,
        legalName = "Ray Sponsible",
        legalEmail = "ray.sponsible@gmail.com",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>sign</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn(
            MandateConversation(
                subject = "Votre recherche : appartement à Yaounde · mandat 7",
                messageId = "mandate-1-7-abc@wutsi.com",
                inReplyTo = "mandate-1-7@wutsi.com",
                references = listOf("mandate-1-7@wutsi.com"),
            ),
        ).whenever(conversationService).of(7L)
    }

    @Test
    fun `the agent signs the e-mail`() {
        val agent = AgentEntity(
            id = 20L,
            party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
            whatsappNumber = "+237699000001",
        )

        mailet.send(contract, party, agent, mandate)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/contract/signature_request.mjml"), context.capture())
        assertEquals("Kim Possible", context.firstValue["agent_full_name"])
        assertEquals("https://wa.me/237699000001", context.firstValue["agent_whatsapp_url"])
        assertEquals(
            "Bonjour, je vous réponds au sujet de votre contrat BAIL-2026-000042.",
            URLDecoder.decode(
                (context.firstValue["agent_whatsapp_reply_url"] as String).substringAfter("?text="),
                "UTF-8",
            ),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Kim Possible", email.firstValue.sender?.displayName)
        assertEquals("Votre recherche : appartement à Yaounde · mandat 7", email.firstValue.subject)
    }

    @Test
    fun `a mandate's contract joins its conversation`() {
        mailet.send(contract, party, mandate = mandate)

        verify(conversationService).of(7L)
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Votre recherche : appartement à Yaounde · mandat 7", email.firstValue.subject)
        assertEquals("mandate-1-7-abc@wutsi.com", email.firstValue.messageId)
        assertEquals("mandate-1-7@wutsi.com", email.firstValue.inReplyTo)
        assertEquals(listOf("mandate-1-7@wutsi.com"), email.firstValue.references)
        verify(conversationService, never()).recordRoot(any(), any())
    }

    @Test
    fun `the first mandate e-mail records the relay's id`() {
        doReturn(
            MandateConversation(
                "Votre recherche : appartement à Yaounde · mandat 7",
                "mandate-1-7@wutsi.com",
                null,
                emptyList(),
            ),
        ).whenever(conversationService).of(7L)
        doReturn("0101-abc@email.amazonses.com").whenever(mailService).send(any())

        mailet.send(contract, party, mandate = mandate)

        verify(conversationService).recordRoot(7L, "0101-abc@email.amazonses.com")
    }

    @Test
    fun `a lease keeps the platform subject`() {
        mailet.send(contract, party)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Votre contrat BAIL-2026-000042 est prêt à être signé", email.firstValue.subject)
        assertNull(email.firstValue.messageId)
        assertNull(email.firstValue.inReplyTo)
        assertEquals(emptyList<String>(), email.firstValue.references)
    }

    @Test
    fun `send signature request to the party`() {
        mailet.send(contract, party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/contract/signature_request.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "contract_type" to "Contrat de bail",
                "contract_number" to "BAIL-2026-000042",
                "deadline" to "01/01/2027",
                "sign_url" to "https://sign.ndopify.com/sign/cp-1",
            ),
            context.firstValue,
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Votre contrat BAIL-2026-000042 est prêt à être signé", email.firstValue.subject)
        assertEquals("<html>sign</html>", email.firstValue.body)
    }

    @Test
    fun `no sign button without eSignature portal`() {
        doReturn(tenant.copy(eSignaturePortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(contract, party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertNull(context.firstValue["sign_url"])
    }

    @Test
    fun `a sale search is a purchase search`() {
        mailet.send(contract.copy(type = ContractType.SALE_SEARCH), party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("Mandat de recherche (achat)", context.firstValue["contract_type"])
    }

    @Test
    fun `a sale listing is a sale mandate`() {
        mailet.send(contract.copy(type = ContractType.SALE_LISTING), party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("Mandat de vente", context.firstValue["contract_type"])
    }

    @Test
    fun `other contract type`() {
        mailet.send(contract.copy(type = ContractType.UNKNOWN), party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("Contrat", context.firstValue["contract_type"])
    }

    @Test
    fun `a sale listing contract is a mandate to sell`() {
        mailet.send(contract.copy(type = ContractType.SALE_LISTING), party)

        assertEquals("Mandat de vente", contractType())
    }

    @Test
    fun `a rental listing contract is a mandate to let`() {
        mailet.send(contract.copy(type = ContractType.LISTING), party)

        assertEquals("Mandat de mise en location", contractType())
    }

    private fun contractType(): Any? {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/contract/signature_request.mjml"), context.capture())
        return context.firstValue["contract_type"]
    }
}
