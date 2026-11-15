package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.mail.ListingUrls
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.MimeUtils
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

/**
 * Tells the agent of a listing that it's published, over Telegram: the same wording as `ListingPublishedMailet`, as the
 * caption of one photo message whose keyboard is the `Voir l'annonce` button.
 *
 * The image is the listing's cover (`ListingImageService.cover`), read from the storage and uploaded to Telegram: its
 * URL is never given to Telegram to fetch. An image the storage can't read or Telegram refuses only drops the photo:
 * the text is sent anyway, so the publication is never lost. [imageService] is lazy, as in the mailet: `ListingService`
 * owns the caller and `ListingImageService` depends on it.
 */
@Service
class ListingPublishedBotlet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    @Lazy private val imageService: ListingImageService,
    private val storageProvider: StorageServiceProvider,
    private val client: TelegramClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingPublishedBotlet::class.java)
    }

    /**
     * Sends the publication to the agent's chat. The recipient is the agent's chat, from #135's link
     * (`AgentTelegramService.chatId`), never its e-mail. Telegram refusing the text throws
     * ([com.wutsi.ndopify.platform.telegram.TelegramException]): the notifier then falls back to the e-mail.
     */
    fun send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long) {
        val caption = caption(listing, customer, agent)
        val buttons = ListingUrls.publicUrl(tenantService.findById(listing.tenantId!!), listing)
            ?.let { url -> listOf(TelegramButton("Voir l'annonce", url = url)) }
            ?: emptyList()

        val cover = imageService.cover(listing)
        if (cover != null && sendPhoto(listing, cover, caption, buttons, chatId)) {
            return
        }
        client.sendMessage(chatId, caption, buttons)
    }

    private fun caption(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity): String {
        val lines = mutableListOf<String>()
        lines.add("Bonjour ${agent.party.fullName()},")
        lines.add("")
        lines.add("L'annonce du bien de ${customer.party.fullName()} est désormais publiée.")
        lines.add("")
        lines.add("Annonce: " + (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"))
        locationService.findByIdOrNull(listing.cityId)?.name?.ifBlank { null }?.let { lines.add("Ville: $it") }
        listing.neighborhoodId
            ?.let { id -> locationService.findByIdOrNull(id)?.name }
            ?.ifBlank { null }
            ?.let { lines.add("Quartier: $it") }
        lines.add("")
        lines.add("Prochaine étape : partagez le lien de l'annonce et organisez les visites.")
        return lines.joinToString("\n")
    }

    // Downloads the image and uploads it: any failure only drops the photo, never the notification. Neither the token
    // nor the image URL is ever logged.
    private fun sendPhoto(
        listing: ListingEntity,
        cover: ListingImageEntity,
        caption: String,
        buttons: List<TelegramButton>,
        chatId: Long,
    ): Boolean {
        return try {
            val out = ByteArrayOutputStream()
            storageProvider.get().get(cover.path, out)
            client.sendPhoto(
                chatId,
                out.toByteArray(),
                "${listing.id}.${MimeUtils.getExtensionFromMimeType(cover.mimeType)}",
                cover.mimeType ?: "image/jpeg",
                caption,
                buttons,
            )
            true
        } catch (ex: Exception) {
            LOGGER.warn("Telegram photo failed for Listing#${listing.id} (image ${cover.id}), sending the text only", ex)
            false
        }
    }
}
