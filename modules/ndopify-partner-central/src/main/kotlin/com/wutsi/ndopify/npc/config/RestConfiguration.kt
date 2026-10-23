package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.client.AuthorizationClientHttpRequestInterceptor
import com.wutsi.ndopify.npc.client.LoggingClientHttpRequestInterceptor
import com.wutsi.ndopify.npc.client.TenantIdClientHttpRequestInterceptor
import com.wutsi.ndopify.npc.service.TenantContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * Applied to every `RestClient.Builder` Spring Boot hands out, so all clients built from it log their calls and
 * send the current tenant and the signed-in agent's access token. Clients for global reference data opt out of
 * both (see `NdTenantClient`).
 */
@Configuration
class RestConfiguration(
    @param:Value("\${spring.http.clients.connect-timeout}") private val connectTimeout: Duration,
    @param:Value("\${spring.http.clients.read-timeout}") private val readTimeout: Duration,
) {
    @Bean
    fun loggingRestClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(LoggingClientHttpRequestInterceptor()) }

    @Bean
    fun authorizationRestClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(AuthorizationClientHttpRequestInterceptor()) }

    @Bean
    fun tenantIdRestClientCustomizer(tenantContext: TenantContext): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(TenantIdClientHttpRequestInterceptor(tenantContext)) }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate(requestFactory())
    }

    /**
     * For the clients built by hand, which `spring.http.clients.*` doesn't reach: without it they'd wait forever.
     */
    private fun requestFactory(): ClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout)
            setReadTimeout(readTimeout)
        }
}
