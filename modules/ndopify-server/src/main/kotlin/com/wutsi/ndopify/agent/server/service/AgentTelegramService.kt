package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.server.dao.AgentTelegramRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.domain.AgentTelegramEntity
import com.wutsi.ndopify.agent.server.event.AgentTelegramCodeCreatedEvent
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.security.server.service.MembershipService
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class AgentTelegramService(
    private val dao: AgentTelegramRepository,
    private val generator: TelegramCodeGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val membershipService: MembershipService,
    @Value("\${ndopify.agent.telegram.code-ttl-minutes}") private val ttlMinutes: Long,
) {
    /**
     * Stores a new connection code for the agent, replacing any previous one, and publishes it so that
     * `AgentTelegramCodeEventListener` e-mails it.
     *
     * An agent whose chat is already linked gets 409 [ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED]: the link is final
     * in this story, so there is nothing to reconnect.
     */
    @Transactional(rollbackOn = [Exception::class])
    fun requestCode(agent: AgentEntity) {
        val existing = dao.findByAgentId(agent.id!!)
        if (existing?.chatId != null) {
            throw ConflictException(Error(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED))
        }

        val now = clock.millis()
        val code = generator.generate()
        val link = (existing ?: AgentTelegramEntity(tenantId = membershipService.currentTenantId(), agentId = agent.id!!))
            .copy(
                code = code,
                codeExpiresAt = Date(now + ttlMinutes * 60_000),
                modifiedAt = Date(now),
            )
        dao.save(link)

        eventPublisher.publishEvent(
            AgentTelegramCodeCreatedEvent(membershipService.currentTenantId(), agent.id!!, code, ttlMinutes)
        )
    }

    /** True once the agent's chat is linked: a row with a pending code only isn't connected. */
    fun isConnected(agentId: Long): Boolean = chatId(agentId) != null

    /** The agent's linked chat, or null while it isn't linked. */
    fun chatId(agentId: Long): Long? = dao.findByAgentId(agentId)?.chatId

    /**
     * The link row of a chat: the global link table names its tenant and agent, which is what a Telegram update acts
     * in (it carries no JWT and no `X-Tenant-ID`).
     */
    fun findByChatId(chatId: Long): AgentTelegramEntity? = dao.findByChatId(chatId)

    /** True when the chat is linked to an agent account. */
    fun isLinked(chatId: Long): Boolean = dao.findByChatId(chatId) != null

    /**
     * Links the chat of a `/connect <code>` to the agent account that code was issued to.
     *
     * The bot's webhook has no token and no tenant: the row is found by the code alone (a code of another tenant
     * links that agent). A null, expired or already-used code is [AgentTelegramLinkResult.INVALID_CODE]; a chat
     * already linked to another account is [AgentTelegramLinkResult.CHAT_ALREADY_LINKED], with both links untouched.
     * [`AgentTelegramRepository.link`] re-checks the code in its own update, so a code used meanwhile links nothing.
     */
    @Transactional(rollbackOn = [Exception::class])
    fun connect(code: String, chatId: Long): AgentTelegramLinkResult {
        val now = Date(clock.millis())
        val link = dao.findByCode(code)
            ?: return AgentTelegramLinkResult.INVALID_CODE
        if (link.chatId != null || link.codeExpiresAt == null || !link.codeExpiresAt.after(now)) {
            return AgentTelegramLinkResult.INVALID_CODE
        }
        if (dao.findByChatId(chatId) != null) {
            return AgentTelegramLinkResult.CHAT_ALREADY_LINKED
        }

        return if (dao.link(code, chatId, now) == 1) {
            AgentTelegramLinkResult.CONNECTED
        } else {
            AgentTelegramLinkResult.INVALID_CODE
        }
    }
}
