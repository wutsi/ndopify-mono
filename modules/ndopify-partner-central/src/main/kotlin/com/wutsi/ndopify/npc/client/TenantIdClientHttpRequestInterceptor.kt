package com.wutsi.ndopify.npc.client

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.npc.service.TenantContext
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * Sends the current tenant to ndopify-server via the `X-Tenant-ID` header.
 *
 * - The tenant is resolved from the incoming web request (see [TenantContext]), so outside a request (startup,
 *   scheduled jobs) no header is added.
 * - A header already set by the caller is left untouched.
 * - [TenantContext] is looked up lazily: it depends on a REST client, which itself gets this interceptor.
 */
class TenantIdClientHttpRequestInterceptor(
    private val tenantContext: TenantContext,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        if (request.headers.getFirst(HttpHeader.TENANT_ID) == null) {
            request.headers.set(HttpHeader.TENANT_ID, tenantContext.getTenantId().toString())
        }
        return execution.execute(request, body)
    }
}
