package com.wutsi.ndopify.platform.ai

/**
 * Names of the [org.springframework.ai.chat.client.ChatClient] beans (see `AIConfiguration`), to inject with
 * `@Qualifier`.
 */
object ChatClients {
    /** Kimi (`spring.ai.openai`): the prompts with images. */
    const val VISION = "visionChatClient"

    /** DeepSeek (`spring.ai.deepseek`): the text-only prompts. */
    const val TEXT = "textChatClient"
}
