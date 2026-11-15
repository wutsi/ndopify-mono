package com.wutsi.ndopify.agent.server.service

/** The outcome of a `/connect <code>` from the bot, mapped to its French reply by `AgentTelegramCommand`. */
enum class AgentTelegramLinkResult {
    CONNECTED,
    INVALID_CODE,

    /** The chat is already linked to another agent account: both links are left untouched. */
    CHAT_ALREADY_LINKED,
}
