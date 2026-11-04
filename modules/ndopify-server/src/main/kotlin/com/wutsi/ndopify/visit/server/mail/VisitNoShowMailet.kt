package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells a visit didn't happen. When the customer caused it (reported by its agent), only the customer is told; when the
 * agent didn't come (marked by a job), both sides are. The customer's link lets it ask for another visit.
 */
@Service
class VisitNoShowMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
) {
    companion object {
        const val SUBJECT_CUSTOMER = "{{tenant_name}}: la visite n'a pas eu lieu"
        const val SUBJECT_AGENT = "{{tenant_name}}: visite manquée avec {{customer_name}}"
        const val TEMPLATE = "/mail/visit/no_show.mjml"

        const val LABEL_CUSTOMER_ABSENT = "Le client ne s'est pas présenté."
        const val LABEL_CODE_REFUSED = "Le code de visite n'a pas pu être vérifié."
        const val LABEL_AGENT_ABSENT = "L'agent ne s'est pas présenté."
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val customerName = customer.party.fullName()
        val agentName = agent.party.fullName()
        val reason = reasonLabel(visit.noShowReason)

        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
        val customerUrl = tenant.publicPortalUrl?.removeSuffix("/")?.let { base ->
            val url = "$base/visits/${visit.id}"
            token?.let { "$url?token=$it" } ?: url
        }
        mailService.send(
            Email(
                recipient = Receipient(email = customer.party.email, displayName = customerName),
                subject = SUBJECT_CUSTOMER.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = context(
                        tenant = tenant,
                        recipientName = customerName,
                        otherParty = agentName,
                        visit = visit,
                        listing = listing,
                        reason = reason,
                        visitUrl = customerUrl,
                        agentRecipient = false,
                    ),
                ),
            ),
        )

        if (visit.noShowParty == VisitParty.AGENT) {
            val agentUrl = tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/visits/${visit.id}" }
            mailService.send(
                Email(
                    recipient = Receipient(email = agent.party.email, displayName = agentName),
                    subject = SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name)
                        .replace("{{customer_name}}", customerName),
                    body = mailBodyResolver.resolve(
                        path = TEMPLATE,
                        context = context(
                            tenant = tenant,
                            recipientName = agentName,
                            otherParty = customerName,
                            visit = visit,
                            listing = listing,
                            reason = reason,
                            visitUrl = agentUrl,
                            agentRecipient = true,
                        ),
                    ),
                ),
            )
        }
    }

    private fun context(
        tenant: TenantEntity,
        recipientName: String,
        otherParty: String,
        visit: VisitEntity,
        listing: ListingEntity,
        reason: String,
        visitUrl: String?,
        agentRecipient: Boolean,
    ): Map<String, Any?> {
        val formatter = TenantFormatter(tenant)
        return mapOf(
            "tenant_logo_url" to tenant.logoUrl,
            "tenant_name" to tenant.name,
            "recipient_name" to recipientName,
            "other_party" to otherParty,
            "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
            "visit_date" to formatter.date(visit.scheduledAt!!),
            "visit_time" to formatter.time(visit.scheduledAt),
            "reason_label" to reason,
            "agent_recipient" to agentRecipient,
            "visit_url" to visitUrl,
        )
    }

    private fun reasonLabel(reason: VisitNoShowReason?): String =
        when (reason) {
            VisitNoShowReason.CODE_REFUSED -> LABEL_CODE_REFUSED
            VisitNoShowReason.AGENT_ABSENT -> LABEL_AGENT_ABSENT
            else -> LABEL_CUSTOMER_ABSENT
        }
}
