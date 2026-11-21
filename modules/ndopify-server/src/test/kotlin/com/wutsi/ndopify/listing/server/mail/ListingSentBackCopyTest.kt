package com.wutsi.ndopify.listing.server.mail

import kotlin.test.Test
import kotlin.test.assertEquals

class ListingSentBackCopyTest {
    @Test
    fun `the property label carries the id and, when known, the place`() {
        assertEquals("n° 101 — Bastos, Yaoundé", ListingSentBackCopy.property(101L, "Yaoundé", "Bastos"))
        assertEquals("n° 101 — Yaoundé", ListingSentBackCopy.property(101L, "Yaoundé", null))
        assertEquals("n° 101 — Bastos", ListingSentBackCopy.property(101L, null, "Bastos"))
        assertEquals("n° 101", ListingSentBackCopy.property(101L, null, null))
        assertEquals("n° 101", ListingSentBackCopy.property(101L, " ", "  "))
    }

    @Test
    fun `the copy names no reason`() {
        assertEquals("Votre annonce", ListingSentBackCopy.HEADLINE)
        assertEquals("a été renvoyée pour correction", ListingSentBackCopy.STATUS)
        assertEquals("Corriger l'annonce", ListingSentBackCopy.BUTTON)
    }
}
