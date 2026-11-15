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
    fun isConnected(agentId: Long): Boolean = dao.findByAgentId(agentId)?.chatId != null
}
