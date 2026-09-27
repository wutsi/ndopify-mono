package com.wutsi.ndopify.security.server.service

import com.auth0.jwt.exceptions.TokenExpiredException
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import jakarta.servlet.http.HttpServletRequest
import org.mockito.Mockito.mock
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccessTokenServiceTest {
    private val clock = mock<Clock>()
    private val request = mock<HttpServletRequest>()
    private val service = AccessTokenService(clock, request)

    @Test
    fun create() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600
        )

        val principal = service.decode(token)
        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal.getApplication())
        assertEquals("111", principal.getSubject())
        assertEquals(111L, principal.getUserId())
        assertEquals(222L, principal.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal.getRoles().toList())
    }

    @Test
    fun `token with no tenant`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = null,
            ttlSeconds = 3600
        )

        val principal = service.decode(token)
        assertNull(principal.getTenantId())
    }

    @Test
    fun `token with no expiry`() {
        val now = 1000L
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = null
        )

        service.decode(token)
    }

    @Test
    fun `expired token`() {
        val now = 1000L
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 1000
        )

        assertFailsWith<TokenExpiredException> { service.decode(token) }
    }

    @Test
    fun `getPrincipalOrNull - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        assertNull(service.getPrincipalOrNull())
    }

    @Test
    fun `getPrincipalOrNull - valid token`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val principal = service.getPrincipalOrNull()

        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal?.getApplication())
        assertEquals(111L, principal?.getUserId())
        assertEquals(222L, principal?.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal?.getRoles()?.toList())
    }

    @Test
    fun `getPrincipalOrNull - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getPrincipalOrNull() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getPrincipal - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<UnauthorizedException> { service.getPrincipal() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getPrincipal - valid token`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val principal = service.getPrincipal()

        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal.getApplication())
        assertEquals(111L, principal.getUserId())
        assertEquals(222L, principal.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal.getRoles().toList())
    }

    @Test
    fun `getPrincipal - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getPrincipal() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }
}
