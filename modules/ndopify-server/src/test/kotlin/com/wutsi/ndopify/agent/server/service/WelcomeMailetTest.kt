package com.wutsi.ndopify.agent.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.Test
import kotlin.test.assertEquals

class WelcomeMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = WelcomeMailet(tenantService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
    )

    private val kyc = KycCaseEntity(
        id = "kyc-1",
        tenantId = 1L,
        party = PartyEntity(
            id = 11L,
            firstName = "John",
            lastName = "Doe",
            email = "john.doe@wutsi.com",
        ),
    )

    @Test
    fun `send welcome email`() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>welcome</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send(kyc)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/agent/welcome.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "kyc_url" to "https://partner.ndopify.com/kyc",
                "recipient_name" to "John Doe",
            ),
            context.firstValue,
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("john.doe@wutsi.com", email.firstValue.recipient.email)
        assertEquals("John Doe", email.firstValue.recipient.displayName)
        assertEquals("Bienvenue sur Ndopify ! Finalisez votre profil agent", email.firstValue.subject)
        assertEquals("<html>welcome</html>", email.firstValue.body)
    }

    @Test
    fun `no email when tenant has no partner central url`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(kyc)

        verify(mailBodyResolver, never()).resolve(any(), any())
        verify(mailService, never()).send(any())
    }
}
