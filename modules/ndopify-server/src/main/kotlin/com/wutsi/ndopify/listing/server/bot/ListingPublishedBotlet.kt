package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingUrls
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent of a listing that it's published, over Telegram: the listing's property line behind a `🏠`, with the
 * `Voir l'annonce` button.
 *
 * This botlet only builds the caption and the button: [ListingTelegramSender] decides whether the message is the
 * listing's photo with the caption, or the text alone.
 */
@Service
class ListingPublishedBotlet(
    private val tenantService: TenantService,
    private val labels: ListingTelegramLabels,
    private val sender: ListingTelegramSender,
) {
    /**
     * Sends the publication to the agent's chat. The recipient is the agent's chat, from #135's link
     * (`AgentTelegramService.chatId`), never its e-mail. Telegram refusing the text throws
     * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
     */
    fun send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val tenant = tenantService.findById(listing.tenantId!!)
        val caption = "🏠 Votre annonce est publiée : " + labels.titleAndPrice(listing, TenantFormatter(tenant))
        val buttons = ListingUrls.publicUrl(tenant, listing)
            ?.let { url -> listOf(TelegramButton("Voir l'annonce", url = url)) }
            ?: emptyList()
        sender.send(chatId, listing, caption, buttons)
    }
}
