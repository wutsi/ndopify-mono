package com.wutsi.koki.platform.logger.servlet

import com.wutsi.koki.common.dto.HttpHeader
import com.wutsi.koki.platform.logger.KVLogger
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import java.io.IOException

class KVLoggerFilter(private val kv: KVLogger) : Filter {
    @Throws(IOException::class, ServletException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        val startTime = System.currentTimeMillis()
        try {
            filterChain.doFilter(servletRequest, servletResponse)
            log(startTime, (servletResponse as HttpServletResponse).status, servletRequest as HttpServletRequest, kv)
            kv.log()
        } catch (e: Exception) {
            log(startTime, 500, servletRequest as HttpServletRequest, kv)
            kv.setException(e)
            kv.log()
            throw e
        }
    }

    private fun log(
        startTime: Long,
        status: Int,
        request: HttpServletRequest,
        kv: KVLogger,
    ) {
        val latencyMillis = System.currentTimeMillis() - startTime

        kv.add("success", status / 100 == 2)
        kv.add("latency_millis", latencyMillis)

        kv.add("http_status", status.toLong())
        kv.add("http_endpoint", request.requestURI)
        kv.add("http_method", request.method)
        kv.add("http_referer", request.getHeader(HttpHeaders.REFERER))
        kv.add("http_user_agent", request.getHeader(HttpHeaders.USER_AGENT))
        kv.add("trace_id", request.getHeader(HttpHeader.TRACE_ID))
        kv.add("client_id", request.getHeader(HttpHeader.CLIENT_ID))
        kv.add("device_id", request.getHeader(HttpHeader.DEVICE_ID))
        kv.add("tenant_id", request.getHeader(HttpHeader.TENANT_ID))
        kv.add("client_info", request.getHeader(HttpHeader.CLIENT_INFO))

        val params = request.parameterMap
        params.keys.forEach { kv.add("http_param_$it", params[it]?.toList()) }

        request.getHeader("Authorization")?.let { kv.add("http_authorization", "***") }
        request.getHeader("X-Api-Key")?.let { kv.add("api_key", "***") }
        request.getHeader("Accept-Language")?.let { kv.add("language", it) }
    }
}
