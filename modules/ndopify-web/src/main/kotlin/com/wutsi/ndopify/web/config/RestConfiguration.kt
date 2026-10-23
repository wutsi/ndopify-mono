package com.wutsi.ndopify.web.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class RestConfiguration(
    @param:Value("\${spring.http.clients.connect-timeout}") private val connectTimeout: Duration,
    @param:Value("\${spring.http.clients.read-timeout}") private val readTimeout: Duration,
) {
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
