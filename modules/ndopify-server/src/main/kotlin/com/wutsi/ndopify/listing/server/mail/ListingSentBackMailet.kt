package com.wutsi.ndopify.listing.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * E-mails the agent of a listing sent back to DRAFT for correction, with the generic wording of
 * [ListingSentBackCopy]: the property's label and a `Corriger l'annonce` button to its Partner Central page. The
 * button is left out without a `partnerCentralUrl`.
 *
 * This is the channel of an agent whose chat isn't linked, and the fallback of a failed Telegram send.
 */
@Service
class ListingSentBackMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT_AGENT = ListingSentBackCopy.SUBJECT
        const val TEMPLATE_AGENT = "/mail/listing/sent_back_agent.mjml"
    }

    fun send(listing: ListingEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(listing.tenantId!!)
        val city = locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }
        val neighbourhood = listing.neighborhoodId
            ?.let { id -> locationService.findByIdOrNull(id)?.name }
            ?.ifBlank { null }
        val fullName = agent.party.fullName()

        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = fullName),
                subject = SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE_AGENT,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "property" to ListingSentBackCopy.property(listing.id!!, city, neighbourhood),
                        "fix_url" to ListingUrls.fixUrl(tenant, listing),
                    ),
                ),
            )
        )
    }
}
