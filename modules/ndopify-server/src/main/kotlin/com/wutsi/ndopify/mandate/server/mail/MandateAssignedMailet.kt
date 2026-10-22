package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/** Tells the customer (the owner, for a listing) which agent its mandate is assigned to, and how to reach it. */
@Service
class MandateAssignedMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Un agent a été assigné à votre mandat à {{place}}"
        const val TEMPLATE = "/mail/mandate/assigned.mjml"
        const val TEMPLATE_LISTING = "/mail/mandate/assigned_listing.mjml"
    }

    fun send(mandate: MandateEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val party = customer.party
        val fullName = party.fullName()
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{place}}", MandateMailLabels.place(mandate, city, locationService)),
                body = mailBodyResolver.resolve(
                    path = if (mandate.mandateType == MandateType.LISTING) TEMPLATE_LISTING else TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "agent_full_name" to agent.party.fullName(),
                        "agent_photo_url" to agent.party.photoUrl?.ifBlank { null },
                        "agent_presentation" to agent.biography?.ifBlank { null },
                    )
                )
            )
        )
    }
}
