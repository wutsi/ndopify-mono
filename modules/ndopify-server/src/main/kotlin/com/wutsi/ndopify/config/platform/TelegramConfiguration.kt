package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramHealthIndicator
import com.wutsi.ndopify.platform.telegram.TelegramPollingTransport
import com.wutsi.ndopify.platform.telegram.TelegramUpdateHandler
import com.wutsi.ndopify.platform.telegram.TelegramWebhookTransport
import com.wutsi.ndopify.platform.util.http.Http
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.JdkHttpClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

/**
 * Wires the Telegram bot: the client over its own [Http] (HTTP/1.1, no JDK read timeout: [Http] sets the configured
 * one on each request, and a long poll takes up to `ndopify.telegram.polling.timeout-seconds`).
 */
@Configuration
class TelegramConfiguration(
    @param:Value("\${ndopify.telegram.token}") private val token: String,
    @param:Value("\${ndopify.telegram.transport}") private val transport: String,
    @param:Value("\${ndopify.telegram.polling.enabled:true}") private val pollingEnabled: Boolean,
    @param:Value("\${ndopify.telegram.polling.timeout-seconds:20}") private val pollingTimeoutSeconds: Int,
    @param:Value("\${ndopify.telegram.webhook.url:}") private val webhookUrl: String,
    @param:Value("\${ndopify.telegram.webhook.secret:}") private val webhookSecret: String,
    @param:Value("\${ndopify.telegram.health.up-ttl-minutes:5}") private val healthUpTtlMinutes: Long,
    @param:Value("\${ndopify.telegram.health.down-ttl-minutes:1}") private val healthDownTtlMinutes: Long,
    private val clock: Clock,
    private val objectMapper: JsonMapper,
    private val httpClientSettings: HttpClientSettings,
) {
    @Bean
    fun telegramClient(): TelegramClient = TelegramClient(token, telegramHttp())

    @Bean
    fun telegramUpdateHandler(client: TelegramClient): TelegramUpdateHandler = TelegramUpdateHandler(client)

    @Bean
    fun telegramPollingTransport(
        client: TelegramClient,
        handler: TelegramUpdateHandler,
    ): TelegramPollingTransport = TelegramPollingTransport(
        client,
        handler,
        pollingEnabled && transport == "polling",
        pollingTimeoutSeconds,
    )

    @Bean
    fun telegramWebhookTransport(client: TelegramClient): TelegramWebhookTransport =
        TelegramWebhookTransport(client, transport, webhookUrl, webhookSecret)

    @Bean
    fun telegramHealthIndicator(client: TelegramClient): TelegramHealthIndicator =
        TelegramHealthIndicator(
            client,
            clock,
            Duration.ofMinutes(healthUpTtlMinutes),
            Duration.ofMinutes(healthDownTtlMinutes),
        )

    private fun telegramHttp() = Http(
        client = JdkHttpClientBuilder()
            .withCustomizer { builder -> builder.version(HttpClient.Version.HTTP_1_1) }
            .build(httpClientSettings.withReadTimeout(null)),
        objectMapper = objectMapper,
        readTimeout = httpClientSettings.readTimeout(),
    )
}
