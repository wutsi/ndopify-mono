package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.refdata.server.domain.TenantEntity

/**
 * What a listing agent is told about the other agents' interest in its listings, built once per run by
 * [CoBrokerageInterestDigestBuilder] so that the e-mail ([com.wutsi.ndopify.match.server.mail.CoBrokerageInterestMailet])
 * and the Telegram message ([com.wutsi.ndopify.match.server.bot.CoBrokerageInterestBotlet]) render the same grouping.
 *
 * The interested agents' identity and contact may be shown; the search CUSTOMER is never read nor carried — by
 * construction there is no `CustomerService` in the builder (anti-sautage).
 *
 * [agentCount] is the number of distinct known search agents across all listings: what the e-mail's subject counts.
 * [listings] are best score first.
 */
data class CoBrokerageInterestDigest(
    val tenant: TenantEntity,
    val agentCount: Int,
    val listings: List<ListingInterest>,
)

/**
 * One of the listing agent's listings that other agents' searches showed interest in: its own [title] (null when it has
 * none), the [price] and [place] shown to the agent, its cover [imageUrl], the link to its [mandateUrl] (null without a
 * partner portal), and the interested [agents] (best score first, at most [CoBrokerageInterestDigestBuilder.MAX_AGENTS],
 * the others counted in [more], null when none are left out). [listingId] is the listing's id, for a channel's fallback
 * label.
 */
data class ListingInterest(
    val listingId: Long,
    val title: String?,
    val price: String,
    val place: String,
    val imageUrl: String?,
    val mandateUrl: String?,
    val agents: List<InterestedAgent>,
    val more: Int?,
)

/**
 * One search agent interested in a listing, as the listing agent may see it: its [fullName], the WhatsApp contact
 * ([whatsapp] formatted by the tenant, [whatsappUrl]) or else the [email] ([emailUrl] `mailto:`), its [photoUrl], and
 * its ANONYMIZED search criteria ([listingType], [propertyType], [place], [roomsLabel], [budget]) — never the search
 * customer. [searchCount] is set only when several of its searches matched the listing.
 */
data class InterestedAgent(
    val fullName: String,
    val whatsapp: String?,
    val whatsappUrl: String?,
    val email: String?,
    val emailUrl: String?,
    val photoUrl: String?,
    val listingType: String,
    val propertyType: String?,
    val place: String,
    val roomsLabel: String?,
    val budget: String,
    val searchCount: Int?,
)
