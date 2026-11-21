package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.mail.ListingSentBackCopy
import com.wutsi.ndopify.listing.server.mail.ListingUrls
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the agent of a listing sent back to DRAFT for correction, over Telegram: the generic wording of
 * [ListingSentBackCopy], the property's label, and a `Corriger l'annonce` button to its Partner Central page.
 *
 * The recipient is the agent's chat, from #135's link (`AgentTelegramService.chatId`), never its e-mail.
 */
@Service
class ListingSentBackBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val client: TelegramClient,
) {
    /**
     * Sends the send-back to the agent's chat. Telegram refusing the message throws
     * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail. The
     * button is left out without a `partnerCentralUrl`, and the place when the city / neighbourhood is unknown.
     */
    fun send(listing: ListingEntity, chatId: Long) {
        val tenant = tenantService.findById(listing.tenantId!!)
        val city = locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }
        val neighbourhood = listing.neighborhoodId
            ?.let { id -> locationService.findByIdOrNull(id)?.name }
            ?.ifBlank { null }

        val lines = listOf(
            "${ListingSentBackCopy.HEADLINE} " +
                "${ListingSentBackCopy.property(listing.id!!, city, neighbourhood)} " +
                ListingSentBackCopy.STATUS,
            "",
            ListingSentBackCopy.NEXT_STEP,
        )
        val buttons = ListingUrls.fixUrl(tenant, listing)
            ?.let { url -> listOf(TelegramButton(ListingSentBackCopy.BUTTON, url = url)) }
            ?: emptyList()
        client.sendMessage(chatId, lines.joinToString("\n"), buttons)
    }
}
