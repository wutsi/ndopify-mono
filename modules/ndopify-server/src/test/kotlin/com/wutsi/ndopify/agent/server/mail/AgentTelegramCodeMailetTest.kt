package com.wutsi.ndopify.agent.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import com.wutsi.ndopify.security.server.service.MembershipService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentTelegramCodeMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val membershipService = mock<MembershipService>()
    private val mailet = AgentTelegramCodeMailet(
        tenantService = tenantService,
        mailService = mailService,
        mailBodyResolver = mailBodyResolver,
        membershipService = membershipService,
        botUsername = "ndopify_bot",
    )

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
    )

    private val membership = MembershipEntity(
        id = 1L,
        tenantId = 1L,
        user = UserEntity(id = 11L, email = "ray.sponsible@gmail.com"),
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )

    private val agent = AgentEntity(id = 10L, party = PartyEntity(id = 100L))

    @BeforeTest
    fun setUp() {
        doReturn(1L).whenever(membershipService).currentTenantId()
        doReturn(membership).whenever(membershipService).findByPartyIdOrNull(100L)
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `send the code email`() {
        doReturn("<html>telegram</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send(agent, "123456", 15)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Votre code de connexion Telegram Ndopify", email.firstValue.subject)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/agent/telegram_code.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "telegram_code" to "123456",
                "ttl" to 15L,
                "bot_url" to "https://t.me/ndopify_bot",
            ),
            context.firstValue,
        )
    }

    @Test
    fun `a missing membership fails`() {
        doReturn(null).whenever(membershipService).findByPartyIdOrNull(100L)

        assertFailsWith<IllegalStateException> {
            mailet.send(agent, "123456", 15)
        }
    }
}
