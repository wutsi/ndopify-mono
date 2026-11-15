package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.util.http.Http
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.JdkHttpClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/**
 * Wires the Telegram bot: the client over its own [Http] (HTTP/1.1, no JDK read timeout: [Http] sets the configured
 * one on each request, and a long poll takes up to `ndopify.telegram.polling.timeout-seconds`).
 */
@Configuration
class TelegramConfiguration(
    @param:Value("\${ndopify.telegram.token}") private val token: String,
    private val objectMapper: JsonMapper,
    private val httpClientSettings: HttpClientSettings,
) {
    @Bean
    fun telegramClient(): TelegramClient = TelegramClient(token, telegramHttp())

    private fun telegramHttp() = Http(
        client = JdkHttpClientBuilder()
            .withCustomizer { builder -> builder.version(HttpClient.Version.HTTP_1_1) }
            .build(httpClientSettings.withReadTimeout(null)),
        objectMapper = objectMapper,
        readTimeout = httpClientSettings.readTimeout(),
    )
}
