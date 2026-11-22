package com.wutsi.ndopify.npc.ui.login

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignInTargetTest {
    @Test
    fun keepsTheRequestedPath() {
        assertEquals("/mandates/42", SignInTarget.of("/mandates/42")?.redirectUrl())
    }

    @Test
    fun keepsTheHomePage() {
        assertEquals("/", SignInTarget.of("/")?.redirectUrl())
    }

    @Test
    fun keepsTheQueryString() {
        assertEquals("/listings/42?match=7", SignInTarget.of("/listings/42", "match=7")?.redirectUrl())
    }

    @Test
    fun keepsTheFragment() {
        val target = SignInTarget.of("/mandates/42", "match=7")?.copy(fragment = "match-7")
        assertEquals("/mandates/42?match=7#match-7", target?.redirectUrl())
    }

    @Test
    fun rejectsNonLocalPaths() {
        assertNull(SignInTarget.of("https://evil.example/mandates/42"))
        assertNull(SignInTarget.of("//evil.example/mandates/42"))
        assertNull(SignInTarget.of("/\\evil.example/mandates/42"))
        assertNull(SignInTarget.of("/mandates/42\r\nSet-Cookie: x=y"))
        assertNull(SignInTarget.of("mandates/42"))
        assertNull(SignInTarget.of(null))
        assertNull(SignInTarget.of("/mandates/42", "a\r\nb"))
    }

    @Test
    fun rejectsLoginAndPublicPaths() {
        listOf(
            "/login", "/login/connection", "/logout", "/error", "/error/403", "/assets/dist/app.min.js",
            "/join", "/join/identity", "/find-property", "/offer-property", "/locations",
            "/sw.js", "/offline.html", "/manifest.json", "/favicon.ico",
        ).forEach { assertNull(SignInTarget.of(it), it) }
    }

    @Test
    fun validatesTheFragment() {
        assertEquals("match-7", SignInTarget.fragmentOf("#match-7"))
        assertEquals("visits-title", SignInTarget.fragmentOf("visits-title"))
        assertNull(SignInTarget.fragmentOf(null))
        assertNull(SignInTarget.fragmentOf(""))
        assertNull(SignInTarget.fragmentOf("#"))
        assertNull(SignInTarget.fragmentOf("a".repeat(SignInTarget.MAX_FRAGMENT_LENGTH + 1)))
        assertNull(SignInTarget.fragmentOf("match 7"))
        assertNull(SignInTarget.fragmentOf("bad\r\nheader"))
    }

    @Test
    fun ignoresAnUnsafeStoredTarget() {
        assertNull(SignInTarget("https://evil.example/mandates/42").redirectUrl())
        assertNull(SignInTarget("//evil.example/mandates/42").redirectUrl())
        assertNull(SignInTarget("/login").redirectUrl())
    }
}
