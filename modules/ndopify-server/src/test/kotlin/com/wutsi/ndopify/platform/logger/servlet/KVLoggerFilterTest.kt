package com.wutsi.koki.common.logger.servlet

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.koki.common.dto.HttpHeader
import com.wutsi.koki.platform.logger.KVLogger
import com.wutsi.koki.platform.logger.servlet.KVLoggerFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.ArgumentMatchers
import org.springframework.http.HttpHeaders
import java.io.IOException
import kotlin.collections.toList
import kotlin.jvm.Throws
import kotlin.to

class KVLoggerFilterTest {
    private lateinit var kv: KVLogger
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var chain: FilterChain

    private lateinit var filter: KVLoggerFilter

    @BeforeEach
    fun setUp() {
        kv = mock()
        request = mock()
        response = mock()
        chain = mock()

        filter = KVLoggerFilter(kv)

        doReturn("/foo/bar").whenever(request).requestURI
        doReturn(201).whenever(response).status

        doReturn("client-id").whenever(request).getHeader(HttpHeader.CLIENT_ID)
        doReturn("trace-id").whenever(request).getHeader(HttpHeader.TRACE_ID)
        doReturn("device-id").whenever(request).getHeader(HttpHeader.DEVICE_ID)
        doReturn("1").whenever(request).getHeader(HttpHeader.TENANT_ID)
        doReturn("client-info").whenever(request).getHeader(HttpHeader.CLIENT_INFO)
        doReturn("fr").whenever(request).getHeader("Accept-Language")
        doReturn("https://www.google.com").whenever(request).getHeader(HttpHeaders.REFERER)
        doReturn("ze-bot").whenever(request).getHeader(HttpHeaders.USER_AGENT)
    }

    @Test
    @Throws(Exception::class)
    fun shouldLog() {
        val value1 = arrayOf("value1.1")
        val value2 = arrayOf("value2.1", "value2.2")
        doReturn(
            mapOf(
                "param1" to value1,
                "param2" to value2,
            ),
        ).whenever(request).parameterMap

        doReturn("Bearer fdoifoidiof").whenever(request).getHeader("Authorization")
        doReturn("feoireoireo").whenever(request).getHeader("X-Api-Key")

        // When
        filter.doFilter(request, response, chain)

        // Then
        verify(kv).add("http_endpoint", "/foo/bar")
        verify(kv).add("http_status", 201L)
        verify(kv).add("http_param_param1", value1.toList())
        verify(kv).add("http_param_param2", value2.toList())
        verify(kv).add("http_authorization", "***")
        verify(kv).add("http_user_agent", "ze-bot")
        verify(kv).add("http_referer", "https://www.google.com")
        verify(kv).add("api_key", "***")
        verify(kv).add("success", true)

        verify(kv).add("client_id", "client-id")
        verify(kv).add("device_id", "device-id")
        verify(kv).add("trace_id", "trace-id")
        verify(kv).add("trace_id", "trace-id")
        verify(kv).add("tenant_id", "1")
        verify(kv).add("client_info", "client-info")

        verify(kv).add("language", "fr")
        verify(kv).log()
    }

    @Test
    @Throws(Exception::class)
    fun shouldLogException() {
        // Given
        val ex = IOException("Error")
        doThrow(ex).whenever(chain).doFilter(ArgumentMatchers.any(), ArgumentMatchers.any())

        try {
            // When
            filter.doFilter(request, response, chain)

            // Then
            fail("")
        } catch (e: IOException) {
            // Then
            verify(kv).add("http_endpoint", "/foo/bar")
            verify(kv).add("http_status", 500L)
            verify(kv).add("success", false)

            verify(kv).add("client_id", "client-id")
            verify(kv).add("device_id", "device-id")
            verify(kv).add("trace_id", "trace-id")
            verify(kv).setException(e)

            verify(kv).log()
        }
    }
}
