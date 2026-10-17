package com.wutsi.ndopify.contract.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
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
    private val mailet = ContractSignatureMailet(tenantService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
    )
    private val contract = ContractEntity(
        id = "c-1",
        tenantId = 1L,
        number = "BAIL-2026-000042",
        type = ContractType.LEASE_AGREEMENT,
        contentUrl = "https://cdn.ndopify.com/contracts/c-1.pdf",
        // 23:30 UTC: already the next day in Douala (UTC+1)
        validUntil = Date.from(Instant.parse("2026-12-31T23:30:00Z")),
    )
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
                "sign_url" to "https://partner.ndopify.com/contracts/c-1/sign?party-id=cp-1",
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
    fun `no sign button without partner central`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(contract, party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertNull(context.firstValue["sign_url"])
    }

    @Test
    fun `other contract type`() {
        mailet.send(contract.copy(type = ContractType.UNKNOWN), party)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("Contrat", context.firstValue["contract_type"])
    }
}
