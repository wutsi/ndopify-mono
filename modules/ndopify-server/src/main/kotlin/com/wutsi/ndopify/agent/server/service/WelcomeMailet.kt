package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

@Service
class WelcomeMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "Bienvenue sur {{tenant_name}} ! Finalisez votre profil agent"
    }

    fun send(kyc: KycCaseEntity) {
        val tenant = tenantService.findById(kyc.tenantId!!)
        if (tenant.partnerCentralUrl == null) {
            return
        }

        val party = kyc.party
        val email = Email(
            recipient = Receipient(
                displayName = party.fullName(),
                email = party.email
            ),
            subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
            body = mailBodyResolver.resolve(
                path = "/mail/agent/welcome.mjml",
                context = mapOf(
                    "tenant_logo_url" to tenant.logoUrl,
                    "tenant_name" to tenant.name,
                    "kyc_url" to "${tenant.partnerCentralUrl}/kyc/${kyc.id}/start",
                    "recipient_name" to party.fullName(),
                )
            )
        )
        mailService.send(email)
    }
}
