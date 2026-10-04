package com.wutsi.ndopify.platform.logger

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class LoggerEncoderTest {
    private val encoder = LoggerEncoder()

    @ParameterizedTest
    @MethodSource("data")
    fun detect(input: String?, expected: String?) {
        kotlin.test.assertEquals(expected, encoder.encode(input))
    }

    companion object {
        @JvmStatic
        fun data() = listOf(
            Arguments.of(null, null),
            Arguments.of("hello\nworld", "\"hello world\""),
            Arguments.of("hello world", "\"hello world\""),
            Arguments.of("\"yo\"", "'yo'"),
        )
    }
}
