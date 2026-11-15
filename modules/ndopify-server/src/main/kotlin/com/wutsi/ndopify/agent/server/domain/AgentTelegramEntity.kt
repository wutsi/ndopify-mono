package com.wutsi.ndopify.agent.server.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Date

/**
 * The link between an agent account and its Telegram chat, plus the pending 6-digit connection code.
 *
 * Deliberately not @TenantId-filtered, like [com.wutsi.ndopify.security.server.domain.UserEntity]: the bot's webhook
 * carries no token and no tenant, and finds the row by the code alone. `agent_id` and `chat_id` are globally unique:
 * one chat belongs to at most one agent account.
 */
@Entity
@Table(name = "T_AGENT_TELEGRAM")
data class AgentTelegramEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val tenantId: Long = 0,

    val agentId: Long = 0,

    val code: String? = null,
    val codeExpiresAt: Date? = null,

    // Set once /connect linked the chat; the code is cleared then
    val chatId: Long? = null,
    val linkedAt: Date? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
