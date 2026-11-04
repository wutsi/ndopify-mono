package com.wutsi.ndopify.visit.server.service

import kotlin.test.Test
import kotlin.test.assertTrue

class VisitCodeGeneratorTest {
    private val generator = VisitCodeGenerator()

    @Test
    fun `a thousand codes are four digits`() {
        val codes = (1..1000).map { generator.generate() }

        codes.forEach { code ->
            assertTrue(Regex("^\\d{4}$").matches(code), "invalid code: $code")
        }
    }
}
