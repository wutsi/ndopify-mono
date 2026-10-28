package com.wutsi.ndopify.platform.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Checks the LLM with a short prompt. Each check is a paid call and `/actuator/health/llm` is public, so the result is
 * cached: for [upTtl] when UP, for [downTtl] (shorter, to see a recovery sooner) when DOWN. [health] is synchronized,
 * so concurrent probes wait for the call in flight instead of each making one. A DOWN shows the error's class only,
 * never its message (the provider's text).
 */
class ChatClientHealthIndicator(
    private val client: ChatClient,
    private val model: ChatModel,
    private val clock: Clock,
    private val upTtl: Duration,
    private val downTtl: Duration,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ChatClientHealthIndicator::class.java)
    }

    private var cached: Health? = null
    private var expiresAt: Instant = Instant.MIN

    @Synchronized
    override fun health(): Health {
        val now = clock.instant()
        cached?.let { health -> if (now.isBefore(expiresAt)) return health }

        val health = check()
        cached = health
        expiresAt = now.plus(if (health.status == Status.UP) upTtl else downTtl)
        return health
    }

    private fun check(): Health {
        try {
            client.prompt()
                .user { user -> user.text("Hi") }
                .call()
                .content()
            return Health.up()
                .withDetail("model", model.options.model ?: "-")
                .withDetail("temperature", model.options.temperature ?: "-")
                .withDetail("maxTokens", model.options.maxTokens ?: "-")
                .build()
        } catch (ex: Exception) {
            LOGGER.warn("Healthcheck error", ex)
            return Health.down()
                .withDetail("error", ex.javaClass.simpleName)
                .build()
        }
    }
}
