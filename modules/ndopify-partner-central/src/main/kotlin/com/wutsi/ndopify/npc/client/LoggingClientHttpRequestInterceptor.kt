package com.wutsi.ndopify.npc.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * Logs every outgoing REST call: HTTP method, full URL (query parameters included) and latency.
 * The call is logged even when it fails with an I/O error, so slow timeouts stay visible.
 */
class LoggingClientHttpRequestInterceptor(
    private val logger: Logger = LoggerFactory.getLogger(LoggingClientHttpRequestInterceptor::class.java),
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val start = System.nanoTime()
        var response: ClientHttpResponse? = null
        try {
            response = execution.execute(request, body)
            return response
        } finally {
            val latencyMillis = (System.nanoTime() - start) / 1_000_000
            logger.info(">>> ${request.method} ${request.uri} ${response?.statusCode} [${latencyMillis}ms]")
        }
    }
}
