package com.wutsi.ndopify

import com.wutsi.ndopify.common.dto.HttpHeader
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse

/**
 * Base class for integration tests that require a tenant ID header to be set in the request.
 * This class extends [BaseEndpointIntegrationTest] and provides functionality to automatically add a tenant ID header to all outgoing requests, unless explicitly ignored.
 * It also allows overriding the default tenant ID for specific tests.
 */
abstract class TenantAwareEndpointIntegrationTest : BaseEndpointIntegrationTest() {
    companion object {
        const val TENANT_ID = 1L
    }

    protected var ignoreTenantIdHeader: Boolean = false
    protected var overrideTenantId: Long? = null

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        request.headers.remove(HttpHeader.TENANT_ID)
        if (!ignoreTenantIdHeader) {
            request.headers.add(HttpHeader.TENANT_ID, (overrideTenantId ?: TENANT_ID).toString())
        }

        return super.intercept(request, body, execution)
    }
}
