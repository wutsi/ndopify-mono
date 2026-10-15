package com.wutsi.ndopify.party.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KycMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = KycMailet(tenantService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
    )

    private val party = PartyEntity(
        id = 111L,
        tenantId = 2L,
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@wutsi.com",
    )

    private val case = KycCaseEntity(id = "case-1", party = party, status = KycStatus.VERIFIED)

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `send verified email`() {
        TenantContext.set(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>verified</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send(case)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/party/kyc_verified.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "John Doe",
                "kyc_url" to "https://partner.ndopify.com/kyc",
                "portal_url" to "https://partner.ndopify.com",
            ),
            context.firstValue,
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("john.doe@wutsi.com", email.firstValue.recipient.email)
        assertEquals("John Doe", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Votre vérification KYC a réussi", email.firstValue.subject)
        assertEquals("<html>verified</html>", email.firstValue.body)
    }

    @Test
    fun `send rejected email`() {
        TenantContext.set(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>rejected</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send(case.copy(status = KycStatus.REJECTED))

        verify(mailBodyResolver).resolve(eq("/mail/party/kyc_rejected.mjml"), any())

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Mise à jour concernant votre vérification KYC", email.firstValue.subject)
        assertEquals("<html>rejected</html>", email.firstValue.body)
    }

    @Test
    fun `fall back to party tenant when no tenant in context`() {
        doReturn(tenant.copy(id = 2L)).whenever(tenantService).findById(2L)
        doReturn("<html>verified</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send(case)

        verify(tenantService).findById(2L)
        verify(mailService).send(any())
    }

    @Test
    fun `no tenant`() {
        val ex = assertFailsWith<BadRequestException> {
            mailet.send(case.copy(party = party.copy(tenantId = null)))
        }

        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, ex.error.code)
        assertEquals(HttpHeader.TENANT_ID, ex.error.parameter?.name)
        verify(mailService, never()).send(any())
    }
}
