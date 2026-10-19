package com.wutsi.ndopify.esignature.config

import com.wutsi.ndopify.esignature.client.AuthorizationClientHttpRequestInterceptor
import com.wutsi.ndopify.esignature.client.LoggingClientHttpRequestInterceptor
import com.wutsi.ndopify.esignature.client.TenantIdClientHttpRequestInterceptor
import com.wutsi.ndopify.esignature.service.TenantContext
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate

/**
 * Applied to every `RestClient.Builder` Spring Boot hands out, so all clients built from it log their calls and
 * send the current tenant and the signer's access token. Clients for global reference data opt out of
 * both (see `NdTenantClient`).
 */
@Configuration
class RestConfiguration {
    @Bean
    fun loggingRestClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(LoggingClientHttpRequestInterceptor()) }

    @Bean
    fun authorizationRestClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(AuthorizationClientHttpRequestInterceptor()) }

    @Bean
    fun tenantIdRestClientCustomizer(tenantContext: TenantContext): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(TenantIdClientHttpRequestInterceptor(tenantContext)) }

    /**
     * Plain client for the contract's document, which lives in file storage: deliberately not built from the
     * Spring `RestClient.Builder`, so the `Authorization` / `X-Tenant-ID` interceptors never reach a storage URL.
     */
    @Bean
    fun contentRestClient(): RestClient = RestClient.create()

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
