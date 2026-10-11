package com.wutsi.ndopify.security.server.service

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
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OtpMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = OtpMailet(tenantService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
    )

    private val user = UserEntity(
        id = 11L,
        email = "john.doe@wutsi.com",
        tenantId = 2L,
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

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `send otp email`() {
        TenantContext.set(1L)
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
    fun `fall back to user home tenant when no tenant in context`() {
        doReturn(tenant.copy(id = 2L)).whenever(tenantService).findById(2L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send("123456", authFactor)

        verify(tenantService).findById(2L)
        verify(mailService).send(any())
    }

    @Test
    fun `user without party`() {
        TenantContext.set(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send("123456", authFactor.copy(user = user.copy(party = null)))

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("", context.firstValue["recipient_name"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertNull(email.firstValue.recipient.displayName)
    }

    @Test
    fun `user with blank name`() {
        TenantContext.set(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>otp</html>").whenever(mailBodyResolver).resolve(any(), any())

        mailet.send("123456", authFactor.copy(user = user.copy(party = PartyEntity(id = 111L))))

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertEquals("", context.firstValue["recipient_name"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertNull(email.firstValue.recipient.displayName)
    }

    @Test
    fun `no tenant`() {
        val ex = assertFailsWith<BadRequestException> {
            mailet.send("123456", authFactor.copy(user = user.copy(tenantId = null)))
        }

        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, ex.error.code)
        assertEquals(HttpHeader.TENANT_ID, ex.error.parameter?.name)
        verify(mailService, never()).send(any())
    }

    @Test
    fun `otp without expiry`() {
        TenantContext.set(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)

        assertFailsWith<IllegalArgumentException> {
            mailet.send("123456", authFactor.copy(expiresAt = null))
        }

        verify(mailService, never()).send(any())
    }
}
