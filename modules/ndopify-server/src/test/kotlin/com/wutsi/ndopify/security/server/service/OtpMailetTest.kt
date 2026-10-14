package com.wutsi.ndopify.security.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OtpMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val membershipService = mock<MembershipService>()
    private val mailet = OtpMailet(tenantService, mailService, mailBodyResolver, membershipService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
    )

    private val user = UserEntity(
        id = 11L,
        email = "john.doe@wutsi.com",
    )

    private val membership = MembershipEntity(
        id = 1L,
        tenantId = 1L,
        user = user,
        party = PartyEntity(id = 111L, firstName = "John", lastName = "Doe"),
    )

    private val now = Date()
    private val authFactor = AuthFactorEntity(
        id = 1L,
        user = user,
        authType = AuthType.OTP,
        modifiedAt = now,
        expiresAt = Date(now.time + 5 * 60_000 + 30_000), // 5min30s
    )

    @BeforeTest
    fun setUp() {
        doReturn(1L).whenever(membershipService).currentTenantId()
        doReturn(membership).whenever(membershipService).findByUserOrNull(user)
    }

    @Test
    fun `send otp email`() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send("123456", authFactor)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/auth/otp.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "John Doe",
                "otp_code" to "123456",
                "ttl" to 5L, // rounded down
            ),
            context.firstValue,
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("john.doe@wutsi.com", email.firstValue.recipient.email)
        assertEquals("John Doe", email.firstValue.recipient.displayName)
        assertEquals("Votre code de vérification Ndopify", email.firstValue.subject)
        assertEquals("<html>otp</html>", email.firstValue.body)
    }

    @Test
    fun `user without party`() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        doReturn(membership.copy(party = null)).whenever(membershipService).findByUserOrNull(user)

        mailet.send("123456", authFactor)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("", context.firstValue["recipient_name"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertNull(email.firstValue.recipient.displayName)
    }

    @Test
    fun `user with blank name`() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        doReturn(membership.copy(party = PartyEntity(id = 111L))).whenever(membershipService).findByUserOrNull(user)

        mailet.send("123456", authFactor)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("", context.firstValue["recipient_name"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertNull(email.firstValue.recipient.displayName)
    }

    @Test
    fun `no tenant`() {
        val error = BadRequestException(Error(code = ErrorCode.HTTP_MISSING_PARAMETER))
        doThrow(error).whenever(membershipService).currentTenantId()

        val ex = assertFailsWith<BadRequestException> {
            mailet.send("123456", authFactor)
        }

        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, ex.error.code)
        verify(mailService, never()).send(any())
    }

    @Test
    fun `user who is no longer a member`() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn(null).whenever(membershipService).findByUserOrNull(user)

        mailet.send("123456", authFactor)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertNull(email.firstValue.recipient.displayName)
    }

    @Test
    fun `otp without expiry`() {
        doReturn(tenant).whenever(tenantService).findById(1L)

        assertFailsWith<IllegalArgumentException> {
            mailet.send("123456", authFactor.copy(expiresAt = null))
        }

        verify(mailService, never()).send(any())
    }
}
