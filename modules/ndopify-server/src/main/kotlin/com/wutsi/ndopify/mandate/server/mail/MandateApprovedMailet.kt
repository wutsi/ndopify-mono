package com.wutsi.ndopify.mandate.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/** Tells the agent that the customer signed its mandate's contract: the mandate is approved, the work can start. */
@Service
class MandateApprovedMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: Votre mandat à {{city}} est approuvé"
        const val TEMPLATE = "/mail/mandate/approved.mjml"
        const val TEMPLATE_LISTING = "/mail/mandate/approved_listing.mjml"
    }

    fun send(mandate: MandateEntity, contract: ContractEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(mandate.tenantId!!)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = agent.party
        val fullName = party.fullName()
        val formatter = TenantFormatter(tenant)
        val listing = mandate.mandateType == MandateType.LISTING
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name).replace("{{city}}", city),
                body = mailBodyResolver.resolve(
                    path = if (listing) TEMPLATE_LISTING else TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "customer_name" to customer.party.fullName(),
                        "contract_number" to contract.number,
                        "listing_type" to MandateMailLabels.listingType(mandate),
                        "property_type" to MandateMailLabels.propertyType(mandate),
                        "city" to city,
                        "neighbourhoods" to MandateMailLabels.neighbourhoods(mandate, locationService),
                        "valid_from" to contract.validFrom?.let { date -> formatter.date(date) },
                        "valid_until" to contract.validUntil?.let { date -> formatter.date(date) },
                        "mandate_url" to tenant.partnerCentralUrl?.let { url -> "$url/mandates/${mandate.id}" },
                    ) + if (listing) {
                        mapOf(
                            "price_label" to MandateMailLabels.priceLabel(mandate),
                            "price" to MandateMailLabels.budget(mandate, formatter),
                        )
                    } else {
                        mapOf("budget" to MandateMailLabels.budget(mandate, formatter))
                    }
                )
            )
        )
    }
}
