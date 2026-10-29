package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.ai.ChatClientHealthEndpointGroupsPostProcessor
import com.wutsi.ndopify.platform.ai.ChatClientHealthIndicator
import com.wutsi.ndopify.platform.ai.ChatClients
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/**
 * Two LLMs, each behind its own [ChatClient], injected by name (`@Qualifier`):
 * - [ChatClients.VISION]: Kimi (`spring.ai.openai`), for the prompts with images (KYC, listing pictures).
 * - [ChatClients.TEXT]: DeepSeek (`spring.ai.deepseek`), for the text-only prompts.
 *
 * Spring AI's `ChatClient.Builder` needs a single `ChatModel`, so it's disabled (`spring.ai.chat.client.enabled`) and
 * the clients are built here.
 */
@Configuration
class AIConfiguration(
    private val visionModel: OpenAiChatModel,
    private val textModel: DeepSeekChatModel,
    private val observationRegistry: ObjectProvider<ObservationRegistry>,
    private val clock: Clock,
    @param:Value("\${ndopify.ai.health.up-ttl-minutes}") private val healthUpTtlMinutes: Long,
    @param:Value("\${ndopify.ai.health.down-ttl-minutes}") private val healthDownTtlMinutes: Long,
) {
    @Bean(ChatClients.VISION)
    fun visionChatClient(): ChatClient =
        ChatClient.create(visionModel, observationRegistry.getIfUnique { ObservationRegistry.NOOP })

    @Bean(ChatClients.TEXT)
    fun textChatClient(): ChatClient =
        ChatClient.create(textModel, observationRegistry.getIfUnique { ObservationRegistry.NOOP })

    // Health contributors are named after the bean, minus "HealthIndicator": visionChatClient, textChatClient
    @Bean
    fun visionChatClientHealthIndicator(): ChatClientHealthIndicator =
        healthIndicator(visionChatClient(), visionModel)

    @Bean
    fun textChatClientHealthIndicator(): ChatClientHealthIndicator =
        healthIndicator(textChatClient(), textModel)

    @Bean
    fun chatClientHealthEndpointGroupsPostProcessor(): ChatClientHealthEndpointGroupsPostProcessor =
        ChatClientHealthEndpointGroupsPostProcessor(setOf(ChatClients.VISION, ChatClients.TEXT))

    private fun healthIndicator(client: ChatClient, model: ChatModel): ChatClientHealthIndicator =
        ChatClientHealthIndicator(
            client,
            model,
            clock,
            Duration.ofMinutes(healthUpTtlMinutes),
            Duration.ofMinutes(healthDownTtlMinutes),
        )
}
