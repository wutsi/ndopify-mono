package com.wutsi.ndopify.esignature.config

import com.wutsi.ndopify.web.core.client.AuthorizationClientHttpRequestInterceptor
import com.wutsi.ndopify.web.core.client.LoggingClientHttpRequestInterceptor
import com.wutsi.ndopify.web.core.client.TenantIdClientHttpRequestInterceptor
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * Applied to every `RestClient.Builder` Spring Boot hands out, so all clients built from it log their calls and
 * send the current tenant and the signer's access token. Clients for global reference data opt out of
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
    fun authorizationRestClientCustomizer(accessTokenService: AccessTokenService): RestClientCustomizer =
        RestClientCustomizer { builder ->
            builder.requestInterceptor(AuthorizationClientHttpRequestInterceptor(accessTokenService.cookieName))
        }

    @Bean
    fun tenantIdRestClientCustomizer(tenantContext: TenantContext): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(TenantIdClientHttpRequestInterceptor(tenantContext)) }

    /**
     * Plain client for the contract's document, which lives in file storage: deliberately not built from the
     * Spring `RestClient.Builder`, so the `Authorization` / `X-Tenant-ID` interceptors never reach a storage URL.
     */
    @Bean
    fun contentRestClient(): RestClient = RestClient.builder().requestFactory(requestFactory()).build()

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
