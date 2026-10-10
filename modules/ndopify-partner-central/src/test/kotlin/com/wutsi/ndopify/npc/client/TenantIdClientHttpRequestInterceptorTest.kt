package com.wutsi.ndopify.npc.client

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.npc.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import kotlin.test.assertFailsWith

class TenantIdClientHttpRequestInterceptorTest {
    private val tenantContext = mock<TenantContext> {
        on { getTenantId() } doReturn 11L
    }
    private lateinit var server: MockRestServiceServer
    private lateinit var rest: RestClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().requestInterceptor(TenantIdClientHttpRequestInterceptor(tenantContext))
        server = MockRestServiceServer.bindTo(builder).build()
        rest = builder.baseUrl("http://localhost:8080").build()
    }

    @Test
    fun addsTenantIdHeader() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(header(HttpHeader.TENANT_ID, "11"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").retrieve().body<String>()

        server.verify()
    }

    @Test
    fun keepsTenantIdHeaderSetByCaller() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(header(HttpHeader.TENANT_ID, "22"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        rest.get().uri("/v1/agents").header(HttpHeader.TENANT_ID, "22").retrieve().body<String>()

        server.verify()
        verify(tenantContext, never()).getTenantId()
    }

    @Test
    fun failsWhenTenantCannotBeResolved() {
        val tenantContext = mock<TenantContext> {
            on { getTenantId() } doThrow IllegalStateException("No tenant found for URL: http://unknown")
        }
        val builder = RestClient.builder().requestInterceptor(TenantIdClientHttpRequestInterceptor(tenantContext))
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(ExpectedCount.never(), requestTo("http://localhost:8080/v1/agents"))
        val rest = builder.baseUrl("http://localhost:8080").build()

        assertFailsWith<IllegalStateException> {
            rest.get().uri("/v1/agents").retrieve().body<String>()
        }

        server.verify()
    }
}
