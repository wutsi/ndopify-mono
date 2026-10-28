package com.wutsi.ndopify.listing.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the customer (the property's owner) and the agent of a listing that it's published, with a link to its public
 * page: the tenant's `publicPortalUrl` followed by the listing's `url`, none without either.
 */
@Service
class ListingPublishedMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT_CUSTOMER = "{{tenant_name}}: Votre bien à {{city}} est en ligne"
        const val SUBJECT_AGENT = "{{tenant_name}}: Votre annonce à {{city}} est publiée"
        const val TEMPLATE_CUSTOMER = "/mail/listing/published_customer.mjml"
        const val TEMPLATE_AGENT = "/mail/listing/published_agent.mjml"
    }

    fun send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(listing.tenantId!!)
        val city = locationService.findByIdOrNull(listing.cityId)?.name ?: ""
        val context = mapOf(
            "tenant_logo_url" to tenant.logoUrl,
            "tenant_name" to tenant.name,
            "customer_name" to customer.party.fullName(),
            "agent_name" to agent.party.fullName(),
            "title" to listing.title,
            "city" to city,
            "neighbourhood" to listing.neighborhoodId?.let { id -> locationService.findByIdOrNull(id)?.name },
            "listing_url" to listingUrl(tenant, listing),
        )

        send(tenant, customer.party, SUBJECT_CUSTOMER, TEMPLATE_CUSTOMER, city, context)
        send(tenant, agent.party, SUBJECT_AGENT, TEMPLATE_AGENT, city, context)
    }

    private fun send(
        tenant: TenantEntity,
        party: PartyEntity,
        subject: String,
        template: String,
        city: String,
        context: Map<String, Any?>,
    ) {
        val fullName = party.fullName()
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = subject.replace("{{tenant_name}}", tenant.name).replace("{{city}}", city),
                body = mailBodyResolver.resolve(
                    path = template,
                    context = context + ("recipient_name" to fullName),
                ),
            )
        )
    }

    private fun listingUrl(tenant: TenantEntity, listing: ListingEntity): String? {
        val baseUrl = tenant.publicPortalUrl ?: return null
        val url = listing.url ?: return null
        return baseUrl.removeSuffix("/") + url
    }
}
