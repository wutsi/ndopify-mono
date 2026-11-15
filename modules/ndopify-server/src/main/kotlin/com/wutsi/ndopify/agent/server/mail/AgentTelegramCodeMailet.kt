package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.MembershipService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * E-mails an agent its Telegram connection code, to its account's login address (not the party's contact
 * address), with the bot's link.
 */
@Service
class AgentTelegramCodeMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val membershipService: MembershipService,
    @Value("\${ndopify.telegram.username}") private val botUsername: String,
) {
    companion object {
        const val SUBJECT = "Votre code de connexion Telegram {{tenant_name}}"
    }

    fun send(agent: AgentEntity, code: String, ttlMinutes: Long) {
        val membership = membershipService.findByPartyIdOrNull(agent.party.id!!)
            ?: throw IllegalStateException("Agent ${agent.id} has no membership")
        val tenant = tenantService.findById(membershipService.currentTenantId())
        val fullName = membership.party?.fullName()?.takeIf { it.isNotBlank() }

        mailService.send(
            Email(
                recipient = Receipient(email = membership.user.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = "/mail/agent/telegram_code.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to (fullName ?: ""),
                        "telegram_code" to code,
                        "ttl" to ttlMinutes,
                        "bot_url" to "https://t.me/$botUsername",
                    ),
                ),
            )
        )
    }
}
