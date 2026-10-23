package com.wutsi.ndopify.platform.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

class ChatClientHealthIndicator(
    private val client: ChatClient,
    private val model: ChatModel,
) : HealthIndicator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ChatClientHealthIndicator::class.java)
    }

    override fun health(): Health {
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
                .withException(ex)
                .build()
        }
    }
}
