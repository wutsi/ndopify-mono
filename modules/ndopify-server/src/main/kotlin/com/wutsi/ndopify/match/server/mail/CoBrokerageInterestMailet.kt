package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.match.server.service.CoBrokerageInterestDigest
import com.wutsi.ndopify.match.server.service.InterestedAgent
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import org.springframework.stereotype.Service

/**
 * Tells a listing agent that OTHER agents' searches showed interest in its listings: one e-mail grouped by listing
 * (best score first), then, within a listing, one row per interested search agent (best score first) with its
 * anonymized criteria, at most [com.wutsi.ndopify.match.server.service.CoBrokerageInterestDigestBuilder.MAX_AGENTS]
 * each, the others counted.
 *
 * It renders the [CoBrokerageInterestDigest] built once for the agent by
 * [com.wutsi.ndopify.match.server.service.CoBrokerageInterestDigestBuilder], the same one the Telegram message renders.
 * The interested agent's identity/contact may be shown, but the search CUSTOMER is never read nor rendered. The CTA is
 * the listing agent's own mandate, absent without a partner portal. Sent by the co-brokerage job, as the fallback of
 * the Telegram channel. The e-mail's listing photos are resized through [MailImages]; the digest keeps the raw URL.
 */
@Service
class CoBrokerageInterestMailet(
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val imageService: ImageService,
) {
    companion object {
        const val SUBJECT_ONE = "{{tenant_name}}: 1 agent s'intéresse à votre bien"
        const val SUBJECT_MANY = "{{tenant_name}}: {{count}} agents s'intéressent à vos biens"
        const val TEMPLATE = "/mail/match/co_brokerage_interest.mjml"
    }

    /** [agent]: the listing agent (the recipient), in [digest]'s tenant. No e-mail without a listing to show. */
    fun send(agent: AgentEntity, digest: CoBrokerageInterestDigest) {
        if (digest.listings.isEmpty()) {
            return
        }
        val tenant = digest.tenant
        val fullName = agent.party.fullName()
        val subject = if (digest.agentCount == 1) {
            SUBJECT_ONE
        } else {
            SUBJECT_MANY.replace("{{count}}", "${digest.agentCount}")
        }
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = fullName),
                subject = subject.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "listings" to digest.listings.map { listing ->
                            mapOf(
                                "image_url" to MailImages.imageUrl(listing.imageUrl, imageService, MailImages.THUMBNAIL),
                                "title" to (listing.title ?: "Bien n° ${listing.listingId}"),
                                "price" to listing.price,
                                "mandate_url" to listing.mandateUrl,
                                "agents" to listing.agents.map { interested -> interested.toContext() },
                                "more" to listing.more,
                            )
                        },
                    ),
                ),
            ),
        )
    }

    // The keys of mail/match/co_brokerage_interest.mjml, with the digest's values
    private fun InterestedAgent.toContext(): Map<String, Any?> = mapOf(
        "agent_full_name" to fullName,
        "agent_photo_url" to photoUrl,
        "agent_whatsapp" to whatsapp,
        "agent_whatsapp_url" to whatsappUrl,
        "agent_email" to email,
        "agent_email_url" to emailUrl,
        "search_count" to searchCount,
        "listing_type" to listingType,
        "property_type" to propertyType,
        "place" to place,
        "rooms_label" to roomsLabel,
        "budget" to budget,
    )
}
