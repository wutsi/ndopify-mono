package com.wutsi.ndopify.security.server.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtpGeneratorTest {
    private val generator = OtpGenerator()

    @Test
    fun generate() {
        val set = mutableSetOf<String>()
        for (i in 1..100) {
            val otp = generator.generate()
            assertEquals(6, otp.length)
            assertTrue(otp.all { it.isDigit() })
            set.add(otp)
        }

        assertEquals(100, set.size) // Ensure all OTPs are unique
    }
}
