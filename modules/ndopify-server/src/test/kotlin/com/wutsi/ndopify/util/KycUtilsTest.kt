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
        assertEquals(1.0, KycUtils.verifyName("Herve Tchepannou", "Hervé Tchepannou"))
    }

    @Test
    fun `match ignores special characters`() {
        assertEquals(1.0, KycUtils.verifyName("Jean-Pierre Dupont", "Jean Pierre Dupont"))
    }

    @Test
    fun `match ignores extra whitespace`() {
        assertEquals(1.0, KycUtils.verifyName("John    Smith", "John Smith"))
    }

    @Test
    fun `match ignores leading and trailing whitespace`() {
        assertEquals(1.0, KycUtils.verifyName("  John Smith  ", "John Smith"))
    }

    @Test
    fun `handles inverted name order`() {
        assertEquals(1.0, KycUtils.verifyName("John Smith", "Smith John"))
    }

    @Test
    fun `high score for minor typo`() {
        assertTrue(KycUtils.verifyName("Jonathan Smith", "Johnathan Smith") > 0.9)
    }

    @Test
    fun `high score for similar names`() {
        assertTrue(KycUtils.verifyName("Jonathan Bernard Smith", "Johnathan B. Smith") > 0.75)
    }

    @Test
    fun `low score for completely different names`() {
        assertTrue(KycUtils.verifyName("John Smith", "Alice Johnson") < 0.7)
    }

    @Test
    fun `empty input returns zero`() {
        assertEquals(0.0, KycUtils.verifyName("", "John Smith"))
    }

    @Test
    fun `both empty returns perfect score`() {
        assertEquals(1.0, KycUtils.verifyName("", ""))
    }

    @Test
    fun `score is between zero and one`() {
        val score = KycUtils.verifyName("Herve", "Harvey")
        assertTrue(score in 0.0..1.0)
    }
}
