package com.wutsi.koki.platform.logger

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationContext
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.Optional

class DynamicKVLoggerTest {
    private val context = mock<ApplicationContext>()
    private val attribute = mock<RequestAttributes>()
    private val delegate = mock<KVLogger>()
    private val kv = DynamicKVLogger(context)

    @BeforeEach
    fun setUp() {
        RequestContextHolder.setRequestAttributes(attribute)
        doReturn(delegate).whenever(context).getBean(DefaultKVLogger::class.java)
    }

    @Test
    fun log() {
        kv.log()

        verify(delegate).log()
    }

    @Test
    fun setException() {
        val ex = Exception()
        kv.setException(ex)

        verify(delegate).setException(ex)
    }

    @Test
    fun addString() {
        kv.add("foo", "bar")

        verify(delegate).add("foo", "bar")
    }

    @Test
    fun addLong() {
        kv.add("foo", 11L)

        verify(delegate).add("foo", 11L)
    }

    @Test
    fun addDouble() {
        kv.add("foo", 11.0)

        verify(delegate).add("foo", 11.0)
    }

    @Test
    fun addOptional() {
        kv.add("foo", Optional.of(11))

        verify(delegate).add("foo", Optional.of(11))
    }

    @Test
    fun addList() {
        kv.add("foo", listOf(11))

        verify(delegate).add("foo", listOf(11))
    }

    @Test
    fun addAny() {
        val any = Object()
        kv.add("foo", any)

        verify(delegate).add("foo", any)
    }

    @Test
    fun noLogger() {
        RequestContextHolder.resetRequestAttributes()
        kv.add("foo", "bar")
        kv.add("foo", 11L)
        kv.add("foo", 11.0)
        kv.add("foo", Optional.of(11))
        kv.add("foo", listOf(11))
        kv.add("foo", Object())

        verify(delegate, never()).add(any(), any<String>())
    }
}
