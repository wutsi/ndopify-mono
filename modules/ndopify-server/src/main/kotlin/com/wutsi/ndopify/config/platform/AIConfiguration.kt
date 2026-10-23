package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.ai.ChatClientHealthIndicator
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AIConfiguration(private val chatClientBuilder: ChatClient.Builder, val chatModel: ChatModel) {
    @Bean
    fun chatClient(): ChatClient = chatClientBuilder.build()

    @Bean
    fun chatClientHealthIndicator(): ChatClientHealthIndicator = ChatClientHealthIndicator(chatClient(), chatModel)
}
