package com.wutsi.koki.platform.logger

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.slf4j.Logger
import java.io.IOException
import java.util.Optional
import kotlin.test.assertEquals

class DefaultKVLoggerTest {
    private lateinit var logger: Logger
    private lateinit var kv: DefaultKVLogger

    @BeforeEach
    fun setUp() {
        logger = mock(Logger::class.java)
        kv = DefaultKVLogger(logger, LoggerEncoder())
    }

    @Test
    fun shouldLog() {
        // Given
        kv.add("foo", "bar")
        kv.add("john", "doe")
        kv.add("valueLong", 1L)
        kv.add("valueInt", 2)
        kv.add("valueDouble", 3.5)
        kv.add("valueOpt", Optional.of(1))
        kv.add("valueCollection", listOf(1, 2))

        // When
        kv.log()

        // Then
        verify(logger).info("foo=bar john=doe valueCollection=\"1 2\" valueDouble=3.5 valueInt=2 valueLong=1 valueOpt=1")
    }

    @Test
    fun shouldNotLogWhenEmpty() {
        // When
        kv.log()

        // Then
        verify(logger, never()).info(anyString())
    }

    @Test
    fun shouldLogMultiValue() {
        // Given
        kv.add("foo", "john")
        kv.add("foo", "doe")

        // When
        kv.log()

        // Then
        verify(logger).info("foo=\"john doe\"")
    }

    @Test
    fun shouldLogWithSortedKeys() {
        // Given
        kv.add("Z", "bar")
        kv.add("A", "doe")

        // When
        kv.log()

        // Then
        verify(logger).info("A=doe Z=bar")
    }

    @Test
    fun shouldLogException() {
        // Given
        val ex = IOException("error")
        kv.setException(ex)

        // When
        kv.log()

        // Then
        val msg = ArgumentCaptor.forClass(String::class.java)
        val exception = ArgumentCaptor.forClass(Throwable::class.java)
        verify(logger).error(msg.capture(), exception.capture())
        assertEquals("exception=java.io.IOException exception_message=error", msg.value)
        assertEquals(ex, exception.value)
    }

    @Test
    @Throws(Exception::class)
    fun shouldLogAMaximumOf10000Characters() {
        // Given
        val ch100 =
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
        val longString = StringBuilder()
        for (i in 0..999) {
            longString.append(ch100).append('\n')
        }

        kv.add("foo", "bar")
        kv.add("john", "smith")
        kv.add("name", longString.toString())

        // When
        kv.log()

        // Then
        val msg = ArgumentCaptor.forClass(String::class.java)
        verify(logger).info(msg.capture())
        assertEquals(DefaultKVLogger.MAX_LENGTH, msg.value.length)
    }

    @Test
    fun shouldNotLogNullValue() {
        // Given
        kv.add("foo", "bar")
        kv.add("john", null as String?)
        kv.add("roger", null as Long?)
        kv.add("milla", null as Double?)
        kv.add("stephen", null as Collection<Long>?)
        kv.add("tataw", null as Any?)

        // When
        kv.log()

        // Then
        verify(logger).info("foo=bar")
    }

    @Test
    fun shouldNotLogOptionalEmpty() {
        // Given
        kv.add("foo", "bar")
        kv.add("john", Optional.empty<String>())

        // When
        kv.log()

        // Then
        verify(logger).info("foo=bar")
    }
}
