package com.wutsi.ndopify.npc.service.security

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class JwtAuthenticationTest {
    val principal = mock<JwtPrincipal>()

    @Test
    fun authenticated() {
        doReturn("yo").whenever(principal).name

        val auth = JwtAuthentication(principal)

        assertEquals("yo", auth.name)
        assertEquals(principal, auth.principal)
        assertEquals(null, auth.credentials)
        assertEquals(null, auth.details)
        assertEquals(0, auth.authorities?.size)
        assertEquals(true, auth.isAuthenticated)
    }

    @Test
    fun anonymous() {
        val auth = JwtAuthentication(principal)
        auth.isAuthenticated = false

        assertEquals(false, auth.isAuthenticated)
    }
}
