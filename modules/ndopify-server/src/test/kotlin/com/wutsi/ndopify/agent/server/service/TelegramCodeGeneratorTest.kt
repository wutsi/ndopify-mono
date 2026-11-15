package com.wutsi.ndopify.agent.server.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramCodeGeneratorTest {
    @Test
    fun generate() {
        val codes = (1..100).map { TelegramCodeGenerator().generate() }

        codes.forEach {
            assertEquals(6, it.length)
            assertTrue(it.all(Char::isDigit))
        }
    }
}
