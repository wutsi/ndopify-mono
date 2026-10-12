package com.wutsi.ndopify.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KycUtilsTest {
    @Test
    fun `exact match returns perfect score`() {
        assertEquals(1.0, KycUtils.verifyName("John Smith", "John Smith"))
    }

    @Test
    fun `match is case insensitive`() {
        assertEquals(1.0, KycUtils.verifyName("JOHN SMITH", "john smith"))
    }

    @Test
    fun `match ignores accents`() {
        assertEquals(1.0, KycUtils.verifyName("Herve Tchepannou", "Hervé Tchépannou"))
    }

    @Test
    fun `match treats hyphens as spaces`() {
        assertEquals(1.0, KycUtils.verifyName("Jean-Pierre Dupont", "Jean Pierre Dupont"))
    }

    @Test
    fun `match ignores special characters`() {
        assertEquals(1.0, KycUtils.verifyName("O'Neil Smith", "ONeil Smith"))
    }

    @Test
    fun `match ignores extra whitespace`() {
        assertEquals(1.0, KycUtils.verifyName("  John    Smith  ", "John Smith"))
    }

    @Test
    fun `match ignores name order`() {
        assertTrue(KycUtils.verifyName("John Smith", "Smith John") >= 0.95)
    }

    @Test
    fun `missing middle name in input scores 95 percent`() {
        assertEquals(0.95, KycUtils.verifyName("Herve Tchepannou", "Bansi Herve Tchepannou"))
    }

    @Test
    fun `missing middle name in input scores 95 percent regardless of order`() {
        assertEquals(0.95, KycUtils.verifyName("Tchepannou Herve", "Bansi Herve Tchepannou"))
    }

    @Test
    fun `extra name in input scores high`() {
        assertTrue(KycUtils.verifyName("Bansi Herve Tchepannou", "Herve Tchepannou") >= 0.95)
    }

    @Test
    fun `minor typo scores high`() {
        assertTrue(KycUtils.verifyName("Jonathan Smith", "Johnathan Smith") > 0.9)
    }

    @Test
    fun `abbreviated middle name scores high`() {
        assertTrue(KycUtils.verifyName("Jonathan Bernard Smith", "Johnathan B. Smith") > 0.75)
    }

    @Test
    fun `completely different names score low`() {
        assertTrue(KycUtils.verifyName("John Smith", "Alice Johnson") < 0.7)
    }

    @Test
    fun `empty input returns zero`() {
        assertEquals(0.0, KycUtils.verifyName("", "John Smith"))
    }

    @Test
    fun `empty db name returns zero`() {
        assertEquals(0.0, KycUtils.verifyName("John Smith", ""))
    }

    @Test
    fun `both empty returns zero`() {
        assertEquals(0.0, KycUtils.verifyName("", ""))
    }

    @Test
    fun `blank names return zero`() {
        assertEquals(0.0, KycUtils.verifyName("   ", "   "))
    }

    @Test
    fun `score is between zero and one`() {
        listOf(
            "Herve" to "Harvey",
            "John Smith" to "Alice Johnson",
            "Jonathan Bernard Smith" to "Johnathan B. Smith",
            "A" to "Bansi Herve Tchepannou",
        ).forEach { (input, db) ->
            val score = KycUtils.verifyName(input, db)
            assertTrue(score in 0.0..1.0, "$input vs $db: $score")
        }
    }
}
