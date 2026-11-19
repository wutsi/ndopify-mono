package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.MimeUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

/**
 * Sends one Telegram message about a listing: the listing's cover as a photo with [text] as its caption, or [text] alone.
 *
 * The photo is the listing's cover (`ListingImageService.cover`), read from the storage and uploaded to Telegram: its
 * URL is never given to Telegram to fetch. The photo is used only when the text fits Telegram's caption limit
 * ([CAPTION_MAX_LENGTH] code points); a longer text is sent as a text message, never truncated, without reading the
 * image. An image the storage can't read or Telegram refuses only drops the photo: the text is sent anyway, so the
 * notification is never lost. Telegram refusing the text throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]), which the notifiers turn into their e-mail fallback.
 *
 * [imageService] is lazy: `ListingImageService` depends on `ListingService`, which owns the publication notifier and
 * this chain. Neither the bot token nor the image URL is ever logged.
 */
@Service
class ListingTelegramSender(
    @Lazy private val imageService: ListingImageService,
    private val storageProvider: StorageServiceProvider,
    private val client: TelegramClient,
) {
    companion object {
        const val CAPTION_MAX_LENGTH = 1024
        private val LOGGER = LoggerFactory.getLogger(ListingTelegramSender::class.java)
    }

    fun send(chatId: Long, listing: ListingEntity, text: String, buttons: List<TelegramButton> = emptyList()) {
        if (text.codePointCount(0, text.length) <= CAPTION_MAX_LENGTH) {
            val cover = imageService.cover(listing)
            if (cover != null && sendPhoto(chatId, listing, cover, text, buttons)) {
                return
            }
        }
        client.sendMessage(chatId, text, buttons)
    }

    // Downloads the image and uploads it: any failure only drops the photo, never the message
    private fun sendPhoto(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ): Boolean {
        return try {
            val out = ByteArrayOutputStream()
            storageProvider.get().get(cover.path, out)
            client.sendPhoto(
                chatId,
                out.toByteArray(),
                "${listing.id}.${MimeUtils.getExtensionFromMimeType(cover.mimeType)}",
                cover.mimeType ?: "image/jpeg",
                text,
                buttons,
            )
            true
        } catch (ex: Exception) {
            LOGGER.warn("Telegram photo failed for Listing#${listing.id} (image ${cover.id}), sending the text only", ex)
            false
        }
    }
}
