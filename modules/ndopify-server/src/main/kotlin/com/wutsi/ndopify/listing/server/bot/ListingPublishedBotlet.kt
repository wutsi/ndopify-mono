package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingUrls
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent of a listing that it's published, over Telegram: the same wording as `ListingPublishedMailet`, with
 * the `Voir l'annonce` button.
 *
 * This botlet only builds the caption and the button: [ListingTelegramSender] decides whether the message is the
 * listing's photo with the caption, or the text alone.
 */
@Service
class ListingPublishedBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val sender: ListingTelegramSender,
) {
    /**
     * Sends the publication to the agent's chat. The recipient is the agent's chat, from #135's link
     * (`AgentTelegramService.chatId`), never its e-mail. Telegram refusing the text throws
     * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
     */
    fun send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val caption = caption(listing, customer)
        val buttons = ListingUrls.publicUrl(tenantService.findById(listing.tenantId!!), listing)
            ?.let { url -> listOf(TelegramButton("Voir l'annonce", url = url)) }
            ?: emptyList()
        sender.send(chatId, listing, caption, buttons)
    }

    private fun caption(listing: ListingEntity, customer: CustomerEntity): String {
        val lines = mutableListOf<String>()
        val city = locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }
        lines.add("Votre annonce" + (city?.let { " à $it" } ?: "") + " est publiée")
        lines.add("")
        lines.add("Le bien de ${customer.party.fullName()} est en ligne.")
        lines.add("")
        lines.add("Annonce: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        city?.let { lines.add("Ville: $it") }
        listing.neighborhoodId
            ?.let { id -> locationService.findByIdOrNull(id)?.name }
            ?.ifBlank { null }
            ?.let { lines.add("Quartier: $it") }
        lines.add("")
        lines.add("Prochaine étape : partagez le lien de l'annonce et organisez les visites.")
        return lines.joinToString("\n")
    }
}
